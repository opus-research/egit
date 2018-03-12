/*******************************************************************************
 * Copyright (c) 2010, 2011 SAP AG and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Stefan Lay (SAP AG) - initial implementation
 *    Daniel Megert <daniel_megert@ch.ibm.com> - Create Patch... dialog should not set file location - http://bugs.eclipse.org/361405
 *    Tomasz Zarna <Tomasz.Zarna@pl.ibm.com> - Allow to save patches in Workspace
 *******************************************************************************/
package org.eclipse.egit.ui.internal.history;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.egit.core.op.CreatePatchOperation;
import org.eclipse.egit.core.op.CreatePatchOperation.DiffHeaderFormat;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIIcons;
import org.eclipse.egit.ui.UIText;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * A wizard for creating a patch file by running the git diff command.
 */
public class GitCreatePatchWizard extends Wizard {

	private RevCommit commit;

	private Repository db;

	private LocationPage locationPage;

	private OptionsPage optionsPage;

	// The initial size of this wizard.
	private final static int INITIAL_WIDTH = 300;

	private final static int INITIAL_HEIGHT = 150;

	/**
	 *
	 * @param shell
	 * @param commit
	 * @param db
	 */
	public static void run(Shell shell, final RevCommit commit,
			Repository db) {
		final String title = UIText.GitCreatePatchWizard_CreatePatchTitle;
		final GitCreatePatchWizard wizard = new GitCreatePatchWizard(commit, db);
		wizard.setWindowTitle(title);
		WizardDialog dialog = new WizardDialog(shell, wizard);
		dialog.setMinimumPageSize(INITIAL_WIDTH, INITIAL_HEIGHT);
		dialog.setHelpAvailable(false);
		dialog.open();
	}

	/**
	 * Creates a wizard which is used to export the changes introduced by a
	 * commit.
	 *
	 * @param commit
	 * @param db
	 */
	public GitCreatePatchWizard(RevCommit commit, Repository db) {
		this.commit = commit;
		this.db = db;

		setDialogSettings(getOrCreateSection(Activator.getDefault().getDialogSettings(), "GitCreatePatchWizard")); //$NON-NLS-1$
	}

	/*
	 * Copy of org.eclipse.jface.dialogs.DialogSettings.getOrCreateSection(IDialogSettings, String)
	 * which is not available in 3.5.
	 */
	private static IDialogSettings getOrCreateSection(IDialogSettings settings,
			String sectionName) {
		IDialogSettings section = settings.getSection(sectionName);
		if (section == null)
			section = settings.addNewSection(sectionName);
		return section;
	}

	@Override
	public void addPages() {
		String pageTitle = UIText.GitCreatePatchWizard_SelectLocationTitle;
		String pageDescription = UIText.GitCreatePatchWizard_SelectLocationDescription;

		locationPage = new LocationPage(pageTitle, pageTitle, UIIcons.WIZBAN_CREATE_PATCH);
		locationPage.setDescription(pageDescription);
		addPage(locationPage);

		pageTitle = UIText.GitCreatePatchWizard_SelectOptionsTitle;
		pageDescription = UIText.GitCreatePatchWizard_SelectOptionsDescription;
		optionsPage = new OptionsPage(pageTitle, pageTitle, UIIcons.WIZBAN_CREATE_PATCH);
		optionsPage.setDescription(pageDescription);
		addPage(optionsPage);
	}

	@Override
	public boolean performFinish() {
		final CreatePatchOperation operation = new CreatePatchOperation(db,
				commit);
		operation.setHeaderFormat(optionsPage.getSelectedHeaderFormat());
		operation.setContextLines(Integer.parseInt(optionsPage.contextLines.getText()));

		final File file = !locationPage.fsRadio.getSelection() ? locationPage
				.getFile() : null;

		if (!(file == null || validateFile(file)))
			return false;

		try {
			getContainer().run(true, true, new IRunnableWithProgress() {
				public void run(IProgressMonitor monitor)
						throws InvocationTargetException {
					try {
						operation.execute(monitor);

						String content = operation.getPatchContent();
						if (file != null)
							writeToFile(file, content);
						else
							copyToClipboard(content);
					} catch (IOException e) {
						throw new InvocationTargetException(e);
					} catch (CoreException e) {
						throw new InvocationTargetException(e);
					}
				}
			});
		} catch (InvocationTargetException e) {
			((WizardPage) getContainer().getCurrentPage()).setErrorMessage(e
					.getMessage() == null ? e.getMessage()
					: UIText.GitCreatePatchWizard_InternalError);
			Activator.logError("Patch file was not written", e); //$NON-NLS-1$
			return false;
		} catch (InterruptedException e) {
			Activator.logError("Patch file was not written", e); //$NON-NLS-1$
			return false;
		}
		return true;
	}

	private void copyToClipboard(final String content) {
		getShell().getDisplay().syncExec(new Runnable() {
			public void run() {
				TextTransfer plainTextTransfer = TextTransfer.getInstance();
				Clipboard clipboard = new Clipboard(getShell().getDisplay());
				clipboard.setContents(new String[] { content },
						new Transfer[] { plainTextTransfer });
				clipboard.dispose();
			}
		});
	}

	private void writeToFile(final File file, String content)
			throws IOException {
		Writer output = new BufferedWriter(new FileWriter(file));
		try {
			// FileWriter always assumes default encoding is
			// OK!
			output.write(content);
		} finally {
			output.close();
		}
	}

	private boolean validateFile(File file) {
		if (file == null)
			return false;

		// Consider file valid if it doesn't exist for now.
		if (!file.exists())
			return true;

		// The file exists.
		if (!file.canWrite()) {
			final String title= UIText.GitCreatePatchWizard_ReadOnlyTitle;
			final String msg= UIText.GitCreatePatchWizard_ReadOnlyMsg;
			final MessageDialog dialog= new MessageDialog(getShell(), title, null, msg, MessageDialog.ERROR, new String[] { IDialogConstants.OK_LABEL }, 0);
			dialog.open();
			return false;
		}

		final String title = UIText.GitCreatePatchWizard_OverwriteTitle;
		final String msg = UIText.GitCreatePatchWizard_OverwriteMsg;
		final MessageDialog dialog = new MessageDialog(getShell(), title, null, msg, MessageDialog.QUESTION, new String[] { IDialogConstants.YES_LABEL, IDialogConstants.CANCEL_LABEL }, 0);
		dialog.open();
		if (dialog.getReturnCode() != 0)
			return false;

		return true;
	}

	/**
	 *
	 * A wizard Page used to specify options of the created patch
	 */
	public class OptionsPage extends WizardPage {
		private Label formatLabel;
		private ComboViewer formatCombo;
		private Text contextLines;
		private Label contextLinesLabel;

		/**
		 *
		 * @param pageName
		 * @param title
		 * @param titleImage
		 */
		protected OptionsPage(String pageName, String title,
				ImageDescriptor titleImage) {
			super(pageName, title, titleImage);
		}

		public void createControl(Composite parent) {
			final Composite composite = new Composite(parent, SWT.NULL);
			GridLayout gridLayout = new GridLayout(2, false);
			composite.setLayout(gridLayout);
			composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

			GridData gd = new GridData(GridData.HORIZONTAL_ALIGN_BEGINNING);
			gd.horizontalSpan = 2;

			formatLabel = new Label(composite, SWT.NONE);
			formatLabel.setText(UIText.GitCreatePatchWizard_Format);

			formatCombo = new ComboViewer(composite, SWT.DROP_DOWN | SWT.READ_ONLY);
			formatCombo.getCombo().setLayoutData(new GridData(SWT.FILL, SWT.DEFAULT, true, false));
			formatCombo.setContentProvider(ArrayContentProvider.getInstance());
			formatCombo.setLabelProvider(new LabelProvider() {
				@Override
				public String getText(Object element) {
					return ((DiffHeaderFormat) element).getDescription();
				}
			});
			formatCombo.setInput(DiffHeaderFormat.values());
			formatCombo.setFilters(new ViewerFilter[] { new ViewerFilter() {
				@Override
				public boolean select(Viewer viewer, Object parentElement,
						Object element) {
					return commit != null
							|| !((DiffHeaderFormat) element).isCommitRequired();
				}
			}});
			formatCombo.setSelection(new StructuredSelection(DiffHeaderFormat.NONE));

			contextLinesLabel = new Label(composite, SWT.NONE);
			contextLinesLabel.setText(UIText.GitCreatePatchWizard_LinesOfContext);

			contextLines = new Text(composite, SWT.BORDER | SWT.RIGHT);
			contextLines.setText(String.valueOf(CreatePatchOperation.DEFAULT_CONTEXT_LINES));
			contextLines.addModifyListener(new ModifyListener() {

				public void modifyText(ModifyEvent e) {
					validatePage();
				}
			});
			GridDataFactory.swtDefaults().hint(30, SWT.DEFAULT).applyTo(contextLines);

			Dialog.applyDialogFont(composite);
			setControl(composite);
		}

		private void validatePage() {
			boolean pageValid = true;
			pageValid = validateContextLines();
			if (pageValid) {
				setMessage(null);
				setErrorMessage(null);
			}
			setPageComplete(pageValid);
		}

		private boolean validateContextLines() {
			String text = contextLines.getText();
			if(text == null || text.trim().length() == 0) {
				setErrorMessage(UIText.GitCreatePatchWizard_ContextMustBePositiveInt);
				return false;
			}

			text = text.trim();

			char[] charArray = text.toCharArray();
			for (char c : charArray)
				if(!Character.isDigit(c)) {
					setErrorMessage(UIText.GitCreatePatchWizard_ContextMustBePositiveInt);
					return false;
				}
			return true;
		}

		DiffHeaderFormat getSelectedHeaderFormat() {
			IStructuredSelection selection = (IStructuredSelection) formatCombo
					.getSelection();
			return (DiffHeaderFormat) selection.getFirstElement();
		}
	}

	Repository getRepository() {
		return db;
	}

	RevCommit getCommit() {
		return commit;
	}
}