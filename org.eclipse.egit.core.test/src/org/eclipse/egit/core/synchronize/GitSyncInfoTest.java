/*******************************************************************************
 * Copyright (C) 2010, Dariusz Luksza <dariusz@luksza.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core.synchronize;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.eclipse.team.core.synchronize.SyncInfo.ADDITION;
import static org.eclipse.team.core.synchronize.SyncInfo.CHANGE;
import static org.eclipse.team.core.synchronize.SyncInfo.CONFLICTING;
import static org.eclipse.team.core.synchronize.SyncInfo.DELETION;
import static org.eclipse.team.core.synchronize.SyncInfo.INCOMING;
import static org.eclipse.team.core.synchronize.SyncInfo.IN_SYNC;
import static org.eclipse.team.core.synchronize.SyncInfo.OUTGOING;
import static org.eclipse.team.core.synchronize.SyncInfo.PSEUDO_CONFLICT;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.Arrays;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IPath;
import org.eclipse.egit.core.op.ConnectProviderOperation;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.egit.core.synchronize.dto.GitSynchronizeData;
import org.eclipse.egit.core.synchronize.dto.GitSynchronizeDataSet;
import org.eclipse.egit.core.test.GitTestCase;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.GitIndex;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.GitIndex.Entry;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevCommitList;
import org.junit.Before;
import org.junit.Test;

public class GitSyncInfoTest extends GitTestCase {

	private Repository repo;

	private GitResourceVariantComparator comparator;

	@Before
	public void setUp() throws Exception {
		super.setUp();
		IProject iProject = project.project;
		if (!gitDir.exists())
			new Repository(gitDir).create();

		new ConnectProviderOperation(iProject, gitDir).execute(null);
		repo = RepositoryMapping.getMapping(iProject).getRepository();

		GitSynchronizeData data = new GitSynchronizeData(repo, "", "", true);
		GitSynchronizeDataSet dataSet = new GitSynchronizeDataSet(data);
		comparator = new GitResourceVariantComparator(dataSet, null);
	}

	@Test
	@SuppressWarnings("boxing")
	// files are in sync because local, base and remote has the same ObjectId
	public void shouldReturnResourceFileInSync() throws Exception {
		// when

		// given
		String fileName = "test-file";
		byte[] localBytes = new byte[8200];
		Arrays.fill(localBytes, (byte) 'a');

		IFile local = createMock(IFile.class);
		expect(local.isDerived()).andReturn(false);
		expect(local.exists()).andReturn(true).times(2);
		expect(local.getName()).andReturn(fileName).anyTimes();
		expect(local.getProject()).andReturn(project.getProject());
		expect(local.getContents()).andReturn(
				new ByteArrayInputStream(localBytes));
		replay(local);

		ObjectId objectId = stageAndCommit(fileName, localBytes);

		IFile baseResource = createMock(IFile.class);
		replay(baseResource);
		GitBlobResourceVariant base = new GitBlobResourceVariant(baseResource,
				repo, objectId, null);

		IResource remoteResource = createMock(IResource.class);
		replay(remoteResource);
		GitBlobResourceVariant remote = new GitBlobResourceVariant(
				remoteResource, repo, objectId, null);

		GitSyncInfo gsi = new GitSyncInfo(local, base, remote, comparator);
		gsi.init();

		// then
		assertEquals(IN_SYNC, gsi.getKind());
		verify(local, baseResource, remoteResource);
	}

	@Test
	@SuppressWarnings("boxing")
	// folder are in sync because they are using same mocked instance of
	// IResource
	public void shouldReturnResourceFolderInSync() throws Exception {
		// when

		// given
		IResource local = createMock(IResource.class);
		expect(local.isDerived()).andReturn(false);
		expect(local.exists()).andReturn(true).times(3);
		expect(local.getName()).andReturn("Mian.java").anyTimes();
		replay(local);

		GitFolderResourceVariant base = new GitFolderResourceVariant(local);
		GitFolderResourceVariant remote = new GitFolderResourceVariant(local);

		GitSyncInfo gsi = new GitSyncInfo(local, base, remote, comparator);
		gsi.init();

		// then
		assertEquals(IN_SYNC, gsi.getKind());
		verify(local);
	}

	@Test
	@SuppressWarnings("boxing")
	// should return outgoing change because local RevCommitList contains more
	// commit RevCommit elements
	public void shouldReturnOutgoingFileChange() throws Exception {
		// when

		// given
		String fileName = "test-file";
		byte[] localBytes = new byte[8200];
		Arrays.fill(localBytes, (byte) 'a');

		IFile local = createMock(IFile.class);
		expect(local.isDerived()).andReturn(false);
		expect(local.exists()).andReturn(true).times(2);
		expect(local.getName()).andReturn(fileName).anyTimes();
		expect(local.getProject()).andReturn(project.getProject());
		expect(local.getContents()).andReturn(
				new ByteArrayInputStream(localBytes));
		replay(local);

		stage(fileName, localBytes);
		RevCommit firstCommit = commit();
		localBytes[8120] = 'b';
		ObjectId objectId = stage(fileName, localBytes);
		RevCommit secondCommit = commit();
		RevCommitList<RevCommit> baseCommits = new RevCommitList<RevCommit>();
		baseCommits.add(firstCommit);
		baseCommits.add(secondCommit);

		IFile baseResource = createMock(IFile.class);
		replay(baseResource);
		GitBlobResourceVariant base = new GitBlobResourceVariant(baseResource,
				repo, objectId, baseCommits); // baseComits has two entry

		IResource remoteResource = createMock(IResource.class);
		replay(remoteResource);
		RevCommitList<RevCommit> remoteCommits = new RevCommitList<RevCommit>();
		remoteCommits.add(firstCommit);
		GitBlobResourceVariant remote = new GitBlobResourceVariant(
				remoteResource,
				repo,
				ObjectId.fromString("0123456789012345678901234567890123456789"),
				remoteCommits); // remoteCommits has only one entry

		GitSyncInfo gsi = new GitSyncInfo(local, base, remote, comparator);
		gsi.init();

		// then
		assertEquals(OUTGOING | CHANGE, gsi.getKind());
		verify(local, baseResource, remoteResource);
	}

	@Test
	@SuppressWarnings("boxing")
	// remote RevCommitList is null, because that we have here outgoing change
	public void shouldReturnOutgoingFileChange1() throws Exception {
		// when

		// given
		String fileName = "test-file";
		byte[] localBytes = new byte[8200];
		Arrays.fill(localBytes, (byte) 'a');

		IFile local = createMock(IFile.class);
		expect(local.isDerived()).andReturn(false);
		expect(local.exists()).andReturn(true).times(2);
		expect(local.getName()).andReturn(fileName).anyTimes();
		expect(local.getProject()).andReturn(project.getProject());
		expect(local.getContents()).andReturn(
				new ByteArrayInputStream(localBytes));
		replay(local);

		stage(fileName, localBytes);
		RevCommit firstCommit = commit();
		byte[] remoteBytes = Arrays.copyOf(localBytes, localBytes.length);
		remoteBytes[8100] = 'b';
		ObjectId objectId = stage(fileName, remoteBytes);
		RevCommit secondCommit = commit();
		RevCommitList<RevCommit> baseCommits = new RevCommitList<RevCommit>();
		baseCommits.add(firstCommit);
		baseCommits.add(secondCommit);

		IFile baseResource = createMock(IFile.class);
		replay(baseResource);
		GitBlobResourceVariant base = new GitBlobResourceVariant(baseResource,
				repo, objectId, baseCommits);

		IResource remoteResource = createMock(IResource.class);
		replay(remoteResource);

		GitBlobResourceVariant remote = new GitBlobResourceVariant(
				remoteResource,
				repo,
				ObjectId.fromString("0123456789012345678901234567890123456789"),
				null); // RevCommitList is null

		GitSyncInfo gsi = new GitSyncInfo(local, base, remote, comparator);
		gsi.init();

		// then
		assertEquals(OUTGOING | CHANGE, gsi.getKind());
		verify(local, baseResource, remoteResource);
	}

	@Test
	@SuppressWarnings("boxing")
	// should return incoming change because remote RevCommitList has more
	// RevCommits elements than local one
	public void shouldReturnIncomingFileChange() throws Exception {
		// when

		// given
		String fileName = "test-file";
		byte[] localBytes = new byte[8200];
		Arrays.fill(localBytes, (byte) 'a');

		IFile local = createMock(IFile.class);
		expect(local.isDerived()).andReturn(false);
		expect(local.exists()).andReturn(true).times(2);
		expect(local.getName()).andReturn(fileName).anyTimes();
		expect(local.getProject()).andReturn(project.getProject());
		expect(local.getContents()).andReturn(
				new ByteArrayInputStream(localBytes));
		replay(local);

		ObjectId objectId = stage(fileName, localBytes);
		RevCommit firstCommit = commit();
		RevCommitList<RevCommit> baseCommits = new RevCommitList<RevCommit>();
		baseCommits.add(firstCommit);

		IFile baseResource = createMock(IFile.class);
		replay(baseResource);
		GitBlobResourceVariant base = new GitBlobResourceVariant(baseResource,
				repo, objectId, baseCommits); // baseCommits has one element

		IResource remoteResource = createMock(IResource.class);
		replay(remoteResource);

		stage(fileName, localBytes);
		RevCommit secondCommit = commit();
		RevCommitList<RevCommit> remoteCommits = new RevCommitList<RevCommit>();
		remoteCommits.add(secondCommit);
		remoteCommits.add(firstCommit);
		GitBlobResourceVariant remote = new GitBlobResourceVariant(
				remoteResource,
				repo,
				ObjectId.fromString("0123456789012345678901234567890123456789"),
				remoteCommits); // remoteCommits has two elements

		GitSyncInfo gsi = new GitSyncInfo(local, base, remote, comparator);
		gsi.init();

		// then
		assertEquals(INCOMING | CHANGE, gsi.getKind());
		verify(local, baseResource, remoteResource);
	}

	@Test
	@SuppressWarnings("boxing")
	// remote and base resource exist but local not, therefore it is outgoing
	// deletion
	public void shouldReturnOutgoingDeletion() throws Exception {
		// when

		// given
		IResource local = createMock(IResource.class);
		expect(local.isDerived()).andReturn(false);
		expect(local.exists()).andReturn(false);
		expect(local.getName()).andReturn("Mian.java").anyTimes();
		replay(local);

		IPath path = createMock(IPath.class);
		replay(path);

		IResource baseResource = createMock(IResource.class);
		expect(baseResource.exists()).andReturn(true);
		expect(baseResource.getFullPath()).andReturn(path);
		replay(baseResource);
		GitFolderResourceVariant base = new GitFolderResourceVariant(
				baseResource);

		IResource remoteResource = createMock(IResource.class);
		expect(remoteResource.exists()).andReturn(true);
		expect(remoteResource.getFullPath()).andReturn(path);
		replay(remoteResource);
		GitFolderResourceVariant remote = new GitFolderResourceVariant(
				remoteResource);

		GitSyncInfo gsi = new GitSyncInfo(local, base, remote, comparator);
		gsi.init();

		// then
		assertEquals(OUTGOING | DELETION, gsi.getKind());
		verify(local, baseResource, remoteResource, path);
	}

	@Test
	@SuppressWarnings("boxing")
	// remote variant is null, therefore it is incoming deletion
	public void shouldReturnIncomingFileDeletion() throws Exception {
		// when

		// given
		String fileName = "test-file";
		byte[] localBytes = new byte[8200];
		Arrays.fill(localBytes, (byte) 'a');

		IFile local = createMock(IFile.class);
		expect(local.isDerived()).andReturn(false);
		expect(local.exists()).andReturn(true).times(2);
		expect(local.getName()).andReturn(fileName).anyTimes();
		expect(local.getProject()).andReturn(project.getProject());
		expect(local.getContents()).andReturn(
				new ByteArrayInputStream(localBytes));
		replay(local);

		stage(fileName, localBytes);
		RevCommit firstCommit = commit();
		ObjectId objectId = stage(fileName, localBytes);
		RevCommit secondCommit = commit();
		RevCommitList<RevCommit> baseCommits = new RevCommitList<RevCommit>();
		baseCommits.add(firstCommit);
		baseCommits.add(secondCommit);

		IFile baseResource = createMock(IFile.class);
		replay(baseResource);
		GitBlobResourceVariant base = new GitBlobResourceVariant(baseResource,
				repo, objectId, baseCommits);

		GitSyncInfo gsi = new GitSyncInfo(local, base, null, comparator);
		gsi.init();

		// then
		assertEquals(INCOMING | DELETION, gsi.getKind());
		verify(local, baseResource);
	}

	@Test
	@SuppressWarnings("boxing")
	// local resource does not exist in base and remote, therefore it is
	// outgoing addition
	public void shouldReturnOutgoingAddition() throws Exception {
		// when

		// given
		IResource baseResource = createMock(IResource.class);
		expect(baseResource.exists()).andReturn(true).times(2);
		expect(baseResource.isDerived()).andReturn(false);
		expect(baseResource.getName()).andReturn("Mian.java").anyTimes();
		replay(baseResource);
		GitSyncInfo gsi = new GitSyncInfo(baseResource, null, null, comparator);
		gsi.init();

		// then
		assertEquals(OUTGOING | ADDITION, gsi.getKind());
		verify(baseResource);
	}

	@Test
	@SuppressWarnings("boxing")
	// conflicting change, because base and remote RevCommitList has same size
	// but different RevCommit objects
	public void shouldReturnConflictingFileChange() throws Exception {
		// when

		// given
		String fileName = "test-file";
		byte[] localBytes = new byte[8200];
		Arrays.fill(localBytes, (byte) 'a');

		IFile local = createMock(IFile.class);
		expect(local.isDerived()).andReturn(false);
		expect(local.exists()).andReturn(true).times(2);
		expect(local.getName()).andReturn(fileName).anyTimes();
		expect(local.getProject()).andReturn(project.getProject());
		expect(local.getContents()).andReturn(
				new ByteArrayInputStream(localBytes));
		replay(local);

		ObjectId objectId = stage(fileName, localBytes);
		RevCommit firstCommit = commit();
		RevCommitList<RevCommit> baseCommits = new RevCommitList<RevCommit>();
		baseCommits.add(firstCommit);

		IFile baseResource = createMock(IFile.class);
		replay(baseResource);
		GitBlobResourceVariant base = new GitBlobResourceVariant(baseResource,
				repo, objectId, baseCommits);

		IResource remoteResource = createMock(IResource.class);
		replay(remoteResource);

		stage(fileName, localBytes);
		RevCommit secondCommit = commit();
		RevCommitList<RevCommit> remoteCommits = new RevCommitList<RevCommit>();
		remoteCommits.add(secondCommit);
		GitBlobResourceVariant remote = new GitBlobResourceVariant(
				remoteResource,
				repo,
				ObjectId.fromString("0123456789012345678901234567890123456789"),
				remoteCommits);

		GitSyncInfo gsi = new GitSyncInfo(local, base, remote, comparator);
		gsi.init();

		// then
		assertEquals(CONFLICTING | CHANGE, gsi.getKind());
		verify(local, baseResource, remoteResource);
	}

	@Test
	@SuppressWarnings("boxing")
	// remote variant is folder, base and local are file's, therefore this is
	// conflicting change
	public void shouldReturnConflictingFileChange1() throws Exception {
		// when

		// given
		IFile local = createMock(IFile.class);
		expect(local.isDerived()).andReturn(false);
		expect(local.exists()).andReturn(true).times(1);
		expect(local.getName()).andReturn("test-file").anyTimes();
		replay(local);

		IFile baseResource = createMock(IFile.class);
		replay(baseResource);
		GitBlobResourceVariant base = new GitBlobResourceVariant(baseResource,
				repo, null, null);
		IResource remoteResource = createMock(IResource.class);
		replay(remoteResource);
		GitFolderResourceVariant remote = new GitFolderResourceVariant(
				remoteResource);

		GitSyncInfo gsi = new GitSyncInfo(local, base, remote, comparator);
		gsi.init();

		// then
		assertEquals(CONFLICTING | CHANGE, gsi.getKind());
		verify(local, baseResource, remoteResource);
	}

	@Test
	@SuppressWarnings("boxing")
	// remote variant does not exist (it is null), base variant is different
	// then local; therefore this is conflicting change
	public void shouldReturnConflictingFileChange2() throws Exception {
		// when

		// given
		String fileName = "test-file";
		byte[] localBytes = new byte[8200];
		Arrays.fill(localBytes, (byte) 'a');

		IFile local = createMock(IFile.class);
		expect(local.isDerived()).andReturn(false);
		expect(local.exists()).andReturn(true).times(2);
		expect(local.getName()).andReturn(fileName).anyTimes();
		expect(local.getProject()).andReturn(project.getProject());
		expect(local.getContents()).andReturn(
				new ByteArrayInputStream(localBytes));
		replay(local);

		stage(fileName, localBytes);
		RevCommit firstCommit = commit();
		byte[] remoteBytes = Arrays.copyOf(localBytes, localBytes.length);
		remoteBytes[8100] = 'b';
		ObjectId objectId = stage(fileName, remoteBytes);
		RevCommit secondCommit = commit();
		RevCommitList<RevCommit> baseCommits = new RevCommitList<RevCommit>();
		baseCommits.add(firstCommit);
		baseCommits.add(secondCommit);

		IFile baseResource = createMock(IFile.class);
		replay(baseResource);
		GitBlobResourceVariant base = new GitBlobResourceVariant(baseResource,
				repo, objectId, baseCommits);

		GitSyncInfo gsi = new GitSyncInfo(local, base, null, comparator);
		gsi.init();

		// then
		assertEquals(CONFLICTING | CHANGE, gsi.getKind());
		verify(local, baseResource);
	}

	@Test
	@SuppressWarnings("boxing")
	// folder does not exists in base, but it is existing in remote and locally,
	// therefore this is conflicting change
	public void shouldReturnConflictingFolderChange() throws Exception {
		// when

		// given
		IResource local = createMock(IResource.class);
		expect(local.exists()).andReturn(true);
		expect(local.isDerived()).andReturn(false);
		expect(local.getName()).andReturn("Mian.java").anyTimes();
		replay(local);

		IResource baseResource = createMock(IResource.class);
		expect(baseResource.exists()).andReturn(false);
		replay(baseResource);
		GitFolderResourceVariant base = new GitFolderResourceVariant(
				baseResource);

		IResource remoteResource = createMock(IResource.class);
		expect(remoteResource.exists()).andReturn(true);
		replay(remoteResource);
		GitFolderResourceVariant remote = new GitFolderResourceVariant(
				remoteResource);
		GitSyncInfo gsi = new GitSyncInfo(local, base, remote, comparator);
		gsi.init();

		// then
		assertEquals(CONFLICTING | CHANGE, gsi.getKind());
		verify(local, baseResource, remoteResource);
	}

	@Test
	@SuppressWarnings("boxing")
	// local and remote folder does not exist but it existing in base
	public void shouldReturnConflictingFolderChange1() throws Exception {
		// when

		// given
		IResource local = createMock(IResource.class);
		expect(local.exists()).andReturn(false);
		expect(local.isDerived()).andReturn(false);
		expect(local.getName()).andReturn("Mian.java").anyTimes();
		replay(local);

		IResource baseResource = createMock(IResource.class);
		expect(baseResource.exists()).andReturn(true);
		replay(baseResource);
		GitFolderResourceVariant base = new GitFolderResourceVariant(
				baseResource);

		IResource remoteResource = createMock(IResource.class);
		expect(remoteResource.exists()).andReturn(false);
		replay(remoteResource);
		GitFolderResourceVariant remote = new GitFolderResourceVariant(
				remoteResource);
		GitSyncInfo gsi = new GitSyncInfo(local, base, remote, comparator);
		gsi.init();

		// then
		assertEquals(CONFLICTING | CHANGE, gsi.getKind());
		verify(local, baseResource, remoteResource);
	}

	@Test
	@SuppressWarnings("boxing")
	// remote is a file, base is a folder -> conflicting change
	public void shouldReturnConflictingFolderAndFileChange() throws Exception {
		// when

		// given
		IResource local = createMock(IResource.class);
		expect(local.exists()).andReturn(false);
		expect(local.isDerived()).andReturn(false);
		expect(local.getName()).andReturn("Mian.java").anyTimes();
		replay(local);

		IResource baseResource = createMock(IResource.class);
		expect(baseResource.exists()).andReturn(true);
		replay(baseResource);
		GitFolderResourceVariant base = new GitFolderResourceVariant(
				baseResource);

		IResource remoteResource = createMock(IResource.class);
		expect(remoteResource.exists()).andReturn(true);
		replay(remoteResource);
		GitBlobResourceVariant remote = new GitBlobResourceVariant(
				remoteResource, repo, ObjectId.zeroId(), null);
		GitSyncInfo gsi = new GitSyncInfo(local, base, remote, comparator);
		gsi.init();

		// then
		assertEquals(CONFLICTING | CHANGE, gsi.getKind());
		verify(local, baseResource, remoteResource);
	}

	@Test
	@SuppressWarnings("boxing")
	// remote is a folder, base is a file -> conflicting change
	public void shouldReturnConflictingFileAndFolderChange() throws Exception {
		// when

		// given
		IResource local = createMock(IResource.class);
		expect(local.exists()).andReturn(false);
		expect(local.isDerived()).andReturn(false);
		expect(local.getName()).andReturn("Mian.java").anyTimes();
		replay(local);

		IResource baseResource = createMock(IResource.class);
		expect(baseResource.exists()).andReturn(true);
		replay(baseResource);
		GitBlobResourceVariant base = new GitBlobResourceVariant(baseResource,
				repo, ObjectId.zeroId(), null);

		IResource remoteResource = createMock(IResource.class);
		expect(remoteResource.exists()).andReturn(true);
		replay(remoteResource);
		GitFolderResourceVariant remote = new GitFolderResourceVariant(
				remoteResource);
		GitSyncInfo gsi = new GitSyncInfo(local, base, remote, comparator);
		gsi.init();

		// then
		assertEquals(CONFLICTING | CHANGE, gsi.getKind());
		verify(local, baseResource, remoteResource);
	}

	@Test
	@SuppressWarnings("boxing")
	// remote is null, local file does not exist
	public void shouldReturnConflictingDeletationPseudoConflict()
			throws Exception {
		// when

		// given
		IResource local = createMock(IResource.class);
		expect(local.exists()).andReturn(false);
		expect(local.isDerived()).andReturn(false);
		expect(local.getName()).andReturn("Mian.java").anyTimes();
		replay(local);

		IResource baseResource = createMock(IResource.class);
		replay(baseResource);
		GitFolderResourceVariant base = new GitFolderResourceVariant(
				baseResource);

		GitSyncInfo gsi = new GitSyncInfo(local, base, null, comparator);
		gsi.init();

		// then
		assertEquals(CONFLICTING | DELETION | PSEUDO_CONFLICT, gsi.getKind());
		verify(local, baseResource);
	}

	@Test
	@SuppressWarnings("boxing")
	// file exists in local and remote but it does not exists in base
	public void shouldReturnConflictingAddition() throws Exception {
		// when

		// given
		IResource local = createMock(IResource.class);
		expect(local.exists()).andReturn(true).times(3);
		expect(local.isDerived()).andReturn(false);
		expect(local.getName()).andReturn("Mian.java").anyTimes();
		replay(local);

		IResource remoteResource = createMock(IResource.class);
		replay(remoteResource);
		GitBlobResourceVariant remote = new GitBlobResourceVariant(
				remoteResource, repo, ObjectId.zeroId(), null);
		GitSyncInfo gsi = new GitSyncInfo(local, null, remote, comparator);
		gsi.init();

		// then
		assertEquals(CONFLICTING | ADDITION, gsi.getKind());
		verify(local, remoteResource);
	}

	private ObjectId stageAndCommit(String fileName, byte[] content)
			throws Exception {
		ObjectId objectId = stage(fileName, content);
		commit();

		return objectId;
	}

	private ObjectId stage(String fileName, byte[] content) throws Exception {
		// TODO reimplement using DirCache class
		GitIndex index = repo.getIndex();
		File workdir = project.getProject().getFullPath().toFile();
		File file = new File(workdir, fileName);
		Entry entry = index.add(workdir, file, content);
		index.write();

		return entry.getObjectId();
	}

	private RevCommit commit() throws Exception {
		Git git = new Git(repo);
		CommitCommand commit = git.commit();
		commit.setMessage("Initial  commit");
		commit.setAuthor("EGit", "egi@eclipse.org");
		return commit.call();
	}

}
