/*******************************************************************************
 * Copyright (C) 2010, Dariusz Luksza <dariusz@luksza.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.synchronize.model;

import static org.eclipse.compare.structuremergeviewer.Differencer.CHANGE;
import static org.eclipse.compare.structuremergeviewer.Differencer.RIGHT;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.IPath;
import org.eclipse.egit.core.synchronize.GitCommitsModelCache.Change;
import org.eclipse.egit.ui.internal.synchronize.model.GitModelCache.FileModelFactory;
import org.eclipse.jgit.lib.Repository;

/**
 * Because Git cache holds changes on file level (SHA-1 of trees are same as in
 * repository) we must emulate tree representation of staged files.
 */
public class GitModelCacheTree extends GitModelTree {

	private final FileModelFactory factory;

	private final Map<String, GitModelObject> cacheTreeMap;

	private final Repository repo;

	/**
	 * @param parent
	 *            parent object
	 * @param repo
	 *            repository associated with this object parent object
	 * @param fullPath
	 *            absolute path of object
	 * @param factory
	 */
	public GitModelCacheTree(GitModelObjectContainer parent, Repository repo,
			IPath fullPath, FileModelFactory factory) {
		super(parent, fullPath, RIGHT | CHANGE);
		this.repo = repo;
		this.factory = factory;
		cacheTreeMap = new HashMap<String, GitModelObject>();
	}

	@Override
	public GitModelObject[] getChildren() {
		Collection<GitModelObject> values = cacheTreeMap.values();

		return values.toArray(new GitModelObject[values.size()]);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
			return true;

		if (obj == null)
			return false;

		if (obj.getClass() != getClass())
			return false;

		GitModelCacheTree objTree = (GitModelCacheTree) obj;
		return path.equals(objTree.path)
				&& factory.isWorkingTree() == objTree.factory.isWorkingTree();
	}

	@Override
	public int hashCode() {
		return path.hashCode() + (factory.isWorkingTree() ? 31 : 41);
	}

	@Override
	public String toString() {
		return "GitModelCacheTree[" + getLocation() + ", isWorkingTree:" + factory.isWorkingTree() + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	/**
	 * Distinguish working tree from cached/staged tree
	 *
	 * @return {@code true} when this tree is working tree, {@code false}
	 *         when it is a cached tree
	 */
	public boolean isWorkingTree() {
		return factory.isWorkingTree();
	}

	void addChild(Change change, String nestedPath) {
		String pathKey;
		int firstSlash = nestedPath.indexOf("/"); //$NON-NLS-1$
		if (firstSlash > -1)
			pathKey = nestedPath.substring(0, firstSlash);
		else
			pathKey = nestedPath;

		IPath fullPath = getLocation().append(pathKey);
		if (nestedPath.contains("/")) { //$NON-NLS-1$
			GitModelCacheTree cacheEntry = (GitModelCacheTree) cacheTreeMap
					.get(pathKey);
			if (cacheEntry == null) {
				cacheEntry = new GitModelCacheTree(this, repo, fullPath, factory);
				cacheTreeMap.put(pathKey, cacheEntry);
			}
			cacheEntry.addChild(change, nestedPath.substring(firstSlash + 1));
		} else
			cacheTreeMap.put(pathKey,
					factory.createFileModel(this, repo, change, fullPath));
	}

}
