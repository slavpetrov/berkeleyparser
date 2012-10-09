/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.util.Arrays;
import java.util.List;

import edu.berkeley.nlp.discPCFG.Linearizer;
import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.math.SloppyMath;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.ScalingTools;

/**
 * @author petrov
 * 
 */
public class ConstrainedHierarchicalTwoChartParser extends
		ConstrainedTwoChartsParser {
	/**
	 * inside and outside scores; start idx, end idx, state, substate ->
	 * logProb/prob
	 */
	/** NEW: we now have two charts one before applying unaries and one after: */
	protected double[][][][][] h_iScorePreU, h_iScorePostU; // [start][end][state][level][substate]
	protected double[][][][][] h_oScorePreU, h_oScorePostU;

	int finalLevel;
	int[] substatesToCover;

	public ConstrainedHierarchicalTwoChartParser(Grammar gr, Lexicon lex,
			SpanPredictor sp, int f) {
		super(gr, lex, sp);
		finalLevel = f;
		substatesToCover = new int[finalLevel + 1];
		for (int i = 0; i <= finalLevel; i++)
			substatesToCover[i] = (int) Math.pow(2, finalLevel - i);
	}

	@Override
	void doConstrainedInsideScores(final boolean viterbi) {
		doConstrainedInsideScores(viterbi, null);
	}

	@Override
	void doConstrainedInsideScores(final boolean viterbi,
			final double[][][] spanScores) {
		for (int diff = 1; diff <= length; diff++) {
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
						HierarchicalAdaptiveBinaryRule r = (HierarchicalAdaptiveBinaryRule) parentRules[i];
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

						for (int split = min; split <= max; split++) {
							boolean changeThisRound = false;
							if (allowedSubStates[start][split][lState] == null)
								continue;
							if (allowedSubStates[split][end][rState] == null)
								continue;

							changeThisRound = computeInsideScore(start, split,
									end, r, viterbi);

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
									ScalingTools
											.scaleArrayToScale(
													h_iScorePreU[start][end][pState][finalLevel],
													parentScale, newScale);
									iScale[start][end][pState] = newScale;
								}
							}
							for (int np = 0; np < nParentStates; np++) {
								if (viterbi) {
									h_iScorePreU[start][end][pState][finalLevel][np] = Math
											.max(h_iScorePreU[start][end][pState][finalLevel][np],
													unscaledScoresToAdd[np]);
								} else {
									h_iScorePreU[start][end][pState][finalLevel][np] += unscaledScoresToAdd[np];
								}
							}
							Arrays.fill(unscaledScoresToAdd, 0);
						}
					}
					if (somethingChanged) {
						// apply span predictions
						if (spanScores != null) {
							double val = spanScores[start][end][stateClass[pState]];
							if (val != 1) {
								for (int np = 0; np < nParentStates; np++) {
									h_iScorePreU[start][end][pState][finalLevel][np] *= val;
								}
							}
						}

						updateHierarchy(h_iScorePreU[start][end][pState]);

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
					if (diff == length && pState != 0)
						continue;
					if (allowedSubStates[start][end][pState] == null)
						continue;
					// if (pState==0)
					// System.out.println("ROOT");
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
						HierarchicalAdaptiveUnaryRule ur = (HierarchicalAdaptiveUnaryRule) unaries[r];
						int cState = ur.childState;
						if ((pState == cState))
							continue;

						if (allowedSubStates[start][end][cState] == null)
							continue;
						if (h_iScorePreU[start][end][cState] == null)
							continue;

						boolean changeThisRound = computeInsideScore(start,
								end, ur, viterbi);

						if (!changeThisRound)
							continue;
						somethingChanged = true;
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
								ScalingTools
										.scaleArrayToScale(
												h_iScorePostU[start][end][pState][finalLevel],
												parentScale, newScale);
								parentScale = newScale;
							}
						}
						for (int np = 0; np < nParentStates; np++) {
							if (viterbi) {
								h_iScorePostU[start][end][pState][finalLevel][np] = Math
										.max(h_iScorePostU[start][end][pState][finalLevel][np],
												unscaledScoresToAdd[np]);
							} else {
								h_iScorePostU[start][end][pState][finalLevel][np] += unscaledScoresToAdd[np];
							}
						}
						Arrays.fill(unscaledScoresToAdd, 0);
					}
					if (somethingChanged) {
						int newScale = Math
								.max(scaleBeforeUnaries, parentScale);
						ScalingTools.scaleArrayToScale(
								h_iScorePreU[start][end][pState][finalLevel],
								scaleBeforeUnaries, newScale);
						ScalingTools.scaleArrayToScale(
								h_iScorePostU[start][end][pState][finalLevel],
								parentScale, newScale);
						iScale[start][end][pState] = newScale;

						if (newScale != scaleBeforeUnaries) {
							if (pState != 0)
								updateHierarchy(h_iScorePreU[start][end][pState]);
						}

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
					// in any case copy/add the scores from before and apply the
					// spanScores
					for (int np = 0; np < nParentStates; np++) {
						double val = h_iScorePreU[start][end][pState][finalLevel][np];
						if (val > 0) {
							if (viterbi) {
								h_iScorePostU[start][end][pState][finalLevel][np] = Math
										.max(h_iScorePostU[start][end][pState][finalLevel][np],
												val);
							} else {
								h_iScorePostU[start][end][pState][finalLevel][np] += val;
							}
						}
					}
					if (pState != 0)
						updateHierarchy(h_iScorePostU[start][end][pState]);
				}
			}
		}
	}

	private final void updateHierarchy(double[][] ds) {
		for (int level = finalLevel - 1; level >= 0; level--) {
			for (int i = 0; i < substatesToCover[finalLevel - level]; i++) {
				ds[level][i] = ds[level + 1][2 * i] + ds[level + 1][2 * i + 1];
			}
		}
	}

	private final boolean computeInsideScore(int start, int split, int end,
			HierarchicalAdaptiveBinaryRule rule, boolean viterbi) {
		int pState = rule.parentState;
		int lState = rule.leftChildState;
		int rState = rule.rightChildState;
		boolean changeThisRound = false;
		for (HierarchicalAdaptiveBinaryRule.SubRule subRule : rule.subRuleList) {
			if (subRule == null)
				continue;
			int level = subRule.level;
			double lS = h_iScorePostU[start][split][lState][level][subRule.lChild];
			if (lS == 0)
				continue;

			double rS = h_iScorePostU[split][end][rState][level][subRule.rChild];
			if (rS == 0)
				continue;

			double score = lS * rS * subRule.score;

			int k = substatesToCover[level] * subRule.parent;
			final int l = k + substatesToCover[level];
			// boolean countMe = false;
			for (int np = k; np < l; np++) {
				if (!allowedSubStates[start][end][pState][np])
					continue;
				// countMe = true;
				if (viterbi) {
					unscaledScoresToAdd[np] = Math.max(unscaledScoresToAdd[np],
							score);
				} else {
					unscaledScoresToAdd[np] += score;
				}
				changeThisRound = true;
			}
			// if (countMe) edgesTouched++;

		}
		return changeThisRound;
	}

	private final boolean computeInsideScore(int start, int end,
			HierarchicalAdaptiveUnaryRule rule, boolean viterbi) {
		int pState = rule.parentState;
		int cState = rule.childState;
		boolean changeThisRound = false;
		for (HierarchicalAdaptiveUnaryRule.SubRule subRule : rule.subRuleList) {
			if (subRule == null)
				continue;
			int level = subRule.level;
			double cS = h_iScorePreU[start][end][cState][level][subRule.child];
			if (cS == 0)
				continue;

			double score = cS * subRule.score;

			int k = substatesToCover[level] * subRule.parent;
			int l = k + substatesToCover[level];
			if (pState == 0)
				l = 1;

			// boolean countMe = false;
			for (int np = k; np < l; np++) {
				if (!allowedSubStates[start][end][pState][np])
					continue;
				// countMe = true;
				if (viterbi) {
					unscaledScoresToAdd[np] = Math.max(unscaledScoresToAdd[np],
							score);
				} else {
					unscaledScoresToAdd[np] += score;
				}
				changeThisRound = true;
			}
			// if (countMe) edgesTouched++;

		}
		return changeThisRound;
	}

	@Override
	void doConstrainedOutsideScores(final boolean viterbi) {
		doConstrainedOutsideScores(viterbi, null);
	}

	@Override
	void doConstrainedOutsideScores(final boolean viterbi,
			final double[][][] spanScores) {
		double initVal = 0;
		// Arrays.fill(scoresToAdd,initVal);
		for (int diff = length; diff >= 1; diff--) {
			for (int start = 0; start + diff <= length; start++) {
				int end = start + diff;
				// do unaries

				final int nStates = (diff == length) ? 1
						: numSubStatesArray.length;
				for (int pState = 0; pState < nStates; pState++) {
					if (allowedSubStates[start][end][pState] == null)
						continue;
					// if (end-start>1 && !grammarTags[cState]) continue;
					final int nChildStates = numSubStatesArray[pState];

					// apply span predictions
					if (spanScores != null) {
						double val = spanScores[start][end][stateClass[pState]];
						if (val != 1) {
							for (int np = 0; np < nChildStates; np++) {
								h_oScorePreU[start][end][pState][finalLevel][np] *= val;
							}
						}
						if (pState != 0)
							updateHierarchy(h_oScorePreU[start][end][pState]);
						else {
							val = h_oScorePreU[start][end][0][finalLevel][0];
							for (int level = finalLevel - 1; level >= 0; level--) {
								h_oScorePreU[start][end][0][level][0] = val;
							}
						}

					}
				}
				for (int cState = 0; cState < numSubStatesArray.length; cState++) {
					if (allowedSubStates[start][end][cState] == null)
						continue;
					// if (end-start>1 && !grammarTags[cState]) continue;
					final int nChildStates = numSubStatesArray[cState];

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
					boolean somethingChanged = false;
					int childScale = oScale[start][end][cState];
					int scaleBeforeUnaries = childScale;

					for (int r = 0; r < rules.length; r++) {
						HierarchicalAdaptiveUnaryRule ur = (HierarchicalAdaptiveUnaryRule) rules[r];
						int pState = ur.parentState;
						if ((pState == cState))
							continue;
						if (allowedSubStates[start][end][pState] == null)
							continue;

						boolean changeThisRound = computeOutsideScore(start,
								end, ur, viterbi);

						if (!changeThisRound)
							continue;
						somethingChanged = true;
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
								ScalingTools
										.scaleArrayToScale(
												h_oScorePostU[start][end][cState][finalLevel],
												childScale, newScale);
								childScale = newScale;
							}
						}
						for (int cp = 0; cp < nChildStates; cp++) {
							if (viterbi) {
								h_oScorePostU[start][end][cState][finalLevel][cp] = Math
										.max(h_oScorePostU[start][end][cState][finalLevel][cp],
												unscaledScoresToAdd[cp]);
							} else {
								h_oScorePostU[start][end][cState][finalLevel][cp] += unscaledScoresToAdd[cp];
							}
						}
						Arrays.fill(unscaledScoresToAdd, initVal);
					}
					if (somethingChanged) {
						int newScale = Math.max(scaleBeforeUnaries, childScale);
						ScalingTools.scaleArrayToScale(
								h_oScorePreU[start][end][cState][finalLevel],
								scaleBeforeUnaries, newScale);
						ScalingTools.scaleArrayToScale(
								h_oScorePostU[start][end][cState][finalLevel],
								childScale, newScale);
						oScale[start][end][cState] = newScale;

						if (newScale != scaleBeforeUnaries) {
							updateHierarchy(h_oScorePreU[start][end][cState]);
						}
					}
					// copy/add the entries where the unaries were not useful
					for (int cp = 0; cp < nChildStates; cp++) {
						double val = h_oScorePreU[start][end][cState][finalLevel][cp];
						if (val > 0) {
							if (viterbi) {
								h_oScorePostU[start][end][cState][finalLevel][cp] = Math
										.max(h_oScorePostU[start][end][cState][finalLevel][cp],
												val);
							} else {
								h_oScorePostU[start][end][cState][finalLevel][cp] += val;
							}
						}
					}
					if (cState != 0)
						updateHierarchy(h_oScorePostU[start][end][cState]);
					else {
						double val = h_oScorePostU[start][end][0][finalLevel][0];
						for (int level = finalLevel - 1; level >= 0; level--) {
							h_oScorePostU[start][end][0][level][0] = val;
						}
					}
				}

				// do binaries
				if (diff == 1)
					continue; // there is no space for a binary
				for (int pState = 0; pState < numSubStatesArray.length; pState++) {
					if (allowedSubStates[start][end][pState] == null)
						continue;

					BinaryRule[] rules = grammar.splitRulesWithP(pState);

					// BinaryRule[] rules = grammar.splitRulesWithLC(lState);
					for (int r = 0; r < rules.length; r++) {
						HierarchicalAdaptiveBinaryRule br = (HierarchicalAdaptiveBinaryRule) rules[r];
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

						int nLeftChildStates = numSubStatesArray[lState];
						int nRightChildStates = numSubStatesArray[rState];

						for (int split = min; split <= max; split++) {

							if (allowedSubStates[start][split][lState] == null)
								continue;
							if (allowedSubStates[split][end][rState] == null)
								continue;
							// if (split-start>1 && !grammarTags[lState])
							// continue;
							// if (end-split>1 && !grammarTags[rState])
							// continue;

							boolean somethingChanged = computeOutsideScore(
									start, split, end, br, viterbi);

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
														h_oScorePreU[start][split][lState][finalLevel],
														leftScale, newScale);
										oScale[start][split][lState] = newScale;
									}
								}
								for (int cp = 0; cp < nLeftChildStates; cp++) {
									if (scoresToAdd[cp] > initVal) {
										if (viterbi) {
											h_oScorePreU[start][split][lState][finalLevel][cp] = Math
													.max(h_oScorePreU[start][split][lState][finalLevel][cp],
															scoresToAdd[cp]);
										} else {
											h_oScorePreU[start][split][lState][finalLevel][cp] += scoresToAdd[cp];
										}
									}
								}
								Arrays.fill(scoresToAdd, 0);
								updateHierarchy(h_oScorePreU[start][split][lState]);

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
										ScalingTools
												.scaleArrayToScale(
														h_oScorePreU[split][end][rState][finalLevel],
														rightScale, newScale);
										oScale[split][end][rState] = newScale;
									}
								}
								for (int cp = 0; cp < nRightChildStates; cp++) {
									if (unscaledScoresToAdd[cp] > initVal) {
										if (viterbi) {
											h_oScorePreU[split][end][rState][finalLevel][cp] = Math
													.max(h_oScorePreU[split][end][rState][finalLevel][cp],
															unscaledScoresToAdd[cp]);
										} else {
											h_oScorePreU[split][end][rState][finalLevel][cp] += unscaledScoresToAdd[cp];
										}
									}
								}
								Arrays.fill(unscaledScoresToAdd, 0);
								updateHierarchy(h_oScorePreU[split][end][rState]);

							}
						}
					}
				}
			}
		}
	}

	private final boolean computeOutsideScore(int start, int split, int end,
			HierarchicalAdaptiveBinaryRule rule, boolean viterbi) {
		int pState = rule.parentState;
		int lState = rule.leftChildState;
		int rState = rule.rightChildState;
		boolean changeThisRound = false;
		for (HierarchicalAdaptiveBinaryRule.SubRule subRule : rule.subRuleList) {
			if (subRule == null)
				continue;
			int level = subRule.level;
			double oS = h_oScorePostU[start][end][pState][level][subRule.parent];
			if (oS == 0)
				continue;

			double lS = h_iScorePostU[start][split][lState][level][subRule.lChild];

			double rS = h_iScorePostU[split][end][rState][level][subRule.rChild];

			double pS = subRule.score;

			double thisRoundL = pS * rS * oS;
			double thisRoundR = pS * lS * oS;

			if (thisRoundL != 0) {
				int k = substatesToCover[level] * subRule.lChild;
				final int l = k + substatesToCover[level];
				for (int lp = k; lp < l; lp++) {
					if (!allowedSubStates[start][split][lState][lp])
						continue;
					if (viterbi) {
						scoresToAdd[lp] = Math.max(scoresToAdd[lp], thisRoundL);
					} else {
						scoresToAdd[lp] += thisRoundL;
					}
					changeThisRound = true;
				}
			}
			if (thisRoundR != 0) {
				int k = substatesToCover[level] * subRule.rChild;
				final int m = k + substatesToCover[level];
				for (int rp = k; rp < m; rp++) {
					if (!allowedSubStates[split][end][rState][rp])
						continue;
					if (viterbi) {
						unscaledScoresToAdd[rp] = Math.max(
								unscaledScoresToAdd[rp], thisRoundR);
					} else {
						unscaledScoresToAdd[rp] += thisRoundR;
					}
					changeThisRound = true;
				}
			}
		}
		return changeThisRound;
	}

	private final boolean computeOutsideScore(int start, int end,
			HierarchicalAdaptiveUnaryRule rule, boolean viterbi) {
		int pState = rule.parentState;
		int cState = rule.childState;
		boolean changeThisRound = false;
		for (HierarchicalAdaptiveUnaryRule.SubRule subRule : rule.subRuleList) {
			if (subRule == null)
				continue;
			int level = subRule.level;
			double pS = h_oScorePreU[start][end][pState][level][subRule.parent];
			if (pS == 0)
				continue;

			double score = pS * subRule.score;

			int k = substatesToCover[level] * subRule.child;
			final int l = k + substatesToCover[level];
			for (int np = k; np < l; np++) {
				if (!allowedSubStates[start][end][cState][np])
					continue;
				if (viterbi) {
					unscaledScoresToAdd[np] = Math.max(unscaledScoresToAdd[np],
							score);
				} else {
					unscaledScoresToAdd[np] += score;
				}
				changeThisRound = true;
			}
		}
		return changeThisRound;
	}

	@Override
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
				if (grammarTags[tag])
					continue;
				if (allowedSubStates[start][end][tag] == null)
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
					double prob = lexiconScores[n];
					h_iScorePreU[start][end][tag][finalLevel][n] = prob;
				}
				updateHierarchy(h_iScorePreU[start][end][tag]);
			}
			start++;
		}
	}

	@Override
	protected void createArrays() {
		if (arraySize < length) { // if we haven't seen such a long sentence
									// before, allocate arrays
			arraySize = length;
			h_iScorePreU = new double[length][length + 1][][][];
			h_iScorePostU = new double[length][length + 1][][][];
			h_oScorePreU = new double[length][length + 1][][][];
			h_oScorePostU = new double[length][length + 1][][][];
			iScale = new int[length][length + 1][];
			oScale = new int[length][length + 1][];

			for (int start = 0; start < length; start++) {
				for (int end = start + 1; end <= length; end++) {
					h_iScorePreU[start][end] = new double[numStates][finalLevel + 1][];
					h_iScorePostU[start][end] = new double[numStates][finalLevel + 1][];
					h_oScorePreU[start][end] = new double[numStates][finalLevel + 1][];
					h_oScorePostU[start][end] = new double[numStates][finalLevel + 1][];
					iScale[start][end] = new int[numStates];
					oScale[start][end] = new int[numStates];
					Arrays.fill(iScale[start][end], Integer.MIN_VALUE);
					Arrays.fill(oScale[start][end], Integer.MIN_VALUE);

					for (int state = 0; state < numSubStatesArray.length; state++) {
						if (end - start > 1 && !grammarTags[state])
							continue;
						for (int level = 0; level <= finalLevel; level++) {
							h_iScorePreU[start][end][state][level] = new double[numSubStatesArray[state]
									/ substatesToCover[level]];
							h_iScorePostU[start][end][state][level] = new double[numSubStatesArray[state]
									/ substatesToCover[level]];
							h_oScorePreU[start][end][state][level] = new double[numSubStatesArray[state]
									/ substatesToCover[level]];
							h_oScorePostU[start][end][state][level] = new double[numSubStatesArray[state]
									/ substatesToCover[level]];
						}
					}
					for (int level = 0; level <= finalLevel; level++) {
						h_oScorePreU[start][end][0][level] = new double[1];
						h_oScorePostU[start][end][0][level] = new double[1];
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
	protected void scrubArrays() {
		if (h_iScorePostU == null)
			return;
		for (int start = 0; start < length; start++) {
			for (int end = start + 1; end <= length; end++) {
				for (int state = 0; state < numSubStatesArray.length; state++) {
					if (allowedSubStates[start][end][state] != null) {
						if (end - start > 1 && !grammarTags[state])
							continue;
						for (int level = 0; level <= finalLevel; level++) {
							Arrays.fill(h_iScorePreU[start][end][state][level],
									0);
							Arrays.fill(
									h_iScorePostU[start][end][state][level], 0);
							Arrays.fill(h_oScorePreU[start][end][state][level],
									0);
							Arrays.fill(
									h_oScorePostU[start][end][state][level], 0);
						}
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

	@Override
	protected double getLikelihoodAndSetRootOutsideScore() {
		for (int level = 0; level <= finalLevel; level++)
			h_oScorePreU[0][length][0][level][0] = 1.0;

		oScale[0][length][0] = 0;
		return Math.log(h_iScorePostU[0][length][0][finalLevel][0])
				+ (ScalingTools.LOGSCALE * iScale[0][length][0]);
	}

	@Override
	void doConstrainedMaxCScores(List<StateSet> sentence) {
		doConstrainedMaxCScores(sentence, null);
	}

	@Override
	void doConstrainedMaxCScores(List<StateSet> sentence,
			final double[][][] spanScores) {
		maxcScore = new double[length][length + 1][numStates];
		maxcSplit = new int[length][length + 1][numStates];
		maxcChild = new int[length][length + 1][numStates];
		maxcLeftChild = new int[length][length + 1][numStates];
		maxcRightChild = new int[length][length + 1][numStates];
		double tree_score = h_iScorePostU[0][length][0][finalLevel][0];
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

						for (int i = 0; i < parentRules.length; i++) {
							HierarchicalAdaptiveBinaryRule r = (HierarchicalAdaptiveBinaryRule) parentRules[i];
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
								if (scalingFactor == 0)
									continue;

								double ruleScore = computeRuleScore(start,
										split, end, r, tree_score,
										scalingFactor);
								if (ruleScore == 0)
									continue;

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

						double[] lexiconScoreArray = h_iScorePreU[start][end][tag][finalLevel];
						double lexiconScores = 0;

						for (int tp = 0; tp < nTagStates; tp++) {
							double pOS = h_oScorePostU[start][end][tag][finalLevel][tp];
							if (pOS == 0)
								continue;
							double ruleS = lexiconScoreArray[tp];
							if (ruleS == 0)
								continue;
							lexiconScores += (pOS * ruleS) / tree_score;
						}
						if (lexiconScores == 0)
							continue;

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
					final double spanScore = (spanScores != null) ? spanScores[start][end][stateClass[pState]]
							: 1;

					UnaryRule[] unaries = grammar
							.getClosedSumUnaryRulesByParent(pState);

					for (int r = 0; r < unaries.length; r++) {
						HierarchicalAdaptiveUnaryRule ur = (HierarchicalAdaptiveUnaryRule) unaries[r];
						// List<UnaryRule> urules =
						// grammar.getUnaryRulesByParent(pState);//
						// for (UnaryRule ur : urules){
						int cState = ur.childState;
						if ((pState == cState))
							continue;// && (np == cp))continue;
						if (allowedSubStates[start][end][cState] == null)
							continue;

						double scalingFactor = ScalingTools
								.calcScaleFactor(oScale[start][end][pState]
										+ iScale[start][end][cState]
										- tree_scale);
						if (scalingFactor == 0)
							continue;

						double ruleScore = computeRuleScore(start, end, ur,
								tree_score, scalingFactor, spanScore);
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

	private final double computeRuleScore(int start, int split, int end,
			HierarchicalAdaptiveBinaryRule rule, double tree_score,
			double scalingFactor) {
		double ruleScore = 0;
		int pState = rule.parentState;
		int lState = rule.leftChildState;
		int rState = rule.rightChildState;

		for (HierarchicalAdaptiveBinaryRule.SubRule subRule : rule.subRuleList) {
			int level = subRule.level;

			double lS = h_iScorePostU[start][split][lState][level][subRule.lChild];
			if (lS == 0)
				continue;

			double rS = h_iScorePostU[split][end][rState][level][subRule.rChild];
			if (rS == 0)
				continue;

			double pOS = h_oScorePostU[start][end][pState][level][subRule.parent];
			if (pOS == 0)
				continue;

			ruleScore += subRule.score * lS / tree_score * rS * scalingFactor
					* pOS;
			// if (isValidExpectation(ruleCount)){
		}
		return ruleScore;
	}

	private final double computeRuleScore(int start, int end,
			HierarchicalAdaptiveUnaryRule rule, double tree_score,
			double scalingFactor, double spanScore) {
		double ruleScore = 0;
		int pState = rule.parentState;
		int cState = rule.childState;

		for (HierarchicalAdaptiveUnaryRule.SubRule subRule : rule.subRuleList) {
			if (subRule == null)
				continue;
			int level = subRule.level;

			double cS = h_iScorePreU[start][end][cState][level][subRule.child];
			if (cS == 0)
				continue;

			double pOS = h_oScorePreU[start][end][pState][level][subRule.parent];
			if (pOS == 0)
				continue;

			ruleScore += subRule.score * cS / tree_score * scalingFactor
					/ spanScore * pOS;

			// if (isValidExpectation(ruleCount)){
		}
		return ruleScore;
	}

	@Override
	public void incrementExpectedCounts(Linearizer linearizer, double[] probs,
			List<StateSet> sentence) {
		double tree_score = h_iScorePostU[0][length][0][finalLevel][0];// *
																		// h_oScorePreU[0][length][finalLevel][0][0];
		int tree_scale = iScale[0][length][0];
		if (ConditionalTrainer.Options.lockGrammar) {
			linearizer.increment(probs, sentence, getClassBracketPosteriors(),
					false);
			return;
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
					double iS = h_iScorePreU[start][start + 1][tag][finalLevel][substate];
					if (iS == 0)
						continue;
					double oS = h_oScorePostU[start][start + 1][tag][finalLevel][substate];
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

					BinaryRule[] parentRules = grammar.splitRulesWithP(pState);
					for (int i = 0; i < parentRules.length; i++) {
						HierarchicalAdaptiveBinaryRule r = (HierarchicalAdaptiveBinaryRule) parentRules[i];
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

						boolean foundSomething = false;
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

							boolean tmp = computeExpectedCount(start, split,
									end, r, tree_score, scalingFactor);
							foundSomething = foundSomething || tmp;

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

						boolean foundSomething = computeExpectedCount(start,
								end, (HierarchicalAdaptiveUnaryRule) ur,
								tree_score, scalingFactor);
						if (!foundSomething)
							continue;

						linearizer.increment(probs, ur, tmpCountsArray, false); // probs[thisStartIndex
																				// +
																				// curInd-1]
																				// +=
																				// ruleCount;
					}
				}
			}
		}
		if (spanPredictor != null)
			linearizer.increment(probs, sentence, getClassBracketPosteriors(),
					false);
	}

	private final boolean computeExpectedCount(int start, int split, int end,
			HierarchicalAdaptiveBinaryRule rule, double tree_score,
			double scalingFactor) {
		int pState = rule.parentState;
		int lState = rule.leftChildState;
		int rState = rule.rightChildState;
		boolean foundSomething = false;
		int curInd = -1;
		for (HierarchicalAdaptiveBinaryRule.SubRule subRule : rule.subRuleList) {
			if (subRule == null)
				continue; // shouldn't happen!
			int level = subRule.level;
			curInd++;

			double lS = h_iScorePostU[start][split][lState][level][subRule.lChild];
			if (lS == 0)
				continue;

			double rS = h_iScorePostU[split][end][rState][level][subRule.rChild];
			if (rS == 0)
				continue;

			double pOS = h_oScorePostU[start][end][pState][level][subRule.parent];
			if (pOS == 0)
				continue;

			double ruleCount = subRule.score * lS / tree_score * rS
					* scalingFactor * pOS;

			if (isValidExpectation(ruleCount)) {
				tmpCountsArray[curInd] += ruleCount;
				foundSomething = true;
			}// else if (ruleCount!=0)
				// System.out.println("not an expected count, b: "+ruleCount+"\n"+rule.toString());
		}
		return foundSomething;
	}

	private final boolean computeExpectedCount(int start, int end,
			HierarchicalAdaptiveUnaryRule rule, double tree_score,
			double scalingFactor) {
		int pState = rule.parentState;
		int cState = rule.childState;
		boolean foundSomething = false;
		int curInd = -1;
		for (HierarchicalAdaptiveUnaryRule.SubRule subRule : rule.subRuleList) {
			curInd++;
			if (subRule == null)
				continue;
			int level = subRule.level;

			double cS = h_iScorePreU[start][end][cState][level][subRule.child];
			if (cS == 0)
				continue;

			double pOS = h_oScorePreU[start][end][pState][level][subRule.parent];
			if (pOS == 0)
				continue;

			double ruleCount = subRule.score * cS / tree_score * scalingFactor
					* pOS;

			if (spanScores != null) {
				ruleCount /= spanScores[start][end][stateClass[pState]];
			}

			if (isValidExpectation(ruleCount)) {
				tmpCountsArray[curInd] = ruleCount;
				foundSomething = true;
			} // else if (ruleCount!=0)
			// System.out.println("not an expected count, u: "+ruleCount+"\n"+rule.toString()+"\n"+cS
			// +" / "+ tree_score +" * "+scalingFactor +" * "+ pOS);
		}
		return foundSomething;
	}

	@Override
	boolean[][][][] computeAllowedStates(double threshold) {
		double tree_score = h_iScorePostU[0][length][0][finalLevel][0];
		int tree_scale = iScale[0][length][0];
		boolean[][][][] result = new boolean[length][length + 1][][];
		for (int start = 0; start < length; start++) {
			for (int end = start + 1; end <= length; end++) {
				result[start][end] = new boolean[numStates][];

				final int lastState = numSubStatesArray.length;
				for (int state = 0; state < lastState; state++) {
					double spanScore = (spanScores != null) ? spanScores[start][end][stateClass[state]]
							: 1;

					if (allowedSubStates[start][end][state] == null)
						continue;
					boolean atLeastOnePossible = false;

					for (int substate = 0; substate < numSubStatesArray[state]; substate++) {
						if (!allowedSubStates[start][end][state][substate])
							continue;
						double iS = h_iScorePostU[start][end][state][finalLevel][substate];
						// if (iS==0) continue;
						double oS = h_oScorePostU[start][end][state][finalLevel][substate];
						// if (oS==0) continue;

						double scalingFactor = ScalingTools
								.calcScaleFactor(oScale[start][end][state]
										+ iScale[start][end][state]
										- tree_scale);
						if (scalingFactor == 0)
							continue;

						double tmp = Math
								.max(iS
										* h_oScorePreU[start][end][state][finalLevel][substate],
										h_iScorePreU[start][end][state][finalLevel][substate]
												* oS);
						double posterior = tmp / spanScore / tree_score
								* scalingFactor;
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
	 * Calculate the inside scores, P(words_i,j|nonterminal_i,j) of a tree given
	 * the string if words it should parse to.
	 * 
	 * @param tree
	 * @param sentence
	 */
	@Override
	void doInsideScores(Tree<StateSet> tree, boolean noSmoothing,
			boolean debugOutput, double[][][] spanScores) {
		if (grammar.isLogarithmMode() || lexicon.isLogarithmMode())
			throw new Error(
					"Grammar in logarithm mode!  Cannot do inside scores!");
		if (tree.isLeaf()) {
			return;
		}
		List<Tree<StateSet>> children = tree.getChildren();
		for (Tree<StateSet> child : children) {
			if (!child.isLeaf())
				doInsideScores(child, noSmoothing, debugOutput, spanScores);
		}
		StateSet parent = tree.getLabel();
		short pState = parent.getState();
		int nParentStates = parent.numSubStates();
		if (tree.isPreTerminal()) {
			// Plays a role similar to initializeChart()
			StateSet wordStateSet = tree.getChildren().get(0).getLabel();
			double[] lexiconScores = lexicon.score(wordStateSet, pState,
					noSmoothing, false);
			if (lexiconScores.length != nParentStates) {
				System.out.println("Have more scores than substates!");// truncate
																		// the
																		// array
			}
			parent.setIScores(lexiconScores);
			parent.scaleIScores(0);
		} else {
			switch (children.size()) {
			case 0:
				break;
			case 1:
				StateSet child = children.get(0).getLabel();
				short cState = child.getState();
				HierarchicalAdaptiveUnaryRule urule = (HierarchicalAdaptiveUnaryRule) grammar
						.getUnaryRule(pState, cState);
				double[] iScores = new double[nParentStates];

				for (HierarchicalAdaptiveUnaryRule.SubRule subRule : urule.subRuleList) {
					if (subRule == null)
						continue;
					int level = subRule.level;

					int i = substatesToCover[level] * subRule.child;
					int j = i + substatesToCover[level];

					int k = substatesToCover[level] * subRule.parent;
					int l = k + substatesToCover[level];

					double cS = 0;
					for (int cp = i; cp < j; cp++) {
						cS += child.getIScore(cp);
					}

					if (pState == 0)
						l = 1;
					for (int np = k; np < l; np++) {
						double score = cS * subRule.score;
						iScores[np] += score;
					}
				}
				parent.setIScores(iScores);
				parent.scaleIScores(child.getIScale());
				break;
			case 2:
				StateSet leftChild = children.get(0).getLabel();
				StateSet rightChild = children.get(1).getLabel();
				short lState = leftChild.getState();
				short rState = rightChild.getState();
				double[] iScores2 = new double[nParentStates];

				HierarchicalAdaptiveBinaryRule brule = (HierarchicalAdaptiveBinaryRule) grammar
						.getBinaryRule(pState, lState, rState);

				for (HierarchicalAdaptiveBinaryRule.SubRule subRule : brule.subRuleList) {
					if (subRule == null)
						continue;
					int level = subRule.level;

					int e = substatesToCover[level] * subRule.lChild;
					int f = e + substatesToCover[level];

					int i = substatesToCover[level] * subRule.rChild;
					int j = i + substatesToCover[level];

					int k = substatesToCover[level] * subRule.parent;
					int l = k + substatesToCover[level];

					double lS = 0;
					for (int lp = e; lp < f; lp++) {
						lS += leftChild.getIScore(lp);
					}

					double rS = 0;
					for (int rp = i; rp < j; rp++) {
						rS += rightChild.getIScore(rp);
					}

					for (int np = k; np < l; np++) {
						double score = lS * rS * subRule.score;
						iScores2[np] += score;
					}
				}

				if (spanScores != null) {
					for (int i = 0; i < nParentStates; i++) {
						iScores2[i] *= spanScores[parent.from][parent.to][stateClass[pState]];
					}
				}
				parent.setIScores(iScores2);
				parent.scaleIScores(leftChild.getIScale()
						+ rightChild.getIScale());
				break;
			default:
				throw new Error("Malformed tree: more than two children");
			}
		}
	}

	/**
	 * Set the outside score of the root node to P=1.
	 * 
	 * @param tree
	 */
	@Override
	void setRootOutsideScore(Tree<StateSet> tree) {
		tree.getLabel().setOScore(0, 1);
		tree.getLabel().setOScale(0);
	}

	/**
	 * Calculate the outside scores of a tree; that is,
	 * P(nonterminal_i,j|words_0,i; words_j,end). It is calculate from the
	 * inside scores of the tree.
	 * 
	 * <p>
	 * Note: when calling this, call setRootOutsideScore() first.
	 * 
	 * @param tree
	 */
	@Override
	void doOutsideScores(Tree<StateSet> tree, boolean unaryAbove,
			double[][][] spanScores) {
		if (grammar.isLogarithmMode() || lexicon.isLogarithmMode())
			throw new Error(
					"Grammar in logarithm mode!  Cannot do inside scores!");
		if (tree.isLeaf())
			return;
		List<Tree<StateSet>> children = tree.getChildren();
		StateSet parent = tree.getLabel();
		short pState = parent.getState();
		int nParentStates = parent.numSubStates();
		// this sets the outside scores for the children
		if (tree.isPreTerminal()) {

		} else {
			double[] parentScores = parent.getOScores();
			if (spanScores != null && !unaryAbove) {
				for (int i = 0; i < nParentStates; i++) {
					parentScores[i] *= spanScores[parent.from][parent.to][stateClass[pState]];
				}
			}
			switch (children.size()) {
			case 0:
				// Nothing to do
				break;
			case 1:
				StateSet child = children.get(0).getLabel();
				short cState = child.getState();
				int nChildStates = child.numSubStates();
				double[] oScores = new double[nChildStates];
				HierarchicalAdaptiveUnaryRule urule = (HierarchicalAdaptiveUnaryRule) grammar
						.getUnaryRule(pState, cState);

				for (HierarchicalAdaptiveUnaryRule.SubRule subRule : urule.subRuleList) {
					if (subRule == null)
						continue;
					int level = subRule.level;

					int i = substatesToCover[level] * subRule.child;
					int j = i + substatesToCover[level];

					int k = substatesToCover[level] * subRule.parent;
					int l = k + substatesToCover[level];

					if (pState == 0)
						l = 1;
					double pS = 0;
					for (int np = k; np < l; np++) {
						pS += parent.getOScore(np);
					}

					for (int cp = i; cp < j; cp++) {
						double score = pS * subRule.score;
						oScores[cp] += score;
					}
				}
				child.setOScores(oScores);
				child.scaleOScores(parent.getOScale());
				unaryAbove = true;
				break;
			case 2:
				StateSet leftChild = children.get(0).getLabel();
				StateSet rightChild = children.get(1).getLabel();
				int nLeftChildStates = leftChild.numSubStates();
				int nRightChildStates = rightChild.numSubStates();
				short lState = leftChild.getState();
				short rState = rightChild.getState();

				double[] lOScores = new double[nLeftChildStates];
				double[] rOScores = new double[nRightChildStates];
				HierarchicalAdaptiveBinaryRule brule = (HierarchicalAdaptiveBinaryRule) grammar
						.getBinaryRule(pState, lState, rState);

				for (HierarchicalAdaptiveBinaryRule.SubRule subRule : brule.subRuleList) {
					if (subRule == null)
						continue;
					int level = subRule.level;

					int e = substatesToCover[level] * subRule.lChild;
					int f = e + substatesToCover[level];

					int i = substatesToCover[level] * subRule.rChild;
					int j = i + substatesToCover[level];

					int k = substatesToCover[level] * subRule.parent;
					int l = k + substatesToCover[level];

					double lcS = 0;
					for (int lp = e; lp < f; lp++) {
						lcS += leftChild.getIScore(lp);
					}
					double rcS = 0;
					for (int rp = i; rp < j; rp++) {
						rcS += rightChild.getIScore(rp);
					}
					double pS = 0;
					for (int np = k; np < l; np++) {
						pS += parent.getOScore(np);
					}

					double leftScore = pS * subRule.score * rcS;
					for (int lp = e; lp < f; lp++) {
						lOScores[lp] += leftScore;
					}
					double rightScore = pS * subRule.score * lcS;
					for (int rp = i; rp < j; rp++) {
						rOScores[rp] += rightScore;
					}
				}
				leftChild.setOScores(lOScores);
				leftChild.scaleOScores(parent.getOScale()
						+ rightChild.getIScale());
				rightChild.setOScores(rOScores);
				rightChild.scaleOScores(parent.getOScale()
						+ leftChild.getIScale());
				unaryAbove = false;
				break;
			default:
				throw new Error("Malformed tree: more than two children");
			}
			for (Tree<StateSet> child : children) {
				doOutsideScores(child, unaryAbove, spanScores);
			}
		}
	}

	@Override
	public double doInsideOutsideScores(Tree<StateSet> tree,
			boolean noSmoothing, boolean debugOutput, double[][][] spanScores) {
		doInsideScores(tree, noSmoothing, debugOutput, spanScores);
		setRootOutsideScore(tree);
		doOutsideScores(tree, false, spanScores);
		return Math.log(tree.getLabel().getIScore(0))
				+ (ScalingTools.LOGSCALE * tree.getLabel().getIScale());
	}

	@Override
	public void doInsideOutsideScores(Tree<StateSet> tree, boolean noSmoothing,
			boolean debugOutput) {
		doInsideScores(tree, noSmoothing, debugOutput, null);
		setRootOutsideScore(tree);
		doOutsideScores(tree, false, null);
	}

	@Override
	public void incrementExpectedGoldCounts(Linearizer linearizer,
			double[] probs, Tree<StateSet> tree) {
		if (ConditionalTrainer.Options.lockGrammar)
			return;

		incrementExpectedGoldCounts(linearizer, probs, tree, tree.getLabel()
				.getIScore(0), tree.getLabel().getIScale());
	}

	@Override
	public void incrementExpectedGoldCounts(Linearizer linearizer,
			double[] probs, Tree<StateSet> tree, double tree_score,
			int tree_scale) {

		if (tree.isLeaf())
			return;
		if (tree.isPreTerminal()) {
			StateSet parent = tree.getLabel();
			StateSet child = tree.getChildren().get(0).getLabel();

			short tag = tree.getLabel().getState();

			final int nSubStates = grammar.numSubStates[tag];
			double scalingFactor = ScalingTools.calcScaleFactor(parent
					.getOScale() + parent.getIScale() - tree_scale);

			for (short substate = 0; substate < nSubStates; substate++) {
				// weight by the probability of seeing the tag and word
				// together, given the sentence
				double pIS = parent.getIScore(substate); // Parent outside score
				if (pIS == 0) {
					continue;
				}
				double pOS = parent.getOScore(substate); // Parent outside score
				if (pOS == 0) {
					continue;
				}
				double weight = 1;
				weight = (pIS / tree_score) * scalingFactor * pOS;
				if (isValidExpectation(weight)) {
					tmpCountsArray[substate] = weight;
				} else
					System.out.println("Overflow when counting gold tags? "
							+ weight);

			}
			linearizer.increment(probs, child, tag, tmpCountsArray, true); // probs[startIndexWord+substate]
																			// +=
																			// weight;
			return;
		}
		List<Tree<StateSet>> children = tree.getChildren();
		StateSet parent = tree.getLabel();
		short parentState = parent.getState();

		switch (children.size()) {
		case 0:
			// This is a leaf (a preterminal node, if we count the words
			// themselves),
			// nothing to do
			break;
		case 1:
			StateSet child = children.get(0).getLabel();
			short childState = child.getState();

			HierarchicalAdaptiveUnaryRule urule = (HierarchicalAdaptiveUnaryRule) grammar
					.getUnaryRule(parentState, childState);
			double scalingFactor = ScalingTools.calcScaleFactor(parent
					.getOScale() + child.getIScale() - tree_scale);

			int curInd = -1;
			for (HierarchicalAdaptiveUnaryRule.SubRule subRule : urule.subRuleList) {
				curInd++;
				if (subRule == null)
					continue;
				int level = subRule.level;

				int i = substatesToCover[level] * subRule.child;
				int j = i + substatesToCover[level];

				int k = substatesToCover[level] * subRule.parent;
				int l = k + substatesToCover[level];

				if (parentState == 0)
					l = 1;
				double pOS = 0;
				for (int np = k; np < l; np++) {
					pOS += parent.getOScore(np);
				}
				double cS = 0;
				for (int cp = i; cp < j; cp++) {
					cS += child.getIScore(cp);
				}

				double ruleCount = subRule.score * cS / tree_score
						* scalingFactor * pOS;

				if (spanScores != null) {
					ruleCount /= spanScores[child.from][child.to][stateClass[parentState]];
				}

				if (isValidExpectation(ruleCount)) {
					tmpCountsArray[curInd] = ruleCount;
				} else if (ruleCount != 0)
					System.out.println("not an expected gold count, u: "
							+ ruleCount + "\n" + urule.toString());
			}
			linearizer.increment(probs, urule, tmpCountsArray, true);
			break;
		case 2:
			StateSet leftChild = children.get(0).getLabel();
			short lChildState = leftChild.getState();
			StateSet rightChild = children.get(1).getLabel();
			short rChildState = rightChild.getState();

			HierarchicalAdaptiveBinaryRule brule = (HierarchicalAdaptiveBinaryRule) grammar
					.getBinaryRule(parentState, lChildState, rChildState);
			scalingFactor = ScalingTools.calcScaleFactor(parent.getOScale()
					+ leftChild.getIScale() + rightChild.getIScale()
					- tree_scale);

			curInd = -1;
			for (HierarchicalAdaptiveBinaryRule.SubRule subRule : brule.subRuleList) {
				int level = subRule.level;
				curInd++;

				int e = substatesToCover[level] * subRule.lChild;
				int f = e + substatesToCover[level];

				int i = substatesToCover[level] * subRule.rChild;
				int j = i + substatesToCover[level];

				int k = substatesToCover[level] * subRule.parent;
				int l = k + substatesToCover[level];

				double pOS = 0;
				for (int np = k; np < l; np++) {
					pOS += parent.getOScore(np);
				}
				double lS = 0;
				for (int lp = e; lp < f; lp++) {
					lS += leftChild.getIScore(lp);
				}
				double rS = 0;
				for (int rp = i; rp < j; rp++) {
					rS += rightChild.getIScore(rp);
				}

				double ruleCount = subRule.score * lS / tree_score * rS
						* scalingFactor * pOS;

				if (isValidExpectation(ruleCount)) {
					tmpCountsArray[curInd] += ruleCount;
				} else if (ruleCount != 0)
					System.out.println("not an expected gold count, b: "
							+ ruleCount + "\n" + brule.toString());
			}

			linearizer.increment(probs, brule, tmpCountsArray, true);

			break;
		default:
			throw new Error("Malformed tree: more than two children");
		}

		for (Tree<StateSet> child : children) {
			incrementExpectedGoldCounts(linearizer, probs, child, tree_score,
					tree_scale);
		}
	}

	public double[][][] getClassBracketPosteriors() {
		double tree_score = h_iScorePostU[0][length][0][finalLevel][0];// *
																		// h_oScorePreU[0][length][finalLevel][0][0];
		int tree_scale = iScale[0][length][0];
		double[][][] result = new double[length][length + 1][spanPredictor
				.getNClasses()];

		for (int start = 0; start < length; start++) {
			for (int end = start + 1; end <= length; end++) {

				final int lastState = numSubStatesArray.length;
				for (int state = 0; state < lastState; state++) {
					final int clas = stateClass[state];
					final double spanScore = spanScores[start][end][clas];
					double statePosterior = 0;

					if (allowedSubStates[start][end][state] == null)
						continue;

					for (int substate = 0; substate < numSubStatesArray[state]; substate++) {
						if (!allowedSubStates[start][end][state][substate])
							continue;
						double iS = h_iScorePostU[start][end][state][finalLevel][substate];
						double oS = h_oScorePreU[start][end][state][finalLevel][substate];

						double iS2 = h_iScorePreU[start][end][state][finalLevel][substate];
						double oS2 = h_oScorePostU[start][end][state][finalLevel][substate];

						double scalingFactor = ScalingTools
								.calcScaleFactor(oScale[start][end][state]
										+ iScale[start][end][state]
										- tree_scale);
						if (scalingFactor == 0)
							continue;

						double tmp = iS * oS;
						double tmp2 = iS2 * oS2;

						double posterior = Math.max(tmp, tmp2) / spanScore
								/ tree_score * scalingFactor;

						if (SloppyMath.isDangerous(posterior))
							continue;

						if (posterior > 1.01) {
							System.out.println("too much posterior s:" + start
									+ " e:" + end + " state " + state + " "
									+ posterior + " " + spanScores[start][end]);
							if (SloppyMath.isVeryDangerous(posterior))
								posterior = 0;
						}
						result[start][end][clas] += posterior;

						statePosterior += posterior;
					}
					if (statePosterior > 1.01) {
						System.out.println("Too much for a single state: "
								+ statePosterior);
						for (int substate = 0; substate < numSubStatesArray[state]; substate++) {
							if (!allowedSubStates[start][end][state][substate])
								continue;
							double iS = h_iScorePostU[start][end][state][finalLevel][substate];
							double oS = h_oScorePreU[start][end][state][finalLevel][substate];

							double iS2 = h_iScorePreU[start][end][state][finalLevel][substate];
							double oS2 = h_oScorePostU[start][end][state][finalLevel][substate];

							double scalingFactor = ScalingTools
									.calcScaleFactor(oScale[start][end][state]
											+ iScale[start][end][state]
											- tree_scale);
							if (scalingFactor == 0)
								continue;

							double tmp = iS * oS;// Math.max(iS*oS, iS2*oS2);
							double tmp2 = iS2 * oS2;

							double posterior = Math.max(tmp, tmp2) / spanScore
									/ tree_score * scalingFactor;
							System.out.println(posterior);
						}
					}
				}
				if (result[start][end][0] > 2.01) {
					System.out.println("too much in the sum, start " + start
							+ " end " + end + "  " + result[start][end]);
					result[start][end][0] = 0;
				}
			}
		}
		// System.out.println("length "+length);
		return result;
	}

}
