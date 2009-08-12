/**
 * 
 */
package edu.berkeley.nlp.HMM;

import java.util.ArrayList;
import java.util.List;

import fig.basic.LogInfo;

/**
 * @author petrov
 *
 */
public class PhoneDecoder implements Decoder {

	private SubphoneHMM hmm;
//	private double emissionAttenuation;
	
	public PhoneDecoder(SubphoneHMM hmm, double emissionAttenuation) {
		this.hmm = hmm;
//		this.emissionAttenuation = emissionAttenuation;
	}

	public List<List<Phone>> decode(List<double[][]> obsSequences) {
		LogInfo.track("phoneDecoder");
		List<List<Phone>> retVal = new ArrayList<List<Phone>>();

		PosteriorProbChart probChart = new PosteriorProbChart(hmm);
		int k = 0;
		for (double[][] obsSequence : obsSequences) {
			LogInfo.logs("sequence" + k++);
			probChart.init(obsSequence);
			probChart.calcAlphas();
			
			double bestScore = 0;
			int bestPhone = -1;
			for (int phone=2; phone<hmm.numPhones; phone++){
				double score = 0;
				for (int substate=0; substate<hmm.numSubstatesPerState[phone]; substate++){
					score += probChart.alphas[probChart.T-2][phone][substate]*hmm.c[1][phone][substate]*hmm.a[0][1][phone][substate];
				}
				if (score>bestScore){
					bestScore = score;
					bestPhone = phone;
				}
			}

			List<Phone> phoneList = new ArrayList<Phone>();
			phoneList.add(Corpus.START_PHONE);
			for (int t = 1; t < probChart.T - 1; t++) {
				phoneList.add(hmm.phoneIndexer.get(bestPhone));
			}
			phoneList.add(Corpus.END_PHONE);
			retVal.add(phoneList);

		}
//		System.out.println();
		LogInfo.end_track();
		return retVal;
	}

}
