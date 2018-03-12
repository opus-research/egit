package org.eclipse.egit.core.mergestrategy;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IExecutableExtensionFactory;
import org.eclipse.jgit.merge.MergeStrategy;

/**
 * This is used to provide the "OURS" MergeStrategy as a registered
 * MergeStrategy.
 */
public class MergeStrategyExtensionFactory implements
		IExecutableExtensionFactory {

	@Override
	public Object create() throws CoreException {
		return MergeStrategy.OURS;
	}
}
