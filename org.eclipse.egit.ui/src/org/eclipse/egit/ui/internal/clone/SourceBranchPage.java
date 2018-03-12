/*******************************************************************************
 * Copyright (C) 2007, Dave Watson <dwatson@mimvista.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2008, Marek Zawirski <marek.zawirski@gmail.com>
 * Copyright (C) 2010, Mathias Kinzler <mathias.kinzler@sap.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.clone;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.egit.core.op.ListRemoteOperation;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.internal.components.RepositorySelection;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;

class SourceBranchPage extends WizardPage {

	private RepositorySelection validatedRepoSelection;

	private Ref head;

	private final List<Ref> availableRefs = new ArrayList<Ref>();

	private List<Ref> selectedRefs = new ArrayList<Ref>();

	private Label label;

	private Table refsTable;

	private String transportError;

	private Button selectB;

	private Button unselectB;

	SourceBranchPage() {
		super(SourceBranchPage.class.getName());
		setTitle(UIText.SourceBranchPage_title);
		setDescription(UIText.SourceBranchPage_description);
	}

	List<Ref> getSelectedBranches() {
		return new ArrayList<Ref>(selectedRefs);
	}

	List<Ref> getAvailableBranches() {
		return availableRefs;
	}

	Ref getHEAD() {
		return head;
	}

	boolean isSourceRepoEmpty() {
		return availableRefs.isEmpty();
	}

	boolean isAllSelected() {
		return availableRefs.size() == selectedRefs.size();
	}

	boolean selectionEquals(final List<Ref> actSelectedRef, final Ref actHead) {
		return this.selectedRefs.equals(actSelectedRef) && this.head == actHead;
	}

	public void createControl(final Composite parent) {
		final Composite panel = new Composite(parent, SWT.NULL);
		final GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		panel.setLayout(layout);

		label = new Label(panel, SWT.NONE);
		label.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		refsTable = new Table(panel, SWT.CHECK | SWT.V_SCROLL | SWT.BORDER);
		refsTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		refsTable.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(final SelectionEvent e) {
				if (e.detail != SWT.CHECK)
					return;

				final TableItem tableItem = (TableItem) e.item;
				final int i = refsTable.indexOf(tableItem);
				final Ref ref = availableRefs.get(i);

				if (tableItem.getChecked()) {
					int insertionPos = 0;
					for (int j = 0; j < i; j++) {
						if (selectedRefs.contains(availableRefs.get(j)))
							insertionPos++;
					}
					selectedRefs.add(insertionPos, ref);
				} else
					selectedRefs.remove(ref);

				checkPage();
			}
		});

		final Composite bPanel = new Composite(panel, SWT.NONE);
		bPanel.setLayout(new RowLayout());
		selectB = new Button(bPanel, SWT.PUSH);
		selectB.setText(UIText.SourceBranchPage_selectAll);
		selectB.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(final SelectionEvent e) {
				for (int i = 0; i < refsTable.getItemCount(); i++)
					refsTable.getItem(i).setChecked(true);
				selectedRefs.clear();
				selectedRefs.addAll(availableRefs);
				checkPage();
			}
		});
		unselectB = new Button(bPanel, SWT.PUSH);
		unselectB.setText(UIText.SourceBranchPage_selectNone);
		unselectB.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(final SelectionEvent e) {
				for (int i = 0; i < refsTable.getItemCount(); i++)
					refsTable.getItem(i).setChecked(false);
				selectedRefs.clear();
				checkPage();
			}
		});
		bPanel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		Dialog.applyDialogFont(panel);
		setControl(panel);
		checkPage();
	}

	public void setSelection(RepositorySelection selection){
		revalidate(selection);
	}

	/**
	 * Check internal state for page completion status. This method should be
	 * called only when all necessary data from previous form is available.
	 */
	private void checkPage() {
		setMessage(null);
		selectB.setEnabled(selectedRefs.size() != availableRefs.size());
		unselectB.setEnabled(selectedRefs.size() != 0);
		if (transportError != null) {
			setErrorMessage(transportError);
			setPageComplete(false);
			return;
		}

		if (isSourceRepoEmpty()) {
			setMessage(UIText.SourceBranchPage_repoEmpty, IMessageProvider.WARNING);
			setPageComplete(true);
			return;
		}

		if ( getSelectedBranches().isEmpty()) {
			setErrorMessage(UIText.SourceBranchPage_errorBranchRequired);
			setPageComplete(false);
			return;
		}
		setErrorMessage(null);
		setPageComplete(true);
	}

	private void revalidate(final RepositorySelection newRepoSelection) {
		if (newRepoSelection.equals(validatedRepoSelection)) {
			// URI hasn't changed, no need to refill the page with new data
			checkPage();
			return;
		}

		label.setText(NLS.bind(UIText.SourceBranchPage_branchList,
				newRepoSelection.getURI().toString()));
		label.getParent().layout();

		validatedRepoSelection = null;
		transportError = null;
		head = null;
		availableRefs.clear();
		selectedRefs.clear();
		refsTable.removeAll();
		setPageComplete(false);
		setErrorMessage(null);
		label.getDisplay().asyncExec(new Runnable() {
			public void run() {
				revalidateImpl(newRepoSelection);
			}
		});
	}

	private void revalidateImpl(final RepositorySelection newRepoSelection) {
		if (label.isDisposed() || !isCurrentPage())
			return;

		final ListRemoteOperation listRemoteOp;
		try {
			final URIish uri = newRepoSelection.getURI();
			final Repository db = new FileRepository(new File("/tmp")); //$NON-NLS-1$
			listRemoteOp = new ListRemoteOperation(db, uri);
			getContainer().run(true, true, new IRunnableWithProgress() {
				public void run(IProgressMonitor monitor)
						throws InvocationTargetException, InterruptedException {
					listRemoteOp.run(monitor);
				}
			});
		} catch (InvocationTargetException e) {
			Throwable why = e.getCause();
			transportError(why.getMessage());
			ErrorDialog.openError(getShell(),
					UIText.SourceBranchPage_transportError,
					UIText.SourceBranchPage_cannotListBranches, new Status(
							IStatus.ERROR, Activator.getPluginId(), 0, why
									.getMessage(), why.getCause()));
			return;
		} catch (IOException e) {
			transportError(UIText.SourceBranchPage_cannotCreateTemp);
			return;
		} catch (InterruptedException e) {
			transportError(UIText.SourceBranchPage_remoteListingCancelled);
			return;
		}

		final Ref idHEAD = listRemoteOp.getRemoteRef(Constants.HEAD);
		head = null;
		for (final Ref r : listRemoteOp.getRemoteRefs()) {
			final String n = r.getName();
			if (!n.startsWith(Constants.R_HEADS))
				continue;
			availableRefs.add(r);
			if (idHEAD == null || head != null)
				continue;
			if (r.getObjectId().equals(idHEAD.getObjectId()))
				head = r;
		}
		Collections.sort(availableRefs, new Comparator<Ref>() {
			public int compare(final Ref o1, final Ref o2) {
				return o1.getName().compareTo(o2.getName());
			}
		});
		if (idHEAD != null && head == null) {
			head = idHEAD;
			availableRefs.add(0, idHEAD);
		}

		validatedRepoSelection = newRepoSelection;
		for (final Ref r : availableRefs) {
			String n = r.getName();
			if (n.startsWith(Constants.R_HEADS))
				n = n.substring(Constants.R_HEADS.length());
			final TableItem ti = new TableItem(refsTable, SWT.NONE);
			ti.setText(n);
			ti.setChecked(true);
			selectedRefs.add(r);
		}
		checkPage();
	}

	private void transportError(final String msg) {
		transportError = msg;
		checkPage();
	}
}
