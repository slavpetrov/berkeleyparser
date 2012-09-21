package edu.berkeley.nlp.PCFGLA;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees;
import edu.berkeley.nlp.util.Numberer;

public class TreeGenerator {

	static Grammar grammar;
	static SophisticatedLexicon lexicon;
	static Numberer tagNumberer;
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length <3) {
			System.out.println("usage: java TreeGenerator <input file for grammar> <maxLength> <nTrees>\n");
			System.exit(2);
		}
		String inFileName = args[0];
		int maxLength = Integer.parseInt(args[1]);
		int nTrees = Integer.parseInt(args[2]);

		System.out.println("Loading grammar from " + inFileName + ".");
		ParserData pData = ParserData.Load(inFileName);
		if (pData == null) {
			System.out.println("Failed to load grammar from file" + inFileName + ".");
			System.exit(1);
		}
		
		grammar = pData.getGrammar();
		lexicon = (SophisticatedLexicon)pData.getLexicon();
		Numberer.setNumberers(pData.getNumbs());
		tagNumberer = Numberer.getGlobalNumberer("tags");
		grammar.splitRules();
		
		int nGen = 0;
		while (nGen < nTrees){
			Tree<String> artTree = generateTree(0, 0);
			System.out.println(artTree.getYield().toString());
		  Tree<String> tree = TreeAnnotations.unAnnotateTree(artTree, false);
		  if (tree.getYield().size() > maxLength) continue;
		  System.out.println("Generated tree of length "+tree.getYield().size()+".\n"+Trees.PennTreeRenderer.render(tree)+"\n");
		  nGen++;
		}

	}

	private static Tree<String> generateTree(int pState, int pSubState) {
   	String root = (String)tagNumberer.object(pState);
   	//System.out.println("Current parent: "+root+"-"+pSubState);
		BinaryRule[] bRules = grammar.splitRulesWithP(pState);
    //System.out.println("Number of binary rules: " +bRules.length);
    double randval = GrammarTrainer.RANDOM.nextDouble();
    double sum=0;
    ArrayList<Tree<String>> children = new ArrayList<Tree<String>>();
    for (int i = 0; i < bRules.length; i++) {
    	double[][][] scores = bRules[i].scores;
    	for (int lC=0; lC<scores.length; lC++){
    		for (int rC=0; rC<scores[lC].length; rC++){
    			if (scores[lC][rC]!=null)
    				sum += scores[lC][rC][pSubState];
    			if (sum>randval){
    	      children.add( generateTree(bRules[i].leftChildState, lC) );
    	      children.add( generateTree(bRules[i].rightChildState, rC) );
    	      return new Tree<String>( root, children );
    			}
    		}
    	}
   	}
    List<UnaryRule> uRulesList = grammar.getUnaryRulesByParent(pState); //)  getClosedViterbiUnaryRulesByParent(
    //for (int i = 0; i < uRules.length; i++) {
    	//double[][] scores = uRules[i].scores;
    for (UnaryRule uRule : uRulesList){
    	double[][] scores = uRule.scores;
  		for (int uC=0; uC<scores.length; uC++){
  			if (uRule.parentState==uRule.childState)
  				continue;
  			if (scores[uC]!=null)
  				sum += scores[uC][pSubState];
  			if (sum>randval){
  	      children.add( generateTree(uRule.childState, uC) );
  	      return new Tree<String>( root, children );
  			}
  		}
   	}    
    
    if (sum==0) {
    	//System.out.println("There are no rules with "+root+" as parent.");
    	String word = sampleWord(pState, pSubState);
    	List<Tree<String>> child = Collections.singletonList( new Tree<String>(word) );
      return new Tree<String>( root, child );
    }
    else
      throw new Error("rule probability sum "+sum+" is more than 1!");
 	}

	// P(T|W) = P(W|T)*P(W)/P(T)
	private static String sampleWord(int tag, int substate) {
		String w = (String)tagNumberer.object(tag);
		double randval = GrammarTrainer.RANDOM.nextDouble();
    double sum=0;
    HashMap<String,double[]> wordToTagCounter = lexicon.wordToTagCounters[tag];
    for (String word : wordToTagCounter.keySet()){
	    double c_TW = 0;
			if (lexicon.wordToTagCounters[tag]!=null &&
					lexicon.wordToTagCounters[tag].get(word)!=null) {
				c_TW = wordToTagCounter.get(word)[substate];
			}
			
	    double c_W = lexicon.wordCounter.getCount(word);
	    double c_T = lexicon.tagCounter[tag][substate];
			double total = lexicon.totalTokens;
			double pb_T_W = c_TW / c_W;
	
			double p_T = (c_T / total);
			double p_W = (c_W / total);
			double pb_W_T = pb_T_W * p_W / p_T;
			sum += pb_W_T;
			if (sum>randval) 
				return word;
    }

    return w;
	}

}
