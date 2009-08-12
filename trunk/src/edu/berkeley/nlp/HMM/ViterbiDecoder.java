/**
 * 
 */
package edu.berkeley.nlp.HMM;

import java.util.ArrayList;
import java.util.List;

import fig.basic.LogInfo;

/**
 * @author adpauls
 * 
 */
public class ViterbiDecoder implements Decoder {

	private SubphoneHMM hmm;
	private double phoneInsertionPenalty;
	private double emissionAttenuation;

	public ViterbiDecoder(SubphoneHMM hmm, double phoneInsertionPenatly, double emissionAttenuation) {
		this.hmm = hmm;
		this.phoneInsertionPenalty = phoneInsertionPenatly;
		this.emissionAttenuation = emissionAttenuation;
	}

	public List<List<Phone>> decode(List<double[][]> obsSequences) {
		LogInfo.track("viterbiDecode");
		List<List<Phone>> retVal = new ArrayList<List<Phone>>();

		ViterbiChart probChart = new ViterbiChart(hmm, phoneInsertionPenalty, emissionAttenuation);
		int k = 0;
		for (double[][] obsSequence : obsSequences) {
			LogInfo.logs("sequence " + k++);
			probChart.init(obsSequence);

			retVal.add(probChart.calc());

		}
//		System.out.println();
		LogInfo.end_track();
		return retVal;
	}

}
