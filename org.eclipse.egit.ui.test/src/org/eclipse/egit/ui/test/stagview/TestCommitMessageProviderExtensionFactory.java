/*******************************************************************************
 * Copyright (C) 2017 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.test.stagview;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IExecutableExtensionFactory;
import org.eclipse.egit.ui.ICommitMessageProvider;

public class TestCommitMessageProviderExtensionFactory
		implements IExecutableExtensionFactory {

	// Indirection needed since the extension point may create new factory
	// instances.
	protected static class InternalCommitMessageProviderFactory
			implements IExecutableExtensionFactory {

		private ICommitMessageProvider currentProvider;

		private final ICommitMessageProvider emptyProvider = new ICommitMessageProvider() {

			@Override
			public String getMessage(IResource[] resources) {
				return "";
			}
		};

		public void setCommitMessageProvider(
				ICommitMessageProvider provider) {
			currentProvider = provider;
		}

		public ICommitMessageProvider getProvider() {
			return currentProvider;
		}

		@Override
		public Object create() throws CoreException {
			return currentProvider != null ? currentProvider : emptyProvider;
		}

	}

	protected static final InternalCommitMessageProviderFactory INSTANCE = new InternalCommitMessageProviderFactory();

	@Override
	public Object create() throws CoreException {
		return INSTANCE.create();
	}

}
