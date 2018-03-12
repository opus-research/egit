/*******************************************************************************
 * Copyright (C) 2012, Robin Stocker <robin@nibor.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.egit.core.internal.util.ProjectUtil;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.Saveable;

/**
 * Filter which checks if the path/resources behind the saveable is part of the
 * work dir of the repository.
 */
public class RepositorySaveableFilter extends SaveFilter {

	private final IPath workDir;

	/**
	 * @param repository
	 *            to check
	 */
	public RepositorySaveableFilter(Repository repository) {
		super(ProjectUtil.getProjects(repository));
		this.workDir = new Path(repository.getWorkTree().getAbsolutePath());
	}

	public boolean select(Saveable saveable, IWorkbenchPart[] containingParts) {
		boolean selected = super.select(saveable, containingParts);
		if (!selected)
			return isTextFileBufferInWorkDir(saveable);
		return selected;
	}

	private boolean isTextFileBufferInWorkDir(Saveable saveable) {
		IDocument document = (IDocument) saveable.getAdapter(IDocument.class);
		if (document == null)
			return true; // be conservative and assume this needs to be saved
		ITextFileBuffer textFileBuffer = FileBuffers.getTextFileBufferManager()
				.getTextFileBuffer(document);
		if (textFileBuffer != null)
			return isInWorkDir(textFileBuffer.getLocation());
		return false;
	}

	private boolean isInWorkDir(IPath location) {
		return location != null && workDir.isPrefixOf(location);
	}
}