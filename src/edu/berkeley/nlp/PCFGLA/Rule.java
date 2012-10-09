package edu.berkeley.nlp.PCFGLA;

import java.util.Comparator;

/**
 * Parent class for unary and binary rules.
 * 
 * @author Dan Klein
 */
public class Rule implements java.io.Serializable {

	public int identifier; // a unique number

	public short parentState = -1;
	public boolean logarithmMode = false;

	public short getParentState() {
		return parentState;
	}

	// public int getParentSubState() {
	// return parentSubState;
	// }
	//
	// /** @return the log of the rule probability */
	// public double score() {
	// return score;
	// }

	public boolean isUnary() {
		return false;
	}

	static class ScoreComparator implements Comparator<Rule> {
		public int compare(Rule r1, Rule r2) {
			// TODO : fix
			// if (r1.score() < r2.score()) {
			// return -1;
			// } else if (r1.score() == r2.score()) {
			// return 0;
			// } else {
			return 1;
			// }
		}

		ScoreComparator() {
		}

	}

	private static Comparator<Rule> scoreComparator = new ScoreComparator();

	public static Comparator<Rule> scoreComparator() {
		return scoreComparator;
	}

	private static final long serialVersionUID = 1L;

}
