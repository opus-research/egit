/*******************************************************************************
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * Copyright (C) 2012, Matthias Sohn <matthias.sohn@sap.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.history;

import java.io.IOException;
import java.text.MessageFormat;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.IJobChangeEvent;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.jobs.JobChangeAdapter;
import org.eclipse.egit.ui.Activator;
import org.eclipse.egit.ui.JobFamilies;
import org.eclipse.egit.ui.UIPreferences;
import org.eclipse.egit.ui.UIText;
import org.eclipse.egit.ui.internal.trace.GitTraceLocation;
import org.eclipse.jface.resource.ResourceManager;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;

class GenerateHistoryJob extends Job {
	private static final int BATCH_SIZE = 256;

	private final GitHistoryPage page;

	private final SWTCommitList loadedCommits;

	private int itemToLoad = 1;

	private RevCommit commitToLoad;

	private RevCommit commitToShow;

	private int lastUpdateCnt;

	private boolean trace;

	private final RevWalk walk;

	private RevFlag highlightFlag;

	GenerateHistoryJob(final GitHistoryPage ghp, Control control, RevWalk walk,
			ResourceManager resources) {
		super(NLS.bind(UIText.HistoryPage_refreshJob, Activator.getDefault()
				.getRepositoryUtil().getRepositoryName(
						ghp.getInputInternal().getRepository())));
		page = ghp;
		this.walk = walk;
		highlightFlag = walk.newFlag("highlight"); //$NON-NLS-1$
		loadedCommits = new SWTCommitList(control, resources);
		loadedCommits.source(walk);
		trace = GitTraceLocation.HISTORYVIEW.isActive();
	}

	@Override
	protected IStatus run(final IProgressMonitor monitor) {
		IStatus status = Status.OK_STATUS;
		int maxCommits = Activator.getDefault().getPreferenceStore()
					.getInt(UIPreferences.HISTORY_MAX_NUM_COMMITS);
		boolean incomplete = false;
		try {
			if (trace)
				GitTraceLocation.getTrace().traceEntry(
						GitTraceLocation.HISTORYVIEW.getLocation());
			try {
				for (;;) {
					int oldsz = loadedCommits.size();
					if (trace)
						GitTraceLocation.getTrace().trace(
								GitTraceLocation.HISTORYVIEW.getLocation(),
								"Filling commit list"); //$NON-NLS-1$
					// ensure that filling (here) and reading (CommitGraphTable)
					// the commit list is thread safe
					synchronized (loadedCommits) {
						if (commitToLoad != null) {
							loadedCommits.fillTo(commitToLoad, maxCommits);
							commitToShow = commitToLoad;
							commitToLoad = null;
						} else
							loadedCommits.fillTo(oldsz + BATCH_SIZE - 1);
					}
					if (monitor.isCanceled())
						return Status.CANCEL_STATUS;
					final boolean loadIncrementally = !Activator.getDefault().getPreferenceStore()
							.getBoolean(UIPreferences.RESOURCEHISTORY_SHOW_FINDTOOLBAR);
					if (loadedCommits.size() > itemToLoad + (BATCH_SIZE / 2) + 1 && loadIncrementally)
						break;
					if (maxCommits > 0 && loadedCommits.size() > maxCommits)
						incomplete = true;
					if (incomplete || oldsz == loadedCommits.size())
						break;

					if (loadedCommits.size() != 1)
						monitor.setTaskName(MessageFormat
								.format(UIText.GenerateHistoryJob_taskFoundMultipleCommits,
										Integer.valueOf(loadedCommits.size())));
					else
						monitor.setTaskName(UIText.GenerateHistoryJob_taskFoundSingleCommit);
				}
			} catch (IOException e) {
				status = new Status(IStatus.ERROR, Activator.getPluginId(),
						UIText.GenerateHistoryJob_errorComputingHistory, e);
			}
			if (trace)
				GitTraceLocation.getTrace().trace(
						GitTraceLocation.HISTORYVIEW.getLocation(),
						"Loaded " + loadedCommits.size() + " commits"); //$NON-NLS-1$ //$NON-NLS-2$
			updateUI(incomplete);
		} finally {
			monitor.done();
			if (trace)
				GitTraceLocation.getTrace().traceExit(
						GitTraceLocation.HISTORYVIEW.getLocation());
		}
		return status;
	}

	private void updateUI(boolean incomplete) {
		if (trace)
			GitTraceLocation.getTrace().traceEntry(
					GitTraceLocation.HISTORYVIEW.getLocation());
		try {
			if (!incomplete && loadedCommits.size() == lastUpdateCnt)
				return;

			final SWTCommit[] asArray = new SWTCommit[loadedCommits.size()];
			loadedCommits.toArray(asArray);
			page.showCommitList(this, loadedCommits, asArray, commitToShow, incomplete, highlightFlag);
			commitToShow = null;
			lastUpdateCnt = loadedCommits.size();
		} finally {
			if (trace)
				GitTraceLocation.getTrace().traceExit(
						GitTraceLocation.HISTORYVIEW.getLocation());
		}
	}

	void release() {
		if (getState() == Job.NONE)
			dispose();
		else
			addJobChangeListener(new JobChangeAdapter() {
				@Override
				public void done(final IJobChangeEvent event) {
					dispose();
				}
			});

	}

	private void dispose() {
		walk.release();
		Display.getDefault().asyncExec(new Runnable() {

			public void run() {
				loadedCommits.dispose();
			}
		});
	}

	@Override
	public boolean belongsTo(Object family) {
		if (family.equals(JobFamilies.GENERATE_HISTORY))
			return true;
		return super.belongsTo(family);
	}

	void setLoadHint(final int index) {
		itemToLoad = index;
	}

	void setLoadHint(final RevCommit c) {
		commitToLoad = c;
	}

	int loadMoreItemsThreshold() {
		return loadedCommits.size() - (BATCH_SIZE / 2);
	}
}
