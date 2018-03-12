/*******************************************************************************
 * Copyright (C) 2011, Dariusz Luksza <dariusz@luksza.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core.synchronize;

import static org.eclipse.compare.structuremergeviewer.Differencer.ADDITION;
import static org.eclipse.compare.structuremergeviewer.Differencer.CHANGE;
import static org.eclipse.compare.structuremergeviewer.Differencer.DELETION;
import static org.eclipse.compare.structuremergeviewer.Differencer.LEFT;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.eclipse.egit.core.synchronize.CheckedInCommitsCache.Change;
import org.eclipse.jgit.api.Git;
import org.junit.Test;

@SuppressWarnings("boxing")
public class WorkingTreeChangeCacheTest extends AbstractCacheTest {

	@Test
	public void shouldListSingleWorkspaceAddition() throws Exception {
		// given
		writeTrashFile("a.txt", "trash");

		// when
		Map<String, Change> result = WorkingTreeChangeCache.build(db);

		// then
		assertThat(result.size(), is(1));
		assertFileAddition(result, "a.txt", "a.txt");
	}

	@Test
	public void shouldListTwoWorkspaceAdditions() throws Exception {
		// given
		writeTrashFile("a.txt", "trash");
		writeTrashFile("b.txt", "trash");

		// when
		Map<String, Change> result = WorkingTreeChangeCache.build(db);

		// then
		assertThat(result.size(), is(2));
		assertFileAddition(result, "a.txt", "a.txt");
		assertFileAddition(result, "b.txt", "b.txt");
	}

	@Test
	public void shouldListSingleWorkspaceAdditionInFolder() throws Exception {
		// given
		writeTrashFile("folder/a.txt", "trash");

		// when
		Map<String, Change> result = WorkingTreeChangeCache.build(db);

		// then
		assertThat(result.size(), is(1));
		assertFileAddition(result, "folder/a.txt", "a.txt");
	}

	@Test
	public void shouldListTwoWorkspaceAdditionsInFolder() throws Exception {
		// given
		writeTrashFile("folder/a.txt", "trash");
		writeTrashFile("folder/b.txt", "trash");

		// when
		Map<String, Change> result = WorkingTreeChangeCache.build(db);

		// then
		assertThat(result.size(), is(2));
		assertFileAddition(result, "folder/a.txt", "a.txt");
		assertFileAddition(result, "folder/b.txt", "b.txt");
	}

	@Test
	public void shouldListSingleWorkspaceDeletion() throws Exception {
		// given
		writeTrashFile("a.txt", "trash");
		new Git(db).add().addFilepattern("a.txt").call();
		deleteTrashFile("a.txt");

		// when
		Map<String, Change> result = WorkingTreeChangeCache.build(db);

		// then
		assertThat(result.size(), is(1));
		assertFileDeletion(result, "a.txt", "a.txt");
	}

	@Test
	public void shouldListTwoWorkspaceDeletions() throws Exception {
		// given
		writeTrashFile("a.txt", "trash");
		writeTrashFile("b.txt", "trash");
		new Git(db).add().addFilepattern("a.txt").addFilepattern("b.txt").call();
		deleteTrashFile("a.txt");
		deleteTrashFile("b.txt");

		// when
		Map<String, Change> result = WorkingTreeChangeCache.build(db);

		// then
		assertThat(result.size(), is(2));
		assertFileDeletion(result, "a.txt", "a.txt");
		assertFileDeletion(result, "b.txt", "b.txt");
	}

	@Test
	public void shouldListSingleWorkspaceDeletionInFolder() throws Exception {
		// given
		writeTrashFile("folder/a.txt", "trash");
		new Git(db).add().addFilepattern("folder/a.txt").call();
		deleteTrashFile("folder/a.txt");

		// when
		Map<String, Change> result = WorkingTreeChangeCache.build(db);

		// then
		assertThat(result.size(), is(1));
		assertFileDeletion(result, "folder/a.txt", "a.txt");
	}

	@Test
	public void shouldListTwoWorkspaceDeletionsInFolder() throws Exception {
		// given
		writeTrashFile("folder/a.txt", "trash");
		writeTrashFile("folder/b.txt", "trash");
		new Git(db).add().addFilepattern("folder/a.txt").addFilepattern("folder/b.txt").call();
		deleteTrashFile("folder/a.txt");
		deleteTrashFile("folder/b.txt");

		// when
		Map<String, Change> result = WorkingTreeChangeCache.build(db);

		// then
		assertThat(result.size(), is(2));
		assertFileDeletion(result, "folder/a.txt", "a.txt");
		assertFileDeletion(result, "folder/b.txt", "b.txt");
	}

	@Test
	public void shouldListSingleWorkspaceChange() throws Exception {
		// given
		writeTrashFile("a.txt", "trash");
		new Git(db).add().addFilepattern("a.txt").call();
		writeTrashFile("a.txt", "modification");

		// when
		Map<String, Change> result = WorkingTreeChangeCache.build(db);

		// then
		assertThat(result.size(), is(1));
		assertFileChange(result, "a.txt", "a.txt");
	}

	@Test
	public void shouldListTwoWorkspaceChanges() throws Exception {
		// given
		writeTrashFile("a.txt", "trash");
		writeTrashFile("b.txt", "trash");
		new Git(db).add().addFilepattern("a.txt").addFilepattern("b.txt").call();
		writeTrashFile("a.txt", "modification");
		writeTrashFile("b.txt", "modification");

		// when
		Map<String, Change> result = WorkingTreeChangeCache.build(db);

		// then
		assertThat(result.size(), is(2));
		assertFileChange(result, "a.txt", "a.txt");
		assertFileChange(result, "b.txt", "b.txt");
	}

	@Test
	public void shouldListSingleWorkspaceChangeInFolder() throws Exception {
		// given
		writeTrashFile("folder/a.txt", "trash");
		new Git(db).add().addFilepattern("folder/a.txt").call();
		writeTrashFile("folder/a.txt", "modification");

		// when
		Map<String, Change> result = WorkingTreeChangeCache.build(db);

		// then
		assertThat(result.size(), is(1));
		assertFileChange(result, "folder/a.txt", "a.txt");
	}

	@Test
	public void shouldListTwoWorkspaceChagneInFolder() throws Exception {
		// given
		writeTrashFile("folder/a.txt", "trash");
		writeTrashFile("folder/b.txt", "trash");
		new Git(db).add().addFilepattern("folder/a.txt").addFilepattern("folder/b.txt").call();
		writeTrashFile("folder/a.txt", "modification");
		writeTrashFile("folder/b.txt", "modification");

		// when
		Map<String, Change> result = WorkingTreeChangeCache.build(db);

		// then
		assertThat(result.size(), is(2));
		assertFileChange(result, "folder/a.txt", "a.txt");
		assertFileChange(result, "folder/b.txt", "b.txt");
	}

	private void assertFileAddition(Map<String, Change> result, String path, String fileName) {
		commonFileAsserts(result, path, fileName);
		assertThat(result.get(path).getKind(), is(LEFT | ADDITION));
		assertThat(result.get(path).getObjectId(), not(ZERO_ID));
		assertNull(result.get(path).getRemoteObjectId());
	}

	private void assertFileDeletion(Map<String, Change> result, String path, String fileName) {
		commonFileAsserts(result, path, fileName);
		assertThat(result.get(path).getKind(), is(LEFT | DELETION));
		assertThat(result.get(path).getRemoteObjectId(), not(ZERO_ID));
		assertNull(result.get(path).getObjectId());
	}

	private void assertFileChange(Map<String, Change> result, String path, String fileName) {
		commonFileAsserts(result, path, fileName);
		assertThat(result.get(path).getKind(), is(LEFT | CHANGE));
		assertThat(result.get(path).getObjectId(), not(ZERO_ID));
		assertThat(result.get(path).getRemoteObjectId(), not(ZERO_ID));
	}

	private void commonFileAsserts(Map<String, Change> result, String path,
			String fileName) {
		assertTrue(result.containsKey(path));
		assertThat(result.get(path).getName(), is(fileName));
	}

}
