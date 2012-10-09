/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.util.Arrays;
import java.util.List;

import edu.berkeley.nlp.discPCFG.Linearizer;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;

/**
 * @author petrov
 * 
 */
public class CoarseToFineTwoChartsParser extends CoarseToFineMaxRuleParser {
	/**
	 * inside and outside scores; start idx, end idx, state, substate ->
	 * logProb/prob
	 */
	/** NEW: we now have two charts one before applying unaries and one after: */
	protected double[][][][] iScorePreU, iScorePostU;
	protected double[][][][] oScorePreU, oScorePostU;

	/**
	 * @param gr
	 * @param lex
	 * @param unaryPenalty
	 * @param endL
	 * @param viterbi
	 * @param sub
	 * @param score
	 */
	public CoarseToFineTwoChartsParser(Grammar gr, Lexicon lex,
			double unaryPenalty, int endL, boolean viterbi, boolean sub,
			boolean score, boolean accurate) {
		super(gr, lex, unaryPenalty, endL, viterbi, sub, score, accurate,
				false, false, true);
	}

	void doConstrainedInsideScores(Grammar grammar, boolean viterbi,
			boolean logScores) {
		if (!viterbi && logScores)
			throw new Error(
					"This would require logAdds and is slow. Exponentiate the scores instead.");
		numSubStatesArray = grammar.numSubStates;
		double initVal = (logScores) ? Double.NEGATIVE_INFINITY : 0;
		// double[] oldIScores = new double[maxNSubStates];
		// int smallestScale = 10, largestScale = -10;
		for (int diff = 1; diff <= length; diff++) {
			// smallestScale = 10; largestScale = -10;
			// System.out.print(diff + " ");
			for (int start = 0; start < (length - diff + 1); start++) {
				int end = start + diff;
				for (int pState = 0; pState < numSubStatesArray.length; pState++) {
					if (diff == 1)
						continue; // there are no binary rules that span over 1
									// symbol only
					if (allowedSubStates[start][end][pState] == null)
						continue;
					BinaryRule[] parentRules = grammar.splitRulesWithP(pState);
					int nParentStates = numSubStatesArray[pState];
					// we will oftern write to the scoresToAdd array and then
					// transfer the accumulated values once
					// to the iScores arrays because writing to large arrays is
					// slow
					// scoresToAdd = new double[nParentStates];
					Arrays.fill(scoresToAdd, initVal);
					boolean somethingChanged = false;
					for (int i = 0; i < parentRules.length; i++) {
						BinaryRule r = parentRules[i];
						int lState = r.leftChildState;
						int rState = r.rightChildState;

						int narrowR = narrowRExtent[start][lState];
						boolean iPossibleL = (narrowR < end); // can this left
																// constituent
																// leave space
																// for a right
																// constituent?
						if (!iPossibleL) {
							continue;
						}

						int narrowL = narrowLExtent[end][rState];
						boolean iPossibleR = (narrowL >= narrowR); // can this
																	// right
																	// constituent
																	// fit next
																	// to the
																	// left
																	// constituent?
						if (!iPossibleR) {
							continue;
						}

						int min1 = narrowR;
						int min2 = wideLExtent[end][rState];
						int min = (min1 > min2 ? min1 : min2); // can this right
																// constituent
																// stretch far
																// enough to
																// reach the
																// left
																// constituent?
						if (min > narrowL) {
							continue;
						}

						int max1 = wideRExtent[start][lState];
						int max2 = narrowL;
						int max = (max1 < max2 ? max1 : max2); // can this left
																// constituent
																// stretch far
																// enough to
																// reach the
																// right
																// constituent?
						if (min > max) {
							continue;
						}

						// TODO switch order of loops for efficiency
						double[][][] scores = r.getScores2();
						int nLeftChildStates = numSubStatesArray[lState];
						int nRightChildStates = numSubStatesArray[rState];
						for (int split = min; split <= max; split++) {
							if (allowedSubStates[start][split][lState] == null)
								continue;
							if (allowedSubStates[split][end][rState] == null)
								continue;
							for (int lp = 0; lp < nLeftChildStates; lp++) {
								// if (iScore[start][split][lState] == null)
								// continue;
								// if
								// (!allowedSubStates[start][split][lState][lp])
								// continue;
								double lS = iScorePostU[start][split][lState][lp];
								if (lS == initVal)
									continue;

								for (int rp = 0; rp < nRightChildStates; rp++) {
									if (scores[lp][rp] == null)
										continue;
									double rS = iScorePostU[split][end][rState][rp];
									if (rS == initVal)
										continue;

									for (int np = 0; np < nParentStates; np++) {
										if (!allowedSubStates[start][end][pState][np])
											continue;
										double pS = scores[lp][rp][np];
										if (pS == initVal)
											continue;
										// if (iScore[split][end][rState] ==
										// null) continue;
										// if
										// (!allowedSubStates[split][end][rState][rp])
										// continue;

										double thisRound = (logScores) ? pS
												+ lS + rS : pS * lS * rS;

										if (viterbi)
											scoresToAdd[np] = Math.max(
													thisRound, scoresToAdd[np]);
										else
											scoresToAdd[np] += thisRound;
										somethingChanged = true;
									}
								}
							}
							// if (!somethingChanged) continue;
							// boolean firstTime = false;
							/*
							 * int parentScale = iScale[start][end][pState]; int
							 * currentScale =
							 * iScale[start][split][lState]+iScale
							 * [split][end][rState]; if
							 * (parentScale==currentScale) { // already had a
							 * way to generate this state and the scales are the
							 * same // -> nothing to do } else { if
							 * (parentScale==Integer.MIN_VALUE){ // first time
							 * we can build this state firstTime = true;
							 * parentScale =
							 * scaleArray(scoresToAdd,currentScale);
							 * iScale[start][end][pState] = parentScale;
							 * //smallestScale =
							 * Math.min(smallestScale,parentScale);
							 * //largestScale =
							 * Math.max(largestScale,parentScale); } else { //
							 * scale the smaller one to the base of the bigger
							 * one int newScale =
							 * Math.max(currentScale,parentScale);
							 * scaleArrayToScale
							 * (scoresToAdd,currentScale,newScale);
							 * scaleArrayToScale
							 * (iScore[start][end][pState],parentScale
							 * ,newScale); iScale[start][end][pState] =
							 * newScale; //smallestScale =
							 * Math.min(smallestScale,newScale); //largestScale
							 * = Math.max(largestScale,newScale); } }
							 */
						}
					}
					if (!somethingChanged)
						continue;
					for (int np = 0; np < nParentStates; np++) {
						if (scoresToAdd[np] > initVal) {
							iScorePreU[start][end][pState][np] = scoresToAdd[np];
						}
					}
					// iScale[start][end][pState] = currentScale;
					// iScale[start][end][pState] =
					// scaleArray(iScore[start][end][pState],iScale[start][end][pState]);
					if (true/* firstTime */) {
						if (start > narrowLExtent[end][pState]) {
							narrowLExtent[end][pState] = start;
							wideLExtent[end][pState] = start;
						} else {
							if (start < wideLExtent[end][pState]) {
								wideLExtent[end][pState] = start;
							}
						}
						if (end < narrowRExtent[start][pState]) {
							narrowRExtent[start][pState] = end;
							wideRExtent[start][pState] = end;
						} else {
							if (end > wideRExtent[start][pState]) {
								wideRExtent[start][pState] = end;
							}
						}
					}
				}
				// now do the unaries
				for (int pState = 0; pState < numSubStatesArray.length; pState++) {
					if (allowedSubStates[start][end][pState] == null)
						continue;
					// Should be: Closure under sum-product:
					UnaryRule[] unaries = grammar
							.getClosedSumUnaryRulesByParent(pState);
					// UnaryRule[] unaries =
					// grammar.getUnaryRulesByParent(pState).toArray(new
					// UnaryRule[0]);

					int nParentStates = numSubStatesArray[pState];// scores[0].length;
					boolean firstTime = true;
					boolean somethingChanged = false;
					for (int r = 0; r < unaries.length; r++) {
						UnaryRule ur = unaries[r];
						int cState = ur.childState;
						if ((pState == cState))
							continue;

						if (iScorePreU[start][end][cState] == null)
							continue;
						// if (!allowedStates[start][end][cState]) continue;
						// new loop over all substates
						// System.out.println("Rule "+r+" out of "+unaries.length+" "+ur);
						double[][] scores = ur.getScores2();
						int nChildStates = numSubStatesArray[cState];// scores[0].length;
						for (int cp = 0; cp < nChildStates; cp++) {
							if (scores[cp] == null)
								continue;
							double iS = iScorePreU[start][end][cState][cp];
							if (iS == initVal)
								continue;

							for (int np = 0; np < nParentStates; np++) {
								if (!allowedSubStates[start][end][pState][np])
									continue;
								// if
								// (!allowedSubStates[start][end][cState][cp])
								// continue;
								double pS = scores[cp][np];
								if (pS == initVal)
									continue;

								if (firstTime) {
									firstTime = false;
									Arrays.fill(scoresToAdd, initVal);
								}

								double thisRound = (logScores) ? iS + pS : iS
										* pS;

								if (viterbi)
									scoresToAdd[np] = Math.max(thisRound,
											scoresToAdd[np]);
								else
									scoresToAdd[np] += thisRound;
								somethingChanged = true;
							}
						}
					}
					/*
					 * boolean firstTime = false; int currentScale =
					 * iScale[start][end][cState]; int parentScale =
					 * iScale[start][end][pState]; if
					 * (parentScale==currentScale) { // already had a way to
					 * generate this state and the scales are the same // ->
					 * nothing to do } else { if
					 * (parentScale==Integer.MIN_VALUE){ // first time we can
					 * build this state firstTime = true; parentScale =
					 * scaleArray(scoresToAdd,currentScale);
					 * iScale[start][end][pState] = parentScale; //smallestScale
					 * = Math.min(smallestScale,parentScale); //largestScale =
					 * Math.max(largestScale,parentScale); } else { // scale the
					 * smaller one to the base of the bigger one int newScale =
					 * Math.max(currentScale,parentScale);
					 * scaleArrayToScale(scoresToAdd,currentScale,newScale);
					 * scaleArrayToScale
					 * (iScore[start][end][pState],parentScale,newScale);
					 * iScale[start][end][pState] = newScale; //smallestScale =
					 * Math.min(smallestScale,newScale); //largestScale =
					 * Math.max(largestScale,newScale);
					 * 
					 * } }
					 */
					if (!somethingChanged) {
						iScorePostU[start][end][pState] = iScorePreU[start][end][pState]
								.clone();
						continue;
					} else {
						for (int np = 0; np < nParentStates; np++) {
							if (scoresToAdd[np] > initVal) {
								if (viterbi)
									iScorePostU[start][end][pState][np] = Math
											.max(iScorePreU[start][end][pState][np],
													scoresToAdd[np]);
								else
									iScorePostU[start][end][pState][np] = iScorePreU[start][end][pState][np]
											+ scoresToAdd[np];
							} else
								iScorePostU[start][end][pState][np] = iScorePreU[start][end][pState][np];
						}
					}
					// iScale[start][end][pState] = currentScale;
					// iScale[start][end][pState] =
					// scaleArray(iScore[start][end][pState],iScale[start][end][pState]);
					if (true) {
						if (start > narrowLExtent[end][pState]) {
							narrowLExtent[end][pState] = start;
							wideLExtent[end][pState] = start;
						} else {
							if (start < wideLExtent[end][pState]) {
								wideLExtent[end][pState] = start;
							}
						}
						if (end < narrowRExtent[start][pState]) {
							narrowRExtent[start][pState] = end;
							wideRExtent[start][pState] = end;
						} else {
							if (end > wideRExtent[start][pState]) {
								wideRExtent[start][pState] = end;
							}
						}
					}
				}
			}
		}
	}

	void doConstrainedOutsideScores(Grammar grammar, boolean viterbi,
			boolean logScores) {
		numSubStatesArray = grammar.numSubStates;
		double initVal = (logScores) ? Double.NEGATIVE_INFINITY : 0;
		for (int diff = length; diff >= 1; diff--) {
			for (int start = 0; start + diff <= length; start++) {
				int end = start + diff;
				// do unaries
				boolean somethingChanged = false;
				for (int pState = 0; pState < numSubStatesArray.length; pState++) {
					if (oScorePreU[start][end][pState] == null) {
						continue;
					}
					// if (!allowedStates[start][end][pState]) continue;
					// Should be: Closure under sum-product:
					UnaryRule[] rules = grammar
							.getClosedSumUnaryRulesByParent(pState);
					// UnaryRule[] rules =
					// grammar.getClosedViterbiUnaryRulesByParent(pState);
					// For now:
					// UnaryRule[] rules =
					// grammar.getUnaryRulesByParent(pState).toArray(new
					// UnaryRule[0]);
					for (int r = 0; r < rules.length; r++) {
						UnaryRule ur = rules[r];
						int cState = ur.childState;
						if ((pState == cState))
							continue;

						// if (!allowedStates[start][end][cState]) continue;
						if (oScorePreU[start][end][cState] == null) {
							continue;
						}

						double[][] scores = ur.getScores2();
						int nParentStates = numSubStatesArray[pState];
						int nChildStates = scores.length;
						boolean firstTime = true;
						for (int cp = 0; cp < nChildStates; cp++) {
							if (scores[cp] == null)
								continue;
							if (!allowedSubStates[start][end][cState][cp])
								continue;
							for (int np = 0; np < nParentStates; np++) {
								// if
								// (!allowedSubStates[start][end][pState][np])
								// continue;
								double pS = scores[cp][np];
								if (pS == initVal)
									continue;

								double oS = oScorePreU[start][end][pState][np];
								if (oS == initVal)
									continue;

								double thisRound = (logScores) ? oS + pS : oS
										* pS;

								if (firstTime) {
									firstTime = false;
									Arrays.fill(scoresToAdd, initVal);
								}

								if (viterbi)
									scoresToAdd[cp] = Math.max(thisRound,
											scoresToAdd[cp]);
								else
									scoresToAdd[cp] += thisRound;
								somethingChanged = true;
							}
						}

						// check first whether there was a change at all
						// boolean firstTime = false;
						/*
						 * int currentScale = oScale[start][end][pState]; int
						 * childScale = oScale[start][end][cState]; if
						 * (childScale==currentScale) { // already had a way to
						 * generate this state and the scales are the same // ->
						 * nothing to do } else { if
						 * (childScale==Integer.MIN_VALUE){ // first time we can
						 * build this state firstTime = true; childScale =
						 * scaleArray(scoresToAdd,currentScale);
						 * oScale[start][end][cState] = childScale; } else { //
						 * scale the smaller one to the base of the bigger one
						 * int newScale = Math.max(currentScale,childScale);
						 * scaleArrayToScale(scoresToAdd,currentScale,newScale);
						 * scaleArrayToScale
						 * (oScore[start][end][cState],childScale,newScale);
						 * oScale[start][end][cState] = newScale; } }
						 */
						if (somethingChanged) {
							for (int cp = 0; cp < nChildStates; cp++) {
								// if (true /*firstTime*/)
								// oScore[start][end][cState][cp] =
								// scoresToAdd[cp];
								// else
								// if (scoresToAdd[cp] > 0)
								// oScore[start][end][cState][cp] +=
								// scoresToAdd[cp];
								if (scoresToAdd[cp] > initVal) {
									if (viterbi)
										oScorePostU[start][end][cState][cp] = Math
												.max(oScorePostU[start][end][cState][cp],
														scoresToAdd[cp]);
									else
										oScorePostU[start][end][cState][cp] += scoresToAdd[cp];
								}
							}
						}
					}
				}
				// copy/add the entries where the unaries where not useful
				for (int cState = 0; cState < numSubStatesArray.length; cState++) {
					if (oScorePostU[start][end][cState] == null)
						continue;
					for (int cp = 0; cp < numSubStatesArray[cState]; cp++) {
						if (viterbi)
							oScorePostU[start][end][cState][cp] = Math.max(
									oScorePostU[start][end][cState][cp],
									oScorePreU[start][end][cState][cp]);
						else
							oScorePostU[start][end][cState][cp] += oScorePreU[start][end][cState][cp];
					}
				}

				// do binaries

				for (int pState = 0; pState < numSubStatesArray.length; pState++) {
					if (oScorePostU[start][end][pState] == null) {
						continue;
					}
					final int nParentChildStates = numSubStatesArray[pState];
					// if (!allowedStates[start][end][pState]) continue;
					BinaryRule[] rules = grammar.splitRulesWithP(pState);

					// BinaryRule[] rules = grammar.splitRulesWithLC(lState);
					for (int r = 0; r < rules.length; r++) {
						BinaryRule br = rules[r];
						int lState = br.leftChildState;
						int min1 = narrowRExtent[start][lState];
						if (end < min1) {
							continue;
						}

						int rState = br.rightChildState;
						int max1 = narrowLExtent[end][rState];
						if (max1 < min1) {
							continue;
						}

						int min = min1;
						int max = max1;
						if (max - min > 2) {
							int min2 = wideLExtent[end][rState];
							min = (min1 > min2 ? min1 : min2);
							if (max1 < min) {
								continue;
							}
							int max2 = wideRExtent[start][lState];
							max = (max1 < max2 ? max1 : max2);
							if (max < min) {
								continue;
							}
						}

						double[][][] scores = br.getScores2();
						int nLeftChildStates = numSubStatesArray[lState];
						int nRightChildStates = numSubStatesArray[rState];
						for (int split = min; split <= max; split++) {
							if (oScorePreU[start][split][lState] == null)
								continue;
							if (oScorePreU[split][end][rState] == null)
								continue;
							// if (!allowedStates[start][split][lState])
							// continue;
							// if (!allowedStates[split][end][rState]) continue;
							double[] rightScores = new double[nRightChildStates];
							Arrays.fill(scoresToAdd, initVal);
							Arrays.fill(rightScores, initVal);
							somethingChanged = false;
							for (int lp = 0; lp < nLeftChildStates; lp++) {
								double lS = iScorePostU[start][split][lState][lp];
								if (lS == initVal) {
									continue;
								}
								// if
								// (!allowedSubStates[start][split][lState][lp])
								// continue;
								for (int rp = 0; rp < nRightChildStates; rp++) {
									if (scores[lp][rp] == null)
										continue;
									double rS = iScorePostU[split][end][rState][rp];
									if (rS == initVal) {
										continue;
									}
									// if
									// (!allowedSubStates[split][end][rState][rp])
									// continue;

									for (int np = 0; np < nParentChildStates; np++) {
										double pS = scores[lp][rp][np];
										if (pS == initVal)
											continue;

										double oS = oScorePostU[start][end][pState][np];
										if (oS == initVal)
											continue;
										// if
										// (!allowedSubStates[start][end][pState][np])
										// continue;

										double thisRoundL = (logScores) ? pS
												+ rS + oS : pS * rS * oS;
										double thisRoundR = (logScores) ? pS
												+ lS + oS : pS * lS * oS;

										if (viterbi) {
											scoresToAdd[lp] = Math
													.max(thisRoundL,
															scoresToAdd[lp]);
											rightScores[rp] = Math
													.max(thisRoundR,
															rightScores[rp]);
										} else {
											scoresToAdd[lp] += thisRoundL;
											rightScores[rp] += thisRoundR;
										}

										somethingChanged = true;
									}
								}
							}
							if (!somethingChanged)
								continue;
							/*
							 * boolean firstTime = false; int leftScale =
							 * oScale[start][split][lState]; int rightScale =
							 * oScale[split][end][rState]; int parentScale =
							 * oScale[start][end][pState]; int currentScale =
							 * parentScale+iScale[split][end][rState]; if
							 * (leftScale==currentScale) { // already had a way
							 * to generate this state and the scales are the
							 * same // -> nothing to do } else { if
							 * (leftScale==Integer.MIN_VALUE){ // first time we
							 * can build this state firstTime = true; leftScale
							 * = scaleArray(scoresToAdd,currentScale);
							 * oScale[start][split][lState] = leftScale; } else
							 * { // scale the smaller one to the base of the
							 * bigger one int newScale =
							 * Math.max(currentScale,leftScale);
							 * scaleArrayToScale
							 * (scoresToAdd,currentScale,newScale);
							 * scaleArrayToScale
							 * (oScore[start][split][lState],leftScale
							 * ,newScale); oScale[start][split][lState] =
							 * newScale; } }
							 */
							for (int cp = 0; cp < nLeftChildStates; cp++) {
								// if (true /*firstTime*/)
								// oScore[start][split][lState][cp] =
								// scoresToAdd[cp];
								if (scoresToAdd[cp] > initVal) {
									if (viterbi)
										oScorePreU[start][split][lState][cp] = Math
												.max(oScorePreU[start][split][lState][cp],
														scoresToAdd[cp]);
									else
										oScorePreU[start][split][lState][cp] += scoresToAdd[cp];
								}
							}
							// oScale[start][split][lState] = currentScale;
							// oScale[start][split][lState] =
							// scaleArray(oScore[start][split][lState],oScale[start][split][lState]);

							// currentScale =
							// parentScale+iScale[start][split][lState];
							/*
							 * firstTime = false; if (rightScale==currentScale)
							 * { // already had a way to generate this state and
							 * the scales are the same // -> nothing to do }
							 * else { if (rightScale==Integer.MIN_VALUE){ //
							 * first time we can build this state firstTime =
							 * true; rightScale =
							 * scaleArray(rightScores,currentScale);
							 * oScale[split][end][rState] = rightScale; } else {
							 * // scale the smaller one to the base of the
							 * bigger one int newScale =
							 * Math.max(currentScale,rightScale);
							 * scaleArrayToScale
							 * (rightScores,currentScale,newScale);
							 * scaleArrayToScale
							 * (oScore[split][end][rState],rightScale,newScale);
							 * oScale[split][end][rState] = newScale; } }
							 */
							for (int cp = 0; cp < nRightChildStates; cp++) {
								// if (true/*firstTime*/)
								// oScore[split][end][rState][cp] =
								// rightScores[cp];
								// else
								if (rightScores[cp] > initVal) {
									if (viterbi)
										oScorePreU[split][end][rState][cp] = Math
												.max(oScorePreU[split][end][rState][cp],
														rightScores[cp]);
									else
										oScorePreU[split][end][rState][cp] += rightScores[cp];
								}
							}
							// oScale[split][end][rState] = currentScale;
							// oScale[split][end][rState] =
							// scaleArray(oScore[split][end][rState],oScale[split][end][rState]);
						}
					}
				}
			}
		}
	}

	void initializeChart(List<String> sentence, Lexicon lexicon,
			boolean noSubstates, boolean noSmoothing) {
		int start = 0;
		int end = start + 1;
		for (String word : sentence) {
			end = start + 1;
			for (int tag = 0; tag < numSubStatesArray.length; tag++) {
				if (!noSubstates && allowedSubStates[start][end][tag] == null)
					continue;
				if (grammarTags[tag])
					continue;
				// System.out.println("Initializing");
				// if (dummy) allowedStates[start][end][tag] = true;
				narrowRExtent[start][tag] = end;
				narrowLExtent[end][tag] = start;
				wideRExtent[start][tag] = end;
				wideLExtent[end][tag] = start;
				double[] lexiconScores = lexicon.score(word, (short) tag,
						start, noSmoothing, false);
				// if (!logProbs) iScale[start][end][tag] =
				// scaleArray(lexiconScores,0);
				for (short n = 0; n < lexiconScores.length; n++) {
					double prob = lexiconScores[n];
					if (noSubstates)
						viScore[start][end][tag] = prob;
					else
						iScorePreU[start][end][tag][n] = prob;
				}
				/*
				 * if (start==1){
				 * System.out.println(word+" +TAG "+(String)tagNumberer
				 * .object(tag)+" "+Arrays.toString(lexiconScores)); }
				 */
			}
			start++;
		}
	}

	protected void createArrays(boolean firstTime, int numStates,
			short[] numSubStatesArray, int level, double initVal,
			boolean justInit) {
		// zero out some stuff first in case we recently ran out of memory and
		// are reallocating
		// spanMass = new double[length][length+1];
		if (firstTime) {
			viScore = new double[length][length + 1][];
			voScore = new double[length][length + 1][];
			iScorePreU = new double[length][length + 1][][];
			iScorePostU = new double[length][length + 1][][];
			oScorePreU = new double[length][length + 1][][];
			oScorePostU = new double[length][length + 1][][];
			allowedSubStates = new boolean[length][length + 1][][];
			allowedStates = new boolean[length][length + 1][];
			vAllowedStates = new boolean[length][length + 1];
		}

		for (int start = 0; start < length; start++) {
			for (int end = start + 1; end <= length; end++) {
				if (firstTime) {
					viScore[start][end] = new double[numStates];
					voScore[start][end] = new double[numStates];
					iScorePreU[start][end] = new double[numStates][];
					iScorePostU[start][end] = new double[numStates][];
					oScorePreU[start][end] = new double[numStates][];
					oScorePostU[start][end] = new double[numStates][];
					// iScale[start][end] = new int[numStates];
					// oScale[start][end] = new int[numStates];
					allowedSubStates[start][end] = new boolean[numStates][];
					allowedStates[start][end] = grammarTags.clone();
					if (level == 1 && (end - start == 1))
						Arrays.fill(allowedStates[start][end], true);
					vAllowedStates[start][end] = true;
				}
				for (int state = 0; state < numSubStatesArray.length; state++) {
					if (allowedSubStates[start][end][state] != null) {
						if (level < 1) {
							viScore[start][end][state] = Double.NEGATIVE_INFINITY;
							voScore[start][end][state] = Double.NEGATIVE_INFINITY;
						} else {
							iScorePreU[start][end][state] = new double[numSubStatesArray[state]];
							iScorePostU[start][end][state] = new double[numSubStatesArray[state]];
							oScorePreU[start][end][state] = new double[numSubStatesArray[state]];
							oScorePostU[start][end][state] = new double[numSubStatesArray[state]];
							Arrays.fill(iScorePreU[start][end][state], initVal);
							Arrays.fill(iScorePostU[start][end][state], initVal);
							Arrays.fill(oScorePreU[start][end][state], initVal);
							Arrays.fill(oScorePostU[start][end][state], initVal);
							// Arrays.fill(iScale[start][end],
							// Integer.MIN_VALUE);
							// Arrays.fill(oScale[start][end],
							// Integer.MIN_VALUE);

							// boolean[] newAllowedSubStates = new
							// boolean[numSubStatesArray[state]];
							// if (allowedSubStates[start][end][state]==null ||
							// level<=1){
							// Arrays.fill(newAllowedSubStates,true);
							// allowedSubStates[start][end][state] =
							// newAllowedSubStates;
							// } else{
							// if (!justInit){
							// // int[][] curLChildMap = lChildMap[level-2];
							// // int[][] curRChildMap = rChildMap[level-2];
							// // for (int i=0;
							// i<allowedSubStates[start][end][state].length;
							// i++){
							// // boolean val =
							// allowedSubStates[start][end][state][i];
							// // newAllowedSubStates[curLChildMap[state][i]] =
							// val;
							// // newAllowedSubStates[curRChildMap[state][i]] =
							// val;
							// // }
							// // allowedSubStates[start][end][state] =
							// newAllowedSubStates;
							// }
							// }
						}
					} else {
						if (level < 1) {
							viScore[start][end][state] = Double.NEGATIVE_INFINITY;
							voScore[start][end][state] = Double.NEGATIVE_INFINITY;
						} else {
							iScorePreU[start][end][state] = null;
							iScorePostU[start][end][state] = null;
							oScorePreU[start][end][state] = null;
							oScorePostU[start][end][state] = null;
							// allowedSubStates[start][end][state] = new
							// boolean[1];
							// allowedSubStates[start][end][state][0] = false;
						}
					}
				}
				if (level > 0 && start == 0 && end == length) {
					if (iScorePostU[start][end][0] == null)
						System.out
								.println("ROOT does not span the entire tree!");
				}
			}
		}
		narrowRExtent = new int[length + 1][numStates];
		wideRExtent = new int[length + 1][numStates];
		narrowLExtent = new int[length + 1][numStates];
		wideLExtent = new int[length + 1][numStates];

		for (int loc = 0; loc <= length; loc++) {
			Arrays.fill(narrowLExtent[loc], -1); // the rightmost left with
													// state s ending at i that
													// we can get is the
													// beginning
			Arrays.fill(wideLExtent[loc], length + 1); // the leftmost left with
														// state s ending at i
														// that we can get is
														// the end
			Arrays.fill(narrowRExtent[loc], length + 1); // the leftmost right
															// with state s
															// starting at i
															// that we can get
															// is the end
			Arrays.fill(wideRExtent[loc], -1); // the rightmost right with state
												// s starting at i that we can
												// get is the beginning
		}
	}

	protected void clearArrays() {
		iScorePreU = iScorePostU = oScorePreU = oScorePostU = null;
		viScore = voScore = null;
		allowedSubStates = null;
		vAllowedStates = null;
		// iPossibleByL = iPossibleByR = oFilteredEnd = oFilteredStart =
		// oPossibleByL = oPossibleByR = tags = null;
		narrowRExtent = wideRExtent = narrowLExtent = wideLExtent = null;
	}

	public void doPreParses(List<String> sentence, Tree<StateSet> tree,
			boolean noSmoothing) {

		boolean keepGoldAlive = (tree != null); // we are given the gold tree ->
												// make sure we don't prune it
												// away
		clearArrays();
		length = (short) sentence.size();
		double score = 0;
		Grammar curGrammar = null;
		Lexicon curLexicon = null;

		double[] accurateThresholds = { -8, -12, -12, -11, -12, -12, -14 };
		double[] fastThresholds = { -8, -9.75, -10, -9.6, -9.66, -8.01, -7.4,
				-10 };
		double[] pruningThreshold = null;

		if (accurate)
			pruningThreshold = accurateThresholds;
		else
			pruningThreshold = fastThresholds;

		for (int level = startLevel; level < endLevel; level++) {
			if (level == -1)
				continue; // don't do the pre-pre parse
			if (!isBaseline && level == endLevel)
				continue;//

			curGrammar = grammarCascade[level - startLevel];
			curLexicon = lexiconCascade[level - startLevel];

			createArrays(level == 0, curGrammar.numStates,
					curGrammar.numSubStates, level, Double.NEGATIVE_INFINITY,
					false);

			initializeChart(sentence, curLexicon, level < 1, noSmoothing);
			final boolean viterbi = true, logScores = true;
			if (level < 1) {
				doConstrainedViterbiInsideScores(curGrammar,
						level == startLevel);
				score = viScore[0][length][0];
			} else {
				doConstrainedInsideScores(curGrammar, viterbi, logScores);
				score = iScorePostU[0][length][0][0];
			}

			// System.out.println("\nFound a parse for sentence with length "+length+". The LL is "+score+".");
			if (score == Double.NEGATIVE_INFINITY)
				continue;

			if (level < 1) {
				voScore[0][length][0] = 0.0;
				doConstrainedViterbiOutsideScores(curGrammar,
						level == startLevel);
			} else {
				oScorePreU[0][length][0][0] = 0.0;
				doConstrainedOutsideScores(curGrammar, viterbi, logScores);
			}

			pruneChart(
					Double.NEGATIVE_INFINITY/* pruningThreshold[level+1] */,
					curGrammar.numSubStates, level);
			// if (keepGoldAlive) ensureGoldTreeSurvives(tree, level);
		}
	}

	public Tree<String> getBestConstrainedParse(List<String> sentence,
			List<Integer>[][] pStates) {
		doPreParses(sentence, null, false);
		// length = (short)sentence.size();
		// constrainChart();

		bestTree = new Tree<String>("ROOT");
		double score = 0;

		Grammar curGrammar = grammarCascade[endLevel - startLevel + 1];
		Lexicon curLexicon = lexiconCascade[endLevel - startLevel + 1];
		grammar = curGrammar;
		lexicon = curLexicon;

		double initVal = (viterbiParse) ? Double.NEGATIVE_INFINITY : 0;
		int level = isBaseline ? 1 : endLevel;
		createArrays(false, curGrammar.numStates, curGrammar.numSubStates,
				level, initVal, !isBaseline);

		initializeChart(sentence, curLexicon, false, false);

		doConstrainedInsideScores(curGrammar, viterbiParse, false);
		score = iScorePostU[0][length][0][0];

		if (!viterbiParse)
			score = Math.log(score);// + (100*iScale[0][length][0]);
		logLikelihood = score;
		if (score != Double.NEGATIVE_INFINITY) {
			// System.out.println("\nFound a parse for sentence with length "+length+". The LL is "+score+".");

			if (!viterbiParse) {
				oScorePreU[0][length][0][0] = 1.0;
				doConstrainedOutsideScores(curGrammar, viterbiParse, false);
				doConstrainedMaxCScores(sentence, curGrammar, curLexicon, false);
			}
			// iScore = iScorePostU;
			// oScore = oScorePostU;

			if (viterbiParse)
				bestTree = extractBestViterbiParse(0, 0, 0, length, sentence);
			else
				bestTree = extractBestMaxRuleParse(0, length, sentence);

		}

		maxcScore = null;
		maxcSplit = null;
		maxcChild = null;
		maxcLeftChild = null;
		maxcRightChild = null;

		return bestTree;
	}

	/**
	 * Assumes that inside and outside scores (sum version, not viterbi) have
	 * been computed. In particular, the narrowRExtent and other arrays need not
	 * be updated.
	 */
	void doConstrainedMaxCScores(List<String> sentence, Grammar grammar,
			SophisticatedLexicon lexicon) {
		numSubStatesArray = grammar.numSubStates;
		maxcScore = new double[length][length + 1][numStates];
		maxcSplit = new int[length][length + 1][numStates];
		maxcChild = new int[length][length + 1][numStates];
		maxcLeftChild = new int[length][length + 1][numStates];
		maxcRightChild = new int[length][length + 1][numStates];
		double threshold = 1.0e-2;
		double logNormalizer = iScorePostU[0][length][0][0];
		double thresh2 = threshold * logNormalizer;
		for (int diff = 1; diff <= length; diff++) {
			// System.out.print(diff + " ");
			for (int start = 0; start < (length - diff + 1); start++) {
				int end = start + diff;
				Arrays.fill(maxcSplit[start][end], -1);
				Arrays.fill(maxcChild[start][end], -1);
				Arrays.fill(maxcLeftChild[start][end], -1);
				Arrays.fill(maxcRightChild[start][end], -1);
				if (diff > 1) {
					// diff > 1: Try binary rules
					for (int pState = 0; pState < numSubStatesArray.length; pState++) {
						if (oScorePostU[start][end][pState] == null) {
							continue;
						}
						// if (!allowedStates[start][end][pState]) continue;
						BinaryRule[] parentRules = grammar
								.splitRulesWithP(pState);
						int nParentStates = numSubStatesArray[pState]; // ==
																		// scores[0][0].length;

						for (int i = 0; i < parentRules.length; i++) {
							BinaryRule r = parentRules[i];
							int lState = r.leftChildState;
							int rState = r.rightChildState;

							int narrowR = narrowRExtent[start][lState];
							boolean iPossibleL = (narrowR < end); // can this
																	// left
																	// constituent
																	// leave
																	// space for
																	// a right
																	// constituent?
							if (!iPossibleL) {
								continue;
							}

							int narrowL = narrowLExtent[end][rState];
							boolean iPossibleR = (narrowL >= narrowR); // can
																		// this
																		// right
																		// constituent
																		// fit
																		// next
																		// to
																		// the
																		// left
																		// constituent?
							if (!iPossibleR) {
								continue;
							}

							int min1 = narrowR;
							int min2 = wideLExtent[end][rState];
							int min = (min1 > min2 ? min1 : min2); // can this
																	// right
																	// constituent
																	// stretch
																	// far
																	// enough to
																	// reach the
																	// left
																	// constituent?
							if (min > narrowL) {
								continue;
							}

							int max1 = wideRExtent[start][lState];
							int max2 = narrowL;
							int max = (max1 < max2 ? max1 : max2); // can this
																	// left
																	// constituent
																	// stretch
																	// far
																	// enough to
																	// reach the
																	// right
																	// constituent?
							if (min > max) {
								continue;
							}
							// TODO switch order of loops for efficiency
							double[][][] scores = r.getScores2();
							int nLeftChildStates = numSubStatesArray[lState]; // ==
																				// scores.length;
							int nRightChildStates = numSubStatesArray[rState]; // ==
																				// scores[0].length;
							for (int split = min; split <= max; split++) {
								double ruleScore = 0;
								if (iScorePostU[start][split][lState] == null)
									continue;
								if (iScorePostU[split][end][rState] == null)
									continue;
								// if (!allowedStates[start][split][lState])
								// continue;
								// if (!allowedStates[split][end][rState])
								// continue;
								for (int lp = 0; lp < nLeftChildStates; lp++) {
									double lIS = iScorePostU[start][split][lState][lp];
									// if (lIS == 0) continue;
									if (lIS < thresh2)
										continue;
									// if
									// (!allowedSubStates[start][split][lState][lp])
									// continue;

									for (int rp = 0; rp < nRightChildStates; rp++) {
										if (scores[lp][rp] == null)
											continue;
										double rIS = iScorePostU[split][end][rState][rp];
										// if (rIS == 0) continue;
										if (rIS < thresh2)
											continue;
										// if
										// (!allowedSubStates[split][end][rState][rp])
										// continue;
										for (int np = 0; np < nParentStates; np++) {
											// if
											// (!allowedSubStates[start][end][pState][np])
											// continue;
											double pOS = oScorePostU[start][end][pState][np];
											// if (pOS == 0) continue;
											if (pOS < thresh2)
												continue;

											double ruleS = scores[lp][rp][np];
											if (ruleS == 0)
												continue;
											ruleScore += (pOS * ruleS * lIS * rIS)
													/ logNormalizer;
										}
									}
								}
								double scale = 1.0;/*
													 * Math.pow(GrammarTrainer.SCALE
													 * ,
													 * oScale[start][end][pState
													 * ]
													 * +iScale[start][split][lState
													 * ]+
													 * iScale[split][end][rState
													 * ]-iScale[0][length][0]);
													 */
								double leftChildScore = maxcScore[start][split][lState];
								double rightChildScore = maxcScore[split][end][rState];
								double gScore = ruleScore * leftChildScore
										* rightChildScore * scale;
								if (gScore > maxcScore[start][end][pState]) {
									maxcScore[start][end][pState] = gScore;
									maxcSplit[start][end][pState] = split;
									maxcLeftChild[start][end][pState] = lState;
									maxcRightChild[start][end][pState] = rState;
								}
							}
						}
					}
				} else { // diff == 1
					// We treat TAG --> word exactly as if it was a unary rule,
					// except the score of the rule is
					// given by the lexicon rather than the grammar and that we
					// allow another unary on top of it.
					// for (int tag : lexicon.getAllTags()){
					for (int tag = 0; tag < numSubStatesArray.length; tag++) {
						if (allowedSubStates[start][end][tag] == null)
							continue;
						int nTagStates = numSubStatesArray[tag];
						String word = sentence.get(start);
						// System.out.print("Attempting");
						if (grammar.isGrammarTag(tag))
							continue;
						// System.out.println("Computing maxcScore for span "
						// +start + " to "+end);
						double[] lexiconScoreArray = lexicon.score(word,
								(short) tag, start, false, false);
						double lexiconScores = 0;
						for (int tp = 0; tp < nTagStates; tp++) {
							double pOS = oScorePostU[start][end][tag][tp];
							if (pOS < thresh2)
								continue;
							double ruleS = lexiconScoreArray[tp];
							lexiconScores += (pOS * ruleS) / logNormalizer; // The
																			// inside
																			// score
																			// of
																			// a
																			// word
																			// is
																			// 0.0f
						}
						double scale = 1.0;/*
											 * Math.pow(GrammarTrainer.SCALE,
											 * oScale
											 * [start][end][tag]-iScale[0][
											 * length][0]);
											 */
						// System.out.println("Setting maxcScore for span "
						// +start + " to "+end+" to " +lexiconScores * scale);
						maxcScore[start][end][tag] = lexiconScores * scale;
					}
				}
				// Try unary rules
				// Replacement for maxcScore[start][end], which is updated in
				// batch
				double[] maxcScoreStartEnd = new double[numStates];
				for (int i = 0; i < numStates; i++) {
					maxcScoreStartEnd[i] = maxcScore[start][end][i];
				}
				for (int pState = 0; pState < numSubStatesArray.length; pState++) {
					if (oScorePostU[start][end][pState] == null) {
						continue;
					}
					// if (!allowedStates[start][end][pState]) continue;
					UnaryRule[] unaries = grammar
							.getClosedSumUnaryRulesByParent(pState);
					int nParentStates = numSubStatesArray[pState]; // ==
																	// scores[0].length;
					for (int r = 0; r < unaries.length; r++) {
						UnaryRule ur = unaries[r];
						// List<UnaryRule> urules =
						// grammar.getUnaryRulesByParent(pState);//
						// for (UnaryRule ur : urules){
						int cState = ur.childState;
						if ((pState == cState))
							continue;// && (np == cp))continue;
						if (iScorePostU[start][end][cState] == null)
							continue;
						// if (!allowedStates[start][end][cState]) continue;
						// new loop over all substates
						double[][] scores = ur.getScores2();
						int nChildStates = numSubStatesArray[cState]; // ==
																		// scores.length;
						double ruleScore = 0;
						for (int cp = 0; cp < nChildStates; cp++) {
							double cIS = iScorePreU[start][end][cState][cp];
							// if (cIS == 0) continue;
							if (cIS < thresh2)
								continue;
							// if (!allowedSubStates[start][end][cState][cp])
							// continue;

							if (scores[cp] == null)
								continue;
							for (int np = 0; np < nParentStates; np++) {
								// if
								// (!allowedSubStates[start][end][pState][np])
								// continue;
								double pOS = oScorePreU[start][end][pState][np];
								if (pOS < thresh2)
									continue;

								double ruleS = scores[cp][np];
								if (ruleS == 0)
									continue;
								ruleScore += (pOS * ruleS * cIS)
										/ logNormalizer;
							}
						}

						// log_threshold is a penalty on unaries, to control
						// precision
						double scale = 1.0;/*
											 * Math.pow(GrammarTrainer.SCALE,
											 * oScale
											 * [start][end][pState]+iScale[
											 * start][end][cState]
											 * -iScale[0][length][0]);
											 */
						double childScore = maxcScore[start][end][cState];
						double gScore = ruleScore / unaryPenalty * childScore
								* scale;
						if (gScore > maxcScoreStartEnd[pState]) {
							maxcScoreStartEnd[pState] = gScore;
							maxcChild[start][end][pState] = cState;
						}
					}
				}
				maxcScore[start][end] = maxcScoreStartEnd;
			}
		}
	}

	// public void constrainChart(){
	// viScore = new double[length][length + 1][];
	// viScore[0][length] = new double[1];
	// iScorePreU = new double[length][length + 1][][];
	// iScorePostU = new double[length][length + 1][][];
	// oScorePreU = new double[length][length + 1][][];
	// oScorePostU = new double[length][length + 1][][];
	// allowedSubStates = new boolean[length][length+1][][];
	// allowedStates = new boolean[length][length+1][];
	//
	// for (int start = 0; start < length; start++) {
	// for (int end = start + 1; end <= length; end++) {
	// iScorePreU[start][end] = new double[numStates][];
	// iScorePostU[start][end] = new double[numStates][];
	// oScorePreU[start][end] = new double[numStates][];
	// oScorePostU[start][end] = new double[numStates][];
	// allowedStates[start][end] = new boolean[numStates];
	// allowedSubStates[start][end] = new boolean[numStates][];
	//
	// //for (int pState=0; pState<numSubStatesArray.length; pState++){ //
	// for (Integer pState : possibleStates[start][end]){
	// allowedStates[start][end][pState] = true;
	// }
	// }
	// }
	//
	// }

	public double doInsideOutsideScores(List<String> sentence,
			Tree<StateSet> tree) {
		final boolean noSmoothing = true;
		doPreParses(sentence, tree, noSmoothing);

		// clearArrays();
		length = (short) sentence.size();

		Grammar curGrammar = grammarCascade[endLevel - startLevel + 1];
		Lexicon curLexicon = lexiconCascade[endLevel - startLevel + 1];

		double initVal = 0;
		int level = isBaseline ? 1 : endLevel;
		// ensureGoldTreeSurvives(tree, level);
		createArrays(isBaseline, curGrammar.numStates, curGrammar.numSubStates,
				level, initVal, false/* !isBaseline */); // remove false

		initializeChart(sentence, curLexicon, false, noSmoothing);
		doConstrainedInsideScores(curGrammar, false, false);
		logLikelihood = Math.log(iScorePostU[0][length][0][0]); // +
																// (100*iScale[0][length][0]);

		// System.out.println("Found a parse for sentence with length "+length+". The LL is "+logLikelihood+".");

		oScorePreU[0][length][0][0] = 1.0;
		doConstrainedOutsideScores(curGrammar, false, false);
		return logLikelihood;
	}

	public double doConstrainedInsideOutsideScores(List<String> sentence,
			boolean[][][][] cons) {
		final boolean noSmoothing = true;
		clearArrays();
		// doPreParses(sentence,null,noSmoothing);
		Grammar curGrammar = grammarCascade[endLevel - startLevel + 1];
		Lexicon curLexicon = lexiconCascade[endLevel - startLevel + 1];
		numSubStatesArray = curGrammar.numSubStates;
		length = (short) sentence.size();
		setConstraints(cons);

		double initVal = 0;
		int level = isBaseline ? 1 : endLevel;
		// ensureGoldTreeSurvives(tree, level);
		createArrays(true, curGrammar.numStates, curGrammar.numSubStates,
				level, initVal, false/* !isBaseline */); // remove false

		initializeChart(sentence, curLexicon, false, noSmoothing);
		doConstrainedInsideScores(curGrammar, false, false);
		logLikelihood = Math.log(iScorePostU[0][length][0][0]); // +
																// (100*iScale[0][length][0]);

		// System.out.println("Found a parse for sentence with length "+length+". The LL is "+logLikelihood+".");

		oScorePreU[0][length][0][0] = 1.0;
		doConstrainedOutsideScores(curGrammar, false, false);
		return logLikelihood;
	}

	protected void pruneChart(double threshold, short[] numSubStatesArray,
			int level) {
		int totalStates = 0, previouslyPossible = 0, nowPossible = 0;
		// threshold = Double.NEGATIVE_INFINITY;

		double sentenceProb = (level < 1) ? viScore[0][length][0]
				: iScorePostU[0][length][0][0];
		// double sentenceScale = iScale[0][length][0];//+1.0 for oScale
		if (level < 1)
			nowPossible = totalStates = previouslyPossible = length;
		int startDiff = (level < 0) ? 2 : 1;
		for (int diff = startDiff; diff <= length; diff++) {
			for (int start = 0; start < (length - diff + 1); start++) {
				int end = start + diff;
				int lastState = (level < 0) ? 1 : numSubStatesArray.length;
				for (int state = 0; state < lastState; state++) {
					if (diff > 1 && !grammarTags[state])
						continue;
					// boolean allFalse = true;
					if (level == 0) {
						if (!vAllowedStates[start][end]) {
							allowedSubStates[start][end][state] = null;//
							// allowedStates[start][end][state]=false;
							totalStates++;
							continue;
						}
					} else if (level > 0) {
						// if (!allowedStates[start][end][state]) {
						// totalStates+=numSubStatesArray[state];
						// continue;
						// }
					}
					if (level < 1) {
						totalStates++;
						previouslyPossible++;
						double iS = viScore[start][end][state];
						double oS = voScore[start][end][state];
						if (iS == Double.NEGATIVE_INFINITY
								|| oS == Double.NEGATIVE_INFINITY) {
							if (level == 0)
								allowedSubStates[start][end][state] = null;// allowedStates[start][end][state]
																			// =
																			// false;
							else
								/* level==-1 */vAllowedStates[start][end] = false;
							continue;
						}
						double posterior = iS + oS - sentenceProb;
						if (posterior > threshold) {
							boolean[] tmp = new boolean[numSubStatesArray[state]];
							Arrays.fill(tmp, true);
							if (level == 0)
								allowedSubStates[start][end][state] = tmp;// allowedStates[start][end][state]=true;
							else
								vAllowedStates[start][end] = true;
							// spanMass[start][end]+=Math.exp(posterior);
							nowPossible++;
						} else {
							if (level == 0)
								allowedSubStates[start][end][state] = null;// allowedStates[start][end][state]
																			// =
																			// false;
							else
								vAllowedStates[start][end] = false;
						}
						continue;
					}
					// level >= 1 -> iterate over substates
					boolean nonePossible = true;
					for (int substate = 0; substate < numSubStatesArray[state]; substate++) {
						totalStates++;
						if (!allowedSubStates[start][end][state][substate])
							continue;
						previouslyPossible++;
						// double iS = iScore[start][end][state][substate];
						// double oS = oScore[start][end][state][substate];
						double iS = iScorePostU[start][end][state][substate];
						double oS = oScorePostU[start][end][state][substate];

						if (iS == Double.NEGATIVE_INFINITY
								|| oS == Double.NEGATIVE_INFINITY) {
							allowedSubStates[start][end][state][substate] = false;
							continue;
						}
						double posterior = iS + oS - sentenceProb;
						if (posterior > threshold) {
							allowedSubStates[start][end][state][substate] = true;
							nowPossible++;
							// spanMass[start][end]+=Math.exp(posterior);
							nonePossible = false;
						} else {
							allowedSubStates[start][end][state][substate] = false;
						}

						/*
						 * if (thisScale>sentenceScale){ posterior *=
						 * Math.pow(GrammarTrainer
						 * .SCALE,thisScale-sentenceScale); }
						 */
						// }
						// allowedStates[start][end][state][0] = !allFalse;

						// int thisScale =
						// iScale[start][end][state]+oScale[start][end][state];
						/*
						 * if (sentenceScale>thisScale){ // too small anyways
						 * allowedStates[start][end][state][0] = false;
						 * continue; }
						 */
					}
					if (nonePossible)
						allowedSubStates[start][end][state] = null;// allowedStates[start][end][state]=false;
				}
			}
		}
		/*
		 * System.out.print("["); for(int st=0; st<length; st++){ for(int en=0;
		 * en<=length; en++){ System.out.print(spanMass[st][en]); if (en<length)
		 * System.out.print(", "); } if (st<length-1) System.out.print(";\n"); }
		 * System.out.print("]\n");
		 */
		String parse = "";
		if (level == -1)
			parse = "Pre-Parse";
		else if (level == 0)
			parse = "X-Bar";
		else
			parse = ((int) Math.pow(2, level)) + "-Substates";
		// System.out.print(parse+". NoPruning: " +totalStates +
		// ". Before: "+previouslyPossible+". After: "+nowPossible+".");
	}

	// recently updated, slav (may 2nd)
	public void incrementExpectedCounts(Linearizer linearizer, double[] probs,
			Grammar grammar, Lexicon lexicon, List<StateSet> sentence,
			boolean hardCounts, int lexiconOffset) {
		throw new Error("Currently disabled");
		// numSubStatesArray = grammar.numSubStates;
		// double tree_score = iScorePostU[0][length][0][0];
		// if (tree_score==0){
		// System.out.println("Training tree has zero probability - presumably underflow!");
		// System.exit(-1);
		// }
		//
		// for (int start = 0; start < length; start++) {
		// final int lastState = numSubStatesArray.length;
		// String word = sentence.get(start);
		// for (int tag=0; tag<lastState; tag++){
		// if (grammar.isGrammarTag(tag)) continue;
		// if (allowedSubStates[start][start+1][tag] == null) continue;
		// int startIndexWord = linearizer.getLinearIndex(word, tag);
		// if (startIndexWord==-1) continue;
		// startIndexWord += lexiconOffset;
		// final int nSubStates = numSubStatesArray[tag];
		// for (short substate=0; substate<nSubStates; substate++) {
		// //weight by the probability of seeing the tag and word together,
		// given the sentence
		// double weight = 1;
		// weight = iScorePreU[start][start+1][tag][substate] / tree_score *
		// oScorePostU[start][start+1][tag][substate];
		// probs[startIndexWord+substate] += weight;
		// }
		// }
		// }
		//
		//
		// for (int diff = 1; diff <= length; diff++) {
		// for (int start = 0; start < (length - diff + 1); start++) {
		// int end = start + diff;
		// final int lastState = numSubStatesArray.length;
		// for (short pState=0; pState<lastState; pState++){
		// if (diff==1) continue; // there are no binary rules that span over 1
		// symbol only
		// //if (iScore[start][end][pState] == null) { continue; }
		// //if (!grammarTags[pState]) continue;
		// if (allowedSubStates[start][end][pState] == null) continue;
		// final int nParentSubStates = numSubStatesArray[pState];
		// BinaryRule[] parentRules = grammar.splitRulesWithP(pState);
		// for (int i = 0; i < parentRules.length; i++) {
		// BinaryRule r = parentRules[i];
		// short lState = r.leftChildState;
		// short rState = r.rightChildState;
		//
		// int thisStartIndex = linearizer.getLinearIndex(new BinaryRule(pState,
		// lState, rState));
		//
		// int narrowR = narrowRExtent[start][lState];
		// boolean iPossibleL = (narrowR < end); // can this left constituent
		// leave space for a right constituent?
		// if (!iPossibleL) { continue; }
		//
		// int narrowL = narrowLExtent[end][rState];
		// boolean iPossibleR = (narrowL >= narrowR); // can this right
		// constituent fit next to the left constituent?
		// if (!iPossibleR) { continue; }
		//
		// int min1 = narrowR;
		// int min2 = wideLExtent[end][rState];
		// int min = (min1 > min2 ? min1 : min2); // can this right constituent
		// stretch far enough to reach the left constituent?
		// if (min > narrowL) { continue; }
		//
		// int max1 = wideRExtent[start][lState];
		// int max2 = narrowL;
		// int max = (max1 < max2 ? max1 : max2); // can this left constituent
		// stretch far enough to reach the right constituent?
		// if (min > max) { continue; }
		//
		// // new: loop over all substates
		// double[][][] scores = r.getScores2();
		// for (int split = min; split <= max; split++) {
		// if (allowedSubStates[start][split][lState] == null) continue;
		// if (allowedSubStates[split][end][rState] == null) continue;
		// int curInd = 0;
		//
		// for (int lp = 0; lp < scores.length; lp++) {
		// double lcIS = iScorePostU[start][split][lState][lp];
		//
		// for (int rp = 0; rp < scores[0].length; rp++) {
		// if (scores[lp][rp]==null) continue;
		// double rcIS = iScorePostU[split][end][rState][rp];
		//
		// for (int np = 0; np < nParentSubStates; np++) {
		// curInd++;
		// if (lcIS == 0) { continue; }
		// if (rcIS == 0) { continue; }
		// if (!allowedSubStates[start][end][pState][np]) continue;
		// double pOS = oScorePostU[start][end][pState][np];
		// if (pOS==0) { continue; }
		//
		// double rS = scores[lp][rp][np];
		// if (rS==0) { continue; }
		//
		// double ruleCount = (hardCounts) ? 1 : (rS * lcIS / tree_score) * rcIS
		// * pOS;
		// probs[thisStartIndex + curInd-1] += ruleCount;
		// }
		// }
		// }
		// }
		// }
		// }
		// final int lastStateU = numSubStatesArray.length;
		// for (short pState=0; pState<lastStateU; pState++){
		// //if (!grammarTags[pState]) continue;
		// //if (iScore[start][end][pState] == null) { continue; }
		// //if (!allowedStates[start][end][pState][0]) continue;
		// if (allowedSubStates[start][end][pState] == null) continue;
		// List<UnaryRule> unaries = grammar.getUnaryRulesByParent(pState);
		// int nParentSubStates = numSubStatesArray[pState];
		// for (UnaryRule ur : unaries) {
		// short cState = ur.childState;
		// if ((pState == cState)) continue;// && (np == cp))continue;
		// if (allowedSubStates[start][end][cState] == null) continue;
		// //new loop over all substates
		// double[][] scores = ur.getScores2();
		// int thisStartIndex = linearizer.getLinearIndex(new UnaryRule(pState,
		// cState));
		// // if (thisStartIndex<0) continue; // a unary chain rule...
		// int curInd = 0;
		// for (int cp = 0; cp < scores.length; cp++) {
		// if (scores[cp]==null) continue;
		// double cIS = iScorePreU[start][end][cState][cp];
		// for (int np = 0; np < nParentSubStates; np++) {
		// curInd++;
		// if (cIS == 0) { continue; }
		// if (!allowedSubStates[start][end][pState][np]) continue;
		// double rS = scores[cp][np];
		// if (rS==0){ continue; }
		//
		// double pOS = oScorePreU[start][end][pState][np];
		//
		// double ruleCount = (hardCounts) ? 1 : (rS * cIS / tree_score) * pOS;
		// probs[thisStartIndex + curInd-1] += ruleCount;
		// }
		// }
		// }
		// }
		// }
		// }
	}

	private void setConstraints(boolean[][][][] allowedSubStates2) {
		allowedSubStates = new boolean[length][length + 1][][];
		for (int start = 0; start < length; start++) {
			for (int end = start + 1; end <= length; end++) {
				allowedSubStates[start][end] = new boolean[numStates][];
				for (int state = 0; state < numStates; state++) {
					if (allowedSubStates2 == null) { // then we parse without
														// constraints
						boolean[] tmp = new boolean[numSubStatesArray[state]];
						Arrays.fill(tmp, true);
						allowedSubStates[start][end][state] = tmp;
					} else if (allowedSubStates2[start][end][state] != null) {
						allowedSubStates[start][end][state] = new boolean[numSubStatesArray[state]];
						for (int substate = 0; substate < allowedSubStates2[start][end][state].length; substate++) {
							if (allowedSubStates2[start][end][state][substate]) {
								allowedSubStates[start][end][state][2 * substate] = true;
								if (state != 0)
									allowedSubStates[start][end][state][2 * substate + 1] = true;
							}
						}
					}
				}
			}
		}
	}

	public double[][][][] getPreUnaryInsideScores() {
		return iScorePreU;
	}

	public double[][][][] getPostUnaryInsideScores() {
		return iScorePostU;
	}

	public double[][][][] getPreUnaryOutsideScores() {
		return oScorePreU;
	}

	public double[][][][] getPostUnaryOutsideScores() {
		return oScorePostU;
	}
}
