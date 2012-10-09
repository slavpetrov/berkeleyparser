package edu.berkeley.nlp.treebank;

import java.io.Serializable;

import edu.berkeley.nlp.tokenizer.TokenizerFactory;
import edu.berkeley.nlp.util.Filter;
import edu.berkeley.nlp.util.Filters;

/**
 * Language pack for Chinese treebank. (Look into using native2ascii to edit
 * this file as a GB file)
 * 
 * @author Roger Levy
 */

public class ChineseTreebankLanguagePack extends AbstractTreebankLanguagePack
		implements Serializable {

	private static TokenizerFactory tf;

	public static void setTokenizerFactory(TokenizerFactory tf) {
		ChineseTreebankLanguagePack.tf = tf;
	}

	public static final String ENCODING = "GB18030";

	/**
	 * Return the input Charset encoding for the Treebank. See documentation for
	 * the <code>Charset</code> class.
	 * 
	 * @return Name of Charset
	 */
	public String getEncoding() {
		return ENCODING;
	}

	/**
	 * Accepts a String that is a punctuation tag name, and rejects everything
	 * else.
	 * 
	 * @return Whether this is a punctuation tag
	 */
	public boolean isPunctuationTag(String str) {
		return str.equals("PU");
	}

	/**
	 * Accepts a String that is a punctuation word, and rejects everything else.
	 * If one can't tell for sure (as for ' in the Penn Treebank), it maks the
	 * best guess that it can.
	 * 
	 * @return Whether this is a punctuation word
	 */
	public boolean isPunctuationWord(String str) {
		return chineseCommaAcceptFilter().accept(str)
				|| chineseEndSentenceAcceptFilter().accept(str)
				|| chineseDouHaoAcceptFilter().accept(str)
				|| chineseQuoteMarkAcceptFilter().accept(str)
				|| chineseParenthesisAcceptFilter().accept(str)
				|| chineseColonAcceptFilter().accept(str)
				|| chineseDashAcceptFilter().accept(str)
				|| chineseOtherAcceptFilter().accept(str);

	}

	/**
	 * Accepts a String that is a sentence end punctuation tag, and rejects
	 * everything else.
	 * 
	 * @return Whether this is a sentence final punctuation tag
	 */
	public boolean isSentenceFinalPunctuationTag(String str) {
		return chineseEndSentenceAcceptFilter().accept(str);
	}

	/**
	 * Returns a String array of punctuation tags for this treebank/language.
	 * 
	 * @return The punctuation tags
	 */
	public String[] punctuationTags() {
		return tags;
	}

	/**
	 * Returns a String array of punctuation words for this treebank/language.
	 * 
	 * @return The punctuation words
	 */
	public String[] punctuationWords() {
		return punctWords;
	}

	/**
	 * Returns a String array of sentence final punctuation tags for this
	 * treebank/language.
	 * 
	 * @return The sentence final punctuation tags
	 */
	public String[] sentenceFinalPunctuationTags() {
		return tags;
	}

	/**
	 * Returns a String array of sentence final punctuation words for this
	 * treebank/language.
	 * 
	 * @return The sentence final punctuation tags
	 */
	public String[] sentenceFinalPunctuationWords() {
		return endSentence;
	}

	/**
	 * Accepts a String that is a punctuation tag that should be ignored by
	 * EVALB-style evaluation, and rejects everything else. Traditionally, EVALB
	 * has ignored a subset of the total set of punctuation tags in the English
	 * Penn Treebank (quotes and period, comma, colon, etc., but not brackets)
	 * 
	 * @return Whether this is a EVALB-ignored punctuation tag
	 */
	public boolean isEvalBIgnoredPunctuationTag(String str) {
		return Filters.collectionAcceptFilter(tags).accept(str);
	}

	/**
	 * The first 3 are used by the Penn Treebank; # is used by the BLLIP corpus,
	 * and ^ and ~ are used by Klein's lexparser. Identical to
	 * PennTreebankLanguagePack.
	 */
	private static final char[] annotationIntroducingChars = { '-', '=', '|',
			'#', '^', '~' };

	/**
	 * Return an array of characters at which a String should be truncated to
	 * give the basic syntactic category of a label. The idea here is that Penn
	 * treebank style labels follow a syntactic category with various functional
	 * and crossreferencing information introduced by special characters (such
	 * as "NP-SBJ=1"). This would be truncated to "NP" by the array containing
	 * '-' and "=".
	 * 
	 * @return An array of characters that set off label name suffixes
	 */
	public char[] labelAnnotationIntroducingCharacters() {
		return annotationIntroducingChars;
	}

	/**
	 * This is valid for "BobChrisTreeNormalizer" conventions only. Again,
	 * identical to PennTreebankLanguagePack.
	 */
	private static final String[] startSymbols = { "ROOT" };

	/**
	 * Returns a String array of treebank start symbols.
	 * 
	 * @return The start symbols
	 */
	public String[] startSymbols() {
		return startSymbols;
	}

	private static final String[] tags = { "PU" };
	private static final String[] comma = { ",", "\uff0c", "\u3000" }; // \u3000
																		// is an
																		// "ideographic space"...?
	private static final String[] endSentence = { "\u3002", "\uff0e", "\uff01",
			"\uff1f", "?", "!", "." };
	private static final String[] douHao = { "\u3001" };
	private static final String[] quoteMark = { "\u201c", "\u201d", "\u2018",
			"\u2019", "\u300a", "\u300b", "\u300e", "\u300f", "\u3008",
			"\u3009", "\u300c", "\u300d", "\uff02", "\uff1c", "\uff1e", "`",
			"\uff07" };
	private static final String[] parenthesis = { "\uff08", "\uff09", "-LRB-",
			"-RRB-", "\u3010", "\u3011" };
	private static final String[] colon = { "\uff1a", "\uff1b", "\u2236", ":" };
	private static final String[] dash = { "\u2026", "\u2014", "\u2014\u2014",
			"\u2014\u2014\u2014", "\uff0d", "\uff0d\uff0d", "\u2500\u2500",
			"\u2501", "\u2501\u2501", "\u2014\uff0d", "-", "----", "~",
			"\u2026\u2026", "\uff5e" };
	private static final String[] other = { "\u00b7", "\uff0f", "\uff0f",
			"\uff0a", "\uff06", "/", "//", "*" }; // slashes are used in urls

	private static String[] leftQuoteMark = { "\u201c", "\u2018", "\u300a",
			"\u300e", "\u3008", "\u300c", "\uff1c", "`" };
	private static String[] rightQuoteMark = { "\u201d", "\u2019", "\u300b",
			"\u300f", "\u3009", "\u300d", "\uff1e", "\uff07" };
	private static String[] leftParenthesis = { "\uff08", "-LRB-", "\u3010" };
	private static String[] rightParenthesis = { "\uff09", "-RRB-", "\u3011" };

	private static final String[] punctWords;

	static {
		int n = tags.length + comma.length + endSentence.length + douHao.length
				+ quoteMark.length + parenthesis.length + colon.length
				+ dash.length + other.length;
		punctWords = new String[n];
		int m = 0;
		System.arraycopy(tags, 0, punctWords, m, tags.length);
		m += tags.length;
		System.arraycopy(comma, 0, punctWords, m, comma.length);
		m += comma.length;
		System.arraycopy(endSentence, 0, punctWords, m, endSentence.length);
		m += endSentence.length;
		System.arraycopy(douHao, 0, punctWords, m, douHao.length);
		m += douHao.length;
		System.arraycopy(quoteMark, 0, punctWords, m, quoteMark.length);
		m += quoteMark.length;
		System.arraycopy(parenthesis, 0, punctWords, m, parenthesis.length);
		m += parenthesis.length;
		System.arraycopy(colon, 0, punctWords, m, colon.length);
		m += colon.length;
		System.arraycopy(dash, 0, punctWords, m, dash.length);
		m += dash.length;
		System.arraycopy(other, 0, punctWords, m, other.length);
	}

	public static Filter<String> chineseCommaAcceptFilter() {
		return Filters.collectionAcceptFilter(comma);
	}

	public static Filter<String> chineseEndSentenceAcceptFilter() {
		return Filters.collectionAcceptFilter(endSentence);
	}

	public static Filter<String> chineseDouHaoAcceptFilter() {
		return Filters.collectionAcceptFilter(douHao);
	}

	public static Filter<String> chineseQuoteMarkAcceptFilter() {
		return Filters.collectionAcceptFilter(quoteMark);
	}

	public static Filter<String> chineseParenthesisAcceptFilter() {
		return Filters.collectionAcceptFilter(parenthesis);
	}

	public static Filter<String> chineseColonAcceptFilter() {
		return Filters.collectionAcceptFilter(colon);
	}

	public static Filter<String> chineseDashAcceptFilter() {
		return Filters.collectionAcceptFilter(dash);
	}

	public static Filter<String> chineseOtherAcceptFilter() {
		return Filters.collectionAcceptFilter(other);
	}

	public static Filter<String> chineseLeftParenthesisAcceptFilter() {
		return Filters.collectionAcceptFilter(leftParenthesis);
	}

	public static Filter<String> chineseRightParenthesisAcceptFilter() {
		return Filters.collectionAcceptFilter(rightParenthesis);
	}

	public static Filter<String> chineseLeftQuoteMarkAcceptFilter() {
		return Filters.collectionAcceptFilter(leftQuoteMark);
	}

	public static Filter<String> chineseRightQuoteMarkAcceptFilter() {
		return Filters.collectionAcceptFilter(rightQuoteMark);
	}

	/**
	 * Returns the extension of treebank files for this treebank. This is "fid".
	 */
	public String treebankFileExtension() {
		return "fid";
	}

	private static final long serialVersionUID = 5757403475523638802L;

}
