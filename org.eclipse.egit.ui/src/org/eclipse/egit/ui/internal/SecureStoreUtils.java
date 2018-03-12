/*******************************************************************************
 * Copyright (C) 2010, Jens Baumgart <jens.baumgart@sap.com>
 * Copyright (C) 2010, Philipp Thun <philipp.thun@sap.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal;

import java.io.IOException;

import org.eclipse.egit.core.securestorage.UserPasswordCredentials;
import org.eclipse.egit.ui.Activator;
import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.jgit.transport.URIish;

/**
 * Utilities for EGit secure store
 */
public class SecureStoreUtils {
	/**
	 * Store credentials for the given uri
	 *
	 * @param credentials
	 * @param uri
	 * @return true if successful
	 */
	public static boolean storeCredentials(UserPasswordCredentials credentials,
			URIish uri) {
		if (credentials != null && uri != null) {
			try {
				org.eclipse.egit.core.Activator.getDefault().getSecureStore()
						.putCredentials(uri, credentials);
			} catch (StorageException e) {
				Activator.handleError(
						UIText.SecureStoreUtils_writingCredentialsFailed, e,
						true);
				return false;
			} catch (IOException e) {
				Activator.handleError(
						UIText.SecureStoreUtils_writingCredentialsFailed, e,
						true);
				return false;
			}
		}
		return true;
	}

	/**
	 * @param uri
	 * @param rethrow
	 *            if {@code true} this method will rethrow exceptions raised by
	 *            the underlying implementation
	 * @return credentials stored in secure store for given uri
	 */
	public static UserPasswordCredentials getCredentials(final URIish uri,
			boolean rethrow) {
		try {
			return org.eclipse.egit.core.Activator.getDefault()
					.getSecureStore().getCredentials(uri);
		} catch (StorageException e) {
			Activator.logError(
					UIText.EGitCredentialsProvider_errorReadingCredentials, e);
			if (rethrow)
				throw new RuntimeException(e.getMessage(), e);
		}
		return null;
	}
}
