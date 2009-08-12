///**
// * 
// */
//package edu.berkeley.nlp.speech.phonedecoder;
//
//import java.util.Arrays;
//
//import edu.berkeley.nlp.HMM.AcousticModel;
//import edu.berkeley.nlp.sequence.StationarySequenceInstance;
//
///**
// * Wrapper/Converter for a subphone sequence HMM to an undirected markov chain.
// * 
// * @author John DeNero
// */
//public class AcousticSequenceInstance implements StationarySequenceInstance {
//
//	private double[][] seq;
//
//	private AcousticModel model;
//
//	private SubphoneIndexer indexer;
//
//	/**
//	 * @param seq2
//	 * @param acModel
//	 * @param subphoneIndexer
//	 * @param model
//	 *          the HMM model.
//	 * @param seq
//	 *          the obvervation sequence.
//	 */
//	public AcousticSequenceInstance(AcousticModel acModel,
//			SubphoneIndexer subphoneIndexer, double[][] seq) {
//		this.model = acModel;
//		this.indexer = subphoneIndexer;
//		this.seq = seq;
//	}
//
//	public void fillEdgePotentials(double[][] potentials) {
//		int numSubPhones = indexer.size();
//		for (int subPh = 0; subPh < numSubPhones; subPh++) {
//			int phone = indexer.getPhone(subPh), sub = indexer.getSubstate(subPh);
//			for (int next = 0; next < numSubPhones; next++) {
//				int phone2 = indexer.getPhone(next), sub2 = indexer.getSubstate(next);
//				double score = model.getTransitionScore(phone, sub, phone2, sub2);
//				potentials[subPh][next] = score;
//			}
//		}
//	}
//
//	public int getSequenceLength() {
//		return seq.length; // Space for start, syllables and end
//	}
//
//	// ////////////////////////// 
//	// Node potential methods //
//	// //////////////////////////
//
//	public void fillNodePotentials(double[][] potentials) {
//		fillStartTime(potentials[0]);
//		for (int t = 1; t + 1 < seq.length; t++) {
//			fillTime(potentials[t], seq[t]);
//		}
//		fillEndTime(potentials[seq.length - 1]);
//	}
//
//	private void fillStartTime(double[] potentials) {
//		int start = indexer.getStartStateIndex();
//		Arrays.fill(potentials, 0.0);
//		potentials[start] = 1.0;
//	}
//
//	private void fillTime(double[] potentials, double[] signal) {
//		for (int i = 0; i < potentials.length; i++) {
//			if (indexer.getSpecialStates().contains(i)) {
//				potentials[i] = 0.0;
//			} else {
//				// Potential is the observation probability
//				int phone = indexer.getPhone(i);
//				int substate = indexer.getSubstate(i);
//				double prob = model.getObservationScore(phone, substate, signal);
//				potentials[i] = Math.exp(prob);
//			}
//		}
//	}
//
//	private void fillEndTime(double[] potentials) {
//		int end = indexer.getEndStateIndex();
//		Arrays.fill(potentials, 0.0);
//		potentials[end] = 1.0;
//	}
//
//
//}
