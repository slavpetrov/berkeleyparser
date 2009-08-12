/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import java.util.ArrayList;
import java.util.List;

import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.Lists;
import edu.berkeley.nlp.util.PriorityQueue;
import fig.basic.Pair;

/**
 * @author dburkett
 *
 */
public class ForestReranker {
	private final BaseModel baseModel;
	private final FeatureExtractorManager featureExtractorManager;
	/**
	 * @return the featureExtractorManager
	 */
	public FeatureExtractorManager getFeatureExtractorManager() {
		return featureExtractorManager;
	}

	private final LocalFeatureExtractor localFeatureExtractor;
	private final NonlocalFeatureExtractor nonlocalFeatureExtractor;
	private final int beamSize;
	
	private double[] w;
	
	public ForestReranker(BaseModel baseModel, FeatureExtractorManager featureExtractorManager, int beamSize) {
		this.baseModel = baseModel;
		this.featureExtractorManager = featureExtractorManager;
		this.localFeatureExtractor = featureExtractorManager.getLocalFeatureExtractor();
		this.nonlocalFeatureExtractor = featureExtractorManager.getNonlocalFeatureExtractor();
		this.beamSize = beamSize;
		
		this.w = defaultWeightVector();
	}
	
	private double[] defaultWeightVector() {
		return DoubleArrays.constantArray(0.0, featureExtractorManager.getTotalNumFeatures());
	}
	
	public Tree<String> getBestParse(PrunedForest prunedForest) {
		return baseModel.relabelStates(getViterbiStateTree(prunedForest), prunedForest.getSentence());
	}

	public Tree<String> getBestParse(PrunedForest prunedForest,
			int[][] binaryLocalIndicatorFeatures, int[][] unaryLocalIndicatorFeatures) {
		return baseModel.relabelStates(getViterbiStateTree(prunedForest, binaryLocalIndicatorFeatures, unaryLocalIndicatorFeatures), prunedForest.getSentence());
	}
	
	public Tree<Node> getViterbiStateTree(PrunedForest prunedForest) {
		return getViterbiStateTree(prunedForest, null, null);
	}
	
	public Tree<Node> getViterbiStateTree(PrunedForest prunedForest,
			int[][] binaryLocalIndicatorFeatures, int[][] unaryLocalIndicatorFeatures) {
		RerankedForest rerankedForest = rerankForest(prunedForest, binaryLocalIndicatorFeatures, unaryLocalIndicatorFeatures);
		if (rerankedForest.hasParseFailure()) {
			return null;
		}
		return rerankedForest.getViterbiTree();
	}
	
	/**
	 * Returns a pair of the base model score and the indicator features for the best tree in this forest.
	 * @param prunedForest
	 * @return
	 */
	public Pair<Double, int[]> getViterbiTreeFeatureVector(PrunedForest prunedForest) {
		return getViterbiTreeFeatureVector(prunedForest, null, null);
	}
	
	/**
	 * Returns a pair of the base model score and the indicator features for the best tree in this forest.
	 * @param prunedForest
	 * @return
	 */
	public Pair<Double, int[]> getViterbiTreeFeatureVector(PrunedForest prunedForest,
			int[][] binaryLocalIndicatorFeatures, int[][] unaryLocalIndicatorFeatures) {
		RerankedForest rerankedForest = rerankForest(prunedForest, binaryLocalIndicatorFeatures, unaryLocalIndicatorFeatures);
		if (rerankedForest.hasParseFailure()) {
			return null;
		}
		return getViterbiTreeFeatureVector(rerankedForest);
	}
	
	/**
	 * Returns a pair of the base model score and the indicator features for the best tree in this forest.
	 * @param rerankedForest
	 * @return
	 */
	public Pair<Double, int[]> getViterbiTreeFeatureVector(RerankedForest rerankedForest) {
		List<Integer> indicatorFeatures = new ArrayList<Integer>();
		double baseModelScore = 0;
		for (int[] features : localFeatureExtractor.precomputeLocalIndicatorFeatures(rerankedForest.getBinaryEdgesFromViterbiTree(), rerankedForest.sentence)) {
			concat(indicatorFeatures, features);
		}
		for (int[] features : localFeatureExtractor.precomputeLocalIndicatorFeatures(rerankedForest.getUnaryEdgesFromViterbiTree(), rerankedForest.sentence)) {
			concat(indicatorFeatures, features);
		}
		for (int br : rerankedForest.getBinaryEdgeIndicesFromViterbiTree()) {
			indicatorFeatures.addAll(
					nonlocalFeatureExtractor.computeNonlocalIndicatorFeaturesForBinaryEdge(rerankedForest.baseForest, br,
							rerankedForest.isUnaryEdgeBacktrace, rerankedForest.edgeIndexBacktrace,
							rerankedForest.childKBacktrace, 0, 0, rerankedForest.sentence));
			baseModelScore += rerankedForest.baseForest.getBinaryEdgeScore(br);
		}
		for (int ur : rerankedForest.getUnaryEdgeIndicesFromViterbiTree()) {
			indicatorFeatures.addAll(
					nonlocalFeatureExtractor.computeNonlocalIndicatorFeaturesForUnaryEdge(rerankedForest.baseForest, ur,
							rerankedForest.isUnaryEdgeBacktrace, rerankedForest.edgeIndexBacktrace,
							rerankedForest.childKBacktrace, 0, rerankedForest.sentence));
			baseModelScore += rerankedForest.baseForest.getUnaryEdgeScore(ur);
		}
		return Pair.makePair(baseModelScore, Lists.toPrimitiveArray(indicatorFeatures));
	}
	
	private void concat(List<Integer> list, int[] array) {
		for (int f : array) {
			list.add(f);
		}
	}
	
	private RerankedForest rerankForest(PrunedForest prunedForest,
			int[][] binaryLocalIndicatorFeatures, int[][] unaryLocalIndicatorFeatures) {
		List<String> sentence = prunedForest.getSentence();
		Node[] nodes = prunedForest.getNodes();
		int[][] binaryEdgeFeatures = binaryLocalIndicatorFeatures;
		if (binaryEdgeFeatures == null) {
			binaryEdgeFeatures = localFeatureExtractor.precomputeLocalIndicatorFeatures(prunedForest.getBinaryEdges(), sentence);
		}
		int[][] unaryEdgeFeatures = unaryLocalIndicatorFeatures;
		if (unaryEdgeFeatures == null) {
			unaryEdgeFeatures = localFeatureExtractor.precomputeLocalIndicatorFeatures(prunedForest.getUnaryEdges(), sentence);
		}
		
		double[][] nodeScores = new double[nodes.length][];
		boolean[][] isUnaryEdgeBacktrace = new boolean[nodes.length][];
		int[][] edgeIndexBacktrace = new int[nodes.length][];
		int[][][] childKBacktrace = new int[nodes.length][][];
		
		for (int i=0; i<nodes.length; i++) {
			if (isPosNode(nodes[i])) {
				nodeScores[i] = new double[] { w[0] * prunedForest.getLexicalNodeScore(i) };
				continue;
			}
			PriorityQueue<BeamKey> heap = new PriorityQueue<BeamKey>();
			List<Pair<Double, BeamKey>> buffer = new ArrayList<Pair<Double, BeamKey>>();
			for (int br : prunedForest.getBinaryEdgesByNode(i)) {
				BeamKey key = new BeamKey(false, br, 0, 0);
				pushOntoHeap(key, heap, prunedForest, binaryEdgeFeatures, unaryEdgeFeatures, nodeScores,
						isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, sentence);
			}
			for (int ur : prunedForest.getUnaryEdgesByNode(i)) {
				BeamKey key = new BeamKey(true, ur, 0, -1);
				pushOntoHeap(key, heap, prunedForest, binaryEdgeFeatures, unaryEdgeFeatures, nodeScores,
						isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, sentence);
			}
			while (buffer.size() < beamSize && heap.hasNext()) {
				double score = heap.getPriority();
				BeamKey poppedKey = heap.next();
				buffer.add(Pair.makePair(score, poppedKey));
				if (poppedKey.unaryEdge) {
					pushOntoHeap(new BeamKey(true, poppedKey.edgeIndex, poppedKey.leftChildK+1, -1),
							heap, prunedForest, binaryEdgeFeatures, unaryEdgeFeatures, nodeScores,
							isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, sentence);
				} else {
					pushOntoHeap(new BeamKey(false, poppedKey.edgeIndex, poppedKey.leftChildK+1, poppedKey.rightChildK),
							heap, prunedForest, binaryEdgeFeatures, unaryEdgeFeatures, nodeScores,
							isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, sentence);
					pushOntoHeap(new BeamKey(false, poppedKey.edgeIndex, poppedKey.leftChildK, poppedKey.rightChildK+1),
							heap, prunedForest, binaryEdgeFeatures, unaryEdgeFeatures, nodeScores,
							isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, sentence);
				}
			}
			storeBuffer(i, buffer, nodeScores, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace);
		}
		return new RerankedForest(prunedForest, sentence, nodeScores, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace);
	}

	private boolean isPosNode(Node node) {
		return baseModel.isPosTag(node.state);
	}
	
	private void storeBuffer(int node, List<Pair<Double, BeamKey>> buffer, double[][] nodeScores,
			boolean[][] isUnaryEdgeBacktrace, int[][] edgeIndexBacktrace, int[][][] childKBacktrace) {
		int cands = buffer.size();
		nodeScores[node] = new double[cands];
		isUnaryEdgeBacktrace[node] = new boolean[cands];
		edgeIndexBacktrace[node] = new int[cands];
		childKBacktrace[node] = new int[cands][];
		for (int k=0; k<cands; k++) {
			nodeScores[node][k] = buffer.get(k).getFirst();
			BeamKey key = buffer.get(k).getSecond();
			isUnaryEdgeBacktrace[node][k] = key.unaryEdge;
			edgeIndexBacktrace[node][k] = key.edgeIndex;
			if (key.unaryEdge) {
				childKBacktrace[node][k] = new int[] { key.leftChildK };
			} else {
				childKBacktrace[node][k] = new int[] { key.leftChildK, key.rightChildK };
			}
		}
	}

	private void pushOntoHeap(BeamKey key, PriorityQueue<BeamKey> heap, PrunedForest prunedForest,
			int[][] binaryEdgeFeatures, int[][] unaryEdgeFeatures, double[][] nodeScores,
			boolean[][] isUnaryEdgeBacktrace, int[][] edgeIndexBacktrace, int[][][] childKBacktrace,
			List<String> sentence) {
		if (key.unaryEdge) {
			int c = prunedForest.getUnaryChildNodeIndex(key.edgeIndex);
			if (key.leftChildK >= nodeScores[c].length) {
				return;
			}
			int[] lf = unaryEdgeFeatures[key.edgeIndex];
			List<Integer> nlf = nonlocalFeatureExtractor.computeNonlocalIndicatorFeaturesForUnaryEdge(
					prunedForest, key.edgeIndex, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace,
					key.leftChildK, sentence);
			double ruleScore = prunedForest.getUnaryEdgeScore(key.edgeIndex);
			double edgeScore = w[0]*ruleScore + dotProduct(w, lf) + dotProduct(w, nlf) + nodeScores[c][key.leftChildK];
			heap.add(key, edgeScore);
		} else {
			int lc = prunedForest.getLeftChildNodeIndex(key.edgeIndex);
			int rc = prunedForest.getRightChildNodeIndex(key.edgeIndex);
			if (key.leftChildK >= nodeScores[lc].length || key.rightChildK >= nodeScores[rc].length) {
				return;
			}
			int[] lf = binaryEdgeFeatures[key.edgeIndex];
			List<Integer> nlf = nonlocalFeatureExtractor.computeNonlocalIndicatorFeaturesForBinaryEdge(
					prunedForest, key.edgeIndex, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace,
					key.leftChildK, key.rightChildK, sentence);
			double ruleScore = prunedForest.getBinaryEdgeScore(key.edgeIndex);
			double edgeScore = w[0]*ruleScore + dotProduct(w, lf) + dotProduct(w, nlf) +
												 nodeScores[lc][key.leftChildK] + nodeScores[rc][key.rightChildK];
			heap.add(key, edgeScore);
		}
	}

	private static double dotProduct(double[] w, int[] features) {
		double val = 0.0;
		for (int f : features) {
			if (f > 0 && f < w.length) {
				val += w[f];
			}
		}
		return val;
	}

	private static double dotProduct(double[] w, List<Integer> features) {
		double val = 0.0;
		for (int f : features) {
			if (f > 0 && f < w.length) {
				val += w[f];
			}
		}
		return val;
	}

	public void setWeights(double[] w) {
		this.w = w;
	}

	public double[] getCurrentWeights() {
		return w;
	}

	private static class BeamKey {
		public final boolean unaryEdge;
		public final int edgeIndex;
		public final int leftChildK;
		public final int rightChildK;
		
		public BeamKey(boolean unaryEdge, int edgeIndex, int leftChildK, int rightChildK) {
			this.unaryEdge = unaryEdge;
			this.edgeIndex = edgeIndex;
			this.leftChildK = leftChildK;
			this.rightChildK = rightChildK;
		}
		
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof BeamKey)) {
				return false;
			}
			BeamKey k = (BeamKey)obj;
			return k.unaryEdge == unaryEdge &&
				k.edgeIndex == edgeIndex &&
				k.leftChildK == leftChildK &&
				k.rightChildK == rightChildK;
		}
		
		@Override
		public int hashCode() {
			final int prime = 39;
			return edgeIndex * prime * prime + leftChildK * prime + rightChildK;
		}
	}
}
