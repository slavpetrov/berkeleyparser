package edu.berkeley.nlp.HDPPCFG;

import edu.berkeley.nlp.parser.EnglishPennTreebankParseEvaluator;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees;
import edu.berkeley.nlp.util.CommandLineUtils;
import edu.berkeley.nlp.util.Numberer;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Reads in the Penn Treebank and generates N_GRAMMARS different grammars.
 *
 * @author Slav Petrov
 */
public class ConstrainedParserTester  {

  @SuppressWarnings("unchecked")
	public static void main(String[] args) throws IOException {
    if (args.length<1) {
      System.out.println(
          "usage: java ConstrainedParserTester " +
          "               -i     Input File for Grammar (Required)\n" +
          "          			-c     Constraint Base Name\n" +
      		"               -path  Path to Corpus (Default: null)\n" +
      		"               -lang  Language:  1-ENG, 2-CHN, 3-GER, 4-ARB (Default: 1)\n" +
          "               -chsh  If this is enabled, then we train on a short segment of\n" +
          "                      the Chinese treebank (Default: false)" +
      		"               -trfr  The fraction of the training corpus to keep (Default: 1.0)\n" +
          "          			-s     Test Set: dev/final/train (Default: dev)\n"+
          "								-p		 Parser: vanilla/coarse-to-fine/coarse-two-fine/max-bracket/max-rule/n-best/sum-rule/n-best-sum (Default: vanilla)\n"+
          "								-type	 For coarse-to-fine parser only: viterbi/max-rule (Default: max-rule)\n"+
          "								-v		 verbose/quiet (Default: quiet)\n"+
          "               -split Number of segments to split evaluation into (Default: 1)\n" +
          "               -spme  Which segment this parser is evaluating, indexed from 0 (Default: 0)\n"+
          "								-from	 Start testing at this tree (Default: 0)"+
          "								-to		 Stop testing at this tree (Default: end)"+
          "								-f		 Filter rules with prob under f (Default: -1)"+          
          "								-maxL	 Maximum sentence length (Default: 40)"+
          "								-N	   Produce N-Best list (Default: 1)"+
          "								-Nsamp Use Nsamp samples for risk calculations (Default: 100)"+
          "								-gold  Use gold POS tags (Default: false)"+
          "								-ll    Compute LL of Gold Trees only (Default: false)"+
          "								-endL  Final level for coarse to fine (Default: -1)"+
          "								-out   Write parses to textfile (Default: false)"+
          "     For the max-brackets parser:\n"+
          "								-m 		 Maximum Number of restored unaries for same span (Default: 1\n"+
          "								-t 		 Threshold for restoring unaries for same span (Default: 1.0)\n"
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
    int lang = Integer.parseInt(CommandLineUtils.getValueOrUseDefault(input, "-lang", "1"));
    System.out.println("Loading trees from "+path+" and using language "+lang);
    
    boolean chineseShort = Boolean.parseBoolean(CommandLineUtils
				.getValueOrUseDefault(input, "-chsh", "false"));
    
    boolean computeLLonly = Boolean.parseBoolean(CommandLineUtils
				.getValueOrUseDefault(input, "-ll", "false"));
    
    double trainingFractionToKeep = Double.parseDouble(CommandLineUtils
				.getValueOrUseDefault(input, "-trfr", "1.0"));

    boolean verbose = CommandLineUtils.getValueOrUseDefault(input, "-v","quiet").equals(("verbose"));

    Corpus.hmm = Boolean.parseBoolean(CommandLineUtils.
        getValueOrUseDefault(input, "-hmm", "false"));
    System.out.println("HMM mode: " + Corpus.hmm);
    
    boolean viterbiParse = CommandLineUtils.getValueOrUseDefault(input, "-type","max-rule").equals(("viterbi"));

    int nbest = Integer.parseInt(CommandLineUtils.getValueOrUseDefault(input, "-N","1"));
    int nSamples = Integer.parseInt(CommandLineUtils.getValueOrUseDefault(input, "-Nsamp","100"));
    int maxUnaries = Integer.parseInt(CommandLineUtils.getValueOrUseDefault(input, "-m","1"));
    int endL = Integer.parseInt(CommandLineUtils.getValueOrUseDefault(input, "-endL","-1"));
    double threshold = Double.parseDouble(CommandLineUtils.getValueOrUseDefault(input, "-t","1.0"));
    double filter = Double.parseDouble(CommandLineUtils.getValueOrUseDefault(input, "-f","-1"));
    
    int evalSplitSegments = Integer.parseInt(CommandLineUtils.getValueOrUseDefault(input, "-split","1"));
    int evalSplitMySegment = Integer.parseInt(CommandLineUtils.getValueOrUseDefault(input, "-spme","0"));
    
    String testSetString = CommandLineUtils.getValueOrUseDefault(input, "-s","dev");
    boolean devTestSet = testSetString.equals("dev");
    boolean finalTestSet = testSetString.equals("final");
    boolean trainTestSet = testSetString.equals("train");
    boolean valTestSet = testSetString.equals("val");
    if (!(devTestSet || finalTestSet || trainTestSet || valTestSet)) {
    	System.out.println("I didn't understand dev/final test set argument "+testSetString);
    	System.exit(1);
    }
    System.out.println(" using "+testSetString+" test set");
    
    Corpus corpus = new Corpus(path,lang,trainingFractionToKeep, chineseShort,!(trainTestSet||valTestSet));
    List<Tree<String>> testTrees = null; 
    if (devTestSet)
    	testTrees = corpus.getDevTestingTrees();
    if (finalTestSet)
    	testTrees = corpus.getFinalTestingTrees();
    if (trainTestSet)
    	testTrees = corpus.getTrainTrees();
    if (valTestSet)
    	testTrees = corpus.getValidationTrees();

    System.out.println("The test set has "+testTrees.size()+" test trees.");
    
    String outFile = CommandLineUtils.getValueOrUseDefault(input, "-out", null);
    BufferedWriter output = null;
		try {
			if (outFile!=null)
		    output = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), Charset.forName("UTF-8")));//GB18030")));
		} catch (Exception ex) {
			ex.printStackTrace();
		}
    
    String inFileName = CommandLineUtils.getValueOrUseDefault(input, "-i", null);
    if (inFileName==null) {
    	throw new Error("Did not provide a grammar.");
    }
    System.out.println("Loading grammar from "+inFileName+".");

    ParserData pData = ParserData.Load(inFileName);
    if (pData==null) {
      System.out.println("Failed to load grammar from file"+inFileName+".");
      System.exit(1);
    }
    Numberer.setNumberers(pData.getNumbs());
    Numberer tagNumberer =  Numberer.getGlobalNumberer("tags");

    Grammar grammar = pData.getGrammar().copyGrammar();
    grammar.splitRules();
    LexiconInterface lexicon = pData.getLexicon();
    //lexicon.setConditional(true);
    if (filter>0){
    	double power = 1.0;
    	//System.out.println("Removing rules with probability less than "+filter);
    	grammar.removeUnlikelyRules(filter,power);
    	//System.out.println("And also tags with probability below "+filter);
    	lexicon.removeUnlikelyTags(filter);
    }
//    lexicon.newMstep();
    boolean useGoldPOS = CommandLineUtils.getValueOrUseDefault(input, "-gold", "").equals("true");
    short[] numSubStatesArray = grammar.numSubStates;
    
    String baseFileName = CommandLineUtils.getValueOrUseDefault(input, "-c", "null");
    boolean noConstraints = false;
    if (baseFileName.equals("null")&&!useGoldPOS) {
    	System.out.println("Will parse without constraints...");
    	noConstraints = true;
    }
    
    int maxSentenceLength = Integer.parseInt(CommandLineUtils.getValueOrUseDefault(input, "-maxL", "40"));
    System.out.println("Will skip sentences with more than "+maxSentenceLength+" words.");
    
    //maxSentenceLength = Math.min(maxSentenceLength,GrammarTester.MAX_SENTENCE_LENGTH);
    
    
    String parser_type = CommandLineUtils.getValueOrUseDefault(input, "-p", "vanilla");
    ConstrainedParser parser = null;
    if (parser_type.equals("vanilla")) {  
  		grammar.logarithmMode();
  		lexicon.logarithmMode();
  		System.out.println("Using viterbi parser.");
      parser = new ConstrainedArrayParser(grammar, lexicon, numSubStatesArray);
//    } else if (parser_type.equals("max-bracket")) { 
//      parser = new ConstrainedLabelledRecallParser(grammar, lexicon, numSubStatesArray, maxUnaries, threshold);
//    } else if (parser_type.equals("n-best")) { 
//      parser = new ConstrainedNbestMaxRuleParser(grammar, lexicon, numSubStatesArray, threshold,nbest);
//    } else if (parser_type.equals("n-best-sum")) { 
//      parser = new ConstrainedNbestMaxRuleSumPosParser(grammar, lexicon, numSubStatesArray, threshold,nbest);
//    } else if (parser_type.equals("sum-rule")) {
//      parser = new ConstrainedMaxRuleSumPosParser(grammar, lexicon, numSubStatesArray, threshold);      
//    } else if (parser_type.equals("coarse-to-fine")) {
//      parser = new CoarseToFineMaxRuleParser(grammar, lexicon, threshold,endL,viterbiParse,false,false);      
//    } else if (parser_type.equals("coarse-two-fine")) {
//      parser = new ConstrainedCoarseToFineTwoChartsParser(grammar, lexicon, threshold,endL,viterbiParse,false,false);      
       //vParser = new CoarseToFineMaxRuleParser(grammar, lexicon, threshold,endL,true,false,false);    
      //nbest = 2;
      } else {
      parser = new ConstrainedMaxRuleParser(grammar, lexicon, numSubStatesArray, threshold);
      System.out.println("Using max-rule parser.");
    }
    if (noConstraints) parser.setNoConstraints(true);
    else System.out.println("Using constaints from "+baseFileName+".data");
    
    
    EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String>[] evals = new EnglishPennTreebankParseEvaluator.LabeledConstituentEval[nbest+1];
    for (int i=0; i<nbest+1; i++){
    	evals[i] = new EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String>(Collections.singleton("ROOT"), new HashSet<String>(Arrays.asList(new String[] {"''", "``", ".", ":", ","})));
    }
    EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String> oracleEval = new EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String>(Collections.singleton("ROOT"), new HashSet<String>(Arrays.asList(new String[] {"''", "``", ".", ":", ","})));
    EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String> rand_eval = new EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String>(Collections.singleton("ROOT"), new HashSet<String>(Arrays.asList(new String[] {"''", "``", ".", ":", ","})));
    EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String> tmp_const = new EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String>(Collections.singleton("ROOT"), new HashSet<String>(Arrays.asList(new String[] {"''", "``", ".", ":", ","})));
    EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String>[] const_evals = new EnglishPennTreebankParseEvaluator.LabeledConstituentEval[4];
    for (int i=0; i<4; i++){
    	const_evals[i] = new EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String>(Collections.singleton("ROOT"), new HashSet<String>(Arrays.asList(new String[] {"''", "``", ".", ":", ","})));
    }
/*    EnglishPennTreebankParseEvaluator.RuleEval<String> tmp_rule = new EnglishPennTreebankParseEvaluator.RuleEval<String>(Collections.singleton("ROOT"), new HashSet<String>(Collections.EMPTY_LIST));
    EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String>[] rule_evals = new EnglishPennTreebankParseEvaluator.LabeledConstituentEval[4];
    for (int i=0; i<4; i++){
    	rule_evals[i] = new EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String>(Collections.singleton("ROOT"), new HashSet<String>(Arrays.asList(new String[] {"''", "``", ".", ":", ","})));
    }*/
    List<Integer>[][][] recentHistory = new ArrayList[100][][];
    int recentHistoryIndex = 100;
    int sentenceNumber = 1;

    // filter trees first:
    List<Tree<String>> testTreesFiltered = new ArrayList<Tree<String>>();
    for (Tree<String> testTree : testTrees) {
      List<String> testSentence = testTree.getYield();
      if( testSentence.size() >  maxSentenceLength) continue;
      testTreesFiltered.add(testTree);
    }
    testTrees = testTreesFiltered;
    
    if (computeLLonly){
	int i =0;
      ArrayParser llParser = new ArrayParser(grammar, lexicon);
  		List<Tree<String>> binTestTrees = Corpus.binarizeAndFilterTrees(corpus
  				.getDevTestingTrees(), true, pData.v_markov,pData.h_markov,
  				1000, pData.bin, false,false, false, false);

    	double ll = 0;
      StateSetTreeList testStateSetTrees = new StateSetTreeList(binTestTrees, numSubStatesArray, false, tagNumberer, false);
 			for (Tree<StateSet> tree : testStateSetTrees){
			    System.out.println(testTrees.get(i++).getChildren().get(0));
			    List<StateSet> testSentence = tree.getYield();
 	      int sentenceLength = testSentence.size();  
 	      if( devTestSet && sentenceLength > maxSentenceLength ) continue;
 				llParser.doInsideScores(tree,false,false);  // Only inside scores are needed here
				ll = tree.getLabel().getIScore(0);
				ll = Math.log(ll) + (100*tree.getLabel().getIScale());
				System.out.println("Gold LL for sentence of length "+sentenceLength+" is: "+ll);
  		}
 			System.exit(0);
    }
    
    //calculate which trees we want to evaluate
    int totalNumberOfTestTrees = testTrees.size();
    double treesPerSegment = ((double)totalNumberOfTestTrees)/evalSplitSegments;
    int myFirstTree = (int)Math.ceil(treesPerSegment * evalSplitMySegment);
    int nextSegmentFirstTree = (int)Math.ceil(treesPerSegment * (evalSplitMySegment+1)); 
    
    int treeNumber = -1;
    if (!noConstraints&&!useGoldPOS){
    	String fileName = baseFileName+".data";
    	recentHistory = loadData(fileName);
    	recentHistoryIndex=0;
    }
    fig.basic.StatFig hmmCorrectSentences = new fig.basic.StatFig();
    fig.basic.StatFig hmmCorrectPositions = new fig.basic.StatFig();
    for (Tree<String> testTree : testTrees) {
      List<String> testSentence = testTree.getYield();
      int sentenceLength = testSentence.size();  
      //if( sentenceLength > GrammarTester.MAX_SENTENCE_LENGTH ) continue;
      if (noConstraints) recentHistoryIndex = 0;

    	treeNumber++;
    	if (treeNumber < myFirstTree || treeNumber >= nextSegmentFirstTree) {
    		System.out.println("skipping tree number "+treeNumber);
    	} else {
    		System.out.println("parsing tree number "+treeNumber);
    		
    		List<Integer>[][] constrains = recentHistory[recentHistoryIndex];
    		
    		if (useGoldPOS){
    			constrains = new ArrayList[sentenceLength][sentenceLength+1];
      		System.out.println("Warning: using GOLD POS!");
    			
    			List<String> goldTags = testTree.getPreTerminalYield();
      		for (int start = 0; start < sentenceLength; start++) {
     				for (int end=start+1; end <=sentenceLength; end++){
	      			ArrayList<Integer> allowedStates =new ArrayList<Integer>();
	     				for (int state = 0; state<grammar.numStates; state++){
	     					if (grammar.isGrammarTag(state)) {
	     						//System.out.println("Allowed for " +start +" "+ (String)tagNumberer.object(state));
	     						allowedStates.add(state);
	     					}
	     				}
	     				allowedStates.add(tagNumberer.number(goldTags.get(start)));
	     				constrains[start][end] = allowedStates;
     				}
      		}
    		}
    		if (parser_type.equals("n-best")||parser_type.equals("n-best-sum")) {
    			System.out.println(""+nbest +"\t"+treeNumber);
    			double[] treeLLs = new double[nbest+1];
    			Tree<String>[] sampledTrees = new Tree[nSamples];
    			List<Tree<String>> parsedTrees = parser.getNBestConstrainedParses(testSentence,constrains,treeLLs,sampledTrees);
    	    recentHistoryIndex++;
          sentenceNumber++;
    			//if (treeLLs[nbest] < 0.3) continue;
          if (sampledTrees[0]==null) continue;
          
       	double[][] riskScores = new double[nbest][4];
  
/*    		for (int I=0; I<nbest; I++){
  	      	riskScores[I] = tmp_rule.massEvaluate(parsedTrees.get(I),sampledTrees);
  	    }
  */	    
  	    List<Tree<String>> unAnnotatedTrees = new ArrayList<Tree<String>>();
    			int i=0, mI = 0;
    			double currF1 = -1, maxF1 = -1;
    			for (Tree<String> parsedTree : parsedTrees){
    				parsedTree = TreeAnnotations.unAnnotateTree(parsedTree);
    				unAnnotatedTrees.add(parsedTree);
    				System.out.print((i+1)+".");
    				currF1 = evals[i++].evaluate(parsedTree, testTree);
    				if (currF1>maxF1){
    					mI=i-1;
    					maxF1=currF1;
    				}
    			}
    			Tree<String> parsedTree = unAnnotatedTrees.get(mI);
    			System.out.print("O: ");
    			oracleEval.evaluate(parsedTree,testTree);
    	  	// append the min risk tree also

    			/*for (int itree=0; itree < nbest; itree++){
    				System.out.println(treeLLs[itree]+"\n"+unAnnotatedTrees.get(itree));//sampledTrees.get(itree));//unAnnotatedTrees.get(itree));
    			}*/
  				
    /*	    for (int riskType=0; riskType<4; riskType++){
    	    	double maxExpected = -1;
    	    	int maxExpectedID = 0;
    	    	for (int I=0; I<nbest; I++){
    	    		if (riskScores[I][riskType]>maxExpected){
    	    			maxExpected = riskScores[I][riskType];
    	    			maxExpectedID = I;
    	    		}
    	    	}
    	    	System.out.print("R"+riskType+": ");
    	    	rule_evals[riskType].evaluate(unAnnotatedTrees.get(maxExpectedID),testTree);
    	    }
   	  */  

    	    for (int I=0; I<sampledTrees.length; I++){
    	    	sampledTrees[I] = TreeAnnotations.unAnnotateTree(sampledTrees[I]);
    	    }
    	    for (int I=0; I<nbest; I++){
  	      	riskScores[I] = tmp_const.massEvaluate(unAnnotatedTrees.get(I),sampledTrees);
	  	    }
	  	    for (int riskType=0; riskType<4; riskType++){
	  	    	double maxExpected = 0;
	  	    	int maxExpectedID = -1;
	  	    	for (int I=0; I<nbest; I++){
	  	    		if (riskScores[I][riskType]>maxExpected){
	  	    			maxExpected = riskScores[I][riskType];
	  	    			maxExpectedID = I;
	  	    		}
	  	    	}
	  	    	if (maxExpectedID==-1){
	  	    		maxExpectedID = (int)Math.floor(Math.random()*nbest);
	  	    	}
	  	    	System.out.print("C"+riskType+": ");
    	    	const_evals[riskType].evaluate(unAnnotatedTrees.get(maxExpectedID),testTree);
    	    }
	  	    rand_eval.evaluate(unAnnotatedTrees.get((int)(Math.floor(Math.random()*nbest))),testTree);
	  	    
    			continue;
    		}
    		
    		    		
    		Tree<String> parsedTree = parser.getBestConstrainedParse(testSentence,constrains);
//    		double totalLL = parser.getLogLikelihood();
    		//Tree<String> vParsedTree = vParser.getBestConstrainedParse(testSentence,constrains);
    		//double vLL = vParser.getLogLikelihood();
    		
    		//System.out.println("Total LL "+totalLL + " viterbi tree LL "+vLL);
    		
    		//double condProb = Math.exp(vLL-totalLL);
    		//System.out.println("The conditional probability of this parse tree is "+condProb);
    		
    		//if (condProb < 0.1) continue;
//  		double currLL = 1;//grammar.logLikelihood(parsedTree, lexicon);
    		if (verbose) System.out.println("Annotated result:\n"+Trees.PennTreeRenderer.render(parsedTree));
    		parsedTree = TreeAnnotations.unAnnotateTree(parsedTree);
    		//vParsedTree = TreeAnnotations.unAnnotateTree(vParsedTree);
    		//if (!vanillaParser){
    		//parsedTree = TreeAnnotations.removeSuperfluousNodes(parsedTree);
    		//}
    		//System.out.println(testTree.getChildren().get(0));
    		if (outFile!=null) output.write(parsedTree+"\n");
    		if (!parsedTree.getChildren().isEmpty()) { 
	         			System.out.println(parsedTree.getChildren().get(0));
	        // 			System.out.println("\n"+vParsedTree.getChildren().get(0));
    		} else System.out.println("()");
//  		System.out.println(TreeAnnotations.removeSuperfluousNodes(testTree));
    		//System.out.println("Original tree:\n"+testTree);
    		//System.out.println("Parsing result:\n"+Trees.PennTreeRenderer.render(parsedTree));
//  		System.out.println("Log likelihood:"+currLL);
    		
        if(Corpus.hmm) {
          boolean allCorrect = true;
          Tree<String> predPtr = parsedTree.getChildren().get(0);
          Tree<String> truePtr = testTree.getChildren().get(0);
          for(int i = 0; i < testSentence.size(); i++) {
            String predTag = predPtr.getChildren().get(0).getLabel();
            String trueTag = truePtr.getChildren().get(0).getLabel();
            //System.out.println(i + " " + predTag + " " + trueTag);
            boolean posCorrect = predTag.equals(trueTag);
            hmmCorrectPositions.add(posCorrect ? 1 : 0);
            if(!posCorrect) allCorrect = false;
            if(i < testSentence.size()-1) {
              predPtr = predPtr.getChildren().get(1);
              truePtr = truePtr.getChildren().get(1);
            }
          }
          hmmCorrectSentences.add(allCorrect ? 1 : 0);
        }
        else
          evals[0].evaluate(parsedTree, testTree);
    	//	evals[1].evaluate(vParsedTree, testTree);
    	}
      recentHistoryIndex++;
      sentenceNumber++;
    }
    parser.printUnaryStats();
    //parser.printStateAndRuleTallies();
    for (int i=0; i<nbest; i++){
    	System.out.print((i+1)+". best:");
    	evals[i].display(true);
    }
 		if (parser_type.equals("n-best")||parser_type.equals("n-best-sum")) {
 	   	System.out.print("LL Reranked F1:");
 	   	evals[nbest].display(true);
 	   	System.out.print("Oracle F1:");
 	   	oracleEval.display(true);
/* 	   	for (int riskType=0; riskType<4; riskType++){
 	   		if (riskType==0)
 	   			System.out.print("MinRule P-risk: ");
 	   		else if (riskType==1)
 	   			System.out.print("MinRule R-risk: ");
 	   		else if (riskType==2)
 	   			System.out.print("MinRule F-risk: ");
 	   		else 
 	   			System.out.print("MinRule E-risk: ");
 	   		rule_evals[riskType].display(true);
 	   	}*/
 	   	for (int riskType=0; riskType<4; riskType++){
 	   		if (riskType==0)
 	   			System.out.print("MinCons P-risk: ");
 	   		else if (riskType==1)
 	   			System.out.print("MinCons R-risk: ");
 	   		else if (riskType==2)
 	   			System.out.print("MinCons F-risk: ");
 	   		else 
 	   			System.out.print("MinCons E-risk: ");
 	   		const_evals[riskType].display(true);
 	   	}
 	   	System.out.print("Random F1:");
 	   	rand_eval.display(true);
 		}
 		if (outFile!=null){
 			output.flush();
 			output.close();
 		}
    if(Corpus.hmm)
      System.out.printf("F1: %.2f EX: %.2f\n", hmmCorrectPositions.mean()*100, hmmCorrectSentences.mean()*100);

    System.exit(0);
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

}

