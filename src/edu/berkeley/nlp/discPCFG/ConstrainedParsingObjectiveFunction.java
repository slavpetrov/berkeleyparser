package edu.berkeley.nlp.discPCFG;

///**
// * 
// */
//package edu.berkeley.nlp.classify;
//
//import java.io.FileInputStream;
//import java.io.IOException;
//import java.io.ObjectInputStream;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.List;
//import java.util.zip.GZIPInputStream;
//
//import edu.berkeley.nlp.syntax.StateSet;
//import edu.berkeley.nlp.syntax.Tree;
//import edu.berkeley.nlp.math.DoubleArrays;
//import edu.berkeley.nlp.math.SloppyMath;
//import edu.berkeley.nlp.oldPCFGLA.ArrayParser;
//import edu.berkeley.nlp.oldPCFGLA.Binarization;
//import edu.berkeley.nlp.oldPCFGLA.ConstrainedCoarseToFineParser;
//import edu.berkeley.nlp.oldPCFGLA.ConstrainedCoarseToFineTwoChartsParser;
//import edu.berkeley.nlp.oldPCFGLA.Grammar;
//import edu.berkeley.nlp.oldPCFGLA.Lexicon;
//import edu.berkeley.nlp.oldPCFGLA.ParserData;
//import edu.berkeley.nlp.oldPCFGLA.StateSetTreeList;
//import edu.berkeley.nlp.util.Numberer;
//import fig.basic.Pair;
//
///**
// * @author petrov
// *
// */
//public class ConstrainedParsingObjectiveFunction extends ParsingObjectiveFunction implements ObjectiveFunction{
//
//	/**
//	 * @param grammar
//	 * @param lexicon
//	 * @param trainingTrees
//	 * @param sigma
//	 */
//	String baseFileName;
//	ConstrainedCoarseToFineParser parser;
//	List<Integer>[][][] constraintsCollection;
//
//	public ConstrainedParsingObjectiveFunction(Grammar grammar, int[][][] grammarIndex, Lexicon lexicon, 
//			int[][] lexiconIndex, StateSetTreeList trainingTrees, 
//			double sigma, String fileName, boolean regularize, boolean alwaysUpdate, int nGrW, int nLexW) {
//    this.sigma = sigma;
//    this.trainingTrees = trainingTrees;
//		this.dim = nGrW+nLexW;
//		this.alwaysUpdate = alwaysUpdate;
//		if (alwaysUpdate) System.out.println("Will update gold counts in every round.");
//
//		this.grammar = grammar;
//		this.lexicon = lexicon;
//  	this.startIndexGrammar = grammarIndex;
//  	this.startIndexLexicon = lexiconIndex;
//
//  	this.parser = new ConstrainedCoarseToFineTwoChartsParser(grammar, lexicon, 1.0,-1,false,false,false);
//  	this.baseFileName = fileName;
//  	
//  	constraintsCollection = loadData(fileName);
//  	this.REGULARIZE = regularize;
//  	
//  	this.expectedGoldCounts = DoubleArrays.constantArray(0.0, dimension());
//  	goldParser = new ArrayParser(grammar.copyGrammar(), lexicon.copyLexicon());
//  	this.updateGoldCounts = true;
//  	
//  	this.nGrammarWeights = nGrW;
//  	this.nLexiconWeights = nLexW;
//  	
////  	boolean hardCounts = false, noSmoothing = true, debugOutput = false;
////  	int treeNumber = 0;
////  	for (Tree<StateSet> stateSetTree : trainingTrees) {
////  		//parser.doGoldInsideOutsideScores(stateSetTree, sentence);
////  		goldParser.doInsideOutsideScores(stateSetTree, noSmoothing, debugOutput);
////  		StateSet node = stateSetTree.getLabel();
////    	double tree_score = node.getIScore(0);
////  		int tree_scale = node.getIScale();
////  		goldLLs[treeNumber++] = Math.log(tree_score) + (100*tree_scale);
////    	parser.incrementExpectedGoldCounts(expectedGoldCounts, stateSetTree, grammar, startIndex, hardCounts, tree_score, tree_scale);
////  	}  	
////  	
////  	for (int i=0; i<expectedGoldCounts.length; i++){
////  		if (expectedGoldCounts[i]==0) structuralZeros[i] = true;
////  		else structuralZeros[i] = false;
////  	}
////  	
//  
//	}
//
//  /**
//   * The most important part of the classifier learning process!  This method determines, for the given weight vector
//   * x, what the (negative) log conditional likelihood of the data is, as well as the derivatives of that likelihood
//   * wrt each weight parameter.
//   */
//  public Pair<Double, double[]> calculate() {
//  	//goldParser = new ArrayParser(grammar, lexicon);
//  	parser.updateFinalGrammarAndLexicon(grammar,lexicon);
//    System.out.print("In Constrained-Calculate");
//    double objective = -1.0;
//  	double[] expectedCounts = DoubleArrays.constantArray(0.0, dimension());
//  	deltas = DoubleArrays.constantArray(0.0, dimension());
//  	//HERE
//  	if (updateGoldCounts||alwaysUpdate) {
//  		this.expectedGoldCounts = DoubleArrays.constantArray(0.0, dimension());
//  		goldParser = new ArrayParser(grammar, lexicon);
//  		System.out.println("Will update gold counts in this round.");
//  	}
//
//  	int nInvalidTrees = 0, maxInvalidTrees = 50000, nValidTrees = 0;
//  	boolean tooManyInvalidTrees = false;
//  	int i = 0;
//  	
//  	// load first constraints file
//  	int treeNumber = 0;
//  	int cIndex = 0;
//    //String fileName = baseFileName+"-"+cIndex+".data";
//    cIndex++;
//    
//  	for (Tree<StateSet> stateSetTree : trainingTrees) {
//
//  		if(nInvalidTrees>maxInvalidTrees) {
//  			tooManyInvalidTrees = true;
//  			break;
//  		}
//  		
//  		List<StateSet> yield = stateSetTree.getYield();
//    	List<String> sentence = new ArrayList<String>(yield.size());
//    	for (StateSet el : yield){ sentence.add(el.getWord()); }
//    	
//    	//goldParser.doInsideOutsideScores(stateSetTree,true,false);   
//    	//grammar.tallyStateSetTree(stateSetTree, grammar);
//    	parser.setConstraints(constraintsCollection[treeNumber]);
//    	treeNumber++;
//    	Pair<double[][][][],double[][][][]> chart = parser.doInsideOutsideScores(sentence,stateSetTree);
//    	double allLL = parser.getLogInsideScore();
//
//    	
//    	if (Double.isInfinite(allLL)) {
//    		System.out.println("Couldn't compute a parse. allLL:"+allLL+"\n"+sentence);
//    		//allLL = -1000;
//    		nInvalidTrees++;
//    		continue;
//    	}
//    	
//    	parser.incrementExpectedCounts(expectedCounts, grammar, startIndexGrammar, lexicon, startIndexLexicon, sentence, false);
//
//    	
//  		//parser.doGoldInsideOutsideScores(stateSetTree, sentence);
//  		
//    	
//    	//goldParser = new ArrayParser(grammar, lexicon);
//    	boolean hardCounts = false, noSmoothing = true, debugOutput = false;
//    	goldParser.doInsideOutsideScores(stateSetTree,noSmoothing,debugOutput);
//    		
//    	    	
//    	StateSet node = stateSetTree.getLabel();
//    	double tree_score = node.getIScore(0);
//  		int tree_scale = node.getIScale();
//  		//HERE
//  		if (updateGoldCounts||alwaysUpdate) parser.incrementExpectedGoldCounts(expectedGoldCounts, stateSetTree, grammar, startIndexGrammar, lexicon, startIndexLexicon, hardCounts, tree_score, tree_scale);
//    	
//  		
//  		//System.out.println("\nSum Gold: "+DoubleArrays.sum(expectedGoldCounts)+" Sum Emp: "+DoubleArrays.sum(expectedCounts));
//      
//  		
//  		
//    	//double goldLL1 = parser.getLogInsideScore(); //Math.log(stateSetTree.getLabel().getIScore(0)) + (100*stateSetTree.getLabel().getIScale());//System.out.println(stateSetTree);
//    	double goldLL = Math.log(tree_score) + (100*tree_scale);//goldLLs[treeNumber-2];//
//    	
//    	//if (goldLL!=goldLL1)	System.out.println("Different LL "+goldLL+", "+goldLL1+" for tree "+stateSetTree);
//    		
//    		
//    	if (Double.isInfinite(goldLL)) {
//    		System.out.println("Couldn't score the gold parse. goldLL:"+goldLL+"\n"+sentence);
//    		//goldLL = -10000;
//    		nInvalidTrees++;
//    		continue;
//    	}
// 
//    		
//    	
//    	if (goldLL > allLL){
//    		System.out.println("Something is wrong! The gold LL is " + goldLL + " and the all LL is " + allLL+"\n"+sentence);
//    		nInvalidTrees++;
//    		//continue;
//    	}
//    	
//     	//System.out.println("0: "+stateSetTree.getChildren().get(0).getChildren().get(0).getLabel().getIScore(0)+
//    //			"1: "+stateSetTree.getChildren().get(0).getChildren().get(0).getLabel().getIScore(1));
// 	
//    	objective += (goldLL - allLL);
//    	nValidTrees++;
//    	
////    	System.out.println("gLL " + goldLL + " aLL "+allLL+" COND LL "+(goldLL-allLL));
//       	
//    	if (i++ % 100 == 0) System.out.print(".");
//    	if (i % 1000 == 0) System.out.print(i);
//    	/*if (treeNumber % 2000 == 0) {
//    		fileName = baseFileName+"-"+cIndex+".data";
//        cIndex++;
//        treeNumber=0;
//    		constraintsCollection = loadData(fileName);
//    	}*/
//    	
//    	//lexicon.trainTree(stateSetTree, -1, previousLexicon, secondHalf,false);
//    	
//    }
//    
//  	updateGoldCounts = false;
//  	//if (firstTime) firstTime = false;
//  	
//    System.out.print("done.\nThe objective was "+objective);
//
//    
//    double[] derivatives = computeDerivatives(expectedGoldCounts, expectedCounts);
//     
//    if (REGULARIZE){
//    	objective = regularize(objective, derivatives);
//    }		    
//	  System.out.print(" and is "+objective+" after regularization.");
//	  System.out.print(" Sum Gold: "+DoubleArrays.sum(expectedGoldCounts)+" Sum Emp: "+DoubleArrays.sum(expectedCounts)+"\n");
//    
//    objective *= -1.0; // flip sign since we are working with a minimizer rather than with a maximizer
//    for (int index = 0; index < lastX.length; index++) {
//      // 'x' and 'derivatives' have same layout
//      derivatives[index] *= -1.0;
//      double val = Math.log(expectedGoldCounts[index]/expectedCounts[index]);
//      val = (SloppyMath.isVeryDangerous(val)) ? 0 : val;
//      deltas[index] = val;
//    }
//    System.out.print(" Sum Derivatives: "+DoubleArrays.sum(derivatives));
//    
//////    
////    System.out.println("Exp GOLD: "+Arrays.toString(expectedGoldCounts));
////  	System.out.println("Expected: "+Arrays.toString(expectedCounts));
////  	
////    System.out.println("Weights:   "+Arrays.toString(lastX));
////  	
////    System.out.println("Derivatives: "+Arrays.toString(derivatives));
////    System.out.println(grammar);
//////  	
//    
//    if (tooManyInvalidTrees || nValidTrees < 2){
//    	return failedSearchResult();
//    }
//   
//    ParserData pData = new ParserData(lexicon, grammar, Numberer.getNumberers(), grammar.numSubStates, 1, 0, Binarization.RIGHT);
//
//    String outFileName = "tmp"+objective+".gr";
//    //System.out.println("Saving grammar to "+outFileName+".");
//    //if 
//    pData.Save(outFileName); //System.out.println("Saving successful.");
//    //else System.out.println("Saving failed!");
//
//   
//    return new Pair<Double, double[]>(objective, derivatives);
//  }
//  
//  public static List<Integer>[][][] loadData(String fileName) {
//  	List<Integer>[][][] data = null;
//    try {
//      FileInputStream fis = new FileInputStream(fileName); // Load from file
//      GZIPInputStream gzis = new GZIPInputStream(fis); // Compressed
//      ObjectInputStream in = new ObjectInputStream(gzis); // Load objects
//      data = (List<Integer>[][][])in.readObject(); // Read the mix of grammars
//      in.close(); // And close the stream.
//    } catch (IOException e) {
//      System.out.println("IOException\n"+e);
//      return null;
//    } catch (ClassNotFoundException e) {
//      System.out.println("Class not found!");
//      return null;
//    }
//    return data;
//  }
//	
// }
