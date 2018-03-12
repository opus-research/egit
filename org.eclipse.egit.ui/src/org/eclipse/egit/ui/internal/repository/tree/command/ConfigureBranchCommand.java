/*******************************************************************************
 * Copyright (c) 2012 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Mathias Kinzler (SAP AG) - initial implementation
 *******************************************************************************/
package org.eclipse.egit.ui.internal.repository.tree.command;

import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.egit.ui.internal.dialogs.BranchConfigurationDialog;
import org.eclipse.egit.ui.internal.repository.tree.RefNode;
import org.eclipse.jgit.lib.Repository;

/**
 * "Configures" a branch
 */
public class ConfigureBranchCommand extends
		RepositoriesViewCommandHandler<RefNode> {
	public Object execute(ExecutionEvent event) throws ExecutionException {
		final List<RefNode> nodes = getSelectedNodes(event);
		if (nodes.size() == 1) {
			RefNode node = nodes.get(0);
			String branchName = Repository.shortenRefName(node.getObject()
					.getName());
			BranchConfigurationDialog dlg = new BranchConfigurationDialog(
					getShell(event), branchName, node.getRepository());
			dlg.open();
		}
		return null;
	}
}
