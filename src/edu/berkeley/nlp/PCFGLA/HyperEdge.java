/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

/**
 * @author petrov
 * 
 */
public class HyperEdge {
	int parentState, lChildState, rChildState, childState;
	boolean isUnary;
	double score, ruleScore;
	int start, split, end;
	int parentBest, lChildBest, rChildBest, childBest;
	boolean alreadyExpanded;

	public HyperEdge(int pState, int lState, int rState, int pBest, int lBest,
			int rBest, int begin, int mid, int finale, double cost,
			double ruleCost) {
		this.parentState = pState;
		this.lChildState = lState;
		this.rChildState = rState;
		this.parentBest = pBest;
		this.lChildBest = lBest;
		this.rChildBest = rBest;
		this.childState = -1;
		this.start = begin;
		this.split = mid;
		this.end = finale;
		this.score = cost;
		this.isUnary = false;
		this.ruleScore = ruleCost;
	}

	public HyperEdge(int pState, int cState, int pBest, int cBest, int begin,
			int finale, double cost, double ruleCost) {
		this.parentState = pState;
		this.childState = cState;
		this.lChildState = -1;
		this.rChildState = -1;
		this.parentBest = pBest;
		this.childBest = cBest;
		this.start = begin;
		this.end = finale;
		this.score = cost;
		this.isUnary = true;
		this.ruleScore = ruleCost;
	}

	public boolean differsInPOSatMost(HyperEdge other, boolean[] grammarTags) {
		// assume the edges go over the same span and have the same head
		if (this.split != other.split)
			return false;
		// if (this.score==other.score)
		// return true;
		if (this.isUnary) {
			if (/* this.score==other.score */this.childBest == other.childBest
					&& other.childState == this.childState)
				return true;
		} else {
			if (this.end - this.split == 1
					&& this.lChildState == other.lChildState
					&& /* this.score==other.score */this.lChildBest == other.lChildBest
					&& !grammarTags[this.rChildState]
					&& !grammarTags[other.rChildState])
				return true;
			if (this.split - this.start == 1
					&& this.rChildState == other.rChildState
					&& /* this.score==other.score */this.rChildBest == other.rChildBest)
				return true;
			if (this.lChildState == other.lChildState
					&& this.rChildState == other.rChildState
					&& /* this.score==other.score */this.rChildBest == other.rChildBest
					&& this.lChildBest == other.lChildBest
					&& !grammarTags[this.lChildState]
					&& !grammarTags[other.lChildState])
				return true;
		}

		return false;
	}

}
