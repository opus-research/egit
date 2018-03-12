/*******************************************************************************
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2007, Robin Rosenberg <me@lathund.dewire.com.dewire.com>
 * Copyright (C) 2007, Robin Rosenberg <me@lathund.dewire.com>
 * Copyright (C) 2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2007, Shawn O. Pearce <spearce@spearce.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.dialogs;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.compare.CompareUI;
import org.eclipse.compare.ITypedElement;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.egit.core.Activator;
import org.eclipse.egit.core.GitProvider;
import org.eclipse.egit.core.internal.storage.GitFileHistoryProvider;

import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.internal.GitCompareFileRevisionEditorInput;
import org.eclipse.jface.bindings.keys.KeyStroke;
import org.eclipse.jface.bindings.keys.ParseException;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.fieldassist.ContentProposalAdapter;
import org.eclipse.jface.fieldassist.ControlDecoration;
import org.eclipse.jface.fieldassist.FieldDecorationRegistry;
import org.eclipse.jface.fieldassist.IContentProposal;
import org.eclipse.jface.fieldassist.IContentProposalProvider;
import org.eclipse.jface.fieldassist.TextContentAdapter;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.history.IFileHistory;
import org.eclipse.team.core.history.IFileHistoryProvider;
import org.eclipse.team.core.history.IFileRevision;
import org.eclipse.team.internal.ui.history.FileRevisionTypedElement;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.eclipse.jgit.lib.Commit;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.GitIndex;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.Tree;
import org.eclipse.jgit.lib.TreeEntry;
import org.eclipse.jgit.lib.GitIndex.Entry;

/**
 * Dialog is shown to user when they request to commit files. Changes in the
 * selected portion of the tree are shown.
 */
public class CommitDialog extends Dialog {

	class CommitContentProvider implements IStructuredContentProvider {

		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			// Empty
		}

		public void dispose() {
			// Empty
		}

		public Object[] getElements(Object inputElement) {
			return items.toArray();
		}

	}

	class CommitLabelProvider extends WorkbenchLabelProvider implements
			ITableLabelProvider {
		public String getColumnText(Object obj, int columnIndex) {
			CommitItem item = (CommitItem) obj;

			switch (columnIndex) {
			case 0:
				return item.status;

			case 1:
				return item.file.getProject().getName() + ": " //$NON-NLS-1$
						+ item.file.getProjectRelativePath();

			default:
				return null;
			}
		}

		public Image getColumnImage(Object element, int columnIndex) {
			if (columnIndex == 0)
				return getImage(element);
			return null;
		}
	}

	private final class CommitItemFilter extends ViewerFilter {
		@Override
		public boolean select(Viewer viewer, Object parentElement,
				Object element) {
			boolean result = true;
			if (!showUntracked){
				if (element instanceof CommitItem) {
					CommitItem item = (CommitItem)element;
					if (item.status.equals(UIText.CommitDialog_StatusUntracked))
						result = false;
				}
			}
			return result;
		}
	}

	ArrayList<CommitItem> items = new ArrayList<CommitItem>();

	// these activate the value help on author/committer fields; alphanumeric,
	// space plus some expected special chars
	private static final char[] VALUE_HELP_ACTIVATIONCHARS = "abcdefghijklmnopqrstuvwxyz0123457890*@ <>".toCharArray(); //$NON-NLS-1$

	private static final String COMMITTER_VALUES_PREF = "CommitDialog.committerValues"; //$NON-NLS-1$

	private static final String AUTHOR_VALUES_PREF = "CommitDialog.authorValues"; //$NON-NLS-1$

	private static final String SHOW_UNTRACKED_PREF = "CommitDialog.showUntracked"; //$NON-NLS-1$


	/**
	 * @param parentShell
	 */
	public CommitDialog(Shell parentShell) {
		super(parentShell);
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.SELECT_ALL_ID, UIText.CommitDialog_SelectAll, false);
		createButton(parent, IDialogConstants.DESELECT_ALL_ID, UIText.CommitDialog_DeselectAll, false);

		createButton(parent, IDialogConstants.OK_ID, UIText.CommitDialog_Commit, true);
		createButton(parent, IDialogConstants.CANCEL_ID,
				IDialogConstants.CANCEL_LABEL, false);
	}

	Text commitText;
	Text authorText;
	Text committerText;
	Button amendingButton;
	Button signedOffButton;
	Button showUntrackedButton;

	CheckboxTableViewer filesViewer;

	/**
	 * A collection of files that should be already checked in the table.
	 */
	private Collection<IFile> preselectedFiles = Collections.emptyList();

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite container = (Composite) super.createDialogArea(parent);
		parent.getShell().setText(UIText.CommitDialog_CommitChanges);

		GridLayout layout = new GridLayout(2, false);
		container.setLayout(layout);

		Label label = new Label(container, SWT.LEFT);
		label.setText(UIText.CommitDialog_CommitMessage);
		label.setLayoutData(GridDataFactory.fillDefaults().span(2, 1).grab(true, false).create());

		commitText = new Text(container, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL);
		commitText.setLayoutData(GridDataFactory.fillDefaults().span(2, 1).grab(true, true)
				.hint(600, 200).create());

		// allow to commit with ctrl-enter
		commitText.addKeyListener(new KeyAdapter() {
			public void keyPressed(KeyEvent arg0) {
				if (arg0.keyCode == SWT.CR
						&& (arg0.stateMask & SWT.CONTROL) > 0) {
					okPressed();
				} else if (arg0.keyCode == SWT.TAB
						&& (arg0.stateMask & SWT.SHIFT) == 0) {
					arg0.doit = false;
					commitText.traverse(SWT.TRAVERSE_TAB_NEXT);
				}
			}
		});

		new Label(container, SWT.LEFT).setText(UIText.CommitDialog_Author);
		authorText = new Text(container, SWT.BORDER);
		authorText.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());
		if (author != null)
			authorText.setText(author);

		addContentProposalToText(authorText, AUTHOR_VALUES_PREF);
		new Label(container, SWT.LEFT).setText(UIText.CommitDialog_Committer);
		committerText = new Text(container, SWT.BORDER);
		committerText.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).create());
		if (committer != null)
			committerText.setText(committer);
		committerText.addModifyListener(new ModifyListener() {
			String oldCommitter = committerText.getText();
			public void modifyText(ModifyEvent e) {
				if (signedOffButton.getSelection()) {
					// the commit message is signed
					// the signature must be updated
					String newCommitter = committerText.getText();
					String oldSignOff = getSignedOff(oldCommitter);
					String newSignOff = getSignedOff(newCommitter);
					commitText.setText(replaceSignOff(commitText.getText(), oldSignOff, newSignOff));
					oldCommitter = newCommitter;
				}
			}
		});

		addContentProposalToText(committerText, COMMITTER_VALUES_PREF);

		amendingButton = new Button(container, SWT.CHECK);
		if (amending) {
			amendingButton.setSelection(amending);
			amendingButton.setEnabled(false); // if already set, don't allow any changes
			commitText.setText(previousCommitMessage);
			authorText.setText(previousAuthor);
		} else if (!amendAllowed) {
			amendingButton.setEnabled(false);
		}
		amendingButton.addSelectionListener(new SelectionListener() {
			boolean alreadyAdded = false;
			public void widgetSelected(SelectionEvent arg0) {
				if (alreadyAdded)
					return;
				if (amendingButton.getSelection()) {
					alreadyAdded = true;
					String curText = commitText.getText();
					if (curText.length() > 0)
						curText += "\n"; //$NON-NLS-1$
					commitText.setText(curText + previousCommitMessage);
					authorText.setText(previousAuthor);
				}
			}

			public void widgetDefaultSelected(SelectionEvent arg0) {
				// Empty
			}
		});

		amendingButton.setText(UIText.CommitDialog_AmendPreviousCommit);
		amendingButton.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).span(2, 1).create());

		signedOffButton = new Button(container, SWT.CHECK);
		signedOffButton.setSelection(signedOff);
		signedOffButton.setText(UIText.CommitDialog_AddSOB);
		signedOffButton.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).span(2, 1).create());

		signedOffButton.addSelectionListener(new SelectionListener() {
			public void widgetSelected(SelectionEvent arg0) {
				String curText = commitText.getText();
				if (signedOffButton.getSelection()) {
					// add signed off line
					commitText.setText(signOff(curText));
				} else {
					// remove signed off line
					curText = replaceSignOff(curText, getSignedOff(), ""); //$NON-NLS-1$
					if (curText.endsWith(Text.DELIMITER + Text.DELIMITER))
						curText = curText.substring(0, curText.length() - Text.DELIMITER.length());
					commitText.setText(curText);
				}
			}

			public void widgetDefaultSelected(SelectionEvent arg0) {
				// Empty
			}
		});

		showUntrackedButton = new Button(container, SWT.CHECK);
		showUntrackedButton.setText(UIText.CommitDialog_ShowUntrackedFiles);
		showUntrackedButton.setLayoutData(GridDataFactory.fillDefaults().grab(true, false).span(2, 1).create());

		IDialogSettings settings = org.eclipse.egit.ui.Activator.getDefault()
				.getDialogSettings();
		if (settings.get(SHOW_UNTRACKED_PREF) != null) {
			showUntracked = Boolean.valueOf(settings.get(SHOW_UNTRACKED_PREF))
					.booleanValue();
		}

		showUntrackedButton.setSelection(showUntracked);

		showUntrackedButton.addSelectionListener(new SelectionListener() {

			public void widgetSelected(SelectionEvent e) {
				showUntracked = showUntrackedButton.getSelection();
				filesViewer.refresh(true);
			}

			public void widgetDefaultSelected(SelectionEvent e) {
				// Empty
			}
		});

		commitText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				updateSignedOffButton();
			}
		});
		updateSignedOffButton();

		Table resourcesTable = new Table(container, SWT.H_SCROLL | SWT.V_SCROLL
				| SWT.FULL_SELECTION | SWT.MULTI | SWT.CHECK | SWT.BORDER);
		resourcesTable.setLayoutData(GridDataFactory.fillDefaults().hint(600,
				200).span(2,1).grab(true, true).create());

		resourcesTable.addSelectionListener(new CommitItemSelectionListener());

		resourcesTable.setHeaderVisible(true);
		TableColumn statCol = new TableColumn(resourcesTable, SWT.LEFT);
		statCol.setText(UIText.CommitDialog_Status);
		statCol.setWidth(150);
		statCol.addSelectionListener(new HeaderSelectionListener(CommitItem.Order.ByStatus));

		TableColumn resourceCol = new TableColumn(resourcesTable, SWT.LEFT);
		resourceCol.setText(UIText.CommitDialog_File);
		resourceCol.setWidth(415);
		resourceCol.addSelectionListener(new HeaderSelectionListener(CommitItem.Order.ByFile));

		filesViewer = new CheckboxTableViewer(resourcesTable);
		filesViewer.setContentProvider(new CommitContentProvider());
		filesViewer.setLabelProvider(new CommitLabelProvider());
		filesViewer.addFilter(new CommitItemFilter());
		filesViewer.setInput(items);
		filesViewer.getTable().setMenu(getContextMenu());

		// pre-emptively check any preselected files
		for (IFile selectedFile : preselectedFiles) {
			for (CommitItem item : items) {
				if (item.file.equals(selectedFile)) {
					filesViewer.setChecked(item, true);
					break;
				}
			}
		}

		container.pack();
		return container;
	}

	private void updateSignedOffButton() {
		String curText = commitText.getText();
		if (!curText.endsWith(Text.DELIMITER))
			curText += Text.DELIMITER;

		signedOffButton.setSelection(curText.indexOf(getSignedOff() + Text.DELIMITER) != -1);
	}

	private String getSignedOff() {
		return getSignedOff(committerText.getText());
	}

	private String getSignedOff(String signer) {
		return Constants.SIGNED_OFF_BY_TAG + signer;
	}

	private String signOff(String input) {
		String output = input;
		if (!output.endsWith(Text.DELIMITER))
			output += Text.DELIMITER;

		// if the last line is not a signed off (amend a commit), had a line break
		if (!getLastLine(output).startsWith(Constants.SIGNED_OFF_BY_TAG))
			output += Text.DELIMITER;
		output += getSignedOff();
		return output;
	}

	private String getLastLine(String input) {
		String output = input;
		int breakLength = Text.DELIMITER.length();

		// remove last line break if exist
		int lastIndexOfLineBreak = output.lastIndexOf(Text.DELIMITER);
		if (lastIndexOfLineBreak != -1 && lastIndexOfLineBreak == output.length() - breakLength)
			output = output.substring(0, output.length() - breakLength);

		// get the last line
		lastIndexOfLineBreak = output.lastIndexOf(Text.DELIMITER);
		return lastIndexOfLineBreak == -1 ? output : output.substring(lastIndexOfLineBreak + breakLength, output.length());
	}

	private String replaceSignOff(String input, String oldSignOff, String newSignOff) {
		assert input != null;
		assert oldSignOff != null;
		assert newSignOff != null;

		String curText = input;
		if (!curText.endsWith(Text.DELIMITER))
			curText += Text.DELIMITER;

		int indexOfSignOff = curText.indexOf(oldSignOff + Text.DELIMITER);
		if (indexOfSignOff == -1)
			return input;

		return input.substring(0, indexOfSignOff) + newSignOff + input.substring(indexOfSignOff + oldSignOff.length(), input.length());
	}

	private Menu getContextMenu() {
		Menu menu = new Menu(filesViewer.getTable());
		MenuItem item = new MenuItem(menu, SWT.PUSH);
		item.setText(UIText.CommitDialog_AddFileOnDiskToIndex);
		item.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event arg0) {
				IStructuredSelection sel = (IStructuredSelection) filesViewer.getSelection();
				if (sel.isEmpty()) {
					return;
				}
				try {
					ArrayList<GitIndex> changedIndexes = new ArrayList<GitIndex>();
					for (Iterator<?> it = sel.iterator(); it.hasNext();) {
						CommitItem commitItem = (CommitItem) it.next();

						IProject project = commitItem.file.getProject();
						RepositoryMapping map = RepositoryMapping.getMapping(project);

						Repository repo = map.getRepository();
						GitIndex index = null;
						index = repo.getIndex();
						String repoRelativePath = map.getRepoRelativePath(commitItem.file);
						Entry entry = index.getEntry(repoRelativePath);
						if (entry != null && entry.isModified(map.getWorkDir())) {
							entry.update(new File(map.getWorkDir(), entry.getName()));
							if (!changedIndexes.contains(index))
								changedIndexes.add(index);
							commitItem.status = UIText.CommitDialog_StatusModified;
						} else if (entry == null) {
							final Tree headTree = repo.mapTree(Constants.HEAD);
							TreeEntry  headEntry = (headTree == null ? null : headTree.findBlobMember(repoRelativePath));
							if (headEntry == null){
								entry = index.add(map.getWorkDir(), new File(map.getWorkDir(), repoRelativePath));
								if (!changedIndexes.contains(index))
									changedIndexes.add(index);
								commitItem.status = UIText.CommitDialog_StatusAdded;
							}
						}
					}
					if (!changedIndexes.isEmpty()) {
						for (GitIndex idx : changedIndexes) {
							idx.write();
						}
						filesViewer.refresh(true);
					}
				} catch (IOException e) {

					return;
				}
			}
		});

		return menu;
	}

	private static String getFileStatus(IFile file) {
		String prefix = UIText.CommitDialog_StatusUnknown;

		try {
			RepositoryMapping repositoryMapping = RepositoryMapping
					.getMapping(file.getProject());

			Repository repo = repositoryMapping.getRepository();
			GitIndex index = repo.getIndex();
			Tree headTree = repo.mapTree(Constants.HEAD);

			String repoPath = repositoryMapping.getRepoRelativePath(file);
			TreeEntry headEntry = (headTree == null ? null : headTree.findBlobMember(repoPath));
			boolean headExists = (headTree == null ? false : headTree.existsBlob(repoPath));

			Entry indexEntry = index.getEntry(repoPath);
			if (headEntry == null) {
				prefix = UIText.CommitDialog_StatusAdded;
				if (indexEntry == null) {
					prefix = UIText.CommitDialog_StatusUntracked;
				}
				else if (indexEntry.isModified(repositoryMapping.getWorkDir()))
					prefix = UIText.CommitDialog_StatusAddedIndexDiff;
			} else if (indexEntry == null) {
				prefix = UIText.CommitDialog_StatusRemoved;
			} else if (headExists
					&& !headEntry.getId().equals(indexEntry.getObjectId())) {
				prefix = UIText.CommitDialog_StatusModified;

				if (indexEntry.isModified(repositoryMapping.getWorkDir()))
					prefix = UIText.CommitDialog_StatusModifiedIndexDiff;
			} else if (!new File(repositoryMapping.getWorkDir(), indexEntry
					.getName()).isFile()) {
				prefix = UIText.CommitDialog_StatusRemovedNotStaged;
			} else if (indexEntry.isModified(repositoryMapping.getWorkDir())) {
				prefix = UIText.CommitDialog_StatusModifiedNotStaged;
			}

		} catch (Exception e) {
			Activator.logError(UIText.CommitDialog_problemFindingFileStatus, e);
			prefix = e.getMessage();
		}

		return prefix;
	}

	/**
	 * @return The message the user entered
	 */
	public String getCommitMessage() {
		return commitMessage.replaceAll(Text.DELIMITER, "\n"); //$NON-NLS-1$;
	}

	/**
	 * Preset a commit message. This might be for amending a commit.
	 * @param s the commit message
	 */
	public void setCommitMessage(String s) {
		this.commitMessage = s;
	}

	private String commitMessage = ""; //$NON-NLS-1$
	private String author = null;
	private String committer = null;
	private String previousAuthor = null;
	private boolean signedOff = false;
	private boolean amending = false;
	private boolean amendAllowed = true;
	private boolean showUntracked = true;

	private ArrayList<IFile> selectedFiles = new ArrayList<IFile>();
	private String previousCommitMessage = ""; //$NON-NLS-1$

	/**
	 * Pre-select suggested set of resources to commit
	 *
	 * @param items
	 */
	public void setSelectedFiles(IFile[] items) {
		Collections.addAll(selectedFiles, items);
	}

	/**
	 * @return the resources selected by the user to commit.
	 */
	public IFile[] getSelectedFiles() {
		return selectedFiles.toArray(new IFile[0]);
	}

	/**
	 * Sets the files that should be checked in this table.
	 *
	 * @param preselectedFiles
	 *            the files to be checked in the dialog's table, must not be
	 *            <code>null</code>
	 */
	public void setPreselectedFiles(Collection<IFile> preselectedFiles) {
		Assert.isNotNull(preselectedFiles);
		this.preselectedFiles = preselectedFiles;
	}

	class HeaderSelectionListener extends SelectionAdapter {

		private CommitItem.Order order;

		private boolean reversed;

		public HeaderSelectionListener(CommitItem.Order order) {
			this.order = order;
		}

		@Override
		public void widgetSelected(SelectionEvent e) {
			TableColumn column = (TableColumn)e.widget;
			Table table = column.getParent();

			if (column == table.getSortColumn()) {
				reversed = !reversed;
			} else {
				reversed = false;
			}
			table.setSortColumn(column);

			Comparator<CommitItem> comparator;
			if (reversed) {
				comparator = order.descending();
				table.setSortDirection(SWT.DOWN);
			} else {
				comparator = order;
				table.setSortDirection(SWT.UP);
			}

			filesViewer.setComparator(new CommitViewerComparator(comparator));
		}

	}

	class CommitItemSelectionListener extends SelectionAdapter {

		public void widgetDefaultSelected(SelectionEvent e) {
			IStructuredSelection selection = (IStructuredSelection) filesViewer.getSelection();

			CommitItem commitItem = (CommitItem) selection.getFirstElement();
			if (commitItem == null) {
				return;
			}
			if (commitItem.status.equals(UIText.CommitDialog_StatusUntracked)) {
				return;
			}

			IProject project = commitItem.file.getProject();
			RepositoryMapping mapping = RepositoryMapping.getMapping(project);
			if (mapping == null) {
				return;
			}
			Repository repository = mapping.getRepository();

			Commit headCommit;
			try {
				headCommit = repository.mapCommit(Constants.HEAD);
			} catch (IOException e1) {
				headCommit = null;
			}
			if (headCommit == null) {
				return;
			}

			GitProvider provider = (GitProvider) RepositoryProvider.getProvider(project);
			GitFileHistoryProvider fileHistoryProvider = (GitFileHistoryProvider) provider.getFileHistoryProvider();

			IFileHistory fileHistory = fileHistoryProvider.getFileHistoryFor(commitItem.file, IFileHistoryProvider.SINGLE_REVISION, null);

			IFileRevision baseFile = fileHistory.getFileRevisions()[0];
			IFileRevision nextFile = fileHistoryProvider.getWorkspaceFileRevision(commitItem.file);

			ITypedElement base = new FileRevisionTypedElement(baseFile);
			ITypedElement next = new FileRevisionTypedElement(nextFile);

			GitCompareFileRevisionEditorInput input = new GitCompareFileRevisionEditorInput(base, next, null);
			CompareUI.openCompareDialog(input);
		}

	}

	@Override
	protected void okPressed() {
		commitMessage = commitText.getText();
		author = authorText.getText().trim();
		committer = committerText.getText().trim();
		signedOff = signedOffButton.getSelection();
		amending = amendingButton.getSelection();

		Object[] checkedElements = filesViewer.getCheckedElements();
		selectedFiles.clear();
		for (Object obj : checkedElements)
			selectedFiles.add(((CommitItem) obj).file);

		if (commitMessage.trim().length() == 0) {
			MessageDialog.openWarning(getShell(), UIText.CommitDialog_ErrorNoMessage, UIText.CommitDialog_ErrorMustEnterCommitMessage);
			return;
		}

		boolean authorValid = false;
		if (author.length() > 0) {
			try {
				new PersonIdent(author);
				authorValid = true;
			} catch (IllegalArgumentException e) {
				authorValid = false;
			}
		}
		if (!authorValid) {
			MessageDialog.openWarning(getShell(), UIText.CommitDialog_ErrorInvalidAuthor, UIText.CommitDialog_ErrorInvalidAuthorSpecified);
			return;
		}

		boolean committerValid = false;
		if (committer.length() > 0) {
			try {
				new PersonIdent(committer);
				committerValid = true;
			} catch (IllegalArgumentException e) {
				committerValid = false;
			}
		}
		if (!committerValid) {
			MessageDialog.openWarning(getShell(), UIText.CommitDialog_ErrorInvalidAuthor, UIText.CommitDialog_ErrorInvalidCommitterSpecified);
			return;
		}

		if (selectedFiles.isEmpty() && !amending) {
			MessageDialog.openWarning(getShell(), UIText.CommitDialog_ErrorNoItemsSelected, UIText.CommitDialog_ErrorNoItemsSelectedToBeCommitted);
			return;
		}

		addValueToPrefs(author, AUTHOR_VALUES_PREF);
		addValueToPrefs(committer, COMMITTER_VALUES_PREF);

		IDialogSettings settings = org.eclipse.egit.ui.Activator
			.getDefault().getDialogSettings();
		settings.put(SHOW_UNTRACKED_PREF, showUntracked);
		super.okPressed();
	}

	private void addValueToPrefs(String value, String prefsName) {
		// don't store empty values
		if (value.length() > 0) {
			// we need to mix the value in
			IDialogSettings settings = org.eclipse.egit.ui.Activator
					.getDefault().getDialogSettings();
			String[] existingValues = settings.getArray(prefsName);
			if (existingValues == null) {
				existingValues = new String[] { value };
				settings.put(prefsName, existingValues);
			} else {

				List<String> values = new ArrayList<String>(
						existingValues.length + 1);

				for (String existingValue : existingValues)
					values.add(existingValue);
				// if it is already the first value, we don't need to do
				// anything
				if (values.indexOf(value) == 0)
					return;

				values.remove(value);
				// we insert at the top
				values.add(0, value);

				while (values.size() > 10)
					values.remove(values.size() - 1);

				settings.put(prefsName, values
						.toArray(new String[values.size()]));
			}
		}
	}

	/**
	 * Set the total list of changed resources, including additions and
	 * removals
	 *
	 * @param files potentially affected by a new commit
	 */
	public void setFileList(ArrayList<IFile> files) {
		items.clear();
		for (IFile file : files) {
			CommitItem item = new CommitItem();
			item.status = getFileStatus(file);
			item.file = file;
			items.add(item);
		}
	}

	@Override
	protected void buttonPressed(int buttonId) {
		if (IDialogConstants.SELECT_ALL_ID == buttonId) {
			filesViewer.setAllChecked(true);
		}
		if (IDialogConstants.DESELECT_ALL_ID == buttonId) {
			filesViewer.setAllChecked(false);
		}
		super.buttonPressed(buttonId);
	}

	/**
	 * @return The author to set for the commit
	 */
	public String getAuthor() {
		return author;
	}

	/**
	 * Pre-set author for the commit
	 *
	 * @param author
	 */
	public void setAuthor(String author) {
		this.author = author;
	}

	/**
	 * @return The committer to set for the commit
	 */
	public String getCommitter() {
		return committer;
	}

	/**
	 * Pre-set committer for the commit
	 *
	 * @param committer
	 */
	public void setCommitter(String committer) {
		this.committer = committer;
	}

	/**
	 * Pre-set the previous author if amending the commit
	 *
	 * @param previousAuthor
	 */
	public void setPreviousAuthor(String previousAuthor) {
		this.previousAuthor = previousAuthor;
	}

	/**
	 * @return whether to auto-add a signed-off line to the message
	 */
	public boolean isSignedOff() {
		return signedOff;
	}

	/**
	 * Pre-set whether a signed-off line should be included in the commit
	 * message.
	 *
	 * @param signedOff
	 */
	public void setSignedOff(boolean signedOff) {
		this.signedOff = signedOff;
	}

	/**
	 * @return whether the last commit is to be amended
	 */
	public boolean isAmending() {
		return amending;
	}

	/**
	 * Pre-set whether the last commit is going to be amended
	 *
	 * @param amending
	 */
	public void setAmending(boolean amending) {
		this.amending = amending;
	}

	/**
	 * Set the message from the previous commit for amending.
	 *
	 * @param string
	 */
	public void setPreviousCommitMessage(String string) {
		this.previousCommitMessage = string;
	}

	/**
	 * Set whether the previous commit may be amended
	 *
	 * @param amendAllowed
	 */
	public void setAmendAllowed(boolean amendAllowed) {
		this.amendAllowed = amendAllowed;
	}

	@Override
	protected int getShellStyle() {
		return super.getShellStyle() | SWT.RESIZE;
	}

	private void addContentProposalToText(Text textField,
			final String preferenceKey) {

		KeyStroke stroke;
		try {
			stroke = KeyStroke.getInstance("M1+SPACE"); //$NON-NLS-1$
		} catch (ParseException e1) {
			org.eclipse.egit.ui.Activator.getDefault().getLog().log(
					new Status(IStatus.ERROR, org.eclipse.egit.ui.Activator
							.getPluginId(), e1.getMessage(), e1));
			return;
		}

		ControlDecoration dec = new ControlDecoration(textField, SWT.TOP
				| SWT.LEFT);

		dec.setImage(FieldDecorationRegistry.getDefault().getFieldDecoration(
				FieldDecorationRegistry.DEC_CONTENT_PROPOSAL).getImage());

		dec.setShowOnlyOnFocus(true);
		dec.setShowHover(true);

		dec.setDescriptionText(NLS.bind(UIText.CommitDialog_ValueHelp_Message,
				stroke.format()));

		IContentProposalProvider cp = new IContentProposalProvider() {

			public IContentProposal[] getProposals(String contents, int position) {

				List<IContentProposal> resultList = new ArrayList<IContentProposal>();

				// make the simplest possible pattern check: allow "*"
				// for multiple characters
				String patternString = contents;
				// ignore spaces in the beginning
				while (patternString.length() > 0
						&& patternString.charAt(0) == ' ') {
					patternString = patternString.substring(1);
				}

				// we quote the string as it may contain spaces
				// and other stuff colliding with the Pattern
				patternString = Pattern.quote(patternString);

				patternString = patternString.replaceAll("\\x2A", ".*"); //$NON-NLS-1$ //$NON-NLS-2$

				// make sure we add a (logical) * at the end
				if (!patternString.endsWith(".*")) { //$NON-NLS-1$
					patternString = patternString + ".*"; //$NON-NLS-1$
				}

				// let's compile a case-insensitive pattern (assumes ASCII only)
				Pattern pattern;
				try {
					pattern = Pattern.compile(patternString,
							Pattern.CASE_INSENSITIVE);
				} catch (PatternSyntaxException e) {
					pattern = null;
				}

				String[] proposals = org.eclipse.egit.ui.Activator.getDefault()
						.getDialogSettings().getArray(preferenceKey);

				if (proposals != null)
					for (final String uriString : proposals) {

						if (pattern != null
								&& !pattern.matcher(uriString).matches())
							continue;

						IContentProposal propsal = new IContentProposal() {

							public String getLabel() {
								return null;
							}

							public String getDescription() {
								return null;
							}

							public int getCursorPosition() {
								return 0;
							}

							public String getContent() {
								return uriString;
							}
						};
						resultList.add(propsal);
					}

				return resultList.toArray(new IContentProposal[resultList
						.size()]);
			}
		};

		ContentProposalAdapter adapter = new ContentProposalAdapter(textField,
				new TextContentAdapter(), cp, stroke,
				VALUE_HELP_ACTIVATIONCHARS);
		// set the acceptance style to always replace the complete content
		adapter
				.setProposalAcceptanceStyle(ContentProposalAdapter.PROPOSAL_REPLACE);

	}

}

class CommitItem {
	String status;

	IFile file;

	public static enum Order implements Comparator<CommitItem> {
		ByStatus() {

			public int compare(CommitItem o1, CommitItem o2) {
				return o1.status.compareTo(o2.status);
			}

		},

		ByFile() {

			public int compare(CommitItem o1, CommitItem o2) {
				return o1.file.getProjectRelativePath().toString().
					compareTo(o2.file.getProjectRelativePath().toString());
			}

		};

		public Comparator<CommitItem> ascending() {
			return this;
		}

		public Comparator<CommitItem> descending() {
			return Collections.reverseOrder(this);
		}
	}
}

class CommitViewerComparator extends ViewerComparator {

	public CommitViewerComparator(Comparator comparator){
		super(comparator);
	}

	@SuppressWarnings("unchecked")
	@Override
	public int compare(Viewer viewer, Object e1, Object e2) {
		return getComparator().compare(e1, e2);
	}

}
