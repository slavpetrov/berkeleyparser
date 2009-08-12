package edu.berkeley.nlp.HDPPCFG;

import edu.berkeley.nlp.HDPPCFG.smoothing.NoSmoothing;
import edu.berkeley.nlp.HDPPCFG.smoothing.SmoothAcrossParentBits;
import edu.berkeley.nlp.HDPPCFG.smoothing.SmoothAcrossParentSubstate;
import edu.berkeley.nlp.HDPPCFG.smoothing.Smoother;
import edu.berkeley.nlp.HDPPCFG.sparsity.AllowAllTransitions;
import edu.berkeley.nlp.HDPPCFG.sparsity.HoldBitsOnTrunk;
import edu.berkeley.nlp.HDPPCFG.sparsity.NoChangesOnTrunk;
import edu.berkeley.nlp.HDPPCFG.sparsity.Sparsifier;
import edu.berkeley.nlp.HDPPCFG.vardp.DirichletCollectionFactory;
import edu.berkeley.nlp.HDPPCFG.vardp.DiscreteDistrib;
import edu.berkeley.nlp.HDPPCFG.vardp.DiscreteDistribCollectionFactory;
import edu.berkeley.nlp.HDPPCFG.vardp.MLECollectionFactory;
import edu.berkeley.nlp.HDPPCFG.vardp.TopLevelDistrib;
import edu.berkeley.nlp.HDPPCFG.vardp.TopLevelWordDistrib;
import edu.berkeley.nlp.HDPPCFG.vardp.UnaryDiscreteDistribCollection;
import edu.berkeley.nlp.HDPPCFG.vardp.BinaryDiscreteDistribCollection;
import edu.berkeley.nlp.HDPPCFG.vardp.VarDPOptions;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees;
import edu.berkeley.nlp.util.Numberer;
import edu.berkeley.nlp.util.CommandLineUtils;

import java.util.*;
import fig.record.Record;

/**
 * Reads in the Penn Treebank and generates N_GRAMMARS different grammars.
 *
 * @author Slav Petrov
 */
public class OldGrammarTrainer {

	public static Random RANDOM;
  // SCALING
  public static final double SCALE = Math.exp(100);
  // Note: e^709 is the largest double java can handle.

  public static boolean VERBOSE = false; 
  public static int HORIZONTAL_MARKOVIZATION = 1; 
  public static int VERTICAL_MARKOVIZATION = 2;
  
  public static void main(String[] args) {
    if (args.length<1) {
      System.out.println(
          "usage: java GrammarTrainer\n" +
          "               -o      Output File for Grammar (Required)\n" +
      		"               -path   Path to Corpus (Default: null)\n" +
      		"               -lang   Language:  1-ENG, 2-CHN, 3-GER, 4-ARB (Default: 1)\n" +
          "               -chsh   If this is enabled, then we train on a short segment of\n" +
          "                       the Chinese treebank (Default: false)" +
      		"               -trfr   The fraction of the training corpus to keep (Default: 1.0)\n" +
          "               -a      Annotate/Noannotate (Default: Annotate)\n"+
      		"               -b      Left/Right/Head/Parent Binarization (Default: Right)\n"+
          "               -r      Level of Randomness at init (Default: 20)\n"+
          "               -sub    Initial number of substates (Default: 2, overwritten by -x flag)\n"+ 
          "          			-s      SymbolCount or ParentCount for Mappings (Default: Symbol)\n"+
          "          			-hor    Horizontal Markovization (Default: 0)\n"+
          "          			-ver    Vertical Markovization (Default: 1)\n"+
          "          			-v      Verbose/Quiet (Default: Quiet)\n"+
          "               -x      Baseline\n" +
          "               -sp     The number of times to split the grammar substates;\n"+
          "                       each time, each substate is split into two new ones.  (Default: 0)\n" +
          "               -spg    A grammar to load and split (the number of splitting times is given\n" +
          "                       with the -sp option\n" +
          "               -di     The number of allowed iterations in which the validation likelihood\n" +
          "                       drops. (Default: 6)\n" +
          "               -rinit  The initialization style (small or MMT) (Default is small=0.04)\n"+
          "                       This controls whether the initial randomness is added or multiplied\n" +
          "                       That is, like our intuition, or like the paper.  If MMT (like the\n"+
          "                       paper, the randomness parameter does not matter.\n" +
          "               -sm1    Smoothing parameter 1\n"+
          "               -sm2    Smoothing parameter 2\n" +
          "               -path   Whether or not to store the best path info (true/false) (Default: true)\n" +
          "               -sparse Type of sparsity enforced in substate transitions.  Current options\n" +
          "                       are 'AllowAllTransitions', 'NoChangesOnTrunk', and 'HoldBitsOnTrunk'.\n" +
          "               -bits   Number of bits to hold when using 'HoldBitsOnTrunk'. (Default: 2)\n" +
          "               -smooth Type of grammar smoothing used.  This takes the maximum likelihood rule\n" +
          "                       probabilities and smooths them with each other.  Current options are\n" +
          "                       'NoSmoothing', 'SmoothAcrossParentSubstate', and 'SmoothAcrossParentBits'.\n" +
          "               -grsm   Grammar smoothing parameter, in range [0,1].  (Default: 0.1)\n" +
          "               -maxIt  Maximum number of EM iterations (Default: 50)"+
          "               -minIt  Minimum number of EM iterations (Default: 0)"+
          "               -dL     Delete labels? (true/false) (Default: false)"+
          "               -dPC    Delete phrasal categories? (true/false) (Default: false)"+
          "               -noSplit Don't split - just load and continue training an existing grammar (true/false) (Default:false)"+
          "								-f		  Filter rules with prob under f (Default: -1)"+          
          "								-vardp  Use variational DP prior (Default: false)"+
          "               -maxL   Maximum sentence length (Default <=10000)"+
          "               -rSeed  Seed for random number generator (Default: 1)"+
          "               -unkT   Threshold for unknown words (Default: 5)"+
          ""
          );
      System.exit(2);
    }
    
    // provide feedback on command-line arguments
    System.out.print("Running with arguments:  ");
    for (String arg : args) {
    	System.out.print(" '"+arg+"'");
    }
    System.out.println("");

    // parse the input arguments
    Map<String, String> input = CommandLineUtils.simpleCommandLineParser(args);
    
    String path = CommandLineUtils.getValueOrUseDefault(input, "-path", null);
    boolean dummy = path==null;
    int lang = Integer.parseInt(CommandLineUtils.getValueOrUseDefault(input, "-lang", "1"));
    System.out.println("Loading trees from "+path+" and using language "+lang);
        
    boolean chineseShort = Boolean.parseBoolean(CommandLineUtils
				.getValueOrUseDefault(input, "-chsh", "false"));
    
    double trainingFractionToKeep = Double.parseDouble(CommandLineUtils
				.getValueOrUseDefault(input, "-trfr", "1.0"));

    String annotateString = CommandLineUtils.getValueOrUseDefault(input, "-a", "annotate"); 
    boolean annotate=false;
    if (annotateString.equals("annotate")) {
      annotate=true;
    } else if (annotateString.equals("noannotate")) {
      annotate=false;
    } else {
      System.out.println("annotation argument "+annotateString+" makes no sense to me");
      System.exit(1);
    }
    
    int maxSentenceLength = Integer.parseInt(CommandLineUtils.getValueOrUseDefault(input, "-maxL", "10000"));
    System.out.println("Will remove sentences with more than "+maxSentenceLength+" words.");
    
    int rSeed = Integer.parseInt(CommandLineUtils.getValueOrUseDefault(input, "-rSeed", "1"));
    RANDOM = new Random(rSeed);
    
    HORIZONTAL_MARKOVIZATION = Integer.parseInt(CommandLineUtils.getValueOrUseDefault(input, "-hor", "0"));
    VERTICAL_MARKOVIZATION = Integer.parseInt(CommandLineUtils.getValueOrUseDefault(input, "-ver", "1"));
    System.out.println("Using horizontal="+HORIZONTAL_MARKOVIZATION+" and vertical="+VERTICAL_MARKOVIZATION+" markovization.");
    
    String binarizationString = CommandLineUtils.getValueOrUseDefault(input, "-b", "right");
    Binarization binarization = Binarization.RIGHT;
    if (binarizationString.equals("left")) 
    	binarization = Binarization.LEFT;
    else if (binarizationString.equals("right"))
    	binarization = Binarization.RIGHT;
    else if (binarizationString.equals("parent"))
    	binarization = Binarization.PARENT;
    else if (binarizationString.equals("head"))
    	binarization = Binarization.HEAD;
    else {
    	throw new Error("didn't understand -b "+binarizationString);
    }
    System.out.println("Using "+ binarization.name() + " binarization and "+annotateString+".");

    double randomness = Double.parseDouble(CommandLineUtils.getValueOrUseDefault(input, "-r", "4"));
    System.out.println("Using a randomness value of "+randomness);
    
    
    String outFileName = CommandLineUtils.getValueOrUseDefault(input, "-o", null);
    if (outFileName==null) {
    	System.out.println("Output File name is required.");
    	System.exit(-1);
    }
    else System.out.println("Using grammar output file "+outFileName+".");
    
    String verbosityString = CommandLineUtils.getValueOrUseDefault(input, "-v", "quiet");
    boolean verbose = (verbosityString.equals("verbose"));
    VERBOSE = verbose;
    System.out.println("Using verbosity of "+verbosityString+".");

    boolean manualAnnotation = false;
    boolean baseline = CommandLineUtils.getValueOrUseDefault(input, "-x", "").equals("baseline");
    boolean noSplit = CommandLineUtils.getValueOrUseDefault(input, "-noSplit", "false").equals("true");
    int numSplitTimes = Integer.parseInt(CommandLineUtils.getValueOrUseDefault(input,"-sp","0"));
    String splitGrammarFile = CommandLineUtils.getValueOrUseDefault(input,"-spg",null);
    int allowedDroppingIters = Integer.parseInt(CommandLineUtils.getValueOrUseDefault(input,"-di","10"));
    
    boolean deleteLabels = CommandLineUtils.getValueOrUseDefault(input, "-dL", "").equals("true");
    if (deleteLabels) System.out.println("All Labels will be deleted!");
    
    boolean deletePC = CommandLineUtils.getValueOrUseDefault(input, "-dPC", "").equals("true");
    if (deletePC) System.out.println("Phrasal categories will be deleted!");

    int maxIterations = Integer.parseInt(CommandLineUtils.getValueOrUseDefault(input,"-maxIt","50"));
    int minIterations = Integer.parseInt(CommandLineUtils.getValueOrUseDefault(input,"-minIt","0"));
    if (minIterations>0)
    	System.out.println("I will do at least "+minIterations+" iterations.");

    double smooth1 = Double.parseDouble(CommandLineUtils.getValueOrUseDefault(input,"-sm1","0.5"));
    double smooth2 = Double.parseDouble(CommandLineUtils.getValueOrUseDefault(input,"-sm2","0.1"));
    double[] smoothParams = {smooth1,smooth2};
    System.out.println("Using smoothing parameters "+smoothParams[0]+" and "+smoothParams[1]);
    
    int bits = Integer.parseInt(CommandLineUtils.getValueOrUseDefault(input,
				"-bits", "2"));
        
    String sparsifierStr = CommandLineUtils.getValueOrUseDefault(input,
				"-sparse", "AllowAllTransitions");
    Sparsifier sparsifier = null;
    boolean allowMoreSubstatesThanCounts = false;
    if (sparsifierStr.equals("AllowAllTransitions")) {
    	sparsifier = new AllowAllTransitions();
    	allowMoreSubstatesThanCounts = false;
    }
    else if (sparsifierStr.equals("NoChangesOnTrunk")) {
    	sparsifier = new NoChangesOnTrunk();
    	allowMoreSubstatesThanCounts = true;
    }
    else if (sparsifierStr.equals("HoldBitsOnTrunk")) {
    	// NOTE: the "HoldBitsOnTrunk" sparsifier requires having its "nSubStates" array
    	// set whenever that changes.  There is therefore code for this below.  I know
    	// it's suboptimal, but it keeps the signature of Sparsifier from changing, so
    	// we can reuse old grammars.  --Leon
    	System.out.println("Holding "+bits+" bits");
    	sparsifier = new HoldBitsOnTrunk(bits);
    	allowMoreSubstatesThanCounts = true;
    }
    else
    	throw new Error("I didn't understand the type of sparsifier '"+sparsifierStr+"'");
    System.out.println("Using sparsifier "+sparsifierStr);
    
    double grammarSmoothing = Double.parseDouble(CommandLineUtils.getValueOrUseDefault(input,
    		"-grsm","0.1"));
    
    boolean findClosedUnaryPaths = Boolean.parseBoolean(CommandLineUtils.getValueOrUseDefault(input,"-path","true"));
    
    Corpus corpus = null;
   	corpus = new Corpus(path,lang,trainingFractionToKeep,chineseShort,false);
    //int nTrees = corpus.getTrainTrees().size();
    //binarize trees
    List<Tree<String>> trainTrees = Corpus.binarizeAndFilterTrees(corpus
				.getTrainTrees(), annotate, VERTICAL_MARKOVIZATION,
				HORIZONTAL_MARKOVIZATION, maxSentenceLength, binarization, manualAnnotation,
				VERBOSE, deleteLabels, deletePC);
		List<Tree<String>> validationTrees = Corpus.binarizeAndFilterTrees(corpus
				.getValidationTrees(), annotate, VERTICAL_MARKOVIZATION,
				HORIZONTAL_MARKOVIZATION, maxSentenceLength, binarization, manualAnnotation,
				VERBOSE, deleteLabels, deletePC);
    Numberer tagNumberer =  Numberer.getGlobalNumberer("tags");
    
    int nTrees = trainTrees.size();
    System.out.println("There are "+nTrees+" trees in the training set.");
    
    short nSubstates = Short.parseShort(CommandLineUtils.getValueOrUseDefault(input, "-sub", "2"));
    System.out.println("Number of substates: "+nSubstates);
    
		double filter = Double.parseDouble(CommandLineUtils.getValueOrUseDefault(input, "-f","-1"));
		if(filter>0) System.out.println("Will remove rules with prob under "+filter);

    //{1, 1, 2, 1, 1, 1, 1};//
    short[] numSubStatesArray = initializeSubStateArray(trainTrees, validationTrees, tagNumberer, nSubstates, deletePC);
    if (baseline) {
    	short one = 1;
    	Arrays.fill(numSubStatesArray, one);
    	System.out.println("Training just the baseline grammar (1 substate for all states)");
    	randomness = 0.0f;
    }
    else if (splitGrammarFile==null){
    	// initializeSubStateArray did it for us
    }
    
    for (int i=0; i<numSubStatesArray.length; i++){
    	System.out.println("Tag "+(String)tagNumberer.object(i)+" "+i+" nSubstates "+numSubStatesArray[i]);
    }
    /*
    StateSetTreeList trainStateSetTrees = new StateSetTreeList(trainTrees, numSubStatesArray, false, tagNumberer);
    StateSetTreeList validationStateSetTrees = new StateSetTreeList(validationTrees, numSubStatesArray, false, tagNumberer);
    
    // get rid of the old trees
    trainTrees = null;
    validationTrees = null;
    corpus = null;
    System.gc();
    */
    //initialize lexicon and grammar
    LexiconInterface lexicon = null;
    Grammar grammar = null;
    Grammar maxGrammar = null;
    LexiconInterface maxLexicon = null;
    Grammar previousGrammar = null;
    LexiconInterface previousLexicon = null;
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
    
    boolean useVarDP = CommandLineUtils.getValueOrUseDefault(input, "-vardp", "").equals("true");
    VarDPOptions varDPOptions = null;
    if(useVarDP) {
      varDPOptions = new VarDPOptions();
      // Keep only command-line options that start with "vardp."
      fig.basic.OptionsParser op = new fig.basic.OptionsParser("vardp", varDPOptions);
      op.ignoreUnknownOpts();
      op.mustMatchFullName();
      op.doParse(args);
      op.printHelp();
      op.writeEasy("vardp.map"); // Hopefully we're in a decent directory
      ddcFactory = varDPOptions.createDDCFactory();
    
      int numStates = numSubStatesArray.length;
      fig.basic.IOUtils.printLinesEasy("general.map",
        fig.basic.ListUtils.newList("numSubstates\t"+nSubstates));

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
      unaryBackboneDistribs = new UnaryDiscreteDistribCollection[numStates];
      binaryBackboneDistribs = new BinaryDiscreteDistribCollection[numStates];
    	unaryBinaryDistribution = new DiscreteDistrib[numStates][]; 
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

		// compute roughly the memory usage
		long totalMemory  = Runtime.getRuntime().totalMemory();
		long freeMemory   = Runtime.getRuntime().freeMemory();
		long usedMemory   = totalMemory - freeMemory;
		System.out.println("Before creating any grammar related objects we have:\nMemory statistics. Total: "+totalMemory+", Free: "+freeMemory+", Used: "+usedMemory+".");

    
    // EM: iterate until the validation likelihood drops for four consecutive
		// iterations
    int iter = 0;
    int droppingIter = 0;
    
    // If we're training without loading a split grammar, then we run once without splitting.
    //  Else, we load the old grammar and start off by splitting.
    int startSplit = 0;
    if (splitGrammarFile!=null) {
    	System.out.println("Loading old grammar from "+splitGrammarFile);
    	startSplit = 1; // we've already trained the grammar
    	ParserData pData = ParserData.Load(splitGrammarFile);
    	maxGrammar = pData.gr;
    	maxLexicon = pData.lex;
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

    String smootherStr = CommandLineUtils.getValueOrUseDefault(input,
    		"-smooth", "NoSmoothing");
    Smoother lexiconSmoother = null;
    Smoother grammarSmoother = null;
    if (splitGrammarFile!=null){
    	lexiconSmoother = maxLexicon.getSmoother(); 
    	grammarSmoother = maxGrammar.smoother;
    	System.out.println("Using smoother from input grammar.");
    }
    else if (smootherStr.equals("NoSmoothing"))
    	lexiconSmoother = grammarSmoother = new NoSmoothing();
    else if (smootherStr.equals("SmoothAcrossParentSubstate"))
    	lexiconSmoother = grammarSmoother = new SmoothAcrossParentSubstate(grammarSmoothing);
    else if (smootherStr.equals("SmoothAcrossParentBits")) {
    	System.out.println("FIX THIS");
    	if (splitGrammarFile==null) lexiconSmoother = grammarSmoother  = new NoSmoothing();
    	else lexiconSmoother = grammarSmoother  = new SmoothAcrossParentBits(grammarSmoothing, maxGrammar.splitTrees);
    }
    else
    	throw new Error("I didn't understand the type of smoother '"+smootherStr+"'");
    System.out.println("Using smoother "+smootherStr);

    
		int threshold = Integer.parseInt(CommandLineUtils.getValueOrUseDefault(input,"-unkT","5"));
		System.out.println("Words which appear <= "+threshold+" times will be replaced with their unknown word signature.");
		// create a baseline lexicon and grammar from which can get the relative rule frequncies in the treebank

		short[] baselineSubstates = new short[numSubStatesArray.length];
		Arrays.fill(baselineSubstates,(short)1);
		StateSetTreeList trainStateSetTrees = new StateSetTreeList(trainTrees, baselineSubstates, false, tagNumberer, deletePC);
    
//		SimpleLexicon baselineLexicon = new SimpleLexicon(baselineSubstates,Lexicon.DEFAULT_SMOOTHING_CUTOFF,smoothParams, lexiconSmoother,filter, trainStateSetTrees,threshold);
//		Grammar baselineGrammar = new Grammar(baselineSubstates, findClosedUnaryPaths, null, new NoSmoothing(), null, -1);
//		for (Tree<StateSet> stateSetTree : trainStateSetTrees) {
//			baselineLexicon.trainTree(stateSetTree, 0, null, false,false);
//			baselineGrammar.tallyUninitializedStateSetTree(stateSetTree);
//		}
//		baselineLexicon.optimize(ddcFactory, topWordDistribs);
//		baselineGrammar.optimize(0, null, null, null, null, null);

		trainStateSetTrees = new StateSetTreeList(trainTrees, numSubStatesArray, false, tagNumberer, deletePC);
    StateSetTreeList validationStateSetTrees = new StateSetTreeList(validationTrees, numSubStatesArray, false, tagNumberer, deletePC);

    // get rid of the old trees
    trainTrees = null;
    validationTrees = null;
    corpus = null;
    System.gc();


    //startSplit = 0;
    // the main loop: split and train the grammar
    for (int splitIndex = startSplit; splitIndex < numSplitTimes+1; splitIndex++) {
    	System.out.println("In split loop...");
	// split every time but the first time
    	if (splitIndex!=0) {
    		System.out.println("Splitting grammar substates in two.");
    		System.out.println("NEW: States are never split into more substates than they are actually ever seen!");
    		// do the actual splitting
    		System.out.println("Before splitting, we have a total of "+maxGrammar.totalSubStates()+" substates");
    		
		CorpusStatistics corpusStatistics = new CorpusStatistics(tagNumberer,trainStateSetTrees);
		corpusStatistics.countSymbols();
		int[] counts = corpusStatistics.getSymbolCounts();

    		maxGrammar = maxGrammar.splitAllStates(randomness, counts, allowMoreSubstatesThanCounts);
    		maxLexicon = maxLexicon.splitAllStates(counts, allowMoreSubstatesThanCounts);
    		grammar = maxGrammar;
    		lexicon = maxLexicon;
    		previousGrammar = maxGrammar;
    		previousLexicon = maxLexicon;
    		System.out.println("After splitting, we have a total of "+maxGrammar.totalSubStates()+" substates");
    		// rebuild the sets of trees to reflect the new number of substates
    		numSubStatesArray = grammar.numSubStates;
  			trainStateSetTrees = new StateSetTreeList(trainStateSetTrees, numSubStatesArray, false);
  			validationStateSetTrees = new StateSetTreeList(validationStateSetTrees, numSubStatesArray, false);     	
    		// reset our inner loop termination condition
    		droppingIter = 0;
    		// recalculate the likelihood for the new grammar
  			ArrayParser parser = new ArrayParser(maxGrammar, maxLexicon);
  			maxLikelihood = 0;  // The validation LL of previousGrammar/previousLexicon
    		int unparsable = 0;
    		maxLikelihood = 0;
  			for (Tree<StateSet> stateSetTree : validationStateSetTrees) {
  				parser.doInsideScores(stateSetTree,false,false);  // Only inside scores are needed here
  				double ll = stateSetTree.getLabel().getIScore(0);
  				ll = Math.log(ll) + (100*stateSetTree.getLabel().getIScale());
  				if (Double.isInfinite(ll) || Double.isNaN(ll)) { 
  					//System.out.println("Something is wrong with tree "+n+". The ll is: "+ll);
  					unparsable++;
  					//System.out.println("The scaling factor is "+stateSetTree.getLabel().getIScale());
  					//printBadLLReason(stateSetTree, lexicon);
  				}
  				else maxLikelihood += ll;  // there are for some reason some sentences that are unparsable 
  			}
  			System.out.println("done.\n Number of unparsable trees: "+unparsable+".");
  			System.out.println("After splitting the " + splitIndex
						+ "th time, we get a validation likelihood of " + maxLikelihood);
    	}
    	// NOTE: The "HoldBitsOnTrunk" sparsifier needs an up-to-date copy of the numSubStatesArray,
    	// so update that here.  --Leon
    	if (sparsifier instanceof HoldBitsOnTrunk) {
    		((HoldBitsOnTrunk)sparsifier).setNSubStates(numSubStatesArray);
    	}
    	//the first inner loop: train the grammar via EM until validation likelihood reliably drops
    	do {
        Record.begin("iteration", iter);
    		iter += 1;
    		System.out.println("Beginning iteration "+(iter-1)+":");
    		// keep track of the best grammar for when we stop
    		// M step: best grammar
    		double validationLikelihood = 0;
    		
    		// initialize if we're just starting out, but not if we're loading a grammar
    		if (iter == 1 && splitGrammarFile==null) {
    			// If it is the first iteration, initialize
    			grammar = new Grammar(numSubStatesArray, findClosedUnaryPaths, sparsifier, grammarSmoother, null, filter);
//    			lexicon = new Lexicon(numSubStatesArray,Lexicon.DEFAULT_SMOOTHING_CUTOFF,smoothParams, lexiconSmoother,filter);
    			lexicon = new SimpleLexicon(numSubStatesArray,Lexicon.DEFAULT_SMOOTHING_CUTOFF,smoothParams, lexiconSmoother,filter, trainStateSetTrees,threshold);
    			if (useVarDP) {
            grammar.useVarDP = true;
            lexicon.setUseVarDP(true);
          }
    			
    			int n = 0;
    			boolean secondHalf = false;
    			for (Tree<StateSet> stateSetTree : trainStateSetTrees) {
    				secondHalf = (n++>nTrees/2.0); 
    				lexicon.trainTree(stateSetTree, randomness, null, secondHalf,false);
    				grammar.tallyUninitializedStateSetTree(stateSetTree);
    			}
    			lexicon.optimize(ddcFactory, topWordDistribs);
//    			grammar.baselineGrammar = baselineGrammar;
    			grammar.optimize(randomness, ddcFactory, topSubstateDistribs, unaryBackboneDistribs, binaryBackboneDistribs, unaryBinaryDistribution);

    			
    			//System.out.println(grammar);
    			maxGrammar = grammar; //needed for baseline - when there is no EM loop
    			maxLexicon = lexicon;
    		} else {
    			// If it is not the first iteration, perform the M step
    			// 1) Compute the validation likelihood of the previous iteration
    			System.out.print("Calculating validation likelihood...");
    			ArrayParser parser = new ArrayParser(previousGrammar, previousLexicon);
    			//previousGrammar.checkNumberOfSubstates();
    			validationLikelihood = 0;  // The validation LL of previousGrammar/previousLexicon
    			int n = 0;
    			int unparsable = 0;
    			for (Tree<StateSet> stateSetTree : validationStateSetTrees) {
    				n++;
        		//System.out.println("Doing tree: "+stateSetTree);
    				parser.doInsideScores(stateSetTree,false,false);  // Only inside scores are needed here
    				double ll = stateSetTree.getLabel().getIScore(0);
    				ll = Math.log(ll) + (100*stateSetTree.getLabel().getIScale());
    				if (Double.isInfinite(ll) || Double.isNaN(ll)) { 
    					//System.out.println("Something is wrong with tree "+n+". The ll is: "+ll);
    					unparsable++;
    					//System.out.println("The scaling factor is "+stateSetTree.getLabel().getIScale());
    					//printBadLLReason(stateSetTree, lexicon);
    				}
    				else validationLikelihood += ll;  // there are for some reason some sentences that are unparsable 
    			}
          Record.add("validationLikelihood", validationLikelihood);
    			System.out.println("done.\n Number of unparsable trees: "+unparsable+".\n Iteration "+(iter-1)+" gives validation likelihood "+validationLikelihood);
    			
    			// 2) Perform the M step while computing the training likelihood of the previous iteration
    			System.out.print("Calculating training likelihood...");
    			grammar = new Grammar(numSubStatesArray, findClosedUnaryPaths, sparsifier, grammarSmoother, grammar, filter);
    			if (useVarDP) {
            grammar.useVarDP = true; 
            lexicon.setUseVarDP(true);
          }
//    			lexicon = new Lexicon(numSubStatesArray,	Lexicon.DEFAULT_SMOOTHING_CUTOFF, smoothParams, lexiconSmoother,filter);
    			lexicon = maxLexicon.copyLexicon();
    			double trainingLikelihood = 0;  // The training LL of previousGrammar/previousLexicon
    			boolean secondHalf = false;
    			n = 0;
    			for (Tree<StateSet> stateSetTree : trainStateSetTrees) {

    				// check the memory from time to time
    				// and run the garbage collector if necessary
        		if ((n%50==0)) { 
      				totalMemory  = Runtime.getRuntime().totalMemory();
          		freeMemory   = Runtime.getRuntime().freeMemory();
          		usedMemory   = totalMemory - freeMemory;
          		if (usedMemory>=1500000000){
          			System.out.println("Calling System.gc() since the memory usage is: "+usedMemory);
          			System.gc();
          		}
        		}
    				
    				secondHalf = (n++>nTrees/2.0); 
    				boolean noSmoothing = true, debugOutput = false;
    				parser.doInsideOutsideScores(stateSetTree,noSmoothing,debugOutput);                    // E Step
    				double ll = stateSetTree.getLabel().getIScore(0);
    				ll = Math.log(ll) + (100*stateSetTree.getLabel().getIScale());//System.out.println(stateSetTree);
    				if (Double.isInfinite(ll) || Double.isNaN(ll)) {
    					System.out.println("Training sentence "+n+" is given "+ll+" log likelihood!");
							System.out
									.println("Something is wrong with the ll. It is: " + ll);
							System.out.println("Root iScore "+ stateSetTree.getLabel().getIScore(0)+" scale "+stateSetTree.getLabel().getIScale());
							//System.out.println("tree probabilities " + stateSetTree);
							// find out where the problem lies
							//printBadLLReason(stateSetTree, lexicon);
							// print the tree in the dummy case
							if (dummy) {
								System.out.println("tree probabilities " + stateSetTree);
							}
    				}
    				else {
      			  lexicon.trainTree(stateSetTree, -1, previousLexicon, secondHalf,noSmoothing);
      				grammar.tallyStateSetTree(stateSetTree, previousGrammar,-1);      // E Step
    					trainingLikelihood  += ll;  // there are for some reason some sentences that are unparsable 
    				}
			
    			}
    			System.out.println("done.\n  Iteration "+(iter-1)+" gives training likelihood "+trainingLikelihood);
          Record.add("trainingLikelihood", trainingLikelihood);
      		totalMemory  = Runtime.getRuntime().totalMemory();
      		freeMemory   = Runtime.getRuntime().freeMemory();
      		usedMemory   = totalMemory - freeMemory;
      		System.out.println("Memory statistics. Total: "+totalMemory+", Free: "+freeMemory+", Used: "+usedMemory+".");
					System.out.print("optimizing Lexicon...");
    			lexicon.optimize(ddcFactory, topWordDistribs);   // M Step
    			System.out.println("done.");
					System.out.print("optimizing Grammar...");
    			grammar.optimize(0, ddcFactory, topSubstateDistribs, unaryBackboneDistribs, binaryBackboneDistribs, unaryBinaryDistribution);  // M Step
    			System.out.println("done.");
    			
    			// 3) Check whether previousGrammar/previousLexicon was in fact better than the best
    			if (iter<minIterations){
    				maxLikelihood = Math.max(validationLikelihood,maxLikelihood);
    				maxGrammar = previousGrammar;
    				maxLexicon = previousLexicon;
    				droppingIter = 0;
    			}
    			else if(validationLikelihood >= maxLikelihood) {
    				maxLikelihood = validationLikelihood;
    				maxGrammar = previousGrammar;
    				maxLexicon = previousLexicon;
    				droppingIter = 0;
    			} else {
    				/*        	if (validationLikelihood > previousLikelihood) {
    				 droppingIter = 0;
    				 }
    				 else {
    				 */         droppingIter++;
    				 //       	}
    			}
    		}
    		if (dummy&&verbose) {
    			System.out.println("First tree:");
    			System.out.println(Trees.PennTreeRenderer.render(trainStateSetTrees.iterator().next()));
    			System.out.println("Grammar:");
    			System.out.println(grammar);
    	//		System.out.println(lexicon);
    		}
//    		if (iter>20 && iter%5==0){
//    			ParserData pData = new ParserData(previousLexicon, previousGrammar, Numberer.getNumberers(), numSubStatesArray, VERTICAL_MARKOVIZATION, HORIZONTAL_MARKOVIZATION, binarization);
//	        System.out.println("Saving grammar to "+outFileName+"-it-"+iter+".");
//	        System.out.println("It gives a validation data log likelihood of: "+maxLikelihood);
//	        if (pData.Save(outFileName+"-it-"+iter)) System.out.println("Saving successful");
//	        else System.out.println("Saving failed!");
//	        pData = null;
//    		}
//        System.gc();
//    		System.out.println(lexicon);
//    		System.out.println(grammar);
    		previousGrammar = grammar;
     		previousLexicon = lexicon;
        Record.end();
        Record.flush();
    	} while ((droppingIter < allowedDroppingIters) && (!baseline) && (iter<maxIterations));
    }
    
    // The last grammar/lexicon has not yet been evaluated. Even though the validation likelihood
    // has been dropping in the past few iteration, there is still a chance that the last one was in
    // fact the best so just in case we evaluate it.
    System.out.print("Calculating last validation likelihood...");
    ArrayParser parser = new ArrayParser(grammar, lexicon);
    double validationLikelihood = 0;  // The validation LL of previousGrammar/previousLexicon
    for (Tree<StateSet> stateSetTree : validationStateSetTrees) {
      parser.doInsideScores(stateSetTree,false,false);  // Only inside scores are needed here
      double ll = stateSetTree.getLabel().getIScore(0);
      ll = Math.log(ll) + (100*stateSetTree.getLabel().getIScale());
			if (Double.isInfinite(ll) || Double.isNaN(ll)){
      	System.out.println("Something is wrong with the ll. It is: "+ll);
      	//printBadLLReason(stateSetTree, lexicon);
      }
      else  validationLikelihood += ll;  // there are for some reason some sentences that are unparsable 
    }
    System.out.println("done.\n  Iteration "+iter+" (final) gives validation likelihood "+validationLikelihood);
    if (validationLikelihood > maxLikelihood) {
      maxLikelihood = validationLikelihood;
      maxGrammar = previousGrammar;
      maxLexicon = previousLexicon;
    }
    
/*    if (baseline) {
      System.out.println("Grammar symbols:");
      grammar.printSymbolCounter(tagNumberer);
      System.out.println("POS symbols:");
      lexicon.printTagCounter(tagNumberer);
    }
*/
    //use the best grammar we've found, not the last one
		// compute roughly the memory usage
		totalMemory  = Runtime.getRuntime().totalMemory();
		freeMemory   = Runtime.getRuntime().freeMemory();
		usedMemory   = totalMemory - freeMemory;
		System.out.println("\nMemory statistics. Total: "+totalMemory+", Free: "+freeMemory+", Used: "+usedMemory+".");

		grammar = null;
		lexicon = null;
    System.gc();
		totalMemory  = Runtime.getRuntime().totalMemory();
		freeMemory   = Runtime.getRuntime().freeMemory();
		usedMemory   = totalMemory - freeMemory;
		System.out.println("After removing one grammar and one lexicon:\nMemory statistics. Total: "+totalMemory+", Free: "+freeMemory+", Used: "+usedMemory+".");
//		System.out.println(maxGrammar);
//		System.out.println(maxLexicon);
		
    ParserData pData = new ParserData(maxLexicon, maxGrammar, Numberer.getNumberers(), numSubStatesArray, VERTICAL_MARKOVIZATION, HORIZONTAL_MARKOVIZATION, binarization);

    System.out.println("Saving grammar to "+outFileName+".");
    System.out.println("It gives a validation data log likelihood of: "+maxLikelihood);
    if (pData.Save(outFileName)) System.out.println("Saving successful.");
    else System.out.println("Saving failed!");

    pData = null;
    grammar = maxGrammar = previousGrammar = null;
    lexicon = maxLexicon = previousLexicon = null;
    System.gc();
    
		// compute roughly the memory usage
		totalMemory  = Runtime.getRuntime().totalMemory();
		freeMemory   = Runtime.getRuntime().freeMemory();
		usedMemory   = totalMemory - freeMemory;
		System.out.println("After freeing memory from grammar related objects we get:\nMemory statistics. Total: "+totalMemory+", Free: "+freeMemory+", Used: "+usedMemory+".");

    Record.finish();
    
    System.exit(0);
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
			List<Tree<String>> validationTrees, Numberer tagNumberer, short nSubStates,
			boolean dontSplitTags) {
		// first generate unsplit grammar and lexicon
    short[] nSub = new short[2];
    nSub[0] = 1;
    nSub[1] = nSubStates;
    //List<Tree<StateSet>> trainStateSetTrees = convertAndInitializeTrees(trainTrees, nSub, true, tagNumberer);
    //List<Tree<StateSet>> validationStateSetTrees = convertAndInitializeTrees(validationTrees, nSub, true, tagNumberer);
    StateSetTreeList trainStateSetTrees = new StateSetTreeList(trainTrees,
				nSub, true, tagNumberer, dontSplitTags);
    // do the validation set so that the numberer sees all tags and we can
		// allocate big enough arrays
		// note: although this variable is never read, this constructor adds the
		// validation trees into the tagNumberer as a side effect, which is
		// important
		@SuppressWarnings("unused")
		StateSetTreeList validationStateSetTrees = new StateSetTreeList(
				validationTrees, nSub, true, tagNumberer, dontSplitTags);

    short numStates = (short)tagNumberer.total();
    short[] nSubStateArray = new short[numStates];
  	short two = nSubStates;
  	Arrays.fill(nSubStateArray, two);
  	//System.out.println("Everything is split in two except for the root.");
  	nSubStateArray[0] = 1; // that's the ROOT
    return nSubStateArray;
  }

  /*private static short convertCountToNumberOfSubStates(int n, boolean symbolCount){
  	if (symbolCount) {
  		// this could become somthing like:
  		// return mappings[factor * Math.log(n)] 
  		// where mappings is the array that we pass in or
  		// just return factor * Math.log(n)
  		
	  	if (n<2) return less2;
	  	else if (n<3) return less10;
	    else if (n<100) return less100;//3;
	    else if (n<1000) return less1000;//4;
	    else if (n<10000) return less10000;//5;
	    else if (n<38000) return less38000;//6;
	    else return more38000;//7;
  	}
  	else {
     	if (n<2) return less2;
    	else if (n<3) return less10;
      else if (n<10) return less100;
      else if (n<20) return less1000;
      else if (n<60) return less10000;
      else if (n<140) return less38000;
      else return more38000;
      // vor v=1 and h=0
/*  		if (n<2) return less2;
    	else if (n<10) return less10;
      else if (n<20) return less100;
      else if (n<80) return less1000;
      else if (n<200) return less10000;
      else if (n<1200) return less38000;
      else return more38000; 
  	}
  }
  */
  /*private static void parseMappings(String s){
    int start=0, end=0;
    end=s.indexOf(" ",start);
    less2 = Short.parseShort(s.substring(start,end));
    start = end+1; end=s.indexOf(" ",start);
    less10 = Short.parseShort(s.substring(start,end));
    start = end+1; end=s.indexOf(" ",start);
    less100 = Short.parseShort(s.substring(start,end));
    start = end+1; end=s.indexOf(" ",start);
    less1000 = Short.parseShort(s.substring(start,end));
    start = end+1; end=s.indexOf(" ",start);
    less10000 = Short.parseShort(s.substring(start,end));
    start = end+1; end=s.indexOf(" ",start);
    less38000 = Short.parseShort(s.substring(start,end));
    start = end+1; end=s.length();
    more38000 = Short.parseShort(s.substring(start,Math.abs(end)));
  }
  */
}
