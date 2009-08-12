/**
 * 
 */
package edu.berkeley.nlp.prob;

import java.io.Serializable;

/**
 * @author adpauls
 *
 */
public interface GaussianSuffStats extends Serializable {
	
	void add(double[] x, double weight);
	void add(GaussianSuffStats stats);
	Gaussian estimate();
	/**
	 * @return
	 */
	GaussianSuffStats clone();

}
