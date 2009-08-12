//package edu.berkeley.nlp.auxv;
//
//import static fig.basic.LogInfo.end_track;
//import static fig.basic.LogInfo.logs;
//import static fig.basic.LogInfo.track;
//
//import java.io.File;
//import java.io.IOException;
//import java.io.PrintWriter;
//import java.util.Arrays;
//import java.util.List;
//import java.util.Random;
//
//import nuts.io.Extensions;
//import edu.berkeley.nlp.PCFGLA.ConditionalTrainer;
//import edu.berkeley.nlp.PCFGLA.Corpus;
//import edu.berkeley.nlp.PCFGLA.DoublyConstrainedTwoChartsParser;
//import edu.berkeley.nlp.PCFGLA.Grammar;
//import edu.berkeley.nlp.PCFGLA.ParserData;
//import edu.berkeley.nlp.PCFGLA.SimpleLexicon;
//import edu.berkeley.nlp.PCFGLA.Corpus.TreeBankType;
//import edu.berkeley.nlp.auxv.AuxVarInsideOutside.IterManager;
//import edu.berkeley.nlp.auxv.AuxVarInsideOutside.PrintMonitor;
//import edu.berkeley.nlp.auxv.bractrl.InformedBracketProposer;
//import edu.berkeley.nlp.auxv.bractrl.BracketNumberControllers.SimpleBracketNumberController;
//import edu.berkeley.nlp.syntax.Tree;
//import edu.berkeley.nlp.util.Numberer;
//import fig.basic.IOUtils;
//import fig.basic.LogInfo;
//import fig.basic.Option;
//import fig.basic.StopWatch;
//import fig.exec.Execution;
//
//public class ExperimentsExpectations implements Runnable
//{
//	private DoublyConstrainedTwoChartsParser parser;
//	private Grammar grammar;
//	private SimpleLexicon lexicon;
//  private AuxVarInsideOutside aux;
//  private InformedBracketProposer prop;
//  private List<Tree<String>> trainTrees;
//
//  
//  @Option(gloss="Location of the grammar")
//  public String grammarLocation;
//  @Option(required=true, gloss="Source of sentences to parse")
//  public String sentencesLocation;
//  @Option(required=false, gloss="Corpus tyoe: WSJ/CONLL/etc")
//  public TreeBankType corpusType = TreeBankType.WSJ;
//  @Option(gloss="Output file prefix for dumping statistics")
//  public String outputPrefix = "output";
//  @Option(gloss="Maximum sentence length for training the initial grammar")
//  public int maxInitialGrammarTrainingSentenceLength = 10;
//  
//  @Option(gloss="Higher values encourage selection large bracket constraints (optimistic)")
//  public double largeSpanOptExponent = 1.0;
//  @Option(gloss="Higher values encourage selection large bracket constraints (pessimistic)")
//  public double largeSpanPesExponent = 1.0;
//  @Option(gloss="Higher values encourage selection of high proposal posterior probability")
//  public double likelySpanExponent = 1.0;
//  
//  @Option(gloss="Number of bracket constraints")
//  public int nBrackets = 8;
//  @Option(gloss="Number of iterations")
//  public int nIterations = 20;
//  @Option(gloss="Length of the burnin period")
//  public int nBurnIn = 5;
//  @Option(gloss="Minimum sentence length")
//  public int minSentenceLength = 10;
//  @Option(gloss="Source of randomness")
//  public Random rand = new Random(1);
//  
//  // for debug only
//  @Option(gloss="Debug mode: do not load wsj (stupid laptop too slow)")
//  public boolean isDebug = false;
//  private static final List<String> debugSent = Arrays.asList("astronomers","saw",
//  		"ears","with","ears","with","ears","with","stars","with","stars",
//  		"with","astronomers");
//
//  
//  public void init() throws IOException
//  {
//    ParserData pData = null;
//    File tmp = new File(grammarLocation);
//    if (!tmp.exists())
//    {
//    	//    train a simple grammar if none was provided
//    	if (isDebug) throw new RuntimeException();
//      LogInfo.logss("Creating new baseline grammar.");
//    	if (grammarLocation == null) throw new RuntimeException();
//  		String[] baselineArgs = {"-path", sentencesLocation, "-out", grammarLocation, 
//  				"-baseline", "-maxL", maxInitialGrammarTrainingSentenceLength+"", 
//  				"-lang", "" +  corpusType};
//  		ConditionalTrainer.main(baselineArgs);
//      pData = ParserData.Load(grammarLocation);
//    }
//    else pData = ParserData.Load(grammarLocation);
//
//    grammar = pData.getGrammar();
//    grammar.splitRules();
//    lexicon = (SimpleLexicon)pData.getLexicon();
//    Numberer.setNumberers(pData.getNumbs());
//    
//    if (!isDebug) 
//    {
//    	Corpus corpus = new Corpus(sentencesLocation,corpusType,1.0,false);
//    	trainTrees = corpus.getTrainTrees();
//      if (trainTrees!=null)trainTrees = Corpus.filterTreesForConditional(trainTrees,false,false,false);
//    	Corpus.replaceRareWords(trainTrees,new SimpleLexicon(new short[0],-1), 5);
//      int nTrees = trainTrees.size();
//      System.out.println("There are "+nTrees+" trees in the training set.");
//    }
//    	
//    parser = new DoublyConstrainedTwoChartsParser(grammar, lexicon);
//    
//    prop = new InformedBracketProposer(largeSpanOptExponent, largeSpanPesExponent, likelySpanExponent);
//    aux = new AuxVarInsideOutside(parser, prop, 
//        new SimpleBracketNumberController(nBrackets, 0), new IterManager(nIterations, nBurnIn), rand);
//  }
//
//  public static void main(String [] args) throws IOException 
//  {
//    Execution.run(args, new ExperimentsExpectations());
//  }
//
//  private int iteration = 0;
//  private HTMLRenderer renderer = new HTMLRenderer();
//  public void run()
//  {
//    try
//    {
//      init();
//      iteration = 0;
//      if (isDebug) compareMethods(debugSent); else
//      for (Tree<String> tree : trainTrees)
//      {
//        List<String> sentence = tree.getYield(); 
//        if (sentence.size() >= minSentenceLength)
//          compareMethods(sentence);
//        iteration++;
//      }
//    }
//    catch (IOException e) { e.printStackTrace(); }
//  }
//  
//  private void compareMethods(List<String> sentence)
//  {
//    track("Comparing exact and approximate, sentence length: " + sentence.size(),true);
//    try
//    {
//    	VectorizedSuffStat suffStatExact = new VectorizedSuffStat(grammar, lexicon);
//      StopWatch watch = new StopWatch();
//      
//      track("ExactInsideOutside.compute()", true);
//      try
//      {
//        watch.start();
//        parser.compute(sentence, suffStatExact);
//        watch.stop();
//      }
//      catch (DoublyConstrainedTwoChartsParser.UnderflowException ufe)
//      {
//      	LogInfo.warning("Underflow while running the exact parser. " +
//      			"Sent. length: " + sentence.size());
//      	return;
//      }
//      finally {end_track();}
//  
//      prop.put(sentence, parser.getBracketPosteriors());
//          println("Time for exact: " + watch.ms + "ms");
//      
//      VectorizedSuffStat suffStat = suffStatExact.newInstance();
//      PrintMonitor monitor = aux.new PrintMonitor(/*suffStatExact.mlGrammar(), */renderer);
//      aux.setMonitor(monitor);
//      try {
//        aux.compute(sentence, suffStat);
//      } catch (Exception e) {
//        e.printStackTrace();
//      }
//    }
//    finally 
//    {
//    	end_track();
//    	printStats(renderer.getHTMLPage());
//      renderer = new HTMLRenderer();
//    }
//  }
//  
//  private void printStats(StringBuilder stats)
//  {
//    try
//    {
//      String outputFile = Execution.getFile(outputPrefix);
//      if (outputFile == null) outputFile = outputPrefix;
//      PrintWriter out = IOUtils.openOut(outputFile + "." + Extensions.extension2String(iteration) );
//          //+ ".eff" + Extensions.extension2String(currentLargeSpanIncrement) 
//          //+ ".sta" + Extensions.extension2String(currentLikelyIncrement));
//      out.append(stats);
//      out.close();
//    }
//    catch (IOException e) { e.printStackTrace(); }
//  }
//  
//  private void println(String string) 
//  { 
//    logs(string); 
//    renderer.addItem(string);
//  }
//}
