/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import java.util.ArrayList;
import java.util.List;

import edu.berkeley.nlp.PCFGLA.reranker.FeatureExtractorManager.Feature;
import edu.berkeley.nlp.PCFGLA.reranker.RerankingFeatures.BigramTreeFeature;
import edu.berkeley.nlp.PCFGLA.reranker.RerankingFeatures.CoarseRuleFeature;
import edu.berkeley.nlp.PCFGLA.reranker.RerankingFeatures.HeavyFeature;
import edu.berkeley.nlp.PCFGLA.reranker.RerankingFeatures.RuleFeature;
import edu.berkeley.nlp.PCFGLA.reranker.RerankingFeatures.ThreeAncestorWord;
import edu.berkeley.nlp.PCFGLA.reranker.RerankingFeatures.WordEdge;
import fig.basic.Indexer;



/**
 * Class for extracting local features - features that can be defined by only the local subtree and the sentence.
 * 
 * @author rafferty
 *
 */
public class LocalFeatureExtractors implements LocalFeatureExtractor {
	private final Indexer<Feature> featureIndex;
	private final BaseModel baseModel;
	private final boolean useOnlyStateFeatures;
	private final boolean useFullParsingFeatures;


	public LocalFeatureExtractors(Indexer<Feature> featureIndex, BaseModel baseModel) {
		this(featureIndex, baseModel, true);
	}

	/**
	 * Make a new LocalFeatureExtractors object which stores its features in the 
	 * given index. This index should be shared between all feature extractors.
	 * @param featureIndex
	 */
	public LocalFeatureExtractors(Indexer<Feature> featureIndex, BaseModel baseModel, boolean useOnlyStateFeatures) {
		this.featureIndex = featureIndex;
		this.baseModel = baseModel;
		this.useOnlyStateFeatures = useOnlyStateFeatures;
		this.useFullParsingFeatures = false;
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.PCFGLA.reranker.LocalFeatureExtractor#precomputeLocalIndicatorFeatures(edu.berkeley.nlp.PCFGLA.reranker.BinaryEdge[], java.util.List)
	 */
	public int[][] precomputeLocalIndicatorFeatures(BinaryEdge[] binaryEdges, List<String> sentence) {
		int[][] localFeatures = new int[binaryEdges.length][];
		for(int i=0; i < binaryEdges.length; i++) {
			List<Integer> feats = new ArrayList<Integer>();
			computeRuleFeatures(binaryEdges[i],feats);
			computeWordEdgesFeatures(binaryEdges[i], sentence,feats);
			computeHeavyFeatures(binaryEdges[i], sentence,feats);
			computeBigramTreeLocalFeatures(binaryEdges[i], sentence,feats);
			computeWord2AncestorFeatures(binaryEdges[i], sentence,feats);
			if (useFullParsingFeatures) {
				computeCoarseRuleFeatures(binaryEdges[i], feats);
				computeEmptyNodeFeatures(binaryEdges[i].parentState, feats);
			}
			// now turn our feature list into an array
			int[] featureArray = toPrimitive(feats);
			localFeatures[i] = featureArray;
		}
		return localFeatures;
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.PCFGLA.reranker.LocalFeatureExtractor#precomputeLocalIndicatorFeatures(edu.berkeley.nlp.PCFGLA.reranker.UnaryEdge[], java.util.List)
	 */
	public int[][] precomputeLocalIndicatorFeatures(UnaryEdge[] unaryEdges, List<String> sentence) {
		int[][] localFeatures = new int[unaryEdges.length][];
		for(int i=0; i < unaryEdges.length; i++) {
			List<Integer> feats = new ArrayList<Integer>();
			computeRuleFeatures(unaryEdges[i], feats);
			computeWordEdgesFeatures(unaryEdges[i], sentence, feats);
			computeHeavyFeatures(unaryEdges[i], sentence, feats);
			computeWord2AncestorFeatures(unaryEdges[i], sentence,feats);
			if (useFullParsingFeatures) {
				computeCoarseRuleFeatures(unaryEdges[i], feats);
				computeEmptyNodeFeatures(unaryEdges[i].parentState, feats);
				Node intermediateNode = baseModel.getIntermediateCoarseNode(unaryEdges[i]);
				if (intermediateNode != null) {
					computeEmptyNodeFeatures(intermediateNode.state, feats);
				}
			}

			// now turn our feature list into an array
			int[] featureArray = toPrimitive(feats);
			localFeatures[i] = featureArray;
		}
		return localFeatures;
	}





	/**
	 * Compute words, their preterminal labels, and their parent labels
	 * @param binaryEdge
	 * @param sentence
	 * @return
	 */
	public void computeWord2AncestorFeatures(BinaryEdge binaryEdge, List<String> sentence, List<Integer> feats) {
		if(baseModel.isPosTag(binaryEdge.leftState)) {
			if(!useOnlyStateFeatures) {
				feats.add(featureIndex.getIndex(new ThreeAncestorWord(binaryEdge.parentState, binaryEdge.parentSubstate, binaryEdge.leftState, binaryEdge.leftSubstate,sentence.get(binaryEdge.startIndex))));
			}
			feats.add(featureIndex.getIndex(new ThreeAncestorWord(binaryEdge.parentState, -1, binaryEdge.leftState, -1,sentence.get(binaryEdge.startIndex))));

		} 
		if(baseModel.isPosTag(binaryEdge.rightState)) {
			if(!useOnlyStateFeatures) {
				feats.add(featureIndex.getIndex(new ThreeAncestorWord(binaryEdge.parentState, binaryEdge.parentSubstate, binaryEdge.rightState, binaryEdge.rightSubstate,sentence.get(binaryEdge.splitIndex))));
			}
			feats.add(featureIndex.getIndex(new ThreeAncestorWord(binaryEdge.parentState, -1, binaryEdge.rightState, -1,sentence.get(binaryEdge.splitIndex))));

		}
	}

	public void computeWord2AncestorFeatures(UnaryEdge unaryEdge, List<String> sentence, List<Integer> feats) {
		if(baseModel.isPosTag(unaryEdge.childState)) {
			if(!useOnlyStateFeatures) {
				feats.add(featureIndex.getIndex(new ThreeAncestorWord(unaryEdge.parentState, unaryEdge.parentSubstate, unaryEdge.childState, unaryEdge.childSubstate,sentence.get(unaryEdge.startIndex))));
			}
			feats.add(featureIndex.getIndex(new ThreeAncestorWord(unaryEdge.parentState, -1, unaryEdge.childState, -1,sentence.get(unaryEdge.startIndex))));
		} 
	}


	/**
	 * Computes bigram tree local features.  These are features where the children in the binary
	 * edge are preterminals; other bigram tree features are computed as nonlocal features.
	 * 
	 * -1 is returned if there is not a bigram tree local feature for this edge.
	 * 
	 * Computes non-lexicalized form, although it's unclear if this will be helpful since all that
	 * info is already contained in the rule.
	 * @param binaryEdge
	 * @param sentence
	 * @return
	 */
	public void computeBigramTreeLocalFeatures(BinaryEdge binaryEdge, List<String> sentence, List<Integer> feats) {
		Node leftChild = binaryEdge.getLeftChild();
		Node rightChild = binaryEdge.getRightChild();
		if(baseModel.isPosTag(leftChild.state) && baseModel.isPosTag(rightChild.state)) {
			if(!useOnlyStateFeatures) {
				feats.add(featureIndex.getIndex(new BigramTreeFeature(binaryEdge.parentState, binaryEdge.parentSubstate,
						binaryEdge.leftState, binaryEdge.leftSubstate,
						binaryEdge.rightState, binaryEdge.rightSubstate,
						binaryEdge.leftState, binaryEdge.leftSubstate,
						binaryEdge.rightState, binaryEdge.rightSubstate,
						sentence.get(leftChild.startIndex), sentence.get(rightChild.startIndex))));
				feats.add(featureIndex.getIndex(new BigramTreeFeature(binaryEdge.parentState, binaryEdge.parentSubstate,
						binaryEdge.leftState, binaryEdge.leftSubstate,
						binaryEdge.rightState, binaryEdge.rightSubstate,
						binaryEdge.leftState, binaryEdge.leftSubstate,
						binaryEdge.rightState, binaryEdge.rightSubstate,
						"", "")));//non-lexicalized form
			}
			feats.add(featureIndex.getIndex(new BigramTreeFeature(binaryEdge.parentState, -1,
					binaryEdge.leftState, -1,
					binaryEdge.rightState, -1,
					binaryEdge.leftState, -1,
					binaryEdge.rightState, -1,
					sentence.get(leftChild.startIndex), sentence.get(rightChild.startIndex))));
			feats.add(featureIndex.getIndex(new BigramTreeFeature(binaryEdge.parentState, -1,
					binaryEdge.leftState, -1,
					binaryEdge.rightState, -1,
					binaryEdge.leftState, -1,
					binaryEdge.rightState, -1,
					"", "")));//non-lexicalized form
		}
	}


	/**
	 * Compute "heavy" features (Charniak and Johnson):
	 * classify nodes by category, binned length, whether they're at the end of the sentence, 
	 * and whether they're followed by punctuation
	 * @param binaryEdge
	 * @param sentence
	 * @return
	 */
	public void computeHeavyFeatures(BinaryEdge binaryEdge, List<String> sentence, List<Integer> feats) {
		boolean endSentence = false;
		boolean punctFollows = false;

		if(sentence.size() == binaryEdge.stopIndex) {
			endSentence = true;
		} else {
			if(isPunctuation(sentence.get(binaryEdge.stopIndex)))
				punctFollows = true;
		}
		if(!useOnlyStateFeatures) {
			feats.add(featureIndex.getIndex(new HeavyFeature(binaryEdge.parentState, 
					binaryEdge.parentSubstate, 
					getBinnedLength(binaryEdge.stopIndex- binaryEdge.startIndex),
					endSentence,
					punctFollows)));
		}
		feats.add(featureIndex.getIndex(new HeavyFeature(binaryEdge.parentState, 
				-1, 
				getBinnedLength(binaryEdge.stopIndex- binaryEdge.startIndex),
				endSentence,
				punctFollows)));
	}

	public void computeHeavyFeatures(UnaryEdge unaryEdge, List<String> sentence, List<Integer> feats) {
		boolean endSentence = false;
		boolean punctFollows = false;

		if(sentence.size() == unaryEdge.stopIndex) {
			endSentence = true;
		} else {
			if(isPunctuation(sentence.get(unaryEdge.stopIndex)))
				punctFollows = true;
		}
		if(!useOnlyStateFeatures) {
			feats.add(featureIndex.getIndex(new HeavyFeature(unaryEdge.parentState, 
					unaryEdge.parentSubstate, 
					getBinnedLength(unaryEdge.stopIndex- unaryEdge.startIndex),
					endSentence,
					punctFollows)));
		}
		feats.add(featureIndex.getIndex(new HeavyFeature(unaryEdge.parentState, 
				-1, 
				getBinnedLength(unaryEdge.stopIndex- unaryEdge.startIndex),
				endSentence,
				punctFollows)));
	}



	/**
	 * Returns the standardized binned length for the provided raw length. Length should be at least 1.
	 * @param length
	 * @return
	 */
	private int getBinnedLength(int length) {
		if(length == 1)
			return 1;
		else if(length == 2)
			return 2;
		else if(length > 2 && length < 5)
			return 3;
		else if(length >= 5 && length < 10)
			return 5;
		else if(length >= 10)
			return 10;
		return 0;
	}

	/**
	 * Returns whether the word is punctuation - currently a bit rough.
	 * @param word
	 * @return
	 */
	private boolean isPunctuation(String word) {
		if(word.length() == 1) {
			if(word.contains(",") || 
					word.contains(".") ||
					word.contains(":") ||
					word.contains(";") ||
					word.contains("("))
				return true;
		}
		return false;
	}

	/**
	 * Compute conjunction features as described in Charniak and Johnson (2005)
	 * @param binaryEdge
	 * @param sentence
	 * @return
	 */
	public void computeConjunctionFeatures(BinaryEdge binaryEdge, List<String> sentence, List<Integer> feats) {

	}

	/**
	 * Features of the form (parentState/Substate numWords leftWord rightWord) where
	 * leftWord and rightWord are the first words to the left and right that are not
	 * in the span of this subtree, and numWords is the number of words spanned by this
	 * subtree
	 * @param binaryEdge
	 * @param sentence
	 * @return list of feature indices that are on for this edge
	 */
	public void computeWordEdgesFeatures(BinaryEdge binaryEdge, List<String> sentence, List<Integer> feats) {
		int numWords = binaryEdge.stopIndex - binaryEdge.startIndex;
		String leftWord = ((binaryEdge.startIndex == 0) ? null : sentence.get(binaryEdge.startIndex-1));
		String rightWord = ((binaryEdge.stopIndex == sentence.size()) ? null : sentence.get(binaryEdge.stopIndex));
		if(!useOnlyStateFeatures) {
			feats.add(featureIndex.getIndex(new WordEdge(binaryEdge.parentState, binaryEdge.parentSubstate, leftWord, rightWord, numWords)));
		}
		feats.add(featureIndex.getIndex(new WordEdge(binaryEdge.parentState, -1, leftWord, rightWord, numWords)));
	}

	public void computeWordEdgesFeatures(UnaryEdge unaryEdge, List<String> sentence, List<Integer> feats) {
		int numWords = unaryEdge.stopIndex - unaryEdge.startIndex;
		String leftWord = ((unaryEdge.startIndex == 0) ? null : sentence.get(unaryEdge.startIndex-1));
		String rightWord = ((unaryEdge.stopIndex == sentence.size()) ? null : sentence.get(unaryEdge.stopIndex));
		if(!useOnlyStateFeatures) {
			feats.add(featureIndex.getIndex(new WordEdge(unaryEdge.parentState, unaryEdge.parentSubstate, leftWord, rightWord, numWords)));
		}
		feats.add(featureIndex.getIndex(new WordEdge(unaryEdge.parentState, -1, leftWord, rightWord, numWords)));
	}



	/**
	 * Computes features corresponding to the rule for this edge
	 * @param binaryEdge
	 * @return
	 */
	public void computeRuleFeatures(BinaryEdge binaryEdge, List<Integer> feats) {
		if(!useOnlyStateFeatures) {
			feats.add(featureIndex.getIndex(new RuleFeature(binaryEdge.parentState,
					binaryEdge.parentSubstate,
					binaryEdge.leftState,
					binaryEdge.leftSubstate,
					binaryEdge.rightState,
					binaryEdge.rightSubstate)));
		}
		feats.add(featureIndex.getIndex(new RuleFeature(binaryEdge.parentState,
				-1,
				binaryEdge.leftState,
				-1,
				binaryEdge.rightState,
				-1)));
	}

	/**
	 * Computes features corresponding to the rule for this edge. Uses same feature
	 * template as for binary.
	 * @param unaryEdge
	 * @return
	 */
	public void computeRuleFeatures(UnaryEdge unaryEdge, List<Integer> feats) {
		if(!useOnlyStateFeatures) {
			feats.add(featureIndex.getIndex(new RuleFeature(unaryEdge.parentState,
					unaryEdge.parentSubstate,
					unaryEdge.childState,
					unaryEdge.childSubstate,
					-1,
					-1)));
		}
		feats.add(featureIndex.getIndex(new RuleFeature(unaryEdge.parentState,
				-1,
				unaryEdge.childState,
				-1,
				-1,
				-1)));
	}
	
	public void computeCoarseRuleFeatures(BinaryEdge binaryEdge, List<Integer> feats) {
		feats.add(featureIndex.getIndex(new CoarseRuleFeature(
				baseModel.getBaseState(binaryEdge.parentState),
				baseModel.getBaseState(binaryEdge.leftState),
				baseModel.getBaseState(binaryEdge.rightState))));
	}

	public void computeCoarseRuleFeatures(UnaryEdge binaryEdge, List<Integer> feats) {
		feats.add(featureIndex.getIndex(new CoarseRuleFeature(
				baseModel.getBaseState(binaryEdge.parentState),
				baseModel.getBaseState(binaryEdge.childState),
				-1)));
	}
	
	public void computeEmptyNodeFeatures(int parentState, List<Integer> feats) {
		if (baseModel.hasEmptyChildren(parentState)) {
			int baseParent = baseModel.getBaseState(parentState);
			for (int emptyChild : baseModel.getEmptyChildList(parentState)) {
				feats.add(featureIndex.getIndex(new RerankingFeatures.EmptyNodeFeature(baseParent, emptyChild)));
			}
		}
	}

	private int[] toPrimitive(List<Integer> curList) {
		int[] newArray = new int[curList.size()];
		for(int i=0; i < newArray.length; i++) {
			newArray[i] = curList.get(i);
		}
		return newArray;
	}




}
