package edu.berkeley.nlp.util.functional;

/**
 * User: aria42 Date: Mar 25, 2009
 */
public class Functions {

	public static final Function<String, String> lowerCaseFn = FunctionalUtils
			.getAccessor("toLowercase", String.class);

}
