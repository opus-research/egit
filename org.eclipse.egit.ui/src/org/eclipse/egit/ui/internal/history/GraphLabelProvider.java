/*******************************************************************************
 * Copyright (C) 2006, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2011, Matthias Sohn <matthias.sohn@sap.com>
 * Copyright (C) 2011, IBM Corporation
 * Copyright (C) 2012, Mathias Kinzler <mathias.kinzler@sap.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.history;

import java.io.IOException;

import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.internal.dialogs.CommitLabelProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;

/**
 * A Label Provider for Commits
 */
class GraphLabelProvider extends CommitLabelProvider implements
		ITableLabelProvider {
	@Override
	public String getColumnText(Object element, int columnIndex) {
		final SWTCommit c = (SWTCommit) element;
		try {
			c.parseBody();
		} catch (IOException e) {
			Activator.error("Error parsing body", e); //$NON-NLS-1$
			return ""; //$NON-NLS-1$
		}
		return super.getColumnText(c, columnIndex);
	}
}
