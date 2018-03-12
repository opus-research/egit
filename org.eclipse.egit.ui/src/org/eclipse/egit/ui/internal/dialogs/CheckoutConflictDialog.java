package org.eclipse.egit.ui.internal.dialogs;

import java.util.List;

import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.internal.CommonUtils;
import org.eclipse.egit.ui.internal.repository.tree.RepositoryNode;
import org.eclipse.egit.ui.internal.repository.tree.command.CommitCommand;
import org.eclipse.egit.ui.internal.repository.tree.command.ResetCommand;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

/**
 * Display a checkout conflict
 */
public class CheckoutConflictDialog extends MessageDialog {
	private static final Image INFO = PlatformUI.getWorkbench()
			.getSharedImages().getImage(ISharedImages.IMG_OBJS_INFO_TSK);
	private List<String> conflicts;
	private Repository repository;

	/**
	 * @param shell
	 * @param repository
	 * @param conflicts
	 */
	public CheckoutConflictDialog(Shell shell, Repository repository, List<String> conflicts) {
		super(shell, UIText.BranchResultDialog_CheckoutConflictsTitle, INFO,
				UIText.CheckoutConflictDialog_conflictMessage,
				MessageDialog.INFORMATION,
				new String[] { IDialogConstants.OK_LABEL }, 0);
		setShellStyle(getShellStyle() | SWT.SHELL_TRIM);
		this.repository = repository;
		this.conflicts = conflicts;
	}

	@Override
	protected Control createCustomArea(Composite parent) {
		Composite main = new Composite(parent, SWT.NONE);
		main.setLayout(new GridLayout(1, false));
		GridDataFactory.fillDefaults().indent(0, 0).grab(true, true)
				.applyTo(main);
		new NonDeletedFilesTree(main, repository, this.conflicts);
		applyDialogFont(main);

		return main;
	}

	protected void buttonPressed(int buttonId) {
		switch (buttonId) {
		case IDialogConstants.PROCEED_ID:
			CommonUtils.runCommand(CommitCommand.ID, new StructuredSelection(
					new RepositoryNode(null, repository)));
			break;
		case IDialogConstants.ABORT_ID:
			CommonUtils.runCommand(ResetCommand.ID, new StructuredSelection(
					new RepositoryNode(null, repository)));
			break;
		}
		super.buttonPressed(buttonId);
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent) {
		super.createButtonsForButtonBar(parent);
		createButton(parent, IDialogConstants.ABORT_ID,
				UIText.BranchResultDialog_buttonReset, false);
		createButton(parent, IDialogConstants.PROCEED_ID,
				UIText.BranchResultDialog_buttonCommit, false);
	}
}
