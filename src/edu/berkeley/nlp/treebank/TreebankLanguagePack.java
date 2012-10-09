package edu.berkeley.nlp.treebank;

import java.io.Serializable;

/**
 * This interface specifies language/treebank specific information for a
 * Treebank, which a parser or other treebank user might need to know.
 * <p>
 * <p/>
 * Some of this is fixed for a (treebank,language) pair, but some of it reflects
 * feature extraction decisions, so it can be sensible to have multiple
 * implementations of this interface for the same (treebank,language) pair.
 * <p>
 * <p/>
 * So far this covers punctuation, character encodings, and characters reserved
 * for label annotations. It should probably be expanded to cover other stuff
 * (unknown words?).
 * <p>
 * <p/>
 * Various methods in this class return arrays. You should treat them as
 * read-only, even though one cannot enforce that in Java.
 * <p>
 * <p/>
 * Implementations of this method do not call basicCategory() on arguments
 * before testing them, so if needed, you should explicitly call basicCategory()
 * yourself before passing arguments to these routines for testing.
 * 
 * @author Christopher Manning
 * @version 1.1, Mar 2003
 */
public interface TreebankLanguagePack extends Serializable {

	/**
	 * Use this as the default encoding for Readers and Writers of Treebank
	 * data.
	 */
	public static final String DEFAULT_ENCODING = "UTF-8";

	/**
	 * Accepts a String that is a punctuation tag name, and rejects everything
	 * else.
	 * 
	 * @return Whether this is a punctuation tag
	 */
	public boolean isPunctuationTag(String str);

	/**
	 * Accepts a String that is a punctuation word, and rejects everything else.
	 * If one can't tell for sure (as for ' in the Penn Treebank), it maks the
	 * best guess that it can.
	 * 
	 * @return Whether this is a punctuation word
	 */
	public boolean isPunctuationWord(String str);

	/**
	 * Accepts a String that is a sentence end punctuation tag, and rejects
	 * everything else.
	 * 
	 * @return Whether this is a sentence final punctuation tag
	 */
	public boolean isSentenceFinalPunctuationTag(String str);

	/**
	 * Accepts a String that is a punctuation tag that should be ignored by
	 * EVALB-style evaluation, and rejects everything else. Traditionally, EVALB
	 * has ignored a subset of the total set of punctuation tags in the English
	 * Penn Treebank (quotes and period, comma, colon, etc., but not brackets)
	 * 
	 * @return Whether this is a EVALB-ignored punctuation tag
	 */
	public boolean isEvalBIgnoredPunctuationTag(String str);

	/**
	 * Returns a String array of punctuation tags for this treebank/language.
	 * 
	 * @return The punctuation tags
	 */
	public String[] punctuationTags();

	/**
	 * Returns a String array of punctuation words for this treebank/language.
	 * 
	 * @return The punctuation words
	 */
	public String[] punctuationWords();

	/**
	 * Returns a String array of sentence final punctuation tags for this
	 * treebank/language.
	 * 
	 * @return The sentence final punctuation tags
	 */
	public String[] sentenceFinalPunctuationTags();

	/**
	 * Returns a String array of sentence final punctuation words for this
	 * treebank/language.
	 * 
	 * @return The punctuation words
	 */
	public String[] sentenceFinalPunctuationWords();

	/**
	 * Returns a String array of punctuation tags that EVALB-style evaluation
	 * should ignore for this treebank/language. Traditionally, EVALB has
	 * ignored a subset of the total set of punctuation tags in the English Penn
	 * Treebank (quotes and period, comma, colon, etc., but not brackets)
	 * 
	 * @return Whether this is a EVALB-ignored punctuation tag
	 */
	public String[] evalBIgnoredPunctuationTags();

	/**
	 * Return the charset encoding of the Treebank. See documentation for the
	 * <code>Charset</code> class.
	 * 
	 * @return Name of Charset
	 */
	public String getEncoding();

	/**
	 * Return an array of characters at which a String should be truncated to
	 * give the basic syntactic category of a label. The idea here is that Penn
	 * treebank style labels follow a syntactic category with various functional
	 * and crossreferencing information introduced by special characters (such
	 * as "NP-SBJ=1"). This would be truncated to "NP" by the array containing
	 * '-' and "=". <br>
	 * Note that these are never deleted as the first character as a label (so
	 * they are okay as one character tags, etc.), but only when subsequent
	 * characters.
	 * 
	 * @return An array of characters that set off label name suffixes
	 */
	public char[] labelAnnotationIntroducingCharacters();

	/**
	 * Say whether this character is an annotation introducing character.
	 */
	public boolean isLabelAnnotationIntroducingCharacter(char ch);

	/**
	 * Returns the basic syntactic category of a String by truncating stuff
	 * after a (non-word-initial) occurrence of one of the
	 * <code>labelAnnotationIntroducingCharacters()</code>. This function should
	 * work on phrasal category and POS tag labels, but needn't (and couldn't be
	 * expected to) work on arbitrary Word strings.
	 * 
	 * @param category
	 *            The whole String name of the label
	 * @return The basic category of the String
	 */
	public String basicCategory(String category);

	/**
	 * Returns the syntactic category and 'function' of a String. This normally
	 * involves truncating numerical coindexation showing coreference, etc. By
	 * 'function', this means keeping, say, Penn Treebank functional tags or ICE
	 * phrasal functions, perhaps returning them as
	 * <code>category-function</code>.
	 * 
	 * @param category
	 *            The whole String name of the label
	 * @return A String giving the category and function
	 */
	public String categoryAndFunction(String category);

	/**
	 * Accepts a String that is a start symbol of the treebank.
	 * 
	 * @return Whether this is a start symbol
	 */
	public boolean isStartSymbol(String str);

	/**
	 * Returns a String array of treebank start symbols.
	 * 
	 * @return The start symbols
	 */
	public String[] startSymbols();

	/**
	 * Returns a String which is the first (perhaps unique) start symbol of the
	 * treebank, or null if none is defined.
	 * 
	 * @return The start symbol
	 */
	public String startSymbol();

	/**
	 * Returns the extension of treebank files for this treebank. This should be
	 * passed as an argument to Treebank loading classes. It might be "mrg" or
	 * "fid" or whatever. Don't inlcude the period.
	 */
	public String treebankFileExtension();

}
