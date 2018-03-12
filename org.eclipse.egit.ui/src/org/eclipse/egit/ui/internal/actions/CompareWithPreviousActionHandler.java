/*******************************************************************************
 *  Copyright (c) 2011, 2015 GitHub Inc. and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *    Kevin Sawicki (GitHub Inc.) - initial API and implementation
 *    François Rey - gracefully ignore linked resources
 *    Laurent Goubet <laurent.goubet@obeo.fr>
 *    Stefan Dirix <sdirix@eclipsesource.com>
 *******************************************************************************/
package org.eclipse.egit.ui.internal.actions;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.internal.CompareUtils;
import org.eclipse.egit.ui.internal.UIText;
import org.eclipse.egit.ui.internal.dialogs.CommitSelectDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.StringUtils;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Compare with previous revision action handler.
 */
public class CompareWithPreviousActionHandler extends RepositoryActionHandler {

	/**
	 * @see org.eclipse.core.commands.IHandler#execute(org.eclipse.core.commands.ExecutionEvent)
	 */
	@Override
	public Object execute(final ExecutionEvent event) throws ExecutionException {
		final Repository repository = getRepository(true, event);
		if (repository == null) {
			return null;
		}

		final IResource[] resources = getSelectedResources(event);

		Job job = new Job(UIText.CompareUtils_jobName) {

			@Override
			public IStatus run(IProgressMonitor monitor) {
				if (monitor.isCanceled()) {
					return Status.CANCEL_STATUS;
				}
				try {
					IWorkbenchPage workBenchPage = HandlerUtil
							.getActiveWorkbenchWindowChecked(event)
							.getActivePage();

					final RevCommit previous = getPreviousRevision(event,
							resources);

					if (previous != null) {
						CompareUtils.compare(resources, repository,
								Constants.HEAD, previous.getName(),
								true, workBenchPage);
					} else {
						showNotFoundDialog(event, resources);
					}
				} catch (Exception e) {
					Activator.handleError(
							UIText.CompareWithRefAction_errorOnSynchronize, e,
							true);
				}
				return Status.OK_STATUS;
			}

		};
		job.setUser(true);
		job.schedule();

		return null;
	}

	@Override
	public boolean isEnabled() {
		IResource[] selectedResources = getSelectedResources();
		return super.isEnabled() && selectedResources.length == 1 &&
				selectionMapsToSingleRepository();
	}

	private RevCommit getPreviousRevision(final ExecutionEvent event,
			final IResource[] resources) throws IOException {
		final List<RevCommit> previousList = findPreviousCommits(Arrays
				.asList(resources));

		final AtomicReference<RevCommit> previous = new AtomicReference<RevCommit>();

		if (previousList.size() == 1)
			previous.set(previousList.get(0));
		else {
			final List<RevCommit> commits = new ArrayList<RevCommit>();
			for (RevCommit pc : previousList)
				commits.add(pc);
			HandlerUtil.getActiveShell(event).getDisplay()
					.syncExec(new Runnable() {
						@Override
						public void run() {
							CommitSelectDialog dlg = new CommitSelectDialog(
									HandlerUtil.getActiveShell(event), commits);
							if (dlg.open() == Window.OK)
								for (RevCommit pc : previousList)
									if (pc.equals(dlg
											.getSelectedCommit())) {
										previous.set(pc);
										break;
									}
						}
					});
		}

		return previous.get();
	}

	private void showNotFoundDialog(final ExecutionEvent event,
			IResource[] resources) {

		final List<String> names = new LinkedList<String>();
		for (IResource resource : resources) {
			if (resource.getName() != null) {
				names.add(resource.getName());
			}
		}

		final String resourceNames = StringUtils.join(names, ",", "&"); //$NON-NLS-1$ //$NON-NLS-2$

		final String message = MessageFormat
				.format(UIText.CompareWithPreviousActionHandler_MessageRevisionNotFound,
						resourceNames);
		PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {

			@Override
			public void run() {
				MessageDialog
						.openWarning(
						HandlerUtil.getActiveShell(event),
								UIText.CompareWithPreviousActionHandler_TitleRevisionNotFound,
								message);
			}
		});
	}
}
