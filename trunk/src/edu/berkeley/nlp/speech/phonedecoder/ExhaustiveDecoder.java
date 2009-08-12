/**
 * 
 */
package edu.berkeley.nlp.speech.phonedecoder;

import edu.berkeley.nlp.HMM.AcousticModel;
//import edu.berkeley.nlp.sequence.StationaryForwardBackward;
//import edu.berkeley.nlp.sequence.StationarySequenceInstance;

/**
 * 
 * @author John DeNero
 */
public class ExhaustiveDecoder implements SubphoneDecoder {

	/**
	 * 
	 * @author John DeNero
	 */
	public static class Factory implements SubphoneDecoder.Factory {

		public SubphoneDecoder newDecoder(AcousticModel model) {
			return new ExhaustiveDecoder(model);
		}

	}

	SubphoneIndexer subphoneIndexer; // States for generic model

	private SubphoneSequenceModel seqModel; // Generic model for inference

	private AcousticModel acModel; // Speech-specific HMM

	/**
	 * @param model
	 * @param phoneIndexer
	 */
	public ExhaustiveDecoder(AcousticModel model) {
		subphoneIndexer = new SubphoneIndexer(model);
		this.acModel = model;
		this.seqModel = new SubphoneSequenceModel(acModel, subphoneIndexer);
	}

	/**
	 * Compute posteriors over subphone states
	 */
	public double[][] getSubphonePosteriors(double[][] seq) {
//		StationarySequenceInstance obs;
//		obs = new AcousticSequenceInstance(acModel, subphoneIndexer, seq);
//		StationaryForwardBackward fb = new StationaryForwardBackward(seqModel);
//		fb.setInput(obs);
//		return fb.getNodeMarginals();
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.speech.phonedecoder.PhoneDecoder#getSubphoneIndexer()
	 */
	public SubphoneIndexer getSubphoneIndexer() {
		// TODO this should return an indexer with all substates as 0
		return subphoneIndexer;
	}
}
