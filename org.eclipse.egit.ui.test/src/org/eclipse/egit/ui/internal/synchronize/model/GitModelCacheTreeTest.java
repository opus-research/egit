/*******************************************************************************
 * Copyright (C) 2010, Dariusz Luksza <dariusz@luksza.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.synchronize.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.eclipse.core.runtime.IPath;
import org.eclipse.egit.core.synchronize.GitCommitsModelCache.Change;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.internal.synchronize.model.GitModelCache.FileModelFactory;
import org.eclipse.jgit.lib.Repository;
import org.junit.BeforeClass;
import org.junit.Test;

public class GitModelCacheTreeTest extends GitModelTestCase {

	@Test public void shouldReturnEqualForSameInstance() throws Exception {
		// given
		GitModelCacheTree left = crateCacheTree(getTreeLocation());

		// when
		boolean actual = left.equals(left);

		// then
		assertTrue(actual);
	}

	@Test public void shouldReturnEqualForSameData() throws Exception {
		// given
		GitModelCacheTree left = crateCacheTree(getTreeLocation());
		GitModelCacheTree right = crateCacheTree(getTreeLocation());

		// when
		boolean actual = left.equals(right);

		// then
		assertTrue(actual);
	}

	@Test public void shouldReturnNotEqualForDifferetnLocation()
			throws Exception {
		// given
		GitModelCacheTree left = crateCacheTree(getTreeLocation());
		GitModelCacheTree right = crateCacheTree(getTree1Location());

		// when
		boolean actual = left.equals(right);

		// then
		assertFalse(actual);
	}

	@Test public void shouldReturnNotEqualWhenComparingCacheTreeAndTree()
			throws Exception {
		// given
		GitModelCacheTree left = crateCacheTree(getTreeLocation());
		GitModelTree right = mock(GitModelTree.class);

		// when
		boolean actual = left.equals(right);

		// then
		assertFalse(actual);
	}

	@BeforeClass public static void setupEnvironment() throws Exception {
		leftRepoFile = createProjectAndCommitToRepository();

		Activator.getDefault().getRepositoryUtil()
				.addConfiguredRepository(leftRepoFile);
	}

	private GitModelCacheTree crateCacheTree(IPath location)
			throws Exception {
		return new GitModelCacheTree(createModelCommit(),
				lookupRepository(leftRepoFile), location, new FileModelFactory() {
					public boolean isWorkingTree() {
						return false;
					}
					public GitModelBlob createFileModel(GitModelObjectContainer objParent,
							Repository repo, Change change, IPath fullPath) {
						return null;
					}
				});
	}
}
