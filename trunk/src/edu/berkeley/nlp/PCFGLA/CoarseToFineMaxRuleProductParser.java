package edu.berkeley.nlp.PCFGLA;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.Numberer;
import edu.berkeley.nlp.util.ScalingTools;

/**
 * 
 * @author Slav Petrov
 * 
 *         SHOULD BE CLEANED UP!!! AND PROBABLY ALSO RENAMED SINCE IT CAN
 *         COMPUTE VITERBI PARSES AS WELL
 * 
 *         An extension of ConstrainedArrayParser that computes the scores
 *         P(w_{i:j}|A), whose computation involves a sum, rather than the
 *         Viterbi scores, which involve a max. This is used by the Labeled
 *         Recall parser (maximizes the expected number of correct symbols) and
 *         the Max-Rule parser (maximizes the expected number of correct rules,
 *         ie all 3 symbols correct).
 * 
 */

public class CoarseToFineMaxRuleProductParser extends CoarseToFineMaxRuleParser {
	boolean[][][][] allowedSubStates;
	boolean[][][] allowedStates;
	boolean[][] vAllowedStates;
	double[][] spanMass;
	// allowedStates[start][end][state][0] -> is this category allowed
	// allowedStates[start][end][state][i+1] -> is subcategory i allowed
	Grammar[][] grammarCascade;
	Lexicon[][] lexiconCascade;
	int[][][][] lChildMap;
	int[][][][] rChildMap;
	int startLevel;
	int endLevel;
	// protected double[][][][] iScore;
	/** outside scores; start idx, end idx, state -> logProb */
	// protected double[][][][] oScore;
	double[] maxThresholds;
	double logLikelihood;
	Tree<String> bestTree;
	boolean isBaseline;
	protected final boolean doVariational;

	// inside scores
	protected double[][][] viScore; // start idx, end idx, state -> logProb
	protected double[][][] voScore; // start idx, end idx, state -> logProb

	// maxcScore does not have substate information since these are marginalized
	// out
	protected double savedScore;
	protected double[][][] maxcScore; // start, end, state --> logProb
	protected double[][][] maxsScore; // start, end, state --> logProb
	protected int[][][] maxcSplit; // start, end, state -> split position
	protected int[][][] maxcChild; // start, end, state -> unary child (if any)
	protected int[][][] maxcLeftChild; // start, end, state -> left child
	protected int[][][] maxcRightChild; // start, end, state -> right child
	protected double unaryPenalty;
	int nLevels;
	final boolean[] grammarTags;
	final boolean viterbiParse;
	final boolean outputSub;
	final boolean outputScore;
	Numberer wordNumberer = Numberer.getGlobalNumberer("words");
	final boolean accurate;
	final boolean useGoldPOS;
	double[] unscaledScoresToAdd;
	ArrayParser llParser;
	List<Posterior> posteriorsToDump;

	int nGrammars;
	// protected short[] numSubStatesArray;
	protected short[][] numSubStates;
	List<double[][][][]> all_iScores;
	List<double[][][][]> all_oScores;
	List<int[][][]> all_iScales;
	List<int[][][]> all_oScales;
	Grammar[] grammars;
	Lexicon[] lexicons;

	public CoarseToFineMaxRuleProductParser(Grammar[] gr, Lexicon[] lex,
			double unaryPenalty, int endL, boolean viterbi, boolean sub,
			boolean score, boolean accurate, boolean variational,
			boolean useGoldPOS, boolean initializeCascade) {
		// grammar=gr;
		// lexicon=lex;
		// this.numSubStatesArray = gr.numSubStates.clone();
		// System.out.println("The unary penalty for parsing is "+unaryPenalty+".");
		this.nGrammars = gr.length;
		this.unaryPenalty = unaryPenalty;
		this.accurate = accurate;
		this.viterbiParse = viterbi;
		this.outputScore = score;
		this.outputSub = sub;
		this.doVariational = variational;
		this.useGoldPOS = useGoldPOS;

		totalUsedUnaries = 0;
		nTimesRestoredUnaries = 0;
		nRules = 0;
		nRulesInf = 0;
		this.tagNumberer = Numberer.getGlobalNumberer("tags");
		this.numStates = gr[0].numStates;
		this.maxNSubStates = maxSubStates(gr);
		this.idxC = new int[maxNSubStates];
		this.scoresToAdd = new double[maxNSubStates];
		this.unscaledScoresToAdd = new double[maxNSubStates];
		this.grammarTags = new boolean[numStates];
		for (int i = 0; i < numStates; i++) {
			grammarTags[i] = gr[0].isGrammarTag(i);
		}
		grammarTags[0] = true;

		nLevels = (int) Math.ceil(Math.log(ArrayUtil.max(gr[0].numSubStates))
				/ Math.log(2));
		this.grammarCascade = new Grammar[nGrammars][nLevels + 3];
		this.lexiconCascade = new Lexicon[nGrammars][nLevels + 3];
		this.maxThresholds = new double[nLevels + 3];
		this.lChildMap = new int[nGrammars][nLevels][][];
		this.rChildMap = new int[nGrammars][nLevels][][];
		this.startLevel = -1;
		this.endLevel = endL;
		if (endLevel == -1)
			this.endLevel = nLevels;
		this.isBaseline = (endLevel == 0);

		if (initializeCascade) {
			grammars = new Grammar[nGrammars];
			lexicons = new Lexicon[nGrammars];
			numSubStates = new short[nGrammars][];
			for (int nGr = 0; nGr < nGrammars; nGr++) {
				grammars[nGr] = gr[nGr];
				lexicons[nGr] = lex[nGr];
				numSubStates[nGr] = gr[nGr].numSubStates;
				// System.out.println(numSubStates[nGr][1]);
				if (grammars[nGr].numStates != numStates) {
					System.out.println("Grammars are not compatible!");
					System.out.println("numStates don't match");
					System.exit(-1);
				}
				initCascade(gr[nGr], lex[nGr], nGr);
			}
		}
	}

	// belongs in the grammar but i didnt want to change the signature for
	// now...
	public int maxSubStates(Grammar[] grammars) {
		int max = 0;
		for (int g = 0; g < grammars.length; g++) {
			for (int i = 0; i < numStates; i++) {
				if (grammars[g].numSubStates[i] > max)
					max = grammars[g].numSubStates[i];
			}
		}
		return max;
	}

	public void initCascade(CoarseToFineMaxRuleProductParser otherParser) {
		lChildMap = otherParser.lChildMap;
		rChildMap = otherParser.rChildMap;
		grammarCascade = otherParser.grammarCascade;
		lexiconCascade = otherParser.lexiconCascade;
		binarization = otherParser.binarization;
	}

	public void initCascade(Grammar gr, Lexicon lex, int nGr) {
		// the cascades will contain all the projections (in logarithm mode) and
		// at the end the final grammar,
		// once in logarithm-mode and once not
		for (int level = startLevel; level <= endLevel + 1; level++) {
			if (level == -1)
				continue; // don't do the pre-pre parse
			Grammar tmpGrammar = null;
			Lexicon tmpLexicon = null;
			if (level == endLevel) {
				tmpGrammar = gr.copyGrammar(false);
				tmpLexicon = lex.copyLexicon();
			} else if (level > endLevel) {
				tmpGrammar = gr;
				tmpLexicon = lex;
			} else /* if (level>0&& level<endLevel) */{
				int[][] fromMapping = gr.computeMapping(1);
				int[][] toSubstateMapping = gr.computeSubstateMapping(level);
				int[][] toMapping = gr.computeToMapping(level,
						toSubstateMapping);
				int[][] curLChildMap = new int[toSubstateMapping.length][];
				int[][] curRChildMap = new int[toSubstateMapping.length][];
				double[] condProbs = gr.computeConditionalProbabilities(
						fromMapping, toMapping);

				if (level == -1)
					tmpGrammar = gr.projectTo0LevelGrammar(condProbs,
							fromMapping, toMapping);
				else
					tmpGrammar = gr.projectGrammar(condProbs, fromMapping,
							toSubstateMapping);
				tmpLexicon = lex.projectLexicon(condProbs, fromMapping,
						toSubstateMapping);

				if (level > 0) {
					lChildMap[nGr][level + startLevel] = curLChildMap;
					rChildMap[nGr][level + startLevel] = curRChildMap;
					gr.computeReverseSubstateMapping(level, curLChildMap,
							curRChildMap);
				}
			}

			tmpGrammar.splitRules();
			double filter = 1.0e-4;
			double exponent = 0.9;
			if (level >= 0 && level < endLevel) { // switch all to 1.0
				tmpGrammar.removeUnlikelyRules(filter, exponent);
				tmpLexicon.removeUnlikelyTags(filter, exponent);
			} else if (level >= endLevel) {
				tmpGrammar.removeUnlikelyRules(1.0e-10, 1.0);
				tmpLexicon.removeUnlikelyTags(1.0e-10, 1.0);
			}
			// System.out.println(baseGrammar.toString());

			// DumpGrammar.dumpGrammar("wsj_"+level+".gr", tmpGrammar,
			// (SophisticatedLexicon)tmpLexicon);

			if (level <= endLevel || viterbiParse) {
				tmpGrammar.logarithmMode();
				tmpLexicon.logarithmMode();
			}
			grammarCascade[nGr][level - startLevel] = tmpGrammar;
			lexiconCascade[nGr][level - startLevel] = tmpLexicon;

		}
	}

	void doConstrainedInsideScores(Grammar grammar, boolean viterbi,
			boolean logScores) {
		if (!viterbi && logScores)
			throw new Error(
					"This would require logAdds and is slow. Exponentiate the scores instead.");
		short[] numSubStatesArray = grammar.numSubStates;
		double initVal = (logScores) ? Double.NEGATIVE_INFINITY : 0;

		for (int diff = 1; diff <= length; diff++) {
			for (int start = 0; start < (length - diff + 1); start++) {
				int end = start + diff;
				for (int pState = 0; pState < numStates; pState++) {
					if (diff == 1)
						continue; // there are no binary rules that span over 1
									// symbol only
					if (!allowedStates[start][end][pState])
						continue;
					BinaryRule[] parentRules = grammar.splitRulesWithP(pState);
					final int nParentStates = numSubStatesArray[pState];
					Arrays.fill(scoresToAdd, initVal);
					boolean somethingChanged = false;
					final int numRules = parentRules.length;
					for (int i = 0; i < numRules; i++) {
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
						final int max = (max1 < max2 ? max1 : max2); // can this
																		// left
																		// constituent
																		// stretch
																		// far
																		// enough
																		// to
																		// reach
																		// the
																		// right
																		// constituent?
						if (min > max) {
							continue;
						}
						// TODO switch order of loops for efficiency
						double[][][] scores = r.getScores2();
						final int nLeftChildStates = numSubStatesArray[lState];
						final int nRightChildStates = numSubStatesArray[rState];
						for (int split = min; split <= max; split++) {
							if (!allowedStates[start][split][lState])
								continue;
							if (!allowedStates[split][end][rState])
								continue;
							for (int lp = 0; lp < nLeftChildStates; lp++) {
								// if (iScore[start][split][lState] == null)
								// continue;
								// if
								// (!allowedSubStates[start][split][lState][lp])
								// continue;
								double lS = iScore[start][split][lState][lp];
								if (lS == initVal)
									continue;

								for (int rp = 0; rp < nRightChildStates; rp++) {
									if (scores[lp][rp] == null)
										continue;
									double rS = iScore[split][end][rState][rp];
									if (rS == initVal)
										continue;
									for (int np = 0; np < nParentStates; np++) {
										if (!allowedSubStates[start][end][pState][np])
											continue;
										// if (level==endLevel-1)
										// edgesTouched++;

										double pS = scores[lp][rp][np];
										if (pS == initVal)
											continue;

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
						}
					}
					if (!somethingChanged)
						continue;

					for (int np = 0; np < nParentStates; np++) {
						if (scoresToAdd[np] > initVal) {
							iScore[start][end][pState][np] = scoresToAdd[np];
						}
					}
					if (true) {// firstTime) {
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
				double[][] scoresAfterUnaries = new double[numStates][];
				boolean somethingChanged = false;
				for (int pState = 0; pState < numStates; pState++) {
					if (!allowedStates[start][end][pState])
						continue;
					// Should be: Closure under sum-product:
					UnaryRule[] unaries = null;
					if (viterbi)
						unaries = grammar
								.getClosedViterbiUnaryRulesByParent(pState);
					else
						unaries = grammar
								.getClosedSumUnaryRulesByParent(pState);
					final int nParentStates = numSubStatesArray[pState];// scores[0].length;
					boolean firstTime = true;
					final int numRules = unaries.length;
					for (int r = 0; r < numRules; r++) {
						UnaryRule ur = unaries[r];
						int cState = ur.childState;
						if ((pState == cState))
							continue;// && (np == cp))continue;
						if (iScore[start][end][cState] == null)
							continue;
						double[][] scores = ur.getScores2();
						final int nChildStates = numSubStatesArray[cState];// scores[0].length;
						for (int cp = 0; cp < nChildStates; cp++) {
							if (scores[cp] == null)
								continue;
							for (int np = 0; np < nParentStates; np++) {
								if (!allowedSubStates[start][end][pState][np])
									continue;
								// if
								// (!allowedSubStates[start][end][cState][cp])
								// continue;
								// if (level==endLevel-1) edgesTouched++;

								double pS = scores[cp][np];
								if (pS == initVal)
									continue;

								double iS = iScore[start][end][cState][cp];
								if (iS == initVal)
									continue;

								if (firstTime) {
									firstTime = false;
									scoresAfterUnaries[pState] = new double[nParentStates];
									Arrays.fill(scoresAfterUnaries[pState],
											initVal);

								}
								double thisRound = (logScores) ? iS + pS : iS
										* pS;

								if (viterbi)
									scoresAfterUnaries[pState][np] = Math.max(
											thisRound,
											scoresAfterUnaries[pState][np]);
								else
									scoresAfterUnaries[pState][np] += thisRound;
								somethingChanged = true;
							}
						}
					}
				}
				if (!somethingChanged)
					continue;
				for (int pState = 0; pState < numStates; pState++) {
					final int nParentStates = numSubStatesArray[pState];
					double[] thisCell = scoresAfterUnaries[pState];
					if (thisCell == null)
						continue;
					for (int np = 0; np < nParentStates; np++) {
						if (thisCell[np] > initVal) {
							if (viterbi)
								iScore[start][end][pState][np] = Math.max(
										iScore[start][end][pState][np],
										thisCell[np]);
							else
								iScore[start][end][pState][np] = iScore[start][end][pState][np]
										+ thisCell[np];
						}
					}
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

	/**
	 * Fills in the oScore array of each category over each span of length 2 or
	 * more. This version computes the posterior outside scores, not the Viterbi
	 * outside scores.
	 */

	void doConstrainedOutsideScores(Grammar grammar, boolean viterbi,
			boolean logScores) {
		short[] numSubStatesArray = grammar.numSubStates;
		double initVal = (logScores) ? Double.NEGATIVE_INFINITY : 0.0;
		for (int diff = length; diff >= 1; diff--) {
			for (int start = 0; start + diff <= length; start++) {
				int end = start + diff;
				// do unaries
				double[][] scoresAfterUnaries = new double[numStates][];
				boolean somethingChanged = false;
				for (int cState = 0; cState < numStates; cState++) {
					if (diff > 1 && !grammar.isGrammarTag[cState])
						continue;
					if (!allowedStates[start][end][cState]) {
						continue;
					}
					UnaryRule[] rules = null;
					if (viterbi)
						rules = grammar
								.getClosedViterbiUnaryRulesByChild(cState);
					else
						rules = grammar.getClosedSumUnaryRulesByChild(cState);
					final int nChildStates = numSubStatesArray[cState];
					final int numRules = rules.length;
					for (int r = 0; r < numRules; r++) {
						UnaryRule ur = rules[r];
						int pState = ur.parentState;
						if ((pState == cState))
							continue;// && (np == cp))continue;
						if (!allowedStates[start][end][pState]) {
							continue;
						}

						double[][] scores = ur.getScores2();
						final int nParentStates = numSubStatesArray[pState];
						for (int cp = 0; cp < nChildStates; cp++) {
							if (scores[cp] == null)
								continue;
							if (!allowedSubStates[start][end][cState][cp])
								continue;
							for (int np = 0; np < nParentStates; np++) {
								double pS = scores[cp][np];
								if (pS == initVal)
									continue;

								double oS = oScore[start][end][pState][np];
								if (oS == initVal)
									continue;

								double thisRound = (logScores) ? oS + pS : oS
										* pS;

								if (scoresAfterUnaries[cState] == null) {
									scoresAfterUnaries[cState] = new double[numSubStatesArray[cState]];
									if (viterbi)
										Arrays.fill(scoresAfterUnaries[cState],
												initVal);
								}

								if (viterbi)
									scoresAfterUnaries[cState][cp] = Math.max(
											thisRound,
											scoresAfterUnaries[cState][cp]);
								else
									scoresAfterUnaries[cState][cp] += thisRound;
								somethingChanged = true;
							}
						}
					}
				}
				if (somethingChanged) {
					for (int cState = 0; cState < numStates; cState++) {
						double[] thisCell = scoresAfterUnaries[cState];
						if (thisCell == null)
							continue;
						for (int cp = 0; cp < numSubStatesArray[cState]; cp++) {
							if (thisCell[cp] > initVal) {
								if (viterbi)
									oScore[start][end][cState][cp] = Math.max(
											oScore[start][end][cState][cp],
											thisCell[cp]);
								else
									oScore[start][end][cState][cp] += thisCell[cp];
							}
						}
					}
				}

				// do binaries

				for (int pState = 0; pState < numSubStatesArray.length; pState++) {
					if (!allowedStates[start][end][pState]) {
						continue;
					}
					final int nParentChildStates = numSubStatesArray[pState];
					// if (!allowedStates[start][end][pState]) continue;
					BinaryRule[] rules = grammar.splitRulesWithP(pState);
					final int numRules = rules.length;
					for (int r = 0; r < numRules; r++) {
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
						final int nLeftChildStates = numSubStatesArray[lState];
						final int nRightChildStates = numSubStatesArray[rState];
						for (int split = min; split <= max; split++) {
							if (!allowedStates[start][split][lState])
								continue;
							if (!allowedStates[split][end][rState])
								continue;
							// if (!allowedStates[start][split][lState])
							// continue;
							// if (!allowedStates[split][end][rState]) continue;
							double[] rightScores = new double[nRightChildStates];
							if (viterbi)
								Arrays.fill(rightScores, initVal);
							Arrays.fill(scoresToAdd, initVal);
							somethingChanged = false;
							for (int lp = 0; lp < nLeftChildStates; lp++) {
								double lS = iScore[start][split][lState][lp];
								if (lS == initVal) {
									continue;
								}
								// if
								// (!allowedSubStates[start][split][lState][lp])
								// continue;
								for (int rp = 0; rp < nRightChildStates; rp++) {
									if (scores[lp][rp] == null)
										continue;
									double rS = iScore[split][end][rState][rp];
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

										double oS = oScore[start][end][pState][np];
										if (oS == initVal)
											continue;

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
							for (int cp = 0; cp < nLeftChildStates; cp++) {
								if (scoresToAdd[cp] > initVal) {
									if (viterbi)
										oScore[start][split][lState][cp] = Math
												.max(oScore[start][split][lState][cp],
														scoresToAdd[cp]);
									else
										oScore[start][split][lState][cp] += scoresToAdd[cp];
								}
							}

							for (int cp = 0; cp < nRightChildStates; cp++) {
								if (rightScores[cp] > initVal) {
									if (viterbi)
										oScore[split][end][rState][cp] = Math
												.max(oScore[split][end][rState][cp],
														rightScores[cp]);
									else
										oScore[split][end][rState][cp] += rightScores[cp];
								}
							}
						}
					}
				}
			}
		}
	}

	void initializeChart(List<String> sentence, Lexicon lexicon,
			boolean noSubstates, boolean noSmoothing, List<String> posTags,
			boolean scale) {
		int start = 0;
		int end = start + 1;
		for (String word : sentence) {
			end = start + 1;
			int goldTag = -1;
			if (useGoldPOS && posTags != null) {
				goldTag = tagNumberer.number(posTags.get(start));
			}
			for (int tag = 0; tag < numStates; tag++) {
				if (!allowedStates[start][end][tag])
					continue;
				if (grammarTags[tag])
					continue;
				if (useGoldPOS && posTags != null && tag != goldTag)
					continue;
				// System.out.println("Initializing");
				// if (dummy) allowedStates[start][end][tag] = true;
				narrowRExtent[start][tag] = end;
				narrowLExtent[end][tag] = start;
				wideRExtent[start][tag] = end;
				wideLExtent[end][tag] = start;
				double[] lexiconScores = lexicon.score(word, (short) tag,
						start, noSmoothing, false);
				if (scale)
					iScale[start][end][tag] = 0;
				for (short n = 0; n < lexiconScores.length; n++) {
					if (!noSubstates && !allowedSubStates[start][end][tag][n])
						continue;
					double prob = lexiconScores[n];
					if (noSubstates)
						viScore[start][end][tag] = prob;
					else
						iScore[start][end][tag][n] = prob;
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
			boolean justInit, int nGr) {
		// spanMass = new double[length][length+1];
		if (firstTime) {
			// clearArrays();

			// allocate just the parts of iScore and oScore used (end > start,
			// etc.)
			// System.out.println("initializing iScore arrays with length " +
			// length + " and numStates " + numStates);
			// if (logProbs){
			viScore = new double[length][length + 1][];
			voScore = new double[length][length + 1][];

			// } else{
			iScore = new double[length][length + 1][][];
			oScore = new double[length][length + 1][][];
			// iScale = new int[length][length + 1][];
			// oScale = new int[length][length + 1][];
			// }
			allowedSubStates = new boolean[length][length + 1][][];

			if (nGr == 0) {
				allowedStates = new boolean[length][length + 1][];
				vAllowedStates = new boolean[length][length + 1];
			}
		}

		for (int start = 0; start < length; start++) {
			for (int end = start + 1; end <= length; end++) {
				if (firstTime) {
					viScore[start][end] = new double[numStates];
					voScore[start][end] = new double[numStates];
					iScore[start][end] = new double[numStates][];
					oScore[start][end] = new double[numStates][];
					// iScale[start][end] = new int[numStates];
					// oScale[start][end] = new int[numStates];
					allowedSubStates[start][end] = new boolean[numStates][];
					if (nGr == 0) {
						allowedStates[start][end] = grammarTags.clone();
						if (end - start == 1) { // POS-level
							Arrays.fill(allowedStates[start][end], true);
						}
						vAllowedStates[start][end] = true;
					}
				}
				for (int state = 0; state < numSubStatesArray.length; state++) {
					if (firstTime || (allowedStates[start][end][state])) {
						if (level < 1) {
							viScore[start][end][state] = Double.NEGATIVE_INFINITY;
							voScore[start][end][state] = Double.NEGATIVE_INFINITY;
						} else {
							iScore[start][end][state] = new double[numSubStatesArray[state]];
							oScore[start][end][state] = new double[numSubStatesArray[state]];
							Arrays.fill(iScore[start][end][state], initVal);
							Arrays.fill(oScore[start][end][state], initVal);
							// Arrays.fill(iScale[start][end],
							// Integer.MIN_VALUE);
							// Arrays.fill(oScale[start][end],
							// Integer.MIN_VALUE);

							boolean[] newAllowedSubStates = new boolean[numSubStatesArray[state]];
							if (allowedSubStates[start][end][state] == null) {
								Arrays.fill(newAllowedSubStates, true);
								allowedSubStates[start][end][state] = newAllowedSubStates;
							} else {
								if (!justInit) {
									int[][] curLChildMap = lChildMap[nGr][level - 2];
									int[][] curRChildMap = rChildMap[nGr][level - 2];
									for (int i = 0; i < allowedSubStates[start][end][state].length; i++) {
										boolean val = allowedSubStates[start][end][state][i];
										newAllowedSubStates[curLChildMap[state][i]] = val;
										newAllowedSubStates[curRChildMap[state][i]] = val;
									}
									allowedSubStates[start][end][state] = newAllowedSubStates;
								}
							}
						}
					} else {
						if (level < 1) {
							viScore[start][end][state] = Double.NEGATIVE_INFINITY;
							voScore[start][end][state] = Double.NEGATIVE_INFINITY;
						} else {
							iScore[start][end][state] = null;
							oScore[start][end][state] = null;
							// allowedSubStates[start][end][state] = new
							// boolean[1];
							// allowedSubStates[start][end][state][0] = false;
						}
					}
				}
				if (level > 0 && start == 0 && end == length) {
					if (iScore[start][end][0] == null)
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
		iScale = null;
		oScale = null;
		if (level > 0) {
			for (int start = 0; start < length; start++) {
				for (int end = start + 1; end <= length; end++) {
					for (int state = 1; state < numSubStatesArray.length; state++) {
						if (allowedStates[start][end][state]) {
							if (iScore[start][end][state] == null)
								System.err
										.println("iScore array not initialized correctly");
							if (oScore[start][end][state] == null)
								System.err
										.println("oScore array not initialized correctly");
						}
					}
				}
			}
		}
	}

	protected void clearArrays() {
		iScore = oScore = null;
		viScore = voScore = null;
		allowedSubStates = null;
		vAllowedStates = null;
		// iPossibleByL = iPossibleByR = oFilteredEnd = oFilteredStart =
		// oPossibleByL = oPossibleByR = tags = null;
		narrowRExtent = wideRExtent = narrowLExtent = wideLExtent = null;
	}

	protected void pruneChart(double threshold, short[] numSubStatesArray,
			int level) {
		int totalStates = 0, previouslyPossible = 0, nowPossible = 0;
		// threshold = Double.NEGATIVE_INFINITY;

		double sentenceProb = (level < 1) ? viScore[0][length][0]
				: iScore[0][length][0][0];
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
					if (state == 0) {
						allowedStates[start][end][state] = true;
						// if (level>1){
						// allowedSubStates[start][end][state] = new boolean[1];
						// allowedSubStates[start][end][state][0] = true;
						// }
						continue;
					}

					if (level == 0) {
						if (!vAllowedStates[start][end]) {
							allowedStates[start][end][state] = false;
							totalStates++;
							continue;
						}
					} else if (level > 0) {
						if (!allowedStates[start][end][state]) {
							totalStates += numSubStatesArray[state];
							continue;
						}
					}
					if (level < 1) {
						totalStates++;
						previouslyPossible++;
						double iS = viScore[start][end][state];
						double oS = voScore[start][end][state];
						if (iS == Double.NEGATIVE_INFINITY
								|| oS == Double.NEGATIVE_INFINITY) {
							if (level == 0)
								allowedStates[start][end][state] = false;
							else
								/* level==-1 */vAllowedStates[start][end] = false;
							continue;
						}
						double posterior = iS + oS - sentenceProb;
						if (posterior > threshold) {
							// spanMass[start][end]+=Math.exp(posterior);
							if (level == 0)
								allowedStates[start][end][state] = true;
							else
								vAllowedStates[start][end] = true;
							nowPossible++;
						} else {
							if (level == 0)
								allowedStates[start][end][state] = false;
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
						double iS = iScore[start][end][state][substate];
						double oS = oScore[start][end][state][substate];

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
						allowedStates[start][end][state] = false;
				}
			}
		}

		// System.out.print("[");
		// for(int st=0; st<length; st++){
		// for(int en=0; en<=length; en++){
		// System.out.print(spanMass[st][en]);
		// if (en<length) System.out.print(", ");
		// }
		// if (st<length-1) System.out.print(";\n");
		// }
		// System.out.print("]\n");
		// String parse = "";
		// if (level==-1) parse = "Pre-Parse";
		// else if (level==0) parse = "X-Bar";
		// else parse = ((int)Math.pow(2,level))+"-Substates";
		// System.out.print(parse+". NoPruning: " +totalStates +
		// ". Before: "+previouslyPossible+". After: "+nowPossible+".\n");
	}

	int level;

	public void doPreParses(List<String> sentence, Tree<StateSet> tree,
			boolean noSmoothing, List<String> posTags) {
		boolean keepGoldAlive = (tree != null); // we are given the gold tree ->
												// make sure we don't prune it
												// away
		clearArrays();
		all_iScales = new ArrayList<int[][][]>();
		all_oScales = new ArrayList<int[][][]>();
		all_iScores = new ArrayList<double[][][][]>();
		all_oScores = new ArrayList<double[][][][]>();

		length = (short) sentence.size();
		double score = 0;
		Grammar curGrammar = null;
		Lexicon curLexicon = null;
		double[] accurateThresholds = { -8, -12, -12, -11, -12, -12, -14, -14 };
		// double[] accurateThresholds = {-10,-14,-14,-14,-14,-14,-16,-16};
		double[] fastThresholds = { -8, -9.75, -10, -9.6, -9.66, -8.01, -7.4,
				-10, -10 };
		// double[] accurateThresholds = {-8,-9,-9,-9,-9,-9,-10};
		// double[] fastThresholds = {-2,-8,-9,-8,-8,-7.5,-7,-8};
		double[] pruningThreshold = null;

		if (accurate)
			pruningThreshold = accurateThresholds;
		else
			pruningThreshold = fastThresholds;

		for (int nGr = 0; nGr < nGrammars; nGr++) {
			// int startLevel = -1;
			boolean firstTime = true;
			for (level = startLevel; level <= endLevel; level++) {
				if (level == -1)
					continue; // don't do the pre-pre parse
				if (nGr > 0 && level < 1)
					continue; // second time around we can skip the x-bar pass
				if (!isBaseline && level == endLevel)
					continue;//
				curGrammar = grammarCascade[nGr][level - startLevel];
				curLexicon = lexiconCascade[nGr][level - startLevel];
				// numSubStatesArray = curGrammar.numSubStates;

				createArrays(firstTime, curGrammar.numStates,
						curGrammar.numSubStates, level,
						Double.NEGATIVE_INFINITY, false, nGr);
				firstTime = false;

				initializeChart(sentence, curLexicon, level < 1, noSmoothing,
						posTags, false);
				final boolean viterbi = true, logScores = true;
				if (level < 1) {
					doConstrainedViterbiInsideScores(curGrammar,
							level == startLevel);
					score = viScore[0][length][0];
				} else {
					doConstrainedInsideScores(curGrammar, viterbi, logScores);
					score = iScore[0][length][0][0];
				}

				if (score == Double.NEGATIVE_INFINITY)
					continue;
				// System.out.println("\nFound a parse for sentence with length "+length+". The LL is "+score+".");
				if (level < 1) {
					voScore[0][length][0] = 0.0;
					doConstrainedViterbiOutsideScores(curGrammar,
							level == startLevel);
				} else {
					oScore[0][length][0][0] = 0.0;
					doConstrainedOutsideScores(curGrammar, viterbi, logScores);
				}

				pruneChart(
						/* Double.NEGATIVE_INFINITY */pruningThreshold[level + 1],
						curGrammar.numSubStates, level);
				if (keepGoldAlive)
					ensureGoldTreeSurvives(tree, level);
			}

			curGrammar = grammarCascade[nGr][endLevel - startLevel + 1];
			curLexicon = lexiconCascade[nGr][endLevel - startLevel + 1];
			// numSubStatesArray = curGrammar.numSubStates;
			// clearArrays();
			double initVal = (viterbiParse) ? Double.NEGATIVE_INFINITY : 0;
			int level = isBaseline ? 1 : endLevel;
			createArrays(false, curGrammar.numStates, curGrammar.numSubStates,
					level, initVal, false, nGr);
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

			} else {
				// System.err.println("Using scaling code for sentence with length "+length+".");
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
				score = iScore[0][length][0][0];
				if (!viterbiParse)
					score = Math.log(score);// + (100*iScale[0][length][0]);
			}

			all_iScales.add(iScale);
			all_oScales.add(oScale);
			all_iScores.add(iScore);
			all_oScores.add(oScore);
		}
	}

	protected void ensureGoldTreeSurvives(Tree<StateSet> tree, int level) {
		List<Tree<StateSet>> children = tree.getChildren();
		for (Tree<StateSet> child : children) {
			if (!child.isLeaf())
				ensureGoldTreeSurvives(child, level);
		}
		StateSet node = tree.getLabel();
		short state = node.getState();
		if (level < 0) {
			vAllowedStates[node.from][node.to] = true;
		} else {
			int start = node.from, end = node.to;
			/*
			 * if (end-start==1 && !grammarTags[state]){ // POS tags -> use gold
			 * ones until lexicon is updated allowedStates[start][end]= new
			 * boolean[numStates]; Arrays.fill(allowedStates[start][end],
			 * false); allowedSubStates[start][end] = new boolean[numStates][];
			 * }
			 */
			allowedStates[start][end][state] = true;
			if (allowedSubStates[start][end] == null)
				allowedSubStates[start][end] = new boolean[numStates][];
			allowedSubStates[start][end][state] = null; // will be taken care of
														// in createArrays
			// boolean[] newArray = new boolean[numSubStatesArray[state]+1];
			// Arrays.fill(newArray, true);
			// allowedSubStates[node.from][node.to][state] = newArray;
		}

	}

	private void setGoldTreeCountsToOne(Tree<StateSet> tree) {
		StateSet node = tree.getLabel();
		short state = node.getState();
		iScore[node.from][node.to][state][0] = 1.0;
		oScore[node.from][node.to][state][0] = 1.0;
		List<Tree<StateSet>> children = tree.getChildren();
		for (Tree<StateSet> child : children) {
			if (!child.isLeaf())
				setGoldTreeCountsToOne(child);
		}
	}

	// public void updateFinalGrammarAndLexicon(Grammar grammar, Lexicon
	// lexicon){
	// grammarCascade[endLevel-startLevel+1] = grammar;
	// lexiconCascade[endLevel-startLevel+1] = lexicon;
	// Grammar tmpGrammar = grammar.copyGrammar(false);
	// tmpGrammar.logarithmMode();
	// Lexicon tmpLexicon = lexicon.copyLexicon();
	// tmpLexicon.logarithmMode();
	// grammarCascade[endLevel-startLevel] = null;//tmpGrammar; <- since we
	// don't pre-parse with G
	// lexiconCascade[endLevel-startLevel] = null;//tmpLexicon;
	// }

	public Tree<String> getBestParse(List<String> sentence) {
		return getBestConstrainedParse(sentence, null, false);
	}

	public double getLogInsideScore() {
		return logLikelihood;
	}

	// public Tree<String> getBestConstrainedParse(List<String> sentence,
	// List<String> posTags, boolean[][][][] allowedS){//List<Integer>[][]
	// pStates) {
	// if (allowedS==null) return getBestConstrainedParse(sentence, posTags,
	// false);
	// clearArrays();
	// length = (short)sentence.size();
	// Grammar curGrammar = grammarCascade[endLevel-startLevel+1];
	// Lexicon curLexicon = lexiconCascade[endLevel-startLevel+1];
	// double initVal = (viterbiParse) ? Double.NEGATIVE_INFINITY : 0;
	// int level = isBaseline ? 1 : endLevel;
	// allowedSubStates = allowedS;
	// createArrays(true,curGrammar.numStates,curGrammar.numSubStates,level,initVal,false);
	// setConstraints(allowedS);
	// return getBestConstrainedParse(sentence, posTags, true);
	// }

	// /**
	// * @param allowedS
	// */
	// private void setConstraints(boolean[][][][] allowedS) {
	// allowedSubStates = allowedS;
	// for (int start = 0; start < length; start++) {
	// for (int end = start + 1; end <= length; end++) {
	// for (int state=0; state<numSubStatesArray.length;state++){
	// boolean onePossible = false;
	// if (allowedSubStates[start][end][state]==null) continue;
	// for (int substate=0; substate<numSubStatesArray[state];substate++){
	// if (allowedSubStates[start][end][state][substate]) {
	// onePossible = true;
	// break;
	// }
	// }
	// if (onePossible) allowedStates[start][end][state]=true;
	// }
	// }
	// }
	// }

	public Tree<String> getBestConstrainedParse(List<String> sentence,
			List<String> posTags, boolean noPreparse) {
		if (sentence.size() == 0)
			return new Tree<String>("ROOT");
		if (!noPreparse)
			doPreParses(sentence, null, false, posTags);
		bestTree = new Tree<String>("ROOT");
		boolean scale = false;
		for (int nGr = 0; nGr < nGrammars; nGr++) {
			scale = scale || all_iScales.get(nGr) != null;
		}
		doCombinedMaxCScores(sentence, scale);
		// System.err.println("Done with scores");
		if (maxcScore[0][sentence.size()][0] == Double.NEGATIVE_INFINITY) {
			System.err
					.println("MaxCscore for ROOT was -Inf. Using single grammar.");
			nGrammars = 1;
			doCombinedMaxCScores(sentence, scale);
			nGrammars = all_iScores.size();
		}
		if (maxcScore[0][sentence.size()][0] != Double.NEGATIVE_INFINITY) {
			bestTree = extractBestMaxRuleParse(0, sentence.size(), sentence);
		} else {
			System.err.println("Still couldn't parse this sentence!");
		}
		return bestTree;
	}

	public double getModelScore(Tree<String> parsedTree) {
		if (viterbiParse)
			return logLikelihood;
		return savedScore;
	}

	public double getConfidence(Tree<String> tree) {
		if (logLikelihood == Double.NEGATIVE_INFINITY)
			return logLikelihood;
		// try{
		double treeLL = getLogLikelihood(tree);
		double sentenceLL = getLogLikelihood();
		return treeLL - sentenceLL;
		// } catch (Exception e){
		// System.err.println("Couldn't compute LL of tree: " + tree);
		// return Double.NEGATIVE_INFINITY;
		// }

	}

	public double getLogLikelihood(Tree<String> tree) {
		// if (logLikelihood == Double.NEGATIVE_INFINITY) return logLikelihood;
		//
		// if (viterbiParse) return logLikelihood;
		// ArrayList<Tree<String>> resultList = new ArrayList<Tree<String>>();
		// Tree<String> newTree =
		// TreeAnnotations.processTree(tree,1,0,binarization,false);
		// resultList.add(newTree);
		// StateSetTreeList resultStateSetTrees = new
		// StateSetTreeList(resultList, numSubStatesArray, false, tagNumberer);
		// if (llParser==null) llParser = new ArrayParser(grammar, lexicon);
		// for (Tree<StateSet> t : resultStateSetTrees){
		// llParser.doInsideScores(t,false,false,null); // Only inside scores
		// are needed here
		// double ll = Math.log(t.getLabel().getIScore(0));
		// ll += 100*t.getLabel().getIScale();
		// return ll;
		// }
		// return Double.NEGATIVE_INFINITY;
		if (true) {
			System.out.println("Not implemented (getLogLikelihood)");
			System.exit(-1);
		}
		return Double.NEGATIVE_INFINITY;
	}

	public double getLogLikelihood() {
		if (logLikelihood == Double.NEGATIVE_INFINITY)
			return logLikelihood;

		if (viterbiParse)
			return logLikelihood;

		logLikelihood = Math.log(iScore[0][length][0][0]);// +
		if (iScale != null)
			logLikelihood += ScalingTools.LOGSCALE * iScale[0][length][0];

		return logLikelihood;

	}

	/**
	 * Assumes that inside and outside scores (sum version, not viterbi) have
	 * been computed. In particular, the narrowRExtent and other arrays need not
	 * be updated.
	 */
	void doConstrainedMaxCScores(List<String> sentence, Grammar grammar,
			Lexicon lexicon, final boolean scale) {
		short[] numSubStatesArray = grammar.numSubStates;
		double initVal = Double.NEGATIVE_INFINITY;
		maxcScore = new double[length][length + 1][numStates];
		maxcSplit = new int[length][length + 1][numStates];
		maxcChild = new int[length][length + 1][numStates];
		maxcLeftChild = new int[length][length + 1][numStates];
		maxcRightChild = new int[length][length + 1][numStates];
		ArrayUtil.fill(maxcScore, Double.NEGATIVE_INFINITY);

		double logNormalizer = iScore[0][length][0][0];
		// double thresh2 = threshold*logNormalizer;
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
							double scoreToBeat = maxcScore[start][end][pState];
							for (int split = min; split <= max; split++) {
								double ruleScore = 0;
								if (!allowedStates[start][split][lState])
									continue;
								if (!allowedStates[split][end][rState])
									continue;
								double leftChildScore = maxcScore[start][split][lState];
								double rightChildScore = maxcScore[split][end][rState];
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

								if (gScore < scoreToBeat)
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
								if (doVariational) {
									double norm = 0;
									for (int np = 0; np < nParentStates; np++) {
										norm += oScore[start][end][pState][np]
												/ logNormalizer
												* iScore[start][end][pState][np];
									}
									ruleScore /= norm;
								}
								// double gScore = ruleScore * leftChildScore *
								// scalingFactor * rightChildScore;

								gScore += Math.log(ruleScore);

								if (gScore > scoreToBeat) {
									scoreToBeat = gScore;
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
						if (doVariational)
							lexiconScores = 1;
						else if (scale)
							scalingFactor = Math.log(ScalingTools
									.calcScaleFactor(oScale[start][end][tag]
											- iScale[0][length][0]));

						maxcScore[start][end][tag] = Math.log(lexiconScores)
								+ scalingFactor;
					}
				}
				// Try unary rules
				// Replacement for maxcScore[start][end], which is updated in
				// batch
				double[] maxcScoreStartEnd = new double[numStates];
				for (int i = 0; i < numStates; i++) {
					maxcScoreStartEnd[i] = maxcScore[start][end][i];
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
						double childScore = maxcScore[start][end][cState];
						if (childScore == initVal)
							continue;

						double scalingFactor = 0.0;
						if (scale)
							scalingFactor = Math.log(ScalingTools
									.calcScaleFactor(oScale[start][end][pState]
											+ iScale[start][end][cState]
											- iScale[0][length][0]));

						double gScore = scalingFactor + childScore;
						if (gScore < maxcScoreStartEnd[pState])
							continue;

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
								foundOne = true;
							}
						}
						if (ruleScore == 0)
							continue;
						if (doVariational) {
							double norm = 0;
							for (int np = 0; np < nParentStates; np++) {
								norm += oScore[start][end][pState][np]
										/ logNormalizer
										* iScore[start][end][pState][np];
							}
							ruleScore /= norm;
							ruleScores[pState][cState] = Math.max(ruleScore,
									ruleScores[pState][cState]);
						}

						gScore += Math.log(ruleScore);

						if (gScore > maxcScoreStartEnd[pState]) {
							maxcScoreStartEnd[pState] = gScore;
							maxcChild[start][end][pState] = cState;
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
				if (foundOne && doVariational)
					maxcScoreStartEnd = closeVariationalRules(ruleScores,
							start, end);
				maxcScore[start][end] = maxcScoreStartEnd;
			}
		}
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

			totalUsedUnaries++;
			// System.out.println("Adding a unary spanning from "+start+" to "+end+". P: "+stateStr+" C: "+child.get(0).getLabel());
			int intermediateNode = grammars[0].getUnaryIntermediate(
					(short) state, (short) cState);
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
			List<String> sentence) {
		List<Tree<String>> children = new ArrayList<Tree<String>>();
		String stateStr = (String) tagNumberer.object(state);// +""+start+""+end;
		if (stateStr.endsWith("^g"))
			stateStr = stateStr.substring(0, stateStr.length() - 2);
		boolean posLevel = (end - start == 1);
		if (posLevel) {
			if (grammars[0].isGrammarTag(state)) {
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
						+ Arrays.toString(iScore[start][end][state])
						+ " and the outsideScores are "
						+ Arrays.toString(oScore[start][end][state]));
				System.err.println("The maxcScore is "
						+ maxcScore[start][end][state]);
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

	/**
	 * Fills in the iScore array of each category over each span of length 2 or
	 * more.
	 */

	void doConstrainedViterbiInsideScores(Grammar grammar, boolean level0grammar) {
		short[] numSubStatesArray = grammar.numSubStates;
		// double[] oldIScores = new double[maxNSubStates];
		// int smallestScale = 10, largestScale = -10;
		for (int diff = 1; diff <= length; diff++) {
			for (int start = 0; start < (length - diff + 1); start++) {
				int end = start + diff;
				final int lastState = (level0grammar) ? 1
						: numSubStatesArray.length;
				for (int pState = 0; pState < lastState; pState++) {
					if (diff == 1)
						continue; // there are no binary rules that span over 1
									// symbol only
					// if (iScore[start][end][pState] == null) { continue; }
					if (!grammarTags[pState])
						continue;
					if (!vAllowedStates[start][end])
						continue;
					double oldIScore = viScore[start][end][pState];
					double bestIScore = oldIScore;
					BinaryRule[] parentRules = grammar.splitRulesWithP(pState);
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

						// new: loop over all substates
						double[][][] scores = r.getScores2();
						double pS = Double.NEGATIVE_INFINITY;
						if (scores[0][0] != null)
							pS = scores[0][0][0];
						if (pS == Double.NEGATIVE_INFINITY)
							continue;

						for (int split = min; split <= max; split++) {
							if (!vAllowedStates[start][split])
								continue;
							if (!vAllowedStates[split][end])
								continue;

							double lS = viScore[start][split][lState];
							if (lS == Double.NEGATIVE_INFINITY)
								continue;

							double rS = viScore[split][end][rState];
							if (rS == Double.NEGATIVE_INFINITY)
								continue;

							double tot = pS + lS + rS;
							if (tot >= bestIScore) {
								bestIScore = tot;
							}
						}
					}
					if (bestIScore > oldIScore) { // this way of making
													// "parentState" is better
						// than previous
						viScore[start][end][pState] = bestIScore;
						if (oldIScore == Double.NEGATIVE_INFINITY) {
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
				final int lastStateU = (level0grammar && diff > 1) ? 1
						: numSubStatesArray.length;
				for (int pState = 0; pState < lastStateU; pState++) {
					if (!grammarTags[pState])
						continue;
					// if (iScore[start][end][pState] == null) { continue; }
					// if (!allowedStates[start][end][pState][0]) continue;
					if (diff != 1 && !vAllowedStates[start][end])
						continue;
					UnaryRule[] unaries = grammar
							.getClosedViterbiUnaryRulesByParent(pState);
					double oldIScore = viScore[start][end][pState];
					double bestIScore = oldIScore;
					for (int r = 0; r < unaries.length; r++) {
						UnaryRule ur = unaries[r];
						int cState = ur.childState;

						if ((pState == cState))
							continue;// && (np == cp))continue;

						double iS = viScore[start][end][cState];
						if (iS == Double.NEGATIVE_INFINITY)
							continue;

						double[][] scores = ur.getScores2();
						double pS = Double.NEGATIVE_INFINITY;
						if (scores[0] != null)
							pS = scores[0][0];
						if (pS == Double.NEGATIVE_INFINITY)
							continue;

						double tot = iS + pS;

						if (tot >= bestIScore) {
							bestIScore = tot;
						}
					}
					if (bestIScore > oldIScore) {
						viScore[start][end][pState] = bestIScore;
						if (oldIScore == Double.NEGATIVE_INFINITY) {
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
						// }
						// }
					}
				}
			}
		}
	}

	// void doConstrainedViterbiSubstateInsideScores(Grammar grammar) {
	// numSubStatesArray = grammar.numSubStates;
	//
	// for (int diff = 1; diff <= length; diff++) {
	// for (int start = 0; start < (length - diff + 1); start++) {
	// int end = start + diff;
	// final int lastState = numSubStatesArray.length;
	// for (int pState=0; pState<lastState; pState++){
	// if (diff==1) continue; // there are no binary rules that span over 1
	// symbol only
	// //if (iScore[start][end][pState] == null) { continue; }
	// //if (!grammarTags[pState]) continue;
	// if (!allowedStates[start][end][pState]) continue;
	// final int nParentSubStates = numSubStatesArray[pState];
	// double[] bestIScore = new double[nParentSubStates];
	// double[] oldIScore = new double[nParentSubStates];
	// for (int s=0; s<nParentSubStates; s++) bestIScore[s] = oldIScore[s] =
	// iScore[start][end][pState][s];
	// BinaryRule[] parentRules = grammar.splitRulesWithP(pState);
	// for (int i = 0; i < parentRules.length; i++) {
	// BinaryRule r = parentRules[i];
	// int lState = r.leftChildState;
	// int rState = r.rightChildState;
	//
	// int narrowR = narrowRExtent[start][lState];
	// boolean iPossibleL = (narrowR < end); // can this left constituent leave
	// space for a right constituent?
	// if (!iPossibleL) { continue; }
	//
	// int narrowL = narrowLExtent[end][rState];
	// boolean iPossibleR = (narrowL >= narrowR); // can this right constituent
	// fit next to the left constituent?
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
	// for (int np = 0; np < nParentSubStates; np++) {
	// if (!allowedSubStates[start][end][pState][np]) continue;
	// for (int split = min; split <= max; split++) {
	// if (!allowedStates[start][split][lState]) continue;
	// if (!allowedStates[split][end][rState]) continue;
	//
	// for (int lp = 0; lp < scores.length; lp++) {
	// //if (!allowedSubStates[start][split][lState][lp]) continue;
	// double lS = iScore[start][split][lState][lp];
	// if (lS == Double.NEGATIVE_INFINITY) continue;
	//
	// for (int rp = 0; rp < scores[0].length; rp++) {
	// //if (!allowedSubStates[split][end][rState][rp]) continue;
	// double pS = Double.NEGATIVE_INFINITY;
	// if (scores[lp][rp]!=null) pS = scores[lp][rp][np];
	// if (pS==Double.NEGATIVE_INFINITY){
	// continue;
	// //System.out.println("s "+start+" sp "+split+" e "+end+" pS "+pS+" rS "+rS);
	// }
	// double rS = iScore[split][end][rState][rp];
	// if (rS == Double.NEGATIVE_INFINITY) continue;
	//
	// double tot = pS + lS + rS;
	// if (tot >= bestIScore[np]) { bestIScore[np] = tot;}
	// }
	// }
	// }
	// }
	// }
	// boolean firstTime = true;
	// for (int s=0; s<nParentSubStates; s++) {
	// if (bestIScore[s] > oldIScore[s]) { // this way of making "parentState"
	// is better
	// // than previous
	// iScore[start][end][pState][s] = bestIScore[s];
	// if (firstTime && oldIScore[s] == Double.NEGATIVE_INFINITY) {
	// firstTime = false;
	// if (start > narrowLExtent[end][pState]) {
	// narrowLExtent[end][pState] = start;
	// wideLExtent[end][pState] = start;
	// } else {
	// if (start < wideLExtent[end][pState]) {
	// wideLExtent[end][pState] = start;
	// }
	// }
	// if (end < narrowRExtent[start][pState]) {
	// narrowRExtent[start][pState] = end;
	// wideRExtent[start][pState] = end;
	// } else {
	// if (end > wideRExtent[start][pState]) {
	// wideRExtent[start][pState] = end;
	// }
	// }
	// }
	// }
	// }
	// }
	// final int lastStateU = numSubStatesArray.length;
	// for (int pState=0; pState<lastStateU; pState++){
	// //if (!grammarTags[pState]) continue;
	// //if (iScore[start][end][pState] == null) { continue; }
	// //if (!allowedStates[start][end][pState][0]) continue;
	// if (!allowedStates[start][end][pState]) continue;
	// UnaryRule[] unaries = grammar.getClosedViterbiUnaryRulesByParent(pState);
	// int nParentSubStates = numSubStatesArray[pState];
	// double[] bestIScore = new double[nParentSubStates];
	// double[] oldIScore = new double[nParentSubStates];
	// for (int s=0; s<nParentSubStates; s++) bestIScore[s] = oldIScore[s] =
	// iScore[start][end][pState][s];
	// for (int r = 0; r < unaries.length; r++) {
	// UnaryRule ur = unaries[r];
	// int cState = ur.childState;
	// if ((pState == cState)) continue;// && (np == cp))continue;
	// if (!allowedStates[start][end][cState]) continue;
	// //new loop over all substates
	// double[][] scores = ur.getScores2();
	// for (int np = 0; np < nParentSubStates; np++) {
	// if (!allowedSubStates[start][end][pState][np]) continue;
	// for (int cp = 0; cp < scores.length; cp++) {
	// //if (!allowedSubStates[start][end][cState][cp]) continue;
	// double pS = Double.NEGATIVE_INFINITY;
	// if (scores[cp]!=null) pS = scores[cp][np];
	// if (pS==Double.NEGATIVE_INFINITY){
	// continue;
	// }
	// double iS = iScore[start][end][cState][cp];
	// if (iS == Double.NEGATIVE_INFINITY) continue;
	// double tot = iS + pS;
	//
	// if (tot >= bestIScore[np]) { bestIScore[np] = tot; }
	// }
	// }
	// }
	// boolean firstTime = true;
	// for (int s=0; s<nParentSubStates; s++){
	// if (bestIScore[s] > oldIScore[s]) {
	// iScore[start][end][pState][s] = bestIScore[s];
	// if (firstTime && oldIScore[s] == Double.NEGATIVE_INFINITY) {
	// firstTime = false;
	// if (start > narrowLExtent[end][pState]) {
	// narrowLExtent[end][pState] = start;
	// wideLExtent[end][pState] = start;
	// } else {
	// if (start < wideLExtent[end][pState]) {
	// wideLExtent[end][pState] = start;
	// }
	// }
	// if (end < narrowRExtent[start][pState]) {
	// narrowRExtent[start][pState] = end;
	// wideRExtent[start][pState] = end;
	// } else {
	// if (end > wideRExtent[start][pState]) {
	// wideRExtent[start][pState] = end;
	// }
	// }
	// }
	// }
	// }
	// }
	// }
	// }
	// }

	void doConstrainedViterbiOutsideScores(Grammar grammar,
			boolean level0grammar) {
		for (int diff = length; diff >= 1; diff--) {
			for (int start = 0; start + diff <= length; start++) {
				int end = start + diff;
				final int lastState = (level0grammar) ? 1
						: grammar.numSubStates.length;
				for (int cState = 0; cState < lastState; cState++) {
					// if (diff>1 && !grammar.isGrammarTag[cState]) continue;
					if (!vAllowedStates[start][end])
						continue;

					double iS = viScore[start][end][cState];
					if (iS == Double.NEGATIVE_INFINITY) {
						continue;
					}

					double oldOScore = voScore[start][end][cState];
					double bestOScore = oldOScore;
					UnaryRule[] rules = grammar
							.getClosedViterbiUnaryRulesByChild(cState);
					for (int r = 0; r < rules.length; r++) {
						UnaryRule ur = rules[r];
						int pState = ur.parentState;
						if (cState == pState)
							continue;

						double oS = voScore[start][end][pState];
						if (oS == Double.NEGATIVE_INFINITY) {
							continue;
						}

						double[][] scores = ur.getScores2();

						double pS = scores[0][0];
						double tot = oS + pS;
						if (tot > bestOScore) {
							bestOScore = tot;
						}
					}
					if (bestOScore > oldOScore) {
						voScore[start][end][cState] = bestOScore;
					}

				}
				for (int pState = 0; pState < lastState; pState++) {
					if (!grammarTags[pState])
						continue;
					double oS = voScore[start][end][pState];
					if (oS == Double.NEGATIVE_INFINITY) {
						continue;
					}
					// if (!vAllowedStates[start][end]) continue;
					BinaryRule[] rules = grammar.splitRulesWithP(pState);
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
						double pS = Double.NEGATIVE_INFINITY;// scores[0][0][0];
						if (scores[0][0] != null)
							pS = scores[0][0][0];
						if (pS == Double.NEGATIVE_INFINITY) {
							continue;
						}

						for (int split = min; split <= max; split++) {
							if (!vAllowedStates[start][split])
								continue;
							if (!vAllowedStates[split][end])
								continue;

							double lS = viScore[start][split][lState];
							if (lS == Double.NEGATIVE_INFINITY) {
								continue;
							}

							double rS = viScore[split][end][rState];
							if (rS == Double.NEGATIVE_INFINITY) {
								continue;
							}

							double totL = pS + rS + oS;
							if (totL > voScore[start][split][lState]) {
								voScore[start][split][lState] = totL;
							}
							double totR = pS + lS + oS;
							if (totR > voScore[split][end][rState]) {
								voScore[split][end][rState] = totR;
							}
						}
					}
				}
			}
		}
	}

	// void doConstrainedViterbiSubstateOutsideScores(Grammar grammar) {
	// for (int diff = length; diff >= 1; diff--) {
	// for (int start = 0; start + diff <= length; start++) {
	// int end = start + diff;
	// final int lastState = numSubStatesArray.length;
	// for (int pState=0; pState<lastState; pState++){
	// //if (!grammarTags[pState]) continue;
	// //if (iScore[start][end][pState] == null) { continue; }
	// //if (!allowedStates[start][end][pState][0]) continue;
	// if (!allowedStates[start][end][pState]) continue;
	// UnaryRule[] rules = grammar.getClosedViterbiUnaryRulesByParent(pState);
	// for (int r = 0; r < rules.length; r++) {
	// UnaryRule ur = rules[r];
	// int cState = ur.childState;
	// if (cState == pState) continue;
	// if (!allowedStates[start][end][cState]) continue;
	//
	// //new loop over all substates
	// double[][] scores = ur.getScores2();
	// final int nParentSubStates = numSubStatesArray[cState];
	// double[] bestOScore = new double[nParentSubStates];
	// double[] oldOScore = new double[nParentSubStates];
	// for (int s=0; s<nParentSubStates; s++) bestOScore[s] = oldOScore[s] =
	// oScore[start][end][cState][s];
	//
	// for (int cp = 0; cp < scores.length; cp++) {
	// //if (!allowedSubStates[start][end][cState][cp]) continue;
	//
	// double iS = iScore[start][end][cState][cp];
	// if (iS == Double.NEGATIVE_INFINITY) { continue; }
	//
	// for (int np = 0; np < scores[0].length; np++) {
	// //if (!allowedSubStates[start][end][pState][np]) continue;
	// double pS = Double.NEGATIVE_INFINITY;
	// if (scores[cp]!=null) pS = scores[cp][np];
	// if (pS == Double.NEGATIVE_INFINITY) { continue; }
	//
	// double oS = oScore[start][end][pState][np];
	// if (oS == Double.NEGATIVE_INFINITY) { continue; }
	// double tot = oS + pS;
	//
	// if (tot > bestOScore[cp]) {
	// bestOScore[cp] = tot;
	// }
	// }
	// }
	// for (int s=0; s<nParentSubStates; s++) {
	// if (bestOScore[s] > oldOScore[s]) {
	// oScore[start][end][cState][s] = bestOScore[s];
	// }
	// }
	// }
	// }
	// for (int pState=0; pState<lastState; pState++){
	// if (!grammarTags[pState]) continue;
	// //if (oScore[start][end][pState] == null) { continue; }
	// //if (!allowedStates[start][end][pState][0]) continue;
	// if (!allowedStates[start][end][pState]) continue;
	// BinaryRule[] rules = grammar.splitRulesWithP(pState);
	// for (int r = 0; r < rules.length; r++) {
	// BinaryRule br = rules[r];
	//
	// int lState = br.leftChildState;
	// int min1 = narrowRExtent[start][lState];
	// if (end < min1) { continue; }
	//
	// int rState = br.rightChildState;
	// int max1 = narrowLExtent[end][rState];
	// if (max1 < min1) { continue; }
	//
	// int min = min1;
	// int max = max1;
	// if (max - min > 2) {
	// int min2 = wideLExtent[end][rState];
	// min = (min1 > min2 ? min1 : min2);
	// if (max1 < min) { continue; }
	// int max2 = wideRExtent[start][lState];
	// max = (max1 < max2 ? max1 : max2);
	// if (max < min) { continue; }
	// }
	//
	// double[][][] scores = br.getScores2();
	// for (int split = min; split <= max; split++) {
	// if (!allowedStates[start][split][lState]) continue;
	// if (!allowedStates[split][end][rState]) continue;
	//
	// for (int lp=0; lp<scores.length; lp++){
	// double lS = iScore[start][split][lState][lp];
	// if (lS == Double.NEGATIVE_INFINITY) { continue; }
	// double oldLOScore = oScore[start][split][lState][lp];
	// double bestLOScore = oldLOScore;
	//
	// for (int rp=0; rp<scores[lp].length; rp++){
	// if (scores[lp][rp]==null) continue;
	//
	// double rS = iScore[split][end][rState][rp];
	// if (rS == Double.NEGATIVE_INFINITY) { continue; }
	//
	// double oldROScore = oScore[split][end][rState][rp];
	// double bestROScore = oldROScore;
	//
	// for (int np=0; np<scores[lp][rp].length; np++){
	// double oS = oScore[start][end][pState][np];
	// if (oS == Double.NEGATIVE_INFINITY) { continue; }
	//
	// double pS = scores[lp][rp][np];
	// if (pS == Double.NEGATIVE_INFINITY) { continue; }
	//
	// double totL = pS + rS + oS;
	// if (totL > bestLOScore) {
	// bestLOScore = totL;
	// }
	// double totR = pS + lS + oS;
	// if (totR > bestROScore) {
	// bestROScore = totR;
	// }
	// }
	// if (bestLOScore > oldLOScore) {
	// oScore[start][split][lState][lp] = bestLOScore;
	// }
	// if (bestROScore > oldROScore) {
	// oScore[split][end][rState][rp] = bestROScore;
	// }
	// }
	// }
	// }
	// }
	// }
	// }
	// }
	// }

	public void printUnaryStats() {
		System.out.println("Touched " + touchedRules + " rules.");
		System.out.println("Used a total of " + totalUsedUnaries + " unaries.");
		System.out.println("Restored " + nTimesRestoredUnaries
				+ " unary chains.");
	}

	/**
	 * Return the single best parse. Note that the returned tree may be missing
	 * intermediate nodes in a unary chain because it parses with a unary-closed
	 * grammar.
	 */
	public Tree<String> extractBestViterbiParse(int gState, int gp, int start,
			int end, List<String> sentence) {
		// find sources of inside score
		// no backtraces so we can speed up the parsing for its primary use
		double bestScore = iScore[start][end][gState][gp];
		String goalStr = (String) tagNumberer.object(gState);
		if (goalStr.endsWith("^g"))
			goalStr = goalStr.substring(0, goalStr.length() - 2);
		if (outputSub)
			goalStr = goalStr + "-" + gp;
		if (outputScore)
			goalStr = goalStr + " " + bestScore;
		// System.out.println("Looking for "+goalStr+" from "+start+" to "+end+" with score "+
		// bestScore+".");
		if (end - start == 1) {
			// if the goal state is a preterminal state, then it can't transform
			// into
			// anything but the word below it
			if (!grammarTags[gState]) {
				List<Tree<String>> child = new ArrayList<Tree<String>>();
				child.add(new Tree<String>(sentence.get(start)));
				return new Tree<String>(goalStr, child);
			}
			// if the goal state is not a preterminal state, then find a way to
			// transform it into one
			else {
				double veryBestScore = Double.NEGATIVE_INFINITY;
				int newIndex = -1;
				int newCp = -1;
				UnaryRule[] unaries = grammar
						.getClosedViterbiUnaryRulesByParent(gState);
				double childScore = bestScore;
				for (int r = 0; r < unaries.length; r++) {
					UnaryRule ur = unaries[r];
					int cState = ur.childState;
					if (cState == gState)
						continue;
					if (grammarTags[cState])
						continue;
					if (!allowedStates[start][end][cState])
						continue;
					double[][] scores = ur.getScores2();
					for (int cp = 0; cp < scores.length; cp++) {
						if (scores[cp] == null)
							continue;
						double ruleScore = iScore[start][end][cState][cp]
								+ scores[cp][gp];
						if (ruleScore >= veryBestScore) {
							childScore = iScore[start][end][cState][cp];
							veryBestScore = ruleScore;
							newIndex = cState;
							newCp = cp;
						}
					}
				}

				List<Tree<String>> child1 = new ArrayList<Tree<String>>();
				child1.add(new Tree<String>(sentence.get(start)));
				String goalStr1 = (String) tagNumberer.object(newIndex);
				if (outputSub)
					goalStr1 = goalStr1 + "-" + newCp;
				if (outputScore)
					goalStr1 = goalStr1 + " " + childScore;
				if (goalStr1 == null)
					System.out.println("goalStr1==null with newIndex=="
							+ newIndex + " goalStr==" + goalStr);
				List<Tree<String>> child = new ArrayList<Tree<String>>();
				child.add(new Tree<String>(goalStr1, child1));
				return new Tree<String>(goalStr, child);
			}
		}
		// check binaries first
		BinaryRule[] parentRules = grammar.splitRulesWithP(gState);
		for (int split = start + 1; split < end; split++) {
			// for (Iterator binaryI = grammar.bRuleIteratorByParent(gState,
			// gp); binaryI.hasNext();) {
			// BinaryRule br = (BinaryRule) binaryI.next();
			for (int i = 0; i < parentRules.length; i++) {
				BinaryRule br = parentRules[i];

				int lState = br.leftChildState;
				if (iScore[start][split][lState] == null)
					continue;

				int rState = br.rightChildState;
				if (iScore[split][end][rState] == null)
					continue;

				// new: iterate over substates
				double[][][] scores = br.getScores2();
				for (int lp = 0; lp < scores.length; lp++) {
					for (int rp = 0; rp < scores[lp].length; rp++) {
						if (scores[lp][rp] == null)
							continue;
						double score = scores[lp][rp][gp]
								+ iScore[start][split][lState][lp]
								+ iScore[split][end][rState][rp];
						if (matches(score, bestScore)) {
							// build binary split
							Tree<String> leftChildTree = extractBestViterbiParse(
									lState, lp, start, split, sentence);
							Tree<String> rightChildTree = extractBestViterbiParse(
									rState, rp, split, end, sentence);
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
			if (cState == gState)
				continue;

			if (iScore[start][end][cState] == null)
				continue;

			// new: iterate over substates
			double[][] scores = ur.getScores2();
			for (int cp = 0; cp < scores.length; cp++) {
				if (scores[cp] == null)
					continue;
				double score = scores[cp][gp] + iScore[start][end][cState][cp];
				if (matches(score, bestScore)) {
					// build unary
					Tree<String> childTree = extractBestViterbiParse(cState,
							cp, start, end, sentence);
					List<Tree<String>> children = new ArrayList<Tree<String>>();
					children.add(childTree);

					// short intermediateNode =
					// grammar.getUnaryIntermediate((short)gState,(short)cState);
					// if (intermediateNode>0){
					// List<Tree<String>> restoredChild = new
					// ArrayList<Tree<String>>();
					// nTimesRestoredUnaries++;
					// String stateStr2 =
					// (String)tagNumberer.object(intermediateNode);
					// if (stateStr2.endsWith("^g")) stateStr2 =
					// stateStr2.substring(0,stateStr2.length()-2);
					// if (outputSub) stateStr2 = stateStr2 + "-" + 0;
					// if (outputScore) stateStr2 = stateStr2 + " " +
					// childScore;
					//
					// restoredChild.add(new Tree<String>(stateStr2, children));
					// //System.out.println("Restored a unary from "+start+" to "+end+": "+stateStr+" -> "+stateStr2+" -> "+child.get(0).getLabel());
					// return new Tree<String>(goalStr,restoredChild);
					// }
					// else {
					Tree<String> result = new Tree<String>(goalStr, children);
					return result;
					// }
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
		return new Tree<String>("ROOT");
	}

	// public double computeTightThresholds(List<String> sentence) {
	// clearArrays();
	// length = (short)sentence.size();
	// double score = 0;
	// Grammar curGrammar = null;
	// Lexicon curLexicon = null;
	// // double[] pruningThreshold =
	// {-6,-12,-14,-14,-14,-14,-14,-14};//Double.NEGATIVE_INFINITY;//Math.log(1.0e-10);
	// // double[] pruningThreshold =
	// {-6,-10,-10,-10,-10,-10,-10,-10};//Double.NEGATIVE_INFINITY;//Math.log(1.0e-10);
	// // double[] pruningThreshold =
	// {-6,-9.75,-10,-9.6,-9.66,-8.01,-7.4,-10};//Double.NEGATIVE_INFINITY;//Math.log(1.0e-10);
	// double[] pruningThreshold = {-16,-16,-16,-16,-16,-16,-16,-16};
	// //int startLevel = -1;
	// for (int level=startLevel; level<endLevel; level++){
	// if (level==-1) continue; // don't do the pre-pre parse
	//
	// curGrammar = grammarCascade[level-startLevel];
	// curLexicon = lexiconCascade[level-startLevel];
	//
	// createArrays(level==0,curGrammar.numStates,curGrammar.numSubStates,level,Double.NEGATIVE_INFINITY,false);
	//
	// initializeChart(sentence,curLexicon,level<1,false,null,false);
	// final boolean viterbi = true, logScores = true;
	// if (level<1){
	// doConstrainedViterbiInsideScores(curGrammar,level==startLevel);
	// score = viScore[0][length][0];
	// } else {
	// doConstrainedInsideScores(curGrammar,viterbi,logScores);
	// score = iScore[0][length][0][0];
	// }
	// if (score==Double.NEGATIVE_INFINITY) return -1;
	// System.out.println("\nFound a parse for sentence with length "+length+". The LL is "+score+".");
	// if (level<1){
	// voScore[0][length][0] = 0.0;
	// doConstrainedViterbiOutsideScores(curGrammar,level==startLevel);
	// } else {
	// oScore[0][length][0][0] = 0.0;
	// doConstrainedOutsideScores(curGrammar,viterbi,logScores);
	// }
	// double minThresh = -10;
	// if (level>=0){
	// minThresh = getTightestThrehold(0,length,0, true, level);
	// if (minThresh == Double.NEGATIVE_INFINITY) {
	// System.out.println("Something is wrong.");
	// return -20;
	// }
	// System.out.println("Can set the threshold for level "+level+" to "+minThresh);
	// maxThresholds[level] = Math.min(maxThresholds[level],minThresh);
	// }
	// // pruneChart(minThresh-1, curGrammar.numSubStates, level);
	// pruneChart(pruningThreshold[level+1], curGrammar.numSubStates, level);
	// }
	// return -1.0;
	//
	// }
	//
	// private double getTightestThrehold(int start, int end, int state, boolean
	// canStartWithUnary, int level) {
	// boolean posLevel = (end - start == 1);
	// if (posLevel) return -2;
	// double minChildren = Double.POSITIVE_INFINITY;
	// if (canStartWithUnary){
	// int cState = maxcChild[start][end][state];
	// if (cState != -1) {
	// return getTightestThrehold(start, end, cState, false,level);
	// }
	// }
	// int split = maxcSplit[start][end][state];
	// double lThresh = getTightestThrehold(start, split,
	// maxcLeftChild[start][end][state], true,level);
	// double rThresh = getTightestThrehold(split, end,
	// maxcRightChild[start][end][state], true,level);
	// minChildren = Math.min(lThresh,rThresh);
	//
	// double sentenceProb = (level<1) ? viScore[0][length][0] :
	// iScore[0][length][0][0];
	// double maxThreshold = Double.NEGATIVE_INFINITY;
	// for (int substate=0; substate < numSubStatesArray[state]; substate++){
	// double iS = (level<1) ? viScore[start][end][state] :
	// iScore[start][end][state][substate];
	// double oS = (level<1) ? voScore[start][end][state] :
	// oScore[start][end][state][substate];
	// if (iS==Double.NEGATIVE_INFINITY||oS==Double.NEGATIVE_INFINITY) continue;
	// double posterior = iS + oS - sentenceProb;
	// if (posterior > maxThreshold) maxThreshold = posterior;
	// }
	//
	// return Math.min(maxThreshold,minChildren);
	// }
	//
	//
	//
	// public void doGoldInsideOutsideScores(Tree<StateSet> tree, List<String>
	// sentence) {
	// Grammar curGrammar = grammarCascade[endLevel-startLevel+1];
	// Lexicon curLexicon = lexiconCascade[endLevel-startLevel+1];
	//
	// //pruneChart(Double.POSITIVE_INFINITY/*pruningThreshold[level+1]*/,
	// curGrammar.numSubStates, endLevel);
	// allowedStates = new boolean[length][length+1][numSubStatesArray.length];
	// ensureGoldTreeSurvives(tree, endLevel);
	//
	// double initVal = 0;
	// int level = isBaseline ? 1 : endLevel;
	// createArrays(false/*false*/,curGrammar.numStates,curGrammar.numSubStates,level,initVal,false);
	//
	// //setGoldTreeCountsToOne(tree);
	// initializeChart(sentence,curLexicon,false,true,null,false);
	// // doConstrainedInsideScores(curGrammar);
	// // logLikelihood = Math.log(iScore[0][length][0][0]); // +
	// (100*iScale[0][length][0]);
	// //
	// // oScore[0][length][0][0] = 1.0;
	// // doConstrainedOutsideScores(curGrammar);
	//
	// }
	//
	// public Tree<String> removeStars(Tree<String> tree) {
	//
	// String transformedLabel = tree.getLabel();
	// int starIndex = transformedLabel.indexOf("*");
	// if (starIndex != -1) transformedLabel =
	// transformedLabel.substring(0,starIndex);
	// if (tree.isPreTerminal()) {
	// return new Tree<String>(transformedLabel,tree.getChildren());
	// }
	// List<Tree<String>> transformedChildren = new ArrayList<Tree<String>>();
	// for (Tree<String> child : tree.getChildren()) {
	// transformedChildren.add(removeStars(child));
	// }
	// return new Tree<String>(transformedLabel, transformedChildren);
	// }
	//
	private double[] closeVariationalRules(double[][] ruleScores, int start,
			int end) {
		double[] closedScores = new double[numStates];
		for (int i = 0; i < numStates; i++) {
			closedScores[i] = maxcScore[start][end][i];
		}

		for (int length = 1; length < 10; length++) {
			for (int startState = 0; startState < numStates; startState++) {
				for (int endState = 0; endState < numStates; endState++) {
					for (int interState = 0; interState < numStates; interState++) {
						ruleScores[startState][endState] = Math.max(
								ruleScores[startState][endState],
								ruleScores[startState][interState]
										* ruleScores[interState][endState]);
					}
				}
			}
		}

		for (int childState = 0; childState < numStates; childState++) {
			double childScore = maxcScore[start][end][childState];
			if (childScore == 0)
				continue;
			for (int parentState = 0; parentState < numStates; parentState++) {
				double newScore = childScore
						* ruleScores[parentState][childState];
				if (newScore > closedScores[parentState]) {
					closedScores[parentState] = newScore;
					maxcChild[start][end][parentState] = childState;
				}

			}
		}
		return closedScores;
	}

	void doScaledConstrainedInsideScores(Grammar grammar) {
		double initVal = 0;
		short[] numSubStatesArray = grammar.numSubStates;
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
					if (!allowedStates[start][end][pState])
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

						for (int split = min; split <= max; split++) {
							boolean changeThisRound = false;
							if (allowedStates[start][split][lState] == false)
								continue;
							if (allowedStates[split][end][rState] == false)
								continue;

							for (int lp = 0; lp < nLeftChildStates; lp++) {
								double lS = iScore[start][split][lState][lp];
								if (lS == initVal)
									continue;

								for (int rp = 0; rp < nRightChildStates; rp++) {
									if (scores[lp][rp] == null)
										continue;
									double rS = iScore[split][end][rState][rp];
									if (rS == initVal)
										continue;

									for (int np = 0; np < nParentStates; np++) {
										if (!allowedSubStates[start][end][pState][np])
											continue;
										double pS = scores[lp][rp][np];
										if (pS == initVal)
											continue;

										double thisRound = pS * lS * rS;
										unscaledScoresToAdd[np] += thisRound;

										somethingChanged = true;
										changeThisRound = true;
									}
								}
							}
							if (!changeThisRound)
								continue;
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
											iScore[start][end][pState],
											parentScale, newScale);
									iScale[start][end][pState] = newScale;
								}
							}
							for (int np = 0; np < nParentStates; np++) {
								iScore[start][end][pState][np] += unscaledScoresToAdd[np];
							}
							Arrays.fill(unscaledScoresToAdd, 0);
						}
					}
					if (somethingChanged) {
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
				double[][] scoresAfterUnaries = new double[numStates][];
				for (int pState = 0; pState < numSubStatesArray.length; pState++) {
					if (!allowedStates[start][end][pState])
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

						if (iScore[start][end][cState] == null)
							continue;

						double[][] scores = ur.getScores2();
						boolean changeThisRound = false;
						int nChildStates = numSubStatesArray[cState];// scores[0].length;
						for (int cp = 0; cp < nChildStates; cp++) {
							if (scores[cp] == null)
								continue;
							double iS = iScore[start][end][cState][cp];
							if (iS == initVal)
								continue;

							for (int np = 0; np < nParentStates; np++) {
								if (!allowedSubStates[start][end][pState][np])
									continue;
								double pS = scores[cp][np];
								if (pS == initVal)
									continue;

								double thisRound = iS * pS;
								unscaledScoresToAdd[np] += thisRound;

								somethingChanged = true;
								changeThisRound = true;
							}
						}
						if (!changeThisRound)
							continue;

						if (scoresAfterUnaries[pState] == null) {
							scoresAfterUnaries[pState] = new double[numSubStatesArray[pState]];
						}

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
										scoresAfterUnaries[pState],
										parentScale, newScale);
								parentScale = newScale;
							}
						}
						for (int np = 0; np < nParentStates; np++) {
							scoresAfterUnaries[pState][np] += unscaledScoresToAdd[np];
						}
						Arrays.fill(unscaledScoresToAdd, 0);
					}
					if (somethingChanged) {
						int newScale = Math
								.max(scaleBeforeUnaries, parentScale);
						ScalingTools.scaleArrayToScale(
								iScore[start][end][pState], scaleBeforeUnaries,
								newScale);
						ScalingTools.scaleArrayToScale(
								scoresAfterUnaries[pState], parentScale,
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
						if (scoresAfterUnaries[pState] == null)
							continue;
						double val = scoresAfterUnaries[pState][np];
						if (val > 0) {
							iScore[start][end][pState][np] += val;
						}
					}
				}
			}
		}
	}

	void doScaledConstrainedOutsideScores(Grammar grammar) {
		double initVal = 0;
		short[] numSubStatesArray = grammar.numSubStates;
		// Arrays.fill(scoresToAdd,initVal);
		for (int diff = length; diff >= 1; diff--) {
			for (int start = 0; start + diff <= length; start++) {
				int end = start + diff;
				// do unaries
				double[][] scoresAfterUnaries = new double[numStates][];

				for (int cState = 0; cState < numSubStatesArray.length; cState++) {
					if (allowedStates[start][end][cState] == false)
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
					// grammar.getUnaryRulesByParent(pState).toArray(new
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
						if (allowedStates[start][end][pState] == false)
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

								double oS = oScore[start][end][pState][np];
								if (oS == initVal)
									continue;

								double thisRound = oS * pS;
								unscaledScoresToAdd[cp] += thisRound;

								somethingChanged = true;
								changeThisRound = true;
							}
						}
						if (!changeThisRound)
							continue;
						if (scoresAfterUnaries[cState] == null) {
							scoresAfterUnaries[cState] = new double[numSubStatesArray[cState]];
						}
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
										scoresAfterUnaries[cState], childScale,
										newScale);
								childScale = newScale;
							}
						}
						for (int cp = 0; cp < nChildStates; cp++) {
							scoresAfterUnaries[cState][cp] += unscaledScoresToAdd[cp];
						}
						Arrays.fill(unscaledScoresToAdd, initVal);
					}
					if (somethingChanged) {
						int newScale = Math.max(scaleBeforeUnaries, childScale);
						ScalingTools.scaleArrayToScale(
								oScore[start][end][cState], scaleBeforeUnaries,
								newScale);
						ScalingTools.scaleArrayToScale(
								scoresAfterUnaries[cState], childScale,
								newScale);
						oScale[start][end][cState] = newScale;
					}
					// copy/add the entries where the unaries where not useful
					for (int cp = 0; cp < nChildStates; cp++) {
						if (scoresAfterUnaries[cState] == null)
							continue;
						double val = scoresAfterUnaries[cState][cp];
						if (val > 0) {
							oScore[start][end][cState][cp] += val;
						}
					}
				}

				// do binaries
				if (diff == 1)
					continue; // there is no space for a binary
				for (int pState = 0; pState < numSubStatesArray.length; pState++) {
					if (allowedStates[start][end][pState] == false)
						continue;
					final int nParentChildStates = numSubStatesArray[pState];
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
							if (allowedStates[start][split][lState] == false)
								continue;
							if (allowedStates[split][end][rState] == false)
								continue;

							boolean somethingChanged = false;
							for (int lp = 0; lp < nLeftChildStates; lp++) {
								double lS = iScore[start][split][lState][lp];
								// if (lS==0) continue;

								for (int rp = 0; rp < nRightChildStates; rp++) {
									if (scores[lp][rp] == null)
										continue;
									double rS = iScore[split][end][rState][rp];
									// if (rS==0) continue;

									for (int np = 0; np < nParentChildStates; np++) {
										double pS = scores[lp][rp][np];
										if (pS == initVal)
											continue;

										double oS = oScore[start][end][pState][np];
										if (oS == initVal)
											continue;
										// if
										// (!allowedSubStates[start][end][pState][np])
										// continue;

										double thisRoundL = pS * rS * oS;
										double thisRoundR = pS * lS * oS;

										scoresToAdd[lp] += thisRoundL;
										unscaledScoresToAdd[rp] += thisRoundR;

										somethingChanged = true;
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
										ScalingTools.scaleArrayToScale(
												oScore[start][split][lState],
												leftScale, newScale);
										oScale[start][split][lState] = newScale;
									}
								}
								for (int cp = 0; cp < nLeftChildStates; cp++) {
									if (scoresToAdd[cp] > initVal) {
										oScore[start][split][lState][cp] += scoresToAdd[cp];
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
												oScore[split][end][rState],
												rightScale, newScale);
										oScale[split][end][rState] = newScale;
									}
								}
								for (int cp = 0; cp < nRightChildStates; cp++) {
									if (unscaledScoresToAdd[cp] > initVal) {
										oScore[split][end][rState][cp] += unscaledScoresToAdd[cp];
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

	protected void setupScaling() {
		// create arrays for scaling coefficients
		iScale = new int[length][length + 1][];
		oScale = new int[length][length + 1][];

		for (int start = 0; start < length; start++) {
			for (int end = start + 1; end <= length; end++) {
				iScale[start][end] = new int[numStates];
				oScale[start][end] = new int[numStates];
				Arrays.fill(iScale[start][end], Integer.MIN_VALUE);
				Arrays.fill(oScale[start][end], Integer.MIN_VALUE);
			}
		}
		// scrub the iScores array
		for (int start = 0; start < length; start++) {
			for (int end = start + 1; end <= length; end++) {
				for (int state = 0; state < numStates; state++) {
					if (allowedStates[start][end][state] == true) {// != null){
						Arrays.fill(iScore[start][end][state], 0);
					}
				}
			}
		}

	}

	public synchronized Object call() {
		Tree<String> parse = getBestParse(nextSentence);
		nextSentence = null;
		ArrayList<Tree<String>> result = new ArrayList<Tree<String>>();
		result.add(parse);
		synchronized (queue) {
			queue.add(result, -nextSentenceID);
			queue.notifyAll();
		}
		return null;
	}

	// public CoarseToFineMaxRuleProductParser newInstance(){
	// CoarseToFineMaxRuleProductParser newParser = new
	// CoarseToFineMaxRuleProductParser(grammar, lexicon, unaryPenalty,
	// endLevel, viterbiParse, outputSub, outputScore, accurate,
	// this.doVariational,useGoldPOS, false);
	// newParser.initCascade(this);
	// return newParser;
	// }

	public double getSentenceProbability(int start, int end, boolean sumScores) {
		// System.out.println((allowedStates[start][end][0]));
		// System.out.println((allowedSubStates[start][end][0][0]));
		// System.out.println(Arrays.toString(iScore[start][end][0]));
		double score = 0;
		if (sumScores) {
			System.err.println("Not implemented (getSentenceProbability).");
			System.exit(-1);
			// for (int pState=0; pState<numStates; pState++){
			// if (allowedStates[start][end+1][pState] == false) continue;
			// for (int cp=0; cp<numSubStatesArray[pState]; cp++){
			// score += (iScore[start][end+1][pState][cp]);
			// }
			// }
		} else {
			score = iScore[start][end + 1][0][0];
		}
		return Math.log(score);
	}

	public boolean[][][] getAllowedStates() {
		return allowedStates;
	}

	public boolean[][][][] getAllowedSubStates() {
		return allowedSubStates;
	}

	int nThBlock = 0;

	public void dumpPosteriors(String fileName, int blockSize) {
		if (posteriorsToDump == null && blockSize > 0) {
			posteriorsToDump = new ArrayList<Posterior>(blockSize);
		}

		if (posteriorsToDump.size() == blockSize || blockSize == -1) {
			fileName = fileName + "." + nThBlock++;
			try {
				ObjectOutputStream out = new ObjectOutputStream(
						new GZIPOutputStream(new FileOutputStream(fileName)));
				out.writeObject(posteriorsToDump);
				out.flush();
				out.close();
			} catch (IOException e) {
				System.out.println("IOException: " + e);
			}
			if (blockSize == -1)
				return;
			posteriorsToDump = new ArrayList<Posterior>(blockSize);
		}
		Posterior posterior = new Posterior(iScore, oScore, iScale, oScale,
				allowedStates);
		posteriorsToDump.add(posterior);

	}

	private void doCombinedMaxCScores(List<String> sentence, boolean scale) {
		maxcScore = new double[length][length + 1][numStates];
		maxcSplit = new int[length][length + 1][numStates];
		maxcChild = new int[length][length + 1][numStates];
		maxcLeftChild = new int[length][length + 1][numStates];
		maxcRightChild = new int[length][length + 1][numStates];
		ArrayUtil.fill(maxcScore, Double.NEGATIVE_INFINITY);
		if (scale)
			System.out.println("Using scaling code");
		double[] logNormalizer = new double[nGrammars];
		for (int i = 0; i < nGrammars; i++) {
			logNormalizer[i] = all_iScores.get(i)[0][length][0][0];
		}

		for (int diff = 1; diff <= length; diff++) {
			for (int start = 0; start < (length - diff + 1); start++) {
				int end = start + diff;
				Arrays.fill(maxcSplit[start][end], -1);
				Arrays.fill(maxcChild[start][end], -1);
				Arrays.fill(maxcLeftChild[start][end], -1);
				Arrays.fill(maxcRightChild[start][end], -1);
				if (diff > 1) {
					// diff > 1: Try binary rules
					for (short pState = 0; pState < numStates; pState++) {
						if (!allowedStates[start][end][pState])
							continue;
						BinaryRule[] parentRules = grammars[0]
								.splitRulesWithP(pState);

						for (int i = 0; i < parentRules.length; i++) {
							BinaryRule r = parentRules[i];
							short lState = r.leftChildState;
							short rState = r.rightChildState;

							double scoreToBeat = maxcScore[start][end][pState];
							for (int split = start + 1; split <= end - 1; split++) {
								if (!allowedStates[start][split][lState])
									continue;
								if (!allowedStates[split][end][rState])
									continue;
								double leftChildScore = maxcScore[start][split][lState];
								double rightChildScore = maxcScore[split][end][rState];
								if (leftChildScore == Double.NEGATIVE_INFINITY
										|| rightChildScore == Double.NEGATIVE_INFINITY)
									continue;

								double scalingFactor = 0.0;
								if (scale) {
									for (int gr = 0; gr < nGrammars; gr++) {
										if (all_oScales.get(gr) != null) {
											scalingFactor += all_oScales
													.get(gr)[start][end][pState]
													+ all_iScales.get(gr)[start][split][lState]
													+ all_iScales.get(gr)[split][end][rState]
													- all_iScales.get(gr)[0][length][0];
										}
									}
									scalingFactor = Math.log(ScalingTools
											.calcScaleFactor(scalingFactor));
								}
								double gScore = leftChildScore + scalingFactor
										+ rightChildScore;

								if (gScore < scoreToBeat)
									continue; // no chance of finding a better
												// derivation

								for (int gr = 0; gr < nGrammars; gr++) {
									double ruleScore = 0;
									BinaryRule rule = grammars[gr]
											.getBinaryRule(pState, lState,
													rState);
									if (rule == null) {
										System.err.println("Dont have rule "
												+ (String) tagNumberer
														.object(pState)
												+ " -> "
												+ (String) tagNumberer
														.object(lState)
												+ " "
												+ (String) tagNumberer
														.object(rState)
												+ " in grammar " + gr);
										continue;
									}
									double[][][] scores = rule.getScores2();
									int nParentStates = numSubStates[gr][pState]; // ==
																					// scores[0][0].length;
									int nLeftChildStates = numSubStates[gr][lState]; // ==
																						// scores.length;
									int nRightChildStates = numSubStates[gr][rState]; // ==
																						// scores[0].length;

									for (int lp = 0; lp < nLeftChildStates; lp++) {
										double lIS = all_iScores.get(gr)[start][split][lState][lp];
										if (lIS == 0)
											continue;

										for (int rp = 0; rp < nRightChildStates; rp++) {
											if (scores[lp][rp] == null)
												continue;
											double rIS = all_iScores.get(gr)[split][end][rState][rp];
											if (rIS == 0)
												continue;
											for (int np = 0; np < nParentStates; np++) {
												double pOS = all_oScores
														.get(gr)[start][end][pState][np];
												if (pOS == 0)
													continue;

												double ruleS = scores[lp][rp][np];
												if (ruleS == 0)
													continue;
												ruleScore += (pOS * ruleS * lIS * rIS)
														/ logNormalizer[gr];
											}
										}
									}
									// if (ruleScore==0) continue;
									gScore += Math.log(ruleScore);
								}

								if (gScore > scoreToBeat) {
									scoreToBeat = gScore;
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
					for (int tag = 0; tag < numStates; tag++) {
						if (!allowedStates[start][end][tag])
							continue;

						String word = sentence.get(start);
						if (grammarTags[tag])
							continue;
						double lexiconScores = 0;
						for (int gr = 0; gr < nGrammars; gr++) {
							if (all_oScores.get(gr)[start][end][tag] == null) {
								System.err.println("Grammar " + gr
										+ " is missing scores for tag " + tag
										+ " " + tagNumberer.object(tag));
								System.err.println("Start " + start);
								System.err.println("End " + end);
								System.err.println("Tag " + tag);
								continue;
							}
							double ruleScore = 0;
							double[] lexiconScoreArray = lexicons[gr].score(
									word, (short) tag, start, false, false);
							for (int tp = 0; tp < numSubStates[gr][tag]; tp++) {
								// System.err.println("Found score for grammar "+gr+", tag "+tag+" "+tagNumberer.object(tag));
								double pOS = all_oScores.get(gr)[start][end][tag][tp];
								double ruleS = lexiconScoreArray[tp];
								ruleScore += (pOS * ruleS) / logNormalizer[gr]; // The
																				// inside
																				// score
																				// of
																				// a
																				// word
																				// is
																				// 0.0f
							}
							// if (ruleScore==0) continue;
							lexiconScores += Math.log(ruleScore);
						}

						double scalingFactor = 0.0;
						if (scale) {
							for (int gr = 0; gr < nGrammars; gr++) {
								try {
									if (all_oScales.get(gr) != null) {
										scalingFactor += all_oScales.get(gr)[start][end][tag]
												- all_iScales.get(gr)[0][length][0];
									}
								} catch (java.lang.Exception e) {
									System.err.println("Start " + start);
									System.err.println("End " + end);
									System.err.println("Length " + length);
									System.err.println("Tag " + tag);
									System.err.println("Grammar " + gr);
								}
							}
							// System.err.println(scalingFactor);
							scalingFactor = Math.log(ScalingTools
									.calcScaleFactor(scalingFactor));
						}

						maxcScore[start][end][tag] = lexiconScores
								+ scalingFactor;
					}
				}
				// Try unary rules
				// Replacement for maxcScore[start][end], which is updated in
				// batch
				double[] maxcScoreStartEnd = new double[numStates];
				for (int i = 0; i < numStates; i++) {
					maxcScoreStartEnd[i] = maxcScore[start][end][i];
				}

				for (short pState = 0; pState < numStates; pState++) {
					if (!allowedStates[start][end][pState])
						continue;
					UnaryRule[] unaries = grammars[0]
							.getClosedSumUnaryRulesByParent(pState);
					for (int r = 0; r < unaries.length; r++) {
						UnaryRule ur = unaries[r];
						short cState = ur.childState;
						if ((pState == cState))
							continue;// && (np == cp))continue;
						if (!allowedStates[start][end][cState])
							continue;
						double childScore = maxcScore[start][end][cState];
						if (childScore == Double.NEGATIVE_INFINITY)
							continue;

						double scalingFactor = 0.0;
						if (scale) {
							for (int gr = 0; gr < nGrammars; gr++) {
								if (all_oScales.get(gr) != null) {
									scalingFactor += all_oScales.get(gr)[start][end][pState]
											+ all_iScales.get(gr)[start][end][cState]
											- all_iScales.get(gr)[0][length][0];
								}
							}
							// System.err.println(scalingFactor);
							scalingFactor = Math.log(ScalingTools
									.calcScaleFactor(scalingFactor));
						}

						double gScore = scalingFactor + childScore;
						if (gScore < maxcScoreStartEnd[pState])
							continue;

						for (int gr = 0; gr < nGrammars; gr++) {
							double ruleScore = 0;

							// TODO: this could be a problem
							// ClosedSumUnaryRulesByParent(pState);

							// double[][] scores =
							// grammars[gr].getUnaryRule(pState,
							// cState).getScores2();
							UnaryRule rule = grammars[gr].getUnaryRule(pState,
									cState);
							if (rule == null) {
								System.err.println("Dont have rule "
										+ (String) tagNumberer.object(pState)
										+ " -> "
										+ (String) tagNumberer.object(cState)
										+ " in grammar " + gr);
								continue;
							}
							double[][] scores = rule.getScores2();

							int nChildStates = numSubStates[gr][cState]; // ==
																			// scores.length;
							int nParentStates = numSubStates[gr][pState]; // ==
																			// scores[0].length;

							for (int cp = 0; cp < nChildStates; cp++) {
								double cIS = all_iScores.get(gr)[start][end][cState][cp];
								if (cIS == 0)
									continue;

								if (scores[cp] == null)
									continue;
								if (all_oScores.get(gr)[start][end][pState] == null) {
									System.err
											.println("Missing oScore for grammar "
													+ gr
													+ " "
													+ tagNumberer
															.object(pState));
									System.err.println("Start " + start);
									System.err.println("End " + end);
									System.err.println("Tag " + pState);
									System.err
											.println("allowed: "
													+ allowedStates[start][end][pState]);
									if (all_iScores.get(gr)[start][end][pState] != null) {
										System.err.println("Have iScore");
									}
									continue;
								}
								for (int np = 0; np < nParentStates; np++) {
									double pOS = all_oScores.get(gr)[start][end][pState][np];
									if (pOS < 0)
										continue;

									double ruleS = scores[cp][np];
									if (ruleS == 0)
										continue;
									ruleScore += (pOS * ruleS * cIS)
											/ logNormalizer[gr];
								}
							}
							// if (ruleScore==0) continue;
							gScore += Math.log(ruleScore);
						}

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

}
