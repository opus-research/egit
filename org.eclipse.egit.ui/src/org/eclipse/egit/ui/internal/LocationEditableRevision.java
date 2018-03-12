/*******************************************************************************
 * Copyright (C) 2013, Robin Stocker <robin@nibor.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.egit.ui.Activator;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.team.core.history.IFileRevision;

/**
 * Editable revision backed by a file outside of the workspace (just IPath).
 * <p>
 * Use {@link ResourceEditableRevision} if you have a resource.
 */
public class LocationEditableRevision extends EditableRevision {

	private final IPath location;

	private final IRunnableContext runnableContext;

	/**
	 * @param fileRevision
	 * @param location
	 * @param runnableContext
	 */
	public LocationEditableRevision(IFileRevision fileRevision, IPath location,
			IRunnableContext runnableContext) {
		super(fileRevision, null);
		this.location = location;
		Assert.isNotNull(runnableContext);
		this.runnableContext = runnableContext;
	}

	@Override
	public void setContent(final byte[] newContent) {
		try {
			runnableContext.run(false, false, new IRunnableWithProgress() {
				public void run(IProgressMonitor monitor)
						throws InvocationTargetException, InterruptedException {
					IFileStore store = EFS.getLocalFileSystem().getStore(
							location);
					BufferedOutputStream out = null;
					try {
						out = new BufferedOutputStream(store.openOutputStream(
								0, monitor));
						out.write(newContent);
					} catch (CoreException e) {
						throw new InvocationTargetException(e);
					} catch (IOException e) {
						throw new InvocationTargetException(e);
					} finally {
						if (out != null) {
							try {
								out.close();
							} catch (IOException e) {
								// Ignore this one
							}
						}
					}
				}
			});
		} catch (InvocationTargetException e) {
			Activator.handleError(e.getTargetException().getMessage(),
					e.getTargetException(), true);
		} catch (InterruptedException e) {
			// ignore here
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result
				+ ((location == null) ? 0 : location.hashCode());
		result = prime * result
				+ ((runnableContext == null) ? 0 : runnableContext.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		LocationEditableRevision other = (LocationEditableRevision) obj;
		if (location == null) {
			if (other.location != null)
				return false;
		} else if (!location.equals(other.location))
			return false;
		if (runnableContext == null) {
			if (other.runnableContext != null)
				return false;
		} else if (!runnableContext.equals(other.runnableContext))
			return false;
		return true;
	}

}
