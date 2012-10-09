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
public class NoSmoothing implements Smoother, Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * edu.berkeley.nlp.PCFGLA.smoothing.Smoother#smooth(edu.berkeley.nlp.util
	 * .UnaryCounterTable, edu.berkeley.nlp.util.BinaryCounterTable)
	 */
	public void smooth(UnaryCounterTable unaryCounter,
			BinaryCounterTable binaryCounter) {
		// perform no smoothing at all
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.PCFGLA.smoothing.Smoother#smooth(short, float[])
	 */
	public void smooth(short tag, double[] ruleScores) {
		// do nothing
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.PCFGLA.smoothing.Smoother#copy()
	 */
	public Smoother copy() {
		return this;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.PCFGLA.smoothing.Smoother#updateWeights(int[][])
	 */
	public void updateWeights(int[][] toSubstateMapping) {
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * edu.berkeley.nlp.PCFGLA.smoothing.Smoother#remapStates(edu.berkeley.nlp
	 * .util.Numberer, edu.berkeley.nlp.util.Numberer)
	 */
	public Smoother remapStates(Numberer thisNumberer, Numberer newNumberer) {
		// TODO Auto-generated method stub
		return null;
	}

}
