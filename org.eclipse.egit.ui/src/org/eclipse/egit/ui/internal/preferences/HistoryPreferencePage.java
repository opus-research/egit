/*******************************************************************************
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.preferences;

import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIPreferences;
import org.eclipse.egit.ui.UIText;
import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

/** Preferences for our history view. */
public class HistoryPreferencePage extends FieldEditorPreferencePage
		implements IWorkbenchPreferencePage {
	/** */
	public HistoryPreferencePage() {
		super(GRID);
		setTitle(UIText.HistoryPreferencePage_title);
		setPreferenceStore(Activator.getDefault().getPreferenceStore());
	}

	@Override
	protected void createFieldEditors() {
		addField(new BooleanFieldEditor(
				UIPreferences.RESOURCEHISTORY_SHOW_COMMENT_WRAP,
				UIText.ResourceHistory_toggleCommentWrap,
				getFieldEditorParent()));
		addField(new BooleanFieldEditor(
				UIPreferences.RESOURCEHISTORY_SHOW_REV_COMMENT,
				UIText.ResourceHistory_toggleRevComment, getFieldEditorParent()));
		addField(new BooleanFieldEditor(
				UIPreferences.RESOURCEHISTORY_SHOW_REV_DETAIL,
				UIText.ResourceHistory_toggleRevDetail, getFieldEditorParent()));
	}

	public boolean performOk() {
		Activator.getDefault().savePluginPreferences();
		return super.performOk();
	}

	public void init(IWorkbench workbench) {
		// Nothing to do
	}

}
