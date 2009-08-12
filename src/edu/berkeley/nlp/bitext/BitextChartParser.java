package edu.berkeley.nlp.bitext;

import java.util.Arrays;
import java.util.List;

import sun.awt.geom.Crossings.NonZero;

import edu.berkeley.nlp.bitext.GrammarStateFactory.GrammarState;
import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.Counter;
import fig.basic.StopWatch;
import fig.basic.LogInfo;

/**
 * @author petrov
 *
 */
public class BitextChartParser implements BitextParser{
	private BitextGrammar bitextGrammar;
	private BitextLexicon bitextLexicon;

	private Grammar lGrammar, rGrammar;

	private int numEdgesPopped;
	private int numEdgesDiscovered;

	private List<String> leftInput, rightInput;
	private double[][][][][] iScores, oScores; //startL, endL, startR, endR, statePair
	int myLength;
	int numStatesL, numStatesR, numStatePairs;
	private int[] lPOStags, rPOStags;
	private double initVal;
	private int rootIndex;
	private boolean pruneWithMonolingual, useAlignments;
	private double[][][] iScoresL, iScoresR;	
	private CKYParser lParser, rParser;
	private int[] beginAlignment, endAlignment;
	private boolean doLogAdd;
	double nLogAdds;

	public BitextChartParser(BitextGrammar bitextGrammar, BitextLexicon bitextLexicon, boolean prune, boolean useAlignments) {
		this.bitextGrammar = bitextGrammar;
		this.lGrammar = bitextGrammar.getLeftGrammar();
		this.rGrammar = bitextGrammar.getRightGrammar();
		this.bitextLexicon = bitextLexicon;
		this.pruneWithMonolingual = prune;
		this.useAlignments = useAlignments;
		if (prune){
			lParser = new CKYParser(lGrammar, new DefaultLexicon());
			rParser = new CKYParser(rGrammar, new DefaultLexicon());
		}
		myLength = 0;
		numStatesL = lGrammar.getNumStates();
		numStatesR = rGrammar.getNumStates();
		numStatePairs = bitextGrammar.getNumberStatePairs();
		initVal = Double.NEGATIVE_INFINITY;
		rootIndex = bitextGrammar.getBitextStateIndex(lGrammar.getRootState(), rGrammar.getRootState());
		doLogAdd = true;
	}

	public void parse(List<String> leftInput, List<String> rightInput, Alignment alignment) {
		int lengthL = leftInput.size(), lengthR = rightInput.size();
		createArrays(Math.max(lengthL,lengthR));
		initialize(leftInput, rightInput, alignment);
		nLogAdds = 0;

//		System.gc();
//		
		if (pruneWithMonolingual){
			iScoresL = lParser.getInsideScores(leftInput);
			iScoresR = rParser.getInsideScores(rightInput);
		}

		StopWatch stopwatch = new StopWatch();
		stopwatch.start();

		doInsideScores(lengthL, lengthR);

		stopwatch.stop();
//		LogInfo.logsForce("Just inside-scores " + stopWatch.getLastElapsedTime() + " sec");

//		analyzeChart(lengthL, lengthR);

		LogInfo.logsForce("Goal State inside score " + iScores[0][lengthL][0][lengthR][rootIndex]);
		LogInfo.logsForce("Num Edges Processed:" + numEdgesPopped+". Num log adds: "+nLogAdds);

	}

	/**
	 * 
	 */
	private void analyzeChart(int leftLength, int rightLength) {
		double total=0, nonZero=0;
		for (int diffL=1; diffL<=leftLength; diffL++){ 
			for (int startL=0; startL<=leftLength-diffL; startL++){
				int endL = startL + diffL;

				for (int diffR=1; diffR<=rightLength; diffR++){ 
					for (int startR=0; startR<=rightLength-diffR; startR++){
						int endR = startR + diffR;
						for (int statepair=0; statepair<bitextGrammar.getNumberStatePairs(); statepair++){
							if (iScores[startL][endL][startR][endR][statepair]!=Double.NEGATIVE_INFINITY) nonZero++;
							total++;
						}
					}
				}
			}
		}
		LogInfo.logsForce(nonZero+"/"+total+" chart items have non-zero probability");
	}

	private void createArrays(int sentenceLength){
		if (myLength < sentenceLength){
			lPOStags = new int[sentenceLength];
			rPOStags = new int[sentenceLength];
			iScores = new double[sentenceLength][sentenceLength+1][sentenceLength][sentenceLength+1][numStatePairs];
			myLength = sentenceLength;
//			oScores = new double[sentenceLength][sentenceLength+1][sentenceLength][sentenceLength+1][numStatePairs];
//			iScoresL = new double[sentenceLength][sentenceLength+1][numStatesL];
//			iScoresR = new double[sentenceLength][sentenceLength+1][numStatesR];
		} 
//		ArrayUtil.fill(iScoresL, initVal);
//		ArrayUtil.fill(iScoresR, initVal);
		for (int i=0; i<sentenceLength; i++){
			for (int j=0; j<sentenceLength+1; j++){
				for (int k=0; k<sentenceLength; k++){
					for (int l=0; l<sentenceLength+1; l++){
						for (int s=0; s<numStatePairs; s++){
							iScores[i][j][k][l][s] = 0;
						}
					}
				}
//						ArrayUtil.fill(iScores[i][j], initVal);
//				ArrayUtil.fill(oScores[i][j], initVal);
			}
		}
		Arrays.fill(lPOStags,-1);
		Arrays.fill(rPOStags,-1);
	}

	private void initialize(List<String> leftInput, List<String> rightInput, Alignment alignment) {
		this.leftInput = leftInput;
		this.rightInput = rightInput;
		this.numEdgesPopped = 0;
		this.numEdgesDiscovered = 0;

		bitextLexicon.setLhsInputSentence(leftInput);
		bitextLexicon.setRhsInputSentence(rightInput);

		for (int lIndex = 0; lIndex < leftInput.size(); ++lIndex) {
			String tag = bitextLexicon.getLhsLexicon().getTagScores(lIndex).argMax();
			lPOStags[lIndex] = bitextGrammar.getLeftGrammar().getState(tag).id();
		}

		for (int rIndex = 0; rIndex < rightInput.size(); ++rIndex) {
			String tag = bitextLexicon.getRhsLexicon().getTagScores(rIndex).argMax();
			rPOStags[rIndex] = bitextGrammar.getRightGrammar().getState(tag).id();
		}

		if (alignment != null){
			beginAlignment = new int[leftInput.size()];
			Arrays.fill(beginAlignment, Integer.MAX_VALUE);
			endAlignment = new int[leftInput.size()+1];
			Arrays.fill(endAlignment, Integer.MIN_VALUE);
			for (fig.basic.Pair<Integer, Integer> pair : alignment.getPossibleAlignments()){
				beginAlignment[pair.getFirst()] = Math.min(beginAlignment[pair.getFirst()], pair.getSecond());
				endAlignment[pair.getFirst()+1] = Math.max(endAlignment[pair.getFirst()+1], pair.getSecond()+1);
			}

		}

		for (int lIndex = 0; lIndex < leftInput.size(); ++lIndex) {
			for (int rIndex = 0; rIndex < rightInput.size(); ++rIndex) {
				if (useAlignments && beginAlignment[lIndex]!=rIndex) 
					continue;

				Counter<fig.basic.Pair<String, String>> tagScores = bitextLexicon.getTagScores(lIndex, rIndex);
				for (fig.basic.Pair<String, String> tags : tagScores.keySet()) {
					String lTag = tags.getFirst();
					String rTag = tags.getSecond();

					int tagpair = bitextGrammar.getBitextStateIndex(lTag,rTag);
					if (tagpair==-1) continue;
					iScores[lIndex][lIndex+1][rIndex][rIndex+1][tagpair] = 0;
				}
			}
		}

	}

	public int getNumEdgesPopped() {

		return 0;
	}


	public void doInsideScores(int leftLength, int rightLength){
		for (int diffL=1; diffL<=leftLength; diffL++){ 
			for (int startL=0; startL<=leftLength-diffL; startL++){
				int endL = startL + diffL;

				for (int diffR=1; diffR<=rightLength; diffR++){ 
					for (int startR=0; startR<=rightLength-diffR; startR++){
						int endR = startR + diffR;

						if (useAlignments && beginAlignment[startL]!=startR) 
							continue;

						if (useAlignments && endAlignment[endL]!=endR) 
							continue;
//						{
//	{
//						int startR = beginAlignment[startL];
//						int endR = endAlignment[endL];
//						int diffR = endR - startR;

						for (int lS=0; lS<lGrammar.getNumStates(); lS++){
							GrammarState lState = lGrammar.getState(lS);

							if (pruneWithMonolingual && iScoresL[startL][endL][lS]==initVal) 
								continue;

							for (int rS=0; rS<rGrammar.getNumStates(); rS++){
								GrammarState rState = rGrammar.getState(rS);

								if (pruneWithMonolingual && iScoresR[startR][endR][rS]==initVal) 
									continue;

								int parentIndex = bitextGrammar.getBitextStateIndex(lState, rState);
								if (parentIndex==-1) continue;

//								iScores[startL][endL][startR][endR][parentIndex] = 0;
								double bestScore = iScores[startL][endL][startR][endR][parentIndex];
								double oldScore = bestScore;
//
								if (false&&true){//splitL==startL+1){ // try UU
//									for (BitextRule rule : bitextGrammar.getUURulesByParent(lState, rState)) {
//									numEdgesPopped++;
////								UnaryRule lRule = (UnaryRule)rule.getLeftRule();
////								UnaryRule rRule = (UnaryRule)rule.getRightRule();

//									int childIndex = rule.childIndex;

//									double iScoreC = iScores[startL][endL][startR][endR][childIndex];
//									if (iScoreC==Double.NEGATIVE_INFINITY) continue;

//									double newVal = iScoreC + rule.score;
//									bestScore = (doLogAdd) ? logAdd(bestScore,newVal): Math.max(bestScore, newVal);

//									}

									if (diffL>=2){
										// try unary on the right side

										// Left Term BU Projection
										for (BitextRule buRule : bitextGrammar.getLeftTermBURules(lState, rState)) {
											BinaryRule leftBR = (BinaryRule) buRule.getLeftRule();
											GrammarState tag = leftBR.leftChild();
											numEdgesPopped++;
											if (lPOStags[startL]==tag.id()) {
//												UnaryRule rRule = (UnaryRule)buRule.getRightRule();

												int childIndex = buRule.rChildIndex;

												double iScoreC = iScores[startL+1][endL][startR][endR][childIndex];
												if (iScoreC==Double.NEGATIVE_INFINITY) continue;

												double newVal = iScoreC + buRule.score;
												bestScore = (doLogAdd) ? logAdd(bestScore,newVal): Math.max(bestScore, newVal);


//												LogInfo.logsForce("1. Build ["+startL+","+endL+"] ["+startR+","+endR+"] "+lState+"+"+rState);
											}
										}

										// Right BU Projection
										for (BitextRule buRule : bitextGrammar.getRightTermBURules(lState, rState)) {
											BinaryRule leftBR = (BinaryRule) buRule.getLeftRule();
											GrammarState tag = leftBR.rightChild();
											numEdgesPopped++;
											if (lPOStags[endL-1]==tag.id()) {
//												UnaryRule rRule = (UnaryRule)buRule.getRightRule();

//												bitextGrammar.indexRuleStates(buRule);
												int childIndex = buRule.lChildIndex; 

												double iScoreC = iScores[startL][endL-1][startR][endR][childIndex];
												if (iScoreC==Double.NEGATIVE_INFINITY) continue;

												double newVal = iScoreC + buRule.score;
												bestScore = (doLogAdd) ? logAdd(bestScore,newVal): Math.max(bestScore, newVal);

//												LogInfo.logsForce("2. Build ["+startL+","+endL+"] ["+startR+","+endR+"] "+lState+"+"+rState);

											}
										}
									}
									// try unary on the left side

									// Left UB Projection
									if (diffR>=2){
										for (BitextRule ubRule : bitextGrammar.getLeftTermUBRules(lState, rState)) {
											BinaryRule rightBR = (BinaryRule)ubRule.getRightRule();
											GrammarState tag = rightBR.leftChild();
											numEdgesPopped++;
											if (rPOStags[startR]==tag.id()) {
//												UnaryRule lRule = (UnaryRule)ubRule.getLeftRule();

												int childIndex = ubRule.rChildIndex;

												double iScoreC = iScores[startL][endL][startR+1][endR][childIndex];
												if (iScoreC==Double.NEGATIVE_INFINITY) continue;

												double newVal = iScoreC + ubRule.score;
												bestScore = (doLogAdd) ? logAdd(bestScore,newVal): Math.max(bestScore, newVal);

//												LogInfo.logsForce("3. Build ["+startL+","+endL+"] ["+startR+","+endR+"] "+lState+"+"+rState);

											}
										}

										// Right UB Projection
										for (BitextRule ubRule : bitextGrammar.getRightTermUBRules(lState, rState)) {
											BinaryRule rightBR = (BinaryRule) ubRule.getRightRule();
											GrammarState tag = rightBR.rightChild();
											numEdgesPopped++;
											if (rPOStags[endR-1]==tag.id()) {
//												UnaryRule lRule = (UnaryRule)ubRule.getLeftRule();

												int childIndex = ubRule.lChildIndex;

												double iScoreC = iScores[startL][endL][startR][endR-1][childIndex];
												if (iScoreC==Double.NEGATIVE_INFINITY) continue;

												double newVal = iScoreC + ubRule.score;
												bestScore = (doLogAdd) ? logAdd(bestScore,newVal): Math.max(bestScore, newVal);

//												LogInfo.logsForce("4. Build ["+startL+","+endL+"] ["+startR+","+endR+"] "+lState+"+"+rState);

											}
										}
									}
								}									


								for (int splitL=startL+1; splitL<endL; splitL++){
									for (int splitR=startR+1; splitR<endR; splitR++){

										if (useAlignments && beginAlignment[splitL]!=splitR) 
											continue;

										for (BitextRule bbRule : bitextGrammar.getBBRulesByP(lState, rState)){

											numEdgesPopped++;
//											BinaryRule lbr = (BinaryRule) bbRule.getLeftRule();
//											GrammarState lParentState = lbr.parent();
//											BinaryRule rbr = (BinaryRule) bbRule.getRightRule();
//											GrammarState rParentState = rbr.parent();

											int lChildIndex = bbRule.lChildIndex;
											int rChildIndex = bbRule.rChildIndex;

											double lScore = 1;//iScores[startL][splitL][startR][splitR][lChildIndex];
											if (lScore==Double.NEGATIVE_INFINITY) continue;
											double rScore = 1;//iScores[splitL][endL][splitR][endR][rChildIndex];
											if (rScore==Double.NEGATIVE_INFINITY) continue;

											double newVal = lScore + rScore + bbRule.score;
											bestScore = (doLogAdd) ? logAdd(bestScore,newVal): Math.max(bestScore, newVal);
//											LogInfo.logsForce("Bin. Build ["+startL+","+endL+"] ["+startR+","+endR+"] "+lState+"+"+rState);
//											iScores[startL][endL][startR][endR][parentIndex] = bestScore;

										}
//										iScores[startL][endL][startR][endR][(int)(parentIndex*Math.random())] = bestScore+1;
//										for (BitextRule bbRule : bitextGrammar.getInvertBBRulesByP(lState, rState)){

//										BinaryRule lbr = (BinaryRule) bbRule.getLeftRule();
//										GrammarState lParentState = lbr.parent();
//										BinaryRule rbr = (BinaryRule) bbRule.getRightRule();
//										GrammarState rParentState = rbr.parent();

//										int lChildIndex = bbRule.lChildIndex;
//										int rChildIndex = bbRule.rChildIndex;

//										double lScore = iScores[startL][splitL][startR][splitR][lChildIndex];
//										if (lScore==Double.NEGATIVE_INFINITY) continue;
//										double rScore = iScores[splitL][endL][splitR][endR][rChildIndex];
//										if (rScore==Double.NEGATIVE_INFINITY) continue;

//										iScores[startL][endL][startR][endR][bbRule.parentIndex] = 
//										Math.max(iScores[startL][endL][startR][endR][bbRule.parentIndex],
//										lScore + rScore + bbRule.score);
////									LogInfo.logsForce("Bin. Build ["+startL+","+endL+"] ["+startR+","+endR+"] "+lState+"+"+rState);


//										}

									}
								}

								for (BitextRule rule : bitextGrammar.getUURulesByParent(lState, rState)) {
									UnaryRule lRule = (UnaryRule)rule.getLeftRule();
									UnaryRule rRule = (UnaryRule)rule.getRightRule();
									numEdgesPopped++;
									int childIndex = rule.childIndex;
	
									double iScoreC = iScores[startL][endL][startR][endR][childIndex];
									if (iScoreC==Double.NEGATIVE_INFINITY) continue;
	
									double newVal = iScoreC + rule.score;
									bestScore = (doLogAdd) ? logAdd(bestScore,newVal): Math.max(bestScore, newVal);
	
	//													LogInfo.logsForce("Build ["+startL+","+endL+"] ["+startR+","+endR+"] "+lState+"+"+rState);
								}

								if (bestScore > oldScore) {
									iScores[startL][endL][startR][endR][parentIndex] = bestScore;
								}
							}

						}
					}
				}
			}
		}
	}

	/**
	 * @param bestScore
	 * @param newVal
	 * @return
	 */
	public double logAdd(double a, double b) {
		nLogAdds++;
//		a = Math.random(); b = Math.random();
//		return Math.log(Math.exp(Math.random())+a);
		return Math.log( Math.exp(a) + Math.exp(b) );
	}

}
