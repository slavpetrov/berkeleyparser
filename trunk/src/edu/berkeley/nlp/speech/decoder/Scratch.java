/**
 *
 */
package edu.berkeley.nlp.speech.decoder;

import edu.berkeley.nlp.HMM.AcousticModel;

/**
 * @author aria42
 *
 */
public class Scratch {

	AcousticModel acousticModel ;
	
	/**
	 *
	 * @param obs each row is a cepstral vector for a time step
	 */
	public void decode(double[][] obs) {
		// Num TimeSteps
		int T = obs.length;
		// Dimension of Cepstral Vector 
		int K = obs[0].length;
	}
}
