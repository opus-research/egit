/*******************************************************************************
 * Copyright (C) 2012, François Rey <eclipse.org_@_francois_._rey_._name>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.core.test;

import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.HashSet;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.egit.core.Activator;
import org.eclipse.egit.core.internal.indexdiff.GitResourceDeltaVisitor;
import org.eclipse.egit.core.project.RepositoryMapping;

/**
 * Helper class for test cases that need to know what workspace changes egit
 * will detect. Changes to files within the git repository folder are ignored
 * by default, however this behavior can be changed by a constructor parameter.
 * Usage: create one instance per test case and call {@link #setUp()} and
 * {@link #tearDown()} before and after the test case. Use other functions
 * to test and assert what changed resources are expected.
 * Implementation is mainly an {@link IResourceChangeListener} which calls
 * a {@link GitResourceDeltaVisitor} for each change that occurs in a project
 * that uses egit.
 */
public class GitResourceDeltaTestHelper {
	private IResourceChangeListener resourceChangeListener;

	private final Collection<IResource> changedResources;

	private final boolean ignoreTeamPrivateMember;

	public GitResourceDeltaTestHelper() {
		this(true);
	}

	public GitResourceDeltaTestHelper(boolean ignoreTeamPrivateMember) {
		this.changedResources = new HashSet<IResource>();
		this.ignoreTeamPrivateMember = ignoreTeamPrivateMember;
	}

	public void setUp() {
		resourceChangeListener = new IResourceChangeListener() {
			public void resourceChanged(final IResourceChangeEvent event) {
				try {
					event.getDelta().accept(new IResourceDeltaVisitor() {
						public boolean visit(IResourceDelta delta)
								throws CoreException {
							final IResource resource = delta.getResource();
							IProject project = resource.getProject();
							if (project == null)
								return true;
							RepositoryMapping mapping = RepositoryMapping.getMapping(resource);
							if (mapping == null)
								return true;
							GitResourceDeltaVisitor visitor =
									new GitResourceDeltaVisitor(mapping.getRepository());
							try {
								event.getDelta().accept(visitor);
							} catch (CoreException e) {
								Activator.logError(e.getMessage(), e);
								return false;
							}
							IPath gitDirAbsolutePath = mapping.getGitDirAbsolutePath();
							for (IResource res: visitor.getResourcesToUpdate()) {
								if (ignoreTeamPrivateMember && (res.isTeamPrivateMember() ||
											gitDirAbsolutePath.isPrefixOf(
												res.getRawLocation().makeAbsolute())))
									continue;
								changedResources.add(res);
							}
							return false;
						}
					});
				} catch (CoreException e) {
					Activator.logError(e.getMessage(), e);
					return;
				}
			}
		};
		ResourcesPlugin.getWorkspace().addResourceChangeListener(resourceChangeListener,IResourceChangeEvent.POST_CHANGE);
	}

	public void tearDown() {
		if (resourceChangeListener!=null) {
			ResourcesPlugin.getWorkspace().removeResourceChangeListener(resourceChangeListener);
			resourceChangeListener = null;
		}
	}

	public Collection<IResource> getChangedResources() {
		return changedResources;
	}

	public boolean noChangedResources() {
		return changedResources.isEmpty();
	}

	public boolean anyChangedResources() {
		return !changedResources.isEmpty();
	}

	public void assertChangedResources(String[] expected) {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		for (String file: expected) {
			assertTrue(changedResources.contains(root.findMember(file)));
		}
		assertTrue(changedResources.size()==expected.length);
	}

	public void printChangedResources() {
		if (anyChangedResources())
			System.out.println("Changed resources:");
		else {
			System.out.println("No resources changed.");
			return;
		}
		for (IResource res: changedResources)
			System.out.println("  " + res.toString());
	}
}