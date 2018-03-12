/*******************************************************************************
 * Copyright (c) 2013, 2014 Robin Stocker <robin@nibor.org> and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.actions;

import java.io.IOException;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.internal.push.PushBranchWizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/**
 * "Push Branch..." action for repository
 */
public class PushBranchActionHandler extends RepositoryActionHandler {
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		Repository repository = getRepository(true, event);

		try {
			PushBranchWizard wizard = null;
			Ref ref = getBranchRef(repository);
			if (ref != null) {
				wizard = new PushBranchWizard(repository, ref);
			} else {
				ObjectId id = repository.resolve(repository.getFullBranch());
				wizard = new PushBranchWizard(repository, id);
			}
			WizardDialog dlg = new WizardDialog(getShell(event), wizard);
			dlg.open();
		} catch (IOException ex) {
			Activator.handleError(ex.getLocalizedMessage(), ex, false);
		}

		return null;
	}

	@Override
	public boolean isEnabled() {
		Repository repository = getRepository();
		if (repository == null) {
			return false;
		}
		try {
			Ref head = repository.exactRef(Constants.HEAD);
			if (head != null && head.getObjectId() != null) {
				return true;
			}
		} catch (IOException e) {
			Activator.logError(e.getMessage(), e);
		}
		return false;
	}

	private Ref getBranchRef(Repository repository) {
		try {
			String fullBranch = repository.getFullBranch();
			if (fullBranch != null && fullBranch.startsWith(Constants.R_HEADS))
				return repository.exactRef(fullBranch);
		} catch (IOException e) {
			Activator.handleError(e.getLocalizedMessage(), e, false);
		}
		return null;
	}
}
