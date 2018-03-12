/*******************************************************************************
 * Copyright (C) 2015, Max Hohenegger <eclipse@hohenegger.eu>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.gitflow;

import static org.eclipse.egit.gitflow.ui.internal.UIPreferences.FEATURE_FINISH_KEEP_BRANCH;
import static org.eclipse.egit.gitflow.ui.internal.UIPreferences.FEATURE_FINISH_SQUASH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.eclipse.egit.gitflow.GitFlowRepository;
import org.eclipse.egit.gitflow.ui.Activator;
import org.eclipse.egit.gitflow.ui.internal.UIText;
import org.eclipse.egit.ui.test.CapturingSWTBotJunit4Runner;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for the Team->Gitflow->Feature Start/Finish actions
 */
@RunWith(CapturingSWTBotJunit4Runner.class)
public class FeatureFinishKeepBranchHandlerTest extends
		AbstractFeatureFinishHandlerTest {

	@Test
	public void testFeatureFinishKeepBranch() throws Exception {
		init();

		setContentAddAndCommit("bar");

		createFeature(FEATURE_NAME);
		RevCommit featureBranchCommit = setContentAddAndCommit("foo");

		checkoutBranch(DEVELOP);

		checkoutFeature(FEATURE_NAME);

		finishFeature();

		GitFlowRepository gfRepo = new GitFlowRepository(repository);
		RevCommit developHead = gfRepo.findHead();
		assertEquals(developHead, featureBranchCommit);

		assertNotNull(findBranch(gfRepo.getConfig().getFeatureBranchName(FEATURE_NAME)));

		IPreferenceStore prefStore = Activator.getDefault()
				.getPreferenceStore();
		assertFalse(prefStore.getBoolean(FEATURE_FINISH_SQUASH));
		assertTrue(prefStore.getBoolean(FEATURE_FINISH_KEEP_BRANCH));
	}

	@Override
	protected void selectOptions() {
		bot.checkBox(UIText.FinishFeatureDialog_keepBranch).click();
	}
}
