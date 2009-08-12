package edu.berkeley.nlp.bitext;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.Counter;

public interface Lexicon extends Serializable {
	
	public boolean isKnown(String word);
	
	public void setInputSentence(List<String> sentence);
		
	public Counter<String> getTagScores(int loc);

	public void train(Collection<Tree<String>> trees);
	
	public void writeData(BufferedWriter write);
	
	public void readData(BufferedReader reader);	
}