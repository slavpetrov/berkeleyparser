/**
 * 
 */
package edu.berkeley.nlp.discPCFG;

import java.util.Arrays;
import java.util.List;

import edu.berkeley.nlp.util.Pair;

/**
 * @author adpauls
 * 
 */
public class WordInSentence extends Pair<List<String>, Integer> {

	/**
	 * @param first
	 * @param second
	 */
	public WordInSentence(List<String> sentenceWords, Integer wordPos) {
		super(sentenceWords, wordPos);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param first
	 * @param second
	 */
	public WordInSentence(String sentence, Integer wordPos) {
		super(Arrays.asList(sentence.split(" ")), wordPos);
		// TODO Auto-generated constructor stub
	}

}
