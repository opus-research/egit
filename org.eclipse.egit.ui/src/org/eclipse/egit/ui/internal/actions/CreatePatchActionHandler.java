/*******************************************************************************
 * Copyright (C) 2011-2012, IBM Corporation and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.actions;

import java.util.Arrays;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IResource;
import org.eclipse.egit.ui.internal.patch.PatchOperationUI;
import org.eclipse.jgit.lib.Repository;

/**
 * The "Create Patch" action.
 */
public class CreatePatchActionHandler extends RepositoryActionHandler {

	public Object execute(ExecutionEvent event) throws ExecutionException {
		final Repository repository = getRepository(true, event);
		// assert all resources map to the same repository
		if (repository == null)
			return null;
		IResource[] resources = getSelectedResources();
		PatchOperationUI.createPatch(getPart(event), getRepository(),
				Arrays.asList(resources)).start();
		return null;
	}

	@Override
	public boolean isEnabled() {
		return getRepository() != null;
	}
}
