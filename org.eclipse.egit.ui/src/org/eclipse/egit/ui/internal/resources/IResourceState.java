/*******************************************************************************
 * Copyright (C) 2015, Thomas Wolf <thomas.wolf@paranor.ch>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.resources;

import org.eclipse.jgit.annotations.NonNull;

/**
 * Provides information about the repository state of an item in the repository.
 */
public interface IResourceState {

	/**
	 * Set of possible staging states for a resource.
	 */
	enum Staged {
		/** Represents a resource that is not staged */
		NOT_STAGED,

		/** Represents a resource that has been modified */
		MODIFIED,

		/** Represents a resource that is added to Git */
		ADDED,

		/** Represents a resource that is removed from Git */
		REMOVED,

		/** Represents a resource that has been renamed */
		RENAMED
	}

	/**
	 * Returns whether or not the resource is tracked by Git.
	 *
	 * @return whether or not the resource is tracked by Git
	 */
	boolean isTracked();

	/**
	 * Returns whether or not the resource is ignored, either by a global team
	 * ignore in Eclipse, or by .git/info/exclude et al.
	 *
	 * @return whether or not the resource is ignored
	 */
	boolean isIgnored();

	/**
	 * Returns whether or not the resource has changes that are not staged.
	 *
	 * @return whether or not the resource is dirty
	 */
	boolean isDirty();

	/**
	 * Returns the staged state of the resource. The set of allowed values are
	 * defined by the {@link Staged} enum.
	 *
	 * @return the staged state of the resource
	 */
	@NonNull
	Staged staged();

	/**
	 * Returns whether or not the resource has merge conflicts.
	 *
	 * @return whether or not the resource has merge conflicts
	 */
	boolean hasConflicts();

	/**
	 * Returns whether or not the resource is assumed valid.
	 *
	 * @return whether or not the resource is assumed valid
	 */
	boolean isAssumeValid();

}
