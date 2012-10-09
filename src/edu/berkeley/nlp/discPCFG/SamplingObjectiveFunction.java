package edu.berkeley.nlp.discPCFG;

///**
// * 
// */
//package edu.berkeley.nlp.classify;
//
//import java.util.ArrayList;
//import java.util.List;
//
//import edu.berkeley.nlp.PCFGLA.ConstrainedTwoChartsParser;
//import edu.berkeley.nlp.PCFGLA.DoublyConstrainedTwoChartsParser;
//import edu.berkeley.nlp.PCFGLA.Grammar;
//import edu.berkeley.nlp.PCFGLA.Lexicon;
//import edu.berkeley.nlp.PCFGLA.SpanPredictor;
//import edu.berkeley.nlp.PCFGLA.StateSetTreeList;
//import edu.berkeley.nlp.auxv.VectorizedSuffStat;
//import edu.berkeley.nlp.auxv.bractrl.BracketNumberControllers;
//import edu.berkeley.nlp.classify.ParsingObjectiveFunction.Calculator;
//import edu.berkeley.nlp.classify.ParsingObjectiveFunction.Counts;
//import edu.berkeley.nlp.syntax.StateSet;
//import edu.berkeley.nlp.syntax.Tree;
//
///**
// * @author petrov
// *
// */
//public class SamplingObjectiveFunction extends ParsingObjectiveFunction {
//	int maxLengthForExact;
//	
//	//private BracketNumberControllers braNumberControllers;
//
//	/**
//	 * @param linearizer
//	 * @param trainStateSetTrees
//	 * @param sigma
//	 * @param regularize
//	 * @param cons
//	 * @param process
//	 * @param grammarLocation
//	 * @param b
//	 * @param c
//	 */
//	public SamplingObjectiveFunction(Linearizer linearizer, 
//			StateSetTreeList trainStateSetTrees, double sigma, int regularize, 
//			String cons, int process, String grammarLocation, boolean b, boolean c, 
//			int maxLengthForExact) { //, BracketNumberControllers braNumberControllers) {
//		super(linearizer, trainStateSetTrees, sigma, regularize, cons, process, 
//				grammarLocation, b, c);
//		this.maxLengthForExact = maxLengthForExact;
//		//this.braNumberControllers = braNumberControllers;
//	}
//
//	protected Calculator newCalculator(boolean doNotProjectConstraints, int i) {
//		return new Calculator(trainingTrees[i],consBaseName,i, grammar, lexicon, spanPredictor, dimension, doNotProjectConstraints);
//	}
//  
//
//	class Calculator extends ParsingObjectiveFunction.Calculator {
//		SamplingParser sParser;
//		DoublyConstrainedTwoChartsParser dParser;
//
//
//		Calculator(StateSetTreeList myT, String consN, int i, Grammar gr, Lexicon lex, SpanPredictor sp, int dimension, boolean notProject) {
//			super(myT, consN, i, gr, lex, sp, dimension, notProject);
//			sParser = new SamplingParser(gr, lex); //, braNumberControllers);
//			dParser = new DoublyConstrainedTwoChartsParser(gr, lex);
//		}
//
////		protected ConstrainedTwoChartsParser newEParser(Grammar gr, Lexicon lex, SpanPredictor sp) {
////			return 
////		}
//
//		public Counts call() {
//			double myObjective = 0;
//			//				double[] myDerivatives = new double[nCounts];
//			unparsableTrees = 0;
//			incorrectLLTrees = 0;
//			VectorizedSuffStat suffStat = new VectorizedSuffStat(linearizer);
//	  	myDerivatives = new double[dimension];
//
//			if (myConstraints==null) loadConstraints();
//
//			int i = -1;
//			int block = 0;
//			loopOverTrees : for (Tree<StateSet> stateSetTree : myTrees) {
//				i++;
//				List<StateSet> yield = stateSetTree.getYield();
//				List<String> sentence = new ArrayList<String>(yield.size());
//				for (StateSet word : yield) sentence.add(word.getWord());
//				
//				boolean noSmoothing = false /*true*/, debugOutput = false;
//
//				// parse the sentence
//				boolean[][][][] cons = null;
//				if (consName!=null){
//					cons = myConstraints[i];
//					if (cons.length != yield.size()){
//						System.out.println("My ID: "+myID+", block: "+block+", sentence: "+i);
//						System.out.println("Sentence length ("+yield.size()+") and constraints length ("+cons.length+") do not match!");
//						System.exit(-1);
//					}
//				}
//				long start = System.currentTimeMillis();
//				// compute the ll of the gold tree
//				double goldLL = gParser.doInsideOutsideScores(stateSetTree, noSmoothing, debugOutput, null);
//
////				if (i%500==0) 
//					System.out.print(".");
//
//				gParser.incrementExpectedGoldCounts(linearizer, myDerivatives, stateSetTree);
//				long stop = System.currentTimeMillis();
//	    	System.out.println("Time for gold counts: " + (stop -start));
//	    	
//				System.out.println("------------------------------------------");
//				double condLL = 0;
//				start = System.currentTimeMillis();
////    	double allLL = eParser.doConstrainedInsideOutsideScores(yield,cons,noSmoothing,null,null,false);
//				VectorizedSuffStat suffStatExact = suffStat.newInstance();
//				dParser.setConstraints(cons, sentence.size());
//				dParser.compute(sentence, suffStatExact);
//	    	double[] expectedCountsExact = suffStatExact.toArray();
//	    	stop = System.currentTimeMillis();
//	    	System.out.println("Time for exhaustive: " + (stop -start));
////    		new double[linearizer.dimension()];
////    	eParser.incrementExpectedCounts(linearizer, expectedCountsExact, yield); //note those counts will have the wrong sign
//
//				if (sentence.size() <= maxLengthForExact || sParser.initialNBrackets(null, sentence) <= 0) {
//		    	double allLL = eParser.doConstrainedInsideOutsideScores(yield,cons,noSmoothing,null,null,false);
//					if (!sanityCheckLLs(goldLL, allLL, stateSetTree)) {
//						myObjective += -1000;
//						continue loopOverTrees;
//					}
//		    	condLL = (goldLL - allLL);
//		    	eParser.incrementExpectedCounts(linearizer, myDerivatives, yield);
//				} 
//				else {
//					try {
//						suffStat = suffStat.newInstance();
//						sParser.setConstraints(cons, sentence.size());
//						condLL = sParser.computeExpectedCounts(sentence, suffStat, stateSetTree);
//					} catch (MeasureZeroException mze) {
//						unparsableTrees++;
//						myObjective += -1000;
//						continue loopOverTrees;
//					}
//					double[] expectedCountsSampled = suffStat.toArray(); 
//					for (int r=0; r<myDerivatives.length; r++){
//						myDerivatives[r] -= expectedCountsSampled[r];
//					}
//					double cumError = 0, biggestIndividualError = 0;
//					for (int r=0; r<expectedCountsSampled.length; r++){
//						double thisError = Math.abs(expectedCountsSampled[r]-expectedCountsExact[r]);
//					  cumError += thisError; // the + is because the exact counts are negative
//					  if (thisError>biggestIndividualError){
//					  	biggestIndividualError = thisError;
//					  }
//					}
//					System.out.println("Cumulative error in derivative for this sentence (" + yield.size() + "): "+cumError+", biggest individual error: "+biggestIndividualError);
//					
//				}
//				myObjective += condLL;
//			}
//
//			myCounts = new Counts(myObjective,myDerivatives,unparsableTrees,incorrectLLTrees);
//
//			System.out.print(" "+myID+" ");
//			return myCounts;
//		}
//		
//	}
//
//
//
// }
