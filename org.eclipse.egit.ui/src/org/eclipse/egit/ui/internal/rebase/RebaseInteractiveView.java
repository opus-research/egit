/*******************************************************************************
 * Copyright (c) 2013 SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Tobias Pfeifer (SAP AG) - initial implementation
 *******************************************************************************/
package org.eclipse.egit.ui.internal.rebase;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.egit.core.AdapterUtils;
import org.eclipse.egit.core.internal.rebase.RebaseInteractivePlan;
import org.eclipse.egit.core.internal.rebase.RebaseInteractivePlan.ElementAction;
import org.eclipse.egit.core.internal.rebase.RebaseInteractivePlan.PlanElement;
import org.eclipse.egit.core.project.RepositoryMapping;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIUtils;
import org.eclipse.egit.ui.internal.UIIcons;
import org.eclipse.egit.ui.internal.UIText;
import org.eclipse.egit.ui.internal.commands.shared.AbortRebaseCommand;
import org.eclipse.egit.ui.internal.commands.shared.AbstractRebaseCommandHandler;
import org.eclipse.egit.ui.internal.commands.shared.ContinueRebaseCommand;
import org.eclipse.egit.ui.internal.commands.shared.ProcessStepsRebaseCommand;
import org.eclipse.egit.ui.internal.commands.shared.SkipRebaseCommand;
import org.eclipse.egit.ui.internal.repository.RepositoriesView;
import org.eclipse.egit.ui.internal.repository.tree.RepositoryTreeNode;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.util.LocalSelectionTransfer;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryState;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.widgets.ExpandableComposite;
import org.eclipse.ui.forms.widgets.Form;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Section;
import org.eclipse.ui.part.ViewPart;

//TODO: Preferences page
//TODO: Use the toolbar
//TODO: Update RebaseResultDialog to not show "confilics" when rebase interactive stopped with "edit"
//TODO: Tests
//TODO: Show steps that has been processed (done)
//TODO: Link Selection to History View?

/**
 *
 */
public class RebaseInteractiveView extends ViewPart implements
		RebaseInteractivePlan.RebaseInteractivePlanChangeListener {

	/**
	 * interactive rebase view id
	 */
	public static final String VIEW_ID = "org.eclipse.egit.ui.InteractiveRebaseView"; //$NON-NLS-1$

	TreeViewer planTreeViewer;

	private RebaseInteractivePlan currentPlan;

	private RebaseInteractiveStepActionToolBarProvider actionToolBarProvider;

	private ToolItem startItem;

	private ToolItem abortItem;

	private ToolItem skipItem;

	private ToolItem continueItem;

	private ToolItem refreshItem;

	private boolean listenOnRepositoryViewSelection = true;

	private ISelectionListener selectionChangedListener;

	private boolean dndEnabled = false;

	private Image abortImage;

	private Image skipImage;

	private Image continueImage;

	private Image startImage;

	private Image refreshImage;

	/**
	 *
	 */
	public RebaseInteractiveView() {
		setPartName(UIText.InteractiveRebaseView_this_partName);
	}

	/**
	 * @param o
	 */
	public void setInput(Object o) {
		Repository repo = null;
		if (o == null)
			return;
		if (o instanceof TreeSelection) {
			TreeSelection sel = (TreeSelection) o;
			if (sel.size() != 1)
				return;
			o = sel.getFirstElement();
		}
		if (o instanceof RepositoryTreeNode<?>) {
			repo = ((RepositoryTreeNode) o).getRepository();
		} else if (o instanceof Repository) {
			repo = (Repository) o;
		} else if (o instanceof IAdaptable) {
			IResource resource = (IResource) ((IAdaptable) o)
					.getAdapter(IResource.class);
			if (resource != null) {
				RepositoryMapping mapping = RepositoryMapping
						.getMapping(resource);
				repo = mapping.getRepository();
			}
		}

		if (repo == null) {
			repo = AdapterUtils.adapt(o, Repository.class);
		}

		if (repo != null
				&& repo.getRepositoryState() == RepositoryState.REBASING_INTERACTIVE) {
			showRepository(repo);
		}
	}

	/**
	 * @return {@link RebaseInteractiveView#currentPlan}
	 */
	public RebaseInteractivePlan getCurrentPlan() {
		return currentPlan;
	}

	@Override
	public void dispose() {
		removeListeners();
		disposeImages();
		super.dispose();
	}

	private void disposeImages() {
		startImage.dispose();
		abortImage.dispose();
		skipImage.dispose();
		continueImage.dispose();
		refreshImage.dispose();
	}

	private void removeListeners() {
		ISelectionService srv = (ISelectionService) getSite().getService(
				ISelectionService.class);
		srv.removePostSelectionListener(RepositoriesView.VIEW_ID,
				selectionChangedListener);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * org.eclipse.ui.part.WorkbenchPart#createPartControl(org.eclipse.swt.widgets
	 * .Composite)
	 */
	@Override
	public void createPartControl(Composite parent) {
		GridLayoutFactory.fillDefaults().applyTo(parent);

		final FormToolkit toolkit = new FormToolkit(parent.getDisplay());
		parent.addDisposeListener(new DisposeListener() {

			public void widgetDisposed(DisposeEvent e) {
				toolkit.dispose();
			}
		});
		Form form = createForm(parent, toolkit);
		createCommandToolBar(form, toolkit);
		SashForm sashForm = createRebasePlanSashForm(form, toolkit);

		Section rebasePlanSection = toolkit.createSection(sashForm,
				ExpandableComposite.TITLE_BAR);
		planTreeViewer = createPlanTreeViewer(rebasePlanSection, toolkit);

		createColumns();
		createStepActionToolBar(rebasePlanSection, toolkit);
		createPopupMenu(planTreeViewer);

		setupListeners();
		createLocalDragandDrop();
	}

	private void createCommandToolBar(Form form, FormToolkit toolkit) {
		ToolBar toolBar = new ToolBar(form.getHead(), SWT.FLAT);
		toolBar.setOrientation(SWT.RIGHT_TO_LEFT);
		form.setHeadClient(toolBar);

		toolkit.adapt(toolBar);
		toolkit.paintBordersFor(toolBar);

		abortItem = new ToolItem(toolBar, SWT.NONE);
		abortImage = UIIcons.REBASE_ABORT.createImage();
		abortItem.setImage(abortImage);
		abortItem.addSelectionListener(new RebaseCommandItemSelectionListener(
				new AbortRebaseCommand()));
		abortItem.setText(UIText.InteractiveRebaseView_abortItem_text);
		abortItem.setEnabled(false);

		skipItem = new ToolItem(toolBar, SWT.NONE);
		skipImage = UIIcons.REBASE_SKIP.createImage();
		skipItem.setImage(skipImage);
		skipItem.addSelectionListener(new RebaseCommandItemSelectionListener(
				new SkipRebaseCommand()));
		skipItem.setText(UIText.InteractiveRebaseView_skipItem_text);
		skipItem.setEnabled(false);

		continueItem = new ToolItem(toolBar, SWT.NONE);
		continueItem
				.addSelectionListener(new RebaseCommandItemSelectionListener(
						new ContinueRebaseCommand()));
		continueItem.setEnabled(false);
		continueImage = UIIcons.REBASE_CONTINUE.createImage();
		continueItem.setImage(continueImage);
		continueItem.setText(UIText.InteractiveRebaseView_continueItem_text);

		startItem = new ToolItem(toolBar, SWT.NONE);
		startItem.addSelectionListener(new RebaseCommandItemSelectionListener(
				new ProcessStepsRebaseCommand()));
		startItem.setEnabled(false);
		startImage = UIIcons.REBASE_PROCESS_STEPS.createImage();
		startItem.setImage(startImage);
		startItem.setText(UIText.InteractiveRebaseView_startItem_text);

		new ToolItem(toolBar, SWT.SEPARATOR);

		refreshItem = new ToolItem(toolBar, SWT.NONE);
		refreshImage = UIIcons.ELCL16_REFRESH.createImage();
		refreshItem.setImage(refreshImage);
		refreshItem.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				refresh();
			}
		});
		refreshItem.setText(UIText.InteractiveRebaseView_refreshItem_text);
	}

	private TreeViewer createPlanTreeViewer(Section rebasePlanSection,
			FormToolkit toolkit) {

		Composite rebasePlanTableComposite = toolkit
				.createComposite(rebasePlanSection);
		toolkit.paintBordersFor(rebasePlanTableComposite);
		rebasePlanSection.setClient(rebasePlanTableComposite);
		GridLayoutFactory.fillDefaults().extendedMargins(2, 2, 2, 2)
				.applyTo(rebasePlanTableComposite);

		Composite toolbarComposite = toolkit.createComposite(rebasePlanSection);
		toolbarComposite.setBackground(null);
		RowLayout toolbarRowLayout = new RowLayout();
		toolbarRowLayout.marginHeight = 0;
		toolbarRowLayout.marginWidth = 0;
		toolbarRowLayout.marginTop = 0;
		toolbarRowLayout.marginBottom = 0;
		toolbarRowLayout.marginLeft = 0;
		toolbarRowLayout.marginRight = 0;
		toolbarComposite.setLayout(toolbarRowLayout);

		GridLayoutFactory.fillDefaults().extendedMargins(2, 2, 2, 2)
				.applyTo(rebasePlanTableComposite);

		final Tree planTree = toolkit.createTree(rebasePlanTableComposite,
				SWT.FULL_SELECTION | SWT.MULTI);
		planTree.setHeaderVisible(true);
		planTree.setLinesVisible(false);

		TreeViewer tmpPlanViewer = new TreeViewer(planTree);
		tmpPlanViewer
				.addSelectionChangedListener(new PlanViewerSelectionChangedListener());
		GridDataFactory.fillDefaults().grab(true, true)
				.applyTo(tmpPlanViewer.getControl());
		tmpPlanViewer.getTree().setData(FormToolkit.KEY_DRAW_BORDER,
				FormToolkit.TREE_BORDER);
		tmpPlanViewer
				.setContentProvider(RebaseInteractivePlanContentProvider.INSTANCE);
		return tmpPlanViewer;
	}

	private SashForm createRebasePlanSashForm(final Form parent,
			final FormToolkit toolkit) {
		SashForm sashForm = new SashForm(parent.getBody(), SWT.NONE);
		toolkit.adapt(sashForm, true, true);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(sashForm);
		return sashForm;
	}

	private Form createForm(Composite parent, final FormToolkit toolkit) {
		Form form = toolkit.createForm(parent);

		Image repoImage = UIIcons.REPOSITORY.createImage();
		UIUtils.hookDisposal(form, repoImage);
		form.setImage(repoImage);
		form.setText(UIText.RebaseInteractiveView_FormTitle);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(form);
		toolkit.decorateFormHeading(form);
		GridLayoutFactory.swtDefaults().applyTo(form.getBody());

		return form;
	}

	private void setupListeners() {
		setupRepositoryViewSelectionChangeListener();
		refreshUI();
	}

	private void setupRepositoryViewSelectionChangeListener() {
		selectionChangedListener = new ISelectionListener() {
			public void selectionChanged(IWorkbenchPart part,
					ISelection selection) {
				if (!listenOnRepositoryViewSelection)
					return;
				if (!(part instanceof RepositoriesView))
					return;
				setInput(selection);
			}
		};
		ISelectionService srv = (ISelectionService) getSite().getService(
				ISelectionService.class);
		srv.addPostSelectionListener(RepositoriesView.VIEW_ID,
				selectionChangedListener);
	}

	private class RebaseCommandItemSelectionListener extends
			SelectionAdapter {

		private final AbstractRebaseCommandHandler command;

		public RebaseCommandItemSelectionListener(
				AbstractRebaseCommandHandler command) {
			super();
			this.command = command;
		}

		@Override
		public void widgetSelected(SelectionEvent sEvent) {
			try {
				command.execute(currentPlan.getRepository());
			} catch (ExecutionException e) {
				Activator.showError(e.getMessage(), e);
			}
		}
	}

	private class PlanViewerSelectionChangedListener implements
			ISelectionChangedListener {

		public void selectionChanged(SelectionChangedEvent event) {
			if (event == null)
				return;
			ISelection selection = event.getSelection();
			actionToolBarProvider.mapActionItemsToSelection(selection);
		}
	}

	private void createLocalDragandDrop() {
		planTreeViewer.addDragSupport(
				DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK,
				new Transfer[] { LocalSelectionTransfer.getTransfer() },
				new RebaseInteractiveDragSourceListener(this));

		planTreeViewer.addDropSupport(DND.DROP_MOVE,
				new Transfer[] { LocalSelectionTransfer.getTransfer() },
				new RebaseInteractiveDropTargetListener(this, planTreeViewer));
	}

	private void createStepActionToolBar(Section rebasePlanSection,
			final FormToolkit toolkit) {

		actionToolBarProvider = new RebaseInteractiveStepActionToolBarProvider(
				rebasePlanSection, SWT.FLAT | SWT.WRAP, this);
		toolkit.adapt(actionToolBarProvider.getTheToolbar());
		toolkit.paintBordersFor(actionToolBarProvider.getTheToolbar());
		rebasePlanSection.setTextClient(actionToolBarProvider.getTheToolbar());
	}

	/**
	 * Undo previous change in plan
	 */
	protected void undo() {
		// TODO: undo
	}

	/**
	 * Redo previous change in plan reverted by undo
	 */
	protected void redo() {
		// TODO: redo
	}

	// TODO: How to set column width to fit the treeViewer (maximize to not
	// show empty space)
	private void createColumns() {
		String[] headings = { " ", "Action", "CommitID", "Message" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

		TreeViewerColumn infoColumn = new TreeViewerColumn(planTreeViewer,
				SWT.NONE);
		infoColumn.getColumn().setText(headings[0]);
		infoColumn.getColumn().setMoveable(false);
		infoColumn.getColumn().setResizable(true);
		infoColumn.getColumn().setWidth(100);

		infoColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof PlanElement) {
					PlanElement planLine = (PlanElement) element;
					switch (planLine.getElementType()) {
					case DONE:
						return "done"; //$NON-NLS-1$
					case DONE_CURRENT:
						return "curr"; //$NON-NLS-1$
					case TODO:
						return "todo"; //$NON-NLS-1$
					}
				}
				return super.getText(element);
			}
		});

		TreeViewerColumn actionColumn = new TreeViewerColumn(planTreeViewer,
				SWT.NONE);
		actionColumn.getColumn().setText(headings[1]);
		actionColumn.getColumn().setMoveable(false);
		actionColumn.getColumn().setResizable(true);
		actionColumn.getColumn().setWidth(100);

		actionColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof PlanElement) {
					PlanElement planLine = (PlanElement) element;
					if (planLine.isDelete()) {
						// return RebaseInteractivePlan.DELETE_TOKEN;
					}
					return planLine.getPlanElementAction().name();
				}
				return super.getText(element);
			}
		});

		TreeViewerColumn commitIDColumn = new TreeViewerColumn(planTreeViewer,
				SWT.NONE);
		commitIDColumn.getColumn().setText(headings[2]);
		commitIDColumn.getColumn().setMoveable(false);
		commitIDColumn.getColumn().setResizable(true);
		commitIDColumn.getColumn().setWidth(100);

		commitIDColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof PlanElement) {
					PlanElement planLine = (PlanElement) element;
					return planLine.getCommit().name();
				}
				return super.getText(element);
			}
		});

		TreeViewerColumn commitMessageColumn = new TreeViewerColumn(
				planTreeViewer,
				SWT.NONE);
		commitMessageColumn.getColumn().setText(headings[3]);
		commitMessageColumn.getColumn().setMoveable(false);
		commitMessageColumn.getColumn().setResizable(true);
		commitMessageColumn.getColumn().setWidth(100);

		commitMessageColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof PlanElement) {
					PlanElement planLine = (PlanElement) element;
					return planLine.getShortMessage();
				}
				return super.getText(element);
			}
		});
	}

	private void asyncExec(Runnable runnable) {
		PlatformUI.getWorkbench().getDisplay().asyncExec(runnable);
	}

	private void showRepository(final Repository repo) {
		if (repo == null)
			return;
		currentPlan = RebaseInteractivePlan.getPlan(repo);
		currentPlan.addRebaseInteractivePlanChangeListener(this);
		refresh();
	}

	void refresh() {
		if (!isReady())
			return;
		asyncExec(new Runnable() {
			public void run() {
				planTreeViewer.setInput(currentPlan);
				refreshUI();
			}
		});

	}

	private boolean isReady() {
		IWorkbenchPartSite site = this.getSite();
		if (site == null)
			return false;
		return !site.getShell().isDisposed();
	}

	private void refreshUI() {
		if (planTreeViewer != null)
			planTreeViewer.refresh(true);

		startItem.setEnabled(false);
		continueItem.setEnabled(false);
		skipItem.setEnabled(false);
		abortItem.setEnabled(false);
		dndEnabled = false;

		actionToolBarProvider.getTheToolbar().setEnabled(false);

		if (currentPlan == null || !currentPlan.isRebasingInteractive()) {
			return;
		}

		actionToolBarProvider.mapActionItemsToSelection(planTreeViewer.getSelection());
		if (!currentPlan.hasRebaseBeenStartedYet()) {
			actionToolBarProvider.getTheToolbar().setEnabled(true);
			startItem.setEnabled(true);
			abortItem.setEnabled(true);
			dndEnabled  = true;
		} else {
			continueItem.setEnabled(true);
			skipItem.setEnabled(true);
			abortItem.setEnabled(true);
		}
	}

	private void createPopupMenu(TreeViewer planViewer) {
		// TODO Popup menu
	}

	@Override
	public void setFocus() {
		// what to do here?
	}

	boolean isDragAndDropEnabled() {
		return dndEnabled;
	}

	public void planWasUpdatedFromRepository(RebaseInteractivePlan plan) {
		refresh();
	}

	public void planElementTypeChanged(
			RebaseInteractivePlan rebaseInteractivePlan,
			PlanElement element,
			ElementAction oldType,
			ElementAction newType) {
		planTreeViewer.refresh(element, true);
	}

	public void planElementsOrderChanged(
			RebaseInteractivePlan rebaseInteractivePlan, PlanElement element,
			int oldIndex, int newIndex) {
		planTreeViewer.refresh(true);
	}
}