package edu.berkeley.nlp.HDPPCFG;

import edu.berkeley.nlp.HDPPCFG.smoothing.NoSmoothing;
import edu.berkeley.nlp.HDPPCFG.smoothing.SmoothAcrossParentBits;
import edu.berkeley.nlp.HDPPCFG.smoothing.SmoothAcrossParentSubstate;
import edu.berkeley.nlp.HDPPCFG.smoothing.Smoother;
import edu.berkeley.nlp.HDPPCFG.sparsity.AllowAllTransitions;
import edu.berkeley.nlp.HDPPCFG.sparsity.HoldBitsOnTrunk;
import edu.berkeley.nlp.HDPPCFG.sparsity.NoChangesOnTrunk;
import edu.berkeley.nlp.HDPPCFG.sparsity.Sparsifier;
import edu.berkeley.nlp.HDPPCFG.vardp.*;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees;
import edu.berkeley.nlp.util.Numberer;
import edu.berkeley.nlp.util.CommandLineUtils;

import java.io.*;
import java.util.*;

import fig.basic.Option;
import fig.basic.Pair;
import fig.basic.OptionsParser;
import fig.basic.IntRef;
import fig.basic.NumUtils;
import fig.basic.FullStatFig;
import fig.basic.ListUtils;
import fig.basic.LogInfo;
import fig.record.Record;
import fig.exec.Execution;
import static fig.basic.LogInfo.track;
import static fig.basic.LogInfo.end_track;
import static fig.basic.LogInfo.logs;

/**
 * Reads in the Penn Treebank and trains grammars.
 *
 * @author Slav Petrov
 */
public class GrammarTrainer {

  // SCALING
  public static final double SCALE = Math.exp(100);
  // Note: e^709 is the largest double java can handle.

  public static boolean VERBOSE = false; 
  public static int HORIZONTAL_MARKOVIZATION = 1; 
  public static int VERTICAL_MARKOVIZATION = 2;
  public static Random RANDOM = new Random(0);
  
	public static class Options {

		@Option(name = "out", required = true, gloss = "Output File for Grammar (Required)")
		public String outFileName;

		@Option(name = "path", gloss = "Path to Corpus (Default: null)")
		public String path = null;

		@Option(name = "SMcycles", gloss = "The number of split&merge iterations (Default: 1)")
		public int numSplits = 1;

		@Option(name = "mergingPercentage", gloss = "Merging percentage (Default: 0.0)")
		public double mergingPercentage = 0.0;

		@Option(name = "baseline", gloss = "Just read of the MLE baseline grammar")
		public boolean baseline = false;

		@Option(name = "lang", gloss = "Language:  1-ENG, 2-CHN, 3-GER, 4-ARB (Default: 1-ENG)")
		public int lang = 1;

		@Option(name = "splitMaxIt", gloss = "Maximum number of EM iterations after splitting (Default: 50)")
		public int splitMaxIterations = 50;

		@Option(name = "splitMinIt", gloss = "Minimum number of EM iterations after splitting (Default: 50)")
		public int splitMinIterations = 50;

		@Option(name = "mergeMaxIt", gloss = "Maximum number of EM iterations after merging (Default: 20)")
		public int mergeMaxIterations = 20;

		@Option(name = "mergeMinIt", gloss = "Minimum number of EM iterations after merging (Default: 20)")
		public int mergeMinIterations = 20;

		@Option(name = "di", gloss = "The number of allowed iterations in which the validation likelihood drops. (Default: 6)")
		public int di = 6;

		@Option(name = "trfr", gloss = "The fraction of the training corpus to keep (Default: 1.0)\n")
		public double trainingFractionToKeep = 1.0;

		@Option(name = "filter", gloss = "Filter rules with prob below this threshold (Default: 1.0e-30)")
		public double filter = 1.0e-30;

		@Option(name = "smooth", gloss = "Smooth parameters by shrinking them towards their parent.")
		public boolean smooth;
		
		@Option(name = "maxL", gloss = "Maximum sentence length (Default <=10000)")
		public int maxSentenceLength = 10000;

		@Option(name = "b", gloss = "LEFT/RIGHT Binarization (Default: RIGHT)")
		public Binarization binarization = Binarization.RIGHT;

 		@Option(name = "noSplit", gloss = "Don't split - just load and continue training an existing grammar (true/false) (Default:false)")
		public boolean noSplit = false;

		@Option(name = "in", gloss = "Input File for Grammar")
		public String inFile = null;

		@Option(name = "randSeed", gloss = "Seed for random number generator")
		public int randSeed = 8;

		@Option(name = "sep", gloss = "Set merging threshold for grammar and lexicon separately (Default: false)")
		public boolean separateMergingThreshold = false;

		@Option(name = "hor", gloss = "Horizontal Markovization (Default: 0)")
		public int horizontalMarkovization = 0;

		@Option(name = "sub", gloss = "Number of substates to split (Default: 2)")
		public short nSubStates = 2;

		@Option(name = "ver", gloss = "Vertical Markovization (Default: 1)")
		public int verticalMarkovization = 1;

		@Option(name = "v", gloss = "Verbose/Quiet (Default: Quiet)\n")
		public boolean verbose = false;

		@Option(name = "r", gloss = "Level of Randomness at init (Default: 1)\n")
		public double randomization = 1.0;

		@Option(name = "sm1", gloss = "Lexicon smoothing parameter 1")
		public double smoothingParameter1 = 0.5;

		@Option(name = "sm2", gloss = "Lexicon smoothing parameter 2)")
		public double smoothingParameter2 = 0.1;

		@Option(name = "spath", gloss = "Whether or not to store the best path info (true/false) (Default: true)")
		public boolean findClosedUnaryPaths = true;

		@Option(name = "unkT", gloss = "Threshold for unknown words (Default: 5)")
		public int unkThresh = 5;
    
		@Option(gloss = "Use variational DP prior (Default: false)")
		public boolean useVarDP = false;

		@Option(gloss = "Use variational DP prior on the lexicon (Default: false)")
		public boolean lexiconUseVarDP = false;

    public enum TopLevelUpdateMethod { none, direct, average, dirichlet, stick };
		@Option(gloss = "Update the top-level parameters directly from the posteriors of substates")
		public TopLevelUpdateMethod topLevelUpdateMethod = TopLevelUpdateMethod.none;

    @Option(gloss = "Number of rounds of iterative top-level and parameter optimization in the M-step")
    public int numMSubIters = 1;

    @Option(gloss = "Maximum number of iterations of optimization to do for top-level distribution")
    public int maxTopLevelIters = 10;

    @Option(gloss = "Verbosity level of top-level optimization")
    public int topLevelVerbose = 1;

    @Option(gloss = "Initialize for grammar g8 to test usefulness of hierarchy")
    public boolean initG8 = false;
    @Option(gloss = "Initialize grammar g7 with the true grammar")
    public boolean initG7 = false;

    @Option(gloss = "Multiply E-step counts by this factor (typically used to downweight likelihood)")
    public double dataFactor = 1;

		@Option(gloss = "Set number of substates manually for these states")
		public ArrayList<Pair<String,Integer>> specialNumSubstates = new ArrayList();

//		@Option(name = "grsm", usage = "Grammar smoothing parameter, in range [0,1].  (Default: 0.1)\n")
//		public double grammarSmoothingParameter = 0.1;

//	@Option(name = "a", usage = "annotate (Default: true)\n")
//	public boolean annotate = true;
	}

	public static void main(String[] args) {
    Options opts;
    VarDPOptions varDPOptions;
    
    OptionsParser op = new OptionsParser(
      "main", opts = new Options(),
      "vardp", varDPOptions = new VarDPOptions(),
      "corpus", Corpus.class);
    if(!op.doParse(args)) System.exit(1);
    op.writeEasy("options-trainer.map");
    LogInfo.msPerLine = 0;
    LogInfo.init();

    String path = opts.path;
    int lang = opts.lang;
    System.out.println("Loading trees from "+path+" and using language "+lang);
           
    double trainingFractionToKeep = opts.trainingFractionToKeep;
    
    int maxSentenceLength = opts.maxSentenceLength;
    System.out.println("Will remove sentences with more than "+maxSentenceLength+" words.");
    
    HORIZONTAL_MARKOVIZATION = opts.horizontalMarkovization;
    VERTICAL_MARKOVIZATION = opts.verticalMarkovization;
    System.out.println("Using horizontal="+HORIZONTAL_MARKOVIZATION+" and vertical="+VERTICAL_MARKOVIZATION+" markovization.");
    
    Binarization binarization = opts.binarization; 
    System.out.println("Using "+ binarization.name() + " binarization.");// and "+annotateString+".");

    double randomness = opts.randomization;
    System.out.println("Using a randomness value of "+randomness);
    
    String outFileName = opts.outFileName;
    if (outFileName==null) {
    	System.out.println("Output File name is required.");
    	System.exit(-1);
    }
    else System.out.println("Using grammar output file "+outFileName+".");
    
    VERBOSE = opts.verbose;
    RANDOM = new Random(opts.randSeed);
    System.out.println("Random number generator seeded at "+opts.randSeed+".");

    boolean manualAnnotation = false;
    boolean baseline = opts.baseline;
    boolean noSplit = opts.noSplit;
    int numSplitTimes = opts.numSplits;
    if (baseline) numSplitTimes = 0;
    String splitGrammarFile = opts.inFile;
    int allowedDroppingIters = opts.di;

    int maxIterations = opts.splitMaxIterations;
    int minIterations = opts.splitMinIterations;
    if (minIterations>0)
    	System.out.println("I will do at least "+minIterations+" iterations.");

    double[] smoothParams = {opts.smoothingParameter1,opts.smoothingParameter2};
    System.out.println("Using smoothing parameters "+smoothParams[0]+" and "+smoothParams[1]);
    

    boolean findClosedUnaryPaths = opts.findClosedUnaryPaths;

    Corpus corpus = null;
   	corpus = new Corpus(path,lang,trainingFractionToKeep,false,false);
    //int nTrees = corpus.getTrainTrees().size();
    //binarize trees
    List<Tree<String>> trainTrees = Corpus.binarizeAndFilterTrees(corpus
				.getTrainTrees(), true, VERTICAL_MARKOVIZATION,
				HORIZONTAL_MARKOVIZATION, maxSentenceLength, binarization, manualAnnotation,
				VERBOSE, false,false);
		List<Tree<String>> validationTrees = Corpus.binarizeAndFilterTrees(corpus
				.getValidationTrees(), true, VERTICAL_MARKOVIZATION,
				HORIZONTAL_MARKOVIZATION, maxSentenceLength, binarization, manualAnnotation,
				VERBOSE, false,false);
    Numberer tagNumberer =  Numberer.getGlobalNumberer("tags");
    
    int nTrees = trainTrees.size();
    System.out.println("There are "+nTrees+" trees in the training set.");
    
		double filter = opts.filter;
		if(filter>0) System.out.println("Will remove rules with prob under "+filter+
				".\nEven though only unlikely rules are pruned the training LL is not guaranteed to increase in every round anymore " +
				"(especially when we are close to converging)." +
				"\nFurthermore it increases the variance because 'good' rules can be pruned away in early stages.");

    short nSubstates = opts.nSubStates;
    short[] numSubStatesArray = initializeSubStateArray(trainTrees, validationTrees, tagNumberer, nSubstates);
    // Create more substates for one category
    for(Pair<String,Integer> p : opts.specialNumSubstates) {
      int t = tagNumberer.number(p.getFirst());
      numSubStatesArray[t] = (short)(int)p.getSecond();
      System.out.println("Special state: " + p.getFirst() + " (" + t + ") has " + p.getSecond() + " substates");
    }
    if (baseline) {
    	short one = 1;
    	Arrays.fill(numSubStatesArray, one);
    	System.out.println("Training just the baseline grammar (1 substate for all states)");
    	randomness = 0.0f;
    }
    
    if (VERBOSE){
	    for (int i=0; i<numSubStatesArray.length; i++){
	    	System.out.println("Tag "+(String)tagNumberer.object(i)+" "+i);
	    }
    }

    // Start training with MLE, switch later to what the option was
    VarDPOptions.EstimationMethod savedEstimationMethod = varDPOptions.estimationMethod;
    Alpha saveTopStateAlpha = new Alpha(varDPOptions.topStateAlpha);
    Alpha saveTopSubstateAlpha = new Alpha(varDPOptions.topSubstateAlpha);
    Alpha saveStateAlpha = new Alpha(varDPOptions.stateAlpha);
    Alpha saveSubstateAlpha = new Alpha(varDPOptions.substateAlpha);
    Alpha saveWordAlpha = new Alpha(varDPOptions.wordAlpha);
    if(varDPOptions.trainFirstWithMLE) {
      Alpha uniformAlpha = new Alpha(1, false);
      // Set to MLE
      varDPOptions.estimationMethod = VarDPOptions.EstimationMethod.map;
      varDPOptions.topStateAlpha.set(uniformAlpha);
      varDPOptions.topSubstateAlpha.set(uniformAlpha);
      varDPOptions.stateAlpha.set(uniformAlpha);
      varDPOptions.substateAlpha.set(uniformAlpha);
      varDPOptions.wordAlpha.set(uniformAlpha);
    }

    //initialize lexicon and grammar
    LexiconInterface lexicon = null, maxLexicon = null, previousLexicon = null;
    Grammar grammar = null, maxGrammar = null, previousGrammar = null;
    double maxLikelihood = Double.NEGATIVE_INFINITY;

    DiscreteDistribCollectionFactory ddcFactory = null;
    // state -> distribution over substates
    TopLevelDistrib topStateDistrib = null;
    TopLevelDistrib[] topSubstateDistribs = null;
    TopLevelWordDistrib[] topWordDistribs = null;
    // state -> (substate -> state)
    UnaryDiscreteDistribCollection[] unaryBackboneDistribs = null;
    // state -> (substate -> state, state)
    BinaryDiscreteDistribCollection[] binaryBackboneDistribs = null;
    DiscreteDistrib[][] unaryBinaryDistribution = null; // for deciding whether to choose a unary or a binary rule

    Record.init("record");
    
    boolean useVarDP = opts.useVarDP;
    if(useVarDP) {
      ddcFactory = varDPOptions.createDDCFactory();
    
      int numStates = numSubStatesArray.length;

      // Create the factory
    	System.out.println("Using Percy's stuff.");
      // Create the top-level distribution over substates for each state
      topStateDistrib = ddcFactory.newTopLevelState(numStates);
      topSubstateDistribs = new TopLevelDistrib[numStates];
      topWordDistribs = new TopLevelWordDistrib[numStates]; // we dont know the number of words here...
      for(int i = 0; i < numStates; i++){
        topSubstateDistribs[i] = ddcFactory.newTopLevelSubstate(numSubStatesArray[i]);
        topWordDistribs[i] = ddcFactory.newTopLevelWord();
      }

      // Create backbone distributions
    	unaryBinaryDistribution = new DiscreteDistrib[numStates][]; 
      unaryBackboneDistribs = new UnaryDiscreteDistribCollection[numStates];
      binaryBackboneDistribs = new BinaryDiscreteDistribCollection[numStates];
      for(int i = 0; i < numStates; i++) {
      	unaryBinaryDistribution[i] = new DiscreteDistrib[numSubStatesArray[i]];
      	for(int j = 0; j < numSubStatesArray[i]; j++) {
      		unaryBinaryDistribution[i][j] = ddcFactory.newRule();
      	}
      	unaryBackboneDistribs[i] =
          ddcFactory.newUnaryState(topStateDistrib,
              numStates, numSubStatesArray[i]);
        binaryBackboneDistribs[i] =
          ddcFactory.newBinaryState(topStateDistrib, topStateDistrib,
              numStates, numStates, numSubStatesArray[i]);
      }
    }

    // EM: iterate until the validation likelihood drops for four consecutive
		// iterations
    int iter = 0;
    int droppingIter = 0;
    
    //  If we are splitting, we load the old grammar and start off by splitting.
    int startSplit = 0;
    if (splitGrammarFile!=null) {
    	System.out.println("Loading old grammar from "+splitGrammarFile);
    	startSplit = 1; // we've already trained the grammar
    	ParserData pData = ParserData.Load(splitGrammarFile);
    	maxGrammar = pData.gr;
    	maxLexicon = (SimpleLexicon)pData.lex;
    	numSubStatesArray = maxGrammar.numSubStates;
      previousGrammar = grammar = maxGrammar;
      previousLexicon = lexicon = maxLexicon;
      Numberer.setNumberers(pData.getNumbs());
      tagNumberer =  Numberer.getGlobalNumberer("tags");
      System.out.println("Loading old grammar complete.");
      if (noSplit){
      	System.out.println("Will NOT split the loaded grammar.");
      	startSplit=0;
      }
    } 
    
    double mergingPercentage = opts.mergingPercentage;
  	boolean separateMergingThreshold = opts.separateMergingThreshold;
    if (mergingPercentage>0){
    	System.out.println("Will merge "+(int)(mergingPercentage*100)+"% of the splits in each round.");
    	System.out.println("The threshold for merging lexical and phrasal categories will be set separately: "+separateMergingThreshold);
    }
    
    StateSetTreeList trainStateSetTrees = new StateSetTreeList(trainTrees, numSubStatesArray, false, tagNumberer,false);
    StateSetTreeList validationStateSetTrees = new StateSetTreeList(validationTrees, numSubStatesArray, false, tagNumberer,false);//deletePC);
    
    // get rid of the old trees
    trainTrees = null;
    validationTrees = null;
    corpus = null;
    System.gc();

    
    // If we're training without loading a split grammar, then we run once without splitting.
    if (splitGrammarFile==null) {
    	System.out.println("Words which appear <= "+opts.unkThresh+" times will be replaced with their unknown word signature.");
    	grammar = new Grammar(numSubStatesArray, findClosedUnaryPaths, null, new NoSmoothing(), null, filter);
			lexicon = new SimpleLexicon(numSubStatesArray,Lexicon.DEFAULT_SMOOTHING_CUTOFF,smoothParams, new NoSmoothing(),filter, trainStateSetTrees,opts.unkThresh);
			if (useVarDP) {
        grammar.useVarDP = true;
        lexicon.setUseVarDP(opts.lexiconUseVarDP);
      }
			int n = 0;
			boolean secondHalf = false;
      // Collect random initial counts (fake E-step)
			for (Tree<StateSet> stateSetTree : trainStateSetTrees) {
				secondHalf = (n++>nTrees/2.0); 
        if(opts.initG7) {
          grammar.tallyUninitializedStateSetTreeG7(stateSetTree);
          ((SimpleLexicon)lexicon).trainTreeG7();
        }
        else if(opts.initG8) {
          grammar.tallyUninitializedStateSetTreeG8(stateSetTree);
          lexicon.trainTree(stateSetTree, randomness, null, secondHalf,false);
        }
        else {
          grammar.tallyUninitializedStateSetTree(stateSetTree);
          lexicon.trainTree(stateSetTree, randomness, null, secondHalf,false);
        }
			}
      lexicon.optimize(ddcFactory, topWordDistribs);
      grammar.optimize(randomness, ddcFactory, topSubstateDistribs, unaryBackboneDistribs, binaryBackboneDistribs, unaryBinaryDistribution);
			//System.out.println(grammar);
			previousGrammar = maxGrammar = grammar; //needed for baseline - when there is no EM loop
			previousLexicon = maxLexicon = lexicon;
	  }

    if (opts.smooth){
    	System.out.println("Will smooth parameters by shrinking them towards their parent.");
      Smoother grSmoother = new SmoothAcrossParentSubstate(0.01);
      Smoother lexSmoother = new SmoothAcrossParentSubstate(0.1);
      maxGrammar.setSmoother(grSmoother);
      maxLexicon.setSmoother(lexSmoother);
    }

    // the main loop: split and train the grammar
    for (int splitIndex = startSplit; splitIndex < numSplitTimes*3; splitIndex++) {

    	// now do either a merge or a split and the end a smooth
    	// on odd iterations merge, on even iterations split
    	String opString = "";
    	if (splitIndex%3==2){//(splitIndex==numSplitTimes*2){
    		continue;
//    		if (opts.smooth.equals("NoSmoothing")) continue;
//    		System.out.println("Setting smoother for grammar and lexicon.");
//        Smoother grSmoother = new SmoothAcrossParentBits(0.01,maxGrammar.splitTrees);
//        Smoother lexSmoother = new SmoothAcrossParentBits(0.1,maxGrammar.splitTrees);
//        maxGrammar.setSmoother(grSmoother);
//        maxLexicon.smoother = lexSmoother;
//        minIterations = maxIterations = 10;
//        opString = "smoothing";
    	}
    	else if (splitIndex%3==0) {
    		// the case where we split
    		opString = "splitting";
        maxIterations = opts.splitMaxIterations;
        minIterations = opts.splitMinIterations;
//    		continue;
//    		if (opts.noSplit) 
//    		System.out.println("Before splitting, we have a total of "+maxGrammar.totalSubStates()+" substates.");
//    		CorpusStatistics corpusStatistics = new CorpusStatistics(tagNumberer,trainStateSetTrees);
//				int[] counts = corpusStatistics.getSymbolCounts();
//
//    		maxGrammar = maxGrammar.splitAllStates(randomness, counts, allowMoreSubstatesThanCounts);
//    		maxLexicon = maxLexicon.splitAllStates(counts, allowMoreSubstatesThanCounts);
//        Smoother grSmoother = new NoSmoothing();
//        Smoother lexSmoother = new NoSmoothing();
//        maxGrammar.setSmoother(grSmoother);
////        maxLexicon.smoother = lexSmoother;
//    		System.out.println("After splitting, we have a total of "+maxGrammar.totalSubStates()+" substates.");
//    		System.out.println("Rule probabilities are NOT normalized in the split, therefore the training LL is not guaranteed to improve between iteration 0 and 1!");
    	}
    	else {
    		if(opts != null) continue; // Get rid of unreachable statement warning
//    		if (mergingPercentage==0) 
//    		// the case where we merge
//    		double[][] mergeWeights = GrammarMerger.computeMergeWeights(maxGrammar, maxLexicon,trainStateSetTrees);
//    		double[][][] deltas = GrammarMerger.computeDeltas(maxGrammar, maxLexicon, mergeWeights, trainStateSetTrees);
//    		boolean[][][] mergeThesePairs = GrammarMerger.determineMergePairs(deltas,separateMergingThreshold,mergingPercentage,maxGrammar);
//    		
//    		grammar = GrammarMerger.doTheMerges(maxGrammar, maxLexicon, mergeThesePairs, mergeWeights);
//    		short[] newNumSubStatesArray = grammar.numSubStates;
//  			trainStateSetTrees = new StateSetTreeList(trainStateSetTrees, newNumSubStatesArray, false);
//  			validationStateSetTrees = new StateSetTreeList(validationStateSetTrees, newNumSubStatesArray, false);
//
//    		// retrain lexicon to finish the lexicon merge (updates the unknown words model)...
//    		lexicon = new Lexicon(newNumSubStatesArray,Lexicon.DEFAULT_SMOOTHING_CUTOFF, maxLexicon.smooth, maxLexicon.smoother, maxLexicon.threshold);
//    		boolean updateOnlyLexicon = true;
//    		double trainingLikelihood = GrammarTrainer.doOneEStep(grammar, maxLexicon, null, lexicon, trainStateSetTrees, updateOnlyLexicon);
////    		System.out.println("The training LL is "+trainingLikelihood);
//    		lexicon.optimize();//Grammar.RandomInitializationType.INITIALIZE_WITH_SMALL_RANDOMIZATION);   // M Step    		
//
//    		GrammarMerger.printMergingStatistics(maxGrammar, grammar);
//    		opString = "merging";
//    		maxGrammar = grammar; maxLexicon = lexicon;
//    		maxIterations = opts.mergeMaxIterations;
//    		minIterations = opts.mergeMinIterations;
    	}
    	// update the substate dependent objects
    	if (splitIndex==startSplit){
    		previousGrammar = grammar = maxGrammar;
	  		previousLexicon = lexicon = maxLexicon;
				droppingIter = 0;
				numSubStatesArray = grammar.numSubStates;
				trainStateSetTrees = new StateSetTreeList(trainStateSetTrees, numSubStatesArray, false);
				validationStateSetTrees = new StateSetTreeList(validationStateSetTrees, numSubStatesArray, false);
    	}
  		maxLikelihood = calculateLogLikelihood(maxGrammar, maxLexicon, validationStateSetTrees, null);
//  		maxLikelihood = calculateLogLikelihood(maxGrammar, maxLexicon, trainStateSetTrees);
  		System.out.println("After "+opString+" in the " + (splitIndex/3+1) + "th round, we get a validation likelihood of " + maxLikelihood);
  		iter = 0;
     	//the inner loop: train the grammar via EM until validation likelihood reliably drops
    	do {
        if(maxIterations == 0) break;
    		iter += 1;
        track("Iteration %d", iter-1);
    		//System.out.println("Beginning iteration "+(iter-1)+":");
        Record.begin("iteration", iter);

        if(iter == maxIterations/2 && varDPOptions.trainFirstWithMLE) {
          logs("Switching to " + savedEstimationMethod);
          varDPOptions.estimationMethod = savedEstimationMethod;
          ddcFactory.estimationMethodChanged();
          varDPOptions.topStateAlpha.set(saveTopStateAlpha);
          varDPOptions.topSubstateAlpha.set(saveTopSubstateAlpha);
          varDPOptions.stateAlpha.set(saveStateAlpha);
          varDPOptions.substateAlpha.set(saveSubstateAlpha);
          varDPOptions.wordAlpha.set(saveWordAlpha);
        }
        
  			// 1) Compute the validation likelihood of the previous iteration
  			track("Calculating validation likelihood...");
        IntRef validationUnparsable = new IntRef();
  			double validationLikelihood = calculateLogLikelihood(previousGrammar, previousLexicon, validationStateSetTrees, validationUnparsable);  // The validation LL of previousGrammar/previousLexicon
  			logs("done: "+validationLikelihood);
        end_track();
        Record.add("validationLikelihood", validationLikelihood);

  			// 2) Perform the E step while computing the training likelihood of the previous iteration
  			track("Calculating training likelihood...");
//  			grammar = new Grammar(grammar.numSubStates, grammar.findClosedPaths, grammar.smoother, grammar, grammar.threshold);
//  			lexicon = new Lexicon(lexicon.numSubStates,	Lexicon.DEFAULT_SMOOTHING_CUTOFF, lexicon.smooth, lexicon.smoother, lexicon.threshold);
  			lexicon = maxLexicon.copyLexicon();
  			grammar = new Grammar(numSubStatesArray, findClosedUnaryPaths, null, grammar.smoother, grammar, filter);
  			if (useVarDP) {
          grammar.useVarDP = true; 
          lexicon.setUseVarDP(opts.lexiconUseVarDP);
        }

  			boolean updateOnlyLexicon = false;
        IntRef trainingUnparsable = new IntRef();
  			double trainingLikelihood = doOneEStep(previousGrammar,previousLexicon,grammar,lexicon,trainStateSetTrees,updateOnlyLexicon, trainingUnparsable, opts.dataFactor);  // The training LL of previousGrammar/previousLexicon
  			logs("done: "+trainingLikelihood);
        end_track();
        Record.add("trainingLikelihood", trainingLikelihood);

        fig.basic.IOUtils.printLinesEasy("output-trainer.map",
            fig.basic.ListUtils.newList(
              "currIter\t"+iter,
              "trainingLikelihood\t"+trainingLikelihood,
              "validationLikelihood\t"+validationLikelihood,
              "trainingUnparsable\t"+trainingUnparsable,
              "validationUnparsable\t"+validationUnparsable));

  			// 3) Perform the M-Step
        for(int mIter = 0; mIter < opts.numMSubIters; mIter++) {
          track("M-step: sub iteration %d/%d", mIter, opts.numMSubIters);
          lexicon.optimize(ddcFactory, topWordDistribs);   // M Step
          grammar.optimize(0, ddcFactory, topSubstateDistribs, unaryBackboneDistribs, binaryBackboneDistribs, unaryBinaryDistribution);  // M Step

          // Optimize top-level
          if(useVarDP && varDPOptions.estimationMethod != VarDPOptions.EstimationMethod.mle) {
            logs("Optimizing top-level: " + opts.topLevelUpdateMethod);
            double[][] counts = null;
            switch(opts.topLevelUpdateMethod) {
              case none:
                break;
              case direct:
                counts = GrammarMerger.computeMergeWeights(grammar,
                    lexicon, trainStateSetTrees);
                for(int s = 0; s < numSubStatesArray.length; s++) {
                  for(int j = 0; j < counts[s].length; j++)
                    assert NumUtils.isFinite(counts[s][j]) : s + "," + j + ": " + fig.basic.Fmt.D(counts[s]);
                  topSubstateDistribs[s].updateDirect(counts[s]);
                }
                break;
              case average:
                counts = new TopLevelAverager(topSubstateDistribs,
                  grammar.unaryRuleMap.keySet(),
                  grammar.binaryRuleMap.keySet()).getCounts();
                for(int s = 0; s < numSubStatesArray.length; s++)
                  topSubstateDistribs[s].updateDirect(counts[s]);
                break;
              case dirichlet:
              case stick:
                // Try evaluating the averager with respect to the objective function
                counts = new TopLevelAverager(topSubstateDistribs,
                  grammar.unaryRuleMap.keySet(),
                  grammar.binaryRuleMap.keySet()).getCounts();
                TopLevelOptimizationProblem p = new TopLevelOptimizationProblem(topSubstateDistribs,
                  grammar.unaryRuleMap.keySet(),
                  grammar.binaryRuleMap.keySet(),
                  opts.topLevelUpdateMethod == Options.TopLevelUpdateMethod.stick);
                for(int s = 0; s < numSubStatesArray.length; s++)
                  Utils.normalize(counts[s]);
                p.eval(counts);

                new TopLevelOptimizationProblem(topSubstateDistribs,
                  grammar.unaryRuleMap.keySet(),
                  grammar.binaryRuleMap.keySet(),
                  opts.topLevelUpdateMethod == Options.TopLevelUpdateMethod.stick).optimize(opts.maxTopLevelIters, opts.topLevelVerbose);
                break;
              default: throw new RuntimeException("Unknown case");
            }

            Record.begin("topLevel");
            FullStatFig totalEntropy = new FullStatFig();
            for(int s = 0; s < numSubStatesArray.length; s++) {
              double[] probs = topSubstateDistribs[s].getProbs();
              double entropy = NumUtils.entropy(probs);
              Record.begin("state", tagNumberer.object(s));
              Record.add("entropy",  entropy);
              Record.addArray("substate", ListUtils.toObjArray(probs));
              //double[] sortedProbs = (double[])probs.clone();
              //Arrays.sort(sortedProbs);
              //Record.addArray("sortedSubstate", ListUtils.toObjArray(sortedProbs));
              Record.end();
              totalEntropy.add(entropy);
            }
            Record.addEmbed("entropy", totalEntropy);
            Record.end();
          }
          end_track();
        }
  			
  			// 4) Check whether previousGrammar/previousLexicon was in fact better than the best
  			if(iter<minIterations || validationLikelihood >= maxLikelihood) {
  				maxLikelihood = validationLikelihood;
  				maxGrammar = previousGrammar;
  				maxLexicon = previousLexicon;
  				droppingIter = 0;
  			} else { droppingIter++; }

  			// 5) advance the 'pointers'
    		previousGrammar = grammar;
     		previousLexicon = lexicon;
        Record.end();
        Record.flush();

        end_track();
    	} while ((droppingIter < allowedDroppingIters) && (!baseline) && (iter<maxIterations));
			
			// Dump a grammar file to disk from time to time
			ParserData pData = new ParserData(maxLexicon, maxGrammar, Numberer.getNumberers(), numSubStatesArray, VERTICAL_MARKOVIZATION, HORIZONTAL_MARKOVIZATION, binarization);
      String outTmpName = outFileName + "_"+ (splitIndex/3+1)+"_"+opString+".gr";
			System.out.println("Saving grammar to "+outTmpName+".");
      if (pData.Save(outTmpName)) System.out.println("Saving successful.");
      else System.out.println("Saving failed!");
      pData = null;

    }
    
    // The last grammar/lexicon has not yet been evaluated. Even though the validation likelihood
    // has been dropping in the past few iteration, there is still a chance that the last one was in
    // fact the best so just in case we evaluate it.
    System.out.print("Calculating last validation likelihood...");
    double validationLikelihood = calculateLogLikelihood(grammar, lexicon, validationStateSetTrees, null);
    System.out.println("done.\n  Iteration "+iter+" (final) gives validation likelihood "+validationLikelihood);
    if (validationLikelihood > maxLikelihood) {
      maxLikelihood = validationLikelihood;
      maxGrammar = previousGrammar;
      maxLexicon = previousLexicon;
    }
    
    ParserData pData = new ParserData(maxLexicon, maxGrammar, Numberer.getNumberers(), numSubStatesArray, VERTICAL_MARKOVIZATION, HORIZONTAL_MARKOVIZATION, binarization);

    System.out.println("Saving grammar to "+outFileName+".");
    System.out.println("It gives a validation data log likelihood of: "+maxLikelihood);
    if (pData.Save(outFileName)) System.out.println("Saving successful.");
    else System.out.println("Saving failed!");
    Record.finish();

    System.exit(0);
  }

	/**
	 * @param previousGrammar
	 * @param previousLexicon
	 * @param grammar
	 * @param lexicon
	 * @param trainStateSetTrees
	 * @return
	 */
	public static double doOneEStep(Grammar previousGrammar, LexiconInterface previousLexicon, Grammar grammar, LexiconInterface lexicon, StateSetTreeList trainStateSetTrees,
			boolean updateOnlyLexicon, IntRef numUnparsable, double dataFactor) {
		boolean secondHalf = false;
		ArrayParser parser = new ArrayParser(previousGrammar,previousLexicon);
		double trainingLikelihood = 0;
		int n = 0;
		int nTrees = trainStateSetTrees.size();
    int unparsable = 0;
    grammar.clearCounts(); // Clear counts
		for (Tree<StateSet> stateSetTree : trainStateSetTrees) {
      if(new File("kill").exists()) throw new RuntimeException("GrammarTrainer killed by kill file");

			secondHalf = (n++>nTrees/2.0); 
			boolean noSmoothing = true, debugOutput = false;
			parser.doInsideOutsideScores(stateSetTree,noSmoothing,debugOutput);                    // E Step
			double ll = stateSetTree.getLabel().getIScore(0);
			ll = Math.log(ll) + (100*stateSetTree.getLabel().getIScale());//System.out.println(stateSetTree);
			if ((Double.isInfinite(ll) || Double.isNaN(ll))) {
				if (VERBOSE){
					System.out.println("Training sentence "+n+" is given "+ll+" log likelihood!");
					System.out.println("Root iScore "+ stateSetTree.getLabel().getIScore(0)+" scale "+stateSetTree.getLabel().getIScale());
				}
        unparsable++;
			}
			else {
			  ((SimpleLexicon)lexicon).trainTree(stateSetTree, -1, previousLexicon, secondHalf,noSmoothing, dataFactor);
				if (!updateOnlyLexicon) grammar.tallyStateSetTree(stateSetTree, previousGrammar, dataFactor);      // E Step
				trainingLikelihood  += ll;  // there are for some reason some sentences that are unparsable 
			}
      if (unparsable>0) System.out.print("Number of unparsable trees: "+unparsable+".");
      if(numUnparsable != null) numUnparsable.value = unparsable;
		}
		return trainingLikelihood;
	}


	/**
	 * @param maxGrammar
	 * @param maxLexicon
	 * @param validationStateSetTrees
	 * @return
	 */
	public static double calculateLogLikelihood(Grammar maxGrammar, LexiconInterface maxLexicon, StateSetTreeList validationStateSetTrees, IntRef numUnparsable) {
		ArrayParser parser = new ArrayParser(maxGrammar, maxLexicon);
		int unparsable = 0;
		double maxLikelihood = 0;
		for (Tree<StateSet> stateSetTree : validationStateSetTrees) {
			parser.doInsideScores(stateSetTree,false,false);  // Only inside scores are needed here
			double ll = stateSetTree.getLabel().getIScore(0);
			ll = Math.log(ll) + (100*stateSetTree.getLabel().getIScale());
			if (Double.isInfinite(ll) || Double.isNaN(ll)) { 
				unparsable++;
				//printBadLLReason(stateSetTree, lexicon);
			}
			else maxLikelihood += ll;  // there are for some reason some sentences that are unparsable 
		}
		if (unparsable>0) System.out.print("Number of unparsable trees: "+unparsable+".");
    if(numUnparsable != null) numUnparsable.value = unparsable;
		return maxLikelihood;
	}

	/**
	 * @param stateSetTree
	 */
	public static void printBadLLReason(Tree<StateSet> stateSetTree, Lexicon lexicon) {
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
				System.out.println("LEXICON PROBLEM ON STATE " + stateSet.getState()+" word "+word);
				System.out.println("  word "+lexicon.wordCounter.getCount(stateSet.getWord()));
				for (int i=0; i<stateSet.numSubStates(); i++) {
					System.out.println("  tag "+lexicon.tagCounter[stateSet.getState()][i]);
					System.out.println("  word/state/sub "+lexicon.wordToTagCounters[stateSet.getState()].get(stateSet.getWord())[i]);
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
   * This function probably doesn't belong here, but because it should be called
   * after {@link #updateStateSetTrees}, Leon left it here.
   * 
   * @param trees Trees which have already had their inside-outside probabilities calculated,
   * as by {@link #updateStateSetTrees}.
   * @return The log likelihood of the trees.
   */
  public static double logLikelihood(List<Tree<StateSet>> trees, boolean verbose) {
    double likelihood = 0, l=0;
    for (Tree<StateSet> tree : trees) {
      l = tree.getLabel().getIScore(0);
      if (verbose) System.out.println("LL is "+l+".");
      if (Double.isInfinite(l) || Double.isNaN(l)){
        System.out.println("LL is not finite.");
      }
      else {
        likelihood += l;
      }
    }
    return likelihood;
  }
  
  
  /**
   * This updates the inside-outside probabilities for the list of trees using the parser's
   * doInsideScores and doOutsideScores methods.
   * 
   * @param trees A list of binarized, annotated StateSet Trees.
   * @param parser The parser to score the trees.
   */
  public static void updateStateSetTrees (List<Tree<StateSet>> trees, ArrayParser parser) {
    for (Tree<StateSet> tree : trees) {
      parser.setRootOutsideScore(tree);
      parser.doInsideScores(tree,false,false);
      parser.doOutsideScores(tree);
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
  
  public static short[] initializeSubStateArray(List<Tree<String>> trainTrees,
			List<Tree<String>> validationTrees, Numberer tagNumberer, short nSubStates){
//			boolean dontSplitTags) {
		// first generate unsplit grammar and lexicon
    short[] nSub = new short[2];
    nSub[0] = 1;
    nSub[1] = nSubStates;
    //List<Tree<StateSet>> trainStateSetTrees = convertAndInitializeTrees(trainTrees, nSub, true, tagNumberer);
    //List<Tree<StateSet>> validationStateSetTrees = convertAndInitializeTrees(validationTrees, nSub, true, tagNumberer);
    StateSetTreeList trainStateSetTrees = new StateSetTreeList(trainTrees,
				nSub, true, tagNumberer,false);
    // do the validation set so that the numberer sees all tags and we can
		// allocate big enough arrays
		// note: although this variable is never read, this constructor adds the
		// validation trees into the tagNumberer as a side effect, which is
		// important
		@SuppressWarnings("unused")
		StateSetTreeList validationStateSetTrees = new StateSetTreeList(
				validationTrees, nSub, true, tagNumberer,false);

    short numStates = (short)tagNumberer.total();
    short[] nSubStateArray = new short[numStates];
  	short two = nSubStates;
  	Arrays.fill(nSubStateArray, two);
  	//System.out.println("Everything is split in two except for the root.");
  	nSubStateArray[0] = 1; // that's the ROOT
    return nSubStateArray;
  }
}
