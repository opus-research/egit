/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc, and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.push;

import org.eclipse.egit.ui.internal.UIText;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

/**
 * A dialog dedicated to {@link PushBranchWizard}, customizing button labels
 */
public class PushBranchDialog extends WizardDialog {

	/**
	 * @param parentShell
	 * @param newWizard
	 */
	public PushBranchDialog(Shell parentShell, PushBranchWizard newWizard) {
		super(parentShell, newWizard);
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		super.createButtonsForButtonBar(parent);
		Button nextButton = getButton(IDialogConstants.NEXT_ID);
		if (nextButton != null) {
			nextButton.setText(UIText.PushBranchWizard_previewButton);
		}
		Button finishButton = getButton(IDialogConstants.FINISH_ID);
		if (finishButton != null) {
			finishButton.setText(UIText.PushBranchWizard_pushButton);
		}
	}

}
