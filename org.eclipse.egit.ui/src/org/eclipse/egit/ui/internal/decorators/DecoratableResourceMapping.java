/*******************************************************************************
 * Copyright (C) 2011, Markus Duft <markus.duft@salomon.at>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.egit.ui.internal.decorators;

import static org.eclipse.jgit.lib.Repository.stripWorkDir;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.mapping.ResourceMapping;
import org.eclipse.core.runtime.IPath;
import org.eclipse.egit.core.internal.indexdiff.IndexDiffData;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.egit.ui.internal.resources.ResourceStateFactory;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.ui.IWorkingSet;

/**
 * Represents a decoratable resource mapping (i.e. a group of resources).
 */
public class DecoratableResourceMapping extends DecoratableResource {

	private static final String MULTIPLE = "*"; //$NON-NLS-1$

	/**
	 * Denotes the type of decoratable resource, used by the decoration helper.
	 */
	public static final int RESOURCE_MAPPING = 0x10;

	/**
	 * Denotes the type of decoratable resource, used by the decoration helper.
	 */
	public static final int WORKING_SET = 0x9999;

	/**
	 * Stores the actual mapping we are currently decorating.
	 */
	private ResourceMapping mapping;

	/**
	 * Creates a decoratable resource mapping (used for e.g. working sets)
	 *
	 * @param mapping the resource mapping to decorate
	 * @throws IOException
	 */
	public DecoratableResourceMapping(ResourceMapping mapping) throws IOException {
		super(null); // no resource ...

		this.mapping = mapping;
		IProject[] projects = mapping.getProjects();

		if(projects == null || projects.length == 0)
			return;

		// collect repositories to allow decoration of mappings (bug 369969)
		Set<Repository> repositories = new HashSet<>(projects.length);

		// we could use DecoratableResourceAdapter for each project, but that would be too much overhead,
		// as we need only very little information at all...
		for(IProject prj : projects) {
			RepositoryMapping repoMapping = RepositoryMapping.getMapping(prj);
			if(repoMapping == null)
				continue;

			IndexDiffData diffData = ResourceStateFactory.getInstance()
					.getIndexDiffDataOrNull(prj);
			if(diffData == null)
				continue;

			// at least one contained resource is tracked for sure here.
			setTracked(true);

			Repository repository = repoMapping.getRepository();
			String repoRelative = makeRepoRelative(repository, prj);
			if (repoRelative == null) {
				continue;
			}
			repoRelative += "/"; //$NON-NLS-1$

			Set<String> modified = diffData.getModified();
			Set<String> conflicting = diffData.getConflicting();

			// attention - never reset these to false (so don't use the return value of the methods!)
			if(containsPrefix(modified, repoRelative))
				setDirty(true);

			if(containsPrefix(conflicting, repoRelative))
				setConflicts(true);

			// collect repository
			repositories.add(repository);
		}

		// collect repository info for decoration (bug 369969)
		if(repositories.size() == 1) {
			// single repo, single branch --> [repo branch]
			Repository repository = repositories.iterator().next();
			repositoryName = DecoratableResourceHelper
					.getRepositoryName(repository);
			branch = DecoratableResourceHelper.getShortBranch(repository);
			branchStatus = DecoratableResourceHelper
					.getBranchStatus(repository);
		} else if(repositories.size() > 1) {
			// collect branch names but skip branch status (doesn't make sense)
			Set<String> branches = new HashSet<>(2);
			for (Repository repository : repositories) {
				branches.add(DecoratableResourceHelper
						.getShortBranch(repository));
			    if (branches.size() > 1)
			        break;
			}

			// multiple repos, one branch --> [* branch]
			if (branches.size() == 1) {
				repositoryName = MULTIPLE;
				branch = branches.iterator().next();
			}

			// we set nothing in the following case:
			//   multiple repos, multiple branches
		}
	}

	@Override
	public int getType() {
		if (mapping.getModelObject() instanceof IWorkingSet)
			return WORKING_SET;
		return RESOURCE_MAPPING;
	}

	@Override
	public String getName() {
		// TODO: check whether something other than a WorkingSet can
		//       appear here, and calculate a proper name for it.
		if(mapping.getModelObject() instanceof IWorkingSet) {
			IWorkingSet ws = (IWorkingSet)mapping.getModelObject();
			return ws.getLabel();
		}

		return "<unknown>"; //$NON-NLS-1$
	}

	@Nullable
	private String makeRepoRelative(Repository repository, IResource res) {
		if (repository.isBare()) {
			return null;
		}
		IPath location = res.getLocation();
		if (location == null) {
			return null;
		}
		return stripWorkDir(repository.getWorkTree(), location.toFile());
	}

	private boolean containsPrefix(Set<String> collection, String prefix) {
		// when prefix is empty we are handling repository root, therefore we
		// should return true whenever collection isn't empty
		if (prefix.length() == 1 && !collection.isEmpty())
			return true;

		for (String path : collection)
			if (path.startsWith(prefix))
				return true;
		return false;
	}
}
