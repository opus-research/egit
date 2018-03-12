/*******************************************************************************
 * Copyright (C) 2011, 2012 Tomasz Zarna <Tomasz.Zarna@pl.ibm.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.patch;

import org.eclipse.core.resources.IResource;
import org.eclipse.egit.core.op.CreatePatchOperation;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.internal.history.GitCreatePatchWizard;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;

/**
 * The UI wrapper for {@link CreatePatchOperation}
 */
public class PatchOperationUI {
	private IWorkbenchPart part;

	private Repository repository;

	private RevCommit commit;

	private IResource resource;

	private PatchOperationUI(IWorkbenchPart part, Repository repo) {
		this.part = part;
		this.repository = repo;
	}

	private PatchOperationUI(IWorkbenchPart part, Repository repo,
			RevCommit commit) {
		this(part, repo);
		this.commit = commit;
	}

	private PatchOperationUI(IWorkbenchPart part, Repository repo, IResource resource) {
		this(part, repo);
		this.resource = resource;
	}

	/**
	 * Create an operation for creating a patch for a specific commit.
	 *
	 * @param part
	 *            the part
	 * @param commit
	 *            a commit
	 * @param repo
	 *            the repository
	 * @return the {@link PatchOperationUI}
	 */
	public static PatchOperationUI createPatch(IWorkbenchPart part,
			RevCommit commit, Repository repo) {
		return new PatchOperationUI(part, repo, commit);
	}

	/**
	 * Create an operation for creating a patch for change made relative to the
	 * index.
	 *
	 * @param part
	 *            the part
	 * @param resource
	 *            the resource
	 * @return the {@link PatchOperationUI}
	 */
	public static PatchOperationUI createPatch(IWorkbenchPart part,
			IResource resource) {
		RepositoryMapping mapping = RepositoryMapping.getMapping(resource);
		if (mapping == null) {
			MessageDialog.openError(getShell(part),
					UIText.RepositoryAction_errorFindingRepoTitle,
					UIText.RepositoryAction_errorFindingRepo);
			return null;
		}

		return new PatchOperationUI(null, mapping.getRepository(), resource);
	}

	/**
	 * Starts the operation asynchronously
	 */
	public void start() {
		if (commit != null) {
			GitCreatePatchWizard.run(getShell(), commit, null /*TODO*/, repository);
			return;
		} else

		if (isWorkingTreeClean()) {
			MessageDialog.openInformation(getShell(),
					UIText.GitCreatePatchAction_cannotCreatePatch,
					UIText.GitCreatePatchAction_workingTreeClean);
			return;
		}
		GitCreatePatchWizard.run(getShell(), null, resource , repository);
	}

	private boolean isWorkingTreeClean() {
		Git git = new Git(repository);
		try {
			Status status = git.status().call();
			return status.getModified().isEmpty()
					&& status.getUntracked().isEmpty()
					&& status.getMissing().isEmpty();
		} catch (Exception e) {
			MessageDialog.openError(getShell(),
					UIText.GitCreatePatchAction_cannotCreatePatch, e
							.getMessage() == null ? e.getMessage()
							: UIText.GitCreatePatchWizard_InternalError);
		}
		return true;
	}

	private Shell getShell() {
		return getShell(part);
	}

	private static Shell getShell(IWorkbenchPart part) {
		if (part != null)
			return part.getSite().getShell();
		return PlatformUI.getWorkbench().getDisplay().getActiveShell();
	}
}
