/*******************************************************************************
 * Copyright (C) 2010, 2012 Mathias Kinzler <mathias.kinzler@sap.com> and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Mathias Kinzler <mathias.kinzler@sap.com>
 *    Laurent Goubet <laurent.goubet@obeo.fr>
 *    Gunnar Wagenknecht <gunnar@wagenknecht.org>
 *******************************************************************************/

package org.eclipse.egit.ui.internal.actions;

import java.io.IOException;

import org.eclipse.compare.CompareUI;
import org.eclipse.compare.ITypedElement;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.mapping.ResourceMapping;
import org.eclipse.core.resources.mapping.ResourceMappingContext;
import org.eclipse.egit.core.internal.util.ResourceUtil;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.egit.core.synchronize.dto.GitSynchronizeData;
import org.eclipse.egit.core.synchronize.dto.GitSynchronizeDataSet;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.internal.CompareUtils;
import org.eclipse.egit.ui.internal.GitCompareFileRevisionEditorInput;
import org.eclipse.egit.ui.internal.dialogs.CompareTargetSelectionDialog;
import org.eclipse.egit.ui.internal.dialogs.CompareTreeView;
import org.eclipse.egit.ui.internal.synchronize.GitModelSynchronize;
import org.eclipse.jface.window.Window;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.team.ui.synchronize.SaveableCompareEditorInput;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

/**
 * The "compare with ref" action. This action opens a diff editor comparing the
 * file as found in the working directory and the version in the selected ref.
 */
public class CompareWithRefActionHandler extends RepositoryActionHandler {
	public Object execute(ExecutionEvent event) throws ExecutionException {
		final Repository repo = getRepository(true, event);
		// assert all resources map to the same repository
		if (repo == null)
			return null;
		final IResource[] resources = getSelectedResources(event);

		CompareTargetSelectionDialog dlg = new CompareTargetSelectionDialog(
				getShell(event), repo, resources.length == 1 ? resources[0]
						.getFullPath().lastSegment() : null);
		if (dlg.open() == Window.OK) {

			if (resources.length == 1 && resources[0] instanceof IFile) {
				final IFile baseFile = (IFile) resources[0];

				if (CompareUtils.canDirectlyOpenInCompare(baseFile)) {
					showSingleFileComparison(baseFile, dlg.getRefName());
				} else {
					synchronizeModel(baseFile, repo, dlg.getRefName());
				}
			} else {
				CompareTreeView view;
				try {
					view = (CompareTreeView) PlatformUI.getWorkbench()
							.getActiveWorkbenchWindow().getActivePage()
							.showView(CompareTreeView.ID);
					view.setInput(resources, dlg.getRefName());
				} catch (PartInitException e) {
					Activator.handleError(e.getMessage(), e, true);
				}
			}
		}
		return null;
	}

	private void showSingleFileComparison(IFile file, String refName) {
		final ITypedElement base = SaveableCompareEditorInput
				.createFileElement(file);

		final ITypedElement next;
		try {
			RepositoryMapping mapping = RepositoryMapping.getMapping(file);
			next = getElementForRef(mapping.getRepository(),
					mapping.getRepoRelativePath(file), refName);
		} catch (IOException e) {
			Activator.handleError(
					UIText.CompareWithIndexAction_errorOnAddToIndex, e, true);
			return;
		}

		final GitCompareFileRevisionEditorInput in = new GitCompareFileRevisionEditorInput(
				base, next, null);
		in.getCompareConfiguration().setRightLabel(refName);
		CompareUI.openCompareEditor(in);
	}

	private void synchronizeModel(final IFile file, Repository repo,
			String refName) {
		try {
			final GitSynchronizeData data = new GitSynchronizeData(repo,
					Constants.HEAD, refName, true);
			final GitSynchronizeDataSet dataSet = new GitSynchronizeDataSet(
					data);

			// use all available local mappings for proper model support
			final ResourceMapping[] mappings = ResourceUtil
					.getResourceMappings(file,
							ResourceMappingContext.LOCAL_CONTEXT);

			GitModelSynchronize.launch(dataSet, mappings);
		} catch (IOException e) {
			Activator.handleError(
					UIText.CompareWithRefAction_errorOnSynchronize, e, true);
			return;
		}
	}

	private ITypedElement getElementForRef(final Repository repository,
			final String gitPath, final String refName) throws IOException {
		ObjectId commitId = repository.resolve(refName + "^{commit}"); //$NON-NLS-1$
		RevWalk rw = new RevWalk(repository);
		RevCommit commit = rw.parseCommit(commitId);
		rw.release();

		return CompareUtils.getFileRevisionTypedElement(gitPath, commit, repository);
	}

	@Override
	public boolean isEnabled() {
		return getRepository() != null;
	}
}
