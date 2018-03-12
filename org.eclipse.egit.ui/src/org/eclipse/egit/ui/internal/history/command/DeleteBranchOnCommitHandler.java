/*******************************************************************************
 * Copyright (C) 2011, Mathias Kinzler <mathias.kinzler@sap.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.history.command;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.egit.core.op.DeleteBranchOperation;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.internal.dialogs.BranchSelectionDialog;
import org.eclipse.egit.ui.internal.dialogs.UnmergedBranchDialog;
import org.eclipse.egit.ui.internal.history.GitHistoryPage;
import org.eclipse.egit.ui.internal.history.HistoryPageInput;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revplot.PlotCommit;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;

/**
 * Delete a branch pointing to a commit.
 */
public class DeleteBranchOnCommitHandler extends AbstractHistoryCommandHandler {
	public Object execute(ExecutionEvent event) throws ExecutionException {
		GitHistoryPage page = getPage();

		final Repository repository = getRepository(page);
		if (repository == null)
			return null;

		int totalBranchCount;
		List<Ref> branchesOfCommit;
		try {
			totalBranchCount = getBranchesOfCommit(page, repository, false).size();
			branchesOfCommit = getBranchesOfCommit(page, repository, true);
		} catch (IOException e) {
			throw new ExecutionException("Could not obtain current Branch", e); //$NON-NLS-1$
		}
		// this should have been checked by isEnabled()
		if (branchesOfCommit.isEmpty())
			return null;

		final List<Ref> unmergedBranches = new ArrayList<Ref>();
		final Shell shell = getPart(event).getSite().getShell();

		final List<Ref> branchesToDelete;
		// we will show the dialog if there are either multiple branches that might be
		// deleted or if one of the branches is the current head (which we can't delete);
		// in the latter case, we may show the dialog even if there is only one branch to
		// delete instead of quietly deleting an unexpected one, for example a remote
		// tracking branch
		if (totalBranchCount > 1) {
			BranchSelectionDialog<Ref> dlg = new BranchSelectionDialog<Ref>(
					shell,
					branchesOfCommit,
					UIText.DeleteBranchOnCommitHandler_SelectBranchDialogTitle,
					UIText.DeleteBranchOnCommitHandler_SelectBranchDialogMessage,
					SWT.MULTI);
			if (dlg.open() != Window.OK)
				return null;
			branchesToDelete = dlg.getSelectedNodes();
		} else
			branchesToDelete = branchesOfCommit;

		try {
			new ProgressMonitorDialog(shell).run(false, false,
					new IRunnableWithProgress() {
						public void run(IProgressMonitor monitor)
								throws InvocationTargetException,
								InterruptedException {
							try {
								monitor.beginTask(
										UIText.DeleteBranchCommand_DeletingBranchesProgress,
										branchesToDelete.size());
								for (Ref refNode : branchesToDelete) {
									int result = deleteBranch(repository,
											refNode, false);
									if (result == DeleteBranchOperation.REJECTED_CURRENT) {
										throw new CoreException(
												Activator
														.createErrorStatus(
																UIText.DeleteBranchCommand_CannotDeleteCheckedOutBranch,
																null));
									} else if (result == DeleteBranchOperation.REJECTED_UNMERGED) {
										unmergedBranches.add(refNode);
									} else
										monitor.worked(1);
								}
								if (!unmergedBranches.isEmpty()) {
									MessageDialog messageDialog = new UnmergedBranchDialog<Ref>(
											shell, unmergedBranches);
									if (messageDialog.open() == Window.OK) {
										for (Ref node : unmergedBranches) {
											deleteBranch(repository, node, true);
											monitor.worked(1);
										}
									}
								}
							} catch (CoreException ex) {
								throw new InvocationTargetException(ex);
							} finally {
								monitor.done();
							}
						}
					});
		} catch (InvocationTargetException e1) {
			Activator.handleError(
					UIText.RepositoriesView_BranchDeletionFailureMessage,
					e1.getCause(), true);
		} catch (InterruptedException e1) {
			// ignore
		}

		return null;
	}

	private List<Ref> getBranchesOfCommit(GitHistoryPage page,
			final Repository repo, boolean hideCurrentBranch) throws IOException {
		final List<Ref> branchesOfCommit = new ArrayList<Ref>();
		IStructuredSelection selection = getSelection(page);
		if (selection.isEmpty())
			return branchesOfCommit;
		PlotCommit commit = (PlotCommit) selection.getFirstElement();
		String head = repo.getFullBranch();

		int refCount = commit.getRefCount();
		for (int i = 0; i < refCount; i++) {
			Ref ref = commit.getRef(i);
			String refName = ref.getName();
			if (hideCurrentBranch && head != null && refName.equals(head))
				continue;
			if (refName.startsWith(Constants.R_HEADS)
					|| refName.startsWith(Constants.R_REMOTES))
				branchesOfCommit.add(ref);
		}
		return branchesOfCommit;
	}

	private Repository getRepository(GitHistoryPage page) {
		if (page == null)
			return null;
		HistoryPageInput input = page.getInputInternal();
		if (input == null)
			return null;

		final Repository repository = input.getRepository();
		return repository;
	}

	private int deleteBranch(Repository repo, final Ref ref, boolean force)
			throws CoreException {
		DeleteBranchOperation dbop = new DeleteBranchOperation(repo, ref, force);
		dbop.execute(null);
		return dbop.getStatus();
	}

	@Override
	public boolean isEnabled() {
		GitHistoryPage page = getPage();

		Repository repository = getRepository(page);
		if (repository == null)
			return false;

		List<Ref> branchesOfCommit;
		try {
			branchesOfCommit = getBranchesOfCommit(page, repository, true);
		} catch (IOException e) {
			Activator.logError("Could not calculate Enablement", e); //$NON-NLS-1$
			return false;
		}
		return !branchesOfCommit.isEmpty();
	}
}
