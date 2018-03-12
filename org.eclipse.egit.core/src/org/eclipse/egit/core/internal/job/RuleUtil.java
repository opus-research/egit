/*******************************************************************************
 * Copyright (C) 2011, Jens Baumgart <jens.baumgart@sap.com>
 * Copyright (C) 2012, Robin Stocker <robin@nibor.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core.internal.job;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceRuleFactory;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.MultiRule;
import org.eclipse.egit.core.internal.util.ResourceUtil;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.jgit.lib.Repository;

/**
 * Utility class for scheduling rules
 *
 */
public class RuleUtil {

	/**
	 * Calculates an {@link ISchedulingRule} for Jobs working on the index of a
	 * repository. The rule consists of all projects belonging to the
	 * repository. If you schedule two jobs working on different resources of
	 * the same repository you have to ensure that these jobs cannot run in
	 * parallel because the Git index lock fails if another thread is already
	 * owning the lock. Thus it is a good practice to lock all projects
	 * belonging to a Git repository when executing a write operation on the
	 * index of a repository in a Job.
	 *
	 * @param repository
	 * @return scheduling rule
	 */
	public static ISchedulingRule getRule(Repository repository) {
		IProject[] projects = getProjects(repository);
		if (projects.length == 0)
			return null;
		return new MultiRule(projects);
	}

	/**
	 * Calculates a {@link ISchedulingRule} for all repositories related to the
	 * given resources.
	 *
	 * @see RuleUtil#getRule(Repository)
	 * @param resources
	 * @return scheduling rule
	 */
	public static ISchedulingRule getRuleForRepositories(IResource[] resources) {
		ISchedulingRule result = null;
		Set<Repository> repositories = new HashSet<Repository>();
		for (IResource resource : resources) {
			RepositoryMapping mapping = RepositoryMapping.getMapping(resource);
			if (mapping != null)
				repositories.add(mapping.getRepository());
		}
		for (Repository repository : repositories) {
			ISchedulingRule rule = getRule(repository);
			result = MultiRule.combine(result, rule);
		}
		return result;
	}

	/**
	 * Calculates a {@link ISchedulingRule} for all containers of the paths that
	 * are in the workspace.
	 *
	 * @param paths
	 * @return scheduling rule
	 */
	public static ISchedulingRule getRuleForContainers(Collection<IPath> paths) {
		List<ISchedulingRule> rules = new ArrayList<ISchedulingRule>();
		IResourceRuleFactory ruleFactory = ResourcesPlugin.getWorkspace()
				.getRuleFactory();
		for (IPath path : paths) {
			IResource resource = ResourceUtil.getResourceForLocation(path);
			if (resource != null) {
				IContainer container = resource.getParent();
				ISchedulingRule rule = ruleFactory.modifyRule(container);
				if (rule != null)
					rules.add(rule);
			}
		}
		if (rules.size() == 0)
			return null;
		else
			return new MultiRule(rules.toArray(new ISchedulingRule[rules.size()]));
	}

	private static IProject[] getProjects(Repository repository) {
		final IProject[] projects = ResourcesPlugin.getWorkspace().getRoot()
				.getProjects();
		List<IProject> result = new ArrayList<IProject>();
		final File parentFile = repository.getWorkTree();
		for (IProject p : projects) {
			IPath projectLocation = p.getLocation();
			if (projectLocation == null)
				continue;
			if (projectLocation.toFile().getAbsolutePath()
					.startsWith(parentFile.getAbsolutePath()))
				result.add(p);
		}
		return result.toArray(new IProject[result.size()]);
	}

}
