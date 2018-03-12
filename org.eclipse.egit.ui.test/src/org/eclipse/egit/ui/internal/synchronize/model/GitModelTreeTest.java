/*******************************************************************************
 * Copyright (C) 2011, Dariusz Luksza <dariusz@luksza.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.synchronize.model;

import static org.eclipse.compare.structuremergeviewer.Differencer.CHANGE;
import static org.eclipse.compare.structuremergeviewer.Differencer.RIGHT;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import org.eclipse.core.runtime.IPath;
import org.eclipse.egit.ui.Activator;
import org.junit.BeforeClass;
import org.junit.Test;

public class GitModelTreeTest extends GitModelTestCase {

	@Test public void shouldReturnEqualForSameInstance() throws Exception {
		// given
		GitModelTree left = createModelTree();

		// when
		boolean actual = left.equals(left);

		// then
		assertTrue(actual);
	}

	@Test public void shouldReturnEqualForSameBaseCommit() throws Exception {
		// given
		GitModelTree left = createModelTree(getTreeLocation());
		GitModelTree right = createModelTree(getTreeLocation());
		
		// when
		boolean actual = left.equals(right);
		
		// then
		assertTrue(actual);
	}

	@Test public void shouldReturnNotEqualForDifferentLocation()
			throws Exception {
		// given
		GitModelTree left = createModelTree(getTreeLocation());
		GitModelTree right = createModelTree(getTree1Location());

		// when
		boolean actual = left.equals(right);

		// then
		assertFalse(actual);
	}

	@Test public void shouldReturnNotEqualForTreeAndCommit()
			throws Exception {
		// given
		GitModelTree left = createModelTree(getTreeLocation());
		GitModelCommit right = mock(GitModelCommit.class);

		// when
		boolean actual = left.equals(right);

		// then
		assertFalse(actual);
	}

	@Test public void shouldReturnNotEqualForTreeAndBlob()
			throws Exception {
		// given
		GitModelTree left = createModelTree(getTreeLocation());
		GitModelBlob right = mock(GitModelBlob.class);

		// when
		boolean actual = left.equals(right);

		// then
		assertFalse(actual);
	}

	@Test public void shouldReturnNotEqualForTreeAndCacheTree()
			throws Exception {
		// given
		GitModelTree left = createModelTree(getTreeLocation());
		GitModelCacheTree right = mock(GitModelCacheTree.class);

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

	private GitModelTree createModelTree() throws Exception {
		return createModelTree(getTreeLocation());
	}

	private GitModelTree createModelTree(IPath location)
			throws Exception {
		return new GitModelTree(createModelCommit(), location, RIGHT | CHANGE);
	}

}
