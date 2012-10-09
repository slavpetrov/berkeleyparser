package edu.berkeley.nlp.parser;

import java.util.List;

import edu.berkeley.nlp.syntax.Tree;

public interface Parser {
	Tree<String> getBestParse(List<String> sentence);
}
