package edu.berkeley.nlp.PCFGLA;

import java.text.NumberFormat;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import edu.berkeley.nlp.PCFGLA.Corpus.TreeBankType;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees;
import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.CommandLineUtils;
import edu.berkeley.nlp.util.Numberer;
import edu.berkeley.nlp.util.PriorityQueue;

public class GrammarMerger {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length < 1) {
			System.out
					.println("usage: java GrammarMerger \n"
							+ "\t\t  -i       Input File for Grammar (Required)\n"
							+ "\t\t  -o       Output File for Merged Grammar (Required)\n"
							+ "\t\t  -p       Merging percentage (Default: 0.5)\n"
							+ "\t\t  -2p      Merging percentage for non-siblings (Default: 0.0)\n"
							+ "\t\t  -top     Keep top N substates, overrides -p!"
							+ "               -path  Path to Corpus (Default: null)\n"
							+
							// "               -lang  Language:  1-ENG, 2-CHN, 3-GER, 4-ARB (Default: 1)\n"
							// +
							"\t\t  -chsh    If this is enabled, then we train on a short segment of\n"
							+ "\t\t           the Chinese treebank (Default: false)"
							+ "\t\t  -trfr    The fraction of the training corpus to keep (Default: 1.0)\n"
							+ "\t\t  -maxIt   Maximum number of EM iterations (Default: 100)"
							+ "\t\t  -minIt   Minimum number of EM iterations (Default: 5)"
							+ "\t\t	 -f		    Filter rules with prob under f (Default: -1)"
							+ "\t\t  -dL      Delete labels? (true/false) (Default: false)"
							+ "\t\t  -ent 	  Use Entropic prior (Default: false)"
							+ "\t\t  -maxL 	  Maximum sentence length (Default: 10000)"
							+ "\t\t	 -sep	    Set merging threshold for grammar and lexicon separately (Default: false)"

					);
			System.exit(2);
		}
		// provide feedback on command-line arguments
		System.out.print("Running with arguments:  ");
		for (String arg : args) {
			System.out.print(" '" + arg + "'");
		}
		System.out.println("");

		// parse the input arguments
		Map<String, String> input = CommandLineUtils
				.simpleCommandLineParser(args);

		double mergingPercentage = Double.parseDouble(CommandLineUtils
				.getValueOrUseDefault(input, "-p", "0.5"));
		double mergingPercentage2 = Double.parseDouble(CommandLineUtils
				.getValueOrUseDefault(input, "-2p", "0.0"));
		String outFileName = CommandLineUtils.getValueOrUseDefault(input, "-o",
				null);
		String inFileName = CommandLineUtils.getValueOrUseDefault(input, "-i",
				null);
		System.out.println("Loading grammar from " + inFileName + ".");

		ParserData pData = ParserData.Load(inFileName);
		if (pData == null) {
			System.out.println("Failed to load grammar from file" + inFileName
					+ ".");
			System.exit(1);
		}
		int minIterations = Integer.parseInt(CommandLineUtils
				.getValueOrUseDefault(input, "-minIt", "0"));
		if (minIterations > 0)
			System.out.println("I will do at least " + minIterations
					+ " iterations.");

		boolean separateMerge = CommandLineUtils.getValueOrUseDefault(input,
				"-sep", "").equals("true");

		int maxIterations = Integer.parseInt(CommandLineUtils
				.getValueOrUseDefault(input, "-maxIt", "100"));
		if (maxIterations > 0)
			System.out.println("But at most " + maxIterations + " iterations.");
		boolean deleteLabels = CommandLineUtils.getValueOrUseDefault(input,
				"-dL", "").equals("true");

		boolean useEntropicPrior = CommandLineUtils.getValueOrUseDefault(input,
				"-ent", "").equals("true");

		int maxSentenceLength = Integer.parseInt(CommandLineUtils
				.getValueOrUseDefault(input, "-maxL", "10000"));
		System.out.println("Will remove sentences with more than "
				+ maxSentenceLength + " words.");

		String path = CommandLineUtils.getValueOrUseDefault(input, "-path",
				null);
		// int lang =
		// Integer.parseInt(CommandLineUtils.getValueOrUseDefault(input,
		// "-lang", "1"));
		// System.out.println("Loading trees from "+path+" and using language "+lang);

		boolean chineseShort = Boolean.parseBoolean(CommandLineUtils
				.getValueOrUseDefault(input, "-chsh", "false"));

		double trainingFractionToKeep = Double.parseDouble(CommandLineUtils
				.getValueOrUseDefault(input, "-trfr", "1.0"));

		Grammar grammar = pData.getGrammar();
		Lexicon lexicon = pData.getLexicon();
		Numberer.setNumberers(pData.getNumbs());
		int h_markov = pData.h_markov;
		int v_markov = pData.v_markov;
		Binarization bin = pData.bin;
		short[] numSubStatesArray = pData.numSubStatesArray;
		Numberer tagNumberer = Numberer.getGlobalNumberer("tags");

		double filter = Double.parseDouble(CommandLineUtils
				.getValueOrUseDefault(input, "-f", "-1"));
		if (filter > 0)
			System.out.println("Will remove rules with prob under " + filter);

		Corpus corpus = new Corpus(path, TreeBankType.WSJ,
				trainingFractionToKeep, false);
		// int nTrees = corpus.getTrainTrees().size();
		// binarize trees
		List<Tree<String>> trainTrees = Corpus.binarizeAndFilterTrees(
				corpus.getTrainTrees(), v_markov, h_markov, maxSentenceLength,
				bin, false, false);
		List<Tree<String>> validationTrees = Corpus.binarizeAndFilterTrees(
				corpus.getValidationTrees(), v_markov, h_markov,
				maxSentenceLength, bin, false, false);

		int nTrees = trainTrees.size();
		System.out.println("There are " + nTrees
				+ " trees in the training set.");

		StateSetTreeList trainStateSetTrees = new StateSetTreeList(trainTrees,
				numSubStatesArray, false, tagNumberer);
		StateSetTreeList validationStateSetTrees = new StateSetTreeList(
				validationTrees, numSubStatesArray, false, tagNumberer);

		// get rid of the old trees
		// trainTrees = null;
		// validationTrees = null;
		// corpus = null;
		// System.gc();

		// System.out.println("before merging, we have split trees:");
		// for (int i=0; i<grammar.numStates; i++) {
		// System.out.println(grammar.splitTrees[i]);
		// }
		//

		double[][] mergeWeights = computeMergeWeights(grammar, lexicon,
				trainStateSetTrees);

		double[][][] deltas = computeDeltas(grammar, lexicon, mergeWeights,
				trainStateSetTrees);

		boolean[][][] mergeThesePairs = determineMergePairs(deltas,
				separateMerge, mergingPercentage, grammar);

		Grammar newGrammar = doTheMerges(grammar, lexicon, mergeThesePairs,
				mergeWeights);

		printMergingStatistics(grammar, newGrammar);

		short[] newNumSubStatesArray = newGrammar.numSubStates;
		trainStateSetTrees = new StateSetTreeList(trainTrees,
				newNumSubStatesArray, false, tagNumberer);
		validationStateSetTrees = new StateSetTreeList(validationTrees,
				newNumSubStatesArray, false, tagNumberer);

		// retrain lexicon to finish the lexicon merge (updates the unknown
		// words model)...
		System.out.println("completing lexicon merge");
		ArrayParser newParser = new ArrayParser(newGrammar, lexicon);
		SophisticatedLexicon newLexicon = new SophisticatedLexicon(
				newNumSubStatesArray,
				SophisticatedLexicon.DEFAULT_SMOOTHING_CUTOFF,
				lexicon.getSmoothingParams(), lexicon.getSmoother(), filter);
		boolean updateOnlyLexicon = true;
		double trainingLikelihood = GrammarTrainer.doOneEStep(newGrammar,
				lexicon, null, newLexicon, trainStateSetTrees,
				updateOnlyLexicon, 4 /* opts.rare */);

		// int n = 0;
		// for (Tree<StateSet> stateSetTree : trainStateSetTrees) {
		// boolean secondHalf = (n++>nTrees/2.0);
		// newParser.doInsideOutsideScores(stateSetTree,noSmoothing,debugOutput);
		// // E Step
		// double ll = stateSetTree.getLabel().getIScore(0);
		// ll = Math.log(ll) +
		// (100*stateSetTree.getLabel().getIScale());//System.out.println(stateSetTree);
		// if (Double.isInfinite(ll)||Double.isNaN(ll)) {
		// System.out.println("Training sentence "+n+" is given "+ll+" log likelihood!");
		// GrammarTrainer.printBadLLReason(stateSetTree,lexicon);
		// }
		// else {
		// //System.out.println("Training sentence "+n+" is good.");
		// trainingLikelihood += ll;
		// newLexicon.trainTree(stateSetTree, -1, lexicon, secondHalf,true);
		// }
		// }
		System.out.println("The training LL is " + trainingLikelihood);
		newLexicon.optimize();// Grammar.RandomInitializationType.INITIALIZE_WITH_SMALL_RANDOMIZATION);
								// // M Step

		// do 5 iterations of EM to clean things up
		SophisticatedLexicon previousLexicon = null;
		Grammar previousGrammar = null;
		System.out.println("Doing some iterations of EM to clean things up...");
		double maxLikelihood = Double.NEGATIVE_INFINITY;
		int droppingIter = 0;
		int iter = 0;
		while ((droppingIter < 2) && (iter < maxIterations)) {
			iter++;
			previousLexicon = newLexicon;
			previousGrammar = newGrammar;
			boolean noSmoothing = false, debugOutput = false;
			newParser = new ArrayParser(previousGrammar, previousLexicon);

			newLexicon = new SophisticatedLexicon(newNumSubStatesArray,
					SophisticatedLexicon.DEFAULT_SMOOTHING_CUTOFF,
					lexicon.getSmoothingParams(), lexicon.getSmoother(), filter);
			newGrammar = new Grammar(newNumSubStatesArray,
					grammar.findClosedPaths, grammar.smoother, grammar, filter);
			if (useEntropicPrior)
				grammar.useEntropicPrior = true;
			int n = 0;
			trainingLikelihood = 0;
			for (Tree<StateSet> stateSetTree : trainStateSetTrees) {
				boolean secondHalf = (n++ > nTrees / 2.0);
				newParser.doInsideOutsideScores(stateSetTree, noSmoothing,
						debugOutput); // E Step
				double ll = stateSetTree.getLabel().getIScore(0);
				ll = Math.log(ll) + (100 * stateSetTree.getLabel().getIScale());// System.out.println(stateSetTree);
				if (Double.isInfinite(ll) || Double.isNaN(ll)) {
					System.out.println("Training sentence " + n + " is given "
							+ ll + " log likelihood!");
					GrammarTrainer.printBadLLReason(stateSetTree,
							previousLexicon);
				} else {
					trainingLikelihood += ll;
					newGrammar.tallyStateSetTree(stateSetTree, previousGrammar); // E
																					// Step
					newLexicon.trainTree(stateSetTree, -1, previousLexicon,
							secondHalf, false, 4 /* opts.rare */);
				}
			}
			System.out.println("The training LL is " + trainingLikelihood);

			newLexicon.optimize();// Grammar.RandomInitializationType.INITIALIZE_WITH_SMALL_RANDOMIZATION);
									// // M Step
			newGrammar.optimize(0);// Grammar.RandomInitializationType.INITIALIZE_WITH_SMALL_RANDOMIZATION);
									// // M Step
			newParser = new ArrayParser(newGrammar, newLexicon);

			// System.out.println("Evaluating new grammar");
			double validationLikelihood = 0;
			n = 0;
			for (Tree<StateSet> stateSetTree : validationStateSetTrees) {
				n++;
				newParser.doInsideScores(stateSetTree, false, false, null); // E
																			// Step
				double ll = stateSetTree.getLabel().getIScore(0);
				ll = Math.log(ll) + (100 * stateSetTree.getLabel().getIScale());// System.out.println(stateSetTree);
				if (Double.isInfinite(ll) || Double.isNaN(ll)) {
					System.out.println("Validation sentence " + n
							+ " is given -inf log likelihood!");
				} else
					validationLikelihood += ll; // there are for some reason
												// some sentences that are
												// unparsable
			}
			System.out.println("The validation LL after merging and "
					+ (iter + 1) + " iterations is " + validationLikelihood);
			if (iter < minIterations) {
				maxLikelihood = Math.max(validationLikelihood, maxLikelihood);
				grammar = newGrammar;
				lexicon = newLexicon;
				droppingIter = 0;
			} else if (validationLikelihood > maxLikelihood) {
				maxLikelihood = validationLikelihood;
				grammar = newGrammar;
				lexicon = newLexicon;
				droppingIter = 0;
			} else {
				droppingIter++;
			}

			if (iter > 0 && iter % 5 == 0) {
				pData = new ParserData(newLexicon, newGrammar, null,
						Numberer.getNumberers(), newNumSubStatesArray,
						v_markov, h_markov, bin);
				System.out.println("Saving grammar to " + outFileName + "-it-"
						+ iter + ".");
				System.out
						.println("It gives a validation data log likelihood of: "
								+ maxLikelihood);
				if (pData.Save(outFileName + "-it-" + iter))
					System.out.println("Saving successful");
				else
					System.out.println("Saving failed!");
				pData = null;
			}

		}

		System.out.println("Saving grammar to " + outFileName + ".");
		System.out.println("It gives a validation data log likelihood of: "
				+ maxLikelihood);

		// for (int i=0; i<grammar.numStates; i++){
		// if (grammar.numSubStates[i]!=lexicon.numSubStates[i])
		// System.out.println("DISAGREEMENT: The grammar thinks that state "+i+" is split into "+grammar.numSubStates[i]+" substates, while the lexicon thinks "+lexicon.numSubStates[i]);
		// }
		ParserData newPData = new ParserData(lexicon, grammar, null,
				Numberer.getNumberers(), newNumSubStatesArray, v_markov,
				h_markov, bin);
		if (newPData.Save(outFileName))
			System.out.println("Saving successful.");
		else
			System.out.println("Saving failed!");

		System.exit(0);

	}

	/**
	 * @param grammar
	 * @param newGrammar
	 */
	public static void printMergingStatistics(Grammar grammar,
			Grammar newGrammar) {
		PriorityQueue<String> lexiconStates = new PriorityQueue<String>();
		PriorityQueue<String> grammarStates = new PriorityQueue<String>();
		short[] numSubStatesArray = grammar.numSubStates;
		short[] newNumSubStatesArray = newGrammar.numSubStates;
		Numberer tagNumberer = grammar.tagNumberer;
		for (short state = 0; state < numSubStatesArray.length; state++) {
			System.out.print("\nState " + tagNumberer.object(state) + " had "
					+ numSubStatesArray[state] + " substates and now has "
					+ newNumSubStatesArray[state] + ".");
			if (!grammar.isGrammarTag(state)) {
				lexiconStates.add((String) tagNumberer.object(state),
						newNumSubStatesArray[state]);
			} else {
				grammarStates.add((String) tagNumberer.object(state),
						newNumSubStatesArray[state]);
			}
		}

		System.out.print("\n");
		System.out.println("Lexicon: " + lexiconStates.toString());
		System.out.println("Grammar: " + grammarStates.toString());

		// System.out.println("after merging, we have split trees:");
		// for (int i=0; i<grammar.numStates; i++) {
		// System.out.println(grammar.splitTrees[i]);
		// }

	}

	/**
	 * This function was written to have the ability to also merge non-sibling
	 * pairs, however this functionality is not used anymore since it seemed
	 * tricky to determine an appropriate threshold for merging non-siblings.
	 * The function returns a new grammar object and changes the lexicon in
	 * place!
	 * 
	 * @param grammar
	 * @param newGrammar
	 * @param lexicon
	 * @param mergeThesePairs
	 */
	public static Grammar doTheMerges(Grammar grammar, Lexicon lexicon,
			boolean[][][] mergeThesePairs, double[][] mergeWeights) {
		short[] numSubStatesArray = grammar.numSubStates;
		short[] newNumSubStatesArray = grammar.numSubStates;
		Grammar newGrammar = null;
		while (true) {
			// we want to continue as long as there's something to merge
			boolean somethingToMerge = false;
			for (int tag = 0; tag < numSubStatesArray.length; tag++) {
				for (int i = 0; i < newNumSubStatesArray[tag]; i++) {
					for (int j = 0; j < newNumSubStatesArray[tag]; j++) {
						somethingToMerge = somethingToMerge
								|| mergeThesePairs[tag][i][j];
					}
				}
			}
			if (!somethingToMerge)
				break;
			/**
			 * mergeThisIteration is which states to merge on this iteration
			 * through the loop
			 */
			boolean[][][] mergeThisIteration = new boolean[newNumSubStatesArray.length][][];
			// make mergeThisIteration a copy of mergeTheseStates
			for (int tag = 0; tag < numSubStatesArray.length; tag++) {
				mergeThisIteration[tag] = new boolean[mergeThesePairs[tag].length][mergeThesePairs[tag].length];
				for (int i = 0; i < mergeThesePairs[tag].length; i++) {
					for (int j = 0; j < mergeThesePairs[tag].length; j++) {
						mergeThisIteration[tag][i][j] = mergeThesePairs[tag][i][j];
					}
				}
			}
			// delete all complicated merges from mergeThisIteration
			for (int tag = 0; tag < numSubStatesArray.length; tag++) {
				boolean[] alreadyDecidedToMerge = new boolean[mergeThesePairs[tag].length];
				for (int i = 0; i < mergeThesePairs[tag].length; i++) {
					for (int j = 0; j < mergeThesePairs[tag].length; j++) {
						if (alreadyDecidedToMerge[i]
								|| alreadyDecidedToMerge[j])
							mergeThisIteration[tag][i][j] = false;
						alreadyDecidedToMerge[i] = alreadyDecidedToMerge[i]
								|| mergeThesePairs[tag][i][j];
						alreadyDecidedToMerge[j] = alreadyDecidedToMerge[j]
								|| mergeThesePairs[tag][i][j];
					}
				}
			}
			// remove merges in mergeThisIteration from mergeThesePairs
			for (int tag = 0; tag < numSubStatesArray.length; tag++) {
				for (int i = 0; i < mergeThesePairs[tag].length; i++) {
					for (int j = 0; j < mergeThesePairs[tag].length; j++) {
						mergeThesePairs[tag][i][j] = mergeThesePairs[tag][i][j]
								&& !mergeThisIteration[tag][i][j];
					}
				}
			}
			// System.out.println("\nDoing one merge iteration.");
			// for (short state=0; state<numSubStatesArray.length; state++) {
			// System.out.print("\n  State "+grammar.tagNumberer.object(state));
			// for (int i=0; i<mergeThisIteration[state].length; i++){
			// for (int j=i+1; j<mergeThisIteration[state][i].length; j++){
			// if (mergeThisIteration[state][i][j])
			// System.out.print(". Merging pair ("+i+","+j+")");
			// }
			// }
			// }
			newGrammar = grammar.mergeStates(mergeThisIteration, mergeWeights);
			lexicon.mergeStates(mergeThisIteration, mergeWeights);
			// fix merge weights
			grammar.fixMergeWeightsEtc(mergeThesePairs, mergeWeights,
					mergeThisIteration);
			grammar = newGrammar;
			newNumSubStatesArray = grammar.numSubStates;
		}
		grammar.makeCRArrays();

		return grammar;
	}

	/**
	 * @param grammar
	 * @param lexicon
	 * @param mergeWeights
	 * @param trainStateSetTrees
	 * @return
	 */
	public static double[][][] computeDeltas(Grammar grammar, Lexicon lexicon,
			double[][] mergeWeights, StateSetTreeList trainStateSetTrees) {
		ArrayParser parser = new ArrayParser(grammar, lexicon);
		double[][][] deltas = new double[grammar.numSubStates.length][mergeWeights[0].length][mergeWeights[0].length];
		boolean noSmoothing = false, debugOutput = false;
		for (Tree<StateSet> stateSetTree : trainStateSetTrees) {
			parser.doInsideOutsideScores(stateSetTree, noSmoothing, debugOutput); // E
																					// Step
			double ll = stateSetTree.getLabel().getIScore(0);
			ll = Math.log(ll) + (100 * stateSetTree.getLabel().getIScale());// System.out.println(stateSetTree);
			if (!Double.isInfinite(ll))
				grammar.tallyMergeScores(stateSetTree, deltas, mergeWeights);
		}
		return deltas;
	}

	/**
	 * @param grammar
	 * @param lexicon
	 * @param trainStateSetTrees
	 * @return
	 */
	public static double[][] computeMergeWeights(Grammar grammar,
			Lexicon lexicon, StateSetTreeList trainStateSetTrees) {
		double[][] mergeWeights = new double[grammar.numSubStates.length][(int) ArrayUtil
				.max(grammar.numSubStates)];
		double trainingLikelihood = 0;
		ArrayParser parser = new ArrayParser(grammar, lexicon);
		boolean noSmoothing = false, debugOutput = false;
		int n = 0;
		for (Tree<StateSet> stateSetTree : trainStateSetTrees) {
			parser.doInsideOutsideScores(stateSetTree, noSmoothing, debugOutput); // E
																					// Step
			double ll = stateSetTree.getLabel().getIScore(0);
			ll = Math.log(ll) + (100 * stateSetTree.getLabel().getIScale());// System.out.println(stateSetTree);
			if (Double.isInfinite(ll)) {
				System.out.println("Training sentence " + n
						+ " is given -inf log likelihood!");
			} else {
				trainingLikelihood += ll; // there are for some reason some
											// sentences that are unparsable
				grammar.tallyMergeWeights(stateSetTree, mergeWeights);
			}
			n++;
		}
		System.out.println("The trainings LL before merging is "
				+ trainingLikelihood);
		// normalize the weights
		grammar.normalizeMergeWeights(mergeWeights);

		return mergeWeights;
	}

	/**
	 * @param deltas
	 * @return
	 */
	public static boolean[][][] determineMergePairs(double[][][] deltas,
			boolean separateMerge, double mergingPercentage, Grammar grammar) {
		boolean[][][] mergeThesePairs = new boolean[grammar.numSubStates.length][][];
		short[] numSubStatesArray = grammar.numSubStates;
		// set the threshold so that p percent of the splits are merged again.
		ArrayList<Double> deltaSiblings = new ArrayList<Double>();
		ArrayList<Double> deltaPairs = new ArrayList<Double>();
		ArrayList<Double> deltaLexicon = new ArrayList<Double>();
		ArrayList<Double> deltaGrammar = new ArrayList<Double>();
		int nSiblings = 0, nPairs = 0, nSiblingsGr = 0, nSiblingsLex = 0;
		for (int state = 0; state < mergeThesePairs.length; state++) {
			for (int sub1 = 0; sub1 < numSubStatesArray[state] - 1; sub1++) {
				if (sub1 % 2 == 0 && deltas[state][sub1][sub1 + 1] != 0) {
					deltaSiblings.add(deltas[state][sub1][sub1 + 1]);
					if (separateMerge) {
						if (grammar.isGrammarTag(state)) {
							deltaGrammar.add(deltas[state][sub1][sub1 + 1]);
							nSiblingsGr++;
						} else {
							deltaLexicon.add(deltas[state][sub1][sub1 + 1]);
							nSiblingsLex++;
						}
					}
					nSiblings++;
				}
				for (int sub2 = sub1 + 1; sub2 < numSubStatesArray[state]; sub2++) {
					if (!(sub2 != sub1 + 1 && sub1 % 2 != 0)
							&& deltas[state][sub1][sub2] != 0) {
						deltaPairs.add(deltas[state][sub1][sub2]);
						nPairs++;
					}
				}
			}
		}
		double threshold = -1, threshold2 = -1, thresholdGr = -1, thresholdLex = -1;
		if (separateMerge) {
			System.out.println("Going to merge "
					+ (int) (mergingPercentage * 100)
					+ "% of the substates siblings.");
			System.out
					.println("Setting the merging threshold for lexicon and grammar separately.");
			Collections.sort(deltaGrammar);
			Collections.sort(deltaLexicon);
			thresholdGr = deltaGrammar
					.get((int) (nSiblingsGr * mergingPercentage));
			thresholdLex = deltaLexicon.get((int) (nSiblingsLex
					* mergingPercentage * 1.5));
			System.out.println("Setting the threshold for lexical siblings to "
					+ thresholdLex);
			System.out
					.println("Setting the threshold for grammatical siblings to "
							+ thresholdGr);
		} else {
			// String topNmerge = CommandLineUtils.getValueOrUseDefault(input,
			// "-top", "");
			// Collections.sort(deltaPairs);
			// System.out.println(deltaPairs);
			Collections.sort(deltaSiblings);
			// if (topNmerge.equals("")) {
			System.out.println("Going to merge "
					+ (int) (mergingPercentage * 100)
					+ "% of the substates siblings.");
			// System.out.println("Furthermore "+(int)(mergingPercentage2*100)+"% of the non-siblings will be merged.");
			threshold = deltaSiblings
					.get((int) (nSiblings * mergingPercentage));
			// if (maxSubStates>2 && mergingPercentage2>0) threshold2 =
			// deltaPairs.get((int)(nPairs*mergingPercentage2));
			// } else {
			// int top = Integer.parseInt(topNmerge);
			// System.out.println("Keeping the top "+top+" substates.");
			// threshold = deltaSiblings.get(nPairs-top);
			// }
			System.out.println("Setting the threshold for siblings to "
					+ threshold + ".");
		}
		// if (maxSubStates>2 && mergingPercentage2>0)
		// System.out.println("Setting the threshold for other pairs to "+threshold2);
		int mergePair = 0, mergeSiblings = 0;
		for (int state = 0; state < mergeThesePairs.length; state++) {
			mergeThesePairs[state] = new boolean[numSubStatesArray[state]][numSubStatesArray[state]];
			for (int i = 0; i < numSubStatesArray[state] - 1; i++) {
				if (i % 2 == 0 && deltas[state][i][i + 1] != 0) {
					if (separateMerge) {
						if (grammar.isGrammarTag(state))
							mergeThesePairs[state][i][i + 1] = deltas[state][i][i + 1] <= thresholdGr;
						else
							mergeThesePairs[state][i][i + 1] = deltas[state][i][i + 1] <= thresholdLex;
					} else
						mergeThesePairs[state][i][i + 1] = deltas[state][i][i + 1] <= threshold;
					if (mergeThesePairs[state][i][i + 1]) {
						mergeSiblings++;
					}
				}
				// if (mergingPercentage2>0) {
				// for (int j=i+1; j<numSubStatesArray[state]; j++) {
				// if (!(j!=i+1 && i%2!=0) && deltas[state][i][j]!=0 &&
				// deltas[state][i][j] <= threshold2){
				// mergeThesePairs[state][i][j] = true;
				// mergePair++;
				// System.out.println("Merging pair ("+i+","+j+") of state "+tagNumberer.object(state));
				// }
				// }
				// }
			}
		}
		System.out.println("Merging " + mergeSiblings + " siblings and "
				+ mergePair + " other pairs.");
		for (short state = 0; state < deltas.length; state++) {
			System.out.print("State " + grammar.tagNumberer.object(state));
			for (int i = 0; i < numSubStatesArray[state]; i++) {
				for (int j = i + 1; j < numSubStatesArray[state]; j++) {
					if (mergeThesePairs[state][i][j])
						System.out.print(". Merging pair (" + i + "," + j
								+ ") at cost " + deltas[state][i][j]);
				}
			}
			System.out.print(".\n");
		}
		return mergeThesePairs;
	}

}
