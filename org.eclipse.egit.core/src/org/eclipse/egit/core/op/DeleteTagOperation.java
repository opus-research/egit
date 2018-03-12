/******************************************************************************
 *  Copyright (c) 2011 GitHub Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *    Kevin Sawicki (GitHub Inc.) - initial API and implementation
 *****************************************************************************/
package org.eclipse.egit.core.op;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.egit.core.Activator;
import org.eclipse.egit.core.CoreText;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;

/**
 * Operation that deletes a tag
 */
public class DeleteTagOperation implements IEGitOperation {

	private final Repository repository;

	private final String tag;

	/**
	 * Create operation that deletes a single tag
	 *
	 * @param repository
	 * @param tag
	 */
	public DeleteTagOperation(final Repository repository, final String tag) {
		this.repository = repository;
		this.tag = tag;
	}

	public void execute(IProgressMonitor monitor) throws CoreException {
		try {
			Git.wrap(repository).tagDelete().setTags(tag).call();
		} catch (GitAPIException e) {
			throw new CoreException(Activator.error(
					CoreText.DeleteTagOperation_exceptionMessage, e));
		}
	}

	public ISchedulingRule getSchedulingRule() {
		return null;
	}
}
