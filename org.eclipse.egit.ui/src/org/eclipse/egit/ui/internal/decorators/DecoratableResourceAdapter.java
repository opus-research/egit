/*******************************************************************************
 * Copyright (C) 2007, IBM Corporation and others
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2008, Google Inc.
 * Copyright (C) 2008, Tor Arne Vestbø <torarnv@gmail.com>
 * Copyright (C) 2011, Dariusz Luksza <dariusz@luksza.org>
 * Copyright (C) 2011, Christian Halstrick <christian.halstrick@sap.com>
 * Copyright (C) 2015, Thomas Wolf <thomas.wolf@paranor.ch>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.egit.ui.internal.decorators;

import java.io.IOException;

import org.eclipse.core.resources.IResource;
import org.eclipse.egit.core.internal.indexdiff.IndexDiffData;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.egit.ui.internal.resources.IResourceState;
import org.eclipse.egit.ui.internal.resources.ResourceStateFactory;
import org.eclipse.egit.ui.internal.trace.GitTraceLocation;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.lib.Repository;

class DecoratableResourceAdapter extends DecoratableResource {

	public DecoratableResourceAdapter(@NonNull IndexDiffData indexDiffData,
			@NonNull IResource resourceToWrap)
			throws IOException {
		super(resourceToWrap);
		boolean trace = GitTraceLocation.DECORATION.isActive();
		long start = 0;
		if (trace) {
			GitTraceLocation.getTrace().trace(
					GitTraceLocation.DECORATION.getLocation(),
					"Decorate " + resource.getFullPath()); //$NON-NLS-1$
			start = System.currentTimeMillis();
		}
		try {
			RepositoryMapping mapping = RepositoryMapping
					.getMapping(resourceToWrap);
			if (mapping == null) {
				return;
			}
			Repository repository = mapping.getRepository();
			if (repository == null) {
				return;
			}
			IResourceState baseState = ResourceStateFactory.getInstance()
					.get(indexDiffData, resourceToWrap);
			setTracked(baseState.isTracked());
			setIgnored(baseState.isIgnored());
			setDirty(baseState.isDirty());
			setConflicts(baseState.hasConflicts());
			setAssumeValid(baseState.isAssumeValid());
			setStaged(baseState.staged());
			if (resource.getType() == IResource.PROJECT) {
				// We only need this very expensive info for project decoration
				repositoryName = DecoratableResourceHelper
						.getRepositoryName(repository);
				branch = DecoratableResourceHelper.getShortBranch(repository);
				branchStatus = DecoratableResourceHelper.getBranchStatus(repository);
			}
		} finally {
			if (trace)
				GitTraceLocation
						.getTrace()
						.trace(GitTraceLocation.DECORATION.getLocation(),
								"Decoration took " + (System.currentTimeMillis() - start) //$NON-NLS-1$
										+ " ms"); //$NON-NLS-1$
		}
	}

	@Override
	public String toString() {
		return "DecoratableResourceAdapter[" + getName() //$NON-NLS-1$
				+ (isTracked() ? ", tracked" : "") //$NON-NLS-1$ //$NON-NLS-2$
				+ (isIgnored() ? ", ignored" : "") //$NON-NLS-1$ //$NON-NLS-2$
				+ (isDirty() ? ", dirty" : "") //$NON-NLS-1$//$NON-NLS-2$
				+ (hasConflicts() ? ",conflicts" : "")//$NON-NLS-1$//$NON-NLS-2$
				+ ", staged=" + staged() //$NON-NLS-1$
				+ "]"; //$NON-NLS-1$
	}

}
