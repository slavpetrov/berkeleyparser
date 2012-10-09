package edu.berkeley.nlp.PCFGLA;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import edu.berkeley.nlp.PCFGLA.Corpus.TreeBankType;
import edu.berkeley.nlp.PCFGLA.smoothing.NoSmoothing;
import edu.berkeley.nlp.PCFGLA.smoothing.SmoothAcrossParentBits;
import edu.berkeley.nlp.PCFGLA.smoothing.Smoother;
import edu.berkeley.nlp.discPCFG.ConditionalMerger;
import edu.berkeley.nlp.discPCFG.DefaultLinearizer;
import edu.berkeley.nlp.discPCFG.HiearchicalAdaptiveLinearizer;
import edu.berkeley.nlp.discPCFG.HierarchicalLinearizer;
import edu.berkeley.nlp.discPCFG.Linearizer;
import edu.berkeley.nlp.discPCFG.ParsingObjectiveFunction;
import edu.berkeley.nlp.math.LBFGSMinimizer;
import edu.berkeley.nlp.math.OW_LBFGSMinimizer;
import edu.berkeley.nlp.math.SloppyMath;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.Numberer;

/**
 * Reads in the Penn Treebank and generates N_GRAMMARS different grammars.
 * 
 * @author Slav Petrov
 */
public class ConditionalTrainer {

	/**
	 * @author adampauls
	 * 
	 */
	public static interface ParsingObjectFunctionFactory {

		/**
		 * @param opts
		 * @param outFileName
		 * @param linearizer
		 * @param trainStateSetTrees
		 * @param regularize
		 * @param newSigma
		 * @return
		 */
		public ParsingObjectiveFunction newParsingObjectiveFunction(
				Options opts, String outFileName, Linearizer linearizer,
				StateSetTreeList trainStateSetTrees, int regularize,
				double newSigma);

	}

	public static class Options {

		@Option(name = "-out", usage = "Output File for Grammar")
		public String outFileName;

		@Option(name = "-outDir", usage = "Output Directory for Grammar")
		public String outDir;

		@Option(name = "-path", usage = "Path to Corpus")
		public String path = null;

		@Option(name = "-SMcycles", usage = "The number of split&merge iterations (Default: 6)")
		public int numSplits = 6;

		@Option(name = "-mergingPercentage", usage = "Merging percentage (Default: 0.0)")
		public double mergingPercentage = 0;

		@Option(name = "-baseline", usage = "Just read of the MLE baseline grammar")
		public boolean baseline = false;

		@Option(name = "-treebank", usage = "Language:  WSJ, CHNINESE, GERMAN, CONLL, SINGLEFILE (Default: ENGLISH)")
		public TreeBankType treebank = TreeBankType.WSJ;

		@Option(name = "-splitMaxIt", usage = "Maximum number of EM iterations after splitting (Default: 50)")
		public int splitMaxIterations = 100;

		@Option(name = "-splitMinIt", usage = "Minimum number of EM iterations after splitting (Default: 50)")
		public int splitMinIterations = 50;

		@Option(name = "-mergeMaxIt", usage = "Maximum number of EM iterations after merging (Default: 20)")
		public int mergeMaxIterations = 20;

		@Option(name = "-mergeMinIt", usage = "Minimum number of EM iterations after merging (Default: 20)")
		public int mergeMinIterations = 20;

		@Option(name = "-di", usage = "The number of allowed iterations in which the validation likelihood drops. (Default: 6)")
		public int di = 6;

		@Option(name = "-trfr", usage = "The fraction of the training corpus to keep (Default: 1.0)\n")
		public double trainingFractionToKeep = 1.0;

		@Option(name = "-filter", usage = "Filter rules with prob below this threshold (Default: 1.0e-30)")
		public double filter = 1.0e-30;

		@Option(name = "-smooth", usage = "Type of grammar smoothing used.")
		public String smooth = "NoSmoothing";

		@Option(name = "-b", usage = "LEFT/RIGHT Binarization (Default: RIGHT)")
		public Binarization binarization = Binarization.RIGHT;

		@Option(name = "-noSplit", usage = "Don't split - just load and continue training an existing grammar (true/false) (Default:false)")
		public boolean noSplit = false;

		@Option(name = "-initializeZero", usage = "Initialize conditional weights with zero")
		public boolean initializeZero = false;

		@Option(name = "-in", usage = "Input File for Grammar")
		public String inFile = null;

		@Option(name = "-randSeed", usage = "Seed for random number generator")
		public int randSeed = 8;

		@Option(name = "-sep", usage = "Set merging threshold for grammar and lexicon separately (Default: false)")
		public boolean separateMergingThreshold = false;

		@Option(name = "-hor", usage = "Horizontal Markovization (Default: 0)")
		public int horizontalMarkovization = 0;

		@Option(name = "-sub", usage = "Number of substates to split (Default: 1)")
		public int nSubStates = 1;

		@Option(name = "-ver", usage = "Vertical Markovization (Default: 1)")
		public int verticalMarkovization = 1;

		@Option(name = "-v", usage = "Verbose/Quiet (Default: Quiet)\n")
		public boolean verbose = false;

		@Option(name = "-r", usage = "Level of Randomness at init (Default: 1)\n")
		public double randomization = 1.0;

		@Option(name = "-sm1", usage = "Lexicon smoothing parameter 1")
		public double smoothingParameter1 = 0.5;

		@Option(name = "-sm2", usage = "Lexicon smoothing parameter 2)")
		public double smoothingParameter2 = 0.1;

		@Option(name = "-rare", usage = "Rare word threshold (Default 4)")
		public int rare = 4;

		@Option(name = "-spath", usage = "Whether or not to store the best path info (true/false) (Default: true)")
		public boolean findClosedUnaryPaths = true;

		@Option(name = "-unkT", usage = "Threshold for unknown words (Default: 5)")
		public int unkThresh = 5;

		@Option(name = "-doConditional", usage = "Do conditional training")
		public boolean doConditional = false;

		@Option(name = "-regularize", usage = "Regularize during optimization: 0-no regularization, 1-l1, 2-l2")
		public int regularize = 0;

		@Option(name = "-onlyMerge", usage = "Do only a conditional merge")
		public boolean onlyMerge = false;

		@Option(name = "-sigma", usage = "Regularization coefficient")
		public double sigma = 1.0;

		@Option(name = "-cons", usage = "File with constraints")
		public String cons = null;

		@Option(name = "-nProcess", usage = "Distribute on that many cores")
		public int nProcess = 1;

		@Option(name = "-doNOTprojectConstraints", usage = "Do NOT project constraints")
		public boolean doNOTprojectConstraints = false;

		@Option(name = "-section", usage = "Which section of the corpus to process.")
		public String section = "train";

		@Option(name = "-outputLog", usage = "Print output to this file rather than STDOUT.")
		public String outputLog = null;

		@Option(name = "-maxL", usage = "Skip sentences which are longer than this.")
		public int maxL = 10000;

		@Option(name = "-nChunks", usage = "Store constraints in that many files.")
		public int nChunks = 1;

		@Option(name = "-logT", usage = "Log threshold for pruning")
		public double logT = -10;

		@Option(name = "-lasso", usage = "Start of by regularizing less and make the regularization stronger with time")
		public boolean lasso = false;

		@Option(name = "-hierarchical", usage = "Use hierarchical rules")
		public boolean hierarchical = false;

		@Option(name = "-keepGoldTreeAlive", usage = "Don't prune the gold train when computing constraints")
		public boolean keepGoldTreeAlive = false;

		@Option(name = "-flattenParameters", usage = "Flatten parameters to reduce overconfidence")
		public double flattenParameters = 1.0;

		@Option(name = "-usePosteriorTraining", usage = "Adam's new objective function")
		public boolean usePosteriorTraining = false;

		@Option(name = "-dontLoad", usage = "Don't load anything from the pipeline")
		public boolean dontLoad = false;

		@Option(name = "-predefinedMaxSplit", usage = "Use predifined number of subcategories")
		public boolean predefinedMaxSplit = false;

		@Option(name = "-collapseUnaries", usage = "Dont throw away trees with unaries, just collapse the unary chains")
		public boolean collapseUnaries = false;

		@Option(name = "-connectedLexicon", usage = "Score each word with the sum of its score and its signature score")
		public boolean connectedLexicon = false;

		@Option(name = "-adaptive", usage = "Use adpatively refined rules")
		public boolean adaptive = false;

		@Option(name = "-checkDerivative", usage = "Check the derivative of the objective function against an estimate with finite difference")
		public boolean checkDerivative = false;

		@Option(name = "-initRandomness", usage = "Amount of randomness to initialize the grammar with")
		public double initRandomness = 1.0;

		@Option(name = "-markUnaryParents", usage = "Filter all training trees with any unaries (other than lexical and ROOT productions)")
		public boolean markUnaryParents = false;

		@Option(name = "-filterAllUnaries", usage = "Mark any unary parent with a ^u")
		public boolean filterAllUnaries = false;

		@Option(name = "-filterStupidFrickinWHNP", usage = "Temp hack!")
		public boolean filterStupidFrickinWHNP = false;

		@Option(name = "-initializeDir", usage = "Temp hack!")
		public String initializeDir = null;

		@Option(name = "-allPosteriorsWeight", usage = "Weight for the all posteriors regularizer")
		public double allPosteriorsWeight = 0.0;

		@Option(name = "-dontSaveGrammarsAfterEachIteration")
		public static boolean dontSaveGrammarsAfterEachIteration = false;

		@Option(name = "-hierarchicalChart")
		public static boolean hierarchicalChart = false;

		@Option(name = "-testAll", usage = "Test grammars after each iteration, proceed by splitting the best")
		public boolean testAll = false;

		@Option(name = "-lockGrammar", usage = "Lock grammar weights, learn only span feature weights")
		public static boolean lockGrammar = false;
		@Option(name = "-featurizedLexicon", usage = "Use featurized lexicon (no fixed signature classes")
		public boolean featurizedLexicon = false;

		@Option(name = "-spanFeatures", usage = "Use span features")
		public boolean spanFeatures = false;

		@Option(name = "-useFirstAndLast", usage = "Use first and last span words as span features")
		public static boolean useFirstAndLast = false;
		@Option(name = "-usePreviousAndNext", usage = "Use previous and next span words as span features")
		public static boolean usePreviousAndNext = false;
		@Option(name = "-useBeginAndEndPairs", usage = "Use begin and end word-pairs as span features")
		public static boolean useBeginAndEndPairs = false;
		@Option(name = "-useSyntheticClass", usage = "Distiguish between real and synthetic constituents")
		public static boolean useSyntheticClass = false;
		@Option(name = "-usePunctuation", usage = "Use punctuation cues")
		public static boolean usePunctuation = false;
		@Option(name = "-minFeatureFrequency", usage = "Use punctuation cues")
		public static int minFeatureFrequency = 0;
		@Option(name = "-lbfgsHistorySize", usage = "Max size of L-BFGS history (use -1 for defaults)")
		public int lbfgsHistorySize = -1;

		// -spanFeatures -usePunctuation -useSyntheticClass -useFirstAndLast
		// -usePreviousAndNext -useBeginAndEndPairs
	}

	private static ParsingObjectFunctionFactory parsingObjectFunctionFactory = new ParsingObjectFunctionFactory() {

		public ParsingObjectiveFunction newParsingObjectiveFunction(
				Options opts, String outFileName, Linearizer linearizer,
				StateSetTreeList trainStateSetTrees, int regularize,
				double newSigma) {
			return ConditionalTrainer.newParsingObjectiveFunction(opts,
					outFileName, linearizer, trainStateSetTrees, regularize,
					newSigma);
		}

	};

	public static void setParsingObjectiveFunctionFactory(
			ParsingObjectFunctionFactory fact) {
		parsingObjectFunctionFactory = fact;
	}

	public static void main(String[] args) {

		OptionParser optParser = new OptionParser(Options.class);
		Options opts = (Options) optParser.parse(args, false);
		// provide feedback on command-line arguments
		System.out.println("Calling ConditionalTrainer with "
				+ optParser.getPassedInOptions());

		String path = opts.path;
		// int lang = opts.lang;
		System.out.println("Loading trees from " + path
				+ " and using language " + opts.treebank);

		double trainingFractionToKeep = opts.trainingFractionToKeep;

		int maxSentenceLength = opts.maxL;
		System.out.println("Will remove sentences with more than "
				+ maxSentenceLength + " words.");

		Binarization binarization = opts.binarization;
		System.out.println("Using " + binarization.name() + " binarization.");// and
																				// "+annotateString+".");

		double randomness = opts.randomization;
		System.out.println("Using a randomness value of " + randomness);

		String outFileName = opts.outFileName;
		if (outFileName == null) {
			System.out.println("Output File name is required.");
			System.exit(-1);
		} else
			System.out
					.println("Using grammar output file " + outFileName + ".");

		GrammarTrainer.VERBOSE = opts.verbose;
		GrammarTrainer.RANDOM = new Random(opts.randSeed);
		System.out.println("Random number generator seeded at " + opts.randSeed
				+ ".");

		boolean manualAnnotation = false;
		boolean baseline = opts.baseline;
		boolean noSplit = opts.noSplit;
		int numSplitTimes = opts.numSplits;
		if (baseline)
			numSplitTimes = 0;
		String splitGrammarFile = opts.inFile;
		int allowedDroppingIters = opts.di;

		int maxIterations = opts.splitMaxIterations;
		int minIterations = opts.splitMinIterations;
		if (minIterations > 0)
			System.out.println("I will do at least " + minIterations
					+ " iterations.");

		double[] smoothParams = { opts.smoothingParameter1,
				opts.smoothingParameter2 };
		System.out.println("Using smoothing parameters " + smoothParams[0]
				+ " and " + smoothParams[1]);

		if (opts.connectedLexicon)
			System.out.println("Using connected lexicon.");
		if (opts.featurizedLexicon)
			System.out.println("Using featuized lexicon.");

		// boolean allowMoreSubstatesThanCounts = false;
		boolean findClosedUnaryPaths = opts.findClosedUnaryPaths;

		Corpus corpus = new Corpus(path, opts.treebank, trainingFractionToKeep,
				false);
		List<Tree<String>> trainTrees = Corpus
				.binarizeAndFilterTrees(corpus.getTrainTrees(),
						opts.verticalMarkovization,
						opts.horizontalMarkovization, maxSentenceLength,
						binarization, manualAnnotation, GrammarTrainer.VERBOSE,
						opts.markUnaryParents);
		List<Tree<String>> validationTrees = Corpus
				.binarizeAndFilterTrees(corpus.getValidationTrees(),
						opts.verticalMarkovization,
						opts.horizontalMarkovization, maxSentenceLength,
						binarization, manualAnnotation, GrammarTrainer.VERBOSE,
						opts.markUnaryParents);
		Numberer tagNumberer = Numberer.getGlobalNumberer("tags");

		if (opts.collapseUnaries)
			System.out.println("Collpasing unary chains.");
		if (trainTrees != null)
			trainTrees = Corpus.filterTreesForConditional(trainTrees,
					opts.filterAllUnaries, opts.filterStupidFrickinWHNP,
					opts.collapseUnaries);
		if (validationTrees != null)
			validationTrees = Corpus.filterTreesForConditional(validationTrees,
					opts.filterAllUnaries, opts.filterStupidFrickinWHNP,
					opts.collapseUnaries);
		int nTrees = trainTrees.size();
		System.out.println("There are " + nTrees
				+ " trees in the training set.");

		// List<Tree<String>> devTrees = Corpus.binarizeAndFilterTrees(corpus
		// .getDevTestingTrees(), opts.verticalMarkovization,
		// opts.horizontalMarkovization, maxSentenceLength, binarization,
		// manualAnnotation,GrammarTrainer.VERBOSE, opts.markUnaryParents);
		//
		// for (Tree<String> t : devTrees){
		// System.out.println(t);
		// }

		double filter = opts.filter;

		short nSubstates = (short) opts.nSubStates;
		short[] numSubStatesArray = initializeSubStateArray(trainTrees,
				validationTrees, tagNumberer, nSubstates);
		if (baseline) {
			short one = 1;
			Arrays.fill(numSubStatesArray, one);
			System.out
					.println("Training just the baseline grammar (1 substate for all states)");
			randomness = 0.0f;
		}

		if (GrammarTrainer.VERBOSE) {
			for (int i = 0; i < numSubStatesArray.length; i++) {
				System.out.println("Tag " + (String) tagNumberer.object(i)
						+ " " + i);
			}
		}

		// initialize lexicon and grammar
		SimpleLexicon lexicon = null, maxLexicon = null, previousLexicon = null;
		Grammar grammar = null, maxGrammar = null, previousGrammar = null;
		SpanPredictor spanPredictor = null;
		double maxLikelihood = Double.NEGATIVE_INFINITY;

		// EM: iterate until the validation likelihood drops for four
		// consecutive
		// iterations
		int iter = 0;
		int droppingIter = 0;

		// If we are splitting, we load the old grammar and start off by
		// splitting.
		int startSplit = 0;
		Linearizer linearizer = null;

		if (splitGrammarFile != null) {
			System.out.println("Loading old grammar from " + splitGrammarFile);
			startSplit = 1; // we've already trained the grammar
			ParserData pData = ParserData.Load(splitGrammarFile);
			Numberer.setNumberers(pData.getNumbs());
			tagNumberer = Numberer.getGlobalNumberer("tags");

			boolean noUnaryChains = true;
			previousGrammar = pData.gr.copyGrammar(noUnaryChains);
			previousLexicon = (SimpleLexicon) pData.lex.copyLexicon();

			maxGrammar = pData.gr.copyGrammar(noUnaryChains);
			maxLexicon = (SimpleLexicon) pData.lex.copyLexicon();

			spanPredictor = pData.getSpanPredictor();

			if (opts.hierarchical && previousGrammar.numSubStates[1] == 1) { // the
																				// previous
																				// grammar
																				// was
																				// the
																				// baseline
																				// grammar
				System.out.println("Converting grammar to hierarchical rules.");
				// convert it to a hierarchical grammar
				if (opts.adaptive) {
					// maxGrammar = new
					// HierarchicalAdaptiveGrammar(previousGrammar);
					// maxLexicon = new
					// HierarchicalFullyConnectedAdaptiveLexicon(previousLexicon,
					// opts.unkThresh);
				} else {
					maxGrammar = new HierarchicalGrammar(previousGrammar);
					if (opts.connectedLexicon) {
						maxLexicon = new HierarchicalFullyConnectedLexicon(
								previousLexicon, opts.unkThresh);
					} else
						maxLexicon = new HierarchicalLexicon(previousLexicon);
				}
			}

			if (!opts.noSplit) {
				System.out.println("Splitting the input grammar and lexicon");
				boolean allowMoreSubstatesThanCounts = true;// false;
				StateSetTreeList trainStateSetTrees = new StateSetTreeList(
						trainTrees, numSubStatesArray, false, tagNumberer);
				CorpusStatistics corpusStatistics = new CorpusStatistics(
						tagNumberer, trainStateSetTrees);
				int[] counts = corpusStatistics.getSymbolCounts();
				if (opts.predefinedMaxSplit) {
					System.out
							.println("Using predefnied max number of subcategories!");
					allowMoreSubstatesThanCounts = false;
					int[] tmp = new int[] { 1, 18, 26, 62, 64, 49, 21, 2, 58,
							6, 35, 15, 6, 5, 59, 1, 46, 33, 21, 61, 36, 29, 7,
							28, 21, 59, 4, 37, 39, 1, 6, 1, 2, 17, 28, 25, 2,
							3, 1, 1, 1, 3, 2, 6, 3, 6, 2, 2, 1, 2, 2, 2, 9, 2,
							2, 6, 6, 2, 1, 2, 2, 2, 1, 1, 2, 1, 2, 5, 3, 3, 5,
							7, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
							1, 1, 1, 1, 1, 1, 2 };
					if (tmp.length != counts.length)
						throw new Error("counts do not match");
					counts = tmp;
				}
				System.out.println(Arrays.toString(counts));
				int mode = (opts.hierarchical) ? 2 : 1;
				maxGrammar = maxGrammar.splitAllStates(randomness, counts,
						allowMoreSubstatesThanCounts, mode);
				maxLexicon = maxLexicon.splitAllStates(counts,
						allowMoreSubstatesThanCounts, mode);
			}

			if (opts.hierarchical) {
				System.out.println("Using hierarchical rules!");
				short finalLevel = (short) (Math
						.log(maxGrammar.numSubStates[1]) / Math.log(2));
				System.out.println("The final level of refinement will be: "
						+ finalLevel);
				maxGrammar.finalLevel = finalLevel;
				if (opts.adaptive) {
					linearizer = new HiearchicalAdaptiveLinearizer(maxGrammar,
							maxLexicon, spanPredictor, finalLevel);
				} else
					linearizer = new HierarchicalLinearizer(maxGrammar,
							maxLexicon, spanPredictor, finalLevel);
			} else
				linearizer = new DefaultLinearizer(maxGrammar, maxLexicon,
						spanPredictor);

			numSubStatesArray = maxGrammar.numSubStates;
			previousGrammar = grammar = maxGrammar;
			previousLexicon = lexicon = maxLexicon;
			System.out.println("Loading old grammar complete.");
			if (noSplit) {
				System.out.println("Will NOT split the loaded grammar.");
				startSplit = 0;
			}
		}

		double mergingPercentage = opts.mergingPercentage;
		boolean separateMergingThreshold = opts.separateMergingThreshold;
		if (mergingPercentage > 0) {
			System.out.println("Will merge " + (int) (mergingPercentage * 100)
					+ "% of the splits in each round.");
			System.out
					.println("The threshold for merging lexical and phrasal categories will be set separately: "
							+ separateMergingThreshold);
		}

		StateSetTreeList trainStateSetTrees = new StateSetTreeList(trainTrees,
				numSubStatesArray, false, tagNumberer);
		StateSetTreeList validationStateSetTrees = new StateSetTreeList(
				validationTrees, numSubStatesArray, false, tagNumberer);// deletePC);

		// replaces rare words with their signatures
		if (!(opts.connectedLexicon) || !opts.doConditional
				|| opts.unkThresh < 0) {
			System.out
					.println("Replacing words which have been seen less than "
							+ opts.unkThresh + " times with their signature.");
			Corpus.replaceRareWords(trainStateSetTrees, new SimpleLexicon(
					numSubStatesArray, -1), Math.abs(opts.unkThresh));
		}

		if (splitGrammarFile != null)
			maxLexicon.labelTrees(trainStateSetTrees);

		if (splitGrammarFile != null)
			lexicon = maxLexicon;

		if (splitGrammarFile != null && spanPredictor == null
				&& opts.spanFeatures) {
			System.out.println("Adding a span predictor since there was none!");
			spanPredictor = new SpanPredictor(maxLexicon.nWords,
					trainStateSetTrees, tagNumberer, maxLexicon.wordIndexer);
			linearizer = new HiearchicalAdaptiveLinearizer(maxGrammar,
					maxLexicon, spanPredictor, maxGrammar.finalLevel);
		}

		// get rid of the old trees
		trainTrees = null;
		validationTrees = null;
		corpus = null;
		System.gc();

		// If we're training without loading a split grammar, then we run once
		// without splitting.
		if (splitGrammarFile == null) {
			int n = 0;
			grammar = new Grammar(numSubStatesArray, findClosedUnaryPaths,
					new NoSmoothing(), null, filter);
			lexicon = new SimpleLexicon(numSubStatesArray, -1, smoothParams,
					new NoSmoothing(), filter, trainStateSetTrees);

			boolean secondHalf = false;
			for (Tree<StateSet> stateSetTree : trainStateSetTrees) {
				secondHalf = (n++ > nTrees / 2.0);
				lexicon.trainTree(stateSetTree, randomness, null, secondHalf,
						false, opts.rare);
				grammar.tallyUninitializedStateSetTree(stateSetTree);
			}
			lexicon.optimize();
			grammar.optimize(randomness);
			// System.out.println(grammar);
			boolean noUnaryChains = true;
			Grammar grammar2 = grammar.copyGrammar(noUnaryChains);
			SimpleLexicon lexicon2 = lexicon.copyLexicon();
			System.out.println("Known word cut-off at " + opts.unkThresh
					+ " occurences.");

			if (opts.adaptive) {
				System.out
						.println("Using hierarchical adaptive grammar and lexicon.");
				grammar2 = new HierarchicalAdaptiveGrammar(grammar2);
				lexicon2 = (opts.featurizedLexicon) ? new HierarchicalFullyConnectedAdaptiveLexiconWithFeatures(
						numSubStatesArray, -1, smoothParams, new NoSmoothing(),
						trainStateSetTrees, opts.unkThresh)
						: new HierarchicalFullyConnectedAdaptiveLexicon(
								numSubStatesArray, -1, smoothParams,
								new NoSmoothing(), trainStateSetTrees,
								opts.unkThresh);
				if (opts.spanFeatures)
					spanPredictor = new SpanPredictor(lexicon2.nWords,
							trainStateSetTrees, tagNumberer,
							lexicon2.wordIndexer);

				linearizer = new HiearchicalAdaptiveLinearizer(grammar2,
						lexicon2, spanPredictor, 0);
			} else if (opts.connectedLexicon && opts.doConditional) {
				lexicon2 = new HierarchicalFullyConnectedLexicon(
						numSubStatesArray, -1, smoothParams, new NoSmoothing(),
						trainStateSetTrees, opts.unkThresh);
				linearizer = new DefaultLinearizer(grammar2, lexicon2,
						spanPredictor);
			} else {
				linearizer = new DefaultLinearizer(grammar2, lexicon2,
						spanPredictor);
			}

			if (opts.initializeZero)
				System.out.println("Initializing weigths with zero!");

			Random rand = GrammarTrainer.RANDOM;
			double[] init = linearizer.getLinearizedWeights();
			if (opts.initializeZero) {
				// Arrays.fill(init, 0);
				for (int i = 0; i < init.length; i++) {
					init[i] = opts.initRandomness * rand.nextDouble() / 100;
				}
			}
			linearizer.delinearizeWeights(init);
			grammar2 = linearizer.getGrammar();
			lexicon2 = linearizer.getLexicon();
			spanPredictor = linearizer.getSpanPredictor();

			grammar2.splitRules();

			previousGrammar = maxGrammar = grammar = grammar2; // needed for
																// baseline -
																// when there is
																// no EM loop
			previousLexicon = maxLexicon = lexicon = lexicon2;
		}

		if (opts.doConditional) {

			if (opts.onlyMerge) {
				System.out.println("Will do only a conditional merge.");
				ConditionalMerger merger = new ConditionalMerger(opts.nProcess,
						opts.cons, trainStateSetTrees, grammar, lexicon,
						opts.mergingPercentage, opts.outFileName);
				merger.mergeGrammarAndLexicon();
				System.exit(1);
			}

			ParsingObjectiveFunction objective = null;

			int regularize = opts.regularize;
			int iterations = opts.splitMaxIterations;
			double sigma = opts.sigma;
			if (regularize > 0) {
				System.out.println("Regularizing with sigma=" + sigma);
			}

			LBFGSMinimizer minimizer = null;

			int maxIter = (opts.noSplit) ? 2 : 4;
			for (int it = 1; it < maxIter; it++) {
				if (opts.regularize == 1)
					minimizer = new OW_LBFGSMinimizer(iterations);
				else
					minimizer = new LBFGSMinimizer(iterations);
				if (opts.lbfgsHistorySize >= 0)
					minimizer.setMaxHistorySize(opts.lbfgsHistorySize);
				double newSigma = sigma;
				if (opts.lasso && !opts.noSplit) {
					newSigma = sigma + 3 - it;
					System.out
							.println("The regularization parameter for this round will be: "
									+ newSigma);
				}
				if (it == 1) {

					objective = parsingObjectFunctionFactory
							.newParsingObjectiveFunction(opts, outFileName,
									linearizer, trainStateSetTrees, regularize,
									newSigma);
					minimizer.setMinIteratons(15);
				} else {
					minimizer.setMinIteratons(5);
				}
				objective.setSigma(newSigma);

				double[] weights = objective.getCurrentWeights();

				if (it == 1 && opts.checkDerivative) {
					System.out.print("\nChecking derivative: ");
					double f = objective.valueAt(weights);
					double[] deriv = objective.derivativeAt(weights);
					double[] fDif = deriv.clone();
					final double h = 1e-4;
					for (int i = 0; i < 1; ++i) {
						double[] newWeights = weights.clone();
						newWeights[i] += h;
						double fplush = objective.valueAt(newWeights);
						double finiteDif = (fplush - f) / h;
						if (finiteDif - deriv[i] > 0.1) {
							System.out.println("Derivative is whack!");
						}
						fDif[i] = finiteDif;
					}
					System.out.println("done");
				}
				System.out.print("\nChecking weights: ");
				int invalid = 0;
				for (int i = 0; i < weights.length; i++) {
					if (SloppyMath.isVeryDangerous(weights[i])) {
						invalid++;
						weights[i] = 0;
					}
				}
				System.out
						.print(invalid
								+ " out of "
								+ weights.length
								+ " features had -Inf weight and have been set to 0.\n");

				// objective.updateGoldCountsNextRound();
				// objective = new ConstrainedParsingObjectiveFunction(grammar,
				// startIndexGrammar, lexicon, startIndexLexicon,
				// trainStateSetTrees, sigma, consFileName, regularize,false,
				// nRules, nRules2);
				System.out.println("In the " + it + ". EM-like Iteration.");
				weights = minimizer.minimize(objective, weights, 1e-4);

				linearizer.delinearizeWeights(weights);
				grammar = linearizer.getGrammar();
				lexicon = linearizer.getLexicon();
				spanPredictor = linearizer.getSpanPredictor();

				ParserData pData = new ParserData(maxLexicon, maxGrammar,
						spanPredictor, Numberer.getNumberers(),
						numSubStatesArray, opts.verticalMarkovization,
						opts.horizontalMarkovization, binarization);
				System.out.println("Saving grammar to " + outFileName + "-"
						+ it + ".");
				if (pData.Save(outFileName + "-" + it))
					System.out.println("Saving successful.");
				else
					System.out.println("Saving failed!");

			}

			if (true) {
				if (opts.hierarchical && splitGrammarFile != null) {
					System.out.println("Collapsing unused parameters.");
					HierarchicalGrammar hrGrammar = (HierarchicalGrammar) maxGrammar;
					HierarchicalLexicon hrLexicon = (HierarchicalLexicon) maxLexicon;
					if (opts.mergingPercentage != -1) {
						hrGrammar.mergeGrammar();
						hrLexicon.mergeLexicon();
					}
					maxGrammar = hrGrammar;
					maxLexicon = hrLexicon;
				}
				objective.shutdown();

				ParserData pData = new ParserData(maxLexicon, maxGrammar,
						spanPredictor, Numberer.getNumberers(),
						numSubStatesArray, opts.verticalMarkovization,
						opts.horizontalMarkovization, binarization);
				System.out.println("Saving grammar to " + outFileName + ".");
				if (pData.Save(outFileName))
					System.out.println("Saving successful.");
				else
					System.out.println("Saving failed!");
				return;
				// System.exit(1);
			}
		}

		boolean allowMoreSubstatesThanCounts = true;

		// the main loop: split and train the grammar
		for (int splitIndex = startSplit; splitIndex < 3 * numSplitTimes; splitIndex++) {

			// now do either a merge or a split and the end a smooth
			// on odd iterations merge, on even iterations split
			String opString = "";

			if (splitIndex % 3 == 2) {// (splitIndex==numSplitTimes*2){
				if (opts.onlyMerge)
					continue;

				if (opts.smooth.equals("NoSmoothing"))
					continue;
				System.out.println("Setting smoother for grammar and lexicon.");
				Smoother grSmoother = new SmoothAcrossParentBits(0.01,
						maxGrammar.splitTrees);
				Smoother lexSmoother = new SmoothAcrossParentBits(0.1,
						maxGrammar.splitTrees);
				// Smoother grSmoother = new SmoothAcrossParentSubstate(0.01);
				// Smoother lexSmoother = new SmoothAcrossParentSubstate(0.1);
				maxGrammar.setSmoother(grSmoother);
				maxLexicon.setSmoother(lexSmoother);
				minIterations = maxIterations = 10;
				opString = "smoothing";
			} else if (splitIndex % 3 == 0) {
				if (opts.onlyMerge)
					continue;
				// the case where we split
				if (!opts.noSplit) {
					System.out.println("Before splitting, we have a total of "
							+ maxGrammar.totalSubStates() + " substates.");
					CorpusStatistics corpusStatistics = new CorpusStatistics(
							tagNumberer, trainStateSetTrees);
					int[] counts = corpusStatistics.getSymbolCounts();

					maxGrammar = maxGrammar.splitAllStates(randomness, counts,
							allowMoreSubstatesThanCounts, 0);
					maxLexicon = maxLexicon.splitAllStates(counts,
							allowMoreSubstatesThanCounts, 0);
					Smoother grSmoother = new NoSmoothing();
					Smoother lexSmoother = new NoSmoothing();
					maxGrammar.setSmoother(grSmoother);
					maxLexicon.setSmoother(lexSmoother);
					System.out.println("After splitting, we have a total of "
							+ maxGrammar.totalSubStates() + " substates.");
					System.out
							.println("Rule probabilities are NOT normalized in the split, therefore the training LL is not guaranteed to improve between iteration 0 and 1!");
				}
				opString = "splitting";
				maxIterations = opts.splitMaxIterations;
				minIterations = opts.splitMinIterations;
			} else {
				if (mergingPercentage == 0)
					continue;
				// the case where we merge
				double[][] mergeWeights = GrammarMerger.computeMergeWeights(
						maxGrammar, maxLexicon, trainStateSetTrees);
				double[][][] deltas = GrammarMerger.computeDeltas(maxGrammar,
						maxLexicon, mergeWeights, trainStateSetTrees);
				boolean[][][] mergeThesePairs = GrammarMerger
						.determineMergePairs(deltas, separateMergingThreshold,
								mergingPercentage, maxGrammar);

				// merges grammar and lexicon and returns the merged grammar
				// while the lexicon is merged in place
				grammar = GrammarMerger.doTheMerges(maxGrammar, maxLexicon,
						mergeThesePairs, mergeWeights);
				lexicon = maxLexicon;
				short[] newNumSubStatesArray = grammar.numSubStates;
				trainStateSetTrees = new StateSetTreeList(trainStateSetTrees,
						newNumSubStatesArray, false);
				validationStateSetTrees = new StateSetTreeList(
						validationStateSetTrees, newNumSubStatesArray, false);

				// retrain lexicon to finish the lexicon merge (updates the
				// unknown words model)...
				// lexicon = new
				// Lexicon(newNumSubStatesArray,Lexicon.DEFAULT_SMOOTHING_CUTOFF,
				// maxLexicon.smooth, maxLexicon.smoother,
				// maxLexicon.threshold);
				// boolean updateOnlyLexicon = true;
				// double trainingLikelihood =
				// ConditionalTrainer.doOneEStep(grammar, maxLexicon, null,
				// lexicon, trainStateSetTrees, updateOnlyLexicon);
				// System.out.println("The training LL is "+trainingLikelihood);
				// lexicon.optimize();//Grammar.RandomInitializationType.INITIALIZE_WITH_SMALL_RANDOMIZATION);
				// // M Step

				GrammarMerger.printMergingStatistics(maxGrammar, grammar);
				opString = "merging";
				maxGrammar = grammar;
				maxLexicon = lexicon;
				maxIterations = opts.mergeMaxIterations;
				minIterations = opts.mergeMinIterations;
			}
			// update the substate dependent objects
			previousGrammar = grammar = maxGrammar;
			previousLexicon = lexicon = maxLexicon;
			droppingIter = 0;
			numSubStatesArray = grammar.numSubStates;
			trainStateSetTrees = new StateSetTreeList(trainStateSetTrees,
					numSubStatesArray, false);
			validationStateSetTrees = new StateSetTreeList(
					validationStateSetTrees, numSubStatesArray, false);
			maxLikelihood = calculateLogLikelihood(maxGrammar, maxLexicon,
					validationStateSetTrees);
			System.out.println("After " + opString + " in the "
					+ (splitIndex / 3 + 1)
					+ "th round, we get a validation likelihood of "
					+ maxLikelihood);
			iter = 0;

			// the inner loop: train the grammar via EM until validation
			// likelihood reliably drops
			do {
				if (maxIterations > 0) {
					iter += 1;
					System.out.println("Beginning iteration " + (iter - 1)
							+ ":");

					// 1) Compute the validation likelihood of the previous
					// iteration
					System.out.print("Calculating validation likelihood...");
					double validationLikelihood = calculateLogLikelihood(
							previousGrammar, previousLexicon,
							validationStateSetTrees); // The validation LL of
														// previousGrammar/previousLexicon
					System.out.println("done: " + validationLikelihood);

					// 2) Perform the E step while computing the training
					// likelihood of the previous iteration
					System.out.print("Calculating training likelihood...");
					grammar = new Grammar(grammar.numSubStates,
							grammar.findClosedPaths, grammar.smoother, grammar,
							grammar.threshold);
					// lexicon = new SimpleLexicon(grammar.numSubStates,
					// SophisticatedLexicon.DEFAULT_SMOOTHING_CUTOFF, null, new
					// NoSmoothing(), opts.unkThresh);
					lexicon = maxLexicon.copyLexicon();
					boolean updateOnlyLexicon = false;
					double trainingLikelihood = doOneEStep(previousGrammar,
							previousLexicon, grammar, lexicon,
							trainStateSetTrees, updateOnlyLexicon, opts.rare); // The
																				// training
																				// LL
																				// of
																				// previousGrammar/previousLexicon
					System.out.println("done: " + trainingLikelihood);

					// 3) Perform the M-Step
					lexicon.optimize(); // M Step
					grammar.optimize(0); // M Step

					// 4) Check whether previousGrammar/previousLexicon was in
					// fact better than the best
					if (iter < minIterations
							|| validationLikelihood >= maxLikelihood) {
						maxLikelihood = validationLikelihood;
						maxGrammar = previousGrammar;
						maxLexicon = previousLexicon;
						droppingIter = 0;
					} else {
						droppingIter++;
					}

					// 5) advance the 'pointers'
					previousGrammar = grammar;
					previousLexicon = lexicon;
				}
			} while ((droppingIter < allowedDroppingIters) && (!baseline)
					&& (iter < maxIterations));

			// Dump a grammar file to disk from time to time
			ParserData pData = new ParserData(maxLexicon, maxGrammar, null,
					Numberer.getNumberers(), numSubStatesArray, 1, 0,
					binarization);
			String outTmpName = outFileName + "_" + (splitIndex / 3 + 1) + "_"
					+ opString + ".gr";
			System.out.println("Saving grammar to " + outTmpName + ".");
			if (pData.Save(outTmpName))
				System.out.println("Saving successful.");
			else
				System.out.println("Saving failed!");
			pData = null;

		}

		// The last grammar/lexicon has not yet been evaluated. Even though the
		// validation likelihood
		// has been dropping in the past few iteration, there is still a chance
		// that the last one was in
		// fact the best so just in case we evaluate it.
		System.out.print("Calculating last validation likelihood...");
		double validationLikelihood = calculateLogLikelihood(grammar, lexicon,
				validationStateSetTrees);
		System.out.println("done.\n  Iteration " + iter
				+ " (final) gives validation likelihood "
				+ validationLikelihood);
		if (validationLikelihood > maxLikelihood) {
			maxLikelihood = validationLikelihood;
			maxGrammar = previousGrammar;
			maxLexicon = previousLexicon;
		}

		// System.out.println(lexicon);
		// System.out.println(grammar);

		ParserData pData = new ParserData(maxLexicon, maxGrammar, null,
				Numberer.getNumberers(), numSubStatesArray,
				opts.verticalMarkovization, opts.horizontalMarkovization,
				binarization);
		System.out.println("Saving grammar to " + outFileName + ".");
		System.out.println("It gives a validation data log likelihood of: "
				+ maxLikelihood);
		if (pData.Save(outFileName))
			System.out.println("Saving successful.");
		else
			System.out.println("Saving failed!");

		// System.exit(0);
	}

	/**
	 * @param opts
	 * @param outFileName
	 * @param lexicon
	 * @param grammar
	 * @param trainStateSetTrees
	 * @param regularize
	 * @param sigma
	 * @return
	 */
	private static ParsingObjectiveFunction newParsingObjectiveFunction(
			Options opts, String outFileName, Linearizer linearizer,
			StateSetTreeList trainStateSetTrees, int regularize, double sigma) {
		return /*
				 * opts.usePosteriorTraining? new
				 * PosteriorTrainingObjectiveFunction(linearizer,
				 * trainStateSetTrees, sigma, regularize, opts.boostIncorrect,
				 * opts.cons, opts.nProcess, outFileName, opts.doGEM,
				 * opts.doNOTprojectConstraints, opts.allPosteriorsWeight):
				 */new ParsingObjectiveFunction(linearizer, trainStateSetTrees,
				sigma, regularize, opts.cons, opts.nProcess, outFileName,
				opts.doNOTprojectConstraints, opts.connectedLexicon);
	}

	/**
	 * @param previousGrammar
	 * @param previousLexicon
	 * @param grammar
	 * @param lexicon
	 * @param trainStateSetTrees
	 * @return
	 */
	public static double doOneEStep(Grammar previousGrammar,
			Lexicon previousLexicon, Grammar grammar, Lexicon lexicon,
			StateSetTreeList trainStateSetTrees, boolean updateOnlyLexicon,
			int unkThreshold) {
		boolean secondHalf = false;
		ArrayParser parser = new ArrayParser(previousGrammar, previousLexicon);
		double trainingLikelihood = 0;
		int n = 0;
		int nTrees = trainStateSetTrees.size();
		for (Tree<StateSet> stateSetTree : trainStateSetTrees) {
			secondHalf = (n++ > nTrees / 2.0);
			boolean noSmoothing = true, debugOutput = false;
			parser.doInsideOutsideScores(stateSetTree, noSmoothing, debugOutput); // E
																					// Step
			double ll = stateSetTree.getLabel().getIScore(0);
			ll = Math.log(ll) + (100 * stateSetTree.getLabel().getIScale());// System.out.println(stateSetTree);
			if ((Double.isInfinite(ll) || Double.isNaN(ll))) {
				if (GrammarTrainer.VERBOSE) {
					System.out.println("Training sentence " + n + " is given "
							+ ll + " log likelihood!");
					System.out.println("Root iScore "
							+ stateSetTree.getLabel().getIScore(0) + " scale "
							+ stateSetTree.getLabel().getIScale());
				}
			} else {
				lexicon.trainTree(stateSetTree, -1, previousLexicon,
						secondHalf, noSmoothing, unkThreshold);
				if (!updateOnlyLexicon)
					grammar.tallyStateSetTree(stateSetTree, previousGrammar); // E
																				// Step
				trainingLikelihood += ll; // there are for some reason some
											// sentences that are unparsable
			}
		}
		return trainingLikelihood;
	}

	/**
	 * @param maxGrammar
	 * @param maxLexicon
	 * @param validationStateSetTrees
	 * @return
	 */
	public static double calculateLogLikelihood(Grammar maxGrammar,
			Lexicon maxLexicon, StateSetTreeList validationStateSetTrees) {
		ArrayParser parser = new ArrayParser(maxGrammar, maxLexicon);
		int unparsable = 0;
		double maxLikelihood = 0;
		for (Tree<StateSet> stateSetTree : validationStateSetTrees) {
			parser.doInsideScores(stateSetTree, false, false, null); // Only
																		// inside
																		// scores
																		// are
																		// needed
																		// here
			double ll = stateSetTree.getLabel().getIScore(0);
			ll = Math.log(ll) + (100 * stateSetTree.getLabel().getIScale());
			if (Double.isInfinite(ll) || Double.isNaN(ll)) {
				unparsable++;
				// printBadLLReason(stateSetTree, lexicon);
			} else
				maxLikelihood += ll; // there are for some reason some sentences
										// that are unparsable
		}
		// if (unparsable>0)
		// System.out.print("Number of unparsable trees: "+unparsable+".");
		return maxLikelihood;
	}

	/**
	 * @param stateSetTree
	 */
	public static void printBadLLReason(Tree<StateSet> stateSetTree,
			SophisticatedLexicon lexicon) {
		System.out.println(stateSetTree.toString());
		boolean lexiconProblem = false;
		List<StateSet> words = stateSetTree.getYield();
		Iterator<StateSet> wordIterator = words.iterator();
		for (StateSet stateSet : stateSetTree.getPreTerminalYield()) {
			String word = wordIterator.next().getWord();
			boolean lexiconProblemHere = true;
			for (int i = 0; i < stateSet.numSubStates(); i++) {
				double score = stateSet.getIScore(i);
				if (!(Double.isInfinite(score) || Double.isNaN(score))) {
					lexiconProblemHere = false;
				}
			}
			if (lexiconProblemHere) {
				System.out.println("LEXICON PROBLEM ON STATE "
						+ stateSet.getState() + " word " + word);
				System.out.println("  word "
						+ lexicon.wordCounter.getCount(stateSet.getWord()));
				for (int i = 0; i < stateSet.numSubStates(); i++) {
					System.out.println("  tag "
							+ lexicon.tagCounter[stateSet.getState()][i]);
					System.out.println("  word/state/sub "
							+ lexicon.wordToTagCounters[stateSet.getState()]
									.get(stateSet.getWord())[i]);
				}
			}
			lexiconProblem = lexiconProblem || lexiconProblemHere;
		}
		if (lexiconProblem)
			System.out
					.println("  the likelihood is bad because of the lexicon");
		else
			System.out
					.println("  the likelihood is bad because of the grammar");
	}

	/**
	 * This function probably doesn't belong here, but because it should be
	 * called after {@link #updateStateSetTrees}, Leon left it here.
	 * 
	 * @param trees
	 *            Trees which have already had their inside-outside
	 *            probabilities calculated, as by {@link #updateStateSetTrees}.
	 * @return The log likelihood of the trees.
	 */
	public static double logLikelihood(List<Tree<StateSet>> trees,
			boolean verbose) {
		double likelihood = 0, l = 0;
		for (Tree<StateSet> tree : trees) {
			l = tree.getLabel().getIScore(0);
			if (verbose)
				System.out.println("LL is " + l + ".");
			if (Double.isInfinite(l) || Double.isNaN(l)) {
				System.out.println("LL is not finite.");
			} else {
				likelihood += l;
			}
		}
		return likelihood;
	}

	/**
	 * This updates the inside-outside probabilities for the list of trees using
	 * the parser's doInsideScores and doOutsideScores methods.
	 * 
	 * @param trees
	 *            A list of binarized, annotated StateSet Trees.
	 * @param parser
	 *            The parser to score the trees.
	 */
	public static void updateStateSetTrees(List<Tree<StateSet>> trees,
			ArrayParser parser) {
		for (Tree<StateSet> tree : trees) {
			parser.doInsideOutsideScores(tree, false, false);
		}
	}

	/**
	 * Convert a single Tree[String] to Tree[StateSet]
	 * 
	 * @param tree
	 * @param numStates
	 * @param tagNumberer
	 * @return
	 */

	public static short[] initializeSubStateArray(
			List<Tree<String>> trainTrees, List<Tree<String>> validationTrees,
			Numberer tagNumberer, short nSubStates) {
		// boolean dontSplitTags) {
		// first generate unsplit grammar and lexicon
		short[] nSub = new short[2];
		nSub[0] = 1;
		nSub[1] = nSubStates;

		// do the validation set so that the numberer sees all tags and we can
		// allocate big enough arrays
		// note: although this variable is never read, this constructor adds the
		// validation trees into the tagNumberer as a side effect, which is
		// important
		StateSetTreeList trainStateSetTrees = new StateSetTreeList(trainTrees,
				nSub, true, tagNumberer);
		@SuppressWarnings("unused")
		StateSetTreeList validationStateSetTrees = new StateSetTreeList(
				validationTrees, nSub, true, tagNumberer);

		StateSetTreeList.initializeTagNumberer(trainTrees, tagNumberer);
		StateSetTreeList.initializeTagNumberer(validationTrees, tagNumberer);

		short numStates = (short) tagNumberer.total();
		short[] nSubStateArray = new short[numStates];
		Arrays.fill(nSubStateArray, nSubStates);
		// System.out.println("Everything is split in two except for the root.");
		nSubStateArray[0] = 1; // that's the ROOT
		return nSubStateArray;
	}

	public static boolean[][][][][] loadDataNoZip(String fileName) {
		boolean[][][][][] data = null;
		try {
			FileInputStream fis = new FileInputStream(fileName); // Load from
																	// file
			// GZIPInputStream gzis = new GZIPInputStream(fis); // Compressed
			ObjectInputStream in = new ObjectInputStream(fis); // Load objects
			data = (boolean[][][][][]) in.readObject(); // Read the mix of
														// grammars
			in.close(); // And close the stream.
		} catch (IOException e) {
			System.out.println("IOException\n" + e);
			return null;
		} catch (ClassNotFoundException e) {
			System.out.println("Class not found!");
			return null;
		}
		return data;
	}

	public static boolean saveDataNoZip(boolean[][][][][] data, String fileName) {
		try {
			// here's some code from online; it looks good and gzips the output!
			// there's a whole explanation at
			// http://www.ecst.csuchico.edu/~amk/foo/advjava/notes/serial.html
			// Create the necessary output streams to save the scribble.
			FileOutputStream fos = new FileOutputStream(fileName); // Save to
																	// file
			// GZIPOutputStream gzos = new GZIPOutputStream(fos); // Compressed
			ObjectOutputStream out = new ObjectOutputStream(fos); // Save
																	// objects
			out.writeObject(data); // Write the mix of grammars
			out.flush(); // Always flush the output.
			out.close(); // And close the stream.
		} catch (IOException e) {
			System.out.println("IOException: " + e);
			return false;
		}
		return true;
	}

	private static final double TOL = 1e-5;

	protected static boolean matches(double x, double y) {
		return (Math.abs(x - y) / (Math.abs(x) + Math.abs(y) + 1e-10) < TOL);
	}

}
