/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import java.util.List;

import edu.berkeley.nlp.util.Triple;

/**
 * @author dburkett
 *
 */
public interface AnticipatedFeatureExtractor {
	/**
	 * The return value is a list of features, where each feature is represented as a 3-value array:
	 * First, the feature index, and then the bounds of the minimal span that will contain this feature
	 * if it is actually extracted.
	 */
	List<int[]> computeAnticipatedIndicatorFeaturesForBinaryEdge(PrunedForest forest, int rootEdgeIndex,
			boolean[][] isUnaryEdgeBacktrace, int[][] edgeIndexBacktrace, int[][][] childKBacktrace,
			int leftChildK, int rightChildK, List<String> sentence);

	/**
	 * The return value is a list of features, where each feature is represented as a 3-value array:
	 * First, the feature index, and then the bounds of the minimal span that will contain this feature
	 * if it is actually extracted.
	 */
	List<int[]> computeAnticipatedIndicatorFeaturesForUnaryEdge(PrunedForest forest, int rootEdgeIndex,
			boolean[][] isUnaryEdgeBacktrace, int[][] edgeIndexBacktrace, int[][][] childKBacktrace,
			int childK, List<String> sentence);
}
