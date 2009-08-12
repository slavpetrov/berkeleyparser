/**
 * 
 */
package edu.berkeley.nlp.speech.phonedecoder;

import edu.berkeley.nlp.HMM.AcousticModel;

/**
 * 
 * @author John DeNero
 */
public class VariationalDecoder implements PhoneDecoder {

	public static class Factory implements PhoneDecoder.Factory {

		public PhoneDecoder newDecoder(AcousticModel model) {
			return new VariationalDecoder(model);
		}

	}

	private AcousticModel acModel; // Speech-specific HMM

	private SubphoneDecoder substateDecoder;

	/**
	 * @param model
	 * @param phoneIndexer
	 */
	public VariationalDecoder(AcousticModel model) {
		substateDecoder = new ExhaustiveDecoder(model);
		this.acModel = model;
	}

	public double[][] getPhonePosteriors(double[][] seq) {
		double[][] subPhonePosteriors = substateDecoder.getSubphonePosteriors(seq);
		return sumSubphonePosteriors(subPhonePosteriors);
	}

	private double[][] sumSubphonePosteriors(double[][] subPhonePosteriors) {
		SubphoneIndexer subphoneIndexer = substateDecoder.getSubphoneIndexer();
		int numTimes = subPhonePosteriors.length;
		int numPhones = acModel.getPhoneIndexer().size();
		double[][] post = new double[numTimes][numPhones];
		for (int t = 0; t < numTimes; t++) {
			for (int p = 0; p < numPhones; p++) {
				for (int s = 0; s < acModel.getNumStates(p); s++) {
					int pairIndex = subphoneIndexer.indexOf(p, s);
					post[t][p] += subPhonePosteriors[t][pairIndex];
				}
			}
		}
		return post;
	}
}
