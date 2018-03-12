/*******************************************************************************
 * Copyright (C) 2015, Max Hohenegger <eclipse@hohenegger.eu>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.gitflow.ui.internal.actions;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.egit.core.internal.job.JobUtil;
import org.eclipse.egit.gitflow.GitFlowRepository;
import org.eclipse.egit.gitflow.op.InitOperation;
import org.eclipse.egit.gitflow.ui.internal.JobFamilies;
import org.eclipse.egit.gitflow.ui.internal.UIText;
import org.eclipse.egit.gitflow.ui.internal.dialogs.InitDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * git flow feature init
 */
public class InitHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		final GitFlowRepository gfRepo = GitFlowHandlerUtil.getRepository(event);
		if (gfRepo == null) {
			return null;
		}

		List<Ref> branchList;
		try {
			branchList = Git.wrap(gfRepo.getRepository()).branchList().call();
		} catch (GitAPIException e) {
			throw new ExecutionException(e.getMessage());
		}

		Shell activeShell = HandlerUtil.getActiveShell(event);
		InitDialog dialog = new InitDialog(activeShell, gfRepo, branchList);
		if (dialog.open() != Window.OK) {
			return null;
		}

		InitOperation initOperation = new InitOperation(gfRepo.getRepository(),
				dialog.getResult());
		JobUtil.scheduleUserWorkspaceJob(initOperation,
				UIText.InitHandler_initializing, JobFamilies.GITFLOW_FAMILY);

		return null;
	}
}
