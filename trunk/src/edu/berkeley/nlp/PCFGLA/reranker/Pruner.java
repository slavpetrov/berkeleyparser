/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import java.util.List;

/**
 * @author dburkett
 *
 */
public interface Pruner {
	PrunedForest getPrunedForest(List<String> sentence);
}
