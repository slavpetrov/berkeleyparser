/**
 * 
 */
package edu.berkeley.nlp.HDPPCFG.smoothing;

import java.io.Serializable;

import edu.berkeley.nlp.HDPPCFG.BinaryRule;
import edu.berkeley.nlp.HDPPCFG.UnaryRule;
import edu.berkeley.nlp.HDPPCFG.BinaryCounterTable;
import edu.berkeley.nlp.HDPPCFG.UnaryCounterTable;

/**
 * @author leon
 *
 */
public class SmoothAcrossParentSubstate implements Smoother, Serializable  {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	double same;
	double different;
	
	public SmoothAcrossParentSubstate(double smooth) {
		different = smooth;
		same = 1-different;
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.PCFGLA.smoothing.Smoother#smooth(edu.berkeley.nlp.util.UnaryCounterTable, edu.berkeley.nlp.util.BinaryCounterTable)
	 */
	public void smooth(UnaryCounterTable unaryCounter,
			BinaryCounterTable binaryCounter) {
		for (UnaryRule r : unaryCounter.keySet()) {
			double[][] scopy = smooth(unaryCounter.getCount(r),-1);
			unaryCounter.setCount(r,scopy);
		}
		for (BinaryRule r : binaryCounter.keySet()) {
			double[][][] scopy = smooth(binaryCounter.getCount(r),-1);
			binaryCounter.setCount(r,scopy);
		}
	}

	/**
	 * @param count
	 * @return
	 */
	public double[][][] smooth(double[][][] scores, int pState) {
		double[][][] scopy = new double[scores.length][scores[0].length][];
		for (int j=0; j<scores.length; j++) {
			for (int l=0; l<scores[0].length; l++) {
				if (scores[j][l]==null) 	continue; //nothing to smooth
				
				scopy[j][l] = new double[scores[j][l].length];
				double diff = different/(scores[j][l].length-1);
				for (int i=0; i<scores[j][l].length; i++) {
					for (int k=0; k<scores[j][l].length; k++) {
						scopy[j][l][i] += (i==k ? same : (diff)) * scores[j][l][k];
					}
				}
			}
		}
		return scopy;
	}

	/**
	 * @param count
	 * @return
	 */
	public double[][] smooth(double[][] scores, int pState) {
		double[][] scopy = new double[scores.length][];
		for (int j=0; j<scores.length; j++) {
			if (scores[j]==null)	continue; //nothing to smooth
			
			scopy[j] = new double[scores[j].length];
			double diff = different/(scores[j].length-1);
			for (int i=0; i<scores[j].length; i++) {
				for (int k=0; k<scores[j].length; k++) {
					scopy[j][i] += (i==k ? same : (diff)) * scores[j][k];
				}
			}
		}
		return scopy;
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.PCFGLA.smoothing.Smoother#smooth(short, float[])
	 */
	public void smooth(short tag, double[] scores) {
		double[] scopy = new double[scores.length];
		for (int i=0; i<scores.length; i++) {
			double diff = different/(scores.length-1);
			for (int k=0; k<scores.length; k++) {
				scopy[i] += (i==k ? same : (diff)) * scores[k];
			}
		}
		for (int i=0; i<scores.length; i++) {
			scores[i] = scopy[i];
		}
	}

}
