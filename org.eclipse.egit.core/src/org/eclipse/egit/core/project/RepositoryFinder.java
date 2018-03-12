/*******************************************************************************
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2007, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2008, Google Inc.
 * Copyright (C) 2012, François Rey <eclipse.org_@_francois_._rey_._name>
 * Copyright (C) 2013, Carsten Pfeiffer <carsten.pfeiffer@gebit.de>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core.project;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.egit.core.internal.CoreText;
import org.eclipse.egit.core.internal.trace.GitTraceLocation;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.SystemReader;

/**
 * Searches for existing Git repositories associated with a project's files.
 * <p>
 * This finder algorithm searches a project's contained files to see if any of
 * them are located within the working directory of an existing Git repository.
 * By default linked resources are ignored and not included in the search.
 * </p>
 * <p>
 * The search algorithm is exhaustive, it will find all matching repositories.
 * For the project itself and possibly for each linked container within the
 * project it scans down the local filesystem trees to locate any Git
 * repositories which may be found there. It also scans up the local filesystem
 * tree to locate any Git repository which may be outside of Eclipse's
 * workspace-view of the world.
 * In short, if there is a Git repository associated, it finds it.
 * </p>
 */
public class RepositoryFinder {
	private final IProject proj;

	private final Collection<RepositoryMapping> results = new ArrayList<RepositoryMapping>();
	private final Set<File> gitdirs = new HashSet<File>();

	private Set<File> ceilingDirectories = new HashSet<File>();

	/**
	 * Create a new finder to locate Git repositories for a project.
	 *
	 * @param p
	 *            the project this new finder should locate the existing Git
	 *            repositories of.
	 */
	public RepositoryFinder(final IProject p) {
		proj = p;
		String ceilingDirectoriesVar = SystemReader.getInstance().getenv(
				Constants.GIT_CEILING_DIRECTORIES_KEY);
		if (ceilingDirectoriesVar != null) {
			for (String path : ceilingDirectoriesVar.split(File.pathSeparator))
				ceilingDirectories.add(new File(path));
		}
	}

	/**
	 * Run the search algorithm, ignoring linked resources.
	 *
	 * @param m
	 *            a progress monitor to report feedback to; may be null.
	 * @return all found {@link RepositoryMapping} instances associated with the
	 *         project supplied to this instance's constructor.
	 * @throws CoreException
	 *             Eclipse was unable to access its workspace, and threw up on
	 *             us. We're throwing it back at the caller.
	 */
	public Collection<RepositoryMapping> find(IProgressMonitor m)
			throws CoreException {
		return find(m, false);
	}

	/**
	 * Run the search algorithm.
	 *
	 * @param m
	 *            a progress monitor to report feedback to; may be null.
	 * @param searchLinkedFolders
	 *            specify if linked folders should be included in the search
	 * @return all found {@link RepositoryMapping} instances associated with the
	 *         project supplied to this instance's constructor.
	 * @throws CoreException
	 *             Eclipse was unable to access its workspace, and threw up on
	 *             us. We're throwing it back at the caller.
	 * @since 2.3
	 */
	public Collection<RepositoryMapping> find(IProgressMonitor m, boolean searchLinkedFolders)
			throws CoreException {
		IProgressMonitor monitor;
		if (m == null)
			monitor = new NullProgressMonitor();
		else
			monitor = m;
		find(monitor, proj, searchLinkedFolders);
		return results;
	}

	private void find(final IProgressMonitor m, final IContainer c, boolean searchLinkedFolders)
				throws CoreException {
		if (!searchLinkedFolders && c.isLinked())
			return; // Ignore linked folders
		final IPath loc = c.getLocation();

		m.beginTask("", 101);  //$NON-NLS-1$
		m.subTask(CoreText.RepositoryFinder_finding);
		try {
			if (loc != null) {
				final File fsLoc = loc.toFile();
				assert fsLoc.isAbsolute();

				if (c instanceof IProject)
					findInDirectoryAndParents(c, fsLoc);
				else
					findInDirectory(c, fsLoc);
				m.worked(1);

				final IResource[] children = c.members();
				if (children != null && children.length > 0) {
					final int scale = 100 / children.length;
					for (int k = 0; k < children.length; k++) {
						final IResource o = children[k];
						if (o instanceof IContainer
								&& !o.getName().equals(Constants.DOT_GIT)) {
							find(new SubProgressMonitor(m, scale),
									(IContainer) o, searchLinkedFolders);
						} else {
							m.worked(scale);
						}
					}
				}
			}
		} finally {
			m.done();
		}
	}

	private void findInDirectoryAndParents(IContainer container, File startPath) {
		File path = startPath;
		while (path != null && !ceilingDirectories.contains(path)) {
			findInDirectory(container, path);
			path = path.getParentFile();
		}
	}

	private void findInDirectory(final IContainer container,
			final File path) {
		if (GitTraceLocation.CORE.isActive())
			GitTraceLocation.getTrace().trace(
					GitTraceLocation.CORE.getLocation(),
					"Looking at candidate dir: " //$NON-NLS-1$
							+ path);

		FileRepositoryBuilder builder = new FileRepositoryBuilder();
		File parent = path.getParentFile();
		if (parent != null)
			builder.addCeilingDirectory(parent);
		builder.findGitDir(path);
		File gitDir = builder.getGitDir();
		if (gitDir != null)
			register(container, gitDir);
	}

	private void register(final IContainer c, final File gitdir) {
		File f = gitdir.getAbsoluteFile();
		if (gitdirs.contains(f))
			return;
		gitdirs.add(f);
		results.add(new RepositoryMapping(c, f));
	}
}
