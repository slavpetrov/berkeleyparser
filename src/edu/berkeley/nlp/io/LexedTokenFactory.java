package edu.berkeley.nlp.io;

/**
 * Constructs a token (of arbitrary type) from a String and its position in the
 * underlying text.
 */
public interface LexedTokenFactory<T> {
	/**
	 * Constructs a token (of arbitrary type) from a String and its position in
	 * the underlying text.
	 * 
	 * @param str
	 *            The String extracted by the lexer.
	 * @param begin
	 *            The offset in the document of the first character in this
	 *            string.
	 * @param length
	 *            The number of characters the string takes up in the document.
	 */
	public T makeToken(String str, int begin, int length);
}
