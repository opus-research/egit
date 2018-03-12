/*******************************************************************************
 * Copyright (c) 2010, SAP AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Mathias Kinzler (SAP AG) - initial implementation
 *******************************************************************************/
package org.eclipse.egit.ui.internal.preferences;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.egit.core.GitCorePreferences;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.UIText;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.BaseLabelProvider;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.ICellEditorListener;
import org.eclipse.jface.viewers.ICellEditorValidator;
import org.eclipse.jface.viewers.IFontProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.window.IShellProvider;
import org.eclipse.jface.window.SameShellProvider;
import org.eclipse.jface.window.Window;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.ide.FileStoreEditorInput;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.model.WorkbenchAdapter;
import org.eclipse.ui.model.WorkbenchContentProvider;

/**
 * A reusable UI component to display and edit a Git configuration.
 * <p>
 * Concrete subclasses that are interested in displaying error messages should
 * override {@link #setErrorMessage(String)}
 * <p>
 * TODO: do the changes in memory and offer methods to obtain dirty state, to
 * save, and something like setDirty(boolean) to be implemented by subclasses so
 * that proper save/revert can be implemented; we could also offer this for
 * non-stored configurations
 */
public class ConfigurationEditorComponent {

	private final static String DOT = "."; //$NON-NLS-1$

	private StoredConfig editableConfig;

	private final IShellProvider shellProvider;

	private final Composite parent;

	private final boolean useDialogFont;

	private Composite contents;

	private Button newValue;

	private Button remove;

	private TreeViewer tv;

	private Text location;

	private final boolean changeablePath;

	private boolean editable;

	/**
	 * @param parent
	 *            the parent
	 * @param config
	 *            to be used instead of the user configuration
	 * @param useDialogFont
	 *            if <code>true</code>, the current dialog font is used
	 * @param changeablePath
	 *            The user can change the path of the configuration file
	 */
	public ConfigurationEditorComponent(Composite parent, StoredConfig config,
			boolean useDialogFont, boolean changeablePath) {
		editableConfig = config;
		this.shellProvider = new SameShellProvider(parent);
		this.parent = parent;
		this.useDialogFont = useDialogFont;
		this.changeablePath = changeablePath;
	}

	void setConfig(FileBasedConfig config) throws IOException {
		editableConfig = config;
		try {
			editableConfig.clear();
			editableConfig.load();
		} catch (ConfigInvalidException e) {
			throw new IOException(e.getMessage());
		}
		initControlsFromConfig();
	}

	/**
	 * Saves and (in case of success) reloads the current configuration
	 *
	 * @throws IOException
	 */
	public void save() throws IOException {
		editableConfig.save();
		setDirty(false);
		initControlsFromConfig();
	}

	/**
	 * Restores and (in case of success) reloads the current configuration
	 *
	 * @throws IOException
	 */
	public void restore() throws IOException {
		if (changeablePath) {
			try {
				IEclipsePreferences node = new InstanceScope()
						.getNode(org.eclipse.egit.core.Activator
								.getPluginId());
				node.remove(GitCorePreferences.core_gitPrefix);
				node.flush();
				// Create a temporary FS instance to compute the Git prefix
				File gitPrefix = FS.detect().gitPrefix();
				// Update THE FS instance.
				// TODO: This works today when there is only one FS
				// instance, but much of the JGit code is actually
				// written to work if there are multiple instances.
				FS.DETECTED.setGitPrefix(gitPrefix);
			} catch (Exception e1) {
				Activator
						.logError(
								UIText.ConfigurationEditorComponent_CannotChangeGitPrefixError,
								e1);
			}
		}
		try {
			editableConfig.clear();
			editableConfig.load();
		} catch (ConfigInvalidException e) {
			throw new IOException(e.getMessage());
		}
		initControlsFromConfig();
	}

	/**
	 * @return the control being created
	 */
	public Control createContents() {
		final Composite main = new Composite(parent, SWT.NONE);
		main.setLayout(new GridLayout(2, false));
		GridDataFactory.fillDefaults().grab(true, true).applyTo(main);

		if (editableConfig instanceof FileBasedConfig) {
			Composite locationPanel = new Composite(main, SWT.NONE);
			locationPanel.setLayout(new GridLayout(4, false));
			GridDataFactory.fillDefaults().grab(true, false).span(2, 1)
					.applyTo(locationPanel);
			Label locationLabel = new Label(locationPanel, SWT.NONE);
			locationLabel
					.setText(UIText.ConfigurationEditorComponent_ConfigLocationLabel);
			// GridDataFactory.fillDefaults().applyTo(locationLabel);
			int locationStyle = SWT.BORDER|SWT.READ_ONLY;
			location = new Text(locationPanel, locationStyle);
			GridDataFactory.fillDefaults().align(SWT.FILL, SWT.CENTER)
					.grab(true, false).applyTo(location);
			if (changeablePath) {
				Button selectPath = new Button(locationPanel, SWT.PUSH);
				selectPath
						.setText(UIText.ConfigurationEditorComponent_BrowseForPrefix);
				selectPath.addSelectionListener(new SelectionAdapter() {
					@Override
					public void widgetSelected(SelectionEvent e) {
						DirectoryDialog dialog = new DirectoryDialog(
								getShell(), SWT.OPEN);
						dialog.setText(UIText.ConfigurationEditorComponent_SelectGitInstallation);
						String file = dialog.open();
						if (file != null) {
							File etc = new File(file, "etc"); //$NON-NLS-1$
							File bin = new File(file, "bin"); //$NON-NLS-1$
							if (!new File(etc, "gitconfig").exists() //$NON-NLS-1$
									&& !new File(bin, "git").exists() //$NON-NLS-1$
									&& (!System
											.getProperty("os.name").startsWith("Windows") || //$NON-NLS-1$ //$NON-NLS-2$
									!new File(bin, "git.exe").exists())) { //$NON-NLS-1$
								MessageDialog
										.open(SWT.ERROR,
												getShell(),
												UIText.ConfigurationEditorComponent_GitPrefixSelectionErrorTitle,
												UIText.ConfigurationEditorComponent_GitPrefixSelectionErrorMessage,
												SWT.NONE);
								return;
							}
							location.setText(file);
							try {
								IEclipsePreferences node = new InstanceScope()
										.getNode(org.eclipse.egit.core.Activator
												.getPluginId());
								node.put(GitCorePreferences.core_gitPrefix,
										file);
								node.flush();
								setChangeSystemPrefix(file);
							} catch (Exception e1) {
								Activator
										.logError(
												UIText.ConfigurationEditorComponent_CannotChangeGitPrefixError,
												e1);
							}
						}
					}
				});
			}
			Button openEditor = new Button(locationPanel, SWT.PUSH);
			openEditor
					.setText(UIText.ConfigurationEditorComponent_OpenEditorButton);
			openEditor
					.setToolTipText(UIText.ConfigurationEditorComponent_OpenEditorTooltip);
			openEditor.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
					IFileStore store = EFS.getLocalFileSystem().getStore(
							new Path(((FileBasedConfig) editableConfig)
									.getFile().getAbsolutePath()));
					try {
						IDE.openEditor(PlatformUI.getWorkbench()
								.getActiveWorkbenchWindow().getActivePage(),
								new FileStoreEditorInput(store),
								EditorsUI.DEFAULT_TEXT_EDITOR_ID);
					} catch (PartInitException ex) {
						Activator.handleError(ex.getMessage(), ex, true);
					}
				}
			});
			openEditor
					.setEnabled(((FileBasedConfig) editableConfig).getFile() != null);
		}
		tv = new TreeViewer(main, SWT.SINGLE | SWT.FULL_SELECTION | SWT.BORDER);
		Tree tree = tv.getTree();
		GridDataFactory.fillDefaults().hint(100, 60).grab(true, true)
				.applyTo(tree);
		TreeColumn key = new TreeColumn(tree, SWT.NONE);
		key.setText(UIText.ConfigurationEditorComponent_KeyColumnHeader);
		key.setWidth(150);

		final TextCellEditor editor = new TextCellEditor(tree);
		editor.setValidator(new ICellEditorValidator() {

			public String isValid(Object value) {
				String editedValue = value.toString();
				return editedValue.length() > 0 ? null
						: UIText.ConfigurationEditorComponent_EmptyStringNotAllowed;
			}
		});
		editor.addListener(new ICellEditorListener() {

			public void editorValueChanged(boolean oldValidState,
					boolean newValidState) {
				setErrorMessage(editor.getErrorMessage());
			}

			public void cancelEditor() {
				setErrorMessage(null);
			}

			public void applyEditorValue() {
				setErrorMessage(null);
			}
		});

		TreeColumn value = new TreeColumn(tree, SWT.NONE);
		value.setText(UIText.ConfigurationEditorComponent_ValueColumnHeader);
		value.setWidth(250);
		new TreeViewerColumn(tv, value)
				.setEditingSupport(new EditingSupport(tv) {

					protected void setValue(Object element, Object newValue) {
						Entry entry = (Entry) element;
						if (!entry.value.equals(newValue)) {
							entry.changeValue(newValue.toString());
							markDirty();
						}
					}

					protected Object getValue(Object element) {
						return ((Entry) element).value;
					}

					protected CellEditor getCellEditor(Object element) {
						return editor;
					}

					protected boolean canEdit(Object element) {
						return editable && element instanceof Entry;
					}
				});

		tv.setContentProvider(new WorkbenchContentProvider());
		Font defaultFont;
		if (useDialogFont)
			defaultFont = JFaceResources.getDialogFont();
		else
			defaultFont = JFaceResources.getDefaultFont();
		tv.setLabelProvider(new ConfigEditorLabelProvider(defaultFont));

		tree.setHeaderVisible(true);
		tree.setLinesVisible(true);

		Composite buttonPanel = new Composite(main, SWT.NONE);
		GridLayoutFactory.fillDefaults().applyTo(buttonPanel);
		GridDataFactory.fillDefaults().grab(false, false).applyTo(buttonPanel);
		newValue = new Button(buttonPanel, SWT.PUSH);
		GridDataFactory.fillDefaults().applyTo(newValue);
		newValue.setText(UIText.ConfigurationEditorComponent_AddButton);
		newValue.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {

				String suggestedKey;
				IStructuredSelection sel = (IStructuredSelection) tv
						.getSelection();
				Object first = sel.getFirstElement();
				if (first instanceof Section)
					suggestedKey = ((Section) first).name + DOT;
				else if (first instanceof SubSection) {
					SubSection sub = (SubSection) first;
					suggestedKey = sub.parent.name + DOT + sub.name + DOT;
				} else if (first instanceof Entry) {
					Entry entry = (Entry) first;
					if (entry.sectionparent != null)
						suggestedKey = entry.sectionparent.name + DOT;
					else
						suggestedKey = entry.subsectionparent.parent.name + DOT
								+ entry.subsectionparent.name + DOT;
				} else
					suggestedKey = null;

				AddConfigEntryDialog dlg = new AddConfigEntryDialog(getShell(),
						suggestedKey);
				if (dlg.open() == Window.OK) {
					StringTokenizer st = new StringTokenizer(dlg.getKey(), DOT);
					if (st.countTokens() == 2) {
						String sectionName = st.nextToken();
						String entryName = st.nextToken();
						Entry entry = ((GitConfig) tv.getInput()).getEntry(
								sectionName, null, entryName);
						if (entry == null)
							editableConfig.setString(sectionName, null,
									entryName, dlg.getValue());
						else
							entry.addValue(dlg.getValue());
						markDirty();
					} else if (st.countTokens() == 3) {
						String sectionName = st.nextToken();
						String subSectionName = st.nextToken();
						String entryName = st.nextToken();
						Entry entry = ((GitConfig) tv.getInput()).getEntry(
								sectionName, subSectionName, entryName);
						if (entry == null)
							editableConfig.setString(sectionName,
									subSectionName, entryName, dlg.getValue());
						else
							entry.addValue(dlg.getValue());
						markDirty();
					} else
						Activator
								.handleError(
										UIText.ConfigurationEditorComponent_WrongNumberOfTokensMessage,
										null, true);
				}
			}

		});
		remove = new Button(buttonPanel, SWT.PUSH);
		GridDataFactory.fillDefaults().applyTo(remove);
		remove.setText(UIText.ConfigurationEditorComponent_RemoveButton);
		remove.setToolTipText(UIText.ConfigurationEditorComponent_RemoveTooltip);
		remove.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				IStructuredSelection sel = (IStructuredSelection) tv
						.getSelection();
				Object first = sel.getFirstElement();
				if (first instanceof Section) {
					Section section = (Section) first;
					if (MessageDialog
							.openConfirm(
									getShell(),
									UIText.ConfigurationEditorComponent_RemoveSectionTitle,
									NLS.bind(
											UIText.ConfigurationEditorComponent_RemoveSectionMessage,
											section.name))) {
						editableConfig.unsetSection(section.name, null);
						markDirty();
					}
				} else if (first instanceof SubSection) {
					SubSection section = (SubSection) first;
					if (MessageDialog
							.openConfirm(
									getShell(),
									UIText.ConfigurationEditorComponent_RemoveSubsectionTitle,
									NLS.bind(
											UIText.ConfigurationEditorComponent_RemoveSubsectionMessage,
											section.parent.name + DOT
													+ section.name))) {
						editableConfig.unsetSection(section.parent.name,
								section.name);
						markDirty();
					}
				} else if (first instanceof Entry) {
					((Entry) first).removeValue();
					markDirty();
				} else
					Activator
							.handleError(
									UIText.ConfigurationEditorComponent_NoSectionSubsectionMessage,
									null, true);

				super.widgetSelected(e);
			}
		});

		tv.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				updateEnablement();
			}
		});

		initControlsFromConfig();
		contents = main;
		return contents;
	}

	/**
	 * @return the composite containing all the controls
	 */
	public Composite getContents() {
		return contents;
	}

	private boolean isWriteable(final File f) {
		if (f.exists())
			if (f.isFile())
				if (f.canWrite())
					return true;
				else
					return false;
			else
				return false;
		// no file, can we create one
		for (File d = f.getParentFile(); d != null; d = d.getParentFile())
			if (d.isDirectory())
				if (d.canWrite())
					return true;
				else
					return false;
			else if (d.exists())
				return false;
		// else continue
		return false;
	}

	private void initControlsFromConfig() {
		try {
			editableConfig.load();
			tv.setInput(new GitConfig(editableConfig));
			editable = true;
			if (editableConfig instanceof FileBasedConfig) {
				FileBasedConfig fileConfig = (FileBasedConfig) editableConfig;
				File configFile = fileConfig.getFile();
				if (configFile != null)
					if (isWriteable(configFile))
						location.setText(configFile.getPath());
					else {
						location.setText(NLS
								.bind(UIText.ConfigurationEditorComponent_ReadOnlyLocationFormat,
										configFile.getPath()));
						editable = false;
					}
				else {
					location.setText(UIText.ConfigurationEditorComponent_NoConfigLocationKnown);
					editable = false;
				}
			}
		} catch (IOException e) {
			Activator.handleError(e.getMessage(), e, true);
		} catch (ConfigInvalidException e) {
			Activator.handleError(e.getMessage(), e, true);
		}
		tv.expandAll();
		updateEnablement();
	}

	/**
	 * @param message
	 *            the error message to display
	 */
	protected void setErrorMessage(String message) {
		// the default implementation does nothing
	}

	/**
	 * @param dirty
	 *            the dirty flag
	 */
	protected void setDirty(boolean dirty) {
		// the default implementation does nothing
	}

	/**
	 * @param prefix
	 *            new System prefix
	 * @throws IOException
	 */
	protected void setChangeSystemPrefix(String prefix) throws IOException {
		// the default implementation does nothing
	}

	private void updateEnablement() {
		remove.setEnabled(editable);
		newValue.setEnabled(editable);
	}

	private void markDirty() {
		setDirty(true);
		((GitConfig) tv.getInput()).refresh();
		tv.refresh();
	}

	private final static class GitConfig extends WorkbenchAdapter {

		private final Config config;

		private Section[] children;

		GitConfig(Config config) {
			this.config = config;
		}

		GitConfig refresh() {
			children = null;
			return this;
		}

		public Object[] getChildren(Object o) {
			if (children == null)
				if (config != null) {
					List<Section> sections = new ArrayList<Section>();
					Set<String> sectionNames = config.getSections();
					for (String sectionName : sectionNames)
						sections.add(new Section(this, sectionName));
					Collections.sort(sections, new Comparator<Section>() {

						public int compare(Section o1, Section o2) {
							return o1.name.compareTo(o2.name);
						}
					});
					children = sections.toArray(new Section[sections.size()]);
				} else
					children = new Section[0];
			return children;
		}

		public Entry getEntry(String sectionName, String subsectionName,
				String entryName) {
			for (Object child : getChildren(this)) {
				Section section = (Section) child;
				if (sectionName.equals(section.name))
					return section.getEntry(subsectionName, entryName);
			}
			return null;
		}
	}

	private final static class Section extends WorkbenchAdapter {
		private final String name;

		private final GitConfig parent;

		private Object[] children;

		Section(GitConfig parent, String name) {
			this.parent = parent;
			this.name = name;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + name.hashCode();
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Section other = (Section) obj;
			if (!name.equals(other.name))
				return false;
			return true;
		}

		public Object getParent(Object object) {
			return parent;
		}

		public Object[] getChildren(Object o) {
			if (children == null) {
				List<Object> allChildren = new ArrayList<Object>();
				Set<String> subSectionNames = parent.config
						.getSubsections(name);
				for (String subSectionName : subSectionNames)
					allChildren.add(new SubSection(parent.config, this,
							subSectionName));

				Set<String> entryNames = parent.config.getNames(name);
				for (String entryName : entryNames) {
					String[] values = parent.config.getStringList(name, null,
							entryName);
					if (values.length == 1)
						allChildren.add(new Entry(this, entryName, values[0],
								-1));
					else {
						int index = 0;
						for (String value : values)
							allChildren.add(new Entry(this, entryName, value,
									index++));
					}
				}
				children = allChildren.toArray();
			}
			return children;
		}

		public String getLabel(Object o) {
			return name;
		}

		public Entry getEntry(String subsectionName, String entryName) {
			if (subsectionName != null) {
				for (Object child : getChildren(this))
					if (child instanceof SubSection
							&& ((SubSection) child).name.equals(subsectionName))
						return ((SubSection) child).getEntry(entryName);
			} else
				for (Object child : getChildren(this))
					if (child instanceof Entry
							&& ((Entry) child).name.equals(entryName))
						return (Entry) child;
			return null;
		}
	}

	private final static class SubSection extends WorkbenchAdapter {

		private final Config config;

		private final Section parent;

		private final String name;

		private Entry[] children;

		SubSection(Config config, Section parent, String name) {
			this.config = config;
			this.parent = parent;
			this.name = name;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + name.hashCode();
			result = prime * result + parent.hashCode();
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			SubSection other = (SubSection) obj;
			if (!name.equals(other.name))
				return false;
			if (!parent.equals(other.parent))
				return false;
			return true;
		}

		public Object[] getChildren(Object o) {
			if (children == null) {
				List<Entry> entries = new ArrayList<Entry>();
				Set<String> entryNames = config.getNames(parent.name, name);
				for (String entryName : entryNames) {
					String[] values = config.getStringList(parent.name, name,
							entryName);
					if (values.length == 1)
						entries.add(new Entry(this, entryName, values[0], -1));
					else {
						int index = 0;
						for (String value : values)
							entries.add(new Entry(this, entryName, value,
									index++));
					}
				}
				children = entries.toArray(new Entry[entries.size()]);
			}
			return children;
		}

		public String getLabel(Object o) {
			return name;
		}

		public Object getParent(Object object) {
			return parent;
		}

		public Entry getEntry(String entryName) {
			for (Object child : getChildren(this))
				if (entryName.equals(((Entry) child).name))
					return (Entry) child;
			return null;
		}
	}

	private final static class Entry extends WorkbenchAdapter {

		private final Section sectionparent;

		private final SubSection subsectionparent;

		private final String name;

		private final String value;

		private final int index;

		Entry(Section parent, String name, String value, int index) {
			this.sectionparent = parent;
			this.subsectionparent = null;
			this.name = name;
			this.value = value;
			this.index = index;
		}

		public void addValue(String newValue) {
			if (newValue.length() == 0)
				throw new IllegalArgumentException(
						UIText.ConfigurationEditorComponent_EmptyStringNotAllowed);
			Config config = getConfig();

			List<String> entries;
			if (sectionparent != null) {
				// Arrays.asList returns a fixed-size list, so we need to copy
				// over to a mutable list
				entries = new ArrayList<String>(Arrays.asList(config
						.getStringList(sectionparent.name, null, name)));
				entries.add(Math.max(index, 0), newValue);
				config.setStringList(sectionparent.name, null, name, entries);
			} else {
				// Arrays.asList returns a fixed-size list, so we need to copy
				// over to a mutable list
				entries = new ArrayList<String>(Arrays.asList(config
						.getStringList(subsectionparent.parent.name,
								subsectionparent.name, name)));
				entries.add(Math.max(index, 0), newValue);
				config.setStringList(subsectionparent.parent.name,
						subsectionparent.name, name, entries);
			}

		}

		Entry(SubSection parent, String name, String value, int index) {
			this.sectionparent = null;
			this.subsectionparent = parent;
			this.name = name;
			this.value = value;
			this.index = index;
		}

		public void changeValue(String newValue)
				throws IllegalArgumentException {
			if (newValue.length() == 0)
				throw new IllegalArgumentException(
						UIText.ConfigurationEditorComponent_EmptyStringNotAllowed);
			Config config = getConfig();

			if (index < 0) {
				if (sectionparent != null)
					config.setString(sectionparent.name, null, name, newValue);
				else
					config.setString(subsectionparent.parent.name,
							subsectionparent.name, name, newValue);
			} else {
				String[] entries;
				if (sectionparent != null) {
					entries = config.getStringList(sectionparent.name, null,
							name);
					entries[index] = newValue;
					config.setStringList(sectionparent.name, null, name,
							Arrays.asList(entries));
				} else {
					entries = config.getStringList(
							subsectionparent.parent.name,
							subsectionparent.name, name);
					entries[index] = newValue;
					config.setStringList(subsectionparent.parent.name,
							subsectionparent.name, name, Arrays.asList(entries));
				}
			}
		}

		private Config getConfig() {
			Config config;
			if (sectionparent != null)
				config = sectionparent.parent.config;
			else
				config = subsectionparent.parent.parent.config;
			return config;
		}

		public void removeValue() {
			Config config = getConfig();

			if (index < 0) {
				if (sectionparent != null)
					config.unset(sectionparent.name, null, name);
				else
					config.unset(subsectionparent.parent.name,
							subsectionparent.name, name);
			} else {
				List<String> entries;
				if (sectionparent != null) {
					// Arrays.asList returns a fixed-size list, so we need to
					// copy over to a mutable list
					entries = new ArrayList<String>(Arrays.asList(config
							.getStringList(sectionparent.name, null, name)));

					entries.remove(index);
					config.setStringList(sectionparent.name, null, name,
							entries);
				} else {
					// Arrays.asList returns a fixed-size list, so we need to
					// copy over to a mutable list
					entries = new ArrayList<String>(Arrays.asList(config
							.getStringList(subsectionparent.parent.name,
									subsectionparent.name, name)));
					// the list is fixed-size, so we have to copy over
					entries.remove(index);
					config.setStringList(subsectionparent.parent.name,
							subsectionparent.name, name, entries);
				}
			}
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + index;
			result = prime * result + name.hashCode();
			result = prime * result
					+ ((sectionparent == null) ? 0 : sectionparent.hashCode());
			result = prime
					* result
					+ ((subsectionparent == null) ? 0 : subsectionparent
							.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Entry other = (Entry) obj;
			// the index may change between 0 and -1 when values are added
			if (index != other.index && (index > 0 || other.index > 0))
				return false;
			if (!name.equals(other.name))
				return false;
			if (sectionparent == null) {
				if (other.sectionparent != null)
					return false;
			} else if (!sectionparent.equals(other.sectionparent))
				return false;
			if (subsectionparent == null) {
				if (other.subsectionparent != null)
					return false;
			} else if (!subsectionparent.equals(other.subsectionparent))
				return false;
			return true;
		}
	}

	private static final class ConfigEditorLabelProvider extends
			BaseLabelProvider implements ITableLabelProvider, IFontProvider {
		private Font boldFont = null;

		private final Font defaultFont;

		public ConfigEditorLabelProvider(Font defaultFont) {
			this.defaultFont = defaultFont;
		}

		public Image getColumnImage(Object element, int columnIndex) {
			return null;
		}

		public String getColumnText(Object element, int columnIndex) {
			switch (columnIndex) {
			case 0:
				if (element instanceof Section)
					return ((Section) element).name;
				if (element instanceof SubSection)
					return ((SubSection) element).name;
				if (element instanceof Entry) {
					Entry entry = (Entry) element;
					if (entry.index < 0)
						return entry.name;
					return entry.name + "[" + entry.index + "]"; //$NON-NLS-1$ //$NON-NLS-2$
				}
				return null;
			case 1:
				if (element instanceof Entry)
					return ((Entry) element).value;
				return null;
			default:
				return null;
			}
		}

		public Font getFont(Object element) {
			if (element instanceof Section || element instanceof SubSection)
				return getBoldFont();
			else
				return null;
		}

		private Font getBoldFont() {
			if (boldFont != null)
				return boldFont;
			FontData[] data = defaultFont.getFontData();
			for (int i = 0; i < data.length; i++)
				data[i].setStyle(data[i].getStyle() | SWT.BOLD);

			boldFont = new Font(Display.getDefault(), data);
			return boldFont;
		}

		@Override
		public void dispose() {
			if (boldFont != null)
				boldFont.dispose();
			super.dispose();
		}
	}

	private Shell getShell() {
		return shellProvider.getShell();
	}
}
