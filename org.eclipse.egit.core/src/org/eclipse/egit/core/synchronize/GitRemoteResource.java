/*******************************************************************************
 * Copyright (C) 2011, Dariusz Luksza <dariusz@luksza.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core.synchronize;

import static org.eclipse.jgit.lib.ObjectId.zeroId;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.team.core.variants.CachedResourceVariant;

abstract class GitRemoteResource extends CachedResourceVariant {

	private final String path;

	private final RevCommit commitId;

	private final ObjectId objectId;

	GitRemoteResource(RevCommit commitId, ObjectId objectId, String path) {
		this.path = path;
		this.objectId = objectId;
		this.commitId = commitId;
	}

	public String getName() {
		int lastSeparator = path.lastIndexOf("/"); //$NON-NLS-1$
		return path.substring(lastSeparator + 1, path.length());
	}

	public String getContentIdentifier() {
		return commitId.abbreviate(7).name() + "..."; //$NON-NLS-1$
	}

	public byte[] asBytes() {
		return getObjectId().name().getBytes();
	}

	@Override
	protected String getCachePath() {
		return path;
	}

	@Override
	protected String getCacheId() {
		return "org.eclipse.egit"; //$NON-NLS-1$
	}

	boolean exists() {
		return commitId != null;
	}

	RevCommit getCommitId() {
		return commitId;
	}

	/**
	 * @return object id, or {code {@link RevCommit#zeroId()} if object doesn't
	 *         exist in repository
	 */
	ObjectId getObjectId() {
		return objectId != null ? objectId : zeroId();
	}

	String getPath() {
		return path;
	}

}
