/*******************************************************************************
 * Copyright (C) 2007,2010 Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com>
 * Copyright (C) 2012, Matthias Sohn <matthias.sohn@sap.com>
 * Copyright (C) 2015, Philipp Bumann <bumannp@gmail.com>
 * Copyright (C) 2016, Dani Megert <daniel_megert@ch.ibm.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.core.RepositoryCache;
import org.eclipse.egit.core.RepositoryUtil;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.egit.ui.internal.ConfigurationChecker;
import org.eclipse.egit.ui.internal.KnownHosts;
import org.eclipse.egit.ui.internal.RepositoryCacheRule;
import org.eclipse.egit.ui.internal.UIText;
import org.eclipse.egit.ui.internal.credentials.EGitCredentialsProvider;
import org.eclipse.egit.ui.internal.trace.GitTraceLocation;
import org.eclipse.egit.ui.internal.variables.GitTemplateVariableResolver;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.resource.ResourceManager;
import org.eclipse.jface.text.templates.ContextTypeRegistry;
import org.eclipse.jface.text.templates.TemplateContextType;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jgit.events.IndexChangedEvent;
import org.eclipse.jgit.events.IndexChangedListener;
import org.eclipse.jgit.events.ListenerHandle;
import org.eclipse.jgit.events.RepositoryEvent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.osgi.service.debug.DebugOptions;
import org.eclipse.osgi.service.debug.DebugOptionsListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IWindowListener;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipse.ui.statushandlers.StatusManager;
import org.eclipse.ui.themes.ITheme;
import org.osgi.framework.BundleContext;

/**
 * This is a plugin singleton mostly controlling logging.
 */
public class Activator extends AbstractUIPlugin implements DebugOptionsListener {

	/**
	 *  The one and only instance
	 */
	private static Activator plugin;

	/**
	 * Property listeners for plugin specific events
	 */
	private static List<IPropertyChangeListener> propertyChangeListeners =
		new ArrayList<>(5);

	/**
	 * Property constant indicating the decorator configuration has changed.
	 */
	public static final String DECORATORS_CHANGED = "org.eclipse.egit.ui.DECORATORS_CHANGED"; //$NON-NLS-1$

	/**
	 * @return the {@link Activator} singleton.
	 */
	public static Activator getDefault() {
		return plugin;
	}

	/**
	 * @return the id of the egit ui plugin
	 */
	public static String getPluginId() {
		return getDefault().getBundle().getSymbolicName();
	}

	/**
	 * Creates an {@link IStatus} from the parameters. If the throwable is an
	 * {@link InvocationTargetException}, the status is created from the first
	 * exception that is either not an InvocationTargetException or that has a
	 * message. If the message passed is empty, tries to supply a message from
	 * that exception.
	 *
	 * @param severity
	 *            of the {@link IStatus}
	 * @param message
	 *            for the status
	 * @param throwable
	 *            that caused the status, may be {@code null}
	 * @return the status
	 */
	private static IStatus toStatus(int severity, String message,
			Throwable throwable) {
		Throwable exc = throwable;
		while (exc instanceof InvocationTargetException) {
			String msg = exc.getLocalizedMessage();
			if (msg != null && !msg.isEmpty()) {
				break;
			}
			Throwable cause = exc.getCause();
			if (cause == null) {
				break;
			}
			exc = cause;
		}
		if (exc != null && (message == null || message.isEmpty())) {
			message = exc.getLocalizedMessage();
		}
		return new Status(severity, getPluginId(), message, exc);
	}

	/**
	 * Handle an error. The error is logged. If <code>show</code> is
	 * <code>true</code> the error is shown to the user.
	 *
	 * @param message
	 *            a localized message
	 * @param throwable
	 * @param show
	 */
	public static void handleError(String message, Throwable throwable,
			boolean show) {
		handleIssue(IStatus.ERROR, message, throwable, show);
	}

	/**
	 * Handle an issue. The issue is logged. If <code>show</code> is
	 * <code>true</code> the issue is shown to the user.
	 *
	 * @param severity
	 *            status severity, use constants defined in {@link IStatus}
	 * @param message
	 *            a localized message
	 * @param throwable
	 * @param show
	 * @since 2.2
	 */
	public static void handleIssue(int severity, String message, Throwable throwable,
			boolean show) {
		IStatus status = toStatus(severity, message, throwable);
		int style = StatusManager.LOG;
		if (show)
			style |= StatusManager.SHOW;
		StatusManager.getManager().handle(status, style);
	}

	/**
	 * Shows an error. The error is NOT logged.
	 *
	 * @param message
	 *            a localized message
	 * @param throwable
	 */
	public static void showError(String message, Throwable throwable) {
		IStatus status = toStatus(IStatus.ERROR, message, throwable);
		StatusManager.getManager().handle(status, StatusManager.SHOW);
	}

	/**
	 * Shows an error. The error is NOT logged.
	 *
	 * @param message
	 *            a localized message
	 * @param status
	 */
	public static void showErrorStatus(String message, IStatus status) {
		StatusManager.getManager().handle(status, StatusManager.SHOW);
	}

	/**
	 * @param message
	 * @param e
	 */
	public static void logError(String message, Throwable e) {
		handleError(message, e, false);
	}

	/**
	 * Utility method to log warnings for this plug-in.
	 *
	 * @param message
	 *            User comprehensible message
	 * @param thr
	 *            The exception through which we noticed the warning
	 */
	public static void logWarning(final String message, final Throwable thr) {
		handleIssue(IStatus.WARNING, message, thr, false);
	}

	/**
	 * @param message
	 * @param e
	 */
	public static void error(String message, Throwable e) {
		handleError(message, e, false);
	}

	/**
	 * Creates an error status
	 *
	 * @param message
	 *            a localized message
	 * @param throwable
	 * @return a new Status object
	 */
	public static IStatus createErrorStatus(String message,
			Throwable throwable) {
		return toStatus(IStatus.ERROR, message, throwable);
	}

	/**
	 * Creates an error status
	 *
	 * @param message
	 *            a localized message
	 * @return a new Status object
	 */
	public static IStatus createErrorStatus(String message) {
		return toStatus(IStatus.ERROR, message, null);
	}

	/**
	 * Get the theme used by this plugin.
	 *
	 * @return our theme.
	 */
	public static ITheme getTheme() {
		return plugin.getWorkbench().getThemeManager().getCurrentTheme();
	}

	/**
	 * Get a font known to this plugin.
	 *
	 * @param id
	 *            one of our THEME_* font preference ids (see
	 *            {@link UIPreferences});
	 * @return the configured font, borrowed from the registry.
	 */
	public static Font getFont(final String id) {
		return getTheme().getFontRegistry().get(id);
	}

	/**
	 * Get a font known to this plugin, but with bold style applied over top.
	 *
	 * @param id
	 *            one of our THEME_* font preference ids (see
	 *            {@link UIPreferences});
	 * @return the configured font, borrowed from the registry.
	 */
	public static Font getBoldFont(final String id) {
		return getTheme().getFontRegistry().getBold(id);
	}

	private ResourceManager resourceManager;
	private RepositoryChangeScanner rcs;
	private ResourceRefreshJob refreshJob;
	private ListenerHandle refreshHandle;
	private DebugOptions debugOptions;

	private volatile boolean uiIsActive;
	private IWindowListener focusListener;

	/**
	 * Construct the {@link Activator} egit ui plugin singleton instance
	 */
	public Activator() {
		Activator.setActivator(this);
	}

	private static void setActivator(Activator a) {
		plugin = a;
	}


	@Override
	public void start(final BundleContext context) throws Exception {
		super.start(context);
		// we want to be notified about debug options changes
		Dictionary<String, String> props = new Hashtable<>(4);
		props.put(DebugOptions.LISTENER_SYMBOLICNAME, context.getBundle()
				.getSymbolicName());
		context.registerService(DebugOptionsListener.class.getName(), this,
				props);

		setupRepoChangeScanner();
		setupRepoIndexRefresh();
		setupFocusHandling();
		setupCredentialsProvider();
		ConfigurationChecker.checkConfiguration();

		registerTemplateVariableResolvers();
	}

	private void setupCredentialsProvider() {
		CredentialsProvider.setDefault(new EGitCredentialsProvider());
	}

	private void registerTemplateVariableResolvers() {
		if (hasJavaPlugin()) {
			final ContextTypeRegistry codeTemplateContextRegistry = JavaPlugin
					.getDefault().getCodeTemplateContextRegistry();
			final Iterator<?> ctIter = codeTemplateContextRegistry
					.contextTypes();

			while (ctIter.hasNext()) {
				final TemplateContextType contextType = (TemplateContextType) ctIter
						.next();
				contextType
						.addResolver(new GitTemplateVariableResolver(
								"git_config", //$NON-NLS-1$
								UIText.GitTemplateVariableResolver_GitConfigDescription));
			}
		}
	}

	/**
	 * @return true if at least one Eclipse window is active
	 */
	static boolean isActive() {
		return getDefault().uiIsActive;
	}


	private void setupFocusHandling() {
		focusListener = new IWindowListener() {

			private void updateUiState() {
				Display.getCurrent().asyncExec(new Runnable() {
					@Override
					public void run() {
						boolean wasActive = uiIsActive;
						uiIsActive = Display.getCurrent().getActiveShell() != null;
						if (uiIsActive != wasActive
								&& GitTraceLocation.REPOSITORYCHANGESCANNER
										.isActive())
							traceUiIsActive();
					}

					private void traceUiIsActive() {
						StringBuilder message = new StringBuilder(
								"workbench is "); //$NON-NLS-1$
						message.append(uiIsActive ? "active" : "inactive"); //$NON-NLS-1$//$NON-NLS-2$
						GitTraceLocation.getTrace().trace(
								GitTraceLocation.REPOSITORYCHANGESCANNER
										.getLocation(), message.toString());
					}
				});
			}

			@Override
			public void windowOpened(IWorkbenchWindow window) {
				updateUiState();
			}

			@Override
			public void windowDeactivated(IWorkbenchWindow window) {
				updateUiState();
			}

			@Override
			public void windowClosed(IWorkbenchWindow window) {
				updateUiState();
			}

			@Override
			public void windowActivated(IWorkbenchWindow window) {
				updateUiState();
				// 500: give the UI task a chance to update the active state
				rcs.schedule(500);
				refreshJob.triggerRefresh();
			}
		};
		Job job = new Job(UIText.Activator_setupFocusListener) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				if (PlatformUI.isWorkbenchRunning())
					PlatformUI.getWorkbench().addWindowListener(focusListener);
				else
					schedule(1000L);
				return Status.OK_STATUS;
			}
		};
		job.schedule();
	}

	@Override
	public void optionsChanged(DebugOptions options) {
		// initialize the trace stuff
		debugOptions = options;
		GitTraceLocation.initializeFromOptions(options, isDebugging());
	}

	/**
	 * @return the {@link DebugOptions}
	 */
	public DebugOptions getDebugOptions() {
		return debugOptions;
	}

	private void setupRepoIndexRefresh() {
		refreshJob = new ResourceRefreshJob();
		refreshHandle = Repository.getGlobalListenerList()
				.addIndexChangedListener(refreshJob);
	}

	/**
	 * Register for changes made to Team properties.
	 *
	 * @param listener
	 *            The listener to register
	 */
	public static synchronized void addPropertyChangeListener(
			IPropertyChangeListener listener) {
		propertyChangeListeners.add(listener);
	}

	/**
	 * Remove a Team property changes.
	 *
	 * @param listener
	 *            The listener to remove
	 */
	public static synchronized void removePropertyChangeListener(
			IPropertyChangeListener listener) {
		propertyChangeListeners.remove(listener);
	}

	/**
	 * Broadcast a Team property change.
	 *
	 * @param event
	 *            The event to broadcast
	 */
	public static synchronized void broadcastPropertyChange(PropertyChangeEvent event) {
		for (IPropertyChangeListener listener : propertyChangeListeners)
			listener.propertyChange(event);
	}

	/**
	 * Refresh projects in repositories that we suspect may have resource
	 * changes.
	 */
	static class ResourceRefreshJob extends Job	implements
			IndexChangedListener {

		ResourceRefreshJob() {
			super(UIText.Activator_refreshJobName);
			setUser(false);
			setSystem(true);
		}

		private Set<Repository> repositoriesChanged = new LinkedHashSet<>();

		@Override
		public IStatus run(IProgressMonitor monitor) {
			Set<Repository> repos;
			synchronized (repositoriesChanged) {
				if (repositoriesChanged.isEmpty()) {
					return Status.OK_STATUS;
				}
				repos = new LinkedHashSet<>(repositoriesChanged);
				repositoriesChanged.clear();
			}
			IWorkspace workspace = ResourcesPlugin.getWorkspace();
			IProject[] projects = workspace.getRoot().getProjects();
			final Set<IProject> toRefresh = new LinkedHashSet<>();
			for (IProject p : projects) {
				if (!p.isAccessible()) {
					continue;
				}
				RepositoryMapping mapping = RepositoryMapping.getMapping(p);
				if (mapping != null
						&& repos.contains(mapping.getRepository())) {
					toRefresh.add(p);
				}
			}

			if (toRefresh.isEmpty()) {
				return Status.OK_STATUS;
			}

			try {
				workspace.run(new IWorkspaceRunnable() {
					@Override
					public void run(IProgressMonitor m) throws CoreException {
						SubMonitor subMonitor = SubMonitor.convert(m,
								UIText.Activator_refreshingProjects,
								toRefresh.size());
						for (IProject p : toRefresh) {
							if (subMonitor.isCanceled()) {
								return;
							}
							ISchedulingRule rule = p.getWorkspace().getRuleFactory().refreshRule(p);
							try {
								getJobManager().beginRule(rule, subMonitor);
								// handle missing projects after branch switch
								if (p.isAccessible()) {
									p.refreshLocal(IResource.DEPTH_INFINITE,
											subMonitor.newChild(1));
								}
							} catch (CoreException e) {
								handleError(UIText.Activator_refreshFailed, e, false);
							} finally {
								getJobManager().endRule(rule);
							}
						}
					}
				}, workspace.getRuleFactory().refreshRule(workspace.getRoot()),
						IWorkspace.AVOID_UPDATE, monitor);
			} catch (CoreException e) {
				handleError(UIText.Activator_refreshFailed, e, false);
				return new Status(IStatus.ERROR, getPluginId(), e.getMessage());
			}

			if (!monitor.isCanceled()) {
				// re-schedule if we got some changes in the meantime
				synchronized (repositoriesChanged) {
					if (!repositoriesChanged.isEmpty()) {
						schedule(100);
					}
				}
			}
			monitor.done();
			return Status.OK_STATUS;
		}

		@Override
		public void onIndexChanged(IndexChangedEvent e) {
			if (Activator.getDefault().getPreferenceStore()
					.getBoolean(UIPreferences.REFESH_ON_INDEX_CHANGE)) {
				mayTriggerRefresh(e);
			}
		}

		/**
		 * Record which projects have changes. Initiate a resource refresh job
		 * if the user settings allow it.
		 *
		 * @param e
		 *            The {@link RepositoryEvent} that triggered this refresh
		 */
		private void mayTriggerRefresh(RepositoryEvent e) {
			synchronized (repositoriesChanged) {
				repositoriesChanged.add(e.getRepository());
			}
			if (!Activator.getDefault().getPreferenceStore()
					.getBoolean(UIPreferences.REFESH_ONLY_WHEN_ACTIVE)
					|| isActive()) {
				triggerRefresh();
			}
		}

		/**
		 * Figure which projects belong to a repository, add them to a set of
		 * project to refresh and schedule the refresh as a job.
		 */
		void triggerRefresh() {
			if (GitTraceLocation.REPOSITORYCHANGESCANNER.isActive()) {
				GitTraceLocation.getTrace().trace(
						GitTraceLocation.REPOSITORYCHANGESCANNER.getLocation(),
						"Triggered refresh"); //$NON-NLS-1$
			}
			schedule();
		}
	}

	/**
	 * A Job that looks at the repository meta data and triggers a refresh of
	 * the resources in the affected projects.
	 */
	private static class RepositoryChangeScanner extends Job
			implements IPropertyChangeListener {

		// volatile in order to ensure thread synchronization
		private volatile boolean doReschedule;

		private int interval;

		private final RepositoryCache repositoryCache;

		RepositoryChangeScanner() {
			super(UIText.Activator_repoScanJobName);
			setRule(new RepositoryCacheRule());
			setSystem(true);
			setUser(false);
			repositoryCache = org.eclipse.egit.core.Activator.getDefault()
					.getRepositoryCache();
			updateRefreshInterval();
		}

		@Override
		public boolean shouldSchedule() {
			return doReschedule;
		}

		@Override
		public boolean shouldRun() {
			return doReschedule;
		}

		void setReschedule(boolean reschedule){
			doReschedule = reschedule;
		}

		@Override
		protected IStatus run(IProgressMonitor monitor) {
			// When people use Git from the command line a lot of changes
			// may happen. Don't scan when inactive depending on the user's
			// choice.
			if (getDefault().getPreferenceStore()
					.getBoolean(UIPreferences.REFESH_ONLY_WHEN_ACTIVE)) {
				if (!isActive()) {
					monitor.done();
					return Status.OK_STATUS;
				}
			}

			Repository[] repos = repositoryCache.getAllRepositories();
			if (repos.length == 0) {
				return Status.OK_STATUS;
			}

			monitor.beginTask(UIText.Activator_scanningRepositories,
					repos.length);
			try {
				for (Repository repo : repos) {
					if (monitor.isCanceled()) {
						break;
					}
					if (GitTraceLocation.REPOSITORYCHANGESCANNER.isActive()) {
						GitTraceLocation.getTrace().trace(
								GitTraceLocation.REPOSITORYCHANGESCANNER
										.getLocation(),
								"Scanning " + repo + " for changes"); //$NON-NLS-1$ //$NON-NLS-2$
					}

					repo.scanForRepoChanges();
					monitor.worked(1);
				}
			} catch (IOException e) {
				if (GitTraceLocation.REPOSITORYCHANGESCANNER.isActive()) {
					GitTraceLocation.getTrace().trace(
							GitTraceLocation.REPOSITORYCHANGESCANNER
									.getLocation(),
							"Stopped rescheduling " + getName() + "job"); //$NON-NLS-1$ //$NON-NLS-2$
				}
				return createErrorStatus(UIText.Activator_scanError, e);
			} finally {
				monitor.done();
			}
			if (GitTraceLocation.REPOSITORYCHANGESCANNER.isActive()) {
				GitTraceLocation.getTrace().trace(
						GitTraceLocation.REPOSITORYCHANGESCANNER.getLocation(),
						"Rescheduling " + getName() + " job"); //$NON-NLS-1$ //$NON-NLS-2$
			}
			schedule(interval);
			return Status.OK_STATUS;
		}

		@Override
		public void propertyChange(PropertyChangeEvent event) {
			if (!UIPreferences.REFESH_INDEX_INTERVAL
					.equals(event.getProperty())) {
				return;
			}
			updateRefreshInterval();
		}

		private void updateRefreshInterval() {
			interval = getRefreshIndexInterval();
			setReschedule(interval > 0);
			cancel();
			schedule(interval);
		}

		/**
		 * @return interval in milliseconds for automatic index check, 0 is if
		 *         check should be disabled
		 */
		private static int getRefreshIndexInterval() {
			return 1000 * getDefault().getPreferenceStore()
					.getInt(UIPreferences.REFESH_INDEX_INTERVAL);
		}
	}

	private void setupRepoChangeScanner() {
		rcs = new RepositoryChangeScanner();
		getPreferenceStore().addPropertyChangeListener(rcs);
	}

	@Override
	public void stop(final BundleContext context) throws Exception {
		if (refreshHandle != null) {
			refreshHandle.remove();
			refreshHandle = null;
		}

		if (focusListener != null) {
			if (PlatformUI.isWorkbenchRunning()) {
				PlatformUI.getWorkbench().removeWindowListener(focusListener);
			}
			focusListener = null;
		}

		if (GitTraceLocation.REPOSITORYCHANGESCANNER.isActive()) {
			GitTraceLocation.getTrace().trace(
					GitTraceLocation.REPOSITORYCHANGESCANNER.getLocation(),
					"Trying to cancel " + rcs.getName() + " job"); //$NON-NLS-1$ //$NON-NLS-2$
		}

		getPreferenceStore().removePropertyChangeListener(rcs);
		rcs.setReschedule(false);
		rcs.cancel();
		if (GitTraceLocation.REPOSITORYCHANGESCANNER.isActive()) {
			GitTraceLocation.getTrace().trace(
					GitTraceLocation.REPOSITORYCHANGESCANNER.getLocation(),
					"Trying to cancel " + refreshJob.getName() + " job"); //$NON-NLS-1$ //$NON-NLS-2$
		}
		refreshJob.cancel();

		rcs.join();
		refreshJob.join();

		if (GitTraceLocation.REPOSITORYCHANGESCANNER.isActive()) {
			GitTraceLocation.getTrace().trace(
					GitTraceLocation.REPOSITORYCHANGESCANNER.getLocation(),
					"Jobs terminated"); //$NON-NLS-1$
		}
		if (resourceManager != null) {
			resourceManager.dispose();
			resourceManager = null;
		}
		super.stop(context);
		plugin = null;
	}

	@Override
	protected void saveDialogSettings() {
		KnownHosts.store();
		super.saveDialogSettings();
	}
	/**
	 * @return the {@link RepositoryUtil} instance
	 */
	public RepositoryUtil getRepositoryUtil() {
		return org.eclipse.egit.core.Activator.getDefault().getRepositoryUtil();
	}

	/**
	 * Gets this plugin's {@link ResourceManager}.
	 *
	 * @return the {@link ResourceManager} of this plugin
	 */
	public synchronized ResourceManager getResourceManager() {
		if (resourceManager == null) {
			Display display = PlatformUI.getWorkbench().getDisplay();
			if (display == null) {
				// Workbench already closed?
				throw new IllegalStateException();
			}
			resourceManager = new LocalResourceManager(JFaceResources
					.getResources(display));
		}
		return resourceManager;
	}

	/**
	 * @return true if the Java Plugin is loaded
	 */
	public static final boolean hasJavaPlugin() {
		try {
			return Class.forName("org.eclipse.jdt.internal.ui.JavaPlugin") != null; //$NON-NLS-1$
		} catch (ClassNotFoundException e) {
			return false;
		}
	}
}
