/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees.PennTreeRenderer;
import edu.berkeley.nlp.util.Numberer;
import edu.berkeley.nlp.util.Pair;

/**
 * @author petrov
 * 
 */
public class HierarchicalAdaptiveUnaryRule extends HierarchicalUnaryRule {
	private static final long serialVersionUID = 1L;

	public short[][] mapping;
	Tree<Double> hierarchy;
	public int nParam;
	public SubRule[] subRuleList;

	// assume for now that the rule being passed in is unsplit
	public HierarchicalAdaptiveUnaryRule(UnaryRule b) {
		super(b);
		hierarchy = new Tree<Double>(0.0);
		scores = new double[1][1];
		mapping = new short[1][1]; // to parameters
		nParam = 1;
	}

	public Pair<Integer, Integer> countParameters() {
		// first one is the max_depth, second one is the number of parameters
		int maxDepth = hierarchy.getDepth();
		nParam = hierarchy.getYield().size();
		return new Pair<Integer, Integer>(maxDepth, nParam);
	}

	public HierarchicalAdaptiveUnaryRule splitRule(short[] numSubStates,
			short[] newNumSubStates, Random random, double randomness,
			boolean doNotNormalize, int mode) {
		int splitFactor = 4;
		splitRuleHelper(hierarchy, random, splitFactor);
		// mapping = new
		// short[newNumSubStates[this.childState]][newNumSubStates[this.childState]];
		// int finalLevel = (int)(Math.log(mapping[0].length)/Math.log(2));
		// updateMapping((short)0, 0, 0, 0, finalLevel, hierarchy);
		return this;
	}

	public int mergeRule() {
		int paramBefore = nParam;
		compactifyHierarchy(hierarchy);
		scores = null;
		mapping = null;
		subRuleList = null;
		scoreHierarchy = null;
		return paramBefore - nParam;
	}

	// private short updateMapping(short myID, int nextChildSubstate, int
	// nextParentSubstate, int myDepth, int finalDepth, Tree<Double> tree) {
	// if (tree.isLeaf()){
	// if (myDepth==finalDepth){
	// mapping[nextChildSubstate][nextParentSubstate] = myID;
	// } else {
	// int substatesToCover = (int)Math.pow(2,finalDepth-myDepth);
	// nextChildSubstate *= substatesToCover;
	// nextParentSubstate *= substatesToCover;
	// for (int i=0; i<substatesToCover; i++){
	// for (int j=0; j<substatesToCover; j++){
	// mapping[nextChildSubstate+i][nextParentSubstate+j] = myID;
	// }
	// }
	// }
	// myID++;
	// } else {
	// int i = 0;
	// for (Tree<Double> child : tree.getChildren()){
	// myID = updateMapping(myID, nextChildSubstate*2 + (i/2),
	// nextParentSubstate*2 + (i%2), myDepth+1, finalDepth, child);
	// i++;
	// }
	// }
	// return myID;
	// }

	private void splitRuleHelper(Tree<Double> tree, Random random,
			int splitFactor) {
		if (tree.isLeaf()) {
			if (tree.getLabel() != 0 || nParam == 1) { // split it
				ArrayList<Tree<Double>> children = new ArrayList<Tree<Double>>(
						splitFactor);
				for (int i = 0; i < splitFactor; i++) {
					Tree<Double> child = new Tree<Double>(
							random.nextDouble() / 100.0);
					children.add(child);
				}
				tree.setChildren(children);
				nParam += splitFactor - 1;
				// } else { //perturb it
				// tree.setLabel(random.nextDouble()/100.0);
			}
		} else {
			for (Tree<Double> child : tree.getChildren()) {
				splitRuleHelper(child, random, splitFactor);
			}
		}
	}

	public void explicitlyComputeScores(int finalLevel, short[] newNumSubStates) {
		// int nSubstates = (int)Math.pow(2, finalLevel);
		// scores = new double[nSubstates][nSubstates];
		// int nextSubstate = fillScores((short)0, 0, 0, 0, 0, finalLevel,
		// hierarchy);
		// if (nextSubstate != nParam)
		// System.out.println("Didn't fill all scores!");
		computeSubRuleList();

	}

	// private short fillScores(short myID, double previousScore, int
	// nextChildSubstate, int nextParentSubstate, int myDepth, int finalDepth,
	// Tree<Double> tree){
	// if (tree.isLeaf()){
	// double myScore = Math.exp(previousScore + tree.getLabel());
	// if (myDepth==finalDepth){
	// scores[nextChildSubstate][nextParentSubstate] = myScore;
	// } else {
	// int substatesToCover = (int)Math.pow(2,finalDepth-myDepth);
	// nextChildSubstate *= substatesToCover;
	// nextParentSubstate *= substatesToCover;
	// for (int i=0; i<substatesToCover; i++){
	// for (int j=0; j<substatesToCover; j++){
	// scores[nextChildSubstate+i][nextParentSubstate+j] = myScore;
	// }
	// }
	// }
	// myID++;
	// } else {
	// double myScore = previousScore + tree.getLabel();
	// int i = 0;
	// for (Tree<Double> child : tree.getChildren()){
	// myID = fillScores(myID, myScore, nextChildSubstate*2 + (i/2),
	// nextParentSubstate*2 + (i%2), myDepth+1, finalDepth, child);
	// i++;
	// }
	// }
	// return myID;
	// }

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
				val = 0;
				System.out
						.println("Ignored proposed unary value since it was danegrous");
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
				allZero = allZero && child.getLabel() == 0;
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

	public String toStringShort() {
		Numberer n = Numberer.getGlobalNumberer("tags");
		String cState = (String) n.object(childState);
		String pState = (String) n.object(parentState);
		return (pState + " -> " + cState);
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		Numberer n = Numberer.getGlobalNumberer("tags");
		String cState = (String) n.object(childState);
		String pState = (String) n.object(parentState);
		sb.append(pState + " -> " + cState + "\n");
		if (subRuleList == null) {
			compactifyHierarchy(hierarchy);
			lastLevel = hierarchy.getDepth();
			computeSubRuleList();
		}

		for (SubRule rule : subRuleList) {
			if (rule == null)
				continue;
			sb.append(rule.toString(lastLevel - 1));
			sb.append("\n");
		}

		// sb.append(PennTreeRenderer.render(hierarchy));
		sb.append("\n");
		// sb.append(Arrays.toString(scores));
		return sb.toString();
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

	public void computeSubRuleList() {
		subRuleList = new SubRule[nParam];
		int nRules = computeSubRules(0, 0, 0, 0, 0, hierarchy);
		if (parentState != 0 && nRules != nParam)
			System.out.println("A rule got lost");
	}

	private int computeSubRules(int myID, double previousScore,
			int nextChildSubstate, int nextParentSubstate, int myDepth,
			Tree<Double> tree) {
		if (tree.isLeaf()) {
			if (parentState == 0 && nextParentSubstate > 0) {
				myID++;
				return myID;
			}
			double myScore = Math.exp(previousScore + tree.getLabel());
			SubRule rule = new SubRule((short) nextChildSubstate,
					(short) nextParentSubstate, (short) myDepth, myScore);
			subRuleList[myID] = rule;
			myID++;
		} else {
			double myScore = previousScore + tree.getLabel();
			int i = 0;
			for (Tree<Double> child : tree.getChildren()) {
				myID = computeSubRules(myID, myScore, nextChildSubstate * 2
						+ (i / 2), nextParentSubstate * 2 + (i % 2),
						myDepth + 1, child);
				i++;
			}
		}
		return myID;
	}

	class SubRule implements Serializable {
		private static final long serialVersionUID = 1;
		short child, parent, level;
		double score;

		SubRule(short c, short p, short l, double s) {
			child = c;
			parent = p;
			level = l;
			score = s;
		}

		public String toString() {
			String s = "[" + parent + "] \t -> \t [" + child + "] \t " + score;
			return s;
		}

		public String toString(int finalLevel) {
			if (finalLevel == level)
				return toString();
			int k = (int) Math.pow(2, finalLevel - level);
			String s = "[" + (k * parent) + "-" + (k * parent + k - 1)
					+ "] \t -> \t [" + (k * child) + "-" + (k * child + k - 1)
					+ "] \t " + score + "\t level: " + level;
			;
			return s;
		}

	}

}
