/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.Logger;
import fig.basic.Pair;

/**
 * @author dburkett
 *
 */
public class OracleTreeFinder {
	private final BaseModel baseModel;
	
	public OracleTreeFinder(BaseModel baseModel) {
		this.baseModel = baseModel;
	}
	
	public Tree<String> getOracleTree(PrunedForest candidates, Tree<String> goldTree) {
		RerankedForest oracle = getOracleTreeAsForest(candidates, goldTree);
		if (oracle.hasParseFailure()) return null;
		Tree<Node> stateTree = oracle.getViterbiTree();
		return baseModel.relabelStates(stateTree, candidates.getSentence());
	}
	
	public RerankedForest getOracleTreeAsForest(PrunedForest candidates, Tree<String> goldTree) {
		Set<Node> goldNodes = getGoldNodeSet(goldTree);
		Node[] nodes = candidates.getNodes();
		OracleScore[] oracleScores = new OracleScore[nodes.length];
		for (int node=0; node<oracleScores.length; node++) {
			if (baseModel.isPosTag(nodes[node].state)) {
				oracleScores[node] = new OracleScore();
			}
			else {
				for (int bi : candidates.getBinaryEdgesByNode(node)) {
					OracleScore rightScore = oracleScores[candidates.getRightChildNodeIndex(bi)];
					OracleScore leftScore = oracleScores[candidates.getLeftChildNodeIndex(bi)];
					oracleScores[node] = OracleScore.add(OracleScore.multiply(leftScore, rightScore), oracleScores[node]);
				}
				for (int ui : candidates.getUnaryEdgesByNode(node)) {
					Node intermediateNode = baseModel.getIntermediateCoarseNode(candidates.getUnaryEdges()[ui]);
					OracleScore childScore = oracleScores[candidates.getUnaryChildNodeIndex(ui)];
					if (intermediateNode != null && childScore != null) {
						childScore = childScore.shift(1, goldNodes.contains(intermediateNode));
					}
					oracleScores[node] = OracleScore.add(childScore, oracleScores[node]);
				}
				if (oracleScores[node] != null && !baseModel.isSyntheticState(nodes[node].state)) {
					oracleScores[node] = oracleScores[node].shift(1, goldNodes.contains(nodes[node].coarseNode()));
				}
			}
		}

		return findOracleTopDown(candidates, goldTree.getYield(), goldNodes, oracleScores);
	}
	
	private RerankedForest findOracleTopDown(PrunedForest candidates, List<String> sentence,
			Set<Node> goldNodes, OracleScore[] oracleScores) {
		int n = oracleScores.length;
		boolean[][] isUnaryEdgeBacktrace = new boolean[n][];
		int[][] edgeIndexBacktrace = new int[n][];
		int[][][] childKBacktrace = new int[n][][];
		int root = candidates.getRootNodeIndex();
		if (oracleScores[root] == null) {
			return new RerankedForest(candidates, sentence, new double[n][0], isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace);
		}
		int numNodes = oracleScores[root].getBestF1Size(goldNodes.size() - sentence.size());
		int numGoldNodes = oracleScores[root].val(numNodes);
		try{
			buildBacktrace(candidates, goldNodes, oracleScores, root, numNodes, numGoldNodes,
					isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace);
		} catch(IllegalArgumentException e) {
			Logger.err("Couldn't build backtrace -- dunno why.  Returning parse failure.");
			return new RerankedForest(candidates, sentence, new double[n][0], isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace);
		}
		
		return new RerankedForest(candidates, sentence, null, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace);
	}

	private void buildBacktrace(PrunedForest candidates, Set<Node> goldNodes, OracleScore[] oracleScores,
			int nodeIndex, int numNodes, int numGoldNodes,
			boolean[][] isUnaryEdgeBacktrace, int[][] edgeIndexBacktrace, int[][][] childKBacktrace) {
		Node node = candidates.getNodes()[nodeIndex];
		if (baseModel.isPosTag(node.state)) return;
		int targetNodes = numNodes;
		int targetGoldNodes = numGoldNodes;
		if (!baseModel.isSyntheticState(node.state)) {
			targetNodes -= 1;
			targetGoldNodes -= (goldNodes.contains(node.coarseNode()) ? 1 : 0);
		}
		List<Pair<Integer, Integer>> binaryCandidates = new ArrayList<Pair<Integer, Integer>>();
		List<Integer> unaryCandidates = new ArrayList<Integer>();
		for (int bi : candidates.getBinaryEdgesByNode(nodeIndex)) {
			OracleScore leftScore = oracleScores[candidates.getLeftChildNodeIndex(bi)];
			OracleScore rightScore = oracleScores[candidates.getRightChildNodeIndex(bi)];
			int split = OracleScore.checkAttainableAndFindSplit(leftScore, rightScore, targetNodes, targetGoldNodes);
			if (split >= 0) {
				binaryCandidates.add(Pair.makePair(bi, split));
			}
		}
		for (int ui : candidates.getUnaryEdgesByNode(nodeIndex)) {
			OracleScore childScore = oracleScores[candidates.getUnaryChildNodeIndex(ui)];
			Node intermediateNode = baseModel.getIntermediateCoarseNode(candidates.getUnaryEdges()[ui]);
			if (childScore != null && intermediateNode != null) {
				childScore = childScore.shift(1, goldNodes.contains(intermediateNode));
			}
			if (childScore != null && childScore.val(targetNodes) == targetGoldNodes) {
				unaryCandidates.add(ui);
			}
		}
		binaryCandidates = filterBinaryCandidatesByPOS(candidates.getBinaryEdges(), binaryCandidates, goldNodes);
		if (targetNodes == 0) {
			unaryCandidates = filterUnaryCandidatesByPOS(candidates.getUnaryEdges(), unaryCandidates, goldNodes);
		}
		Pair<Pair<Integer, Integer>, Integer> bestEdge = getBestCandidate(candidates, binaryCandidates, unaryCandidates);
		if (bestEdge == null) {
			throw new IllegalArgumentException("Error in constructing backtrace!");
		}
		if (bestEdge.getFirst() == null) {
			int edge = bestEdge.getSecond();
			isUnaryEdgeBacktrace[nodeIndex] = new boolean[] { true };
			edgeIndexBacktrace[nodeIndex] = new int[] { edge };
			childKBacktrace[nodeIndex] = new int[][] { { 0 } };
			Node intermediateNode = baseModel.getIntermediateCoarseNode(candidates.getUnaryEdges()[edge]);
			if (intermediateNode != null) {
				targetNodes -= 1;
				targetGoldNodes -= goldNodes.contains(intermediateNode) ? 1 : 0;
			}
			buildBacktrace(candidates, goldNodes, oracleScores, candidates.getUnaryChildNodeIndex(edge),
					targetNodes, targetGoldNodes, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace);
		} else {
			int edge = bestEdge.getFirst().getFirst();
			int nodes1 = bestEdge.getFirst().getSecond();
			int nodes2 = targetNodes - nodes1;
			int child1 = candidates.getLeftChildNodeIndex(edge);
			int child2 = candidates.getRightChildNodeIndex(edge);
			int gold1 = oracleScores[child1].val(nodes1);
			int gold2 = oracleScores[child2].val(nodes2);
			isUnaryEdgeBacktrace[nodeIndex] = new boolean[] { false };
			edgeIndexBacktrace[nodeIndex] = new int[] { edge };
			childKBacktrace[nodeIndex] = new int[][] { { 0, 0 } };
			buildBacktrace(candidates, goldNodes, oracleScores, child1, nodes1, gold1, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace);
			buildBacktrace(candidates, goldNodes, oracleScores, child2, nodes2, gold2, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace);
		}
	}

	private Pair<Pair<Integer, Integer>, Integer> getBestCandidate(PrunedForest forest,
			List<Pair<Integer, Integer>> binaryCandidates, List<Integer> unaryCandidates) {
		double bestPruningScore = Double.NEGATIVE_INFINITY;
		Pair<Pair<Integer, Integer>, Integer> bestCandidate = null;
		for (Pair<Integer, Integer> cand  : binaryCandidates) {
			double pruningScore = forest.getBinaryEdgePruningScore(cand.getFirst());
			if (pruningScore > bestPruningScore) {
				bestPruningScore = pruningScore;
				bestCandidate = new Pair<Pair<Integer, Integer>, Integer>(cand, null);
			}
		}
		for (Integer cand : unaryCandidates) {
			double pruningScore = forest.getUnaryEdgePruningScore(cand);
			if (pruningScore > bestPruningScore) {
				bestPruningScore = pruningScore;
				bestCandidate = new Pair<Pair<Integer, Integer>, Integer>(null, cand);
			}
		}
		return bestCandidate;
	}

	private List<Integer> filterUnaryCandidatesByPOS(UnaryEdge[] unaryEdges, List<Integer> unaryCandidates, Set<Node> goldNodes) {
		boolean hasGoldPOS = false;
		for (int cand : unaryCandidates) {
			if (baseModel.isPosTag(unaryEdges[cand].childState) && goldNodes.contains(unaryEdges[cand].getChild().coarseNode())) {
				hasGoldPOS = true;
			}
		}
		if (!hasGoldPOS) return unaryCandidates;
		List<Integer> newCands = new ArrayList<Integer>();
		for (int cand : unaryCandidates) {
			if (baseModel.isPosTag(unaryEdges[cand].childState) && goldNodes.contains(unaryEdges[cand].getChild().coarseNode())) {
				newCands.add(cand);
			}
		}
		return newCands;
	}

	private List<Pair<Integer, Integer>> filterBinaryCandidatesByPOS(BinaryEdge[] binaryEdges,
			List<Pair<Integer, Integer>> binaryCandidates, Set<Node> goldNodes) {
		int bestGoldPOSCount = 0;
		for (Pair<Integer, Integer> cand : binaryCandidates) {
			int goldCount = 0;
			BinaryEdge edge = binaryEdges[cand.getFirst()];
			if (baseModel.isPosTag(edge.leftState) && goldNodes.contains(edge.getLeftChild().coarseNode())) goldCount++;
			if (baseModel.isPosTag(edge.rightState) && goldNodes.contains(edge.getRightChild().coarseNode())) goldCount++;
			bestGoldPOSCount = Math.max(bestGoldPOSCount, goldCount);
		}
		if (bestGoldPOSCount == 0) return binaryCandidates;
		List<Pair<Integer, Integer>> newCands = new ArrayList<Pair<Integer,Integer>>();
		for (Pair<Integer, Integer> cand : binaryCandidates) {
			int goldCount = 0;
			BinaryEdge edge = binaryEdges[cand.getFirst()];
			if (baseModel.isPosTag(edge.leftState) && goldNodes.contains(edge.getLeftChild().coarseNode())) goldCount++;
			if (baseModel.isPosTag(edge.rightState) && goldNodes.contains(edge.getRightChild().coarseNode())) goldCount++;
			if (goldCount == bestGoldPOSCount) {
				newCands.add(cand);
			}
		}
		return newCands;
	}

	public Set<Node> getGoldNodeSet(Tree<String> goldTree) {
		Tree<StateSet> stateSets = stringTreeToStatesetTree(goldTree);
		Set<Node> nodeSet = new HashSet<Node>();
		for (Tree<StateSet> stateNode : stateSets.getPreOrderTraversal()) {
			if (stateNode.isLeaf()) continue;
			StateSet state = stateNode.getLabel();
			nodeSet.add(new Node(state.from, state.to, state.getState()));
		}
		return nodeSet;
	}

  /**
   * Convert a single Tree[String] to Tree[StateSet]
   * 
   * @param tree
   * @param numStates
   * @param tagNumberer
   * @return
   */
  private Tree<StateSet> stringTreeToStatesetTree (Tree<String> tree){
    Tree<StateSet> result = stringTreeToStatesetTree(tree,false,0,tree.getYield().size());
    // set the positions properly:
  	List<StateSet> words = result.getYield();
  	//for all words in sentence
  	for (short position = 0; position < words.size(); position++) {
  		words.get(position).from = position;
  		words.get(position).to = (short)(position + 1);
  	}
  	return result;
  }
    
  private Tree<StateSet> stringTreeToStatesetTree (Tree<String> tree, boolean splitRoot, int from, int to){
    if (tree.isLeaf()) {
      StateSet newState = new StateSet((short)0, (short)1, tree.getLabel().intern(),(short)from,(short)to);
      return new Tree<StateSet>(newState);
    }
    short label = (short)baseModel.getState(tree.getLabel());
    if (label<0) label =0;
    short nodeNumStates = baseModel.getNumSubstates(label);
    if (!splitRoot) nodeNumStates = 1;
    StateSet newState = new StateSet(label, nodeNumStates, null, (short)from , (short)to);
    Tree<StateSet> newTree = new Tree<StateSet>(newState);
    List<Tree<StateSet>> newChildren = new ArrayList<Tree<StateSet>>(); 
    for (Tree<String> child : tree.getChildren()) {
    	short length = (short) child.getYield().size(); 
      Tree<StateSet> newChild = stringTreeToStatesetTree(child, true, from, from+length);
      from += length;
      newChildren.add(newChild);
    }
    newTree.setChildren(newChildren);
    return newTree;
  }
}
