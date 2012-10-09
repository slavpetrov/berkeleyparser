/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.ScalingTools;

/**
 * @author petrov
 * 
 */
public class CoarseToFineMaxRuleDerivationParser extends
		CoarseToFineMaxRuleParser {

	protected double[][][][] maxcScore; // start, end, state --> logProb
	protected double[][][][] maxsScore; // start, end, state --> logProb
	protected int[][][][] maxcSplit; // start, end, state -> split position
	protected int[][][][] maxcChild; // start, end, state -> unary child (if
										// any)
	protected int[][][][] maxcChildSub; // start, end, state -> unary child (if
										// any)
	protected int[][][][] maxcLeftChild; // start, end, state -> left child
	protected int[][][][] maxcRightChild; // start, end, state -> right child
	protected int[][][][] maxcLeftChildSub; // start, end, state -> left child
	protected int[][][][] maxcRightChildSub; // start, end, state -> right child

	public CoarseToFineMaxRuleDerivationParser(Grammar gr, Lexicon lex,
			double unaryPenalty, int endL, boolean viterbi, boolean sub,
			boolean score, boolean accurate, boolean variational,
			boolean useGoldPOS, boolean initializeCascade) {
		super(gr, lex, unaryPenalty, endL, viterbi, sub, score, accurate,
				variational, useGoldPOS, initializeCascade);
	}

	@Override
	void doConstrainedMaxCScores(List<String> sentence, Grammar grammar,
			Lexicon lexicon, final boolean scale) {
		numSubStatesArray = grammar.numSubStates;
		maxcScore = new double[length][length + 1][numStates][];
		maxcSplit = new int[length][length + 1][numStates][];
		maxcChild = new int[length][length + 1][numStates][];
		maxcChildSub = new int[length][length + 1][numStates][];
		maxcLeftChild = new int[length][length + 1][numStates][];
		maxcRightChild = new int[length][length + 1][numStates][];
		maxcLeftChildSub = new int[length][length + 1][numStates][];
		maxcRightChildSub = new int[length][length + 1][numStates][];

		double initVal = Double.NEGATIVE_INFINITY;

		for (int start = 0; start < length; start++) {
			for (int end = start + 1; end <= length; end++) {
				for (int state = 0; state < numSubStatesArray.length; state++) {
					if (!allowedStates[start][end][state])
						continue;
					maxcSplit[start][end][state] = new int[numSubStatesArray[state]];
					maxcChild[start][end][state] = new int[numSubStatesArray[state]];
					maxcChildSub[start][end][state] = new int[numSubStatesArray[state]];
					maxcLeftChild[start][end][state] = new int[numSubStatesArray[state]];
					maxcRightChild[start][end][state] = new int[numSubStatesArray[state]];
					maxcLeftChildSub[start][end][state] = new int[numSubStatesArray[state]];
					maxcRightChildSub[start][end][state] = new int[numSubStatesArray[state]];
					maxcScore[start][end][state] = new double[numSubStatesArray[state]];
					Arrays.fill(maxcSplit[start][end][state], -1);
					Arrays.fill(maxcChild[start][end][state], -1);
					Arrays.fill(maxcChildSub[start][end][state], -1);
					Arrays.fill(maxcLeftChild[start][end][state], -1);
					Arrays.fill(maxcRightChild[start][end][state], -1);
					Arrays.fill(maxcLeftChildSub[start][end][state], -1);
					Arrays.fill(maxcRightChildSub[start][end][state], -1);
					Arrays.fill(maxcScore[start][end][state], initVal);
				}
			}
		}

		double logNormalizer = iScore[0][length][0][0];
		// double thresh2 = threshold*logNormalizer;
		for (int diff = 1; diff <= length; diff++) {
			// System.out.print(diff + " ");
			for (int start = 0; start < (length - diff + 1); start++) {
				int end = start + diff;
				if (diff > 1) {
					// diff > 1: Try binary rules
					for (int pState = 0; pState < numSubStatesArray.length; pState++) {
						if (!allowedStates[start][end][pState])
							continue;
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
								if (!allowedStates[start][split][lState])
									continue;
								if (!allowedStates[split][end][rState])
									continue;

								double scalingFactor = 0.0;
								if (scale)
									scalingFactor = Math
											.log(ScalingTools
													.calcScaleFactor(oScale[start][end][pState]
															+ iScale[start][split][lState]
															+ iScale[split][end][rState]
															- iScale[0][length][0]));

								for (int lp = 0; lp < nLeftChildStates; lp++) {
									double lIS = iScore[start][split][lState][lp];
									if (lIS == 0)
										continue;
									// if (lIS < thresh2) continue;
									// if
									// (!allowedSubStates[start][split][lState][lp])
									// continue;

									for (int rp = 0; rp < nRightChildStates; rp++) {
										if (scores[lp][rp] == null)
											continue;
										double rIS = iScore[split][end][rState][rp];
										if (rIS == 0)
											continue;

										double leftChildScore = maxcScore[start][split][lState][lp];
										double rightChildScore = maxcScore[split][end][rState][rp];
										if (leftChildScore == initVal
												|| rightChildScore == initVal)
											continue;
										double gScore = leftChildScore
												+ scalingFactor
												+ rightChildScore;

										for (int np = 0; np < nParentStates; np++) {
											double pOS = oScore[start][end][pState][np];
											if (pOS == 0)
												continue;

											double scoreToBeat = maxcScore[start][end][pState][np];
											if (gScore < scoreToBeat)
												continue; // no chance of
															// finding a better
															// derivation

											double ruleS = scores[lp][rp][np];
											if (ruleS == 0)
												continue;
											ruleScore = (pOS * ruleS * lIS * rIS)
													/ logNormalizer;

											if (ruleScore == 0)
												continue;
											if (doVariational) {
												ruleScore /= oScore[start][end][pState][np]
														/ logNormalizer
														* iScore[start][end][pState][np];
											}

											ruleScore = gScore
													+ Math.log(ruleScore);

											if (ruleScore > scoreToBeat) {
												maxcScore[start][end][pState][np] = ruleScore;
												maxcSplit[start][end][pState][np] = split;
												maxcLeftChild[start][end][pState][np] = lState;
												maxcRightChild[start][end][pState][np] = rState;
												maxcLeftChildSub[start][end][pState][np] = lp;
												maxcRightChildSub[start][end][pState][np] = rp;
											}

										}
									}
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
						if (!allowedStates[start][end][tag])
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
							double pOS = oScore[start][end][tag][tp];
							// if (pOS < thresh2) continue;
							double ruleS = lexiconScoreArray[tp];
							lexiconScores = (pOS * ruleS) / logNormalizer; // The
																			// inside
																			// score
																			// of
																			// a
																			// word
																			// is
																			// 0.0f
							double scalingFactor = 0.0;
							if (doVariational)
								lexiconScores = 1;
							else if (scale)
								scalingFactor = Math
										.log(ScalingTools
												.calcScaleFactor(oScale[start][end][tag]
														- iScale[0][length][0]));

							maxcScore[start][end][tag][tp] = Math
									.log(lexiconScores) + scalingFactor;
						}
					}
				}
				// Try unary rules
				// Replacement for maxcScore[start][end], which is updated in
				// batch
				double[][] maxcScoreStartEnd = new double[numStates][];
				for (int i = 0; i < numStates; i++) {
					if (!allowedStates[start][end][i])
						continue;
					maxcScoreStartEnd[i] = new double[numSubStatesArray[i]];
					for (int j = 0; j < numSubStatesArray[i]; j++) {
						maxcScoreStartEnd[i][j] = maxcScore[start][end][i][j];
					}
				}
				// double[] unaryBonus = new double[numStates];
				// int[] unaryChild = new int[numStates];
				double[][] ruleScores = null;
				if (doVariational)
					ruleScores = new double[numStates][numStates];
				boolean foundOne = false;
				for (int pState = 0; pState < numSubStatesArray.length; pState++) {
					if (!allowedStates[start][end][pState])
						continue;
					int nParentStates = numSubStatesArray[pState]; // ==
																	// scores[0].length;
					UnaryRule[] unaries = grammar
							.getClosedSumUnaryRulesByParent(pState);
					if (doVariational)
						unaries = grammar.getUnaryRulesByParent(pState)
								.toArray(new UnaryRule[0]);
					for (int r = 0; r < unaries.length; r++) {
						UnaryRule ur = unaries[r];
						int cState = ur.childState;
						if ((pState == cState))
							continue;// && (np == cp))continue;
						if (iScore[start][end][cState] == null)
							continue;

						double scalingFactor = 0.0;
						if (scale)
							scalingFactor = Math.log(ScalingTools
									.calcScaleFactor(oScale[start][end][pState]
											+ iScale[start][end][cState]
											- iScale[0][length][0]));

						double[][] scores = ur.getScores2();
						int nChildStates = numSubStatesArray[cState]; // ==
																		// scores.length;
						double ruleScore = 0;
						for (int cp = 0; cp < nChildStates; cp++) {
							double cIS = iScore[start][end][cState][cp];
							if (cIS == 0)
								continue;

							double childScore = maxcScore[start][end][cState][cp];
							if (childScore == initVal)
								continue;

							if (scores[cp] == null)
								continue;
							for (int np = 0; np < nParentStates; np++) {
								double pOS = oScore[start][end][pState][np];
								if (pOS < 0)
									continue;

								double gScore = scalingFactor + childScore;
								if (gScore < maxcScoreStartEnd[pState][np])
									continue;

								double ruleS = scores[cp][np];
								if (ruleS == 0)
									continue;
								ruleScore = (pOS * ruleS * cIS) / logNormalizer;
								foundOne = true;

								if (ruleScore == 0)
									continue;
								if (doVariational) {
									ruleScore /= oScore[start][end][pState][np]
											/ logNormalizer
											* iScore[start][end][pState][np];
								}

								ruleScore = gScore + Math.log(ruleScore);

								if (ruleScore > maxcScoreStartEnd[pState][np]) {
									maxcScoreStartEnd[pState][np] = ruleScore;
									maxcChild[start][end][pState][np] = cState;
									maxcChildSub[start][end][pState][np] = cp;
								}
							}
						}
					}
				}
				// for (int i = 0; i < numStates; i++) {
				// if (maxcScore[start][end][i]+(1-unaryBonus[i]) >
				// maxcScoreStartEnd[i]){
				// maxcScore[start][end][i]+=(1-unaryBonus[i]);
				// } else {
				// maxcScore[start][end][i] = maxcScoreStartEnd[i];
				// maxcChild[start][end][i] = unaryChild[i];
				// }
				// }
				// if (foundOne&&doVariational) maxcScoreStartEnd =
				// closeVariationalRules(ruleScores,start,end);
				maxcScore[start][end] = maxcScoreStartEnd;
			}
		}
	}

	@Override
	public Tree<String> extractBestMaxRuleParse(int start, int end,
			List<String> sentence) {
		return extractBestMaxRuleParse1(start, end, 0, 0, sentence);
	}

	/**
	 * Returns the best parse for state "state", potentially starting with a
	 * unary rule
	 */
	public Tree<String> extractBestMaxRuleParse1(int start, int end, int state,
			int substate, List<String> sentence) {
		// System.out.println(start+", "+end+";");
		int cState = maxcChild[start][end][state][substate];
		int cSubState = maxcChildSub[start][end][state][substate];
		if (cState == -1) {
			return extractBestMaxRuleParse2(start, end, state, substate,
					sentence);
		} else {
			List<Tree<String>> child = new ArrayList<Tree<String>>();
			child.add(extractBestMaxRuleParse2(start, end, cState, cSubState,
					sentence));
			String stateStr = (String) tagNumberer.object(state);
			if (stateStr.endsWith("^g"))
				stateStr = stateStr.substring(0, stateStr.length() - 2);

			totalUsedUnaries++;
			// System.out.println("Adding a unary spanning from "+start+" to "+end+". P: "+stateStr+" C: "+child.get(0).getLabel());
			int intermediateNode = grammar.getUnaryIntermediate((short) state,
					(short) cState);
			// if (intermediateNode==0){
			// System.out.println("Added a bad unary from "+start+" to "+end+". P: "+stateStr+" C: "+child.get(0).getLabel());
			// }
			if (intermediateNode > 0) {
				List<Tree<String>> restoredChild = new ArrayList<Tree<String>>();
				nTimesRestoredUnaries++;
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
			int substate, List<String> sentence) {
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
						.object(maxcChild[start][end][state][substate]);// +""+start+""+end;
				children.add(new Tree<String>(stateStr2, childs));
			} else
				children.add(new Tree<String>(sentence.get(start)));
		} else {
			int split = maxcSplit[start][end][state][substate];
			if (split == -1) {
				System.err
						.println("Warning: no symbol can generate the span from "
								+ start + " to " + end + ".");
				System.err.println("The score is "
						+ maxcScore[start][end][state]
						+ " and the state is supposed to be " + stateStr);
				System.err.println("The insideScores are "
						+ Arrays.toString(iScore[start][end][state])
						+ " and the outsideScores are "
						+ Arrays.toString(oScore[start][end][state]));
				System.err.println("The maxcScore is "
						+ maxcScore[start][end][state]);
				// return extractBestMaxRuleParse2(start, end,
				// maxcChild[start][end][state], sentence);
				return new Tree<String>("ROOT");
			}
			int lState = maxcLeftChild[start][end][state][substate];
			int lSubState = maxcLeftChildSub[start][end][state][substate];
			int rState = maxcRightChild[start][end][state][substate];
			int rSubState = maxcRightChildSub[start][end][state][substate];
			Tree<String> leftChildTree = extractBestMaxRuleParse1(start, split,
					lState, lSubState, sentence);
			Tree<String> rightChildTree = extractBestMaxRuleParse1(split, end,
					rState, rSubState, sentence);
			children.add(leftChildTree);
			children.add(rightChildTree);
		}
		return new Tree<String>(stateStr, children);
	}

}
