/*******************************************************************************
 * Copyright (C) 2011, Dariusz Luksza <dariusz@luksza.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core.synchronize;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.egit.core.CoreText;
import org.eclipse.egit.core.synchronize.ThreeWayDiffEntry.ChangeType;
import org.eclipse.osgi.util.NLS;

/**
 * Thin cache object. It contains list of object members, object name and
 * {@link ThreeWayDiffEntry} data.
 */
class GitSyncObjectCache {

	private final String name;

	private final ThreeWayDiffEntry diffEntry;

	private Map<String, GitSyncObjectCache> members;

	/**
	 * Creates node and leaf element
	 *
	 * @param name
	 *            entry name
	 * @param diffEntry
	 *            entry meta data
	 */
	GitSyncObjectCache(String name, ThreeWayDiffEntry diffEntry) {
		this.name = name;
		this.diffEntry = diffEntry;
	}

	/**
	 * @return name of this object
	 */
	public String getName() {
		return name;
	}

	/**
	 *
	 * @return entry meta data
	 */
	public ThreeWayDiffEntry getDiffEntry() {
		return diffEntry;
	}

	/**
	 * Store given {@code entry} in cache. It assumes that parent of
	 * {@code entry} is already in cache, if not {@link RuntimeException} will
	 * be thrown.
	 *
	 * @param entry
	 *            that should be stored in cache
	 * @throws RuntimeException
	 *             when cannot find parent of given {@code entry} in cache
	 */
	public void addMember(ThreeWayDiffEntry entry) {
		String memberPath = entry.getPath();

		if (members == null)
			members = new HashMap<String, GitSyncObjectCache>();

		int start = -1;
		Map<String, GitSyncObjectCache> parent = members;
		int separatorIdx = memberPath.indexOf("/"); //$NON-NLS-1$
		while (separatorIdx > 0) {
			String key = memberPath.substring(start + 1, separatorIdx);
			GitSyncObjectCache cacheObject = parent.get(key);
			if (cacheObject == null)
				throw new RuntimeException(NLS.bind(
						CoreText.GitSyncObjectCache_noData, key));

			start = separatorIdx;
			separatorIdx = memberPath.indexOf("/", separatorIdx + 1); //$NON-NLS-1$
			if (cacheObject.members == null)
				cacheObject.members = new HashMap<String, GitSyncObjectCache>();

			parent = cacheObject.members;
		}

		String newName;
		if (start > 0)
			newName = memberPath.substring(start + 1);
		else
			newName = memberPath;

		GitSyncObjectCache obj = new GitSyncObjectCache(newName, entry);
		parent.put(newName, obj);
	}

	/**
	 * @param childPath
	 *            repository relative path of entry that should be obtained
	 * @return cached object, or {@code null} when cache doen't contain object
	 *         for given path
	 */
	public GitSyncObjectCache get(String childPath) {
		if (childPath.length() == 0)
			return this;
		if (childPath
				.substring(childPath.lastIndexOf("/") + 1, childPath.length()).equals(name)) //$NON-NLS-1$
			return this;
		if (members == null)
			return null;

		int start = -1;
		Map<String, GitSyncObjectCache> parent = members;
		int separatorIdx = childPath.indexOf("/"); //$NON-NLS-1$
		while (separatorIdx > 0) {
			String key = childPath.substring(start + 1, separatorIdx);

			GitSyncObjectCache childObject = parent.get(key);
			if (childObject == null)
				return null;

			start = separatorIdx;
			separatorIdx = childPath.indexOf("/", separatorIdx + 1); //$NON-NLS-1$
			parent = childObject.members;
			if (parent == null)
				return null;
		}

		return parent.get(childPath.subSequence(
				childPath.lastIndexOf("/") + 1, childPath.length())); //$NON-NLS-1$
	}

	/**
	 * @return number of cached members
	 */
	public int membersCount() {
		return members != null ? members.size() : 0;
	}

	/**
	 * @return list of all cached members or {@code null} when there this object
	 *         doesn't contain members
	 */
	public Collection<GitSyncObjectCache> members() {
		return members != null ? members.values() : null;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("entry: ").append(diffEntry).append("\n"); //$NON-NLS-1$ //$NON-NLS-2$
		if (members != null) {
			builder.append("members: "); //$NON-NLS-1$
			for (GitSyncObjectCache obj : members.values())
				builder.append(obj.toString()).append("\n"); //$NON-NLS-1$
		}

		return builder.toString();
	}

	void merge(GitSyncObjectCache value) {
		if (value.members != null) {
			if (members == null)
				members = new HashMap<String, GitSyncObjectCache>();
			else
				for (Entry<String, GitSyncObjectCache> entry : members
						.entrySet())
					if (!value.members.containsKey(entry.getKey()))
						entry.getValue().diffEntry.changeType = ChangeType.IN_SYNC;

			for (Entry<String, GitSyncObjectCache> entry : value.members
					.entrySet()) {
				String key = entry.getKey();
				if (members.containsKey(key))
					members.get(key).merge(entry.getValue());
				else
					members.put(key, entry.getValue());
			}
		} else if (members != null)
			for (GitSyncObjectCache obj : members.values())
				obj.diffEntry.changeType = ChangeType.IN_SYNC;
		else // we should be on leaf entry, just update the change type value
			diffEntry.changeType = value.diffEntry.changeType;
	}

}
