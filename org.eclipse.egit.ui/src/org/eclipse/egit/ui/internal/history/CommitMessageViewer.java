/*******************************************************************************
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2011, Mathias Kinzler <mathias.kinzler@sap.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.history;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIPreferences;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.UIUtils;
import org.eclipse.egit.ui.internal.CompareUtils;
import org.eclipse.egit.ui.internal.trace.GitTraceLocation;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.text.DefaultTextDoubleClickStrategy;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextOperationTarget;
import org.eclipse.jface.text.TextViewer;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revplot.PlotCommit;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.part.IPageSite;

class CommitMessageViewer extends TextViewer implements
		ISelectionChangedListener {
	private static final String SPACE = " "; //$NON-NLS-1$

	private static final String LF = "\n"; //$NON-NLS-1$

	private static final Color SYS_LINKCOLOR = PlatformUI.getWorkbench()
			.getDisplay().getSystemColor(SWT.COLOR_BLUE);

	private static final Color SYS_DARKGRAY = PlatformUI.getWorkbench()
			.getDisplay().getSystemColor(SWT.COLOR_DARK_GRAY);

	private static final Color SYS_HUNKHEADER_COLOR = PlatformUI.getWorkbench()
			.getDisplay().getSystemColor(SWT.COLOR_BLUE);

	private static final Color SYS_LINES_ADDED_COLOR = PlatformUI
			.getWorkbench().getDisplay().getSystemColor(SWT.COLOR_DARK_GREEN);

	private static final Color SYS_LINES_REMOVED_COLOR = PlatformUI
			.getWorkbench().getDisplay().getSystemColor(SWT.COLOR_DARK_RED);

	private static final Cursor SYS_LINK_CURSOR = PlatformUI.getWorkbench()
			.getDisplay().getSystemCursor(SWT.CURSOR_HAND);

	private static final DateFormat fmt = new SimpleDateFormat(
			"yyyy-MM-dd HH:mm:ss"); //$NON-NLS-1$

	private final Cursor sys_normalCursor;

	// notified when clicking on a link in the message (branch, commit...)
	private final ListenerList navListeners = new ListenerList();

	// set by selecting files in the file list
	private final List<FileDiff> currentDiffs = new ArrayList<FileDiff>();

	// listener to detect changes in the wrap and fill preferences
	private final IPropertyChangeListener listener;

	// the current repository
	private Repository db;

	// the "input" (set by setInput())
	private PlotCommit<?> commit;

	// formatting option to fill the lines
	private boolean fill;

	CommitMessageViewer(final Composite parent, final IPageSite site) {
		super(parent, SWT.H_SCROLL | SWT.V_SCROLL | SWT.READ_ONLY);

		final StyledText t = getTextWidget();
		t.setFont(UIUtils.getFont(UIPreferences.THEME_CommitMessageFont));

		sys_normalCursor = t.getCursor();

		// set the cursor when hovering over a link
		t.addListener(SWT.MouseMove, new Listener() {
			public void handleEvent(final Event e) {
				final int o;
				try {
					o = t.getOffsetAtLocation(new Point(e.x, e.y));
				} catch (IllegalArgumentException err) {
					t.setCursor(sys_normalCursor);
					return;
				}

				final StyleRange r = t.getStyleRangeAtOffset(o);
				if (r instanceof ObjectLink)
					t.setCursor(SYS_LINK_CURSOR);
				else
					t.setCursor(sys_normalCursor);
			}
		});
		// react on link click
		t.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDown(final MouseEvent e) {
				// only process the hyper link if it was a primary mouse click
				if (e.button != 1) {
					return;
				}

				final int o;
				try {
					o = t.getOffsetAtLocation(new Point(e.x, e.y));
				} catch (IllegalArgumentException err) {
					return;
				}

				final StyleRange r = t.getStyleRangeAtOffset(o);
				if (r instanceof ObjectLink) {
					final RevCommit c = ((ObjectLink) r).targetCommit;
					for (final Object l : navListeners.getListeners())
						((CommitNavigationListener) l).showCommit(c);
				}
			}
		});
		setTextDoubleClickStrategy(new DefaultTextDoubleClickStrategy(),
				IDocument.DEFAULT_CONTENT_TYPE);
		activatePlugins();

		// react on changes in the fill and wrap preferences
		listener = new IPropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent event) {
				if (event.getProperty().equals(
						UIPreferences.RESOURCEHISTORY_SHOW_COMMENT_WRAP)) {
					setWrap(((Boolean) event.getNewValue()).booleanValue());
					return;
				}
				if (event.getProperty().equals(
						UIPreferences.RESOURCEHISTORY_SHOW_COMMENT_FILL)) {
					setFill(((Boolean) event.getNewValue()).booleanValue());
					return;
				}
			}
		};
		Activator.getDefault().getPreferenceStore().addPropertyChangeListener(
				listener);

		// global action handlers for select all and copy
		final IAction selectAll = new Action() {
			@Override
			public void run() {
				doOperation(ITextOperationTarget.SELECT_ALL);
			}

			@Override
			public boolean isEnabled() {
				return canDoOperation(ITextOperationTarget.SELECT_ALL);
			}
		};

		final IAction copy = new Action() {
			@Override
			public void run() {
				doOperation(ITextOperationTarget.COPY);
			}

			@Override
			public boolean isEnabled() {
				return canDoOperation(ITextOperationTarget.COPY);
			}
		};
		// register and unregister the global actions upon focus events
		getControl().addFocusListener(new FocusListener() {
			public void focusLost(FocusEvent e) {
				site.getActionBars().setGlobalActionHandler(
						ActionFactory.SELECT_ALL.getId(), null);
				site.getActionBars().setGlobalActionHandler(
						ActionFactory.COPY.getId(), null);
				site.getActionBars().updateActionBars();
			}

			public void focusGained(FocusEvent e) {
				site.getActionBars().setGlobalActionHandler(
						ActionFactory.SELECT_ALL.getId(), selectAll);
				site.getActionBars().setGlobalActionHandler(
						ActionFactory.COPY.getId(), copy);
				site.getActionBars().updateActionBars();
			}
		});
	}

	@Override
	protected void handleDispose() {
		Activator.getDefault().getPreferenceStore()
				.removePropertyChangeListener(listener);
		super.handleDispose();
	}

	void addCommitNavigationListener(final CommitNavigationListener l) {
		navListeners.add(l);
	}

	void removeCommitNavigationListener(final CommitNavigationListener l) {
		navListeners.remove(l);
	}

	@Override
	public void setInput(final Object input) {
		// right-clicking on a commit will fire selection change events,
		// so we only rebuild this when the commit did in fact change
		if (input == commit)
			return;
		currentDiffs.clear();
		commit = (PlotCommit<?>) input;
		format();
	}

	public Object getInput() {
		return commit;
	}

	void setRepository(final Repository repository) {
		this.db = repository;
	}

	private void format() {
		if (commit == null) {
			setDocument(new Document(
					UIText.CommitMessageViewer_SelectOneCommitMessage));
			return;
		}

		final List<StyleRange> styles = new ArrayList<StyleRange>();
		final StringBuilder d = new StringBuilder();
		// we do the formatting asynchronously
		try {
			PlatformUI.getWorkbench().getProgressService().busyCursorWhile(
					new IRunnableWithProgress() {
						public void run(IProgressMonitor monitor)
								throws InvocationTargetException,
								InterruptedException {
							format(styles, d, monitor);
						}
					});

		} catch (InvocationTargetException e) {
			Activator.handleError(NLS.bind(
					UIText.CommitMessageViewer_errorGettingFileDifference,
					commit.getId()), e.getTargetException(), false);
		} catch (InterruptedException e) {
			// ignore here, the list of differences will simply be
			// incomplete
		}
		// now we're back in the UI thread and update the control
		final StyleRange[] arr = new StyleRange[styles.size()];
		styles.toArray(arr);
		Arrays.sort(arr, new Comparator<StyleRange>() {
			public int compare(StyleRange o1, StyleRange o2) {
				return o1.start - o2.start;
			}
		});
		setDocument(new Document(d.toString()));
		getTextWidget().setStyleRanges(arr);
	}

	private String getTagsString() {
		StringBuilder sb = new StringBuilder();
		Map<String, Ref> tagsMap = db.getTags();
		for (String tagName : tagsMap.keySet()) {
			ObjectId peeledId = tagsMap.get(tagName).getPeeledObjectId();
			if (peeledId != null && peeledId.equals(commit)) {
				if (sb.length() > 0)
					sb.append(", "); //$NON-NLS-1$
				sb.append(tagName);
			}
		}
		return sb.toString();
	}

	private String formatHeadRef(Ref ref) {
		final String name = ref.getName();
		if (name.startsWith(Constants.R_HEADS))
			return name.substring(Constants.R_HEADS.length());
		else if (name.startsWith(Constants.R_REMOTES))
			return name.substring(Constants.R_REMOTES.length());
		return name;
	}

	private String formatTagRef(Ref ref) {
		final String name = ref.getName();
		if (name.startsWith(Constants.R_TAGS))
			return name.substring(Constants.R_TAGS.length());
		return name;
	}

	private void makeGrayText(StringBuilder d, List<StyleRange> styles) {
		int p0 = 0;
		for (int i = 0; i < styles.size(); ++i) {
			StyleRange r = styles.get(i);
			if (p0 < r.start) {
				StyleRange nr = new StyleRange(p0, r.start - p0, SYS_DARKGRAY,
						null);
				styles.add(i, nr);
				p0 = r.start;
			} else {
				if (r.foreground == null)
					r.foreground = SYS_DARKGRAY;
				p0 = r.start + r.length;
			}
		}
		if (d.length() - 1 > p0) {
			StyleRange nr = new StyleRange(p0, d.length() - p0, SYS_DARKGRAY,
					null);
			styles.add(nr);
		}
	}

	private void addLink(final StringBuilder d, String linkLabel,
			final List<StyleRange> styles, final RevCommit to) {
		final ObjectLink sr = new ObjectLink();
		sr.targetCommit = to;
		sr.foreground = SYS_LINKCOLOR;
		sr.underline = true;
		sr.start = d.length();
		d.append(linkLabel);
		sr.length = d.length() - sr.start;
		styles.add(sr);
	}

	private void addLink(final StringBuilder d, final List<StyleRange> styles,
			final RevCommit to) {
		addLink(d, to.getId().name(), styles, to);
	}

	private void buildDiffs(final StringBuilder d,
			final List<StyleRange> styles, IProgressMonitor monitor,
			boolean trace) throws InterruptedException,
			InvocationTargetException {

		// the encoding for the currently processed file
		final String[] currentEncoding = new String[1];

		if (trace)
			GitTraceLocation.getTrace().traceEntry(
					GitTraceLocation.HISTORYVIEW.getLocation());
		try {
			monitor.beginTask(UIText.CommitMessageViewer_BuildDiffListTaskName,
					currentDiffs.size());
			BufferedOutputStream bos = new BufferedOutputStream(
					new ByteArrayOutputStream() {
						@Override
						public synchronized void write(byte[] b, int off,
								int len) {
							super.write(b, off, len);
							if (currentEncoding[0] == null)
								d.append(toString());
							else
								try {
									d.append(toString(currentEncoding[0]));
								} catch (UnsupportedEncodingException e) {
									d.append(toString());
								}
							reset();
						}

					});
			final DiffFormatter diffFmt = new MessageViewerFormatter(bos,
					styles, d);

			for (FileDiff currentDiff : currentDiffs) {
				if (monitor.isCanceled())
					throw new InterruptedException();
				if (currentDiff.getBlobs().length == 2) {
					String path = currentDiff.getPath();
					monitor
							.setTaskName(NLS
									.bind(
											UIText.CommitMessageViewer_BuildDiffTaskName,
											path));
					currentEncoding[0] = CompareUtils.getResourceEncoding(db,
							path);
					d.append(formatPathLine(path)).append(LF);
					currentDiff.outputDiff(d, db, diffFmt, true);
					diffFmt.flush();
				}
				monitor.worked(1);
			}

		} catch (IOException e) {
			throw new InvocationTargetException(e);
		} finally {
			monitor.done();
			if (trace)
				GitTraceLocation.getTrace().traceExit(
						GitTraceLocation.HISTORYVIEW.getLocation());
		}
	}

	private String formatPathLine(String path) {
		int n = 80 - path.length() - 2;
		if (n < 0)
			return path;
		final StringBuilder d = new StringBuilder();
		int i = 0;
		for (; i < n / 2; i++)
			d.append("-"); //$NON-NLS-1$
		d.append(SPACE).append(path).append(SPACE);
		for (; i < n - 1; i++)
			d.append("-"); //$NON-NLS-1$
		return d.toString();
	}

	private static final class MessageViewerFormatter extends DiffFormatter {
		private final List<StyleRange> styles;

		private final StringBuilder d;

		private MessageViewerFormatter(OutputStream out,
				List<StyleRange> styles, StringBuilder d) {
			super(out);
			this.styles = styles;
			this.d = d;
		}

		@Override
		protected void writeHunkHeader(int aCur, int aEnd, int bCur, int bEnd)
				throws IOException {
			flush();
			int start = d.length();
			super.writeHunkHeader(aCur, aEnd, bCur, bEnd);
			flush();
			int end = d.length();
			styles.add(new StyleRange(start, end - start, SYS_HUNKHEADER_COLOR,
					null));
		}

		@Override
		protected void writeAddedLine(RawText b, int bCur) throws IOException {
			flush();
			int start = d.length();
			super.writeAddedLine(b, bCur);
			flush();
			int end = d.length();
			styles.add(new StyleRange(start, end - start,
					SYS_LINES_ADDED_COLOR, null));
		}

		@Override
		protected void writeRemovedLine(RawText b, int bCur) throws IOException {
			flush();
			int start = d.length();
			super.writeRemovedLine(b, bCur);
			flush();
			int end = d.length();
			styles.add(new StyleRange(start, end - start,
					SYS_LINES_REMOVED_COLOR, null));
		}
	}

	private static final class ObjectLink extends StyleRange {
		RevCommit targetCommit;

		public boolean similarTo(final StyleRange style) {
			if (!(style instanceof ObjectLink))
				return false;
			if (targetCommit != ((ObjectLink) style).targetCommit)
				return false;
			return super.similarTo(style);
		}

		@Override
		public boolean equals(Object object) {
			return super.equals(object)
					&& targetCommit.equals(((ObjectLink) object).targetCommit);
		}

		@Override
		public int hashCode() {
			return super.hashCode() ^ targetCommit.hashCode();
		}
	}

	private void setWrap(boolean wrap) {
		getTextWidget().setWordWrap(wrap);
	}

	private void setFill(boolean fill) {
		this.fill = fill;
		format();
	}

	public void selectionChanged(SelectionChangedEvent event) {
		currentDiffs.clear();
		ISelection selection = event.getSelection();
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection sel = (IStructuredSelection) selection;
			for (Object obj : sel.toList())
				if (obj instanceof FileDiff)
					currentDiffs.add((FileDiff) obj);
		}
		format();
	}

	/**
	 * Finds next door tagged revision. Searches forwards (in descendants) or
	 * backwards (in ancestors)
	 *
	 * @param searchDescendant
	 *            if <code>false</code>, will search for tagged revision in
	 *            ancestors
	 * @param monitor
	 * @return {@link Ref} or <code>null</code> if no tag found
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private Ref getNextTag(boolean searchDescendant, IProgressMonitor monitor)
			throws IOException, InterruptedException {
		RevWalk revWalk = new RevWalk(db);

		Map<String, Ref> tagsMap = db.getTags();
		Ref tagRef = null;

		for (String tagName : tagsMap.keySet()) {
			if (monitor.isCanceled())
				throw new InterruptedException();
			// both RevCommits must be allocated using same RevWalk instance,
			// otherwise isMergedInto returns wrong result!
			RevCommit current = revWalk.parseCommit(commit);
			Ref ref = tagsMap.get(tagName);
			// tags can point to any object, we only want tags pointing at
			// commits
			RevObject any = revWalk.peel(revWalk.parseAny(ref.getObjectId()));
			if (!(any instanceof RevCommit))
				continue;
			RevCommit newTag = (RevCommit) any;
			if (newTag.getId().equals(commit))
				continue;

			// check if newTag matches our criteria
			if (isMergedInto(revWalk, newTag, current, searchDescendant)) {
				if (tagRef != null) {
					RevCommit oldTag = revWalk
							.parseCommit(tagRef.getObjectId());

					// both oldTag and newTag satisfy search criteria, so taking
					// the closest one
					if (isMergedInto(revWalk, oldTag, newTag, searchDescendant))
						tagRef = ref;
				} else
					tagRef = ref;
			}
		}
		return tagRef;
	}

	/**
	 * @param rw
	 * @param base
	 * @param tip
	 * @param swap
	 *            if <code>true</code>, base and tip arguments are swapped
	 * @return <code>true</code> if there is a path directly from tip to base
	 *         (and thus base is fully merged into tip); <code>false</code>
	 *         otherwise.
	 * @throws IOException
	 */
	private boolean isMergedInto(final RevWalk rw, final RevCommit base,
			final RevCommit tip, boolean swap) throws IOException {
		return !swap ? rw.isMergedInto(base, tip) : rw.isMergedInto(tip, base);
	}

	/**
	 * @return List of heads from those current commit is reachable
	 */
	private List<Ref> getBranches() {
		RevWalk revWalk = new RevWalk(db);
		List<Ref> result = new ArrayList<Ref>();

		try {
			Map<String, Ref> refsMap = new HashMap<String, Ref>();
			refsMap.putAll(db.getRefDatabase().getRefs(Constants.R_HEADS));
			// add remote heads to search
			refsMap.putAll(db.getRefDatabase().getRefs(Constants.R_REMOTES));

			for (String headName : refsMap.keySet()) {
				RevCommit headCommit = revWalk.parseCommit(refsMap
						.get(headName).getObjectId());
				// the base RevCommit also must be allocated using same RevWalk
				// instance,
				// otherwise isMergedInto returns wrong result!
				RevCommit base = revWalk.parseCommit(commit);

				if (revWalk.isMergedInto(base, headCommit))
					result.add(refsMap.get(headName)); // commit is reachable
				// from this head
			}
		} catch (IOException e) {
			// skip exception
		}
		return result;
	}

	private void format(final List<StyleRange> styles, final StringBuilder d,
			IProgressMonitor monitor) throws InterruptedException,
			InvocationTargetException {
		boolean trace = GitTraceLocation.HISTORYVIEW.isActive();
		if (trace)
			GitTraceLocation.getTrace().traceEntry(
					GitTraceLocation.HISTORYVIEW.getLocation());
		monitor
				.setTaskName(UIText.CommitMessageViewer_FormattingMessageTaskName);
		final PersonIdent author = commit.getAuthorIdent();
		final PersonIdent committer = commit.getCommitterIdent();
		d.append(UIText.CommitMessageViewer_commit);
		d.append(SPACE);
		d.append(commit.getId().name());
		d.append(LF);

		if (author != null) {
			d.append(UIText.CommitMessageViewer_author);
			d.append(": "); //$NON-NLS-1$
			d.append(author.getName());
			d.append(" <"); //$NON-NLS-1$
			d.append(author.getEmailAddress());
			d.append("> "); //$NON-NLS-1$
			d.append(fmt.format(author.getWhen()));
			d.append(LF);
		}

		if (committer != null) {
			d.append(UIText.CommitMessageViewer_committer);
			d.append(": "); //$NON-NLS-1$
			d.append(committer.getName());
			d.append(" <"); //$NON-NLS-1$
			d.append(committer.getEmailAddress());
			d.append("> "); //$NON-NLS-1$
			d.append(fmt.format(committer.getWhen()));
			d.append(LF);
		}

		for (int i = 0; i < commit.getParentCount(); i++) {
			final RevCommit p = commit.getParent(i);
			d.append(UIText.CommitMessageViewer_parent);
			d.append(": "); //$NON-NLS-1$
			addLink(d, styles, p);
			d.append(" ("); //$NON-NLS-1$
			d.append(p.getShortMessage());
			d.append(")"); //$NON-NLS-1$
			d.append(LF);
		}

		for (int i = 0; i < commit.getChildCount(); i++) {
			final RevCommit p = commit.getChild(i);
			d.append(UIText.CommitMessageViewer_child);
			d.append(": "); //$NON-NLS-1$
			addLink(d, styles, p);
			d.append(" ("); //$NON-NLS-1$
			d.append(p.getShortMessage());
			d.append(")"); //$NON-NLS-1$
			d.append(LF);
		}

		List<Ref> branches = getBranches();
		if (!branches.isEmpty()) {
			d.append(UIText.CommitMessageViewer_branches);
			d.append(": "); //$NON-NLS-1$
			for (Iterator<Ref> i = branches.iterator(); i.hasNext();) {
				Ref head = i.next();
				RevCommit p;
				try {
					p = new RevWalk(db).parseCommit(head.getObjectId());
					addLink(d, formatHeadRef(head), styles, p);
					if (i.hasNext())
						d.append(", "); //$NON-NLS-1$
				} catch (MissingObjectException e) {
					Activator.logError(e.getMessage(), e);
				} catch (IncorrectObjectTypeException e) {
					Activator.logError(e.getMessage(), e);
				} catch (IOException e) {
					Activator.logError(e.getMessage(), e);
				}
			}
			d.append(LF);
		}

		String tagsString = getTagsString();
		if (tagsString.length() > 0) {
			d.append(UIText.CommitMessageViewer_tags);
			d.append(": "); //$NON-NLS-1$
			d.append(tagsString);
			d.append(LF);
		}

		if (Activator.getDefault().getPreferenceStore().getBoolean(
				UIPreferences.HISTORY_SHOW_TAG_SEQUENCE)) {
			try {
				monitor
						.setTaskName(UIText.CommitMessageViewer_GettingPreviousTagTaskName);
				Ref followingTag = getNextTag(false, monitor);
				if (followingTag != null) {
					d.append(UIText.CommitMessageViewer_follows);
					d.append(": "); //$NON-NLS-1$
					RevCommit p = new RevWalk(db).parseCommit(followingTag
							.getObjectId());
					addLink(d, formatTagRef(followingTag), styles, p);
					d.append(LF);
				}
			} catch (IOException e) {
				Activator.logError(e.getMessage(), e);
			}

			try {
				monitor
						.setTaskName(UIText.CommitMessageViewer_GettingNextTagTaskName);
				Ref precedingTag = getNextTag(true, monitor);
				if (precedingTag != null) {
					d.append(UIText.CommitMessageViewer_precedes);
					d.append(": "); //$NON-NLS-1$
					RevCommit p = new RevWalk(db).parseCommit(precedingTag
							.getObjectId());
					addLink(d, formatTagRef(precedingTag), styles, p);
					d.append(LF);
				}
			} catch (IOException e) {
				Activator.logError(e.getMessage(), e);
			}
		}

		makeGrayText(d, styles);
		d.append(LF);
		String msg = commit.getFullMessage();
		Pattern p = Pattern.compile("\n([A-Z](?:[A-Za-z]+-)+by: [^\n]+)"); //$NON-NLS-1$
		if (fill) {
			Matcher spm = p.matcher(msg);
			if (spm.find()) {
				String subMsg = msg.substring(0, spm.end());
				msg = subMsg.replaceAll("([\\w.,; \t])\n(\\w)", "$1 $2") //$NON-NLS-1$ //$NON-NLS-2$
						+ msg.substring(spm.end());
			}
		}
		int h0 = d.length();
		d.append(msg);
		d.append(LF);

		Matcher matcher = p.matcher(msg);
		while (matcher.find()) {
			styles.add(new StyleRange(h0 + matcher.start(), matcher.end()
					- matcher.start(), null, null, SWT.ITALIC));
		}

		// build the list of file diffs asynchronously to ensure UI
		// responsiveness
		if (!currentDiffs.isEmpty() && commit.getParentCount() == 1)
			buildDiffs(d, styles, monitor, trace);

		if (trace)
			GitTraceLocation.getTrace().traceExit(
					GitTraceLocation.HISTORYVIEW.getLocation());
	}
}
