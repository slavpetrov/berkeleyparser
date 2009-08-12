/**
 * 
 */
package edu.berkeley.nlp.HMM;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


import edu.berkeley.nlp.PCFGLA.OptionParser;
import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.PriorityQueue;

/**
 * @author adpauls
 *
 */
public class SubstateStatistics {
	


//	public static void main(String[] argv) {
//	
//		OptionParser optionParser = new OptionParser(TimitTester.Options.class);
//		TimitTester.Options opts = (TimitTester.Options)optionParser.parse(argv,true,false);
//		System.out.println("Calling with " + optionParser.getPassedInOptions());
//		opts.numTrain = opts.numTrain < 0 ? Integer.MAX_VALUE :opts.numTrain;
//		opts.numDecode = opts.numDecode < 0 ? Integer.MAX_VALUE : opts.numDecode;
//		
//		// Corpus corpus = new Corpus("/Users/petrov/Data/timit");
//		boolean dummy = opts.corpusType == Corpus.CorpusType.DUMMY;
//		Corpus corpus = new Corpus(dummy ? null
//				: opts.corpusPath.getPath(), opts.corpusType, false, false);
//		System.out.println("done loading");
//		List<List<Phone>> phoneSequencesAsObjects = corpus.getPhoneSequencesTrain();
//		if (opts.collapseTimit) phoneSequencesAsObjects = PhoneIndexer.getCollapsedPhoneLists(phoneSequencesAsObjects,true);
//		List<int[]> phoneSequences = corpus.getPhoneIndexer().indexSequences(phoneSequencesAsObjects);
//		List<double[][]> obsList = corpus.getObsListsTrain();
//		PhoneIndexer phoneIndexer = corpus.getPhoneIndexer();
//		int gaussianDim = corpus.getGaussianDim();
//		
//		System.out.println("Loaded "+phoneSequences.size()+" training sequences with dimension "+gaussianDim+".");
//		System.out.println("There are "+phoneIndexer.size()+" phones.");
//	
//		SubphoneHMM hmm = new SubphoneHMM(phoneIndexer, gaussianDim, opts.randSeed, opts.fullCovariance, opts.transitionSmooth, opts.meanSmooth,opts.varSmooth,opts.minVarSmooth,opts.emissionAttenuation);
//		int lastElem = Math.min(phoneSequences.size() , opts.numTrain);
//
//		
//		if (opts.numSubstates!=0){
//			System.out.println("Doing flat training.");
//			hmm.initializeModelFromStateSequence(phoneSequences, obsList, opts.numSubstates, opts.randomness);
//			if (dummy)
//			  hmm.train(phoneSequences, obsList, 10);
//			else
//				hmm.train(phoneSequences.subList(0,lastElem), obsList.subList(0,lastElem), opts.numIter);
//		}
//		else{
//			hmm.initializeModelFromStateSequence(phoneSequences, obsList, 1, 0);
//
//			int numSplits = opts.numSplits;
//			System.out.println("Will do "+numSplits+" split iterations.");
//
//			for (int split=0; split<numSplits; split++){
//				System.out.println("In split iteration "+split+".");
//				hmm.splitModelInTwo(opts.randomness,null);
//				if (dummy)
//				  hmm.train(phoneSequences, obsList, 10);
//				else
//					hmm.train(phoneSequences.subList(0,lastElem), obsList.subList(0,lastElem), opts.numIter);
//				
//				if (opts.mergePercent>0){
//					hmm.mergeModel(phoneSequences.subList(0,lastElem), obsList.subList(0,lastElem), opts.mergePercent, opts.mergingType, opts.mergeThreshType);
//					if (dummy)
//					  hmm.train(phoneSequences, obsList, 10/2);
//					else
//						hmm.train(phoneSequences.subList(0,lastElem), obsList.subList(0,lastElem), 10 /*opts.numIter/2*/);
//				}
//				hmm.printNumberOfSubstates();
//			}				
//			
//			
//		}
//		System.out.println("Decoding training sequences");
//		ViterbiDecoder viterbiDecoder = new ViterbiDecoder(hmm, opts.phoneInsertionPenatly,opts.emissionAttenuation);
//		List<List<Phone>> viterbiTrainSequence = viterbiDecoder.decode(obsList.subList(0,lastElem));
//		Map<AnnotatedPhone, Counter<AnnotatedPhone>> rightSelfStates = new HashMap<AnnotatedPhone, Counter<AnnotatedPhone>>();
//		Map<AnnotatedPhone, Counter<AnnotatedPhone>> leftSelfStates = new HashMap<AnnotatedPhone, Counter<AnnotatedPhone>>();
//		Map<AnnotatedPhone, Counter<AnnotatedPhone>> rightOtherStates = new HashMap<AnnotatedPhone, Counter<AnnotatedPhone>>();
//		Map<AnnotatedPhone, Counter<AnnotatedPhone>> leftOtherStates = new HashMap<AnnotatedPhone, Counter<AnnotatedPhone>>();
//		for (List<Phone> seq : viterbiTrainSequence)
//		{
//			for (int i = 0; i < seq.size(); ++i)
//			{
//				AnnotatedPhone curr = (AnnotatedPhone)seq.get(i);
//				if (!rightSelfStates.containsKey(curr)) rightSelfStates.put(curr,new Counter<AnnotatedPhone>());
//				if (!leftSelfStates.containsKey(curr)) leftSelfStates.put(curr,new Counter<AnnotatedPhone>());
//				if (!rightOtherStates.containsKey(curr)) rightOtherStates.put(curr,new Counter<AnnotatedPhone>());
//				if (!leftOtherStates.containsKey(curr)) leftOtherStates.put(curr,new Counter<AnnotatedPhone>());
//				
//				AnnotatedPhone rightSelfState = null;
//				AnnotatedPhone rightOtherState = null;
//				int right = i+1;
//				while (right+1 < seq.size() && rightOtherState == null)
//				{
//					if (rightSelfState == null)
//					{
//						rightSelfState =  (AnnotatedPhone)seq.get(right);
//					}
//					if (!curr.unnannotatedEquals(seq.get(right)))
//					{
//						rightOtherState = (AnnotatedPhone)seq.get(right);
//					}
//					right++;
//				}
//				rightSelfStates.get(curr).incrementCount(rightSelfState,1.0);
//				rightOtherStates.get(curr).incrementCount(rightOtherState,1.0);
//
//				AnnotatedPhone leftSelfState = null;
//				
//				AnnotatedPhone leftOtherState = null;
//				int left = i-1;
//				while (left >= 0 && leftOtherState == null)
//				{
//					if (leftSelfState == null)
//					{
//						leftSelfState =  (AnnotatedPhone)seq.get(left);
//					}
//					if (!curr.unnannotatedEquals(seq.get(left)))
//					{
//						leftOtherState = (AnnotatedPhone)seq.get(left);
//					}
//					left--;
//				}
//				leftSelfStates.get(curr).incrementCount(leftSelfState,1.0);
//				leftOtherStates.get(curr).incrementCount(leftOtherState,1.0);
//			}
//		}
//		printStats(rightSelfStates, "Right self states ");
//		printStats(rightOtherStates, "Right other states ");
//		printStats(leftSelfStates, "Left self states ");
//		printStats(leftOtherStates, "Left other states ");
//		
//		
//		
////		}
////		List<double[][]> obsListsDev = dummy ? obsList : corpus.getObsListsDev();
////		List<List<Phone>> actualPhoneSequences = dummy ? phoneSequencesAsObjects : corpus.getPhoneSequencesDev();
////		if (opts.collapseTimit) actualPhoneSequences = PhoneIndexer.collapsePhoneLists(actualPhoneSequences);
////		int lastDecodeElem = Math.min(actualPhoneSequences.size() , opts.numDecode);
////		
////		List<List<Phone>> viterbiDevSequence = null;
////		if (opts.doViterbi)
////		{
////			System.out.println("Starting viterbi decoding");
////			viterbiDevSequence = hmm.viterbiDecode(obsListsDev.subList(0,
////					lastDecodeElem));
////		}
////		System.out.println("Starting posterior sum decoding");
////		List<List<Phone>> posteriorSumoutDevSequence = hmm.posteriorSumoutDecode(obsListsDev.subList(0,lastDecodeElem));
////
////		System.out.println();
////	
////		
////		FrameErrorAccuracy frameErrorCalc = new FrameErrorAccuracy(corpus.getPhoneIndexer());
////		
////		List<List<Phone>> goldSeqs = actualPhoneSequences.subList(0,lastDecodeElem);
////		List<List<Phone>> posteriorDevSeqs = posteriorSumoutDevSequence.subList(0,lastDecodeElem);
////		double posteriorError = (!opts.collapseTimit) ? frameErrorCalc.getAccuracy(goldSeqs, posteriorDevSeqs) : -1.0;
////		List<List<Phone>> viterbiDevSeqs = opts.doViterbi ? viterbiDevSequence.subList(0,lastDecodeElem) : null;
////		double viterbiError = (opts.doViterbi && !opts.collapseTimit) ? frameErrorCalc.getAccuracy(goldSeqs, viterbiDevSeqs) : -1.0;
////	
////		EditDistanceAccuracy editDistanceAccuracy = new EditDistanceAccuracy(corpus.getPhoneIndexer());
////		double viterbiEditDistance = (opts.doViterbi  && (opts.doEditDistance || opts.collapseTimit)) ? editDistanceAccuracy.getAccuracy(goldSeqs,viterbiDevSeqs) : -1.0;
////		double posteriorEditDistance = (opts.doEditDistance || opts.collapseTimit) ? editDistanceAccuracy.getAccuracy(goldSeqs,posteriorDevSeqs) : -1.0;;
////		
////		for (int i = 0; i < lastDecodeElem; ++i )
////		{
////		
////			
////			System.out.print("SPosterior:\t");
////			AccuracyCalc.printSeq(posteriorDevSeqs.get(i), goldSeqs.get(i), phoneIndexer);
////			if (opts.doViterbi)
////			{
////				System.out.print("Viterbi:\t");
////				AccuracyCalc.printSeq(viterbiDevSeqs.get(i), goldSeqs.get(i), phoneIndexer);
////			}
////			System.out.print("Actual:  \t");
////			AccuracyCalc.printSeq(goldSeqs.get(i), goldSeqs.get(i), phoneIndexer);
////		}
//////			for (int j = 0; j < actualPhoneSequences.get(i).length; ++j) 
//////			{
//////				if (j >0 && j < actualPhoneSequences.get(i).length -1)// skip first and last one (START and END tokens
//////				{
//////					//posteriorNumCorrect += (actualPhoneSequences.get(i)[j] == phoneIndexer.indexOf(posteriorDevSequence.get(i).get(j).getUnnannotatedPhone())) ? 1 : 0;
////////					posteriorNumCorrect += (actualPhoneSequences.get(i)[j] == phoneIndexer.indexOf(posteriorDevSequence.get(i).get(j))) ? 1 : 0;
////////					posteriorSumoutNumCorrect += (actualPhoneSequences.get(i)[j] == phoneIndexer.indexOf(posteriorSumoutDevSequence.get(i).get(j))) ? 1 : 0;
////////					viterbiNumCorrect += (actualPhoneSequences.get(i)[j] == phoneIndexer.indexOf(viterbiDevSequence.get(i).get(j).getUnnannotatedPhone())) ? 1 : 0;
//////					posteriorNumCorrect += (actualPhoneSequences.get(i)[j] == corpus.mapping[phoneIndexer.indexOf(posteriorDevSequence.get(i).get(j))-2]+1) ? 1 : 0;
//////					posteriorSumoutNumCorrect += (actualPhoneSequences.get(i)[j] == corpus.mapping[phoneIndexer.indexOf(posteriorSumoutDevSequence.get(i).get(j))-2]+1) ? 1 : 0;
//////					viterbiNumCorrect += (actualPhoneSequences.get(i)[j] == corpus.mapping[phoneIndexer.indexOf(viterbiDevSequence.get(i).get(j).getUnnannotatedPhone())-2]+1) ? 1 : 0;
//////				}
//////				System.out.print(" "+ phoneIndexer.get(actualPhoneSequences.get(i)[j]) + ",");
//////			}
//////			System.out.println();
//////			System.out.println("Posterior Accuracy:\t" + (double)posteriorNumCorrect / (actualPhoneSequences.get(i).length - 2) + 
//////												 "\tViterbi Accuracy:\t" + (double)viterbiNumCorrect / (actualPhoneSequences.get(i).length - 2) + 
//////												 "\tSPosterior Accuracy:\t" + (double)posteriorSumoutNumCorrect / (actualPhoneSequences.get(i).length - 2));
//////												 
//////			System.out.println("\n");
//////			totalNum += (actualPhoneSequences.get(i).length - 2);
//////			totalPosteriorNumCorrect += posteriorNumCorrect;
//////			totalPosteriorSumoutNumCorrect += posteriorSumoutNumCorrect;
//////			totalViterbiNumCorrect += viterbiNumCorrect;
//////		}
////
////		System.out.println("Total Posterior Error:\t" +posteriorError);
////		if (opts.doEditDistance) System.out.println("Total Posterior Edit Distance:\t" + posteriorEditDistance);
////		
////		if (opts.doViterbi)
////		{
////			System.out.println("Total Viterbi Error:\t" + viterbiError);
////			if (opts.doEditDistance)  System.out.println("Total Viterbi Edit Distance:\t" + viterbiEditDistance);
////		}
////		// System.out.println(hmm);
////	
//	}
//
//	private static void printStats(Map<AnnotatedPhone, Counter<AnnotatedPhone>> states, String text) {
//		for (AnnotatedPhone p : states.keySet())
//		{
//			System.out.print(text + " for " + p + "\t:");
//			PriorityQueue<AnnotatedPhone> rightSelfQueue = states.get(p).asPriorityQueue();
//			for (int i = 0; i < 5; ++i)
//			{
//				if (!rightSelfQueue.hasNext()) break;
//				AnnotatedPhone other = rightSelfQueue.next();
//				System.out.print(other + ",");
//			}
//			System.out.println();
//
//		}
//	}
//	

}
