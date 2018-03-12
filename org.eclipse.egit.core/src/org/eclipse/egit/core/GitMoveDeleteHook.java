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
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.team.IMoveDeleteHook;
import org.eclipse.core.resources.team.IResourceTree;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.core.project.GitProjectData;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.osgi.util.NLS;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.TeamException;

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
				MoveResult result = moveIndexContent(dPath, srcm, sPath);
				switch (result) {
				case SUCCESS:
					break;
				case FAILED:
					tree.failed(new Status(IStatus.ERROR, Activator.getPluginId(),
							0, CoreText.MoveDeleteHook_operationError, null));
					return I_AM_DONE;
				case UNTRACKED:
					// we are not responsible for moving untracked files
					return FINISH_FOR_ME;
				}
			}
			tree.standardMoveFolder(srcf, dstf, updateFlags, monitor);
		} catch (IOException e) {
			tree.failed(new Status(IStatus.ERROR, Activator.getPluginId(), 0,
					CoreText.MoveDeleteHook_operationError, e));
		}
		return true;
	}

	private void mapProject(final IProject source,
			final IProjectDescription description,
			final IProgressMonitor monitor, IPath gitDir) throws CoreException,
			TeamException {
		IProject destination = source.getWorkspace().getRoot()
				.getProject(description.getName());
		GitProjectData projectData = new GitProjectData(destination);
		RepositoryMapping repositoryMapping = new RepositoryMapping(
				destination, gitDir.toFile());
		projectData.setRepositoryMappings(Arrays
				.asList(repositoryMapping));
		projectData.store();
		GitProjectData.add(destination, projectData);
		RepositoryProvider
				.map(destination, GitProvider.class.getName());
		destination.refreshLocal(IResource.DEPTH_INFINITE,
				new SubProgressMonitor(monitor, 50));
	}

	private boolean unmapProject(final IResourceTree tree, final IProject source) {
		// The Repository mapping does not support moving
		// projects, so just disconnect/reconnect for now
		try {
			RepositoryProvider.unmap(source);
		} catch (TeamException e) {
			tree.failed(new Status(IStatus.ERROR, Activator
					.getPluginId(), 0,
					CoreText.MoveDeleteHook_operationError, e));
					return true; // Do not let Eclipse complete the operation
		}
		return false;
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
//		// Prevent a serious error.
//		if (sourceLocation.isPrefixOf(newLocation)) {
//			// Graceful handling of bug. Require lots of work to handle
//			tree.failed(new Status(
//					IStatus.ERROR,
//					Activator.getPluginId(),
//					0,
//					"Cannot move project. See https://bugs.eclipse.org/bugs/show_bug.cgi?id=307140 (not resolved in 3.7)", //$NON-NLS-1$
//					null));
//			return true;
//		}
		File newLocationFile = newLocation.toFile();
		// check if new location is below the same repository
		Path workTree = new Path(srcm.getRepository().getWorkTree().getAbsolutePath());
		if (workTree.matchingFirstSegments(newLocation) == workTree.segmentCount()) {
			/*
			 * FIXME: Stefan Lay say: Aug 22 This leads to a
			 * java.lang.StringIndexOutOfBoundsException if I rename a project
			 * which has a repository in the project folder and which is located
			 * outside the eclipse workspace. In this case the location does not
			 * change. This error had been there before. I think it is the same
			 * as was reported in comment 2 of bug 334112. Mabe we could fix it
			 * in another change, but you could mention it in the commit message
			 * as a further limitation.
			 */
			final String sPath = srcm.getRepoRelativePath(source);
			final String absoluteWorkTreePath = srcm.getRepository().getWorkTree().getAbsolutePath();
			final String newLocationAbsolutePath = newLocationFile.getAbsolutePath();
			final String dPath;
			if (newLocationAbsolutePath.equals(absoluteWorkTreePath))
				dPath = ""; //$NON-NLS-1$
			else
				dPath = new Path(
						newLocationAbsolutePath.substring(absoluteWorkTreePath
								.length() + 1) + "/").toPortableString(); //$NON-NLS-1$
			try {
				IPath gitDir = srcm.getGitDirAbsolutePath();
				if (unmapProject(tree, source))
					return true;

				monitor.worked(100);

				MoveResult result = moveIndexContent(dPath, srcm, sPath);
				switch (result) {
				case SUCCESS:
					break;
				case FAILED:
					tree.failed(new Status(IStatus.ERROR, Activator
							.getPluginId(), 0,
							CoreText.MoveDeleteHook_operationError, null));
					break;
				case UNTRACKED:
					// we are not responsible for moving untracked files
					return FINISH_FOR_ME;
				}

				tree.standardMoveProject(source, description, updateFlags,
						monitor);

				// Reconnect
				mapProject(
						source.getWorkspace().getRoot()
								.getProject(description.getName()),
						description, monitor, gitDir);
			} catch (IOException e) {
				tree.failed(new Status(IStatus.ERROR, Activator.getPluginId(),
						0, CoreText.MoveDeleteHook_operationError, e));
			} catch (CoreException e) {
				tree.failed(new Status(IStatus.ERROR, Activator.getPluginId(),
						0, CoreText.MoveDeleteHook_operationError, e));
			}
			return true;
		} else if (newLocation.matchingFirstSegments(RepositoryMapping
				.getMapping(source).getGitDirAbsolutePath()) == RepositoryMapping
				.getMapping(source).getGitDirAbsolutePath().segmentCount() - 2) {

			// Moving repo, we need to unplug the previous location and
			// Re-plug it again with the new location.
			// This require that we actually can move the repo.
			// Especially on Windows there are many ways this could fail.
			Repository repository = srcm.getRepository();
			IPath gitDir = srcm.getGitDirAbsolutePath();
			if (unmapProject(tree, source))
				return true; // Error information in tree
			ISchedulingRule rule = ResourcesPlugin.getWorkspace().getRuleFactory().modifyRule(source);
			try {
				Job.getJobManager().beginRule(rule, null);
				repository.close(); // ? FIXME...
				if (false /*repository.isUsed()*/) { //FIXME
					// commented code works, but I susgpect that is by accident
					tree.failed(new Status(
							IStatus.ERROR,
							Activator.getPluginId(),
							0,
							NLS.bind(
									"Repository in use: ${0}", repository.getDirectory().getAbsolutePath()), new Throwable())); //$NON-NLS-1$
					return false;
				}
				monitor.worked(100);

				IPath relativeGitDir = gitDir.makeRelativeTo(sourceLocation);
				tree.standardMoveProject(source, description, updateFlags,
						monitor);

				IPath newGitDir = newLocation.append(relativeGitDir);
				// Reconnect
				try {
					mapProject(source, description, monitor, newGitDir);
				} catch (CoreException e) {
					tree.failed(new Status(IStatus.ERROR, Activator.getPluginId(),
							0, CoreText.MoveDeleteHook_operationError, e));
				}
			} finally {
				Job.getJobManager().endRule(rule);
			}
			return true; // We're done with the move
		} else
			return FINISH_FOR_ME;
	}

	enum MoveResult{SUCCESS, FAILED, UNTRACKED}

	private MoveResult moveIndexContent(String dPath,
			final RepositoryMapping srcm, final String sPath) throws IOException {
		final DirCache sCache = srcm.getRepository().lockDirCache();
		final DirCacheEntry[] sEnt = sCache.getEntriesWithin(sPath);
		if (sEnt.length == 0) {
			sCache.unlock();
			return MoveResult.UNTRACKED;
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
		if (sEdit.commit())
			return MoveResult.SUCCESS;
		else
			return MoveResult.FAILED;

	}

	private boolean cannotModifyRepository(final IResourceTree tree) {
		tree.failed(new Status(IStatus.ERROR, Activator.getPluginId(), 0,
				CoreText.MoveDeleteHook_cannotModifyFolder, null));
		return I_AM_DONE;
	}
}
