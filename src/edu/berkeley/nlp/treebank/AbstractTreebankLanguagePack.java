package edu.berkeley.nlp.treebank;

import edu.berkeley.nlp.util.Filter;
import edu.berkeley.nlp.util.Filters;

import java.io.Serializable;

/**
 * This provides an implementation of parts of the TreebankLanguagePack API to
 * reduce the load on fresh implementations. Only the abstract methods below
 * need to be implemented to give a reasonable solution for a new language.
 * 
 * @author Christopher Manning
 * @version 1.1
 */
public abstract class AbstractTreebankLanguagePack implements
		TreebankLanguagePack, Serializable {

	/**
	 * Use this as the default encoding for Readers and Writers of Treebank
	 * data.
	 */
	public static final String DEFAULT_ENCODING = "UTF-8";

	/**
	 * Gives a handle to the TreebankLanguagePack
	 */
	public AbstractTreebankLanguagePack() {
	}

	/**
	 * Returns a String array of punctuation tags for this treebank/language.
	 * 
	 * @return The punctuation tags
	 */
	public abstract String[] punctuationTags();

	/**
	 * Returns a String array of punctuation words for this treebank/language.
	 * 
	 * @return The punctuation words
	 */
	public abstract String[] punctuationWords();

	/**
	 * Returns a String array of sentence final punctuation tags for this
	 * treebank/language.
	 * 
	 * @return The sentence final punctuation tags
	 */
	public abstract String[] sentenceFinalPunctuationTags();

	/**
	 * Returns a String array of punctuation tags that EVALB-style evaluation
	 * should ignore for this treebank/language. Traditionally, EVALB has
	 * ignored a subset of the total set of punctuation tags in the English Penn
	 * Treebank (quotes and period, comma, colon, etc., but not brackets)
	 * 
	 * @return Whether this is a EVALB-ignored punctuation tag
	 */
	public String[] evalBIgnoredPunctuationTags() {
		return punctuationTags();
	}

	/**
	 * Accepts a String that is a punctuation tag name, and rejects everything
	 * else.
	 * 
	 * @return Whether this is a punctuation tag
	 */
	public boolean isPunctuationTag(String str) {
		return punctTagStringAcceptFilter.accept(str);
	}

	/**
	 * Accepts a String that is a punctuation word, and rejects everything else.
	 * If one can't tell for sure (as for ' in the Penn Treebank), it maks the
	 * best guess that it can.
	 * 
	 * @return Whether this is a punctuation word
	 */
	public boolean isPunctuationWord(String str) {
		return punctWordStringAcceptFilter.accept(str);
	}

	/**
	 * Accepts a String that is a sentence end punctuation tag, and rejects
	 * everything else.
	 * 
	 * @return Whether this is a sentence final punctuation tag
	 */
	public boolean isSentenceFinalPunctuationTag(String str) {
		return sFPunctTagStringAcceptFilter.accept(str);
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
		return eIPunctTagStringAcceptFilter.accept(str);
	}

	/**
	 * Return a filter that accepts a String that is a punctuation tag name, and
	 * rejects everything else.
	 * 
	 * @return The filter
	 */
	public Filter punctuationTagAcceptFilter() {
		return punctTagStringAcceptFilter;
	}

	/**
	 * Return a filter that rejects a String that is a punctuation tag name, and
	 * rejects everything else.
	 * 
	 * @return The filter
	 */
	public Filter punctuationTagRejectFilter() {
		return Filters.notFilter(punctTagStringAcceptFilter);
	}

	/**
	 * Returns a filter that accepts a String that is a punctuation word, and
	 * rejects everything else. If one can't tell for sure (as for ' in the Penn
	 * Treebank), it makes the best guess that it can.
	 * 
	 * @return The Filter
	 */
	public Filter punctuationWordAcceptFilter() {
		return punctWordStringAcceptFilter;
	}

	/**
	 * Returns a filter that accepts a String that is not a punctuation word,
	 * and rejects punctuation. If one can't tell for sure (as for ' in the Penn
	 * Treebank), it makes the best guess that it can.
	 * 
	 * @return The Filter
	 */
	public Filter punctuationWordRejectFilter() {
		return Filters.notFilter(punctWordStringAcceptFilter);
	}

	/**
	 * Returns a filter that accepts a String that is a sentence end punctuation
	 * tag, and rejects everything else.
	 * 
	 * @return The Filter
	 */
	public Filter sentenceFinalPunctuationTagAcceptFilter() {
		return sFPunctTagStringAcceptFilter;
	}

	/**
	 * Returns a filter that accepts a String that is a punctuation tag that
	 * should be ignored by EVALB-style evaluation, and rejects everything else.
	 * Traditionally, EVALB has ignored a subset of the total set of punctuation
	 * tags in the English Penn Treebank (quotes and period, comma, colon, etc.,
	 * but not brackets)
	 * 
	 * @return The Filter
	 */
	public Filter evalBIgnoredPunctuationTagAcceptFilter() {
		return eIPunctTagStringAcceptFilter;
	}

	/**
	 * Returns a filter that accepts everything except a String that is a
	 * punctuation tag that should be ignored by EVALB-style evaluation.
	 * Traditionally, EVALB has ignored a subset of the total set of punctuation
	 * tags in the English Penn Treebank (quotes and period, comma, colon, etc.,
	 * but not brackets)
	 * 
	 * @return The Filter
	 */
	public Filter evalBIgnoredPunctuationTagRejectFilter() {
		return Filters.notFilter(eIPunctTagStringAcceptFilter);
	}

	/**
	 * Return the input Charset encoding for the Treebank. See documentation for
	 * the <code>Charset</code> class.
	 * 
	 * @return Name of Charset
	 */
	public String getEncoding() {
		return DEFAULT_ENCODING;
	}

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
		return new char[0];
	}

	/**
	 * Returns the index of the first character that is after the basic label.
	 * That is, if category is "NP-LGS", it returns 2. This routine assumes
	 * category != null This routine returns 0 iff the String is of length 0.
	 * This routine always returns a number &lt;= category.length(), and so it
	 * is safe to pass it as an argument to category.substring().
	 */
	private int postBasicCategoryIndex(String category) {
		boolean sawAtZero = false;
		int i = 0;
		for (int leng = category.length(); i < leng; i++) {
			char ch = category.charAt(i);
			if (isLabelAnnotationIntroducingCharacter(ch)) {
				if (i == 0) {
					sawAtZero = true;
				} else if (sawAtZero) {
					sawAtZero = false;
				} else {
					break;
				}
			}
		}
		return i;
	}

	/**
	 * Returns the basic syntactic category of a String. This implementation
	 * basically truncates stuff after an occurrence of one of the
	 * <code>labelAnnotationIntroducingCharacters()</code>. However, there is
	 * also special case stuff to deal with labelAnnotationIntroducingCharacters
	 * in category labels: (i) if the first char is in this set, it's never
	 * truncated (e.g., '-' or '=' as a token), and (ii) if it starts with one
	 * ofthis set, a second item of this set is also excluded (to deal with
	 * '-LLB-', '-RCB-', etc.).
	 * 
	 * @param category
	 *            The whole String name of the label
	 * @return The basic category of the String
	 */
	public String basicCategory(String category) {
		if (category == null) {
			return null;
		}

		return category.substring(0, postBasicCategoryIndex(category));
	}

	/**
	 * Returns the syntactic category and 'function' of a String. This normally
	 * involves truncating numerical coindexation showing coreference, etc. By
	 * 'function', this means keeping, say, Penn Treebank functional tags or ICE
	 * phrasal functions, perhaps returning them as
	 * <code>category-function</code>.
	 * <p/>
	 * This implementation strips numeric tags after label introducing
	 * characters (assuming that non-numeric things are functional tags).
	 * 
	 * @param category
	 *            The whole String name of the label
	 * @return A String giving the category and function
	 */
	public String categoryAndFunction(String category) {
		if (category == null) {
			return null;
		}
		String catFunc = category.substring(0);
		int i = lastIndexOfNumericTag(catFunc);
		while (i >= 0) {
			catFunc = catFunc.substring(0, i);
			i = lastIndexOfNumericTag(catFunc);
		}
		return catFunc;
	}

	/**
	 * Returns the index within this string of the last occurrence of a
	 * isLabelAnnotationIntroducingCharacter which is followed by only digits,
	 * corresponding to a numeric tag at the end of the string. Example:
	 * <code>lastIndexOfNumericTag("NP-TMP-1") returns
	 * 6</code>.
	 */
	private int lastIndexOfNumericTag(String category) {
		if (category == null) {
			return -1;
		}
		int last = -1;
		for (int i = category.length() - 1; i >= 0; i--) {
			if (isLabelAnnotationIntroducingCharacter(category.charAt(i))) {
				boolean onlyDigitsFollow = false;
				for (int j = i + 1; j < category.length(); j++) {
					onlyDigitsFollow = true;
					if (!(Character.isDigit(category.charAt(j)))) {
						onlyDigitsFollow = false;
						break;
					}
				}
				if (onlyDigitsFollow) {
					last = i;
				}
			}
		}
		return last;
	}

	/**
	 * Say whether this character is an annotation introducing character.
	 * 
	 * @param ch
	 *            The character to check
	 * @return Whether it is an annotation introducing character
	 */
	public boolean isLabelAnnotationIntroducingCharacter(char ch) {
		char[] cutChars = labelAnnotationIntroducingCharacters();
		for (int i = 0; i < cutChars.length; i++) {
			if (ch == cutChars[i]) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Accepts a String that is a start symbol of the treebank.
	 * 
	 * @return Whether this is a start symbol
	 */
	public boolean isStartSymbol(String str) {
		return startSymbolAcceptFilter.accept(str);
	}

	/**
	 * Return a filter that accepts a String that is a start symbol of the
	 * treebank, and rejects everything else.
	 * 
	 * @return The filter
	 */
	public Filter startSymbolAcceptFilter() {
		return startSymbolAcceptFilter;
	}

	/**
	 * Returns a String array of treebank start symbols.
	 * 
	 * @return The start symbols
	 */
	public abstract String[] startSymbols();

	/**
	 * Returns a String which is the first (perhaps unique) start symbol of the
	 * treebank, or null if none is defined.
	 * 
	 * @return The start symbol
	 */
	public String startSymbol() {
		String[] ssyms = startSymbols();
		if (ssyms == null || ssyms.length == 0) {
			return null;
		}
		return ssyms[0];
	}

	private final Filter punctTagStringAcceptFilter = Filters
			.collectionAcceptFilter(punctuationTags());

	private final Filter punctWordStringAcceptFilter = Filters
			.collectionAcceptFilter(punctuationWords());

	private final Filter sFPunctTagStringAcceptFilter = Filters
			.collectionAcceptFilter(sentenceFinalPunctuationTags());

	private final Filter eIPunctTagStringAcceptFilter = Filters
			.collectionAcceptFilter(evalBIgnoredPunctuationTags());

	private final Filter startSymbolAcceptFilter = Filters
			.collectionAcceptFilter(startSymbols());

	/**
	 * So changed versions deserialize correctly.
	 */
	private static final long serialVersionUID = -6506749780512708352L;

}
