/**
 * 
 */
package edu.berkeley.nlp.HDPPCFG.smoothing;

import edu.berkeley.nlp.HDPPCFG.BinaryCounterTable;
import edu.berkeley.nlp.HDPPCFG.UnaryCounterTable;

/**
 * @author leon
 *
 */
public interface Smoother {
	public void smooth(UnaryCounterTable unaryCounter, BinaryCounterTable binaryCounter);
	public void smooth(short tag, double[] ruleScores);
	double[][][] smooth(double[][][] scores, int pState);
	double[][] smooth(double[][] scores, int pState);
}
