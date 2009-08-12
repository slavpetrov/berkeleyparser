/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.berkeley.nlp.PCFGLA.BinaryRule;
import edu.berkeley.nlp.PCFGLA.CoarseToFineMaxRuleParser;
import edu.berkeley.nlp.PCFGLA.ConstrainedTwoChartsParser;
import edu.berkeley.nlp.PCFGLA.Grammar;
import edu.berkeley.nlp.PCFGLA.Lexicon;
import edu.berkeley.nlp.PCFGLA.SpanPredictor;
import edu.berkeley.nlp.PCFGLA.UnaryRule;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.util.Lists;
import edu.berkeley.nlp.util.Logger;
import edu.berkeley.nlp.util.PriorityQueue;
import edu.berkeley.nlp.util.ScalingTools;
import edu.berkeley.nlp.util.Triple;
import fig.basic.IOUtils;
import fig.basic.Pair;

/**
 * @author dburkett
 *
 */
public class ViterbiPruner implements Pruner {
	private final Grammar baseGrammar; // in logarithm mode
	private final Lexicon baseLexicon; // in logarithm mode
	private final Grammar pruningGrammar; // NOT in logarithm mode
	private final CoarseToFineMaxRuleParser preParser; // pre-parses in logarithm mode, viterbi style
	private final ConstrainedTwoChartsParser parser; // viterbi, but using that weird scale thing
	private final short[] numSubStatesArray;
	private final double pruningThreshold;
	
	public ViterbiPruner(Grammar grammar, Lexicon lexicon, SpanPredictor spanPredictor, double pruningThreshold) {
		pruningGrammar = grammar.copyGrammar(false);
		Lexicon copyLexicon = lexicon.copyLexicon();
		this.baseGrammar = grammar;
		this.baseLexicon = lexicon;
		this.pruningThreshold = pruningThreshold;
		this.preParser = new CoarseToFineMaxRuleParser(grammar, lexicon, 1.0, -1, true, false, false, false, false, false, false);
		preParser.initCascade(grammar, lexicon);
		this.parser = new ConstrainedTwoChartsParser(pruningGrammar, copyLexicon, spanPredictor);
		this.numSubStatesArray = parser.getNumSubStatesArray();
	}
	
	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.PCFGLA.reranker.BaseModel#getPrunedForest(java.util.List)
	 */
	public PrunedForest getPrunedForest(List<String> sentence) {
		List<BinaryEdge> binaryEdges = new ArrayList<BinaryEdge>();
		List<UnaryEdge> unaryEdges = new ArrayList<UnaryEdge>();
		Set<Node> nodeSet = new HashSet<Node>();
		List<Node> nodes = new ArrayList<Node>();
		List<Double> binaryEdgeCosts = new ArrayList<Double>();
		List<Double> unaryEdgeCosts = new ArrayList<Double>();
		List<Double> binaryEdgePruningScores = new ArrayList<Double>();
		List<Double> unaryEdgePruningScores = new ArrayList<Double>();

		Chart chart = getChart(sentence);

		int n = sentence.size();
		double normalizer = chart.iScoresPostU[0][n][0][0];
		int sentenceScale = chart.iScale[0][n][0];
		for (int diff = 1; diff<=n; diff++) {
			for (int i=0; i+diff<=n; i++) {
				int j = i+diff;
				// We alternate between binary and unary rules on increasing length spans to ensure
				// that nodes are added to the list in topological order
				if (diff > 1) {
					Triple<List<BinaryEdge>, List<Double>, List<Double>> spanBinaryEdges = pruneBinaryEdges(chart, i, j, normalizer, sentenceScale);
					binaryEdges.addAll(spanBinaryEdges.getFirst());
					binaryEdgeCosts.addAll(spanBinaryEdges.getSecond());
					binaryEdgePruningScores.addAll(spanBinaryEdges.getThird());
					addChildNodesFromBinaryRules(nodes, nodeSet, spanBinaryEdges.getFirst());
				}
				Triple<List<UnaryEdge>, List<Double>, List<Double>> spanUnaryEdges = pruneUnaryEdges(chart, i, j, normalizer, sentenceScale);
				unaryEdges.addAll(spanUnaryEdges.getFirst());
				unaryEdgeCosts.addAll(spanUnaryEdges.getSecond());
				unaryEdgePruningScores.addAll(spanUnaryEdges.getThird());
				addChildNodesFromUnaryRules(nodes, nodeSet, spanUnaryEdges.getFirst());
			}
		}
		nodes.add(new Node(0, n, 0, 0));
		double[] lexicalNodeCosts = new double[nodes.size()];
		for (int i=0; i<nodes.size(); i++) {
			Node node = nodes.get(i);
			if (!baseGrammar.isGrammarTag(node.state)) {
				lexicalNodeCosts[i] = baseLexicon.score(sentence.get(node.startIndex), (short)node.state,
																								node.startIndex, false, false)[node.substate];
			}
		}
		
		return new PrunedForest(nodes.toArray(new Node[0]), binaryEdges.toArray(new BinaryEdge[0]), unaryEdges.toArray(new UnaryEdge[0]),
				Lists.toPrimitiveArray(binaryEdgeCosts), Lists.toPrimitiveArray(unaryEdgeCosts), lexicalNodeCosts,
				Lists.toPrimitiveArray(binaryEdgePruningScores), Lists.toPrimitiveArray(unaryEdgePruningScores), sentence);
	}
	
	private void addChildNodesFromUnaryRules(List<Node> nodes, Set<Node> nodeSet, List<UnaryEdge> spanUnaryEdges) {
		// This is quite a bit more complicated than the binary rules because we have to ensure a topological sort.
		// In other words, if a rule has a child node, n, that is the parent node in some other rule, we have to wait
		// until the child from that other rule has been added before we add n.
		Set<Node> childNodes = new HashSet<Node>();
		Map<Node, List<Node>> constraintMap = new HashMap<Node, List<Node>>();
		for (UnaryEdge edge : spanUnaryEdges) {
			childNodes.add(edge.getChild());
			constraintMap.put(edge.getChild(), new ArrayList<Node>());
		}
		for (UnaryEdge edge : spanUnaryEdges) {
			if (constraintMap.containsKey(edge.getParent())) {
				constraintMap.get(edge.getParent()).add(edge.getChild());
			}
		}
		boolean added = true;
		while (!childNodes.isEmpty() && added) {
			Set<Node> nodesToDelete = new HashSet<Node>();
			added = false;
			for (Node n : childNodes) {
				boolean safe = true;
				for (Node cons : constraintMap.get(n)) {
					safe = safe && nodeSet.contains(cons);
				}
				if (safe) {
					added = true;
					nodesToDelete.add(n);
					if (!nodeSet.contains(n)) {
						nodes.add(n);
						nodeSet.add(n);
						assert(nodes.size() == nodeSet.size());
					}
				}
			}
			childNodes.removeAll(nodesToDelete);
		}
		if (!childNodes.isEmpty()) {
			Logger.err("Topological sort failed and not all unary child nodes were added!");
		}
	}

	private void addChildNodesFromBinaryRules(List<Node> nodes, Set<Node> nodeSet, List<BinaryEdge> spanBinaryEdges) {
		for (BinaryEdge edge : spanBinaryEdges) {
			Node leftChild = edge.getLeftChild();
			if (!nodeSet.contains(leftChild)) {
				nodes.add(leftChild);
				nodeSet.add(leftChild);
				assert(nodes.size() == nodeSet.size());
			}
			Node rightChild = edge.getRightChild();
			if (!nodeSet.contains(rightChild)) {
				nodes.add(rightChild);
				nodeSet.add(rightChild);
			}
		}
	}
	
	private Triple<List<UnaryEdge>, List<Double>, List<Double>> pruneUnaryEdges(Chart chart, int i, int j, double normalizer, int sentenceScale) {
		List<UnaryEdge> edges = new ArrayList<UnaryEdge>();
		List<Double> edgeScores = new ArrayList<Double>();
		List<Double> pruningScores = new ArrayList<Double>();
		PriorityQueue<UnaryEdge> pq = new PriorityQueue<UnaryEdge>();
		for (short p=0; p<numSubStatesArray.length; p++) {
			if (!chart.allowedStates[i][j][p]) continue;
			for (short ps=0; ps<numSubStatesArray[p]; ps++) {
				if (!chart.allowedSubStates[i][j][p][ps]) continue;
				for (UnaryRule ur : pruningGrammar.getClosedSumUnaryRulesByParent(p)) {
					short c = ur.childState;
					if (!chart.allowedStates[i][j][c]) continue;
					double scalingFactor = 1;
					int thisScale = chart.oScale[i][j][p] + chart.iScale[i][j][c];
					if (thisScale != sentenceScale) {
						scalingFactor *= Math.pow(ScalingTools.SCALE, thisScale-sentenceScale);
					}
					for (short cs=0; cs<numSubStatesArray[c]; cs++) {
						if (!chart.allowedSubStates[i][j][c][cs]) continue;
						double score = chart.oScoresPreU[i][j][p][ps] * ur.getScore(ps, cs) *
													 chart.iScoresPreU[i][j][c][cs] * scalingFactor / normalizer;
						UnaryEdge edge = new UnaryEdge(i, j, p, ps, c, cs);
						if (score >= pruningThreshold) {
							pq.add(edge, score);
						}
					}
				}
			}
		}
		// All this mumbo jumbo here is to avoid having a bidirectional path between two nodes.
		// We need to be able to topologically sort the nodes
		HashSet<Pair<Integer, Integer>> dominanceRelations = new HashSet<Pair<Integer, Integer>>();
		while (pq.hasNext()) {
			double score = pq.getPriority();
			UnaryEdge edge = pq.next();
			Pair<Integer, Integer> conflictingRelation = Pair.makePair(edge.childState, edge.parentState);
			if (!dominanceRelations.contains(conflictingRelation)) {
				edges.add(edge);
				edgeScores.add(baseGrammar.getUnaryRule((short)edge.parentState, (short)edge.childState).getScore(edge.parentSubstate, edge.childSubstate));
				pruningScores.add(score);
				addTransitiveClosure(dominanceRelations, Pair.makePair(edge.parentState, edge.childState));
			}
		}
		return Triple.makeTriple(edges, edgeScores, pruningScores);
	}

	private void addTransitiveClosure(HashSet<Pair<Integer, Integer>> dominanceRelations, Pair<Integer, Integer> newRelation) {
		Set<Pair<Integer, Integer>> nodesToAdd = new HashSet<Pair<Integer, Integer>>();
		for (Pair<Integer, Integer> existingRelation : dominanceRelations) {
			if (existingRelation.getSecond().equals(newRelation.getFirst())) {
				nodesToAdd.add(Pair.makePair(existingRelation.getFirst(), newRelation.getSecond()));
			}
			if (existingRelation.getFirst().equals(newRelation.getSecond())) {
				nodesToAdd.add(Pair.makePair(newRelation.getFirst(), existingRelation.getSecond()));
			}
		}
		dominanceRelations.add(newRelation);
		for (Pair<Integer, Integer> newAddition : nodesToAdd) {
			addTransitiveClosure(dominanceRelations, newAddition);
		}
	}

	private Triple<List<BinaryEdge>, List<Double>, List<Double>> pruneBinaryEdges(Chart chart, int i, int j, double normalizer, int sentenceScale) {
		List<BinaryEdge> edges = new ArrayList<BinaryEdge>();
		List<Double> edgeScores = new ArrayList<Double>();
		List<Double> pruningScores = new ArrayList<Double>();
		for (short p=0; p<numSubStatesArray.length; p++) {
			if (!chart.allowedStates[i][j][p]) continue;
			for (short ps=0; ps<numSubStatesArray[p]; ps++) {
				if (!chart.allowedSubStates[i][j][p][ps]) continue;
				for (int k=i+1; k<j; k++) {
					for (BinaryRule br : pruningGrammar.splitRulesWithP(p)) {
						short lc = br.leftChildState;
						if (!chart.allowedStates[i][k][lc]) continue;
						short rc = br.rightChildState;
						if (!chart.allowedStates[k][j][rc]) continue;
						double scalingFactor = 1;
						int thisScale = chart.oScale[i][j][p] + chart.iScale[i][k][lc] + chart.iScale[k][j][rc];
						if (thisScale != sentenceScale) {
							scalingFactor *= Math.pow(ScalingTools.SCALE, thisScale-sentenceScale);
						}
						for (short lcs=0; lcs<numSubStatesArray[lc]; lcs++) {
							if (!chart.allowedSubStates[i][k][lc][lcs]) continue;
							for (short rcs=0; rcs<numSubStatesArray[rc]; rcs++) {
								if (!chart.allowedSubStates[k][j][rc][rcs]) continue;
								double score = chart.oScoresPostU[i][j][p][ps] * br.getScore(ps, lcs, rcs) *
															 chart.iScoresPostU[i][k][lc][lcs] * chart.iScoresPostU[k][j][rc][rcs] *
															 scalingFactor / normalizer;
								BinaryEdge edge = new BinaryEdge(i, j, p, ps, lc, lcs, rc, rcs, k);
								if (score >= pruningThreshold) {
									edges.add(edge);
									edgeScores.add(baseGrammar.getBinaryRule((short)edge.parentState, (short)edge.leftState, (short)edge.rightState).getScore(edge.parentSubstate, edge.leftSubstate, edge.rightSubstate));
									pruningScores.add(score);
								}
							}
						}
					}
				}
			}
		}
		return Triple.makeTriple(edges, edgeScores, pruningScores);
	}

	private Chart getChart(List<String> sentence) {
		preParser.getBestParse(sentence);
		boolean[][][][] constraints = preParser.getAllowedSubStates().clone();
		parser.projectConstraints(constraints, false);
		parser.doConstrainedInsideOutsideScores(convertToStateSetList(sentence), constraints, false, null, null, true);

		return new Chart(parser.getPreUnaryInsideScores(), parser.getPostUnaryInsideScores(),
				parser.getPreUnaryOutsideScores(), parser.getPostUnaryOutsideScores(),
				parser.getInsideScalingFactors(), parser.getOutsideScalingFactors(),
				preParser.getAllowedStates(), constraints);
	}

	private List<StateSet> convertToStateSetList(List<String> sentence) {
		ArrayList<StateSet> list = new ArrayList<StateSet>(sentence.size());
		short ind = 0;
		for (String word : sentence){
	  	StateSet stateSet = new StateSet((short)-1, (short)1, word, ind, (short)(ind+1));
	  	ind++;
	  	stateSet.wordIndex = -2;
	  	stateSet.sigIndex = -2;
			list.add(stateSet);
		}
		return list;
	}

	private static class Chart {
		public final double[][][][] iScoresPreU, iScoresPostU;
		public final double[][][][] oScoresPreU, oScoresPostU;
		public final int[][][] iScale, oScale;
		public final boolean[][][] allowedStates;
		public final boolean[][][][] allowedSubStates;

		public Chart(double[][][][] iScoresPreU, double[][][][] iScoresPostU,
				double[][][][] oScoresPreU,double[][][][] oScoresPostU,
				int[][][] iScale, int[][][] oScale,
				boolean[][][] allowedStates, boolean[][][][] allowedSubStates) {
			this.iScoresPreU = iScoresPreU;
			this.iScoresPostU = iScoresPostU;
			this.oScoresPreU = oScoresPreU;
			this.oScoresPostU = oScoresPostU;
			this.iScale = iScale;
			this.oScale = oScale;
			this.allowedStates = allowedStates;
			this.allowedSubStates = allowedSubStates;
		}
	}
}
