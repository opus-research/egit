/*******************************************************************************
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.history.command;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.internal.CompareUtils;
import org.eclipse.egit.ui.internal.EgitUiEditorUtils;
import org.eclipse.egit.ui.internal.history.GitHistoryPage;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.osgi.util.NLS;
import org.eclipse.team.core.history.IFileRevision;

/**
 * Open a file or files in a text editor
 */
public class OpenInTextEditorHandler extends AbstractHistoryCommanndHandler {
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IStructuredSelection selection = getSelection(getPage());
		if (selection.size() < 1)
			return null;
		Object input = getInput(event);
		if (!(input instanceof IFile))
			return null;
		IFile resource = (IFile) input;
		final RepositoryMapping map = RepositoryMapping.getMapping(resource);
		final String gitPath = map.getRepoRelativePath(resource);
		Iterator<?> it = selection.iterator();
		boolean errorOccured = false;
		List<ObjectId> ids = new ArrayList<ObjectId>();
		while (it.hasNext()) {
			RevCommit commit = (RevCommit) it.next();
			IFileRevision rev = null;
			try {
				rev = CompareUtils.getFileRevision(gitPath, commit, map
						.getRepository(), null);
			} catch (IOException e) {
				Activator.logError(NLS.bind(
						UIText.GitHistoryPage_errorLookingUpPath, gitPath,
						commit.getId()), e);
				errorOccured = true;
			}
			if (rev != null) {
				try {
					EgitUiEditorUtils.openTextEditor(getPart(event).getSite()
							.getPage(), rev, null);
				} catch (CoreException e) {
					Activator.logError(e.getMessage(), e);
					errorOccured = true;
				}
			} else {
				ids.add(commit.getId());
			}
		}
		if (errorOccured)
			Activator.showError(UIText.GitHistoryPage_openFailed, null);
		if (ids.size() > 0) {
			String idList = ""; //$NON-NLS-1$
			for (ObjectId objectId : ids) {
				idList += objectId.getName() + " "; //$NON-NLS-1$
			}
			MessageDialog.openError(getPart(event).getSite().getShell(),
					UIText.GitHistoryPage_fileNotFound, NLS.bind(
							UIText.GitHistoryPage_notContainedInCommits,
							gitPath, idList));
		}
		return null;
	}

	@Override
	public boolean isEnabled() {
		GitHistoryPage page = getPage();
		if (page == null)
			return false;
		int size = getSelection(page).size();
		return size >= 1
				&& IFile.class.isAssignableFrom(page.getInput().getClass());
	}
}
