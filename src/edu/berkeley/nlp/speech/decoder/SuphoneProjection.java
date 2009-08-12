/**
 * 
 */
package edu.berkeley.nlp.speech.decoder;

import edu.berkeley.nlp.HMM.AcousticModel;

/**
 * @author aria42
 *
 */
public class SuphoneProjection {
	
	public SuphoneProjection(AcousticModel acousticModel) {
		
	}
	
	public void setInput(double[][] obs, AcousticModel[] models) {
		for (int i=0; i < models.length; ++i) {
			assert models[i].getMaxNumberOfSubstates() == (1 << i);
		}
		
	}
	
	public double[][][] getPosteriors() { 
		return null;
	}

}
