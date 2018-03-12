/*******************************************************************************
 * Copyright (C) 2011, Dariusz Luksza <dariusz@luksza.org>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.performance;

import junit.framework.Test;
import junit.framework.TestSuite;

public class AllTests {

	public static Test suite() {
		TestSuite suite = new TestSuite("Performance tests for EGit");
		suite.addTestSuite(SynchronizeWithStagedChangesPerformanceTest.class);
		suite.addTestSuite(SynchronizeWithoutLocalChangesPerformanceTest.class);
		suite.addTestSuite(SynchronizeWithWorkingTreeChangesPerformanceTest.class);

		return suite;
	}

}
