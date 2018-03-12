﻿/*******************************************************************************
 * Copyright (C) 2008, Roger C. Soares <rogersoares@intelinet.com.br>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com>
 * Copyright (C) 2010, Benjamin Muskalla <bmuskalla@eclipsesource.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.clone;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Collections;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.core.RepositoryUtil;
import org.eclipse.egit.core.op.CloneOperation;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIIcons;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.internal.components.RepositorySelectionPage;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.IWizardContainer;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Display;

/**
 * Import Git Repository Wizard. A front end to a git clone operation.
 */
public class GitCloneWizard extends Wizard {

	/**
	 * Job family of the Clone Repository job.
	 */
	public static final Object CLONE_JOB_FAMILY = new Object();

	private RepositorySelectionPage cloneSource;

	private SourceBranchPage validSource;

	private CloneDestinationPage cloneDestination;

	private String alreadyClonedInto;

	private IWizardContainer parentContainer;

	/**
	 * The default constructor
	 */
	public GitCloneWizard() {
		setWindowTitle(UIText.GitCloneWizard_title);
		setDefaultPageImageDescriptor(UIIcons.WIZBAN_IMPORT_REPO);
		setNeedsProgressMonitor(true);
		cloneSource = new RepositorySelectionPage(true, null);
		validSource = new SourceBranchPage() {

			@Override
			public void setVisible(boolean visible) {
				if (visible)
					setSelection(cloneSource.getSelection());
				super.setVisible(visible);
			}

		};
		cloneDestination = new CloneDestinationPage() {
			@Override
			public void setVisible(boolean visible) {
				if (visible)
					setSelection(cloneSource.getSelection(), validSource
							.getAvailableBranches(), validSource
							.getSelectedBranches(), validSource.getHEAD());
				super.setVisible(visible);
			}
		};
	}

	/**
	 * Sets the parent {@link IWizardContainer} to run the clone job.
	 *
	 * @param parentContainer
	 */
	public void setParentContainer(IWizardContainer parentContainer) {
		this.parentContainer = parentContainer;
	}

	@Override
	public boolean performCancel() {
		if (alreadyClonedInto != null) {
			File test = new File(alreadyClonedInto);
			if (test.exists()
					&& MessageDialog.openQuestion(getShell(),
							UIText.GitCloneWizard_abortingCloneTitle,
							UIText.GitCloneWizard_abortingCloneMsg)) {
				deleteRecursively(new File(alreadyClonedInto));
			}
		}
		return true;
	}

	private void deleteRecursively(File f) {
		File[] children = f.listFiles();
		if (children != null)
			for (File i : children) {
				if (i.isDirectory()) {
					deleteRecursively(i);
				} else {
					if (!i.delete()) {
						i.deleteOnExit();
					}
				}
			}
		if (!f.delete())
			f.deleteOnExit();
	}

	@Override
	public void addPages() {
		addPage(cloneSource);
		addPage(validSource);
		addPage(cloneDestination);
	}

	@Override
	public boolean canFinish() {
		return cloneDestination.isPageComplete();
	}

	@Override
	public boolean performFinish() {
		try {
			return performClone();
		} finally {
			setWindowTitle(UIText.GitCloneWizard_title);
		}
	}

	boolean performClone() {
		final URIish uri = cloneSource.getSelection().getURI();
		setWindowTitle(NLS.bind(UIText.GitCloneWizard_jobName, uri.toString()));
		final boolean allSelected;
		final Collection<Ref> selectedBranches;
		if (validSource.isSourceRepoEmpty()) {
			// fetch all branches of empty repo
			allSelected = true;
			selectedBranches = Collections.emptyList();
		} else {
			allSelected = validSource.isAllSelected();
			selectedBranches = validSource.getSelectedBranches();
		}
		final File workdir = cloneDestination.getDestinationFile();
		final String branch = cloneDestination.getInitialBranch();
		final String remoteName = cloneDestination.getRemote();

		workdir.mkdirs();

		if (!workdir.isDirectory()) {
			final String errorMessage = NLS.bind(
					UIText.GitCloneWizard_errorCannotCreate, workdir.getPath());
			ErrorDialog.openError(getShell(), getWindowTitle(),
					UIText.GitCloneWizard_failed, new Status(IStatus.ERROR,
							Activator.getPluginId(), 0, errorMessage, null));
			// let's give user a chance to fix this minor problem
			return false;
		}

		final CloneOperation op = new CloneOperation(uri, allSelected,
				selectedBranches, workdir, branch, remoteName);
		alreadyClonedInto = workdir.getPath();

		cloneSource.saveUriInPrefs();
		if (parentContainer == null) {
			runAsJob(uri, op);
		} else {
			runInParentContainer(op);
		}
		return true;
	}

	private void runInParentContainer(final CloneOperation op) {
		Runnable runInParentContainer = new Runnable() {
			public void run() {
				try {
					parentContainer.run(true, true,
							new IRunnableWithProgress() {
								public void run(IProgressMonitor monitor)
										throws InvocationTargetException,
										InterruptedException {
									executeCloneOperation(op, monitor);
								}
							});
				} catch (InvocationTargetException e) {
					Activator.handleError(UIText.GitCloneWizard_failed,
							e.getCause(), true);
				} catch (InterruptedException e) {
					// nothing to do
				}
			}
		};
		// we need to run this async in order to cleanly close the inner wizard
		Display.getCurrent().asyncExec(runInParentContainer);
	}

	private void runAsJob(final URIish uri, final CloneOperation op) {
		final Job job = new Job(NLS.bind(UIText.GitCloneWizard_jobName,
				uri.toString())) {
			@Override
			protected IStatus run(final IProgressMonitor monitor) {
				try {
					return executeCloneOperation(op, monitor);
				} catch (InterruptedException e) {
					return Status.CANCEL_STATUS;
				} catch (InvocationTargetException e) {
					Throwable thr = e.getCause();
					return new Status(IStatus.ERROR, Activator.getPluginId(),
							0, thr.getMessage(), thr);
				}
			}

			@Override
			public boolean belongsTo(Object family) {
				return CLONE_JOB_FAMILY.equals(family);
			}
		};
		job.setUser(true);
		job.schedule();
	}

	private IStatus executeCloneOperation(final CloneOperation op,
			final IProgressMonitor monitor) throws InvocationTargetException,
			InterruptedException {

		final RepositoryUtil util = Activator.getDefault()
				.getRepositoryUtil();

		op.run(monitor);
		util.addConfiguredRepository(op.getGitDir());
		return Status.OK_STATUS;
	}
}
