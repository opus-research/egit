/*******************************************************************************
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2006, Shawn O. Pearce <spearce@spearce.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.actions;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.Command;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.commands.NotEnabledException;
import org.eclipse.core.commands.NotHandledException;
import org.eclipse.core.commands.ParameterizedCommand;
import org.eclipse.core.commands.common.NotDefinedException;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.egit.ui.Activator;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.commands.ICommandService;
import org.eclipse.ui.handlers.IHandlerService;

/**
 * A helper class for Team Actions on Git controlled projects
 */
public abstract class RepositoryAction extends AbstractHandler implements
		IObjectActionDelegate {

	private final String commandId;

	private IWorkbenchPart part;

	/**
	 * @param commandId
	 */
	protected RepositoryAction(String commandId) {
		this.commandId = commandId;
	}

	/**
	 * @return the current selection
	 */
	protected IStructuredSelection getSelection() {
		// TODO Synchronize CommitOperation overwrites this, can we get rid
		// of it?
		ISelectionService srv = (ISelectionService) PlatformUI.getWorkbench()
				.getActiveWorkbenchWindow().getService(ISelectionService.class);
		if (srv == null)
			return new StructuredSelection();
		return (IStructuredSelection) srv.getSelection();
	}

	public void setActivePart(IAction action, IWorkbenchPart targetPart) {
		part = targetPart;
	}

	public void run(IAction action) {
		ICommandService srv = (ICommandService) part.getSite().getService(
				ICommandService.class);
		IHandlerService hsrv = (IHandlerService) part.getSite().getService(
				IHandlerService.class);
		Command command = srv.getCommand(commandId);

		IEvaluationContext context = hsrv.createContextSnapshot(true);

		try {
			hsrv.executeCommandInContext(
					new ParameterizedCommand(command, null), null, context);
		} catch (ExecutionException e) {
			Activator.handleError(e.getMessage(), e, true);
		} catch (NotDefinedException e) {
			Activator.handleError(e.getMessage(), e, true);
		} catch (NotEnabledException e) {
			Activator.handleError(e.getMessage(), e, true);
		} catch (NotHandledException e) {
			Activator.handleError(e.getMessage(), e, true);
		}
	}

	public final void selectionChanged(IAction action, ISelection selection) {
		action.setEnabled(isEnabled());
	}

	public final Object execute(ExecutionEvent event) throws ExecutionException {
		ICommandService srv = (ICommandService) part.getSite().getService(
				ICommandService.class);
		Command command = srv.getCommand(commandId);
		try {
			return command.executeWithChecks(event);
		} catch (ExecutionException e) {
			Activator.handleError(e.getMessage(), e, true);
		} catch (NotDefinedException e) {
			Activator.handleError(e.getMessage(), e, true);
		} catch (NotEnabledException e) {
			Activator.handleError(e.getMessage(), e, true);
		} catch (NotHandledException e) {
			Activator.handleError(e.getMessage(), e, true);
		}
		return null;
	}

	@Override
	public final boolean isEnabled() {
		if (part == null)
			return false;
		ICommandService srv = (ICommandService) part.getSite().getService(
				ICommandService.class);
		Command command = srv.getCommand(commandId);
		return command.isEnabled();
	}
}
