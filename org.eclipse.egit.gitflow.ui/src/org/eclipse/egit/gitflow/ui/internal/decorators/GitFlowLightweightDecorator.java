/*******************************************************************************
 * Copyright (C) 2015, Max Hohenegger <eclipse@hohenegger.eu>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.gitflow.ui.internal.decorators;

import java.io.IOException;

import org.eclipse.core.runtime.ILog;
import org.eclipse.egit.gitflow.GitFlowRepository;
import org.eclipse.egit.gitflow.ui.Activator;
import org.eclipse.egit.gitflow.ui.internal.UIIcons;
import org.eclipse.egit.ui.internal.repository.tree.RepositoryNode;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ILightweightLabelDecorator;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.ui.PlatformUI;

/**
 * Supplies annotations for displayed repositories
 *
 */
public class GitFlowLightweightDecorator extends LabelProvider implements
		ILightweightLabelDecorator {

	/**
	 * Property constant pointing back to the extension point id of the
	 * decorator
	 */
	public static final String DECORATOR_ID = "org.eclipse.egit.gitflow.ui.internal.decorators.GitflowLightweightDecorator"; //$NON-NLS-1$

	private ILog log;

	/**
	 *
	 */
	public GitFlowLightweightDecorator() {
		log = Activator.getDefault().getLog();
	}

	/**
	 * This method should only be called by the decorator thread.
	 *
	 * @see org.eclipse.jface.viewers.ILightweightLabelDecorator#decorate(java.lang.Object,
	 *      org.eclipse.jface.viewers.IDecoration)
	 */
	@Override
	public void decorate(Object element, IDecoration decoration) {
		// Don't decorate if UI plugin is not running
		if (Activator.getDefault() == null) {
			return;
		}

		// Don't decorate if the workbench is not running
		if (!PlatformUI.isWorkbenchRunning()) {
			return;
		}


		final GitFlowRepository repository = getRepository(element);
		try {
			if (repository != null) {
				decorateRepository(repository, decoration);
			}
		} catch (Exception e) {
			handleException(repository, e);
		}
	}

	private static GitFlowRepository getRepository(Object element) {
		GitFlowRepository repository = null;
		if (element instanceof GitFlowRepository) {
			repository = (GitFlowRepository) element;
		}

		if (element instanceof RepositoryNode) {
			RepositoryNode node = (RepositoryNode) element;
			repository = new GitFlowRepository(node.getObject());
		}

		return repository;
	}

	/**
	 * Decorates a single repository.
	 *
	 * @param repository
	 *            the repository to decorate
	 * @param decoration
	 *            the decoration
	 * @throws IOException
	 */
	private void decorateRepository(GitFlowRepository repository,
			IDecoration decoration) throws IOException {
		final DecorationHelper helper = new DecorationHelper();
		helper.decorate(decoration, repository);
	}


	/**
	 * Helper class for doing repository decoration
	 *
	 * Used for real-time decoration, as well as in the decorator preview
	 * preferences page
	 */
	public static class DecorationHelper {

		/** */
		public static final String BINDING_GITFLOW_FLAG = "dirty"; //$NON-NLS-1$

		/**
		 * Define a cached image descriptor which only creates the image data
		 * once
		 */
		private static class CachedImageDescriptor extends ImageDescriptor {
			ImageDescriptor descriptor;

			ImageData data;

			public CachedImageDescriptor(ImageDescriptor descriptor) {
				this.descriptor = descriptor;
			}

			@Override
			public ImageData getImageData() {
				if (data == null) {
					data = descriptor.getImageData();
				}
				return data;
			}
		}

		private static ImageDescriptor initializedImage;

		static {
			initializedImage = new CachedImageDescriptor(UIIcons.OVR_GITFLOW);
		}

		/**
		 * Decorates the given <code>decoration</code> based on the state of the
		 * given <code>repository</code>.
		 *
		 * @param decoration
		 *            the decoration to decorate
		 * @param repository
		 *            the repository to retrieve state from
		 * @throws IOException
		 */
		public void decorate(IDecoration decoration, GitFlowRepository repository)
				throws IOException {
			decorateIcons(decoration, repository);
		}

		private void decorateIcons(IDecoration decoration,
				GitFlowRepository repository) throws IOException {
			ImageDescriptor overlay = null;

			if (repository.getConfig().isInitialized()) {
				overlay = initializedImage;
			}

			// TODO: change decoration depending on branch type, e.g. "F"-icon
			// for feature branch

			// Overlays can only be added once, so do it at the end
			decoration.addOverlay(overlay);
		}

	}

	/**
	 * Perform a blanket refresh of all decorations
	 */
	public static void refresh() {
		PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
			@Override
			public void run() {
				Activator.getDefault().getWorkbench().getDecoratorManager()
						.update(DECORATOR_ID);
			}
		});
	}


	/**
	 * Handle exceptions that occur in the decorator.
	 *
	 * @param repository
	 *            The repository that triggered the exception
	 * @param e
	 *            The exception that occurred
	 */
	private void handleException(GitFlowRepository repository, Exception e) {
		if (repository != null) {
			log.log(Activator.error(e.getMessage(), e));
		 }
	}
}
