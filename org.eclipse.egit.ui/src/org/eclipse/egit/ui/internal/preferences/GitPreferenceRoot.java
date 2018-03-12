/*******************************************************************************
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2010, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.preferences;

import java.io.File;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.VariablesPlugin;

import org.eclipse.debug.ui.StringVariableSelectionDialog;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIPreferences;
import org.eclipse.egit.ui.UIText;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.DirectoryFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

/** Root preference page for the all of our workspace preferences. */
public class GitPreferenceRoot extends FieldEditorPreferencePage implements
		IWorkbenchPreferencePage {
	private final static int GROUP_SPAN = 3;

	private final static String[][] MERGE_MODE_NAMES_AND_VALUES = new String[3][2];

	static {
		MERGE_MODE_NAMES_AND_VALUES[0][0] = UIText.GitPreferenceRoot_MergeMode_0_Label;
		MERGE_MODE_NAMES_AND_VALUES[0][1] = "0";//$NON-NLS-1$
		MERGE_MODE_NAMES_AND_VALUES[1][0] = UIText.GitPreferenceRoot_MergeMode_1_Label;
		MERGE_MODE_NAMES_AND_VALUES[1][1] = "1";//$NON-NLS-1$
		MERGE_MODE_NAMES_AND_VALUES[2][0] = UIText.GitPreferenceRoot_MergeMode_2_Label;
		MERGE_MODE_NAMES_AND_VALUES[2][1] = "2"; //$NON-NLS-1$
	}

	/**
	 * The default constructor
	 */
	public GitPreferenceRoot() {
		super(FLAT);
	}

	protected IPreferenceStore doGetPreferenceStore() {
		return Activator.getDefault().getPreferenceStore();
	}

	public void init(final IWorkbench workbench) {
		// Do nothing.
	}

	@Override
	protected void createFieldEditors() {
		Composite main = getFieldEditorParent();
		GridLayoutFactory.swtDefaults().margins(0, 0).applyTo(main);

		Group cloningGroup = new Group(main, SWT.SHADOW_ETCHED_IN);
		cloningGroup.setText(UIText.GitPreferenceRoot_CloningRepoGroupHeader);
		GridDataFactory.fillDefaults().grab(true, false).span(GROUP_SPAN, 1)
				.applyTo(cloningGroup);
		DirectoryFieldEditor editor = new DirectoryFieldEditor(
				UIPreferences.DEFAULT_REPO_DIR,
				UIText.GitPreferenceRoot_DefaultRepoFolderLabel, cloningGroup) {

			/** The own control is the variableButton */
			private static final int NUMBER_OF_OWN_CONTROLS = 1;

			@Override
			protected boolean doCheckState() {
				String fileName = getTextControl().getText();
				fileName = fileName.trim();
				if (fileName.length() == 0 && isEmptyStringAllowed())
					return true;

				IStringVariableManager manager = VariablesPlugin.getDefault().getStringVariableManager();
				String substitutedFileName;
				try {
					substitutedFileName = manager.performStringSubstitution(fileName);
				} catch (CoreException e) {
					// It's apparently invalid
					return false;
				}

				File file = new File(substitutedFileName);
				// other than the super implementation, we don't
				// require the file to exist
				return !file.exists() || file.isDirectory();
			}

			@Override
			public int getNumberOfControls() {
				return super.getNumberOfControls() + NUMBER_OF_OWN_CONTROLS;
			}

			@Override
			protected void doFillIntoGrid(Composite parent, int numColumns) {
				super.doFillIntoGrid(parent, numColumns - NUMBER_OF_OWN_CONTROLS);
			}

			@Override
			protected void adjustForNumColumns(int numColumns) {
				super.adjustForNumColumns(numColumns - NUMBER_OF_OWN_CONTROLS);
			}

			@Override
			protected void createControl(Composite parent) {
				// setting validate strategy using the setter method is too late
				super.setValidateStrategy(StringFieldEditor.VALIDATE_ON_KEY_STROKE);

				super.createControl(parent);

				Button variableButton = new Button(parent, SWT.PUSH);
				variableButton.setText(UIText.GitPreferenceRoot_DefaultRepoFolderVariableButton);

				variableButton.addSelectionListener(new SelectionAdapter() {
					public void widgetSelected(SelectionEvent e) {
						StringVariableSelectionDialog dialog = new StringVariableSelectionDialog(getShell());
						int returnCode = dialog.open();
						if (returnCode == Window.OK)
							setStringValue(dialog.getVariableExpression());
					}
				});
			}
		};
		updateMargins(cloningGroup);
		editor.setEmptyStringAllowed(false);
		editor.getLabelControl(cloningGroup).setToolTipText(
				UIText.GitPreferenceRoot_DefaultRepoFolderTooltip);
		addField(editor);

		Group remoteConnectionsGroup = new Group(main, SWT.SHADOW_ETCHED_IN);
		GridDataFactory.fillDefaults().grab(true, false).span(GROUP_SPAN, 1)
				.applyTo(remoteConnectionsGroup);
		remoteConnectionsGroup
				.setText(UIText.GitPreferenceRoot_RemoteConnectionsGroupHeader);

		IntegerFieldEditor timeoutEditor = new IntegerFieldEditor(
				UIPreferences.REMOTE_CONNECTION_TIMEOUT,
				UIText.RemoteConnectionPreferencePage_TimeoutLabel,
				remoteConnectionsGroup);
		timeoutEditor.getLabelControl(remoteConnectionsGroup).setToolTipText(
				UIText.RemoteConnectionPreferencePage_ZeroValueTooltip);
		addField(timeoutEditor);
		updateMargins(remoteConnectionsGroup);

		Group repoChangeScannerGroup = new Group(main, SWT.SHADOW_ETCHED_IN);
		GridDataFactory.fillDefaults().grab(true, false).span(GROUP_SPAN, 1)
				.applyTo(repoChangeScannerGroup);
		repoChangeScannerGroup
				.setText(UIText.GitPreferenceRoot_RepoChangeScannerGroupHeader);
		addField(new BooleanFieldEditor(UIPreferences.REFESH_ON_INDEX_CHANGE,
				UIText.RefreshPreferencesPage_RefreshWhenIndexChange,
				repoChangeScannerGroup));
		addField(new BooleanFieldEditor(UIPreferences.REFESH_ONLY_WHEN_ACTIVE,
				UIText.RefreshPreferencesPage_RefreshOnlyWhenActive,
				repoChangeScannerGroup));
		updateMargins(repoChangeScannerGroup);

		Group mergeGroup = new Group(main, SWT.SHADOW_ETCHED_IN);
		GridDataFactory.fillDefaults().grab(true, false).span(GROUP_SPAN, 1)
				.applyTo(mergeGroup);
		mergeGroup.setText(UIText.GitPreferenceRoot_MergeGroupHeader);
		ComboFieldEditor mergeMode = new ComboFieldEditor(
				UIPreferences.MERGE_MODE,
				UIText.GitPreferenceRoot_MergeModeLabel,
				MERGE_MODE_NAMES_AND_VALUES, mergeGroup);
		mergeMode.getLabelControl(mergeGroup).setToolTipText(
				UIText.GitPreferenceRoot_MergeModeTooltip);
		addField(mergeMode);
		updateMargins(mergeGroup);

		Group blameGroup = new Group(main, SWT.SHADOW_ETCHED_IN);
		GridDataFactory.fillDefaults().grab(true, false).span(GROUP_SPAN, 1)
				.applyTo(blameGroup);
		blameGroup.setText(UIText.GitPreferenceRoot_BlameGroupHeader);
		addField(new BooleanFieldEditor(UIPreferences.BLAME_IGNORE_WHITESPACE,
				UIText.GitPreferenceRoot_BlameIgnoreWhitespaceLabel, blameGroup));
		updateMargins(blameGroup);

		Group secureGroup = new Group(main, SWT.SHADOW_ETCHED_IN);
		GridDataFactory.fillDefaults().grab(true, false).span(GROUP_SPAN, 1)
				.applyTo(secureGroup);
		secureGroup.setText(UIText.GitPreferenceRoot_SecureStoreGroupLabel);
		addField(new BooleanFieldEditor(UIPreferences.CLONE_WIZARD_STORE_SECURESTORE,
				UIText.GitPreferenceRoot_SecureStoreUseByDefault, secureGroup));
		updateMargins(secureGroup);
	}

	private void updateMargins(Group group) {
		// make sure there is some room between the group border
		// and the controls in the group
		GridLayout layout = (GridLayout) group.getLayout();
		layout.marginWidth = 5;
		layout.marginHeight = 5;
	}
}
