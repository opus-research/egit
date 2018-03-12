/*******************************************************************************
 * Copyright (C) 2011, 2013 Jens Baumgart <jens.baumgart@sap.com> and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core.internal.indexdiff;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.IFileBuffer;
import org.eclipse.core.filebuffers.IFileBufferListener;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.core.Activator;
import org.eclipse.egit.core.JobFamilies;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.Repository;

/**
 * This class provides access to a cached {@link IndexDiff} for a given
 * repository
 */
public class IndexDiffCache {

	static final String GITIGNORE_NAME = ".gitignore"; //$NON-NLS-1$

	private Map<Repository, IndexDiffCacheEntry> entries = new HashMap<Repository, IndexDiffCacheEntry>();

	private Set<IndexDiffChangedListener> listeners = new HashSet<IndexDiffChangedListener>();

	private IndexDiffChangedListener globalListener;

	private ExternalFileBufferListener bufferListener;

	/**
	 * Listener on buffer changes related to the workspace external files.
	 */
	static class ExternalFileBufferListener implements IFileBufferListener {

		private void updateRepoState(IFileBuffer buffer) {
			IFile file = getResource(buffer);
			if (file != null) {
				// this is a workspace file. Changes on those files are
				// monitored better (and differently) in IndexDiffCacheEntry.
				return;
			}

			// the file is not known in the workspace: we should check if it
			// contained in a Git repository we aware of
			Repository repo = getRepository(buffer);
			if (repo == null || repo.isBare()) {
				return;
			}
			IPath relativePath = getRelativePath(repo, buffer);
			if (relativePath == null) {
				return;
			}

			// manually trigger update of IndexDiffCacheEntry state
			IndexDiffCacheEntry diffEntry = Activator
					.getDefault().getIndexDiffCache()
					.getIndexDiffCacheEntry(repo);
			if (diffEntry != null) {
				// since .gitignore change can affect other files, reload index
				if (GITIGNORE_NAME.equals(relativePath.lastSegment())) {
					diffEntry.refresh();
				} else {
					diffEntry.refreshFiles(
						Collections.singleton(relativePath.toString()));
				}
			}
		}

		@Nullable
		private IPath getRelativePath(Repository repo, IFileBuffer buffer) {
			IPath path = getPath(buffer);
			if (path == null) {
				return null;
			}
			IPath repositoryRoot = new Path(repo.getWorkTree().getPath());
			return path.makeRelativeTo(repositoryRoot);
		}

		@Nullable
		private IFile getResource(IFileBuffer buffer) {
			IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
			IPath location = buffer.getLocation();
			if (location == null) {
				return null;
			}
			IFile file = root.getFile(location);
			if (!file.isAccessible()) {
				return null;
			}
			return file;
		}

		@Nullable
		private Repository getRepository(IFileBuffer buffer) {
			IPath location = getPath(buffer);
			if (location != null) {
				return org.eclipse.egit.core.Activator.getDefault()
						.getRepositoryCache().getRepository(location);
			}
			return null;
		}

		@Nullable
		private IPath getPath(IFileBuffer buffer) {
			IPath location = buffer.getLocation();
			if (location != null) {
				return location;
			}
			IFileStore store = buffer.getFileStore();
			if (store != null) {
				URI uri = store.toURI();
				if (uri != null) {
					try {
						File file = new File(uri);
						return new Path(file.getAbsolutePath());
					} catch (IllegalArgumentException e) {
						// ignore
					}
				}
			}
			return null;
		}

		@Override
		public void underlyingFileDeleted(IFileBuffer buffer) {
			updateRepoState(buffer);
		}

		@Override
		public void dirtyStateChanged(IFileBuffer buffer, boolean isDirty) {
			if (!isDirty) {
				updateRepoState(buffer);
			}
		}

		@Override
		public void underlyingFileMoved(IFileBuffer buffer, IPath path) {
			// nop
		}

		@Override
		public void stateValidationChanged(IFileBuffer buffer,
				boolean isStateValidated) {
			// nop
		}

		@Override
		public void stateChanging(IFileBuffer buffer) {
			// nop
		}

		@Override
		public void stateChangeFailed(IFileBuffer buffer) {
			// nop
		}

		@Override
		public void bufferDisposed(IFileBuffer buffer) {
			// nop
		}

		@Override
		public void bufferCreated(IFileBuffer buffer) {
			// nop
		}

		@Override
		public void bufferContentReplaced(IFileBuffer buffer) {
			// nop
		}

		@Override
		public void bufferContentAboutToBeReplaced(IFileBuffer buffer) {
			// nop
		}
	}

	/**
	 * constructor
	 */
	public IndexDiffCache() {
		createGlobalListener();
		registerBufferListener();
	}

	private void registerBufferListener() {
		bufferListener = new ExternalFileBufferListener();
		ITextFileBufferManager bufferManager = FileBuffers
				.getTextFileBufferManager();
		if (bufferManager != null) {
			bufferManager.addFileBufferListener(bufferListener);
		}
	}

	/**
	 * @param repository
	 * @return cache entry
	 */
	@Nullable
	public IndexDiffCacheEntry getIndexDiffCacheEntry(@NonNull Repository repository) {
		IndexDiffCacheEntry entry;
		synchronized (entries) {
			entry = entries.get(repository);
			if (entry != null)
				return entry;
			if (repository.isBare())
				return null;
			entry = new IndexDiffCacheEntry(repository);
			entries.put(repository, entry);
		}
		entry.addIndexDiffChangedListener(globalListener);
		return entry;
	}

	/**
	 * Adds a listener for IndexDiff changes. Note that only caches are
	 * available for those repositories for which getIndexDiffCacheEntry was
	 * called.
	 *
	 * @param listener
	 */
	public void addIndexDiffChangedListener(IndexDiffChangedListener listener) {
		synchronized (listeners) {
			listeners.add(listener);
		}
	}

	/**
	 * @param listener
	 */
	public void removeIndexDiffChangedListener(IndexDiffChangedListener listener) {
		synchronized (listeners) {
			listeners.remove(listener);
		}
	}

	private void createGlobalListener() {
		globalListener = new IndexDiffChangedListener() {
			@Override
			public void indexDiffChanged(Repository repository,
					IndexDiffData indexDiffData) {
				notifyListeners(repository, indexDiffData);
			}
		};
	}

	private void notifyListeners(Repository repository,
			IndexDiffData indexDiffData) {
		IndexDiffChangedListener[] tmpListeners;
		synchronized (listeners) {
			tmpListeners = listeners
					.toArray(new IndexDiffChangedListener[listeners.size()]);
		}
		for (int i = 0; i < tmpListeners.length; i++) {
			try {
				tmpListeners[i].indexDiffChanged(repository, indexDiffData);
			} catch (RuntimeException e) {
				Activator.logError(
						"Exception occured in an IndexDiffChangedListener", e); //$NON-NLS-1$
			}
		}
	}

	/**
	 * Used by {@link Activator}
	 */
	public void dispose() {
		if (bufferListener != null) {
			ITextFileBufferManager bufferManager = FileBuffers
					.getTextFileBufferManager();
			if (bufferManager != null) {
				bufferManager.removeFileBufferListener(bufferListener);
				bufferListener = null;
			}
		}
		for (IndexDiffCacheEntry entry : entries.values()) {
			entry.dispose();
		}
		Job.getJobManager().cancel(JobFamilies.INDEX_DIFF_CACHE_UPDATE);
		try {
			Job.getJobManager().join(JobFamilies.INDEX_DIFF_CACHE_UPDATE, null);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
