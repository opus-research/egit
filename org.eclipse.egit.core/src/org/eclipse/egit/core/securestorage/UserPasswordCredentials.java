/*******************************************************************************
 * Copyright (C) 2010, Jens Baumgart <jens.baumgart@sap.com>
 * Copyright (C) 2010, Edwin Kempin <edwin.kempin@sap.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core.securestorage;

/**
 * Implements a credentials object containing user and password.
 */
public class UserPasswordCredentials {

	private final String user;
	private final String password;

	/**
	 * @param user
	 * @param password
	 */
	public UserPasswordCredentials(String user, String password) {
		this.user = user;
		if (password != null && password.length() > 0)
			this.password = password;
		else
			this.password = null;
	}

	/**
	 * @return user
	 */
	public String getUser() {
		return user;
	}

	/**
	 * @return password
	 */
	public String getPassword() {
		return password;
	}

	/**
	 * @return {@code true} if credentials are not null and not empty
	 */
	public boolean isValid() {
		return user != null && user.length() > 0 && password != null
				&& password.length() > 0;
	}
}
