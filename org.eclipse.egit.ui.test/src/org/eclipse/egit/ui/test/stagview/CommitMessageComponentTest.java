/*******************************************************************************
 * Copyright (C) 2017, Stefan Rademacher <stefan.rademacher@tk.de> and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.test.stagview;

import static org.junit.Assert.assertEquals;

import org.eclipse.core.resources.IResource;
import org.eclipse.egit.ui.CommitMessageWithCaretPosition;
import org.eclipse.egit.ui.ICommitMessageProvider;
import org.eclipse.egit.ui.ICommitMessageProvider2;
import org.eclipse.egit.ui.common.StagingViewTester;
import org.junit.After;
import org.junit.Test;

public class CommitMessageComponentTest extends AbstractStagingViewTestCase {

	@After
	public void resetCommitMessageProvider() {
		TestCommitMessageProviderExtensionFactory.INSTANCE.reset();
	}

	private void assertPosition(int actualPosition, String message) {
		// Convention: expected position in message is marked by "><"
		int expectedPosition = message.indexOf("><") + 1;
		assertEquals("Position mismatch", expectedPosition, actualPosition);
	}

	@Test
	public void testCaretPosition() throws Exception {
		setContent("I have changed this");
		TestCommitMessageProviderExtensionFactory.INSTANCE
				.setCommitMessageProviders(new TestCommitMessageProvider(
						"Caret test\n\nCaret is supposed to be there: ",
						"\n\nThis is a commit message from testCaretPosition"));
		StagingViewTester stagingViewTester = StagingViewTester
				.openStagingView();

		stagingViewTester.stageFile(FILE1_PATH);

		assertPosition(stagingViewTester.getCaretPosition(),
				stagingViewTester.getCommitMessage());
	}

	@Test
	public void testCaretPositionUndefined() throws Exception {
		setContent("I have changed this");
		TestCommitMessageProviderExtensionFactory.INSTANCE
				.setCommitMessageProviders(new ICommitMessageProvider() {

					@Override
					public String getMessage(IResource[] resources) {
						return "Commit msg from testCaretPositionUndefined";
					}

				});
		StagingViewTester stagingViewTester = StagingViewTester
				.openStagingView();

		stagingViewTester.stageFile(FILE1_PATH);

		assertPosition(stagingViewTester.getCaretPosition(),
				stagingViewTester.getCommitMessage());
	}

	@Test
	public void testTwoProvidersFromSameExtension() throws Exception {
		setContent("I have changed this");
		TestCommitMessageProvider provider1 = new TestCommitMessageProvider(
				"Caret test\n\nCaret is supposed to be there: ",
				"\n\nThis is a commit message from testTwoProvidersFromSameExtension");

		TestCommitMessageProvider provider2 = new TestCommitMessageProvider(
				"Another commit message. \n\nCaret is NOT supposed to be there: ",
				"\n\nThis is a commit message from testTwoProvidersFromSameExtension");
		TestCommitMessageProviderExtensionFactory.INSTANCE
				.setCommitMessageProviders(provider1, provider2);

		StagingViewTester stagingViewTester = StagingViewTester
				.openStagingView();

		stagingViewTester.stageFile(FILE1_PATH);

		assertPosition(stagingViewTester.getCaretPosition(),
				stagingViewTester.getCommitMessage());

		assertEquals(
				provider1.getMessage(null) + "\n\n"
						+ provider2.getMessage(null),
				stagingViewTester.getCommitMessage());
	}

	private static class TestCommitMessageProvider
			implements ICommitMessageProvider2 {

		private String message;

		private int pos;

		public TestCommitMessageProvider(String prefix, String suffix) {
			message = prefix + "><" + suffix;
			pos = prefix.length() + 1;
		}

		@Override
		public String getMessage(IResource[] resources) {
			return message;
		}

		@Override
		public CommitMessageWithCaretPosition getCommitMessageWithPosition(
				IResource[] resources) {
			return new CommitMessageWithCaretPosition(message, pos);
		}

	}

}
