/*******************************************************************************
 * Copyright (c) 2010 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Mathias Kinzler (SAP AG) - initial implementation
 *******************************************************************************/
package org.eclipse.egit.ui.internal.repository;

import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.internal.components.RepositorySelectionPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jgit.transport.URIish;

/**
 * Wizard to select a URI
 */
public class SelectUriWizard extends Wizard {
	private URIish uri;

	/**
	 * @param sourceSelection
	 */
	public SelectUriWizard(boolean sourceSelection) {
		addPage(new RepositorySelectionPage(sourceSelection, null));
		setWindowTitle(UIText.SelectUriWiazrd_Title);
	}

	/**
	 * @param sourceSelection
	 * @param presetUri
	 */
	public SelectUriWizard(boolean sourceSelection, String presetUri) {
		addPage(new RepositorySelectionPage(sourceSelection, presetUri));
		setWindowTitle(UIText.SelectUriWiazrd_Title);
	}

	/**
	 * @return the URI
	 */
	public URIish getUri() {
		return uri;
	}

	@Override
	public boolean performFinish() {
		uri = ((RepositorySelectionPage) getPages()[0]).getSelection().getURI();
		return uri != null;
	}
}
