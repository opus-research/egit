/*******************************************************************************
 * Copyright (c) 2014 Maik Schreiber
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Maik Schreiber - initial implementation
 *******************************************************************************/
package org.eclipse.egit.core.op;

import java.text.MessageFormat;
import java.util.List;

import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.egit.core.CommitUtil;
import org.eclipse.egit.core.internal.CoreText;
import org.eclipse.egit.core.internal.job.RuleUtil;
import org.eclipse.egit.core.internal.util.ProjectUtil;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RebaseCommand;
import org.eclipse.jgit.api.RebaseCommand.InteractiveHandler;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IllegalTodoFileModification;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.RebaseTodoLine;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.team.core.TeamException;

/** Squashes multiple commits into one. */
public class SquashCommitsOperation implements IEGitOperation {
	private Repository repository;

	private List<RevCommit> commits;

	private InteractiveHandler messageHandler;

	/**
	 * Constructs a new squash commits operation.
	 *
	 * @param repository
	 *            the repository to work on
	 * @param commits
	 *            the commits
	 * @param messageHandler
	 *            handler that will be used to prompt for a commit message
	 */
	public SquashCommitsOperation(Repository repository,
			List<RevCommit> commits, InteractiveHandler messageHandler) {
		this.repository = repository;
		this.commits = CommitUtil.sortCommits(commits);
		this.messageHandler = messageHandler;
	}

	public void execute(IProgressMonitor m) throws CoreException {
		IProgressMonitor monitor = m != null ? m : new NullProgressMonitor();

		IWorkspaceRunnable action = new IWorkspaceRunnable() {
			public void run(IProgressMonitor pm) throws CoreException {
				pm.beginTask("", 2); //$NON-NLS-1$

				pm.subTask(MessageFormat.format(
						CoreText.SquashCommitsOperation_squashing,
						Integer.valueOf(commits.size())));

				InteractiveHandler handler = new InteractiveHandler() {
					public void prepareSteps(List<RebaseTodoLine> steps) {
						RevCommit firstCommit = commits.get(0);
						for (RebaseTodoLine step : steps) {
							if (isRelevant(step.getCommit())) {
								try {
									if (step.getCommit().prefixCompare(
											firstCommit) == 0)
										step.setAction(RebaseTodoLine.Action.PICK);
									else
										step.setAction(RebaseTodoLine.Action.SQUASH);
								} catch (IllegalTodoFileModification e) {
									// shouldn't happen
								}
							}
						}
					}

					private boolean isRelevant(AbbreviatedObjectId id) {
						for (RevCommit commit : commits) {
							if (id.prefixCompare(commit) == 0)
								return true;
						}
						return false;
					}

					public String modifyCommitMessage(String oldMessage) {
						return messageHandler.modifyCommitMessage(oldMessage);
					}
				};
				try {
					Git git = new Git(repository);
					git.rebase().setUpstream(commits.get(0).getParent(0))
							.runInteractively(handler)
							.setOperation(RebaseCommand.Operation.BEGIN).call();
				} catch (GitAPIException e) {
					throw new TeamException(e.getLocalizedMessage(),
							e.getCause());
				}
				pm.worked(1);

				ProjectUtil.refreshValidProjects(
						ProjectUtil.getValidOpenProjects(repository),
						new SubProgressMonitor(pm, 1));

				pm.done();
			}
		};
		ResourcesPlugin.getWorkspace().run(action, getSchedulingRule(),
				IWorkspace.AVOID_UPDATE, monitor);
	}

	public ISchedulingRule getSchedulingRule() {
		return RuleUtil.getRule(repository);
	}
}
