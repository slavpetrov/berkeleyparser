/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.ScalingTools;

/**
 * @author petrov
 * 
 */
public class CoarseToFineNBestParser extends CoarseToFineMaxRuleParser {
	LazyList[][][] chartBeforeU;
	LazyList[][][] chartAfterU;
	int k;
	List<Double> maxRuleScores;
	int tmp_k;

	/**
	 * @param gr
	 * @param lex
	 * @param unaryPenalty
	 * @param endL
	 * @param viterbi
	 * @param sub
	 * @param score
	 * @param accurate
	 * @param variational
	 * @param useGoldPOS
	 */
	public CoarseToFineNBestParser(Grammar gr, Lexicon lex, int k,
			double unaryPenalty, int endL, boolean viterbi, boolean sub,
			boolean score, boolean accurate, boolean variational,
			boolean useGoldPOS, boolean initCascade) {
		super(gr, lex, unaryPenalty, endL, viterbi, sub, score, accurate,
				variational, useGoldPOS, initCascade);
		this.k = k;
	}

	/**
	 * Assumes that inside and outside scores (sum version, not viterbi) have
	 * been computed. In particular, the narrowRExtent and other arrays need not
	 * be updated.
	 */
	void doConstrainedMaxCScores(List<String> sentence, Grammar grammar,
			Lexicon lexicon, final boolean scale) {
		numSubStatesArray = grammar.numSubStates;
		double initVal = Double.NEGATIVE_INFINITY;
		chartBeforeU = new LazyList[length][length + 1][numStates];
		chartAfterU = new LazyList[length][length + 1][numStates];

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
						chartBeforeU[start][end][pState] = new LazyList(
								grammar.isGrammarTag);
						BinaryRule[] parentRules = grammar
								.splitRulesWithP(pState);
						int nParentStates = numSubStatesArray[pState]; // ==
																		// scores[0][0].length;
						double bestScore = Double.NEGATIVE_INFINITY;
						HyperEdge bestElement = null;

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

								HyperEdge bestLeft = chartAfterU[start][split][lState]
										.getKbest(0);
								double leftChildScore = (bestLeft == null) ? Double.NEGATIVE_INFINITY
										: bestLeft.score;

								HyperEdge bestRight = chartAfterU[split][end][rState]
										.getKbest(0);
								double rightChildScore = (bestRight == null) ? Double.NEGATIVE_INFINITY
										: bestRight.score;

								// double leftChildScore =
								// maxcScore[start][split][lState];
								// double rightChildScore =
								// maxcScore[split][end][rState];
								if (leftChildScore == initVal
										|| rightChildScore == initVal)
									continue;

								double scalingFactor = 0.0;
								if (scale)
									scalingFactor = Math
											.log(ScalingTools
													.calcScaleFactor(oScale[start][end][pState]
															+ iScale[start][split][lState]
															+ iScale[split][end][rState]
															- iScale[0][length][0]));
								double gScore = leftChildScore + scalingFactor
										+ rightChildScore;

								if (gScore == Double.NEGATIVE_INFINITY)
									continue; // no chance of finding a better
												// derivation

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
										// if (rIS < thresh2) continue;
										// if
										// (!allowedSubStates[split][end][rState][rp])
										// continue;
										for (int np = 0; np < nParentStates; np++) {
											// if
											// (!allowedSubStates[start][end][pState][np])
											// continue;
											double pOS = oScore[start][end][pState][np];
											if (pOS == 0)
												continue;
											// if (pOS < thresh2) continue;

											double ruleS = scores[lp][rp][np];
											if (ruleS == 0)
												continue;
											ruleScore += (pOS * ruleS * lIS * rIS)
													/ logNormalizer;
										}
									}
								}
								if (ruleScore == 0)
									continue;

								ruleScore = Math.log(ruleScore);
								gScore += ruleScore;

								if (gScore > Double.NEGATIVE_INFINITY) {
									HyperEdge newElement = new HyperEdge(
											pState, lState, rState, 0, 0, 0,
											start, split, end, gScore,
											ruleScore);
									if (gScore > bestScore) {
										bestScore = gScore;
										bestElement = newElement;
									}
									if (diff > 2)
										chartBeforeU[start][end][pState]
												.addToFringe(newElement);
								}
							}
						}
						if (diff == 2 && bestElement != null)
							chartBeforeU[start][end][pState]
									.addToFringe(bestElement);
						// chartBeforeU[start][end][pState].expandNextBest();
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
						chartBeforeU[start][end][tag] = new LazyList(
								grammar.isGrammarTag);
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
							lexiconScores += (pOS * ruleS) / logNormalizer; // The
																			// inside
																			// score
																			// of
																			// a
																			// word
																			// is
																			// 0.0f
						}
						double scalingFactor = 0.0;
						if (scale)
							scalingFactor = Math.log(ScalingTools
									.calcScaleFactor(oScale[start][end][tag]
											- iScale[0][length][0]));

						lexiconScores = Math.log(lexiconScores);
						double gScore = lexiconScores + scalingFactor;
						HyperEdge newElement = new HyperEdge(tag, -1, -1, 0, 0,
								0, start, start, end, gScore, lexiconScores);
						chartBeforeU[start][end][tag].addToFringe(newElement);
						// chartBeforeU[start][end][tag].expandNextBest();
					}
				}
				// Try unary rules
				// Replacement for maxcScore[start][end], which is updated in
				// batch
				// double[] maxcScoreStartEnd = new double[numStates];
				// for (int i = 0; i < numStates; i++) {
				// maxcScoreStartEnd[i] = maxcScore[start][end][i];
				// }
				for (int pState = 0; pState < numSubStatesArray.length; pState++) {
					if (!allowedStates[start][end][pState])
						continue;
					chartAfterU[start][end][pState] = new LazyList(
							grammar.isGrammarTag);
					int nParentStates = numSubStatesArray[pState]; // ==
																	// scores[0].length;
					UnaryRule[] unaries = grammar
							.getClosedSumUnaryRulesByParent(pState);
					HyperEdge bestElement = null;
					double bestScore = Double.NEGATIVE_INFINITY;

					for (int r = 0; r < unaries.length; r++) {
						UnaryRule ur = unaries[r];
						int cState = ur.childState;
						if ((pState == cState))
							continue;// && (np == cp))continue;
						if (iScore[start][end][cState] == null)
							continue;

						double childScore = Double.NEGATIVE_INFINITY;
						if (chartBeforeU[start][end][cState] != null) {
							HyperEdge bestChild = chartBeforeU[start][end][cState]
									.getKbest(0);
							childScore = (bestChild == null) ? Double.NEGATIVE_INFINITY
									: bestChild.score;
						}

						// double childScore = maxcScore[start][end][cState];
						if (childScore == initVal)
							continue;

						double scalingFactor = 0.0;
						if (scale)
							scalingFactor = Math.log(ScalingTools
									.calcScaleFactor(oScale[start][end][pState]
											+ iScale[start][end][cState]
											- iScale[0][length][0]));

						double gScore = scalingFactor + childScore;
						// if (gScore < maxcScoreStartEnd[pState]) continue;

						double[][] scores = ur.getScores2();
						int nChildStates = numSubStatesArray[cState]; // ==
																		// scores.length;
						double ruleScore = 0;
						for (int cp = 0; cp < nChildStates; cp++) {
							double cIS = iScore[start][end][cState][cp];
							if (cIS == 0)
								continue;
							// if (cIS < thresh2) continue;
							// if (!allowedSubStates[start][end][cState][cp])
							// continue;

							if (scores[cp] == null)
								continue;
							for (int np = 0; np < nParentStates; np++) {
								// if
								// (!allowedSubStates[start][end][pState][np])
								// continue;
								double pOS = oScore[start][end][pState][np];
								if (pOS < 0)
									continue;
								// if (pOS < thresh2) continue;

								double ruleS = scores[cp][np];
								if (ruleS == 0)
									continue;
								ruleScore += (pOS * ruleS * cIS)
										/ logNormalizer;
							}
						}
						if (ruleScore == 0)
							continue;

						ruleScore = Math.log(ruleScore);
						gScore += ruleScore;

						if (gScore > Double.NEGATIVE_INFINITY) {
							HyperEdge newElement = new HyperEdge(pState,
									cState, 0, 0, start, end, gScore, ruleScore);
							if (gScore > bestScore) {
								bestScore = gScore;
								bestElement = newElement;
							}
							if (diff > 1)
								chartAfterU[start][end][pState]
										.addToFringe(newElement);
						}
					}
					if (diff == 1 && bestElement != null)
						chartAfterU[start][end][pState]
								.addToFringe(bestElement);
					if (chartBeforeU[start][end][pState] != null) {
						HyperEdge bestSelf = chartBeforeU[start][end][pState]
								.getKbest(0);
						if (bestSelf != null) {
							HyperEdge selfRule = new HyperEdge(pState, pState,
									0, 0, start, end, bestSelf.score, 0);
							chartAfterU[start][end][pState]
									.addToFringe(selfRule);
						}
					}

					// chartAfterU[start][end][pState].expandNextBest();
				}
				// maxcScore[start][end] = maxcScoreStartEnd;
			}
		}
	}

	/**
	 * Returns the best parse, the one with maximum expected labelled recall.
	 * Assumes that the maxc* arrays have been filled.
	 */
	public Tree<String> extractBestMaxRuleParse(int start, int end,
			List<String> sentence) {
		return extractBestMaxRuleParse1(start, end, 0, 0, sentence);
		// System.out.println(extractBestMaxRuleParse1(start, end, 0, 0,
		// sentence));
		// System.out.println(extractBestMaxRuleParse1(start, end, 0, 1,
		// sentence));
		// System.out.println(extractBestMaxRuleParse1(start, end, 0, 2,
		// sentence));
		// return extractBestMaxRuleParse1(start, end, 0, 3, sentence);
	}

	public List<Tree<String>> extractKBestMaxRuleParses(int start, int end,
			List<String> sentence, int k) {
		List<Tree<String>> list = new ArrayList<Tree<String>>(k);
		maxRuleScores = new ArrayList<Double>(k);
		tmp_k = 0;
		for (int i = 0; i < k; i++) {
			Tree<String> tmp = extractBestMaxRuleParse1(start, end, 0, i,
					sentence);
			if (tmp != null) {
				maxRuleScores.add(chartAfterU[0][length][0].getKbest(i).score);
			}
			// HyperEdge parentNode = chartAfterU[start][end][0].getKbest(i);
			// if (parentNode!=null) System.out.println(parentNode.score+" ");
			if (tmp != null)
				list.add(tmp);
			else
				break;
		}
		return list;

	}

	public double getModelScore(Tree<String> parsedTree) {
		return maxRuleScores.get(tmp_k++);
	}

	/**
	 * Returns the best parse for state "state", potentially starting with a
	 * unary rule
	 */
	public Tree<String> extractBestMaxRuleParse1(int start, int end, int state,
			int suboptimalities, List<String> sentence) {
		// System.out.println(start+", "+end+";");

		HyperEdge parentNode = chartAfterU[start][end][state]
				.getKbest(suboptimalities);
		if (parentNode == null) {
			System.err.println("Don't have a " + (suboptimalities + 1)
					+ "-best tree.");
			return null;
		}
		int cState = parentNode.childState;
		Tree<String> result = null;

		HyperEdge childNode = chartBeforeU[start][end][cState]
				.getKbest(parentNode.childBest);

		List<Tree<String>> children = new ArrayList<Tree<String>>();
		String stateStr = (String) tagNumberer.object(cState);// +""+start+""+end;
		if (stateStr.endsWith("^g"))
			stateStr = stateStr.substring(0, stateStr.length() - 2);

		boolean posLevel = (end - start == 1);
		if (posLevel) {
			// List<Tree<String>> childs = new ArrayList<Tree<String>>();
			// childs.add(new Tree<String>(sentence.get(start)));
			// String stateStr2 =
			// (String)tagNumberer.object(childNode.parentState);//+""+start+""+end;
			// children.add(new Tree<String>(stateStr2,childs));
			// }
			// else {
			children.add(new Tree<String>(sentence.get(start)));
			// }
		} else {
			int split = childNode.split;
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
			int lState = childNode.lChildState;
			int rState = childNode.rChildState;
			Tree<String> leftChildTree = extractBestMaxRuleParse1(start, split,
					lState, childNode.lChildBest, sentence);
			Tree<String> rightChildTree = extractBestMaxRuleParse1(split, end,
					rState, childNode.rChildBest, sentence);
			children.add(leftChildTree);
			children.add(rightChildTree);
		}

		boolean scale = false;
		updateConstrainedMaxCScores(sentence, scale, childNode);

		result = new Tree<String>(stateStr, children);
		if (cState != state) { // unaryRule
			stateStr = (String) tagNumberer.object(state);// +""+start+""+end;
			if (stateStr.endsWith("^g"))
				stateStr = stateStr.substring(0, stateStr.length() - 2);

			int intermediateNode = grammar.getUnaryIntermediate((short) state,
					(short) cState);
			if (intermediateNode > 0) {
				List<Tree<String>> restoredChild = new ArrayList<Tree<String>>();
				String stateStr2 = (String) tagNumberer
						.object(intermediateNode);
				if (stateStr2.endsWith("^g"))
					stateStr2 = stateStr2.substring(0, stateStr2.length() - 2);
				restoredChild.add(result);
				result = new Tree<String>(stateStr2, restoredChild);
			}
			List<Tree<String>> childs = new ArrayList<Tree<String>>();
			childs.add(result);
			result = new Tree<String>(stateStr, childs);
		}
		updateConstrainedMaxCScores(sentence, scale, parentNode);

		return result;
	}

	void updateConstrainedMaxCScores(List<String> sentence,
			final boolean scale, HyperEdge parent) {

		int start = parent.start;
		int end = parent.end;
		int pState = parent.parentState;
		int suboptimalities = parent.parentBest + 1;
		double ruleScore = parent.ruleScore;

		if (parent.alreadyExpanded)
			return;

		if (!parent.isUnary) {
			// if (chartBeforeU[start][end][pState].sortedListSize() >=
			// suboptimalities) return; // already have enough derivations

			int lState = parent.lChildState;
			int rState = parent.rChildState;
			int split = parent.split;

			HyperEdge newParentL = null, newParentR = null;
			if (split - start > 1) { // left is not a POS
				int lBest = parent.lChildBest + 1;
				HyperEdge lChild = chartAfterU[start][split][lState]
						.getKbest(lBest);
				if (lChild != null) {
					int rBest = parent.rChildBest;
					HyperEdge rChild = chartAfterU[split][end][rState]
							.getKbest(rBest);
					double newScore = lChild.score + rChild.score + ruleScore;
					newParentL = new HyperEdge(pState, lState, rState,
							suboptimalities, lBest, rBest, start, split, end,
							newScore, ruleScore);
					// chartBeforeU[start][end][pState].addToFringe(newParentL);
				}
			}
			if (end - split > 1) {
				int rBest = parent.rChildBest + 1;
				HyperEdge rChild = chartAfterU[split][end][rState]
						.getKbest(rBest);
				if (rChild != null) {
					int lBest = parent.lChildBest;
					HyperEdge lChild = chartAfterU[start][split][lState]
							.getKbest(lBest);
					double newScore = lChild.score + rChild.score + ruleScore;
					newParentR = new HyperEdge(pState, lState, rState,
							suboptimalities, lBest, rBest, start, split, end,
							newScore, ruleScore);
					// chartBeforeU[start][end][pState].addToFringe(newParentR);
				}
			}

			if (newParentL != null && newParentR != null
					&& newParentL.score > newParentR.score)
				chartBeforeU[start][end][pState].addToFringe(newParentL);
			else if (newParentL != null && newParentR != null)
				chartBeforeU[start][end][pState].addToFringe(newParentR);
			else if (newParentL != null || newParentR != null) {
				if (newParentL != null)
					chartBeforeU[start][end][pState].addToFringe(newParentL);
				else
					/* newParentR!=null */chartBeforeU[start][end][pState]
							.addToFringe(newParentR);
			}
			parent.alreadyExpanded = true;

			// chartBeforeU[start][end][pState].expandNextBest();
		} else { // unary
		// if (chartAfterU[start][end][pState].sortedListSize() >=
		// suboptimalities) return; // already have enough derivations

			int cState = parent.childState;
			int cBest = parent.childBest + 1;

			if (end - start > 1) {
				HyperEdge child = chartBeforeU[start][end][cState]
						.getKbest(cBest);
				if (child != null) {
					double newScore = child.score + ruleScore;
					HyperEdge newParent = new HyperEdge(pState, cState,
							suboptimalities, cBest, start, end, newScore,
							ruleScore);
					// if (newScore>=parent.score)
					// System.out.println("ullala");
					chartAfterU[start][end][pState].addToFringe(newParent);
				}
				parent.alreadyExpanded = true;
				// chartAfterU[start][end][pState].expandNextBest();
			}
		}
	}

	public List<Tree<String>> getKBestConstrainedParses(List<String> sentence,
			List<String> posTags, int k) {
		if (sentence.size() == 0) {
			ArrayList<Tree<String>> result = new ArrayList<Tree<String>>();
			result.add(new Tree<String>("ROOT"));
			return result;
		}
		doPreParses(sentence, null, false, posTags);
		List<Tree<String>> bestTrees = null;
		double score = 0;
		// bestTree = extractBestViterbiParse(0, 0, 0, length, sentence);
		// score = viScore[0][length][0];
		if (true) {// score != Double.NEGATIVE_INFINITY) {
			// score = Math.log(score) + (100*iScale[0][length][0]);
			// System.out.println("\nFound a parse for sentence with length "+length+". The LL is "+score+".");

			// voScore[0][length][0] = 0.0;
			// doConstrainedViterbiOutsideScores(baseGrammar);

			// pruneChart(pruningThreshold, baseGrammar.numSubStates,
			// grammar.numSubStates, true);
			Grammar curGrammar = grammarCascade[endLevel - startLevel + 1];
			Lexicon curLexicon = lexiconCascade[endLevel - startLevel + 1];
			// numSubStatesArray = grammar.numSubStates;
			// clearArrays();
			double initVal = (viterbiParse) ? Double.NEGATIVE_INFINITY : 0;
			int level = isBaseline ? 1 : endLevel;
			createArrays(false, curGrammar.numStates, curGrammar.numSubStates,
					level, initVal, false);
			initializeChart(sentence, curLexicon, false, false, posTags, false);
			doConstrainedInsideScores(curGrammar, viterbiParse, viterbiParse);

			score = iScore[0][length][0][0];
			if (!viterbiParse)
				score = Math.log(score);// + (100*iScale[0][length][0]);
			logLikelihood = score;
			if (score != Double.NEGATIVE_INFINITY) {
				// System.out.println("\nFinally found a parse for sentence with length "+length+". The LL is "+score+".");

				if (!viterbiParse) {
					oScore[0][length][0][0] = 1.0;
					doConstrainedOutsideScores(curGrammar, viterbiParse, false);
					doConstrainedMaxCScores(sentence, curGrammar, curLexicon,
							false);

				}

				// Tree<String> withoutRoot = extractBestMaxRuleParse(0, length,
				// sentence);
				// add the root
				// ArrayList<Tree<String>> rootChild = new
				// ArrayList<Tree<String>>();
				// rootChild.add(withoutRoot);
				// bestTree = new Tree<String>("ROOT",rootChild);

				// System.out.print(bestTree);
			} else {
				// System.out.println("Using scaling code for sentence with length "+length+".");
				setupScaling();
				initializeChart(sentence, curLexicon, false, false, posTags,
						true);
				doScaledConstrainedInsideScores(curGrammar);
				score = iScore[0][length][0][0];
				if (!viterbiParse)
					score = Math.log(score) + (100 * iScale[0][length][0]);
				// System.out.println("Finally found a parse for sentence with length "+length+". The LL is "+score+".");
				// System.out.println("Scale: "+iScale[0][length][0]);
				oScore[0][length][0][0] = 1.0;
				oScale[0][length][0] = 0;
				doScaledConstrainedOutsideScores(curGrammar);
				doConstrainedMaxCScores(sentence, curGrammar, curLexicon, true);
			}

			grammar = curGrammar;
			lexicon = curLexicon;
			bestTrees = extractKBestMaxRuleParses(0, length, sentence, k);
		}
		return bestTrees;
	}

	public CoarseToFineNBestParser newInstance() {
		CoarseToFineNBestParser newParser = new CoarseToFineNBestParser(
				grammar, lexicon, k, unaryPenalty, endLevel, viterbiParse,
				outputSub, outputScore, accurate, this.doVariational,
				useGoldPOS, false);
		newParser.initCascade(this);
		return newParser;
	}

	public synchronized Object call() {
		List<Tree<String>> result = getKBestConstrainedParses(nextSentence,
				null, k);
		nextSentence = null;
		synchronized (queue) {
			queue.add(result, -nextSentenceID);
			queue.notifyAll();
		}
		return null;
	}

}
