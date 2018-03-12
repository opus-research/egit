/*******************************************************************************
 * Copyright (C) 2009, Mykola Nikishov <mn@mn.com.ua>
 * Copyright (C) 2011, Robin Stocker <robin@nibor.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.egit.core.op.CloneOperation;
import org.eclipse.egit.core.op.ConnectProviderOperation;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.osgi.util.NLS;
import org.eclipse.team.core.ProjectSetCapability;
import org.eclipse.team.core.ProjectSetSerializationContext;
import org.eclipse.team.core.TeamException;

/**
 * Serializer/deserializer for references to projects.
 */
public final class GitProjectSetCapability extends ProjectSetCapability {

	// TODO: CVS and Subclipse use "," here, should we do the same?
	private static final String SEPARATOR = "|"; //$NON-NLS-1$

	private final class ProjectReferenceComparator implements
			Comparator<ProjectReference> {
		public int compare(ProjectReference o1, ProjectReference o2) {
			final boolean reposEqual = o1.repository.equals(o2.repository);
			final boolean branchesEqual = o1.branch
					.equals(o2.branch);
			final boolean projectDirsEqual = o1.projectDir
					.equals(o2.projectDir);
			return reposEqual && branchesEqual && projectDirsEqual ? 0 : 1;
		}
	}

	final class ProjectReference {
		/** Separator char */
		private static final String SEP_RE = "\\|"; //$NON-NLS-1$

		private static final String DEFAULT_BRANCH = Constants.MASTER;

		/**
		 * a relative path (from the repository root) to a project
		 */
		String projectDir;

		/**
		 * <code>repository</code> parameter
		 */
		URIish repository;

		/**
		 * the remote branch that will be checked out, see <code>--branch</code>
		 * option
		 */
		String branch = DEFAULT_BRANCH;

		/**
		 * use this name instead of using the remote name origin to keep track
		 * of the upstream repository, see <code>--origin</code> option.
		 */
		String origin = Constants.DEFAULT_REMOTE_NAME;

		@SuppressWarnings("boxing")
		ProjectReference(final String reference) throws URISyntaxException, IllegalArgumentException {
			final String[] tokens = reference.split(SEP_RE);
			if (tokens.length != 3) {
				throw new IllegalArgumentException(NLS.bind(
						CoreText.PsfReference_InvalidTokensCount, new Object[] {
								3, tokens.length, tokens }));
			}
			this.repository = new URIish(tokens[0]);
			if (!"".equals(tokens[1])) { //$NON-NLS-1$
				this.branch = tokens[1];
			}
			this.projectDir = tokens[2];
		}
	}

	@Override
	public String[] asReference(IProject[] projects,
			ProjectSetSerializationContext context, IProgressMonitor monitor)
			throws TeamException {
		String[] references = new String[projects.length];
		for (int i = 0; i < projects.length; i++) {
			// TODO: Should we catch TeamException here and proceed with the rest of the projects?
			references[i] = asReference(projects[i]);
		}
		return references;
	}

	private String asReference(IProject project) throws TeamException {
		RepositoryMapping mapping = RepositoryMapping.getMapping(project);
		String branch;
		try {
			branch = mapping.getRepository().getBranch();
		} catch (IOException e) {
			throw new TeamException("Could not get branch", e); //$NON-NLS-1$
		}
		StoredConfig config = mapping.getRepository().getConfig();
		String remote = config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, branch, ConfigConstants.CONFIG_KEY_REMOTE);
		String url = config.getString(ConfigConstants.CONFIG_REMOTE_SECTION, remote, ConfigConstants.CONFIG_KEY_URL);
		if (url == null) {
			throw new TeamException("Could not get remote"); //$NON-NLS-1$
		}

		String projectPath = mapping.getRepoRelativePath(project);
		if (projectPath.equals("")) { //$NON-NLS-1$
			projectPath = "."; //$NON-NLS-1$
		}

		StringBuilder sb = new StringBuilder();

		// TODO: Should we prepend a version string, as CVS and Subclipse do?
		sb.append(url);
		sb.append(SEPARATOR);
		sb.append(branch);
		sb.append(SEPARATOR);
		sb.append(projectPath);

		return sb.toString();
	}

	@Override
	public IProject[] addToWorkspace(final String[] referenceStrings,
			final ProjectSetSerializationContext context,
			final IProgressMonitor monitor) throws TeamException {
		final Map<URIish, Map<String, Set<ProjectReference>>> repositories = new HashMap<URIish, Map<String, Set<ProjectReference>>>();
		for (final String reference : referenceStrings) {
			try {
				final ProjectReference projectReference = new ProjectReference(
						reference);
				Map<String, Set<ProjectReference>> repositoryBranches = repositories
						.get(projectReference.repository);
				if (repositoryBranches == null) {
					repositoryBranches = new HashMap<String, Set<ProjectReference>>();
					repositories.put(projectReference.repository,
							repositoryBranches);
				}
				Set<ProjectReference> projectReferences = repositoryBranches.get(projectReference.branch);
				if (projectReferences == null) {
					projectReferences = new TreeSet<ProjectReference>(new ProjectReferenceComparator());
					repositoryBranches.put(projectReference.branch, projectReferences);
				}

				projectReferences.add(projectReference);
			} catch (final IllegalArgumentException e) {
				throw new TeamException(reference, e);
			} catch (final URISyntaxException e) {
				throw new TeamException(reference, e);
			}
		}
		final ArrayList<IProject> importedProjects = new ArrayList<IProject>();
		for (final URIish gitUrl : repositories.keySet()) {
			Map<String, Set<ProjectReference>> branches = repositories
					.get(gitUrl);
			for (final String branch : branches.keySet()) {
				final Set<ProjectReference> projects = branches.get(branch);
				try {
					final IPath workDir = getWorkingDir(gitUrl, branch,
							branches.keySet());
					if (workDir.toFile().exists()) {
						final Collection<String> projectNames = new LinkedList<String>();
						for (final ProjectReference projectReference : projects) {
							projectNames.add(projectReference.projectDir);
						}
						throw new TeamException(NLS.bind(
								CoreText.PsfImport_CloneToExistingDirectory,
								new Object[] { workDir, projectNames, gitUrl }));
					}
					// TODO: Where to get the timeout?
					int timeout = 0;
					String refName = Constants.R_HEADS + branch;
					final CloneOperation cloneOperation = new CloneOperation(
							gitUrl, true, null, workDir.toFile(), refName,
							Constants.DEFAULT_REMOTE_NAME, timeout);
					cloneOperation.run(monitor);

					// import projects from the current repository to workspace
					final IWorkspace workspace = ResourcesPlugin.getWorkspace();
					final IWorkspaceRoot root = workspace.getRoot();
					for (final ProjectReference projectToImport : projects) {
						final IPath projectDir = workDir
								.append(projectToImport.projectDir);
						final IProjectDescription projectDescription = workspace
								.loadProjectDescription(projectDir
										.append(IProjectDescription.DESCRIPTION_FILE_NAME));
						final IProject project = root
								.getProject(projectDescription.getName());
						project.create(projectDescription, monitor);
						importedProjects.add(project);

						project.open(monitor);
						final File repositoryPath = workDir.append(
								Constants.DOT_GIT_EXT).toFile();
						final ConnectProviderOperation connectProviderOperation = new ConnectProviderOperation(
								project, repositoryPath);
						connectProviderOperation.execute(monitor);
					}
				} catch (final InvocationTargetException e) {
					throw TeamException.asTeamException(e);
				} catch (final CoreException e) {
					throw TeamException.asTeamException(e);
				} catch (final InterruptedException e) {
					// was canceled by user
					return new IProject[0];
				}
			}
		}
		final IProject[] result = importedProjects
				.toArray(new IProject[importedProjects.size()]);
		return result;
	}

	/**
	 * @param gitUrl
	 * @param branch the branch to check out
	 * @param allBranches all branches which should be checked out for this gitUrl
	 * @return the directory where the project should be checked out
	 */
	private static IPath getWorkingDir(URIish gitUrl, String branch, Set<String> allBranches) {
		// TODO ask user where to checkout
		final IPath workspaceLocation = ResourcesPlugin.getWorkspace()
				.getRoot().getRawLocation();
		final String humanishName = gitUrl.getHumanishName();
		String extendedName;
		if (allBranches.size() == 1 || branch.equals(Constants.MASTER)) {
			extendedName = humanishName;
		} else {
			extendedName = humanishName + "_" + branch; //$NON-NLS-1$
		}
		final IPath workDir = workspaceLocation.append(extendedName);
		return workDir;
	}

}
