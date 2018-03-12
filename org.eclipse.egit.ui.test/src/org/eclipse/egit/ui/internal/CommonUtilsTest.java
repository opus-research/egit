/*******************************************************************************
 * Copyright (C) 2011, Robin Stocker <robin@nibor.org>
 * Copyright (C) 2013, Michael Keppler <michael.keppler@gmx.de>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

import org.junit.Test;

/**
 * Tests for {@link CommonUtils}.
 */
public class CommonUtilsTest {
	@Test
	public void sortingShouldWorkForEasyStrings() {
		assertSortedLike("", "");
		assertSortedLike("", "a");
		assertSortedLike("a", "asdf");
		assertSortedLike("aaa", "bbb");
		assertSortedLike("1", "2");
	}

	@Test
	public void sortingShouldWorkForEqualAndEmptyStrings() {
		assertEquals(0, CommonUtils.STRING_ASCENDING_COMPARATOR.compare("", ""));
		assertEquals(0,
				CommonUtils.STRING_ASCENDING_COMPARATOR.compare("a", "a"));
		assertTrue(CommonUtils.STRING_ASCENDING_COMPARATOR.compare("", "a") < 0);
		assertTrue(CommonUtils.STRING_ASCENDING_COMPARATOR.compare("a", "") > 0);
	}

	@Test
	public void sortingShouldWorkForNumbers() {
		assertSortedLike("2", "10", "100");
	}

	@Test
	public void sortingShouldWorkForMixedParts() {
		assertSortedLike("v1c", "v2b", "v10a");
		assertSortedLike("asdf", "asdf2", "asdf10");
		assertSortedLike("1_1", "1_10");
		assertSortedLike("project-1-0-0-final", "project-1-0-1-beta");
		assertSortedLike("1-a", "01-b");
		assertSortedLike("1", "asdf-2", "asdf-10", "b20");
	}

	@Test
	public void sortingShouldWorkForBigNumbers() {
		assertSortedLike("100000000", "100000000000", "100000000000");
	}

	@Test
	public void sortingShouldIgnoreLeadingZeros() {
		assertSortedLike("00001", "2", "3");
		assertSortedLike("a-01", "a-002");

		assertNotEquals(0,
				CommonUtils.STRING_ASCENDING_COMPARATOR.compare("01", "1"));
		assertNotEquals(0,
				CommonUtils.STRING_ASCENDING_COMPARATOR.compare("1", "01"));
		assertTrue(CommonUtils.STRING_ASCENDING_COMPARATOR.compare("01x", "1") > 0);
		assertTrue(CommonUtils.STRING_ASCENDING_COMPARATOR.compare("01", "1x") < 0);
	}

	@Test
	public void sortingShouldIgnoreCase() {
		assertSortedLike("a", "b", "z");
		assertSortedLike("a", "B", "c", "D");

		assertNotEquals(0,
				CommonUtils.STRING_ASCENDING_COMPARATOR.compare("b1", "B1"));
	}

	/**
	 * Assert that sorting the given strings keeps the same order as passed.
	 *
	 * @param inputs
	 */
	private void assertSortedLike(String... inputs) {
		List<String> expected = Arrays.asList(inputs);
		List<String> tmp = new ArrayList<String>(expected);
		Collections.shuffle(tmp, new Random(1));
		Collections.sort(tmp, CommonUtils.STRING_ASCENDING_COMPARATOR);
		assertEquals(expected, tmp);

		List<String> expectedWithoutDuplicates = new ArrayList<String>(
				new LinkedHashSet<String>(expected));
		List<String> shuffeled = new ArrayList<String>(expected);
		Collections.shuffle(shuffeled, new Random(1));
		TreeSet<String> sortedSet = new TreeSet<String>(
				CommonUtils.STRING_ASCENDING_COMPARATOR);
		sortedSet.addAll(shuffeled);
		assertEquals(expectedWithoutDuplicates,
				new ArrayList<String>(sortedSet));
	}
}
