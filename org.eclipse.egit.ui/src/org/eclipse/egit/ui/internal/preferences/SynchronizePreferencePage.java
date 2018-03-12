/*******************************************************************************
 * Copyright (C) 2011, 2015 Mathias Kinzler <mathias.kinzler@sap.com> and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.preferences;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.egit.core.Activator.MergeStrategyDescriptor;
import org.eclipse.egit.core.GitCorePreferences;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIPreferences;
import org.eclipse.egit.ui.internal.UIText;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.RadioGroupFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

/** Preference page for views preferences */
public class SynchronizePreferencePage extends FieldEditorPreferencePage
		implements IWorkbenchPreferencePage {

	private BooleanFieldEditor useLogicalModelEditor;

	private RadioGroupFieldEditor modelStrategyEditor;

	private ScopedPreferenceStore corePreferenceStore;

	/**
	 * The default constructor
	 */
	public SynchronizePreferencePage() {
		super(FLAT);
	}

	@Override
	protected IPreferenceStore doGetPreferenceStore() {
		return Activator.getDefault().getPreferenceStore();
	}

	@Override
	public void init(final IWorkbench workbench) {
		// Do nothing.
	}

	@Override
	protected void createFieldEditors() {
		addField(new BooleanFieldEditor(
				UIPreferences.SYNC_VIEW_FETCH_BEFORE_LAUNCH,
				UIText.GitPreferenceRoot_fetchBeforeSynchronization,
				getFieldEditorParent()));
		addField(new BooleanFieldEditor(
				UIPreferences.SYNC_VIEW_ALWAYS_SHOW_CHANGESET_MODEL,
				UIText.GitPreferenceRoot_automaticallyEnableChangesetModel,
				getFieldEditorParent()));
		useLogicalModelEditor = new BooleanFieldEditor(
				GitCorePreferences.core_enableLogicalModel,
				UIText.GitPreferenceRoot_useLogicalModel,
				getFieldEditorParent()) {
			@Override
			public IPreferenceStore getPreferenceStore() {
				return getCorePreferenceStore();
			}
		};
		addField(useLogicalModelEditor);

		Label spacer = new Label(getFieldEditorParent(), SWT.NONE);
		spacer.setSize(0, 12);
		Composite modelStrategyParent = getFieldEditorParent();
		modelStrategyEditor = new RadioGroupFieldEditor(
				GitCorePreferences.core_preferredMergeStrategy,
				UIText.GitPreferenceRoot_preferreMergeStrategy_group, 1,
				getAvailableMergeStrategies(), modelStrategyParent, false) {
			@Override
			public IPreferenceStore getPreferenceStore() {
				return getCorePreferenceStore();
			}

		};
		modelStrategyEditor.getLabelControl(modelStrategyParent)
				.setToolTipText(UIText.GitPreferenceRoot_preferreMergeStrategy_label);
		addField(modelStrategyEditor);
	}

	private String[][] getAvailableMergeStrategies() {
		org.eclipse.egit.core.Activator coreActivator = org.eclipse.egit.core.Activator
				.getDefault();
		List<String[]> strategies = new ArrayList<>();
		strategies.add(new String[] {
				UIText.GitPreferenceRoot_defaultMergeStrategyLabel, "" }); //$NON-NLS-1$
		for (MergeStrategyDescriptor strategy : coreActivator
				.getRegisteredMergeStrategies()) {
			strategies.add(new String[] { strategy.getLabel(),
					strategy.getName() });
		}
		return strategies.toArray(new String[strategies.size()][2]);
	}

	private ScopedPreferenceStore getCorePreferenceStore() {
		if (corePreferenceStore == null) {
			corePreferenceStore = new ScopedPreferenceStore(
					InstanceScope.INSTANCE,
					org.eclipse.egit.core.Activator.getPluginId());
		}
		return corePreferenceStore;
	}
}
