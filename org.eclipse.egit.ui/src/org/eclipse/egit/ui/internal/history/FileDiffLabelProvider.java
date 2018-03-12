/*******************************************************************************
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.history;

import org.eclipse.core.runtime.Path;
import org.eclipse.egit.ui.UIIcons;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.resource.ResourceManager;
import org.eclipse.jface.viewers.BaseLabelProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

class FileDiffLabelProvider extends BaseLabelProvider implements
		ITableLabelProvider {
	private Image ADD = UIIcons.ELCL16_ADD.createImage();

	private Image COPY = PlatformUI.getWorkbench().getSharedImages().getImage(
			ISharedImages.IMG_TOOL_COPY);

	private Image DELETE = PlatformUI.getWorkbench().getSharedImages()
			.getImage(ISharedImages.IMG_ETOOL_DELETE);

	private Image DEFAULT = PlatformUI.getWorkbench().getSharedImages()
			.getImage(ISharedImages.IMG_OBJ_FILE);

	private ResourceManager resourceManager = new LocalResourceManager(
			JFaceResources.getResources());

	public String getColumnText(final Object element, final int columnIndex) {
		if (columnIndex == 0) {
			final FileDiff c = (FileDiff) element;
			return c.getPath();
		}
		return null;
	}

	public Image getColumnImage(final Object element, final int columnIndex) {
		if (columnIndex == 0) {
			final FileDiff c = (FileDiff) element;
			switch (c.getChange()) {
			case ADD:
				return ADD;
			case COPY:
				return COPY;
			case DELETE:
				return DELETE;
			case RENAME:
				// fall through
			case MODIFY:
				Image image = DEFAULT;
				String name = new Path(c.getPath()).lastSegment();
				if (name != null) {
					ImageDescriptor descriptor = PlatformUI.getWorkbench()
							.getEditorRegistry().getImageDescriptor(name);
					image = (Image) this.resourceManager.get(descriptor);
				}
				return image;
			}
		}
		return null;
	}

	@Override
	public void dispose() {
		this.resourceManager.dispose();
		ADD.dispose();
		// DELETE is shared, don't dispose
		super.dispose();
	}

}
