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
public class HierarchicalUnaryRule extends UnaryRule {

	private static final long serialVersionUID = 1L;

	public HierarchicalUnaryRule(HierarchicalUnaryRule b) {
		super(b);
		this.scoreHierarchy = new ArrayList<double[][]>();
		for (double[][] scores : b.scoreHierarchy) {
			this.scoreHierarchy.add(ArrayUtil.clone(scores));
		}
		this.lastLevel = b.lastLevel;
		this.scores = null;
	}

	// assume for now that the rule being passed in is unsplit
	public HierarchicalUnaryRule(UnaryRule b) {
		super(b);
		this.scoreHierarchy = new ArrayList<double[][]>();
		double[][] scoreThisLevel = new double[1][1];
		scoreThisLevel[0][0] = Math.log(b.scores[0][0]);
		scoreHierarchy.add(scoreThisLevel);
		this.lastLevel = 0;
		this.scores = null;
	}

	/*
	 * new stuff below
	 */

	/**
	 * before: scores[childSubState][parentSubState] gives score for this rule
	 * now: have a hierarchy of refinements
	 */

	List<double[][]> scoreHierarchy;
	public int lastLevel = -1;

	public void explicitlyComputeScores(int finalLevel, short[] newNumSubStates) {
		int newMaxStates = (int) Math.pow(2, finalLevel + 1);
		int newPStates = Math.min(newMaxStates,
				newNumSubStates[this.parentState]);
		int newCStates = Math.min(newMaxStates,
				newNumSubStates[this.childState]);

		newPStates = (this.parentState == 0) ? 1 : newPStates;
		this.scores = new double[newCStates][newPStates];
		for (int level = 0; level <= lastLevel; level++) {
			double[][] scoresThisLevel = scoreHierarchy.get(level);
			if (scoresThisLevel == null)
				continue;
			int divisorC = newCStates / scoresThisLevel.length;
			int divisorP = newPStates / scoresThisLevel[0].length;
			for (int child = 0; child < newCStates; child++) {
				for (int parent = 0; parent < newPStates; parent++) {
					this.scores[child][parent] += scoresThisLevel[child
							/ divisorC][parent / divisorP];
				}
			}
		}
		for (int child = 0; child < newCStates; child++) {
			for (int parent = 0; parent < newPStates; parent++) {
				this.scores[child][parent] = Math.exp(scores[child][parent]);
			}
		}
	}

	public double[][] getLastLevel() {
		return this.scoreHierarchy.get(lastLevel);
	}

	@Override
	public HierarchicalUnaryRule splitRule(short[] numSubStates,
			short[] newNumSubStates, Random random, double randomness,
			boolean doNotNormalize, int mode) {
		// when splitting on parent, never split on ROOT, but otherwise split
		// everything
		if (mode != 2)
			throw new Error("Can't split hiereachical rule in this mode!");

		int newMaxStates = (int) Math.pow(2, lastLevel + 1);
		int newPStates = Math.min(newMaxStates,
				newNumSubStates[this.parentState]);
		int newCStates = Math.min(newMaxStates,
				newNumSubStates[this.childState]);

		if (parentState == 0)
			newPStates = 1;
		double[][] newScores = new double[newCStates][newPStates];
		for (int child = 0; child < newCStates; child++) {
			for (int parent = 0; parent < newPStates; parent++) {
				newScores[child][parent] = random.nextDouble() / 100.0;
			}
		}
		HierarchicalUnaryRule newRule = new HierarchicalUnaryRule(this);
		newRule.scoreHierarchy.add(newScores);
		newRule.lastLevel++;
		return newRule;
	}

	public int mergeRule() {
		double[][] scoresFinalLevel = scoreHierarchy.get(lastLevel);
		boolean allZero = true;
		for (int child = 0; child < scoresFinalLevel.length; child++) {
			for (int parent = 0; parent < scoresFinalLevel[0].length; parent++) {
				allZero = allZero && (scoresFinalLevel[child][parent] == 0.0);
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
		String cState = (String) n.object(childState);
		String pState = (String) n.object(parentState);
		if (scores == null)
			return pState + " -> " + cState + "\n";
		StringBuilder sb = new StringBuilder();
		sb.append(pState + " -> " + cState + "\n");
		sb.append(ArrayUtil.toString(scores) + "\n");
		for (double[][] s : scoreHierarchy) {
			sb.append(ArrayUtil.toString(s) + "\n");
		}
		sb.append("\n");
		// for (int cS=0; cS<scores.length; cS++){
		// if (scores[cS]==null) continue;
		// for (int pS=0; pS<scores[cS].length; pS++){
		// double p = scores[cS][pS];
		// if (p>0)
		// sb.append(pState+"_"+pS+ " -> " + cState+"_"+cS +" "+p+"\n");
		// }
		// }
		return sb.toString();
	}

	public int countNonZeroFeatures() {
		int total = 0;
		for (int level = 0; level <= lastLevel; level++) {
			double[][] scoresThisLevel = scoreHierarchy.get(level);
			if (scoresThisLevel == null)
				continue;
			for (int child = 0; child < scoresThisLevel.length; child++) {
				for (int parent = 0; parent < scoresThisLevel[0].length; parent++) {
					if (scoresThisLevel[child][parent] != 0)
						total++;
				}
			}
		}
		return total;
	}

}
