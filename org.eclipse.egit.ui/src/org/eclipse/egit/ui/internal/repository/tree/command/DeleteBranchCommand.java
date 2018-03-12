/*******************************************************************************
 * Copyright (c) 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Mathias Kinzler (SAP AG) - initial implementation
 *******************************************************************************/
package org.eclipse.egit.ui.internal.repository.tree.command;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.internal.repository.tree.RefNode;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.osgi.util.NLS;

/**
 * Deletes a branch.
 * <p>
 * TODO This uses the force option always, so a warning pop-up is shown to the
 * user; instead this should check if deletion can be performed without data
 * loss and in this case the deletion should be done quietly; the warning pop-up
 * should only be shown if the force option is really needed.
 */
public class DeleteBranchCommand extends
		RepositoriesViewCommandHandler<RefNode> {
	public Object execute(final ExecutionEvent event) throws ExecutionException {
		final RefNode node = getSelectedNodes(event).get(0);
		final Ref ref = node.getObject();

		if (!MessageDialog.openConfirm(getView(event).getSite().getShell(),
				UIText.RepositoriesView_ConfirmDeleteTitle, NLS.bind(
						UIText.RepositoriesView_ConfirmBranchDeletionMessage,
						ref.getName())))
			return null;

		try {
			new ProgressMonitorDialog(getView(event).getSite().getShell()).run(
					false, false, new IRunnableWithProgress() {
						public void run(IProgressMonitor monitor)
								throws InvocationTargetException,
								InterruptedException {
							try {
								RefUpdate op = node.getRepository().updateRef(
										ref.getName());
								op.setRefLogMessage("branch deleted", //$NON-NLS-1$
										false);
								// we set the force update in order
								// to avoid having this rejected
								// due to minor issues
								op.setForceUpdate(true);
								op.delete();
							} catch (IOException ioe) {
								throw new InvocationTargetException(ioe);
							}
						}
					});
		} catch (InvocationTargetException e1) {
			Activator.handleError(
					UIText.RepositoriesView_BranchDeletionFailureMessage, e1
							.getCause(), true);
			e1.printStackTrace();
		} catch (InterruptedException e1) {
			// ignore
		}

		return null;
	}
}
