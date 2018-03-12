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
package org.eclipse.egit.ui.internal.repository.tree;

import java.io.IOException;
import java.net.URISyntaxException;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RemoteConfig;

/**
 * Property Tester used for enabling/disabling of context menus in the Git
 * Repositories View.
 */
public class RepositoriesViewPropertyTester extends PropertyTester {

	public boolean test(Object receiver, String property, Object[] args,
			Object expectedValue) {

		if (!(receiver instanceof RepositoryTreeNode))
			return false;
		RepositoryTreeNode node = (RepositoryTreeNode) receiver;

		if (property.equals("isBare")) //$NON-NLS-1$
			return node.getRepository().isBare();

		if (property.equals("isRefCheckedOut")) { //$NON-NLS-1$
			if (!(node.getObject() instanceof Ref))
				return false;
			Ref ref = (Ref) node.getObject();
			try {
				return ref.getName().equals(
						node.getRepository().getFullBranch());
			} catch (IOException e) {
				return false;
			}
		}
		if (property.equals("isLocalBranch")) { //$NON-NLS-1$
			if (!(node.getObject() instanceof Ref))
				return false;
			Ref ref = (Ref) node.getObject();
			return ref.getName().startsWith(Constants.R_HEADS);
		}
		if (property.equals("fetchExists")) { //$NON-NLS-1$
			if (node instanceof RemoteNode) {
				String configName = ((RemoteNode) node).getObject();

				RemoteConfig rconfig;
				try {
					rconfig = new RemoteConfig(
							node.getRepository().getConfig(), configName);
				} catch (URISyntaxException e2) {
					return false;
				}
                // we need to have a fetch ref spec and a fetch URI
				return !rconfig.getFetchRefSpecs().isEmpty() && !rconfig.getURIs().isEmpty();
			}
		}
		if (property.equals("pushExists")) { //$NON-NLS-1$
			if (node instanceof RemoteNode) {
				String configName = ((RemoteNode) node).getObject();

				RemoteConfig rconfig;
				try {
					rconfig = new RemoteConfig(
							node.getRepository().getConfig(), configName);
				} catch (URISyntaxException e2) {
					return false;
				}
                // we need to have at least a push ref spec and any URI
				return !rconfig.getPushRefSpecs().isEmpty() && (!rconfig.getPushURIs().isEmpty() || !rconfig.getURIs().isEmpty());
			}
		}
		if (property.equals("canMerge")) { //$NON-NLS-1$
			Repository rep = node.getRepository();
			try {
				String branch = rep.getFullBranch();
				if (branch == null)
					return false; // fail gracefully...
				return branch.startsWith(Constants.R_HEADS);
			} catch (IOException e) {
				return false;
			}
		}
		return false;
	}
}
