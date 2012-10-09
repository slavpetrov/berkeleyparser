package edu.berkeley.nlp.PCFGLA;

import java.util.Random;

import edu.berkeley.nlp.util.*;

/**
 * Unary Rules (with ints for parent and child)
 * 
 * @author Dan Klein
 */
public class UnaryRule extends Rule implements java.io.Serializable, Comparable {

	public short childState = -1;
	/**
	 * NEW: scores[childSubState][parentSubState]
	 */
	public double[][] scores;

	/*
	 * public UnaryRule(String s, Numberer n) { String[] fields =
	 * StringUtils.splitOnCharWithQuoting(s, ' ', '\"', '\\'); //
	 * System.out.println("fields:\n" + fields[0] + "\n" + fields[2] + "\n" +
	 * fields[3]); this.parent = n.number(fields[0]); this.child =
	 * n.number(fields[2]); this.score = Double.parseDouble(fields[3]); }
	 */
	public UnaryRule(short pState, short cState, double[][] scores) {
		this.parentState = pState;
		this.childState = cState;
		this.scores = scores;
	}

	public UnaryRule(short pState, short cState) {
		this.parentState = pState;
		this.childState = cState;
		// this.scores = new double[1][1];
	}

	/** Copy constructor */
	public UnaryRule(UnaryRule u) {
		this(u.parentState, u.childState, ArrayUtil.copy(u.scores));
	}

	public UnaryRule(UnaryRule u, double[][] newScores) {
		this(u.parentState, u.childState, newScores);
	}

	public UnaryRule(short pState, short cState, short pSubStates,
			short cSubStates) {
		this.parentState = pState;
		this.childState = cState;
		this.scores = new double[cSubStates][pSubStates];
	}

	public boolean isUnary() {
		return true;
	}

	public int hashCode() {
		return ((int) parentState << 18) ^ ((int) childState);
	}

	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o instanceof UnaryRule) {
			UnaryRule ur = (UnaryRule) o;
			if (parentState == ur.parentState && childState == ur.childState) {
				return true;
			}
		}
		return false;
	}

	public int compareTo(Object o) {
		UnaryRule ur = (UnaryRule) o;
		if (parentState < ur.parentState) {
			return -1;
		}
		if (parentState > ur.parentState) {
			return 1;
		}
		if (childState < ur.childState) {
			return -1;
		}
		if (childState > ur.childState) {
			return 1;
		}
		return 0;
	}

	private static final char[] charsToEscape = new char[] { '\"' };

	public String toString() {
		Numberer n = Numberer.getGlobalNumberer("tags");
		String cState = (String) n.object(childState);
		if (cState.endsWith("^g"))
			cState = cState.substring(0, cState.length() - 2);
		String pState = (String) n.object(parentState);
		if (pState.endsWith("^g"))
			pState = pState.substring(0, pState.length() - 2);
		if (scores == null)
			return pState + " -> " + cState + "\n";
		StringBuilder sb = new StringBuilder();
		for (int cS = 0; cS < scores.length; cS++) {
			if (scores[cS] == null)
				continue;
			for (int pS = 0; pS < scores[cS].length; pS++) {
				double p = scores[cS][pS];
				if (p > 0)
					sb.append(pState + "_" + pS + " -> " + cState + "_" + cS
							+ " " + p + "\n");
			}
		}
		return sb.toString();
	}

	public String toString_old() {
		Numberer n = Numberer.getGlobalNumberer("tags");
		return "\""
				+ StringUtils.escapeString(n.object(parentState).toString(),
						charsToEscape, '\\')
				+ "\" -> \""
				+ StringUtils.escapeString(n.object(childState).toString(),
						charsToEscape, '\\') + "\" "
				+ ArrayUtil.toString(scores);
	}

	public short getChildState() {
		return childState;
	}

	public void setScore(int pS, int cS, double score) {
		// sets the score for a particular combination of substates
		scores[cS][pS] = score;
	}

	public double getScore(int pS, int cS) {
		// gets the score for a particular combination of substates
		if (scores[cS] == null) {
			if (logarithmMode)
				return Double.NEGATIVE_INFINITY;
			return 0;
		}
		return scores[cS][pS];
	}

	public void setScores2(double[][] scores) {
		this.scores = scores;
	}

	/**
	 * scores[parentSubState][childSubState]
	 */
	public double[][] getScores2() {
		return scores;
	}

	public void setNodes(short pState, short cState) {
		this.parentState = pState;
		this.childState = cState;
	}

	private static final long serialVersionUID = 2L;

	/**
	 * @return
	 */
	public UnaryRule splitRule(short[] numSubStates, short[] newNumSubStates,
			Random random, double randomness, boolean doNotNormalize, int mode) {
		// when splitting on parent, never split on ROOT parent
		short parentSplitFactor = this.getParentState() == 0 ? (short) 1
				: (short) 2;
		if (newNumSubStates[this.parentState] == numSubStates[this.parentState]) {
			parentSplitFactor = 1;
		}
		int childSplitFactor = 2;
		if (newNumSubStates[this.childState] == numSubStates[this.childState]) {
			childSplitFactor = 1;
		}
		double[][] oldScores = this.getScores2();
		double[][] newScores = new double[newNumSubStates[this.childState]][];

		// for all current substates
		for (short cS = 0; cS < oldScores.length; cS++) {
			if (oldScores[cS] == null)
				continue;

			for (short c = 0; c < childSplitFactor; c++) {
				short newCS = (short) (childSplitFactor * cS + c);
				newScores[newCS] = new double[newNumSubStates[this.parentState]];
			}

			for (short pS = 0; pS < oldScores[cS].length; pS++) {
				double score = oldScores[cS][pS];
				// split on parent
				for (short p = 0; p < parentSplitFactor; p++) {
					double divFactor = (doNotNormalize) ? 1.0
							: childSplitFactor;
					double randomComponent = score / divFactor * randomness
							/ 100 * (random.nextDouble() - 0.5);
					// split on child
					for (short c = 0; c < childSplitFactor; c++) {
						if (c == 1) {
							randomComponent *= -1;
						}
						if (childSplitFactor == 1) {
							randomComponent = 0;
						}
						// divide score by divFactor because we're splitting
						// each rule in 1/divFactor
						short newPS = (short) (parentSplitFactor * pS + p);
						short newCS = (short) (childSplitFactor * cS + c);
						double splitFactor = (doNotNormalize) ? 1.0
								: childSplitFactor;
						newScores[newCS][newPS] = (score / splitFactor + randomComponent);
						// sparsifier.splitUnaryWeight(
						// oldRule.getParentState(), cS,
						// oldRule.getChildState(), pS,
						// newPS, newCS, childSplitFactor, randomComponent,
						// score, tagNumberer);
						if (mode == 2)
							newScores[newCS][newPS] = 1.0 + random.nextDouble() / 100.0;
					}
				}
			}
		}
		return new UnaryRule(this, newScores);
	}

}
