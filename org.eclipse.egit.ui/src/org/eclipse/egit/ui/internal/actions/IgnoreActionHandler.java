/*******************************************************************************
 * Copyright (C) 2009, Alex Blewitt <alex.blewitt@gmail.com>
 * Copyright (C) 2010, Jens Baumgart <jens.baumgart@sap.com>
 * Copyright (C) 2012, Robin Stocker <robin@nibor.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * See LICENSE for the full license text, also available.
 *******************************************************************************/
package org.eclipse.egit.ui.internal.actions;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.egit.ui.internal.operations.IgnoreOperationUI;

/** Action for ignoring files via .gitignore. */
public class IgnoreActionHandler extends RepositoryActionHandler {

	public Object execute(ExecutionEvent event) throws ExecutionException {
		final IResource[] resources = getSelectedResources(event);
		if (resources.length == 0)
			return null;
		List<IPath> paths = new ArrayList<IPath>();
		for (IResource resource : resources)
			paths.add(resource.getLocation());

		IgnoreOperationUI operation = new IgnoreOperationUI(paths);
		operation.run();
		return null;
	}

	@Override
	public boolean isEnabled() {
		// Do not consult Team.isIgnoredHint here because the user
		// should be allowed to add ignored resources to .gitignore
		return true;
	}
}
