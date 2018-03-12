/*******************************************************************************
 * Copyright (c) 2010, SAP AG
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Stefan Lay (SAP AG) - initial implementation
 *******************************************************************************/
package org.eclipse.egit.ui.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTree;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotTreeItem;

public class ExistingOrNewPage {

	private static final SWTWorkbenchBot bot = new SWTWorkbenchBot();

	@SuppressWarnings("boxing")
	public void assertEnabling(boolean createRepository, boolean textField,
			boolean finish) {
		assertEquals(createRepository, bot.button("Create Repository")
				.isEnabled());
		assertEquals(textField, bot.text().isEnabled());
		assertEquals(finish, bot.button("Finish").isEnabled());
	}

	public void assertContents(String project, String path, String repository,
			String newRepoPath) {
		assertContents(new Row[] { new Row(project, path, repository) },
				newRepoPath);
	}

	public void assertContents(Row[] rows, String newRepoPath) {
		assertContents(bot.tree(), rows);
		assertEquals(newRepoPath, bot.text().getText());
	}

	private void assertContents(SWTBotTree tree, Row[] rows) {
		assertEquals(rows.length, bot.tree().rowCount());
		for (int i = 0; i < rows.length; i++) {
			assertEquals(rows[i].getProject(), bot.tree().cell(i, 0));
			assertEquals(rows[i].getPath(), bot.tree().cell(i, 1));
			assertEquals(rows[i].getRepository(), bot.tree().cell(i, 2));
			SWTBotTreeItem subteeItems = bot.tree().getAllItems()[i];
			Row[] subrows = rows[i].getSubrows();
			if (subrows != null) {
				assertEquals("Row " + i + " is a tree:", subrows.length, subteeItems.getItems().length);
				assertNotNull("Rows " + i + " is not a tree", subteeItems.getItems());
				for (int j = 0; j < subrows.length; ++j) {
					Row r = subrows[j];
					assertEquals(r.getProject(), subteeItems.cell(j, 0));
					assertEquals(r.getPath(), subteeItems.cell(j, 1));
					assertEquals(r.getRepository(), subteeItems.cell(j, 2));
				}
			} else
				assertEquals("Row " + i + " is a tree:", 0, subteeItems.getItems().length);
		}
	}

	public static class Row {
		private String project;

		private String path;

		private String repository;

		private final Row[] subrows;

		public Row(String project, String path, String repository) {
			this(project, path, repository, null);
		}
		public Row(String project, String path, String repository, Row[] subrows) {
			this.project = project;
			this.path = path;
			this.repository = repository;
			this.subrows = subrows;
		}

		public String getProject() {
			return project;
		}

		public String getPath() {
			return path;
		}

		public String getRepository() {
			return repository;
		}

		public Row[] getSubrows() {
			return subrows;
		}
	}

}
