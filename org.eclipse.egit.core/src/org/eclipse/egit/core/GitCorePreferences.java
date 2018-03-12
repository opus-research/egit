/*******************************************************************************
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2011, Robin Rosenberg
 * Copyright (C) 2013, Matthias Sohn <matthias.sohn@sap.com>
 * Copyright (C) 2015, Obeo
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core;

/** Preferences used by the core plugin. */
public class GitCorePreferences {
	/** */
	public static final String core_packedGitWindowSize =
		"core_packedGitWindowSize";  //$NON-NLS-1$
	/** */
	public static final String core_packedGitLimit =
		"core_packedGitLimit";  //$NON-NLS-1$
	/** */
	public static final String core_packedGitMMAP =
		"core_packedGitMMAP";  //$NON-NLS-1$
	/** */
	public static final String core_deltaBaseCacheLimit =
		"core_deltaBaseCacheLimit";  //$NON-NLS-1$
	/** */
	public static final String core_streamFileThreshold =
		"core_streamFileThreshold"; //$NON-NLS-1$
	/** */
	public static final String core_autoShareProjects =
		"core_autoShareProjects";  //$NON-NLS-1$
	/** */
	public static final String core_autoIgnoreDerivedResources =
		"core_autoIgnoreDerivedResources"; //$NON-NLS-1$

	/** Holds true if the logical model should be used. */
	public static final String core_enableLogicalModel =
		"core_enableLogicalModel"; //$NON-NLS-1$

	/**
	 * Holds the key to the preferred merge strategy in the MergeStrategy
	 * registry, i.e. the preferred strategy can be obtained by
	 * {@code MergeStrategy.get(key)}.
	 */
	public static final String core_preferredMergeStrategy = "core_preferredMergeStrategy"; //$NON-NLS-1$
}
