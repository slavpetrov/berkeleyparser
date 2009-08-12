/**
 * 
 */
package edu.berkeley.nlp.speech.phonedecoder;

import edu.berkeley.nlp.HMM.AcousticModel;

/**
 * 
 * @author John DeNero
 */
public interface PhoneDecoder {

	public static interface Factory {
		public PhoneDecoder newDecoder(AcousticModel model);
	}

	/**
	 * Gives posteriors over (indexed) phones for each time step.
	 * 
	 * @param seq
	 * @return
	 */
	double[][] getPhonePosteriors(double[][] seq);

}
