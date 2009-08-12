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
public class AllowAllTransitions implements Sparsifier, Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.PCFGLA.Sparsity.Sparsifier#unaryWeight(short, short, short, short, double)
	 */
	public double unaryWeight(short parentState, short parentSubState,
			short childState, short childSubState, double originalWeight, Numberer tagNumberer) {
		return originalWeight;
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.PCFGLA.Sparsity.Sparsifier#binaryWeight(short, short, short, short, short, short, double)
	 */
	public double binaryWeight(short parentState, short parentSubState,
			short lChildState, short lChildSubState, short rChildState,
			short rChildSubState, double originalWeight, Numberer tagNumberer) {
		return originalWeight;
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.PCFGLA.sparsity.Sparsifier#splitBinaryWeight(short, int, short, int, short, int, int, int, int, int, int, double, double, double)
	 */
	public double splitBinaryWeight(short parentState, short ps,
			short leftChildState, short lcS, short rightChildState, short rcS, short newPS,
			short newLCS, short newRCS, int lChildSplitFactor, int rChildSplitFactor,
			double randomComponentLC, double randomComponentRC, double expScore,
			Numberer tagNumberer) {
		return (double) (expScore / (lChildSplitFactor * rChildSplitFactor)
				+ randomComponentLC + randomComponentRC);
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.PCFGLA.sparsity.Sparsifier#splitUnaryWeight(short, int, short, int, int, int, int, double, double)
	 */
	public double splitUnaryWeight(short parentState, short ps, short childState,
			short cS, short newPS, short newCS, int childSplitFactor,
			double randomComponent, double expScore, Numberer tagNumberer) {
		return (double) (expScore / childSplitFactor + randomComponent);
	}
}
