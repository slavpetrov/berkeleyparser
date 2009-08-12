/**
 * 
 */
package edu.berkeley.nlp.speech.phonedecoder;

import edu.berkeley.nlp.HMM.AcousticModel;

/**
 * 
 * @author John DeNero
 */
public interface SubphoneDecoder {

	public static interface Factory {
		public SubphoneDecoder newDecoder(AcousticModel model);
	}

	/**
	 * Gives posteriors over (indexed) phones for each time step.
	 * 
	 * @param seq
	 * @return
	 */
	double[][] getSubphonePosteriors(double[][] seq);

	SubphoneIndexer getSubphoneIndexer();

}
