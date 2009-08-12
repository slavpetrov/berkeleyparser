/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import java.util.List;

/**
 * @author dburkett
 *
 */
public interface LocalFeatureExtractor {
	/**
	 * Compute local features for binary edges
	 * @param binaryEdges
	 * @param sentence
	 * @return a 2-d int array indexed by the edge (should match indexing of binary edges), and the 
	 * list of features that are in that edge (supports only binary features)
	 */
		int[][] precomputeLocalIndicatorFeatures(BinaryEdge[] binaryEdges, List<String> sentence);
		
		/**
		 * Compute local features for unary edges
		 * @param unaryEdges
		 * @param sentence
		 * @return a 2-d int array indexed by the edge (should match indexing of unary edges), and the 
	   * list of features that are in that edge (supports only binary features)
		 */
		int[][] precomputeLocalIndicatorFeatures(UnaryEdge[] unaryEdges, List<String> sentence);
		
}
