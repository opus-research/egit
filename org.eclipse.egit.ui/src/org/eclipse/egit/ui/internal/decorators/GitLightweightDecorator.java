/*******************************************************************************
 * Copyright (C) 2007, IBM Corporation and others
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2008, Google Inc.
 * Copyright (C) 2008, Tor Arne Vestbø <torarnv@gmail.com>
 * Copyright (C) 2011, Dariusz Luksza <dariusz@luksza.org>
 * Copyright (C) 2011, Christian Halstrick <christian.halstrick@sap.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.egit.ui.internal.decorators;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.mapping.ResourceMapping;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.core.internal.indexdiff.IndexDiffChangedListener;
import org.eclipse.egit.core.internal.indexdiff.IndexDiffData;
import org.eclipse.egit.core.internal.util.ExceptionCollector;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIIcons;
import org.eclipse.egit.ui.UIPreferences;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.internal.decorators.IDecoratableResource.Staged;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ILightweightLabelDecorator;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.LabelProviderChangedEvent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.osgi.util.TextProcessor;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.widgets.Display;
import org.eclipse.team.internal.ui.Utils;
import org.eclipse.team.ui.ISharedImages;
import org.eclipse.team.ui.TeamImages;
import org.eclipse.team.ui.TeamUI;
import org.eclipse.ui.IContributorResourceAdapter;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.themes.ITheme;

/**
 * Supplies annotations for displayed resources
 *
 * This decorator provides annotations to indicate the status of each resource
 * when compared to <code>HEAD</code>, as well as the index in the relevant
 * repository.
 */
public class GitLightweightDecorator extends LabelProvider implements
		ILightweightLabelDecorator, IPropertyChangeListener,
		IndexDiffChangedListener {

	/**
	 * Property constant pointing back to the extension point id of the
	 * decorator
	 */
	public static final String DECORATOR_ID = "org.eclipse.egit.ui.internal.decorators.GitLightweightDecorator"; //$NON-NLS-1$

	/**
	 * Collector for keeping the error view from filling up with exceptions
	 */
	private static ExceptionCollector exceptions = new ExceptionCollector(
			UIText.Decorator_exceptionMessage, Activator.getPluginId(),
			IStatus.ERROR, Activator.getDefault().getLog());

	private static String[] fonts = new String[]  {
		UIPreferences.THEME_UncommittedChangeFont};

	private static String[] colors = new String[] {
		UIPreferences.THEME_UncommittedChangeBackgroundColor,
		UIPreferences.THEME_UncommittedChangeForegroundColor};

	/**
	 * Constructs a new Git resource decorator
	 */
	public GitLightweightDecorator() {
		TeamUI.addPropertyChangeListener(this);
		Activator.addPropertyChangeListener(this);
		PlatformUI.getWorkbench().getThemeManager().getCurrentTheme()
				.addPropertyChangeListener(this);

		org.eclipse.egit.core.Activator.getDefault().getIndexDiffCache().addIndexDiffChangedListener(this);
		// This is an optimization to ensure that while decorating our fonts and colors are
		// pre-created and decoration can occur without having to syncExec.
		ensureFontAndColorsCreated(fonts, colors);
	}

	/**
	 * This method will ensure that the fonts and colors used by the decorator
	 * are cached in the registries. This avoids having to syncExec when
	 * decorating since we ensure that the fonts and colors are pre-created.
	 *
	 * @param actFonts fonts ids to cache
	 * @param actColors color ids to cache
	 */
	private void ensureFontAndColorsCreated(final String[] actFonts, final String[] actColors) {
		Display.getDefault().syncExec(new Runnable() {
			public void run() {
				ITheme theme  = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme();
				for (int i = 0; i < actColors.length; i++) {
					theme.getColorRegistry().get(actColors[i]);

				}
				for (int i = 0; i < actFonts.length; i++) {
					theme.getFontRegistry().get(actFonts[i]);
				}
			}
		});
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.jface.viewers.IBaseLabelProvider#dispose()
	 */
	@Override
	public void dispose() {
		super.dispose();
		PlatformUI.getWorkbench().getThemeManager().getCurrentTheme()
				.removePropertyChangeListener(this);
		TeamUI.removePropertyChangeListener(this);
		Activator.removePropertyChangeListener(this);
		org.eclipse.egit.core.Activator.getDefault().getIndexDiffCache().removeIndexDiffChangedListener(this);
	}

	/**
	 * This method should only be called by the decorator thread.
	 *
	 * @see org.eclipse.jface.viewers.ILightweightLabelDecorator#decorate(java.lang.Object,
	 *      org.eclipse.jface.viewers.IDecoration)
	 */
	public void decorate(Object element, IDecoration decoration) {
		// Don't decorate if UI plugin is not running
		if (Activator.getDefault() == null)
			return;

		// Don't decorate if the workbench is not running
		if (!PlatformUI.isWorkbenchRunning())
			return;

		final IResource resource = getResource(element);
		if (resource == null)
			decorateResourceMapping(element, decoration);
		else {
			try {
				decorateResource(resource, decoration);
			} catch(CoreException e) {
				handleException(resource, e);
			}
		}
	}

	/**
	 * Decorates a single resource (i.e. a project).
	 *
	 * @param resource the resource to decorate
	 * @param decoration the decoration
	 * @throws CoreException
	 */
	private void decorateResource(IResource resource, IDecoration decoration) throws CoreException {
		IndexDiffData indexDiffData = getIndexDiffDataOrNull(resource);

		if(indexDiffData == null)
			return;

		IDecoratableResource decoratableResource = null;
		final DecorationHelper helper = new DecorationHelper(
				Activator.getDefault().getPreferenceStore());
		try {
			decoratableResource = new DecoratableResourceAdapter(indexDiffData, resource);
		} catch (IOException e) {
			throw new CoreException(Activator.createErrorStatus(UIText.Decorator_exceptionMessage, e));
		}
		helper.decorate(decoration, decoratableResource);
	}

	static IndexDiffData getIndexDiffDataOrNull(IResource resource) {
		if (resource.getType() == IResource.ROOT)
			return null;

		// Don't decorate non-existing resources
		if (!resource.exists() && !resource.isPhantom())
			return null;

		// Make sure we're dealing with a project under Git revision control
		final RepositoryMapping mapping = RepositoryMapping
				.getMapping(resource);
		if (mapping == null)
			return null;

		// Cannot decorate linked resources
		if (mapping.getRepoRelativePath(resource) == null)
			return null;

		IndexDiffData indexDiffData = org.eclipse.egit.core.Activator
				.getDefault().getIndexDiffCache()
				.getIndexDiffCacheEntry(mapping.getRepository()).getIndexDiff();

		return indexDiffData;
	}

	/**
	 * Decorates a resource mapping (i.e. a Working Set).
	 *
	 * @param element the element for which the decoration was initially called
	 * @param decoration the decoration
	 */
	private void decorateResourceMapping(Object element, IDecoration decoration) {
		@SuppressWarnings("restriction")
		ResourceMapping mapping = Utils.getResourceMapping(element);

		IDecoratableResource decoRes = new DecoratableResourceMapping(mapping);

		/*
		 *  don't render question marks on working sets. !isTracked() can have two reasons:
		 *   1) nothing is tracked.
		 *   2) no indexDiff for the contained projects ready yet.
		 *  in both cases, don't do anything to not pollute the display of the sets.
		 */
		if(!decoRes.isTracked())
			return;

		final DecorationHelper helper = new DecorationHelper(
				Activator.getDefault().getPreferenceStore());

		helper.decorate(decoration, decoRes);
	}


	/**
	 * Helper class for doing resource decoration, based on the given
	 * preferences
	 *
	 * Used for real-time decoration, as well as in the decorator preview
	 * preferences page
	 */
	public static class DecorationHelper {

		/** */
		public static final String BINDING_RESOURCE_NAME = "name"; //$NON-NLS-1$

		/** */
		public static final String BINDING_BRANCH_NAME = "branch"; //$NON-NLS-1$

		/** */
		public static final String BINDING_BRANCH_STATUS = "branch_status"; //$NON-NLS-1$

		/** */
		public static final String BINDING_REPOSITORY_NAME = "repository"; //$NON-NLS-1$

		/** */
		public static final String BINDING_DIRTY_FLAG = "dirty"; //$NON-NLS-1$

		/** */
		public static final String BINDING_STAGED_FLAG = "staged"; //$NON-NLS-1$

		/** */
		public static final String FILE_FORMAT_DEFAULT="{dirty:>} {name}"; //$NON-NLS-1$

		/** */
		public static final String FOLDER_FORMAT_DEFAULT = "{dirty:>} {name}"; //$NON-NLS-1$

		/** */
		public static final String PROJECT_FORMAT_DEFAULT ="{dirty:>} {name} [{repository} {branch}{ branch_status}]";  //$NON-NLS-1$

		private IPreferenceStore store;

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

			public ImageData getImageData() {
				if (data == null) {
					data = descriptor.getImageData();
				}
				return data;
			}
		}

		private static ImageDescriptor trackedImage;

		private static ImageDescriptor untrackedImage;

		private static ImageDescriptor stagedImage;

		private static ImageDescriptor stagedAddedImage;

		private static ImageDescriptor stagedRemovedImage;

		private static ImageDescriptor conflictImage;

		private static ImageDescriptor assumeValidImage;

		private static ImageDescriptor dirtyImage;

		static {
			trackedImage = new CachedImageDescriptor(TeamImages
					.getImageDescriptor(ISharedImages.IMG_CHECKEDIN_OVR));
			untrackedImage = new CachedImageDescriptor(UIIcons.OVR_UNTRACKED);
			stagedImage = new CachedImageDescriptor(UIIcons.OVR_STAGED);
			stagedAddedImage = new CachedImageDescriptor(UIIcons.OVR_STAGED_ADD);
			stagedRemovedImage = new CachedImageDescriptor(
					UIIcons.OVR_STAGED_REMOVE);
			conflictImage = new CachedImageDescriptor(UIIcons.OVR_CONFLICT);
			assumeValidImage = new CachedImageDescriptor(UIIcons.OVR_ASSUMEVALID);
			dirtyImage = new CachedImageDescriptor(UIIcons.OVR_DIRTY);
		}

		/**
		 * Constructs a decorator using the rules from the given
		 * <code>preferencesStore</code>
		 *
		 * @param preferencesStore
		 *            the preferences store with the preferred decorator rules
		 */
		public DecorationHelper(IPreferenceStore preferencesStore) {
			store = preferencesStore;
		}

		/**
		 * Decorates the given <code>decoration</code> based on the state of the
		 * given <code>resource</code>, using the preferences passed when
		 * constructing this decoration helper.
		 *
		 * @param decoration
		 *            the decoration to decorate
		 * @param resource
		 *            the resource to retrieve state from
		 */
		public void decorate(IDecoration decoration,
				IDecoratableResource resource) {
			if (resource.isIgnored())
				return;

			decorateText(decoration, resource);
			decorateIcons(decoration, resource);
			decorateFontAndColour(decoration, resource);
		}

		private void decorateFontAndColour(IDecoration decoration,
				IDecoratableResource resource) {
			ITheme current = PlatformUI.getWorkbench().getThemeManager().getCurrentTheme();
			if (resource.isIgnored()) {
				return;
			}
			if (!resource.isTracked()
					|| resource.isDirty()
					|| resource.staged() != Staged.NOT_STAGED) {
				Color bc = current.getColorRegistry().get(UIPreferences.THEME_UncommittedChangeBackgroundColor);
				Color fc = current.getColorRegistry().get(UIPreferences.THEME_UncommittedChangeForegroundColor);
				Font f = current.getFontRegistry().get(UIPreferences.THEME_UncommittedChangeFont);

				decoration.setBackgroundColor(bc);
				decoration.setForegroundColor(fc);
				decoration.setFont(f);
			}
		}

		private void decorateText(IDecoration decoration,
				IDecoratableResource resource) {
			String format = ""; //$NON-NLS-1$
			switch (resource.getType()) {
			case IResource.FILE:
				format = store
						.getString(UIPreferences.DECORATOR_FILETEXT_DECORATION);
				break;
			case IResource.FOLDER:
			case DecoratableResourceMapping.RESOURCE_MAPPING:
				format = store
						.getString(UIPreferences.DECORATOR_FOLDERTEXT_DECORATION);
				break;
			case IResource.PROJECT:
				format = store
						.getString(UIPreferences.DECORATOR_PROJECTTEXT_DECORATION);
				break;
			}

			Map<String, String> bindings = new HashMap<String, String>();
			bindings.put(BINDING_RESOURCE_NAME, resource.getName());
			bindings.put(BINDING_REPOSITORY_NAME, resource.getRepositoryName());
			bindings.put(BINDING_BRANCH_NAME, resource.getBranch());
			bindings.put(BINDING_BRANCH_STATUS, resource.getBranchStatus());
			bindings.put(BINDING_DIRTY_FLAG, resource.isDirty() ? ">" : null); //$NON-NLS-1$
			bindings.put(BINDING_STAGED_FLAG,
					resource.staged() != Staged.NOT_STAGED ? "*" : null); //$NON-NLS-1$

			decorate(decoration, format, bindings);
		}

		private void decorateIcons(IDecoration decoration,
				IDecoratableResource resource) {
			ImageDescriptor overlay = null;

			if (resource.isTracked()) {
				if (store.getBoolean(UIPreferences.DECORATOR_SHOW_TRACKED_ICON))
					overlay = trackedImage;

				if (store
						.getBoolean(UIPreferences.DECORATOR_SHOW_ASSUME_VALID_ICON)
						&& resource.isAssumeValid())
					overlay = assumeValidImage;

				// Staged overrides tracked
				Staged staged = resource.staged();
				if (store.getBoolean(UIPreferences.DECORATOR_SHOW_STAGED_ICON)
						&& staged != Staged.NOT_STAGED) {
					if (staged == Staged.ADDED)
						overlay = stagedAddedImage;
					else if (staged == Staged.REMOVED)
						overlay = stagedRemovedImage;
					else
						overlay = stagedImage;
				}

				// Dirty overrides staged
				if(store
						.getBoolean(UIPreferences.DECORATOR_SHOW_DIRTY_ICON) && resource.isDirty()) {
					overlay = dirtyImage;
				}

				// Conflicts override everything
				if (store
						.getBoolean(UIPreferences.DECORATOR_SHOW_CONFLICTS_ICON)
						&& resource.hasConflicts())
					overlay = conflictImage;

			} else if (store
					.getBoolean(UIPreferences.DECORATOR_SHOW_UNTRACKED_ICON)) {
				overlay = untrackedImage;
			}

			// Overlays can only be added once, so do it at the end
			decoration.addOverlay(overlay);
		}

		/**
		 * Decorates the given <code>decoration</code>, using the specified text
		 * <code>format</code>, and mapped using the variable bindings from
		 * <code>bindings</code>
		 *
		 * @param decoration
		 *            the decoration to decorate
		 * @param format
		 *            the format to base the decoration on
		 * @param bindings
		 *            the bindings between variables in the format and actual
		 *            values
		 */
		public static void decorate(IDecoration decoration, String format,
				Map<String, String> bindings) {
			StringBuilder prefix = new StringBuilder();
			StringBuilder suffix = new StringBuilder();
			StringBuilder output = prefix;

			int length = format.length();
			int start = -1;
			int end = length;
			while (true) {
				if ((end = format.indexOf('{', start)) > -1) {
					output.append(format.substring(start + 1, end));
					if ((start = format.indexOf('}', end)) > -1) {
						String key = format.substring(end + 1, start);
						String s;
						boolean spaceBefore = false;
						boolean spaceAfter = false;

						// Allow users to override the binding
						if (key.indexOf(':') > -1) {
							String[] keyAndBinding = key.split(":", 2); //$NON-NLS-1$
							key = keyAndBinding[0];
							if (keyAndBinding.length > 1
									&& bindings.get(key) != null)
								bindings.put(key, keyAndBinding[1]);
						} else {
							if (key.charAt(0) == ' ') {
								spaceBefore = true;
								key = key.substring(1);
							}
							if (key.charAt(key.length() - 1) == ' ') {
								spaceAfter = true;
								key = key.substring(0, key.length() - 1);
							}
						}


						// We use the BINDING_RESOURCE_NAME key to determine if
						// we are doing the prefix or suffix. The name isn't
						// actually part of either.
						if (key.equals(BINDING_RESOURCE_NAME)) {
							output = suffix;
							s = null;
						} else {
							s = bindings.get(key);
						}

						if (s != null) {
							if (spaceBefore)
								output.append(' ');
							output.append(s);
							if (spaceAfter)
								output.append(' ');
						} else {
							// Support removing prefix character if binding is
							// null
							int curLength = output.length();
							if (curLength > 0) {
								char c = output.charAt(curLength - 1);
								if (c == ':' || c == '@') {
									output.deleteCharAt(curLength - 1);
								}
							}
						}
					} else {
						output.append(format.substring(end, length));
						break;
					}
				} else {
					output.append(format.substring(start + 1, length));
					break;
				}
			}

			String prefixString = prefix.toString().replaceAll("^\\s+", ""); //$NON-NLS-1$ //$NON-NLS-2$
			if (prefixString.length() > 0)
				decoration.addPrefix(TextProcessor.process(prefixString,
						"()[].")); //$NON-NLS-1$
			String suffixString = suffix.toString().replaceAll("\\s+$", ""); //$NON-NLS-1$ //$NON-NLS-2$
			if (suffixString.length() > 0)
				decoration.addSuffix(TextProcessor.process(suffixString,
						"()[].")); //$NON-NLS-1$
		}
	}

	// -------- Refresh handling --------

	/**
	 * Perform a blanket refresh of all decorations
	 */
	public static void refresh() {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				Activator.getDefault().getWorkbench().getDecoratorManager()
						.update(DECORATOR_ID);
			}
		});
	}

	/**
	 * Callback for IPropertyChangeListener events
	 *
	 * If any of the relevant preferences has been changed we refresh all
	 * decorations (all projects and their resources).
	 *
	 * @see org.eclipse.jface.util.IPropertyChangeListener#propertyChange(org.eclipse.jface.util.PropertyChangeEvent)
	 */
	public void propertyChange(PropertyChangeEvent event) {
		final String prop = event.getProperty();
		// If the property is of any interest to us
		if (prop.equals(TeamUI.GLOBAL_IGNORES_CHANGED)
				|| prop.equals(TeamUI.GLOBAL_FILE_TYPES_CHANGED)
				|| prop.equals(Activator.DECORATORS_CHANGED)) {
			postLabelEvent();
		} else if (prop.equals(UIPreferences.THEME_UncommittedChangeBackgroundColor)
				|| prop.equals(UIPreferences.THEME_UncommittedChangeFont)
				|| prop.equals(UIPreferences.THEME_UncommittedChangeForegroundColor)) {
			ensureFontAndColorsCreated(fonts, colors);
			postLabelEvent(); // TODO do I really need this?
		}
	}

	public void indexDiffChanged(Repository repository,
			IndexDiffData indexDiffData) {
		postLabelEvent();
	}

	// -------- Helper methods --------

	private static IResource getResource(Object actElement) {
		Object element = actElement;
		if (element instanceof ResourceMapping) {
			element = ((ResourceMapping) element).getModelObject();
		}

		IResource resource = null;
		if (element instanceof IResource) {
			resource = (IResource) element;
		} else if (element instanceof IAdaptable) {
			final IAdaptable adaptable = (IAdaptable) element;
			resource = (IResource) adaptable.getAdapter(IResource.class);
			if (resource == null) {
				final IContributorResourceAdapter adapter = (IContributorResourceAdapter) adaptable
						.getAdapter(IContributorResourceAdapter.class);
				if (adapter != null)
					resource = adapter.getAdaptedResource(adaptable);
			}
		}

		return resource;
	}

	/**
	 * Post a label event to the LabelEventJob
	 *
	 * Posts a generic label event. No specific elements are provided; all
	 * decorations shall be invalidated. Same as
	 * <code>postLabelEvent(null, true)</code>.
	 */
	private void postLabelEvent() {
		// Post label event to LabelEventJob
		LabelEventJob.getInstance().postLabelEvent(this);
	}

	void fireLabelEvent() {
		final LabelProviderChangedEvent event = new LabelProviderChangedEvent(
				this);
		// Re-trigger decoration process (in UI thread)
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				fireLabelProviderChanged(event);
			}
		});
	}

	/**
	 * Handle exceptions that occur in the decorator. Exceptions are only logged
	 * for resources that are accessible (i.e. exist in an open project).
	 *
	 * @param resource
	 *            The resource that triggered the exception
	 * @param e
	 *            The exception that occurred
	 */
	private static void handleException(IResource resource, CoreException e) {
		if (resource == null || resource.isAccessible())
			exceptions.handleException(e);
	}
}

/**
 * Job reducing label events to prevent unnecessary (i.e. redundant) event
 * processing
 */
class LabelEventJob extends Job {

	/**
	 * Constant defining the waiting time (in milliseconds) until an event is
	 * fired
	 */
	private static final long DELAY = 100L;

	private static LabelEventJob instance = new LabelEventJob("LabelEventJob"); //$NON-NLS-1$

	/**
	 * Get the LabelEventJob singleton
	 *
	 * @return the LabelEventJob singleton
	 */
	static LabelEventJob getInstance() {
		return instance;
	}

	private LabelEventJob(final String name) {
		super(name);
	}

	private GitLightweightDecorator glwDecorator = null;

	/**
	 * Post a label event
	 *
	 * @param decorator
	 *            The GitLightweightDecorator that is used to fire a
	 *            LabelProviderChangedEvent
	 */
	void postLabelEvent(final GitLightweightDecorator decorator) {
		if (this.glwDecorator == null)
			this.glwDecorator = decorator;
		if (getState() == SLEEPING || getState() == WAITING)
			cancel();
		schedule(DELAY);
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		if (glwDecorator != null)
			glwDecorator.fireLabelEvent();
		return Status.OK_STATUS;
	}
}
