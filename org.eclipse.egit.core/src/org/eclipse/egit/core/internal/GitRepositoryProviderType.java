/*******************************************************************************
 * Copyright (c) 2011 Chris Aniszczyk and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Chris Aniszczyk <caniszczyk@gmail.com> - initial API and implementation
 *     Robin Stocker <robin@nibor.org> - ProjectSetCapability
 *******************************************************************************/
package org.eclipse.egit.core.internal;

import java.io.IOException;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.egit.core.Activator;
import org.eclipse.egit.core.GitProjectSetCapability;
import org.eclipse.egit.core.synchronize.GitResourceVariantTreeSubscriber;
import org.eclipse.egit.core.synchronize.dto.GitSynchronizeData;
import org.eclipse.egit.core.synchronize.dto.GitSynchronizeDataSet;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.team.core.ProjectSetCapability;
import org.eclipse.team.core.RepositoryProviderType;
import org.eclipse.team.core.subscribers.Subscriber;

/**
 * The repository type for Git
 */
public class GitRepositoryProviderType extends RepositoryProviderType {

	private final Subscriber subscriber;

	/**
	 * Creates {@link GitRepositoryProviderType}
	 */
	public GitRepositoryProviderType() {
		GitSynchronizeDataSet set = new GitSynchronizeDataSet();
		try {
			Repository[] repositories = Activator.getDefault()
					.getRepositoryCache().getAllRepositories();
			for (int i = 0; i < repositories.length; i++) {
				GitSynchronizeData data = new GitSynchronizeData(
						repositories[i], Constants.HEAD, Constants.HEAD, true);
				set.add(data);
			}
		} catch (IOException e) {
			// do nothing
		}

		GitResourceVariantTreeSubscriber gitSubscriber = new GitResourceVariantTreeSubscriber(set);
		gitSubscriber.init(new NullProgressMonitor());

		subscriber = gitSubscriber;
	}

	public Subscriber getSubscriber() {
		return subscriber;
	}

	@Override
	public ProjectSetCapability getProjectSetCapability() {
		return new GitProjectSetCapability();
	}
}
