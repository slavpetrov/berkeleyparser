package edu.berkeley.nlp.PCFGLA;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.GZIPInputStream;

import edu.berkeley.nlp.PCFGLA.Corpus.TreeBankType;
import edu.berkeley.nlp.PCFGLA.smoothing.SmoothAcrossParentSubstate;
import edu.berkeley.nlp.PCFGLA.smoothing.Smoother;
import edu.berkeley.nlp.parser.EnglishPennTreebankParseEvaluator;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees;
import edu.berkeley.nlp.util.Numberer;
import edu.berkeley.nlp.util.Pair;

/**
 * Reads in the Penn Treebank and generates N_GRAMMARS different grammars.
 *
 * @author Slav Petrov
 */
public class GrammarTester implements Callable{

  public static ParserFactory externalParserFactory = null;

  public static interface ParserFactory {
    public ConstrainedArrayParser newParser(Grammar gr, Lexicon lex,
        SpanPredictor sp);
  }

  public static class Options {

    @Option(name = "-in", required = true, usage = "Input File for Grammar (Required)\n")
    public String inFileName;

    @Option(name = "-path", usage = "Path to Corpus (Default: null)\n")
    public String path = null;

    @Option(name = "-treebank", usage = "Language:  WSJ, CHNINESE, GERMAN, CONLL, SINGLEFILE (Default: ENGLISH)")
    public TreeBankType treebank = TreeBankType.WSJ;

    @Option(name = "-maxL", usage = "Maximum sentence length (Default <=40)")
    public int maxSentenceLength = 40;

    @Option(name = "-section", usage = "On which part of the WSJ to test: train/dev/test (Default: dev)")
    public String section = "dev";

    @Option(name = "-maxS", usage = "Maximum number of sentences (Default all)")
    public int maxSentences = 1000000;

    @Option(name = "-parser", usage = "Parser type: c-to-f, plain, kbest, basic, maxderivation")
    public String parser = "c-to-f";

    @Option(name = "-k", usage = "k for k-best parsing")
    public int k = 1;

    @Option(name = "-cons", usage = "Constraints for plain parser")
    public String cons = null;

    @Option(name = "-viterbi", usage = "Compute viterbi derivation instead of max-rule parse (Default: max-rule)")
    public boolean viterbi = false;

    @Option(name = "-allowAllSubstates", usage = "Don't prune at the substate level")
    public boolean allowAllSubstates = false;

    @Option(name = "-unaryPenalty", usage = "Unary penalty (Default: 1.0)")
    public double unaryPenalty = 1.0;

    @Option(name = "-finalLevel", usage = "Parse with projected grammar from this level (Default: -1 = input grammar)")
    public int finalLevel = -1;

    @Option(name = "-verbose", usage = "Verbose/Quiet (Default: Quiet)\n")
    public boolean verbose = false;

    @Option(name = "-accurate", usage = "Set thresholds for accuracy. (Default: set thresholds for efficiency)")
    public boolean accurate = false;

    @Option(name = "-useGoldPOS", usage = "Use gold part of speech tags (Default: false)")
    public boolean useGoldPOS = false;

    @Option(name = "-smooth", usage = "Smooth the parameters before parsing")
    public static boolean smooth = false;

    @Option(name = "-doNOTprojectConstraints", usage = "Do NOT project constraints")
    public boolean doNOTprojectConstraints = false;

    @Option(name = "-nThreads", usage = "Parse in parallel using this many threads (Default: 1).")
    public int nThreads = 1;

    @Option(name = "-filterTrees", usage = "Parse in parallel using this many threads (Default: 1).")
    public boolean filterTrees = false;

    @Option(name = "-filterAllUnaries", usage="Mark any unary parent with a ^u")
    public boolean filterAllUnaries = false;

    @Option(name = "-filterStupidFrickinWHNP", usage="Temp hack!")
    public boolean filterStupidFrickinWHNP = false;

    @Option(name = "-printGoldTree", usage="Print (flat) gold tree")
    public boolean printGoldTree = false;

    @Option(name = "-computeConstraints", usage="Compute constraints from the given grammar (rather than loading with -cons)")
    public boolean computeConstraints = false;

    @Option(name = "-evaluateConstraints", usage="Evaluate search errors from constraints")
    public boolean evaluateConstraints = false;

    @Option(name = "-logT", usage="Threshold for constraints")
    public double logT = -10;

    @Option(name="-printAllKBest", usage="Print every kBest parse")
    public boolean printAllKBest = false;

    @Option(name="-testAll", usage="Test all grammar files starting with this name")
    public boolean testAll = false;

    @Option(name="-filePath", usage="Path for grammars to be tested")
    public String filePath = null;

    @Option(name = "-nProcess", usage = "Parse in parallel using this many threads (Default: 1).")
    public int nProcess = 1;

    @Option(name = "-lowercase", usage = "Lowercase all words in the treebank")
    public boolean lowercase = false;

    @Option(name = "-allSubstatesAllowed", usage = "When using constraints whether to prune on the substate level")
    public boolean allSubstatesAllowed = false;

    @Option(name = "-printAllF1", usage = "Print all F1 scores (when using testAll)")
    public boolean printAllF1 = false;

    @Option(name = "-nGrammars", usage = "Use a product model based on that many grammars")
    public int nGrammars = 1;
  }

  List<Tree<String>> testTrees;
  boolean[][][][][] cons;
  String fileName;
  int maxSentenceLength;

  public static void main(String[] args){
    OptionParser optParser = new OptionParser(Options.class);
    Options opts = (Options) optParser.parse(args, true);
    // provide feedback on command-line arguments
    System.out.println("Calling with " + optParser.getPassedInOptions());


    String path = opts.path;
    //    int lang = opts.lang;
    System.out.println("Loading trees from "+path+" and using treebank type "+opts.treebank);


    int maxSentenceLength = opts.maxSentenceLength;
    System.out.println("Will remove sentences with more than "+maxSentenceLength+" words.");


    //    int nbest = Integer.parseInt(CommandLineUtils.getValueOrUseDefault(input, "-N","1"));

    String testSetString = opts.section;
    boolean devTestSet = testSetString.equals("dev");
    boolean finalTestSet = testSetString.equals("final");
    boolean trainTestSet = testSetString.equals("train");
    if (!(devTestSet || finalTestSet || trainTestSet)) {
      System.out.println("I didn't understand dev/final test set argument "+testSetString);
      System.exit(1);
    }
    System.out.println(" using "+testSetString+" test set");

    boolean[][][][][] cons = null;

    if (opts.computeConstraints)
    {
      String[] args1 = new String[0];

      String dirName = ".";
      String baseName="tmp";
      String[] consArgsTrain = addOptions(args1, new String[]{"-logT", "" + opts.logT,"-maxL", "" + opts.maxSentenceLength,"-path",opts.path, "-filterStupidFrickinWHNP", opts.filterStupidFrickinWHNP ? "true" : "false","-markUnaryParents", "true", "-out", dirName+"/"+baseName+"0_" + opts.section, "-in", opts.inFileName, "-section", opts.section, "-nChunks", "1", "-outputLog", dirName+"/"+baseName+".cons.log"}); 

      ParserConstrainer.main(consArgsTrain);	
      opts.cons = dirName+"/"+baseName+"0_" + opts.section + "-0.data";
    }
    if (opts.cons!=null) cons = ParserConstrainer.loadData(opts.cons);


    Corpus corpus = new Corpus(path,opts.treebank,1.0,!trainTestSet);
    List<Tree<String>> testTrees = null; 
    if (devTestSet)
      testTrees = corpus.getDevTestingTrees();
    if (finalTestSet)
      testTrees = corpus.getFinalTestingTrees();
    if (trainTestSet)
      testTrees = corpus.getTrainTrees();


    //    for (Tree<String> tree : testTrees){
    //    	System.out.println(tree);
    //    }

    if (opts.lowercase){
      System.out.println("Lowercasing the treebank.");
      Corpus.lowercaseWords(testTrees);
    }

    String inFileName = (opts.testAll) ? opts.filePath+"/"+opts.inFileName : opts.inFileName;
    if (inFileName==null) {
      throw new Error("Did not provide a grammar.");
    }
    System.out.println("Loading grammar from "+inFileName+".");



    int finalLevel = opts.finalLevel;
    if (finalLevel!=-1) System.out.println("Parsing with projected grammar from level "+finalLevel+".");
    boolean viterbiParse = opts.viterbi;
    if (viterbiParse) System.out.println("Computing viterbi derivation instead of max-rule parse.");
    //    CoarseToFineMaxRuleParser  parser = new CoarseToFineTwoChartsParser(grammar, lexicon, opts.unaryPenalty,finalLevel,viterbiParse,false,false,opts.accurate); 

    boolean doVariational = false;
    boolean useGoldPOS = opts.useGoldPOS;
    ConstrainedArrayParser parser =  null;

    EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String> eval = new EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String>(new HashSet<String>(Arrays.asList(new String[] {"ROOT","PSEUDO"})), new HashSet<String>(Arrays.asList(new String[] {"''", "``", ".", ":", ","})));
    EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String> tmpEval = null;
    System.out.println("The computed F1,LP,LR scores are just a rough guide. They are typically 0.1-0.2 lower than the official EVALB scores.");

    //    for (Tree<String> testTree : testTrees) {
    //    	System.out.println(testTree);
    //    } System.exit(0);

    if (externalParserFactory != null) {
      //			parser = externalParserFactory.newParser(grammar, lexicon, spanPredictor);

    } else {
      if (opts.nGrammars != 1){
        Grammar[] grammars = new Grammar[opts.nGrammars];
        Lexicon[] lexicons = new Lexicon[opts.nGrammars];
        Binarization bin = null;
        for (int nGr = 0; nGr < opts.nGrammars; nGr++){
          inFileName = opts.inFileName+"."+nGr;
          ParserData pData = ParserData.Load(inFileName);
          Numberer.setNumberers(pData.getNumbs());

          if (pData==null) {
            System.out.println("Failed to load grammar from file"+inFileName+".");
            System.exit(1);
          }
          grammars[nGr] = pData.getGrammar();
          lexicons[nGr] = pData.getLexicon();
          Numberer.setNumberers(pData.getNumbs());
          bin = pData.getBinarization();
        }
        parser = new CoarseToFineMaxRuleProductParser(grammars, lexicons, opts.unaryPenalty,-1,opts.viterbi,false,false, opts.accurate, false, true, true);
        parser.binarization = bin;
      } else {
        ParserData pData = ParserData.Load(inFileName);
        if (pData==null) {
          System.out.println("Failed to load grammar from file"+inFileName+".");
          System.exit(1);
        }
        Grammar grammar = pData.getGrammar();
        grammar.splitRules();
        Lexicon lexicon = pData.getLexicon();
        SpanPredictor spanPredictor = pData.getSpanPredictor();
        if (opts.smooth){
          System.out.println("Smoothing only lexicon.");
          //	        Smoother grSmoother = new SmoothAcrossParentBits(0.01,grammar.splitTrees);
          //	        grammar.setSmoother(grSmoother);
          //	        grammar.smooth(false);

          //	      Smoother lexSmoother = new SmoothAcrossParentBits(0.01,grammar.splitTrees);
          Smoother lexSmoother = new SmoothAcrossParentSubstate(0.01);
          lexicon.setSmoother(lexSmoother);
        }
        Numberer.setNumberers(pData.getNumbs());


        if ("plain".equals(opts.parser)){
          testTrees = Corpus.filterTreesForConditional(testTrees,opts.filterAllUnaries,opts.filterStupidFrickinWHNP,false);
          grammar.clearUnaryIntermediates();

          if (grammar instanceof HierarchicalAdaptiveGrammar){
            lexicon.explicitlyComputeScores(grammar.finalLevel);
            parser = new ConstrainedHierarchicalTwoChartParser(grammar, lexicon, spanPredictor, grammar.finalLevel);
          }else 
            parser = new ConstrainedTwoChartsParser(grammar, lexicon, spanPredictor);
          if (opts.viterbi) parser.viterbi = true;
        }
        else if ("basic".equals(opts.parser)){
          parser = new ConstrainedArrayParser(grammar, lexicon, grammar.numSubStates);
        }
        else if ("kbest".equals(opts.parser)){
          parser = new CoarseToFineNBestParser(grammar, lexicon, opts.k, opts.unaryPenalty,finalLevel,viterbiParse,false,false,opts.accurate, doVariational, useGoldPOS, true);
          tmpEval = new EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String>(Collections.singleton("ROOT"), new HashSet<String>(Arrays.asList(new String[] {"''", "``", ".", ":", ","})));
        }
        else if ("maxderivation".equals(opts.parser)){
          parser = new CoarseToFineMaxRuleDerivationParser(grammar, lexicon, opts.unaryPenalty,finalLevel,viterbiParse,false,false,opts.accurate, doVariational, useGoldPOS, true);
        }
        else parser = new CoarseToFineMaxRuleParser(grammar, lexicon, opts.unaryPenalty,finalLevel,viterbiParse,false,false,opts.accurate, doVariational, useGoldPOS, true);
        parser.binarization = pData.getBinarization();

      }
    }

    boolean kBestParsing ="kbest".equals(opts.parser);

    if (opts.allSubstatesAllowed) System.out.println("All substates are allowed.");

    if (opts.filterTrees) testTrees = Corpus.filterTreesForConditional(testTrees,opts.filterAllUnaries,opts.filterStupidFrickinWHNP,false);

    if (opts.nThreads > 1){
      System.out.println("Parsing with "+opts.nThreads+" threads in parallel.");

      MultiThreadedParserWrapper m_parser = new MultiThreadedParserWrapper(parser, opts.nThreads);
      int treeNumber = 0;

      ArrayList<Tree<String>> newList = new ArrayList<Tree<String>>();
      for (Tree<String> testTree : testTrees) {
        List<String> testSentence = testTree.getYield();
        int sentenceLength = testSentence.size();  
        if (sentenceLength > maxSentenceLength) continue;
        newList.add(testTree);
      }
      testTrees = newList;

      for (Tree<String> testTree : testTrees) {
        List<String> testSentence = testTree.getYield();
        int sentenceLength = testSentence.size();  
        if (sentenceLength > maxSentenceLength){
          System.out.println("()\n");
          continue;
        }

        //        m_parser.waitUntilFreeThread();
        m_parser.parseThisSentence(testSentence);

        while (m_parser.hasNext()){
          List<Tree<String>> parsedTrees = m_parser.getNext();
          Tree<String> tTree = testTrees.get(treeNumber++);
          Tree<String> bestTree = null;
          if (kBestParsing){
            double bestFscore = -1;
            for (Tree<String> pTree : parsedTrees){
              pTree = TreeAnnotations.unAnnotateTree(pTree, false);
              double f1 = tmpEval.evaluate(pTree, tTree, false);
              if (f1>bestFscore) {
                bestTree = pTree;
                bestFscore = f1;
              }
            }
          }
          else {
            bestTree = parsedTrees.get(0);
            bestTree = TreeAnnotations.unAnnotateTree(bestTree, false);
          }
          if (!bestTree.getChildren().isEmpty()) { 
            System.out.println(bestTree.getChildren().get(0));
          } else System.out.println("()\n");
          eval.evaluate(bestTree, tTree);
        }
      }

      while (!m_parser.isDone()){
        while (m_parser.hasNext()){
          List<Tree<String>> parsedTrees = m_parser.getNext();
          Tree<String> tTree = testTrees.get(treeNumber++);
          Tree<String> bestTree = null;
          if (kBestParsing){
            double bestFscore = -1;
            for (Tree<String> pTree : parsedTrees){
              pTree = TreeAnnotations.unAnnotateTree(pTree, false);
              if (opts.printAllKBest)
                System.out.println("\t" + pTree);
              double f1 = tmpEval.evaluate(pTree, tTree, false);
              if (f1>bestFscore) {
                bestTree = pTree;
                bestFscore = f1;
              }
            }
          }
          else {
            bestTree = parsedTrees.get(0);
            bestTree = TreeAnnotations.unAnnotateTree(bestTree, false);
          }
          if (!bestTree.getChildren().isEmpty()) { 
            System.out.println(bestTree.getChildren().get(0));
          } else System.out.println("()\n");
          if (opts.printGoldTree) System.out.println(tTree.getChildren().get(0));
          eval.evaluate(bestTree, tTree);
        }
      }
      System.out.println("Parsed "+treeNumber+" sentences.");
      eval.display(true);
      System.out.println("The computed F1,LP,LR scores are just a rough guide. They are typically 0.1-0.2 lower than the official EVALB scores.");
      System.exit(0);
    }

    if (!opts.testAll){
      int i = 0;
      int totalGoldPruned = 0;
      int totalPruned = 0;
      for (Tree<String> testTree : testTrees) {
        List<String> testSentence = testTree.getYield();
        int sentenceLength = testSentence.size();  
        if( sentenceLength >  maxSentenceLength) {
          System.out.println("()\n");
          continue;
        }
        //	      System.out.println("Gold: "+testTree);
        //	      if (true) continue;

        List<String> posTags = null;
        if (useGoldPOS) posTags = testTree.getPreTerminalYield();

        //	      if (true){
        //	      	for (int ii=0; ii<posTags.size(); ii++){
        //	      		System.out.println(testSentence.get(ii)+"\t"+posTags.get(ii));
        //	      	}
        //	      	System.out.println("");
        //	      	continue;
        //	      }
        boolean[][][][] allowedStates = null;
        if (cons!=null) {
          if (cons[i]==null) {
            i++;
            continue;
          }
          if (!opts.doNOTprojectConstraints) parser.projectConstraints(cons[i], opts.allSubstatesAllowed);
          allowedStates = cons[i];
        }
        Tree<String> parsedTree = null;
        if (kBestParsing){
          List<Tree<String>> list = parser.getKBestConstrainedParses(testSentence, posTags, opts.k);
          double bestFscore = 0;
          for (Tree<String> tree : list){
            Tree<String> tmp = TreeAnnotations.unAnnotateTree(tree, false);
            if (opts.printAllKBest)
              System.out.println("\t"+tmp);
            double f1 = tmpEval.evaluate(tmp, testTree, false);
            if (f1>bestFscore) {
              parsedTree = tmp;
              bestFscore = f1;
            }
          }
          if (parsedTree==null) parsedTree = new Tree<String>("ROOT");
        }
        else {		      
          parsedTree = parser.getBestConstrainedParse(testSentence,posTags,allowedStates);
          if (opts.verbose) System.out.println("Annotated result:\n"+Trees.PennTreeRenderer.render(parsedTree));

          parsedTree = TreeAnnotations.unAnnotateTree(parsedTree, false);
          if (useGoldPOS && parsedTree.getChildren().isEmpty()){ // parse error when using goldPOS, try without
            parsedTree = parser.getBestConstrainedParse(testSentence,null,allowedStates);
            parsedTree = TreeAnnotations.unAnnotateTree(parsedTree, false);
          }
        }


        //    		if (outFile!=null) output.write(parsedTree+"\n");
        if (!parsedTree.getChildren().isEmpty()) { 
          System.out.println(parsedTree.getChildren().get(0));
        } else System.out.println("()\nLength: "+sentenceLength);//System.out.println(testTree);//
        int numGoldPruned = 0;
        int numPruned = 0;
        if (opts.evaluateConstraints && cons != null)
        {
          numGoldPruned = countPrunedNodes(testTree, allowedStates, Numberer.getGlobalNumberer("tags"), false, 0, testTree.getYield().size());
          numPruned = countPrunedNodes(allowedStates, Numberer.getGlobalNumberer("tags"), false, 0, testTree.getYield().size());
          System.out.println("Pruned " + numGoldPruned + " constituents.");
          totalGoldPruned += numGoldPruned;
          totalPruned += numPruned;
        }
        if (opts.printGoldTree) System.out.println("Gold: " + testTree.getChildren().get(0));

        eval.evaluate(parsedTree, testTree);
        if (++i > opts.maxSentences) break;
      }
      if (opts.evaluateConstraints)
        System.out.println("Pruned total of " + totalGoldPruned + " gold constituents out of a total of " + totalPruned +" constituents pruned.");
      eval.display(true);
      System.out.println("The computed F1,LP,LR scores are just a rough guide. They are typically 0.1-0.2 lower than the official EVALB scores.");

    } else {
      int k=0;
      for (Tree<String> testTree : testTrees) {
        List<String> testSentence = testTree.getYield();
        int sentenceLength = testSentence.size();  
        if( sentenceLength >  maxSentenceLength) {
          System.out.println("()\n");
          continue;
        }

        boolean[][][][] allowedStates = null;
        if (cons!=null) {
          if (cons[k]==null) {
            k++;
            continue;
          }
          if (!opts.doNOTprojectConstraints) parser.projectConstraints(cons[k], opts.allSubstatesAllowed);
        }
        k++;
      }

      File[] fileList = null;
      final String fileName = opts.inFileName;
      if (opts.testAll){
        FilenameFilter filter = new FilenameFilter(){
          public boolean accept(File arg0, String arg1) {
            return arg1.startsWith(fileName);
          }  };
          fileList = new File(opts.filePath).listFiles(filter);
          Comparator DATE_COMPARE = new Comparator()
          {
            private Date d1 = new Date();
            private Date d2 = new Date();

            public int compare(Object file1, Object file2)
            {
              d1.setTime(((File) file1).lastModified());
              d2.setTime(((File) file2).lastModified());

              return d1.compareTo(d2);
            }
          };
          Arrays.sort(fileList,DATE_COMPARE);
      } else {
        fileList = new File[1];
      }

      int nProcess = opts.nProcess;
      double bestF1 = -1;
      String bestGrammar = null;

      ExecutorService pool = Executors.newFixedThreadPool(nProcess);
      Future[] submits = new Future[nProcess];

      for (int f=0; f<fileList.length; f+=nProcess){

        GrammarTester thisThreadConstrainer = null;
        for (int i=0; i<nProcess; i++){
          String fName = (f+i<fileList.length) ? fileList[f+i].getName() : fileList[f].getName();
          String thisGrammar = opts.filePath+"/"+fName;
          GrammarTester tester = new GrammarTester(thisGrammar, testTrees, maxSentenceLength, cons);
          submits[i] = pool.submit(tester);
        }

        while (true) {
          boolean done = true;
          for (Future task : submits) {
            done &= task.isDone();
          }
          if (done)
            break;
        }
        try {
          for (int i = 0; i < nProcess; i++) {
            Pair<EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String>,String> res = (Pair<EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String>,String>) submits[i].get();
            System.out.print(res.getSecond()+"\t");
            double thisF1 = res.getFirst().display(true);
            if (opts.printAllF1)
              System.out.println(res.getSecond() + " had F1 " + thisF1);
            if (thisF1 > bestF1){
              bestF1 = thisF1;
              bestGrammar = res.getSecond();
            }
          }
        } catch (ExecutionException e) {
          e.printStackTrace();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
      System.out.println("The best F1 was: "+bestF1);
      System.out.println("The best grammar was: "+bestGrammar);
      File finalGrammar = new File(bestGrammar);
      finalGrammar.renameTo(new File(opts.filePath+"/"+opts.inFileName));
      pool.shutdown();
    }
    if (!opts.testAll) System.exit(0);
  }


  GrammarTester(String fName, List<Tree<String>> tT, int maxL, boolean[][][][][] c){
    testTrees = tT;
    cons = c;
    fileName = fName;
    maxSentenceLength = maxL;
  }

  /**
   * @param allowedStates
   * @param globalNumberer
   * @param b
   * @param i
   * @param size
   * @return
   */
  private static int countPrunedNodes(boolean[][][][] allowedStates,
      Numberer globalNumberer, boolean b, int start, int end) {
    int total = 0;
    for (int i = start; i < end; ++i)
    {
      for (int j = i+1; j <= end; ++j)
      {
        for (int state = 0; state < allowedStates[i][j].length; ++state)
        {
          if (!hasTrue(allowedStates[i][j][state]))
            total++;
        }
      }
    }
    return total;
  }

  public static List<Integer>[][][] loadData(String fileName) {
    List<Integer>[][][] data = null;
    try {
      FileInputStream fis = new FileInputStream(fileName); // Load from file
      GZIPInputStream gzis = new GZIPInputStream(fis); // Compressed
      ObjectInputStream in = new ObjectInputStream(gzis); // Load objects
      data = (List<Integer>[][][])in.readObject(); // Read the mix of grammars
      in.close(); // And close the stream.
    } catch (IOException e) {
      System.out.println("IOException\n"+e);
      return null;
    } catch (ClassNotFoundException e) {
      System.out.println("Class not found!");
      return null;
    }
    return data;
  }

  private static String[] addOptions(String[] a, String[] b) {
    String[] res = new String[a.length+b.length];
    for (int i=0; i<a.length; i++){
      res[i] = a[i];
    }
    for (int i=0; i<b.length; i++){
      res[i+a.length] = b[i];
    }
    return res;
  }

  private static boolean isAllowed(String label, Numberer tagNumberer, boolean[][] cons, boolean isPreTerminal)
  {

    for (int state = 0; state < cons.length; ++state)
    {
      boolean[] allowed = cons[state];
      assert tagNumberer.total() > state;
      String asString = (String)tagNumberer.object(state);
      String unannotatedLabel = asString;
      if (!isPreTerminal)
        unannotatedLabel = TreeAnnotations.unAnnotateTree(new Tree<String>(asString, Collections.singletonList(new Tree<String>("FakeLabel"))), false).getLabel();
      if (unannotatedLabel.equals(label))
      {
        if (hasTrue(allowed))
          return true;
      }
    }
    return false;
  }

  private static int countPrunedNodes (Tree<String> tree, boolean[][][][] cons, Numberer tagNumberer,boolean splitRoot, int from, int to){

    int total = 0;
    if (!isAllowed(tree.getLabel(),tagNumberer,cons[from][to],tree.isPreTerminal()))
    {
      total += 1;
    }
    if (tree.isPreTerminal()) {
      return total;
    }


    //    if (label<0) label =0;
    ////    System.out.println(label + " " +tree.getLabel());
    //    if (label>=numStates.length){
    ////    	System.err.println("Have never seen this state before: "+tree.getLabel());
    ////      StateSet newState = new StateSet(zero, one, tree.getLabel().intern(),(short)from,(short)to);
    ////      return new Tree<StateSet>(newState);
    //    }
    //    short nodeNumStates = allSplitTheSame ? numStates[0] : numStates[label];
    //    if (!splitRoot) nodeNumStates = 1;
    //    StateSet newState = new StateSet(label, nodeNumStates, null, (short)from , (short)to);
    //    Tree<StateSet> newTree = new Tree<StateSet>(newState);
    //    List<Tree<StateSet>> newChildren = new ArrayList<Tree<StateSet>>(); 
    for (Tree<String> child : tree.getChildren()) {
      short length = (short) child.getYield().size(); 
      total += countPrunedNodes(child, cons, tagNumberer, true, from, from+length);
      from += length;

    }

    return total;

  }
  public static boolean hasTrue(boolean[] a)
  {
    boolean hasTrue = false;
    if (a == null) return hasTrue;
    for (boolean b : a)
      hasTrue |= b;
    return hasTrue;
  }

  public Pair<EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String>, String> call() throws Exception {
    EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String> eval = new EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String>(Collections.singleton("ROOT"), new HashSet<String>(Arrays.asList(new String[] {"''", "``", ".", ":", ","})));
    ParserData pData = ParserData.Load(fileName);
    if (pData==null) {
      System.out.println("Failed to load grammar from file"+fileName+".");
      System.exit(1);
    }
    Grammar grammar = pData.getGrammar();
    grammar.splitRules();
    Lexicon lexicon = pData.getLexicon();
    grammar.clearUnaryIntermediates();
    lexicon.explicitlyComputeScores(grammar.finalLevel);
    if (GrammarTester.Options.smooth){
      System.out.println("Smoothing only the lexicon.");
      Smoother lexSmoother = new SmoothAcrossParentSubstate(0.01);
      lexicon.setSmoother(lexSmoother);
    }

    SpanPredictor spanPredictor = pData.getSpanPredictor();

    ConstrainedArrayParser parser = null;// new
    // ConstrainedHierarchicalTwoChartParser
    // (grammar, lexicon, spanPredictor,
    // grammar.finalLevel);
    if (grammar instanceof HierarchicalAdaptiveGrammar) {
      lexicon.explicitlyComputeScores(grammar.finalLevel);
      parser = new ConstrainedHierarchicalTwoChartParser(grammar, lexicon,
          spanPredictor, grammar.finalLevel);
    } else
      parser = new ConstrainedTwoChartsParser(grammar, lexicon, spanPredictor);

    int i=0;
    for (Tree<String> testTree : testTrees) {
      List<String> testSentence = testTree.getYield();
      int sentenceLength = testSentence.size();  
      if(sentenceLength >  maxSentenceLength) continue;

      Tree<String> parsedTree = null;
      boolean[][][][] con = (cons==null) ? null : cons[i];
      parsedTree = parser.getBestConstrainedParse(testSentence,null,con);
      parsedTree = TreeAnnotations.unAnnotateTree(parsedTree, false);

      eval.evaluate(parsedTree, testTree, false);
      i++;
    }
    return new Pair<EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String>,String>(eval,fileName);

  }

}

