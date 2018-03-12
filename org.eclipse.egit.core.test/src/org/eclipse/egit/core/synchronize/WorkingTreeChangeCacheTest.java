/*******************************************************************************
 * Copyright (C) 2011, Dariusz Luksza <dariusz@luksza.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core.synchronize;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.Map;

import org.eclipse.egit.core.synchronize.GitCommitsModelCache.Change;
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

	@Test
	public void shouldNotListIgnorefFile() throws Exception {
		// given
		writeTrashFile("a.txt", "content");
		writeTrashFile(".gitignore", "a.txt");

		// when
		Map<String, Change> result = WorkingTreeChangeCache.build(db);

		// then
		assertThat(result.size(), is(1));
		assertFileAddition(result, ".gitignore", ".gitignore");
	}

}
