/*******************************************************************************
 * Copyright (C) 2015, Obeo.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core.internal.merge;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.core.resources.IResource;
import org.eclipse.egit.core.internal.storage.GitLocalResourceVariant;
import org.eclipse.egit.core.internal.storage.IndexResourceVariant;
import org.eclipse.egit.core.internal.util.ResourceUtil;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.team.core.variants.IResourceVariantTree;

/**
 * This will populate its three {@link IResourceVariantTree} by looking up
 * information within the repository's DirCache.
 * <p>
 * Files that are not located within the workspace will be ignored and thus will
 * not be accessible through the trees created by this provider.
 * </p>
 */
public class DirCacheResourceVariantTreeProvider implements
		GitResourceVariantTreeProvider {
	private final IResourceVariantTree baseTree;

	private final IResourceVariantTree sourceTree;

	private final IResourceVariantTree remoteTree;

	private final Set<IResource> roots;

	private final Set<IResource> knownResources;

	/**
	 * Constructs the resource variant trees by iterating over the given
	 * repository's DirCache entries.
	 *
	 * @param repository
	 *            The repository which DirCache info we need to cache as
	 *            IResourceVariantTrees.
	 * @param useWorkspace
	 *            Whether we should use local data instead of what's in the
	 *            index for our side.
	 * @throws IOException
	 *             if we somehow cannot read the DirCache.
	 */
	public DirCacheResourceVariantTreeProvider(Repository repository,
			boolean useWorkspace)
			throws IOException {
		final DirCache cache = repository.readDirCache();
		final GitResourceVariantCache baseCache = new GitResourceVariantCache();
		final GitResourceVariantCache sourceCache = new GitResourceVariantCache();
		final GitResourceVariantCache remoteCache = new GitResourceVariantCache();

		for (int i = 0; i < cache.getEntryCount(); i++) {
			final DirCacheEntry entry = cache.getEntry(i);
			final IResource resource = ResourceUtil
					.getResourceHandleForLocation(
							repository,
							entry.getPathString(),
							FileMode.fromBits(entry.getRawMode()) == FileMode.TREE);
			// Resource variants only make sense for IResources. Do not consider
			// files outside of the workspace or otherwise non accessible.
			if (resource == null || resource.getProject() == null
					|| !resource.getProject().isAccessible()) {
				continue;
			}
			switch (entry.getStage()) {
			case DirCacheEntry.STAGE_0:
				if (useWorkspace) {
					sourceCache.setVariant(resource,
							new GitLocalResourceVariant(resource));
				} else {
					sourceCache.setVariant(resource,
							IndexResourceVariant.create(repository, entry));
				}
				baseCache.setVariant(resource,
						IndexResourceVariant.create(repository, entry));
				remoteCache.setVariant(resource,
						IndexResourceVariant.create(repository, entry));
				break;
			case DirCacheEntry.STAGE_1:
				baseCache.setVariant(resource,
						IndexResourceVariant.create(repository, entry));
				break;
			case DirCacheEntry.STAGE_2:
				if (useWorkspace) {
					sourceCache.setVariant(resource,
							new GitLocalResourceVariant(resource));
				} else {
					sourceCache.setVariant(resource,
							IndexResourceVariant.create(repository, entry));
				}
				break;
			case DirCacheEntry.STAGE_3:
				remoteCache.setVariant(resource,
						IndexResourceVariant.create(repository, entry));
				break;
			default:
				throw new IllegalStateException(
						"Invalid stage: " + entry.getStage()); //$NON-NLS-1$
			}
		}

		baseTree = new GitCachedResourceVariantTree(baseCache);
		sourceTree = new GitCachedResourceVariantTree(sourceCache);
		remoteTree = new GitCachedResourceVariantTree(remoteCache);

		roots = new LinkedHashSet<IResource>();
		roots.addAll(baseCache.getRoots());
		roots.addAll(sourceCache.getRoots());
		roots.addAll(remoteCache.getRoots());

		knownResources = new LinkedHashSet<IResource>();
		knownResources.addAll(baseCache.getKnownResources());
		knownResources.addAll(sourceCache.getKnownResources());
		knownResources.addAll(remoteCache.getKnownResources());
	}

	public IResourceVariantTree getBaseTree() {
		return baseTree;
	}

	public IResourceVariantTree getRemoteTree() {
		return remoteTree;
	}

	public IResourceVariantTree getSourceTree() {
		return sourceTree;
	}

	public Set<IResource> getKnownResources() {
		return knownResources;
	}

	public Set<IResource> getRoots() {
		return roots;
	}
}
