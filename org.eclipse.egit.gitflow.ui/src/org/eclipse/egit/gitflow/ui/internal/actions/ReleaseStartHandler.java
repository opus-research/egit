/*******************************************************************************
 * Copyright (C) 2015, Max Hohenegger <eclipse@hohenegger.eu>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.gitflow.ui.internal.actions;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.egit.core.internal.job.JobUtil;
import org.eclipse.egit.gitflow.GitFlowRepository;
import org.eclipse.egit.gitflow.WrongGitFlowStateException;
import org.eclipse.egit.gitflow.op.ReleaseStartOperation;
import org.eclipse.egit.gitflow.ui.internal.JobFamilies;
import org.eclipse.egit.gitflow.ui.internal.UIText;
import org.eclipse.egit.gitflow.ui.internal.validation.ReleaseNameValidator;
import org.eclipse.egit.ui.internal.selection.SelectionUtils;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.jgit.revplot.PlotCommit;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * git flow release start
 */
public class ReleaseStartHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		final GitFlowRepository gfRepo = GitFlowHandlerUtil.getRepository(event);
		final String startCommitSha1 = getStartCommit(event);

		Shell activeShell = HandlerUtil.getActiveShell(event);

		doExecute(gfRepo, startCommitSha1, activeShell);

		return null;
	}

	void doExecute(GitFlowRepository gfRepo,
			final String startCommitSha1, Shell activeShell) {
		InputDialog inputDialog = new InputDialog(
				activeShell,
				UIText.ReleaseStartHandler_provideReleaseName,
				UIText.ReleaseStartHandler_provideANameForTheNewRelease, "", //$NON-NLS-1$
				new ReleaseNameValidator(gfRepo));

		if (inputDialog.open() != Window.OK) {
			return;
		}

		final String releaseName = inputDialog.getValue();

		ReleaseStartOperation releaseStartOperation = new ReleaseStartOperation(
				gfRepo, startCommitSha1, releaseName);
		JobUtil.scheduleUserWorkspaceJob(releaseStartOperation,
				UIText.ReleaseStartHandler_startingNewRelease,
				JobFamilies.GITFLOW_FAMILY);
	}

	private String getStartCommit(ExecutionEvent event)
			throws ExecutionException {
		ISelection currentSelection = HandlerUtil.getCurrentSelection(event);
		IStructuredSelection selection = SelectionUtils.getStructuredSelection(currentSelection);
		if (selection.getFirstElement() instanceof PlotCommit) {
			RevCommit plotCommit = (RevCommit) selection.getFirstElement();
			return plotCommit.getName();
		} else {
			GitFlowRepository gitFlowRepository = GitFlowHandlerUtil.getRepository(event);
			if (gitFlowRepository == null) {
				throw new ExecutionException(UIText.ReleaseStartHandler_startCommitCouldNotBeDetermined);
			}
			RevCommit head;
			try {
				head = gitFlowRepository.findHead();
			} catch (WrongGitFlowStateException e) {
				throw new ExecutionException(e.getMessage(), e);
			}
			return head.getName();
		}
	}
}
