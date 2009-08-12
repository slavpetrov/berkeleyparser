/**
 * 
 */
package edu.berkeley.nlp.speech.phonedecoder;

import java.util.List;

import edu.berkeley.nlp.HMM.AcousticModel;
import edu.berkeley.nlp.sequence.SequenceModel;
import fig.basic.NumUtils;

/**
 * A wrapper for an acoustic model to interface with the util F-B code.
 * 
 * @author John DeNero
 */
class SubphoneSequenceModel implements SequenceModel {

	private static final int MAX_SEQUENCE_LENGTH = 5000;

	private AcousticModel model;

	private int numLabels;

	private int[][] allTransitions;

	private SubphoneIndexer indexer;

	private int numSpecialLabels;

	public SubphoneSequenceModel(AcousticModel model,
			SubphoneIndexer subphoneIndexer) {
		this.model = model;
		this.numLabels = subphoneIndexer.size();
		this.indexer = subphoneIndexer;
		numSpecialLabels = indexer.getSpecialStates().size();
	}

	public int[][] getAllowableBackwardTransitions() {
		int[][] backward = null; //NumUtils.copy(getAllTransitions());
		for (int s = 0; s < numLabels; s++) {
			if (s == model.getStartPhoneIndex()) {
				backward[s] = new int[0];
			} else {
				backward[s][numLabels - numSpecialLabels] = model.getStartPhoneIndex();
			}
		}
		return backward;
	}

	public int[][] getAllowableForwardTransitions() {
		int[][] forward = null; //NumUtils.copy(getAllTransitions());
		for (int s = 0; s < numLabels; s++) {
			if (s == model.getEndPhoneIndex()) {
				forward[s] = new int[0];
			} else {
				forward[s][numLabels - numSpecialLabels] = model.getEndPhoneIndex();
			}
		}
		return forward;
	}

	/**
	 * Fills all transitions except to start and end
	 */
	private int[][] getAllTransitions() {
		if (allTransitions != null)
			return allTransitions;

		allTransitions = new int[numLabels][numLabels - numSpecialLabels + 1];
		List<Integer> skipstates = indexer.getSpecialStates();
		for (int i = 0; i < numLabels; i++) {
			int pos = 0;
			for (int j = 0; j < numLabels; j++) {
				if (skipstates.contains(j))
					continue;
				allTransitions[i][pos++] = j;
			}
		}
		return allTransitions;
	}

	public int getMaximumSequenceLength() {
		return MAX_SEQUENCE_LENGTH;
	}

	public int getNumLabels() {
		return numLabels;
	}

}
