/*******************************************************************************
 * Copyright (C) 2010, Dariusz Luksza <dariusz@luksza.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core.synchronize;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.InputStream;
import java.util.Arrays;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.egit.core.op.AddToIndexOperation;
import org.eclipse.egit.core.op.BranchOperation;
import org.eclipse.egit.core.op.ConnectProviderOperation;
import org.eclipse.egit.core.op.DisconnectProviderOperation;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.egit.core.synchronize.dto.GitSynchronizeData;
import org.eclipse.egit.core.synchronize.dto.GitSynchronizeDataSet;
import org.eclipse.egit.core.test.GitTestCase;
import org.eclipse.egit.core.test.TestProject;
import org.eclipse.egit.core.test.TestRepository;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.team.core.variants.IResourceVariant;
import org.eclipse.team.core.variants.ResourceVariantByteStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GitResourceVariantTreeTest extends GitTestCase {

	private Repository repo;

	private IProject iProject;

	private TestRepository testRepo;

	private ResourceVariantByteStore store;

	@Before
	public void createGitRepository() throws Exception {
		IProject iProject = project.project;
		if (!gitDir.exists())
			new FileRepository(gitDir).create();

		new ConnectProviderOperation(iProject, gitDir).execute(null);
		testRepo = new TestRepository(gitDir);
		testRepo.connect(iProject);
		repo = RepositoryMapping.getMapping(iProject).getRepository();
	}

	@After
	public void clearGitResources() throws Exception {
		testRepo.disconnect(iProject);
		testRepo.dispose();
		repo = null;
		super.tearDown();
	}

	/**
	 * roots() method should return list of projects that are associated with
	 * given repository. In this case there is only one project associated with
	 * this repository therefore only one root should be returned.
	 */
	@Test
	public void shouldReturnOneRoot() {
		// when
		GitSynchronizeData data = new GitSynchronizeData(repo, "", "", false);
		GitSynchronizeDataSet dataSet = new GitSynchronizeDataSet(data);

		// given
		GitResourceVariantTree grvt = new GitTestResourceVariantTree(dataSet,
				store);

		// then
		assertEquals(1, grvt.roots().length);
		IResource actualProject = grvt.roots()[0];
		assertEquals(this.project.getProject(), actualProject);
	}

	/**
	 * When we have two or more project associated with repository, roots()
	 * method should return list of project. In this case we have two project
	 * associated with particular repository, therefore '2' value is expected.
	 *
	 * @throws Exception
	 */
	@Test
	public void shouldReturnTwoRoots() throws Exception {
		// when
		// create second project
		TestProject secondProject = new TestProject(true, "Project-2");
		IProject secondIProject = secondProject.project;
		// add connect project with repository
		new ConnectProviderOperation(secondIProject, gitDir).execute(null);
		GitSynchronizeData data = new GitSynchronizeData(repo, "", "", false);
		GitSynchronizeDataSet dataSet = new GitSynchronizeDataSet(data);

		// given
		GitResourceVariantTree grvt = new GitTestResourceVariantTree(dataSet,
				store);

		// then
		assertEquals(2, grvt.roots().length);
		IResource actualProject = grvt.roots()[1];
		assertEquals(this.project.project, actualProject);
		IResource actualProject1 = grvt.roots()[0];
		assertEquals(secondIProject, actualProject1);
	}

	/**
	 * Checks that getResourceVariant will not throw NPE for null argument. This
	 * method is called with null argument when local or remote resource does
	 * not exist.
	 *
	 * @throws Exception
	 */
	@Test
	public void shouldReturnNullResourceVariant() throws Exception {
		// when
		GitSynchronizeData data = new GitSynchronizeData(repo, Constants.HEAD,
				Constants.MASTER, false);
		GitSynchronizeDataSet dataSet = new GitSynchronizeDataSet(data);

		// given
		GitResourceVariantTree grvt = new GitRemoteResourceVariantTree(dataSet);

		// then
		assertNull(grvt.getResourceVariant(null));
	}

	/**
	 * getResourceVariant() should return null when given resource doesn't exist
	 * in repository.
	 *
	 * @throws Exception
	 */
	@Test
	public void shouldReturnNullResourceVariant2() throws Exception {
		// when
		IPackageFragment iPackage = project.createPackage("org.egit.test");
		IType mainJava = project.createType(iPackage, "Main.java",
				"class Main {}");
		GitSynchronizeData data = new GitSynchronizeData(repo, Constants.HEAD,
				Constants.MASTER, false);
		GitSynchronizeDataSet dataSet = new GitSynchronizeDataSet(data);

		// given
		GitResourceVariantTree grvt = new GitRemoteResourceVariantTree(dataSet);

		// then
		assertNull(grvt.getResourceVariant(mainJava.getResource()));
	}

	/**
	 * Check if getResourceVariant() does return the same resource that was
	 * committed. Passes only when it is run as a single test, not as a part of
	 * largest test suite
	 *
	 * @throws Exception
	 */
	@Test
	public void shoulReturnSameResourceVariant() throws Exception {
		// when
		String fileName = "Main.java";
		File file = testRepo.createFile(iProject, fileName);
		testRepo.appendContentAndCommit(iProject, file, "class Main {}",
				"initial commit");
		IFile mainJava = testRepo.getIFile(iProject, file);
		GitSynchronizeData data = new GitSynchronizeData(repo, Constants.HEAD,
				Constants.MASTER, false);
		GitSynchronizeDataSet dataSet = new GitSynchronizeDataSet(data);

		// given
		GitResourceVariantTree grvt = new GitRemoteResourceVariantTree(dataSet);

		// then
		IResourceVariant actual = grvt.getResourceVariant(mainJava);
		assertNotNull(actual);
		assertEquals(fileName, actual.getName());

		InputStream actualIn = actual.getStorage(new NullProgressMonitor())
				.getContents();
		byte[] actualByte = getBytesAndCloseStream(actualIn);
		InputStream expectedIn = mainJava.getContents();
		byte[] expectedByte = getBytesAndCloseStream(expectedIn);
		assertArrayEquals(expectedByte, actualByte);
	}

	/**
	 * Create and commit Main.java file in master branch, then create branch
	 * "test" checkout nearly created branch and modify Main.java file.
	 * getResourceVariant() should obtain Main.java file content from "master"
	 * branch. Passes only when it is run as a single test, not as a part of
	 * largest test suite
	 *
	 * @throws Exception
	 */
	@Test
	public void shouldReturnDifferentResourceVariant() throws Exception {
		// when
		String fileName = "Main.java";
		File file = testRepo.createFile(iProject, fileName);
		testRepo.appendContentAndCommit(iProject, file, "class Main {}",
				"initial commit");
		IFile mainJava = testRepo.getIFile(iProject, file);

		testRepo.createAndCheckoutBranch(Constants.R_HEADS + Constants.MASTER, Constants.R_HEADS + "test");
		testRepo.appendContentAndCommit(iProject, file, "// test", "first commit");
		GitSynchronizeData data = new GitSynchronizeData(repo, Constants.HEAD,
				Constants.MASTER, false);
		GitSynchronizeDataSet dataSet = new GitSynchronizeDataSet(data);

		// given
		GitResourceVariantTree grvt = new GitRemoteResourceVariantTree(dataSet);

		// then
		IResourceVariant actual = grvt.getResourceVariant(mainJava);
		assertNotNull(actual);
		assertEquals(fileName, actual.getName());

		InputStream actualIn = actual.getStorage(new NullProgressMonitor())
		.getContents();
		byte[] actualByte = getBytesAndCloseStream(actualIn);
		InputStream expectedIn = mainJava.getContents();
		byte[] expectedByte = getBytesAndCloseStream(expectedIn);

		// assert arrays not equals
		if (Arrays.equals(expectedByte, actualByte))
			fail();
		else
			assertTrue(true);
	}

	private IType createResourceAndCommit(String packageName, String fileName,
			String fileContent, String commitMsg) throws Exception {
		IPackageFragment iPackage = project.createPackage(packageName);
		return createResourceAndCommit(iPackage, fileName, fileContent,
				commitMsg);
	}

	private IType createResourceAndCommit(IPackageFragment iPackage,
			String fileName, String fileContent, String commitMsg)
			throws Exception {
		IType mainJava = project.createType(iPackage, fileName, fileContent);
		addAndCommitResource(mainJava, commitMsg);

		return mainJava;
	}

	private void addAndCommitResource(IType mainJava, String commitMsg)
			throws Exception {
		List<IResource> resources = new ArrayList<IResource>();
		resources.add(mainJava.getResource());
		new AddToIndexOperation(resources).execute(null); // add resource to git
		new Git(repo).commit().setMessage(commitMsg).call(); // make commit
	}

	private void createBranch(String branchName) throws Exception {
		RefUpdate updateRef;
		updateRef = repo.updateRef(Constants.R_HEADS + branchName);
		Ref startRef = repo.getRef(branchName);
		ObjectId startAt = repo.resolve(Constants.HEAD);
		String startBranch;
		if (startRef != null)
			startBranch = branchName;
		else
			startBranch = startAt.name();
		startBranch = repo.shortenRefName(startBranch);
		updateRef.setNewObjectId(startAt);
		updateRef
				.setRefLogMessage("branch: Created from " + startBranch, false); //$NON-NLS-1$
		updateRef.update();

	private byte[] getBytesAndCloseStream(InputStream stream) throws Exception {
		try {
			byte[] actualByte = new byte[stream.available()];
			stream.read(actualByte);
			return actualByte;
		} finally {
			stream.close();
		}
	}
}
