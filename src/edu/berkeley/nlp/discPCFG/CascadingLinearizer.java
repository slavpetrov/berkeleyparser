package edu.berkeley.nlp.discPCFG;

///**
// * 
// */
//package edu.berkeley.nlp.classify;
//
//import java.io.Serializable;
//
//import edu.berkeley.nlp.PCFGLA.BinaryRule;
//import edu.berkeley.nlp.PCFGLA.Grammar;
//import edu.berkeley.nlp.PCFGLA.Rule;
//import edu.berkeley.nlp.PCFGLA.SimpleLexicon;
//import edu.berkeley.nlp.PCFGLA.UnaryRule;
//import edu.berkeley.nlp.syntax.StateSet;
//import edu.berkeley.nlp.math.ArrayMath;
//import edu.berkeley.nlp.math.DoubleArrays;
//import edu.berkeley.nlp.math.SloppyMath;
//import edu.berkeley.nlp.util.ArrayUtil;
//
///**
// * @author petrov
// *
// */
//public class CascadingLinearizer implements Linearizer, Serializable{
//	private static final long serialVersionUID = 1L;
//	Grammar grammar, oldGrammar;
//	SimpleLexicon lexicon, oldLexicon;
//	DefaultLinearizer linearizer, oldLinearizer;
//	int dimension, lexiconOffset;
//	int nSubstates;
//	
//	/**
//	 * @param grammar
//	 * @param oldGrammar
//	 * @param lexicon
//	 * @param simpleLexicon
//	 */
//	public CascadingLinearizer(Grammar grammar, Grammar oldGrammar, SimpleLexicon lexicon, SimpleLexicon oldLexicon) {
//		this.grammar = grammar;
//		this.oldGrammar = oldGrammar;
//		this.lexicon = lexicon;
//		this.oldLexicon = oldLexicon;
//		this.linearizer = new DefaultLinearizer(grammar, lexicon);
//		this.oldLinearizer = new DefaultLinearizer(oldGrammar, oldLexicon);
//		this.dimension = -1;
//		this.lexiconOffset = -1;
//		this.nSubstates = DoubleArrays.max(grammar.numSubStates);
//	}
//	
//	public int dimension() {
//		if (dimension==-1){
//			lexiconOffset = getLinearizedGrammar().length;
//			dimension = lexiconOffset + getLinearizedLexicon().length;
//		}
//		return dimension;
//	}
//  
//	public int getLexiconOffset(){
//		if (lexiconOffset==-1)
//			lexiconOffset = getLinearizedGrammar().length;
//		return lexiconOffset;
//	}
//
//
//	public Grammar delinearizeGrammar(double[] probs) {
//		int nDangerous = 0;
//		for (BinaryRule bRule : grammar.binaryRuleMap.keySet()){
//			int ind = linearizer.startIndex[linearizer.ruleIndexer.indexOf(bRule)];
//			double[][][] scores = bRule.getScores2();
//			double[][][] oldScores = oldGrammar.getBinaryScore(bRule);
//			for (int j=0; j<scores.length; j++){
//				for (int k=0; k<scores[j].length; k++){
//					if (scores[j][k]!=null){	
//						for (int l=0; l<scores[j][k].length; l++){
//							double val = Math.exp(probs[ind++]) * oldScores[j/2][k/2][l/2];
//							if (linearizer.toBeIgnoredGrammar[ind-1]) val=0;
//		  				if (val<linearizer.threshold) val = 0; // zero out small values
////			  				else if (val>1000){//Double.POSITIVE_INFINITY) {
////			  					System.out.println("POS INF");
////			  					val = 1000;//Double.MAX_VALUE; // prevent overflow
////			  				}
//		  				else if (SloppyMath.isVeryDangerous(val)) {
////		  					System.out.println("dangerous value when delinearizng grammar, binary "+ val);
////			  					val=Double.MAX_VALUE; // shouldn't happen but just in case
////		  					val = 0;
//		  					nDangerous++;
//	  					continue;
//		  				}
//							scores[j][k][l] = val;
//						}
//					}
//				}
//			}
//		}
//		if (nDangerous>0) System.out.println("Left "+nDangerous+" binary rule weights unchanged since the proposed weight was dangerous.");
//
////			UnaryRule[] unaries = this.getClosedSumUnaryRulesByParent(state);
////			for (int r = 0; r < unaries.length; r++) {
////				UnaryRule uRule = unaries[r];
//		nDangerous = 0;
//		for (UnaryRule uRule : grammar.unaryRuleMap.keySet()){
//			int ind = linearizer.startIndex[linearizer.ruleIndexer.indexOf(uRule)];
//			if (uRule.childState==uRule.parentState) continue;
//			double[][] scores = uRule.getScores2();
//			double[][] oldScores = oldGrammar.getUnaryScore(uRule);
//			for (int j=0; j<scores.length; j++){
//				if (scores[j]!=null){	
//					for (int k=0; k<scores[j].length; k++){
//						double val = Math.exp(probs[ind++]) * oldScores[j/2][k/2]; //probs[ind++]
//						if (linearizer.toBeIgnoredGrammar[ind-1]) val=0;
//						if (val<linearizer.threshold) val = 0; // zero out small values
////		  				else if (val>1000){//==Double.POSITIVE_INFINITY){
////		  					System.out.println("POS INF");
////		  					val = 1000;//Double.MAX_VALUE; // prevent overflow
////		  				}
//	  				else if (SloppyMath.isVeryDangerous(val)) {
////	  					System.out.println("dangerous value when delinearizng grammar, unary "+val);
////		  					val=Double.MAX_VALUE; // shouldn't happen but just in case
////	  					val = 0;
//	  					nDangerous++;
//	  					continue;
//	  				}
//						scores[j][k] = val;
//					}
//				}
//			}
//		}
//		if (nDangerous>0) System.out.println("Left "+nDangerous+" unary rule weights unchanged since the proposed weight was dangerous.");
//
//		
//		grammar.closedSumRulesWithParent = grammar.closedViterbiRulesWithParent = grammar.unaryRulesWithParent;
//		grammar.closedSumRulesWithChild = grammar.closedViterbiRulesWithChild = grammar.unaryRulesWithC;
////		computePairsOfUnaries();
//		grammar.makeCRArrays();
//		return grammar;
//	}
//	
//
//	public SimpleLexicon delinearizeLexicon(double[] logProbs) {
//		int nDangerous = 0;
//		for (short tag=0; tag<lexicon.expectedCounts.length; tag++){
//  		for (int word=0; word<lexicon.expectedCounts[tag][0].length; word++){
//  			int index = linearizer.linearIndex[tag][word];
//  			for (int substate=0; substate<lexicon.numSubStates[tag]; substate++){
//  				double val = Math.exp(logProbs[index++]) * oldLexicon.scores[tag][substate/2][word];
//					if (linearizer.toBeIgnoredLexicon[index-1]) val=0;
//  				if (val<linearizer.threshold) val = 0; // zero out small values
////  				else if (val>1000){//==Double.POSITIVE_INFINITY) {
////  					val = 1000;//Double.MAX_VALUE; // prevent overflow
////  				}
//  				else if (SloppyMath.isVeryDangerous(val)) {
////					System.out.println("dangerous value when delinearizng lexicon "+val);
////					System.out.println("Word "+tagWordIndexer[tag].get(0)+" tag "+tag);
////  					val=Double.MAX_VALUE; // shouldn't happen but just in case
////						val = 0;
//  					nDangerous++;
//						continue;
//  				}
//  				lexicon.scores[tag][substate][word] = val;
//  			}
//  		}
//  	}  	
////		System.out.println(lexicon);
//
//		if (nDangerous>0) System.out.println("Left "+nDangerous+" lexicon weights unchanged since the proposed weight was dangerous.");
//		return lexicon;
//
//	}
//
//	public int getLinearIndex(Rule rule) {
//		return linearizer.getLinearIndex(rule);
//	}
//
//
//	public int getLinearIndex(String word, int tag){
//		return getLinearIndex(lexicon.wordIndexer.indexOf(word), tag);
//	}
//
//	public int getLinearIndex(int globalWordIndex, int tag) {
//		return linearizer.getLinearIndex(globalWordIndex, tag);
//	}
//
//	public double[] getLinearizedGrammar() {
//		double[] logProbs = linearizer.getLinearizedGrammar();
//		
//		for (BinaryRule bRule : grammar.binaryRuleMap.keySet()){
//			int ind = linearizer.startIndex[linearizer.ruleIndexer.indexOf(bRule)];
//			double[][][] scores = bRule.getScores2();
//			double[][][] oldScores = oldGrammar.getBinaryScore(bRule);
//			for (int j=0; j<scores.length; j++){
//				for (int k=0; k<scores[j].length; k++){
//					if (scores[j][k]!=null){	
//						for (int l=0; l<scores[j][k].length; l++){
//							double oldVal = Math.log(oldScores[j/2][k/2][l/2]);
////							if (val==Double.NEGATIVE_INFINITY) {
////								toBeIgnored[ind] = true;
////								val=Double.MIN_VALUE;
////							}
//							logProbs[ind++] -= oldVal;
//						}
//					}
//				}
//			}
//		}
//
//		for (UnaryRule uRule : grammar.unaryRuleMap.keySet()){
//			int ind = linearizer.startIndex[linearizer.ruleIndexer.indexOf(uRule)];
//			if (uRule.childState==uRule.parentState) continue;
//			double[][] scores = uRule.getScores2();
//			double[][] oldScores = oldGrammar.getUnaryScore(uRule);
//			for (int j=0; j<scores.length; j++){
//				if (scores[j]!=null){	
//					for (int k=0; k<scores[j].length; k++){
//						double oldVal = Math.log(oldScores[j/2][k/2]);
////						if (val==Double.NEGATIVE_INFINITY) {
////							toBeIgnored[ind] = true;
////							val=Double.MIN_VALUE;
////						}
//						logProbs[ind++] -= oldVal;
//					}
//				}
//			}
//		}
//  	lexiconOffset = logProbs.length;
//		return logProbs;
//	}
//
//	public double[] getLinearizedLexicon() {
//		double[] logProbs = linearizer.getLinearizedLexicon();
//		
//  	int index = 0;
//  	for (short tag=0; tag<lexicon.expectedCounts.length; tag++){
//  		for (int word=0; word<lexicon.expectedCounts[tag][0].length; word++){
//  			for (int substate=0; substate<lexicon.numSubStates[tag]; substate++){
//  				double oldVal = Math.log(oldLexicon.scores[tag][substate/2][word]);
////  				if (val==Double.NEGATIVE_INFINITY) {
////  					toBeIgnored[index] = true;
////  					val=Double.MIN_VALUE;
////  				}
//  				logProbs[index++] -= oldVal;
//  			}
//  		}
//  	}
//  	return logProbs;
//
//		
//	}
//
//	
//	/**
//	 * @return the grammar
//	 */
//	public Grammar getGrammar() {
//		return grammar;
//	}
//
//	/**
//	 * @return the lexicon
//	 */
//	public SimpleLexicon getLexicon() {
//		return lexicon;
//	}
//	
//	public void increment(double[] counts, StateSet stateSet, int tag, double[] weights) {
//		int globalSigIndex = stateSet.sigIndex;
//		if (globalSigIndex != -1){
//			int startIndexWord = getLinearIndex(globalSigIndex, tag);
//			if (startIndexWord<0) System.out.println("incrementing scores for unseen signature tag");
//			startIndexWord += lexiconOffset;
//			for (int i=0; i<nSubstates; i++){
//				counts[startIndexWord++] += weights[i];
//			}
//		}
//		int startIndexWord = getLinearIndex(stateSet.wordIndex, tag);
//		if (startIndexWord<0) System.out.println("incrementing scores for unseen signature tag");
//		startIndexWord += lexiconOffset;
//		for (int i=0; i<nSubstates; i++){
//			counts[startIndexWord++] += weights[i];
//			weights[i]=0;
//		}
//	}
//
//
//	public void increment(double[] counts, UnaryRule rule, double[] weights) {
//		int thisStartIndex = getLinearIndex(rule);
//		int curInd = 0;
//		int nSubstatesParent = (rule.parentState==0) ? 1 : nSubstates;
//		for (int cp = 0; cp < nSubstates; cp++) {
////			if (scores[cp]==null) continue; 
//			for (int np = 0; np < nSubstatesParent; np++) {
//				counts[thisStartIndex++] += weights[curInd];
//				weights[curInd++]=0;
//			}
//		}
//	}
//
//
//	public void increment(double[] counts, BinaryRule rule, double[] weights) {
//		int thisStartIndex = getLinearIndex(rule);
//
//		int curInd = 0;
//		for (int lp = 0; lp < nSubstates; lp++) {
//			for (int rp = 0; rp < nSubstates; rp++) {
////				if (scores[cp]==null) continue; 
//				for (int np = 0; np < nSubstates; np++) {
//					counts[thisStartIndex++] += weights[curInd];
//					weights[curInd++]=0;
//				}
//			}
//		}
//	}
//
//
// }
