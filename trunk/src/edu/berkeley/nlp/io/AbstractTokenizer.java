/**
 * 
 */
package edu.berkeley.nlp.io;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract tokenizer. Tokenizers extending AbstractTokenizer need only
 * implement the <code>getNext()</code> method.
 * 
 * @author Teg Grenager (grenager@stanford.edu)
 */

public abstract class AbstractTokenizer implements Tokenizer {

	protected Object nextToken = null;

	protected abstract Object getNext();

	/** Returns the next token from this Tokenizer. */
	public Object next() {
		if (nextToken == null)
			nextToken = getNext();
		Object result = nextToken;
		nextToken = getNext();
		return result;
	}

	/** Returns <code>true</code> if this Tokenizer has more elements. */
	public boolean hasNext() {
		if (nextToken == null)
			nextToken = getNext();
		return nextToken != null;
	}

	/**
	 * This is an optional operation, by default not supported.
	 */
	public void remove() {
		throw new UnsupportedOperationException();
	}

	/**
	 * This is an optional operation, by default supported.
	 */
	public Object peek() {
		if (nextToken == null)
			nextToken = getNext();
		return nextToken;
	}

	/**
	 * Returns text as a List of tokens.
	 */
	public List tokenize() {
		// System.out.println("tokenize called");
		List result = new ArrayList();
		while (hasNext()) {
			result.add(next());
		}
		return result;
	}

}
