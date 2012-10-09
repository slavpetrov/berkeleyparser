/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.berkeley.nlp.discPCFG.Linearizer;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.math.SloppyMath;
import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.Numberer;
import edu.berkeley.nlp.util.ScalingTools;

/**
 * @author petrov
 * 
 */
public class ConstrainedTwoChartsParser extends ConstrainedArrayParser {
	/**
	 * inside and outside scores; start idx, end idx, state, substate ->
	 * logProb/prob
	 */
	/** NEW: we now have two charts one before applying unaries and one after: */
	protected double[][][][] iScorePreU, iScorePostU;
	protected double[][][][] oScorePreU, oScorePostU;

	protected int[][][] iScale;
	protected int[][][] oScale;

	protected double[][][] maxcScore; // start, end, state --> logProb
	protected double[][][] maxsScore; // start, end, state --> logProb
	protected int[][][] maxcSplit; // start, end, state -> split position
	protected int[][][] maxcChild; // start, end, state -> unary child (if any)
	protected int[][][] maxcLeftChild; // start, end, state -> left child
	protected int[][][] maxcRightChild; // start, end, state -> right child

	public boolean[][][][] allowedSubStates;
	double[] tmpCountsArray;

	boolean[] grammarTags;
	double[] unscaledScoresToAdd;
	int[][] goldBinaryProduction;
	int[][] goldUnaryParent;
	int[][] goldUnaryChild;
	int[] goldPOS;

	SpanPredictor spanPredictor;
	public double[][][] spanScores;
	int[] stateClass;

	// double edgesTouched;
	// int sentencesParsed;

	public ConstrainedTwoChartsParser(Grammar gr, Lexicon lex, SpanPredictor sp) {
		grammar = gr;
		lexicon = lex;
		spanPredictor = sp;
		if (spanPredictor != null)
			stateClass = spanPredictor.getStateClass();
		numSubStatesArray = grammar.numSubStates.clone();
		grammarTags = grammar.isGrammarTag;
		numStates = grammar.numStates;
		scoresToAdd = new double[(int) ArrayUtil.max(numSubStatesArray)];
		unscaledScoresToAdd = new double[scoresToAdd.length];
		tmpCountsArray = new double[scoresToAdd.length * scoresToAdd.length
				* scoresToAdd.length];
		tagNumberer = Numberer.getGlobalNumberer("tags");
		arraySize = 0;
	}

	void doConstrainedInsideScores(final boolean viterbi) {
		double initVal = 0;
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
						int nRuleStates = scores[0][0] == null ? nParentStates
								: scores[0][0].length;
						int divisor = nParentStates / nRuleStates;

						for (int split = min; split <= max; split++) {
							boolean changeThisRound = false;
							if (allowedSubStates[start][split][lState] == null)
								continue;
							if (allowedSubStates[split][end][rState] == null)
								continue;

							for (int lp = 0; lp < nLeftChildStates; lp++) {
								double lS = iScorePostU[start][split][lState][lp];
								if (lS == initVal)
									continue;

								for (int rp = 0; rp < nRightChildStates; rp++) {
									// if (scores[lp][rp]==null) continue;
									double rS = iScorePostU[split][end][rState][rp];
									if (rS == initVal)
										continue;

									double tmp = lS * rS;
									if (nRuleStates == nParentStates) {
										if (scores[lp][rp] == null)
											continue;
										for (int np = 0; np < nParentStates; np++) {
											if (!allowedSubStates[start][end][pState][np])
												continue;
											double pS = scores[lp][rp][np];
											if (pS == initVal)
												continue;

											double thisRound = pS * tmp;

											if (viterbi) {
												unscaledScoresToAdd[np] = Math
														.max(unscaledScoresToAdd[np],
																thisRound);
											} else {
												unscaledScoresToAdd[np] += thisRound;
											}

											changeThisRound = true;
										}
									} else {
										for (int np = 0; np < nRuleStates; np++) {
											double pS = scores[lp / divisor][rp
													/ divisor][np];
											if (pS == initVal)
												continue;

											double thisRound = pS * tmp;

											for (int nnp = 0; nnp < divisor; nnp++) {
												int p = np * divisor + nnp;
												if (!allowedSubStates[start][end][pState][p])
													continue;
												if (viterbi) {
													unscaledScoresToAdd[p] = Math
															.max(unscaledScoresToAdd[p],
																	thisRound);
												} else {
													unscaledScoresToAdd[p] += thisRound;
												}
											}

											changeThisRound = true;
										}

									}
								}
							}
							if (!changeThisRound)
								continue;
							somethingChanged = true;
							// boolean firstTime = false;
							int parentScale = iScale[start][end][pState];
							int currentScale = iScale[start][split][lState]
									+ iScale[split][end][rState];
							currentScale = ScalingTools.scaleArray(
									unscaledScoresToAdd, currentScale);

							if (parentScale != currentScale) {
								if (parentScale == Integer.MIN_VALUE) { // first
																		// time
																		// to
																		// build
																		// this
																		// span
									iScale[start][end][pState] = currentScale;
								} else {
									int newScale = Math.max(currentScale,
											parentScale);
									ScalingTools.scaleArrayToScale(
											unscaledScoresToAdd, currentScale,
											newScale);
									ScalingTools.scaleArrayToScale(
											iScorePreU[start][end][pState],
											parentScale, newScale);
									iScale[start][end][pState] = newScale;
								}
							}
							for (int np = 0; np < nParentStates; np++) {
								if (viterbi) {
									iScorePreU[start][end][pState][np] = Math
											.max(iScorePreU[start][end][pState][np],
													unscaledScoresToAdd[np]);
								} else {
									iScorePreU[start][end][pState][np] += unscaledScoresToAdd[np];
								}
							}
							Arrays.fill(unscaledScoresToAdd, 0);
						}
					}
					if (somethingChanged) {
						// apply span predictions
						// if (spanScores!=null){
						// double val = spanScores[start][end][0];
						// for (int np = 0; np < nParentStates; np++){
						// iScorePreU[start][end][pState][np] *= val;
						// }
						// }

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
					if (iScorePreU[start][end][pState] == null)
						continue;
					// Should be: Closure under sum-product:
					UnaryRule[] unaries = grammar
							.getClosedSumUnaryRulesByParent(pState);
					// UnaryRule[] unaries =
					// grammar.getUnaryRulesByParent(pState).toArray(new
					// UnaryRule[0]);

					int nParentStates = numSubStatesArray[pState];// scores[0].length;
					int parentScale = iScale[start][end][pState];
					int scaleBeforeUnaries = parentScale;
					boolean somethingChanged = false;

					for (int r = 0; r < unaries.length; r++) {
						UnaryRule ur = unaries[r];
						int cState = ur.childState;
						if ((pState == cState))
							continue;

						if (allowedSubStates[start][end][cState] == null)
							continue;
						if (iScorePreU[start][end][cState] == null)
							continue;

						double[][] scores = ur.getScores2();
						boolean changeThisRound = false;
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
								if (np > scores[cp].length)
									System.out.println("how come?");
								double pS = scores[cp][np];
								if (pS == initVal)
									continue;

								double thisRound = iS * pS;

								if (viterbi) {
									unscaledScoresToAdd[np] = Math.max(
											unscaledScoresToAdd[np], thisRound);
								} else {
									unscaledScoresToAdd[np] += thisRound;
								}

								somethingChanged = true;
								changeThisRound = true;
							}
						}
						if (!changeThisRound)
							continue;
						// boolean firstTime = false;
						int currentScale = iScale[start][end][cState];
						currentScale = ScalingTools.scaleArray(
								unscaledScoresToAdd, currentScale);
						if (parentScale != currentScale) {
							if (parentScale == Integer.MIN_VALUE) { // first
																	// time to
																	// build
																	// this span
								parentScale = currentScale;
							} else {
								int newScale = Math.max(currentScale,
										parentScale);
								ScalingTools.scaleArrayToScale(
										unscaledScoresToAdd, currentScale,
										newScale);
								ScalingTools.scaleArrayToScale(
										iScorePostU[start][end][pState],
										parentScale, newScale);
								parentScale = newScale;
							}
						}
						for (int np = 0; np < nParentStates; np++) {
							if (viterbi) {
								iScorePostU[start][end][pState][np] = Math.max(
										iScorePostU[start][end][pState][np],
										unscaledScoresToAdd[np]);
							} else {
								iScorePostU[start][end][pState][np] += unscaledScoresToAdd[np];
							}
						}
						Arrays.fill(unscaledScoresToAdd, 0);
					}
					if (somethingChanged) {
						int newScale = Math
								.max(scaleBeforeUnaries, parentScale);
						ScalingTools.scaleArrayToScale(
								iScorePreU[start][end][pState],
								scaleBeforeUnaries, newScale);
						ScalingTools.scaleArrayToScale(
								iScorePostU[start][end][pState], parentScale,
								newScale);
						iScale[start][end][pState] = newScale;
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
					// in any case copy/add the scores from before
					for (int np = 0; np < nParentStates; np++) {
						double val = iScorePreU[start][end][pState][np];
						if (val > 0) {
							if (viterbi) {
								iScorePostU[start][end][pState][np] = Math.max(
										iScorePostU[start][end][pState][np],
										val);
							} else {
								iScorePostU[start][end][pState][np] += val;
							}
						}
					}
				}
			}
		}
	}

	void doConstrainedOutsideScores(final boolean viterbi) {
		double initVal = 0;
		// Arrays.fill(scoresToAdd,initVal);
		for (int diff = length; diff >= 1; diff--) {
			for (int start = 0; start + diff <= length; start++) {
				int end = start + diff;
				// do unaries

				// apply span predictions
				// if (spanScores!=null){
				// double val = spanScores[start][end][0];
				// if (val != 1){
				// for (int pState=0; pState<numSubStatesArray.length;
				// pState++){
				// if (allowedSubStates[start][end][pState]==null) continue;
				// for (int np = 0; np < numSubStatesArray[pState]; np++){
				// oScorePreU[start][end][pState][np] *= val;
				// }
				// }
				// }
				// }

				for (int cState = 0; cState < numSubStatesArray.length; cState++) {
					if (allowedSubStates[start][end][cState] == null)
						continue;
					if (end - start > 1 && !grammarTags[cState])
						continue;
					if (iScorePostU[start][end][cState] == null)
						continue;
					// Should be: Closure under sum-product:
					// UnaryRule[] rules =
					// grammar.getClosedSumUnaryRulesByParent(pState);
					UnaryRule[] rules = grammar
							.getClosedSumUnaryRulesByChild(cState);
					// UnaryRule[] rules =
					// grammar.getClosedViterbiUnaryRulesByParent(pState);
					// For now:
					// UnaryRule[] rules =
					// grammar.getUnaryRulesByChild(cState).toArray(new
					// UnaryRule[0]);
					int nChildStates = numSubStatesArray[cState];
					boolean somethingChanged = false;
					int childScale = oScale[start][end][cState];
					int scaleBeforeUnaries = childScale;

					for (int r = 0; r < rules.length; r++) {
						UnaryRule ur = rules[r];
						int pState = ur.parentState;
						if ((pState == cState))
							continue;
						if (allowedSubStates[start][end][pState] == null)
							continue;
						if (iScorePostU[start][end][pState] == null)
							continue;

						int nParentStates = numSubStatesArray[pState];

						double[][] scores = ur.getScores2();
						boolean changeThisRound = false;
						for (int cp = 0; cp < nChildStates; cp++) {
							if (scores[cp] == null)
								continue;
							if (!allowedSubStates[start][end][cState][cp])
								continue;
							for (int np = 0; np < nParentStates; np++) {
								if (!allowedSubStates[start][end][pState][np])
									continue;
								double pS = scores[cp][np];
								if (pS == initVal)
									continue;

								double oS = oScorePreU[start][end][pState][np];
								if (oS == initVal)
									continue;

								double thisRound = oS * pS;

								if (viterbi) {
									unscaledScoresToAdd[cp] = Math.max(
											unscaledScoresToAdd[cp], thisRound);
								} else {
									unscaledScoresToAdd[cp] += thisRound;
								}

								somethingChanged = true;
								changeThisRound = true;
							}
						}
						if (!changeThisRound)
							continue;
						int currentScale = oScale[start][end][pState];
						currentScale = ScalingTools.scaleArray(
								unscaledScoresToAdd, currentScale);
						if (childScale != currentScale) {
							if (childScale == Integer.MIN_VALUE) { // first time
																	// to build
																	// this span
								childScale = currentScale;
							} else {
								int newScale = Math.max(currentScale,
										childScale);
								ScalingTools.scaleArrayToScale(
										unscaledScoresToAdd, currentScale,
										newScale);
								ScalingTools.scaleArrayToScale(
										oScorePostU[start][end][cState],
										childScale, newScale);
								childScale = newScale;
							}
						}
						for (int cp = 0; cp < nChildStates; cp++) {
							if (viterbi) {
								oScorePostU[start][end][cState][cp] = Math.max(
										oScorePostU[start][end][cState][cp],
										unscaledScoresToAdd[cp]);
							} else {
								oScorePostU[start][end][cState][cp] += unscaledScoresToAdd[cp];
							}
						}
						Arrays.fill(unscaledScoresToAdd, initVal);
					}
					if (somethingChanged) {
						int newScale = Math.max(scaleBeforeUnaries, childScale);
						ScalingTools.scaleArrayToScale(
								oScorePreU[start][end][cState],
								scaleBeforeUnaries, newScale);
						ScalingTools.scaleArrayToScale(
								oScorePostU[start][end][cState], childScale,
								newScale);
						oScale[start][end][cState] = newScale;
					}
					// copy/add the entries where the unaries were not useful
					for (int cp = 0; cp < nChildStates; cp++) {
						double val = oScorePreU[start][end][cState][cp];
						if (val > 0) {
							if (viterbi) {
								oScorePostU[start][end][cState][cp] = Math.max(
										oScorePostU[start][end][cState][cp],
										val);
							} else {
								oScorePostU[start][end][cState][cp] += val;
							}
						}
					}
				}

				// do binaries
				if (diff == 1)
					continue; // there is no space for a binary
				for (int pState = 0; pState < numSubStatesArray.length; pState++) {
					if (allowedSubStates[start][end][pState] == null)
						continue;
					final int nParentStates = numSubStatesArray[pState];
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
						int nRuleStates = scores[0][0] == null ? nParentStates
								: scores[0][0].length;
						int divisor = nParentStates / nRuleStates;

						for (int split = min; split <= max; split++) {

							if (allowedSubStates[start][split][lState] == null)
								continue;
							if (allowedSubStates[split][end][rState] == null)
								continue;
							if (split - start > 1 && !grammarTags[lState])
								continue;
							if (end - split > 1 && !grammarTags[rState])
								continue;

							boolean somethingChanged = false;
							for (int lp = 0; lp < nLeftChildStates; lp++) {
								double lS = iScorePostU[start][split][lState][lp];
								if (lS == initVal)
									continue;

								for (int rp = 0; rp < nRightChildStates; rp++) {
									// if (scores[lp][rp]==null) continue;
									double rS = iScorePostU[split][end][rState][rp];
									if (rS == initVal)
										continue;

									if (nRuleStates == nParentStates) {
										if (scores[lp][rp] == null)
											continue;
										for (int np = 0; np < nParentStates; np++) {
											double pS = scores[lp][rp][np];
											if (pS == initVal)
												continue;

											double oS = oScorePostU[start][end][pState][np];
											if (oS == initVal)
												continue;
											// if
											// (!allowedSubStates[start][end][pState][np])
											// continue;

											double thisRoundL = pS * rS * oS;
											double thisRoundR = pS * lS * oS;

											if (viterbi) {
												scoresToAdd[lp] = Math.max(
														scoresToAdd[lp],
														thisRoundL);
												unscaledScoresToAdd[rp] = Math
														.max(unscaledScoresToAdd[rp],
																thisRoundR);
											} else {
												scoresToAdd[lp] += thisRoundL;
												unscaledScoresToAdd[rp] += thisRoundR;
											}
											somethingChanged = true;
										}
									} else {
										for (int np = 0; np < nParentStates; np++) {
											double pS = scores[lp / divisor][rp
													/ divisor][np / divisor];
											if (pS == initVal)
												continue;

											double oS = oScorePostU[start][end][pState][np];
											if (oS == initVal)
												continue;
											// if
											// (!allowedSubStates[start][end][pState][np])
											// continue;

											double thisRoundL = pS * rS * oS;
											double thisRoundR = pS * lS * oS;

											if (viterbi) {
												scoresToAdd[lp] = Math.max(
														scoresToAdd[lp],
														thisRoundL);
												unscaledScoresToAdd[rp] = Math
														.max(unscaledScoresToAdd[rp],
																thisRoundR);
											} else {
												scoresToAdd[lp] += thisRoundL;
												unscaledScoresToAdd[rp] += thisRoundR;
											}
											somethingChanged = true;
										}

									}
								}
							}
							if (!somethingChanged)
								continue;

							if (DoubleArrays.max(scoresToAdd) != 0) {// oScale[start][end][pState]!=Integer.MIN_VALUE
																		// &&
																		// iScale[split][end][rState]!=Integer.MIN_VALUE){
								int leftScale = oScale[start][split][lState];
								int currentScale = oScale[start][end][pState]
										+ iScale[split][end][rState];
								currentScale = ScalingTools.scaleArray(
										scoresToAdd, currentScale);
								if (leftScale != currentScale) {
									if (leftScale == Integer.MIN_VALUE) { // first
																			// time
																			// to
																			// build
																			// this
																			// span
										oScale[start][split][lState] = currentScale;
									} else {
										int newScale = Math.max(currentScale,
												leftScale);
										ScalingTools.scaleArrayToScale(
												scoresToAdd, currentScale,
												newScale);
										ScalingTools
												.scaleArrayToScale(
														oScorePreU[start][split][lState],
														leftScale, newScale);
										oScale[start][split][lState] = newScale;
									}
								}
								for (int cp = 0; cp < nLeftChildStates; cp++) {
									if (scoresToAdd[cp] > initVal) {
										if (viterbi) {
											oScorePreU[start][split][lState][cp] = Math
													.max(oScorePreU[start][split][lState][cp],
															scoresToAdd[cp]);
										} else {
											oScorePreU[start][split][lState][cp] += scoresToAdd[cp];
										}
									}
								}
								Arrays.fill(scoresToAdd, 0);
							}

							if (DoubleArrays.max(unscaledScoresToAdd) != 0) {// oScale[start][end][pState]!=Integer.MIN_VALUE
																				// &&
																				// iScale[start][split][lState]!=Integer.MIN_VALUE){
								int rightScale = oScale[split][end][rState];
								int currentScale = oScale[start][end][pState]
										+ iScale[start][split][lState];
								if (currentScale == Integer.MIN_VALUE)
									System.out.println("shhaaa");
								currentScale = ScalingTools.scaleArray(
										unscaledScoresToAdd, currentScale);
								if (rightScale != currentScale) {
									if (rightScale == Integer.MIN_VALUE) { // first
																			// time
																			// to
																			// build
																			// this
																			// span
										oScale[split][end][rState] = currentScale;
									} else {
										int newScale = Math.max(currentScale,
												rightScale);
										ScalingTools.scaleArrayToScale(
												unscaledScoresToAdd,
												currentScale, newScale);
										ScalingTools.scaleArrayToScale(
												oScorePreU[split][end][rState],
												rightScale, newScale);
										oScale[split][end][rState] = newScale;
									}
								}
								for (int cp = 0; cp < nRightChildStates; cp++) {
									if (unscaledScoresToAdd[cp] > initVal) {
										if (viterbi) {
											oScorePreU[split][end][rState][cp] = Math
													.max(oScorePreU[split][end][rState][cp],
															unscaledScoresToAdd[cp]);
										} else {
											oScorePreU[split][end][rState][cp] += unscaledScoresToAdd[cp];
										}
									}
								}
								Arrays.fill(unscaledScoresToAdd, 0);
							}
						}
					}
				}
			}
		}
	}

	void initializeChart(List<StateSet> sentence, boolean noSmoothing,
			List<String> posTags) {
		final boolean useGoldPOS = (posTags != null);
		int start = 0;
		int end = start + 1;
		for (StateSet word : sentence) {
			end = start + 1;
			int goldTag = -1;
			if (useGoldPOS)
				goldTag = tagNumberer.number(posTags.get(start));

			for (short tag = 0; tag < numSubStatesArray.length; tag++) {
				if (allowedSubStates[start][end][tag] == null)
					continue;
				if (grammarTags[tag])
					continue;
				if (useGoldPOS && tag != goldTag)
					continue;

				narrowRExtent[start][tag] = end;
				narrowLExtent[end][tag] = start;
				wideRExtent[start][tag] = end;
				wideLExtent[end][tag] = start;
				// double[] lexiconScores =
				// lexicon.score(word.getWord(),tag,start,noSmoothing,false);
				double[] lexiconScores = lexicon.score(word, tag, noSmoothing,
						false);
				// if (!logProbs) iScale[start][end][tag] =
				// scaleArray(lexiconScores,0);
				iScale[start][end][tag] = 0;
				for (short n = 0; n < lexiconScores.length; n++) {
					if (!allowedSubStates[start][end][tag][n])
						continue;
					double prob = lexiconScores[n];
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

	@Override
	protected void createArrays() {
		if (arraySize < length) { // if we haven't seen such a long sentence
									// before, allocate arrays
			arraySize = length;
			iScorePreU = new double[length][length + 1][][];
			iScorePostU = new double[length][length + 1][][];
			oScorePreU = new double[length][length + 1][][];
			oScorePostU = new double[length][length + 1][][];
			iScale = new int[length][length + 1][];
			oScale = new int[length][length + 1][];

			for (int start = 0; start < length; start++) {
				for (int end = start + 1; end <= length; end++) {
					iScorePreU[start][end] = new double[numStates][];
					iScorePostU[start][end] = new double[numStates][];
					oScorePreU[start][end] = new double[numStates][];
					oScorePostU[start][end] = new double[numStates][];
					iScale[start][end] = new int[numStates];
					oScale[start][end] = new int[numStates];
					Arrays.fill(iScale[start][end], Integer.MIN_VALUE);
					Arrays.fill(oScale[start][end], Integer.MIN_VALUE);

					for (int state = 0; state < numSubStatesArray.length; state++) {
						if (end - start > 1 && !grammarTags[state])
							continue;
						iScorePreU[start][end][state] = new double[numSubStatesArray[state]];
						iScorePostU[start][end][state] = new double[numSubStatesArray[state]];
						oScorePreU[start][end][state] = new double[numSubStatesArray[state]];
						oScorePostU[start][end][state] = new double[numSubStatesArray[state]];
					}
				}
			}
			narrowRExtent = new int[length + 1][numStates];
			wideRExtent = new int[length + 1][numStates];
			narrowLExtent = new int[length + 1][numStates];
			wideLExtent = new int[length + 1][numStates];

			for (int loc = 0; loc <= length; loc++) {
				Arrays.fill(narrowLExtent[loc], -1); // the rightmost left with
														// state s ending at i
														// that we can get is
														// the beginning
				Arrays.fill(wideLExtent[loc], length + 1); // the leftmost left
															// with state s
															// ending at i that
															// we can get is the
															// end
				Arrays.fill(narrowRExtent[loc], length + 1); // the leftmost
																// right with
																// state s
																// starting at i
																// that we can
																// get is the
																// end
				Arrays.fill(wideRExtent[loc], -1); // the rightmost right with
													// state s starting at i
													// that we can get is the
													// beginning
			}

		}
	}

	@Override
	public Tree<String> getBestConstrainedParse(List<String> sentence,
			List<String> posTags, boolean[][][][] allowedStates) {
		// setConstraints(allowedStates,true);

		boolean noSmoothing = false;
		List<StateSet> testSentenceStateSet = convertToTestSet(sentence);
		double ll = doConstrainedInsideOutsideScores(testSentenceStateSet,
				allowedStates, noSmoothing, null, posTags, viterbi);

		Tree<String> bestTree = null;

		if (ll == Double.NEGATIVE_INFINITY) {
			return new Tree<String>("ROOT");
		}
		if (viterbi) {
			bestTree = extractBestViterbiParse(0, 0, 0, length, sentence, true);
		} else {
			if (spanScores == null)
				doConstrainedMaxCScores(testSentenceStateSet);
			else
				doConstrainedMaxCScores(testSentenceStateSet, spanScores);
			bestTree = extractBestMaxRuleParse(0, length, sentence);
		}

		maxcScore = null;
		maxcSplit = null;
		maxcChild = null;
		maxcLeftChild = null;
		maxcRightChild = null;

		// sentencesParsed++;
		// System.out.println("For parsing "+sentencesParsed+" I hat to touch "+edgesTouched/((double)sentencesParsed)+" on average.");

		return bestTree;
	}

	/**
	 * Assumes that inside and outside scores (sum version, not viterbi) have
	 * been computed. In particular, the narrowRExtent and other arrays need not
	 * be updated.
	 */
	void doConstrainedMaxCScores(List<StateSet> sentence) {
		maxcScore = new double[length][length + 1][numStates];
		maxcSplit = new int[length][length + 1][numStates];
		maxcChild = new int[length][length + 1][numStates];
		maxcLeftChild = new int[length][length + 1][numStates];
		maxcRightChild = new int[length][length + 1][numStates];
		double tree_score = iScorePostU[0][length][0][0];
		int tree_scale = iScale[0][length][0];
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
						if (allowedSubStates[start][end][pState] == null) {
							continue;
						}
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
							double[][][] scores = r.getScores2();
							int nLeftChildStates = numSubStatesArray[lState]; // ==
																				// scores.length;
							int nRightChildStates = numSubStatesArray[rState]; // ==
																				// scores[0].length;
							for (int split = min; split <= max; split++) {
								double ruleScore = 0;
								if (allowedSubStates[start][split][lState] == null)
									continue;
								if (allowedSubStates[split][end][rState] == null)
									continue;
								double scalingFactor = ScalingTools
										.calcScaleFactor(oScale[start][end][pState]
												+ iScale[start][split][lState]
												+ iScale[split][end][rState]
												- tree_scale);
								if (scalingFactor == 0)
									continue;

								for (int lp = 0; lp < nLeftChildStates; lp++) {
									double lIS = iScorePostU[start][split][lState][lp];
									if (lIS == 0)
										continue;
									// if
									// (!allowedSubStates[start][split][lState][lp])
									// continue;

									for (int rp = 0; rp < nRightChildStates; rp++) {
										if (scores[lp][rp] == null)
											continue;
										double rIS = iScorePostU[split][end][rState][rp];
										if (rIS == 0)
											continue;
										// if
										// (!allowedSubStates[split][end][rState][rp])
										// continue;
										for (int np = 0; np < nParentStates; np++) {
											// if
											// (!allowedSubStates[start][end][pState][np])
											// continue;
											double pOS = oScorePostU[start][end][pState][np];
											if (pOS == 0)
												continue;

											double ruleS = scores[lp][rp][np];
											if (ruleS == 0)
												continue;
											ruleScore += pOS * scalingFactor
													* ruleS / tree_score * lIS
													* rIS;

										}
									}
								}
								if (ruleScore == 0)
									continue;

								// if (ruleScore==0) {
								// System.out.println("possible underflow binary");
								// if (ruleScore==0){
								// System.out.println("Underflow:");
								// System.out.println("pOS: "+Arrays.toString(oScorePostU[start][end][pState]));
								// System.out.println("scalingFactor: "+scalingFactor);
								// System.out.println("tree_score: "+tree_score);
								// System.out.println("lIS: "+Arrays.toString(iScorePostU[start][split][lState]));
								// System.out.println("rIS: "+Arrays.toString(iScorePostU[split][end][rState]));
								// }
								// }
								double leftChildScore = maxcScore[start][split][lState];
								double rightChildScore = maxcScore[split][end][rState];
								double gScore = ruleScore * leftChildScore
										* rightChildScore;

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
					for (short tag = 0; tag < numSubStatesArray.length; tag++) {
						if (allowedSubStates[start][end][tag] == null)
							continue;
						if (grammar.isGrammarTag(tag))
							continue;
						// maxcScore[start][end][tag] = 1;
						double scalingFactor = ScalingTools
								.calcScaleFactor(oScale[start][end][tag]
										- tree_scale);
						if (scalingFactor == 0) {
							continue;
						}

						int nTagStates = numSubStatesArray[tag];
						// String word = sentence.get(start);
						StateSet word = sentence.get(start);
						double[] lexiconScoreArray = lexicon.score(word, tag,
								false, false);
						double lexiconScores = 0;

						for (int tp = 0; tp < nTagStates; tp++) {
							double pOS = oScorePostU[start][end][tag][tp];
							if (pOS == 0)
								continue;
							double ruleS = lexiconScoreArray[tp];
							if (ruleS == 0)
								continue;
							lexiconScores += (pOS * ruleS) / tree_score;
						}
						if (lexiconScores == 0)
							continue;
						// if (lexiconScores==0)
						// System.out.println("possible underflow lexicon");
						maxcScore[start][end][tag] = lexiconScores
								* scalingFactor;
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
					if (allowedSubStates[start][end][pState] == null) {
						continue;
					}
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
						if (allowedSubStates[start][end][cState] == null)
							continue;

						double[][] scores = ur.getScores2();
						int nChildStates = numSubStatesArray[cState]; // ==
																		// scores.length;
						double ruleScore = 0;
						double scalingFactor = ScalingTools
								.calcScaleFactor(oScale[start][end][pState]
										+ iScale[start][end][cState]
										- tree_scale);
						if (scalingFactor == 0) {
							continue;
						}

						for (int cp = 0; cp < nChildStates; cp++) {
							double cIS = iScorePreU[start][end][cState][cp];
							if (cIS == 0)
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
								if (pOS == 0)
									continue;

								double ruleS = scores[cp][np];
								if (ruleS == 0)
									continue;
								ruleScore += pOS * scalingFactor * ruleS
										/ tree_score * cIS;
							}
						}

						if (ruleScore == 0)
							continue;

						double childScore = maxcScore[start][end][cState];
						double gScore = ruleScore * childScore;
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

	public double doConstrainedInsideOutsideScores(List<StateSet> sentence,
			boolean[][][][] allowed, boolean noSmoothing,
			Tree<StateSet> goldTree, List<String> posTags, boolean viterbi) {
		scrubArrays();

		length = (short) sentence.size();
		if (allowed != null)
			allowedSubStates = allowed;
		else
			setConstraints(null, false);

		createArrays();

		initializeChart(sentence, noSmoothing, posTags);

		double logLikelihood = Double.NEGATIVE_INFINITY;

		if (spanPredictor != null) {
			spanScores = spanPredictor.predictSpans(sentence);

			doConstrainedInsideScores(viterbi, spanScores);
			logLikelihood = getLikelihoodAndSetRootOutsideScore();
			doConstrainedOutsideScores(viterbi, spanScores);
		} else {
			doConstrainedInsideScores(viterbi);
			logLikelihood = getLikelihoodAndSetRootOutsideScore();
			doConstrainedOutsideScores(viterbi);
		}

		return logLikelihood;
	}

	void doConstrainedMaxCScores(List<StateSet> testSentenceStateSet,
			double[][][] spanScores2) {
		throw new Error("Currently not supported");
	}

	void doConstrainedOutsideScores(boolean viterbi, double[][][] spanScores2) {
		throw new Error("Currently not supported");
	}

	void doConstrainedInsideScores(boolean viterbi, double[][][] spanScores2) {
		throw new Error("Currently not supported");
	}

	protected double getLikelihoodAndSetRootOutsideScore() {
		oScorePreU[0][length][0][0] = 1.0;
		oScale[0][length][0] = 0;
		return Math.log(iScorePostU[0][length][0][0])
				+ (ScalingTools.LOGSCALE * iScale[0][length][0]);
	}

	/**
		 * 
		 */
	protected void scrubArrays() {
		if (iScorePostU == null)
			return;
		for (int start = 0; start < length; start++) {
			for (int end = start + 1; end <= length; end++) {
				for (int state = 0; state < numSubStatesArray.length; state++) {
					if (allowedSubStates[start][end][state] != null) {
						if (end - start > 1 && !grammarTags[state])
							continue;

						Arrays.fill(iScorePreU[start][end][state], 0);
						Arrays.fill(iScorePostU[start][end][state], 0);
						Arrays.fill(oScorePreU[start][end][state], 0);
						Arrays.fill(oScorePostU[start][end][state], 0);
						Arrays.fill(iScale[start][end], Integer.MIN_VALUE);
						Arrays.fill(oScale[start][end], Integer.MIN_VALUE);
					}
				}
			}
		}
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

	/**
	 * @param allowedSubStates2
	 */
	protected void setConstraints(boolean[][][][] allowedSubStates2,
			boolean allSubstates) {
		allowedSubStates = new boolean[length][length + 1][][];
		for (int start = 0; start < length; start++) {
			for (int end = start + 1; end <= length; end++) {
				allowedSubStates[start][end] = new boolean[numStates][];
				for (int state = 0; state < numStates; state++) {
					if (allowedSubStates2 == null) { // then we parse without
														// constraints
						if (end - start > 1 && !grammarTags[state])
							continue;
						boolean[] tmp = new boolean[numSubStatesArray[state]];
						Arrays.fill(tmp, true);
						allowedSubStates[start][end][state] = tmp;
					} else if (allowedSubStates2[start][end][state] != null) {
						allowedSubStates[start][end][state] = new boolean[numSubStatesArray[state]];
						if (allSubstates)
							Arrays.fill(allowedSubStates[start][end][state],
									true);
						else {
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

	}

	public void incrementExpectedCounts(Linearizer linearizer, double[] probs,
			List<StateSet> sentence) {
		// numSubStatesArray = grammar.numSubStates;
		double tree_score = iScorePostU[0][length][0][0];
		int tree_scale = iScale[0][length][0];
		if (SloppyMath.isDangerous(tree_score)) {
			System.out
					.println("Training tree has zero probability - presumably underflow!");
			return;
			// System.exit(-1);
		}

		for (int start = 0; start < length; start++) {
			final int lastState = numSubStatesArray.length;
			StateSet currentStateSet = sentence.get(start);

			for (int tag = 0; tag < lastState; tag++) {
				if (grammar.isGrammarTag(tag))
					continue;
				if (allowedSubStates[start][start + 1][tag] == null)
					continue;
				double scalingFactor = ScalingTools
						.calcScaleFactor(oScale[start][start + 1][tag]
								+ iScale[start][start + 1][tag] - tree_scale);
				if (scalingFactor == 0) {
					continue;
				}

				final int nSubStates = numSubStatesArray[tag];
				// if (!combinedLexicon){
				for (short substate = 0; substate < nSubStates; substate++) {
					// weight by the probability of seeing the tag and word
					// together, given the sentence
					double iS = iScorePreU[start][start + 1][tag][substate];
					if (iS == 0)
						continue;
					double oS = oScorePostU[start][start + 1][tag][substate];
					if (oS == 0)
						continue;
					double weight = iS / tree_score * scalingFactor * oS;

					if (isValidExpectation(weight)) {
						tmpCountsArray[substate] = weight;
					}
				}
				linearizer.increment(probs, currentStateSet, tag,
						tmpCountsArray, false); // probs[startIndexWord+substate]
												// += weight;
				// linearizer.increment(probs, sigIndex, tag, tmpCountsArray);
				// //probs[startIndexWord+substate] += weight;
				// }
				// else {
				// double[] wordScores = lexicon.scoreWord(currentStateSet,
				// tag);
				// for (short substate=0; substate<nSubStates; substate++) {
				// //weight by the probability of seeing the tag and word
				// together, given the sentence
				// double iS = wordScores[substate];
				// if (iS==0) continue;
				// double oS = oScorePostU[start][start+1][tag][substate];
				// if (oS==0) continue;
				// double weight = iS / tree_score * scalingFactor * oS;
				// tmpCountsArray[substate] = weight;
				// }
				// linearizer.increment(probs, wordIndex, tag, tmpCountsArray);
				// //probs[startIndexWord+substate] += weight;
				//
				// double[] sigScores = lexicon.scoreSignature(currentStateSet,
				// tag);
				// if (sigScores==null) continue;
				// for (short substate=0; substate<nSubStates; substate++) {
				// //weight by the probability of seeing the tag and word
				// together, given the sentence
				// double iS = sigScores[substate];
				// if (iS==0) continue;
				// double oS = oScorePostU[start][start+1][tag][substate];
				// if (oS==0) continue;
				// double weight = iS / tree_score * scalingFactor * oS;
				// tmpCountsArray[substate] = weight;
				// }
				// linearizer.increment(probs, sigIndex, tag, tmpCountsArray);
				// //probs[startIndexWord+substate] += weight;
				// }
			}

		}

		for (int diff = 1; diff <= length; diff++) {
			for (int start = 0; start < (length - diff + 1); start++) {
				int end = start + diff;

				final int lastState = numSubStatesArray.length;
				for (short pState = 0; pState < lastState; pState++) {
					if (diff == 1)
						continue; // there are no binary rules that span over 1
									// symbol only
					if (allowedSubStates[start][end][pState] == null)
						continue;
					final int nParentSubStates = numSubStatesArray[pState];
					BinaryRule[] parentRules = grammar.splitRulesWithP(pState);
					for (int i = 0; i < parentRules.length; i++) {
						BinaryRule r = parentRules[i];
						short lState = r.leftChildState;
						short rState = r.rightChildState;

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

						double[][][] scores = r.getScores2();
						boolean foundSomething = false;
						int nRuleStates = scores[0][0].length;
						int divisor = nParentSubStates / nRuleStates;

						for (int split = min; split <= max; split++) {
							if (allowedSubStates[start][split][lState] == null)
								continue;
							if (allowedSubStates[split][end][rState] == null)
								continue;
							double scalingFactor = ScalingTools
									.calcScaleFactor(oScale[start][end][pState]
											+ iScale[start][split][lState]
											+ iScale[split][end][rState]
											- tree_scale);

							if (scalingFactor == 0) {
								continue;
							}

							int curInd = 0;
							for (int lp = 0; lp < scores.length; lp++) {
								double lcIS = iScorePostU[start][split][lState][lp];
								if (lcIS == 0) {
									curInd += scores[0].length
											* nParentSubStates;
									continue;
								}

								double tmpA = lcIS / tree_score;

								for (int rp = 0; rp < scores[0].length; rp++) {
									// if (scores[lp][rp]==null) continue;
									double rcIS = iScorePostU[split][end][rState][rp];

									if (rcIS == 0) {
										curInd += nParentSubStates;
										continue;
									}

									double tmpB = tmpA * rcIS * scalingFactor;

									if (nRuleStates == nParentSubStates) {
										for (int np = 0; np < nParentSubStates; np++) {
											double pOS = oScorePostU[start][end][pState][np];
											if (pOS == 0) {
												curInd++;
												continue;
											}

											double rS = scores[lp][rp][np];
											double ruleCount = rS * tmpB * pOS;

											if (isValidExpectation(ruleCount)) {
												tmpCountsArray[curInd] += ruleCount;
												foundSomething = true;
											}
											curInd++;
										}
									} else {
										for (int np = 0; np < nParentSubStates; np++) {
											double pOS = oScorePostU[start][end][pState][np];
											if (pOS == 0) {
												curInd++;
												continue;
											}

											double rS = scores[lp / divisor][rp
													/ divisor][np / divisor];
											double ruleCount = rS * tmpB * pOS;

											if (isValidExpectation(ruleCount)) {
												tmpCountsArray[curInd] += ruleCount;
												foundSomething = true;
											}
											curInd++;
										}

									}
								}
							}
						}
						if (!foundSomething)
							continue; // nothing changed this round
						linearizer.increment(probs, r, tmpCountsArray, false);
					}
				}
				final int lastStateU = numSubStatesArray.length;
				for (short pState = 0; pState < lastStateU; pState++) {
					if (allowedSubStates[start][end][pState] == null)
						continue;

					// List<UnaryRule> unaries =
					// grammar.getUnaryRulesByParent(pState);
					int nParentSubStates = numSubStatesArray[pState];
					UnaryRule[] unaries = grammar
							.getClosedSumUnaryRulesByParent(pState);
					for (UnaryRule ur : unaries) {
						short cState = ur.childState;
						if ((pState == cState))
							continue;// && (np == cp))continue;
						if (allowedSubStates[start][end][cState] == null)
							continue;
						double scalingFactor = ScalingTools
								.calcScaleFactor(oScale[start][end][pState]
										+ iScale[start][end][cState]
										- tree_scale);

						if (scalingFactor == 0) {
							continue;
						}

						double[][] scores = ur.getScores2();

						int curInd = 0;
						for (int cp = 0; cp < scores.length; cp++) {
							if (scores[cp] == null)
								continue;
							double cIS = iScorePreU[start][end][cState][cp];

							if (cIS == 0) {
								curInd += nParentSubStates;
								continue;
							}

							double tmpA = cIS / tree_score * scalingFactor;

							for (int np = 0; np < nParentSubStates; np++) {
								double pOS = oScorePreU[start][end][pState][np];
								if (pOS == 0) {
									curInd++;
									continue;
								}

								double rS = scores[cp][np];

								double ruleCount = rS * tmpA * pOS;
								if (isValidExpectation(ruleCount)) {
									tmpCountsArray[curInd] = ruleCount;
								}
								curInd++;
							}
						}
						linearizer.increment(probs, ur, tmpCountsArray, false); // probs[thisStartIndex
																				// +
																				// curInd-1]
																				// +=
																				// ruleCount;
					}
				}
			}
		}
	}

	public boolean isValidExpectation(double val) {
		return (val > 0 && val < 1.01);
	}

	public void updateGrammarAndLexicon(Grammar grammar2, Lexicon lexicon2) {
		this.grammar = grammar2;
		this.lexicon = lexicon2;
	}

	/**
	 * @param testSentence
	 * @param tree
	 * @param threshold
	 * @return
	 */
	public boolean[][][][] getPossibleStates(List<String> testSentence,
			Tree<StateSet> tree, double threshold,
			boolean[][][][] previousConstraints, StringBuilder sb) {
		boolean noSmoothing = false;// true;//(tree!=null);
		int previouslyPossibleSub = (countPossibleSubStates(previousConstraints) - 1) / 2;
		int previouslyPossible = countPossibleStates(previousConstraints);
		List<StateSet> testSentenceStateSet = convertToTestSet(testSentence);
		doConstrainedInsideOutsideScores(testSentenceStateSet,
				previousConstraints, noSmoothing, null, null, true);
		boolean[][][][] allowedStates = computeAllowedStates(threshold);
		if (allowedStates[0][testSentence.size()][0] == null)
			System.out.println("Root got pruned!");
		int possibleStates = countPossibleStates(allowedStates);
		int possibleSubStates = countPossibleSubStates(allowedStates);
		if (tree != null) {
			if (possibleSubStates == 0)
				sb.append("Only gold tree is left!");
			putGoldTreeBackIn(tree, allowedStates);
		}
		int possibleSubStates2 = countPossibleSubStates(allowedStates);
		if (possibleSubStates != possibleSubStates2) {
			sb.append(", saved gold tree");
			possibleSubStates = possibleSubStates2;
			possibleStates = countPossibleStates(allowedStates);
		}

		if (possibleSubStates2 == 0) {
			sb.append(", Parse failure! No pruning!");
			allowedStates = previousConstraints;
			possibleSubStates = previouslyPossible;
		}
		sb.append(", from: " + previouslyPossibleSub + " ("
				+ previouslyPossible + ") to: " + possibleSubStates + " ("
				+ possibleStates + ") substates.");
		return allowedStates;
	}

	/**
	 * @param testSentence
	 * @return
	 */
	protected List<StateSet> convertToTestSet(List<String> testSentence) {
		ArrayList<StateSet> list = new ArrayList<StateSet>(testSentence.size());
		short ind = 0;
		for (String word : testSentence) {
			StateSet stateSet = new StateSet((short) -1, (short) 1, word, ind,
					(short) (ind + 1));
			ind++;
			stateSet.wordIndex = -2;
			stateSet.sigIndex = -2;
			list.add(stateSet);
		}
		return list;
	}

	/**
	 * @param allowedStates
	 * @return
	 */
	private int countPossibleSubStates(boolean[][][][] allowedStates) {
		if (allowedStates == null)
			return 0;
		int possibleStates = 0;
		for (int start = 0; start < allowedStates.length; start++) {
			for (int end = start + 1; end <= allowedStates.length; end++) {
				final int lastState = numSubStatesArray.length;
				for (int state = 0; state < lastState; state++) {
					if (allowedStates[start][end][state] == null)
						continue;
					for (int substate = 0; substate < allowedStates[start][end][state].length; substate++) {
						if (allowedStates[start][end][state][substate])
							possibleStates++;
					}
				}
			}
		}
		return possibleStates;
	}

	private int countPossibleStates(boolean[][][][] allowedStates) {
		if (allowedStates == null)
			return 0;
		int possibleStates = 0;
		for (int start = 0; start < allowedStates.length; start++) {
			for (int end = start + 1; end <= allowedStates.length; end++) {
				final int lastState = numSubStatesArray.length;
				for (int state = 0; state < lastState; state++) {
					if (allowedStates[start][end][state] == null)
						continue;
					for (int substate = 0; substate < allowedStates[start][end][state].length; substate++) {
						if (allowedStates[start][end][state][substate]) {
							possibleStates++;
							break;
						}
					}
				}
			}
		}
		return possibleStates;
	}

	/**
	 * @param tree
	 * @param allowedStates
	 */
	private void putGoldTreeBackIn(Tree<StateSet> tree,
			boolean[][][][] allowedStates) {
		StateSet node = tree.getLabel();
		int state = node.getState();
		if (state < numStates) {
			boolean[] tmp = new boolean[numSubStatesArray[state]];
			Arrays.fill(tmp, true);
			allowedStates[node.from][node.to][state] = tmp;
		} else {
			System.out.println("Haven't seen state " + node);
		}
		for (Tree<StateSet> child : tree.getChildren()) {
			if (!child.isLeaf())
				putGoldTreeBackIn(child, allowedStates);
		}
	}

	/**
	 * @param tree
	 * @param threshold
	 * @return
	 */
	boolean[][][][] computeAllowedStates(double threshold) {
		double tree_score = iScorePostU[0][length][0][0];
		int tree_scale = iScale[0][length][0];
		boolean[][][][] result = new boolean[length][length + 1][][];
		for (int start = 0; start < length; start++) {
			for (int end = start + 1; end <= length; end++) {
				result[start][end] = new boolean[numStates][];
				final int lastState = numSubStatesArray.length;
				for (int state = 0; state < lastState; state++) {

					if (allowedSubStates[start][end][state] == null)
						continue;
					boolean atLeastOnePossible = false;
					for (int substate = 0; substate < numSubStatesArray[state]; substate++) {
						if (!allowedSubStates[start][end][state][substate])
							continue;
						double iS = iScorePostU[start][end][state][substate];
						if (iS == 0)
							continue;
						double oS = oScorePostU[start][end][state][substate];
						if (oS == 0)
							continue;

						double scalingFactor = ScalingTools
								.calcScaleFactor(oScale[start][end][state]
										+ iScale[start][end][state]
										- tree_scale);
						if (scalingFactor == 0)
							continue;

						double tmp = Math.max(iS
								* oScorePreU[start][end][state][substate],
								iScorePreU[start][end][state][substate] * oS);
						double posterior = tmp / tree_score * scalingFactor;
						if (posterior > threshold) {
							if (result[start][end][state] == null)
								result[start][end][state] = new boolean[numSubStatesArray[state]];
							result[start][end][state][substate] = true;
							atLeastOnePossible = true;
						}
					}
					if (!atLeastOnePossible)
						result[start][end][state] = null;
				}
			}
		}
		return result;
	}

	/**
	 * Returns the best parse, the one with maximum expected labelled recall.
	 * Assumes that the maxc* arrays have been filled.
	 */
	public Tree<String> extractBestMaxRuleParse(int start, int end,
			List<String> sentence) {
		return extractBestMaxRuleParse1(start, end, 0, sentence);
	}

	/**
	 * Returns the best parse for state "state", potentially starting with a
	 * unary rule
	 */
	public Tree<String> extractBestMaxRuleParse1(int start, int end, int state,
			List<String> sentence) {
		// System.out.println(start+", "+end+";");
		int cState = maxcChild[start][end][state];
		if (cState == -1) {
			return extractBestMaxRuleParse2(start, end, state, sentence);
		} else {
			List<Tree<String>> child = new ArrayList<Tree<String>>();
			child.add(extractBestMaxRuleParse2(start, end, cState, sentence));
			String stateStr = (String) tagNumberer.object(state);
			if (stateStr.endsWith("^g"))
				stateStr = stateStr.substring(0, stateStr.length() - 2);

			// System.out.println("Adding a unary spanning from "+start+" to "+end+". P: "+stateStr+" C: "+child.get(0).getLabel());
			int intermediateNode = grammar.getUnaryIntermediate((short) state,
					(short) cState);
			if (intermediateNode == 0) {
				// System.out.println("Added a bad unary from "+start+" to "+end+". P: "+stateStr+" C: "+child.get(0).getLabel());
			}
			if (intermediateNode > 0) {
				List<Tree<String>> restoredChild = new ArrayList<Tree<String>>();
				String stateStr2 = (String) tagNumberer
						.object(intermediateNode);
				if (stateStr2.endsWith("^g"))
					stateStr2 = stateStr2.substring(0, stateStr2.length() - 2);
				restoredChild.add(new Tree<String>(stateStr2, child));
				// System.out.println("Restored a unary from "+start+" to "+end+": "+stateStr+" -> "+stateStr2+" -> "+child.get(0).getLabel());
				return new Tree<String>(stateStr, restoredChild);
			}
			return new Tree<String>(stateStr, child);
		}
	}

	/**
	 * Returns the best parse for state "state", but cannot start with a unary
	 */
	public Tree<String> extractBestMaxRuleParse2(int start, int end, int state,
			List<String> sentence) {
		List<Tree<String>> children = new ArrayList<Tree<String>>();
		String stateStr = (String) tagNumberer.object(state);// +""+start+""+end;
		if (stateStr.endsWith("^g"))
			stateStr = stateStr.substring(0, stateStr.length() - 2);
		boolean posLevel = (end - start == 1);
		if (posLevel) {
			if (grammar.isGrammarTag(state)) {
				List<Tree<String>> childs = new ArrayList<Tree<String>>();
				childs.add(new Tree<String>(sentence.get(start)));
				String stateStr2 = (String) tagNumberer
						.object(maxcChild[start][end][state]);// +""+start+""+end;
				children.add(new Tree<String>(stateStr2, childs));
			} else
				children.add(new Tree<String>(sentence.get(start)));
		} else {
			int split = maxcSplit[start][end][state];
			if (split == -1) {
				System.err
						.println("Warning: no symbol can generate the span from "
								+ start + " to " + end + ".");
				System.err.println("The score is "
						+ maxcScore[start][end][state]
						+ " and the state is supposed to be " + stateStr);
				System.err.println("The insideScores are "
						+ Arrays.toString(iScorePostU[start][end][state])
						+ " and the outsideScores are "
						+ Arrays.toString(oScorePostU[start][end][state]));
				System.err.println("The maxcScore is "
						+ maxcScore[start][end][state]);
				for (short start2 = 0; start2 < length; start2++) {
					for (short tag = 0; tag < numSubStatesArray.length; tag++) {
						if (grammar.isGrammarTag(tag))
							continue;
						if (maxcScore[start2][start2 + 1][tag] > 0)
							System.err.println("The maxcScore for word "
									+ start2 + " is "
									+ maxcScore[start2][start2 + 1][tag]);
					}
				}
				// return extractBestMaxRuleParse2(start, end,
				// maxcChild[start][end][state], sentence);
				return new Tree<String>("ROOT");
			}
			int lState = maxcLeftChild[start][end][state];
			int rState = maxcRightChild[start][end][state];
			Tree<String> leftChildTree = extractBestMaxRuleParse1(start, split,
					lState, sentence);
			Tree<String> rightChildTree = extractBestMaxRuleParse1(split, end,
					rState, sentence);
			children.add(leftChildTree);
			children.add(rightChildTree);
		}
		return new Tree<String>(stateStr, children);
	}

	@Override
	public void projectConstraints(boolean[][][][] allowed,
			boolean allSubstatesAllowed) {
		if (allowed == null)
			return;
		for (int start = 0; start < allowed.length; start++) {
			for (int end = start + 1; end <= allowed.length; end++) {
				for (int state = 0; state < numStates; state++) {
					if (allowed[start][end][state] != null) {
						if (numSubStatesArray[state] == allowed[start][end][state].length)
							continue;
						boolean[] tmp = new boolean[numSubStatesArray[state]];
						if (allSubstatesAllowed)
							Arrays.fill(tmp, true);
						else {
							for (int substate = 0; substate < allowed[start][end][state].length; substate++) {
								if (allowed[start][end][state][substate]) {
									if (2 * substate >= tmp.length)
										System.out.println("too long");
									tmp[2 * substate] = true;
									if (grammar.numSubStates[state] != 1)
										tmp[2 * substate + 1] = true;
								}
							}
						}
						allowed[start][end][state] = tmp;
					}
				}
			}
		}
	}

	public void checkScores(Tree<StateSet> tree) {
		StateSet node = tree.getLabel();
		int state = node.getState();
		int from = node.from, to = node.to;
		int oldS = iScale[from][to][state];
		int newS = ScalingTools.scaleArray(iScorePostU[from][to][state], oldS);
		if (oldS > newS) {
			System.out.println("why?? iscale");
		}

		oldS = oScale[from][to][state];
		newS = ScalingTools.scaleArray(oScorePostU[from][to][state], oldS);
		if (oldS > newS) {
			ScalingTools.scaleArrayToScale(oScorePostU[from][to][state], newS,
					oldS);
			System.out.println("why?? oscale");
		}
		for (int substate = 0; substate < numSubStatesArray[state]; substate++) {
			if ((node.getIScale() == iScale[from][to][state])
					&& (!SloppyMath.isGreater(
							iScorePostU[from][to][state][substate],
							node.getIScore(substate)))) {
				if (!allowedSubStates[from][to][state][substate])
					System.out.println("This state was pruned!");
				else {
					System.out.println("Gold iScore is higher for state "
							+ state + " from " + from + " to " + to + "!");
					System.out.println("Gold " + node.getIScore(substate)
							+ " all " + iScorePostU[from][to][state][substate]);
				}
			}
			double tmpA = node.getOScore(substate);
			double tmpB = oScorePostU[from][to][state][substate];
			if ((node.getOScale() == oScale[from][to][state])
					&& (!SloppyMath.isGreater(tmpB, tmpA))) {
				if (!allowedSubStates[from][to][state][substate])
					System.out.println("This state was pruned!");
				else {
					System.out.println("Gold oScore is higher for state "
							+ state + " from " + from + " to " + to + "!");
					System.out.println("Gold " + node.getOScore(substate)
							+ " all " + oScorePostU[from][to][state][substate]);
				}
			}
		}
		for (Tree<StateSet> child : tree.getChildren()) {
			if (!child.isLeaf())
				checkScores(child);
		}
	}

	// compute the loss in conditional likelihood for merges in nodes in the
	// gold tree
	public void tallyConditionalLoss(Tree<StateSet> tree, double[][][] deltas,
			double[][] mergeWeights) {
		if (tree.isLeaf())
			return;
		for (Tree<StateSet> child : tree.getChildren()) {
			tallyConditionalLoss(child, deltas, mergeWeights);
		}
		StateSet label = tree.getLabel();
		short state = label.getState();
		if (state == 0)
			return; // nothing to be done for the ROOT

		int start = label.from, end = label.to;
		if (allowedSubStates[start][end][state] == null) {
			System.out.println("Gold state was pruned!!!");
		}
		double[] goldScores = new double[label.numSubStates()];
		double[] allScores = new double[label.numSubStates()];
		double combinedGoldScore, combinedAllScore;

		double separatedGoldScoreSum = 0, separatedAllScoreSum = 0, tmp;
		// don't need to deal with scale factor because we divide below
		for (int i = 0; i < label.numSubStates(); i++) {
			// in the gold tree
			tmp = label.getIScore(i) * label.getOScore(i);
			goldScores[i] = tmp;
			separatedGoldScoreSum += tmp;
			// for all trees
			tmp = iScorePostU[start][end][state][i]
					* oScorePostU[start][end][state][i];
			allScores[i] = tmp;
			separatedAllScoreSum += tmp;
		}
		if (separatedAllScoreSum == 0)
			return; // for some reason this seems to happen quite often
		// calculate merged scores
		for (int i = 0; i < numSubStatesArray[state]; i = i + 2) {
			int j = i + 1;
			double lossInGold = 0, lossInAll = 0;
			int[] map = { i, j };
			double[] tmp1 = new double[2], tmp2 = new double[2];
			double mergeWeightSum = 0;
			for (int k = 0; k < 2; k++) {
				mergeWeightSum += mergeWeights[state][map[k]];
			}
			if (mergeWeightSum == 0)
				mergeWeightSum = 1;

			for (int k = 0; k < 2; k++) {
				tmp1[k] = label.getIScore(map[k]) * mergeWeights[state][map[k]]
						/ mergeWeightSum;
				tmp2[k] = label.getOScore(map[k]);
			}
			combinedGoldScore = (tmp1[0] + tmp1[1]) * (tmp2[0] + tmp2[1]);
			double combinedGoldScoreSum = separatedGoldScoreSum - goldScores[i]
					- goldScores[j] + combinedGoldScore;

			if (combinedGoldScore != 0 && separatedGoldScoreSum != 0)
				lossInGold = separatedGoldScoreSum / combinedGoldScoreSum;

			// now do the same for all trees
			for (int k = 0; k < 2; k++) {
				tmp1[k] = iScorePostU[start][end][state][map[k]]
						* mergeWeights[state][map[k]] / mergeWeightSum;
				tmp2[k] = oScorePostU[start][end][state][map[k]];
			}
			combinedAllScore = (tmp1[0] + tmp1[1]) * (tmp2[0] + tmp2[1]);
			double combinedAllScoreSum = separatedAllScoreSum - allScores[i]
					- allScores[j] + combinedAllScore;

			if (combinedGoldScore != 0 && separatedGoldScoreSum != 0)
				lossInAll = separatedAllScoreSum / combinedAllScoreSum;

			if (SloppyMath.isDangerous(lossInAll)
					|| SloppyMath.isDangerous(lossInGold)) {
				System.out.println("too many zeros ");
				System.out.println("tmp1: " + Arrays.toString(tmp1)
						+ "\ntmp2: " + Arrays.toString(tmp2) + "\ngoldScores: "
						+ Arrays.toString(goldScores) + "\nallScores: "
						+ Arrays.toString(allScores) + "\nmergeWeights: "
						+ Arrays.toString(mergeWeights[state])
						+ "\nseparatedGoldScoreSum: " + separatedGoldScoreSum
						+ "\nseparatedAllScoreSum: " + separatedAllScoreSum
						+ "\ncombinedGoldScoreSum: " + combinedGoldScoreSum
						+ "\ncombinedAllScoreSum: " + combinedAllScoreSum);
			} else
				deltas[state][i][j] += Math.log(lossInGold / lossInAll);

			if (Double.isNaN(deltas[state][i][j])) {
				System.out.println(" deltas[" + tagNumberer.object(state)
						+ "][" + i + "][" + j + "] = NaN");
				System.out.println(Arrays.toString(tmp1) + " "
						+ Arrays.toString(tmp2) + " " + combinedGoldScore + " "
						+ Arrays.toString(mergeWeights[state]));
			}
		}

	}

	private void setGoldProductions(Tree<StateSet> tree, boolean isBinaryChild) {
		StateSet node = tree.getLabel();
		short parentState = node.getState();
		if (parentState == 0) { // this is the ROOT node, initialize arrays
			goldBinaryProduction = new int[length][length + 1];
			ArrayUtil.fill(goldBinaryProduction, -1);
			goldUnaryParent = new int[length][length + 1];
			ArrayUtil.fill(goldUnaryParent, -1);
			goldUnaryChild = new int[length][length + 1];
			ArrayUtil.fill(goldUnaryChild, -1);
			goldPOS = new int[length];
			Arrays.fill(goldPOS, -1);
			goldUnaryParent[0][length] = 0;
		}

		if (isBinaryChild)
			goldBinaryProduction[node.from][node.to] = parentState;
		else if (parentState != 0)
			goldUnaryChild[node.from][node.to] = parentState;

		List<Tree<StateSet>> children = tree.getChildren();
		if (children.size() == 2) { // binary
			goldBinaryProduction[node.from][node.to] = parentState;
			setGoldProductions(children.get(0), true);
			setGoldProductions(children.get(1), true);
		} else { // unary or POS
			Tree<StateSet> child = children.get(0);
			if (child.isLeaf()) {
				goldPOS[node.from] = parentState;
			} else { // unary
				goldUnaryParent[node.from][node.to] = parentState;
				setGoldProductions(child, false);
			}

		}
	}

	public void doPreParses(List<String> sentence, List<String> posTags,
			Grammar[] grammarCascade, Lexicon[] lexiconCascade,
			boolean accurate, int startLevel, int endLevel, boolean isBaseline) {
		throw new Error("currently not supported");
		// boolean noSmoothing = false;
		// clearArrays();
		// length = (short)sentence.size();
		// double score = 0;
		// double[] accurateThresholds = {-8,-12,-12,-11,-12,-12,-14};
		// double[] fastThresholds = {-8,-9.75,-10,-9.6,-9.66,-8.01,-7.4,-10};
		// double[] pruningThreshold = null;
		//
		//
		// createArrays();
		//
		// if (accurate)
		// pruningThreshold = accurateThresholds;
		// else
		// pruningThreshold = fastThresholds;
		//
		// //int startLevel = -1;
		// for (int level=startLevel; level<=endLevel; level++){
		// if (level==-1) continue; // don't do the pre-pre parse
		// if (!isBaseline && level==endLevel) continue;//
		// this.grammar = grammarCascade[level-startLevel];
		// this.lexicon = lexiconCascade[level-startLevel];
		// this.numSubStatesArray = grammar.numSubStates;
		// if (level==startLevel) setConstraints(null, false);
		//
		// scrubArrays();
		//
		//
		// initializeChart(sentence,noSmoothing,posTags);
		// final boolean viterbi = true;
		// doConstrainedInsideScores(viterbi);
		// score = iScorePostU[0][length][0][0];
		//
		//
		// if (score==Double.NEGATIVE_INFINITY) continue;
		// //
		// System.out.println("\nFound a parse for sentence with length "+length+". The LL is "+score+".");
		// oScorePreU[0][length][0][0] = 0.0;
		// doConstrainedOutsideScores(viterbi);
		//
		// pruneChart(/*Double.NEGATIVE_INFINITY*/pruningThreshold[level+1],
		// level);
		// }

	}

	protected void pruneChart(double threshold, int level) {
		int totalStates = 0, previouslyPossible = 0, nowPossible = 0;

		double sentenceProb = iScorePostU[0][length][0][0];
		double sentenceScale = iScale[0][length][0];
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

					if (allowedSubStates[start][end][state] == null)
						continue;
					boolean nonePossible = true;
					int thisScale = iScale[start][end][state]
							+ oScale[start][end][state];
					double scalingFactor = 1;
					if (thisScale != sentenceScale) {
						scalingFactor *= Math.pow(ScalingTools.SCALE, thisScale
								- sentenceScale);
					}

					for (int substate = 0; substate < numSubStatesArray[state]; substate++) {
						totalStates++;
						if (!allowedSubStates[start][end][state][substate])
							continue;
						previouslyPossible++;
						double iS = iScorePostU[start][end][state][substate];
						double oS = oScorePostU[start][end][state][substate];

						if (iS == 0 || oS == 0) {
							allowedSubStates[start][end][state][substate] = false;
							continue;
						}
						double posterior = iS * scalingFactor * oS
								/ sentenceProb;
						if (posterior > threshold) {
							allowedSubStates[start][end][state][substate] = true;
							nowPossible++;
							nonePossible = false;
						} else {
							allowedSubStates[start][end][state][substate] = false;
						}
					}
					if (nonePossible)
						allowedSubStates[start][end][state] = null;
				}
			}
		}
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

	public double[][] getBracketPosteriors() {
		double tree_score = iScorePostU[0][length][0][0];
		int tree_scale = iScale[0][length][0];
		double[][] result = new double[length][length + 1];
		for (int start = 0; start < length; start++) {
			for (int end = start + 1; end <= length; end++) {

				final int lastState = numSubStatesArray.length;
				for (int state = 0; state < lastState; state++) {

					if (allowedSubStates[start][end][state] == null)
						continue;

					for (int substate = 0; substate < numSubStatesArray[state]; substate++) {
						if (!allowedSubStates[start][end][state][substate])
							continue;
						double iS = iScorePostU[start][end][state][substate];
						if (iS == 0)
							continue;
						double oS = oScorePostU[start][end][state][substate];
						if (oS == 0)
							continue;

						double scalingFactor = ScalingTools
								.calcScaleFactor(oScale[start][end][state]
										+ iScale[start][end][state]
										- tree_scale);
						if (scalingFactor == 0)
							continue;

						double tmp = Math.max(iS
								* oScorePreU[start][end][state][substate],
								iScorePreU[start][end][state][substate] * oS);
						double posterior = tmp / tree_score * scalingFactor;
						// if (posterior>1.01)
						// System.out.println("too much");
						result[start][end] += posterior;
						// if (result[start][end]>1.01)
						// result[start][end] = 1;
						// System.out.println("too much");
					}
				}
			}
		}
		return result;
	}

	/**
	 * Return the single best parse. Note that the returned tree may be missing
	 * intermediate nodes in a unary chain because it parses with a unary-closed
	 * grammar.
	 */
	public Tree<String> extractBestViterbiParse(int gState, int gp, int start,
			int end, List<String> sentence, boolean unaryAllowed) {
		// find sources of inside score
		// no backtraces so we can speed up the parsing for its primary use
		double bestScore = (unaryAllowed) ? iScorePostU[start][end][gState][gp]
				: iScorePreU[start][end][gState][gp];
		String goalStr = (String) tagNumberer.object(gState);
		// System.out.println("Looking for "+goalStr+" from "+start+" to "+end+" with score "+
		// bestScore+".");
		if (end - start == 1) {
			// if the goal state is a preterminal state, then it can't transform
			// into
			// anything but the word below it
			// if (lexicon.getAllTags().contains(gState)) {
			if (!grammar.isGrammarTag[gState]) {
				List<Tree<String>> child = new ArrayList<Tree<String>>();
				child.add(new Tree<String>(sentence.get(start)));
				return new Tree<String>(goalStr, child);
			}
			// if the goal state is not a preterminal state, then find a way to
			// transform it into one
			else {
				double veryBestScore = Double.NEGATIVE_INFINITY;
				int newIndex = -1;
				UnaryRule[] unaries = grammar
						.getClosedViterbiUnaryRulesByParent(gState);
				for (int r = 0; r < unaries.length; r++) {
					UnaryRule ur = unaries[r];
					int cState = ur.childState;
					double[][] scores = ur.getScores2();
					for (int cp = 0; cp < scores.length; cp++) {
						if (scores[cp] == null)
							continue;
						double ruleScore = iScorePreU[start][end][cState][cp]
								* scores[cp][gp];
						if ((ruleScore >= veryBestScore)
								&& (gState != cState || gp != cp)
								&& (!grammar.isGrammarTag[ur.getChildState()])) {
							// && lexicon.getAllTags().contains(cState)) {
							veryBestScore = ruleScore;
							newIndex = cState;
						}
					}
				}
				List<Tree<String>> child1 = new ArrayList<Tree<String>>();
				child1.add(new Tree<String>(sentence.get(start)));
				String goalStr1 = (String) tagNumberer.object(newIndex);
				if (goalStr1 == null)
					System.out.println("goalStr1==null with newIndex=="
							+ newIndex + " goalStr==" + goalStr);
				List<Tree<String>> child = new ArrayList<Tree<String>>();
				child.add(new Tree<String>(goalStr1, child1));
				return new Tree<String>(goalStr, child);
			}
		}
		// check binaries first
		for (int split = start + 1; split < end; split++) {
			// for (Iterator binaryI = grammar.bRuleIteratorByParent(gState,
			// gp); binaryI.hasNext();) {
			// BinaryRule br = (BinaryRule) binaryI.next();
			BinaryRule[] parentRules = grammar.splitRulesWithP(gState);
			for (int i = 0; i < parentRules.length; i++) {
				BinaryRule br = parentRules[i];

				int lState = br.leftChildState;
				if (iScorePostU[start][split][lState] == null)
					continue;

				int rState = br.rightChildState;
				if (iScorePostU[split][end][rState] == null)
					continue;

				// new: iterate over substates
				double[][][] scores = br.getScores2();
				for (int lp = 0; lp < scores.length; lp++) {
					for (int rp = 0; rp < scores[lp].length; rp++) {
						if (scores[lp][rp] == null)
							continue;
						double score = ScalingTools.scaleToScale(
								scores[lp][rp][gp]
										* iScorePostU[start][split][lState][lp]
										* iScorePostU[split][end][rState][rp],
								iScale[start][split][lState]
										+ iScale[split][end][rState],
								iScale[start][end][gState]);
						if (matches(score, bestScore)) {
							// build binary split
							Tree<String> leftChildTree = extractBestViterbiParse(
									lState, lp, start, split, sentence, true);
							Tree<String> rightChildTree = extractBestViterbiParse(
									rState, rp, split, end, sentence, true);
							List<Tree<String>> children = new ArrayList<Tree<String>>();
							children.add(leftChildTree);
							children.add(rightChildTree);
							Tree<String> result = new Tree<String>(goalStr,
									children);
							// System.out.println("Binary node: "+result);
							// result.setScore(score);
							return result;
						}
					}
				}
			}
		}
		// check unaries
		// for (Iterator unaryI = grammar.uRuleIteratorByParent(gState, gp);
		// unaryI.hasNext();) {
		// UnaryRule ur = (UnaryRule) unaryI.next();
		UnaryRule[] unaries = grammar
				.getClosedViterbiUnaryRulesByParent(gState);
		for (int r = 0; r < unaries.length; r++) {
			UnaryRule ur = unaries[r];
			int cState = ur.childState;

			if (iScorePostU[start][end][cState] == null)
				continue;

			// new: iterate over substates
			double[][] scores = ur.getScores2();
			for (int cp = 0; cp < scores.length; cp++) {
				if (scores[cp] == null)
					continue;
				double score = ScalingTools.scaleToScale(scores[cp][gp]
						* iScorePreU[start][end][cState][cp],
						iScale[start][end][cState], iScale[start][end][gState]);
				if ((cState != ur.parentState || cp != gp)
						&& matches(score, bestScore)) {
					// build unary
					Tree<String> childTree = extractBestViterbiParse(cState,
							cp, start, end, sentence, false);
					List<Tree<String>> children = new ArrayList<Tree<String>>();
					children.add(childTree);
					Tree<String> result = new Tree<String>(goalStr, children);
					// System.out.println("Unary node: "+result);
					// result.setScore(score);
					return result;
				}
			}
		}
		System.err
				.println("Warning: could not find the optimal way to build state "
						+ goalStr
						+ " spanning from "
						+ start
						+ " to "
						+ end
						+ ".");
		return null;
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

	public int[][][] getInsideScalingFactors() {
		return iScale;
	}

	public int[][][] getOutsideScalingFactors() {
		return oScale;
	}

}
