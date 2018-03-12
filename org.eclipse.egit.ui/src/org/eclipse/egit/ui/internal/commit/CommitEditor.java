/*******************************************************************************
 *  Copyright (c) 2011, 2012 GitHub Inc. and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *    Kevin Sawicki (GitHub Inc.) - initial API and implementation
 *    Daniel Megert <daniel_megert@ch.ibm.com> - Added context menu to the Commit Editor's header text
 *    Tomasz Zarna <Tomasz.Zarna@pl.ibm.com> - Add "Revert" action to Commit Editor
 *******************************************************************************/
package org.eclipse.egit.ui.internal.commit;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.internal.commit.command.CheckoutHandler;
import org.eclipse.egit.ui.internal.commit.command.CreateBranchHandler;
import org.eclipse.egit.ui.internal.commit.command.CreateTagHandler;
import org.eclipse.egit.ui.internal.commit.command.CherryPickHandler;
import org.eclipse.egit.ui.internal.commit.command.RevertHandler;
import org.eclipse.egit.ui.internal.repository.RepositoriesView;
import org.eclipse.jface.action.ContributionManager;
import org.eclipse.jface.action.ControlContribution;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jgit.events.ListenerHandle;
import org.eclipse.jgit.events.RefsChangedEvent;
import org.eclipse.jgit.events.RefsChangedListener;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.IFormColors;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.editor.SharedHeaderFormEditor;
import org.eclipse.ui.forms.events.HyperlinkAdapter;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ImageHyperlink;
import org.eclipse.ui.forms.widgets.ScrolledForm;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.menus.CommandContributionItem;
import org.eclipse.ui.menus.CommandContributionItemParameter;
import org.eclipse.ui.menus.IMenuService;
import org.eclipse.ui.progress.UIJob;

/**
 * Editor class to view a commit in a form editor.
 */
public class CommitEditor extends SharedHeaderFormEditor implements
		RefsChangedListener {

	/**
	 * ID - editor id
	 */
	public static final String ID = "org.eclipse.egit.ui.commitEditor"; //$NON-NLS-1$

	private static final String TOOLBAR_HEADER_ID = ID + ".header.toolbar"; //$NON-NLS-1$

	/**
	 * Open commit in editor
	 *
	 * @param commit
	 * @return opened editor part
	 * @throws PartInitException
	 */
	public static final IEditorPart open(RepositoryCommit commit)
			throws PartInitException {
		return open(commit, true);
	}

	/**
	 * Open commit in editor
	 *
	 * @param commit
	 * @param activateOnOpen <code>true</code> if the newly opened editor should be activated
	 * @return opened editor part
	 * @throws PartInitException
	 * @since 2.1
	 */
	public static final IEditorPart open(RepositoryCommit commit, boolean activateOnOpen)
			throws PartInitException {
		CommitEditorInput input = new CommitEditorInput(commit);
		return IDE.openEditor(PlatformUI.getWorkbench()
				.getActiveWorkbenchWindow().getActivePage(), input, ID, activateOnOpen);
	}

	/**
	 * Open commit in editor
	 *
	 * @param commit
	 * @param activateOnOpen <code>true</code> if the newly opened editor should be activated
	 * @return opened editor part or null if opening fails
	 * @since 2.1
	 */
	public static final IEditorPart openQuiet(RepositoryCommit commit, boolean activateOnOpen) {
		try {
			return open(commit, activateOnOpen);
		} catch (PartInitException e) {
			Activator.logError(e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Open commit in editor
	 *
	 * @param commit
	 * @return opened editor part or null if opening fails
	 */
	public static final IEditorPart openQuiet(RepositoryCommit commit) {
		return openQuiet(commit, true);
	}

	private CommitEditorPage commitPage;

	private DiffEditorPage diffPage;

	private NotesEditorPage notePage;

	private ListenerHandle refListenerHandle;

	/**
	 * @see org.eclipse.ui.forms.editor.FormEditor#addPages()
	 */
	protected void addPages() {
		try {
			commitPage = new CommitEditorPage(this);
			addPage(commitPage);
			if (getCommit().getRevCommit().getParentCount() <= 1) {
				diffPage = new DiffEditorPage(this);
				addPage(diffPage);
			}
			if (getCommit().getNotes().length > 0) {
				notePage = new NotesEditorPage(this);
				addPage(notePage);
			}
		} catch (PartInitException e) {
			Activator.error("Error adding page", e); //$NON-NLS-1$
		}
		refListenerHandle = Repository.getGlobalListenerList()
				.addRefsChangedListener(this);
	}

	private CommandContributionItem createCommandContributionItem(
			String commandId) {
		CommandContributionItemParameter parameter = new CommandContributionItemParameter(
				getSite(), commandId, commandId,
				CommandContributionItem.STYLE_PUSH);
		return new CommandContributionItem(parameter);
	}

	/**
	 * @see org.eclipse.ui.forms.editor.SharedHeaderFormEditor#createHeaderContents(org.eclipse.ui.forms.IManagedForm)
	 */
	protected void createHeaderContents(IManagedForm headerForm) {
		RepositoryCommit commit = getCommit();
		ScrolledForm form = headerForm.getForm();
		new HeaderText(form.getForm(), commit.getRevCommit().name());
		form.setToolTipText(commit.getRevCommit().name());
		getToolkit().decorateFormHeading(form.getForm());

		IToolBarManager toolbar = form.getToolBarManager();

		ControlContribution repositoryLabelControl = new ControlContribution(
				"repositoryLabel") { //$NON-NLS-1$
			@Override
			protected Control createControl(Composite parent) {
				FormToolkit toolkit = getHeaderForm().getToolkit();
				Composite composite = toolkit.createComposite(parent);
				RowLayout layout = new RowLayout();
				composite.setLayout(layout);
				composite.setBackground(null);
				String label = getCommit().getRepositoryName();

				ImageHyperlink link = new ImageHyperlink(composite, SWT.NONE);
				link.setText(label);
				link.setFont(JFaceResources.getBannerFont());
				link.setForeground(toolkit.getColors().getColor(
						IFormColors.TITLE));
				link.setToolTipText(UIText.CommitEditor_showGitRepo);
				link.addHyperlinkListener(new HyperlinkAdapter() {
					@Override
					public void linkActivated(HyperlinkEvent event) {
						RepositoriesView view;
						try {
							view = (RepositoriesView) PlatformUI.getWorkbench()
									.getActiveWorkbenchWindow().getActivePage()
									.showView(RepositoriesView.VIEW_ID);
							view.showRepository(getCommit().getRepository());
						} catch (PartInitException e) {
							Activator.handleError(
									UIText.CommitEditor_couldNotShowRepository,
									e, false);
						}
					}
				});

				return composite;
			}
		};
		toolbar.add(repositoryLabelControl);
		toolbar.add(createCommandContributionItem(CreateTagHandler.ID));
		toolbar.add(createCommandContributionItem(CreateBranchHandler.ID));
		toolbar.add(createCommandContributionItem(CheckoutHandler.ID));
		toolbar.add(createCommandContributionItem(CherryPickHandler.ID));
		toolbar.add(createCommandContributionItem(RevertHandler.ID));
		addContributions(toolbar);
		toolbar.update(true);
		getSite().setSelectionProvider(new ISelectionProvider() {

			public void setSelection(ISelection selection) {
				// Ignored
			}

			public void removeSelectionChangedListener(
					ISelectionChangedListener listener) {
				// Ignored
			}

			public ISelection getSelection() {
				return new StructuredSelection(getCommit());
			}

			public void addSelectionChangedListener(
					ISelectionChangedListener listener) {
				// Ignored
			}
		});
	}

	/**
	 * @see org.eclipse.ui.forms.editor.SharedHeaderFormEditor#setFocus()
	 * @since 2.0
	 */
	@Override
	public void setFocus() {
		commitPage.getPartControl().setFocus();
	}

	private void addContributions(IToolBarManager toolBarManager) {
		IMenuService menuService = (IMenuService) getSite().getService(
				IMenuService.class);
		if (menuService != null
				&& toolBarManager instanceof ContributionManager) {
			ContributionManager contributionManager = (ContributionManager) toolBarManager;
			String toolbarUri = "toolbar:" + TOOLBAR_HEADER_ID; //$NON-NLS-1$
			menuService.populateContributionManager(contributionManager,
					toolbarUri);
		}
	}

	private RepositoryCommit getCommit() {
		return (RepositoryCommit) getAdapter(RepositoryCommit.class);
	}

	/**
	 * @see org.eclipse.ui.part.MultiPageEditorPart#getAdapter(java.lang.Class)
	 */
	public Object getAdapter(Class adapter) {
		if (RepositoryCommit.class == adapter)
			return getEditorInput().getAdapter(adapter);

		return super.getAdapter(adapter);
	}

	/**
	 * @see org.eclipse.ui.forms.editor.FormEditor#init(org.eclipse.ui.IEditorSite,
	 *      org.eclipse.ui.IEditorInput)
	 */
	public void init(IEditorSite site, IEditorInput input)
			throws PartInitException {
		if (input.getAdapter(RepositoryCommit.class) == null)
			throw new PartInitException(
					"Input could not be adapted to commit object"); //$NON-NLS-1$
		super.init(site, input);
		setPartName(input.getName());
		setTitleToolTip(input.getToolTipText());
	}

	public void dispose() {
		refListenerHandle.remove();
		super.dispose();
	}

	/**
	 * @see org.eclipse.ui.part.EditorPart#doSave(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void doSave(IProgressMonitor monitor) {
		// Save not supported
	}

	/**
	 * @see org.eclipse.ui.part.EditorPart#doSaveAs()
	 */
	public void doSaveAs() {
		// Save as not supported
	}

	/**
	 * @see org.eclipse.ui.part.EditorPart#isSaveAsAllowed()
	 */
	public boolean isSaveAsAllowed() {
		return false;
	}

	public void onRefsChanged(RefsChangedEvent event) {
		if (getCommit().getRepository().getDirectory()
				.equals(event.getRepository().getDirectory())) {
			UIJob job = new UIJob("Refreshing editor") { //$NON-NLS-1$

				public IStatus runInUIThread(IProgressMonitor monitor) {
					if (!getContainer().isDisposed())
						commitPage.refresh();
					return Status.OK_STATUS;
				}
			};
			job.schedule();
		}
	}
}
