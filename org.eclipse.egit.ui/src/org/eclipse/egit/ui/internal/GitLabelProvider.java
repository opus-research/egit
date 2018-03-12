/*******************************************************************************
 * Copyright (c) 2011, 2015 Benjamin Muskalla and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Benjamin Muskalla <bmuskalla@tasktop.com> - initial API and implementation
 *******************************************************************************/
package org.eclipse.egit.ui.internal;

import org.eclipse.core.resources.IResource;
import org.eclipse.egit.core.AdapterUtils;
import org.eclipse.egit.ui.internal.clone.ProjectRecord;
import org.eclipse.egit.ui.internal.commit.RepositoryCommit;
import org.eclipse.egit.ui.internal.repository.tree.RefNode;
import org.eclipse.egit.ui.internal.repository.tree.RepositoryTreeNodeType;
import org.eclipse.egit.ui.internal.synchronize.model.GitModelBlob;
import org.eclipse.egit.ui.internal.synchronize.model.GitModelCache;
import org.eclipse.egit.ui.internal.synchronize.model.GitModelCommit;
import org.eclipse.egit.ui.internal.synchronize.model.GitModelRepository;
import org.eclipse.egit.ui.internal.synchronize.model.GitModelTree;
import org.eclipse.egit.ui.internal.synchronize.model.GitModelWorkingTree;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.resource.ResourceManager;
import org.eclipse.jface.viewers.DelegatingStyledCellLabelProvider.IStyledLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StyledString;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE.SharedImages;
import org.eclipse.ui.model.WorkbenchLabelProvider;

/**
 * Common label provider for git related model objects
 *
 */
public class GitLabelProvider extends LabelProvider implements
		IStyledLabelProvider {

	private ResourceManager imageCache;

	private LabelProvider workbenchLabelProvider;

	@Override
	public String getText(Object element) {
		return GitLabels.getPlainShortLabelExtended(element);
	}

	@Override
	public Image getImage(Object element) {
		if (element instanceof Repository) {
			return RepositoryTreeNodeType.REPO.getIcon();
		}

		if (element instanceof RefNode) {
			return getRefIcon(((RefNode) element).getObject());
		}

		if (element instanceof Ref) {
			return getRefIcon((Ref) element);
		}

		if (element instanceof GitModelBlob || element instanceof GitModelTree) {
			Object adapter = AdapterUtils.adapt(element, IResource.class);
			return getWorkbenchLabelProvider().getImage(adapter);
		}

		if (element instanceof GitModelCommit
				|| element instanceof GitModelCache
				|| element instanceof GitModelWorkingTree
				|| element instanceof RepositoryCommit)
			return getChangesetIcon();

		if (element instanceof GitModelRepository)
			return getImage(((GitModelRepository) element).getRepository());

		if (element instanceof ProjectRecord)
			return PlatformUI.getWorkbench().getSharedImages()
					.getImage(SharedImages.IMG_OBJ_PROJECT);

		return super.getImage(element);
	}

	@Override
	public StyledString getStyledText(Object element) {
		return GitLabels.getStyledLabelExtendedSafe(element);
	}

	/**
	 * Returns the common icon for a changeset.
	 *
	 * @return an image
	 */
	protected Image getChangesetIcon() {
		return getImageCache().createImage(UIIcons.CHANGESET);
	}

	private Image getRefIcon(Ref ref) {
		String name = ref.getName();
		if (name.startsWith(Constants.R_HEADS)
				|| name.startsWith(Constants.R_REMOTES))
			return RepositoryTreeNodeType.REF.getIcon();
		else if (name.startsWith(Constants.R_TAGS))
			return RepositoryTreeNodeType.TAG.getIcon();
		else
			return RepositoryTreeNodeType.ADDITIONALREF.getIcon();
	}

	private LabelProvider getWorkbenchLabelProvider() {
		if (workbenchLabelProvider == null)
			workbenchLabelProvider = new WorkbenchLabelProvider();
		return workbenchLabelProvider;
	}

	private ResourceManager getImageCache() {
		if (imageCache == null)
			imageCache = new LocalResourceManager(JFaceResources.getResources());
		return imageCache;
	}

	@Override
	public void dispose() {
		super.dispose();
		if (imageCache != null)
			imageCache.dispose();
		if(workbenchLabelProvider != null)
			workbenchLabelProvider.dispose();
	}
}
