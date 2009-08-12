/**
 * 
 */
package edu.berkeley.nlp.HMM;

import java.util.List;

/**
 * @author adpauls
 *
 */
public class FrameErrorAccuracy extends AccuracyCalc{

	/**
	 * @param phoneIndexer
	 */
	public FrameErrorAccuracy(PhoneIndexer phoneIndexer, List<List<Phone>> goldSeqs,
			List<List<Phone>> testSeqs) {
		super(phoneIndexer,goldSeqs,testSeqs);
		// TODO Auto-generated constructor stub
	}
	

	
	public double getAccuracy() {
		List<List<Phone>> seqs1 = goldSeqs;
		List<List<Phone>> seqs2 = testSeqs;
		if (seqs1.size() != seqs2.size())
			throw new RuntimeException("Hamming distance on unequal sequences");
		int numErrors = 0;
		int totalNum = 0;
		for (int i = 0; i < seqs1.size(); ++i) {
			int thisError = 0;
			List<Phone> seq1 = seqs1.get(i);
			List<Phone> seq2 = seqs2.get(i);
			if (seq1.size() != seq2.size())
				throw new RuntimeException("Hamming distance on unequal sequences");
			
			for (int j = 1; j < seq1.size() - 1; ++j) {
				boolean match = equal39(seq1.get(j), seq2.get(j));
				
				
				if (!match) {
					numErrors++;
					thisError++;
				}
				totalNum++;
			}
			addAccuracy((double)thisError / seq1.size());

		}
		return (double) numErrors / totalNum;
	}

}
