/**
 * 
 */
package edu.berkeley.nlp.tokenizer;

import java.io.Reader;
import java.util.Iterator;

/**
 * A TokenizerFactory is used to convert a java.io.Reader into a Tokenizer (or
 * an Iterator) over the Objects represented by the text in the java.io.Reader.
 * It's mainly a convenience, since you could cast down anyway.
 * 
 * @author Christopher Manning
 */
public interface TokenizerFactory {

	public Iterator getIterator(java.io.Reader r);

	public Tokenizer getTokenizer(Reader r);

}
