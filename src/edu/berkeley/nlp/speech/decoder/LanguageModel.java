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
public interface LanguageModel {
	public double getScore(String word, List<String> history) ;
	
	public Set<String> getSupport();
}
