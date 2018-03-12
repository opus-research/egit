/*******************************************************************************
 *  Copyright (c) 2011 GitHub Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *    Kevin Sawicki (GitHub Inc.) - initial API and implementation
 *******************************************************************************/
package org.eclipse.egit.ui.internal.commit;

import java.io.IOException;

import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.PlatformObject;
import org.eclipse.egit.ui.UIIcons;
import org.eclipse.egit.ui.internal.history.FileDiff;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.ui.model.IWorkbenchAdapter;

/**
 * Class that encapsulates a particular {@link Repository} instance and
 * {@link RevCommit} instance.
 *
 * This class computes and provides access to the {@link FileDiff} objects
 * introduced by the commit.
 */
public class RepositoryCommit extends PlatformObject implements
		IWorkbenchAdapter {

	/**
	 * NAME_LENGTH
	 */
	public static final int NAME_LENGTH = 8;

	private Repository repository;

	private RevCommit commit;

	private FileDiff[] diffs;

	/**
	 * Create a repository commit
	 *
	 * @param repository
	 * @param commit
	 */
	public RepositoryCommit(Repository repository, RevCommit commit) {
		Assert.isNotNull(repository, "Repository cannot be null"); //$NON-NLS-1$
		Assert.isNotNull(commit, "Commit cannot be null"); //$NON-NLS-1$
		this.repository = repository;
		this.commit = commit;
	}

	/**
	 * @see org.eclipse.core.runtime.PlatformObject#getAdapter(java.lang.Class)
	 */
	public Object getAdapter(Class adapter) {
		if (Repository.class == adapter)
			return repository;

		if (RevCommit.class == adapter)
			return commit;

		return super.getAdapter(adapter);
	}

	/**
	 * Abbreviate commit id to {@link #NAME_LENGTH} size.
	 *
	 * @return abbreviated commit id
	 */
	public String abbreviate() {
		return commit.abbreviate(NAME_LENGTH).name();
	}

	/**
	 * Get repository name
	 *
	 * @return repo name
	 */
	public String getRepositoryName() {
		if (!repository.isBare())
			return repository.getDirectory().getParentFile().getName();
		else
			return repository.getDirectory().getName();
	}

	/**
	 * Get repository
	 *
	 * @return repository
	 */
	public Repository getRepository() {
		return repository;
	}

	/**
	 * Get rev commit
	 *
	 * @return rev commit
	 */
	public RevCommit getRevCommit() {
		return commit;
	}

	/**
	 * Get file diffs
	 *
	 * @return non-null but possibly empty array of {@link FileDiff} instances.
	 */
	public FileDiff[] getDiffs() {
		if (diffs == null) {
			RevWalk revWalk = new RevWalk(repository);
			TreeWalk treewalk = new TreeWalk(revWalk.getObjectReader());
			treewalk.setRecursive(true);
			treewalk.setFilter(TreeFilter.ANY_DIFF);
			try {
				for (RevCommit parent : commit.getParents())
					if (parent.getTree() == null)
						revWalk.parseBody(parent);
				diffs = FileDiff.compute(treewalk, commit);
			} catch (IOException e) {
				diffs = new FileDiff[0];
			} finally {
				revWalk.release();
				treewalk.release();
			}
		}
		return diffs;
	}

	public Object[] getChildren(Object o) {
		return new Object[0];
	}

	public ImageDescriptor getImageDescriptor(Object object) {
		return UIIcons.CHANGESET;
	}

	public String getLabel(Object o) {
		return abbreviate();
	}

	public Object getParent(Object o) {
		return null;
	}

}
