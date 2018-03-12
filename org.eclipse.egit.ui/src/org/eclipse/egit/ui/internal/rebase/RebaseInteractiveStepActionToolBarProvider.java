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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.egit.core.internal.rebase.RebaseInteractivePlan;
import org.eclipse.egit.core.internal.rebase.RebaseInteractivePlan.ElementAction;
import org.eclipse.egit.core.internal.rebase.RebaseInteractivePlan.ElementType;
import org.eclipse.egit.core.internal.rebase.RebaseInteractivePlan.PlanElement;
import org.eclipse.egit.ui.internal.UIIcons;
import org.eclipse.egit.ui.internal.UIText;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.resource.LocalResourceManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

/**
 * Toolbar provider for editing rebase interactive steps
 */
public class RebaseInteractiveStepActionToolBarProvider {

	private ToolItem itemPick;

	private ToolItem itemSkip;

	private ToolItem itemEdit;

	private ToolItem itemSquash;

	private ToolItem itemFixup;

	private ToolItem itemReword;

	private ToolItem itemMoveUp;

	private ToolItem itemMoveDown;

	private final ToolItem[] rebaseActionItems = new ToolItem[6];

	private final RebaseInteractiveView view;

	private final ToolBar theToolbar;

	private LocalResourceManager resources = new LocalResourceManager(
			JFaceResources.getResources());

	/**
	 * @return the theToolbar
	 */
	final ToolBar getTheToolbar() {
		return theToolbar;
	}

	/**
	 * @param parent
	 * @param style
	 * @param view
	 */
	public RebaseInteractiveStepActionToolBarProvider(Composite parent,
			int style, RebaseInteractiveView view) {
		this.theToolbar = new ToolBar(parent, style);
		this.view = view;
		createToolBarItems();
		this.theToolbar.addDisposeListener(new DisposeListener() {

			public void widgetDisposed(DisposeEvent e) {
				dispose();
			}
		});
	}

	private Image getImage(ImageDescriptor descriptor) {
		return (Image) this.resources.get(descriptor);
	}

	private void dispose() {
		resources.dispose();
	}

	private void createToolBarItems() {
		itemPick = new ToolItem(theToolbar, SWT.RADIO);
		itemPick.setImage(getImage(UIIcons.CHERRY_PICK));
		itemPick.addSelectionListener(new ActionSelectionListener(
				RebaseInteractivePlan.ElementAction.PICK));
		itemPick.setText(UIText.RebaseInteractiveStepActionToolBarProvider_PickText);
		rebaseActionItems[0] = itemPick;

		itemSkip = new ToolItem(theToolbar, SWT.RADIO);
		itemSkip.setImage(getImage(UIIcons.REBASE_SKIP));
		itemSkip.addSelectionListener(new ActionSelectionListener(
				RebaseInteractivePlan.ElementAction.SKIP));
		itemSkip.setText(UIText.RebaseInteractiveStepActionToolBarProvider_SkipText);
		rebaseActionItems[1] = itemSkip;

		itemEdit = new ToolItem(theToolbar, SWT.RADIO);
		itemEdit.setImage(getImage(UIIcons.EDITCONFIG));
		itemEdit.addSelectionListener(new ActionSelectionListener(
				RebaseInteractivePlan.ElementAction.EDIT));
		itemEdit.setText(UIText.RebaseInteractiveStepActionToolBarProvider_EditText);
		rebaseActionItems[2] = itemEdit;

		itemSquash = new ToolItem(theToolbar, SWT.RADIO);
		itemSquash.setImage(getImage(UIIcons.SQUASH));
		itemSquash.addSelectionListener(new ActionSelectionListener(
				RebaseInteractivePlan.ElementAction.SQUASH));
		itemSquash
				.setText(UIText.RebaseInteractiveStepActionToolBarProvider_SquashText);
		rebaseActionItems[3] = itemSquash;

		itemFixup = new ToolItem(theToolbar, SWT.RADIO);
		itemFixup.setImage(getImage(UIIcons.FIXUP));
		itemFixup.addSelectionListener(new ActionSelectionListener(
				RebaseInteractivePlan.ElementAction.FIXUP));
		itemFixup
				.setText(UIText.RebaseInteractiveStepActionToolBarProvider_FixupText);
		rebaseActionItems[4] = itemFixup;

		itemReword = new ToolItem(theToolbar, SWT.RADIO);
		itemReword.setImage(getImage(UIIcons.REWORD));
		itemReword.addSelectionListener(new ActionSelectionListener(
				RebaseInteractivePlan.ElementAction.REWORD));
		itemReword
				.setText(UIText.RebaseInteractiveStepActionToolBarProvider_RewordText);
		rebaseActionItems[5] = itemReword;

		new ToolItem(theToolbar, SWT.SEPARATOR);

		itemMoveUp = new ToolItem(theToolbar, SWT.NONE);
		itemMoveUp.setImage(getImage(UIIcons.ELCL16_PREVIOUS));
		itemMoveUp
				.setText(UIText.RebaseInteractiveStepActionToolBarProvider_MoveUpText);
		itemMoveUp.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				List<PlanElement> selectedRebaseTodoLines = getSelectedRebaseTodoLines();
				for (PlanElement planElement : selectedRebaseTodoLines) {
					if (planElement.getElementType() != ElementType.TODO)
						return;

					if (!RebaseInteractivePreferences.isOrderReversed())
						view.getCurrentPlan().moveTodoEntryUp(planElement);
					else
						view.getCurrentPlan().moveTodoEntryDown(planElement);

					mapActionItemsToSelection(view.planTreeViewer
							.getSelection());
				}
			}
		});

		itemMoveDown = new ToolItem(theToolbar, SWT.NONE);
		itemMoveDown.setImage(getImage(UIIcons.ELCL16_NEXT));
		itemMoveDown
				.setText(UIText.RebaseInteractiveStepActionToolBarProvider_MoveDownText);
		itemMoveDown.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				List<PlanElement> selectedRebaseTodoLines = getSelectedRebaseTodoLines();
				Collections.reverse(selectedRebaseTodoLines);
				for (PlanElement planElement : selectedRebaseTodoLines) {
					if (planElement.getElementType() != ElementType.TODO)
						return;

					if (!RebaseInteractivePreferences.isOrderReversed())
						view.getCurrentPlan().moveTodoEntryDown(planElement);
					else
						view.getCurrentPlan().moveTodoEntryUp(planElement);

					mapActionItemsToSelection(view.planTreeViewer
							.getSelection());
				}
			}
		});
	}

	private class ActionSelectionListener implements SelectionListener {
		private final RebaseInteractivePlan.ElementAction type;

		ActionSelectionListener(final RebaseInteractivePlan.ElementAction action) {
			this.type = action;
		}

		public void widgetSelected(SelectionEvent e) {
			List<RebaseInteractivePlan.PlanElement> selected = getSelectedRebaseTodoLines();
			if (selected == null || selected.isEmpty())
				return;

			ElementAction typeToSet = type;
			if (type != ElementAction.PICK) {
				boolean allItemsHaveTargetType = true;
				for (RebaseInteractivePlan.PlanElement element : selected)
					allItemsHaveTargetType &= element.getPlanElementAction() == type;
				if (allItemsHaveTargetType) {
					typeToSet = ElementAction.PICK;
					itemPick.setSelection(true);
					if (e.getSource() instanceof ToolItem)
						((ToolItem) e.getSource()).setSelection(false);
				}
			}

			for (RebaseInteractivePlan.PlanElement element : selected)
				element.setPlanElementAction(typeToSet);
			mapActionItemsToSelection(view.planTreeViewer.getSelection());
		}

		public void widgetDefaultSelected(SelectionEvent e) {
			widgetSelected(e);
		}
	}

	/**
	 * Returns the current selected entries in
	 * {@link RebaseInteractiveView#planTreeViewer} of type
	 * {@link org.eclipse.egit.core.internal.rebase.RebaseInteractivePlan.PlanElement}
	 *
	 * @return a {@link List}<
	 *         {@link org.eclipse.egit.core.internal.rebase.RebaseInteractivePlan.PlanElement}
	 *         > as the current selection.
	 */
	protected List<RebaseInteractivePlan.PlanElement> getSelectedRebaseTodoLines() {
		IStructuredSelection selection = (IStructuredSelection) view.planTreeViewer
				.getSelection();
		List<RebaseInteractivePlan.PlanElement> planEntries = new ArrayList<RebaseInteractivePlan.PlanElement>(
				selection.size());
		@SuppressWarnings("unchecked")
		List<RebaseInteractivePlan.PlanElement> candidates = selection.toList();
		for (Object candidate : candidates) {
			if (candidate instanceof RebaseInteractivePlan.PlanElement)
				planEntries.add((RebaseInteractivePlan.PlanElement) candidate);
		}
		return planEntries;
	}

	void mapActionItemsToSelection(ISelection selection) {
		setMoveItemsEnabled(false);
		if (selection == null || selection.isEmpty())
			return;
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection structured = (IStructuredSelection) selection;

			Object obj = structured.getFirstElement();
			if (!(obj instanceof PlanElement))
				return;
			PlanElement firstSelectedEntry = (PlanElement) obj;
			PlanElement lastSelectedEntry = firstSelectedEntry;

			ElementAction type = firstSelectedEntry.getPlanElementAction();

			boolean singleTypeSelected = true;
			if (structured.size() > 1) {
				// multi selection
				for (Object selectedObj : structured.toList()) {
					if (!(selectedObj instanceof PlanElement))
						continue;
					PlanElement entry = (PlanElement) selectedObj;
					lastSelectedEntry = entry;
					if (type != entry.getPlanElementAction()) {
						singleTypeSelected = false;
					}
				}
			}

			if (singleTypeSelected)
				unselectAllActionItemsExecpt(getItemFor(type));
			else
				unselectAllActionItemsExecpt(null);

			enableMoveButtons(firstSelectedEntry, lastSelectedEntry);

		}
	}

	private void enableMoveButtons(
			PlanElement firstSelectedEntry, PlanElement lastSelectedEntry) {
		List<PlanElement> list = view.getCurrentPlan().getList();
		List<PlanElement> stepList = new ArrayList<PlanElement>();
		for (PlanElement planElement : list) {
			if (!planElement.isComment())
				stepList.add(planElement);
		}

		int firstEntryIndex = stepList.indexOf(firstSelectedEntry);
		int lastEntryIndex = stepList.indexOf(lastSelectedEntry);
		if (!RebaseInteractivePreferences.isOrderReversed()) {
			itemMoveUp.setEnabled(firstEntryIndex > 0);
			itemMoveDown.setEnabled(lastEntryIndex < stepList.size() - 1);
		} else {
			itemMoveUp.setEnabled(firstEntryIndex < stepList.size() - 1);
			itemMoveDown.setEnabled(lastEntryIndex > 0);
		}
	}

	private ToolItem getItemFor(ElementAction type) {
		switch (type) {
		case EDIT:
			return itemEdit;
		case FIXUP:
			return itemFixup;
		case PICK:
			return itemPick;
		case REWORD:
			return itemReword;
		case SQUASH:
			return itemSquash;
		case SKIP:
			return itemSkip;
		default:
			return null;
		}
	}

	private void unselectAllActionItemsExecpt(ToolItem item) {
		for (int i = 0; i < rebaseActionItems.length; i++) {
			ToolItem currItem = rebaseActionItems[i];
			if (currItem == null)
				continue;
			if (currItem == item)
				currItem.setSelection(true);
			else
				currItem.setSelection(false);
		}
	}

	private void setMoveItemsEnabled(boolean enabled) {
		itemMoveDown.setEnabled(enabled);
		itemMoveUp.setEnabled(enabled);
	}

}
