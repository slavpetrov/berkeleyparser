package edu.berkeley.nlp.PCFGLA;

import edu.berkeley.nlp.util.*;
import java.io.Serializable;
import java.util.Random;

/**
 * Binary rules (ints for parent, left and right children)
 * 
 * @author Dan Klein
 */

public class BinaryRule extends Rule implements Serializable,
		java.lang.Comparable {

	public short leftChildState = -1;
	public short rightChildState = -1;
	/**
	 * NEW: scores[leftSubState][rightSubState][parentSubState] gives score for
	 * this rule
	 */
	public double[][][] scores;

	/**
	 * Creates a BinaryRule from String s, assuming it was created using
	 * toString().
	 * 
	 * @param s
	 */
	/*
	 * public BinaryRule(String s, Numberer n) { String[] fields =
	 * StringUtils.splitOnCharWithQuoting(s, ' ', '\"', '\\'); //
	 * System.out.println("fields:\n" + fields[0] + "\n" + fields[2] + "\n" +
	 * fields[3] + "\n" + fields[4]); this.parent = n.number(fields[0]);
	 * this.leftChild = n.number(fields[2]); this.rightChild =
	 * n.number(fields[3]); this.score = Double.parseDouble(fields[4]); }
	 */
	public BinaryRule(short pState, short lState, short rState,
			double[][][] scores) {
		this.parentState = pState;
		this.leftChildState = lState;
		this.rightChildState = rState;
		this.scores = scores;
	}

	public BinaryRule(short pState, short lState, short rState) {
		this.parentState = pState;
		this.leftChildState = lState;
		this.rightChildState = rState;
		// this.scores = new double[1][1][1];
	}

	/** Copy constructor */
	public BinaryRule(BinaryRule b) {
		this(b.parentState, b.leftChildState, b.rightChildState, ArrayUtil
				.copy(b.scores));
	}

	public BinaryRule(BinaryRule b, double[][][] newScores) {
		this(b.parentState, b.leftChildState, b.rightChildState, newScores);
	}

	// public BinaryRule(short pState, short lState, short rState, short
	// pSubStates, int lSubStates, int rSubStates) {
	// this.parentState = pState;
	// this.leftChildState = lState;
	// this.rightChildState = rState;
	// this.scores = new double[lSubStates][rSubStates][pSubStates];
	// }

	public int hashCode() {
		return ((int) parentState << 16) ^ ((int) leftChildState << 8)
				^ ((int) rightChildState);
	}

	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o instanceof BinaryRule) {
			BinaryRule br = (BinaryRule) o;
			if (parentState == br.parentState
					&& leftChildState == br.leftChildState
					&& rightChildState == br.rightChildState) {
				return true;
			}
		}
		return false;
	}

	private static final char[] charsToEscape = new char[] { '\"' };

	public String toString() {
		Numberer n = Numberer.getGlobalNumberer("tags");
		String lState = (String) n.object(leftChildState);
		if (lState.endsWith("^g"))
			lState = lState.substring(0, lState.length() - 2);
		String rState = (String) n.object(rightChildState);
		if (rState.endsWith("^g"))
			rState = rState.substring(0, rState.length() - 2);
		String pState = (String) n.object(parentState);
		if (pState.endsWith("^g"))
			pState = pState.substring(0, pState.length() - 2);
		StringBuilder sb = new StringBuilder();
		if (scores == null)
			return pState + " -> " + lState + " " + rState + "\n";
		// sb.append(pState+ " -> "+lState+ " "+rState+ "\n");
		for (int lS = 0; lS < scores.length; lS++) {
			for (int rS = 0; rS < scores[lS].length; rS++) {
				if (scores[lS][rS] == null)
					continue;
				for (int pS = 0; pS < scores[lS][rS].length; pS++) {
					double p = scores[lS][rS][pS];
					if (p > 0)
						sb.append(pState + "_" + pS + " -> " + lState + "_"
								+ lS + " " + rState + "_" + rS + " " + p + "\n");
				}
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
				+ StringUtils.escapeString(n.object(leftChildState).toString(),
						charsToEscape, '\\')
				+ "\" \""
				+ StringUtils.escapeString(
						n.object(rightChildState).toString(), charsToEscape,
						'\\') + "\" " + ArrayUtil.toString(scores);
	}

	public int compareTo(Object o) {
		BinaryRule ur = (BinaryRule) o;
		if (parentState < ur.parentState) {
			return -1;
		}
		if (parentState > ur.parentState) {
			return 1;
		}
		if (leftChildState < ur.leftChildState) {
			return -1;
		}
		if (leftChildState > ur.leftChildState) {
			return 1;
		}
		if (rightChildState < ur.rightChildState) {
			return -1;
		}
		if (rightChildState > ur.rightChildState) {
			return 1;
		}
		return 0;
	}

	public short getLeftChildState() {
		return leftChildState;
	}

	public short getRightChildState() {
		return rightChildState;
	}

	// public void setScore(int pS, int lS, int rS, double score){
	// // sets the score for a particular combination of substates
	// scores[lS][rS][pS] = score;
	// }

	public double getScore(int pS, int lS, int rS) {
		// gets the score for a particular combination of substates
		if (scores[lS][rS] == null) {
			if (logarithmMode)
				return Double.NEGATIVE_INFINITY;
			return 0;
		}
		return scores[lS][rS][pS];
	}

	public void setScores2(double[][][] scores) {
		this.scores = scores;
	}

	/**
	 * scores[parentSubState][leftSubState][rightSubState] gives score for this
	 * rule
	 */
	public double[][][] getScores2() {
		return scores;
	}

	public void setNodes(short pState, short lState, short rState) {
		this.parentState = pState;
		this.leftChildState = lState;
		this.rightChildState = rState;
	}

	private static final long serialVersionUID = 2L;

	public BinaryRule splitRule(short[] numSubStates, short[] newNumSubStates,
			Random random, double randomness, boolean doNotNormalize, int mode) {
		// when splitting on parent, never split on ROOT
		int parentSplitFactor = this.getParentState() == 0 ? 1 : 2; // should

		if (newNumSubStates[this.parentState] == numSubStates[this.parentState]) {
			parentSplitFactor = 1;
		}
		int lChildSplitFactor = 2;
		if (newNumSubStates[this.leftChildState] == numSubStates[this.leftChildState]) {
			lChildSplitFactor = 1;
		}
		int rChildSplitFactor = 2;
		if (newNumSubStates[this.rightChildState] == numSubStates[this.rightChildState]) {
			rChildSplitFactor = 1;
		}

		double[][][] oldScores = this.getScores2();
		double[][][] newScores = new double[oldScores.length
				* lChildSplitFactor][oldScores[0].length * rChildSplitFactor][];
		// [oldScores[0][0].length * parentSplitFactor];
		// Arrays.fill(newScores,Double.NEGATIVE_INFINITY);
		// for all current substates
		for (short lcS = 0; lcS < oldScores.length; lcS++) {
			for (short rcS = 0; rcS < oldScores[0].length; rcS++) {
				if (oldScores[lcS][rcS] == null)
					continue;

				for (short lc = 0; lc < lChildSplitFactor; lc++) {
					for (short rc = 0; rc < rChildSplitFactor; rc++) {
						short newLCS = (short) (lChildSplitFactor * lcS + lc);
						short newRCS = (short) (rChildSplitFactor * rcS + rc);
						newScores[newLCS][newRCS] = new double[newNumSubStates[this.parentState]];
					}
				}

				for (short pS = 0; pS < oldScores[lcS][rcS].length; pS++) {
					double score = oldScores[lcS][rcS][pS];
					// split on parent
					for (short p = 0; p < parentSplitFactor; p++) {
						double divFactor = (doNotNormalize) ? 1.0
								: lChildSplitFactor * rChildSplitFactor;
						double randomComponentLC = score / divFactor
								* randomness / 100
								* (random.nextDouble() - 0.5);
						// split on left child
						for (short lc = 0; lc < lChildSplitFactor; lc++) {
							// reverse the random component for half of the
							// rules
							if (lc == 1) {
								randomComponentLC *= -1;
							}
							// don't add randomness if we're not splitting
							if (lChildSplitFactor == 1) {
								randomComponentLC = 0;
							}
							double randomComponentRC = score / divFactor
									* randomness / 100
									* (random.nextDouble() - 0.5);
							// split on right child
							for (short rc = 0; rc < rChildSplitFactor; rc++) {
								// reverse the random component for half of the
								// rules
								if (rc == 1) {
									randomComponentRC *= -1;
								}
								// don't add randomness if we're not splitting
								if (rChildSplitFactor == 1) {
									randomComponentRC = 0;
								}
								// set new score; divide score by 4 because
								// we're dividing each
								// binary rule under a parent into 4
								short newPS = (short) (parentSplitFactor * pS + p);
								short newLCS = (short) (lChildSplitFactor * lcS + lc);
								short newRCS = (short) (rChildSplitFactor * rcS + rc);
								double splitFactor = (doNotNormalize) ? 1.0
										: lChildSplitFactor * rChildSplitFactor;
								newScores[newLCS][newRCS][newPS] = (score
										/ (splitFactor) + randomComponentLC + randomComponentRC);
								// sparsifier
								// .splitBinaryWeight(oldRule.getParentState(),
								// pS,
								// oldRule.getLeftChildState(), lcS, oldRule
								// .getRightChildState(), rcS, newPS, newLCS,
								// newRCS, lChildSplitFactor, rChildSplitFactor,
								// randomComponentLC, randomComponentRC, ,
								// tagNumberer);
								if (mode == 2)
									newScores[newLCS][newRCS][newPS] = 1.0 + random
											.nextDouble() / 100.0;
							}
						}
					}
				}
			}
		}
		BinaryRule newRule = new BinaryRule(this, newScores);
		return newRule;

	}

}
