/******************************************************************************
 *  Copyright (c) 2012, Matthias Sohn <matthias.sohn@sap.com>
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *****************************************************************************/
package org.eclipse.egit.ui.internal.repository.tree.command;

import java.io.File;
import java.text.MessageFormat;
import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.core.op.GarbageCollectOperation;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.internal.repository.tree.RepositoryNode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.ui.IWorkbenchSite;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.progress.IWorkbenchSiteProgressService;

/**
 * Command to run jgit garbage collector
 */
public class GarbageCollectCommand extends
		RepositoriesViewCommandHandler<RepositoryNode> {

	/**
	 * Command id
	 */
	public static final String ID = "org.eclipse.egit.ui.team.GarbageCollect"; //$NON-NLS-1$

	/**
	 * Execute garbage collection
	 */
	public Object execute(ExecutionEvent event) throws ExecutionException {
		// get selected nodes
		final List<RepositoryNode> selectedNodes;
		try {
			selectedNodes = getSelectedNodes(event);
			if (selectedNodes.isEmpty())
				return null;
		} catch (ExecutionException e) {
			Activator.handleError(e.getMessage(), e, true);
			return null;
		}

		Job job = new Job("Collecting Garbage...") { //$NON-NLS-1$

			@Override
			protected IStatus run(IProgressMonitor monitor) {

				for (RepositoryNode node : selectedNodes) {
					Repository repo = node.getRepository();
					String name = MessageFormat.format(
							UIText.GarbageCollectCommand_jobTitle,
							getRepositoryName(repo));
					this.setName(name);
					final GarbageCollectOperation op = new GarbageCollectOperation(
							repo);
					try {
						op.execute(monitor);
					} catch (CoreException e) {
						Activator.logError(MessageFormat.format(
								UIText.GarbageCollectCommand_failed, repo), e);
					}
				}

				return Status.OK_STATUS;
			}
		};
		IWorkbenchSite activeSite = HandlerUtil.getActiveSite(event);
		IWorkbenchSiteProgressService service = (IWorkbenchSiteProgressService) activeSite
				.getService(IWorkbenchSiteProgressService.class);
		service.schedule(job);

		return null;
	}

	private String getRepositoryName(Repository repository) {
		File directory;
		if (!repository.isBare())
			directory = repository.getDirectory().getParentFile();
		else
			directory = repository.getDirectory();
		StringBuilder sb = new StringBuilder();
		sb.append(directory.getName());
		sb.append(" - "); //$NON-NLS-1$
		sb.append(directory.getAbsolutePath());
		return sb.toString();
	}

}
