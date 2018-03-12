/*******************************************************************************
 * Copyright (C) 2015, Max Hohenegger <eclipse@hohenegger.eu>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.gitflow.ui.internal;

import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.egit.ui.Activator;
import org.eclipse.jface.resource.ImageDescriptor;

/**
 * Icons for Gitflow integration.
 */
public class UIIcons {
	/** Decoration for initialized Gitflow repository. */
	public final static ImageDescriptor OVR_GITFLOW;

	/** base URL */
	public final static URL base;

	static {
		base = init();
		OVR_GITFLOW = map("ovr/staged_renamed.gif"); //$NON-NLS-1$
	}

	private static ImageDescriptor map(final String icon) {
		if (base != null)
			try {
				return ImageDescriptor.createFromURL(new URL(base, icon));
			} catch (MalformedURLException mux) {
				Activator.logError(UIText.UIIcons_errorLoadingPluginImage, mux);
			}
		return ImageDescriptor.getMissingImageDescriptor();
	}

	private static URL init() {
		try {
			return new URL(Activator.getDefault().getBundle().getEntry("/"), //$NON-NLS-1$
					"icons/"); //$NON-NLS-1$
		} catch (MalformedURLException mux) {
			Activator.logError(UIText.UIIcons_errorDeterminingIconBase, mux);
			return null;
		}
	}
}
