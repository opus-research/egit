package org.eclipse.egit.ui.internal.dialogs;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.util.Arrays;

import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.URLHyperlinkDetector;
import org.eclipse.jface.text.rules.IToken;
import org.eclipse.jface.text.source.ISourceViewer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class HyperlinkTokenScannerTests {

	@Mock
	private ISourceViewer viewer;

	private IHyperlinkDetector[] detectors = new IHyperlinkDetector[] {
			new URLHyperlinkDetector() };

	@Test
	public void tokenizeEmpty() {
		String testString = "";
		String expected = "";
		assertTokens(testString, 0, testString.length(), expected);
	}

	@Test
	public void tokenizeNoHyperlinks() {
		String testString = "hello world";
		String expected = "DDDDDDDDDDD";
		assertTokens(testString, 0, testString.length(), expected);
	}

	@Test
	public void tokenizeLeadingHyperlink() {
		String testString = "http://foo bar";
		String expected = "HHHHHHHHHHDDDD";
		assertTokens(testString, 0, testString.length(), expected);
	}

	@Test
	public void tokenizeEmbeddedHyperlink() {
		String testString = "Link: http://foo bar";
		String expected = "DDDDDDHHHHHHHHHHDDDD";
		assertTokens(testString, 0, testString.length(), expected);
	}

	@Test
	public void tokenizeMultipleHyperlinksSimple() {
		String testString = "Link: http://foo http://www.example.com bar";
		String expected = "DDDDDDHHHHHHHHHHDHHHHHHHHHHHHHHHHHHHHHHDDDD";
		assertTokens(testString, 0, testString.length(), expected);
	}

	@Test
	public void tokenizeMultipleHyperlinksMultiline() {
		String testString = "Link: http://foo\n\n* http://foo\n* ftp://somewhere\n\nTwo links above.";
		String expected = "DDDDDDHHHHHHHHHHDDDDHHHHHHHHHHDDDHHHHHHHHHHHHHHHDDDDDDDDDDDDDDDDDD";
		assertTokens(testString, 0, testString.length(), expected);
	}

	@Test
	public void tokenizeHyperlinksOutsideRegion() {
		String testString = "Link: http://foo\n\n* http://foo\n* ftp://somewhere\n\nTwo links above.";
		String expected = "DDDDD                                                             ";
		assertTokens(testString, 0, 5, expected);
		expected = "                                                  DDDDDDDDDDDD    ";
		assertTokens(testString, 50, 12, expected);
	}

	@Test
	public void tokenizeHyperlinksCutByRegion() {
		String testString = "Link: http://foo\n\n* http://foo\n* ftp://somewhere\n\nTwo links above.";
		String expected = "DDDDDDHHHHHHHH                                                    ";
		assertTokens(testString, 0, 14, expected);
		expected = "               HDDDDHHHHHHHHH                                     ";
		assertTokens(testString, 15, 14, expected);
	}

	private void assertTokens(String text, int offset, int length,
			String expected) {
		assertEquals("Test definition problem: 'expected' length mismatch",
				text.length(), expected.length());
		IDocument testDocument = new Document(text);
		when(viewer.getDocument()).thenReturn(testDocument);
		HyperlinkTokenScanner scanner = new HyperlinkTokenScanner(detectors,
				viewer);
		scanner.setRange(testDocument, offset, length);
		IToken token = null;
		char[] found = new char[text.length()];
		Arrays.fill(found, ' ');
		while (!(token = scanner.nextToken()).isEOF()) {
			int tokenOffset = scanner.getTokenOffset();
			int tokenLength = scanner.getTokenLength();
			char ch = 'x';
			if (token == HyperlinkTokenScanner.HYPERLINK) {
				ch = 'H';
			} else if (token == HyperlinkTokenScanner.DEFAULT) {
				ch = 'D';
			}
			Arrays.fill(found, tokenOffset, tokenOffset + tokenLength, ch);
		}
		assertEquals("Unexpected tokens", expected, new String(found));
	}

}
