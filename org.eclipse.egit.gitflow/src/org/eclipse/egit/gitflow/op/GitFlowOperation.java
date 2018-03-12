/*******************************************************************************
 * Copyright (C) 2015, Max Hohenegger <eclipse@hohenegger.eu>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.gitflow.op;

import static java.lang.String.format;
import static org.eclipse.egit.gitflow.Activator.error;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.egit.core.internal.job.RuleUtil;
import org.eclipse.egit.core.op.BranchOperation;
import org.eclipse.egit.core.op.CreateLocalBranchOperation;
import org.eclipse.egit.core.op.DeleteBranchOperation;
import org.eclipse.egit.core.op.FetchOperation;
import org.eclipse.egit.core.op.IEGitOperation;
import org.eclipse.egit.core.op.MergeOperation;
import org.eclipse.egit.gitflow.GitFlowRepository;
import org.eclipse.egit.gitflow.internal.CoreText;
import org.eclipse.jgit.api.CheckoutResult;
import org.eclipse.jgit.api.CheckoutResult.Status;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RemoteConfig;

/**
 * Common logic for Git Flow operations.
 */
abstract public class GitFlowOperation implements IEGitOperation {
	/**
	 * git path separator
	 */
	public static final String SEP = "/"; //$NON-NLS-1$

	/**
	 * repository that is operated on.
	 */
	protected GitFlowRepository repository;

	/**
	 * the status of the latest merge from this operation
	 */
	// TODO: Remove from this class. Not all GitFlow operations involve a merge
	protected MergeResult mergeResult;

	/**
	 * @param repository
	 */
	public GitFlowOperation(GitFlowRepository repository) {
		this.repository = repository;
	}

	@Override
	public ISchedulingRule getSchedulingRule() {
		return RuleUtil.getRule(repository.getRepository());
	}

	/**
	 * @param branchName
	 * @param sourceCommit
	 * @return operation constructed from parameters
	 */
	protected CreateLocalBranchOperation createBranchFromHead(
			String branchName, RevCommit sourceCommit) {
		return new CreateLocalBranchOperation(repository.getRepository(),
				branchName, sourceCommit);
	}

	/**
	 * git flow * start
	 *
	 * @param monitor
	 * @param branchName
	 * @param sourceCommit
	 * @throws CoreException
	 */
	protected void start(IProgressMonitor monitor, String branchName,
			RevCommit sourceCommit) throws CoreException {
		CreateLocalBranchOperation branchOperation = createBranchFromHead(
				branchName, sourceCommit);
		branchOperation.execute(monitor);
		BranchOperation checkoutOperation = new BranchOperation(
				repository.getRepository(), branchName);
		checkoutOperation.execute(monitor);
	}

	/**
	 * git flow * finish
	 *
	 * @param monitor
	 * @param branchName
	 * @param squash
	 * @throws CoreException
	 */
	protected void finish(IProgressMonitor monitor, String branchName,
			boolean squash)
			throws CoreException {
		try {
			mergeResult = mergeTo(monitor, branchName, repository.getConfig()
					.getDevelop(), squash);
			if (!mergeResult.getMergeStatus().isSuccessful()) {
				return;
			}

			Ref branch = repository.findBranch(branchName);
			if (branch == null) {
				throw new IllegalStateException(String.format(
						CoreText.GitFlowOperation_branchMissing, branchName));
			}
			boolean forceDelete = squash;
			new DeleteBranchOperation(repository.getRepository(), branch, forceDelete)
					.execute(monitor);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @param monitor
	 * @param branchName
	 * @throws CoreException
	 */
	protected void finish(IProgressMonitor monitor, String branchName)
			throws CoreException {
		finish(monitor, branchName, false);
	}

	/**
	 * @param monitor
	 * @param branchName
	 * @param targetBranchName
	 * @param squash
	 * @return result of merging back to targetBranchName
	 * @throws CoreException
	 */
	protected MergeResult mergeTo(IProgressMonitor monitor, String branchName,
			String targetBranchName, boolean squash) throws CoreException {
		try {
			if (!repository.hasBranch(targetBranchName)) {
				throw new RuntimeException(String.format(
						CoreText.GitFlowOperation_branchNotFound,
						targetBranchName));
			}
			boolean dontCloseProjects = false;
			BranchOperation branchOperation = new BranchOperation(
					repository.getRepository(), targetBranchName,
					dontCloseProjects);
			branchOperation.execute(monitor);
			Status status = branchOperation.getResult().getStatus();
			if (!CheckoutResult.Status.OK.equals(status)) {
				throw new CoreException(error(format(
						CoreText.GitFlowOperation_unableToCheckout, branchName,
						status.toString())));
			}
			MergeOperation mergeOperation = new MergeOperation(
					repository.getRepository(), branchName);
			mergeOperation.setSquash(squash);
			if (squash) {
				mergeOperation.setCommit(true);
			}
			mergeOperation.execute(monitor);

			return mergeOperation.getResult();
		} catch (GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Merge without squash:
	 * {@link org.eclipse.egit.gitflow.op.GitFlowOperation#mergeTo(IProgressMonitor, String, String, boolean)}
	 *
	 * @param monitor
	 * @param branchName
	 * @param targetBranchName
	 * @return result of merging back to targetBranchName
	 * @throws CoreException
	 */
	protected MergeResult mergeTo(IProgressMonitor monitor, String branchName,
			String targetBranchName) throws CoreException {
		return mergeTo(monitor, branchName, targetBranchName, false);
	}

	/**
	 * @param monitor
	 * @return resulting of fetching from remote
	 * @throws URISyntaxException
	 * @throws InvocationTargetException
	 */
	protected FetchResult fetch(IProgressMonitor monitor)
			throws URISyntaxException, InvocationTargetException {
		RemoteConfig config = repository.getConfig().getDefaultRemoteConfig();
		FetchOperation fetchOperation = new FetchOperation(
				repository.getRepository(), config, 0, false);
		fetchOperation.run(monitor);
		return fetchOperation.getOperationResult();
	}

	/**
	 * @return The result of the merge this operation performs. May be null, if
	 *         no merge was performed.
	 */
	public MergeResult getMergeResult() {
		return mergeResult;
	}
}
