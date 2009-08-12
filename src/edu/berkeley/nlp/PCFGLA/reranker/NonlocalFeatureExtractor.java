/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import java.util.List;

/**
 * @author dburkett
 *
 */
public interface NonlocalFeatureExtractor {
	List<Integer> computeNonlocalIndicatorFeaturesForBinaryEdge(PrunedForest forest, int rootEdgeIndex,
			boolean[][] isUnaryEdgeBacktrace, int[][] edgeIndexBacktrace, int[][][] childKBacktrace,
			int leftChildK, int rightChildK, List<String> sentence);
	List<Integer> computeNonlocalIndicatorFeaturesForUnaryEdge(PrunedForest forest, int rootEdgeIndex,
			boolean[][] isUnaryEdgeBacktrace, int[][] edgeIndexBacktrace, int[][][] childKBacktrace,
			int childK, List<String> sentence);
}
