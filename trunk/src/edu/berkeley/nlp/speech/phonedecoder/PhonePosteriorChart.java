package edu.berkeley.nlp.speech.phonedecoder;

import edu.berkeley.nlp.HMM.AcousticModel;

/**
 * An interface for sharing posteriors among model passes.
 * 
 * @author John DeNero
 */
public interface PhonePosteriorChart {

	public void setModel(AcousticModel model);

	// obs[time][cepstralDim]
	public void setInput(double[][] obs);

	// posteriors[time][phone][substate]
	public void fillPosteriors(double[][][] posteriors);

	/**
	 * An adapter for phone decoder output to present posteriors.
	 * 
	 * @author John DeNero
	 */
	public class ChartFromPhoneDecoder implements PhonePosteriorChart {
		SubphoneDecoder.Factory decoderFactory;

		SubphoneDecoder decoder;

		double[][] subphonePosteriors;

		int inputLength;

		private AcousticModel model;

		public ChartFromPhoneDecoder(SubphoneDecoder.Factory factory) {
			decoderFactory = factory;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see edu.berkeley.nlp.speech.phonedecoder.PhonePosteriorChart#fillPosteriors(double[][][])
		 */
		public void fillPosteriors(double[][][] posteriors) {
			if (posteriors == null)
				throw new RuntimeException("You must set an input first");
			SubphoneIndexer indexer = decoder.getSubphoneIndexer();
			for (int t = 0; t < inputLength; t++) {
				for (int s = 0; s < indexer.size(); s++) {
					int phone = indexer.getPhone(s), substate = indexer.getSubstate(s);
					posteriors[t][phone][substate] = subphonePosteriors[t][s];
				}
			}
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see edu.berkeley.nlp.speech.phonedecoder.PhonePosteriorChart#setInput(double[][])
		 */
		public void setInput(double[][] obs) {
			if (decoder == null)
				throw new RuntimeException("You must set a model before an input");
			subphonePosteriors = decoder.getSubphonePosteriors(obs);
			inputLength = obs.length;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see edu.berkeley.nlp.speech.phonedecoder.PhonePosteriorChart#setModel(edu.berkeley.nlp.HMM.AcousticModel)
		 */
		public void setModel(AcousticModel model) {
			decoder = decoderFactory.newDecoder(model);
			this.model = model;
		}

		/**
		 * 
		 */
		public double[][][] allocateChart() {
			int numPhones = model.getPhoneIndexer().size();
			double[][][] chart = new double[inputLength][numPhones][];
			for (int t = 0; t < inputLength; t++) {
				for (int phone = 0; phone < numPhones; phone++) {
					chart[t][phone] = new double[model.getNumStates(phone)];
				}
			}
			return chart;
		}
	}

}
