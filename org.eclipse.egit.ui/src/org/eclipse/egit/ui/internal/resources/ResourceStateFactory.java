/*******************************************************************************
 * Copyright (C) 2007, IBM Corporation and others
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2008, Google Inc.
 * Copyright (C) 2008, Tor Arne Vestbø <torarnv@gmail.com>
 * Copyright (C) 2011, Dariusz Luksza <dariusz@luksza.org>
 * Copyright (C) 2011, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2015, Thomas Wolf <thomas.wolf@paranor.ch>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Thomas Wolf <thomas.wolf@paranor.ch> - Factored out from DecoratableResourceAdapter
 *                                           and GitLightweightDecorator
 *******************************************************************************/
package org.eclipse.egit.ui.internal.resources;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.egit.core.Activator;
import org.eclipse.egit.core.internal.indexdiff.IndexDiffCacheEntry;
import org.eclipse.egit.core.internal.indexdiff.IndexDiffData;
import org.eclipse.egit.core.internal.util.ResourceUtil;
import org.eclipse.egit.ui.internal.resources.IResourceState.StagingState;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;

/**
 * Factory for creating {@link IResourceState}s.
 */
public class ResourceStateFactory {

	@NonNull
	private static final ResourceStateFactory INSTANCE = new ResourceStateFactory();

	@NonNull
	private static final IResourceState UNKNOWN_STATE = new ResourceState();

	/**
	 * Retrieves the singleton instance of the {@link ResourceStateFactory}.
	 *
	 * @return the factory singleton
	 */
	@NonNull
	public static ResourceStateFactory getInstance() {
		return INSTANCE;
	}

	/**
	 * Returns the {@link IndexDiffData} for a given {@link IResource}, provided
	 * the resource exists and belongs to a git-tracked project.
	 *
	 * @param resource
	 *            context to get the repository to get the index diff data from
	 * @return the IndexDiffData, or {@code null} if none.
	 */
	@Nullable
	public IndexDiffData getIndexDiffDataOrNull(@Nullable IResource resource) {
		if (resource == null || resource.getType() == IResource.ROOT) {
			return null;
		}
		IPath path = resource.getLocation();
		if (path == null) {
			return null;
		}
		return getIndexDiffDataOrNull(path.toFile());
	}

	/**
	 * Returns the {@link IndexDiffData} for a given {@link File}, provided the
	 * file is in a git repository working tree.
	 *
	 * @param file
	 *            context to get the repository to get the index diff data from
	 * @return the IndexDiffData, or {@code null} if none.
	 */
	@Nullable
	public IndexDiffData getIndexDiffDataOrNull(@Nullable File file) {
		if (file == null) {
			return null;
		}
		File absoluteFile = file.getAbsoluteFile();
		Repository repository = Activator.getDefault().getRepositoryCache()
				.getRepository(new org.eclipse.core.runtime.Path(
						absoluteFile.getPath()));
		if (repository == null) {
			return null;
		} else if (repository.isBare()) {
			// For bare repository just return empty data
			return new IndexDiffData();
		}
		IndexDiffCacheEntry diffCacheEntry = Activator.getDefault()
				.getIndexDiffCache().getIndexDiffCacheEntry(repository);
		if (diffCacheEntry == null) {
			return null;
		}
		return diffCacheEntry.getIndexDiff();

	}

	/**
	 * Determines the repository state of the given {@link IResource}.
	 *
	 * @param resource
	 *            to get the state for
	 * @return the state
	 */
	@NonNull
	public IResourceState get(@NonNull IResource resource) {
		IndexDiffData indexDiffData = getIndexDiffDataOrNull(resource);
		if (indexDiffData == null) {
			return UNKNOWN_STATE;
		}
		return get(indexDiffData, resource);
	}

	/**
	 * Determines the repository state of the given {@link File}.
	 *
	 * @param file
	 *            to get the state for
	 * @return the state
	 */
	@NonNull
	public IResourceState get(@NonNull File file) {
		IndexDiffData indexDiffData = getIndexDiffDataOrNull(file);
		if (indexDiffData == null) {
			return UNKNOWN_STATE;
		}
		return get(indexDiffData, file);
	}

	/**
	 * Computes an {@link IResourceState} for the given {@link IResource} from
	 * the given {@link IndexDiffData}.
	 *
	 * @param indexDiffData
	 *            to compute the state from
	 * @param resource
	 *            to get the state of
	 * @return the state
	 */
	@NonNull
	public IResourceState get(@NonNull IndexDiffData indexDiffData,
			@NonNull IResource resource) {
		IPath path = resource.getLocation();
		if (path != null) {
			File file = path.toFile();
			if (file != null) {
				return get(indexDiffData, file);
			}
		}
		return UNKNOWN_STATE;
	}

	/**
	 * Computes an {@link IResourceState} for the given {@link File} from the
	 * given {@link IndexDiffData}.
	 *
	 * @param indexDiffData
	 *            to compute the state from
	 * @param file
	 *            to get the state of
	 * @return the state
	 */
	@NonNull
	public IResourceState get(@NonNull IndexDiffData indexDiffData,
			@NonNull File file) {
		ResourceState result = new ResourceState();
		File absoluteFile = file.getAbsoluteFile();
		IPath path = new org.eclipse.core.runtime.Path(absoluteFile.getPath());
		Repository repository = Activator.getDefault().getRepositoryCache()
				.getRepository(path);
		if (repository == null || repository.isBare()) {
			return result;
		}
		File workTree = repository.getWorkTree();
		String repoRelativePath = path.makeRelativeTo(
				new org.eclipse.core.runtime.Path(workTree.getAbsolutePath()))
				.toString();
		if (repoRelativePath.isEmpty()
				|| repoRelativePath.equals(path.toString())) {
			return result;
		}
		if (file.isDirectory()) {
			if (ResourceUtil.isSymbolicLink(repository, repoRelativePath)) {
				extractFileProperties(indexDiffData, repoRelativePath,
						absoluteFile, result);
			} else {
				extractContainerProperties(indexDiffData, repoRelativePath,
						absoluteFile, result);
			}
		} else {
			extractFileProperties(indexDiffData, repoRelativePath, absoluteFile,
					result);
		}
		return result;
	}

	private void extractFileProperties(@NonNull IndexDiffData indexDiffData,
			@NonNull String repoRelativePath, @NonNull File file,
			@NonNull ResourceState state) {
		Set<String> ignoredFiles = indexDiffData.getIgnoredNotInIndex();
		boolean ignored = ignoredFiles.contains(repoRelativePath)
				|| containsPrefixPath(ignoredFiles, repoRelativePath);
		state.setIgnored(ignored);
		Set<String> untracked = indexDiffData.getUntracked();
		state.setTracked(!ignored && !untracked.contains(repoRelativePath));

		Set<String> added = indexDiffData.getAdded();
		Set<String> removed = indexDiffData.getRemoved();
		Set<String> changed = indexDiffData.getChanged();
		if (added.contains(repoRelativePath)) {
			state.setStagingState(StagingState.ADDED);
		} else if (removed.contains(repoRelativePath)) {
			state.setStagingState(StagingState.REMOVED);
		} else if (changed.contains(repoRelativePath)) {
			state.setStagingState(StagingState.MODIFIED);
		} else {
			state.setStagingState(StagingState.NOT_STAGED);
		}

		// conflicting
		Set<String> conflicting = indexDiffData.getConflicting();
		state.setConflicts(conflicting.contains(repoRelativePath));

		// locally modified
		Set<String> modified = indexDiffData.getModified();
		state.setDirty(modified.contains(repoRelativePath));

		// unstaged deletion; for instance in Synchronize View
		if (!file.exists() && !state.isStaged()) {
			state.setDirty(true);
		}
	}

	private void extractContainerProperties(
			@NonNull IndexDiffData indexDiffData,
			@NonNull String repoRelativePath, @NonNull File directory,
			@NonNull ResourceState state) {
		if (!repoRelativePath.endsWith("/")) { //$NON-NLS-1$
			repoRelativePath += '/';
		}

		Set<String> ignoredFiles = indexDiffData.getIgnoredNotInIndex();
		Set<String> untrackedFolders = indexDiffData.getUntrackedFolders();
		boolean ignored = containsPrefixPath(ignoredFiles, repoRelativePath)
				|| !hasContainerAnyFiles(directory);
		state.setIgnored(ignored);
		state.setTracked(!ignored
				&& !containsPrefixPath(untrackedFolders, repoRelativePath));

		// containers are marked as staged whenever file was added, removed or
		// changed
		Set<String> changed = new HashSet<String>(indexDiffData.getChanged());
		changed.addAll(indexDiffData.getAdded());
		changed.addAll(indexDiffData.getRemoved());
		if (containsPrefix(changed, repoRelativePath)) {
			state.setStagingState(StagingState.MODIFIED);
		} else {
			state.setStagingState(StagingState.NOT_STAGED);
		}
		// conflicting
		Set<String> conflicting = indexDiffData.getConflicting();
		state.setConflicts(containsPrefix(conflicting, repoRelativePath));

		// locally modified / untracked
		Set<String> modified = indexDiffData.getModified();
		Set<String> untracked = indexDiffData.getUntracked();
		Set<String> missing = indexDiffData.getMissing();
		state.setDirty(containsPrefix(modified, repoRelativePath)
				|| containsPrefix(untracked, repoRelativePath)
				|| containsPrefix(missing, repoRelativePath));
	}

	private boolean containsPrefix(Set<String> collection, String prefix) {
		// when prefix is empty we are handling repository root, therefore we
		// should return true whenever collection isn't empty
		if (prefix.length() == 1 && !collection.isEmpty())
			return true;

		for (String path : collection)
			if (path.startsWith(prefix))
				return true;
		return false;
	}

	private boolean containsPrefixPath(Set<String> collection, String path) {
		for (String entry : collection) {
			String entryPath;
			if (entry.endsWith("/")) //$NON-NLS-1$
				entryPath = entry;
			else
				entryPath = entry + "/"; //$NON-NLS-1$
			if (path.startsWith(entryPath))
				return true;
		}
		return false;
	}

	private boolean hasContainerAnyFiles(@NonNull File directory) {
		try {
			final boolean[] result = new boolean[] { false };
			Files.walkFileTree(directory.toPath(),
					new FileVisitor<Path>() {

						@Override
						public FileVisitResult preVisitDirectory(Path dir,
								BasicFileAttributes attrs) throws IOException {
							if (Constants.DOT_GIT.equals(dir.getFileName())) {
								return FileVisitResult.SKIP_SUBTREE;
							}
							return FileVisitResult.CONTINUE;
						}

						@Override
						public FileVisitResult visitFile(Path file,
								BasicFileAttributes attrs) throws IOException {
							if (!attrs.isDirectory()) {
								result[0] = true;
								return FileVisitResult.TERMINATE;
							}
							return FileVisitResult.CONTINUE;
						}

						@Override
						public FileVisitResult visitFileFailed(Path file,
						IOException exc) throws IOException {
							return FileVisitResult.CONTINUE;
						}

						@Override
						public FileVisitResult postVisitDirectory(Path dir,
						IOException exc) throws IOException {
							return FileVisitResult.CONTINUE;
						}

					});
			return result[0];
		} catch (IOException e) {
			return false;
		}
	}
}
