/*******************************************************************************
 * Copyright (C) 2010, Robin Stocker
 * Copyright (C) 2011, Matthias Sohn <matthias.sohn@sap.com>
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.egit.ui.internal.dialogs;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.eclipse.egit.ui.internal.dialogs.SpellcheckableMessageArea;
import org.eclipse.egit.ui.internal.dialogs.SpellcheckableMessageArea.WrapEdit;
import org.junit.Test;

public class SpellcheckableMessageAreaTest {

	@Test
	public void dontWrapShortText() {
		String input = "short message";
		assertWrappedEquals(input, input);
	}

	@Test
	public void dontWrapAlreadyWrappedText() {
		String input = "This is a test of wrapping\n\nDid it work?\n\nHm?";
		assertWrappedEquals(input, input);
	}

	@Test
	public void dontWrapMaximumLengthText() {
		String input = "123456789 123456789 123456789 123456789 123456789 123456789 123456789 12";
		assertWrappedEquals(input, input);
	}

	@Test
	public void wrapOverlengthText() {
		String input = "123456789 123456789 123456789 123456789 123456789 123456789 123456789 12 3";
		String expected = "123456789 123456789 123456789 123456789 123456789 123456789 123456789 12\n3";
		assertWrappedEquals(expected, input);
	}

	@Test
	public void wrapOverlengthTextByOne() {
		String input = "123456789 123456789 123456789 123456789 123456789 123456789 123456789.abc";
		String expected = "123456789 123456789 123456789 123456789 123456789 123456789\n123456789.abc";
		assertWrappedEquals(expected, input);
	}

	@Test
	public void wrapOverlengthText2() {
		String input = "123456789 123456789 123456789 123456789 123456789 123456789. 1234567890123456";
		String expected = "123456789 123456789 123456789 123456789 123456789 123456789.\n1234567890123456";
		assertWrappedEquals(expected, input);
	}

	public void wrapOverlengthTextTwice() {
		String input = "123456789 123456789 123456789 123456789 123456789 123456789 123456789.12 "
				+ "123456789 123456789 123456789 123456789 123456789 123456789 123456789.12 "
				+ "123456789 123456789 123456789 123456789 123456789 123456789 123456789.12";
		String expected = "123456789 123456789 123456789 123456789 123456789 123456789 123456789.12\n"
				+ "123456789 123456789 123456789 123456789 123456789 123456789 123456789.12\n"
				+ "123456789 123456789 123456789 123456789 123456789 123456789 123456789.12";
		assertWrappedEquals(expected, input);
	}

	@Test
	public void dontWrapWordLongerThanOneLineAtStart() {
		String input = "1234567890123456789012345678901234567890123456789012345678901234567890123456789012 the previous was longer than a line";
		String expected = "1234567890123456789012345678901234567890123456789012345678901234567890123456789012\nthe previous was longer than a line";
		assertWrappedEquals(expected, input);
	}

	@Test
	public void dontWrapWordLongerThanOneLine() {
		String input = "This has to be on its own line: 1234567890123456789012345678901234567890123456789012345678901234567890123456789012 this not";
		String expected = "This has to be on its own line:\n1234567890123456789012345678901234567890123456789012345678901234567890123456789012\nthis not";
		assertWrappedEquals(expected, input);
	}

	@Test
	public void wrapSecondLongLine() {
		String input = "First line\n123456789 123456789 123456789 123456789 123456789 123456789 123456789 123456789.12";
		String expected = "First line\n123456789 123456789 123456789 123456789 123456789 123456789 123456789\n123456789.12";
		assertWrappedEquals(expected, input);
	}

	@Test
	public void keepExistingNewlines() {
		String input = "This\n\nis\nall\nok\n123456789 123456789 123456789 123456789 123456789 123456789 123456789.12";
		assertWrappedEquals(input, input);
	}

	@Test
	public void keepNewlineAtEnd() {
		String input = "Newline\nat\nend\n";
		assertWrappedEquals(input, input);
	}

	@Test
	public void keepWhitespace() {
		String input = "  this   is     deliberate whitespace";
		assertWrappedEquals(input, input);
	}

	@Test
	public void keepTrailingSpace() {
		String input = "space at end ";
		assertWrappedEquals(input, input);
	}

	@Test
	public void lineAfterWrappedWordShouldBeJoined() {
		String input = "000000001 000000002 000000003 000000004 000000005 000000006 000000007 000000008\n000000009";
		String expected = "000000001 000000002 000000003 000000004 000000005 000000006 000000007\n000000008 000000009";
		assertWrappedEquals(expected, input);
	}

	@Test
	public void lineAfterWrappedWordShouldBeJoinedAndJoinedLineWrapCorrectly() {
		String input = "000000001 000000002 000000003 000000004 000000005 000000006 000000007 000000008\n"
				+ "000000009 000000010 000000011 000000012 000000013 000000014 000000015 000000016";
		String expected = "000000001 000000002 000000003 000000004 000000005 000000006 000000007\n"
				+ "000000008 000000009 000000010 000000011 000000012 000000013 000000014\n000000015 000000016";
		assertWrappedEquals(expected, input);
	}

	@Test
	public void lineAfterWrappedLineShouldBeJoinedAndFollowingLineWrappedCorrectly() {
		String input = "000000001 000000002 000000003 000000004 000000005 000000006 000000007 "
				+ "000000008 000000009 000000010 000000011 000000012 000000013 000000014\n"
				+ "000000015 000000016 000000017 000000018 000000019 000000020 000000021";
		String expected = "000000001 000000002 000000003 000000004 000000005 000000006 000000007\n"
				+ "000000008 000000009 000000010 000000011 000000012 000000013 000000014\n"
				+ "000000015 000000016 000000017 000000018 000000019 000000020 000000021";
		assertWrappedEquals(expected, input);
	}

	@Test
	public void lineAfterWrappedWordShouldNotBeJoinedIfItsEmpty() {
		String input = "000000001 000000002 000000003 000000004 000000005 000000006 000000007 000000008\n\nNew paragraph";
		String expected = "000000001 000000002 000000003 000000004 000000005 000000006 000000007\n000000008\n\nNew paragraph";
		assertWrappedEquals(expected, input);
	}

	@Test
	public void lineAfterWrappedWordShouldNotBeJoinedIfItStartsWithASymbol() {
		String input = "* 000000001 000000002 000000003 000000004 000000005 000000006 000000007 000000008\n* Bullet 2";
		String expected = "* 000000001 000000002 000000003 000000004 000000005 000000006 000000007\n000000008\n* Bullet 2";
		assertWrappedEquals(expected, input);
	}

	@Test
	public void lineAfterWrappedWordShouldBeJoinedIfItStartsWithAParenthesis() {
		String input = "* 000000001 000000002 000000003 000000004 000000005 000000006 000000007 000000008\n(paren)";
		String expected = "* 000000001 000000002 000000003 000000004 000000005 000000006 000000007\n000000008 (paren)";
		assertWrappedEquals(expected, input);
	}


	private static void assertWrappedEquals(String expected, String input) {
		assertWrappedEqualsOnUnix(expected, input);
		assertWrappedEqualsOnWindows(expected, input);
	}

	private static void assertWrappedEqualsOnUnix(String expected, String input) {
		String wrapped = wrap(input, "\n");
		assertEquals(expected, wrapped);
	}

	private static void assertWrappedEqualsOnWindows(String expected,
			String input) {
		String wrapped = wrap(input.replaceAll("\n", "\r\n"), "\r\n");
		assertEquals(expected.replaceAll("\n", "\r\n"), wrapped);
	}

	private static String wrap(String text, String lineDelimiter) {
		StringBuilder sb = new StringBuilder(text);
		List<WrapEdit> wrapEdits = SpellcheckableMessageArea
				.calculateWrapEdits(text,
						SpellcheckableMessageArea.MAX_LINE_WIDTH, lineDelimiter);
		for (WrapEdit wrapEdit : wrapEdits) {
			sb.replace(wrapEdit.getStart(),
					wrapEdit.getStart() + wrapEdit.getLength(), wrapEdit.getReplacement());
		}
		return sb.toString();
	}
}
