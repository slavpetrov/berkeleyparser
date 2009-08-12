/**
 * 
 */
package edu.berkeley.nlp.HMM;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import fig.basic.Indexer;
import fig.basic.LogInfo;
import fig.basic.Option;
import fig.basic.OptionsParser;
import fig.exec.Execution;

/**
 * @author adpauls
 * 
 */
public class TimitTester implements Runnable {

	public static double staticEmissionAttenuation = 0.0;
	
	public static double staticPrevMeanSmooth =0.0;
	public static double staticPrevVarSmooth = 0.0;
	public static boolean staticOnlySamePrev = false;
	

	public static boolean FAST;

	public static enum MergingType {
		GEN_APPROX, GEN_EXACT, COND_APPROX, COND_EXACT, COND_BUGGY, GEN_BUGGY, COND_DOUBLE_BUGGY, GEN_DOUBLE_BUGGY;
	}

	public static enum MergeThreshType {
		PERCENT, ABS;
	}

	// public static class Options {
	@Option(name = "corpus", gloss = "Which corpus to use: TIMIT, CHE, or DUMMY")
	public Corpus.CorpusType corpusType = Corpus.CorpusType.TIMIT;

	@Option(name = "path", gloss = "Path to corpus")
	public File corpusPath = null;

	@Option(name = "collapseTimit", gloss = "Whether to use frame-by-frame TIMIT annotations or just phone sequences (TIMIT only)")
	public boolean collapseTimit = false;

	@Option(name = "phoneClassification", gloss = "Do phone classification (rather than recognition)")
	public boolean phoneClassification = false;

	@Option(name = "numTrain", gloss = "Maximum number of training sequences")
	public int numTrain = Integer.MAX_VALUE;

	@Option(name = "numDecode", gloss = "Maximum number of decoding sequences")
	public int numDecode = Integer.MAX_VALUE;

	@Option(name = "numSubstates", gloss = "Number of substates per state")
	public int numSubstates = 0;

	@Option(name = "numSplits", gloss = "Number of split iterations (overwritten by numSubstates)")
	public int numSplits = 1;

	@Option(name = "numIter", gloss = "Number of iterations")
	public int numIter = 50;

	@Option(name = "randSeed", gloss = "Seed for random number generator")
	public int randSeed = 1;

	@Option(name = "randomness", gloss = "Amount of randomness used for breaking symmetries")
	public int randomness = 1;

	@Option(name = "fullCovariance", gloss = "Whether to use a full covariance matrix")
	public boolean fullCovariance = false;

	@Option(name = "mergePercent", gloss = "Percentage of splits to be merged back together")
	public double mergePercent = 0;

	@Option(name = "transitionSmooth", gloss = "Factor for linear transition smoothing")
	public double transitionSmooth = 0;

	@Option(name = "meanSmooth", gloss = "Mean smoothing weight")
	public double meanSmooth = 0.0;

	@Option(name = "varSmooth", gloss = "Variance smoothing weight")
	public double varSmooth = 0.0;

	@Option(name = "minVarSmooth", gloss = "Minimum count (not percentage) for prior variance")
	public double minVarSmooth = 0.0;

	@Option(name = "doViterbi", gloss = "Do viterbi decoding")
	public boolean doViterbi = false;

	@Option(name = "doFrameError", gloss = "Calculate frame error rates")
	public boolean doFrameError = false;

	@Option(name = "doPosterior", gloss = "Do posterior decoding")
	public boolean doPosterior = false;

	@Option(name = "doMaxRule", gloss = "Do max-rule decoding")
	public boolean doMaxRule = false;

	@Option(name = "doMaxRuleSeqSum", gloss = "Do max-rule summed sequencedecoding")
	public boolean doMaxRuleSeqSum = false;

	@Option(name = "doMaxRuleSeqSum2", gloss = "Do max-rule summed sequencedecoding")
	public boolean doMaxRuleSeqSum2 = false;

	@Option(name = "doEditDistance", gloss = "Calculate edit distances")
	public boolean doEditDistance = true;

	@Option(name = "transitionExponent", gloss = "Take the transition probabilities to this power in viterbi")
	public double transitionExponent = 1.0;

	@Option(name = "useTestSet", gloss = "Test on the final test set")
	public boolean useTestSet = false;

	@Option(name = "outName", gloss = "Base name for storing models")
	public String outName = null;

	@Option(name = "inName", gloss = "Load this model and test it")
	public String inName = null;

	@Option(name = "mergingType", gloss = "Merge criterion (GEN_APPROX, GEN_EXACT, COND_APPROX, or COND_EXACT)")
	public MergingType mergingType = MergingType.GEN_APPROX;

	@Option(name = "printAllSeqs", gloss = "Print individual sequences and errors")
	public boolean printAllSeqs = false;

	@Option(name = "useFrequencies", gloss = "Determine maximum number of states based on the frequency of the phone")
	public boolean useFrequencies = false;

	@Option(name = "mergeThreshType", gloss = "Type of merging threshold (PERCENT, or ABS)")
	public MergeThreshType mergeThreshType = MergeThreshType.PERCENT;

	@Option(name = "printNonCollapsed", gloss = "Prints also the non-collapsed frame sequences")
	public boolean printNonCollapsed = false;

	@Option(name = "dumpMeansToDisk", gloss = "Dumps means to disks and exits")
	public boolean dumpMeansToDisk = false;

//	@Option(name = "numDistrib", gloss = "Number of machines to distribute on in ADDITION to the current one")
//	public int numDistrib = 0;
//
//	@Option(name = "distribClasspath", gloss = "Classpath for distributed slaves (if different from current)")
//	public String distribClassPath = System.getProperty("java.class.path");
//
//	@Option(name = "disallowedMachines", gloss = "Blacklist of available machines for distributing (comma separated) (e.g. nlp)")
//	public String disallowedMachines = null;
//
//	@Option(name = "frontEnd", gloss = "Front end for distributing (e.g. nlp.millennium.berkeley.edu)")
//	public String frontEnd = null;

	@Option(name = "pruneThresh", gloss = "Beam width")
	public int pruneThresh = 1;

	@Option(name = "insertionPenalty", gloss = "Phone insertion penalty for decoding")
	public double phoneInsertionPenatly = 0.0;

	@Option(name = "posteriorTable", gloss = "Print the posterior table")
	public boolean posteriorTable;

	@Option(name = "phoneLengthTable", gloss = "Print the phone length table")
	public boolean phoneLengthTable;

	@Option(name = "emissionAttenuation", gloss = "Attenuate emission probabilities (alternative to boosting the transitions")
	public double emissionAttenuation = 0.0;

	@Option(name = "cExponent", gloss = "c boosting exponent")
	public double cExponent = 1.0;

	@Option(name = "fast", gloss = "Cut a few corners here and there for the sake of efficiency")
	public boolean fast;

	@Option(name = "skipFirstMerge", gloss = "Skip the first merging phase")
	public boolean skipFirstMerge;

	@Option(name = "skipFirstSplit", gloss = "Skip the first splitting phase")
	public boolean skipFirstSplit;

	@Option(name = "startDecode", gloss = "Sequence to start decoding at")
	public int startDecode;

	@Option(name = "printPosteriors", gloss = "Write posterior probabilities to files")
	public boolean printPosteriors;

	@Option(name = "decodeEachSplit", gloss = "Decode after each split iteration")
	public boolean decodeEachSplit = false;

	@Option(name = "onlySplitMixtures", gloss = "Only split gaussian mixtures (not transitions)")
	public boolean onlySplitMixtures = false;
	
	@Option(name = "gaussianDim", gloss="Number of dimensions to use from MFCC coefficients (max 39)")
	public int gaussianDim = 39;
	
	@Option(name = "prevMeanSmooth", gloss="Modified thing")
	public double prevMeanSmooth = 0.0;
	
	@Option(name = "prevVarSmooth", gloss="Modified thing")
	public double prevVarSmooth = 0.0;
	
	@Option(name = "onlySamePrev", gloss="don't do modified thing across phone boundaries")
	public boolean onlySamePrev = false;
	
	@Option(name="prevDuringTraining", gloss="Do modified thing at training as well")
	public boolean prevDuringTraining = false;
	
	@Option(name="useWav",gloss="Read raw WAV files instead of Fei's preprocessing (this means path should point to proper TIMIT distribution")
	public boolean useWav = false;

	// }

	public static void main(String[] args) {
		Execution.run(args, new TimitTester(), null);//new RemoteOpts());
	}

	private static enum HMMType {
		NORMAL, ALIGNING, MIXTURE;
	}

	// public static void main(String[] argv) {
	public void run() {
		// OptionParser optionParser = new OptionParser(TimitTester.Options.class);
		// TimitTester.Options opts = (TimitTester.Options) optionParser.parse(argv,
		// true, false);
		// System.out.println("Calling with " + optionParser.getPassedInOptions());

		// temp hack
		staticOnlySamePrev = onlySamePrev;
		TimitTester opts = this;
		String cwd = Execution.getVirtualExecDir();
		opts.numTrain = opts.numTrain < 0 ? Integer.MAX_VALUE : opts.numTrain;
		opts.numDecode = opts.numDecode < 0 ? Integer.MAX_VALUE : opts.numDecode;

		boolean onlyTest = (opts.inName != null && opts.numSplits == 0);

		Map<ExecutorService, Integer> execs = new HashMap<ExecutorService, Integer>();
//		execs.put(new DummyExecutorService(), 1);
//		if (opts.numDistrib > 0) {
//
//			RemoteExecutors.setJavaCommand(System.getProperty("java.home")
//					+ "/../bin/java" + " -server -mx2000m -cp " + opts.distribClassPath);
//			if (opts.disallowedMachines != null) {
//				RemoteExecutors.setDisallowedHosts(new HashSet<String>(Arrays
//						.asList(opts.disallowedMachines.split(","))));
//			}
//			if (opts.frontEnd != null) {
//				RemoteExecutors.setFrontEnd(opts.frontEnd);
//			}
//			execs.put(RemoteExecutors.newRemoteExecutorPool(opts.numDistrib, 1),
//					opts.numDistrib);
//
//		}
//		ExecutorService exec = new EnsembleExecutorService(execs);

		boolean dummy = opts.corpusType == Corpus.CorpusType.DUMMY;
		Corpus corpus = new Corpus(dummy ? null : opts.corpusPath.getPath(),
				opts.corpusType, opts.phoneClassification, onlyTest
						&& opts.numIter == 0, gaussianDim, opts.useWav);
		PhoneIndexer phoneIndexer = corpus.getPhoneIndexer();
		int gaussianDim = corpus.getGaussianDim();

		// if (opts.phoneClassification) System.out.println("Will do phone
		// classification rather than recognition!");

		TimitTester.FAST = opts.fast;
		// if (opts.fast) System.out.println("Will cut a few corners here and there
		// for the sake of efficiency.");

		// TimitTester.staticEmissionAttenuation = opts.emissionAttenuation;
		// System.out.println("Using an exponent of "+
		// TimitTester.staticEmissionAttenuation + " to attenuate the emissions");

		boolean doAligning = opts.collapseTimit
				|| opts.corpusType == Corpus.CorpusType.CHE;
		if (opts.collapseTimit && opts.doFrameError) {
			// System.out
			// .println("Warning: cannot do frame error when doing forced
			// alignment.");
			opts.doFrameError = false;
		}
		boolean fullCovariance = opts.fullCovariance;
		SubphoneHMM hmm = null;
		if (opts.inName != null) {
			// System.out.println("Loading a model from " + opts.inName);
			LogInfo.logs("Loading a model from " + opts.inName);
			hmm = SubphoneHMM.Load(opts.inName);
			hmm.setMeanSmooth(opts.meanSmooth);
			hmm.setTransitionSmooth(opts.transitionSmooth);
			hmm.setMinVarSmooth(opts.minVarSmooth);
			hmm.setVarSmooth(opts.varSmooth);
			hmm.dataSent = false;
			if (opts.dumpMeansToDisk) {
				hmm.dumpGaussiansToDisk(opts.inName);
				System.exit(1);
			}
			phoneIndexer = (PhoneIndexer) hmm.phoneIndexer;
			corpus.setPhoneIndexer(phoneIndexer);
			if (opts.printPosteriors)
				hmm.setWritePosteriors(opts.outName);
		} else {
			HMMType type = HMMType.NORMAL;
			if (doAligning)
				type = HMMType.ALIGNING;
			else if (opts.onlySplitMixtures)
				type = HMMType.MIXTURE;

			if (doAligning && opts.onlySplitMixtures)
			{
				LogInfo.error("Haven't implemented aligning for mixtures yet");
				System.exit(1);
			}
			switch (type) {
			case NORMAL:
				hmm = new SubphoneHMM(phoneIndexer, gaussianDim, opts.randSeed,
						fullCovariance, opts.transitionSmooth, opts.meanSmooth,
						opts.varSmooth, opts.minVarSmooth, opts.emissionAttenuation,
						opts.printPosteriors, opts.outName);
				break;
			case ALIGNING:

				hmm = new AligningSubphoneHMM(phoneIndexer, gaussianDim, opts.randSeed,
						fullCovariance, opts.transitionSmooth, opts.meanSmooth,
						opts.varSmooth, opts.pruneThresh, opts.minVarSmooth,
						opts.emissionAttenuation);
				break;
			case MIXTURE:
			{
				
				hmm = new MixtureSubphoneHMM(phoneIndexer, gaussianDim, opts.randSeed,
						fullCovariance, opts.transitionSmooth, opts.meanSmooth,
						opts.varSmooth, opts.minVarSmooth, opts.emissionAttenuation,
						opts.printPosteriors, opts.outName);
				break;
			}
			default:
				assert false;
			}
		}
		if (hmm instanceof AligningSubphoneHMM) {
			opts.collapseTimit = true;
		}
		List<List<Phone>> phoneSequencesAsObjects = corpus.getPhoneSequencesTrain();
		List<List<Phone>> phoneSequencesAsObjectsUnCollapsed = phoneSequencesAsObjects;
		if (opts.collapseTimit && !onlyTest) {
			phoneSequencesAsObjects = PhoneIndexer.getCollapsedPhoneLists(
					phoneSequencesAsObjects, true, 1);

		}

		if (opts.prevDuringTraining)
		{
			staticPrevMeanSmooth = opts.prevMeanSmooth;
			staticPrevVarSmooth = opts.prevVarSmooth;
		}
		if (!onlyTest) {
			List<int[]> phoneSequences = corpus.getPhoneIndexer().indexSequences(
					phoneSequencesAsObjects);
			List<double[][]> obsList = corpus.getObsListsTrain();
			int lastElem = Math.min(phoneSequences.size(), opts.numTrain);

			if (doAligning) {
				((AligningSubphoneHMM) hmm).setUncollapsedPhoneSequences(PhoneIndexer
						.phonesToIndexes(corpus.getPhoneIndexer().indexSequences(
								phoneSequencesAsObjectsUnCollapsed)));
			}
			int[] maxNumberOfStates = new int[phoneIndexer.size()];
			if (opts.useFrequencies) {
				double[] phoneLengths = new double[phoneIndexer.size()];
				int[] phoneAppearances = new int[phoneIndexer.size()];
				CorpusStatistics.getPhoneCounts(corpus, phoneLengths, phoneAppearances);
				maxNumberOfStates = mapCountsToNumberOfSubtates(phoneLengths,
						phoneAppearances, phoneIndexer);
			} else
				Arrays.fill(maxNumberOfStates, Integer.MAX_VALUE);

			LogInfo.logs("Loaded " + phoneSequences.size()
					+ " training sequences with dimension " + gaussianDim + ".");
			LogInfo.logs("There are " + phoneIndexer.size() + " phones.");
			if (opts.numSubstates != 0) {
				LogInfo.logs("Doing flat training.");
				hmm.initializeModelFromStateSequence(corpus.getPhoneIndexer()
						.indexSequences(phoneSequencesAsObjectsUnCollapsed), obsList,
						opts.numSubstates, opts.numSubstates == 1 ? 0 : opts.randomness);

				if (dummy)
					hmm.train(phoneSequences, obsList, 10);
				else

					hmm.train(phoneSequences.subList(0, lastElem), obsList.subList(0,
							lastElem), opts.numIter/*, exec, opts.numDistrib + 1*/);
			} else {
				if (opts.inName == null)
					hmm.initializeModelFromStateSequence(corpus.getPhoneIndexer()
							.indexSequences(phoneSequencesAsObjectsUnCollapsed), obsList, 1,
							0);

				int numSplits = opts.numSplits;
				LogInfo.logs("Will do " + numSplits + " split iterations.");
//				if (opts.decodeEachSplit) {
//					testModel(opts, corpus, phoneIndexer, hmm, ".split0");
//				}
				for (int split = 0; split < numSplits; split++) {
					final String string = ".split" + (split + 1);
					LogInfo.track(string);
					LogInfo.logs("In split iteration " + split + ".");
					Execution.putOutput("currSplitIteration", split);
					if (!(opts.skipFirstSplit && split == 0)) {
						hmm.splitModelInTwo(opts.randomness, maxNumberOfStates);
						if (dummy)
							hmm.train(phoneSequences, obsList, 10);

						else {
							hmm.train(phoneSequences.subList(0, lastElem), obsList.subList(0,
									lastElem), opts.numIter/*, exec, opts.numDistrib + 1*/);
						}

						if (opts.outName != null) {

							if (!hmm.Save(cwd + "/" + opts.outName + string + ".hmm"))
								LogInfo.warning("Saving failed!");
						}
					}
					if (opts.decodeEachSplit) {
						testModel(opts, corpus, phoneIndexer, hmm, string);
					}

					if (opts.mergePercent != 0 && !(opts.skipFirstMerge && split == 0)) {
						hmm.mergeModel(phoneSequences.subList(0, lastElem), obsList
								.subList(0, lastElem), opts.mergePercent, opts.mergingType,
								opts.mergeThreshType);
						if (dummy)
							hmm.train(phoneSequences, obsList, 10 / 2);
						else

							hmm.train(phoneSequences.subList(0, lastElem), obsList.subList(0,
									lastElem), 10 /* opts.numIter/2 , exec,
									opts.numDistrib + 1*/);
						if (opts.outName != null) {
							if (!hmm.Save(cwd + "/" + opts.outName + ".merge" + (split + 1)
									+ ".hmm"))
								LogInfo.warning("Saving failed!");
						}
					}
					hmm.printNumberOfSubstates();
					LogInfo.end_track();

				}
			}
		} else {
			LogInfo.logs("Skipping the training.");
			if (opts.numIter > 0) {
				List<int[]> phoneSequences = corpus.getPhoneIndexer().indexSequences(
						phoneSequencesAsObjects);
				List<double[][]> obsList = corpus.getObsListsTrain();
				int lastElem = Math.min(phoneSequences.size(), opts.numTrain);
				if (doAligning)
					((AligningSubphoneHMM) hmm).setUncollapsedPhoneSequences(PhoneIndexer
							.phonesToIndexes(corpus.getPhoneIndexer().indexSequences(
									phoneSequencesAsObjectsUnCollapsed)));

				LogInfo.logs("Doing " + opts.numIter + " pre-testing EM iterations.");
				hmm.train(phoneSequences.subList(0, lastElem), obsList.subList(0,
						lastElem), opts.numIter/*, exec, opts.numDistrib + 1*/);
			}

		}
		if (opts.outName != null) {
			if (!hmm.Save(cwd + "/" + opts.outName + ".hmm"))
				LogInfo.warning("Saving failed!");
		}

		// System.out.println("Using an exponent of " + opts.transitionExponent
		// + " to boost the transition probabilities.");
		testModel(opts, corpus, phoneIndexer, hmm, "final");

	}

	private static void testModel(TimitTester opts, Corpus corpus,
			PhoneIndexer phoneIndexer, SubphoneHMM hmm, String prefix) {
		LogInfo.track("testModel" + prefix);
		double tmpEmissionAttenuation = TimitTester.staticEmissionAttenuation;
		TimitTester.staticEmissionAttenuation = opts.emissionAttenuation;
		TimitTester.staticPrevMeanSmooth = opts.prevMeanSmooth;
		TimitTester.staticPrevVarSmooth = opts.prevVarSmooth;
		hmm.boostTransitionProbabilities(opts.transitionExponent, opts.cExponent);

		List<List<Phone>> actualPhoneSequences = opts.useTestSet ? corpus
				.getPhoneSequencesTest() : corpus.getPhoneSequencesDev();
		if (opts.collapseTimit)
			actualPhoneSequences = PhoneIndexer.getCollapsedPhoneLists(
					actualPhoneSequences, false);
		List<double[][]> obsListsDev = (opts.useTestSet) ? corpus.getObsListsTest()
				: corpus.getObsListsDev();

		int lastDecodeElem = Math.min(actualPhoneSequences.size(), opts.numDecode);
		List<List<Phone>> goldSeqs = actualPhoneSequences.subList(opts.startDecode,
				lastDecodeElem);
		obsListsDev = obsListsDev.subList(opts.startDecode, lastDecodeElem);

		LogInfo.logs("Decoding sequences " + opts.startDecode + " to "
				+ lastDecodeElem);
		Map<String, Decoder> decoders = new HashMap<String, Decoder>();
		if (opts.doViterbi)
			decoders.put("Viterbi", new ViterbiDecoder(hmm,
					opts.phoneInsertionPenatly, opts.emissionAttenuation));
		if (opts.doPosterior)
			decoders.put("Posterior", new PosteriorDecoder(hmm, opts.posteriorTable,
					actualPhoneSequences, opts.emissionAttenuation));
		if (opts.doMaxRule)
			decoders.put("MaxRule", new MaxRuleDecoder(hmm,
					opts.phoneInsertionPenatly, opts.emissionAttenuation));
		if (opts.doMaxRuleSeqSum)
			decoders.put("MaxRuleSeqSum", new MaxRuleSeqSumDecoder(hmm,
					opts.phoneInsertionPenatly, opts.emissionAttenuation));
		if (opts.doMaxRuleSeqSum2)
			decoders.put("MaxRuleSeqSum2", new MaxRuleSeqSumDecoder2(hmm,
					opts.phoneInsertionPenatly, opts.emissionAttenuation));
		if (opts.phoneClassification)
			decoders.put("PhoneClassifier", new PhoneDecoder(hmm,
					opts.emissionAttenuation));

		Map<String, List<List<Phone>>> testSequences = new HashMap<String, List<List<Phone>>>();
		for (String decoderName : decoders.keySet()) {

			testSequences.put(decoderName, decoders.get(decoderName).decode(
					obsListsDev));
		}
		Map<String, AccuracyCalc> accuracies = new HashMap<String, AccuracyCalc>();
		for (String testSequenceName : testSequences.keySet()) {
			List<List<Phone>> testSequence = testSequences.get(testSequenceName);
			if (opts.doEditDistance)
				accuracies.put(testSequenceName + ".EditDistance",
						new EditDistanceAccuracy(corpus.getPhoneIndexer(), goldSeqs,
								testSequence));
			if (opts.doFrameError)
				accuracies.put(testSequenceName + ".FrameError",
						new FrameErrorAccuracy(corpus.getPhoneIndexer(), goldSeqs,
								testSequence));
		}

		Execution.putOutput(prefix + "numParams", hmm.getTotalNumParameters());
		Execution.putOutput(prefix + "numStates", hmm.getTotalNumStates());
		Execution.putOutput(prefix + "numParams", hmm.getTotalNumParameters());
		Execution.putOutput(prefix + "numStates", hmm.getTotalNumStates());
		for (String accuracyName : accuracies.keySet()) {
			AccuracyCalc accuracyCalc = accuracies.get(accuracyName);
			String string = prefix + "." + accuracyName + ":"
					+ accuracyCalc.getAccuracy() + " (" + hmm.getTotalNumStates()
					+ " states; " + hmm.getTotalNumParameters() + " params)";
			if (accuracyCalc instanceof EditDistanceAccuracy) {
				string = string + " [" + ((EditDistanceAccuracy) accuracyCalc).totalNum
						+ "]";
			}
			LogInfo.logss(string);
			Execution.putOutput(prefix + "." + accuracyName, accuracyCalc
					.getAccuracy());
		}

		if (opts.printAllSeqs) {
			for (int i = 0; i < lastDecodeElem; ++i) {

				for (String testSequenceName : testSequences.keySet()) {
					System.out.print(testSequenceName + ":\t");
					AccuracyCalc.collapseAndPrintSeq(testSequences.get(testSequenceName)
							.get(i), goldSeqs.get(i), phoneIndexer);
				}

				System.out.print("Actual:  \t");
				AccuracyCalc.collapseAndPrintSeq(goldSeqs.get(i), goldSeqs.get(i),
						phoneIndexer);
				for (String accuracyName : accuracies.keySet()) {

					System.out.println(accuracyName + ":\t"
							+ accuracies.get(accuracyName).getIndividualAccuracies().get(i));

				}
				if (opts.printNonCollapsed) {
					for (String testSequenceName : testSequences.keySet()) {
						System.out.print(testSequenceName + " frames :\t");
						AccuracyCalc.printSeq(testSequences.get(testSequenceName).get(i),
								goldSeqs.get(i), phoneIndexer);
					}

					System.out.print("Actual frames:  \t");
					AccuracyCalc.printSeq(goldSeqs.get(i), goldSeqs.get(i), phoneIndexer);
					for (String accuracyName : accuracies.keySet()) {

						System.out
								.println(accuracyName
										+ ":\t"
										+ accuracies.get(accuracyName).getIndividualAccuracies()
												.get(i));

					}

				}

			}
		}
		LogInfo.end_track();
		TimitTester.staticEmissionAttenuation = tmpEmissionAttenuation;
		TimitTester.staticPrevMeanSmooth = 0.0;
		TimitTester.staticPrevVarSmooth = 0.0;
	}

	/**
	 * @param phoneCounts
	 * @return
	 */
	private static int[] mapCountsToNumberOfSubtates(double[] phoneLength,
			int[] phoneCount, PhoneIndexer phoneIndexer) {
		int[] res = new int[phoneLength.length];
		res[0] = 1;
		res[1] = 1;
		// for (int phone=2; phone<phoneCounts.length; phone++){ // by number of
		// frames
		// if (phoneCounts[phone] < 4000) res[phone] = 2;
		// else if (phoneCounts[phone] < 10000) res[phone] = 4;
		// else if (phoneCounts[phone] < 30000) res[phone] = 8;
		// else res[phone] = 16;
		// }
		double log2 = Math.log(2);
		for (int phone = 2; phone < phoneLength.length; phone++) { // by number of
			// frames
			double perLengthBonus = Math.ceil(Math.log(phoneLength[phone]) / log2);
			double perCountBonus = Math.ceil(Math
					.log((double) phoneCount[phone] / 2000.0)
					/ log2);
			res[phone] = (int) Math.pow(2, perLengthBonus + perCountBonus);
			System.out.println("Phone " + phoneIndexer.get(phone).getLabel()
					+ " will get " + res[phone] + " substates.");
		}
		return res;
	}

}
