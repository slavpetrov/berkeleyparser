/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import java.util.ArrayList;
import java.util.List;

import edu.berkeley.nlp.PCFGLA.reranker.FeatureExtractorManager.Feature;
import edu.berkeley.nlp.PCFGLA.reranker.RerankingFeatures.BigramTreeFeature;
import edu.berkeley.nlp.PCFGLA.reranker.RerankingFeatures.ParentRuleFeature;
import edu.berkeley.nlp.PCFGLA.reranker.RerankingFeatures.ThreeAncestorWord;
import edu.berkeley.nlp.PCFGLA.reranker.RerankingFeatures.AnaphorAntecedentFeature;
import fig.basic.Indexer;

/**
 * @author rafferty
 *
 */
public class NonlocalFeatureExtractors implements NonlocalFeatureExtractor {
	private final Indexer<Feature> featureIndex;
	private final BaseModel baseModel;
	private final boolean useOnlyStateFeatures;
	private final boolean useFullParsingFeatures;



	/**
	 * Make a new NonlocalFeatureExtractors object which stores its features in the 
	 * given index. This index should be shared between all feature extractors.
	 * @param featureIndex
	 */
	public NonlocalFeatureExtractors(Indexer<Feature> featureIndex, BaseModel baseModel) {
		this(featureIndex, baseModel, true);
	}

	public NonlocalFeatureExtractors(Indexer<Feature> featureIndex, BaseModel baseModel,boolean useOnlyStateFeatures) {
		this.featureIndex = featureIndex;
		this.baseModel = baseModel;
		this.useOnlyStateFeatures = useOnlyStateFeatures;
		this.useFullParsingFeatures = false;
	}

	/**
	 * backtraces are indexed by nodes, then by k
	 * int rootEdgeIndex root edge of the subtree for which we're currently computing features
	 * boolean[][] isUnaryEdgeBacktrace first indexed by node index (get from forest), then by derivation (use childK)
	 * int[][] edgeIndexBacktrace index of the edge from (first index) node and (second index) derivation
	 * int[][][] childKBacktrace get which derivation we want for the grandchild (can recurse on this to go farther down); indexed by node, derivation, and child (L/R)
	 */
	public List<Integer> computeNonlocalIndicatorFeaturesForBinaryEdge(
			PrunedForest forest, int rootEdgeIndex, boolean[][] isUnaryEdgeBacktrace,
			int[][] edgeIndexBacktrace, int[][][] childKBacktrace, int leftChildK,
			int rightChildK, List<String> sentence) {
		List<Integer> feats = new ArrayList<Integer>();
		computeBinaryParentRuleFeatures(forest, rootEdgeIndex, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, leftChildK, rightChildK,feats);
		computeBinaryBigramTreeNonLocalFeatures(forest, rootEdgeIndex, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, leftChildK, rightChildK, sentence,feats);
		computeBinaryThreeWordAncestorFeatures(forest, rootEdgeIndex, isUnaryEdgeBacktrace,
				 edgeIndexBacktrace, childKBacktrace, leftChildK, rightChildK,  sentence, feats);
		if (useFullParsingFeatures) {
			computeBinaryAntecedentAnaphorFeatures(forest, rootEdgeIndex, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, leftChildK, rightChildK, feats);
		}
		return feats;
	}


	public List<Integer> computeNonlocalIndicatorFeaturesForUnaryEdge(
			PrunedForest forest, int rootEdgeIndex, boolean[][] isUnaryEdgeBacktrace,
			int[][] edgeIndexBacktrace, int[][][] childKBacktrace, int childK, List<String> sentence) {
		List<Integer> feats = new ArrayList<Integer>();
		computeUnaryParentRuleFeatures(forest, rootEdgeIndex, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace,childK,feats);
		computeUnaryThreeWordAncestorFeatures(forest, rootEdgeIndex, isUnaryEdgeBacktrace,
				edgeIndexBacktrace, childKBacktrace, childK, sentence, feats);
		if (useFullParsingFeatures) {
			computeUnaryAntecedentAnaphorFeatures(forest, rootEdgeIndex, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, childK, feats);
		}
		return feats;
	}

	/**
	 * Compute words, their preterminal labels, their parent labels, and their grandparent labels
	 * @param binaryEdge
	 * @param sentence
	 * @return
	 */
	public void computeBinaryThreeWordAncestorFeatures(PrunedForest forest, int rootEdgeIndex, boolean[][] isUnaryEdgeBacktrace,
			int[][] edgeIndexBacktrace, int[][][] childKBacktrace, int leftChildK, int rightChildK, List<String> sentence, List<Integer> feats) {
		BinaryEdge curEdge = forest.getBinaryEdges()[rootEdgeIndex];
		//get the children
		int leftChildNodeIndex = forest.getLeftChildNodeIndex(rootEdgeIndex);
		int rightChildNodeIndex = forest.getRightChildNodeIndex(rootEdgeIndex);
		if(!useOnlyStateFeatures) {
			if(!baseModel.isPosTag(forest.getNodes()[leftChildNodeIndex].state))
				computeThreeWordAncestorFeaturesHelper(curEdge.parentState, curEdge.parentSubstate, 
					forest, isUnaryEdgeBacktrace, edgeIndexBacktrace, leftChildK, sentence, leftChildNodeIndex,feats);
			if(!baseModel.isPosTag(forest.getNodes()[rightChildNodeIndex].state))
				computeThreeWordAncestorFeaturesHelper(curEdge.parentState, curEdge.parentSubstate, 
					forest, isUnaryEdgeBacktrace, edgeIndexBacktrace, rightChildK, sentence, rightChildNodeIndex,feats);
		}
	}

	public void computeUnaryThreeWordAncestorFeatures(PrunedForest forest, int rootEdgeIndex, boolean[][] isUnaryEdgeBacktrace,
			int[][] edgeIndexBacktrace, int[][][] childKBacktrace, int childK, List<String> sentence, List<Integer> feats) {
		UnaryEdge curEdge = forest.getUnaryEdges()[rootEdgeIndex];
		//get the children
		int childNodeIndex = forest.getUnaryChildNodeIndex(rootEdgeIndex);
		if(!baseModel.isPosTag(forest.getNodes()[childNodeIndex].state))
			computeThreeWordAncestorFeaturesHelper(curEdge.parentState, curEdge.parentSubstate, 
				forest, isUnaryEdgeBacktrace, edgeIndexBacktrace, childK, sentence, childNodeIndex, feats);
	}

	public void computeThreeWordAncestorFeaturesHelper(int parentState, int parentSubstate, 
			PrunedForest forest, boolean[][] isUnaryEdgeBacktrace, int[][] edgeIndexBacktrace, int childK, 
			List<String> sentence, int childNodeIndex, List<Integer> feats) {

		int childEdgeIndex = edgeIndexBacktrace[childNodeIndex][childK];
		//see if the child is unary
		if(isUnaryEdgeBacktrace[childNodeIndex][childK]) {
			UnaryEdge childEdge = forest.getUnaryEdges()[childEdgeIndex];
			//we have a feature if the child of the child is a preterm
			if(baseModel.isPosTag(childEdge.childState)) {
				if(!useOnlyStateFeatures) {
					feats.add(featureIndex.getIndex(new ThreeAncestorWord(parentState, parentSubstate, childEdge.parentState, childEdge.parentSubstate, childEdge.childState, childEdge.childSubstate, sentence.get(childEdge.startIndex))));
				}
				feats.add(featureIndex.getIndex(new ThreeAncestorWord(parentState, -1, childEdge.parentState, -1, childEdge.childState, -1, sentence.get(childEdge.startIndex))));
			}
		} else {
			BinaryEdge childEdge = forest.getBinaryEdges()[childEdgeIndex];
			if(baseModel.isPosTag(childEdge.leftState)) {
				if(!useOnlyStateFeatures) {
					feats.add(featureIndex.getIndex(new ThreeAncestorWord(parentState, parentSubstate, childEdge.parentState, childEdge.parentSubstate, childEdge.leftState, childEdge.leftSubstate, sentence.get(childEdge.startIndex))));
				}
				feats.add(featureIndex.getIndex(new ThreeAncestorWord(parentState, -1, childEdge.parentState, -1, childEdge.leftState, -1, sentence.get(childEdge.startIndex))));

			}

			if(baseModel.isPosTag(childEdge.rightState)) {
				if(!useOnlyStateFeatures) {
					feats.add(featureIndex.getIndex(new ThreeAncestorWord(parentState, parentSubstate, childEdge.parentState, childEdge.parentSubstate, childEdge.rightState, childEdge.rightSubstate, sentence.get(childEdge.splitIndex))));		
				}
				feats.add(featureIndex.getIndex(new ThreeAncestorWord(parentState, -1, childEdge.parentState, -1, childEdge.rightState, -1, sentence.get(childEdge.splitIndex))));		
			}
		}
	}


	/**
	 * Computes bigram non-local N-gram tree features.  These are features containing the rightmost preterminal and word
	 * of the left child and the leftmost preterminal and word of the right child.
	 * This feature has no unary edge counterpart.
	 * Returns empty list if no such non-local feature (features of this kind may also be local)
	 * 
	 * Also computes non lexicalized versions of these rules - this seems to be done in Charniak and Johnson,
	 * although unclear if Liang did it.
	 * @param forest
	 * @param rootEdgeIndex
	 * @param isUnaryEdgeBacktrace
	 * @param edgeIndexBacktrace
	 * @param childKBacktrace
	 * @param childK
	 * @return
	 */
	public void computeBinaryBigramTreeNonLocalFeatures(PrunedForest forest, int rootEdgeIndex, boolean[][] isUnaryEdgeBacktrace,
			int[][] edgeIndexBacktrace, int[][][] childKBacktrace, int leftChildK, int rightChildK, List<String> sentence, List<Integer> feats) {
		BinaryEdge curEdge = forest.getBinaryEdges()[rootEdgeIndex];
		//first, get the right and left child nodes
		int leftChildNodeIndex = forest.getLeftChildNodeIndex(rootEdgeIndex);
		int rightChildNodeIndex = forest.getRightChildNodeIndex(rootEdgeIndex);

		//then check: if both the left and right child node are preterminals, this was computed as a local feature
		if(baseModel.isPosTag(forest.getNodes()[leftChildNodeIndex].state) && baseModel.isPosTag(forest.getNodes()[rightChildNodeIndex].state)) {
			return;
		}

		//this is a non-local feature- compute!
		Node leftRightMostChild = getRightMostPreterminal(leftChildNodeIndex, leftChildK, forest, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace);
		Node rightLeftMostChild = getLeftMostPreterminal(rightChildNodeIndex, rightChildK, forest, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace);
		if(!useOnlyStateFeatures) {
			feats.add(featureIndex.getIndex(new BigramTreeFeature(curEdge.parentState, curEdge.parentSubstate, 
					curEdge.leftState, curEdge.leftSubstate, 
					curEdge.rightState, curEdge.rightSubstate, 
					leftRightMostChild.state, leftRightMostChild.substate, 
					rightLeftMostChild.state, rightLeftMostChild.substate,
					sentence.get(leftRightMostChild.startIndex), sentence.get(rightLeftMostChild.startIndex))));
			feats.add(featureIndex.getIndex(new BigramTreeFeature(curEdge.parentState, curEdge.parentSubstate, 
					curEdge.leftState, curEdge.leftSubstate, 
					curEdge.rightState, curEdge.rightSubstate, 
					leftRightMostChild.state, leftRightMostChild.substate, 
					rightLeftMostChild.state, rightLeftMostChild.substate,
					"", "")));//non lexicalized form
		}
		feats.add(featureIndex.getIndex(new BigramTreeFeature(curEdge.parentState, -1, 
				curEdge.leftState, -1, 
				curEdge.rightState, -1, 
				leftRightMostChild.state, -1, 
				rightLeftMostChild.state, -1,
				sentence.get(leftRightMostChild.startIndex), sentence.get(rightLeftMostChild.startIndex))));
		feats.add(featureIndex.getIndex(new BigramTreeFeature(curEdge.parentState, -1, 
				curEdge.leftState, -1, 
				curEdge.rightState, -1, 
				leftRightMostChild.state, -1, 
				rightLeftMostChild.state, -1,
				"", "")));//non lexicalized form

	}


	/**
	 * Computes features with the rule and the parent of the parent node; this should be unnecessary if
	 * trees have vertical Markovization.
	 * 
	 * backtraces are indexed by nodes, then by k
	 * int rootEdgeIndex root edge of the subtree for which we're currently computing features
	 * boolean[][] isUnaryEdgeBacktrace first indexed by node index (get from forest), then by derivation (use childK)
	 * int[][] edgeIndexBacktrace index of the edge from (first index) node and (second index) derivation
	 * int[][][] childKBacktrace get which derivation we want for the grandchild (can recurse on this to go farther down); indexed by node, derivation, and child (L/R)
	 * @param forest
	 * @param rootEdgeIndex
	 * @param isUnaryEdgeBacktrace
	 * @param edgeIndexBacktrace
	 * @param childKBacktrace
	 * @param childK
	 * @return
	 */
	public void computeUnaryParentRuleFeatures(PrunedForest forest, int rootEdgeIndex, boolean[][] isUnaryEdgeBacktrace,
			int[][] edgeIndexBacktrace, int[][][] childKBacktrace, int childK, List<Integer> feats) {
		UnaryEdge curEdge = forest.getUnaryEdges()[rootEdgeIndex];
		//get the child
		int childNodeIndex = forest.getUnaryChildNodeIndex(rootEdgeIndex);
		if(!baseModel.isPosTag(forest.getNodes()[childNodeIndex].state))
			makeParentRuleFeature(curEdge.parentState, curEdge.parentSubstate, childNodeIndex, forest, isUnaryEdgeBacktrace, edgeIndexBacktrace, childK, feats);
	}


	public void computeBinaryParentRuleFeatures(PrunedForest forest, int rootEdgeIndex, boolean[][] isUnaryEdgeBacktrace,
			int[][] edgeIndexBacktrace, int[][][] childKBacktrace, int leftChildK, int rightChildK, List<Integer> feats) {
		BinaryEdge curEdge = forest.getBinaryEdges()[rootEdgeIndex];
		//get the children
		int leftChildNodeIndex = forest.getLeftChildNodeIndex(rootEdgeIndex);
		int rightChildNodeIndex = forest.getRightChildNodeIndex(rootEdgeIndex);
		if(!baseModel.isPosTag(forest.getNodes()[leftChildNodeIndex].state))
			makeParentRuleFeature(curEdge.parentState, curEdge.parentSubstate, leftChildNodeIndex, forest, isUnaryEdgeBacktrace, edgeIndexBacktrace, leftChildK, feats);
		if(!baseModel.isPosTag(forest.getNodes()[rightChildNodeIndex].state))
			makeParentRuleFeature(curEdge.parentState, curEdge.parentSubstate, rightChildNodeIndex, forest, isUnaryEdgeBacktrace, edgeIndexBacktrace, rightChildK, feats);
	}

	private void makeParentRuleFeature(int grandParentState, int grandParentSubstate, int childNodeIndex, PrunedForest forest, boolean[][] isUnaryEdgeBacktrace, int[][] edgeIndexBacktrace, int childK, List<Integer> feats) {
		int childEdgeIndex = edgeIndexBacktrace[childNodeIndex][childK];
		//see if the child is unary
		if(isUnaryEdgeBacktrace[childNodeIndex][childK]) {
			UnaryEdge childEdge = forest.getUnaryEdges()[childEdgeIndex];
			//child is unary - with unaries, we store right child as -1 for rules
			if(!useOnlyStateFeatures) {
				feats.add(featureIndex.getIndex(new ParentRuleFeature(grandParentState, grandParentSubstate,childEdge.parentState, childEdge.parentSubstate, childEdge.childState, childEdge.childSubstate, -1, -1)));
			}
			feats.add(featureIndex.getIndex(new ParentRuleFeature(grandParentState, -1,childEdge.parentState, -1, childEdge.childState, -1, -1, -1)));
		} else {
			BinaryEdge childEdge = forest.getBinaryEdges()[childEdgeIndex];
			if(!useOnlyStateFeatures) {
				feats.add(featureIndex.getIndex(new ParentRuleFeature(grandParentState, grandParentSubstate,childEdge.parentState, childEdge.parentSubstate, childEdge.leftState, childEdge.leftSubstate, childEdge.rightState, childEdge.rightSubstate)));
			}
			feats.add(featureIndex.getIndex(new ParentRuleFeature(grandParentState, -1,childEdge.parentState, -1, childEdge.leftState, -1, childEdge.rightState, -1)));

		}
	}

	private Node getLeftMostPreterminal(int node, int k, PrunedForest prunedForest,
			boolean[][] isUnaryEdgeBacktrace, int[][] edgeIndexBacktrace, int[][][] childKBacktrace) {
		return getOuterMostPreterminal(node,k, prunedForest, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, true);
	}

	private Node getRightMostPreterminal(int node, int k, PrunedForest prunedForest,
			boolean[][] isUnaryEdgeBacktrace, int[][] edgeIndexBacktrace, int[][][] childKBacktrace) {
		return getOuterMostPreterminal(node,k, prunedForest, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, false);
	}

	private Node getOuterMostPreterminal(int node, int k, PrunedForest prunedForest,
			boolean[][] isUnaryEdgeBacktrace, int[][] edgeIndexBacktrace, int[][][] childKBacktrace, boolean getLeft) {
		Node label = prunedForest.getNodes()[node];
		if (baseModel.isPosTag(label.state)) {
			return label;
		}
		if (isUnaryEdgeBacktrace[node][k]) {
			return getOuterMostPreterminal(prunedForest.getUnaryChildNodeIndex(edgeIndexBacktrace[node][k]),
					childKBacktrace[node][k][0], prunedForest,
					isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, getLeft);
		} else {
			if(getLeft)
				return getOuterMostPreterminal(prunedForest.getLeftChildNodeIndex(edgeIndexBacktrace[node][k]),
						childKBacktrace[node][k][0], prunedForest,
						isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, getLeft);
			else
				return getOuterMostPreterminal(prunedForest.getRightChildNodeIndex(edgeIndexBacktrace[node][k]),
						childKBacktrace[node][k][1], prunedForest,
						isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, getLeft);
		}
	}
	
	private void computeBinaryAntecedentAnaphorFeatures(PrunedForest forest, int rootEdgeIndex, boolean[][] isUnaryEdgeBacktrace,
			int[][] edgeIndexBacktrace, int[][][] childKBacktrace, int leftChildK, int rightChildK, List<Integer> feats) {
		int leftNode = forest.getLeftChildNodeIndex(rootEdgeIndex);
		int rightNode = forest.getRightChildNodeIndex(rootEdgeIndex);
		int parentState = forest.getBinaryEdges()[rootEdgeIndex].parentState;
		int leftState = forest.getNodes()[leftNode].state;
		int rightState = forest.getNodes()[rightNode].state;
		if (baseModel.isAntecedentState(parentState)) {
			findAnaphors(forest, leftNode, leftChildK, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, parentState, feats);
			findAnaphors(forest, rightNode, rightChildK, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, parentState, feats);
		}
		if (baseModel.isAntecedentState(leftState)) {
			findAnaphors(forest, rightNode, rightChildK, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, leftState, feats);
		}
		if (baseModel.isAntecedentState(rightState)) {
			findAnaphors(forest, leftNode, rightChildK, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, leftState, feats);
		}
	}

	private void computeUnaryAntecedentAnaphorFeatures(PrunedForest forest, int rootEdgeIndex, boolean[][] isUnaryEdgeBacktrace,
			int[][] edgeIndexBacktrace, int[][][] childKBacktrace, int childK, List<Integer> feats) {
		int childNode = forest.getUnaryChildNodeIndex(rootEdgeIndex);
		int parentState = forest.getUnaryEdges()[rootEdgeIndex].parentState;
		if (baseModel.isAntecedentState(parentState)) {
			findAnaphors(forest, childNode, childK, isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, parentState, feats);
		}
	}

	private void findAnaphors(PrunedForest forest, int nodeIndex, int k,
			boolean[][] isUnaryEdgeBacktrace, int[][] edgeIndexBacktrace,
			int[][][] childKBacktrace, int antecedentState, List<Integer> feats) {
		Node node = forest.getNodes()[nodeIndex];
		if (baseModel.isPosTag(node.state)) {
			return;
		}
		if (baseModel.couldBeAntecedent(antecedentState, node.state)) {
			feats.add(featureIndex.getIndex(new AnaphorAntecedentFeature(baseModel.getMatchedState(antecedentState, node.state),
					antecedentState, node.state)));
		}
		if (isUnaryEdgeBacktrace[nodeIndex][k]) {
			findAnaphors(forest, forest.getUnaryChildNodeIndex(edgeIndexBacktrace[nodeIndex][k]), childKBacktrace[nodeIndex][k][0],
					isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, antecedentState, feats);
		} else {
			findAnaphors(forest, forest.getLeftChildNodeIndex(edgeIndexBacktrace[nodeIndex][k]), childKBacktrace[nodeIndex][k][0],
					isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, antecedentState, feats);
			findAnaphors(forest, forest.getRightChildNodeIndex(edgeIndexBacktrace[nodeIndex][k]), childKBacktrace[nodeIndex][k][1],
					isUnaryEdgeBacktrace, edgeIndexBacktrace, childKBacktrace, antecedentState, feats);
		}
	}


}
