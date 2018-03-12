/*******************************************************************************
 * Copyright (c) 2014, Konrad Kügler <swamblumat-eclipsebugs@yahoo.de> and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.commit.command;

import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.egit.ui.internal.commit.RepositoryCommit;
import org.eclipse.team.ui.history.IHistoryView;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

/**
 * Shows the selected commit in the history view
 */
public class ShowInHistoryHandler extends CommitCommandHandler {

	/**
	 * Command id
	 */
	public static final String ID = "org.eclipse.egit.ui.commit.ShowInHistory"; //$NON-NLS-1$

	@Override
	public Object execute(final ExecutionEvent event) throws ExecutionException {
		List<RepositoryCommit> commits = getCommits(event);
		if (commits.size() == 1) {
			RepositoryCommit repoCommit = commits.get(0);

			try {
				IHistoryView view = (IHistoryView) PlatformUI.getWorkbench()
						.getActiveWorkbenchWindow().getActivePage()
						.showView(IHistoryView.VIEW_ID);
				view.showHistoryFor(repoCommit);
			} catch (PartInitException e) {
				throw new ExecutionException(e.getMessage(), e);
			}
		}
		return null;
	}
}
