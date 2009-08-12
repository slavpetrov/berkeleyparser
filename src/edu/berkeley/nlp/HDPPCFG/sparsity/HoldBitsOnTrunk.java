/**
 * 
 */
package edu.berkeley.nlp.HDPPCFG.sparsity;

import java.io.Serializable;

import edu.berkeley.nlp.util.Numberer;

/**
 * Hold the first few bits (highest-valued bits) of substates along the trunk of
 * a rule (i.e., ones beginning with the 'at' symbol). They must be the first
 * ones because that's what will stay constant when the Grammar is split.
 * 
 * @author leon
 * 
 */
public class HoldBitsOnTrunk implements Sparsifier, Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	int bitsToHold;
	
	short[] nSubStates;
	
	public HoldBitsOnTrunk(int bitsToHold) {
		this.bitsToHold = bitsToHold;
	}
	
	public void setNSubStates(short[] nSubStates) {
		this.nSubStates=nSubStates;
	}
	
	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.PCFGLA.sparsity.Sparsifier#unaryWeight(short, short, short, short, double)
	 */
	public double unaryWeight(short parentState, short parentSubState,
			short childState, short childSubState, double originalWeight,
			Numberer tagNumberer) {
		if (disallowedChange(childSubState, parentSubState, nSubStates[parentState])
				&& isOnTrunk(childState, tagNumberer))
			return 0;
		return originalWeight;
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.PCFGLA.sparsity.Sparsifier#binaryWeight(short, short, short, short, short, short, double)
	 */
	public double binaryWeight(short parentState, short parentSubState,
			short lChildState, short lChildSubState, short rChildState,
			short rChildSubState, double originalWeight, Numberer tagNumberer) {
		boolean lChildDiff = disallowedChange(lChildSubState, parentSubState,
				nSubStates[parentState])
				&& isOnTrunk(lChildState, tagNumberer);
		boolean rChildDiff = disallowedChange(rChildSubState, parentSubState,
				nSubStates[lChildState])
				&& isOnTrunk(rChildState, tagNumberer);
		if (lChildDiff || rChildDiff)
			return 0;
		return originalWeight;
	}

	public boolean isOnTrunk( short state, Numberer tagNumberer ) {
		return ((String)tagNumberer.object(state)).charAt(0)=='@';
	}
	
	int getMask(int nSubStates) {
		int maxBits = (int)Math.round(Math.log(nSubStates)/Math.log(2));
		int mask = 0;
		for (int i=0; i<Math.min(bitsToHold,maxBits); i++) {
			int targetBit = Math.max(maxBits - bitsToHold,0) + i;
			mask = mask | (1 << targetBit);
		}
		return mask;
	}

	public boolean disallowedChange(int a, int b, int nSubStates) {
		// don't allow changes in the first few digits
		int mask = getMask(nSubStates);
		// mask to the first few digits
		a = a & mask;
		b = b & mask;
		// test for diff
		return a != b;
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.PCFGLA.sparsity.Sparsifier#splitBinaryWeight(short, short, short, short, short, short, short, short, short, int, int, double, double, double, edu.berkeley.nlp.util.Numberer)
	 */
	public double splitBinaryWeight(short parentState, short pS,
			short leftChildState, short lcS, short rightChildState, short rcS,
			short newPS, short newLCS, short newRCS, int lChildSplitFactor,
			int rChildSplitFactor, double randomComponentLC,
			double randomComponentRC, double expScore, Numberer tagNumberer) {
		// We don't allow changes if we're within the mask, so the score doesn't decrease
		if ((isOnTrunk(leftChildState, tagNumberer) && (Math.log(nSubStates[lcS]
				* lChildSplitFactor)
				/ Math.log(2) <= bitsToHold))) {
			lChildSplitFactor = 1;
			randomComponentLC = 0;
		}
		if ((isOnTrunk(rightChildState, tagNumberer) && (Math.log(nSubStates[rcS]
						* rChildSplitFactor)
						/ Math.log(2) <= bitsToHold))) {
			rChildSplitFactor = 1;
			randomComponentRC = 0;
		}
		double score = expScore / (lChildSplitFactor * rChildSplitFactor)
				+ randomComponentLC + randomComponentRC;
		// delegate the allowability computation
		return (double) binaryWeight(parentState, newPS, leftChildState, newLCS,
				rightChildState, newRCS, score, tagNumberer);
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.PCFGLA.sparsity.Sparsifier#splitUnaryWeight(short, short, short, short, short, short, int, double, double, edu.berkeley.nlp.util.Numberer)
	 */
	public double splitUnaryWeight(short parentState, short pS, short childState, short cS, short newPS, short newCS, int childSplitFactor, double randomComponent, double expScore, Numberer tagNumberer) {
		double score = 0;
		// We don't allow changes if we're within the mask, so the score doesn't decrease
		if (isOnTrunk(childState,tagNumberer)
				&& Math.log(nSubStates[cS] * childSplitFactor) / Math.log(2) <= bitsToHold)
			score = expScore;
		// When the state is high enough to allow changes, we must reduce the score
		else
			score = expScore / (childSplitFactor) + randomComponent;
		// delegate the allowability computation
		return (double) unaryWeight(parentState, newPS, childState, newCS,
				score, tagNumberer);
	}
}
