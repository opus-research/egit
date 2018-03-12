/*******************************************************************************
 * Copyright (C) 2010, Jens Baumgart <jens.baumgart@sap.com>
 * Copyright (C) 2012, Matthias Sohn <matthias.sohn@sap.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal;

import java.io.File;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIPreferences;
import org.eclipse.egit.ui.UIText;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.PlatformUI;

/**
 * Checks the system configuration
 *
 */
public class ConfigurationChecker {

	/**
	 * Checks the system configuration. Currently only the HOME variable on
	 * Windows is checked
	 */
	public static void checkConfiguration() {
		// Schedule a job
		// This avoids that the check is executed too early
		// because in startup phase the JobManager is suspended
		// and scheduled Jobs are executed later
		Job job = new Job(UIText.ConfigurationChecker_checkConfiguration) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				PlatformUI.getWorkbench().getDisplay()
						.asyncExec(new Runnable() {
							public void run() {
								check();
							}
						});
				return Status.OK_STATUS;
			}
		};
		job.schedule();
	}

	private static void check() {
		checkGitPrefix();
		checkHome();
	}

	private static void checkGitPrefix() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		boolean hidden = !store
				.getBoolean(UIPreferences.SHOW_GIT_PREFIX_WARNING);
		if (!hidden && FS.DETECTED.gitPrefix() == null)
			Activator.handleIssue(IStatus.WARNING,
					UIText.ConfigurationChecker_gitPrefixWarningMessage, null,
					false);
	}

	private static void checkHome() {
		String home = System.getenv("HOME"); //$NON-NLS-1$
		if (home != null)
			return; // home is set => ok
		home = calcHomeDir();
		String message = NLS.bind(UIText.ConfigurationChecker_homeNotSet, home);
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		boolean hidden = !store.getBoolean(UIPreferences.SHOW_HOME_DIR_WARNING);
		if (!hidden)
			Activator.handleIssue(IStatus.WARNING, message, null, false);
	}

	private static String calcHomeDir() {
		if (runsOnWindows()) {
			String homeDrive = System.getenv("HOMEDRIVE"); //$NON-NLS-1$
			if (homeDrive != null) {
				String homePath = SystemReader.getInstance().getenv("HOMEPATH"); //$NON-NLS-1$
				return new File(homeDrive, homePath).getAbsolutePath();
			}
			return System.getenv("HOMESHARE"); //$NON-NLS-1$
		} else {
			// The user.home property is not compatible with Git for Windows
			return System.getProperty("user.home"); //$NON-NLS-1$
		}
	}

	private static boolean runsOnWindows() {
		String os;
		try {
			os = System.getProperty("os.name"); //$NON-NLS-1$
		} catch (RuntimeException e) {
			return false;
		}
		return os.indexOf("Windows") != -1; //$NON-NLS-1$
	}

}
