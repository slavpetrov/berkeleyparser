package edu.berkeley.nlp.bitext;

import java.util.List;

import edu.berkeley.nlp.syntax.Tree;

public interface Parser 
{
	/**
	 * Parse a sentence
	 * @param sentence
	 * @return
	 */
	public Tree<String> parse(List<String> sentence);


}
