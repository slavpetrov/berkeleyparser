/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.Numberer;

/**
 * @author petrov
 * 
 */
public class HierarchicalBinaryRule extends BinaryRule {

	private static final long serialVersionUID = 1L;

	public HierarchicalBinaryRule(HierarchicalBinaryRule b) {
		super(b);
		this.scoreHierarchy = new ArrayList<double[][][]>();
		for (double[][][] scores : b.scoreHierarchy) {
			this.scoreHierarchy.add(ArrayUtil.clone(scores));
		}
		this.lastLevel = b.lastLevel;
		this.scores = null;
	}

	// assume for now that the rule being passed in is unsplit
	public HierarchicalBinaryRule(BinaryRule b) {
		super(b);
		this.scoreHierarchy = new ArrayList<double[][][]>();
		double[][][] scoreThisLevel = new double[1][1][1];
		scoreThisLevel[0][0][0] = Math.log(b.scores[0][0][0]);
		scoreHierarchy.add(scoreThisLevel);
		this.lastLevel = 0;
		this.scores = null;
	}

	/*
	 * new stuff below
	 */

	/**
	 * before: scores[leftSubState][rightSubState][parentSubState] gives score
	 * for this rule now: have a hierarchy of refinements
	 */

	List<double[][][]> scoreHierarchy;
	public int lastLevel = -1;

	public void explicitlyComputeScores(int finalLevel, short[] newNumSubStates) {
		int newMaxStates = (int) Math.pow(2, finalLevel + 1);
		int newPStates = Math.min(newMaxStates,
				newNumSubStates[this.parentState]);
		int newLStates = Math.min(newMaxStates,
				newNumSubStates[this.leftChildState]);
		int newRStates = Math.min(newMaxStates,
				newNumSubStates[this.rightChildState]);

		this.scores = new double[newLStates][newRStates][newPStates];
		for (int level = 0; level <= lastLevel; level++) {
			double[][][] scoresThisLevel = scoreHierarchy.get(level);
			if (scoresThisLevel == null)
				continue;
			int divisorL = newLStates / scoresThisLevel.length;
			int divisorR = newRStates / scoresThisLevel[0].length;
			int divisorP = newPStates / scoresThisLevel[0][0].length;
			for (int lChild = 0; lChild < newLStates; lChild++) {
				for (int rChild = 0; rChild < newRStates; rChild++) {
					for (int parent = 0; parent < newPStates; parent++) {
						this.scores[lChild][rChild][parent] += scoresThisLevel[lChild
								/ divisorL][rChild / divisorR][parent
								/ divisorP];
					}
				}
			}
		}
		for (int lChild = 0; lChild < newLStates; lChild++) {
			for (int rChild = 0; rChild < newRStates; rChild++) {
				for (int parent = 0; parent < newPStates; parent++) {
					this.scores[lChild][rChild][parent] = Math
							.exp(scores[lChild][rChild][parent]);
				}
			}
		}
	}

	public double[][][] getLastLevel() {
		return this.scoreHierarchy.get(lastLevel);
	}

	@Override
	public HierarchicalBinaryRule splitRule(short[] numSubStates,
			short[] newNumSubStates, Random random, double randomness,
			boolean doNotNormalize, int mode) {
		// when splitting on parent, never split on ROOT, but otherwise split
		// everything
		if (mode != 2)
			throw new Error("Can't split hiereachical rule in this mode!");

		int newMaxStates = (int) Math.pow(2, lastLevel + 1);
		int newPStates = Math.min(newMaxStates,
				newNumSubStates[this.parentState]);
		int newLStates = Math.min(newMaxStates,
				newNumSubStates[this.leftChildState]);
		int newRStates = Math.min(newMaxStates,
				newNumSubStates[this.rightChildState]);

		double[][][] newScores = new double[newLStates][newRStates][newPStates];
		for (int lChild = 0; lChild < newLStates; lChild++) {
			for (int rChild = 0; rChild < newRStates; rChild++) {
				for (int parent = 0; parent < newPStates; parent++) {
					newScores[lChild][rChild][parent] = random.nextDouble() / 100.0;
				}
			}
		}
		HierarchicalBinaryRule newRule = new HierarchicalBinaryRule(this);
		newRule.scoreHierarchy.add(newScores);
		newRule.lastLevel++;
		return newRule;
	}

	public int mergeRule() {
		double[][][] scoresFinalLevel = scoreHierarchy.get(lastLevel);
		boolean allZero = true;
		for (int lChild = 0; lChild < scoresFinalLevel.length; lChild++) {
			for (int rChild = 0; rChild < scoresFinalLevel[0].length; rChild++) {
				for (int parent = 0; parent < scoresFinalLevel[0][0].length; parent++) {
					allZero = allZero
							&& (scoresFinalLevel[lChild][rChild][parent] == 0.0);
				}
			}
		}
		if (allZero) {
			scoresFinalLevel = null;
			scoreHierarchy.remove(lastLevel);
			lastLevel--;
			return 1;
		}
		return 0;
	}

	@Override
	public String toString() {
		Numberer n = Numberer.getGlobalNumberer("tags");
		String lState = (String) n.object(leftChildState);
		String rState = (String) n.object(rightChildState);
		String pState = (String) n.object(parentState);
		StringBuilder sb = new StringBuilder();
		if (scores == null)
			return pState + " -> " + lState + " " + rState + "\n";
		// sb.append(pState+ " -> "+lState+ " "+rState+ "\n");
		sb.append(pState + " -> " + lState + " " + rState + "\n");
		sb.append(ArrayUtil.toString(scores) + "\n");
		for (double[][][] s : scoreHierarchy) {
			sb.append(ArrayUtil.toString(s) + "\n");
		}
		sb.append("\n");
		return sb.toString();
	}

	public int countNonZeroFeatures() {
		int total = 0;
		for (int level = 0; level <= lastLevel; level++) {
			double[][][] scoresThisLevel = scoreHierarchy.get(level);
			if (scoresThisLevel == null)
				continue;
			for (int lChild = 0; lChild < scoresThisLevel.length; lChild++) {
				for (int rChild = 0; rChild < scoresThisLevel.length; rChild++) {
					for (int parent = 0; parent < scoresThisLevel.length; parent++) {
						if (scoresThisLevel[lChild][rChild][parent] != 0)
							total++;
					}
				}
			}
		}
		return total;
	}

}
