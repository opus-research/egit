/*******************************************************************************
 * Copyright (c) 2014 Maik Schreiber
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Maik Schreiber - initial implementation
 *******************************************************************************/
package org.eclipse.egit.ui.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.egit.ui.internal.branch.CleanupUncomittedChangesDialog;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.swt.widgets.Shell;

/** Utility class for handling repositories in the UI. */
public final class UIRepositoryUtils {
	private UIRepositoryUtils() {
		// nothing to do
	}

	/**
	 * Checks the repository to see if there are uncommitted changes, and
	 * prompts the user to clean them up.
	 *
	 * @param repo
	 *            the repository
	 * @param shell
	 *            the parent shell for opening the dialog
	 * @return true if the git status was clean or it was dirty and the user
	 *         cleaned up the uncommitted changes and the previous action may
	 *         continue
	 * @throws GitAPIException
	 *             if there was an error checking the repository
	 */
	public static boolean handleUncommittedFiles(Repository repo, Shell shell)
			throws GitAPIException {
		Status status = new Git(repo).status().call();
		if (status.hasUncommittedChanges()) {
			List<String> files = new ArrayList<String>(status.getModified());
			Collections.sort(files);

			CleanupUncomittedChangesDialog cleanupUncomittedChangesDialog = new CleanupUncomittedChangesDialog(
					shell,
					UIText.AbstractRebaseCommandHandler_cleanupDialog_title,
					UIText.AbstractRebaseCommandHandler_cleanupDialog_text,
					repo, files);
			cleanupUncomittedChangesDialog.open();
			return cleanupUncomittedChangesDialog.shouldContinue();
		} else
			return true;
	}
}
