package edu.berkeley.nlp.PCFGLA;

import java.util.List;

import edu.berkeley.nlp.syntax.Tree;

public interface Parser {
	public Tree<String> getBestParse(List<String> sentence);
}
