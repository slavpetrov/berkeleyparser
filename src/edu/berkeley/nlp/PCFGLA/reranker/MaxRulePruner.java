/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import java.util.ArrayList;
import java.util.Arrays;
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
import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.util.Lists;
import edu.berkeley.nlp.util.Logger;
import edu.berkeley.nlp.util.PriorityQueue;
import edu.berkeley.nlp.util.ScalingTools;
import edu.berkeley.nlp.util.Triple;
import fig.basic.Pair;

/**
 * @author dburkett
 *
 */
public class MaxRulePruner implements Pruner {
	private final Grammar baseGrammar;
	private final Lexicon baseLexicon;
	private final CoarseToFineMaxRuleParser preParser;
	private final ConstrainedTwoChartsParser parser;
	private final short[] numSubStatesArray;
	private final double pruningThreshold;
	
	public MaxRulePruner(Grammar grammar, Lexicon lexicon, SpanPredictor spanPredictor, double pruningThreshold) {
		this.baseGrammar = grammar;
		this.baseLexicon = lexicon;
		this.pruningThreshold = pruningThreshold;
		this.preParser = new CoarseToFineMaxRuleParser(grammar, lexicon, 1.0, -1, false, false, false, false, false, false, false);
		preParser.initCascade(grammar, lexicon);
		this.parser = new ConstrainedTwoChartsParser(grammar, lexicon, spanPredictor);
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

		MaxRuleChart chart = getChart(sentence);

		int n = sentence.size();
		double normalizer = chart.iScoresPostU[0][n][0];
		for (int diff = 1; diff<=n; diff++) {
			for (int i=0; i+diff<=n; i++) {
				int j = i+diff;
				// We alternate between binary and unary rules on increasing length spans to ensure
				// that nodes are added to the list in topological order
				if (diff > 1) {
					Triple<List<BinaryEdge>, List<Double>, List<Double>> spanBinaryEdges = pruneBinaryEdges(chart, i, j, normalizer);
					binaryEdges.addAll(spanBinaryEdges.getFirst());
					binaryEdgeCosts.addAll(spanBinaryEdges.getSecond());
					binaryEdgePruningScores.addAll(spanBinaryEdges.getThird());
					addChildNodesFromBinaryRules(nodes, nodeSet, spanBinaryEdges.getFirst());
				}
				Triple<List<UnaryEdge>, List<Double>, List<Double>> spanUnaryEdges = pruneUnaryEdges(chart, i, j, normalizer);
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
				lexicalNodeCosts[i] = chart.lexicalScores[node.startIndex][node.state];
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
			}
			Node rightChild = edge.getRightChild();
			if (!nodeSet.contains(rightChild)) {
				nodes.add(rightChild);
				nodeSet.add(rightChild);
			}
		}
	}
	
	private Triple<List<UnaryEdge>, List<Double>, List<Double>> pruneUnaryEdges(MaxRuleChart chart, int i, int j, double normalizer) {
		List<UnaryEdge> edges = new ArrayList<UnaryEdge>();
		List<Double> edgeScores = new ArrayList<Double>();
		List<Double> pruningScores = new ArrayList<Double>();
		PriorityQueue<Pair<UnaryEdge, Double>> pq = new PriorityQueue<Pair<UnaryEdge, Double>>();
		for (short p=0; p<numSubStatesArray.length; p++) {
			if (!chart.allowedStates[i][j][p]) continue;
			for (int e=0; e<chart.unaryEdges[i][j][p].length; e++) {
				UnaryEdge edge = chart.unaryEdges[i][j][p][e];
				int c = edge.childState;
				if (!chart.allowedStates[i][j][c]) continue;
				double score = chart.oScoresPreU[i][j][p] + chart.unaryEdgeScores[i][j][p][e] +
											 chart.iScoresPreU[i][j][c] - normalizer;
				if (score > pruningThreshold) {
					pq.add(Pair.makePair(edge, chart.unaryEdgeScores[i][j][p][e]), score);
				}
			}
		}
		// All this mumbo jumbo here is to avoid having a bidirectional path between two nodes.
		// We need to be able to topologically sort the nodes
		HashSet<Pair<Integer, Integer>> dominanceRelations = new HashSet<Pair<Integer, Integer>>();
		while (pq.hasNext()) {
			double score = pq.getPriority();
			Pair<UnaryEdge, Double> pair = pq.next();
			UnaryEdge edge = pair.getFirst();
			Pair<Integer, Integer> conflictingRelation = Pair.makePair(edge.childState, edge.parentState);
			if (!dominanceRelations.contains(conflictingRelation)) {
				edges.add(edge);
				edgeScores.add(pair.getSecond());
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

	private Triple<List<BinaryEdge>, List<Double>, List<Double>> pruneBinaryEdges(MaxRuleChart chart, int i, int j, double normalizer) {
		List<BinaryEdge> edges = new ArrayList<BinaryEdge>();
		List<Double> edgeScores = new ArrayList<Double>();
		List<Double> pruningScores = new ArrayList<Double>();
		for (short p=0; p<numSubStatesArray.length; p++) {
			if (!chart.allowedStates[i][j][p]) continue;
			for (int e=0; e<chart.binaryEdges[i][j][p].length; e++) {
				BinaryEdge edge = chart.binaryEdges[i][j][p][e];
				int k = edge.splitIndex;
				int lc = edge.leftState;
				int rc = edge.rightState;
				if (!chart.allowedStates[i][k][lc]) continue;
				if (!chart.allowedStates[k][j][rc]) continue;
				double score = chart.oScoresPostU[i][j][p] + chart.binaryEdgeScores[i][j][p][e] +
											 chart.iScoresPostU[i][k][lc] + chart.iScoresPostU[k][j][rc] - normalizer;
				if (score >= pruningThreshold) {
					edges.add(edge);
					edgeScores.add(chart.binaryEdgeScores[i][j][p][e]);
					pruningScores.add(score);
				}
			}

		}
		return Triple.makeTriple(edges, edgeScores, pruningScores);
	}

	private MaxRuleChart getChart(List<String> sentence) {
		preParser.getBestParse(sentence);
		boolean[][][][] constraints = preParser.getAllowedSubStates().clone();
		parser.projectConstraints(constraints, false);
		parser.doConstrainedInsideOutsideScores(convertToStateSetList(sentence), constraints, false, null, null, false);

		Chart substateChart = new Chart(parser.getPreUnaryInsideScores(), parser.getPostUnaryInsideScores(),
				parser.getPreUnaryOutsideScores(), parser.getPostUnaryOutsideScores(),
				parser.getInsideScalingFactors(), parser.getOutsideScalingFactors(),
				preParser.getAllowedStates(), constraints);
		return new MaxRuleChart(substateChart, sentence);
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

	private class Chart {
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
		
		public double getRuleScore(BinaryRule br, int start, int end, int split, double tree_score, int tree_scale) {
			int pState = br.parentState;
			int lState = br.leftChildState;
			int rState = br.rightChildState;
			double[][][] scores = br.scores;
			double ruleScore = 0;
			double scalingFactor = ScalingTools.calcScaleFactor(
					oScale[start][end][pState]+
					iScale[start][split][lState]+
					iScale[split][end][rState]-tree_scale);
			if (scalingFactor==0) return ruleScore;
			for (int lp = 0; lp < numSubStatesArray[lState]; lp++) {
				double lIS = iScoresPostU[start][split][lState][lp];
				if (lIS == 0) continue;
				for (int rp = 0; rp < numSubStatesArray[rState]; rp++) {
					if (scores[lp][rp]==null) continue;
					double rIS = iScoresPostU[split][end][rState][rp];
					if (rIS == 0) continue;
					for (int np = 0; np < numSubStatesArray[pState]; np++) {
						double pOS = oScoresPostU[start][end][pState][np];
						if (pOS == 0) continue;
						double ruleS = scores[lp][rp][np];
						if (ruleS == 0) continue;
						ruleScore += pOS * scalingFactor * ruleS / tree_score * lIS * rIS;
					}
				}
			}
			return ruleScore;
		}
		
		public double getRuleScore(UnaryRule ur, int start, int end, double tree_score, int tree_scale) {
			int pState = ur.parentState;
			int cState = ur.childState;
			double[][] scores = ur.scores;
      double ruleScore = 0;
			double scalingFactor = ScalingTools.calcScaleFactor(
					oScale[start][end][pState]+iScale[start][end][cState]-tree_scale);
	  	if (scalingFactor==0) return ruleScore;
      for (int cp = 0; cp < numSubStatesArray[cState]; cp++) {
        double cIS = iScoresPreU[start][end][cState][cp];
        if (cIS == 0) continue;
        if (scores[cp]==null) continue;
        for (int np = 0; np < numSubStatesArray[pState]; np++) {
          double pOS = oScoresPreU[start][end][pState][np];
          if (pOS == 0) continue;
          double ruleS = scores[cp][np];
          if (ruleS == 0) continue;
          ruleScore += pOS * scalingFactor * ruleS / tree_score * cIS ;
        }
      }
      return ruleScore;
		}
		
		public double getLexicalScore(List<String> sentence, int index, int tag, double tree_score, int tree_scale) {
			int start = index;
			int end = start+1;
      double lexiconScores = 0;
			double scalingFactor = ScalingTools.calcScaleFactor(oScale[start][end][tag]-tree_scale);
	  	if (scalingFactor==0) return lexiconScores;
	  	int nTagStates = numSubStatesArray[tag];
	  	String word = sentence.get(start);
      double[] lexiconScoreArray = baseLexicon.score(word, (short)tag, start, false, false);
      for (int tp = 0; tp < nTagStates; tp++) {
        double pOS = oScoresPostU[start][end][tag][tp];
        if (pOS == 0) continue;
        double ruleS = lexiconScoreArray[tp];
        if (ruleS==0) continue;
        lexiconScores += (pOS * ruleS) / tree_score;
      }
      return lexiconScores * scalingFactor;
		}
	}
	
	private class MaxRuleChart {
		public final double[][][] iScoresPreU, iScoresPostU;
		public final double[][][] oScoresPreU, oScoresPostU;
		public final int[][][] bestSubstates;
		public final boolean[][][] allowedStates;
		public final BinaryEdge[][][][] binaryEdges;
		public final double[][][][] binaryEdgeScores;
		public final UnaryEdge[][][][] unaryEdges;
		public final double[][][][] unaryEdgeScores;
		public final double[][] lexicalScores;
		
		public MaxRuleChart(Chart substateChart, List<String> sentence) {
			int n = sentence.size();
			this.allowedStates = substateChart.allowedStates;
			this.bestSubstates = findBestSubstates(substateChart, n);
			this.lexicalScores = computeLexicalScores(substateChart, sentence);
			Pair<BinaryEdge[][][][], double[][][][]> binary = computeBinaryRuleScores(substateChart, n);
			this.binaryEdges = binary.getFirst();
			this.binaryEdgeScores = binary.getSecond();
			Pair<UnaryEdge[][][][], double[][][][]> unary = computeUnaryRuleScores(substateChart, n);
			this.unaryEdges = unary.getFirst();
			this.unaryEdgeScores = unary.getSecond();
			Pair<double[][][], double[][][]> iScores = getViterbiInsideScores(n);
			this.iScoresPreU = iScores.getFirst();
			this.iScoresPostU = iScores.getSecond();
			Pair<double[][][], double[][][]> oScores = getViterbiOutsideScores(n);
			this.oScoresPreU = oScores.getFirst();
			this.oScoresPostU = oScores.getSecond();
		}

		private Pair<double[][][], double[][][]> getViterbiOutsideScores(int n) {
			double[][][] preU = new double[n][n+1][];
			double[][][] postU = new double[n][n+1][];
			for (int i=0; i<n; i++) {
				for (int j=i+1; j<=n; j++) {
					preU[i][j] = DoubleArrays.constantArray(Double.NEGATIVE_INFINITY, numSubStatesArray.length);
					if (j - i == n) {
						preU[i][j][0] = 0;
					}
				}
			}
			for (int diff=n; diff>=1; diff--) {
				for (int i=0; i+diff<=n; i++) {
					int j = i+diff;
					postU[i][j] = getUnaryOutside(i, j, preU[i][j]);
					updateBinaryOutside(i, j, preU, postU);
				}
			}
			return Pair.makePair(preU, postU);
		}

		private void updateBinaryOutside(int i, int j, double[][][] preU, double[][][] postU) {
			if (j - i <= 1) return;
			int numStates = numSubStatesArray.length;
			for (int p=0; p<numStates; p++) {
				if (!allowedStates[i][j][p]) continue;
				for (int e=0; e<binaryEdges[i][j][p].length; e++) {
					BinaryEdge be = binaryEdges[i][j][p][e];
					double edgeScore = binaryEdgeScores[i][j][p][e];
					int k = be.splitIndex;
					int lc = be.leftState;
					int rc = be.rightState;
					double leftScore = edgeScore + postU[i][j][p] + iScoresPostU[k][j][rc];
					preU[i][k][lc] = Math.max(leftScore, preU[i][k][lc]);
					double rightScore = edgeScore + postU[i][j][p] + iScoresPostU[i][k][lc];
					preU[k][j][rc] = Math.max(rightScore, preU[k][j][rc]);
				}
			}
		}

		private double[] getUnaryOutside(int i, int j, double[] preU) {
			double[] scores = preU.clone();
			for (int p=0; p<numSubStatesArray.length; p++) {
				if (!allowedStates[i][j][p]) continue;
				for (int e=0; e<unaryEdges[i][j][p].length; e++) {
					UnaryEdge ue = unaryEdges[i][j][p][e];
					int c = ue.childState;
					if (!allowedStates[i][j][c]) continue;
					double edgeScore = unaryEdgeScores[i][j][p][e];
					double score = edgeScore + preU[p];
					scores[c] = Math.max(scores[c], score);
				}
			}
			return scores;
		}

		private Pair<double[][][], double[][][]> getViterbiInsideScores(int n) {
			double[][][] preU = new double[n][n+1][];
			double[][][] postU = new double[n][n+1][];
			for (int diff=1; diff<=n; diff++) {
				for (int i=0; i+diff<=n; i++) {
					int j = i+diff;
					preU[i][j] = getBinaryInside(i, j, postU);
					postU[i][j] = getUnaryInside(i, j, preU[i][j]);
				}
			}
			return Pair.makePair(preU, postU);
		}

		private double[] getUnaryInside(int i, int j, double[] preU) {
			double[] scores = preU.clone();
			for (int p=0; p<numSubStatesArray.length; p++) {
				if (!allowedStates[i][j][p]) continue;
				for (int e=0; e<unaryEdges[i][j][p].length; e++) {
					UnaryEdge ue = unaryEdges[i][j][p][e];
					double edgeScore = unaryEdgeScores[i][j][p][e];
					double score = edgeScore + preU[ue.childState];
					scores[p] = Math.max(scores[p], score);
				}
			}
			return scores;
		}

		private double[] getBinaryInside(int i, int j, double[][][] postU) {
			int numStates = numSubStatesArray.length;
			if (j - i == 1) {
				return lexicalScores[i];
			}
			double[] scores = DoubleArrays.constantArray(Double.NEGATIVE_INFINITY, numStates);
			for (int p=0; p<numStates; p++) {
				if (!allowedStates[i][j][p]) continue;
				for (int e=0; e<binaryEdges[i][j][p].length; e++) {
					BinaryEdge be = binaryEdges[i][j][p][e];
					double edgeScore = binaryEdgeScores[i][j][p][e];
					int k = be.splitIndex;
					int lc = be.leftState;
					int rc = be.rightState;
					double score = edgeScore + postU[i][k][lc] + postU[k][j][rc];
					scores[p] = Math.max(scores[p], score);
				}
			}
			return scores;
		}

		private Pair<UnaryEdge[][][][], double[][][][]> computeUnaryRuleScores(Chart chart, int n) {
			UnaryEdge[][][][] edges = new UnaryEdge[n][n+1][][];
			double[][][][] scores = new double[n][n+1][][];
			UnaryEdge[] dummyEdge = new UnaryEdge[0];
			int numStates = numSubStatesArray.length;
			double tree_score = chart.iScoresPostU[0][n][0][0];
			int tree_scale = chart.iScale[0][n][0];
			for (int i=0; i<n; i++) {
				for (int j=i+1; j<=n; j++) {
					edges[i][j] = new UnaryEdge[numStates][];
					scores[i][j] = new double[numStates][];
					for (int p=0; p<numStates; p++) {
						if (!allowedStates[i][j][p]) continue;
						List<UnaryEdge> currentEdges = new ArrayList<UnaryEdge>();
						List<Double> currentScores = new ArrayList<Double>();
						for (UnaryRule ur : baseGrammar.getClosedSumUnaryRulesByParent(p)) {
							short c = ur.childState;
							if (!allowedStates[i][j][c]) continue;
							double score = chart.getRuleScore(ur, i, j, tree_score, tree_scale);
							if (score > 0) {
								UnaryEdge edge = new UnaryEdge(i, j, p, bestSubstates[i][j][p], c, bestSubstates[i][j][c]);
								currentEdges.add(edge);
								currentScores.add(Math.log(score));
							}
						}
						edges[i][j][p] = currentEdges.toArray(dummyEdge);
						scores[i][j][p] = toArray(currentScores);
					}
				}
			}
			return Pair.makePair(edges, scores);
		}

		private Pair<BinaryEdge[][][][], double[][][][]> computeBinaryRuleScores(Chart chart, int n) {
			BinaryEdge[][][][] edges = new BinaryEdge[n][n+1][][];
			double[][][][] scores = new double[n][n+1][][];
			BinaryEdge[] dummyEdge = new BinaryEdge[0];
			int numStates = numSubStatesArray.length;
			double tree_score = chart.iScoresPostU[0][n][0][0];
			int tree_scale = chart.iScale[0][n][0];
			for (int i=0; i<n; i++) {
				for (int j=i+2; j<=n; j++) {
					edges[i][j] = new BinaryEdge[numStates][];
					scores[i][j] = new double[numStates][];
					for (int p=0; p<numStates; p++) {
						if (!allowedStates[i][j][p]) continue;
						List<BinaryEdge> currentEdges = new ArrayList<BinaryEdge>();
						List<Double> currentScores = new ArrayList<Double>();
						for (int k=i+1; k<j; k++) {
							for (BinaryRule br : baseGrammar.splitRulesWithP(p)) {
								short lc = br.leftChildState;
								short rc = br.rightChildState;
								if (!allowedStates[i][k][lc]) continue;
								if (!allowedStates[k][j][rc]) continue;
								double score = chart.getRuleScore(br, i, j, k, tree_score, tree_scale);
								if (score > 0) {
									BinaryEdge edge = new BinaryEdge(i, j, p, bestSubstates[i][j][p], lc, bestSubstates[i][k][lc],
									  	rc, bestSubstates[k][j][rc], k);
									currentEdges.add(edge);
									currentScores.add(Math.log(score));
								}
							}
						}
						edges[i][j][p] = currentEdges.toArray(dummyEdge);
						scores[i][j][p] = toArray(currentScores);
					}
				}
			}
			return Pair.makePair(edges, scores);
		}

		private double[][] computeLexicalScores(Chart chart, List<String> sentence) {
			int n = sentence.size();
			int numStates = numSubStatesArray.length;
			double tree_score = chart.iScoresPostU[0][n][0][0];
			int tree_scale = chart.iScale[0][n][0];
			double[][] scores = new double[n][numStates];
			for (int tag=0; tag<numStates; tag++) {
				if (baseGrammar.isGrammarTag(tag)) continue;
				for (int i=0; i<n; i++) {
					if (!allowedStates[i][i+1][tag]) continue;
					scores[i][tag] = Math.log(chart.getLexicalScore(sentence, i, tag, tree_score, tree_scale));
				}
			}
			return scores;
		}

		private double[] toArray(List<Double> list) {
			double[] a = new double[list.size()];
			int index = 0;
			for (double d : list) {
				a[index++] = d;
			}
			return a;
		}

		private int[][][] findBestSubstates(Chart chart, int n) {
			int numStates = numSubStatesArray.length;
			int[][][] bestSubstates = new int[n][][];
			for (int i=0; i<n; i++) {
				bestSubstates[i] = new int[n+1][];
				for (int j=i+1; j<=n; j++) {
					boolean initialized = false;
					for (int s=0; s<numStates; s++) {
						if (allowedStates[i][j][s]) {
							if (!initialized) {
								bestSubstates[i][j] = new int[numStates];
								Arrays.fill(bestSubstates[i][j], -1);
								initialized = true;
							}
							double scalingFactor = ScalingTools.calcScaleFactor(chart.oScale[i][j][s] + chart.iScale[i][j][s]);
							if (scalingFactor == 0) {
								continue;
							}
							double bestScore = 0;
							int bestSubstate = -1;
							for (int ss = 0; ss<numSubStatesArray[s]; ss++) {
								double score = chart.iScoresPostU[i][j][s][ss] * scalingFactor * chart.oScoresPreU[i][j][s][ss];
								score = Math.max(score, chart.oScoresPostU[i][j][s][ss] * scalingFactor * chart.iScoresPreU[i][j][s][ss]);
								if (score > bestScore) {
									bestScore = score;
									bestSubstate = ss;
								}
							}
							bestSubstates[i][j][s] = bestSubstate;
						}
					}
				}
			}
			return bestSubstates;
		}
	}

}
