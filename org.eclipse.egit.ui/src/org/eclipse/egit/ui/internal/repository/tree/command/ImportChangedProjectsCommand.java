/******************************************************************************
 *  Copyright (c) 2014, Tobias Melcher <tobias.melcher@sap.com>
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************/
package org.eclipse.egit.ui.internal.repository.tree.command;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.internal.UIText;
import org.eclipse.egit.ui.internal.history.FileDiff;
import org.eclipse.egit.ui.internal.history.HistoryPageInput;
import org.eclipse.egit.ui.internal.repository.tree.RefNode;
import org.eclipse.egit.ui.internal.repository.tree.RepositoryTreeNode;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revplot.PlotCommit;
import org.eclipse.jgit.revplot.PlotWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.swt.widgets.Display;
import org.eclipse.team.ui.history.IHistoryView;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.internal.ide.IDEWorkbenchPlugin;

/**
 * Imports the changed projects for a given revision.
 *
 * Loops over all changed files of a revision and finds the matching projects
 * and imports them.
 */
public class ImportChangedProjectsCommand extends
		RepositoriesViewCommandHandler<RepositoryTreeNode> {
	public Object execute(ExecutionEvent event) throws ExecutionException {
		List<RepositoryTreeNode> selectedNodes = getSelectedNodes(event);
		if (selectedNodes == null || selectedNodes.isEmpty()) {
			MessageDialog.openError(Display.getDefault().getActiveShell(),
					UIText.ImportProjectsWrongSelection,
					UIText.ImportProjectsSelectionInRepositoryRequired);
			return null;
		}

		for (Object node : selectedNodes) {
			List<File> files = null;
			Repository repo = null;
			if (node instanceof PlotCommit) {
				PlotCommit pt = (PlotCommit) node;
				repo = getRepository(event);
				files = getFilesForPlotCommit(pt, repo);
			} else if (node instanceof RefNode) {
				RefNode rn = (RefNode) node;
				repo = getRepository(rn);
				files = getFilesForRefNode(rn, repo);
			}
			if (repo != null) {
				Set<File> dotProjectFiles = findDotProjectFiles(files, repo);
				importProjects(dotProjectFiles);
			}
		}

		return null;
	}

	private List<File> getFilesForPlotCommit(PlotCommit node, Repository repo) {
		try {
			List<File> files = getFiles(repo, node);
			return files;
		} catch (MissingObjectException e) {
			Activator.error(e.getMessage(), e);
		} catch (IncorrectObjectTypeException e) {
			Activator.error(e.getMessage(), e);
		} catch (CorruptObjectException e) {
			Activator.error(e.getMessage(), e);
		} catch (IOException e) {
			Activator.error(e.getMessage(), e);
		}
		return null;
	}

	private Repository getRepository(Object input) throws ExecutionException {
		if (input == null)
			return null;
		if (input instanceof RefNode) {
			Repository repo = ((RefNode) input).getRepository();
			return repo;
		} else if (input instanceof ExecutionEvent) {
			ExecutionEvent event = (ExecutionEvent) input;
			IWorkbenchPart ap = HandlerUtil.getActivePartChecked(event);
			if (ap instanceof IHistoryView) {
				input = ((IHistoryView) ap).getHistoryPage().getInput();
				return getRepository(input);
			}
		} else if (input instanceof HistoryPageInput) {
			return ((HistoryPageInput) input).getRepository();
		} else if (input instanceof RepositoryTreeNode) {
			RepositoryTreeNode rptn = (RepositoryTreeNode) input;
			Repository repo = rptn.getRepository();
			return repo;
		} else if (input instanceof IResource) {
			RepositoryMapping mapping = RepositoryMapping
					.getMapping((IResource) input);
			if (mapping != null) {
				Repository repo = mapping.getRepository();
				return repo;
			}
		}
		return null;
	}

	private List<File> getFilesForRefNode(RefNode rn, Repository repo) {
		Ref ref = rn.getObject();
		List<File> files = new ArrayList<File>();
		PlotWalk walk = null;
		try {
			walk = new PlotWalk(repo);
			RevCommit unfilteredCommit = walk.parseCommit(ref.getLeaf()
					.getObjectId());
			files = getFiles(repo, unfilteredCommit);
		} catch (MissingObjectException e) {
			Activator.error(e.getMessage(), e);
		} catch (IncorrectObjectTypeException e) {
			Activator.error(e.getMessage(), e);
		} catch (IOException e) {
			Activator.error(e.getMessage(), e);
		} finally {
			if (walk != null)
				walk.release();
		}
		return files;
	}

	private List<File> getFiles(Repository repo,
			final RevCommit unfilteredCommit) throws MissingObjectException,
			IOException, IncorrectObjectTypeException, CorruptObjectException {
		List<File> files = new ArrayList<File>();
		TreeWalk tw = new TreeWalk(repo);
		tw.setRecursive(true);
		final RevWalk walk = new RevWalk(repo);
		try {
			for (RevCommit parent : unfilteredCommit.getParents())
				walk.parseBody(parent);

			FileDiff[] diffs = FileDiff.compute(repo, tw, unfilteredCommit,
					TreeFilter.ALL);
			if (diffs != null && diffs.length > 0) {
				File repoDir = repo.getDirectory();
				String repoPath = repoDir.getParentFile().getAbsolutePath();
				for (FileDiff d : diffs) {
					String path = d.getPath();
					File f = new File(repoPath + File.separator + path);
					files.add(f);
				}
			}
		} finally {
			tw.release();
			walk.release();
		}
		return files;
	}

	private Set<File> findDotProjectFiles(List<File> files, Repository repo) {
		Set<File> result = new HashSet<File>();
		String workingTreeRootPath = repo.getWorkTree().toString();
		for (File changedFile : files) {
			if (changedFile.isFile()) {
				File projectFile = searchProjectFileUpwardsInRepo(
						changedFile.getParentFile(), workingTreeRootPath);
				if (projectFile != null)
					result.add(projectFile);
			}
		}
		return result;
	}

	private File searchProjectFileUpwardsInRepo(File subFolder, String rootPath) {
		File projectFile = null;
		File currentPath = subFolder;

		while (currentPath.toString().startsWith(rootPath)) {
			projectFile = new File(currentPath.toString() + File.separator
					+ ".project"); //$NON-NLS-1$
			if (projectFile.isFile())
				break;
			projectFile = null;
			currentPath = currentPath.getParentFile();
		}
		return projectFile;
	}

	private void importProjects(final Set<File> dotProjectFiles) {
		WorkspaceJob job = new WorkspaceJob(
				UIText.ImportChangedProjectsCommand_ImportingChangedProjects) {

			@Override
			public IStatus runInWorkspace(IProgressMonitor monitor)
					throws CoreException {
				for (File f : dotProjectFiles) {
					if (monitor.isCanceled())
						return Status.CANCEL_STATUS;
					String ap = f.getAbsolutePath();
					importProject(ap);
				}
				return Status.OK_STATUS;
			}
		};
		job.schedule();
	}

	private void importProject(String path) {
		try {
			IProjectDescription description = IDEWorkbenchPlugin
					.getPluginWorkspace().loadProjectDescription(
							new org.eclipse.core.runtime.Path(path));
			if (description != null) {
				String projectName = description.getName();
				IProject project = ResourcesPlugin.getWorkspace().getRoot()
						.getProject(projectName);
				if (project.exists() == true) {
					if (project.isOpen() == false)
						project.open(IResource.BACKGROUND_REFRESH,
								new NullProgressMonitor());
				} else {
					project.create(description, new NullProgressMonitor());
					project.open(IResource.BACKGROUND_REFRESH,
							new NullProgressMonitor());
				}
			}
		} catch (CoreException e) {
			Activator.error(e.getMessage(), e);
		}
	}
}
