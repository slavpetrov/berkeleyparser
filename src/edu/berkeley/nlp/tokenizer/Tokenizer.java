/**
 * 
 */
package edu.berkeley.nlp.tokenizer;

import java.util.Iterator;
import java.util.List;

/**
 * Tokenizers break up text into individual Objects. These objects may be
 * Strings, Words, or other Objects. This Tokenizer interface allows the source
 * to be set upon construction, or to be reset using the
 * <code>setSource(Reader r)</code> method. Thus each Tokenizer instance may be
 * used for one data source or several.
 * 
 * @author Teg Grenager (grenager@stanford.edu)
 */
public interface Tokenizer extends Iterator {

	/** Returns the next token from this Tokenizer. */
	public Object next();

	/** Returns <code>true</code> if this Tokenizer has more elements. */
	public boolean hasNext();

	/**
	 * Removes from the underlying collection the last element returned by the
	 * iterator. This is an optional operation for Iterators - a Tokenizer
	 * normally would not support it. This method can be called only once per
	 * call to next.
	 */
	public void remove();

	/**
	 * Returns the next token, without removing it, from the Tokenizer, so that
	 * the same token will be again returned on the next call to next() or
	 * peek().
	 */
	public Object peek();

	/** Returns all tokens of this Tokenizer as a List for convenience. */
	public List tokenize();

}
