/**
 * 
 */
package edu.berkeley.nlp.HDPPCFG.smoothing;

import java.io.Serializable;

import edu.berkeley.nlp.HDPPCFG.BinaryCounterTable;
import edu.berkeley.nlp.HDPPCFG.UnaryCounterTable;

/**
 * @author leon
 *
 */
public class NoSmoothing implements Smoother, Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.PCFGLA.smoothing.Smoother#smooth(edu.berkeley.nlp.util.UnaryCounterTable, edu.berkeley.nlp.util.BinaryCounterTable)
	 */
	public void smooth(UnaryCounterTable unaryCounter,
			BinaryCounterTable binaryCounter) {
		// perform no smoothing at all
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.PCFGLA.smoothing.Smoother#smooth(short, float[])
	 */
	public void smooth(short tag, double[] ruleScores) {
		// do nothing
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.HDPPCFG.smoothing.Smoother#smooth(double[][][])
	 */
	public double[][][] smooth(double[][][] scores, int pState) {
		// TODO Auto-generated method stub
		return scores;
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.HDPPCFG.smoothing.Smoother#smooth(double[][])
	 */
	public double[][] smooth(double[][] scores, int pState) {
		// TODO Auto-generated method stub
		return scores;
	}
	
}
