/*******************************************************************************
 * Copyright (c) 2010 EclipseSource.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Benjamin Muskalla (EclipseSource) - initial implementation
 *******************************************************************************/
package org.eclipse.egit.ui.internal.repository.tree.command;

import java.io.IOException;
import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.internal.ValidationUtils;
import org.eclipse.egit.ui.internal.repository.tree.RefNode;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Shell;

/**
 * Renames a branch
 */
public class RenameBranchCommand extends
		RepositoriesViewCommandHandler<RefNode> {

	public Object execute(ExecutionEvent event) throws ExecutionException {
		final List<RefNode> nodes = getSelectedNodes(event);
		RefNode refNode = nodes.get(0);

		Shell shell = getShell(event);
		Repository db = refNode.getRepository();
		IInputValidator inputValidator = ValidationUtils
				.getRefNameInputValidator(db, Constants.R_HEADS);
		String oldName = refNode.getObject().getName();
		String defaultValue = db.shortenRefName(oldName);
		InputDialog newNameDialog = new InputDialog(shell,
				UIText.RepositoriesView_RenameBranchTitle, NLS.bind(
						UIText.RepositoriesView_RenameBranchMessage,
						defaultValue), defaultValue, inputValidator);
		if (newNameDialog.open() == Window.OK) {
			RefRename r;
			try {
				String newName = Constants.R_HEADS + newNameDialog.getValue();
				r = db.renameRef(oldName, newName);
				if (r.rename() != Result.RENAMED)
					MessageDialog.openError(shell,
							UIText.RepositoriesView_RenameBranchTitle,
							UIText.RepositoriesView_RenameBranchFailure);
			} catch (IOException e) {
				Activator.handleError(
						UIText.RepositoriesView_RenameBranchFailure, e, true);
			}

		}

		return null;
	}

}
