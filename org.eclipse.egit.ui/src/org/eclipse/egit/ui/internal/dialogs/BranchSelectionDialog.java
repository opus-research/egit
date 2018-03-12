/*******************************************************************************
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2007, Robin Rosenberg <me@lathund.dewire.com.dewire.com>
 * Copyright (C) 2007, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.dialogs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.egit.core.op.ResetOperation.ResetType;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIText;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.window.Window;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefRename;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RefUpdate.Result;

/**
 * The branch and reset selection dialog
 *
 */
public class BranchSelectionDialog extends Dialog {
	private final Repository repo;

	private boolean showResetType = true;

	/**
	 * Construct a dialog to select a branch to reset to or check out
	 * @param parentShell
	 * @param repo
	 */
	public BranchSelectionDialog(Shell parentShell, Repository repo) {
		super(parentShell);
		this.repo = repo;
	}

	/**
	 * Pre-set whether or present a reset or checkout dialog
	 * @param show
	 */
	public void setShowResetType(boolean show) {
		this.showResetType = show;
	}

	private Composite parent;

	private Tree branchTree;

	@Override
	protected Composite createDialogArea(Composite base) {
		parent = (Composite) super.createDialogArea(base);
		parent.setLayout(GridLayoutFactory.swtDefaults().create());
		new Label(parent, SWT.NONE).setText(UIText.BranchSelectionDialog_Refs);
		branchTree = new Tree(parent, SWT.BORDER);
		branchTree.setLayoutData(GridDataFactory.fillDefaults().grab(true,true).hint(500, 300).create());

		if (showResetType) {
			buildResetGroup();
		}

		String rawTitle = showResetType ? UIText.BranchSelectionDialog_TitleReset
				: UIText.BranchSelectionDialog_TitleCheckout;
		getShell().setText(
				NLS.bind(rawTitle, new Object[] { repo.getDirectory() }));

		try {
			fillTreeWithBranches(null);
		} catch (Throwable e) {
			Activator.logError(UIText.BranchSelectionDialog_ErrorCouldNotRefresh, e);
		}

		return parent;
	}

	private void buildResetGroup() {
		Group g = new Group(parent, SWT.NONE);
		g.setText(UIText.BranchSelectionDialog_ResetType);
		g.setLayoutData(GridDataFactory.swtDefaults().align(SWT.CENTER, SWT.CENTER).create());
		g.setLayout(new RowLayout(SWT.VERTICAL));

		Button soft = new Button(g, SWT.RADIO);
		soft.setText(UIText.BranchSelectionDialog_ResetTypeSoft);
		soft.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				resetType = ResetType.SOFT;
			}
		});

		Button medium = new Button(g, SWT.RADIO);
		medium.setSelection(true);
		medium.setText(UIText.BranchSelectionDialog_ResetTypeMixed);
		medium.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				resetType = ResetType.MIXED;
			}
		});

		Button hard = new Button(g, SWT.RADIO);
		hard.setText(UIText.BranchSelectionDialog_ResetTypeHard);
		hard.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				resetType = ResetType.HARD;
			}
		});
	}

	private void fillTreeWithBranches(String select) throws IOException {
		String branch = repo.getFullBranch();
		List<String> branches = new ArrayList<String>(repo.getAllRefs()
				.keySet());
		Collections.sort(branches);

		TreeItem curItem = null;
		TreeItem curSubItem = null;
		String curPrefix = null;
		String curSubPrefix = null;
		TreeItem itemToSelect = null;

		for (String ref : branches) {
			String shortName = ref;
			if (ref.startsWith(Constants.R_HEADS)) {
				shortName = ref.substring(11);
				if (!Constants.R_HEADS.equals(curPrefix)) {
					curPrefix = Constants.R_HEADS;
					curSubPrefix = null;
					curSubItem = null;
					curItem = new TreeItem(branchTree, SWT.NONE);
					curItem.setText(UIText.BranchSelectionDialog_LocalBranches);
				}
			} else if (ref.startsWith(Constants.R_REMOTES)) {
				shortName = ref.substring(13);
				if (!Constants.R_REMOTES.equals(curPrefix)) {
					curPrefix = Constants.R_REMOTES;
					curItem = new TreeItem(branchTree, SWT.NONE);
					curItem.setText(UIText.BranchSelectionDialog_RemoteBranches);
					curSubItem = null;
					curSubPrefix = null;
				}

				int slashPos = shortName.indexOf("/"); //$NON-NLS-1$
				if (slashPos > -1) {
					String remoteName = shortName.substring(0, slashPos);
					shortName = shortName.substring(slashPos+1);
					if (!remoteName.equals(curSubPrefix)) {
						curSubItem = new TreeItem(curItem, SWT.NONE);
						curSubItem.setText(remoteName);
						curSubPrefix = remoteName;
					}
				} else {
					curSubItem = null;
					curSubPrefix = null;
				}
			} else if (ref.startsWith(Constants.R_TAGS)) {
				shortName = ref.substring(10);
				if (!Constants.R_TAGS.equals(curPrefix)) {
					curPrefix = Constants.R_TAGS;
					curSubPrefix = null;
					curSubItem = null;
					curItem = new TreeItem(branchTree, SWT.NONE);
					curItem.setText(UIText.BranchSelectionDialog_Tags);
				}
			}
			TreeItem item;
			if (curItem == null)
				item = new TreeItem(branchTree, SWT.NONE);
			else if (curSubItem == null)
				item = new TreeItem(curItem, SWT.NONE);
			else item = new TreeItem(curSubItem, SWT.NONE);
			item.setData(ref);
			if (ref.equals(branch)) {
				item.setText(shortName + UIText.BranchSelectionDialog_BranchSuffix_Current);
				FontData fd = item.getFont().getFontData()[0];
				fd.setStyle(fd.getStyle() | SWT.BOLD);
				final Font f = new Font(getShell().getDisplay(), fd);
				item.setFont(f);
				item.addDisposeListener(new DisposeListener() {
					public void widgetDisposed(DisposeEvent e) {
						f.dispose();
					}
				});
				branchTree.showItem(item);
			}
			else item.setText(shortName);
			if (ref.equals(select))
				itemToSelect = item;
			branchTree.setLinesVisible(true);
		}
		if (itemToSelect != null) {
			branchTree.select(itemToSelect);
			branchTree.showItem(itemToSelect);
		}
	}

	private String refName = null;

	/**
	 * @return Selected ref
	 */
	public String getRefName() {
		return refName;
	}

	private ResetType resetType = ResetType.MIXED;

	/**
	 * @return Type of Reset
	 */
	public ResetType getResetType() {
		return resetType;
	}

	@Override
	protected void okPressed() {
		refNameFromDialog();
		if (refName == null) {
			MessageDialog.openWarning(getShell(),
					UIText.BranchSelectionDialog_NoBranchSeletectTitle,
					UIText.BranchSelectionDialog_NoBranchSeletectMessage);
			return;
		}

		if (showResetType) {
			if (resetType == ResetType.HARD) {
				if (!MessageDialog.openQuestion(getShell(),
						UIText.BranchSelectionDialog_ReallyResetTitle,
						UIText.BranchSelectionDialog_ReallyResetMessage)) {
					return;
				}
			}
		}

		super.okPressed();
	}

	private void refNameFromDialog() {
		TreeItem[] selection = branchTree.getSelection();
		refName = null;
		if (selection != null && selection.length > 0) {
			TreeItem item = selection[0];
			refName = (String) item.getData();
		}
	}

	private InputDialog getRefNameInputDialog(String prompt) {
		InputDialog labelDialog = new InputDialog(
				getShell(),
				UIText.BranchSelectionDialog_QuestionNewBranchTitle,
				prompt,
				null, new IInputValidator() {
					public String isValid(String newText) {
						String testFor = Constants.R_HEADS + newText;
						try {
							if (repo.resolve(testFor) != null)
								return UIText.BranchSelectionDialog_ErrorAlreadyExists;
						} catch (IOException e1) {
							Activator.logError(NLS.bind(
									UIText.BranchSelectionDialog_ErrorCouldNotResolve, testFor), e1);
							return e1.getMessage();
						}
						if (!Repository.isValidRefName(testFor))
							return UIText.BranchSelectionDialog_ErrorInvalidRefName;
						return null;
					}
				});
		labelDialog.setBlockOnOpen(true);
		return labelDialog;
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		if (!showResetType) {
			Button newButton = new Button(parent, SWT.PUSH);
			newButton.setFont(JFaceResources.getDialogFont());
			newButton.setText(UIText.BranchSelectionDialog_NewBranch);
			((GridLayout)parent.getLayout()).numColumns++;
			Button renameButton = new Button(parent, SWT.PUSH);
			renameButton.setText(UIText.BranchSelectionDialog_Rename);
			renameButton.addSelectionListener(new SelectionListener() {
				public void widgetSelected(SelectionEvent e) {
					// check what ref name the user selected, if any.
					refNameFromDialog();

					String branchName;
					if (refName.startsWith(Constants.R_HEADS))
						branchName = refName.substring(Constants.R_HEADS.length());
					else
						branchName = refName;
					InputDialog labelDialog = getRefNameInputDialog(NLS
							.bind(
									UIText.BranchSelectionDialog_QuestionNewBranchNameMessage,
									branchName));
					if (labelDialog.open() == Window.OK) {
						String newRefName = Constants.R_HEADS + labelDialog.getValue();
						try {
							RefRename renameRef = repo.renameRef(refName, newRefName);
							if (renameRef.rename() != Result.RENAMED) {
								reportError(
										null,
										UIText.BranchSelectionDialog_BranchSelectionDialog_RenamedFailedTitle,
										UIText.BranchSelectionDialog_ErrorCouldNotRenameRef,
										refName, newRefName, renameRef
												.getResult());
							}
						} catch (Throwable e1) {
							reportError(
									e1,
									UIText.BranchSelectionDialog_BranchSelectionDialog_RenamedFailedTitle,
									UIText.BranchSelectionDialog_ErrorCouldNotRenameRef,
									refName, newRefName, e1.getMessage());
						}
						try {
							branchTree.removeAll();
							fillTreeWithBranches(newRefName);
						} catch (Throwable e1) {
							reportError(
									e1,
									UIText.BranchSelectionDialog_BranchSelectionDialog_RenamedFailedTitle,
									UIText.BranchSelectionDialog_ErrorCouldNotRefreshBranchList);
						}
					}
				}
				public void widgetDefaultSelected(SelectionEvent e) {
					widgetSelected(e);
				}
			});
			newButton.addSelectionListener(new SelectionListener() {

				public void widgetSelected(SelectionEvent e) {
					// check what ref name the user selected, if any.
					refNameFromDialog();

					InputDialog labelDialog = getRefNameInputDialog(UIText.BranchSelectionDialog_QuestionNewBranchNameMessage);
					if (labelDialog.open() == Window.OK) {
						String newRefName = Constants.R_HEADS + labelDialog.getValue();
						RefUpdate updateRef;
						try {
							updateRef = repo.updateRef(newRefName);
							Ref startRef = repo.getRef(refName);
							ObjectId startAt;
							if (refName == null)
								startAt = repo.resolve(Constants.HEAD);
							else
								startAt = repo.resolve(refName);
							String startBranch;
							if (startRef != null)
								startBranch = refName;
							else
								startBranch = startAt.name();
							startBranch = repo.shortenRefName(startBranch);
							updateRef.setNewObjectId(startAt);
							updateRef.setRefLogMessage("branch: Created from " + startBranch, false); //$NON-NLS-1$
							updateRef.update();
						} catch (Throwable e1) {
							reportError(
									e1,
									UIText.BranchSelectionDialog_BranchSelectionDialog_CreateFailedTitle,
									UIText.BranchSelectionDialog_ErrorCouldNotCreateNewRef,
									newRefName);
						}
						try {
							branchTree.removeAll();
							fillTreeWithBranches(newRefName);
						} catch (Throwable e1) {
							reportError(e1,
									UIText.BranchSelectionDialog_BranchSelectionDialog_CreateFailedTitle,
									UIText.BranchSelectionDialog_ErrorCouldNotRefreshBranchList);
						}
					}
				}


				public void widgetDefaultSelected(SelectionEvent e) {
					widgetSelected(e);
				}
			});
		}
		createButton(parent, IDialogConstants.OK_ID,
				showResetType ? UIText.BranchSelectionDialog_OkReset
						: UIText.BranchSelectionDialog_OkCheckout, true);
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
	}

	@Override
	protected int getShellStyle() {
		return super.getShellStyle() | SWT.RESIZE;
	}

	private void reportError(Throwable e, String title, String message,
			Object... args) {
		String msg = NLS.bind(message, args);
		MessageDialog.openError(getShell(), title, msg);
		Activator.logError(msg, e);
	}
}
