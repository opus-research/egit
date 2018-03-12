/******************************************************************************
 *  Copyright (C) 2012, 2013 GitHub Inc. and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *    Kevin Sawicki (GitHub Inc.) - initial API and implementation
 *****************************************************************************/
package org.eclipse.egit.ui.internal.repository.tree.command;

import java.text.MessageFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.core.op.StashDropOperation;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.JobFamilies;
import org.eclipse.egit.ui.internal.UIText;
import org.eclipse.egit.ui.internal.repository.tree.StashedCommitNode;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.swt.widgets.Shell;

/**
 * Command to drop one or all stashed commits
 */
public class StashDropCommand extends
		RepositoriesViewCommandHandler<StashedCommitNode> {

	public Object execute(ExecutionEvent event) throws ExecutionException {
		final List<StashedCommitNode> nodes = getSelectedNodes(event);
		if (nodes.isEmpty())
			return null;

		final Repository repo = nodes.get(0).getRepository();
		if (repo == null)
			return null;

		// Confirm deletion of selected nodes
		final AtomicBoolean confirmed = new AtomicBoolean();
		final Shell shell = getActiveShell(event);
		shell.getDisplay().syncExec(new Runnable() {

			public void run() {
				String message;
				if (nodes.size() > 1)
					message = MessageFormat.format(
							UIText.StashDropCommand_confirmMultiple,
							Integer.toString(nodes.size()));
				else
					message = MessageFormat.format(
							UIText.StashDropCommand_confirmSingle,
							Integer.toString(nodes.get(0).getIndex()));

				confirmed.set(MessageDialog.openConfirm(shell,
						UIText.StashDropCommand_confirmTitle, message));
			}
		});
		if (!confirmed.get())
			return null;

		Job job = new Job(UIText.StashDropCommand_jobTitle) {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				monitor.beginTask(UIText.StashDropCommand_jobTitle,
						nodes.size());

				// Sort by highest to lowest stash commit index.
				// This avoids shifting problems that cause the indices of the
				// selected nodes not match the indices in the repository
				Collections.sort(nodes, new Comparator<StashedCommitNode>() {

					public int compare(StashedCommitNode n1,
							StashedCommitNode n2) {
						return n1.getIndex() < n2.getIndex() ? 1 : -1;
					}
				});

				for (StashedCommitNode node : nodes) {
					final int index = node.getIndex();
					if (index < 0)
						return null;
					final RevCommit commit = node.getObject();
					if (commit == null)
						return null;
					final String stashName = node.getObject().getName();
					final StashDropOperation op = new StashDropOperation(repo,
							node.getIndex());
					monitor.subTask(stashName);
					try {
						op.execute(monitor);
					} catch (CoreException e) {
						Activator.logError(MessageFormat.format(
								UIText.StashDropCommand_dropFailed,
								node.getObject().name()), e);
					}
					monitor.worked(1);
				}
				monitor.done();
				return Status.OK_STATUS;
			}

			@Override
			public boolean belongsTo(Object family) {
				if (family.equals(JobFamilies.STASH))
					return true;
				return super.belongsTo(family);
			}
		};
		job.setUser(true);
		job.setRule((new StashDropOperation(repo, nodes.get(0).getIndex()))
				.getSchedulingRule());
		job.schedule();
		return null;
	}
}
