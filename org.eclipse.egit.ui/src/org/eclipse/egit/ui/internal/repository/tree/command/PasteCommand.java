/*******************************************************************************
 * Copyright (c) 2010-2011 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Mathias Kinzler (SAP AG) - initial implementation
 *    Matthias Sohn (SAP AG) - imply .git if parent folder is given
 *******************************************************************************/
package org.eclipse.egit.ui.internal.repository.tree.command;

import java.io.File;
import java.net.URISyntaxException;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.internal.components.RepositorySelectionPage.Protocol;
import org.eclipse.egit.ui.internal.repository.tree.RepositoryTreeNode;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;

/**
 * "Adds" a Repository upon pasting the clip-board contents if it contains a
 * local path. Otherwise opens the Clone Wizard if the clipboard content is a
 * valid Git URI.
 * <p>
 * This checks if the clip-board contents corresponds to a Git repository folder
 * and adds that repository to the view if it doesn't already exists. If the
 * clipboard content is not a valid local path it opens the Clone Wizard if the
 * clipboard content is a valid Git URI.
 * <p>
 * TODO we should extend this and open the "Add Repositories" dialog if the
 * clip-board contents corresponds to an existing directory in the local file
 * system. The "Directory" field of the dialog should be pre-filled with the
 * directory from the clip-board.
 */
public class PasteCommand extends
		RepositoriesViewCommandHandler<RepositoryTreeNode> {

	public Object execute(ExecutionEvent event) throws ExecutionException {
		// we check if the pasted content is a directory
		// repository location and try to add this
		String errorMessage = null;

		Clipboard clip = null;
		try {
			clip = new Clipboard(getShell(event).getDisplay());
			String content = (String) clip.getContents(TextTransfer
					.getInstance());
			if (content == null) {
				errorMessage = UIText.RepositoriesView_NothingToPasteMessage;
				return null;
			}

			File file = new File(content);
			if (!file.exists() || !file.isDirectory()) {
				// try if clipboard contains a git URI
				URIish cloneURI = getCloneURI(content);
				if (cloneURI == null) {
					errorMessage = UIText.RepositoriesView_ClipboardContentNotDirectoryOrURIMessage;
					return null;
				} else {
					// start clone wizard
					CloneCommand cmd = new CloneCommand(cloneURI.toString());
					cmd.execute(event);
					return null;
				}
			}

			if (!RepositoryCache.FileKey.isGitRepository(file, FS.DETECTED)) {
				// try if .git folder is one level below
				file = new File(file, Constants.DOT_GIT);
				if (!RepositoryCache.FileKey.isGitRepository(file, FS.DETECTED)) {
					errorMessage = NLS
							.bind(UIText.RepositoriesView_ClipboardContentNoGitRepoMessage,
									content);
					return null;
				}
			}

			if (util.addConfiguredRepository(file)) {
				// let's do the auto-refresh the rest
			} else
				errorMessage = NLS.bind(
						UIText.RepositoriesView_PasteRepoAlreadyThere, content);

			return null;
		} finally {
			if (clip != null)
				// we must dispose ourselves
				clip.dispose();
			if (errorMessage != null)
				Activator.handleError(errorMessage, null, true);
		}
	}

	private URIish getCloneURI(String content) {
		URIish finalURI;
		try {
			finalURI = new URIish(content.trim());
			if (Protocol.FILE.handles(finalURI)
					|| Protocol.GIT.handles(finalURI)
					|| Protocol.HTTP.handles(finalURI)
					|| Protocol.HTTPS.handles(finalURI)
					|| Protocol.SSH.handles(finalURI))
				return finalURI;
			else
				return null;
		} catch (URISyntaxException e) {
			Activator.handleError(e.getLocalizedMessage(), e, true);
			return null;
		}
	}
}
