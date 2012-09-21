package edu.berkeley.nlp.util;

/**
 * A Freezable type locks its set methods after a call to freeze(), and will not
 * allow changes after that point.
 * 
 * @author John DeNero
 */
public interface Freezable {
	public void freeze();
}
