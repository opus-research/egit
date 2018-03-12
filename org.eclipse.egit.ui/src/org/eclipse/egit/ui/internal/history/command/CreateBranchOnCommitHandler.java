/*******************************************************************************
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com>
 * Copyright (C) 2013, Tomasz Zarna <tomasz.zarna@tasktop.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.history.command;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.egit.ui.internal.history.GitHistoryPage;
import org.eclipse.egit.ui.internal.repository.CreateBranchWizard;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.IWizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revplot.PlotCommit;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Create a branch based on a commit.
 */
public class CreateBranchOnCommitHandler extends AbstractHistoryCommandHandler {
	public Object execute(ExecutionEvent event) throws ExecutionException {
		GitHistoryPage page = getPage();
		Repository repo = getRepository(event);

		IWizard wiz = null;
		List<Ref> branches = getBranchesExcludingCurrent(page, repo);

		if (branches.isEmpty()) {
			PlotCommit commit = (PlotCommit) getSelection(page)
					.getFirstElement();
			wiz = new CreateBranchWizard(repo, commit.name());
		} else {
			Ref branch = branches.get(0);
			wiz = new CreateBranchWizard(repo, branch.getName());
		}

		WizardDialog dlg = new WizardDialog(
				HandlerUtil.getActiveShellChecked(event), wiz);
		dlg.setHelpAvailable(false);
		dlg.open();
		return null;
	}

	private List<Ref> getBranchesExcludingCurrent(GitHistoryPage page,
			Repository repo) {
		try {
			return getBranchesOfCommit(page, repo, true);
		} catch (IOException e) {
			// ignore, use commit name
			return Collections.<Ref> emptyList();
		}
	}

	@Override
	public boolean isEnabled() {
		GitHistoryPage page = getPage();
		if (page == null)
			return false;
		IStructuredSelection sel = getSelection(page);
		return sel.size() == 1 && sel.getFirstElement() instanceof RevCommit;
	}
}
