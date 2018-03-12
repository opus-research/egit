/*******************************************************************************
 * Copyright (c) 2010, 2012 EclipseSource.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Benjamin Muskalla (EclipseSource) - initial implementation
 *    Mathias Kinzler <mathias.kinzler@sap.com> - replace InputDialog with RenameBranchDialog
 *******************************************************************************/
package org.eclipse.egit.ui.internal.repository.tree.command;

import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.egit.ui.internal.dialogs.BranchRenameDialog;
import org.eclipse.egit.ui.internal.repository.tree.RefNode;
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
		new BranchRenameDialog(shell, refNode.getRepository(), refNode.getObject()).open();
		return null;
	}
}
