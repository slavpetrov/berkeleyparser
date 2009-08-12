/**
 * 
 */
package edu.berkeley.nlp.speech.decoder;

import java.util.List;
import java.util.Set;

/**
 * @author aria42
 *
 */
public class UniformUnigramLanguageModel implements LanguageModel {
	Set<String> vocab;
	
	public UniformUnigramLanguageModel(Set<String> vocab) {
		this.vocab = vocab;
	}
	
	public double getScore(String word, List<String> history) {
		if (vocab.contains(word)) {
			return 1.0 / vocab.size();
		}
		return 0.0;
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.speech.decoder.LanguageModel#getSupport()
	 */
	public Set<String> getSupport() {
		return vocab;
	}
	
	
}
