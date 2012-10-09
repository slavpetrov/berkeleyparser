/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;

import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.Numberer;
import edu.berkeley.nlp.util.ScalingTools;

public class PosteriorMerger {

	public static class Options {

		@Option(name = "-grammarFiles", required = true, usage = "Input Files for Grammars.")
		public String grammarFiles;

		@Option(name = "-inputFile", usage = "Read input from this file instead of reading it from STDIN.")
		public String inputFile;

		@Option(name = "-outputFile", usage = "Store output in this file instead of printing it to STDOUT.")
		public String outputFile;

		@Option(name = "-nGrammars", usage = "Number of Grammars")
		public int nGrammars;

		@Option(name = "-maxLength", usage = "Maximum sentence length (Default = 200).")
		public int maxLength = 200;
	}

	static double[][][] maxcScore; // start, end, state --> logProb
	static int[][][] maxcSplit; // start, end, state -> split position
	static int[][][] maxcChild; // start, end, state -> unary child (if any)
	static int[][][] maxcLeftChild; // start, end, state -> left child
	static int[][][] maxcRightChild; // start, end, state -> right child

	public static void main(String[] args) {
		OptionParser optParser = new OptionParser(Options.class);
		Options opts = (Options) optParser.parse(args, true);
		// provide feedback on command-line arguments
		System.err.println("Calling with " + optParser.getPassedInOptions());

		String inFileName = opts.grammarFiles;
		if (inFileName == null) {
			throw new Error("Did not provide a grammar.");
		}

		short[][] numSubstates = new short[opts.nGrammars][];
		Grammar[] grammars = new Grammar[opts.nGrammars];
		Lexicon[] lexicons = new Lexicon[opts.nGrammars];
		for (int gr = 0; gr < opts.nGrammars; gr++) {
			System.err.println("Loading grammar from " + inFileName + "."
					+ (gr + 1));
			ParserData pData = ParserData.Load(inFileName + "." + (gr + 1));
			if (pData == null) {
				System.out.println("Failed to load grammar from file"
						+ inFileName + ".");
				System.exit(1);
			}
			numSubstates[gr] = pData.getGrammar().numSubStates;
			Numberer.setNumberers(pData.getNumbs());
			grammars[gr] = pData.getGrammar();
			lexicons[gr] = pData.getLexicon();
		}
		int nGrammars = numSubstates.length;

		CoarseToFineMaxRuleParser parser = new CoarseToFineMaxRuleParser(
				grammars[0], lexicons[0], 1.0, -1, false, false, false, true,
				false, false, false);

		try {
			BufferedReader inputData = (opts.inputFile == null) ? new BufferedReader(
					new InputStreamReader(System.in)) : new BufferedReader(
					new InputStreamReader(new FileInputStream(opts.inputFile),
							"UTF-8"));
			PrintWriter outputData = (opts.outputFile == null) ? new PrintWriter(
					new OutputStreamWriter(System.out)) : new PrintWriter(
					new OutputStreamWriter(
							new FileOutputStream(opts.outputFile), "UTF-8"),
					true);

			String line = "";
			int blockIndex = 0;
			int lineIndex = 0;
			List<Posterior>[] posteriors = null;
			while ((line = inputData.readLine()) != null) {
				List<String> sentence = Arrays.asList(line.split(" "));
				if (posteriors == null || lineIndex == posteriors[0].size()) {
					posteriors = new ArrayList[nGrammars];
					for (int gr = 0; gr < nGrammars; gr++) {
						String fileName = opts.grammarFiles + "." + (gr + 1)
								+ ".posteriors." + blockIndex;
						posteriors[gr] = loadPosteriors(fileName);
					}
					lineIndex = 0;
					blockIndex++;
				}
				int length = sentence.size();
				if (length > opts.maxLength) {
					// lineIndex++;
					outputData.write("(())\n");
					continue;
				}

				List<double[][][][]> iScores = new ArrayList<double[][][][]>(
						nGrammars);
				List<double[][][][]> oScores = new ArrayList<double[][][][]>(
						nGrammars);
				List<int[][][]> iScales = new ArrayList<int[][][]>(nGrammars);
				List<int[][][]> oScales = new ArrayList<int[][][]>(nGrammars);
				boolean[][][] allowedStates = null;

				boolean skip = false;
				for (int gr = 0; gr < nGrammars; gr++) {
					Posterior posterior = posteriors[gr].get(lineIndex);
					iScores.add(posterior.iScore);
					oScores.add(posterior.oScore);
					iScales.add(posterior.iScale);
					oScales.add(posterior.oScale);
					allowedStates = mergeAllowedStates(allowedStates,
							posterior.allowedStates);
					countAllowedStates(allowedStates);
					if (posterior.iScale != null) {
						skip = true;
						System.err.println("Scaling will be used.");
						if (length != posterior.iScale.length) {
							System.err.println("G: " + gr + " sentence "
									+ lineIndex
									+ " Length mismatch. Expected: " + length
									+ " Got: " + posterior.iScale.length);
						}
					}
				}
				lineIndex++;
				if (skip == true) {
					outputData.write("(()) \n");
					continue;
				}
				doCombinedMaxCScores(sentence, iScores, oScores, iScales,
						oScales, allowedStates, grammars, lexicons,
						numSubstates, iScales.get(0) != null);
				System.err.println("Done with scores");
				if (maxcScore[0][sentence.size()][0] == Double.NEGATIVE_INFINITY) {
					System.err.println("MaxCscore for ROOT is -Inf.");
					outputData.write("(()) \n");
					continue;
				}
				parser.maxcScore = maxcScore;
				parser.maxcChild = maxcChild;
				parser.maxcLeftChild = maxcLeftChild;
				parser.maxcRightChild = maxcRightChild;
				parser.maxcSplit = maxcSplit;
				parser.allowedStates = allowedStates;
				Tree<String> parsedTree = parser.extractBestMaxRuleParse(0,
						sentence.size(), sentence);
				parsedTree = TreeAnnotations.unAnnotateTree(parsedTree, false);
				outputData.write(parsedTree + "\n");
				outputData.flush();
			}

			outputData.flush();
			outputData.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		System.exit(0);
	}

	private static boolean[][][] mergeAllowedStates(
			boolean[][][] allowedStates, boolean[][][] allowedStates2) {
		if (allowedStates == null)
			return allowedStates2;
		for (int i = 0; i < allowedStates.length; i++) {
			for (int j = i + 1; j < allowedStates[i].length; j++) {
				for (int k = 0; k < allowedStates[i][j].length; k++) {
					if (!allowedStates2[i][j][k] && allowedStates[i][j][k])
						allowedStates[i][j][k] = false;
				}
			}
		}
		return allowedStates;
	}

	private static void countAllowedStates(boolean[][][] allowedStates) {
		int total = 0;
		int allowed = 0;
		for (int i = 0; i < allowedStates.length; i++) {
			for (int j = i + 1; j < allowedStates[i].length; j++) {
				for (int k = 0; k < allowedStates[i][j].length; k++) {
					if (allowedStates[i][j][k])
						allowed++;
					total++;
				}
			}
		}
		System.err.println(allowed + "/" + total
				+ " allowed for sentence of length " + allowedStates.length);
	}

	static void doCombinedMaxCScores(List<String> sentence,
			List<double[][][][]> iScores, List<double[][][][]> oScores,
			List<int[][][]> iScales, List<int[][][]> oScales,
			boolean[][][] allowedStates, Grammar[] grammars,
			Lexicon[] lexicons, short[][] numSubstates, boolean scale) {

		int length = sentence.size();
		int nGrammars = numSubstates.length;
		int numStates = numSubstates[0].length;
		boolean[] grammarTags = grammars[0].isGrammarTag;
		Numberer tagNumberer = Numberer.getGlobalNumberer("tags");

		maxcScore = new double[length][length + 1][numStates];
		maxcSplit = new int[length][length + 1][numStates];
		maxcChild = new int[length][length + 1][numStates];
		maxcLeftChild = new int[length][length + 1][numStates];
		maxcRightChild = new int[length][length + 1][numStates];
		ArrayUtil.fill(maxcScore, Double.NEGATIVE_INFINITY);

		double[] logNormalizer = new double[nGrammars];
		for (int i = 0; i < nGrammars; i++) {
			logNormalizer[i] = iScores.get(i)[0][length][0][0];
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
										scalingFactor += oScales.get(gr)[start][end][pState]
												+ iScales.get(gr)[start][split][lState]
												+ iScales.get(gr)[split][end][rState]
												- iScales.get(gr)[0][length][0];

									}
									// System.err.println(scalingFactor);
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
									int nParentStates = numSubstates[gr][pState]; // ==
																					// scores[0][0].length;
									int nLeftChildStates = numSubstates[gr][lState]; // ==
																						// scores.length;
									int nRightChildStates = numSubstates[gr][rState]; // ==
																						// scores[0].length;

									for (int lp = 0; lp < nLeftChildStates; lp++) {
										double lIS = iScores.get(gr)[start][split][lState][lp];
										if (lIS == 0)
											continue;

										for (int rp = 0; rp < nRightChildStates; rp++) {
											if (scores[lp][rp] == null)
												continue;
											double rIS = iScores.get(gr)[split][end][rState][rp];
											if (rIS == 0)
												continue;
											for (int np = 0; np < nParentStates; np++) {
												double pOS = oScores.get(gr)[start][end][pState][np];
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
							double ruleScore = 0;
							double[] lexiconScoreArray = lexicons[gr].score(
									word, (short) tag, start, false, false);
							for (int tp = 0; tp < numSubstates[gr][tag]; tp++) {
								double pOS = oScores.get(gr)[start][end][tag][tp];
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
						if (length != iScores.get(0).length) {
							System.err
									.println("Length mismatch. Expected: "
											+ length + " Got: "
											+ iScores.get(0).length);
							System.err.println(sentence);
						}

						double scalingFactor = 0.0;
						if (scale) {
							for (int gr = 0; gr < nGrammars; gr++) {
								try {
									scalingFactor += oScales.get(gr)[start][end][tag]
											- iScales.get(gr)[0][length][0];
								} catch (java.lang.ArrayIndexOutOfBoundsException e) {
									System.err.println("Start " + start);
									System.err.println("End " + end);
									System.err.println("Length " + length);
									System.err.println("Tag " + tag);
									System.err.println("Grammar " + gr);
									int[][][] oS = oScales.get(gr);
									System.err.println("oS.l " + oS.length);
									System.err.println("oS[].l "
											+ oS[start].length);
									System.err.println("oS[][].l "
											+ oS[start][end].length);
									int[][][] iS = iScales.get(gr);
									System.err.println("iS.l " + iS.length);
									System.err.println("iS[].l "
											+ iS[start].length);
									System.err.println("iS[][].l "
											+ iS[start][end].length);
									double[][][][] isS = iScores.get(gr);
									System.err.println("iS.l " + isS.length);
									System.err.println("iS[].l "
											+ isS[start].length);
									System.err.println("iS[][].l "
											+ isS[start][end].length);
									System.err
											.println("Length mismatch. Expected: "
													+ length
													+ " Got: "
													+ iScales.get(gr).length);
									System.err.println(sentence);

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
								scalingFactor += oScales.get(gr)[start][end][pState]
										+ iScales.get(gr)[start][end][cState]
										- iScales.get(gr)[0][length][0];
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

							int nChildStates = numSubstates[gr][cState]; // ==
																			// scores.length;
							int nParentStates = numSubstates[gr][pState]; // ==
																			// scores[0].length;

							for (int cp = 0; cp < nChildStates; cp++) {
								double cIS = iScores.get(gr)[start][end][cState][cp];
								if (cIS == 0)
									continue;

								if (scores[cp] == null)
									continue;
								for (int np = 0; np < nParentStates; np++) {
									double pOS = oScores.get(gr)[start][end][pState][np];
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

	public static List<Posterior> loadPosteriors(String fileName) {
		List<Posterior> posteriors = null;
		try {
			FileInputStream fis = new FileInputStream(fileName); // Load from
																	// file
			GZIPInputStream gzis = new GZIPInputStream(fis); // Compressed
			ObjectInputStream in = new ObjectInputStream(gzis); // Load objects
			posteriors = (List<Posterior>) in.readObject(); // Read the mix of
															// grammars
			in.close(); // And close the stream.
			gzis.close();
			fis.close();
		} catch (IOException e) {
			System.out.println("IOException\n" + e);
			return null;
		} catch (ClassNotFoundException e) {
			System.out.println("Class not found!");
			return null;
		}
		return posteriors;
	}

}
