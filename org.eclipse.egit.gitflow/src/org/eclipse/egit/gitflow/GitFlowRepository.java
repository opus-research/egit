/*******************************************************************************
 * Copyright (C) 2015, Max Hohenegger <eclipse@hohenegger.eu>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.gitflow;

import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Constants.R_TAGS;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.Assert;
import org.eclipse.egit.gitflow.internal.CoreText;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Wrapper for JGit repository.
 */
public class GitFlowRepository {

	private Repository repository;

	private GitFlowConfig config;

	/**
	 * @param repository
	 * @since 4.0
	 */
	public GitFlowRepository(Repository repository) {
		Assert.isNotNull(repository);
		this.repository = repository;
		this.config = new GitFlowConfig(repository);
	}

	/**
	 * @return Whether or not this repository has branches.
	 * @since 4.0
	 */
	public boolean hasBranches() {
		List<Ref> branches;
		try {
			branches = Git.wrap(repository).branchList().call();
			return !branches.isEmpty();
		} catch (GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @param branch
	 * @return Whether or not branch exists in this repository.
	 * @throws GitAPIException
	 * @since 4.0
	 */
	public boolean hasBranch(String branch) throws GitAPIException {
		String fullBranchName = R_HEADS + branch;
		List<Ref> branchList = Git.wrap(repository).branchList().call();
		for (Ref ref : branchList) {
			if (fullBranchName.equals(ref.getTarget().getName())) {
				return true;
			}
		}

		return false;
	}

	/**
	 * @param branchName
	 * @return Ref for branchName.
	 * @throws IOException
	 * @since 4.0
	 */
	public Ref findBranch(String branchName) throws IOException {
		return repository.getRef(R_HEADS + branchName);
	}

	/**
	 * @return current branch has feature-prefix?
	 * @throws IOException
	 * @since 4.0
	 */
	public boolean isFeature() throws IOException {
		return repository.getBranch()
				.startsWith(getConfig().getFeaturePrefix());
	}

	/**
	 * @return branch name has name "develop"?
	 * @throws IOException
	 * @since 4.0
	 */
	public boolean isDevelop() throws IOException {
		return repository.getBranch().equals(getConfig().getDevelop());
	}

	/**
	 * @return branch name has name "master"?
	 * @throws IOException
	 * @since 4.0
	 */
	public boolean isMaster() throws IOException {
		return repository.getBranch().equals(getConfig().getMaster());
	}

	/**
	 * @return current branchs has release-prefix?
	 * @throws IOException
	 * @since 4.0
	 */
	public boolean isRelease() throws IOException {
		return repository.getBranch().startsWith(getConfig().getReleasePrefix());
	}

	/**
	 * @return current branchs has hotfix-prefix?
	 * @throws IOException
	 * @since 4.0
	 */
	public boolean isHotfix() throws IOException {
		return repository.getBranch().startsWith(getConfig().getHotfixPrefix());
	}

	/**
	 * @return HEAD commit
	 * @throws WrongGitFlowStateException
	 * @since 4.0
	 */
	public RevCommit findHead() throws WrongGitFlowStateException {
		try (RevWalk walk = new RevWalk(repository)) {
			try {
				ObjectId head = repository.resolve(HEAD);
				return walk.parseCommit(head);
			} catch (MissingObjectException e) {
				throw new WrongGitFlowStateException(CoreText.GitFlowRepository_gitFlowRepositoryMayNotBeEmpty);
			} catch (RevisionSyntaxException | IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	/**
	 * @param branchName
	 * @return HEAD commit on branch branchName
	 * @since 4.0
	 */
	public RevCommit findHead(String branchName) {
		try (RevWalk walk = new RevWalk(repository)) {
			try {
				ObjectId head = repository.resolve(R_HEADS + branchName);
				return walk.parseCommit(head);
			} catch (RevisionSyntaxException | IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	/**
	 * @param sha1
	 * @return Commit for SHA1
	 * @since 4.0
	 */
	public RevCommit findCommit(String sha1) {
		try (RevWalk walk = new RevWalk(repository)) {
			try {
				ObjectId head = repository.resolve(sha1);
				return walk.parseCommit(head);
			} catch (RevisionSyntaxException | IOException e) {
				throw new RuntimeException(e);
			}
		}

	}

	/**
	 * @return JGit repository
	 * @since 4.0
	 */
	public Repository getRepository() {
		return repository;
	}

	/**
	 * @return git flow feature branches
	 * @since 4.0
	 */
	public List<Ref> getFeatureBranches() {
		return getPrefixBranches(R_HEADS + getConfig().getFeaturePrefix());
	}

	/**
	 * @return git flow release branches
	 * @since 4.0
	 */
	public List<Ref> getReleaseBranches() {
		return getPrefixBranches(R_HEADS + getConfig().getReleasePrefix());
	}

	/**
	 * @return git flow hotfix branches
	 * @since 4.0
	 */
	public List<Ref> getHotfixBranches() {
		return getPrefixBranches(R_HEADS + getConfig().getHotfixPrefix());
	}

	private List<Ref> getPrefixBranches(String prefix) {
		try {
			List<Ref> branches = Git.wrap(repository).branchList().call();
			List<Ref> prefixBranches = new ArrayList<Ref>();
			for (Ref ref : branches) {
				if (ref.getName().startsWith(prefix)) {
					prefixBranches.add(ref);
				}
			}

			return prefixBranches;
		} catch (GitAPIException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @param ref
	 * @return branch name for ref
	 * @since 4.0
	 */
	public String getFeatureBranchName(Ref ref) {
		return ref.getName().substring(
				(R_HEADS + getConfig().getFeaturePrefix()).length());
	}

	/**
	 * @param tagName
	 * @return commit tag tagName points to
	 * @throws MissingObjectException
	 * @throws IncorrectObjectTypeException
	 * @throws IOException
	 * @since 4.0
	 */
	public RevCommit findCommitForTag(String tagName)
			throws MissingObjectException, IncorrectObjectTypeException,
			IOException {
		try (RevWalk revWalk = new RevWalk(repository)) {
			Ref tagRef = repository.getRef(R_TAGS + tagName);
			if (tagRef == null) {
				return null;
			}
			return revWalk.parseCommit(tagRef.getObjectId());
		}
	}

	/**
	 * @param featureName
	 * @param value
	 * @throws IOException
	 * @since 4.0
	 */
	public void setRemote(String featureName, String value) throws IOException {
		getConfig().setRemote(featureName, value);
	}

	/**
	 * @param featureName
	 * @param value
	 * @throws IOException
	 * @since 4.0
	 */
	public void setUpstreamBranchName(String featureName, String value) throws IOException {
		getConfig().setUpstreamBranchName(featureName, value);
	}

	/**
	 * @param featureName
	 * @return Upstream branch name
	 * @since 4.0
	 */
	public String getUpstreamBranchName(String featureName) {
		return getConfig().getUpstreamBranchName(featureName);
	}

	/**
	 * @return the configuration of this repository
	 * @since 4.0
	 */
	public GitFlowConfig getConfig() {
		return this.config;
	}
}
