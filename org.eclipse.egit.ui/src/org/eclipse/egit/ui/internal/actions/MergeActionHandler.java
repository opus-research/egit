/*******************************************************************************
 * Copyright (c) 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Stefan Lay (SAP AG) - initial implementation
 *******************************************************************************/

package org.eclipse.egit.ui.internal.actions;

import java.io.IOException;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.egit.core.op.MergeOperation;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.internal.dialogs.MergeTargetSelectionDialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Display;

/**
 * Action for selecting a commit and merging it with the current branch.
 */
public class MergeActionHandler extends RepositoryActionHandler {

	public Object execute(final ExecutionEvent event) throws ExecutionException {
		final Repository repository = getRepository(true, event);
		if (repository == null)
			return null;

		if (!canMerge(repository, event))
			return null;

		MergeTargetSelectionDialog mergeTargetSelectionDialog = new MergeTargetSelectionDialog(
				getShell(event), repository);
		if (mergeTargetSelectionDialog.open() == IDialogConstants.OK_ID) {

			final String refName = mergeTargetSelectionDialog.getRefName();

			String jobname = NLS.bind(UIText.MergeAction_JobNameMerge, refName);
			final MergeOperation op = new MergeOperation(repository, refName);
			Job job = new Job(jobname) {
				@Override
				protected IStatus run(IProgressMonitor monitor) {
					try {
						op.execute(monitor);
					} catch (final CoreException e) {
						return e.getStatus();
					}
					return Status.OK_STATUS;
				}
			};
			job.setUser(true);
			job.addJobChangeListener(new JobChangeAdapter() {
				@Override
				public void done(IJobChangeEvent cevent) {
					IStatus result = cevent.getJob().getResult();
					if (result.getSeverity() == IStatus.CANCEL) {
						Display.getDefault().asyncExec(new Runnable() {
							public void run() {
								try {
									MessageDialog
											.openInformation(
													getShell(event),
													UIText.MergeAction_MergeCanceledTitle,
													UIText.MergeAction_MergeCanceledMessage);
								} catch (ExecutionException e) {
									Activator
											.handleError(
													UIText.MergeAction_MergeCanceledMessage,
													null, true);
								}
							}
						});
					} else if (!result.isOK()) {
						Activator.handleError(result.getMessage(), result
								.getException(), true);
					} else {
						Display.getDefault().asyncExec(new Runnable() {
							public void run() {
								try {
									MessageDialog
											.openInformation(
													getShell(event),
													UIText.MergeAction_MergeResultTitle,
													op.getResult().toString());
								} catch (ExecutionException e) {
									Activator.handleError(op.getResult()
											.toString(), null, true);
								}
							}
						});
					}
				}
			});
			job.schedule();
		}
		return null;
	}

	private boolean canMerge(final Repository repository, ExecutionEvent event)
			throws ExecutionException {
		String message = null;
		try {
			Ref head = repository.getRef(Constants.HEAD);
			if (head == null || !head.isSymbolic())
				message = UIText.MergeAction_HeadIsNoBranch;
			else if (!repository.getRepositoryState().equals(
					RepositoryState.SAFE))
				message = NLS.bind(UIText.MergeAction_WrongRepositoryState,
						repository.getRepositoryState());
		} catch (IOException e) {
			Activator.logError(e.getMessage(), e);
			message = e.getMessage();
		}

		if (message != null) {
			MessageDialog.openError(getShell(event),
					UIText.MergeAction_CannotMerge, message);
		}
		return (message == null);
	}

	@Override
	public boolean isEnabled() {
		try {
			return getRepository(false, null) != null;
		} catch (ExecutionException e) {
			Activator.handleError(e.getMessage(), e, false);
			return false;
		}
	}

}
