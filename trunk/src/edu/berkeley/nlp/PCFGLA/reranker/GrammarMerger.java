/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import java.util.ArrayList;
import java.util.List;

import edu.berkeley.nlp.PCFGLA.BinaryRule;
import edu.berkeley.nlp.PCFGLA.Grammar;
import edu.berkeley.nlp.PCFGLA.UnaryRule;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.Numberer;

/**
 * @author dburkett
 *
 */
public class GrammarMerger {
	@SuppressWarnings("unchecked")
	public Grammar mergeGrammars(Grammar baseGrammar, Grammar scoreGrammar) {
		Grammar mergedGrammar = baseGrammar.copyGrammar(false);
		Numberer baseNumberer = mergedGrammar.getTagNumberer();
		Numberer scoreNumberer = scoreGrammar.getTagNumberer();
		mergedGrammar.numSubStates = new short[mergedGrammar.numStates];
		mergedGrammar.splitTrees = new Tree[mergedGrammar.numStates];
		for (int s=0; s<mergedGrammar.numStates; s++) {
			short translatedState = translateState(s, baseNumberer, scoreNumberer);
			if (translatedState >= 0) {
				mergedGrammar.numSubStates[s] = scoreGrammar.numSubStates[translatedState];
				mergedGrammar.splitTrees[s] = scoreGrammar.splitTrees[translatedState];
			} else {
				mergedGrammar.numSubStates[s] = 1;
				mergedGrammar.splitTrees[s] = buildDefaultSplitTree(scoreGrammar.splitTrees[0].getDepth());
			}
		}
		for (BinaryRule br : mergedGrammar.binaryRuleMap.values()) {
			short translatedParent = translateState(br.getParentState(), baseNumberer, scoreNumberer);
			short translatedLeftChild = translateState(br.getLeftChildState(), baseNumberer, scoreNumberer);
			short translatedRightChild = translateState(br.getRightChildState(), baseNumberer, scoreNumberer);
			BinaryRule scoreRule = scoreGrammar.getBinaryRule(translatedParent, translatedLeftChild, translatedRightChild);
			if (scoreRule != null) {
				br.setScores2(scoreRule.getScores2());
			} else {
				br.setScores2(new double[mergedGrammar.numSubStates[br.getLeftChildState()]][mergedGrammar.numSubStates[br.getRightChildState()]][]);
			}
		}
		for (UnaryRule ur : mergedGrammar.unaryRuleMap.values()) {
			short translatedParent = translateState(ur.getParentState(), baseNumberer, scoreNumberer);
			short translatedChild = translateState(ur.getChildState(), baseNumberer, scoreNumberer);
			UnaryRule scoreRule = scoreGrammar.getUnaryRule(translatedParent, translatedChild);
			if (scoreRule != null) {
				ur.setScores2(scoreRule.getScores2());
			} else {
				ur.setScores2(new double[mergedGrammar.numSubStates[ur.getChildState()]][]);
			}
		}
		for (int s=0; s<mergedGrammar.numStates; s++) {
			UnaryRule[] rules = mergedGrammar.getClosedSumUnaryRulesByParent(s);
			for (UnaryRule ur : rules) {
				short translatedParent = translateState(ur.getParentState(), baseNumberer, scoreNumberer);
				short translatedChild = translateState(ur.getChildState(), baseNumberer, scoreNumberer);
				UnaryRule scoreRule = null;
				if (translatedParent >= 0) {
					for (UnaryRule cand : scoreGrammar.getClosedSumUnaryRulesByParent(translatedParent)) {
						if (cand.childState == translatedChild) scoreRule = cand;
					}
				}
				if (scoreRule != null) {
					ur.setScores2(scoreRule.getScores2());
				} else {
					ur.setScores2(new double[mergedGrammar.numSubStates[ur.getChildState()]][]);
				}
			}
		}
		return mergedGrammar;
	}

	private Tree<Short> buildDefaultSplitTree(int depth) {
		if (depth <= 1) {
			return new Tree<Short>((short)0);
		} else {
			List<Tree<Short>> child = new ArrayList<Tree<Short>>();
			child.add(buildDefaultSplitTree(depth-1));
			return new Tree<Short>((short)0, child);
		}
	}

	private short translateState(int state, Numberer baseNumberer, Numberer translationNumberer) {
		Object object = baseNumberer.object(state);
		if (translationNumberer.hasSeen(object)) {
			return (short)translationNumberer.number(object);
		} else {
			return (short)-1;
		}
	}

}
