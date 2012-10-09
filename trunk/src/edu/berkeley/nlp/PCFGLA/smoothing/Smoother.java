/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.smoothing;

import java.io.Serializable;

import edu.berkeley.nlp.PCFGLA.BinaryCounterTable;
import edu.berkeley.nlp.PCFGLA.UnaryCounterTable;
import edu.berkeley.nlp.util.Numberer;

/**
 * @author leon
 * 
 */
public interface Smoother {
	public void smooth(UnaryCounterTable unaryCounter,
			BinaryCounterTable binaryCounter);

	public void smooth(short tag, double[] ruleScores);

	public void updateWeights(int[][] toSubstateMapping);

	public Smoother copy();

	public Smoother remapStates(Numberer thisNumberer, Numberer newNumberer);
}
