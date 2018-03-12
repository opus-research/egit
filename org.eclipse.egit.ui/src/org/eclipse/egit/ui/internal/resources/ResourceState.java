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
 * Base implementation of an {@link IResourceState}.
 */
public class ResourceState implements IResourceState {

	/**
	 * Flag indicating whether or not the resource is tracked
	 */
	private boolean tracked;

	/**
	 * Flag indicating whether or not the resource is ignored
	 */
	private boolean ignored;

	/**
	 * Flag indicating whether or not the resource has changes that are not
	 * staged
	 */
	private boolean dirty;

	/**
	 * Staged state of the resource
	 */
	@NonNull
	private Staged staged = Staged.NOT_STAGED;

	/**
	 * Flag indicating whether or not the resource has merge conflicts
	 */
	private boolean conflicts;

	/**
	 * Flag indicating whether or not the resource is assumed valid
	 */
	private boolean assumeValid;

	@Override
	public boolean isTracked() {
		return tracked;
	}

	@Override
	public boolean isIgnored() {
		return ignored;
	}

	@Override
	public boolean isDirty() {
		return dirty;
	}

	@Override
	public Staged staged() {
		return staged;
	}

	@Override
	public boolean hasConflicts() {
		return conflicts;
	}

	@Override
	public boolean isAssumeValid() {
		return assumeValid;
	}

	/**
	 * Sets the staged property.
	 *
	 * @param staged
	 *            value to set.
	 */
	protected void setStaged(@NonNull Staged staged) {
		this.staged = staged;
	}

	/**
	 * Sets the conflicts property.
	 *
	 * @param conflicts
	 *            value to set.
	 */
	protected void setConflicts(boolean conflicts) {
		this.conflicts = conflicts;
	}

	/**
	 * Sets the tracked property.
	 *
	 * @param tracked
	 *            value to set.
	 */
	protected void setTracked(boolean tracked) {
		this.tracked = tracked;
	}

	/**
	 * Sets the ignored property.
	 *
	 * @param ignored
	 *            value to set.
	 */
	protected void setIgnored(boolean ignored) {
		this.ignored = ignored;
	}

	/**
	 * Sets the dirty property.
	 *
	 * @param dirty
	 *            value to set.
	 */
	protected void setDirty(boolean dirty) {
		this.dirty = dirty;
	}

	/**
	 * Sets the assumeValid property.
	 *
	 * @param assumeValid
	 *            value to set.
	 */
	protected void setAssumeValid(boolean assumeValid) {
		this.assumeValid = assumeValid;
	}

}
