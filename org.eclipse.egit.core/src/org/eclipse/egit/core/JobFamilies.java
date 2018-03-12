/*******************************************************************************
 * Copyright (C) 2011, Matthias Sohn <matthias.sohn@sap.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core;

/**
 * Job families of EGit jobs. May be used in tests to join job execution.
 *
 */
public class JobFamilies {

	/**
	 * Job family for Repository changed job
	 */
	public static final Object REPOSITORY_CHANGED = new Object();

	/**
	 * Job family for Index Diff Cache update
	 */
	public static final Object INDEX_DIFF_CACHE_UPDATE = new Object();

	/**
	 * Job family for auto share job
	 */
	public static final Object AUTO_SHARE = new Object();
}
