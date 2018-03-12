/*******************************************************************************
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.history;

import java.io.IOException;
import java.util.Iterator;

import org.eclipse.compare.CompareUI;
import org.eclipse.compare.ITypedElement;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIPreferences;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.UIUtils;
import org.eclipse.egit.ui.internal.CompareUtils;
import org.eclipse.egit.ui.internal.EgitUiEditorUtils;
import org.eclipse.egit.ui.internal.GitCompareFileRevisionEditorInput;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableLayout;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.team.core.history.IFileRevision;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

class CommitFileDiffViewer extends TableViewer {

	private static final String LINESEP = System.getProperty("line.separator"); //$NON-NLS-1$

	private Repository db;

	private TreeWalk walker;

	private Clipboard clipboard;

	private StyledText noInputText;

	private final StackLayout stackLayout;

	/**
	 * Shows a list of file changed by a commit.
	 *
	 * If no input is available, an error message is shown instead.
	 *
	 * @param parent
	 */
	CommitFileDiffViewer(final Composite parent) {
		// since out parent is a SashForm, we can't add the alternate
		// text to be displayed in case of no input directly to that
		// parent; we create our own parent instead and set the
		// StackLayout on it instead
		super(new Composite(parent, SWT.NONE), SWT.MULTI | SWT.H_SCROLL
				| SWT.V_SCROLL | SWT.BORDER | SWT.FULL_SELECTION);
		final Table rawTable = getTable();
		Composite main = rawTable.getParent();
		stackLayout = new StackLayout();
		main.setLayout(stackLayout);

		// this is the text to be displayed if there is no input
		noInputText = new StyledText(main, SWT.NONE);
		// use the same font as in message viewer
		noInputText.setFont(UIUtils
				.getFont(UIPreferences.THEME_CommitMessageFont));
		noInputText.setText(UIText.CommitFileDiffViewer_SelectOneCommitMessage);

		rawTable.setHeaderVisible(true);
		rawTable.setLinesVisible(true);

		final TableLayout layout = new TableLayout();
		rawTable.setLayout(layout);
		createColumns(rawTable, layout);

		setLabelProvider(new FileDiffLabelProvider());
		setContentProvider(new FileDiffContentProvider());
		addOpenListener(new IOpenListener() {
			public void open(final OpenEvent event) {
				final ISelection s = event.getSelection();
				if (s.isEmpty() || !(s instanceof IStructuredSelection))
					return;
				final IStructuredSelection iss = (IStructuredSelection) s;
				final FileDiff d = (FileDiff) iss.getFirstElement();
				if (Activator.getDefault().getPreferenceStore().getBoolean(
						UIPreferences.RESOURCEHISTORY_COMPARE_MODE)) {
					if (d.getBlobs().length <= 2)
						showTwoWayFileDiff(d);
					else
						MessageDialog
								.openInformation(
										PlatformUI.getWorkbench()
												.getActiveWorkbenchWindow()
												.getShell(),
										UIText.CommitFileDiffViewer_CanNotOpenCompareEditorTitle,
										UIText.CommitFileDiffViewer_MergeCommitMultiAncestorMessage);
				} else
					openFileInEditor(d);
			}
		});

		clipboard = new Clipboard(rawTable.getDisplay());
		rawTable.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(final DisposeEvent e) {
				clipboard.dispose();
			}
		});
	}

	@Override
	protected void inputChanged(final Object input, final Object oldInput) {
		boolean inputChanged;
		if (oldInput == null && input == null) {
			inputChanged = false;
		} else if (oldInput == null || input == null) {
			inputChanged = true;
		} else {
			inputChanged = !input.equals(oldInput);
		}
		if (inputChanged) {
			if (input == null && stackLayout.topControl != noInputText) {
				stackLayout.topControl = noInputText;
				getTable().getParent().layout(false);
			} else if (input != null && stackLayout.topControl != getTable()) {
				stackLayout.topControl = getTable();
				getTable().getParent().layout(false);
			}
			super.inputChanged(input, oldInput);
		}
	}

	private void openFileInEditor(FileDiff d) {
		try {
			IWorkbenchWindow window = PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow();
			IWorkbenchPage page = window.getActivePage();
			IFileRevision rev = CompareUtils.getFileRevision(d.getPath(), d
					.getChange().equals(ChangeType.DELETE) ? d.getCommit()
					.getParent(0) : d.getCommit(), db, d.getChange().equals(
					ChangeType.DELETE) ? d.getBlobs()[0] : d.getBlobs()[d
					.getBlobs().length - 1]);
			if (rev != null)
				EgitUiEditorUtils.openEditor(page, rev,
						new NullProgressMonitor());
			else {
				String message = NLS.bind(
						UIText.CommitFileDiffViewer_notContainedInCommit, d
								.getPath(), d.getCommit().getId().getName());
				Activator.showError(message, null);
			}
		} catch (IOException e) {
			Activator.logError(UIText.GitHistoryPage_openFailed, e);
			Activator.showError(UIText.GitHistoryPage_openFailed, null);
		} catch (CoreException e) {
			Activator.logError(UIText.GitHistoryPage_openFailed, e);
			Activator.showError(UIText.GitHistoryPage_openFailed, null);
		}
	}

	void showTwoWayFileDiff(final FileDiff d) {
		final GitCompareFileRevisionEditorInput in;

		final String p = d.getPath();
		final RevCommit c = d.getCommit();
		final ITypedElement base;
		final ITypedElement next;

		if (d.getBlobs().length == 2 && !d.getChange().equals(ChangeType.ADD))
			base = CompareUtils.getFileRevisionTypedElement(p, c.getParent(0),
					db, d.getBlobs()[0]);
		else
			// Initial import
			base = new GitCompareFileRevisionEditorInput.EmptyTypedElement(""); //$NON-NLS-1$

		if (d.getChange().equals(ChangeType.DELETE))
			next = new GitCompareFileRevisionEditorInput.EmptyTypedElement(""); //$NON-NLS-1$
		else
			next = CompareUtils.getFileRevisionTypedElement(p, c, db, d
					.getBlobs()[1]);

		in = new GitCompareFileRevisionEditorInput(next, base, null);
		CompareUI.openCompareEditor(in);
	}

	TreeWalk getTreeWalk() {
		return walker;
	}

	void setTreeWalk(Repository repository, TreeWalk walk) {
		db = repository;
		walker = walk;
	}

	void doSelectAll() {
		final IStructuredContentProvider cp;
		final Object in = getInput();
		if (in == null)
			return;

		cp = ((IStructuredContentProvider) getContentProvider());
		final Object[] el = cp.getElements(in);
		if (el == null || el.length == 0)
			return;
		setSelection(new StructuredSelection(el));
	}

	@SuppressWarnings("unchecked")
	void doCopy() {
		final ISelection s = getSelection();
		if (s.isEmpty() || !(s instanceof IStructuredSelection))
			return;
		final IStructuredSelection iss = (IStructuredSelection) s;
		final Iterator<FileDiff> itr = iss.iterator();
		final StringBuilder r = new StringBuilder();
		while (itr.hasNext()) {
			final FileDiff d = itr.next();
			if (r.length() > 0)
				r.append(LINESEP);
			r.append(d.getPath());
		}

		clipboard.setContents(new Object[] { r.toString() },
				new Transfer[] { TextTransfer.getInstance() }, DND.CLIPBOARD);
	}

	private void createColumns(final Table rawTable, final TableLayout layout) {
		final TableColumn mode = new TableColumn(rawTable, SWT.NONE);
		mode.setResizable(true);
		mode.setText(""); //$NON-NLS-1$
		mode.setWidth(5);
		layout.addColumnData(new ColumnWeightData(1, true));

		final TableColumn path = new TableColumn(rawTable, SWT.NONE);
		path.setResizable(true);
		path.setText(UIText.HistoryPage_pathnameColumn);
		path.setWidth(250);
		layout.addColumnData(new ColumnWeightData(20, true));
	}
}
