/*******************************************************************************
 * Copyright (C) 2010, Jens Baumgart <jens.baumgart@sap.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.wizards.clone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.preferences.ConfigurationScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.egit.ui.common.GitImportRepoWizard;
import org.eclipse.egit.ui.common.LocalRepositoryTestCase;
import org.eclipse.egit.ui.common.RepoRemoteBranchesPage;
import org.eclipse.egit.ui.common.WorkingCopyPage;
import org.eclipse.equinox.internal.security.storage.PasswordProviderSelector;
import org.eclipse.equinox.internal.security.storage.PasswordProviderSelector.ExtStorageModule;
import org.eclipse.equinox.internal.security.storage.friends.IStorageConstants;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.util.FileUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

@SuppressWarnings("restriction")
public abstract class GitCloneWizardTestBase extends LocalRepositoryTestCase {

	protected static final int NUMBER_RANDOM_COMMITS = 100;
	protected GitImportRepoWizard importWizard;
	protected File destRepo;
    // package private for FindBugs
	static SampleTestRepository r;
	@AfterClass
	public static void tearDown() throws Exception {
		r.shutDown();
	}

	public GitCloneWizardTestBase() {
		super();
	}

	protected void cloneRepo(File destinationRepo,
			RepoRemoteBranchesPage remoteBranches) throws Exception {
		remoteBranches.assertRemoteBranches(SampleTestRepository.FIX,
				Constants.MASTER);
		remoteBranches.selectBranches(SampleTestRepository.FIX,
				Constants.MASTER);

		WorkingCopyPage workingCopy = remoteBranches.nextToWorkingCopy();
		workingCopy.setDirectory(destinationRepo.toString());

		workingCopy.assertDirectory(destinationRepo.toString());
		workingCopy.assertBranch(Constants.MASTER);
		workingCopy.assertRemoteName(Constants.DEFAULT_REMOTE_NAME);
		workingCopy.waitForCreate();

		// Some random sampling to see we got something. We do not test
		// the integrity of the repository here. Only a few basic properties
		// we'd expect from a clone made this way, that would possibly
		// not hold true given other parameters in the GUI.
		Repository repository = new FileRepository(new File(destinationRepo,
				Constants.DOT_GIT));
		// we always have an origin/master
		assertNotNull(repository.resolve("origin/master"));
		// and a local master initialized from origin/master (default!)
		assertEquals(repository.resolve("master"), repository
				.resolve("origin/master"));
		// A well known tag
		assertNotNull(repository.resolve(
				Constants.R_TAGS + SampleTestRepository.v1_0_name).name());
		// lots of refs
		int refs = repository.getAllRefs().size();
		assertTrue(refs >= 4);
		// and a known file in the working dir
		assertTrue(new File(destinationRepo, SampleTestRepository.A_txt_name)
				.exists());
		DirCacheEntry fileEntry = null;
		DirCache dc = repository.lockDirCache();
		fileEntry = dc.getEntry(SampleTestRepository.A_txt_name);
		dc.unlock();
		// check that we have the file in the index
		assertNotNull(fileEntry);
		// No project has been imported
		assertEquals(0,
				ResourcesPlugin.getWorkspace().getRoot().getProjects().length);
	}

	@BeforeClass
	public static void disableSecureStoragePasswordProviders() {
		List availableModules = PasswordProviderSelector.getInstance().findAvailableModules(null);
		StringBuffer tmp = new StringBuffer();
		for (Object module : availableModules) {
			ExtStorageModule storageModule = (ExtStorageModule) module;
			tmp.append(storageModule.moduleID).append(",");
		}
		IEclipsePreferences node = ConfigurationScope.INSTANCE.getNode("org.eclipse.equinox.security");
		node.put(IStorageConstants.DISABLED_PROVIDERS_KEY, tmp.toString());
	}

	@Before
	public void setupViews() {
		bot.perspectiveById("org.eclipse.jdt.ui.JavaPerspective").activate();
		bot.viewByTitle("Package Explorer").show();
		importWizard = new GitImportRepoWizard();
	}

	@After
	public void cleanup() throws Exception {
		if (destRepo != null)
			FileUtils.delete(destRepo, FileUtils.RECURSIVE | FileUtils.RETRY);
	}

}
