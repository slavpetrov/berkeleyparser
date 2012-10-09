package edu.berkeley.nlp.PCFGLA;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.Numberer;
import edu.berkeley.nlp.util.PriorityQueue;
import edu.berkeley.nlp.util.StringUtils;

public class ConstrainedArrayParser extends ArrayParser implements Callable {
	List<Integer>[][] possibleStates;
	/** inside scores; start idx, end idx, state -> logProb */
	protected double[][][][] iScore;
	/** outside scores; start idx, end idx, state -> logProb */
	protected double[][][][] oScore;
	protected short[] numSubStatesArray;
	public long totalUsedUnaries;
	public long nRules, nRulesInf;
	// the chart is now using scaled probabilities, NOT log-probs.
	protected int[][][] iScale; // for each (start,end) span there is a scaling
								// factor
	protected int[][][] oScale;
	public Binarization binarization;

	Counter<String> stateCounter = new Counter<String>();
	Counter<String> ruleCounter = new Counter<String>();

	public boolean viterbi = false;
	/** number of times we restored unaries */
	public int nTimesRestoredUnaries;

	boolean noConstrains = false;

	protected List<String> nextSentence;
	protected int nextSentenceID;
	int myID;
	PriorityQueue<List<Tree<String>>> queue;

	public void setID(int i, PriorityQueue<List<Tree<String>>> q) {
		myID = i;
		queue = q;
	}

	public void setNextSentence(List<String> nextS, int nextID) {
		nextSentence = nextS;
		nextSentenceID = nextID;
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

	public ConstrainedArrayParser newInstance() {
		ConstrainedArrayParser newParser = new ConstrainedArrayParser(grammar,
				lexicon, numSubStatesArray);
		return newParser;
	}

	public double getLogLikelihood(Tree<String> t) {
		System.out.println("Unsuported for now!");
		return Double.NEGATIVE_INFINITY;
	}

	public Tree<String>[] getSampledTrees(List<String> sentence,
			List<Integer>[][] pStates, int n) {
		return null;
	}

	public void setNoConstraints(boolean noC) {
		this.noConstrains = noC;
	}

	public List<Tree<String>> getKBestConstrainedParses(List<String> sentence,
			List<String> posTags, int k) {
		return null;
	}

	public ConstrainedArrayParser() {
	}

	public ConstrainedArrayParser(Grammar gr, Lexicon lex, short[] nSub) {
		super(gr, lex);
		this.numSubStatesArray = nSub;
		totalUsedUnaries = 0;
		nTimesRestoredUnaries = 0;
		nRules = 0;
		nRulesInf = 0;
		// Math.pow(GrammarTrainer.SCALE,scaleDiff);
	}

	// public Tree<String> getBestConstrainedParse(List<String> sentence,
	// List<Integer>[][] pStates) {
	// length = (short)sentence.size();
	// this.possibleStates = pStates;
	// createArrays();
	// initializeChart(sentence);
	//
	// doConstrainedInsideScores();
	// //showScores(iScore, "Inside scores:");
	// /* oScore[0][length][0][0] = 0;
	// doConstrainedOutsideScores();
	//
	// List<Integer> possibleParentSt = possibleStates[12][13];
	// for (int pState : possibleParentSt){
	// System.out.println(pState + " " + (String) tagNumberer.object(pState) +
	// " iScore "+ Arrays.toString(iScore[12][13][pState]) + " oScore "+
	// Arrays.toString(oScore[12][13][pState]));
	// }
	//
	// */
	// Tree<String> bestTree = new Tree<String>("ROOT");
	// double score = iScore[0][length][0][0];
	// if (score > Double.NEGATIVE_INFINITY) {
	// //System.out.println("\nFound a parse for sentence with length "+length+". The LL is "+score+".");
	// Tree<StateSet> bestStateSetTree = extractBestStateSetTree(zero, zero,
	// zero, length, sentence);
	// tallyStatesAndRules(bestStateSetTree);
	// bestTree = restoreStateSetTreeUnaries(bestStateSetTree);
	// //bestTree = extractBestParse(0, 0, 0, length, sentence);
	// //restoreUnaries(bestTree);
	// }
	// else {
	// System.out.println("()\nDid NOT find a parse for sentence with length "+length+".");
	// }
	//
	//
	// return bestTree;
	// }

	/**
	 * Create a string representing the state for a StateSet tree that has the
	 * substate first iScore.
	 * 
	 */
	private String getStateString(Tree<StateSet> tree) {
		return tagNumberer.object(tree.getLabel().getState()) + "&"
				+ (short) tree.getLabel().getIScore(0);
	}

	/**
	 * Compute statistics on how often each state and rule appeared.
	 * 
	 * @param bestStateSetTree
	 */
	private void tallyStatesAndRules(Tree<StateSet> bestStateSetTree) {
		if (bestStateSetTree.isLeaf() || bestStateSetTree.isPreTerminal())
			return;
		String stateString = getStateString(bestStateSetTree);
		stateCounter.incrementCount(stateString, 1);
		String ruleString = stateString + "->";
		for (Tree<StateSet> child : bestStateSetTree.getChildren()) {
			tallyStatesAndRules(child);
			ruleString += "|" + getStateString(child);
		}
		ruleCounter.incrementCount(ruleString, 1);
	}

	/**
	 * Print the statistics about how often each state and rule appeared.
	 * 
	 */
	public void printStateAndRuleTallies() {
		System.out.println("STATE TALLIES");
		for (String state : stateCounter.keySet()) {
			System.out.println(state + " " + stateCounter.getCount(state));
		}
		System.out.println("RULE TALLIES");
		for (String rule : ruleCounter.keySet()) {
			System.out.println(rule + " " + ruleCounter.getCount(rule));
		}
	}

	protected void createArrays() {
		// zero out some stuff first in case we recently ran out of memory and
		// are reallocating
		clearArrays();

		// allocate just the parts of iScore and oScore used (end > start, etc.)
		// System.out.println("initializing iScore arrays with length " + length
		// + " and numStates " + numStates);
		iScore = new double[length][length + 1][][];
		oScore = new double[length][length + 1][][];
		iScale = new int[length][length + 1][];
		oScale = new int[length][length + 1][];
		for (int start = 0; start < length; start++) { // initialize for all POS
														// tags so that we can
														// use the lexicon
			int end = start + 1;
			iScore[start][end] = new double[numStates][];
			oScore[start][end] = new double[numStates][];
			iScale[start][end] = new int[numStates];
			oScale[start][end] = new int[numStates];
			for (int state = 0; state < numStates; state++) {
				iScore[start][end][state] = new double[numSubStatesArray[state]];
				oScore[start][end][state] = new double[numSubStatesArray[state]];
				Arrays.fill(iScore[start][end][state], Float.NEGATIVE_INFINITY);
				Arrays.fill(oScore[start][end][state], Float.NEGATIVE_INFINITY);
			}
		}

		for (int start = 0; start < length; start++) {
			for (int end = start + 2; end <= length; end++) {
				iScore[start][end] = new double[numStates][];
				oScore[start][end] = new double[numStates][];
				iScale[start][end] = new int[numStates];
				oScale[start][end] = new int[numStates];
				List<Integer> pStates = null;
				if (noConstrains) {
					pStates = new ArrayList<Integer>();
					for (int i = 0; i < numStates; i++) {
						pStates.add(i);
					}
				} else {
					pStates = possibleStates[start][end];
				}

				for (int state : pStates) {
					iScore[start][end][state] = new double[numSubStatesArray[state]];
					oScore[start][end][state] = new double[numSubStatesArray[state]];
					Arrays.fill(iScore[start][end][state],
							Float.NEGATIVE_INFINITY);
					Arrays.fill(oScore[start][end][state],
							Float.NEGATIVE_INFINITY);
				}
				if (start == 0 && end == length) {
					if (pStates.size() == 0)
						System.out.println("no states span the entire tree!");
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
	}

	void initializeChart(List<String> sentence) {
		// for simplicity the lexicon will store words and tags as strings,
		// while the grammar will be using integers -> Numberer()
		int start = 0;
		int end = start + 1;
		for (String word : sentence) {
			end = start + 1;
			for (short tag = 0; tag < grammar.numSubStates.length; tag++) {
				if (grammar.isGrammarTag[tag])
					continue;
				// List<Integer> possibleSt = possibleStates[start][end];
				// for (int tag : possibleSt){
				narrowRExtent[start][tag] = end;
				narrowLExtent[end][tag] = start;
				wideRExtent[start][tag] = end;
				wideLExtent[end][tag] = start;
				double[] lexiconScores = lexicon.score(word, tag, start, false,
						false);
				for (short n = 0; n < numSubStatesArray[tag]; n++) {
					double prob = lexiconScores[n];
					/*
					 * if (prob>0){ prob = -10;
					 * System.out.println("Should never happen! Log-Prob > 0!!!"
					 * );
					 * System.out.println("Word "+word+" Tag "+(String)tagNumberer
					 * .object(tag)+" prob "+prob); }
					 */
					iScore[start][end][tag][n] = prob;
					/*
					 * UnaryRule[] unaries =
					 * grammar.getClosedUnaryRulesByChild(state); for (int r =
					 * 0; r < unaries.length; r++) { UnaryRule ur = unaries[r];
					 * int parentState = ur.parent; double pS = (double)
					 * ur.score; double tot = prob + pS; if (tot >
					 * iScore[start][end][parentState]) {
					 * iScore[start][end][parentState] = tot;
					 * narrowRExtent[start][parentState] = end;
					 * narrowLExtent[end][parentState] = start;
					 * wideRExtent[start][parentState] = end;
					 * wideLExtent[end][parentState] = start; } }
					 */
				}
			}
			start++;
		}
	}

	public Tree<String> getBestConstrainedParse(List<String> sentence,
			List<String> posTags, boolean[][][][] allowedS) {// List<Integer>[][]
																// pStates) {
		return getBestConstrainedParse(sentence, posTags);
	}

	public Tree<String> getBestConstrainedParse(List<String> sentence,
			List<String> posTags) {// List<Integer>[][] pStates) {
		length = (short) sentence.size();
		// this.possibleStates = pStates;
		noConstrains = true;
		createArrays();
		initializeChart(sentence);
		doConstrainedInsideScores();
		// showScores(iScore, "Inside scores:");
		/*
		 * oScore[0][length][0][0] = 0; doConstrainedOutsideScores();
		 * 
		 * List<Integer> possibleParentSt = possibleStates[12][13]; for (int
		 * pState : possibleParentSt){ System.out.println(pState + " " +
		 * (String) tagNumberer.object(pState) + " iScore "+
		 * Arrays.toString(iScore[12][13][pState]) + " oScore "+
		 * Arrays.toString(oScore[12][13][pState])); }
		 */
		Tree<String> bestTree = new Tree<String>("ROOT");
		double score = iScore[0][length][0][0];
		if (score > Double.NEGATIVE_INFINITY) {
			// System.out.println("\nFound a parse for sentence with length "+length+". The LL is "+score+".");
			Tree<StateSet> bestStateSetTree = extractBestStateSetTree(zero,
					zero, zero, length, sentence);
			// tallyStatesAndRules(bestStateSetTree);
			bestTree = restoreStateSetTreeUnaries(bestStateSetTree);
			// bestTree = extractBestParse(0, 0, 0, length, sentence);
			// restoreUnaries(bestTree);
		} else {
			System.out
					.println("()\nDid NOT find a parse for sentence with length "
							+ length + ".");
		}

		return bestTree;
	}

	/**
	 * Fills in the iScore array of each category over each span of length 2 or
	 * more.
	 */

	void doConstrainedInsideScores() {
		grammar.logarithmMode();
		lexicon.logarithmMode();
		for (int diff = 1; diff <= length; diff++) {
			System.out.print(diff + " ");
			for (int start = 0; start < (length - diff + 1); start++) {
				int end = start + diff;
				List<Integer> possibleSt = null;
				if (noConstrains) {
					possibleSt = new ArrayList<Integer>();
					for (int i = 0; i < numStates; i++) {
						possibleSt.add(i);
					}
				} else {
					possibleSt = possibleStates[start][end];
				}
				for (int pState : possibleSt) {
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
						int nParentSubStates = numSubStatesArray[pState];
						for (int np = 0; np < nParentSubStates; np++) {
							double oldIScore = iScore[start][end][pState][np];
							double bestIScore = oldIScore;
							for (int split = min; split <= max; split++) {
								if (iScore[start][split][lState] == null)
									continue;
								if (iScore[split][end][rState] == null)
									continue;

								for (int lp = 0; lp < scores.length; lp++) {
									double lS = iScore[start][split][lState][lp];
									if (lS == Double.NEGATIVE_INFINITY)
										continue;

									for (int rp = 0; rp < scores[0].length; rp++) {
										nRules++;
										double pS = Double.NEGATIVE_INFINITY;
										if (scores[lp][rp] != null)
											pS = scores[lp][rp][np];
										if (pS == Double.NEGATIVE_INFINITY) {
											nRulesInf++;
											continue;
											// System.out.println("s "+start+" sp "+split+" e "+end+" pS "+pS+" rS "+rS);
										}

										double rS = iScore[split][end][rState][rp];
										if (rS == Double.NEGATIVE_INFINITY)
											continue;

										double tot = pS + lS + rS;
										if (tot >= bestIScore) {
											bestIScore = tot;
										}
									}
								}
							}
							if (bestIScore > oldIScore) { // this way of making
															// "parentState" is
															// better
								// than previous
								iScore[start][end][pState][np] = bestIScore;
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
					}
				}
				for (int pState : possibleSt) {// int pState=0; pState<0;
												// pState++){//
					// UnaryRule[] unaries =
					// grammar.getUnaryRulesByParent(pState).toArray(new
					// UnaryRule[0]);
					// it actually seems to be better to use the unaries without
					// the closure...
					// UnaryRule[] unaries = new UnaryRule[0];
					UnaryRule[] unaries = grammar
							.getClosedViterbiUnaryRulesByParent(pState);
					for (int r = 0; r < unaries.length; r++) {
						UnaryRule ur = unaries[r];
						int cState = ur.childState;
						if (iScore[start][end][cState] == null)
							continue;
						// if ((pState == cState)) continue;// && (np ==
						// cp))continue;
						// new loop over all substates
						double[][] scores = ur.getScores2();
						int nParentSubStates = numSubStatesArray[pState];
						for (int np = 0; np < nParentSubStates; np++) {
							double oldIScore = iScore[start][end][pState][np];
							double bestIScore = oldIScore;
							for (int cp = 0; cp < scores.length; cp++) {
								double pS = Double.NEGATIVE_INFINITY;
								if (scores[cp] != null)
									pS = scores[cp][np];
								nRules++;
								if (pS == Double.NEGATIVE_INFINITY) {
									nRulesInf++;
									continue;
								}
								double iS = iScore[start][end][cState][cp];
								if (iS == Double.NEGATIVE_INFINITY)
									continue;

								double tot = iS + pS;

								if (tot >= bestIScore) {
									bestIScore = tot;
								}
							}

							if (bestIScore > oldIScore) {
								iScore[start][end][pState][np] = bestIScore;
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
					}
				}
			}
		}
	}

	void doConstrainedOutsideScores() {
		grammar.logarithmMode();
		lexicon.logarithmMode();
		for (int diff = length; diff >= 1; diff--) {
			for (int start = 0; start + diff <= length; start++) {
				int end = start + diff;
				// do unaries
				// List<Integer> possibleParentSt = possibleStates[start][end];
				List<Integer> possibleParentSt = null;
				if (noConstrains) {
					possibleParentSt = new ArrayList<Integer>();
					for (int i = 0; i < numStates; i++) {
						possibleParentSt.add(i);
					}
				} else {
					possibleParentSt = possibleStates[start][end];
				}
				for (int pState : possibleParentSt) {
					// this check should be unnecessary. if we get a null
					// pointer
					// exception here, then we did not initialize the arrays
					// properly - slav
					// if (oScore[start][end][pState] == null) { continue; }

					UnaryRule[] rules = grammar
							.getClosedViterbiUnaryRulesByParent(pState);
					for (int r = 0; r < rules.length; r++) {
						UnaryRule ur = rules[r];
						int cState = ur.childState;
						if (oScore[start][end][cState] == null) {
							continue;
						}

						// new loop over all substates
						double[][] scores = ur.getScores2();
						for (int cp = 0; cp < scores.length; cp++) {
							double oldOScore = oScore[start][end][cState][cp];
							double bestOScore = oldOScore;

							double iS = iScore[start][end][cState][cp];
							if (iS == Double.NEGATIVE_INFINITY) {
								continue;
							}

							for (int np = 0; np < scores[0].length; np++) {
								double oS = oScore[start][end][pState][np];
								double pS = Double.NEGATIVE_INFINITY;
								if (scores[cp] != null)
									pS = scores[cp][np];
								double tot = oS + pS;

								if (tot > bestOScore) {
									bestOScore = tot;
								}
							}
							if (bestOScore > oldOScore) {
								oScore[start][end][cState][cp] = bestOScore;
							}
						}
					}
				}
				// do binaries
				// for (int lState = 0; lState < numStates; lState++) {
				for (int pState = 0; pState < numStates; pState++) {
					// BinaryRule[] rules = grammar.splitRulesWithLC(lState);
					BinaryRule[] rules = grammar.splitRulesWithP(pState);
					for (int r = 0; r < rules.length; r++) {
						BinaryRule br = rules[r];
						if (oScore[start][end][br.parentState] == null) {
							continue;
						}

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
						for (int split = min; split <= max; split++) {
							if (oScore[start][split][lState] == null)
								continue;
							if (oScore[split][end][rState] == null)
								continue;
							for (int lp = 0; lp < scores.length; lp++) {
								double lS = iScore[start][split][lState][lp];
								if (lS == Double.NEGATIVE_INFINITY) {
									continue;
								}
								for (int rp = 0; rp < scores[lp].length; rp++) {
									double rS = iScore[split][end][rState][rp];
									if (rS == Double.NEGATIVE_INFINITY) {
										continue;
									}
									if (scores[lp][rp] == null)
										continue;
									for (int np = 0; np < scores[lp][rp].length; np++) {
										double oS = oScore[start][end][br.parentState][np];
										double pS = scores[lp][rp][np];

										double totL = pS + rS + oS;
										if (totL > oScore[start][split][lState][lp]) {
											oScore[start][split][lState][lp] = totL;
										}
										double totR = pS + lS + oS;
										if (totR > oScore[split][end][rState][rp]) {
											oScore[split][end][rState][rp] = totR;
										}
									}
								}
							}
						}
					}
				}
				/*
				 * for (int rState = 0; rState < numStates; rState++) { int max1
				 * = narrowLExtent[end][rState]; if (max1 < start) { continue; }
				 * BinaryRule[] rules = grammar.splitRulesWithRC(rState); for
				 * (int r = 0; r < rules.length; r++) { BinaryRule br =
				 * rules[r];
				 * 
				 * if (oScore[start][end][br.parentState]==null) {continue;} int
				 * lState = br.leftChildState; int min1 =
				 * narrowRExtent[start][lState]; if (max1 < min1) { continue; }
				 * int min = min1; int max = max1; if (max - min > 2) { int min2
				 * = wideLExtent[end][rState]; min = (min1 > min2 ? min1 :
				 * min2); if (max1 < min) { continue; } int max2 =
				 * wideRExtent[start][lState]; max = (max1 < max2 ? max1 :
				 * max2); if (max < min) { continue; } }
				 * 
				 * double[][][] scores = br.getScores(); for (int split = min;
				 * split <= max; split++) { if (oScore[start][split][lState] ==
				 * null) continue; if (oScore[split][end][rState] == null)
				 * continue; for (int lp=0; lp<scores[0].length; lp++){ double
				 * lS = iScore[start][split][lState][lp]; if (lS ==
				 * Double.NEGATIVE_INFINITY) { continue; } for (int rp=0;
				 * rp<scores[0][0].length; rp++){ double rS =
				 * iScore[split][end][rState][rp]; if (rS ==
				 * Double.NEGATIVE_INFINITY) { continue; } for (int np=0;
				 * np<scores.length; np++){ double oS =
				 * oScore[start][end][br.parentState][np]; if (oS ==
				 * Double.NEGATIVE_INFINITY) { continue; } double pS =
				 * scores[np][lp][rp];
				 * 
				 * double totL = pS + rS + oS; if (totL >
				 * oScore[start][split][lState][lp]) {
				 * System.err.println("Shouldn't occur!"); System.exit(1);
				 * oScore[start][split][lState][lp] = totL; } double totR = pS +
				 * lS + oS; if (totR > oScore[split][end][rState][rp]) {
				 * System.err.println("Shouldn't occur!"); System.exit(1);
				 * oScore[split][end][rState][rp] = totR; } } } } } } }
				 */
			}
		}
	}

	public void showScores(double[][][][] scores, String title) {
		System.out.println(title);
		for (int diff = 1; diff <= length; diff++) {
			for (int start = 0; start < (length - diff + 1); start++) {
				int end = start + diff;
				System.out.print("[" + start + " " + end + "]: ");
				// List<Integer> possibleSt = possibleStates[start][end];
				List<Integer> possibleSt = null;
				if (noConstrains) {
					possibleSt = new ArrayList<Integer>();
					for (int i = 0; i < numStates; i++) {
						possibleSt.add(i);
					}
				} else {
					possibleSt = possibleStates[start][end];
				}
				for (int state : possibleSt) {
					if (scores[start][end][state] != null) {
						for (int s = 0; s < grammar.numSubStates[state]; s++) {
							Numberer n = grammar.tagNumberer;
							System.out.print("("
									+ StringUtils.escapeString(n.object(state)
											.toString(), new char[] { '\"' },
											'\\') + "[" + s + "] "
									+ scores[start][end][state][s] + ")");
						}
					}
				}
				System.out.println();
			}
		}
	}

	/**
	 * Return the single best parse. Note that the returned tree may be missing
	 * intermediate nodes in a unary chain because it parses with a unary-closed
	 * grammar.
	 */
	public Tree<String> extractBestParse(int gState, int gp, int start,
			int end, List<String> sentence) {
		// find sources of inside score
		// no backtraces so we can speed up the parsing for its primary use
		double bestScore = iScore[start][end][gState][gp];
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
						double ruleScore = iScore[start][end][cState][cp]
								+ scores[cp][gp];
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
							Tree<String> leftChildTree = extractBestParse(
									lState, lp, start, split, sentence);
							Tree<String> rightChildTree = extractBestParse(
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

			if (iScore[start][end][cState] == null)
				continue;

			// new: iterate over substates
			double[][] scores = ur.getScores2();
			for (int cp = 0; cp < scores.length; cp++) {
				if (scores[cp] == null)
					continue;
				double score = scores[cp][gp] + iScore[start][end][cState][cp];
				if ((cState != ur.parentState || cp != gp)
						&& matches(score, bestScore)) {
					// build unary
					Tree<String> childTree = extractBestParse(cState, cp,
							start, end, sentence);
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

	/**
	 * Return the single best parse. Note that the returned tree may be missing
	 * intermediate nodes in a unary chain because it parses with a unary-closed
	 * grammar. A StateSet tree is returned, but the subState array is used in a
	 * different way: it has only one entry, whose value is the substate! -
	 * dirty hack...
	 */
	public Tree<StateSet> extractBestStateSetTree(short gState, short gp,
			short start, short end, List<String> sentence) {
		// find sources of inside score
		// no backtraces so we can speed up the parsing for its primary use
		double bestScore = iScore[start][end][gState][gp];
		// Numberer tagNumberer = Numberer.getGlobalNumberer("tags");
		// System.out.println("Looking for "+(String)tagNumberer.object(gState)+" from "+start+" to "+end+" with score "+
		// bestScore+".");
		if (end - start == 1) {
			// if the goal state is a preterminal state, then it can't transform
			// into
			// anything but the word below it
			if (!grammar.isGrammarTag(gState)) {
				List<Tree<StateSet>> child = new ArrayList<Tree<StateSet>>();
				StateSet node = new StateSet(zero, zero, sentence.get(start),
						start, end);
				child.add(new Tree<StateSet>(node));
				StateSet root = new StateSet(gState, one, null, start, end);
				root.allocate();
				root.setIScore(0, gp);
				return new Tree<StateSet>(root, child);
			}
			// if the goal state is not a preterminal state, then find a way to
			// transform it into one
			else {
				double veryBestScore = Double.NEGATIVE_INFINITY;
				short newIndex = -1;
				short newSubstate = -1;
				UnaryRule[] unaries = grammar
						.getClosedViterbiUnaryRulesByParent(gState);
				for (int r = 0; r < unaries.length; r++) {
					UnaryRule ur = unaries[r];
					short cState = ur.childState;
					double[][] scores = ur.getScores2();
					for (short cp = 0; cp < scores.length; cp++) {
						if (scores[cp] == null)
							continue;
						if (iScore[start][end][cState] == null)
							continue;
						double ruleScore = iScore[start][end][cState][cp]
								+ scores[cp][gp];
						if ((ruleScore >= veryBestScore)
								&& (gState != cState || gp != cp)
								&& !grammar.isGrammarTag(cState)) {
							veryBestScore = ruleScore;
							newIndex = cState;
							newSubstate = cp;
						}
					}
				}
				List<Tree<StateSet>> child1 = new ArrayList<Tree<StateSet>>();
				StateSet node1 = new StateSet(zero, zero, sentence.get(start),
						start, end);
				child1.add(new Tree<StateSet>(node1));
				if (newIndex == -1)
					System.out.println("goalStr1==null with newIndex=="
							+ newIndex + " goalState==" + gState);
				List<Tree<StateSet>> child = new ArrayList<Tree<StateSet>>();
				StateSet node = new StateSet(newIndex, one, null, start, end);
				node.allocate();
				node.setIScore(0, newSubstate);
				child.add(new Tree<StateSet>(node, child1));
				StateSet root = new StateSet(gState, one, null, start, end);
				root.allocate();
				root.setIScore(0, gp);
				// totalUsedUnaries++;
				return new Tree<StateSet>(root, child);
			}
		}
		// check binaries first
		double bestBScore = Double.NEGATIVE_INFINITY;
		// BinaryRule bestBRule = null;
		// short bestBLp, bestBRp;
		// TODO: fix parsing
		for (int split = start + 1; split < end; split++) {
			BinaryRule[] parentRules = grammar.splitRulesWithP(gState);
			for (short i = 0; i < parentRules.length; i++) {
				BinaryRule br = parentRules[i];

				short lState = br.leftChildState;
				if (iScore[start][split][lState] == null)
					continue;

				short rState = br.rightChildState;
				if (iScore[split][end][rState] == null)
					continue;

				// new: iterate over substates
				double[][][] scores = br.getScores2();
				for (short lp = 0; lp < scores.length; lp++) {
					for (short rp = 0; rp < scores[lp].length; rp++) {
						if (scores[lp][rp] == null)
							continue;
						double score = scores[lp][rp][gp]
								+ iScore[start][split][lState][lp]
								+ iScore[split][end][rState][rp];
						if (score > bestBScore)
							bestBScore = score;
						if (matches(score, bestScore)) {
							// build binary split
							Tree<StateSet> leftChildTree = extractBestStateSetTree(
									lState, lp, start, (short) split, sentence);
							Tree<StateSet> rightChildTree = extractBestStateSetTree(
									rState, rp, (short) split, end, sentence);
							List<Tree<StateSet>> children = new ArrayList<Tree<StateSet>>();
							children.add(leftChildTree);
							children.add(rightChildTree);
							StateSet root = new StateSet(gState, one, null,
									start, end);
							root.allocate();
							root.setIScore(0, gp);
							Tree<StateSet> result = new Tree<StateSet>(root,
									children);
							// System.out.println("Binary node: "+result);
							// result.setScore(score);
							return result;
						}
					}
				}
			}
		}
		double bestUScore = Double.NEGATIVE_INFINITY;
		// check unaries
		UnaryRule[] unaries = grammar
				.getClosedViterbiUnaryRulesByParent(gState);
		for (short r = 0; r < unaries.length; r++) {
			UnaryRule ur = unaries[r];
			short cState = ur.childState;

			if (iScore[start][end][cState] == null)
				continue;

			// new: iterate over substates
			double[][] scores = ur.getScores2();
			for (short cp = 0; cp < scores.length; cp++) {
				if (scores[cp] == null)
					continue;
				double rScore = scores[cp][gp];
				double score = rScore + iScore[start][end][cState][cp];
				if (score > bestUScore)
					bestUScore = score;
				if ((cState != ur.parentState || cp != gp)
						&& matches(score, bestScore)) {
					// build unary
					Tree<StateSet> childTree = extractBestStateSetTree(cState,
							cp, start, end, sentence);
					List<Tree<StateSet>> children = new ArrayList<Tree<StateSet>>();
					children.add(childTree);
					StateSet root = new StateSet(gState, one, null, start, end);
					root.allocate();
					root.setIScore(0, gp);
					Tree<StateSet> result = new Tree<StateSet>(root, children);
					// System.out.println("Unary node: "+result);
					// result.setScore(score);
					totalUsedUnaries++;
					return result;
				}
			}
		}
		System.err
				.println("Warning: could not find the optimal way to build state "
						+ gState
						+ " spanning from "
						+ start
						+ " to "
						+ end
						+ ".");
		System.err.println("The goal score was " + bestScore
				+ ", but the best we found was a binary rule giving "
				+ bestBScore + " and a unary rule giving " + bestUScore);
		showScores(iScore, "iScores");
		return null;
	}

	// the state set tree has nodes that are labeled with substate information
	// the substate information is the first element in the iscore array
	protected Tree<String> restoreStateSetTreeUnaries(Tree<StateSet> t) {
		// System.out.println("In restoreUnaries...");
		// System.out.println("Doing node: "+node.getLabel());

		if (t.isLeaf()) { // shouldn't happen
			System.err.println("Tried to restore unary from a leaf...");
			return null;
		} else if (t.isPreTerminal()) { // preterminal unaries have already been
										// restored
			List<Tree<String>> child = new ArrayList<Tree<String>>();
			child.add(new Tree<String>(t.getChildren().get(0).getLabel()
					.getWord()));
			return new Tree<String>((String) tagNumberer.object(t.getLabel()
					.getState()), child);
		} else if (t.getChildren().size() != 1) { // nothing to restore
			// build binary split
			Tree<String> leftChildTree = restoreStateSetTreeUnaries(t
					.getChildren().get(0));
			Tree<String> rightChildTree = restoreStateSetTreeUnaries(t
					.getChildren().get(1));
			List<Tree<String>> children = new ArrayList<Tree<String>>();
			children.add(leftChildTree);
			children.add(rightChildTree);
			return new Tree<String>((String) tagNumberer.object(t.getLabel()
					.getState()), children);
		} // the interesting part:
			// System.out.println("Not skipping node: "+node.getLabel());
		StateSet parent = t.getLabel();
		StateSet child = t.getChildren().get(0).getLabel();
		short pLabel = parent.getState();
		short pSubState = (short) parent.getIScore(0); // dirty hack
		short cLabel = child.getState();
		short cSubState = (short) child.getIScore(0);

		// System.out.println("P: "+(String)tagNumberer.object(pLabel)+" C: "+(String)tagNumberer.object(cLabel));
		List<Tree<String>> goodChild = new ArrayList<Tree<String>>();
		goodChild.add(restoreStateSetTreeUnaries(t.getChildren().get(0)));
		// do we need a check here? if we can check whether the rule was
		// in the original grammar, then we wouldnt need the getBestPath call.
		// but getBestPath should be able to take care of that...
		// if (grammar.getUnaryScore(new UnaryRule(pLabel,cLabel))[0][0] != 0){
		// continue; }// means the rule was already in grammar

		// System.out.println("Got path: "+path);
		// if (path.size()==1) return goodChild;
		Tree<String> result = new Tree<String>(
				(String) tagNumberer.object(pLabel), goodChild);
		Tree<String> working = result;
		// List<short[]> path = grammar.getBestViterbiPath(pLabel,pSubState,
		// cLabel,cSubState);
		// if (path.size()>2) {
		// nTimesRestoredUnaries++;
		// }
		// for (int pos=1; pos < path.size() - 1; pos++) {
		// int interState = path.get(pos)[0];
		// Tree<String> intermediate = new Tree<String>((String)
		// tagNumberer.object(interState), working.getChildren());
		// List<Tree<String>> children = new ArrayList<Tree<String>>();
		// children.add(intermediate);
		// working.setChildren(children);
		// working = intermediate;
		// }
		return working;
	}

	public double[][][][] getInsideScores() {
		return ArrayUtil.clone(iScore);
	}

	public double[][][][] getOutsideScores() {
		return ArrayUtil.clone(oScore);
	}

	public void printUnaryStats() {
		System.out.println(" Used a total of " + totalUsedUnaries
				+ " unary productions.");
		System.out.println(" restored unaries " + nTimesRestoredUnaries);
		System.out.println(" Out of " + nRules + " rules " + nRulesInf
				+ " had probability=-Inf.");
	}

	public void projectConstraints(boolean[][][][] allowed,
			boolean allSubstatesAllowed) {
		System.err
				.println("Not supported!\nThis parser cannot project constraints!");
	}

	/**
	 * @return the numSubStatesArray
	 */
	public short[] getNumSubStatesArray() {
		return numSubStatesArray;
	}

}
