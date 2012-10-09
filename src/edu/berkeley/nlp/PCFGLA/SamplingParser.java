///**
// * 
// */
//package edu.berkeley.nlp.PCFGLA;
//
//import java.util.List;
//import java.util.Random;
//
//import edu.berkeley.nlp.auxv.AuxVarInsideOutside;
//import edu.berkeley.nlp.auxv.SuffStat;
//import edu.berkeley.nlp.auxv.AuxVarInsideOutside.BottleNeckTimerMonitor;
//import edu.berkeley.nlp.auxv.AuxVarInsideOutside.Choke;
//import edu.berkeley.nlp.auxv.AuxVarInsideOutside.IterManager;
//import edu.berkeley.nlp.auxv.bractrl.BracketNumberController;
//import edu.berkeley.nlp.auxv.bractrl.BracketNumberControllers;
//import edu.berkeley.nlp.auxv.bractrl.BracketsFromGoldTree;
//import edu.berkeley.nlp.auxv.bractrl.InformedBracketProposer;
//import edu.berkeley.nlp.syntax.StateSet;
//import edu.berkeley.nlp.syntax.Tree;
//import edu.berkeley.nlp.math.SloppyMath;
//import fig.basic.Option;
//
///**
// * @author petrov
// *
// */
//public class SamplingParser extends DoublyConstrainedTwoChartsParser {
//
//	AuxVarInsideOutside auxVarInsideOutside;
//	BracketNumberController bnc;
//	
//
//	// TODO: should be make non-static
//	@Option public static double ratioItersToBras = 9.0;
//	@Option public static double ratioBurnInToIters = 0.2; 
//	@Option public static double minActiveRatio = 0.5;
//	
//
//	Random rand = GrammarTrainer.RANDOM;
//
//	public SamplingParser(Grammar gr, Lexicon lex) { 
//		super(gr, lex);
//		bnc = BracketNumberControllers.instance.newBracketNumberController();
//	}
//	
//	public int initialNBrackets(Random rand, List<String> sentence)
//	{
//		return bnc.initialNumberOfBrackets(rand, sentence);
//	}
//	
//	/**
//	 * Throws MeasureZeroException if on measure zero
//	 * in this case, suff stat are left untouched
//	 * @param sentence
//	 * @param finalSuffStats
//	 * @param goldTree
//	 * @return
//	 */
//	public double computeExpectedCounts(List<String> sentence, SuffStat finalSuffStats, Tree<StateSet> goldTree){
//		
//    BracketsFromGoldTree prop = new BracketsFromGoldTree(goldTree);
//    IterManager iterManager = new IterManager(initialNBrackets(rand, sentence), 
//    		ratioItersToBras, ratioBurnInToIters);
//		auxVarInsideOutside = new AuxVarInsideOutside(this, prop, 
//        bnc, iterManager, rand);
//		Choke choke = new Choke(minActiveRatio);
//		
//		BottleNeckTimerMonitor bnt = new AuxVarInsideOutside.BottleNeckTimerMonitor();
//		auxVarInsideOutside.setMonitor(bnt);
//		
//		SuffStat tempSuffStat = finalSuffStats.newInstance();
//		long start = System.currentTimeMillis();
//		auxVarInsideOutside.compute(sentence, tempSuffStat, goldTree, choke);
//		long stop = System.currentTimeMillis();
//		System.out.println("Time for approx:" + (stop - start));
//		System.out.println("Bottleneck exact time: " + bnt.totalTimeCallingExact);
//		System.out.println("Longest iteration time: " + bnt.maxTimeCallingExact);
//		System.out.println("Shortest iteration time: " + bnt.minTimeCallingExact);
//		double posteriorDerivationLogPr = auxVarInsideOutside.posteriorDerivationLogPr();
//		if (SloppyMath.isVeryDangerous(posteriorDerivationLogPr)) //posteriorDerivationLogPr == Double.NEGATIVE_INFINITY)
//			throw new MeasureZeroException();
//		finalSuffStats.add(tempSuffStat);
//		System.out.println("Current cheat ratio: " + choke.cheatRatio());
//		return posteriorDerivationLogPr;
//	}
//	
//	public static class MeasureZeroException extends RuntimeException {
//		private static final long serialVersionUID = 1L;
//	}
// }
