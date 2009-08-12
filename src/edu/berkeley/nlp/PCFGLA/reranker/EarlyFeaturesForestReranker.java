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
public class EarlyFeaturesForestReranker {
	private final BaseModel baseModel;
	private final FeatureExtractorManager featureExtractorManager;
	private final LocalFeatureExtractor localFeatureExtractor;
	private final NonlocalFeatureExtractor nonlocalFeatureExtractor;
	private final AnticipatedFeatureExtractor earlyFeatureExtractor;
	private final int beamSize;
	
	private double[] w;
	
	public EarlyFeaturesForestReranker(BaseModel baseModel, FeatureExtractorManager featureExtractorManager, int beamSize) {
		this.baseModel = baseModel;
		this.featureExtractorManager = featureExtractorManager;
		this.localFeatureExtractor = featureExtractorManager.getLocalFeatureExtractor();
		this.nonlocalFeatureExtractor = featureExtractorManager.getNonlocalFeatureExtractor();
		this.earlyFeatureExtractor = featureExtractorManager.getAnticipatedFeatureExtractor();
		this.beamSize = beamSize;
		
		this.w = defaultWeightVector();
	}
	
	private double[] defaultWeightVector() {
		return DoubleArrays.constantArray(0.0, featureExtractorManager.getTotalNumFeatures());
	}
	
	public Tree<String> getBestParse(PrunedForest prunedForest) {
		return baseModel.relabelStates(getViterbiStateTree(prunedForest), prunedForest.getSentence());
	}
	
	public Tree<Node> getViterbiStateTree(PrunedForest prunedForest) {
		RerankedForest rerankedForest = rerankForest(prunedForest);
		if (rerankedForest.hasParseFailure()) {
			return null;
		}
		return rerankedForest.getViterbiTree();
	}
	
	public Pair<Double, int[]> getViterbiTreeFeatureVector(PrunedForest prunedForest) {
		RerankedForest rerankedForest = rerankForest(prunedForest);
		if (rerankedForest.hasParseFailure()) {
			return null;
		}
		return getViterbiTreeFeatureVector(rerankedForest);
	}
	
	private Pair<Double, int[]> getViterbiTreeFeatureVector(RerankedForest rerankedForest) {
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
	
	private RerankedForest rerankForest(PrunedForest prunedForest) {
		List<String> sentence = prunedForest.getSentence();
		Node[] nodes = prunedForest.getNodes();
		int[][] binaryEdgeFeatures = localFeatureExtractor.precomputeLocalIndicatorFeatures(prunedForest.getBinaryEdges(), sentence);
		int[][] unaryEdgeFeatures = localFeatureExtractor.precomputeLocalIndicatorFeatures(prunedForest.getUnaryEdges(), sentence);
		
		double[][] nodeScores = new double[nodes.length][];
		boolean[][] isUnaryEdgeBacktrace = new boolean[nodes.length][];
		int[][] edgeIndexBacktrace = new int[nodes.length][];
		int[][][] childKBacktrace = new int[nodes.length][][];
		int[][][][] earlyFeatures = new int[nodes.length][][][];
		
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
						isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, earlyFeatures, sentence);
			}
			for (int ur : prunedForest.getUnaryEdgesByNode(i)) {
				BeamKey key = new BeamKey(true, ur, 0, -1);
				pushOntoHeap(key, heap, prunedForest, binaryEdgeFeatures, unaryEdgeFeatures, nodeScores,
						isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, earlyFeatures, sentence);
			}
			while (buffer.size() < beamSize && heap.hasNext()) {
				double score = heap.getPriority();
				BeamKey poppedKey = heap.next();
				buffer.add(Pair.makePair(score, poppedKey));
				if (poppedKey.unaryEdge) {
					pushOntoHeap(new BeamKey(true, poppedKey.edgeIndex, poppedKey.leftChildK+1, -1),
							heap, prunedForest, binaryEdgeFeatures, unaryEdgeFeatures, nodeScores,
							isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, earlyFeatures, sentence);
				} else {
					pushOntoHeap(new BeamKey(false, poppedKey.edgeIndex, poppedKey.leftChildK+1, poppedKey.rightChildK),
							heap, prunedForest, binaryEdgeFeatures, unaryEdgeFeatures, nodeScores,
							isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, earlyFeatures, sentence);
					pushOntoHeap(new BeamKey(false, poppedKey.edgeIndex, poppedKey.leftChildK, poppedKey.rightChildK+1),
							heap, prunedForest, binaryEdgeFeatures, unaryEdgeFeatures, nodeScores,
							isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, earlyFeatures, sentence);
				}
			}
			storeBuffer(i, buffer, nodeScores, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, earlyFeatures);
		}
		return new RerankedForest(prunedForest, sentence, nodeScores, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace);
	}

	private boolean isPosNode(Node node) {
		return baseModel.isPosTag(node.state);
	}
	
	private void storeBuffer(int node, List<Pair<Double, BeamKey>> buffer, double[][] nodeScores,
			boolean[][] isUnaryEdgeBacktrace, int[][] edgeIndexBacktrace, int[][][] childKBacktrace, int[][][][] earlyFeatures) {
		int cands = buffer.size();
		nodeScores[node] = new double[cands];
		isUnaryEdgeBacktrace[node] = new boolean[cands];
		edgeIndexBacktrace[node] = new int[cands];
		childKBacktrace[node] = new int[cands][];
		earlyFeatures[node] = new int[cands][][];
		for (int k=0; k<cands; k++) {
			nodeScores[node][k] = buffer.get(k).getFirst();
			BeamKey key = buffer.get(k).getSecond();
			isUnaryEdgeBacktrace[node][k] = key.unaryEdge;
			edgeIndexBacktrace[node][k] = key.edgeIndex;
			earlyFeatures[node][k] = key.getEarlyFeatures();
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
			int[][][][] earlyFeatures, List<String> sentence) {
		int[][] dummy = new int[0][];
		if (key.unaryEdge) {
			UnaryEdge ue = prunedForest.getUnaryEdges()[key.edgeIndex];
			int c = prunedForest.getUnaryChildNodeIndex(key.edgeIndex);
			if (key.leftChildK >= nodeScores[c].length) {
				return;
			}
			int[] lf = unaryEdgeFeatures[key.edgeIndex];
			List<Integer> nlf = nonlocalFeatureExtractor.computeNonlocalIndicatorFeaturesForUnaryEdge(
					prunedForest, key.edgeIndex, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace,
					key.leftChildK, sentence);
			List<int[]> newEarlyFeatures = earlyFeatureExtractor.computeAnticipatedIndicatorFeaturesForUnaryEdge(
					prunedForest, key.edgeIndex, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace,
					key.leftChildK, sentence);
			Pair<List<int[]>, List<int[]>> childEarlyFeatures =
				splitEarlyFeatures(earlyFeatures[c][key.leftChildK], ue.startIndex, ue.stopIndex);
			double ruleScore = prunedForest.getUnaryEdgeScore(key.edgeIndex);
			double edgeScore = w[0]*ruleScore + dotProduct(w, lf) + listDotProduct(w, nlf) + nodeScores[c][key.leftChildK] +
												dotProduct(w, newEarlyFeatures) - dotProduct(w, childEarlyFeatures.getSecond());
			List<int[]> edgeEarlyFeatures = new ArrayList<int[]>(newEarlyFeatures);
			edgeEarlyFeatures.addAll(childEarlyFeatures.getFirst());
			key.setEarlyFeatures(edgeEarlyFeatures.toArray(dummy));
			heap.add(key, edgeScore);
		} else {
			BinaryEdge be = prunedForest.getBinaryEdges()[key.edgeIndex];
			int lc = prunedForest.getLeftChildNodeIndex(key.edgeIndex);
			int rc = prunedForest.getRightChildNodeIndex(key.edgeIndex);
			if (key.leftChildK >= nodeScores[lc].length || key.rightChildK >= nodeScores[rc].length) {
				return;
			}
			int[] lf = binaryEdgeFeatures[key.edgeIndex];
			List<Integer> nlf = nonlocalFeatureExtractor.computeNonlocalIndicatorFeaturesForBinaryEdge(
					prunedForest, key.edgeIndex, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace,
					key.leftChildK, key.rightChildK, sentence);
			List<int[]> newEarlyFeatures = earlyFeatureExtractor.computeAnticipatedIndicatorFeaturesForBinaryEdge(
					prunedForest, key.edgeIndex, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace,
					key.leftChildK, key.rightChildK, sentence);
			Pair<List<int[]>, List<int[]>> leftChildEarlyFeatures =
				splitEarlyFeatures(earlyFeatures[lc][key.leftChildK], be.startIndex, be.stopIndex);
			Pair<List<int[]>, List<int[]>> rightChildEarlyFeatures =
				splitEarlyFeatures(earlyFeatures[rc][key.rightChildK], be.startIndex, be.stopIndex);
			double ruleScore = prunedForest.getBinaryEdgeScore(key.edgeIndex);
			double edgeScore = w[0]*ruleScore + dotProduct(w, lf) + listDotProduct(w, nlf) +
												 nodeScores[lc][key.leftChildK] + nodeScores[rc][key.rightChildK] +
												 dotProduct(w, newEarlyFeatures) - dotProduct(w, leftChildEarlyFeatures.getSecond()) -
												 dotProduct(w, rightChildEarlyFeatures.getSecond());
			List<int[]> edgeEarlyFeatures = new ArrayList<int[]>(newEarlyFeatures);
			edgeEarlyFeatures.addAll(leftChildEarlyFeatures.getFirst());
			edgeEarlyFeatures.addAll(rightChildEarlyFeatures.getFirst());
			key.setEarlyFeatures(edgeEarlyFeatures.toArray(dummy));
			heap.add(key, edgeScore);
		}
	}

	/**
	 * Returns the list of early features split into two lists.  The first is the features that are still "early"
	 * (i.e. they belong to a span outside the current one).  The second is the features that should be taken
	 * off the early list since they will be included in the actual feature lists if they are actually active.
	 */
	private Pair<List<int[]>, List<int[]>> splitEarlyFeatures(
			int[][] earlyFeatures, int startIndex, int stopIndex) {
		List<int[]> stillEarly = new ArrayList<int[]>();
		List<int[]> done = new ArrayList<int[]>();
		for (int[] f : earlyFeatures) {
			if (f[1] >= startIndex && f[2] <= stopIndex) {
				done.add(f);
			} else {
				stillEarly.add(f);
			}
		}
		return Pair.makePair(stillEarly, done);
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

	private static double listDotProduct(double[] w, List<Integer> features) {
		double val = 0.0;
		for (int f : features) {
			if (f > 0 && f < w.length) {
				val += w[f];
			}
		}
		return val;
	}

	private static double dotProduct(double[] w, List<int[]> earlyFeatures) {
		double val = 0.0;
		for (int[] t : earlyFeatures) {
			int f = t[0];
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
		
		private int[][] earlyFeatures;
		
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

		public void setEarlyFeatures(int[][] earlyFeatures) {
			this.earlyFeatures = earlyFeatures;
		}

		public int[][] getEarlyFeatures() {
			return earlyFeatures;
		}
	}
}
