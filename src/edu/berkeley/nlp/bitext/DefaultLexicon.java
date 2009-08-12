package edu.berkeley.nlp.bitext;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.Collection;
import java.util.List;

import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.Counter;

public class DefaultLexicon implements Lexicon {

	private List<String> sentence;

	public Counter<String> getTagScores(int loc) {
		Counter<String> tagCounter = new Counter<String>();
		tagCounter.incrementCount( sentence.get(loc), 0.0 );
		return tagCounter;
	}

	public boolean isKnown(String word) {
		return true;
	}

	public void readData(BufferedReader reader) {
		// TODO Auto-generated method stub

	}

	public void setInputSentence(List<String> sentence) {
		this.sentence = sentence;			
	}

	public void train(Collection<Tree<String>> trees) {
		// TODO Auto-generated method stub			
	}

	public void writeData(BufferedWriter write) {
		// TODO Auto-generated method stub

	}

}
