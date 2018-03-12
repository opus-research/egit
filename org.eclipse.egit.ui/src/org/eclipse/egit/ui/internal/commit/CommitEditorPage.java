/*******************************************************************************
 *  Copyright (c) 2011 GitHub Inc.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *    Kevin Sawicki (GitHub Inc.) - initial API and implementation
 *******************************************************************************/
package org.eclipse.egit.ui.internal.commit;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.PlatformObject;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIIcons;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.UIUtils;
import org.eclipse.egit.ui.internal.GitLabelProvider;
import org.eclipse.egit.ui.internal.dialogs.SpellcheckableMessageArea;
import org.eclipse.egit.ui.internal.history.CommitFileDiffViewer;
import org.eclipse.egit.ui.internal.history.FileDiff;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.RevWalkUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.forms.IFormColors;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.editor.FormEditor;
import org.eclipse.ui.forms.editor.FormPage;
import org.eclipse.ui.forms.events.ExpansionAdapter;
import org.eclipse.ui.forms.events.ExpansionEvent;
import org.eclipse.ui.forms.events.HyperlinkAdapter;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.widgets.ExpandableComposite;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Hyperlink;
import org.eclipse.ui.forms.widgets.ScrolledForm;
import org.eclipse.ui.forms.widgets.Section;

/**
 * Commit editor page class displaying author, committer, parent commits,
 * message, and file information in form sections.
 */
public class CommitEditorPage extends FormPage implements ISchedulingRule {

	private static final String SIGNED_OFF_BY = "Signed-off-by: {0} <{1}>"; //$NON-NLS-1$

	/**
	 * Abbreviated length of parent id links displayed
	 */
	public static final int PARENT_LENGTH = 20;

	private LocalResourceManager resources = new LocalResourceManager(
			JFaceResources.getResources());

	private Composite tagLabelArea;

	private Section branchSection;

	private TableViewer branchViewer;

	private Section diffSection;

	private CommitFileDiffViewer diffViewer;

	/**
	 * Create commit editor page
	 *
	 * @param editor
	 */
	public CommitEditorPage(FormEditor editor) {
		this(editor, "commitPage", UIText.CommitEditorPage_Title); //$NON-NLS-1$
	}

	/**
	 * Create commit editor page
	 *
	 * @param editor
	 * @param id
	 * @param title
	 */
	public CommitEditorPage(FormEditor editor, String id, String title) {
		super(editor, id, title);
	}

	private void hookExpansionGrabbing(final Section section) {
		section.addExpansionListener(new ExpansionAdapter() {

			public void expansionStateChanged(ExpansionEvent e) {
				((GridData) section.getLayoutData()).grabExcessVerticalSpace = e
						.getState();
				getManagedForm().getForm().getBody().layout(true, true);
			}
		});
	}

	private Image getImage(ImageDescriptor descriptor) {
		return (Image) this.resources.get(descriptor);
	}

	private Section createSection(Composite parent, FormToolkit toolkit,
			int span) {
		Section section = toolkit.createSection(parent,
				ExpandableComposite.TITLE_BAR | ExpandableComposite.TWISTIE
						| ExpandableComposite.EXPANDED);
		GridDataFactory.fillDefaults().span(span, 1).grab(true, true)
				.applyTo(section);
		return section;
	}

	private Composite createSectionClient(Section parent, FormToolkit toolkit) {
		Composite client = toolkit.createComposite(parent);
		GridLayoutFactory.fillDefaults().extendedMargins(2, 2, 2, 2)
				.applyTo(client);
		return client;
	}

	private boolean isSignedOffBy(PersonIdent person) {
		RevCommit commit = getCommit().getRevCommit();
		return commit.getFullMessage().indexOf(getSignedOffByLine(person)) != -1;
	}

	private String getSignedOffByLine(PersonIdent person) {
		return MessageFormat.format(SIGNED_OFF_BY, person.getName(),
				person.getEmailAddress());
	}

	private String replaceSignedOffByLine(String message, PersonIdent person) {
		Pattern pattern = Pattern.compile(
				"^\\s*" + Pattern.quote(getSignedOffByLine(person)) //$NON-NLS-1$
						+ "\\s*$", Pattern.MULTILINE); //$NON-NLS-1$
		return pattern.matcher(message).replaceAll(""); //$NON-NLS-1$
	}

	private Composite createUserArea(Composite parent, FormToolkit toolkit,
			PersonIdent person, boolean author) {
		Composite userArea = toolkit.createComposite(parent);
		GridLayoutFactory.fillDefaults().spacing(2, 2).numColumns(3)
				.applyTo(userArea);

		Label userLabel = toolkit.createLabel(userArea, null);
		userLabel.setImage(getImage(author ? UIIcons.ELCL16_AUTHOR
				: UIIcons.ELCL16_COMMITTER));
		if (author)
			userLabel.setToolTipText(UIText.CommitEditorPage_TooltipAuthor);
		else
			userLabel.setToolTipText(UIText.CommitEditorPage_TooltipCommitter);

		boolean signedOff = isSignedOffBy(person);

		Text userText = new Text(userArea, SWT.FLAT | SWT.READ_ONLY);
		userText.setText(MessageFormat.format(
				author ? UIText.CommitEditorPage_LabelAuthor
						: UIText.CommitEditorPage_LabelCommitter, person
						.getName(), person.getEmailAddress(), person.getWhen()));
		toolkit.adapt(userText, false, false);
		userText.setData(FormToolkit.KEY_DRAW_BORDER, Boolean.FALSE);

		GridDataFactory.fillDefaults().span(signedOff ? 1 : 2, 1)
				.applyTo(userText);
		if (signedOff) {
			Label signedOffLabel = toolkit.createLabel(userArea, null);
			signedOffLabel.setImage(getImage(UIIcons.SIGNED_OFF));
			if (author)
				signedOffLabel
						.setToolTipText(UIText.CommitEditorPage_TooltipSignedOffByAuthor);
			else
				signedOffLabel
						.setToolTipText(UIText.CommitEditorPage_TooltipSignedOffByCommitter);
		}

		return userArea;
	}

	private void updateSectionClient(Section section, Composite client,
			FormToolkit toolkit) {
		hookExpansionGrabbing(section);
		toolkit.paintBordersFor(client);
		section.setClient(client);
	}

	private void createHeaderArea(Composite parent, FormToolkit toolkit,
			int span) {
		RevCommit commit = getCommit().getRevCommit();
		Composite top = toolkit.createComposite(parent);
		GridDataFactory.fillDefaults().grab(true, false).span(span, 1)
				.applyTo(top);
		GridLayoutFactory.fillDefaults().numColumns(2).applyTo(top);

		Composite userArea = toolkit.createComposite(top);
		GridLayoutFactory.fillDefaults().spacing(2, 2).numColumns(1)
				.applyTo(userArea);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(userArea);

		PersonIdent author = commit.getAuthorIdent();
		if (author != null)
			createUserArea(userArea, toolkit, author, true);

		PersonIdent committer = commit.getCommitterIdent();
		if (committer != null && !committer.equals(author))
			createUserArea(userArea, toolkit, committer, false);

		int count = commit.getParentCount();
		if (count > 0) {
			Composite parents = toolkit.createComposite(top);
			GridLayoutFactory.fillDefaults().spacing(2, 2).numColumns(2)
					.applyTo(parents);
			GridDataFactory.fillDefaults().grab(false, false).applyTo(parents);

			for (int i = 0; i < count; i++) {
				final RevCommit parentCommit = commit.getParent(i);
				toolkit.createLabel(parents,
						UIText.CommitEditorPage_LabelParent).setForeground(
						toolkit.getColors().getColor(IFormColors.TB_TOGGLE));
				final Hyperlink link = toolkit
						.createHyperlink(parents,
								parentCommit.abbreviate(PARENT_LENGTH).name(),
								SWT.NONE);
				link.addHyperlinkListener(new HyperlinkAdapter() {

					public void linkActivated(HyperlinkEvent e) {
						try {
							CommitEditor.open(new RepositoryCommit(getCommit()
									.getRepository(), parentCommit));
							if ((e.getStateMask() & SWT.MOD1) != 0)
								getEditor().close(false);
						} catch (PartInitException e1) {
							Activator.logError(
									"Error opening commit editor", e1);//$NON-NLS-1$
						}
					}
				});
			}
		}

		createTagsArea(userArea, toolkit, 2);
	}

	private List<Ref> getTags() {
		Repository repository = getCommit().getRepository();
		List<Ref> tags = new ArrayList<Ref>(repository.getTags().values());
		Collections.sort(tags, new Comparator<Ref>() {

			public int compare(Ref r1, Ref r2) {
				return Repository.shortenRefName(r1.getName())
						.compareToIgnoreCase(
								Repository.shortenRefName(r2.getName()));
			}
		});
		return tags;
	}

	private void createTagsArea(Composite parent, FormToolkit toolkit, int span) {
		Composite tagArea = toolkit.createComposite(parent);
		GridLayoutFactory.fillDefaults().numColumns(2).equalWidth(false)
				.applyTo(tagArea);
		GridDataFactory.fillDefaults().span(span, 1).grab(true, false)
				.applyTo(tagArea);
		toolkit.createLabel(tagArea, UIText.CommitEditorPage_LabelTags)
				.setForeground(
						toolkit.getColors().getColor(IFormColors.TB_TOGGLE));
		tagLabelArea = toolkit.createComposite(tagArea);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(tagLabelArea);
		GridLayoutFactory.fillDefaults().spacing(1, 1).applyTo(tagLabelArea);
	}

	private void fillDiffs(FileDiff[] diffs) {
		diffViewer.setInput(diffs);
		diffSection.setText(MessageFormat.format(
				UIText.CommitEditorPage_SectionFiles,
				Integer.valueOf(diffs.length)));
	}

	private void fillTags(FormToolkit toolkit, List<Ref> tags) {
		for (Control child : tagLabelArea.getChildren())
			child.dispose();

		GridLayoutFactory.fillDefaults().spacing(1, 1).numColumns(tags.size())
				.applyTo(tagLabelArea);

		for (Ref tag : tags) {
			ObjectId id = tag.getPeeledObjectId();
			boolean annotated = id != null;
			if (id == null)
				id = tag.getObjectId();
			CLabel tagLabel = new CLabel(tagLabelArea, SWT.NONE);
			toolkit.adapt(tagLabel, false, false);
			if (annotated)
				tagLabel.setImage(getImage(UIIcons.TAG_ANNOTATED));
			else
				tagLabel.setImage(getImage(UIIcons.TAG));
			tagLabel.setText(Repository.shortenRefName(tag.getName()));
		}
	}

	private void createMessageArea(Composite parent, FormToolkit toolkit,
			int span) {
		Section messageSection = createSection(parent, toolkit, span);
		Composite messageArea = createSectionClient(messageSection, toolkit);

		messageSection.setText(UIText.CommitEditorPage_SectionMessage);

		RevCommit commit = getCommit().getRevCommit();
		String message = commit.getFullMessage();

		PersonIdent author = commit.getAuthorIdent();
		if (author != null)
			message = replaceSignedOffByLine(message, author);
		PersonIdent committer = commit.getCommitterIdent();
		if (committer != null)
			message = replaceSignedOffByLine(message, committer);

		SpellcheckableMessageArea textContent = new SpellcheckableMessageArea(
				messageArea, message, true, toolkit.getBorderStyle()) {

			@Override
			protected IAdaptable getDefaultTarget() {
				return new PlatformObject() {
					public Object getAdapter(Class adapter) {
						return Platform.getAdapterManager().getAdapter(
								getEditorInput(), adapter);
					}
				};
			}

			protected void createMarginPainter() {
				// Disabled intentionally
			}

		};
		if ((toolkit.getBorderStyle() & SWT.BORDER) == 0)
			textContent.setData(FormToolkit.KEY_DRAW_BORDER,
					FormToolkit.TEXT_BORDER);
		GridDataFactory.fillDefaults().hint(SWT.DEFAULT, 80).grab(true, true)
				.applyTo(textContent);

		updateSectionClient(messageSection, messageArea, toolkit);
	}

	private void createBranchesArea(Composite parent, FormToolkit toolkit,
			int span) {
		branchSection = createSection(parent, toolkit, span);
		branchSection.setText(UIText.CommitEditorPage_SectionBranchesEmpty);
		Composite branchesArea = createSectionClient(branchSection, toolkit);

		branchViewer = new TableViewer(toolkit.createTable(branchesArea,
				SWT.V_SCROLL | SWT.H_SCROLL));
		GridDataFactory.fillDefaults().grab(true, true).hint(SWT.DEFAULT, 50)
				.applyTo(branchViewer.getControl());
		branchViewer.setSorter(new ViewerSorter());
		branchViewer.setLabelProvider(new GitLabelProvider() {

			public String getText(Object element) {
				return Repository.shortenRefName(super.getText(element));
			}

		});
		branchViewer.setContentProvider(ArrayContentProvider.getInstance());
		branchViewer.getTable().setData(FormToolkit.KEY_DRAW_BORDER,
				FormToolkit.TREE_BORDER);

		updateSectionClient(branchSection, branchesArea, toolkit);
	}

	private void fillBranches(List<Ref> result) {
		branchViewer.setInput(result);
		branchSection.setText(MessageFormat.format(
				UIText.CommitEditorPage_SectionBranches,
				Integer.valueOf(result.size())));
	}

	private void createFilesArea(Composite parent, FormToolkit toolkit, int span) {
		diffSection = createSection(parent, toolkit, span);
		diffSection.setText(UIText.CommitEditorPage_SectionFilesEmpty);
		Composite filesArea = createSectionClient(diffSection, toolkit);

		diffViewer = new CommitFileDiffViewer(filesArea, getSite(), SWT.MULTI
				| SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION
				| toolkit.getBorderStyle());
		diffViewer.getTable().setData(FormToolkit.KEY_DRAW_BORDER,
				FormToolkit.TREE_BORDER);
		GridDataFactory.fillDefaults().grab(true, true).hint(SWT.DEFAULT, 80)
				.applyTo(diffViewer.getControl());
		diffViewer.setContentProvider(ArrayContentProvider.getInstance());
		diffViewer.setTreeWalk(getCommit().getRepository(), null);

		updateSectionClient(diffSection, filesArea, toolkit);
	}

	private RepositoryCommit getCommit() {
		return (RepositoryCommit) getEditor()
				.getAdapter(RepositoryCommit.class);
	}

	/**
	 * @see org.eclipse.ui.forms.editor.FormPage#createFormContent(org.eclipse.ui.forms.IManagedForm)
	 */
	protected void createFormContent(IManagedForm managedForm) {
		Composite body = managedForm.getForm().getBody();
		body.addDisposeListener(new DisposeListener() {

			public void widgetDisposed(DisposeEvent e) {
				resources.dispose();
			}
		});
		FillLayout bodyLayout = new FillLayout();
		bodyLayout.marginHeight = 5;
		bodyLayout.marginWidth = 5;
		body.setLayout(bodyLayout);

		FormToolkit toolkit = managedForm.getToolkit();

		Composite displayArea = toolkit.createComposite(body);
		GridLayoutFactory.fillDefaults().numColumns(2).applyTo(displayArea);

		createHeaderArea(displayArea, toolkit, 2);
		createMessageArea(displayArea, toolkit, 2);
		createFilesArea(displayArea, toolkit, 1);
		createBranchesArea(displayArea, toolkit, 1);

		loadSections();
	}

	private List<Ref> loadTags() {
		RepositoryCommit repoCommit = getCommit();
		RevCommit commit = repoCommit.getRevCommit();
		Repository repository = repoCommit.getRepository();
		List<Ref> tags = new ArrayList<Ref>();
		for (Ref tag : getTags()) {
			tag = repository.peel(tag);
			ObjectId id = tag.getPeeledObjectId();
			if (id == null)
				id = tag.getObjectId();
			if (!commit.equals(id))
				continue;
			tags.add(tag);
		}
		return tags;
	}

	private List<Ref> loadBranches() {
		Repository repository = getCommit().getRepository();
		RevCommit commit = getCommit().getRevCommit();
		RevWalk revWalk = new RevWalk(repository);
		try {
			Map<String, Ref> refsMap = new HashMap<String, Ref>();
			refsMap.putAll(repository.getRefDatabase().getRefs(
					Constants.R_HEADS));
			refsMap.putAll(repository.getRefDatabase().getRefs(
					Constants.R_REMOTES));
			return RevWalkUtils.findBranchesReachableFrom(commit, revWalk, refsMap.values());
		} catch (IOException e) {
			Activator.handleError(e.getMessage(), e, false);
			return Collections.emptyList();
		}
	}

	private void loadSections() {
		RepositoryCommit commit = getCommit();
		Job refreshJob = new Job(MessageFormat.format(
				UIText.CommitEditorPage_JobName, commit.getRevCommit().name())) {

			@Override
			protected IStatus run(IProgressMonitor monitor) {
				final List<Ref> tags = loadTags();
				final List<Ref> branches = loadBranches();
				final FileDiff[] diffs = getCommit().getDiffs();

				final ScrolledForm form = getManagedForm().getForm();
				if (UIUtils.isUsable(form))
					form.getDisplay().syncExec(new Runnable() {

						public void run() {
							if (!UIUtils.isUsable(form))
								return;

							fillTags(getManagedForm().getToolkit(), tags);
							fillDiffs(diffs);
							fillBranches(branches);
							form.layout(true, true);
						}
					});

				return Status.OK_STATUS;
			}
		};
		refreshJob.setRule(this);
		refreshJob.schedule();
	}

	/**
	 * Refresh the editor page
	 */
	public void refresh() {
		loadSections();
	}

	public boolean contains(ISchedulingRule rule) {
		return rule == this;
	}

	public boolean isConflicting(ISchedulingRule rule) {
		return rule == this;
	}

}
