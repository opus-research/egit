/*******************************************************************************
 * Copyright (c) 2012, 2013 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Mathias Kinzler (SAP AG) - initial implementation
 *******************************************************************************/
package org.eclipse.egit.ui.test.team.actions;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.eclipse.egit.ui.JobFamilies;
import org.eclipse.egit.ui.common.LocalRepositoryTestCase;
import org.eclipse.egit.ui.internal.UIText;
import org.eclipse.egit.ui.test.ContextMenuHelper;
import org.eclipse.egit.ui.test.JobJoiner;
import org.eclipse.egit.ui.test.TestUtil;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for the Replace With actions
 */
@RunWith(SWTBotJunit4ClassRunner.class)
public class ReplaceActionsTest extends LocalRepositoryTestCase {
	private File repositoryFile;

	@Before
	public void setup() throws Exception {
		repositoryFile = createProjectAndCommitToRepository();
	}

	@Test
	public void testReplaceWithPrevious() throws Exception {
		touchAndSubmit(null);
		String initialContent = getTestFileContent();
		String menuLabel = util
				.getPluginLocalizedValue("replaceWithPreviousVersionAction.label");
		clickReplaceWith(menuLabel);
		SWTBotShell confirm = bot
				.shell(UIText.DiscardChangesAction_confirmActionTitle);
		executeReplace(confirm);
		String replacedContent = getTestFileContent();
		assertThat(replacedContent, not(initialContent));
	}

	@Test
	public void testReplaceWithPreviousWithMerge() throws Exception {
		Repository repo = lookupRepository(repositoryFile);
		Git git = new Git(repo);

		git.checkout().setCreateBranch(true).setName("side").call();
		touch(PROJ1, "folder/test.txt", "side");
		RevCommit sideCommit = git.commit().setAll(true)
				.setMessage("Side commit").call();

		git.checkout().setName("master").call();
		touch(PROJ1, "folder/test2.txt", "master");
		git.commit().setAll(true).setMessage("Master commit").call();

		git.merge().include(sideCommit).call();

		String contentAfterMerge = getTestFileContent();
		assertEquals("side", contentAfterMerge);

		String menuLabel = util
				.getPluginLocalizedValue("replaceWithPreviousVersionAction.label");
		clickReplaceWith(menuLabel);
		bot.shell(UIText.DiscardChangesAction_confirmActionTitle).bot()
				.button(IDialogConstants.OK_LABEL).click();
		SWTBotShell selectDialog = bot
				.shell(UIText.CommitSelectDialog_WindowTitle);
		assertEquals(2, selectDialog.bot().table().rowCount());
		selectDialog.close();
		// we have closed, so nothing should have changed
		String contentAfterClose = getTestFileContent();
		assertEquals(contentAfterMerge, contentAfterClose);

		clickReplaceWith(menuLabel);
		bot.shell(UIText.DiscardChangesAction_confirmActionTitle).bot()
				.button(IDialogConstants.OK_LABEL).click();
		selectDialog = bot.shell(UIText.CommitSelectDialog_WindowTitle);
		// Select first parent, which should be the master commit
		selectDialog.bot().table().select(0);
		executeReplace(selectDialog);

		String replacedContent = getTestFileContent();
		assertThat(replacedContent, not(contentAfterMerge));
	}

	private void clickReplaceWith(String menuLabel) {
		SWTBotTree projectExplorerTree = TestUtil.getExplorerTree();
		getProjectItem(projectExplorerTree, PROJ1).select();
		ContextMenuHelper.clickContextMenu(projectExplorerTree, "Replace With",
				menuLabel);
	}

	private void executeReplace(SWTBotShell dialog) {
		JobJoiner jobJoiner = JobJoiner.startListening(
				JobFamilies.DISCARD_CHANGES, 30, TimeUnit.SECONDS);
		dialog.bot().button(IDialogConstants.OK_LABEL).click();
		jobJoiner.join();
	}
}
