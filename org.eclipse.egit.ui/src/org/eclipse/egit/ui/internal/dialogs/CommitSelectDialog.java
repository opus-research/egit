package org.eclipse.egit.ui.internal.dialogs;

import java.util.List;

import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.internal.history.GraphLabelProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

/**
 * Allows to select a single commit from a list of {@link RevCommit}s
 */
public class CommitSelectDialog extends TitleAreaDialog {
	private final List<RevCommit> commits;

	private RevCommit selected;

	/**
	 * @param parent
	 * @param commits
	 **/
	public CommitSelectDialog(Shell parent, List<RevCommit> commits) {
		super(parent);
		setShellStyle(getShellStyle() | SWT.SHELL_TRIM);
		this.commits = commits;
		setHelpAvailable(false);
	}

	/**
	 * @return the selected commit
	 */
	public RevCommit getSelectedCommit() {
		return selected;
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite main = new Composite(parent, SWT.NONE);
		GridLayoutFactory.fillDefaults().applyTo(main);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(main);
		TableViewer tv = new TableViewer(main, SWT.SINGLE | SWT.BORDER
				| SWT.FULL_SELECTION);
		GridDataFactory.fillDefaults().grab(true, true)
				.applyTo(tv.getControl());
		tv.setContentProvider(ArrayContentProvider.getInstance());
		tv.setLabelProvider(new GraphLabelProvider());
		Table table = tv.getTable();
		TableColumn c1 = new TableColumn(table, SWT.NONE);
		c1.setWidth(200);
		c1.setText(UIText.CommitSelectDialog_MessageColumn);
		TableColumn c2 = new TableColumn(table, SWT.NONE);
		c2.setWidth(200);
		c2.setText(UIText.CommitSelectDialog_AuthoColumn);
		TableColumn c3 = new TableColumn(table, SWT.NONE);
		c3.setWidth(150);
		c3.setText(UIText.CommitSelectDialog_DateColumn);
		TableColumn c4 = new TableColumn(table, SWT.NONE);
		c4.setWidth(100);
		c4.setText(UIText.CommitSelectDialog_IdColumn);
		tv.setInput(commits);
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		tv.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				if (!event.getSelection().isEmpty())
					selected = (RevCommit) ((IStructuredSelection) event
							.getSelection()).getFirstElement();
				else
					selected = null;
				getButton(OK).setEnabled(selected != null);
			}
		});
		return main;
	}

	@Override
	public void create() {
		super.create();
		setTitle(UIText.CommitSelectDialog_Title);
		setMessage(UIText.CommitSelectDialog_Message);
		getButton(OK).setEnabled(false);
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText(UIText.CommitSelectDialog_WindowTitle);
	}
}
