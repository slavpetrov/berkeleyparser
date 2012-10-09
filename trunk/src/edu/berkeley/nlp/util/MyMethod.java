package edu.berkeley.nlp.util;

/**
 * A function wrapping interface.
 * 
 * @author John DeNero
 */
public interface MyMethod<I, O> {
	public O call(I obj);
}
