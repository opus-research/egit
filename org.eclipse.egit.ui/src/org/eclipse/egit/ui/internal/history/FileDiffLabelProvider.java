/*******************************************************************************
 * Copyright (C) 2008, 2013 Shawn O. Pearce <spearce@spearce.org> and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.history;

import java.text.MessageFormat;

import org.eclipse.egit.ui.internal.UIText;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.resource.ResourceManager;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.RGB;

/**
 * Label provider for {@link FileDiff} objects
 */
public class FileDiffLabelProvider extends ColumnLabelProvider {

	private final ResourceManager resourceManager = new LocalResourceManager(
			JFaceResources.getResources());
	private final Color dimmedForegroundColor;

	/**
	 * @param dimmedForegroundRgb the color used for as foreground color for "unhighlighted" entries
	 */
	public FileDiffLabelProvider(RGB dimmedForegroundRgb) {
		dimmedForegroundColor = resourceManager.createColor(dimmedForegroundRgb);
	}

	@Override
	public String getText(final Object element) {
		return ((FileDiff) element).getLabel(element);
	}

	@Override
	public Image getImage(final Object element) {
		final FileDiff c = (FileDiff) element;
		return (Image) resourceManager.get(c.getImageDescriptor(c));
	}

	@Override
	public void dispose() {
		this.resourceManager.dispose();
		super.dispose();
	}

	@Override
	public Color getForeground(Object element) {
		final FileDiff c = (FileDiff) element;
		if (!c.isMarked(FileDiffContentProvider.INTERESTING_MARK_TREE_FILTER_INDEX))
			return dimmedForegroundColor;
		else
			return null;
	}

	@Override
	public String getToolTipText(final Object element) {
		final FileDiff c = (FileDiff) element;
		if (c.getChange() == ChangeType.RENAME) {
			return MessageFormat.format(
					UIText.FileDiffLabelProvider_RenamedFromToolTip,
					c.getOldPath());
		}
		return null;
	}
}
