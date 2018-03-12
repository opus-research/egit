/*******************************************************************************
 * Copyright (C) 2015, Max Hohenegger <eclipse@hohenegger.eu>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.gitflow.ui.internal.actions;

import static org.eclipse.egit.gitflow.ui.Activator.error;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.egit.gitflow.GitFlowRepository;
import org.eclipse.egit.gitflow.op.FeatureStartOperation;
import org.eclipse.egit.gitflow.ui.internal.UIText;
import org.eclipse.egit.gitflow.ui.internal.validation.FeatureNameValidator;
import org.eclipse.egit.ui.internal.selection.SelectionUtils;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * git flow feature start
 */
public class FeatureStartHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IStructuredSelection selection = (IStructuredSelection) HandlerUtil
				.getCurrentSelection(event);
		final Repository repository = SelectionUtils.getRepository(selection);
		final GitFlowRepository gfRepo = new GitFlowRepository(repository);

		InputDialog inputDialog = new InputDialog(
				HandlerUtil.getActiveShell(event),
				UIText.FeatureStartHandler_provideFeatureName,
				UIText.FeatureStartHandler_pleaseProvideANameForTheNewFeature,
				"", //$NON-NLS-1$
				new FeatureNameValidator(gfRepo));

		if (inputDialog.open() != Window.OK) {
			return null;
		}

		final String featureName = inputDialog.getValue();

		WorkspaceJob job = new WorkspaceJob(UIText.FeatureStartHandler_startingNewFeature) {
			@Override
			public IStatus runInWorkspace(IProgressMonitor monitor) {
				try {
					new FeatureStartOperation(gfRepo, featureName)
							.execute(monitor);
				} catch (CoreException e) {
					return error(e.getMessage(), e);
				}
				return Status.OK_STATUS;
			}
		};
		job.setUser(true);
		job.schedule();

		return null;
	}
}
