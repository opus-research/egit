/*******************************************************************************
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2011, Matthias Sohn <matthias.sohn@sap.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core;

import java.io.File;
import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.egit.core.internal.indexdiff.IndexDiffCache;
import org.eclipse.egit.core.internal.job.JobUtil;
import org.eclipse.egit.core.internal.trace.GitTraceLocation;
import org.eclipse.egit.core.op.ConnectProviderOperation;
import org.eclipse.egit.core.project.GitProjectData;
import org.eclipse.egit.core.project.RepositoryFinder;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.egit.core.securestorage.EGitSecureStore;
import org.eclipse.equinox.security.storage.SecurePreferencesFactory;
import org.eclipse.jgit.util.FS;
import org.eclipse.osgi.service.debug.DebugOptions;
import org.eclipse.osgi.service.debug.DebugOptionsListener;
import org.eclipse.team.core.RepositoryProvider;
import org.osgi.framework.BundleContext;

/**
 * The plugin class for the org.eclipse.egit.core plugin. This
 * is a singleton class.
 */
public class Activator extends Plugin implements DebugOptionsListener {
	private static Activator plugin;
	private static String pluginId;
	private RepositoryCache repositoryCache;
	private IndexDiffCache indexDiffCache;
	private RepositoryUtil repositoryUtil;
	private EGitSecureStore secureStore;
	private AutoShareProjects shareGitProjectsJob;

	/**
	 * @return the singleton {@link Activator}
	 */
	public static Activator getDefault() {
		return plugin;
	}

	/**
	 * @return the name of this plugin
	 */
	public static String getPluginId() {
		return pluginId;
	}

	/**
	 * Utility to create an error status for this plug-in.
	 *
	 * @param message User comprehensible message
	 * @param thr cause
	 * @return an initialized error status
	 */
	public static IStatus error(final String message, final Throwable thr) {
		return new Status(IStatus.ERROR, getPluginId(), 0,	message, thr);
	}

	/**
	 * Utility method to log errors in the Egit plugin.
	 * @param message User comprehensible message
	 * @param thr The exception through which we noticed the error
	 */
	public static void logError(final String message, final Throwable thr) {
		getDefault().getLog().log(
				new Status(IStatus.ERROR, getPluginId(), 0, message, thr));
	}

	/**
	 * Construct the {@link Activator} singleton instance
	 */
	public Activator() {
		Activator.setActivator(this);
	}

	private static void setActivator(Activator a) {
		plugin = a;
	}

	public void start(final BundleContext context) throws Exception {

		super.start(context);

		pluginId = context.getBundle().getSymbolicName();

		// we want to be notified about debug options changes
		Dictionary<String, String> props = new Hashtable<String, String>(4);
		props.put(DebugOptions.LISTENER_SYMBOLICNAME, pluginId);
		context.registerService(DebugOptionsListener.class.getName(), this,
				props);

		repositoryCache = new RepositoryCache();
		indexDiffCache = new IndexDiffCache();
		try {
			GitProjectData.reconfigureWindowCache();
		} catch (RuntimeException e) {
			logError(CoreText.Activator_ReconfigureWindowCacheError, e);
		}
		GitProjectData.attachToWorkspace(true);

		IEclipsePreferences node = new InstanceScope().getNode(Activator.getPluginId());
		String gitPrefix = node.get(GitCorePreferences.core_gitPrefix, null);
		if (gitPrefix != null)
			FS.DETECTED.setGitPrefix(new File(gitPrefix));

		repositoryUtil = new RepositoryUtil();

		secureStore = new EGitSecureStore(SecurePreferencesFactory.getDefault());

		registerAutoShareProjects();
	}

	public void optionsChanged(DebugOptions options) {
		// initialize the trace stuff
		GitTraceLocation.initializeFromOptions(options, isDebugging());
	}

	/**
	 *  @return cache for Repository objects
	 */
	public RepositoryCache getRepositoryCache() {
		return repositoryCache;
	}

	/**
	 *  @return cache for index diffs
	 */
	public IndexDiffCache getIndexDiffCache() {
		return indexDiffCache;
	}

	/**
	 * @return the {@link RepositoryUtil} instance
	 */
	public RepositoryUtil getRepositoryUtil() {
		return repositoryUtil;
	}

	/**
	 * @return the secure store
	 */
	public EGitSecureStore getSecureStore() {
		return secureStore;
	}

	public void stop(final BundleContext context) throws Exception {
		GitProjectData.detachFromWorkspace();
		repositoryCache = null;
		indexDiffCache = null;
		repositoryUtil.dispose();
		repositoryUtil = null;
		secureStore = null;
		super.stop(context);
		plugin = null;
	}

	private void registerAutoShareProjects() {
		shareGitProjectsJob = new AutoShareProjects();
		ResourcesPlugin.getWorkspace().addResourceChangeListener(
				shareGitProjectsJob, IResourceChangeEvent.POST_CHANGE);
	}

	private static class AutoShareProjects implements
			IResourceChangeListener {

		private static int INTERESTING_CHANGES = IResourceDelta.ADDED
				| IResourceDelta.OPEN;

		public AutoShareProjects() {
			// empty
		}

		private boolean doAutoShare() {
			IEclipsePreferences d = new DefaultScope().getNode(Activator
					.getPluginId());
			IEclipsePreferences p = new InstanceScope().getNode(Activator
					.getPluginId());
			return p.getBoolean(GitCorePreferences.core_autoShareProjects, d
					.getBoolean(GitCorePreferences.core_autoShareProjects,
							false));
		}

		public void resourceChanged(IResourceChangeEvent event) {
			try {
				event.getDelta().accept(new IResourceDeltaVisitor() {

					public boolean visit(IResourceDelta delta)
							throws CoreException {
						if (!doAutoShare())
							return false;
						if (delta.getKind() == IResourceDelta.CHANGED
								&& (delta.getFlags() & INTERESTING_CHANGES) == 0)
							return true;
						final IResource resource = delta.getResource();
						if (!resource.exists() || !resource.isAccessible())
							return false;
						if (resource.getType() != IResource.PROJECT)
							return true;
						if (RepositoryMapping.getMapping(resource) != null)
							return false;
						final IProject project = (IProject) resource;
						RepositoryProvider provider = RepositoryProvider
								.getProvider(project);
						// respect if project is already shared with another
						// team provider
						if (provider != null)
							return false;
						RepositoryFinder f = new RepositoryFinder(project);
						Collection<RepositoryMapping> mappings = f.find(new NullProgressMonitor());
						try {
							if (mappings.size() == 1) {
								// connect
								RepositoryMapping m = mappings.iterator()
										.next();
								final File repositoryDir = m
										.getGitDirAbsolutePath().toFile();
								final ConnectProviderOperation op = new ConnectProviderOperation(
										project, repositoryDir);
								JobUtil.scheduleUserJob(op,
										CoreText.Activator_AutoShareJobName,
										JobFamilies.AUTO_SHARE);
								Activator.getDefault().getRepositoryUtil()
										.addConfiguredRepository(repositoryDir);
							}
						} catch (IllegalArgumentException e) {
							logError(CoreText.Activator_AutoSharingFailed, e);
						}
						return false;
					}
				});
			} catch (CoreException e) {
				Activator.logError(e.getMessage(), e);
				return;
			}
		}
	}
}
