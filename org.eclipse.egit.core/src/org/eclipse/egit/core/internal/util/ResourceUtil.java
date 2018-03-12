/*******************************************************************************
 * Copyright (C) 2011, Jens Baumgart <jens.baumgart@sap.com>
 * Copyright (C) 2012, 2013 Robin Stocker <robin@nibor.org>
 * Copyright (C) 2012, 2013 Laurent Goubet <laurent.goubet@obeo.fr>
 * Copyright (C) 2012, Gunnar Wagenknecht <gunnar@wagenknecht.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core.internal.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.mapping.IModelProviderDescriptor;
import org.eclipse.core.resources.mapping.ModelProvider;
import org.eclipse.core.resources.mapping.ResourceMapping;
import org.eclipse.core.resources.mapping.ResourceMappingContext;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.egit.core.Activator;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.jgit.lib.Repository;

/**
 * Resource utilities
 *
 */
public class ResourceUtil {

	/**
	 * Return the corresponding resource if it exists.
	 * <p>
	 * The returned file will be relative to the most nested non-closed project.
	 *
	 * @param location
	 *            the path to check
	 * @return the resources, or null
	 */
	public static IResource getResourceForLocation(IPath location) {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		URI uri = URIUtil.toURI(location);
		IFile file = getFileForLocationURI(root, uri);
		if (file != null)
			return file;
		return getContainerForLocationURI(root, uri);
	}

	/**
	 * Return the corresponding file if it exists.
	 * <p>
	 * The returned file will be relative to the most nested non-closed project.
	 *
	 * @param location
	 * @return the file, or null
	 */
	public static IFile getFileForLocation(IPath location) {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		URI uri = URIUtil.toURI(location);
		return getFileForLocationURI(root, uri);
	}

	/**
	 * Return the corresponding container if it exists.
	 * <p>
	 * The returned container will be relative to the most nested non-closed project.
	 *
	 * @param location
	 * @return the container, or null
	 */
	public static IContainer getContainerForLocation(IPath location) {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		URI uri = URIUtil.toURI(location);
		return getContainerForLocationURI(root, uri);
	}

	/**
	 * Get the {@link IFile} corresponding to the arguments if it exists.
	 * <p>
	 * The returned file will be relative to the most nested non-closed project.
	 *
	 * @param repository
	 *            the repository of the file
	 * @param repoRelativePath
	 *            the repository-relative path of the file to search for
	 * @return the IFile corresponding to this path, or null
	 */
	public static IFile getFileForLocation(Repository repository,
			String repoRelativePath) {
		IPath path = new Path(repository.getWorkTree().getAbsolutePath()).append(repoRelativePath);
		return getFileForLocation(path);
	}

	/**
	 * The method splits the given resources by their repository. For each
	 * occurring repository a list is built containing the repository relative
	 * paths of the related resources.
	 * <p>
	 * When one of the passed resources corresponds to the working directory,
	 * <code>""</code> will be returned as part of the collection.
	 *
	 * @param resources
	 * @return a map containing a list of repository relative paths for each
	 *         occurring repository
	 */
	public static Map<Repository, Collection<String>> splitResourcesByRepository(
			Collection<IResource> resources) {
		Map<Repository, Collection<String>> result = new HashMap<Repository, Collection<String>>();
		for (IResource resource : resources) {
			RepositoryMapping repositoryMapping = RepositoryMapping
					.getMapping(resource);
			if (repositoryMapping == null)
				continue;
			String path = repositoryMapping.getRepoRelativePath(resource);
			addPathToMap(repositoryMapping, path, result);
		}
		return result;
	}

	/**
	 * @see #splitResourcesByRepository(Collection)
	 * @param resources
	 * @return a map containing a list of repository relative paths for each
	 *         occurring repository
	 */
	public static Map<Repository, Collection<String>> splitResourcesByRepository(
			IResource[] resources) {
		return splitResourcesByRepository(Arrays.asList(resources));
	}

	/**
	 * The method splits the given paths by their repository. For each occurring
	 * repository a list is built containing the repository relative paths of
	 * the related resources.
	 * <p>
	 * When one of the passed paths corresponds to the working directory,
	 * <code>""</code> will be returned as part of the collection.
	 *
	 * @param paths
	 * @return a map containing a list of repository relative paths for each
	 *         occurring repository
	 */
	public static Map<Repository, Collection<String>> splitPathsByRepository(
			Collection<IPath> paths) {
		Map<Repository, Collection<String>> result = new HashMap<Repository, Collection<String>>();
		for (IPath path : paths) {
			RepositoryMapping repositoryMapping = RepositoryMapping.getMapping(path);
			if (repositoryMapping == null)
				continue;
			String p = repositoryMapping.getRepoRelativePath(path);
			addPathToMap(repositoryMapping, p, result);
		}
		return result;
	}

	/**
	 * Determine if given resource is imported into workspace or not
	 *
	 * @param resource
	 * @return {@code true} when given resource is not imported into workspace,
	 *         {@code false} otherwise
	 */
	public static boolean isNonWorkspace(IResource resource) {
		return resource.getLocation() == null;
	}

	private static IFile getFileForLocationURI(IWorkspaceRoot root, URI uri) {
		IFile[] files = root.findFilesForLocationURI(uri);
		return getExistingResourceWithShortestPath(files);
	}

	private static IContainer getContainerForLocationURI(IWorkspaceRoot root,
			URI uri) {
		IContainer[] containers = root.findContainersForLocationURI(uri);
		return getExistingResourceWithShortestPath(containers);
	}

	private static <T extends IResource> T getExistingResourceWithShortestPath(
			T[] resources) {
		int shortestPathSegmentCount = Integer.MAX_VALUE;
		T shortestPath = null;
		for (T resource : resources) {
			if (!resource.exists())
				continue;
			IPath fullPath = resource.getFullPath();
			int segmentCount = fullPath.segmentCount();
			if (segmentCount < shortestPathSegmentCount) {
				shortestPath = resource;
				shortestPathSegmentCount = segmentCount;
			}
		}
		return shortestPath;
	}

	private static void addPathToMap(RepositoryMapping repositoryMapping,
			String path, Map<Repository, Collection<String>> result) {
		if (path != null) {
			Repository repository = repositoryMapping.getRepository();
			Collection<String> resourcesList = result.get(repository);
			if (resourcesList == null) {
				resourcesList = new ArrayList<String>();
				result.put(repository, resourcesList);
			}
			resourcesList.add(path);
		}
	}

	/**
	 * This will query all model providers for those that are enabled on the
	 * given resource and list all mappings available for that resource.
	 *
	 * @param resource
	 *            The resource for which we need the associated resource
	 *            mappings.
	 * @param context
	 *            Context from which remote content could be retrieved.
	 * @return All mappings available for that file.
	 */
	public static ResourceMapping[] getResourceMappings(IResource resource,
			ResourceMappingContext context) {
		final IModelProviderDescriptor[] modelDescriptors = ModelProvider
				.getModelProviderDescriptors();

		final Set<ResourceMapping> mappings = new LinkedHashSet<ResourceMapping>();
		for (IModelProviderDescriptor candidate : modelDescriptors) {
			try {
				final IResource[] resources = candidate
						.getMatchingResources(new IResource[] { resource, });
				if (resources.length > 0) {
					// get mappings from model provider if there are matching resources
					final ModelProvider model = candidate.getModelProvider();
					final ResourceMapping[] modelMappings = model.getMappings(
							resource, context, new NullProgressMonitor());
					for (ResourceMapping mapping : modelMappings)
						mappings.add(mapping);
				}
			} catch (CoreException e) {
				Activator.logError(e.getMessage(), e);
			}
		}
		return mappings.toArray(new ResourceMapping[mappings.size()]);
	}
}
