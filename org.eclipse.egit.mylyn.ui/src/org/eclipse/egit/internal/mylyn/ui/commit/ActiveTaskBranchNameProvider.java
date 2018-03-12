/*******************************************************************************
 * Copyright (c) 2011, 2014 Chris Aniszczyk and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Chris Aniszczyk <caniszczyk@gmail.com> - initial API and implementation
 *     Manuel Doninger <manuel.doninger@googlemail.com>
 *     Benjamin Muskalla <benjamin.muskalla@tasktop.com>
 *     Thorsten Kamann <thorsten@kamann.info>
 *     Steffen Pingel <steffen.pingel@tasktop.com>
 *******************************************************************************/
package org.eclipse.egit.internal.mylyn.ui.commit;

import org.eclipse.egit.ui.IBranchNameProvider;
import org.eclipse.mylyn.internal.tasks.ui.util.TasksUiInternal;
import org.eclipse.mylyn.tasks.core.ITask;
import org.eclipse.mylyn.tasks.ui.TasksUi;

/**
 * A BranchNameProvider using description and title of the currently active task
 * to suggest a branch name.
 */
public class ActiveTaskBranchNameProvider implements IBranchNameProvider {

	/**
	 * @return the currently activated task or <code>null</code> if no task is
	 *         activated
	 */
	protected ITask getCurrentTask() {
		return TasksUi.getTaskActivityManager().getActiveTask();
	}

	public String getBranchNameSuggestion() {
		ITask task = getCurrentTask();
		if (task == null)
			return null;

		String taskKey = task.getTaskKey();
		if (taskKey == null)
			taskKey = task.getTaskId();

		StringBuilder sb = new StringBuilder();
		sb.append(TasksUiInternal.getTaskPrefix(task.getConnectorKind()));
		sb.append(taskKey);
		sb.append('-');
		sb.append(task.getSummary());
		return normalizeBranchName(sb.toString());
	}

	private String normalizeBranchName(String name) {
		String normalized = name.trim()
				.replaceAll("\\s+", "_").replaceAll("[^\\w-]", ""); //$NON-NLS-1$//$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		if (normalized.length() > 30)
			normalized = normalized.substring(0, 30);
		return normalized;
	}

}

