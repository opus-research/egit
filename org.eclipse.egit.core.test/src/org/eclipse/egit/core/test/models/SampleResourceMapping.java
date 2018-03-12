/*******************************************************************************
 * Copyright (C) 2014 Obeo and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core.test.models;

import java.util.LinkedHashSet;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.mapping.ResourceMapping;
import org.eclipse.core.resources.mapping.ResourceMappingContext;
import org.eclipse.core.resources.mapping.ResourceTraversal;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

/**
 * This resource mapping will consider all files of extension
 * {@link SampleModelProvider#SAMPLE_FILE_EXTENSION} in a given folder to be
 * part of the same model.
 */
public class SampleResourceMapping extends ResourceMapping {
	private final IFile file;

	private final String providerId;

	public SampleResourceMapping(IFile file, String providerId) {
		this.file = file;
		this.providerId = providerId;
	}

	@Override
	public Object getModelObject() {
		return file;
	}

	@Override
	public String getModelProviderId() {
		return providerId;
	}

	@Override
	public ResourceTraversal[] getTraversals(
			ResourceMappingContext context, IProgressMonitor monitor)
			throws CoreException {
		Set<IFile> sampleSiblings = new LinkedHashSet<IFile>();
		for (IResource res : file.getParent().members()) {
			if (res instanceof IFile && SampleModelProvider.SAMPLE_FILE_EXTENSION.equals(res.getFileExtension())) {
				sampleSiblings.add((IFile) res);
			}
		}
		final IResource[] resourceArray = sampleSiblings
				.toArray(new IResource[sampleSiblings.size()]);
		return new ResourceTraversal[] { new ResourceTraversal(
				resourceArray, IResource.DEPTH_ONE, IResource.NONE), };
	}

	@Override
	public IProject[] getProjects() {
		return new IProject[] { file.getProject(), };
	}
}
