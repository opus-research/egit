/*******************************************************************************
 * Copyright (c) 2010, 2013 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Mathias Kinzler (SAP AG) - initial implementation
 *******************************************************************************/
package org.eclipse.egit.ui.view.repositories;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.eclipse.core.commands.State;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.egit.core.Activator;
import org.eclipse.egit.core.RepositoryUtil;
import org.eclipse.egit.ui.JobFamilies;
import org.eclipse.egit.ui.common.LocalRepositoryTestCase;
import org.eclipse.egit.ui.internal.UIText;
import org.eclipse.egit.ui.internal.repository.RepositoriesView;
import org.eclipse.egit.ui.internal.repository.tree.command.ToggleBranchCommitCommand;
import org.eclipse.egit.ui.test.JobJoiner;
import org.eclipse.egit.ui.test.TestUtil;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.swtbot.eclipse.finder.widgets.SWTBotView;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;

/**
 * Collection of utility methods for Git Repositories View tests
 */
public abstract class GitRepositoriesViewTestBase extends
		LocalRepositoryTestCase {

	// test utilities
	protected static final TestUtil myUtil = new TestUtil();

	// the human-readable view name
	protected final static String viewName = myUtil
			.getPluginLocalizedValue("GitRepositoriesView_name");

	protected static final GitRepositoriesViewTestUtils myRepoViewUtil = new GitRepositoriesViewTestUtils();

	/**
	 * remove all configured repositories from the view
	 */
	protected static void clearView() {
		InstanceScope.INSTANCE.getNode(Activator.getPluginId()).remove(
				RepositoryUtil.PREFS_DIRECTORIES);
	}

	protected static void createStableBranch(Repository myRepository)
			throws IOException {
		// let's create a stable branch temporarily so
		// that we push two branches to remote
		String newRefName = "refs/heads/stable";
		RefUpdate updateRef = myRepository.updateRef(newRefName);
		Ref sourceBranch = myRepository.getRef("refs/heads/master");
		ObjectId startAt = sourceBranch.getObjectId();
		String startBranch = Repository.shortenRefName(sourceBranch.getName());
		updateRef.setNewObjectId(startAt);
		updateRef
				.setRefLogMessage("branch: Created from " + startBranch, false); //$NON-NLS-1$
		updateRef.update();
	}

	protected static void setVerboseBranchMode(boolean state) {
		ICommandService srv = (ICommandService) PlatformUI.getWorkbench()
				.getService(ICommandService.class);
		State verboseBranchModeState = srv.getCommand(
				ToggleBranchCommitCommand.ID).getState(
				ToggleBranchCommitCommand.TOGGLE_STATE);
		verboseBranchModeState.setValue(Boolean.valueOf(state));
	}

	protected SWTBotView getOrOpenView() throws Exception {
		SWTBotView view = TestUtil.showView(RepositoriesView.VIEW_ID);
		TestUtil.joinJobs(JobFamilies.REPO_VIEW_REFRESH);
		return view;
	}

	protected void assertHasRepo(File repositoryDir) throws Exception {
		final SWTBotTree tree = getOrOpenView().bot().tree();
		final SWTBotTreeItem[] items = tree.getAllItems();
		boolean found = false;
		for (SWTBotTreeItem item : items) {
			if (item.getText().startsWith(
					repositoryDir.getParentFile().getName())) {
				found = true;
				break;
			}
		}
		assertTrue("Tree should have item with correct text", found);
	}

	protected void assertEmpty() throws Exception {
		final SWTBotView view = getOrOpenView();
		view.bot().label(UIText.RepositoriesView_messsageEmpty);
	}

	protected void refreshAndWait() throws Exception {
		RepositoriesView view = (RepositoriesView) getOrOpenView()
				.getReference().getPart(false);
		JobJoiner jobJoiner = JobJoiner.startListening(JobFamilies.REPO_VIEW_REFRESH, 60, TimeUnit.SECONDS);
		view.refresh();
		jobJoiner.join();
	}

	@SuppressWarnings("boxing")
	protected void assertProjectExistence(String projectName, boolean existence) {
		IProject prj = ResourcesPlugin.getWorkspace().getRoot().getProject(
				projectName);
		assertEquals("Project existence " + projectName, prj.exists(),
				existence);
	}
}
