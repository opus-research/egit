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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.internal.components.RefSpecPage;
import org.eclipse.egit.ui.internal.components.RepositorySelection;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.osgi.util.NLS;

/**
 * Allows to configure a "Remote".
 * <p>
 * Asks for a name and whether to configure fetch, push, or both. Depending on
 * the user's decision about what to configure, the fetch, push, or both
 * configurations are performed.
 */
public class ConfigureRemoteWizard extends Wizard {

	final StoredConfig myConfiguration;

	RemoteConfig myRemoteConfiguration;

	final boolean pushMode;

	final String myRemoteName;

	private ConfigureUriPage configureFetchUriPage;

	private RefSpecPage configureFetchSpecPage;

	private ConfigureUriPage configurePushUriPage;

	private RefSpecPage configurePushSpecPage;

	@Override
	public IWizardPage getNextPage(IWizardPage page) {
		if (page == configureFetchUriPage) {
			try {
				getContainer().run(false, false, new IRunnableWithProgress() {

					public void run(IProgressMonitor monitor)
							throws InvocationTargetException,
							InterruptedException {
						String taskName = NLS.bind(
								UIText.NewRemoteWizard_CheckingUriTaskName,
								configureFetchUriPage.getUri()
										.toPrivateString());
						monitor.beginTask(taskName, IProgressMonitor.UNKNOWN);
						configureFetchSpecPage
								.setSelection(new RepositorySelection(
										configureFetchUriPage.getUri(), null));
						configureFetchSpecPage
								.setCredentials(configureFetchUriPage
										.getCredentials());
					}
				});
			} catch (InvocationTargetException e) {
				Activator.handleError(e.getMessage(), e, true);
			} catch (InterruptedException e) {
				Activator.handleError(e.getMessage(), e, true);
			}

		}

		if (page == configurePushUriPage) {
			try {
				getContainer().run(false, false, new IRunnableWithProgress() {

					public void run(IProgressMonitor monitor)
							throws InvocationTargetException,
							InterruptedException {
						String taskName = NLS.bind(
								UIText.NewRemoteWizard_CheckingUriTaskName,
								configurePushUriPage.getAllUris().get(0)
										.toPrivateString());
						monitor.beginTask(taskName, IProgressMonitor.UNKNOWN);
						// use the first URI
						configurePushSpecPage
								.setSelection(new RepositorySelection(
										configurePushUriPage.getAllUris()
												.get(0), null));
						configurePushSpecPage.setCredentials(configurePushUriPage.getCredentials());
					}
				});
			} catch (InvocationTargetException e) {
				Activator.handleError(e.getMessage(), e, true);
			} catch (InterruptedException e) {
				Activator.handleError(e.getMessage(), e, true);
			}
		}

		return super.getNextPage(page);
	}

	/**
	 *
	 * @param repository
	 * @param remoteName
	 * @param push
	 */
	public ConfigureRemoteWizard(Repository repository, String remoteName,
			boolean push) {
		super.setNeedsProgressMonitor(true);
		myConfiguration = repository.getConfig();
		myRemoteName = remoteName;
		pushMode = push;

		try {
			myRemoteConfiguration = new RemoteConfig(myConfiguration,
					remoteName);
		} catch (URISyntaxException e) {
			// handle this by trying to cleanup the configuration entries
			myConfiguration.unsetSection("remote", remoteName); //$NON-NLS-1$
			// TODO Exception handling
			try {
				myRemoteConfiguration = new RemoteConfig(myConfiguration,
						remoteName);
			} catch (URISyntaxException e1) {
				// panic
				throw new IllegalStateException(e1.getMessage());
			}
		}

		configureFetchUriPage = new ConfigureUriPage(true,
				myRemoteConfiguration);
		if (!pushMode)
			addPage(configureFetchUriPage);

		configureFetchSpecPage = new RefSpecPage(repository, false);
		if (!pushMode)
			addPage(configureFetchSpecPage);

		configurePushUriPage = new ConfigureUriPage(false,
				myRemoteConfiguration);
		if (pushMode)
			addPage(configurePushUriPage);

		configurePushSpecPage = new RefSpecPage(repository, true);
		if (pushMode)
			addPage(configurePushSpecPage);

		setWindowTitle(NLS.bind(
				UIText.ConfigureRemoteWizard_WizardTitle_Change, myRemoteName));
	}

	/**
	 * @return the configuration
	 *
	 */
	public StoredConfig getConfiguration() {
		return myConfiguration;
	}

	@Override
	public boolean performFinish() {
		if (pushMode) {
			while (!myRemoteConfiguration.getPushURIs().isEmpty())
				myRemoteConfiguration.removePushURI(myRemoteConfiguration
						.getPushURIs().get(0));
			for (URIish uri : configurePushUriPage.getUris())
				myRemoteConfiguration.addPushURI(uri);
			myRemoteConfiguration.setPushRefSpecs(configurePushSpecPage
					.getRefSpecs());
		} else {
			while (!myRemoteConfiguration.getURIs().isEmpty())
				myRemoteConfiguration.removeURI(myRemoteConfiguration.getURIs()
						.get(0));
			myRemoteConfiguration.addURI(configureFetchUriPage.getUri());
			myRemoteConfiguration.setFetchRefSpecs(configureFetchSpecPage
					.getRefSpecs());
			myRemoteConfiguration.setTagOpt(configureFetchSpecPage.getTagOpt());
		}

		myRemoteConfiguration.update(myConfiguration);

		try {
			myConfiguration.save();
			return true;
		} catch (IOException e) {
			// TODO better Exception handling
			return false;
		}
	}
}
