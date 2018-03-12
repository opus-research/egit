/*******************************************************************************
 * Copyright (c) 2013 Tasktop Technologies
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Tomasz Zarna (Tasktop Technologies) - initial implementation
 *******************************************************************************/
package org.eclipse.egit.ui.internal.components;

import org.eclipse.core.runtime.Assert;
import org.eclipse.egit.ui.UIUtils;
import org.eclipse.egit.ui.internal.UIText;
import org.eclipse.egit.ui.internal.dialogs.AbstractBranchSelectionDialog;
import org.eclipse.egit.ui.internal.dialogs.BranchSelectionAndEditDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * Instances of this class provide a reusable composite with controls that allow
 * the selection of a branch.
 */
public class BranchSelectionComponent {

	private Repository repository;

	private Composite composite;

	private Label branchLabel;

	private Text branchText;

	private Button selectButton;

	private String selectedBranchName;

	/**
	 * @param parent
	 * @param repo
	 */
	public BranchSelectionComponent(final Composite parent, Repository repo) {
		Assert.isNotNull(repo);
		repository = repo;

		int numColumn = 3;

		composite = new Composite(parent, SWT.NONE);
		composite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		composite.setLayout(new GridLayout(numColumn, false));

		branchLabel = new Label(composite, SWT.NONE);
		branchLabel.setText(UIText.BranchConfigurationComponent_label);

		branchText = new Text(composite, SWT.BORDER);
		GridData textData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		textData.horizontalSpan = numColumn - 2;
		textData.horizontalIndent = 0;
		branchText.setLayoutData(textData);

		selectButton = new Button(composite, SWT.PUSH);
		selectButton.setText(UIText.BranchConfigurationComponent_select);
		UIUtils.setButtonLayoutData(selectButton);
		selectButton.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				AbstractBranchSelectionDialog dialog = new LocalBranchSelectionDialog(
						parent.getShell(), repository, branchText.getText());
				if (dialog.open() == Window.OK) {
					branchText.setText(Repository.shortenRefName(dialog
							.getRefName()));
				}
			}
		});

		branchText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				selectedBranchName = branchText.getText();
			}
		});
	}

	/**
	 * @param visible
	 */
	public void setVisible(boolean visible) {
		composite.setVisible(visible);
		GridData gd = (GridData) composite.getLayoutData();
		gd.exclude = !visible;

	}

	/**
	 * @param branchName
	 */
	public void setBranchName(String branchName) {
		// TODO: set selection
		branchText.setText(branchName);
	}

	/**
	 * @return selected branch name
	 */
	public String getSelectedBranchName() {
		return selectedBranchName;
	}

	/**
	 * Register the listener for branch text control.
	 *
	 * @param modifyListener
	 *            the listener to register
	 */
	public void addModifyListener(ModifyListener modifyListener) {
		branchText.addModifyListener(modifyListener);
	}

	private class LocalBranchSelectionDialog extends
			BranchSelectionAndEditDialog {
		public LocalBranchSelectionDialog(Shell shell, Repository repository,
				String branchToMark) {
			super(shell, repository, Constants.R_HEADS + branchToMark,
					SHOW_LOCAL_BRANCHES
					| EXPAND_LOCAL_BRANCHES_NODE | SELECT_CURRENT_REF);
		}
	}

}
