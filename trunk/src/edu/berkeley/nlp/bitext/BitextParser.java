/**
 * 
 */
package edu.berkeley.nlp.bitext;

import java.util.List;

/**
 * @author petrov
 *
 */
public interface BitextParser {
	public void parse(List<String> leftInput, List<String> rightInput, Alignment alignment);
	public int getNumEdgesPopped();
}
