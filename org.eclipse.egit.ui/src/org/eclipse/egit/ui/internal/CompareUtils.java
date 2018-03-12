/*******************************************************************************
 * Copyright (c) 2010, 2015 SAP AG and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Dariusz Luksza - add getFileCachedRevisionTypedElement(String, Repository)
 *    Stefan Lay (SAP AG) - initial implementation
 *    Yann Simon <yann.simon.fr@gmail.com> - implementation of getHeadTypedElement
 *    Robin Stocker <robin@nibor.org>
 *    Laurent Goubet <laurent.goubet@obeo.fr>
 *    Gunnar Wagenknecht <gunnar@wagenknecht.org>
 *******************************************************************************/
package org.eclipse.egit.ui.internal;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.eclipse.compare.CompareEditorInput;
import org.eclipse.compare.CompareUI;
import org.eclipse.compare.IContentChangeListener;
import org.eclipse.compare.IContentChangeNotifier;
import org.eclipse.compare.ITypedElement;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.resources.mapping.RemoteResourceMappingContext;
import org.eclipse.core.resources.mapping.ResourceMapping;
import org.eclipse.core.resources.mapping.ResourceMappingContext;
import org.eclipse.core.resources.mapping.ResourceTraversal;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.IPreferenceChangeListener;
import org.eclipse.core.runtime.preferences.IEclipsePreferences.PreferenceChangeEvent;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.egit.core.RevUtils;
import org.eclipse.egit.core.internal.CompareCoreUtils;
import org.eclipse.egit.core.internal.storage.GitFileRevision;
import org.eclipse.egit.core.internal.storage.WorkingTreeFileRevision;
import org.eclipse.egit.core.internal.storage.WorkspaceFileRevision;
import org.eclipse.egit.core.internal.util.ResourceUtil;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.egit.core.synchronize.GitResourceVariantTreeSubscriber;
import org.eclipse.egit.core.synchronize.GitSubscriberResourceMappingContext;
import org.eclipse.egit.core.synchronize.dto.GitSynchronizeData;
import org.eclipse.egit.core.synchronize.dto.GitSynchronizeDataSet;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIPreferences;
import org.eclipse.egit.ui.internal.merge.GitCompareEditorInput;
import org.eclipse.egit.ui.internal.revision.EditableRevision;
import org.eclipse.egit.ui.internal.revision.FileRevisionTypedElement;
import org.eclipse.egit.ui.internal.revision.GitCompareFileRevisionEditorInput;
import org.eclipse.egit.ui.internal.revision.GitCompareFileRevisionEditorInput.EmptyTypedElement;
import org.eclipse.egit.ui.internal.synchronize.GitModelSynchronize;
import org.eclipse.egit.ui.internal.synchronize.compare.LocalNonWorkspaceTypedElement;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.util.OpenStrategy;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.CoreConfig.AutoCRLF;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.WorkingTreeOptions;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.IO;
import org.eclipse.jgit.util.io.EolCanonicalizingInputStream;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Display;
import org.eclipse.team.core.history.IFileRevision;
import org.eclipse.team.ui.synchronize.SaveableCompareEditorInput;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IReusableEditor;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory.IWorkbenchAction;

/**
 * A collection of helper methods useful for comparing content
 */
public class CompareUtils {
	/**
	 * A copy of the non-accessible preference constant
	 * IPreferenceIds.REUSE_OPEN_COMPARE_EDITOR from the team ui plug in
	 */
	private static final String REUSE_COMPARE_EDITOR_PREFID = "org.eclipse.team.ui.reuse_open_compare_editors"; //$NON-NLS-1$

	/** The team ui plugin ID which is not accessible */
	private static final String TEAM_UI_PLUGIN = "org.eclipse.team.ui"; //$NON-NLS-1$

	/**
	 *
	 * @param gitPath
	 *            path within the commit's tree of the file.
	 * @param commit
	 *            the commit the blob was identified to be within.
	 * @param db
	 *            the repository this commit was loaded out of.
	 * @return an instance of {@link ITypedElement} which can be used in
	 *         {@link CompareEditorInput}
	 */
	public static ITypedElement getFileRevisionTypedElement(
			final String gitPath, final RevCommit commit, final Repository db) {
		return getFileRevisionTypedElement(gitPath, commit, db, null);
	}

	/**
	 * @param gitPath
	 *            path within the commit's tree of the file.
	 * @param commit
	 *            the commit the blob was identified to be within.
	 * @param db
	 *            the repository this commit was loaded out of, and that this
	 *            file's blob should also be reachable through.
	 * @param blobId
	 *            unique name of the content.
	 * @return an instance of {@link ITypedElement} which can be used in
	 *         {@link CompareEditorInput}
	 */
	public static ITypedElement getFileRevisionTypedElement(
			final String gitPath, final RevCommit commit, final Repository db,
			ObjectId blobId) {
		ITypedElement right = new GitCompareFileRevisionEditorInput.EmptyTypedElement(
				NLS.bind(UIText.GitHistoryPage_FileNotInCommit,
						getName(gitPath), truncatedRevision(commit.name())));

		try {
			IFileRevision nextFile = getFileRevision(gitPath, commit, db,
							blobId);
				if (nextFile != null) {
					String encoding = CompareCoreUtils.getResourceEncoding(db, gitPath);
					right = new FileRevisionTypedElement(nextFile, encoding);
				}
		} catch (IOException e) {
			Activator.error(NLS.bind(UIText.GitHistoryPage_errorLookingUpPath,
					gitPath, commit.getId()), e);
		}
		return right;
	}

	private static String getName(String gitPath) {
		final int last = gitPath.lastIndexOf('/');
		return last >= 0 ? gitPath.substring(last + 1) : gitPath;
	}

	/**
	 *
	 * @param gitPath
	 *            path within the commit's tree of the file.
	 * @param commit
	 *            the commit the blob was identified to be within.
	 * @param db
	 *            the repository this commit was loaded out of, and that this
	 *            file's blob should also be reachable through.
	 * @param blobId
	 *            unique name of the content.
	 * @return an instance of {@link IFileRevision} or null if the file is not
	 *         contained in {@code commit}
	 * @throws IOException
	 */
	public static IFileRevision getFileRevision(final String gitPath,
			final RevCommit commit, final Repository db, ObjectId blobId)
			throws IOException {

		TreeWalk w = TreeWalk.forPath(db, gitPath, commit.getTree());
		// check if file is contained in commit
		if (w != null) {
			final IFileRevision fileRevision = GitFileRevision.inCommit(db,
					commit, gitPath, blobId);
			return fileRevision;
		}
		return null;
	}


	/**
	 * Creates a {@link ITypedElement} for the commit which is the common
	 * ancestor of the provided commits. Returns null if no such commit exists
	 * or if {@code gitPath} is not contained in the common ancestor
	 *
	 * @param gitPath
	 *            path within the ancestor commit's tree of the file.
	 * @param commit1
	 * @param commit2
	 * @param db
	 *            the repository this commit was loaded out of.
	 * @return an instance of {@link ITypedElement} which can be used in
	 *         {@link CompareEditorInput}
	 */
	public static ITypedElement getFileRevisionTypedElementForCommonAncestor(
			final String gitPath, ObjectId commit1, ObjectId commit2,
			Repository db) {
		ITypedElement ancestor = null;
		RevCommit commonAncestor = null;
		try {
			commonAncestor = RevUtils.getCommonAncestor(db, commit1, commit2);
		} catch (IOException e) {
			Activator.logError(NLS.bind(UIText.CompareUtils_errorCommonAncestor,
					commit1.getName(), commit2.getName()), e);
		}
		if (commonAncestor != null) {
			ITypedElement ancestorCandidate = CompareUtils
					.getFileRevisionTypedElement(gitPath, commonAncestor, db);
			if (!(ancestorCandidate instanceof EmptyTypedElement))
				ancestor = ancestorCandidate;
		}
		return ancestor;
	}
/**
	 * @param element
	 * @param adapterType
	 * @return the adapted element, or null
	 */
	public static Object getAdapter(Object element, Class adapterType) {
		return getAdapter(element, adapterType, false);
	}

	/**
	 * @param ci
	 * @return a truncated revision identifier if it is long
	 */
	public static String truncatedRevision(String ci) {
		if (ObjectId.isId(ci))
			return ci.substring(0, 7);
		else
			return ci;
	}

	/**
	 * @param element
	 * @param adapterType
	 * @param load
	 * @return the adapted element, or null
	 */
	private static Object getAdapter(Object element, Class adapterType,
			boolean load) {
		if (adapterType.isInstance(element))
			return element;
		if (element instanceof IAdaptable) {
			Object adapted = CommonUtils.getAdapter(((IAdaptable) element), adapterType);
			if (adapterType.isInstance(adapted))
				return adapted;
		}
		if (load) {
			Object adapted = Platform.getAdapterManager().loadAdapter(element,
					adapterType.getName());
			if (adapterType.isInstance(adapted))
				return adapted;
		} else {
			Object adapted = Platform.getAdapterManager().getAdapter(element,
					adapterType);
			if (adapterType.isInstance(adapted))
				return adapted;
		}
		return null;
	}

	/**
	 * Compares two files between the given commits, taking possible renames
	 * into account.
	 *
	 * @param commit1
	 *            the "left" commit for the comparison editor
	 * @param commit2
	 *            the "right" commit for the comparison editor
	 * @param commit1Path
	 *            path to the file within commit1's tree
	 * @param commit2Path
	 *            path to the file within commit2's tree
	 * @param repository
	 *            the repository this commit was loaded out of
	 * @param workBenchPage
	 *            the page to open the compare editor in
	 */
	public static void openInCompare(RevCommit commit1, RevCommit commit2,
			String commit1Path, String commit2Path, Repository repository,
			IWorkbenchPage workBenchPage) {
		final ITypedElement base = CompareUtils.getFileRevisionTypedElement(
				commit1Path, commit1, repository);
		final ITypedElement next = CompareUtils.getFileRevisionTypedElement(
				commit2Path, commit2, repository);
		CompareEditorInput in = new GitCompareFileRevisionEditorInput(base,
				next, null);
		CompareUtils.openInCompare(workBenchPage, in);
	}

	/**
	 * @param workBenchPage
	 * @param input
	 */
	public static void openInCompare(IWorkbenchPage workBenchPage,
			CompareEditorInput input) {
		IEditorPart editor = findReusableCompareEditor(input, workBenchPage);
		if (editor != null) {
			IEditorInput otherInput = editor.getEditorInput();
			if (otherInput.equals(input)) {
				// simply provide focus to editor
				if (OpenStrategy.activateOnOpen())
					workBenchPage.activate(editor);
				else
					workBenchPage.bringToTop(editor);
			} else {
				// if editor is currently not open on that input either re-use
				// existing
				CompareUI.reuseCompareEditor(input, (IReusableEditor) editor);
				if (OpenStrategy.activateOnOpen())
					workBenchPage.activate(editor);
				else
					workBenchPage.bringToTop(editor);
			}
		} else {
			CompareUI.openCompareEditor(input);
		}
	}

	private static IEditorPart findReusableCompareEditor(
			CompareEditorInput input, IWorkbenchPage page) {
		IEditorReference[] editorRefs = page.getEditorReferences();
		// first loop looking for an editor with the same input
		for (int i = 0; i < editorRefs.length; i++) {
			IEditorPart part = editorRefs[i].getEditor(false);
			if (part != null
					&& (part.getEditorInput() instanceof GitCompareFileRevisionEditorInput || part.getEditorInput() instanceof GitCompareEditorInput)
					&& part instanceof IReusableEditor
					&& part.getEditorInput().equals(input)) {
				return part;
			}
		}
		// if none found and "Reuse open compare editors" preference is on use
		// a non-dirty editor
		if (isReuseOpenEditor()) {
			for (int i = 0; i < editorRefs.length; i++) {
				IEditorPart part = editorRefs[i].getEditor(false);
				if (part != null
						&& (part.getEditorInput() instanceof SaveableCompareEditorInput)
						&& part instanceof IReusableEditor && !part.isDirty()) {
					return part;
				}
			}
		}
		// no re-usable editor found
		return null;
	}

	/**
	 * Action to toggle the team 'reuse compare editor' preference
	 */
	public static class ReuseCompareEditorAction extends Action implements
			IPreferenceChangeListener, IWorkbenchAction {
		IEclipsePreferences node = InstanceScope.INSTANCE.getNode(TEAM_UI_PLUGIN);

		/**
		 * Default constructor
		 */
		public ReuseCompareEditorAction() {
			node.addPreferenceChangeListener(this);
			setText(UIText.GitHistoryPage_ReuseCompareEditorMenuLabel);
			setChecked(CompareUtils.isReuseOpenEditor());
		}

		@Override
		public void run() {
			CompareUtils.setReuseOpenEditor(isChecked());
		}

		@Override
		public void dispose() {
			// stop listening
			node.removePreferenceChangeListener(this);
		}

		@Override
		public void preferenceChange(PreferenceChangeEvent event) {
			setChecked(isReuseOpenEditor());

		}
	}

	private static boolean isReuseOpenEditor() {
		boolean defaultReuse = DefaultScope.INSTANCE.getNode(TEAM_UI_PLUGIN)
				.getBoolean(REUSE_COMPARE_EDITOR_PREFID, false);
		return InstanceScope.INSTANCE.getNode(TEAM_UI_PLUGIN).getBoolean(
				REUSE_COMPARE_EDITOR_PREFID, defaultReuse);
	}

	private static void setReuseOpenEditor(boolean value) {
		InstanceScope.INSTANCE.getNode(TEAM_UI_PLUGIN).putBoolean(
				REUSE_COMPARE_EDITOR_PREFID, value);
	}

	/**
	 * Opens a compare editor. The workspace version of the given file is
	 * compared with the version in the HEAD commit.
	 *
	 * @param repository
	 * @param file
	 */
	public static void compareHeadWithWorkspace(Repository repository,
			IFile file) {
		RepositoryMapping mapping = RepositoryMapping.getMapping(file);
		if (mapping == null) {
			Activator.error(NLS.bind(UIText.GitHistoryPage_errorLookingUpPath,
					file.getLocation(), repository), null);
			return;
		}
		String path = mapping.getRepoRelativePath(
				file);
		ITypedElement base = getHeadTypedElement(repository, path);
		if (base == null)
			return;

		IFileRevision nextFile = new WorkspaceFileRevision(file);
		String encoding = null;
		try {
			encoding = file.getCharset();
		} catch (CoreException e) {
			Activator.handleError(UIText.CompareUtils_errorGettingEncoding, e, true);
		}
		ITypedElement next = new FileRevisionTypedElement(nextFile, encoding);
		GitCompareFileRevisionEditorInput input = new GitCompareFileRevisionEditorInput(
				next, base, null);
		CompareUI.openCompareDialog(input);
	}

	/**
	 * Opens a compare editor comparing the working directory version of the
	 * given IFile with the version of that file corresponding to
	 * {@code refName}.
	 *
	 * @param repository
	 *            The repository to load file revisions from.
	 * @param file
	 *            File to compare revisions for.
	 * @param refName
	 *            Reference to compare with the workspace version of
	 *            {@code file}. Can be either a commit ID, a reference or a
	 *            branch name.
	 * @param page
	 *            If not {@null} try to re-use a compare editor on this
	 *            page if any is available. Otherwise open a new one.
	 */
	private static void compareWorkspaceWithRef(final Repository repository,
			final IFile file, final String refName, final IWorkbenchPage page) {

		Job job = new Job(UIText.CompareUtils_jobName) {

			@Override
			public IStatus run(IProgressMonitor monitor) {
				if (monitor.isCanceled()) {
					return Status.CANCEL_STATUS;
				}
				final RepositoryMapping mapping = RepositoryMapping
						.getMapping(file);
				if (mapping == null) {
					return Activator.createErrorStatus(
							NLS.bind(UIText.GitHistoryPage_errorLookingUpPath,
									file.getLocation(), repository));
				}
				final String gitPath = mapping.getRepoRelativePath(file);
				final ITypedElement base = SaveableCompareEditorInput
						.createFileElement(file);

				CompareEditorInput in;
				try {
					in = prepareCompareInput(repository, gitPath, base, refName);
				} catch (IOException e) {
					return Activator.createErrorStatus(
							UIText.CompareWithRefAction_errorOnSynchronize, e);
				}

				if (monitor.isCanceled()) {
					return Status.CANCEL_STATUS;
				}
				openCompareEditorRunnable(page, in);
				return Status.OK_STATUS;
			}
		};
		job.setUser(true);
		job.schedule();
	}

	/**
	 * Opens compare editor in UI thread. Safe to start from background threads
	 * too - in this case the operation will be started asynchronously in UI
	 * thread.
	 *
	 * @param page
	 *            can be null
	 * @param in
	 *            non null
	 */
	private static void openCompareEditorRunnable(
			final IWorkbenchPage page,
			final CompareEditorInput in) {
		// safety check: make sure we open compare editor from UI thread
		if (Display.getCurrent() == null) {
			PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
				@Override
				public void run() {
					openCompareEditorRunnable(page, in);
				}
			});
			return;
		}

		if (page != null) {
			openInCompare(page, in);
		} else {
			CompareUI.openCompareEditor(in);
		}
	}

	/**
	 * Opens a compare editor comparing the working directory version of the
	 * file at the given location with the version corresponding to
	 * {@code refName} of the same file.
	 *
	 * @param repository
	 *            The repository to load file revisions from.
	 * @param location
	 *            Location of the file to compare revisions for.
	 * @param refName
	 *            Reference to compare with the workspace version of
	 *            {@code file}. Can be either a commit ID, a reference or a
	 *            branch name.
	 * @param page
	 *            If not {@null} try to re-use a compare editor on this
	 *            page if any is available. Otherwise open a new one.
	 */
	private static void compareLocalWithRef(final Repository repository,
			final IPath location, final String refName,
			final IWorkbenchPage page) {

		Job job = new Job(UIText.CompareUtils_jobName) {

			@Override
			public IStatus run(IProgressMonitor monitor) {
				if (monitor.isCanceled()) {
					return Status.CANCEL_STATUS;
				}
				final String gitPath = getRepoRelativePath(location, repository);
				final ITypedElement base = new LocalNonWorkspaceTypedElement(
						location);

				CompareEditorInput in;
				try {
					in = prepareCompareInput(repository, gitPath, base, refName);
				} catch (IOException e) {
					return Activator.createErrorStatus(
							UIText.CompareWithRefAction_errorOnSynchronize, e);
				}

				if (monitor.isCanceled()) {
					return Status.CANCEL_STATUS;
				}
				openCompareEditorRunnable(page, in);
				return Status.OK_STATUS;
			}
		};
		job.setUser(true);
		job.schedule();
	}

	/*
	 * Creates a compare input that can be used to compare a given local file
	 * with another reference. The given "base" element should always reflect a
	 * local file, either in the workspace (IFile) or on the file system
	 * (java.io.File) since we'll use "HEAD" to find a common ancestor of this
	 * base and the reference we compare it with.
	 */
	private static CompareEditorInput prepareCompareInput(
			Repository repository, String gitPath, ITypedElement base,
			String refName) throws IOException {
		final ITypedElement destCommit;
		ITypedElement commonAncestor = null;

		if (GitFileRevision.INDEX.equals(refName))
			destCommit = getIndexTypedElement(repository, gitPath);
		else if (Constants.HEAD.equals(refName))
			destCommit = getHeadTypedElement(repository, gitPath);
		else {
			final ObjectId destCommitId = repository.resolve(refName);
			try (RevWalk rw = new RevWalk(repository)) {
				RevCommit commit = rw.parseCommit(destCommitId);
				destCommit = getFileRevisionTypedElement(gitPath, commit,
						repository);

				if (base != null && commit != null) {
					final ObjectId headCommitId = repository
							.resolve(Constants.HEAD);
					commonAncestor = getFileRevisionTypedElementForCommonAncestor(
							gitPath, headCommitId, destCommitId, repository);
				}
			}
		}


		final GitCompareFileRevisionEditorInput in = new GitCompareFileRevisionEditorInput(
				base, destCommit, commonAncestor, null);
		in.getCompareConfiguration().setRightLabel(refName);
		return in;
	}

	/**
	 * This can be used to compare a given set of resources between two
	 * revisions. If only one resource is to be compared, and that resource is
	 * not part of a more important model (as defined in
	 * {@link #canDirectlyOpenInCompare(IFile, ResourceMappingContext)}, we'll
	 * open a comparison editor for that file alone. Otherwise, we'll launch a
	 * synchronization restrained of the given resources set.
	 * <p>
	 * This can also be used to synchronize the whole repository if
	 * <code>resources</code> is empty.
	 * </p>
	 * <p>
	 * Note that this can be used to compare with the index by using
	 * {@link GitFileRevision#INDEX} as either one of the two revs.
	 * </p>
	 *
	 * @param resources
	 *            The set of resources to compare. Can be empty (in which case
	 *            we'll synchronize the whole repository).
	 * @param repository
	 *            The repository to load file revisions from.
	 * @param leftRev
	 *            Left revision of the comparison (usually the local or "new"
	 *            revision). Won't be used if <code>includeLocal</code> is
	 *            <code>true</code>.
	 * @param rightRev
	 *            Right revision of the comparison (usually the "old" revision).
	 * @param includeLocal
	 *            If <code>true</code>, this will use the local data as the
	 *            "left" side of the comparison.
	 * @param page
	 *            If not {@null} try to re-use a compare editor on this
	 *            page if any is available. Otherwise open a new one.
	 * @throws IOException
	 */
	public static void compare(IResource[] resources, Repository repository,
			String leftRev, String rightRev, boolean includeLocal,
			IWorkbenchPage page) throws IOException {
		ResourceMappingContext mappingContext = prepareContext(repository,
				leftRev, rightRev, includeLocal);
		if (resources.length == 1
				&& resources[0] instanceof IFile
				&& canDirectlyOpenInCompare((IFile) resources[0],
						mappingContext)) {
			if (includeLocal)
				compareWorkspaceWithRef(repository, (IFile) resources[0],
						rightRev, page);
			else {
				final IFile file = (IFile) resources[0];
				final RepositoryMapping mapping = RepositoryMapping
						.getMapping(file);
				if (mapping == null) {
					Activator.error(NLS.bind(UIText.GitHistoryPage_errorLookingUpPath,
							file.getLocation(), repository), null);
					return;
				}
				final String gitPath = mapping.getRepoRelativePath(file);

				compareBetween(repository, gitPath, leftRev, rightRev, page);
			}
		} else
			GitModelSynchronize.synchronize(resources, repository, leftRev,
					rightRev, includeLocal, mappingContext);
	}

	/**
	 * This can be used to compare a given set of resources between two
	 * revisions. If only one resource is to be compared, and that resource is
	 * not part of a more important model (as defined in
	 * {@link #canDirectlyOpenInCompare(IFile, ResourceMappingContext)}, we'll
	 * open a comparison editor for that file alone, also taking leftPath and
	 * rightPath into account. Otherwise, we'll launch a synchronization
	 * restrained of the given resources set.
	 * <p>
	 * This can also be used to synchronize the whole repository if
	 * <code>resources</code> is empty.
	 * </p>
	 * <p>
	 * Note that this can be used to compare with the index by using
	 * {@link GitFileRevision#INDEX} as either one of the two revs.
	 * </p>
	 *
	 * @param resources
	 *            The set of resources to compare. Can be empty (in which case
	 *            we'll synchronize the whole repository).
	 * @param repository
	 *            The repository to load file revisions from.
	 * @param leftPath
	 *            The repository relative path to be used for the left revision,
	 *            when comparing directly.
	 * @param rightPath
	 *            The repository relative path to be used for the right
	 *            revision, when comparing directly.
	 * @param leftRev
	 *            Left revision of the comparison (usually the local or "new"
	 *            revision). Won't be used if <code>includeLocal</code> is
	 *            <code>true</code>.
	 * @param rightRev
	 *            Right revision of the comparison (usually the "old" revision).
	 * @param includeLocal
	 *            If <code>true</code>, this will use the local data as the
	 *            "left" side of the comparison.
	 * @param page
	 *            If not {@null} try to re-use a compare editor on this
	 *            page if any is available. Otherwise open a new one.
	 * @throws IOException
	 */
	public static void compare(IResource[] resources, Repository repository,
			String leftPath, String rightPath, String leftRev, String rightRev,
			boolean includeLocal, IWorkbenchPage page) throws IOException {
		ResourceMappingContext mappingContext = prepareContext(repository,
				leftRev, rightRev, includeLocal);
		if (resources.length == 1
				&& resources[0] instanceof IFile
				&& canDirectlyOpenInCompare((IFile) resources[0],
						mappingContext)) {
			if (includeLocal)
				compareWorkspaceWithRef(repository, (IFile) resources[0],
						rightRev, page);
			else {
				compareBetween(repository, leftPath, rightPath, leftRev,
						rightRev, page);
			}
		} else
			GitModelSynchronize.synchronize(resources, repository, leftRev,
					rightRev, includeLocal, mappingContext);
	}

	/**
	 * This can be used to compare a given file between two revisions.
	 *
	 * @param location
	 *            Location of the file to compare.
	 * @param repository
	 *            The repository to load file revisions from.
	 * @param leftRev
	 *            Left revision of the comparison (usually the local or "new"
	 *            revision). Won't be used if <code>includeLocal</code> is
	 *            <code>true</code>.
	 * @param rightRev
	 *            Right revision of the comparison (usually the "old" revision).
	 * @param includeLocal
	 *            If <code>true</code>, this will use the local data as the
	 *            "left" side of the comparison.
	 * @param page
	 *            If not {@null} try to re-use a compare editor on this
	 *            page if any is available. Otherwise open a new one.
	 */
	public static void compare(IPath location, Repository repository,
			String leftRev, String rightRev, boolean includeLocal,
			IWorkbenchPage page) {
		if (includeLocal)
			compareLocalWithRef(repository, location, rightRev, page);
		else {
			String gitPath = getRepoRelativePath(location, repository);
			compareBetween(repository, gitPath, leftRev, rightRev, page);
		}
	}

	private static void compareBetween(Repository repository, String gitPath,
			String leftRev, String rightRev, IWorkbenchPage page) {
		compareBetween(repository, gitPath, gitPath, leftRev, rightRev, page);
	}

	/**
	 * Compares two explicit files specified by leftGitPath and rightGitPath
	 * between the two revisions leftRev and rightRev.
	 *
	 * @param repository
	 *            The repository to load file revisions from.
	 * @param leftGitPath
	 *            The repository relative path to be used for the left revision.
	 * @param rightGitPath
	 *            The repository relative path to be used for the right
	 *            revision.
	 * @param leftRev
	 *            Left revision of the comparison (usually the local or "new"
	 *            revision). Won't be used if <code>includeLocal</code> is
	 *            <code>true</code>.
	 * @param rightRev
	 *            Right revision of the comparison (usually the "old" revision).
	 * @param page
	 *            If not {@null} try to re-use a compare editor on this
	 *            page if any is available. Otherwise open a new one.
	 */
	private static void compareBetween(final Repository repository,
			final String leftGitPath, final String rightGitPath,
			final String leftRev, final String rightRev,
			final IWorkbenchPage page) {

		Job job = new Job(UIText.CompareUtils_jobName) {

			@Override
			public IStatus run(IProgressMonitor monitor) {
				if (monitor.isCanceled()) {
					return Status.CANCEL_STATUS;
				}
				final ITypedElement left;
				final ITypedElement right;
				try {
					left = getTypedElementFor(repository, leftGitPath, leftRev);
					right = getTypedElementFor(repository, rightGitPath,
							rightRev);
				} catch (IOException e) {
					return Activator.createErrorStatus(
							UIText.CompareWithRefAction_errorOnSynchronize, e);
				}
				final ITypedElement commonAncestor;
				if (left != null && right != null
						&& !GitFileRevision.INDEX.equals(leftRev)
						&& !GitFileRevision.INDEX.equals(rightRev)) {
					commonAncestor = getTypedElementForCommonAncestor(
							repository, rightGitPath, leftRev, rightRev);
				} else {
					commonAncestor = null;
				}

				final GitCompareFileRevisionEditorInput in = new GitCompareFileRevisionEditorInput(
						left, right, commonAncestor, null);
				in.getCompareConfiguration().setLeftLabel(leftRev);
				in.getCompareConfiguration().setRightLabel(rightRev);
				if (monitor.isCanceled()) {
					return Status.CANCEL_STATUS;
				}
				openCompareEditorRunnable(page, in);
				return Status.OK_STATUS;
			}
		};
		job.setUser(true);
		job.schedule();
	}

	private static String getRepoRelativePath(IPath location,
			Repository repository) {
		RepositoryMapping mapping = RepositoryMapping.getMapping(location);
		final String gitPath;
		if (mapping != null)
			gitPath = mapping.getRepoRelativePath(location);
		else {
			IPath repoRoot = new Path(repository.getWorkTree().getPath());
			gitPath = location.makeRelativeTo(repoRoot).toString();
		}
		return gitPath;
	}

	private static ITypedElement getTypedElementFor(Repository repository, String gitPath, String rev) throws IOException {
		final ITypedElement typedElement;
		if (GitFileRevision.INDEX.equals(rev))
			typedElement = getIndexTypedElement(repository, gitPath);
		else if (Constants.HEAD.equals(rev))
			typedElement = getHeadTypedElement(repository, gitPath);
		else {
			final ObjectId id = repository.resolve(rev);
			try (final RevWalk rw = new RevWalk(repository)) {
				final RevCommit revCommit = rw.parseCommit(id);
				typedElement = getFileRevisionTypedElement(gitPath, revCommit,
						repository);
			}
		}
		return typedElement;
	}

	private static ITypedElement getTypedElementForCommonAncestor(
			Repository repository, final String gitPath, String srcRev,
			String dstRev) {
		ITypedElement ancestor = null;
		try {
			final ObjectId srcID = repository.resolve(srcRev);
			final ObjectId dstID = repository.resolve(dstRev);
			if (srcID != null && dstID != null)
				ancestor = getFileRevisionTypedElementForCommonAncestor(
						gitPath, srcID, dstID, repository);
		} catch (IOException e) {
			Activator
					.logError(NLS.bind(UIText.CompareUtils_errorCommonAncestor,
							srcRev, dstRev), e);
		}
		return ancestor;
	}

	/**
	 * Opens a compare editor. The working tree version of the given file is
	 * compared with the version in the HEAD commit. Use this method if the
	 * given file is outide the workspace.
	 *
	 * @param repository
	 * @param path
	 */
	public static void compareHeadWithWorkingTree(Repository repository,
			String path) {
		ITypedElement base = getHeadTypedElement(repository, path);
		if (base == null)
			return;
		IFileRevision nextFile;
		nextFile = new WorkingTreeFileRevision(new File(
				repository.getWorkTree(), path));
		String encoding = ResourcesPlugin.getEncoding();
		ITypedElement next = new FileRevisionTypedElement(nextFile, encoding);
		GitCompareFileRevisionEditorInput input = new GitCompareFileRevisionEditorInput(
				next, base, null);
		CompareUI.openCompareDialog(input);
	}

	/**
	 * Get a typed element for the file as contained in HEAD. Tries to return
	 * the last commit that modified the file in order to have more useful
	 * author information.
	 * <p>
	 * Returns an empty typed element if there is not yet a head (initial import
	 * case).
	 * <p>
	 * If there is an error getting the HEAD commit, it is handled and null
	 * returned.
	 *
	 * @param repository
	 * @param repoRelativePath
	 * @return typed element, or null if there was an error getting the HEAD
	 *         commit
	 */
	public static ITypedElement getHeadTypedElement(Repository repository, String repoRelativePath) {
		try {
			Ref head = repository.getRef(Constants.HEAD);
			if (head == null || head.getObjectId() == null)
				// Initial import, not yet a HEAD commit
				return new EmptyTypedElement(""); //$NON-NLS-1$

			RevCommit latestFileCommit;
			try (RevWalk rw = new RevWalk(repository)) {
				RevCommit headCommit = rw.parseCommit(head.getObjectId());
				rw.markStart(headCommit);
				rw.setTreeFilter(AndTreeFilter.create(
						PathFilter.create(repoRelativePath),
						TreeFilter.ANY_DIFF));
				latestFileCommit = rw.next();
				// Fall back to HEAD
				if (latestFileCommit == null)
					latestFileCommit = headCommit;
			}

			return CompareUtils.getFileRevisionTypedElement(repoRelativePath, latestFileCommit, repository);
		} catch (IOException e) {
			Activator.handleError(UIText.CompareUtils_errorGettingHeadCommit,
					e, true);
			return null;
		}
	}

	/**
	 * Get a typed element for the file in the index.
	 *
	 * @param baseFile
	 * @return typed element
	 * @throws IOException
	 */
	public static ITypedElement getIndexTypedElement(final IFile baseFile)
			throws IOException {
		final RepositoryMapping mapping = RepositoryMapping.getMapping(baseFile);
		if (mapping == null) {
			Activator.error(NLS.bind(UIText.GitHistoryPage_errorLookingUpPath,
					baseFile.getLocation(), null), null);
			return null;
		}
		final Repository repository = mapping.getRepository();
		final String gitPath = mapping.getRepoRelativePath(baseFile);
		final String encoding = CompareCoreUtils.getResourceEncoding(baseFile);
		return getIndexTypedElement(repository, gitPath, encoding);
	}

	/**
	 * Get a typed element for the repository and repository-relative path in the index.
	 *
	 * @param repository
	 * @param repoRelativePath
	 * @return typed element
	 * @throws IOException
	 */
	public static ITypedElement getIndexTypedElement(
			final Repository repository, final String repoRelativePath)
			throws IOException {
		String encoding = CompareCoreUtils.getResourceEncoding(repository, repoRelativePath);
		return getIndexTypedElement(repository, repoRelativePath, encoding);
	}

	private static ITypedElement getIndexTypedElement(
			final Repository repository, final String gitPath, String encoding) {
		IFileRevision nextFile = GitFileRevision.inIndex(repository, gitPath);
		final EditableRevision next = new EditableRevision(nextFile, encoding);

		IContentChangeListener listener = new IContentChangeListener() {
			@Override
			public void contentChanged(IContentChangeNotifier source) {
				final byte[] newContent = next.getModifiedContent();
				setIndexEntryContents(repository, gitPath, newContent);
			}
		};

		next.addContentChangeListener(listener);
		return next;
	}

	/**
	 * Set contents on index entry of specified path. Line endings of contents
	 * are canonicalized if configured.
	 *
	 * @param repository
	 * @param gitPath
	 * @param newContent
	 *            content with working directory line endings
	 */
	private static void setIndexEntryContents(final Repository repository,
			final String gitPath, final byte[] newContent) {
		DirCache cache = null;
		try {
			cache = repository.lockDirCache();
			DirCacheEditor editor = cache.editor();
			if (newContent.length == 0) {
				editor.add(new DirCacheEditor.DeletePath(gitPath));
			} else {
				int length;
				byte[] content;
				WorkingTreeOptions workingTreeOptions = repository.getConfig()
						.get(WorkingTreeOptions.KEY);
				AutoCRLF autoCRLF = workingTreeOptions.getAutoCRLF();
				switch (autoCRLF) {
				case FALSE:
					content = newContent;
					length = newContent.length;
					break;
				case INPUT:
				case TRUE:
					EolCanonicalizingInputStream in = new EolCanonicalizingInputStream(
							new ByteArrayInputStream(newContent), true);
					// Canonicalization should lead to same or shorter length
					// (CRLF to LF), so we don't have to expand the byte[].
					content = new byte[newContent.length];
					length = IO.readFully(in, content, 0);
					break;
				default:
					throw new IllegalArgumentException(
							"Unknown autocrlf option " + autoCRLF); //$NON-NLS-1$
				}

				editor.add(new DirCacheEntryEditor(gitPath, repository,
						content, length));
			}
			try {
				editor.commit();
			} catch (RuntimeException e) {
				if (e.getCause() instanceof IOException)
					throw (IOException) e.getCause();
				else
					throw e;
			}

		} catch (IOException e) {
			Activator.handleError(
					UIText.CompareWithIndexAction_errorOnAddToIndex, e, true);
		} finally {
			if (cache != null)
				cache.unlock();
		}
	}

	private static class DirCacheEntryEditor extends DirCacheEditor.PathEdit {

		private final Repository repo;

		private final byte[] content;
		private final int contentLength;

		public DirCacheEntryEditor(String path, Repository repo,
				byte[] content, int contentLength) {
			super(path);
			this.repo = repo;
			this.content = content;
			this.contentLength = contentLength;
		}

		@Override
		public void apply(DirCacheEntry ent) {
			ObjectInserter inserter = repo.newObjectInserter();
			if (ent.getFileMode() != FileMode.REGULAR_FILE)
				ent.setFileMode(FileMode.REGULAR_FILE);

			ent.setLength(contentLength);
			ent.setLastModified(System.currentTimeMillis());
			try {
				ent.setObjectId(inserter.insert(Constants.OBJ_BLOB, content, 0,
						contentLength));
				inserter.flush();
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}
		}
	}

	/**
	 * Indicates if it is OK to open the selected file directly in a compare
	 * editor.
	 * <p>
	 * It is not OK to show the single file if the file is part of a logical
	 * model element that spans multiple files.
	 * </p>
	 *
	 * @param file
	 *            file the user is trying to compare
	 * @param context
	 *            resource mapping context model providers can use to fetch
	 *            remote data for the resource variants.
	 * @return <code>true</code> if the file can be opened directly in a compare
	 *         editor, <code>false</code> if the synchronize view should be
	 *         opened instead.
	 */
	public static boolean canDirectlyOpenInCompare(IFile file,
			ResourceMappingContext context) {
		/*
		 * Using a local context for the ResourceMapping computation would make
		 * for a faster test... but we need the model providers to be able to
		 * load remote information. The local file may very well be a single
		 * file, but it is possible that the remote side has multiple files to
		 * take into account for that model. (if part of the logical model has
		 * been locally deleted, or if some new files have been created on the
		 * remote side(s).)
		 */
		// Only builds the logical model if the preference holds true
		if (Activator.getDefault().getPreferenceStore()
				.getBoolean(UIPreferences.USE_LOGICAL_MODEL)) {
			final ResourceMapping[] mappings = ResourceUtil
					.getResourceMappings(file, context);

			for (ResourceMapping mapping : mappings) {
				try {
					final ResourceTraversal[] traversals = mapping
							.getTraversals(context, null);
					for (ResourceTraversal traversal : traversals) {
						final IResource[] resources = traversal.getResources();
						if (resources.length > 1
								&& Arrays.asList(resources).contains(file)) {
							return false;
						}
					}
				} catch (CoreException e) {
					Activator.logError(e.getMessage(), e);
				}
			}

		}
		return true;
	}

	/**
	 * The model providers need information about the remote sides to properly
	 * detect whether a given file is part of a logical model or not. This will
	 * prepare the RemoteResourceMappingContext corresponding to the given
	 * source branch ("ours" side of the comparison, {@code leftRev} or the work
	 * tree, depending on the state of {@code inclueLocal}) and the given
	 * destination branch ("theirs" side, {@code rightRev}). The common ancestor
	 * ("base" side) for this comparison will be inferred as the first common
	 * ancestor of {@code leftRev} and {@code rightRev}.
	 *
	 * @param repository
	 *            The repository from which we're currently comparing or
	 *            synchronizing files.
	 * @param leftRev
	 *            Left revision of the comparison (usually the local or "new"
	 *            revision). Won't be used if <code>includeLocal</code> is
	 *            <code>true</code>.
	 * @param rightRev
	 *            Right revision of the comparison (usually the "old" revision).
	 * @param includeLocal
	 *            <code>true</code> if we are to consider local data (work tree)
	 *            as being the source of this comparison. <code>false</code> if
	 *            we are to use the data from <code>leftRev</code> for that.
	 * @return a {@link RemoteResourceMappingContext} ready for use by the model
	 *         providers.
	 * @throws IOException
	 */
	private static RemoteResourceMappingContext prepareContext(
			Repository repository, String leftRev, String rightRev,
			boolean includeLocal) throws IOException {
		GitSynchronizeData gsd = new GitSynchronizeData(repository, leftRev,
				rightRev, includeLocal);
		GitSynchronizeDataSet gsds = new GitSynchronizeDataSet(gsd);
		GitResourceVariantTreeSubscriber subscriber = new GitResourceVariantTreeSubscriber(
				gsds);
		subscriber.init(new NullProgressMonitor());

		return new GitSubscriberResourceMappingContext(subscriber, gsds);
	}
}
