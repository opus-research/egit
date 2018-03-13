/*******************************************************************************
 * Copyright (C) 2011-2012, Stefan Lay <stefan.lay@sap.com>
 * Copyright (C) 2017, SATO Yusuke <yusuke.sato.zz@gmail.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core.op;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.egit.core.Activator;
import org.eclipse.egit.core.internal.CoreText;
import org.eclipse.egit.core.op.CloneOperation.PostCloneTask;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.osgi.util.NLS;

/**
 * Adds a fetch specification of the cloned repository and performs a fetch
 */
public class ConfigureFetchAfterCloneTask implements PostCloneTask {

	private String fetchRefSpec;

	private final String remoteName;

	/**
	 * @param remoteName name of the remote in the git config file
	 * @param fetchRefSpec the fetch ref spec which will be added
	 */
	public ConfigureFetchAfterCloneTask(String remoteName, String fetchRefSpec) {
		this.remoteName = remoteName;
		this.fetchRefSpec = fetchRefSpec;
	}

	/**
	 * @param repository the cloned repository
	 * @param monitor
	 * @throws CoreException
	 */
	@Override
	public void execute(Repository repository, IProgressMonitor monitor)
			throws CoreException {
		OperationLogger opLogger = new OperationLogger(CoreText.Start_Fetch,
				CoreText.End_Fetch, CoreText.Error_Fetch,
				new String[] { remoteName,
						OperationLogger.getCurrentBranch(repository) });
		opLogger.logStart();
		try (Git git = new Git(repository)) {
			RemoteConfig configToUse = new RemoteConfig(repository.getConfig(),
					remoteName);
			if (fetchRefSpec != null) {
				configToUse.addFetchRefSpec(new RefSpec(fetchRefSpec));
			}
			configToUse.update(repository.getConfig());
			repository.getConfig().save();
			git.fetch().setRemote(remoteName).call();
			opLogger.logEnd();
		} catch (Exception e) {
			opLogger.logError(e);
			Activator.logError(NLS.bind(
					CoreText.ConfigureFetchAfterCloneTask_couldNotFetch,
					fetchRefSpec), e);
			throw new CoreException(Activator.error(e.getMessage(), e));
		}
	}

}
