//package edu.berkeley.nlp.auxv;
//
//import java.io.File;
//import java.io.IOException;
//import java.util.Arrays;
//import java.util.List;
//import java.util.Random;
//
//import edu.berkeley.nlp.PCFGLA.Binarization;
//import edu.berkeley.nlp.PCFGLA.ConditionalTrainer;
//import edu.berkeley.nlp.PCFGLA.Corpus;
//import edu.berkeley.nlp.PCFGLA.Grammar;
//import edu.berkeley.nlp.PCFGLA.ParserConstrainer;
//import edu.berkeley.nlp.PCFGLA.ParserData;
//import edu.berkeley.nlp.PCFGLA.SamplingParser;
//import edu.berkeley.nlp.PCFGLA.SimpleLexicon;
//import edu.berkeley.nlp.PCFGLA.StateSetTreeList;
//import edu.berkeley.nlp.PCFGLA.Corpus.TreeBankType;
//import edu.berkeley.nlp.PCFGLA.smoothing.NoSmoothing;
//import edu.berkeley.nlp.auxv.bractrl.BracketNumberControllers;
//import edu.berkeley.nlp.discPCFG.DefaultLinearizer;
//import edu.berkeley.nlp.discPCFG.Linearizer;
//import edu.berkeley.nlp.discPCFG.ParsingObjectiveFunction;
//import edu.berkeley.nlp.discPCFG.SamplingObjectiveFunction;
//import edu.berkeley.nlp.syntax.StateSet;
//import edu.berkeley.nlp.syntax.Tree;
//import edu.berkeley.nlp.math.LBFGSMinimizer;
//import edu.berkeley.nlp.math.OW_LBFGSMinimizer;
//import edu.berkeley.nlp.util.Numberer;
//import fig.basic.Option;
//import fig.exec.Execution;
//
//public class ExperimentsDiscriminativeTraining implements Runnable
//{
//	private Grammar grammar;
//	private SimpleLexicon lexicon;
//  Linearizer linearizer;
//  ParsingObjectiveFunction objective;
//
//  private StateSetTreeList trainStateSetTrees;
//  private short[] numSubStatesArray;
//  private Numberer tagNumberer;
//	
//  @Option(gloss="Location of the grammar")
//  public String grammarLocation;
//  @Option(required=true, gloss="Source of sentences to parse")
//  public String sentencesLocation;
//  @Option(required=false, gloss="Corpus type: WSJ/CONLL/etc")
//  public TreeBankType corpusType = TreeBankType.WSJ;
////  @Option(gloss="Output file prefix for dumping statistics")
////  public String outputPrefix = "output";
////  @Option(gloss="Maximum sentence length for training the initial grammar")
////  public int maxInitialGrammarTrainingSentenceLength = 10;
//  
////  @Option(gloss="Higher values encourage selection large bracket constraints (optimistic)")
////  public double largeSpanOptExponent = 1.0;
////  @Option(gloss="Higher values encourage selection large bracket constraints (pessimistic)")
////  public double largeSpanPesExponent = 1.0;
////  @Option(gloss="Higher values encourage selection of high proposal posterior probability")
////  public double likelySpanExponent = 1.0;
//  
////  @Option(gloss="Number of iterations")
////  public int nIterations = 20;
////  @Option(gloss="Length of the burnin period")
////  public int nBurnIn = 5;
////  @Option(gloss="Minimum sentence length")
////  public int minSentenceLength = 10;
//  @Option(gloss="Maximum sentence length")
//  public int maxSentenceLength = 25;
//  @Option(gloss="Maximum sentence length for exact inside outside")
//  public int maxLengthForExact = 10;
//  @Option(gloss="Source of randomness")
//  public Random rand = new Random(1);
//
//  @Option(gloss="Use exact expectations or sampled expectations")
//  public Modus modus = Modus.SAMPLING;
//  public enum Modus{ EXACT, SAMPLING; }
//  
//  @Option(gloss="Use pruning constraints to speed-up training")
//  public boolean useConstraints = false;
//  /*
//   * training of an unsplit x-bar style grammar (no latent variables)
//   */
//  public void init() throws IOException
//  {
//  	loadTrainingData();
//  	initializeLexiconAndGrammar();
//  	createObjectiveFunction();
//  }
//
//  /**
//	 * creates the objective function that will be then fed into the minimizer
//	 */
//	private void createObjectiveFunction() {
//		double sigma = 1; // regularization parameter 
//		int nProcess = 1; // number of threads
//		String cons = null; // pruning based constraints
//		int regularize = 1; // L1 regularization
//
//		if (useConstraints){
//			File nextFile = null;
//			System.out.println("Computing constraints.");
//			
//			// first create an x-bar generative grammar
//			String[] baselineArgs = new String[]{"-out", "cons_0.gr", "-baseline", "-maxL", maxSentenceLength+"", "-treebank", corpusType+"", "-path", sentencesLocation};
//			nextFile = new File("base_gen.gr");
//			if (!nextFile.exists()) ConditionalTrainer.main(baselineArgs);
//			else System.out.println("Skipping this step since "+nextFile.toString()+" already exists.");
//				
//			// now compute constraints with the x-bar generative grammar
//			String[] consArgsTrain = new String[]{"-out", "cons_0", "-in", "cons_0.gr", "-outputLog", "cons_0.log", "-maxL", maxSentenceLength+"", "-treebank", corpusType+"", "-path", sentencesLocation}; 
//			nextFile = new File("cons_0-0.data");
//			if (nextFile.exists()) System.out.println("Skipping this step since "+nextFile.toString()+" already exists.");
//			else {
//				ParserConstrainer.main(consArgsTrain);	
//				consArgsTrain = new String[]{"-out", "cons_0_dev", "-in","cons_0.gr", "-section", "dev", "-nChunks", "1", "-outputLog", "cons_0_dev.log", "-maxL", maxSentenceLength+"", "-treebank", corpusType+"", "-path", sentencesLocation}; 
//				ParserConstrainer.main(consArgsTrain);
//			}
//			cons = "cons_0";
//		}
//		
//		
//		if (modus == Modus.EXACT)
//			objective = new ParsingObjectiveFunction(linearizer, trainStateSetTrees, sigma, 
//					regularize, cons, nProcess, grammarLocation,	true, false); 
//		else
//			objective = new SamplingObjectiveFunction(linearizer, trainStateSetTrees, sigma, 
//					regularize, cons, nProcess, grammarLocation,	true, false, maxLengthForExact);//, braNumberControllers); 
//			
//
//	}
//
//	/**
//	 * creates a grammar and a lexicon and initialize all parameters with 0s
//	 */
//	private void initializeLexiconAndGrammar() {
//    grammar = new Grammar(numSubStatesArray, false, new NoSmoothing(), null, -1);
//    lexicon = new SimpleLexicon(numSubStatesArray,-1,null, new NoSmoothing(),-1, trainStateSetTrees);
//    
//		boolean secondHalf = false;
//		for (Tree<StateSet> stateSetTree : trainStateSetTrees) {
//			lexicon.trainTree(stateSetTree, 1.0, null, secondHalf,false);
//			grammar.tallyUninitializedStateSetTree(stateSetTree);
//		}
//		lexicon.optimize();
//		grammar.optimize(1.0);
//
//		boolean noUnaryChains = true;
//		grammar = grammar.copyGrammar(noUnaryChains);
//		lexicon = lexicon.copyLexicon();
//		linearizer = new DefaultLinearizer(grammar, lexicon, null);
//		
//	  double[] init = linearizer.getLinearizedWeights();
//	  Arrays.fill(init, 0.0);
//
//	  linearizer.delinearizeWeights(init);
//	  grammar = linearizer.getGrammar();
//	  lexicon = linearizer.getLexicon();
//	  grammar.splitRules();
//
//	}
//
//	/**
//	 * initializes the trainStateSetTrees
//	 */
//	private void loadTrainingData() {
//  	Corpus corpus = new Corpus(sentencesLocation,corpusType,1.0,false);
//    List<Tree<String>> trainTrees = Corpus.binarizeAndFilterTrees(corpus
//				.getTrainTrees(), 1, 0, maxSentenceLength, Binarization.RIGHT, false, false, false);
////    if (trainTrees!=null)trainTrees = Corpus.filterTreesForConditional(trainTrees, false, false, false);
//    int nTrees = trainTrees.size();
//    System.out.println("There are "+nTrees+" trees in the training set.");
//
//		tagNumberer = Numberer.getGlobalNumberer("tags");
//		numSubStatesArray = ConditionalTrainer.initializeSubStateArray(trainTrees, trainTrees, tagNumberer, (short)1);
//  	Arrays.fill(numSubStatesArray, (short)1);
//    trainStateSetTrees = new StateSetTreeList(trainTrees, numSubStatesArray, false, tagNumberer);
//    Corpus.replaceRareWords(trainStateSetTrees,new SimpleLexicon(numSubStatesArray,-1), 5);
//	}
//
//	public static void main(String [] args) throws IOException 
//  {
//    Execution.run(args, new ExperimentsDiscriminativeTraining(),
//    		"braNumberCtrler", BracketNumberControllers.instance,
//    		"samplingParser", SamplingParser.class);
//  }
//
//  public void run()
//  {
//    try
//    {
//      init();
//      train();
//
//    }
//    catch (IOException e) { e.printStackTrace(); }
//  }
//
//	/**
//	 * 
//	 */
//	private void train() {
//		// some parameters that could become command line arguments:
//		int regularize = 1; // L1 regularization
//		int iterations = 60; // max-number of L-BFGS iterations
//	  LBFGSMinimizer minimizer = null;
//  	
//	  if (regularize==1) minimizer = new OW_LBFGSMinimizer(iterations);
//  	else minimizer = new LBFGSMinimizer(iterations);
//
//		double[] weights = objective.getCurrentWeights();
//		weights = minimizer.minimize(objective, weights, 1e-4);
//	
//		linearizer.delinearizeWeights(weights);
//		grammar = linearizer.getGrammar();
//	  lexicon = linearizer.getLexicon();
//	
//	  ParserData pData = new ParserData(lexicon, grammar, null, Numberer.getNumberers(), numSubStatesArray, 1, 0, Binarization.RIGHT);
//	  System.out.println("Saving grammar to "+grammarLocation+".");
//	  if (pData.Save(grammarLocation)) System.out.println("Saving successful.");
//	  else System.out.println("Saving failed!");
//  	
//  	
////	boolean lasso = true; // start of by regularizing just a little and then do more and more regularization
////  	for (int it=1; it<4; it++){
////	  	double newSigma = sigma;
////	  	if (lasso){ newSigma = sigma + 3 - it; }
////	  	
////	  	if (it==1) {
////	  		objective = new ParsingObjectiveFunction(linearizer, trainStateSetTrees, newSigma, 
////	  				regularize, false, opts.cons, opts.nProcess, grammarLocation,	false, true, false); 
////	  			
////	  		minimizer.setMinIteratons(15);
////	  	}
////	  	objective.setSigma(newSigma);
////	  	
////	  	double[] weights = objective.getCurrentWeights();
////    	weights = minimizer.minimize(objective, weights, 1e-4);
////
////    	linearizer.delinearizeWeights(weights);
////    	grammar = linearizer.getGrammar();
////      lexicon = linearizer.getLexicon();
////
////      ParserData pData = new ParserData(lexicon, grammar, null, Numberer.getNumberers(), numSubStatesArray, 1, 0, Binarization.RIGHT);
////      System.out.println("Saving grammar to "+grammarLocation+"-"+it+".");
////      if (pData.Save(grammarLocation+"-"+it)) System.out.println("Saving successful.");
////      else System.out.println("Saving failed!");
////
////  	}		
//	}
//
////	public ExperimentsDiscriminativeTraining(final BracketNumberControllers braNumberControllers) 
////	{
////		this.braNumberControllers = braNumberControllers;
////	}
//  
//}
