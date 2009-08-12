/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import java.util.ArrayList;
import java.util.List;

/**
 * @author dburkett
 *
 */
public class DummyFeatureExtractor implements LocalFeatureExtractor, NonlocalFeatureExtractor {

	public int[][] precomputeLocalIndicatorFeatures(BinaryEdge[] binaryEdges, List<String> sentence) {
		return new int [binaryEdges.length][0];
	}

	public int[][] precomputeLocalIndicatorFeatures(UnaryEdge[] unaryEdges, List<String> sentence) {
		return new int[unaryEdges.length][0];
	}

	public List<Integer> computeNonlocalIndicatorFeaturesForBinaryEdge(
			PrunedForest forest, int rootEdgeIndex, boolean[][] isUnaryEdgeBacktrace,
			int[][] edgeIndexBacktrace, int[][][] childKBacktrace, int leftChildK,
			int rightChildK, List<String> sentence) {
		return new ArrayList<Integer>();
	}

	public List<Integer> computeNonlocalIndicatorFeaturesForUnaryEdge(
			PrunedForest forest, int rootEdgeIndex, boolean[][] isUnaryEdgeBacktrace,
			int[][] edgeIndexBacktrace, int[][][] childKBacktrace, int childK, List<String> sentence) {
		return new ArrayList<Integer>();
	}

}
