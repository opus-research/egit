/*******************************************************************************
 *  Copyright (c) 2011, 2016 GitHub Inc. and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *    Kevin Sawicki (GitHub Inc.) - initial API and implementation
 *    Tobias Pfeifer (SAP AG) - customizable font and color for the first header line - https://bugs.eclipse.org/397723
 *    Thomas Wolf <thomas.wolf@paranor.ch> - add hyperlinks, and use JFace syntax coloring
 *******************************************************************************/
package org.eclipse.egit.ui.internal.commit;

import static org.eclipse.egit.ui.UIPreferences.THEME_DiffAddBackgroundColor;
import static org.eclipse.egit.ui.UIPreferences.THEME_DiffAddForegroundColor;
import static org.eclipse.egit.ui.UIPreferences.THEME_DiffHeadlineBackgroundColor;
import static org.eclipse.egit.ui.UIPreferences.THEME_DiffHeadlineFont;
import static org.eclipse.egit.ui.UIPreferences.THEME_DiffHeadlineForegroundColor;
import static org.eclipse.egit.ui.UIPreferences.THEME_DiffHunkBackgroundColor;
import static org.eclipse.egit.ui.UIPreferences.THEME_DiffHunkForegroundColor;
import static org.eclipse.egit.ui.UIPreferences.THEME_DiffRemoveBackgroundColor;
import static org.eclipse.egit.ui.UIPreferences.THEME_DiffRemoveForegroundColor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.compare.ITypedElement;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.egit.core.internal.util.ResourceUtil;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.internal.CompareUtils;
import org.eclipse.egit.ui.internal.EgitUiEditorUtils;
import org.eclipse.egit.ui.internal.UIText;
import org.eclipse.egit.ui.internal.commit.DiffStyleRangeFormatter.DiffStyleRange;
import org.eclipse.egit.ui.internal.commit.DiffStyleRangeFormatter.FileDiffRange;
import org.eclipse.egit.ui.internal.dialogs.HyperlinkSourceViewer;
import org.eclipse.egit.ui.internal.history.FileDiff;
import org.eclipse.egit.ui.internal.revision.GitCompareFileRevisionEditorInput;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceConverter;
import org.eclipse.jface.resource.ColorDescriptor;
import org.eclipse.jface.resource.ColorRegistry;
import org.eclipse.jface.resource.DeviceResourceManager;
import org.eclipse.jface.resource.FontRegistry;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.TextUtilities;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetectorExtension2;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.rules.ITokenScanner;
import org.eclipse.jface.text.rules.Token;
import org.eclipse.jface.text.source.CompositeRuler;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.text.source.IVerticalRuler;
import org.eclipse.jface.text.source.LineNumberRulerColumn;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.team.core.history.IFileRevision;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.texteditor.AbstractDecoratedTextEditorPreferenceConstants;
import org.eclipse.ui.texteditor.AbstractTextEditor;
import org.eclipse.ui.texteditor.SourceViewerDecorationSupport;
import org.eclipse.ui.themes.IThemeManager;

/**
 * Source viewer to display one or more file differences using standard editor
 * colors and fonts preferences. Should be used together with a
 * {@link DiffDocument} to get proper coloring and hyperlink support.
 */
public class DiffViewer extends HyperlinkSourceViewer {

	private final DeviceResourceManager colors = new DeviceResourceManager(
			PlatformUI.getWorkbench().getDisplay());

	private final Map<String, IToken> tokens = new HashMap<>();

	private final Map<String, Color> backgroundColors = new HashMap<>();

	private LineNumberRulerColumn lineNumberRuler;

	private IPropertyChangeListener themeListener = new IPropertyChangeListener() {

		@Override
		public void propertyChange(PropertyChangeEvent event) {
			String property = event.getProperty();
			if (IThemeManager.CHANGE_CURRENT_THEME.equals(property)
					|| THEME_DiffAddBackgroundColor.equals(property)
					|| THEME_DiffAddForegroundColor.equals(property)
					|| THEME_DiffHunkBackgroundColor.equals(property)
					|| THEME_DiffHunkForegroundColor.equals(property)
					|| THEME_DiffHeadlineBackgroundColor.equals(property)
					|| THEME_DiffHeadlineForegroundColor.equals(property)
					|| THEME_DiffHeadlineFont.equals(property)
					|| THEME_DiffRemoveBackgroundColor.equals(property)
					|| THEME_DiffRemoveForegroundColor.equals(property)) {
				refreshDiffStyles();
				refresh();
			}
		}
	};

	private IPropertyChangeListener editorPrefListener = new IPropertyChangeListener() {

		@Override
		public void propertyChange(PropertyChangeEvent event) {
			styleViewer();
		}
	};

	/**
	 * Creates a new {@link DiffViewer} and
	 * {@link #configure(org.eclipse.jface.text.source.SourceViewerConfiguration)
	 * configures} it with a {@link PresentationReconciler} and syntax coloring,
	 * and an {@link IHyperlinkDetector} to provide hyperlinks to open the files
	 * being diff'ed if the document used with the viewer is a
	 * {@link DiffDocument}.
	 *
	 * @param parent
	 *            to contain the viewer
	 * @param ruler
	 *            for the viewer (left side)
	 * @param styles
	 *            for the viewer
	 * @param showCursorLine
	 *            if {@code true},the current line is highlighted
	 */
	public DiffViewer(Composite parent, IVerticalRuler ruler, int styles,
			boolean showCursorLine) {
		super(parent, ruler, styles);
		setDocument(new Document());
		SourceViewerDecorationSupport support = new SourceViewerDecorationSupport(
				this, null, null, EditorsUI.getSharedTextColors());
		if (showCursorLine) {
			support.setCursorLinePainterPreferenceKeys(
					AbstractDecoratedTextEditorPreferenceConstants.EDITOR_CURRENT_LINE,
					AbstractDecoratedTextEditorPreferenceConstants.EDITOR_CURRENT_LINE_COLOR);
		}
		support.install(EditorsUI.getPreferenceStore());
		if (ruler instanceof CompositeRuler) {
			lineNumberRuler = new LineNumberRulerColumn();
			((CompositeRuler) ruler).addDecorator(0, lineNumberRuler);
		}
		getTextWidget().setAlwaysShowScrollBars(false);
		initListeners();
		getControl().addDisposeListener(new DisposeListener() {

			@Override
			public void widgetDisposed(DisposeEvent e) {
				EditorsUI.getPreferenceStore().removePropertyChangeListener(
						editorPrefListener);
				PlatformUI.getWorkbench().getThemeManager()
						.removePropertyChangeListener(themeListener);
				colors.dispose();
			}
		});
		refreshDiffStyles();
		styleViewer();
		configure(new HyperlinkSourceViewer.Configuration(
				EditorsUI.getPreferenceStore()) {

			@Override
			public int getHyperlinkStateMask(ISourceViewer sourceViewer) {
				return SWT.NONE;
			}

			@Override
			protected IHyperlinkDetector[] internalGetHyperlinkDetectors(
					ISourceViewer sourceViewer) {
				IHyperlinkDetector[] result = { new HyperlinkDetector() };
				return result;
			}

			@Override
			public String[] getConfiguredContentTypes(
					ISourceViewer sourceViewer) {
				return tokens.keySet().toArray(new String[tokens.size()]);
			}

			@Override
			public IPresentationReconciler getPresentationReconciler(
					ISourceViewer viewer) {
				PresentationReconciler reconciler = new PresentationReconciler();
				reconciler.setDocumentPartitioning(
						getConfiguredDocumentPartitioning(viewer));
				for (String contentType : tokens.keySet()) {
					DefaultDamagerRepairer damagerRepairer = new DefaultDamagerRepairer(
							new SingleTokenScanner(contentType));
					reconciler.setDamager(damagerRepairer, contentType);
					reconciler.setRepairer(damagerRepairer, contentType);
				}
				return reconciler;
			}
		});
	}

	private void refreshDiffStyles() {
		ColorRegistry col = PlatformUI.getWorkbench().getThemeManager()
				.getCurrentTheme().getColorRegistry();
		FontRegistry reg = PlatformUI.getWorkbench().getThemeManager()
				.getCurrentTheme().getFontRegistry();
		// We do the foreground via syntax coloring and the background via a
		// line background listener. If we did the background also via the
		// TextAttributes, this would take precedence over the line background
		// resulting in strange display if the current line is highlighted:
		// that highlighting would appear only beyond the end of the actual
		// text content (i.e., beyond the end-of-line), while actual text
		// would still get the background from the attribute.
		tokens.put(IDocument.DEFAULT_CONTENT_TYPE, new Token(null));
		tokens.put(DiffDocument.HEADLINE_CONTENT_TYPE,
				new Token(new TextAttribute(
						col.get(THEME_DiffHeadlineForegroundColor), null,
						SWT.NORMAL, reg.get(THEME_DiffHeadlineFont))));
		tokens.put(DiffDocument.HUNK_CONTENT_TYPE, new Token(
				new TextAttribute(col.get(THEME_DiffHunkForegroundColor))));
		tokens.put(DiffDocument.ADDED_CONTENT_TYPE, new Token(
				new TextAttribute(col.get(THEME_DiffAddForegroundColor))));
		tokens.put(DiffDocument.REMOVED_CONTENT_TYPE, new Token(
				new TextAttribute(col.get(THEME_DiffRemoveForegroundColor))));
		backgroundColors.put(DiffDocument.HEADLINE_CONTENT_TYPE,
				col.get(THEME_DiffHeadlineBackgroundColor));
		backgroundColors.put(DiffDocument.HUNK_CONTENT_TYPE,
				col.get(THEME_DiffHunkBackgroundColor));
		backgroundColors.put(DiffDocument.ADDED_CONTENT_TYPE,
				col.get(THEME_DiffAddBackgroundColor));
		backgroundColors.put(DiffDocument.REMOVED_CONTENT_TYPE,
				col.get(THEME_DiffRemoveBackgroundColor));
	}

	private void initListeners() {
		PlatformUI.getWorkbench().getThemeManager()
				.addPropertyChangeListener(this.themeListener);
		EditorsUI.getPreferenceStore().addPropertyChangeListener(
				this.editorPrefListener);
		getTextWidget().addLineBackgroundListener((event) -> {
			IDocument document = getDocument();
			if (document instanceof DiffDocument) {
				try {
					ITypedRegion partition = ((DiffDocument) document)
							.getPartition(event.lineOffset);
					if (partition != null) {
						Color color = backgroundColors.get(partition.getType());
						if (color != null) {
							event.lineBackground = color;
						}
					}
				} catch (BadLocationException e) {
					// Ignore
				}
			}
		});
	}

	private ColorDescriptor createEditorColorDescriptor(String key) {
		return ColorDescriptor.createFrom(PreferenceConverter.getColor(
				EditorsUI.getPreferenceStore(), key));
	}

	private Color getEditorColor(String key) {
		return (Color) colors.get(createEditorColorDescriptor(key));
	}

	private void styleViewer() {
		IPreferenceStore store = EditorsUI.getPreferenceStore();
		Color foreground = null;
		if (!store
				.getBoolean(AbstractTextEditor.PREFERENCE_COLOR_FOREGROUND_SYSTEM_DEFAULT))
			foreground = getEditorColor(AbstractTextEditor.PREFERENCE_COLOR_FOREGROUND);

		Color background = null;
		if (!store
				.getBoolean(AbstractTextEditor.PREFERENCE_COLOR_BACKGROUND_SYSTEM_DEFAULT))
			background = getEditorColor(AbstractTextEditor.PREFERENCE_COLOR_BACKGROUND);

		Color selectionForeground = null;
		if (!store
				.getBoolean(AbstractTextEditor.PREFERENCE_COLOR_SELECTION_FOREGROUND_SYSTEM_DEFAULT))
			selectionForeground = getEditorColor(AbstractTextEditor.PREFERENCE_COLOR_SELECTION_FOREGROUND);

		Color selectionBackground = null;
		if (!store
				.getBoolean(AbstractTextEditor.PREFERENCE_COLOR_SELECTION_BACKGROUND_SYSTEM_DEFAULT))
			selectionBackground = getEditorColor(AbstractTextEditor.PREFERENCE_COLOR_SELECTION_BACKGROUND);

		StyledText text = getTextWidget();
		text.setForeground(foreground);
		text.setBackground(background);
		text.setSelectionForeground(selectionForeground);
		text.setSelectionBackground(selectionBackground);
		text.setFont(JFaceResources.getFont(JFaceResources.TEXT_FONT));
		if (lineNumberRuler != null) {
			lineNumberRuler.setFont(text.getFont());
			lineNumberRuler.setForeground(foreground);
			lineNumberRuler.setBackground(background);
		}
	}

	private class SingleTokenScanner implements ITokenScanner {

		private final String contentType;

		private int currentOffset;

		private int end;

		private int tokenStart;

		public SingleTokenScanner(String contentType) {
			this.contentType = contentType;
		}

		@Override
		public void setRange(IDocument document, int offset, int length) {
			currentOffset = offset;
			end = offset + length;
			tokenStart = -1;
		}

		@Override
		public IToken nextToken() {
			tokenStart = currentOffset;
			if (currentOffset < end) {
				currentOffset = end;
				return tokens.get(contentType);
			}
			return Token.EOF;
		}

		@Override
		public int getTokenOffset() {
			return tokenStart;
		}

		@Override
		public int getTokenLength() {
			return currentOffset - tokenStart;
		}

	}

	private class HyperlinkDetector extends AbstractHyperlinkDetector
			implements IHyperlinkDetectorExtension2 {

		private final Pattern HUNK_LINE_PATTERN = Pattern
				.compile("@@ ([-+]?(\\d+),\\d+) ([-+]?(\\d+),\\d+) @@"); //$NON-NLS-1$

		@Override
		public IHyperlink[] detectHyperlinks(ITextViewer textViewer,
				IRegion region, boolean canShowMultipleHyperlinks) {
			IDocument document = textViewer.getDocument();
			if (textViewer != DiffViewer.this
					|| !(document instanceof DiffDocument)
					|| document.getLength() == 0) {
				return null;
			}
			DiffStyleRange[] ranges = ((DiffDocument) document).getRanges();
			FileDiffRange[] fileRanges = ((DiffDocument) document)
					.getFileRanges();
			if (ranges == null || ranges.length == 0 || fileRanges == null
					|| fileRanges.length == 0) {
				return null;
			}
			int start = region.getOffset();
			int end = region.getOffset() + region.getLength();
			DiffStyleRange key = new DiffStyleRange();
			key.start = start;
			key.length = region.getLength();
			int i = Arrays.binarySearch(ranges, key, (a, b) -> {
				if (a.start > b.start + b.length) {
					return 1;
				}
				if (a.start + a.length < b.start) {
					return -1;
				}
				return 0;
			});
			List<IHyperlink> links = new ArrayList<>();
			FileDiffRange fileRange = null;
			for (; i >= 0 && i < ranges.length; i++) {
				DiffStyleRange range = ranges[i];
				if (range.start >= end) {
					break;
				}
				if (range.start + range.length <= start) {
					continue;
				}
				// Range overlaps region
				switch (range.diffType) {
				case HEADLINE:
					fileRange = findFileRange(fileRanges, fileRange,
							range.start);
					if (fileRange != null) {
						DiffEntry.ChangeType change = fileRange.getDiff()
								.getChange();
						switch (change) {
						case ADD:
						case DELETE:
							break;
						default:
							if (getString(document, range.start, range.length)
									.startsWith("diff")) { //$NON-NLS-1$
								// "diff" is at the beginning
								IRegion linkRegion = new Region(range.start, 4);
								if (TextUtilities.overlaps(region,
										linkRegion)) {
									links.add(new CompareLink(linkRegion,
											fileRange, -1));
								}
							}
							break;
						}
					}
					break;
				case HEADER:
					fileRange = findFileRange(fileRanges, fileRange,
							range.start);
					if (fileRange != null) {
						String line = getString(document, range.start,
								range.length);
						createHeaderLinks((DiffDocument) document, region,
								fileRange, range, line, DiffEntry.Side.OLD,
								links);
						createHeaderLinks((DiffDocument) document, region,
								fileRange, range, line, DiffEntry.Side.NEW,
								links);
					}
					break;
				case HUNK:
					fileRange = findFileRange(fileRanges, fileRange,
							range.start);
					if (fileRange != null) {
						String line = getString(document, range.start,
								range.length);
						Matcher m = HUNK_LINE_PATTERN.matcher(line);
						if (m.find()) {
							int lineOffset = getContextLines(document, range,
									i + 1 < ranges.length ? ranges[i + 1]
											: null);
							createHunkLinks(region, fileRange, range, m,
									lineOffset, links);
						}
					}
					break;
				default:
					break;
				}
			}
			if (links.isEmpty()) {
				return null;
			}
			return links.toArray(new IHyperlink[links.size()]);
		}

		private String getString(IDocument document, int offset, int length) {
			try {
				return document.get(offset, length);
			} catch (BadLocationException e) {
				return ""; //$NON-NLS-1$
			}
		}

		private int getContextLines(IDocument document, DiffStyleRange hunk,
				DiffStyleRange next) {
			if (next != null) {
				switch (next.diffType) {
				case ADD:
				case REMOVE:
					try {
						int diffLine = document.getLineOfOffset(next.start);
						int hunkLine = document.getLineOfOffset(hunk.start);
						return diffLine - hunkLine - 1;
					} catch (BadLocationException e) {
						// Ignore
					}
					break;
				default:
					break;
				}
			}
			return 0;
		}

		private FileDiffRange findFileRange(FileDiffRange[] ranges,
				FileDiffRange candidate, int offset) {
			if (candidate != null && candidate.getStartOffset() <= offset
					&& candidate.getEndOffset() > offset) {
				return candidate;
			}
			FileDiffRange key = new FileDiffRange(null, null, offset, offset);
			int i = Arrays.binarySearch(ranges, key, (a, b) -> {
				if (a.getStartOffset() > b.getEndOffset()) {
					return 1;
				}
				if (b.getStartOffset() > a.getEndOffset()) {
					return -1;
				}
				return 0;
			});
			return i >= 0 ? ranges[i] : null;
		}

		private void createHeaderLinks(DiffDocument document, IRegion region,
				FileDiffRange fileRange, DiffStyleRange range, String line,
				DiffEntry.Side side, List<IHyperlink> links) {
			Pattern p = document.getPathPattern(side);
			if (p == null) {
				return;
			}
			DiffEntry.ChangeType change = fileRange.getDiff().getChange();
			switch (side) {
			case OLD:
				if (change == DiffEntry.ChangeType.ADD) {
					return;
				}
				break;
			default:
				if (change == DiffEntry.ChangeType.DELETE) {
					return;
				}
				break;

			}
			Matcher m = p.matcher(line);
			if (m.find()) {
				IRegion linkRegion = new Region(range.start + m.start(),
						m.end() - m.start());
				if (TextUtilities.overlaps(region, linkRegion)) {
					if (side == DiffEntry.Side.NEW) {
						File file = new Path(fileRange.getRepository()
								.getWorkTree().getAbsolutePath()).append(
										fileRange.getDiff().getNewPath())
										.toFile();
						if (file.exists()) {
							links.add(new FileLink(linkRegion, file, -1));
						}
					}
					links.add(new OpenLink(linkRegion, fileRange, side, -1));
				}
			}
		}

		private void createHunkLinks(IRegion region, FileDiffRange fileRange,
				DiffStyleRange range, Matcher m, int lineOffset,
				List<IHyperlink> links) {
			DiffEntry.ChangeType change = fileRange.getDiff().getChange();
			if (change != DiffEntry.ChangeType.ADD) {
				IRegion linkRegion = new Region(range.start + m.start(1),
						m.end(1) - m.start(1));
				if (TextUtilities.overlaps(linkRegion, region)) {
					int lineNo = Integer.parseInt(m.group(2)) - 1 + lineOffset;
					if (change != DiffEntry.ChangeType.DELETE) {
						links.add(
								new CompareLink(linkRegion, fileRange, lineNo));
					}
					links.add(new OpenLink(linkRegion, fileRange,
							DiffEntry.Side.OLD, lineNo));
				}
			}
			if (change != DiffEntry.ChangeType.DELETE) {
				IRegion linkRegion = new Region(range.start + m.start(3),
						m.end(3) - m.start(3));
				if (TextUtilities.overlaps(linkRegion, region)) {
					int lineNo = Integer.parseInt(m.group(4)) - 1 + lineOffset;
					if (change != DiffEntry.ChangeType.ADD) {
						links.add(
								new CompareLink(linkRegion, fileRange, lineNo));
					}
					File file = new Path(fileRange.getRepository().getWorkTree()
							.getAbsolutePath())
									.append(fileRange.getDiff().getNewPath())
									.toFile();
					if (file.exists()) {
						links.add(new FileLink(linkRegion, file, lineNo));
					}
					links.add(new OpenLink(linkRegion, fileRange,
							DiffEntry.Side.NEW, lineNo));
				}
			}
		}

		@Override
		public int getStateMask() {
			return -1;
		}
	}

	private static abstract class RevealLink implements IHyperlink {

		private final IRegion region;

		protected final int lineNo;

		protected RevealLink(IRegion region, int lineNo) {
			this.region = region;
			this.lineNo = lineNo;
		}

		@Override
		public IRegion getHyperlinkRegion() {
			return region;
		}

		@Override
		public String getTypeLabel() {
			return null;
		}

	}

	private static class FileLink extends RevealLink {

		private final File file;

		public FileLink(IRegion region, File file, int lineNo) {
			super(region, lineNo);
			this.file = file;
		}

		@Override
		public String getHyperlinkText() {
			return UIText.DiffViewer_OpenWorkingTreeLinkLabel;
		}

		@Override
		public void open() {
			openFileInEditor(file, lineNo);
		}

	}

	private static class CompareLink extends RevealLink {

		protected final Repository repository;

		protected final FileDiff fileDiff;

		public CompareLink(IRegion region, FileDiffRange fileRange,
				int lineNo) {
			super(region, lineNo);
			this.repository = fileRange.getRepository();
			this.fileDiff = fileRange.getDiff();
		}

		@Override
		public String getHyperlinkText() {
			return UIText.DiffViewer_OpenComparisonLinkLabel;
		}

		@Override
		public void open() {
			// No way to selectAndReveal a line or a diff node in a
			// CompareEditor?
			showTwoWayFileDiff(repository, fileDiff);
		}

	}

	private static class OpenLink extends CompareLink {

		private final DiffEntry.Side side;

		public OpenLink(IRegion region, FileDiffRange fileRange,
				DiffEntry.Side side, int lineNo) {
			super(region, fileRange, lineNo);
			this.side = side;
		}

		@Override
		public String getHyperlinkText() {
			switch (side) {
			case OLD:
				return UIText.DiffViewer_OpenPreviousLinkLabel;
			default:
				return UIText.DiffViewer_OpenInEditorLinkLabel;
			}
		}

		@Override
		public void open() {
			openInEditor(repository, fileDiff, side, lineNo);
		}

	}

	/**
	 * Opens the file, if it exists, in an editor.
	 *
	 * @param file
	 *            to open
	 * @param lineNoToReveal
	 *            if >= 0, select and reveals the given line
	 */
	public static void openFileInEditor(File file, int lineNoToReveal) {
		if (!file.exists()) {
			Activator.showError(
					NLS.bind(UIText.DiffViewer_FileDoesNotExist,
							file.getPath()),
					null);
			return;
		}
		IWorkbenchPage page = PlatformUI.getWorkbench()
				.getActiveWorkbenchWindow().getActivePage();
		IEditorPart editor = EgitUiEditorUtils.openEditor(file, page);
		EgitUiEditorUtils.revealLine(editor, lineNoToReveal);
	}

	/**
	 * Opens either the new or the old version of a {@link FileDiff} in an
	 * editor.
	 *
	 * @param repository
	 *            the {@link FileDiff} belongs to
	 * @param d
	 *            the {@link FileDiff}
	 * @param side
	 *            to show
	 * @param lineNoToReveal
	 *            if >= 0, select and reveals the given line
	 */
	public static void openInEditor(Repository repository, FileDiff d,
			DiffEntry.Side side, int lineNoToReveal) {
		ObjectId[] blobs = d.getBlobs();
		switch (side) {
		case OLD:
			openInEditor(repository, d.getOldPath(), d.getCommit().getParent(0),
					blobs[0], lineNoToReveal);
			break;
		default:
			openInEditor(repository, d.getNewPath(), d.getCommit(),
					blobs[blobs.length - 1], lineNoToReveal);
			break;
		}
	}

	private static void openInEditor(Repository repository, String path,
			RevCommit commit, ObjectId blob, int reveal) {
		try {
			IFileRevision rev = CompareUtils.getFileRevision(path, commit,
					repository, blob);
			if (rev == null) {
				String message = NLS.bind(
						UIText.DiffViewer_notContainedInCommit, path,
						commit.getName());
				Activator.showError(message, null);
				return;
			}
			IWorkbenchWindow window = PlatformUI.getWorkbench()
					.getActiveWorkbenchWindow();
			IWorkbenchPage page = window.getActivePage();
			IEditorPart editor = EgitUiEditorUtils.openEditor(page, rev,
					new NullProgressMonitor());
			EgitUiEditorUtils.revealLine(editor, reveal);
		} catch (IOException | CoreException e) {
			Activator.handleError(UIText.GitHistoryPage_openFailed, e, true);
		}
	}

	/**
	 * Shows a two-way diff between the old and new versions of a
	 * {@link FileDiff} in a compare editor.
	 *
	 * @param repository
	 *            the {@link FileDiff} belongs to
	 * @param d
	 *            the {@link FileDiff} to show
	 */
	public static void showTwoWayFileDiff(Repository repository, FileDiff d) {
		String np = d.getNewPath();
		String op = d.getOldPath();
		RevCommit c = d.getCommit();
		ObjectId[] blobs = d.getBlobs();

		// extract commits
		final RevCommit oldCommit;
		final ObjectId oldObjectId;
		if (!d.getChange().equals(ChangeType.ADD)) {
			oldCommit = c.getParent(0);
			oldObjectId = blobs[0];
		} else {
			// Initial import
			oldCommit = null;
			oldObjectId = null;
		}

		final RevCommit newCommit;
		final ObjectId newObjectId;
		if (d.getChange().equals(ChangeType.DELETE)) {
			newCommit = null;
			newObjectId = null;
		} else {
			newCommit = c;
			newObjectId = blobs[blobs.length - 1];
		}
		IWorkbenchWindow window = PlatformUI.getWorkbench()
				.getActiveWorkbenchWindow();
		IWorkbenchPage page = window.getActivePage();
		if (oldCommit != null && newCommit != null && repository != null) {
			IFile file = np != null
					? ResourceUtil.getFileForLocation(repository, np, false)
					: null;
			try {
				if (file != null) {
					CompareUtils.compare(file, repository, np, op,
							newCommit.getName(), oldCommit.getName(), false,
							page);
				} else {
					IPath location = new Path(
							repository.getWorkTree().getAbsolutePath())
									.append(np);
					CompareUtils.compare(location, repository,
							newCommit.getName(), oldCommit.getName(), false,
							page);
				}
			} catch (IOException e) {
				Activator.handleError(UIText.GitHistoryPage_openFailed, e,
						true);
			}
			return;
		}

		// still happens on initial commits
		final ITypedElement oldSide = createTypedElement(repository, op,
				oldCommit, oldObjectId);
		final ITypedElement newSide = createTypedElement(repository, np,
				newCommit, newObjectId);
		CompareUtils.openInCompare(page,
				new GitCompareFileRevisionEditorInput(newSide, oldSide, null));
	}

	private static ITypedElement createTypedElement(Repository repository,
			String path, final RevCommit commit, final ObjectId objectId) {
		if (null != commit) {
			return CompareUtils.getFileRevisionTypedElement(path, commit,
					repository, objectId);
		} else {
			return new GitCompareFileRevisionEditorInput.EmptyTypedElement(""); //$NON-NLS-1$
		}
	}

}
