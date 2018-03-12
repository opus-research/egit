/*******************************************************************************
 * Copyright (C) 2011, 2012 Dariusz Luksza <dariusz@luksza.org> and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core.synchronize;

import static org.eclipse.jgit.lib.ObjectId.zeroId;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.team.core.variants.CachedResourceVariant;

abstract class GitRemoteResource extends CachedResourceVariant {

	private final String path;

	private final RevCommit commitId;

	private final ObjectId objectId;

	private final PersonIdent authorIdent;

	GitRemoteResource(RevCommit commitId, ObjectId objectId, String path) {
		this.path = path;
		this.objectId = objectId;
		this.commitId = commitId;
		this.authorIdent = commitId.getAuthorIdent();
	}

	public String getName() {
		int lastSeparator = path.lastIndexOf("/"); //$NON-NLS-1$
		return path.substring(lastSeparator + 1, path.length());
	}

	public String getContentIdentifier() {
		StringBuilder s = new StringBuilder();
		s.append(commitId.abbreviate(7).name());
		s.append("..."); //$NON-NLS-1$
		if (authorIdent != null) {
			s.append(" ("); //$NON-NLS-1$
			s.append(authorIdent.getName());
			s.append(")"); //$NON-NLS-1$
		}
		return s.toString();
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
