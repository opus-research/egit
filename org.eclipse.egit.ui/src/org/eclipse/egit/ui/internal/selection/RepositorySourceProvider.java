/*******************************************************************************
 * Copyright (C) 2016 Thomas Wolf <thomas.wofl@paranor.ch>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.selection;

import java.util.Collections;
import java.util.Map;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.ui.AbstractSourceProvider;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.ISources;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.services.IServiceLocator;

/**
 * An {@link AbstractSourceProvider} that provides the current repository (based
 * on the current selection) as a variable in an Eclipse
 * {@link org.eclipse.core.expressions.IEvaluationContext}.
 */
public class RepositorySourceProvider extends AbstractSourceProvider
		implements ISelectionListener, IWindowListener {

	/**
	 * Key for the new variable in the
	 * {@link org.eclipse.core.expressions.IEvaluationContext}; may be used in a
	 * <with> element in plugin.xml to reference the variable.
	 */
	public static final String REPOSITORY_PROPERTY = "org.eclipse.egit.ui.currentRepository"; //$NON-NLS-1$

	private Repository repository;

	@Override
	public void initialize(IServiceLocator locator) {
		super.initialize(locator);
		PlatformUI.getWorkbench().addWindowListener(this);
	}

	@Override
	public void dispose() {
		PlatformUI.getWorkbench().removeWindowListener(this);
		repository = null;
	}

	@Override
	public Map getCurrentState() {
		return Collections.singletonMap(REPOSITORY_PROPERTY, repository);
	}

	@Override
	public String[] getProvidedSourceNames() {
		return new String[] { REPOSITORY_PROPERTY };
	}

	@Override
	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		Repository newRepository;
		if (selection == null) {
			newRepository = null;
		} else {
			newRepository = SelectionUtils.getRepository(
					SelectionUtils.getStructuredSelection(selection));
		}
		if (repository != newRepository) {
			repository = newRepository;
			fireSourceChanged(ISources.ACTIVE_WORKBENCH_WINDOW,
					REPOSITORY_PROPERTY,
					repository);
		}
	}

	@Override
	public void windowActivated(IWorkbenchWindow window) {
		window.getSelectionService().addSelectionListener(this);
	}

	@Override
	public void windowDeactivated(IWorkbenchWindow window) {
		window.getSelectionService().removeSelectionListener(this);
	}

	@Override
	public void windowClosed(IWorkbenchWindow window) {
		// Nothing to do
	}

	@Override
	public void windowOpened(IWorkbenchWindow window) {
		// Nothing to do
	}

}
