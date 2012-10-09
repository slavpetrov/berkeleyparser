/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees.PennTreeRenderer;
import edu.berkeley.nlp.util.Pair;

/**
 * @author petrov
 * 
 */
public class HierarchicalAdaptiveLexicalRule implements Serializable {
	private static final long serialVersionUID = 1L;

	double[] scores;
	public short[] mapping;
	Tree<Double> hierarchy;
	public int nParam;
	public int identifier;

	// HierarchicalAdaptiveLexicalRule(short t, int w){
	// this.tag = t;
	// this.wordIndex = w;
	// }

	HierarchicalAdaptiveLexicalRule() {
		hierarchy = new Tree<Double>(0.0);
		scores = new double[1];
		mapping = new short[1];
		nParam = 1;
	}

	public Pair<Integer, Integer> countParameters() {
		// first one is the max_depth, second one is the number of parameters
		int maxDepth = hierarchy.getDepth();
		nParam = hierarchy.getYield().size();
		return new Pair<Integer, Integer>(maxDepth, nParam);
	}

	public void splitRule(int nSubstates) {
		splitRuleHelper(hierarchy, 2);
		mapping = new short[nSubstates];
		int finalLevel = (int) (Math.log(mapping.length) / Math.log(2));
		updateMapping((short) 0, 0, 0, finalLevel, hierarchy);
		// mapping[0] = (short)0;
		// mapping[1] = (short)1;
	}

	private Pair<Short, Integer> updateMapping(short myID, int nextSubstate,
			int myDepth, int finalDepth, Tree<Double> tree) {
		if (tree.isLeaf()) {
			if (myDepth == finalDepth) {
				mapping[nextSubstate++] = myID;
			} else {
				int substatesToCover = (int) Math.pow(2, finalDepth - myDepth);
				for (int i = 0; i < substatesToCover; i++) {
					mapping[nextSubstate++] = myID;
				}
			}
			myID++;
		} else {
			for (Tree<Double> child : tree.getChildren()) {
				Pair<Short, Integer> tmp = updateMapping(myID, nextSubstate,
						myDepth + 1, finalDepth, child);
				myID = tmp.getFirst();
				nextSubstate = tmp.getSecond();
			}
		}
		return new Pair<Short, Integer>(myID, nextSubstate);
	}

	private void splitRuleHelper(Tree<Double> tree, int splitFactor) {
		if (tree.isLeaf()) {
			if (tree.getLabel() != 0 || nParam == 1) { // split it
				ArrayList<Tree<Double>> children = new ArrayList<Tree<Double>>(
						splitFactor);
				for (int i = 0; i < splitFactor; i++) {
					Tree<Double> child = new Tree<Double>(
							(GrammarTrainer.RANDOM.nextDouble() - .5) / 100.0);
					children.add(child);
				}
				tree.setChildren(children);
				nParam += splitFactor - 1;
				// } else { //perturb it
				// tree.setLabel(GrammarTrainer.RANDOM.nextDouble()/100.0);
			}
		} else {
			for (Tree<Double> child : tree.getChildren()) {
				splitRuleHelper(child, splitFactor);
			}
		}
	}

	public void explicitlyComputeScores(int finalLevel,
			final boolean usingOnlyLastLevel) {
		int nSubstates = (int) Math.pow(2, finalLevel);
		scores = new double[nSubstates];
		int nextSubstate = fillScores(0, 0, 0, finalLevel, hierarchy,
				usingOnlyLastLevel);
		if (nextSubstate != nSubstates)
			System.out.println("Didn't fill all lexical scores!");
		mapping = new short[nSubstates];
		updateMapping((short) 0, 0, 0, finalLevel, hierarchy);
	}

	private int fillScores(double previousScore, int nextSubstate, int myDepth,
			int finalDepth, Tree<Double> tree, final boolean usingOnlyLastLevel) {
		if (tree.isLeaf()) {
			double myScore = (usingOnlyLastLevel) ? Math.exp(tree.getLabel())
					: Math.exp(previousScore + tree.getLabel());
			if (myDepth == finalDepth) {
				scores[nextSubstate++] = myScore;
			} else {
				int substatesToCover = (int) Math.pow(2, finalDepth - myDepth);
				for (int i = 0; i < substatesToCover; i++) {
					scores[nextSubstate++] = myScore;
				}
			}
		} else {
			double myScore = previousScore + tree.getLabel();
			for (Tree<Double> child : tree.getChildren()) {
				nextSubstate = fillScores(myScore, nextSubstate, myDepth + 1,
						finalDepth, child, usingOnlyLastLevel);
			}
		}
		return nextSubstate;
	}

	public void updateScores(double[] scores) {
		int nSubstates = updateHierarchy(hierarchy, 0, scores);
		if (nSubstates != nParam)
			System.out.println("Didn't update all parameters");
	}

	private int updateHierarchy(Tree<Double> tree, int nextSubstate,
			double[] scores) {
		if (tree.isLeaf()) {
			double val = scores[identifier + nextSubstate++];
			if (val > 200) {
				System.out
						.println("Ignored proposed lexical value since it was danegrous");
				val = 0;
			} else
				tree.setLabel(val);
		} else {
			for (Tree<Double> child : tree.getChildren()) {
				nextSubstate = updateHierarchy(child, nextSubstate, scores);
			}
		}
		return nextSubstate;
	}

	/**
	 * @return
	 */
	public List<Double> getFinalLevel() {
		return hierarchy.getYield();
	}

	private void compactifyHierarchy(Tree<Double> tree) {
		if (tree.getDepth() == 2) {
			boolean allZero = true;
			for (Tree<Double> child : tree.getChildren()) {
				allZero = allZero && (child.getLabel() == 0.0);
			}
			if (allZero) {
				nParam -= tree.getChildren().size() - 1;
				tree.setChildren(Collections.EMPTY_LIST);
			}
		} else {
			for (Tree<Double> child : tree.getChildren()) {
				compactifyHierarchy(child);
			}
		}
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		compactifyHierarchy(hierarchy);
		sb.append(Arrays.toString(scores));
		sb.append("\n");
		sb.append(PennTreeRenderer.render(hierarchy));
		sb.append("\n");
		return sb.toString();
	}

	public int mergeRule() {
		int paramBefore = nParam;
		compactifyHierarchy(hierarchy);
		scores = null;
		mapping = null;
		return paramBefore - nParam;
	}

	public int countNonZeroFeatures() {
		int total = 0;
		for (Tree<Double> d : hierarchy.getPreOrderTraversal()) {
			if (d.getLabel() != 0)
				total++;
		}
		return total;
	}

	public int countNonZeroFringeFeatures() {
		int total = 0;
		for (Tree<Double> d : hierarchy.getTerminals()) {
			if (d.getLabel() != 0)
				total++;
		}
		return total;
	}

}
