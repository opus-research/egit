/*******************************************************************************
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2007, Jing Xue <jingxue@digizenstudio.com>
 * Copyright (C) 2007, Robin Rosenberg <me@lathund.dewire.com>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2010, Stefan Lay <stefan.lay@sap.com>
 * Copyright (C) 2011, Jens Baumgart <jens.baumgart@sap.com>
 * Copyright (C) 2012, Robin Stocker <robin@nibor.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.commit;


import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.egit.core.EclipseGitProgressTransformer;
import org.eclipse.egit.core.IteratorService;
import org.eclipse.egit.core.op.CommitOperation;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.JobFamilies;
import org.eclipse.egit.ui.UIPreferences;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.UIUtils;
import org.eclipse.egit.ui.internal.decorators.GitLightweightDecorator;
import org.eclipse.egit.ui.internal.dialogs.BasicConfigurationDialog;
import org.eclipse.egit.ui.internal.dialogs.CommitDialog;
import org.eclipse.egit.ui.internal.dialogs.CommitMessageComponentStateManager;
import org.eclipse.egit.ui.internal.push.PushOperationUI;
import org.eclipse.egit.ui.internal.push.PushWizard;
import org.eclipse.egit.ui.internal.push.SimpleConfigurePushDialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.IndexDiff;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

/**
 * UI component for performing a commit
 */
public class CommitUI  {

	private IndexDiff indexDiff;

	private Set<String> notIndexed;

	private Set<String> indexChanges;

	private Set<String> notTracked;

	private Set<String> files;

	private boolean amending;

	private Shell shell;

	private Repository repo;

	private IResource[] selectedResources;

	private boolean preselectAll;

	/**
	 * Constructs a CommitUI object
	 * @param shell
	 *            Shell to use for UI interaction. Must not be null.
	 * @param repo
	 *            Repository to commit. Must not be null
	 * @param selectedResources
	 *            Resources selected by the user. A file is preselected in the
	 *            commit dialog if the file is contained in selectedResources or
	 *            if selectedResources contains a resource that is parent of the
	 *            file. selectedResources must not be null.
	 * @param preselectAll
	 * 			  preselect all changed files in the commit dialog.
	 * 			  If set to true selectedResources are ignored.
	 */
	public CommitUI(Shell shell, Repository repo,
			IResource[] selectedResources, boolean preselectAll) {
		this.shell = shell;
		this.repo = repo;
		this.selectedResources = new IResource[selectedResources.length];
		// keep our own copy
		System.arraycopy(selectedResources, 0, this.selectedResources, 0,
				selectedResources.length);
		this.preselectAll = preselectAll;
	}

	/**1
	 * Performs a commit
	 * @return true if a commit operation was triggered
	 */
	public boolean commit() {
		// let's see if there is any dirty editor around and
		// ask the user if they want to save or abort
		if (!UIUtils.saveAllEditors(repo))
			return false;

		BasicConfigurationDialog.show(new Repository[]{repo});

		resetState();
		final IProject[] projects = getProjectsOfRepositories();
		try {
			PlatformUI.getWorkbench().getProgressService().busyCursorWhile(new IRunnableWithProgress() {

				public void run(IProgressMonitor monitor) throws InvocationTargetException,
						InterruptedException {
					try {
						buildIndexHeadDiffList(projects, monitor);
					} catch (IOException e) {
						throw new InvocationTargetException(e);
					}
				}
			});
		} catch (InvocationTargetException e) {
			Activator.handleError(UIText.CommitAction_errorComputingDiffs, e.getCause(),
					true);
			return false;
		} catch (InterruptedException e) {
			return false;
		}

		CommitHelper commitHelper = new CommitHelper(repo);

		if (!commitHelper.canCommit()) {
			MessageDialog.openError(
					shell,
					UIText.CommitAction_cannotCommit,
					commitHelper.getCannotCommitMessage());
			return false;
		}
		boolean amendAllowed = commitHelper.amendAllowed();
		if (files.isEmpty()) {
			if (amendAllowed && commitHelper.getPreviousCommit() != null) {
				boolean result = MessageDialog.openQuestion(shell,
						UIText.CommitAction_noFilesToCommit,
						UIText.CommitAction_amendCommit);
				if (!result)
					return false;
				amending = true;
			} else {
				MessageDialog.openWarning(shell,
						UIText.CommitAction_noFilesToCommit,
						UIText.CommitAction_amendNotPossible);
				return false;
			}
		}

		CommitDialog commitDialog = new CommitDialog(shell);
		commitDialog.setAmending(amending);
		commitDialog.setAmendAllowed(amendAllowed);
		commitDialog.setFiles(repo, files, indexDiff);
		commitDialog.setPreselectedFiles(getSelectedFiles());
		commitDialog.setPreselectAll(preselectAll);
		commitDialog.setAuthor(commitHelper.getAuthor());
		commitDialog.setCommitter(commitHelper.getCommitter());
		commitDialog.setAllowToChangeSelection(!commitHelper.isMergedResolved && !commitHelper.isCherryPickResolved);
		commitDialog.setCommitMessage(commitHelper.getCommitMessage());

		if (commitDialog.open() != IDialogConstants.OK_ID)
			return false;

		final CommitOperation commitOperation;
		try {
			commitOperation= new CommitOperation(
					repo,
					commitDialog.getSelectedFiles(), notTracked, commitDialog.getAuthor(),
					commitDialog.getCommitter(), commitDialog.getCommitMessage());
		} catch (CoreException e1) {
			Activator.handleError(UIText.CommitUI_commitFailed, e1, true);
			return false;
		}
		if (commitDialog.isAmending())
			commitOperation.setAmending(true);
		commitOperation.setComputeChangeId(commitDialog.getCreateChangeId());
		commitOperation.setCommitAll(commitHelper.isMergedResolved);
		if (commitHelper.isMergedResolved)
			commitOperation.setRepository(repo);
		Job commitJob = createCommitJob(repo, commitOperation, false);
		if (commitDialog.isPushRequested())
			pushWhenFinished(commitJob);

		commitJob.schedule();

		return true;
	}

	private void pushWhenFinished(Job commitJob) {
		commitJob.addJobChangeListener(new JobChangeAdapter() {

			@Override
			public void done(IJobChangeEvent event) {
				if (event.getResult().getSeverity() == IStatus.ERROR)
					return;
				RemoteConfig config = SimpleConfigurePushDialog
						.getConfiguredRemote(repo);
				if (config == null) {
					Display.getDefault().syncExec(new Runnable() {

						public void run() {
							try {
								WizardDialog wizardDialog = new WizardDialog(
										shell, new PushWizard(repo));
								wizardDialog.setHelpAvailable(true);
								wizardDialog.open();
							} catch (URISyntaxException e) {
								Activator.handleError(NLS.bind(
										UIText.CommitUI_pushFailedMessage, e),
										e, true);
							}
						}
					});
				} else {
					int timeout = Activator.getDefault().getPreferenceStore()
							.getInt(UIPreferences.REMOTE_CONNECTION_TIMEOUT);
					PushOperationUI op = new PushOperationUI(repo, config
							.getName(), timeout, false);
					op.start();
				}
			}
		});
	}

	/**
	 * Uses a Job to perform the given CommitOperation
	 * @param repository
	 * @param commitOperation
	 * @param openNewCommit
	 */
	public static void performCommit(final Repository repository,
			final CommitOperation commitOperation, final boolean openNewCommit) {
		Job job = createCommitJob(repository, commitOperation, openNewCommit);
		job.schedule();
	}

	private static Job createCommitJob(final Repository repository,
			final CommitOperation commitOperation, final boolean openNewCommit) {
		String jobname = UIText.CommitAction_CommittingChanges;
		Job job = new Job(jobname) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				RevCommit commit = null;
				try {
					commitOperation.execute(monitor);
					commit = commitOperation.getCommit();
					CommitMessageComponentStateManager.deleteState(
							repository);
					RepositoryMapping mapping = RepositoryMapping
							.findRepositoryMapping(repository);
					if (mapping != null)
						mapping.fireRepositoryChanged();
				} catch (CoreException e) {
					if (e.getCause() instanceof JGitInternalException)
						return Activator.createErrorStatus(
								e.getLocalizedMessage(), e.getCause());
					return Activator.createErrorStatus(
							UIText.CommitAction_CommittingFailed, e);
				} finally {
					GitLightweightDecorator.refresh();
				}
				if (openNewCommit && commit != null)
					openCommit(repository, commit);
				return Status.OK_STATUS;
			}

			@Override
			public boolean belongsTo(Object family) {
				if (family.equals(JobFamilies.COMMIT))
					return true;
				return super.belongsTo(family);
			}

		};
		job.setUser(true);
		return job;
	}

	private static void openCommit(final Repository repository,
			final RevCommit newCommit) {
		PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {

			public void run() {
				CommitEditor.openQuiet(new RepositoryCommit(repository,
						newCommit));
			}
		});
	}

	private IProject[] getProjectsOfRepositories() {
		Set<IProject> ret = new HashSet<IProject>();
		final IProject[] projects = ResourcesPlugin.getWorkspace().getRoot()
				.getProjects();
		for (IProject project : projects) {
			RepositoryMapping mapping = RepositoryMapping.getMapping(project);
			if (mapping != null && mapping.getRepository() == repo)
				ret.add(project);
		}
		return ret.toArray(new IProject[ret.size()]);
	}

	private void resetState() {
		files = new LinkedHashSet<String>();
		notIndexed = new LinkedHashSet<String>();
		indexChanges = new LinkedHashSet<String>();
		notTracked = new LinkedHashSet<String>();
		amending = false;
		indexDiff = null;
	}

	/**
	 * Retrieves a collection of files that may be committed based on the user's
	 * selection when they performed the commit action. That is, even if the
	 * user only selected one folder when the action was performed, if the
	 * folder contains any files that could be committed, they will be returned.
	 *
	 * @return a collection of files that is eligible to be committed based on
	 *         the user's selection
	 */
	private Set<String> getSelectedFiles() {
		Set<String> preselectionCandidates = new LinkedHashSet<String>();
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		// iterate through all the files that may be committed
		for (String fileName : files) {
			URI uri = new File(repo.getWorkTree(), fileName).toURI();
			IFile[] workspaceFiles = root.findFilesForLocationURI(uri);
			if (workspaceFiles.length > 0) {
				IFile file = workspaceFiles[0];
				for (IResource resource : selectedResources) {
					// if any selected resource contains the file, add it as a
					// preselection candidate
					if (resource.contains(file)) {
						preselectionCandidates.add(fileName);
						break;
					}
				}
			} else {
				// could be file outside of workspace
				for (IResource resource : selectedResources) {
					if(resource.getFullPath().toFile().equals(new File(uri))) {
						preselectionCandidates.add(fileName);
					}
				}
			}
		}
		return preselectionCandidates;
	}

	private void buildIndexHeadDiffList(IProject[] selectedProjects,
			IProgressMonitor monitor) throws IOException,
			OperationCanceledException {

		monitor.beginTask(UIText.CommitActionHandler_calculatingChanges, 1000);
		EclipseGitProgressTransformer jgitMonitor = new EclipseGitProgressTransformer(
				monitor);
		CountingVisitor counter = new CountingVisitor();
		for (IProject p : selectedProjects) {
			try {
				p.accept(counter);
			} catch (CoreException e) {
				// ignore
			}
		}
		indexDiff = new IndexDiff(repo, Constants.HEAD,
				IteratorService.createInitialIterator(repo));
		indexDiff.diff(jgitMonitor, counter.count, 0, NLS.bind(
				UIText.CommitActionHandler_repository, repo.getDirectory()
						.getPath()));

		includeList(indexDiff.getAdded(), indexChanges);
		includeList(indexDiff.getChanged(), indexChanges);
		includeList(indexDiff.getRemoved(), indexChanges);
		includeList(indexDiff.getMissing(), notIndexed);
		includeList(indexDiff.getModified(), notIndexed);
		includeList(indexDiff.getUntracked(), notTracked);
		if (monitor.isCanceled())
			throw new OperationCanceledException();
		monitor.done();
	}

	static class CountingVisitor implements IResourceVisitor {
		int count;
		public boolean visit(IResource resource) throws CoreException {
			count++;
			return true;
		}
	}

	private void includeList(Set<String> added, Set<String> category) {
		for (String filename : added) {
			if (!files.contains(filename))
				files.add(filename);
			category.add(filename);
		}
	}

}
