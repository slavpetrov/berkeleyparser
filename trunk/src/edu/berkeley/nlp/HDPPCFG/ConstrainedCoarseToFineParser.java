/**
 * 
 */
package edu.berkeley.nlp.HDPPCFG;

import java.util.List;

import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;

/**
 * @author petrov
 *
 */
public class ConstrainedCoarseToFineParser extends CoarseToFineMaxRuleParser{

	/**
	 * @param gr
	 * @param lex
	 * @param unaryPenalty
	 * @param endL
	 * @param viterbi
	 * @param sub
	 * @param score
	 */
	List<Integer>[][] possibleStates; // for a particular sentence
	       
	public ConstrainedCoarseToFineParser(Grammar gr, Lexicon lex, double unaryPenalty, int endL, boolean viterbi, boolean sub, boolean score) {
		super(gr, lex, unaryPenalty, endL, viterbi, sub, score);
		//startLevel = 1; // instead of doing the pre-preparse and the x-bar parse we will use the supplied (precomputed) constrains
		this.grammarCascade[0] = null;
		this.grammarCascade[1] = null;
		this.lexiconCascade[0] = null;
		this.lexiconCascade[1] = null;
		
		
	}
	
	public void setConstraints(List<Integer>[][] pStates){
		this.possibleStates = pStates;
	}
	
	
  public void doPreParses(List<String> sentence, Tree<StateSet> tree,boolean noSmoothing){
  	boolean keepGoldAlive = (tree!=null); // we are given the gold tree -> make sure we don't prune it away
  	clearArrays();
  	length = (short)sentence.size();
  	double score = 0;
  	Grammar curGrammar = null;
  	Lexicon curLexicon = null;
  	// -> good one: double[] pruningThreshold = {-6,-12,-12,-10,-10,-12,-14};//Double.NEGATIVE_INFINITY;//Math.log(1.0e-10);
  	//double[] pruningThreshold = {-6,-14,-14,-14,-14,-14,-14};//Double.NEGATIVE_INFINITY;//Math.log(1.0e-10);
  	double[] pruningThreshold = {-6,-10,-10,-10,-10,-10,-10};//Double.NEGATIVE_INFINITY;//Math.log(1.0e-10);
  	//double[] pruningThreshold = {-6,-12,-12,-12,-12,-12,-12};
  	//int startLevel = -1;
  	
  	constrainChart();
  	
  	for (int level=1; level<=endLevel; level++){
  		curGrammar = grammarCascade[level-startLevel];
  		curLexicon = lexiconCascade[level-startLevel];

  		createArrays(false,curGrammar.numStates,curGrammar.numSubStates,level,Double.NEGATIVE_INFINITY,false);

	    initializeChart(sentence,curLexicon,level<1,noSmoothing);
	    if (level<1){
	    	doConstrainedViterbiInsideScores(curGrammar,level==startLevel); 
	    	score = viScore[0][length][0];
	    } else {
	    	doConstrainedViterbiSubstateInsideScores(curGrammar); 
	    	score = iScore[0][length][0][0];
	    }
	    if (score==Double.NEGATIVE_INFINITY) continue;
      //System.out.println("\nFound a parse for sentence with length "+length+". The LL is "+score+".");
      if (level<1){
      	voScore[0][length][0] = 0.0;
      	doConstrainedViterbiOutsideScores(curGrammar,level==startLevel);
  		} else {
      	oScore[0][length][0][0] = 0.0;
      	doConstrainedViterbiSubstateOutsideScores(curGrammar);
  		}
	      
      pruneChart(pruningThreshold[level+1], curGrammar.numSubStates, level);
      if (keepGoldAlive) ensureGoldTreeSurvives(tree, level);
  	}

  }
  
  public void constrainChart(){
  	viScore = new double[length][length + 1][];
  	viScore[0][length] = new double[1];
		iScore = new double[length][length + 1][][];
		oScore = new double[length][length + 1][][];
		allowedSubStates = new boolean[length][length+1][][];
		allowedStates = new boolean[length][length+1][];

  	for (int start = 0; start < length; start++) {
			for (int end = start + 1; end <= length; end++) {
				iScore[start][end] = new double[numStates][];
				oScore[start][end] = new double[numStates][];
				allowedStates[start][end] = new boolean[numStates];
				allowedSubStates[start][end] = new boolean[numStates][];
				
				for (Integer pState : possibleStates[start][end]){
					allowedStates[start][end][pState] = true;
				}
			}
  	}
			
  }
  

}
