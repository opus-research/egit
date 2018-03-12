/*******************************************************************************
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.actions;

import java.net.URISyntaxException;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.internal.push.PushWizard;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.jgit.lib.Repository;

/**
 * Action for choosing specifications for push, and pushing out to another
 * repository.
 */
public class PushAction extends RepositoryAction {

	@Override
	public void run(IAction action) {
		final Repository repository = getRepository(true);
		if (repository == null)
			return;

		final PushWizard pushWizard;
		try {
			pushWizard = new PushWizard(repository);
		} catch (URISyntaxException x) {
			ErrorDialog.openError(getShell(), UIText.PushAction_wrongURITitle,
					UIText.PushAction_wrongURIDescription, new Status(
							IStatus.ERROR, Activator.getPluginId(), x
									.getMessage(), x));
			return;
		}
		final WizardDialog dialog = new WizardDialog(getShell(), pushWizard);
		dialog.open();
	}

	@Override
	public boolean isEnabled() {
		return getRepository(false) != null;
	}
}
