/*******************************************************************************
 * Copyright (C) 2015, Max Hohenegger <eclipse@hohenegger.eu>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.gitflow.ui.internal.dialogs;

import static org.eclipse.core.databinding.UpdateValueStrategy.POLICY_ON_REQUEST;
import static org.eclipse.core.databinding.beans.PojoProperties.value;
import static org.eclipse.egit.gitflow.InitParameters.DEVELOP_BRANCH_PROPERTY;
import static org.eclipse.egit.gitflow.InitParameters.FEATURE_BRANCH_PREFIX_PROPERTY;
import static org.eclipse.egit.gitflow.InitParameters.HOTFIX_BRANCH_PREFIX_PROPERTY;
import static org.eclipse.egit.gitflow.InitParameters.MASTER_BRANCH_PROPERTY;
import static org.eclipse.egit.gitflow.InitParameters.RELEASE_BRANCH_PREFIX_PROPERTY;
import static org.eclipse.egit.gitflow.InitParameters.VERSION_TAG_PROPERTY;
import static org.eclipse.egit.gitflow.ui.Activator.error;
import static org.eclipse.egit.gitflow.ui.internal.UIText.FinishFeatureDialog_title;
import static org.eclipse.egit.gitflow.ui.internal.UIText.InitDialog_chooseBranchNamesAndPrefixes;
import static org.eclipse.egit.gitflow.ui.internal.UIText.InitDialog_developBranch;
import static org.eclipse.egit.gitflow.ui.internal.UIText.InitDialog_featureBranchPrefix;
import static org.eclipse.egit.gitflow.ui.internal.UIText.InitDialog_hotfixBranchPrefix;
import static org.eclipse.egit.gitflow.ui.internal.UIText.InitDialog_invalidBranchName;
import static org.eclipse.egit.gitflow.ui.internal.UIText.InitDialog_invalidPrefix;
import static org.eclipse.egit.gitflow.ui.internal.UIText.InitDialog_masterBranch;
import static org.eclipse.egit.gitflow.ui.internal.UIText.InitDialog_releaseBranchPrefix;
import static org.eclipse.egit.gitflow.ui.internal.UIText.InitDialog_versionTagPrefix;
import static org.eclipse.jface.databinding.swt.WidgetProperties.text;
import static org.eclipse.jface.dialogs.IDialogConstants.OK_ID;
import static org.eclipse.jface.dialogs.IMessageProvider.ERROR;
import static org.eclipse.jface.dialogs.IMessageProvider.INFORMATION;
import static org.eclipse.jgit.lib.Repository.isValidRefName;
import static org.eclipse.swt.SWT.Modify;

import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.UpdateValueStrategy;
import org.eclipse.core.databinding.ValidationStatusProvider;
import org.eclipse.core.databinding.validation.IValidator;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.egit.gitflow.InitParameters;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jface.databinding.dialog.TitleAreaDialogSupport;
import org.eclipse.jface.databinding.dialog.ValidationMessageProvider;
import org.eclipse.jface.dialogs.TitleAreaDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * Dialog to gather inputs for the git flow init operation.
 */
public class InitDialog extends TitleAreaDialog {
	private Text developText;

	private InitParameters gitflowInitConfig = new InitParameters();

	private Text masterText;

	private Text featureText;

	private Text releaseText;

	private Text hotfixText;

	private Text versionTagText;

	private static final String DUMMY_POSTFIX = "dummy"; //$NON-NLS-1$

	private static final int TEXT_HEIGHT = 15;

	private static final int TEXT_WIDTH = 100;

	/**
	 * @param parentShell
	 */
	public InitDialog(Shell parentShell) {
		super(parentShell);
	}

	@Override
	public void create() {
		super.create();
		setTitle(FinishFeatureDialog_title);
		setMessage(InitDialog_chooseBranchNamesAndPrefixes);
	}

	@Override
	protected Control createDialogArea(Composite parent) {
		Composite area = (Composite) super.createDialogArea(parent);
		Composite container = new Composite(area, SWT.NONE);
		container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		GridLayoutFactory.swtDefaults().numColumns(4).applyTo(container);

		createInputs(container);

		DataBindingContext bindingContext = initDataBinding();

		TitleAreaDialogSupport.create(this, bindingContext).setValidationMessageProvider(new ValidationMessageProvider() {
			@Override
			public String getMessage(ValidationStatusProvider statusProvider) {
				if (statusProvider == null) {
					return InitDialog_chooseBranchNamesAndPrefixes;
				}
				return super.getMessage(statusProvider);
			}

			@Override
			public int getMessageType(ValidationStatusProvider statusProvider) {
				int type = super.getMessageType(statusProvider);
				Button okButton = getButton(OK_ID);
				if(okButton != null) {
					okButton.setEnabled(type != ERROR);
				}
				if (ERROR != type) {
					return INFORMATION;
				}
				return type;
			}
		});

		return area;
	}

	private void createInputs(Composite container) {
		developText = createLabeledText(container, InitDialog_developBranch);
		masterText = createLabeledText(container, InitDialog_masterBranch);
		featureText = createLabeledText(container, InitDialog_featureBranchPrefix);
		releaseText = createLabeledText(container, InitDialog_releaseBranchPrefix);
		hotfixText = createLabeledText(container, InitDialog_hotfixBranchPrefix);
		versionTagText = createLabeledText(container, InitDialog_versionTagPrefix);
	}

	private Text createLabeledText(Composite container, String label) {
		new Label(container, SWT.NONE).setText(label);
		Text result = new Text(container, SWT.BORDER);
		GridDataFactory.swtDefaults().hint(TEXT_WIDTH, TEXT_HEIGHT).applyTo(result);
		return result;
	}

	private DataBindingContext initDataBinding() {
		DataBindingContext context = new DataBindingContext();

		UpdateValueStrategy noModelToTarget = new UpdateValueStrategy(false, POLICY_ON_REQUEST);

		UpdateValueStrategy targetToModel = new UpdateValueStrategy();
		targetToModel.setBeforeSetValidator(new IValidator() {
			@Override
			public IStatus validate(Object value) {
				if (value == null || !isValidRefName(Constants.R_HEADS + value)) {
					return error(NLS.bind(InitDialog_invalidBranchName, value));
				}
				return Status.OK_STATUS;
			}
		});
		bind(context, noModelToTarget, targetToModel, DEVELOP_BRANCH_PROPERTY, developText);
		bind(context, noModelToTarget, targetToModel, MASTER_BRANCH_PROPERTY, masterText);

		UpdateValueStrategy prefixTargetToModel = new UpdateValueStrategy();
		prefixTargetToModel.setBeforeSetValidator(new IValidator() {
			@Override
			public IStatus validate(Object value) {
				if (value == null || !isValidRefName(Constants.R_HEADS + value + DUMMY_POSTFIX)) {
					return error(NLS.bind(InitDialog_invalidPrefix, value));
				}
				return Status.OK_STATUS;
			}
		});
		bind(context, noModelToTarget, prefixTargetToModel, FEATURE_BRANCH_PREFIX_PROPERTY, featureText);
		bind(context, noModelToTarget, prefixTargetToModel, RELEASE_BRANCH_PREFIX_PROPERTY, releaseText);
		bind(context, noModelToTarget, prefixTargetToModel, HOTFIX_BRANCH_PREFIX_PROPERTY, hotfixText);
		bind(context, noModelToTarget, prefixTargetToModel, VERSION_TAG_PROPERTY, versionTagText);

		context.updateTargets();

		return context;
	}

	private void bind(DataBindingContext dataBindingContext,
			UpdateValueStrategy noModelToTargetUpdate,
			UpdateValueStrategy targetToModel, String modelProperty, Text widget) {
		dataBindingContext.bindValue(text(Modify).observe(widget),
				value(modelProperty).observe(gitflowInitConfig), targetToModel,
				noModelToTargetUpdate);
	}

	@Override
	protected Control createButtonBar(Composite parent) {
		// TODO: we should have options to persist the selected configuration
		return super.createButtonBar(parent);
	}

	@Override
	public boolean isHelpAvailable() {
		return false;
	}

	@Override
	protected boolean isResizable() {
		return true;
	}

	/**
	 * @return User inputs
	 */
	@NonNull
	public InitParameters getResult() {
		return gitflowInitConfig;
	}
}