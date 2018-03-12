/*******************************************************************************
 * Copyright (C) 2015, Max Hohenegger <eclipse@hohenegger.eu>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.gitflow.ui.internal.actions;

import static org.eclipse.egit.gitflow.ui.Activator.error;

import java.io.IOException;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.core.internal.job.JobUtil;
import org.eclipse.egit.gitflow.GitFlowRepository;
import org.eclipse.egit.gitflow.WrongGitFlowStateException;
import org.eclipse.egit.gitflow.op.HotfixFinishOperation;
import org.eclipse.egit.gitflow.ui.internal.JobFamilies;
import org.eclipse.egit.gitflow.ui.internal.UIText;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.api.MergeResult.MergeStatus;
import org.eclipse.osgi.util.NLS;

/**
 * git flow hotfix finish
 */
public class HotfixFinishHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		final GitFlowRepository gfRepo = GitFlowHandlerUtil.getRepository(event);

		HotfixFinishOperation hotfixFinishOperation;
		try {
			hotfixFinishOperation = new HotfixFinishOperation(gfRepo);
			JobUtil.scheduleUserWorkspaceJob(hotfixFinishOperation,
					UIText.HotfixFinishHandler_finishingHotfix,
					JobFamilies.GITFLOW_FAMILY);
			IJobManager jobMan = Job.getJobManager();
			jobMan.join(JobFamilies.GITFLOW_FAMILY, null);

			MergeStatus mergeResult = hotfixFinishOperation.getMergeResult();
			if (!MergeStatus.CONFLICTING.equals(mergeResult)) {
				return null;
			}
			if (handleConflictsOnMaster(gfRepo)) {
				return null;
			}
			handleConflictsOnDevelop();
		} catch (WrongGitFlowStateException | CoreException | IOException
				| OperationCanceledException | InterruptedException e) {
			return error(e.getMessage(), e);
		}

		return null;
	}

	private void handleConflictsOnDevelop() {
		MessageDialog.openWarning(null, UIText.HotfixFinishHandler_Conflicts,
				UIText.HotfixFinishHandler_hotfixFinishConflicts);
	}

	private boolean handleConflictsOnMaster(GitFlowRepository gfRepo) throws IOException {
		String master = gfRepo.getConfig().getMaster();
		if (gfRepo.getRepository().getBranch().equals(master)) {
			MessageDialog.openError(null, UIText.HotfixFinishHandler_Conflicts,
					NLS.bind(UIText.HotfixFinishOperation_unexpectedConflictsHotfixAborted,
							master));
			return true;
		}
		return false;
	}
}
