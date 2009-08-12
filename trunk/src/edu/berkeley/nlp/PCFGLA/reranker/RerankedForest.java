/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import java.util.ArrayList;
import java.util.List;

import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.Lists;
import fig.basic.Pair;

/**
 * @author dburkett
 *
 */
public class RerankedForest {
	public final PrunedForest baseForest;
	public final List<String> sentence;
	public final double[][] insideScores;
	public final boolean[][] isUnaryEdgeBacktrace;
	public final int[][] edgeIndexBacktrace;
	public final int[][][] childKBacktrace;
	
	private int[] viterbiBinaryEdgeIndices = null;
	private int[] viterbiUnaryEdgeIndices = null;
	private BinaryEdge[] viterbiBinaryEdges = null;
	private UnaryEdge[] viterbiUnaryEdges = null;
	
	public RerankedForest(PrunedForest baseForest, List<String> sentence, double[][] insideScores,
			boolean[][] isUnaryEdgeBacktrace, int[][] edgeIndexBacktrace, int[][][] childKBacktrace) {
		this.baseForest = baseForest;
		this.sentence = sentence;
		this.insideScores = insideScores;
		this.isUnaryEdgeBacktrace = isUnaryEdgeBacktrace;
		this.edgeIndexBacktrace = edgeIndexBacktrace;
		this.childKBacktrace = childKBacktrace;
	}
	
	public boolean hasParseFailure() {
		return insideScores != null && insideScores[baseForest.getRootNodeIndex()].length == 0;
	}

	public Tree<Node> getViterbiTree() {
		return reconstructTree(baseForest.getRootNodeIndex(), 0);
	}

	public Tree<Node> reconstructTree(int node, int k) {
		Node label = baseForest.getNodes()[node];
		if (isUnaryEdgeBacktrace[node] == null || edgeIndexBacktrace[node] == null || childKBacktrace[node] == null) {
			return new Tree<Node>(label);
		}
		List<Tree<Node>> children = new ArrayList<Tree<Node>>();
		if (isUnaryEdgeBacktrace[node][k]) {
			children.add(reconstructTree(baseForest.getUnaryChildNodeIndex(edgeIndexBacktrace[node][k]),
					childKBacktrace[node][k][0]));
		} else {
			children.add(reconstructTree(baseForest.getLeftChildNodeIndex(edgeIndexBacktrace[node][k]),
					childKBacktrace[node][k][0]));
			children.add(reconstructTree(baseForest.getRightChildNodeIndex(edgeIndexBacktrace[node][k]),
					childKBacktrace[node][k][1]));
		}
		return new Tree<Node>(label, children);
	}
	
	public BinaryEdge[] getBinaryEdgesFromViterbiTree() {
		if (viterbiBinaryEdges == null) {
			computeViterbiEdges();
		}
		return viterbiBinaryEdges;
	}

	public UnaryEdge[] getUnaryEdgesFromViterbiTree() {
		if (viterbiUnaryEdges == null) {
			computeViterbiEdges();
		}
		return viterbiUnaryEdges;
	}
	
	public int[] getBinaryEdgeIndicesFromViterbiTree() {
		if (viterbiBinaryEdgeIndices == null) {
			computeViterbiEdges();
		}
		return viterbiBinaryEdgeIndices;
	}

	public int[] getUnaryEdgeIndicesFromViterbiTree() {
		if (viterbiUnaryEdgeIndices == null) {
			computeViterbiEdges();
		}
		return viterbiUnaryEdgeIndices;
	}

	private void computeViterbiEdges() {
		Pair<List<Integer>, List<Integer>> viterbiEdges = getEdgesFromTree(baseForest.getRootNodeIndex(), 0);
		viterbiBinaryEdgeIndices = Lists.toPrimitiveArray(viterbiEdges.getFirst());
		viterbiUnaryEdgeIndices = Lists.toPrimitiveArray(viterbiEdges.getSecond());
		viterbiBinaryEdges = new BinaryEdge[viterbiBinaryEdgeIndices.length];
		for (int i=0; i<viterbiBinaryEdges.length; i++) {
			viterbiBinaryEdges[i] = baseForest.getBinaryEdges()[viterbiBinaryEdgeIndices[i]];
		}
		viterbiUnaryEdges = new UnaryEdge[viterbiUnaryEdgeIndices.length];
		for (int i=0; i<viterbiUnaryEdges.length; i++) {
			viterbiUnaryEdges[i] = baseForest.getUnaryEdges()[viterbiUnaryEdgeIndices[i]];
		}
	}

	public Pair<List<Integer>, List<Integer>> getEdgesFromTree(int node, int k) {
		List<Integer> binaryEdges = new ArrayList<Integer>();
		List<Integer> unaryEdges = new ArrayList<Integer>();
		getEdgesFromTreeHelper(binaryEdges, unaryEdges, node, k);
		return Pair.makePair(binaryEdges, unaryEdges);
	}
	
	private void getEdgesFromTreeHelper(List<Integer> binaryEdges, List<Integer> unaryEdges, int node, int k) {
		if (isUnaryEdgeBacktrace[node] == null || edgeIndexBacktrace[node] == null || childKBacktrace[node] == null) {
			return;
		}
		int edgeIndex = edgeIndexBacktrace[node][k];
		if (isUnaryEdgeBacktrace[node][k]) {
			unaryEdges.add(edgeIndex);
			getEdgesFromTreeHelper(binaryEdges, unaryEdges, baseForest.getUnaryChildNodeIndex(edgeIndex),
					childKBacktrace[node][k][0]);
		} else {
			binaryEdges.add(edgeIndex);
			getEdgesFromTreeHelper(binaryEdges, unaryEdges, baseForest.getLeftChildNodeIndex(edgeIndex),
					childKBacktrace[node][k][0]);
			getEdgesFromTreeHelper(binaryEdges, unaryEdges, baseForest.getRightChildNodeIndex(edgeIndex),
					childKBacktrace[node][k][1]);
		}
	}
	
	/**
	 * @return the sentence
	 */
	public List<String> getSentence() {
		return sentence;
	}

}
