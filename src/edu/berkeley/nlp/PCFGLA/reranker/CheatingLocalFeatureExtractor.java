/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import edu.berkeley.nlp.PCFGLA.reranker.FeatureExtractorManager.Feature;
import edu.berkeley.nlp.syntax.Tree;
import fig.basic.Indexer;

/**
 * @author dburkett
 *
 */
public class CheatingLocalFeatureExtractor implements LocalFeatureExtractor {

	private final OracleTreeFinder finder;
	private final BaseModel baseModel;
	private final int index;
	private Set<Node> goldNodes;
	
	public CheatingLocalFeatureExtractor(OracleTreeFinder finder, BaseModel baseModel, Indexer<Feature> featureIndexer) {
		this.finder = finder;
		this.baseModel = baseModel;
		this.index = featureIndexer.getIndex(new GotItRightFeature());
	}
	
	public void setGoldTree(Tree<String> goldTree) {
		goldNodes = finder.getGoldNodeSet(goldTree);
	}

	
	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.PCFGLA.reranker.LocalFeatureExtractor#precomputeLocalIndicatorFeatures(edu.berkeley.nlp.PCFGLA.reranker.BinaryEdge[], java.util.List)
	 */
	public int[][] precomputeLocalIndicatorFeatures(BinaryEdge[] edges, List<String> sentence) {
		int[][] features = new int[edges.length][];
		for (int i=0; i < features.length; i++) {
			if (goldNodes.contains(edges[i].getParent().coarseNode())) {
				features[i] = new int[] { index };
			} else {
				features[i] = new int[0];
			}
		}
		return features;
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.PCFGLA.reranker.LocalFeatureExtractor#precomputeLocalIndicatorFeatures(edu.berkeley.nlp.PCFGLA.reranker.UnaryEdge[], java.util.List)
	 */
	public int[][] precomputeLocalIndicatorFeatures(UnaryEdge[] edges, List<String> sentence) {
		int[][] features = new int[edges.length][];
		for (int i=0; i < features.length; i++) {
			int count = 0;
			if (goldNodes.contains(edges[i].getParent().coarseNode())) {
				count++;
			}
			if (goldNodes.contains(baseModel.getIntermediateCoarseNode(edges[i]))) {
				count++;
			}
			features[i] = new int[count];
			Arrays.fill(features[i], index);
		}
		return features;
	}
		
	
	public static class GotItRightFeature implements Feature {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		
		/* (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {
			return obj instanceof GotItRightFeature;
		}
		
		@Override
		public int hashCode() {
			return 3;
		}
	}


}
