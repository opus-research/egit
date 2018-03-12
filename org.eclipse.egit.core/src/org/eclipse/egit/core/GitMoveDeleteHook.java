/*******************************************************************************
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2007, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2008, Google Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.team.IMoveDeleteHook;
import org.eclipse.core.resources.team.IResourceTree;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.egit.core.project.GitProjectData;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.TeamException;
import org.osgi.framework.Version;

class GitMoveDeleteHook implements IMoveDeleteHook {
	private static final boolean I_AM_DONE = true;

	private static final boolean FINISH_FOR_ME = false;

	private final GitProjectData data;

	GitMoveDeleteHook(final GitProjectData d) {
		Assert.isNotNull(d);
		data = d;
	}

	public boolean deleteFile(final IResourceTree tree, final IFile file,
			final int updateFlags, final IProgressMonitor monitor) {
		final boolean force = (updateFlags & IResource.FORCE) == IResource.FORCE;
		if (!force && !tree.isSynchronized(file, IResource.DEPTH_ZERO))
			return false;

		final RepositoryMapping map = RepositoryMapping.getMapping(file);
		if (map == null)
			return false;

		try {
			final DirCache dirc = map.getRepository().lockDirCache();
			final int first = dirc.findEntry(map.getRepoRelativePath(file));
			if (first < 0) {
				dirc.unlock();
				return false;
			}

			final DirCacheBuilder edit = dirc.builder();
			if (first > 0)
				edit.keep(0, first);
			final int next = dirc.nextEntry(first);
			if (next < dirc.getEntryCount())
				edit.keep(next, dirc.getEntryCount() - next);
			if (!edit.commit())
				tree.failed(new Status(IStatus.ERROR, Activator.getPluginId(),
						0, CoreText.MoveDeleteHook_operationError, null));
			tree.standardDeleteFile(file, updateFlags, monitor);
		} catch (IOException e) {
			tree.failed(new Status(IStatus.ERROR, Activator.getPluginId(), 0,
					CoreText.MoveDeleteHook_operationError, e));
		}
		return true;
	}

	public boolean deleteFolder(final IResourceTree tree, final IFolder folder,
			final int updateFlags, final IProgressMonitor monitor) {
		// Deleting a GIT repository which is in use is a pretty bad idea. To
		// delete disconnect the team provider first.
		//
		if (data.isProtected(folder)) {
			return cannotModifyRepository(tree);
		} else {
			return FINISH_FOR_ME;
		}
	}

	public boolean deleteProject(final IResourceTree tree,
			final IProject project, final int updateFlags,
			final IProgressMonitor monitor) {
		// TODO: Note that eclipse thinks folders are real, while
		// Git does not care.
		return FINISH_FOR_ME;
	}

	public boolean moveFile(final IResourceTree tree, final IFile srcf,
			final IFile dstf, final int updateFlags,
			final IProgressMonitor monitor) {
		final boolean force = (updateFlags & IResource.FORCE) == IResource.FORCE;
		if (!force && !tree.isSynchronized(srcf, IResource.DEPTH_ZERO))
			return false;

		final RepositoryMapping srcm = RepositoryMapping.getMapping(srcf);
		if (srcm == null)
			return false;
		final RepositoryMapping dstm = RepositoryMapping.getMapping(dstf);

		try {
			final DirCache sCache = srcm.getRepository().lockDirCache();
			final String sPath = srcm.getRepoRelativePath(srcf);
			final DirCacheEntry sEnt = sCache.getEntry(sPath);
			if (sEnt == null) {
				sCache.unlock();
				return false;
			}

			final DirCacheEditor sEdit = sCache.editor();
			sEdit.add(new DirCacheEditor.DeletePath(sEnt));
			if (dstm != null && dstm.getRepository() == srcm.getRepository()) {
				final String dPath = srcm.getRepoRelativePath(dstf);
				sEdit.add(new DirCacheEditor.PathEdit(dPath) {
					@Override
					public void apply(final DirCacheEntry dEnt) {
						dEnt.copyMetaData(sEnt);
					}
				});
			}
			if (!sEdit.commit())
				tree.failed(new Status(IStatus.ERROR, Activator.getPluginId(),
						0, CoreText.MoveDeleteHook_operationError, null));

			tree.standardMoveFile(srcf, dstf, updateFlags, monitor);
		} catch (IOException e) {
			tree.failed(new Status(IStatus.ERROR, Activator.getPluginId(), 0,
					CoreText.MoveDeleteHook_operationError, e));
		}
		return true;
	}

	public boolean moveFolder(final IResourceTree tree, final IFolder srcf,
			final IFolder dstf, final int updateFlags,
			final IProgressMonitor monitor) {
		final boolean force = (updateFlags & IResource.FORCE) == IResource.FORCE;
		if (!force && !tree.isSynchronized(srcf, IResource.DEPTH_ZERO))
			return false;

		final RepositoryMapping srcm = RepositoryMapping.getMapping(srcf);
		if (srcm == null)
			return false;
		final RepositoryMapping dstm = RepositoryMapping.getMapping(dstf);

		try {
			final String sPath = srcm.getRepoRelativePath(srcf);
			if (dstm != null && dstm.getRepository() == srcm.getRepository()) {
				final String dPath =
					srcm.getRepoRelativePath(dstf) + "/"; //$NON-NLS-1$
				if (!moveIndexContent(dPath, srcm, sPath))
					tree.failed(new Status(IStatus.ERROR, Activator.getPluginId(),
							0, CoreText.MoveDeleteHook_operationError, null));
				tree.standardMoveFolder(srcf, dstf, updateFlags, monitor);
			}
		} catch (IOException e) {
			tree.failed(new Status(IStatus.ERROR, Activator.getPluginId(), 0,
					CoreText.MoveDeleteHook_operationError, e));
		}
		return true;
	}

	public boolean moveProject(final IResourceTree tree, final IProject source,
			final IProjectDescription description, final int updateFlags,
			final IProgressMonitor monitor) {
		final RepositoryMapping srcm = RepositoryMapping.getMapping(source);
		if (srcm == null)
			return false;
		IPath newLocation = null;
		if (description.getLocationURI() != null)
			newLocation = URIUtil.toPath(description.getLocationURI());
		else
			newLocation = source.getWorkspace().getRoot().getLocation()
					.append(description.getName());
		IPath sourceLocation = source.getLocation();
		// Prevent a serious error. Remove the check after the release of Eclipse 3.8
		Version version = Platform.getBundle("org.eclipse.platform").getVersion(); //$NON-NLS-1$
		if (version.compareTo(Version.parseVersion("3.7.0")) < 0) { //$NON-NLS-1$
			if (sourceLocation.isPrefixOf(newLocation)) {
				// Graceful handling of bug. Require lots of work to handle
				tree.failed(new Status(
						IStatus.ERROR,
						Activator.getPluginId(),
						0,
						"Cannot move project. " + //$NON-NLS-1$
						"See https://bugs.eclipse.org/bugs/show_bug.cgi?id=307140 (resolved in 3.7)", null)); //$NON-NLS-1$
				return true;
			}
		}
		if (!srcm.getGitDir().startsWith("../")) { //$NON-NLS-1$
			// Graceful handling of bug. We can probably handle this with some
			// more work
			tree.failed(new Status(IStatus.ERROR, Activator.getPluginId(), 0,
					"Cannot move project. Project contains Git Repo", null)); //$NON-NLS-1$
			return true;
		}
		File newLocationFile = newLocation.toFile();
		// check if new location is below the same repository
		if (newLocationFile.getAbsolutePath().contains(
				srcm.getRepository().getWorkTree().getAbsolutePath())) {
			final String sPath = srcm.getRepoRelativePath(source);
			final String dPath = new Path(newLocationFile.getAbsolutePath()
					.substring(
							srcm.getRepository().getWorkTree()
									.getAbsolutePath().length() + 1)
					+ "/").toPortableString(); //$NON-NLS-1$
			try {
				// The Repository mapping does not support moving
				// projects, so just disconnect/reconnect for now
				RepositoryMapping mapping = RepositoryMapping
						.getMapping(source);
				IPath gitDir = mapping.getGitDirAbsolutePath();
				try {
					RepositoryProvider.unmap(source);
				} catch (TeamException e) {
					tree.failed(new Status(IStatus.ERROR, Activator
							.getPluginId(), 0,
							CoreText.MoveDeleteHook_operationError, e));
					return true; // Do not let Eclipse complete the operation
				}

				monitor.worked(100);

				if (!moveIndexContent(dPath, srcm, sPath)) {
					tree.failed(new Status(IStatus.ERROR, Activator
							.getPluginId(), 0,
							CoreText.MoveDeleteHook_operationError, null));
					return true;
				}
				tree.standardMoveProject(source, description, updateFlags,
						monitor);

				// Reconnect
				IProject destination = source.getWorkspace().getRoot()
						.getProject(description.getName());
				GitProjectData projectData = new GitProjectData(destination);
				RepositoryMapping repositoryMapping = new RepositoryMapping(
						destination, gitDir.toFile());
				projectData.setRepositoryMappings(Arrays
						.asList(repositoryMapping));
				projectData.store();
				GitProjectData.add(destination, projectData);
				RepositoryProvider.map(destination, GitProvider.class.getName());
				destination.refreshLocal(IResource.DEPTH_INFINITE,
						new SubProgressMonitor(monitor, 50));
			} catch (IOException e) {
				tree.failed(new Status(IStatus.ERROR, Activator.getPluginId(),
						0, CoreText.MoveDeleteHook_operationError, e));
			} catch (CoreException e) {
				tree.failed(new Status(IStatus.ERROR, Activator.getPluginId(),
						0, CoreText.MoveDeleteHook_operationError, e));
			}
			return true;
		}

		return FINISH_FOR_ME;
	}

	private boolean moveIndexContent(String dPath,
			final RepositoryMapping srcm, final String sPath) throws IOException {
		final DirCache sCache = srcm.getRepository().lockDirCache();
		final DirCacheEntry[] sEnt = sCache.getEntriesWithin(sPath);
		if (sEnt.length == 0) {
			sCache.unlock();
			return true;
		}

		final DirCacheEditor sEdit = sCache.editor();
		sEdit.add(new DirCacheEditor.DeleteTree(sPath));
		final int sPathLen = sPath.length() + 1;
		for (final DirCacheEntry se : sEnt) {
			final String p = se.getPathString().substring(sPathLen);
			sEdit.add(new DirCacheEditor.PathEdit(dPath + p) {
				@Override
				public void apply(final DirCacheEntry dEnt) {
					dEnt.copyMetaData(se);
				}
			});
		}
		return sEdit.commit();
	}

	private boolean cannotModifyRepository(final IResourceTree tree) {
		tree.failed(new Status(IStatus.ERROR, Activator.getPluginId(), 0,
				CoreText.MoveDeleteHook_cannotModifyFolder, null));
		return I_AM_DONE;
	}
}
