/**
 * 
 */
package edu.berkeley.nlp.HDPPCFG.sparsity;

import java.io.Serializable;

import edu.berkeley.nlp.util.Numberer;

/**
 * @author leon
 *
 */
public class NoChangesOnTrunk implements Sparsifier, Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.PCFGLA.sparsity.Sparsifier#unaryWeight(short, short,
	 *      short, short, double)
	 */
	public double unaryWeight(short parentState, short parentSubState,
			short childState, short childSubState, double originalWeight,
			Numberer tagNumberer) {
		if (childSubState != parentSubState && isOnTrunk(childState, tagNumberer))
			return 0;
		return originalWeight;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.PCFGLA.sparsity.Sparsifier#binaryWeight(short, short,
	 *      short, short, short, short, double)
	 */
	public double binaryWeight(short parentState, short parentSubState,
			short lChildState, short lChildSubState, short rChildState,
			short rChildSubState, double originalWeight, Numberer tagNumberer) {
		boolean lChildDiff = (lChildSubState != parentSubState)
				&& isOnTrunk(lChildState, tagNumberer);
		boolean rChildDiff = (rChildSubState != parentSubState)
				&& isOnTrunk(rChildState, tagNumberer);
		if (lChildDiff || rChildDiff)
			return 0;
		return originalWeight;
	}

	public boolean isOnTrunk( short state, Numberer tagNumberer ) {
		return ((String)tagNumberer.object(state)).charAt(0)=='@';
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.PCFGLA.sparsity.Sparsifier#splitBinaryWeight(short, int, short, int, short, int, int, int, int, int, int, double, double, double)
	 */
	public double splitBinaryWeight(short parentState, short ps,
			short leftChildState, short lcS, short rightChildState, short rcS, short newPS,
			short newLCS, short newRCS, int lChildSplitFactor, int rChildSplitFactor,
			double randomComponentLC, double randomComponentRC, double expScore,
			Numberer tagNumberer) {
		if (isOnTrunk(leftChildState,tagNumberer)) {
			lChildSplitFactor = 1;
			randomComponentLC = 0;
		}
		if (isOnTrunk(rightChildState,tagNumberer)) {
			rChildSplitFactor = 1;
			randomComponentRC = 0;
		}
		return (double)binaryWeight(parentState, newPS, leftChildState, newLCS, rightChildState,
				newRCS, expScore / (lChildSplitFactor * rChildSplitFactor)
						+ randomComponentLC + randomComponentRC, tagNumberer);
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.PCFGLA.sparsity.Sparsifier#splitUnaryWeight(short, int, short, int, int, int, int, double, double)
	 */
	public double splitUnaryWeight(short parentState, short ps, short childState,
			short cS, short newPS, short newCS, int childSplitFactor,
			double randomComponent, double expScore, Numberer tagNumberer) {
		if (isOnTrunk(childState,tagNumberer)) {
			childSplitFactor = 1;
			randomComponent = 0;
		}
		return (double) unaryWeight(parentState, newPS, childState, newCS, expScore
				/ childSplitFactor + randomComponent, tagNumberer);
	}
}
