/**
 * 
 */
package edu.berkeley.nlp.HDPPCFG.sparsity;

import edu.berkeley.nlp.util.Numberer;

/**
 * @author leon
 *
 */
public interface Sparsifier {
	public double unaryWeight( short parentState, short parentSubState,
			short childState, short childSubState, double originalWeight, Numberer tagNumberer );
	public double binaryWeight( short parentState, short parentSubState,
			short lChildState, short lChildSubState, short rChildState,
			short rChildSubState, double originalWeight, Numberer tagNumberer );
	/**
	 * @param parentState
	 * @param ps
	 * @param leftChildState
	 * @param lcS
	 * @param rightChildState
	 * @param rcS
	 * @param newPS
	 * @param newLCS
	 * @param newRCS
	 * @param childSplitFactor
	 * @param childSplitFactor2
	 * @param randomComponentLC
	 * @param randomComponentRC
	 * @param expScore
	 * @return
	 */
	public double splitBinaryWeight(short parentState, short ps,
			short leftChildState, short lcS, short rightChildState, short rcS, short newPS,
			short newLCS, short newRCS, int lChildSplitFactor, int rChildSplitFactor,
			double randomComponentLC, double randomComponentRC, double expScore,
			Numberer tagNumberer);
	public double splitUnaryWeight(short parentState, short ps, short childState,
			short cS, short newPS, short newCS, int childSplitFactor,
			double randomComponent, double expScore, Numberer tagNumberer);
}
