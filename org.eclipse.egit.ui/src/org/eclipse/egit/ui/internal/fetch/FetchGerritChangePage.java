/*******************************************************************************
 * Copyright (c) 2010, 2017 SAP AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Mathias Kinzler (SAP AG) - initial implementation
 *    Marc Khouzam (Ericsson)  - Add an option not to checkout the new branch
 *    Thomas Wolf <thomas.wolf@paranor.ch> - Bug 493935, 495777
 *******************************************************************************/
package org.eclipse.egit.ui.internal.fetch;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.WorkspaceJob;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.egit.core.internal.gerrit.GerritUtil;
import org.eclipse.egit.core.op.CreateLocalBranchOperation;
import org.eclipse.egit.core.op.ListRemoteOperation;
import org.eclipse.egit.core.op.TagOperation;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.JobFamilies;
import org.eclipse.egit.ui.UIPreferences;
import org.eclipse.egit.ui.UIUtils;
import org.eclipse.egit.ui.internal.ActionUtils;
import org.eclipse.egit.ui.internal.UIText;
import org.eclipse.egit.ui.internal.ValidationUtils;
import org.eclipse.egit.ui.internal.branch.BranchOperationUI;
import org.eclipse.egit.ui.internal.components.BranchNameNormalizer;
import org.eclipse.egit.ui.internal.dialogs.AbstractBranchSelectionDialog;
import org.eclipse.egit.ui.internal.dialogs.BranchEditDialog;
import org.eclipse.egit.ui.internal.dialogs.NonBlockingWizardDialog;
import org.eclipse.egit.ui.internal.gerrit.GerritDialogSettings;
import org.eclipse.jface.bindings.keys.KeyStroke;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.IPageChangeProvider;
import org.eclipse.jface.dialogs.IPageChangedListener;
import org.eclipse.jface.dialogs.PageChangedEvent;
import org.eclipse.jface.fieldassist.ContentProposalAdapter;
import org.eclipse.jface.fieldassist.IContentProposal;
import org.eclipse.jface.fieldassist.IContentProposalProvider;
import org.eclipse.jface.fieldassist.TextContentAdapter;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.IWizardContainer;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TagBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.progress.WorkbenchJob;

/**
 * Fetch a change from Gerrit
 */
public class FetchGerritChangePage extends WizardPage {

	private enum CheckoutMode {
		CREATE_BRANCH, CREATE_TAG, CHECKOUT_FETCH_HEAD, NOCHECKOUT
	}

	private final Repository repository;

	private final IDialogSettings settings;

	private final String lastUriKey;

	private Combo uriCombo;

	private Map<String, ChangeList> changeRefs = new HashMap<>();

	private Text refText;

	private Button createBranch;

	private Button createTag;

	private Button checkoutFetchHead;

	private Button updateFetchHead;

	private Label tagTextlabel;

	private Text tagText;

	private Label branchTextlabel;

	private Text branchText;

	private String refName;

	private Composite warningAdditionalRefNotActive;

	private Button activateAdditionalRefs;

	private IInputValidator branchValidator;

	private IInputValidator tagValidator;

	private Button branchEditButton;

	private Button branchCheckoutButton;

	private ExplicitContentProposalAdapter contentProposer;

	private boolean branchTextEdited;

	private boolean tagTextEdited;

	/**
	 * @param repository
	 * @param refName initial value for the ref field
	 */
	public FetchGerritChangePage(Repository repository, String refName) {
		super(FetchGerritChangePage.class.getName());
		this.repository = repository;
		this.refName = refName;
		setTitle(NLS
				.bind(UIText.FetchGerritChangePage_PageTitle,
						Activator.getDefault().getRepositoryUtil()
								.getRepositoryName(repository)));
		setMessage(UIText.FetchGerritChangePage_PageMessage);
		settings = getDialogSettings();
		lastUriKey = repository + GerritDialogSettings.LAST_URI_SUFFIX;

		branchValidator = ValidationUtils.getRefNameInputValidator(repository,
				Constants.R_HEADS, true);
		tagValidator = ValidationUtils.getRefNameInputValidator(repository,
				Constants.R_TAGS, true);
	}

	@Override
	protected IDialogSettings getDialogSettings() {
		return GerritDialogSettings
				.getSection(GerritDialogSettings.FETCH_FROM_GERRIT_SECTION);
	}

	@Override
	public void createControl(Composite parent) {
		parent.addDisposeListener(event -> {
			for (ChangeList l : changeRefs.values()) {
				l.cancel(ChangeList.CancelMode.INTERRUPT);
			}
			changeRefs.clear();
		});
		Clipboard clipboard = new Clipboard(parent.getDisplay());
		String clipText = (String) clipboard.getContents(TextTransfer
				.getInstance());
		clipboard.dispose();
		String defaultUri = null;
		String defaultCommand = null;
		String defaultChange = null;
		String candidateChange = null;
		if (clipText != null) {
			String pattern = "git fetch (\\w+:\\S+) (refs/changes/\\d+/\\d+/\\d+) && git (\\w+) FETCH_HEAD"; //$NON-NLS-1$
			Matcher matcher = Pattern.compile(pattern).matcher(clipText);
			if (matcher.matches()) {
				defaultUri = matcher.group(1);
				defaultChange = matcher.group(2);
				defaultCommand = matcher.group(3);
			} else {
				candidateChange = determineChangeFromString(clipText.trim());
			}
		}
		Composite main = new Composite(parent, SWT.NONE);
		main.setLayout(new GridLayout(2, false));
		GridDataFactory.fillDefaults().grab(true, true).applyTo(main);
		new Label(main, SWT.NONE)
				.setText(UIText.FetchGerritChangePage_UriLabel);
		uriCombo = new Combo(main, SWT.DROP_DOWN);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(uriCombo);
		uriCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				String uriText = uriCombo.getText();
				ChangeList list = changeRefs.get(uriText);
				if (list != null) {
					list.cancel(ChangeList.CancelMode.INTERRUPT);
				}
				list = new ChangeList(repository, uriText);
				changeRefs.put(uriText, list);
				preFetch(list);
			}
		});
		new Label(main, SWT.NONE)
				.setText(UIText.FetchGerritChangePage_ChangeLabel);
		refText = new Text(main, SWT.SINGLE | SWT.BORDER);
		GridDataFactory.fillDefaults().grab(true, false).applyTo(refText);
		contentProposer = addRefContentProposalToText(refText);
		refText.addVerifyListener(event -> {
			event.text = event.text
					// C.f. https://bugs.eclipse.org/bugs/show_bug.cgi?id=273470
					.replaceAll("\\v", " ") //$NON-NLS-1$ //$NON-NLS-2$
					.trim();
		});

		final Group checkoutGroup = new Group(main, SWT.SHADOW_ETCHED_IN);
		checkoutGroup.setLayout(new GridLayout(3, false));
		GridDataFactory.fillDefaults().span(3, 1).grab(true, false)
				.applyTo(checkoutGroup);
		checkoutGroup.setText(UIText.FetchGerritChangePage_AfterFetchGroup);

		// radio: create local branch
		createBranch = new Button(checkoutGroup, SWT.RADIO);
		GridDataFactory.fillDefaults().span(1, 1).applyTo(createBranch);
		createBranch.setText(UIText.FetchGerritChangePage_LocalBranchRadio);
		createBranch.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				checkPage();
			}
		});

		branchCheckoutButton = new Button(checkoutGroup, SWT.CHECK);
		GridDataFactory.fillDefaults().span(2, 1).align(SWT.END, SWT.CENTER)
				.applyTo(branchCheckoutButton);
		branchCheckoutButton.setFont(JFaceResources.getDialogFont());
		branchCheckoutButton
				.setText(UIText.FetchGerritChangePage_LocalBranchCheckout);
		branchCheckoutButton.setSelection(true);

		branchTextlabel = new Label(checkoutGroup, SWT.NONE);
		GridDataFactory.defaultsFor(branchTextlabel).exclude(false)
				.applyTo(branchTextlabel);
		branchTextlabel.setText(UIText.FetchGerritChangePage_BranchNameText);
		branchText = new Text(checkoutGroup, SWT.SINGLE | SWT.BORDER);
		GridDataFactory.fillDefaults().grab(true, false)
				.align(SWT.FILL, SWT.CENTER).applyTo(branchText);
		branchText.addKeyListener(new KeyAdapter() {

			@Override
			public void keyPressed(KeyEvent e) {
				branchTextEdited = true;
			}
		});
		branchText.addVerifyListener(event -> {
			if (event.text.isEmpty()) {
				branchTextEdited = false;
			}
		});
		branchText.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				checkPage();
			}
		});
		BranchNameNormalizer normalizer = new BranchNameNormalizer(branchText);
		normalizer.setVisible(false);
		branchEditButton = new Button(checkoutGroup, SWT.PUSH);
		branchEditButton.setFont(JFaceResources.getDialogFont());
		branchEditButton.setText(UIText.FetchGerritChangePage_BranchEditButton);
		branchEditButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent selectionEvent) {
				String txt = branchText.getText();
				String refToMark = "".equals(txt) ? null : Constants.R_HEADS + txt; //$NON-NLS-1$
				AbstractBranchSelectionDialog dlg = new BranchEditDialog(
						checkoutGroup.getShell(), repository, refToMark);
				if (dlg.open() == Window.OK) {
					branchText.setText(Repository.shortenRefName(dlg
							.getRefName()));
					branchTextEdited = true;
				} else {
					// force calling branchText's modify listeners
					branchText.setText(branchText.getText());
				}
			}
		});
		GridDataFactory.defaultsFor(branchEditButton).exclude(false)
				.applyTo(branchEditButton);

		// radio: create tag
		createTag = new Button(checkoutGroup, SWT.RADIO);
		GridDataFactory.fillDefaults().span(3, 1).applyTo(createTag);
		createTag.setText(UIText.FetchGerritChangePage_TagRadio);
		createTag.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				checkPage();
			}
		});

		tagTextlabel = new Label(checkoutGroup, SWT.NONE);
		GridDataFactory.defaultsFor(tagTextlabel).exclude(true)
				.applyTo(tagTextlabel);
		tagTextlabel.setText(UIText.FetchGerritChangePage_TagNameText);
		tagText = new Text(checkoutGroup, SWT.SINGLE | SWT.BORDER);
		GridDataFactory.fillDefaults().exclude(true).grab(true, false)
				.applyTo(tagText);
		tagText.addKeyListener(new KeyAdapter() {

			@Override
			public void keyPressed(KeyEvent e) {
				tagTextEdited = true;
			}
		});
		tagText.addVerifyListener(event -> {
			if (event.text.isEmpty()) {
				tagTextEdited = false;
			}
		});
		tagText.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				checkPage();
			}
		});
		BranchNameNormalizer tagNormalizer = new BranchNameNormalizer(tagText,
				UIText.BranchNameNormalizer_TooltipForTag);
		tagNormalizer.setVisible(false);

		// radio: checkout FETCH_HEAD
		checkoutFetchHead = new Button(checkoutGroup, SWT.RADIO);
		GridDataFactory.fillDefaults().span(3, 1).applyTo(checkoutFetchHead);
		checkoutFetchHead.setText(UIText.FetchGerritChangePage_CheckoutRadio);
		checkoutFetchHead.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				checkPage();
			}
		});

		// radio: don't checkout
		updateFetchHead = new Button(checkoutGroup, SWT.RADIO);
		GridDataFactory.fillDefaults().span(3, 1).applyTo(updateFetchHead);
		updateFetchHead.setText(UIText.FetchGerritChangePage_UpdateRadio);
		updateFetchHead.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				checkPage();
			}
		});

		if ("checkout".equals(defaultCommand)) { //$NON-NLS-1$
			checkoutFetchHead.setSelection(true);
		} else {
			createBranch.setSelection(true);
		}

		warningAdditionalRefNotActive = new Composite(main, SWT.NONE);
		GridDataFactory.fillDefaults().span(2, 1).grab(true, false)
				.exclude(true).applyTo(warningAdditionalRefNotActive);
		warningAdditionalRefNotActive.setLayout(new GridLayout(2, false));
		warningAdditionalRefNotActive.setVisible(false);

		activateAdditionalRefs = new Button(warningAdditionalRefNotActive,
				SWT.CHECK);
		activateAdditionalRefs
				.setText(UIText.FetchGerritChangePage_ActivateAdditionalRefsButton);
		activateAdditionalRefs
				.setToolTipText(
						UIText.FetchGerritChangePage_ActivateAdditionalRefsTooltip);

		ActionUtils.setGlobalActions(refText, ActionUtils.createGlobalAction(
				ActionFactory.PASTE, () -> doPaste(refText)));
		refText.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				Change change = Change.fromRef(refText.getText());
				String suggestion = ""; //$NON-NLS-1$
				if (change != null) {
					suggestion = NLS.bind(
							UIText.FetchGerritChangePage_SuggestedRefNamePattern,
							change.getChangeNumber(),
							change.getPatchSetNumber());
				}
				if (!branchTextEdited) {
					branchText.setText(suggestion);
				}
				if (!tagTextEdited) {
					tagText.setText(suggestion);
				}
				checkPage();
			}
		});
		if (defaultChange != null) {
			refText.setText(defaultChange);
		} else if (candidateChange != null) {
			refText.setText(candidateChange);
		}

		// get all available Gerrit URIs from the repository
		SortedSet<String> uris = new TreeSet<>();
		try {
			for (RemoteConfig rc : RemoteConfig.getAllRemoteConfigs(repository
					.getConfig())) {
				if (GerritUtil.isGerritFetch(rc)) {
					if (rc.getURIs().size() > 0) {
						uris.add(rc.getURIs().get(0).toPrivateString());
					}
					for (URIish u : rc.getPushURIs()) {
						uris.add(u.toPrivateString());
					}
				}

			}
		} catch (URISyntaxException e) {
			Activator.handleError(e.getMessage(), e, false);
			setErrorMessage(e.getMessage());
		}
		for (String aUri : uris) {
			uriCombo.add(aUri);
			changeRefs.put(aUri, new ChangeList(repository, aUri));
		}
		if (defaultUri != null) {
			uriCombo.setText(defaultUri);
		} else {
			selectLastUsedUri();
		}
		String currentUri = uriCombo.getText();
		ChangeList list = changeRefs.get(currentUri);
		if (list == null) {
			list = new ChangeList(repository, currentUri);
			changeRefs.put(currentUri, list);
		}
		preFetch(list);
		refText.setFocus();
		Dialog.applyDialogFont(main);
		setControl(main);
		if (candidateChange != null) {
			// Launch content assist when the page is displayed
			final IWizardContainer container = getContainer();
			if (container instanceof IPageChangeProvider) {
				((IPageChangeProvider) container)
						.addPageChangedListener(new IPageChangedListener() {
							@Override
							public void pageChanged(PageChangedEvent event) {
								if (event
										.getSelectedPage() == FetchGerritChangePage.this) {
									// Only the first time: remove myself
									event.getPageChangeProvider()
											.removePageChangedListener(this);
									getControl().getDisplay()
											.asyncExec(new Runnable() {
										@Override
										public void run() {
											Control control = getControl();
											if (control != null
													&& !control.isDisposed()) {
												contentProposer
														.openProposalPopup();
											}
										}
									});
								}
							}
						});
			}
		}
		checkPage();
	}

	private void preFetch(ChangeList list) {
		try {
			list.fetch();
		} catch (InvocationTargetException e) {
			Activator.handleError(e.getLocalizedMessage(), e.getCause(), true);
		}
	}

	/**
	 * Tries to determine a Gerrit change number from an input string.
	 *
	 * @param input
	 *            string to derive a change number from
	 * @return the change number as a string, or {@code null} if none could be
	 *         determined.
	 */
	protected static String determineChangeFromString(String input) {
		if (input == null) {
			return null;
		}
		Pattern pattern = Pattern.compile(
				"(?:https?://\\S+?/|/)?([1-9][0-9]*)(?:/([1-9][0-9]*)(?:/([1-9][0-9]*)(?:\\.\\.\\d+)?)?)?(?:/\\S*)?"); //$NON-NLS-1$
		Matcher matcher = pattern.matcher(input);
		if (matcher.matches()) {
			String first = matcher.group(1);
			String second = matcher.group(2);
			String third = matcher.group(3);
			if (second != null && !second.isEmpty()) {
				if (third != null && !third.isEmpty()) {
					return second;
				} else if (input.startsWith("http")) { //$NON-NLS-1$
					// A URL ending with two digits: take the first.
					return first;
				} else {
					// Take the numerically larger. Might be a fragment like
					// /10/65510 as in refs/changes/10/65510/6, or /65510/6 as
					// in https://git.eclipse.org/r/#/c/65510/6. This is a
					// heuristic, it might go wrong on a Gerrit where there are
					// not many changes (yet), and one of them has many patch
					// sets.
					try {
						if (Integer.parseInt(first) > Integer
								.parseInt(second)) {
							return first;
						} else {
							return second;
						}
					} catch (NumberFormatException e) {
						// Numerical overflow?
						return null;
					}
				}
			} else {
				return first;
			}
		}
		return null;
	}

	private void doPaste(Text text) {
		Clipboard clipboard = new Clipboard(text.getDisplay());
		try {
			String clipText = (String) clipboard
					.getContents(TextTransfer.getInstance());
			if (clipText != null) {
				String toInsert = determineChangeFromString(clipText.trim());
				if (toInsert != null) {
					clipboard.setContents(new Object[] { toInsert },
							new Transfer[] { TextTransfer.getInstance() });
					try {
						text.paste();
					} finally {
						clipboard.setContents(new Object[] { clipText },
								new Transfer[] { TextTransfer.getInstance() });
					}
				} else {
					text.paste();
				}
			}
		} finally {
			clipboard.dispose();
		}
	}

	private void storeLastUsedUri(String uri) {
		settings.put(lastUriKey, uri.trim());
	}

	private void selectLastUsedUri() {
		String lastUri = settings.get(lastUriKey);
		if (lastUri != null) {
			int i = uriCombo.indexOf(lastUri);
			if (i != -1) {
				uriCombo.select(i);
				return;
			}
		}
		uriCombo.select(0);
	}

	@Override
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		if (visible && refName != null)
			refText.setText(refName);
	}

	private void checkPage() {
		boolean createBranchSelected = createBranch.getSelection();
		branchText.setEnabled(createBranchSelected);
		branchText.setVisible(createBranchSelected);
		branchTextlabel.setVisible(createBranchSelected);
		branchEditButton.setVisible(createBranchSelected);
		branchCheckoutButton.setVisible(createBranchSelected);
		GridData gd = (GridData) branchText.getLayoutData();
		gd.exclude = !createBranchSelected;
		gd = (GridData) branchTextlabel.getLayoutData();
		gd.exclude = !createBranchSelected;
		gd = (GridData) branchEditButton.getLayoutData();
		gd.exclude = !createBranchSelected;
		gd = (GridData) branchCheckoutButton.getLayoutData();
		gd.exclude = !createBranchSelected;

		boolean createTagSelected = createTag.getSelection();
		tagText.setEnabled(createTagSelected);
		tagText.setVisible(createTagSelected);
		tagTextlabel.setVisible(createTagSelected);
		gd = (GridData) tagText.getLayoutData();
		gd.exclude = !createTagSelected;
		gd = (GridData) tagTextlabel.getLayoutData();
		gd.exclude = !createTagSelected;
		branchText.getParent().layout(true);

		boolean showActivateAdditionalRefs = false;
		showActivateAdditionalRefs = (checkoutFetchHead.getSelection() || updateFetchHead
				.getSelection())
				&& !Activator
						.getDefault()
						.getPreferenceStore()
						.getBoolean(
								UIPreferences.RESOURCEHISTORY_SHOW_ADDITIONAL_REFS);

		gd = (GridData) warningAdditionalRefNotActive.getLayoutData();
		gd.exclude = !showActivateAdditionalRefs;
		warningAdditionalRefNotActive.setVisible(showActivateAdditionalRefs);
		warningAdditionalRefNotActive.getParent().layout(true);

		setErrorMessage(null);
		try {
			if (refText.getText().length() > 0) {
				Change change = Change.fromRef(refText.getText());
				if (change == null) {
					setErrorMessage(UIText.FetchGerritChangePage_MissingChangeMessage);
					return;
				}
			} else {
				setErrorMessage(UIText.FetchGerritChangePage_MissingChangeMessage);
				return;
			}

			if (createBranchSelected)
				setErrorMessage(branchValidator.isValid(branchText.getText()));
			else if (createTagSelected)
				setErrorMessage(tagValidator.isValid(tagText.getText()));
		} finally {
			setPageComplete(getErrorMessage() == null);
		}
	}

	private List<Change> getRefsForContentAssist()
			throws InvocationTargetException, InterruptedException {
		String uriText = uriCombo.getText();
		if (!changeRefs.containsKey(uriText)) {
			changeRefs.put(uriText, new ChangeList(repository, uriText));
		}
		ChangeList list = changeRefs.get(uriText);
		if (!list.isDone()) {
			IWizardContainer container = getContainer();
			IRunnableWithProgress operation = monitor -> {
				monitor.beginTask(MessageFormat.format(
						UIText.FetchGerritChangePage_FetchingRemoteRefsMessage,
						uriText), IProgressMonitor.UNKNOWN);
				List<Change> result = list.get();
				if (monitor.isCanceled()) {
					return;
				}
				// If we get here, the ChangeList future is done.
				if (result == null || result.isEmpty()) {
					// Don't bother if we didn't get any results
					return;
				}
				// If we do have results now, open the proposals.
				Job showProposals = new WorkbenchJob(
						UIText.FetchGerritChangePage_ShowingProposalsJobName) {

					@Override
					public IStatus runInUIThread(IProgressMonitor uiMonitor) {
						// But only if we're not disposed, the focus is still
						// (or again) in the Change field, and the uri is still
						// the same
						try {
							if (container instanceof NonBlockingWizardDialog) {
								// Otherwise the dialog was blocked anyway, and
								// focus will be restored
								if (refText != refText.getDisplay()
										.getFocusControl()) {
									return Status.CANCEL_STATUS;
								}
								String uriNow = uriCombo.getText();
								if (!uriNow.equals(uriText)) {
									return Status.CANCEL_STATUS;
								}
							}
							contentProposer.openProposalPopup();
						} catch (SWTException e) {
							// Disposed already
							return Status.CANCEL_STATUS;
						} finally {
							uiMonitor.done();
						}
						return Status.OK_STATUS;
					}

				};
				showProposals.schedule();
			};
			if (container instanceof NonBlockingWizardDialog) {
				NonBlockingWizardDialog dialog = (NonBlockingWizardDialog) container;
				dialog.run(operation,
						() -> list.cancel(ChangeList.CancelMode.ABANDON));
			} else {
				container.run(true, true, operation);
			}
			return null;
		}
		return list.get();
	}

	boolean doFetch() {
		final RefSpec spec = new RefSpec().setSource(refText.getText())
				.setDestination(Constants.FETCH_HEAD);
		final String uri = uriCombo.getText();
		final CheckoutMode mode = getCheckoutMode();
		final boolean doCheckoutNewBranch = (mode == CheckoutMode.CREATE_BRANCH)
				&& branchCheckoutButton.getSelection();
		final boolean doActivateAdditionalRefs = showAdditionalRefs();
		final String textForTag = tagText.getText();
		final String textForBranch = branchText.getText();

		Job job = new WorkspaceJob(
				UIText.FetchGerritChangePage_GetChangeTaskName) {

			@Override
			public IStatus runInWorkspace(IProgressMonitor monitor) {
				try {
					SubMonitor progress = SubMonitor.convert(monitor,
							UIText.FetchGerritChangePage_GetChangeTaskName,
							getTotalWork(mode));
					RevCommit commit = fetchChange(uri, spec,
							progress.newChild(1));
					switch (mode) {
					case CHECKOUT_FETCH_HEAD:
						checkout(commit.name(), progress.newChild(1));
						break;
					case CREATE_TAG:
						createTag(spec, textForTag, commit,
								progress.newChild(1));
						checkout(commit.name(), progress.newChild(1));
						break;
					case CREATE_BRANCH:
						createBranch(textForBranch, doCheckoutNewBranch, commit,
								progress.newChild(1));
						break;
					default:
						break;
					}
					if (doActivateAdditionalRefs) {
						activateAdditionalRefs();
					}
					if (mode == CheckoutMode.NOCHECKOUT) {
						// Tell the world that FETCH_HEAD only changed. In other
						// cases, JGit will have sent a RefsChangeEvent
						// already.
						repository.fireEvent(new FetchHeadChangedEvent());
					}
					storeLastUsedUri(uri);
				} catch (CoreException ce) {
					return ce.getStatus();
				} catch (Exception e) {
					return Activator.createErrorStatus(e.getLocalizedMessage(),
							e);
				} finally {
					monitor.done();
				}
				return Status.OK_STATUS;
			}

			private int getTotalWork(final CheckoutMode m) {
				switch (m) {
				case CHECKOUT_FETCH_HEAD:
				case CREATE_BRANCH:
					return 2;
				case CREATE_TAG:
					return 3;
				default:
					return 1;
				}
			}

			@Override
			public boolean belongsTo(Object family) {
				if (JobFamilies.FETCH.equals(family))
					return true;
				return super.belongsTo(family);
			}
		};
		job.setUser(true);
		job.schedule();
		return true;
	}

	private boolean showAdditionalRefs() {
		return (checkoutFetchHead.getSelection()
				|| updateFetchHead.getSelection())
				&& activateAdditionalRefs.getSelection();
	}

	private CheckoutMode getCheckoutMode() {
		if (createBranch.getSelection()) {
			return CheckoutMode.CREATE_BRANCH;
		} else if (createTag.getSelection()) {
			return CheckoutMode.CREATE_TAG;
		} else if (checkoutFetchHead.getSelection()) {
			return CheckoutMode.CHECKOUT_FETCH_HEAD;
		} else {
			return CheckoutMode.NOCHECKOUT;
		}
	}

	private RevCommit fetchChange(String uri, RefSpec spec,
			IProgressMonitor monitor) throws CoreException, URISyntaxException,
			IOException {
		int timeout = Activator.getDefault().getPreferenceStore()
				.getInt(UIPreferences.REMOTE_CONNECTION_TIMEOUT);

		List<RefSpec> specs = new ArrayList<>(1);
		specs.add(spec);

		String taskName = NLS
				.bind(UIText.FetchGerritChangePage_FetchingTaskName,
						spec.getSource());
		monitor.subTask(taskName);
		FetchResult fetchRes = new FetchOperationUI(repository,
				new URIish(uri), specs, timeout, false).execute(monitor);

		monitor.worked(1);
		try (RevWalk rw = new RevWalk(repository)) {
			return rw.parseCommit(
					fetchRes.getAdvertisedRef(spec.getSource()).getObjectId());
		}
	}

	private void createTag(final RefSpec spec, final String textForTag,
			RevCommit commit, IProgressMonitor monitor) throws CoreException {
		monitor.subTask(UIText.FetchGerritChangePage_CreatingTagTaskName);
		final TagBuilder tag = new TagBuilder();
		PersonIdent personIdent = new PersonIdent(repository);

		tag.setTag(textForTag);
		tag.setTagger(personIdent);
		tag.setMessage(NLS.bind(
				UIText.FetchGerritChangePage_GeneratedTagMessage,
				spec.getSource()));
		tag.setObjectId(commit);
		new TagOperation(repository, tag, false).execute(monitor);
		monitor.worked(1);
	}

	private void createBranch(final String textForBranch, boolean doCheckout,
			RevCommit commit, IProgressMonitor monitor) throws CoreException {
		SubMonitor progress = SubMonitor.convert(monitor, doCheckout ? 10 : 2);
		progress.subTask(UIText.FetchGerritChangePage_CreatingBranchTaskName);
		CreateLocalBranchOperation bop = new CreateLocalBranchOperation(
				repository, textForBranch, commit);
		bop.execute(progress.newChild(2));
		if (doCheckout) {
			checkout(textForBranch, progress.newChild(8));
		}
	}

	private void checkout(String targetName, IProgressMonitor monitor)
			throws CoreException {
		monitor.subTask(UIText.FetchGerritChangePage_CheckingOutTaskName);
		BranchOperationUI.checkout(repository, targetName).run(monitor);
		monitor.worked(1);
	}

	private void activateAdditionalRefs() {
		// do this in the UI thread as it results in a
		// refresh() on the history page
		PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
			@Override
			public void run() {
				Activator
						.getDefault()
						.getPreferenceStore()
						.setValue(
								UIPreferences.RESOURCEHISTORY_SHOW_ADDITIONAL_REFS,
								true);
			}
		});
	}

	private ExplicitContentProposalAdapter addRefContentProposalToText(
			final Text textField) {
		KeyStroke stroke = UIUtils
				.getKeystrokeOfBestActiveBindingFor(IWorkbenchCommandConstants.EDIT_CONTENT_ASSIST);
		if (stroke != null) {
			UIUtils.addBulbDecorator(textField, NLS.bind(
					UIText.FetchGerritChangePage_ContentAssistTooltip,
					stroke.format()));
		}
		IContentProposalProvider cp = new IContentProposalProvider() {

			@Override
			public IContentProposal[] getProposals(String contents, int position) {
				List<Change> proposals;
				try {
					proposals = getRefsForContentAssist();
				} catch (InvocationTargetException e) {
					Activator.handleError(e.getMessage(), e, true);
					return null;
				} catch (InterruptedException e) {
					return null;
				}

				if (proposals == null) {
					return null;
				}
				List<IContentProposal> resultList = new ArrayList<>();
				Pattern pattern = UIUtils.createProposalPattern(contents);
				for (final Change ref : proposals) {
					if (pattern != null && !pattern
							.matcher(ref.getChangeNumber().toString())
							.matches()) {
						continue;
					}
					resultList.add(new ChangeContentProposal(ref));
				}
				return resultList
						.toArray(new IContentProposal[resultList.size()]);
			}
		};

		ExplicitContentProposalAdapter adapter = new ExplicitContentProposalAdapter(
				textField, cp, stroke);
		// set the acceptance style to always replace the complete content
		adapter.setProposalAcceptanceStyle(ContentProposalAdapter.PROPOSAL_REPLACE);
		return adapter;
	}

	private static class ExplicitContentProposalAdapter
			extends ContentProposalAdapter {

		public ExplicitContentProposalAdapter(Control control,
				IContentProposalProvider proposalProvider,
				KeyStroke keyStroke) {
			super(control, new TextContentAdapter(), proposalProvider,
					keyStroke, null);
		}

		@Override
		public void openProposalPopup() {
			// Make this method accessible
			super.openProposalPopup();
		}
	}

	private final static class Change implements Comparable<Change> {
		private final String refName;

		private final Integer changeNumber;

		private final Integer patchSetNumber;

		static Change fromRef(String refName) {
			try {
				if (refName == null || !refName.startsWith("refs/changes/")) //$NON-NLS-1$
					return null;
				String[] tokens = refName.substring(13).split("/"); //$NON-NLS-1$
				if (tokens.length != 3)
					return null;
				Integer changeNumber = Integer.valueOf(tokens[1]);
				Integer patchSetNumber = Integer.valueOf(tokens[2]);
				return new Change(refName, changeNumber, patchSetNumber);
			} catch (NumberFormatException e) {
				// if we can't parse this, just return null
				return null;
			} catch (IndexOutOfBoundsException e) {
				// if we can't parse this, just return null
				return null;
			}
		}

		private Change(String refName, Integer changeNumber,
				Integer patchSetNumber) {
			this.refName = refName;
			this.changeNumber = changeNumber;
			this.patchSetNumber = patchSetNumber;
		}

		public String getRefName() {
			return refName;
		}

		public Integer getChangeNumber() {
			return changeNumber;
		}

		public Integer getPatchSetNumber() {
			return patchSetNumber;
		}

		@Override
		public String toString() {
			return refName;
		}

		@Override
		public int compareTo(Change o) {
			int changeDiff = this.changeNumber.compareTo(o.changeNumber);
			if (changeDiff == 0) {
				changeDiff = this.getPatchSetNumber()
						.compareTo(o.getPatchSetNumber());
			}
			return changeDiff;
		}
	}

	private final static class ChangeContentProposal implements
			IContentProposal {
		private final Change myChange;

		ChangeContentProposal(Change change) {
			myChange = change;
		}

		@Override
		public String getContent() {
			return myChange.getRefName();
		}

		@Override
		public int getCursorPosition() {
			return 0;
		}

		@Override
		public String getDescription() {
			return NLS.bind(
					UIText.FetchGerritChangePage_ContentAssistDescription,
					myChange.getPatchSetNumber(), myChange.getChangeNumber());
		}

		@Override
		public String getLabel() {
			return NLS
					.bind("{0} - {1}", myChange.getChangeNumber(), myChange.getPatchSetNumber()); //$NON-NLS-1$
		}

		/* (non-Javadoc)
		 * @see java.lang.Object#toString()
		 */
		@Override
		public String toString() {
			return getContent();
		}
	}

	/**
	 * A {@code ChangeList} is a "Future", loading the list of change refs
	 * asynchronously from the remote repository. The {@link ChangeList#get()
	 * get()} method blocks until the result is available or the future is
	 * canceled. Pre-fetching is possible by calling {@link ChangeList#fetch()}
	 * directly.
	 */
	private static class ChangeList {

		/**
		 * Determines how to cancel a not-yet-completed future. Irrespective of
		 * the mechanism, the job may actually terminate normally, and
		 * subsequent calls to get() may return a result.
		 */
		public static enum CancelMode {
			/**
			 * Tries to cancel the job, which may decide to ignore the request.
			 * Callers to get() will remain blocked until the job terminates.
			 */
			CANCEL,
			/**
			 * Tries to cancel the job, which may decide to ignore the request.
			 * Outstanding get() calls will be woken up and may throw
			 * InterruptedException or return a result if the job terminated in
			 * the meantime.
			 */
			ABANDON,
			/**
			 * Tries to cancel the job, and if that doesn't succeed immediately,
			 * interrupts the job's thread. Outstanding calls to get() will be
			 * woken up and may throw InterruptedException or return a result if
			 * the job terminated in the meantime.
			 */
			INTERRUPT
		}

		private static enum State {
			PRISTINE, SCHEDULED, CANCELING, INTERRUPT, CANCELED, DONE
		}

		private final Repository repository;

		private final String uriText;

		private State state = State.PRISTINE;

		private List<Change> result;

		private InterruptibleJob job;

		public ChangeList(Repository repository, String uriText) {
			this.repository = repository;
			this.uriText = uriText;
		}

		/**
		 * Tries to cancel the future. {@code cancel(false)} tries a normal job
		 * cancellation, which may or may not terminated the job (it may decide
		 * not to react to cancellation requests).
		 *
		 * @param cancellation
		 *            {@link CancelMode} defining how to cancel
		 *
		 * @return {@code true} if the future was canceled (its job is not
		 *         running anymore), {@code false} otherwise.
		 */
		public synchronized boolean cancel(CancelMode cancellation) {
			CancelMode mode = cancellation == null ? CancelMode.CANCEL
					: cancellation;
			switch (state) {
			case PRISTINE:
				finish(false);
				return true;
			case SCHEDULED:
				state = State.CANCELING;
				boolean canceled = job.cancel();
				if (canceled) {
					state = State.CANCELED;
				} else if (mode == CancelMode.INTERRUPT) {
					interrupt();
				} else if (mode == CancelMode.ABANDON) {
					notifyAll();
				}
				return canceled;
			case CANCELING:
				// cancel(CANCEL|ABANDON) was called before.
				if (mode == CancelMode.INTERRUPT) {
					interrupt();
				} else if (mode == CancelMode.ABANDON) {
					notifyAll();
				}
				return false;
			case INTERRUPT:
				if (mode != CancelMode.CANCEL) {
					notifyAll();
				}
				return false;
			case CANCELED:
				return true;
			default:
				return false;
			}
		}

		public synchronized boolean isDone() {
			return state == State.CANCELED || state == State.DONE;
		}

		/**
		 * Retrieves the result. If the result is not yet available, the method
		 * blocks until it is or {@link #cancel(CancelMode)} is called with
		 * {@link CancelMode#ABANDON} or {@link CancelMode#INTERRUPT}.
		 *
		 * @return the result, which may be {@code null} if the future was
		 *         canceled
		 * @throws InterruptedException
		 *             if waiting was interrupted
		 * @throws InvocationTargetException
		 *             if the future's job cannot be created
		 */
		public synchronized List<Change> get()
				throws InterruptedException, InvocationTargetException {
			switch (state) {
			case DONE:
			case CANCELED:
				return result;
			case PRISTINE:
				fetch();
				return get();
			default:
				wait();
				if (state == State.CANCELING || state == State.INTERRUPT) {
					// canceled with ABANDON or INTERRUPT
					throw new InterruptedException();
				}
				return get();
			}
		}

		private synchronized void finish(boolean done) {
			state = done ? State.DONE : State.CANCELED;
			job = null;
			notifyAll(); // We're done, wake up all outstanding get() calls
		}

		private synchronized void interrupt() {
			state = State.INTERRUPT;
			job.interrupt();
			notifyAll(); // Abandon outstanding get() calls
		}

		/**
		 * On the first call, starts a background job to fetch the result.
		 * Subsequent calls do nothing and return immediately.
		 *
		 * @throws InvocationTargetException
		 *             if starting the job fails
		 */
		public synchronized void fetch() throws InvocationTargetException {
			if (job != null || state != State.PRISTINE) {
				return;
			}
			ListRemoteOperation listOp;
			try {
				listOp = new ListRemoteOperation(repository,
						new URIish(uriText),
						Activator.getDefault().getPreferenceStore().getInt(
								UIPreferences.REMOTE_CONNECTION_TIMEOUT));
			} catch (URISyntaxException e) {
				finish(false);
				throw new InvocationTargetException(e);
			}
			job = new InterruptibleJob(MessageFormat.format(
					UIText.FetchGerritChangePage_FetchingRemoteRefsMessage,
					uriText)) {

				@Override
				protected IStatus run(IProgressMonitor monitor) {
					try {
						listOp.run(monitor);
					} catch (InterruptedException e) {
						return Status.CANCEL_STATUS;
					} catch (InvocationTargetException e) {
						synchronized (ChangeList.this) {
							if (state == State.CANCELING
									|| state == State.INTERRUPT) {
								// JGit may report a TransportException when the
								// thread is interrupted. Let's just pretend we
								// canceled before. Also, if the user canceled
								// already, he's not interested in errors
								// anymore.
								return Status.CANCEL_STATUS;
							}
						}
						return Activator
								.createErrorStatus(e.getLocalizedMessage(), e);
					}
					List<Change> changes = new ArrayList<>();
					for (Ref ref : listOp.getRemoteRefs()) {
						Change change = Change.fromRef(ref.getName());
						if (change != null) {
							changes.add(change);
						}
					}
					Collections.sort(changes, Collections.reverseOrder());
					result = changes;
					return Status.OK_STATUS;
				}

			};
			job.addJobChangeListener(new JobChangeAdapter() {

				@Override
				public void done(IJobChangeEvent event) {
					IStatus status = event.getResult();
					finish(status != null && status.isOK());
				}

			});
			job.setUser(false);
			job.setSystem(true);
			state = State.SCHEDULED;
			job.schedule();
		}

		private static abstract class InterruptibleJob extends Job {

			public InterruptibleJob(String name) {
				super(name);
			}

			public void interrupt() {
				Thread thread = getThread();
				if (thread != null) {
					thread.interrupt();
				}
			}
		}
	}
}
