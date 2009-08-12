package edu.berkeley.nlp.HDPPCFG;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.StringReader;
import java.util.*;
import java.util.zip.GZIPOutputStream;

import edu.berkeley.nlp.syntax.SpanTree;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.Numberer;

public class ParserConstrainer {
	static final int nSubStates = 2; // for now hardwired (so that we can experiments with the old
															// grammars, but the field will be added to the parserData class
	
	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
    if (args.length!=6 && args.length!=8) {
      System.out.println(
          "usage: java ParserConstrainer <path to corpus or \"null\">\n" +
          "          <lang> <train/dev/final/val (test set)>\n" +
          "          <input grammar file> <output base name> <threshold>\n" +
          "          <path to Chinese corpus or \"null\" (optional)> <whether to use" +
          "          only the short part of the Chinese corpus>\n");
      System.exit(2);
    }
  
    String pathWSJ = args[0];
    if (pathWSJ.equals("null"))
    	pathWSJ = null;
    int lang= Integer.parseInt(args[1]);
    String pathChinese = null;
    boolean chineseShort = false;
    if (args.length==8) {
    	pathChinese = args[6];
    	chineseShort = Boolean.parseBoolean(args[7]);
    }
    if (pathChinese.equals("null"))
    	pathChinese = null;
    System.out.println("Loading corpus from "+pathWSJ);

    String testSetString = args[2];
    boolean devTestSet = testSetString.equals("dev");
    boolean finalTestSet = testSetString.equals("final");
    boolean trainTestSet = testSetString.equals("train");
    boolean valTestSet = testSetString.equals("val");
    /*if (!(devTestSet ^ finalTestSet)) {
    	System.out.println("I didn't understand dev/final test set argument "+testSetString);
    	System.exit(1);
    }*/
    System.out.println(" using "+testSetString+" test set");
    
    Corpus corpus = new Corpus(pathWSJ,lang,1.0,chineseShort,false);
    List<Tree<String>> testTrees = null; 
    if (devTestSet)
    	testTrees = corpus.getDevTestingTrees();
    if (finalTestSet)
    	testTrees = corpus.getFinalTestingTrees();
    if (trainTestSet)
    	testTrees = corpus.getTrainTrees();
    if (valTestSet)
    	testTrees = corpus.getValidationTrees();
    
    String inFileName = args[3];
    System.out.println("Loading grammar from "+inFileName+".");
    ParserData pData = ParserData.Load(inFileName);
    if (pData==null) {
      System.out.println("Failed to load grammar from file "+inFileName+".");
      System.exit(1);
    }
    Grammar grammar = pData.getGrammar();
    grammar.splitRules();
    LexiconInterface lexicon = pData.getLexicon();
    Numberer.setNumberers(pData.getNumbs());
    Numberer tagNumberer = Numberer.getGlobalNumberer("tags");
    //    ConstrainedMaxRuleParser parser = new ConstrainedMaxRuleParser(grammar, lexicon, grammar.numSubStates, 0.5);
    //parser.setNoConstraints(true);
    ArrayParser parser = new ArrayParser(grammar, lexicon);
    int v_markov = pData.getV_markov();    
    int h_markov = pData.getH_markov();
    Binarization binarization = pData.getBinarization();

    double threshold = Double.parseDouble(args[5]);
    String outBaseName = args[4]+"_"+threshold;
    System.out.println("All states with posterior probability below "+threshold+" will be pruned.");
    System.out.println("The constraints will be written to "+outBaseName+".");
    
    // filter trees first:
    List<Tree<String>> testTreesFiltered = new ArrayList<Tree<String>>();
    for (Tree<String> testTree : testTrees) {
      List<String> testSentence = testTree.getYield();
      if (testSentence.size() > 40) {//devTestSet && ( testSentence.size() > GrammarTester.MAX_SENTENCE_LENGTH )) {
      	continue;
      }
      testTreesFiltered.add(testTree);
    }
    testTrees = testTreesFiltered;

    List<Integer>[][][] recentHistory = new ArrayList[testTrees.size()][][];
    int recentHistoryIndex = 0;
    int sentenceNumber = 1, unreachable = 0;
    int blockIndex = 0;
    for (Tree<String> testTree : testTrees) {
    	List<String> testSentence = testTree.getYield();
    	System.out.print("\n"+sentenceNumber+". Length "+testSentence.size());
//    	if (testSentence.size() > 150) continue;

    	List<Integer>[][] possibleStates = parser.getPossibleStates(testSentence,threshold);

      recentHistory[recentHistoryIndex] = possibleStates;
      testTree = TreeAnnotations.processTree(testTree,v_markov,h_markov,binarization,false,false,false);
      SpanTree<String> sTree = convertToSpanTree(testTree);
      sTree.setSpans();

      // leave the ROOT node out when computing the reachability.
      boolean reachable = isGoldReachable(sTree.getChildren().get(0), possibleStates, tagNumberer);
      if (!reachable){ unreachable++; }
      
      // save the list of possible states after 10 sentences
      recentHistoryIndex++;
      sentenceNumber++;
    	if (recentHistoryIndex>0 && (recentHistoryIndex % 10000 == 0)) {
    		String fileName = outBaseName+"-"+blockIndex+".data";
    		blockIndex++;
    		recentHistoryIndex = 0;
    		saveData(recentHistory, fileName);
    	}
    }
  	if (recentHistoryIndex!=0) {
  		String fileName = outBaseName+"-"+blockIndex+".data";
  		saveData(recentHistory, fileName);
  	}
    System.out.println("For "+unreachable+" out of "+(sentenceNumber-1)+" sentences the gold parse is not reachable.");

    System.out.println("Touched "+parser.touchedRules+" rules.");
    System.exit(0);
  }
	
	
	public static boolean saveData(List<Integer>[][][] data, String fileName){
    try {
      //here's some code from online; it looks good and gzips the output!
      //  there's a whole explanation at http://www.ecst.csuchico.edu/~amk/foo/advjava/notes/serial.html
      // Create the necessary output streams to save the scribble.
      FileOutputStream fos = new FileOutputStream(fileName); // Save to file
      GZIPOutputStream gzos = new GZIPOutputStream(fos); // Compressed
      ObjectOutputStream out = new ObjectOutputStream(gzos); // Save objects
      out.writeObject(data); // Write the mix of grammars
      out.flush(); // Always flush the output.
      out.close(); // And close the stream.
    } catch (IOException e) {
      System.out.println("IOException: "+e);
      return false;
    }
    return true;
  }

	/*
	 * Is not needed since our pruing grammar is usually unsplit.
	 */
	/*private static boolean checkState(int start, int end, String state, List[][] possibleStates, Numberer tagNumberer){
		boolean reachable = false;
		for (int n=0; n<nSubStates; n++){
			System.out.println(state +" "+tagNumberer.number(state+"-"+n)+" s "+start+" e "+end);
			reachable = possibleStates[start][end].contains(tagNumberer.number(state+"-"+n));
			if (reachable) { return true;}
		}
		return false;
	}
  */
  
  public static boolean isGoldReachable(SpanTree<String> gold, List[][] possibleStates, Numberer tagNumberer){
  	boolean reachable = true;
		reachable = possibleStates[gold.getStart()][gold.getEnd()].contains(tagNumberer.number(gold.getLabel()));
  	if (reachable && (!gold.isLeaf())){
			for (SpanTree<String> child : gold.getChildren()){
				reachable = isGoldReachable(child, possibleStates, tagNumberer);
				if (!reachable) return false;
			}
		}
  	if (!reachable) {
  		System.out.println("Cannot reach state "+gold.getLabel()+" spanning from "+gold.getStart()+" to "+gold.getEnd()+".");
  	}
  	return reachable;
  }
  
  
	public static SpanTree<String> convertToSpanTree(Tree<String> tree){
		if (tree.isPreTerminal()){
			return new SpanTree<String>(tree.getLabel());
		}
		if (tree.getChildren().size()>2) System.out.println("Binarize properly first!");
		SpanTree<String> spanTree = new SpanTree<String>(tree.getLabel());
		List<SpanTree<String>> spanChildren = new ArrayList<SpanTree<String>>(); 
		for (Tree<String> child : tree.getChildren()){
			SpanTree<String> spanChild = convertToSpanTree(child);
			spanChildren.add(spanChild);
		}
		spanTree.setChildren(spanChildren);
		return spanTree;
	}


}
