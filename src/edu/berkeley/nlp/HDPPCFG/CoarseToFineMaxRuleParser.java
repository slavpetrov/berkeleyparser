package edu.berkeley.nlp.HDPPCFG;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.Numberer;
import fig.basic.Pair;

/**
 * 
 * @author Slav Petrov
 * 
 * SHOULD BE CLEANED UP!!!
 * AND PROBABLY ALSO RENAMED SINCE IT CAN COMPUTE VITERBI PARSES AS WELL
 * 
 * An extension of ConstrainedArrayParser that computes the scores P(w_{i:j}|A), whose
 * computation involves a sum, rather than the Viterbi scores, which involve a max.
 * This is used by the Labelled Recall parser (maximizes the expected number of correct
 * symbols) and the Max-Rule parser (maximizes the expected number of correct rules, ie
 * all 3 symbols correct).  
 *
 */
	
public class CoarseToFineMaxRuleParser extends ConstrainedArrayParser{
	boolean[][][][] allowedSubStates;
	boolean[][][] allowedStates;
	boolean[][] vAllowedStates;
	double[][] spanMass;
	//					allowedStates[start][end][state][0] -> is this category allowed
	//					allowedStates[start][end][state][i+1] -> is subcategory i allowed	
	Grammar[] grammarCascade;
	Lexicon[] lexiconCascade;
	int[][][] lChildMap;
	int[][][] rChildMap;
	int startLevel;  
	int endLevel;
	protected double[][][][] iScore;
	/** outside scores; start idx, end idx, state -> logProb	 */
	protected double[][][][] oScore;
	protected short[] numSubStatesArray;
	double[] maxThresholds;
	double logLikelihood;
	Tree<String> bestTree;
	boolean isBaseline;

	
  // inside scores
  protected double[][][] viScore; // start idx, end idx, state -> logProb
  protected double[][][] voScore; // start idx, end idx, state -> logProb
  
  // maxcScore does not have substate information since these are marginalized out 
  protected double[][][] maxcScore;  // start, end, state --> logProb
  protected double[][][] maxsScore;  // start, end, state --> logProb
  protected int[][][] maxcSplit;  // start, end, state -> split position
  protected int[][][] maxcChild;  // start, end, state -> unary child (if any)
  protected int[][][] maxcLeftChild;  // start, end, state -> left child
  protected int[][][] maxcRightChild;  // start, end, state -> right child
  protected double unaryPenalty;
	int nLevels;
	boolean[] grammarTags;
	boolean viterbiParse;
	boolean outputSub;
	boolean outputScore;
	Numberer wordNumberer = Numberer.getGlobalNumberer("words");
	
  public CoarseToFineMaxRuleParser(Grammar gr, Lexicon lex, double unaryPenalty, int endL, 
  		boolean viterbi, boolean sub, boolean score) {
		this.numSubStatesArray = gr.numSubStates;
    //System.out.println("The unary penalty for parsing is "+unaryPenalty+".");
    this.unaryPenalty = unaryPenalty;
    
    this.viterbiParse = viterbi;
    //if (viterbiParse) System.out.println("Will be computing the viterbi parse!");
    this.outputScore = score;
    this.outputSub = sub;
    
    
		totalUsedUnaries=0;
		nTimesRestoredUnaries=0;
		nRules=0;
		nRulesInf=0;
    //this.grammar = gr;
    //this.lexicon = lex;
    //gr.splitTrees[94]=gr.splitTrees[93].shallowClone();
    //gr.splitTrees[95]=gr.splitTrees[93].shallowClone();
    this.tagNumberer = Numberer.getGlobalNumberer("tags");
    this.numStates = gr.numStates;
    this.maxNSubStates = maxSubStates(gr);
    this.idxC = new int[maxNSubStates];
    this.scoresToAdd = new double[maxNSubStates];
    this.grammarTags = new boolean[numStates];
    for (int i=0; i<numStates; i++){
    	grammarTags[i] = gr.isGrammarTag(i);
    }
    grammarTags[0] = true;

    // the cascades will contain all the projections (in logarithm mode) and at the end the final grammar,
    // once in logarithm-mode and once not
    nLevels = (int)Math.ceil(Math.log((int)ArrayUtil.max(numSubStatesArray))/Math.log(2));
    this.grammarCascade = new Grammar[nLevels+3];
    this.lexiconCascade = new Lexicon[nLevels+3];
    this.maxThresholds = new double[nLevels+3];
    this.lChildMap = new int[nLevels][][];
    this.rChildMap = new int[nLevels][][];
    this.startLevel = -1;
    this.endLevel = endL;
    if (endLevel == -1) 
    	this.endLevel = nLevels;
    this.isBaseline = (endLevel==0);
    
		for (int level=startLevel; level<=endLevel+1; level++){
			Grammar tmpGrammar = null;
			Lexicon tmpLexicon = null;
			if (level==endLevel){
      	tmpGrammar = gr.copyGrammar();
      	tmpLexicon = lex.copyLexicon();
			}
			else if (level>endLevel){
      	tmpGrammar = gr;
      	tmpLexicon = lex;
			}
			else /*if (level>0&& level<endLevel)*/{
				int[][] fromMapping = gr.computeMapping(1);
		    int[][] toSubstateMapping = gr.computeSubstateMapping(level);
		    int[][] toMapping = gr.computeToMapping(level,toSubstateMapping);
		   	int[][] curLChildMap = new int[toSubstateMapping.length][];
	    	int[][] curRChildMap = new int[toSubstateMapping.length][];
	    	double[] condProbs = gr.computeConditionalProbabilities(fromMapping,toMapping);
	    	
	    	if (level==-1) tmpGrammar = gr.projectTo0LevelGrammar(condProbs,fromMapping,toMapping);
      	else tmpGrammar = gr.projectGrammar(condProbs,fromMapping,toSubstateMapping);
      	tmpLexicon = lex.projectLexicon(condProbs,fromMapping,toSubstateMapping);

      	if (level>0) {
	    		lChildMap[level+startLevel] = curLChildMap;
	    		rChildMap[level+startLevel] = curRChildMap;
		    	gr.computeReverseSubstateMapping(level,curLChildMap,curRChildMap);
	    	}
	    }
			  
			tmpGrammar.splitRules();
    	double filter = -1.0e-4;
    	if (level>=0 && level<endLevel){
    		tmpGrammar.removeUnlikelyRules(filter,0.8);
    		tmpLexicon.removeUnlikelyTags(filter);
    	} else if (level>=endLevel){
    		tmpGrammar.removeUnlikelyRules(-1.0e-10,1.0);
    		tmpLexicon.removeUnlikelyTags(-1.0e-10);
    	}
    	//System.out.println(baseGrammar.toString());
    	
  		if (level<=endLevel || viterbiParse){
  			tmpGrammar.logarithmMode();
  			tmpLexicon.logarithmMode();
  		}
    	grammarCascade[level-startLevel]=tmpGrammar;
    	lexiconCascade[level-startLevel]=tmpLexicon;
    }
		this.touchedRules=0;

		//this.grammar.removeUnlikelyRules(1.0e-10,1.0);
		//this.lexicon.removeUnlikelyTags(1.0e-10);
    /*nLevels++;
		gr.logarithmMode();
    grammarCascade[nLevels] = gr;
    lex.logarithmMode();
    lexiconCascade[nLevels] = lex; 
*/
    
  }

/*  void doConstrainedInsideOutsideProduct(Grammar grammar, double threshold) {
  	numSubStatesArray = grammar.numSubStates;
    for (int diff = 1; diff <= length; diff++) {
      for (int start = 0; start < (length - diff + 1); start++) {
        int end = start + diff;
      	for (int pState=0; pState<numSubStatesArray.length; pState++){
        	if (!allowedStates[start][end][pState]) continue;
        	for (int substate=0; substate<numSubStatesArray[pState]; substate++){
        		double iS = iScore[start][end][pState][substate];
        		if (iS<=0) continue;
        		double oS = oScore[start][end][pState][substate];
        		if (oS<=0) continue;
        		double product = iS/oS;
        		if (product>threshold)
        			IOScore[start][end][pState][substate]=product;
        	}
      	}
      }
    }
  }
  */
  
  void doConstrainedInsideScores(Grammar grammar) {
  	numSubStatesArray = grammar.numSubStates;
    //double[] oldIScores = new double[maxNSubStates];
  	//int smallestScale = 10, largestScale = -10;
      for (int diff = 1; diff <= length; diff++) {
    	//smallestScale = 10; largestScale = -10;
      //System.out.print(diff + " ");
      for (int start = 0; start < (length - diff + 1); start++) {
        int end = start + diff;
      	for (int pState=0; pState<numSubStatesArray.length; pState++){
        	if (diff==1) continue; // there are no binary rules that span over 1 symbol only
        	if (!allowedStates[start][end][pState]) continue;
        	BinaryRule[] parentRules = grammar.splitRulesWithP(pState);
          int nParentStates = numSubStatesArray[pState];
          Arrays.fill(scoresToAdd,0.0);
          boolean somethingChanged = false;
          for (int i = 0; i < parentRules.length; i++) {
            BinaryRule r = parentRules[i];
            int lState = r.leftChildState;
            int rState = r.rightChildState;

            int narrowR = narrowRExtent[start][lState];
            boolean iPossibleL = (narrowR < end); // can this left constituent leave space for a right constituent?
            if (!iPossibleL) { continue; }
            
            int narrowL = narrowLExtent[end][rState];
            boolean iPossibleR = (narrowL >= narrowR); // can this right constituent fit next to the left constituent?
            if (!iPossibleR) { continue; }
            
            int min1 = narrowR;
            int min2 = wideLExtent[end][rState];
            int min = (min1 > min2 ? min1 : min2); // can this right constituent stretch far enough to reach the left constituent?
            if (min > narrowL) { continue; }
            
            int max1 = wideRExtent[start][lState];
            int max2 = narrowL;
            int max = (max1 < max2 ? max1 : max2); // can this left constituent stretch far enough to reach the right constituent?
            if (min > max) { continue; }
            // TODO switch order of loops for efficiency
            double[][][] scores = r.getScores2();
            int nLeftChildStates = numSubStatesArray[lState];
            int nRightChildStates = numSubStatesArray[rState];
	        	for (int split = min; split <= max; split++) {
	      			if (!allowedStates[start][split][lState]) continue;
	      			if (!allowedStates[split][end][rState]) continue;
	            for (int lp = 0; lp < nLeftChildStates; lp++) {
	        			//if (iScore[start][split][lState] == null) continue;
	        			//if (!allowedSubStates[start][split][lState][lp]) continue;
	        			double lS = iScore[start][split][lState][lp];
	        			if (lS == 0) continue;
	            	
	        			for (int rp = 0; rp < nRightChildStates; rp++) {
	            		if (scores[lp][rp]==null) continue;
		          			double rS = iScore[split][end][rState][rp];
		          			if (rS == 0) continue;
	          			for (int np = 0; np < nParentStates; np++) {
	          				if (!allowedSubStates[start][end][pState][np]) continue;
            				double pS = scores[lp][rp][np];
            				if (pS==0) continue;
		          			//if (iScore[split][end][rState] == null) continue;
		          			//if (!allowedSubStates[split][end][rState][rp]) continue;
		
            			
            				scoresToAdd[np] += pS * lS * rS; 
            				somethingChanged = true;
            			}
            		}
            	}
	            //if (!somethingChanged) continue;
		          //boolean firstTime = false;
/*		        	int parentScale = iScale[start][end][pState];
		          int currentScale = iScale[start][split][lState]+iScale[split][end][rState];
		          if (parentScale==currentScale) {
		          	// already had a way to generate this state and the scales are the same
		          	// -> nothing to do
		          } 
		          else {
		          	if (parentScale==Integer.MIN_VALUE){ // first time we can build this state
		          		firstTime = true;
		          		parentScale = scaleArray(scoresToAdd,currentScale);
		          		iScale[start][end][pState] = parentScale;
		          		//smallestScale = Math.min(smallestScale,parentScale);
		          		//largestScale = Math.max(largestScale,parentScale);
		          	}
		          	else { // scale the smaller one to the base of the bigger one
		          		int newScale = Math.max(currentScale,parentScale);
		          		scaleArrayToScale(scoresToAdd,currentScale,newScale);
		          		scaleArrayToScale(iScore[start][end][pState],parentScale,newScale);
		          		iScale[start][end][pState] = newScale;
		          		//smallestScale = Math.min(smallestScale,newScale);
		          		//largestScale = Math.max(largestScale,newScale);
		          	}
		          }*/
        		}
          }
          if (!somethingChanged) continue;
          for (int np = 0; np < nParentStates; np++) {
            //if ()// iScore[start][end][pState][np] = scoresToAdd[np];
            if (scoresToAdd[np] > 0) {
            	iScore[start][end][pState][np] += scoresToAdd[np];
          	}
          }
          //iScale[start][end][pState] = currentScale;
          //iScale[start][end][pState] = scaleArray(iScore[start][end][pState],iScale[start][end][pState]);
      		if (true/*firstTime*/) {
      			if (start > narrowLExtent[end][pState]) {
      				narrowLExtent[end][pState] = start;
      				wideLExtent[end][pState] = start;
      			} else {
      				if (start < wideLExtent[end][pState]) {
      					wideLExtent[end][pState] = start;
      				}
      			}
      			if (end < narrowRExtent[start][pState]) {
      				narrowRExtent[start][pState] = end;
      				wideRExtent[start][pState] = end;
      			} else {
      				if (end > wideRExtent[start][pState]) {
      					wideRExtent[start][pState] = end;
      				}
      			}
      		}
        }
      	double[][] scoresAfterUnaries = new double[numStates][];
        boolean somethingChanged = false;
        for (int pState=0; pState<numSubStatesArray.length; pState++){
        	if (!allowedStates[start][end][pState]) continue;
          // Should be: Closure under sum-product:
          UnaryRule[] unaries = grammar.getClosedSumUnaryRulesByParent(pState);
          //UnaryRule[] unaries = grammar.getUnaryRulesByParent(pState).toArray(new UnaryRule[0]);
          //Arrays.fill(scoresToAdd,0.0);
          int nParentStates = numSubStatesArray[pState];//scores[0].length;
          boolean firstTime = true;
          
          for (int r = 0; r < unaries.length; r++) {
          	UnaryRule ur = unaries[r];
            int cState = ur.childState;
            if ((pState == cState)) continue;// && (np == cp))continue;
            if (iScore[start][end][cState]==null) continue;
            //if (!allowedStates[start][end][cState]) continue;
            //new loop over all substates
          	//System.out.println("Rule "+r+" out of "+unaries.length+" "+ur);
            double[][] scores = ur.getScores2();
            int nChildStates = numSubStatesArray[cState];//scores[0].length;
            for (int cp = 0; cp < nChildStates; cp++) {
              if (scores[cp]==null) continue;
              for (int np = 0; np < nParentStates; np++) {
                if (!allowedSubStates[start][end][pState][np]) continue;
	              //if (!allowedSubStates[start][end][cState][cp]) continue;
                double pS = scores[cp][np];
                if (pS==0) continue;

                double iS = iScore[start][end][cState][cp];
	              if (iS == 0) continue;
                
	              if (firstTime) {
	              	firstTime = false;
	              	scoresAfterUnaries[pState] = new double[nParentStates];
	              }
	              //scoresToAdd[np] += iS * pS;
	              scoresAfterUnaries[pState][np] += iS * pS; // since we are working with our (pseudo) closure 
                somethingChanged = true;
              }
            }
          }
      	}
      	if (!somethingChanged) continue;
          /*boolean firstTime = false;
          int currentScale = iScale[start][end][cState];
          int parentScale = iScale[start][end][pState];
          if (parentScale==currentScale) {
          	// already had a way to generate this state and the scales are the same
          	// -> nothing to do
          } 
          else {
          	if (parentScale==Integer.MIN_VALUE){ // first time we can build this state
          		firstTime = true;
          		parentScale = scaleArray(scoresToAdd,currentScale);
          		iScale[start][end][pState] = parentScale;
          		//smallestScale = Math.min(smallestScale,parentScale);
          		//largestScale = Math.max(largestScale,parentScale);
          	}
          	else { // scale the smaller one to the base of the bigger one
          		int newScale = Math.max(currentScale,parentScale);
          		scaleArrayToScale(scoresToAdd,currentScale,newScale);
          		scaleArrayToScale(iScore[start][end][pState],parentScale,newScale);
          		iScale[start][end][pState] = newScale;
          		//smallestScale = Math.min(smallestScale,newScale);
          		//largestScale = Math.max(largestScale,newScale);

          	}
          }*/
      	for (int pState=0; pState<numSubStatesArray.length; pState++){
      		int nParentStates = numSubStatesArray[pState];//scores[0].length;
      		double[] thisCell = scoresAfterUnaries[pState];
        	if (thisCell == null) continue;
        	for (int np = 0; np < nParentStates; np++) {
            //if (scoresToAdd[np] > 0) {	iScore[start][end][pState][np] += scoresToAdd[np];}
          	if (thisCell[np] > 0) {	iScore[start][end][pState][np] += thisCell[np];}          	
          }
          //iScale[start][end][pState] = currentScale;
          //iScale[start][end][pState] = scaleArray(iScore[start][end][pState],iScale[start][end][pState]);
          if (true){
            if (start > narrowLExtent[end][pState]) {
              narrowLExtent[end][pState] = start;
              wideLExtent[end][pState] = start;
            } else {
              if (start < wideLExtent[end][pState]) {
                wideLExtent[end][pState] = start;
              }
            }
            if (end < narrowRExtent[start][pState]) {
              narrowRExtent[start][pState] = end;
              wideRExtent[start][pState] = end;
            } else {
              if (end > wideRExtent[start][pState]) {
                wideRExtent[start][pState] = end;
              }
            }
          }
        }
      }
    }
  }

  /** Fills in the oScore array of each category over each span
   *  of length 2 or more. This version computes the posterior
   *  outside scores, not the Viterbi outside scores.
   */
  
  void doConstrainedOutsideScores(Grammar grammar) {
  	numSubStatesArray = grammar.numSubStates;
  	for (int diff = length; diff >= 1; diff--) {
  		for (int start = 0; start + diff <= length; start++) {
  			int end = start + diff;
  			// do unaries
  			double[][] scoresAfterUnaries = new double[numStates][];
  			boolean somethingChanged = false;
  			for (int pState=0; pState<numSubStatesArray.length; pState++){
  				if (oScore[start][end][pState] == null) { continue; }
  				//if (!allowedStates[start][end][pState]) continue;
  				// Should be: Closure under sum-product:
  				UnaryRule[] rules = grammar.getClosedSumUnaryRulesByParent(pState);
  				//UnaryRule[] rules = grammar.getClosedViterbiUnaryRulesByParent(pState);
  				// For now:
  				//UnaryRule[] rules = grammar.getUnaryRulesByParent(pState).toArray(new UnaryRule[0]);
  				for (int r = 0; r < rules.length; r++) {
  					UnaryRule ur = rules[r];
  					int cState = ur.childState;
  					if ((pState == cState)) continue;// && (np == cp))continue;
  					//if (!allowedStates[start][end][cState]) continue;
  					if (oScore[start][end][cState] == null) { continue; }

  					double[][] scores = ur.getScores2();
  					int nParentStates = numSubStatesArray[pState];
  					int nChildStates = scores.length;
  					for (int cp = 0; cp < nChildStates; cp++) {
  						if (scores[cp]==null) continue;
  						if (!allowedSubStates[start][end][cState][cp]) continue;
  						for (int np = 0; np < nParentStates; np++) {
  							//if (!allowedSubStates[start][end][pState][np]) continue;
  							double pS = scores[cp][np];
  							if (pS == 0) continue;

  							double oS = oScore[start][end][pState][np];
  							if (oS == 0) continue;

  							double tot = oS * pS;
  							if (scoresAfterUnaries[cState]==null) scoresAfterUnaries[cState] = new double[nChildStates];
  							scoresAfterUnaries[cState][cp] += tot;
  							//scoresToAdd[cp] += tot;
  							somethingChanged = true;
  						}
  					}
  				}
  			}
  			// check first whether there was a change at all
            //boolean firstTime = false;
            /*int currentScale = oScale[start][end][pState];
            int childScale = oScale[start][end][cState];
	          if (childScale==currentScale) {
	          	// already had a way to generate this state and the scales are the same
	          	// -> nothing to do
	          } else {
	          	if (childScale==Integer.MIN_VALUE){ // first time we can build this state
	          		firstTime = true;
	          		childScale = scaleArray(scoresToAdd,currentScale);
	          		oScale[start][end][cState] = childScale;
	          	}
	          	else { // scale the smaller one to the base of the bigger one
	          		int newScale = Math.max(currentScale,childScale);
	          		scaleArrayToScale(scoresToAdd,currentScale,newScale);
	          		scaleArrayToScale(oScore[start][end][cState],childScale,newScale);
	          		oScale[start][end][cState] = newScale;
	          	}
	          }*/
  			if (somethingChanged){
		      for (int cState=0; cState<numSubStatesArray.length; cState++){
		      	int nChildStates = numSubStatesArray[cState];
		        for (int cp=0; cp<nChildStates; cp++){
		          //if (true /*firstTime*/) oScore[start][end][cState][cp] = scoresToAdd[cp];
		          //else 
		          //if (scoresToAdd[cp] > 0) oScore[start][end][cState][cp] += scoresToAdd[cp];
		        	double[] thisCell = scoresAfterUnaries[cState];
		        	if (thisCell==null) continue;
		        	oScore[start][end][cState][cp] += scoresAfterUnaries[cState][cp];
		        }
		      }
        }
      
      // do binaries
      	
      	for (int pState=0; pState<numSubStatesArray.length; pState++){
        	if (oScore[start][end][pState] == null) { continue; }
        	final int nParentChildStates = numSubStatesArray[pState];
          //if (!allowedStates[start][end][pState]) continue;
          BinaryRule[] rules = grammar.splitRulesWithP(pState);
          
          //BinaryRule[] rules = grammar.splitRulesWithLC(lState);
          for (int r = 0; r < rules.length; r++) {
            BinaryRule br = rules[r];
            int lState = br.leftChildState;
            int min1 = narrowRExtent[start][lState];
            if (end < min1) { continue; }
            
            int rState = br.rightChildState;
            int max1 = narrowLExtent[end][rState];
            if (max1 < min1) { continue; }
            
            int min = min1;
            int max = max1;
            if (max - min > 2) {
              int min2 = wideLExtent[end][rState];
              min = (min1 > min2 ? min1 : min2);
              if (max1 < min) { continue; }
              int max2 = wideRExtent[start][lState];
              max = (max1 < max2 ? max1 : max2);
              if (max < min) { continue; }
            }
            
            double[][][] scores = br.getScores2();
            int nLeftChildStates = numSubStatesArray[lState];
            int nRightChildStates = numSubStatesArray[rState];
            for (int split = min; split <= max; split++) {
              if (oScore[start][split][lState] == null) continue;
              if (oScore[split][end][rState] == null) continue;
              //if (!allowedStates[start][split][lState]) continue;
              //if (!allowedStates[split][end][rState]) continue;
              Arrays.fill(scoresToAdd,0.0);
              somethingChanged = false;
              double[] rightScores = new double[nRightChildStates];
              for (int lp=0; lp<nLeftChildStates; lp++){
              	double lS = iScore[start][split][lState][lp];
                if (lS == 0) { continue; }
                //if (!allowedSubStates[start][split][lState][lp]) continue;
              	for (int rp=0; rp<nRightChildStates; rp++){
                  if (scores[lp][rp]==null) continue;
                  double rS = iScore[split][end][rState][rp];
                  if (rS == 0) { continue; }
                  //if (!allowedSubStates[split][end][rState][rp]) continue;

                  for (int np=0; np<nParentChildStates; np++){
                    double pS = scores[lp][rp][np];
                    if (pS == 0) continue;

                    double oS = oScore[start][end][pState][np];
                    if (oS == 0) continue;
                    //if (!allowedSubStates[start][end][pState][np]) continue;

                    scoresToAdd[lp] += pS * rS * oS;
                    rightScores[rp] += pS * lS * oS;
                    somethingChanged = true;
                  }
                }
              }
              if (!somethingChanged) continue;
              /*boolean firstTime = false;
              int leftScale = oScale[start][split][lState];
              int rightScale = oScale[split][end][rState];
              int parentScale = oScale[start][end][pState];
              int currentScale = parentScale+iScale[split][end][rState];
  	          if (leftScale==currentScale) {
  	          	// already had a way to generate this state and the scales are the same
  	          	// -> nothing to do
  	          } else {
  	          	if (leftScale==Integer.MIN_VALUE){ // first time we can build this state
  	          		firstTime = true;
  	          		leftScale = scaleArray(scoresToAdd,currentScale);
  	          		oScale[start][split][lState] = leftScale;
  	          	}
  	          	else { // scale the smaller one to the base of the bigger one
  	          		int newScale = Math.max(currentScale,leftScale);
  	          		scaleArrayToScale(scoresToAdd,currentScale,newScale);
  	          		scaleArrayToScale(oScore[start][split][lState],leftScale,newScale);
  	          		oScale[start][split][lState] = newScale;
  	          	}
  	          }*/
              for (int cp=0; cp<nLeftChildStates; cp++){
                //if (true /*firstTime*/) 
                	//oScore[start][split][lState][cp] = scoresToAdd[cp];
                if (scoresToAdd[cp] > 0) oScore[start][split][lState][cp] += scoresToAdd[cp];
              }
              //oScale[start][split][lState] = currentScale;
              //oScale[start][split][lState] = scaleArray(oScore[start][split][lState],oScale[start][split][lState]);

              //currentScale = parentScale+iScale[start][split][lState];
              /*firstTime = false;
  	          if (rightScale==currentScale) {
  	          	// already had a way to generate this state and the scales are the same
  	          	// -> nothing to do
  	          } else {
  	          	if (rightScale==Integer.MIN_VALUE){ // first time we can build this state
  	          		firstTime = true;
  	          		rightScale = scaleArray(rightScores,currentScale);
  	          		oScale[split][end][rState] = rightScale;
  	          	}
  	          	else { // scale the smaller one to the base of the bigger one
  	          		int newScale = Math.max(currentScale,rightScale);
  	          		scaleArrayToScale(rightScores,currentScale,newScale);
  	          		scaleArrayToScale(oScore[split][end][rState],rightScale,newScale);
  	          		oScale[split][end][rState] = newScale;
  	          	}
  	          }*/
              for (int cp=0; cp<nRightChildStates; cp++){
                //if (true/*firstTime*/) oScore[split][end][rState][cp] = rightScores[cp];
                //else 
                	if (rightScores[cp] > 0) oScore[split][end][rState][cp] += rightScores[cp];
              }
              //oScale[split][end][rState] = currentScale;
              //oScale[split][end][rState] = scaleArray(oScore[split][end][rState],oScale[split][end][rState]);
            }
          }
        }
      }
    }
  }
  

  public int scaleArray(double[] scores, int previousScale){
	  return previousScale;
	  /*int logScale = 0;
	  double scale = 1.0;
	  double max = ArrayMath.max(scores);
	  if (max==Double.POSITIVE_INFINITY) {
	  	System.out.println("Infinity");
	  	return 0;
	  }
	  //if (max==0) 	System.out.println("All oScores are 0!");
	  while (max > GrammarTrainer.SCALE) {
	    max /= GrammarTrainer.SCALE;
	    scale *= GrammarTrainer.SCALE;
	    logScale += 1;
	  }
	  while (max > 0.0 && max < 1.0 / GrammarTrainer.SCALE) {
	    max *= GrammarTrainer.SCALE;
	    scale /= GrammarTrainer.SCALE;
	    logScale -= 1;
	  }
	  if (logScale != 0) {
	    for (int i = 0; i < scores.length; i++) {
	      scores[i] /= scale;
	    }
	  }
	  if ((max!=0) && ArrayMath.max(scores)==0){System.out.println("Undeflow when scaling oScores!");}
	  return previousScale + logScale;*/
  }

  public void scaleArrayToScale(double[] scores, int previousScale, int newScale){
	  return;
	  /*int scaleDiff = previousScale-newScale;
	  if (scaleDiff == 0) return; // nothing to do
	  double scale = Math.pow(GrammarTrainer.SCALE,scaleDiff);
	  if (Math.abs(scale)>=8){
	  	// under-/overflow...
	  	Arrays.fill(scores,0.0);
	  	return;
	  }

	  for (int i = 0; i < scores.length; i++) {
      scores[i] *= scale;
    }*/
  }
  
	void initializeChart(List<String> sentence, Lexicon lexicon,boolean noSubstates,boolean noSmoothing) {
		int start = 0;
		int end = start+1;
		for (String word : sentence) {
			end = start+1;
				for (int tag=0; tag<numSubStatesArray.length; tag++){
          if (!noSubstates&&!allowedStates[start][end][tag]) continue;	
          if (grammarTags[tag]) continue;
          //System.out.println("Initializing");
          //if (dummy) allowedStates[start][end][tag] = true;
					narrowRExtent[start][tag] = end;
					narrowLExtent[end][tag] = start;
					wideRExtent[start][tag] = end;
					wideLExtent[end][tag] = start;
					double[] lexiconScores = lexicon.score(word,(short)tag,start,noSmoothing,false);
					//if (!logProbs) iScale[start][end][tag] = scaleArray(lexiconScores,0); 
					for (short n=0; n<lexiconScores.length; n++){
						double prob = lexiconScores[n];
						if (noSubstates) viScore[start][end][tag] = prob;
						else  
							iScore[start][end][tag][n] = prob;
					}
/*				if (start==1){
					System.out.println(word+" +TAG "+(String)tagNumberer.object(tag)+" "+Arrays.toString(lexiconScores));
				}*/
			}
			start++;
		}
	}
	
	protected void createArrays(boolean firstTime, int numStates, short[] numSubStatesArray, int level, double initVal, boolean justInit) {
		// zero out some stuff first in case we recently ran out of memory and are reallocating
		//spanMass = new double[length][length+1];
		if (firstTime) {
			//clearArrays();
		
			// allocate just the parts of iScore and oScore used (end > start, etc.)
			//    System.out.println("initializing iScore arrays with length " + length + " and numStates " + numStates);
			//if (logProbs){
				viScore = new double[length][length + 1][];
				voScore = new double[length][length + 1][];
				
			//} else{
				iScore = new double[length][length + 1][][];
				oScore = new double[length][length + 1][][];
				//iScale = new int[length][length + 1][];
				//oScale = new int[length][length + 1][];
			//}
			allowedSubStates = new boolean[length][length+1][][];
			allowedStates = new boolean[length][length+1][];
			vAllowedStates = new boolean[length][length+1];
			
		}

		for (int start = 0; start < length; start++) {
			for (int end = start + 1; end <= length; end++) {
				if (firstTime){
					viScore[start][end] = new double[numStates];
					voScore[start][end] = new double[numStates];
					iScore[start][end] = new double[numStates][];
					oScore[start][end] = new double[numStates][];
					//iScale[start][end] = new int[numStates];
					//oScale[start][end] = new int[numStates];
					allowedSubStates[start][end] = new boolean[numStates][];
					allowedStates[start][end] = grammarTags.clone();
					//Arrays.fill(allowedStates[start][end], true);
					vAllowedStates[start][end] = true;
				}
				for (int state=0; state<numSubStatesArray.length;state++){
					//if (end-start>1 && !grammarTags[state]) continue;
					/*if (refreshOnly){
						if (allowedStates[start][end][state]){
							Arrays.fill(iScore[start][end][state], 0);
							Arrays.fill(oScore[start][end][state], 0);
						}
						continue;
					}*/

					
					if (firstTime || allowedStates[start][end][state]){
						if (level<1){
							viScore[start][end][state] = Double.NEGATIVE_INFINITY;
							voScore[start][end][state] = Double.NEGATIVE_INFINITY;
						} else{
	          	iScore[start][end][state] = new double[numSubStatesArray[state]];
							oScore[start][end][state] = new double[numSubStatesArray[state]];
							Arrays.fill(iScore[start][end][state], initVal);
							Arrays.fill(oScore[start][end][state], initVal);
							//Arrays.fill(iScale[start][end], Integer.MIN_VALUE);
							//Arrays.fill(oScale[start][end], Integer.MIN_VALUE);       

							boolean[] newAllowedSubStates = new boolean[numSubStatesArray[state]];
							if (allowedSubStates[start][end][state]==null){
								Arrays.fill(newAllowedSubStates,true);
								allowedSubStates[start][end][state] = newAllowedSubStates;
							} else{ 
								if (!justInit){
									int[][] curLChildMap = lChildMap[level-2];
									int[][] curRChildMap = rChildMap[level-2];
									for (int i=0; i<allowedSubStates[start][end][state].length; i++){
										boolean val = allowedSubStates[start][end][state][i];
										newAllowedSubStates[curLChildMap[state][i]] = val;
										newAllowedSubStates[curRChildMap[state][i]] = val;
									}
									allowedSubStates[start][end][state] = newAllowedSubStates;
								}
							}
						}
          }
          else{
          	if (level<1){
  						viScore[start][end][state] = Double.NEGATIVE_INFINITY;
  						voScore[start][end][state] = Double.NEGATIVE_INFINITY;
          	}
          	else{
	          	iScore[start][end][state] = null;
							oScore[start][end][state] = null;
							//allowedSubStates[start][end][state] = new boolean[1];
							//allowedSubStates[start][end][state][0] = false;
          	}
          }
				}
        if (level>0 && start==0 && end==length ) {
          if (iScore[start][end][0]==null)
            System.out.println("ROOT does not span the entire tree!");
        }
			}
		}
		narrowRExtent = new int[length + 1][numStates];
		wideRExtent = new int[length + 1][numStates];
		narrowLExtent = new int[length + 1][numStates];
		wideLExtent = new int[length + 1][numStates];
		
		for (int loc = 0; loc <= length; loc++) {
			Arrays.fill(narrowLExtent[loc], -1); // the rightmost left with state s ending at i that we can get is the beginning
			Arrays.fill(wideLExtent[loc], length + 1); // the leftmost left with state s ending at i that we can get is the end
			Arrays.fill(narrowRExtent[loc], length + 1); // the leftmost right with state s starting at i that we can get is the end
			Arrays.fill(wideRExtent[loc], -1); // the rightmost right with state s starting at i that we can get is the beginning
		}
	}

		
	  protected void clearArrays() {
	    iScore = oScore = null;
	    viScore = voScore = null;
	    allowedSubStates = null;
	    vAllowedStates = null;
	    // iPossibleByL = iPossibleByR = oFilteredEnd = oFilteredStart =
	    // oPossibleByL = oPossibleByR = tags = null;
	    narrowRExtent = wideRExtent = narrowLExtent = wideLExtent = null;
	  }

	
	  protected void pruneChart(double threshold, short[] numSubStatesArray, int level){
	  	int totalStates = 0, previouslyPossible = 0, nowPossible = 0;
	  	//threshold = Double.NEGATIVE_INFINITY;

	  	double sentenceProb = (level<1) ? viScore[0][length][0] : iScore[0][length][0][0];
	  	//double sentenceScale = iScale[0][length][0];//+1.0 for oScale
	  	if (level<1) nowPossible=totalStates=previouslyPossible=length;
	  	int startDiff = (level<0) ? 2 : 1;
	  	for (int diff = startDiff; diff <= length; diff++) {
	  		for (int start = 0; start < (length - diff + 1); start++) {
	  			int end = start + diff;
	  			int lastState = (level<0) ? 1 : numSubStatesArray.length;
	  			for (int state = 0; state < lastState; state++) {
	  				if (diff>1&&!grammarTags[state]) continue;
	  				//boolean allFalse = true;
	  				if (level==0){
	  					if (!vAllowedStates[start][end]) {
	  						allowedStates[start][end][state]=false;
			  				totalStates++;
	  						continue;
	  					}
	  				} else if (level>0){
	  					if (!allowedStates[start][end][state]) {
			  				totalStates+=numSubStatesArray[state];
			  				continue;
	  					}
	  				}
	  				if (level<1){
		  				totalStates++;
	  					previouslyPossible++;
	  					double iS = viScore[start][end][state];
	  					double oS = voScore[start][end][state];
	  					if (iS==Double.NEGATIVE_INFINITY||oS==Double.NEGATIVE_INFINITY) {
	  						if (level==0)	allowedStates[start][end][state] = false;
	  						else /*level==-1*/ vAllowedStates[start][end]=false;
	  						continue;
	  					}
	  					double posterior = iS + oS - sentenceProb;
	  					if (posterior > threshold) {
	  						if (level==0)	allowedStates[start][end][state]=true;
	  						else vAllowedStates[start][end]=true;
	  						//spanMass[start][end]+=Math.exp(posterior);
	  						nowPossible++;
	  					} else {
	  						if (level==0)	allowedStates[start][end][state] = false;
	  						else vAllowedStates[start][end]=false;
	  					}
	  					continue;
	  				}
	  				// level >= 1 -> iterate over substates	
	  				boolean nonePossible = true;
	  				for (int substate = 0; substate < numSubStatesArray[state]; substate++) {
		  				totalStates++;
	  					if (!allowedSubStates[start][end][state][substate]) continue;
	  					previouslyPossible++;
	  					double iS = iScore[start][end][state][substate];
	  					double oS = oScore[start][end][state][substate];
	  					
	  					if (iS==Double.NEGATIVE_INFINITY||oS==Double.NEGATIVE_INFINITY) {
	  						allowedSubStates[start][end][state][substate] = false;
	  						continue;
	  					}
	  					double posterior = iS + oS - sentenceProb;
	  					if (posterior > threshold) {
	  						allowedSubStates[start][end][state][substate]=true;
	  						nowPossible++;
	  						//spanMass[start][end]+=Math.exp(posterior);
	  						nonePossible=false;
	  					} else {
	  						allowedSubStates[start][end][state][substate] = false;
	  					}

	  					/*if (thisScale>sentenceScale){
	  					 posterior *= Math.pow(GrammarTrainer.SCALE,thisScale-sentenceScale);
	  					 }*/
	  					//}
	  					//allowedStates[start][end][state][0] = !allFalse;
	  					
	  					//int thisScale = iScale[start][end][state]+oScale[start][end][state];
	  					/*if (sentenceScale>thisScale){
	  					 // too small anyways
	  					  allowedStates[start][end][state][0] = false;
	  					  continue;
	  					  }*/
	  				}
	  				if (nonePossible) allowedStates[start][end][state]=false;
	  			}
	  		}
	  	}
	  	/*
	  	System.out.print("[");
	  	for(int st=0; st<length; st++){
	  		for(int en=0; en<=length; en++){
	  			System.out.print(spanMass[st][en]);
	  			if (en<length) System.out.print(", ");
	  		}
	  		if (st<length-1) System.out.print(";\n");
	  	}
	  	System.out.print("]\n");*/
	  	String parse = "";
	  	if (level==-1) parse = "Pre-Parse";
	  	else if (level==0) parse = "X-Bar";
	  	else parse = ((int)Math.pow(2,level))+"-Substates";
  		//System.out.print(parse+". NoPruning: " +totalStates + ". Before: "+previouslyPossible+". After: "+nowPossible+".");
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
	  	double[] pruningThreshold = {-6,-10,-10,-10,-10,-10,-10,-10};//Double.NEGATIVE_INFINITY;//Math.log(1.0e-10);
	  	//double[] pruningThreshold = {-6,-12,-12,-12,-12,-12,-12};
	  	//int startLevel = -1;
	  	for (int level=startLevel; level<=endLevel; level++){
	  		curGrammar = grammarCascade[level-startLevel];
	  		curLexicon = lexiconCascade[level-startLevel];

	  		createArrays(level==startLevel,curGrammar.numStates,curGrammar.numSubStates,level,Double.NEGATIVE_INFINITY,false);
	
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
	  
	  
	  protected void ensureGoldTreeSurvives(Tree<StateSet> tree, int level){
	  	List<Tree<StateSet>> children = tree.getChildren();
	    for (Tree<StateSet> child : children) {
	    	if (!child.isLeaf())
	    		ensureGoldTreeSurvives(child,level);
	    }
	  	StateSet node = tree.getLabel();
	  	short state = node.getState();
	  	if (level<0){
	  		vAllowedStates[node.from][node.to]=true;
	  	}
	  	else{
	  		int start = node.from, end = node.to;
	  		/*if (end-start==1 && !grammarTags[state]){ // POS tags -> use gold ones until lexicon is updated
		  		allowedStates[start][end]= new boolean[numStates];
		  		Arrays.fill(allowedStates[start][end], false);
		  		allowedSubStates[start][end] = new boolean[numStates][];
	  		}*/
	  		allowedStates[start][end][state]=true;
	  		allowedSubStates[start][end][state] = null; // will be taken care of in createArrays
		  	//boolean[] newArray = new boolean[numSubStatesArray[state]+1];
		  	//Arrays.fill(newArray, true);
		  	//allowedSubStates[node.from][node.to][state] = newArray;
		  }
	    
	  	
	  }
	  
	  
	  private void setGoldTreeCountsToOne(Tree<StateSet> tree){
	  	StateSet node = tree.getLabel();
	  	short state = node.getState();
	  	iScore[node.from][node.to][state][0]=1.0;
	  	oScore[node.from][node.to][state][0]=1.0;
	  	List<Tree<StateSet>> children = tree.getChildren();
	    for (Tree<StateSet> child : children) {
	    	if (!child.isLeaf()) setGoldTreeCountsToOne(child);
	    }
	  }
	  
	  
	  
	  public void updateFinalGrammarAndLexicon(Grammar grammar, Lexicon lexicon){
	  	grammarCascade[endLevel-startLevel+1] = grammar;
	  	lexiconCascade[endLevel-startLevel+1] = lexicon;
			Grammar tmpGrammar = grammar.copyGrammar();
			tmpGrammar.logarithmMode();
			Lexicon tmpLexicon = lexicon.copyLexicon();
			tmpLexicon.logarithmMode();
			grammarCascade[endLevel-startLevel] = null;//tmpGrammar;
	  	lexiconCascade[endLevel-startLevel] = null;//tmpLexicon;
		}
	  
	  
	  
	  public Pair<double[][][][],double[][][][]> doInsideOutsideScores(List<String> sentence, Tree<StateSet> tree){
	  	doPreParses(sentence,tree,true);
	  	Grammar curGrammar = grammarCascade[endLevel-startLevel+1];
	  	Lexicon curLexicon = lexiconCascade[endLevel-startLevel+1];

	    double initVal = 0;
	    int level = isBaseline ? 1 : endLevel;
			createArrays(false,curGrammar.numStates,curGrammar.numSubStates,level,initVal,!isBaseline);
      
	    initializeChart(sentence,curLexicon,false,true);
	    doConstrainedInsideScores(curGrammar); 
	    logLikelihood = Math.log(iScore[0][length][0][0]); // + (100*iScale[0][length][0]);
    	
	    oScore[0][length][0][0] = 1.0;
	    doConstrainedOutsideScores(curGrammar);
	    return new Pair<double[][][][],double[][][][]>(iScore,oScore);
	  }
	  
	  public double getLogInsideScore(){
	  	return logLikelihood;
	  }
	  
	  
	  public Tree<String> getBestConstrainedParse(List<String> sentence, List<Integer>[][] pStates) {
	  	doPreParses(sentence,null,false);
	  	bestTree = new Tree<String>("ROOT");
	  	double score = 0;
	  	//bestTree = extractBestViterbiParse(0, 0, 0, length, sentence);
	  	//score = viScore[0][length][0];
	    if (true){//score != Double.NEGATIVE_INFINITY) {
	    	//score = Math.log(score) + (100*iScale[0][length][0]);
	      //System.out.println("\nFound a parse for sentence with length "+length+". The LL is "+score+".");
	      
	      //voScore[0][length][0] = 0.0;
	      //doConstrainedViterbiOutsideScores(baseGrammar); 
	      
	      //pruneChart(pruningThreshold, baseGrammar.numSubStates, grammar.numSubStates, true);
		  	Grammar curGrammar = grammarCascade[endLevel-startLevel+1];
		  	Lexicon curLexicon = lexiconCascade[endLevel-startLevel+1];
	  		//numSubStatesArray = grammar.numSubStates;
	      //clearArrays();
		    double initVal = (viterbiParse) ? Double.NEGATIVE_INFINITY : 0;
		    int level = isBaseline ? 1 : endLevel;
				createArrays(false,curGrammar.numStates,curGrammar.numSubStates,level,initVal,!isBaseline);
	      
		    initializeChart(sentence,curLexicon,false,false);
		    if (viterbiParse) doConstrainedViterbiSubstateInsideScores(curGrammar);
		    else doConstrainedInsideScores(curGrammar); 
		    score = iScore[0][length][0][0];
	    	if (!viterbiParse) score = Math.log(score);// + (100*iScale[0][length][0]);
	    	logLikelihood = score;
	      if (score != Double.NEGATIVE_INFINITY) {
	      	//System.out.println("\nFinally found a parse for sentence with length "+length+". The LL is "+score+".");
	      
	      //oScale[0][length][0]=0;
	      
	      
		      if (!viterbiParse) {
		      	oScore[0][length][0][0] = 1.0;
		      	doConstrainedOutsideScores(curGrammar); 
			    	doConstrainedMaxCScores(sentence,curGrammar,curLexicon);
			    }
	
		      //Tree<String> withoutRoot = extractBestMaxRuleParse(0, length, sentence);
		      // add the root
		      //ArrayList<Tree<String>> rootChild = new ArrayList<Tree<String>>();
		      //rootChild.add(withoutRoot);
		      //bestTree = new Tree<String>("ROOT",rootChild);
		      grammar = curGrammar;
		      lexicon = curLexicon;
		      if (viterbiParse) bestTree = extractBestViterbiParse(0, 0, 0, length, sentence);
		      else bestTree = extractBestMaxRuleParse(0, length, sentence);
		      
		      //System.out.print(bestTree);
	      }
	      /*else {
	      	System.out.println("()\nDid NOT find a parse for sentence with length "+length+".");
	      }*/
      	
	    } 
	  	//computeThightThresholds(sentence);
	  	//System.out.println("\n\n\n\nTightest thresholds so far: "+Arrays.toString(maxThresholds)+"\n\n\n\n");
	  	maxcScore = null;
      maxcSplit = null;
      maxcChild = null;
      maxcLeftChild = null;
      maxcRightChild = null;

	    return bestTree;
	  }
	  
	  public double getLogLikelihood(){
	  	if (logLikelihood == Double.NEGATIVE_INFINITY) return logLikelihood;
	      
	  	if (viterbiParse) return logLikelihood;
	  	ArrayList<Tree<String>> resultList = new ArrayList<Tree<String>>();
	    resultList.add(bestTree);
	  	StateSetTreeList resultStateSetTrees = new StateSetTreeList(resultList, numSubStatesArray, false, tagNumberer, false);
	    ArrayParser llParser = new ArrayParser(grammar, lexicon);
	    for (Tree<StateSet> tree : resultStateSetTrees){
				llParser.doInsideScores(tree,false,false);  // Only inside scores are needed here
				double ll = tree.getLabel().getIScore(0);
				ll *= Math.exp(100*tree.getLabel().getIScale());
				return Math.log(ll);
	    }
	    return Double.NEGATIVE_INFINITY;

	  }

	  
	  /** Assumes that inside and outside scores (sum version, not viterbi) have been computed.
	   *  In particular, the narrowRExtent and other arrays need not be updated.
	   */
	  void doConstrainedMaxCScores(List<String> sentence, Grammar grammar, Lexicon lexicon) {
	  	numSubStatesArray = grammar.numSubStates;
	    maxcScore = new double[length][length + 1][numStates];
	    maxcSplit = new int[length][length + 1][numStates];
	    maxcChild      = new int[length][length + 1][numStates];
	    maxcLeftChild  = new int[length][length + 1][numStates];
	    maxcRightChild = new int[length][length + 1][numStates];
	    double threshold = 1.0e-2;
	    double logNormalizer = iScore[0][length][0][0];
	    double thresh2 = threshold*logNormalizer;
	    for (int diff = 1; diff <= length; diff++) {
	      //System.out.print(diff + " ");
	      for (int start = 0; start < (length - diff + 1); start++) {
	        int end = start + diff;
	        Arrays.fill(maxcSplit[start][end], -1);
	        Arrays.fill(maxcChild[start][end], -1);
	        Arrays.fill(maxcLeftChild[start][end], -1);
	        Arrays.fill(maxcRightChild[start][end], -1);
          if (diff > 1) {
	          // diff > 1: Try binary rules
          	for (int pState=0; pState<numSubStatesArray.length; pState++){
          		if (oScore[start][end][pState] == null) { continue; }
              //if (!allowedStates[start][end][pState]) continue;
	            BinaryRule[] parentRules = grammar.splitRulesWithP(pState);
	            int nParentStates = numSubStatesArray[pState]; // == scores[0][0].length;
              
	            for (int i = 0; i < parentRules.length; i++) {
	              BinaryRule r = parentRules[i];
	              int lState = r.leftChildState;
	              int rState = r.rightChildState;

	              int narrowR = narrowRExtent[start][lState];
	              boolean iPossibleL = (narrowR < end); // can this left constituent leave space for a right constituent?
	              if (!iPossibleL) { continue; }
	              
	              int narrowL = narrowLExtent[end][rState];
	              boolean iPossibleR = (narrowL >= narrowR); // can this right constituent fit next to the left constituent?
	              if (!iPossibleR) { continue; }
	              
	              int min1 = narrowR;
	              int min2 = wideLExtent[end][rState];
	              int min = (min1 > min2 ? min1 : min2); // can this right constituent stretch far enough to reach the left constituent?
	              if (min > narrowL) { continue; }
	              
	              int max1 = wideRExtent[start][lState];
	              int max2 = narrowL;
	              int max = (max1 < max2 ? max1 : max2); // can this left constituent stretch far enough to reach the right constituent?
	              if (min > max) { continue; }
	              // TODO switch order of loops for efficiency
	              double[][][] scores = r.getScores2();
	              int nLeftChildStates = numSubStatesArray[lState]; // == scores.length;
	              int nRightChildStates = numSubStatesArray[rState]; // == scores[0].length;
	              for (int split = min; split <= max; split++) {
	                double ruleScore = 0;
	                if (iScore[start][split][lState] == null) continue;
	                if (iScore[split][end][rState] == null) continue;
	                //if (!allowedStates[start][split][lState]) continue;
	                //if (!allowedStates[split][end][rState]) continue;
	                for (int lp = 0; lp < nLeftChildStates; lp++) {
	                  double lIS = iScore[start][split][lState][lp];
	                  //if (lIS == 0) continue;
	                  if (lIS < thresh2) continue;
	                  //if (!allowedSubStates[start][split][lState][lp]) continue;

	                  for (int rp = 0; rp < nRightChildStates; rp++) {
	                    if (scores[lp][rp]==null) continue;
	                    double rIS = iScore[split][end][rState][rp];
	                    //if (rIS == 0) continue;
	                    if (rIS < thresh2) continue;
	                    //if (!allowedSubStates[split][end][rState][rp]) continue;
	                    for (int np = 0; np < nParentStates; np++) {
	                      //if (!allowedSubStates[start][end][pState][np]) continue;
	                      double pOS = oScore[start][end][pState][np];
	                      //if (pOS == 0) continue;
	                      if (pOS < thresh2) continue;

	                      double ruleS = scores[lp][rp][np];
	                      if (ruleS == 0) continue;
	                      ruleScore += (pOS * ruleS * lIS * rIS) / logNormalizer;
	                    }
	                  }
	                }
//		              double norm = 0;
//                  for (int np = 0; np < nParentStates; np++) {
//                  	norm += oScore[start][end][pState][np]*iScore[start][end][pState][np];
//                  }
                  	double scale = 1.0;/*Math.pow(GrammarTrainer.SCALE,
	                		oScale[start][end][pState]+iScale[start][split][lState]+
	                		iScale[split][end][rState]-iScale[0][length][0]);*/
	                double leftChildScore = maxcScore[start][split][lState];
	                double rightChildScore = maxcScore[split][end][rState];
	                double gScore = ruleScore * leftChildScore * rightChildScore;//ruleScore * leftChildScore * rightChildScore * scale;
	                if (gScore > maxcScore[start][end][pState]) {
	                  maxcScore[start][end][pState] = gScore;
	                  maxcSplit[start][end][pState] = split;
	                  maxcLeftChild[start][end][pState] = lState;
	                  maxcRightChild[start][end][pState] = rState;
	                }
	              }
	            }
	          }
	        } else { // diff == 1
	          // We treat TAG --> word exactly as if it was a unary rule, except the score of the rule is
	          // given by the lexicon rather than the grammar and that we allow another unary on top of it.
	          //for (int tag : lexicon.getAllTags()){
          	for (int tag=0; tag<numSubStatesArray.length; tag++){
              if (!allowedStates[start][end][tag]) continue;
	  				  int nTagStates = numSubStatesArray[tag];
	            String word = sentence.get(start);
	            //System.out.print("Attempting");
	            if (grammar.isGrammarTag(tag)) continue;
	            //System.out.println("Computing maxcScore for span " +start + " to "+end);
	            double[] lexiconScoreArray = lexicon.score(word, (short) tag, start, false,false);
	            double lexiconScores = 0;
	            for (int tp = 0; tp < nTagStates; tp++) {
	              double pOS = oScore[start][end][tag][tp];
	              if (pOS < thresh2) continue;
	              double ruleS = lexiconScoreArray[tp];
	              lexiconScores += (pOS * ruleS) / logNormalizer; // The inside score of a word is 0.0f
	            }
	            double scale = 1.0;/*Math.pow(GrammarTrainer.SCALE,
	            		oScale[start][end][tag]-iScale[0][length][0]);*/
	            //System.out.println("Setting maxcScore for span " +start + " to "+end+" to " +lexiconScores * scale);
	            maxcScore[start][end][tag] = lexiconScores * scale;
	          }
	        }
	        // Try unary rules
	        // Replacement for maxcScore[start][end], which is updated in batch   
	        double[] maxcScoreStartEnd = new double[numStates];
	        for (int i = 0; i < numStates; i++) {
	          maxcScoreStartEnd[i] = maxcScore[start][end][i];
	        }
	        
        	for (int pState=0; pState<numSubStatesArray.length; pState++){
        		if (oScore[start][end][pState] == null) { continue; }
            //if (!allowedStates[start][end][pState]) continue;
	        	UnaryRule[] unaries = grammar.getClosedSumUnaryRulesByParent(pState);
            int nParentStates = numSubStatesArray[pState]; // == scores[0].length;
	          for (int r = 0; r < unaries.length; r++) {
	            UnaryRule ur = unaries[r];
	            int cState = ur.childState;
	            if ((pState == cState)) continue;// && (np == cp))continue;
	            if (iScore[start][end][cState]==null) continue;
	            //if (!allowedStates[start][end][cState]) continue;
	            //new loop over all substates
	            double[][] scores = ur.getScores2();
	            int nChildStates = numSubStatesArray[cState]; // == scores.length;
	            double ruleScore = 0;
	            for (int cp = 0; cp < nChildStates; cp++) {
	              double cIS = iScore[start][end][cState][cp];
	              //if (cIS == 0) continue;
	              if (cIS < thresh2) continue;
	              //if (!allowedSubStates[start][end][cState][cp]) continue;
	              
	              if (scores[cp]==null) continue;
	              for (int np = 0; np < nParentStates; np++) {
	                //if (!allowedSubStates[start][end][pState][np]) continue;
	                double pOS = oScore[start][end][pState][np];
	                if (pOS < thresh2) continue;

	                double ruleS = scores[cp][np];
	                if (ruleS == 0) continue;
	                ruleScore += (pOS * ruleS * cIS) / logNormalizer;
	              }
	            }
	            // log_threshold is a penalty on unaries, to control precision
	            double scale = 1.0;/*Math.pow(GrammarTrainer.SCALE,
	            		oScale[start][end][pState]+iScale[start][end][cState]
	            		-iScale[0][length][0]);*/
	            double childScore = maxcScore[start][end][cState];
	            double gScore = ruleScore / unaryPenalty * childScore * scale;
            	if (gScore > maxcScoreStartEnd[pState]) {
	              maxcScoreStartEnd[pState] = gScore;
	              maxcChild[start][end][pState] = cState;
              }
	          }
	        }
	        maxcScore[start][end] = maxcScoreStartEnd;
	      }
	    }
	  }
	  
	  /**
	   * Returns the best parse, the one with maximum expected labelled recall.
	   * Assumes that the maxc* arrays have been filled.
	   */
	  public Tree<String> extractBestMaxRuleParse(int start, int end, List<String> sentence ) {
	    return extractBestMaxRuleParse1(start, end, 0, sentence);
	  }
	  /**
	   * Returns the best parse for state "state", potentially starting with a unary rule
	   */
	  public Tree<String> extractBestMaxRuleParse1(int start, int end, int state, List<String> sentence ) {
	  	//System.out.println(start+", "+end+";");
	  	int cState = maxcChild[start][end][state];
	    if (cState == -1) {
	      return extractBestMaxRuleParse2(start, end, state, sentence);
	    } else {
	      List<Tree<String>> child = new ArrayList<Tree<String>>();
	      child.add( extractBestMaxRuleParse2(start, end, cState, sentence) );
	      String stateStr = (String) tagNumberer.object(state);
	      totalUsedUnaries++;
	      //System.out.println("Adding a unary spanning from "+start+" to "+end+". P: "+stateStr+" C: "+child.get(0).getLabel());
	      short intermediateNode = grammar.getUnaryIntermediate((short)state,(short)cState);
	      if (intermediateNode==0){
	      	System.out.println("Added a bad unary from "+start+" to "+end+". P: "+stateStr+" C: "+child.get(0).getLabel());
	      }
	      if (intermediateNode>0){
	        List<Tree<String>> restoredChild = new ArrayList<Tree<String>>();
	        nTimesRestoredUnaries++;
	        String stateStr2 = (String)tagNumberer.object(intermediateNode);
	        restoredChild.add(new Tree<String>(stateStr2, child));
	        //System.out.println("Restored a unary from "+start+" to "+end+": "+stateStr+" -> "+stateStr2+" -> "+child.get(0).getLabel());
	      	return new Tree<String>(stateStr,restoredChild);
		    }
	      return new Tree<String>(stateStr, child);
	    }
	  }

	  /**
	   * Returns the best parse for state "state", but cannot start with a unary
	   */
	  public Tree<String> extractBestMaxRuleParse2(int start, int end, int state, List<String> sentence ) {
	    List<Tree<String>> children = new ArrayList<Tree<String>>();
	    String stateStr = (String)tagNumberer.object(state);//+""+start+""+end;
	    boolean posLevel = (end - start == 1);
	    if (posLevel) {
	    	if (grammar.isGrammarTag(state)){
	        List<Tree<String>> childs = new ArrayList<Tree<String>>();
	        childs.add(new Tree<String>(sentence.get(start)));
	        String stateStr2 = (String)tagNumberer.object(maxcChild[start][end][state]);//+""+start+""+end;
	        children.add(new Tree<String>(stateStr2,childs));
	    	}
	    	else children.add(new Tree<String>(sentence.get(start)));
	    } else {
	      int split = maxcSplit[start][end][state];
	      if (split == -1) {
	        System.err.println("Warning: no symbol can generate the span from "+ start+ " to "+end+".");
	        System.err.println("The score is "+maxcScore[start][end][state]+" and the state is supposed to be "+stateStr);
	        System.err.println("The insideScores are "+Arrays.toString(iScore[start][end][state])+" and the outsideScores are " +Arrays.toString(oScore[start][end][state]));
	        System.err.println("The maxcScore is "+maxcScore[start][end][state]);
	        //return  extractBestMaxRuleParse2(start, end, maxcChild[start][end][state], sentence);
	        return  new Tree<String>("ROOT");      
	      }
	      int lState = maxcLeftChild[start][end][state];
	      int rState = maxcRightChild[start][end][state];
	      Tree<String> leftChildTree = extractBestMaxRuleParse1(start, split, lState, sentence);
	      Tree<String> rightChildTree = extractBestMaxRuleParse1(split, end, rState, sentence);
	      children.add(leftChildTree);
	      children.add(rightChildTree);
	    }
	    return new Tree<String>(stateStr, children);
	  }

	  
		/** Fills in the iScore array of each category over each span
		 *  of length 2 or more.
		 */
		
	  void doConstrainedViterbiInsideScores(Grammar grammar, boolean level0grammar) {
	  	numSubStatesArray = grammar.numSubStates;
	  	//double[] oldIScores = new double[maxNSubStates];
	  	//int smallestScale = 10, largestScale = -10;
	  	for (int diff = 1; diff <= length; diff++) {
	  		for (int start = 0; start < (length - diff + 1); start++) {
	  			int end = start + diff;
	  			final int lastState = (level0grammar) ? 1 : numSubStatesArray.length;
	  			for (int pState=0; pState<lastState; pState++){
	  				if (diff==1) continue; // there are no binary rules that span over 1 symbol only
	  				//if (iScore[start][end][pState] == null) { continue; }
	  				if (!grammarTags[pState]) continue;
	  				if (!vAllowedStates[start][end]) continue;
  					double oldIScore = viScore[start][end][pState];
  					double bestIScore = oldIScore;
	  				BinaryRule[] parentRules = grammar.splitRulesWithP(pState);
	  				for (int i = 0; i < parentRules.length; i++) {
	  					BinaryRule r = parentRules[i];
	  					int lState = r.leftChildState;
	  					int rState = r.rightChildState;
	  					
	  					int narrowR = narrowRExtent[start][lState];
	  					boolean iPossibleL = (narrowR < end); // can this left constituent leave space for a right constituent?
	  					if (!iPossibleL) { continue; }
	  					
	  					int narrowL = narrowLExtent[end][rState];
	  					boolean iPossibleR = (narrowL >= narrowR); // can this right constituent fit next to the left constituent?
	  					if (!iPossibleR) { continue; }
	  					
	  					int min1 = narrowR;
	  					int min2 = wideLExtent[end][rState];
	  					int min = (min1 > min2 ? min1 : min2); // can this right constituent stretch far enough to reach the left constituent?
	  					if (min > narrowL) { continue; }
	  					
	  					int max1 = wideRExtent[start][lState];
	  					int max2 = narrowL;
	  					int max = (max1 < max2 ? max1 : max2); // can this left constituent stretch far enough to reach the right constituent?
	  					if (min > max) { continue; }
	  					
	  					// new: loop over all substates
	  					double[][][] scores = r.getScores2();
  						double pS = Double.NEGATIVE_INFINITY;
  						if (scores[0][0]!=null) pS = scores[0][0][0];
  						if (pS == Double.NEGATIVE_INFINITY) continue;

  						for (int split = min; split <= max; split++) {
	  						if (!vAllowedStates[start][split]) continue;
	  						if (!vAllowedStates[split][end]) continue;
	  						
	  						double lS = viScore[start][split][lState];
	  						if (lS == Double.NEGATIVE_INFINITY) continue;
	  							  						
	  						double rS = viScore[split][end][rState];
	  						if (rS == Double.NEGATIVE_INFINITY) continue;
	  						
	  						double tot = pS + lS + rS;
	  						if (tot >= bestIScore) { bestIScore = tot;} 
	  					}
	  				}
  					if (bestIScore > oldIScore) { // this way of making "parentState" is better
							// than previous
							viScore[start][end][pState] = bestIScore;
							if (oldIScore == Double.NEGATIVE_INFINITY) {
								if (start > narrowLExtent[end][pState]) {
									narrowLExtent[end][pState] = start;
									wideLExtent[end][pState] = start;
								} else {
									if (start < wideLExtent[end][pState]) {
										wideLExtent[end][pState] = start;
									}
								}
								if (end < narrowRExtent[start][pState]) {
									narrowRExtent[start][pState] = end;
									wideRExtent[start][pState] = end;
								} else {
									if (end > wideRExtent[start][pState]) {
										wideRExtent[start][pState] = end;
									}
								}
							}
	  				}
	  			}
	  			final int lastStateU = (level0grammar&&diff>1) ? 1 : numSubStatesArray.length;
	  			for (int pState=0; pState<lastStateU; pState++){
	  				if (!grammarTags[pState]) continue;
	  				//if (iScore[start][end][pState] == null) { continue; }
	  				//if (!allowedStates[start][end][pState][0]) continue;
	  				if (diff!=1 && !vAllowedStates[start][end]) continue;
	  				UnaryRule[] unaries = grammar.getClosedViterbiUnaryRulesByParent(pState);
	  				double oldIScore = viScore[start][end][pState];
	  				double bestIScore = oldIScore;
	  				for (int r = 0; r < unaries.length; r++) {
	  					UnaryRule ur = unaries[r];
	  					int cState = ur.childState;

	  					if ((pState == cState)) continue;// && (np == cp))continue;

	  					double iS = viScore[start][end][cState];
	  					if (iS == Double.NEGATIVE_INFINITY) continue;

	  					double[][] scores = ur.getScores2();
  						double pS = Double.NEGATIVE_INFINITY;
  						if (scores[0]!=null) pS = scores[0][0];
  						if (pS == Double.NEGATIVE_INFINITY) continue;
	  					
	  					double tot = iS + pS;
	  					
	  					if (tot >= bestIScore) { bestIScore = tot; }
	  				}		
	  				if (bestIScore > oldIScore) {
	  					viScore[start][end][pState] = bestIScore;
	  					if (oldIScore == Double.NEGATIVE_INFINITY) {
	  						if (start > narrowLExtent[end][pState]) {
	  							narrowLExtent[end][pState] = start;
	  							wideLExtent[end][pState] = start;
	  						} else {
	  							if (start < wideLExtent[end][pState]) {
	  								wideLExtent[end][pState] = start;
	  							}
	  						}
	  						if (end < narrowRExtent[start][pState]) {
	  							narrowRExtent[start][pState] = end;
	  							wideRExtent[start][pState] = end;
	  						} else {
	  							if (end > wideRExtent[start][pState]) {
	  								wideRExtent[start][pState] = end;
	  							}
	  						}
	  					}
//	  					}
	  					//}
	  				}
	  			}
	  		}
	  	}
	  }

		void doConstrainedViterbiSubstateInsideScores(Grammar grammar) {
	  	numSubStatesArray = grammar.numSubStates;

	  	for (int diff = 1; diff <= length; diff++) {
				for (int start = 0; start < (length - diff + 1); start++) {
					int end = start + diff;
					final int lastState = numSubStatesArray.length;
	      	for (int pState=0; pState<lastState; pState++){
	        	if (diff==1) continue; // there are no binary rules that span over 1 symbol only
	        	//if (iScore[start][end][pState] == null) { continue; }
	        	//if (!grammarTags[pState]) continue;
	        	if (!allowedStates[start][end][pState]) continue;
						final int nParentSubStates = numSubStatesArray[pState];
						double[] bestIScore = new double[nParentSubStates];
						double[] oldIScore = new double[nParentSubStates];
						for (int s=0; s<nParentSubStates; s++) bestIScore[s] = oldIScore[s] = iScore[start][end][pState][s];
						BinaryRule[] parentRules = grammar.splitRulesWithP(pState);
						for (int i = 0; i < parentRules.length; i++) {
							BinaryRule r = parentRules[i];
							int lState = r.leftChildState;
							int rState = r.rightChildState;

							int narrowR = narrowRExtent[start][lState];
							boolean iPossibleL = (narrowR < end); // can this left constituent leave space for a right constituent?
							if (!iPossibleL) { continue; }
							
							int narrowL = narrowLExtent[end][rState];
							boolean iPossibleR = (narrowL >= narrowR); // can this right constituent fit next to the left constituent?
							if (!iPossibleR) { continue; }
							
							int min1 = narrowR;
							int min2 = wideLExtent[end][rState];
							int min = (min1 > min2 ? min1 : min2); // can this right constituent stretch far enough to reach the left constituent?
							if (min > narrowL) { continue; }
							
							int max1 = wideRExtent[start][lState];
							int max2 = narrowL;
							int max = (max1 < max2 ? max1 : max2); // can this left constituent stretch far enough to reach the right constituent?
							if (min > max) { continue; }
							
							// new: loop over all substates
							double[][][] scores = r.getScores2();
							for (int np = 0; np < nParentSubStates; np++) {
          			if (!allowedSubStates[start][end][pState][np]) continue;
								for (int split = min; split <= max; split++) {
									if (!allowedStates[start][split][lState]) continue;
									if (!allowedStates[split][end][rState]) continue;

									for (int lp = 0; lp < scores.length; lp++) {
		          			//if (!allowedSubStates[start][split][lState][lp]) continue;
		          			double lS = iScore[start][split][lState][lp];
										if (lS == Double.NEGATIVE_INFINITY) continue;
										
										for (int rp = 0; rp < scores[0].length; rp++) {
			          			//if (!allowedSubStates[split][end][rState][rp]) continue;
											double pS = Double.NEGATIVE_INFINITY;
											if (scores[lp][rp]!=null) pS = scores[lp][rp][np];
											if (pS==Double.NEGATIVE_INFINITY){
												continue; 
												//System.out.println("s "+start+" sp "+split+" e "+end+" pS "+pS+" rS "+rS);
											}
											double rS = iScore[split][end][rState][rp];
											if (rS == Double.NEGATIVE_INFINITY) continue;
											
											double tot = pS + lS + rS;
											if (tot >= bestIScore[np]) { bestIScore[np] = tot;} 
										}
									}
								}
							}
						}
						boolean firstTime = true;
						for (int s=0; s<nParentSubStates; s++) {
							if (bestIScore[s] > oldIScore[s]) { // this way of making "parentState" is better
								// than previous
								iScore[start][end][pState][s] = bestIScore[s];
								if (firstTime && oldIScore[s] == Double.NEGATIVE_INFINITY) {
									firstTime = false;
									if (start > narrowLExtent[end][pState]) {
										narrowLExtent[end][pState] = start;
										wideLExtent[end][pState] = start;
									} else {
										if (start < wideLExtent[end][pState]) {
											wideLExtent[end][pState] = start;
										}
									}
									if (end < narrowRExtent[start][pState]) {
										narrowRExtent[start][pState] = end;
										wideRExtent[start][pState] = end;
									} else {
										if (end > wideRExtent[start][pState]) {
											wideRExtent[start][pState] = end;
										}
									}
								}
							}
						}
					}
	      	final int lastStateU = numSubStatesArray.length;
	      	for (int pState=0; pState<lastStateU; pState++){
	      		//if (!grammarTags[pState]) continue;
	      		//if (iScore[start][end][pState] == null) { continue; }
	      		//if (!allowedStates[start][end][pState][0]) continue;
	      		if (!allowedStates[start][end][pState]) continue;
						UnaryRule[] unaries = grammar.getClosedViterbiUnaryRulesByParent(pState);
						int nParentSubStates = numSubStatesArray[pState];
						double[] bestIScore = new double[nParentSubStates];
						double[] oldIScore = new double[nParentSubStates];
						for (int s=0; s<nParentSubStates; s++) bestIScore[s] = oldIScore[s] = iScore[start][end][pState][s];
						for (int r = 0; r < unaries.length; r++) {
							UnaryRule ur = unaries[r];
							int cState = ur.childState;
							if ((pState == cState)) continue;// && (np == cp))continue;
							if (!allowedStates[start][end][cState]) continue;
							//new loop over all substates
							double[][] scores = ur.getScores2();
							for (int np = 0; np < nParentSubStates; np++) {
          			if (!allowedSubStates[start][end][pState][np]) continue;
								for (int cp = 0; cp < scores.length; cp++) {
	          			//if (!allowedSubStates[start][end][cState][cp]) continue;
									double pS = Double.NEGATIVE_INFINITY;
									if (scores[cp]!=null) pS = scores[cp][np];
									if (pS==Double.NEGATIVE_INFINITY){
										continue;
									}
									double iS = iScore[start][end][cState][cp];
									if (iS == Double.NEGATIVE_INFINITY) continue;
									double tot = iS + pS;

									if (tot >= bestIScore[np]) { bestIScore[np] = tot; }
								}
							}
						}
						boolean firstTime = true;
						for (int s=0; s<nParentSubStates; s++){
						if (bestIScore[s] > oldIScore[s]) {
								iScore[start][end][pState][s] = bestIScore[s];
								if (firstTime && oldIScore[s] == Double.NEGATIVE_INFINITY) {
									firstTime = false;
									if (start > narrowLExtent[end][pState]) {
										narrowLExtent[end][pState] = start;
										wideLExtent[end][pState] = start;
									} else {
										if (start < wideLExtent[end][pState]) {
											wideLExtent[end][pState] = start;
										}
									}
									if (end < narrowRExtent[start][pState]) {
										narrowRExtent[start][pState] = end;
										wideRExtent[start][pState] = end;
									} else {
										if (end > wideRExtent[start][pState]) {
											wideRExtent[start][pState] = end;
										}
									}
								}
							}
						}
					}
				}
			}
		}

		void doConstrainedViterbiOutsideScores(Grammar grammar, boolean level0grammar) {
			for (int diff = length; diff >= 1; diff--) {
				for (int start = 0; start + diff <= length; start++) {
					int end = start + diff;
					final int lastState = (level0grammar) ? 1 : numSubStatesArray.length;
					for (int pState=0; pState<lastState; pState++){
						//if (!grammarTags[pState]) continue;
						double oS = voScore[start][end][pState];
						if (oS == Double.NEGATIVE_INFINITY) { continue; }

//						if (diff!=1&&!vAllowedStates[start][end]) continue;
						UnaryRule[] rules = grammar.getClosedViterbiUnaryRulesByParent(pState);
						for (int r = 0; r < rules.length; r++) {
							UnaryRule ur = rules[r];
							int cState = ur.childState;
							if (cState == pState) continue;

							double iS = viScore[start][end][cState];
							if (iS == Double.NEGATIVE_INFINITY) { continue; }							

							double[][] scores = ur.getScores2();
							double oldOScore = voScore[start][end][cState];
							double bestOScore = oldOScore;

							double pS = scores[0][0];
							double tot = oS + pS;
							if (tot > bestOScore) {
								bestOScore = tot; 
								if (bestOScore > oldOScore) {
									voScore[start][end][cState] = bestOScore;
								}
							}
						}
					}
      	for (int pState=0; pState<lastState; pState++){
      		if (!grammarTags[pState]) continue;
          double oS = voScore[start][end][pState];
      		if (oS == Double.NEGATIVE_INFINITY) { continue; }
//    		if (!vAllowedStates[start][end]) continue;
					BinaryRule[] rules = grammar.splitRulesWithP(pState);
					for (int r = 0; r < rules.length; r++) {
						BinaryRule br = rules[r];
						
						int lState = br.leftChildState; 
						int min1 = narrowRExtent[start][lState];
						if (end < min1) { continue; }
						
						int rState = br.rightChildState;
						int max1 = narrowLExtent[end][rState];
						if (max1 < min1) { continue; }
						
						int min = min1;
						int max = max1;
						if (max - min > 2) {
							int min2 = wideLExtent[end][rState];
							min = (min1 > min2 ? min1 : min2);
							if (max1 < min) { continue; }
							int max2 = wideRExtent[start][lState];
							max = (max1 < max2 ? max1 : max2);
							if (max < min) { continue; }
						}
						
						double[][][] scores = br.getScores2();
						double pS = Double.NEGATIVE_INFINITY;//scores[0][0][0];
						if (scores[0][0]!=null) pS = scores[0][0][0];
						if (pS == Double.NEGATIVE_INFINITY) { continue; }

						for (int split = min; split <= max; split++) {
							if (!vAllowedStates[start][split]) continue;
							if (!vAllowedStates[split][end]) continue;

						  double lS = viScore[start][split][lState];
							if (lS == Double.NEGATIVE_INFINITY) { continue; }
		          
							double rS = viScore[split][end][rState];
							if (rS == Double.NEGATIVE_INFINITY) { continue; }

							double totL = pS + rS + oS;
							if (totL > voScore[start][split][lState]) {
								voScore[start][split][lState] = totL;
							}
							double totR = pS + lS + oS;
							if (totR > voScore[split][end][rState]) {
								voScore[split][end][rState] = totR;
							}
						}
					}
				}
			}
		}
	}
	
	void doConstrainedViterbiSubstateOutsideScores(Grammar grammar) {
		for (int diff = length; diff >= 1; diff--) {
			for (int start = 0; start + diff <= length; start++) {
				int end = start + diff;
				final int lastState = numSubStatesArray.length;
      	for (int pState=0; pState<lastState; pState++){
      		//if (!grammarTags[pState]) continue;
      		//if (iScore[start][end][pState] == null) { continue; }
          //if (!allowedStates[start][end][pState][0]) continue;
      		if (!allowedStates[start][end][pState]) continue;
					UnaryRule[] rules = grammar.getClosedViterbiUnaryRulesByParent(pState);
					for (int r = 0; r < rules.length; r++) {
						UnaryRule ur = rules[r];
						int cState = ur.childState;
						if (cState == pState) continue;
						if (!allowedStates[start][end][cState]) continue;

						//new loop over all substates
						double[][] scores = ur.getScores2();
						final int nParentSubStates = numSubStatesArray[cState];
						double[] bestOScore = new double[nParentSubStates];
						double[] oldOScore = new double[nParentSubStates];
						for (int s=0; s<nParentSubStates; s++) bestOScore[s] = oldOScore[s] = oScore[start][end][cState][s];

						for (int cp = 0; cp < scores.length; cp++) {
		          //if (!allowedSubStates[start][end][cState][cp]) continue;

							double iS = iScore[start][end][cState][cp];
							if (iS == Double.NEGATIVE_INFINITY) { continue; }							
							
							for (int np = 0; np < scores[0].length; np++) {
			          //if (!allowedSubStates[start][end][pState][np]) continue;
								double pS = Double.NEGATIVE_INFINITY;
								if (scores[cp]!=null) pS = scores[cp][np];
								if (pS == Double.NEGATIVE_INFINITY) { continue; }							
			          
			          double oS = oScore[start][end][pState][np];
			      		if (oS == Double.NEGATIVE_INFINITY) { continue; }
								double tot = oS + pS;

								if (tot > bestOScore[cp]) {
									bestOScore[cp] = tot; 
								}
							}
						}
						for (int s=0; s<nParentSubStates; s++) {
							if (bestOScore[s] > oldOScore[s]) {
								oScore[start][end][cState][s] = bestOScore[s];
							}
						}
					}
				}
      	for (int pState=0; pState<lastState; pState++){
      		if (!grammarTags[pState]) continue;
      		//if (oScore[start][end][pState] == null) { continue; }
          //if (!allowedStates[start][end][pState][0]) continue;
      		if (!allowedStates[start][end][pState]) continue;
					BinaryRule[] rules = grammar.splitRulesWithP(pState);
					for (int r = 0; r < rules.length; r++) {
						BinaryRule br = rules[r];
						
						int lState = br.leftChildState; 
						int min1 = narrowRExtent[start][lState];
						if (end < min1) { continue; }
						
						int rState = br.rightChildState;
						int max1 = narrowLExtent[end][rState];
						if (max1 < min1) { continue; }
						
						int min = min1;
						int max = max1;
						if (max - min > 2) {
							int min2 = wideLExtent[end][rState];
							min = (min1 > min2 ? min1 : min2);
							if (max1 < min) { continue; }
							int max2 = wideRExtent[start][lState];
							max = (max1 < max2 ? max1 : max2);
							if (max < min) { continue; }
						}
						
						double[][][] scores = br.getScores2();
						for (int split = min; split <= max; split++) {
							if (!allowedStates[start][split][lState]) continue;
							if (!allowedStates[split][end][rState]) continue;

							for (int lp=0; lp<scores.length; lp++){
								double lS = iScore[start][split][lState][lp];
								if (lS == Double.NEGATIVE_INFINITY) { continue; }
								double oldLOScore = oScore[start][split][lState][lp];
								double bestLOScore = oldLOScore;

								for (int rp=0; rp<scores[lp].length; rp++){
									if (scores[lp][rp]==null) continue;
									
									double rS = iScore[split][end][rState][rp];
									if (rS == Double.NEGATIVE_INFINITY) { continue; }

									double oldROScore = oScore[split][end][rState][rp];
									double bestROScore = oldROScore;

									for (int np=0; np<scores[lp][rp].length; np++){
					          double oS = oScore[start][end][pState][np];
					      		if (oS == Double.NEGATIVE_INFINITY) { continue; }
										
					      		double pS = scores[lp][rp][np];
										if (pS == Double.NEGATIVE_INFINITY) { continue; }
										
										double totL = pS + rS + oS;
										if (totL > bestLOScore) {
											bestLOScore = totL;
										}
										double totR = pS + lS + oS;
										if (totR > bestROScore) {
											bestROScore = totR;
										}
									}
									if (bestLOScore > oldLOScore) {
										oScore[start][split][lState][lp] = bestLOScore;
									}
									if (bestROScore > oldROScore) {
										oScore[split][end][rState][rp] = bestROScore;
									}
								}
							}
						}
					}
				}
			}
		}
	}

	public void printUnaryStats(){
 		System.out.println("Touched "+touchedRules+" rules.");
 		System.out.println("Used a total of "+totalUsedUnaries+" unaries.");
 		System.out.println("Restored "+nTimesRestoredUnaries+" unarie chains.");
	}
	
	/**
	 * Return the single best parse.
	 * Note that the returned tree may be missing intermediate nodes in
	 * a unary chain because it parses with a unary-closed grammar.
	 */
	public Tree<String> extractBestViterbiParse(int gState, int gp, int start, int end, List<String> sentence ) {
		// find sources of inside score
		// no backtraces so we can speed up the parsing for its primary use
		double bestScore = iScore[start][end][gState][gp];
		String goalStr = (String)tagNumberer.object(gState);
		if (goalStr.endsWith("^g")) goalStr = goalStr.substring(0,goalStr.length()-2);
		if (outputSub) goalStr = goalStr + "-" + gp;
		if (outputScore) goalStr = goalStr + " " + bestScore;
		//System.out.println("Looking for "+goalStr+" from "+start+" to "+end+" with score "+ bestScore+".");
		if (end - start == 1) {
			// if the goal state is a preterminal state, then it can't transform into
			// anything but the word below it
			if (!grammarTags[gState]) {
				List<Tree<String>> child = new ArrayList<Tree<String>>();
				child.add(new Tree<String>(sentence.get(start)));
				return new Tree<String>(goalStr, child);
			}
			// if the goal state is not a preterminal state, then find a way to
			// transform it into one
			else {
				double veryBestScore = Double.NEGATIVE_INFINITY;
				int newIndex = -1;
				int newCp = -1;
				UnaryRule[] unaries = grammar.getClosedViterbiUnaryRulesByParent(gState);
				double childScore = bestScore;
				for (int r = 0; r < unaries.length; r++) {
					UnaryRule ur = unaries[r];
					int cState = ur.childState;
					if (cState == gState) continue;
					if (grammarTags[cState]) continue;
					if (!allowedStates[start][end][cState]) continue;
					double[][] scores = ur.getScores2();
					for (int cp=0; cp<scores.length; cp++){
						if (scores[cp]==null) continue;
						double ruleScore = iScore[start][end][cState][cp] + scores[cp][gp];
						if (ruleScore >= veryBestScore) {
							childScore = iScore[start][end][cState][cp];
							veryBestScore = ruleScore;
							newIndex = cState;
							newCp = cp;
						}
					}
				}
	      List<Tree<String>> child1 = new ArrayList<Tree<String>>();
	      child1.add(new Tree<String>(sentence.get(start)));
	      String goalStr1 = (String) tagNumberer.object(newIndex);
	  		if (outputSub) goalStr1 = goalStr1 + "-" + newCp;
	  		if (outputScore) goalStr1 = goalStr1 + " " + childScore;
	      if (goalStr1==null)
	        System.out.println("goalStr1==null with newIndex=="+newIndex+" goalStr=="+goalStr);
	      List<Tree<String>> child = new ArrayList<Tree<String>>();
	      child.add(new Tree<String>(goalStr1, child1));
	      return new Tree<String>(goalStr, child);
			}
		}
		// check binaries first
		for (int split = start + 1; split < end; split++) {
			//for (Iterator binaryI = grammar.bRuleIteratorByParent(gState, gp); binaryI.hasNext();) {
			//BinaryRule br = (BinaryRule) binaryI.next();
			BinaryRule[] parentRules = grammar.splitRulesWithP(gState);
			for (int i = 0; i < parentRules.length; i++) {
				BinaryRule br = parentRules[i];
				
				int lState = br.leftChildState;
				if (iScore[start][split][lState]==null) continue;
				
				int rState = br.rightChildState;
				if (iScore[split][end][rState]==null) continue;
				
				//new: iterate over substates
				double[][][] scores = br.getScores2();
				for (int lp=0; lp<scores.length; lp++){
					for (int rp=0; rp<scores[lp].length; rp++){
						if (scores[lp][rp]==null) continue;
						double score = scores[lp][rp][gp] + iScore[start][split][lState][lp]
									+ iScore[split][end][rState][rp];
						if (matches(score, bestScore)) {
							// build binary split
							Tree<String> leftChildTree = extractBestViterbiParse(lState, lp, start, split, sentence);
							Tree<String> rightChildTree = extractBestViterbiParse(rState, rp, split, end, sentence);
							List<Tree<String>> children = new ArrayList<Tree<String>>();
							children.add(leftChildTree);
							children.add(rightChildTree);
							Tree<String> result = new Tree<String>(goalStr, children);
							//System.out.println("Binary node: "+result);
							//result.setScore(score);
							return result;
						}
					}
				}
			}
		}
		// check unaries
		//for (Iterator unaryI = grammar.uRuleIteratorByParent(gState, gp); unaryI.hasNext();) {
		//UnaryRule ur = (UnaryRule) unaryI.next();
		UnaryRule[] unaries = grammar.getClosedViterbiUnaryRulesByParent(gState);
		for (int r = 0; r < unaries.length; r++) {
			UnaryRule ur = unaries[r];
			int cState = ur.childState;
			
			if (iScore[start][end][cState]==null) continue;
			
			//new: iterate over substates
			double[][] scores = ur.getScores2();
			for (int cp=0; cp<scores.length; cp++){
				if (scores[cp]==null) continue;
				double score = scores[cp][gp] + iScore[start][end][cState][cp];
				if ((cState != ur.parentState || cp != gp) && matches(score, bestScore)) {
					// build unary
					Tree<String> childTree = extractBestViterbiParse(cState, cp, start, end, sentence);
					List<Tree<String>> children = new ArrayList<Tree<String>>();
					children.add(childTree);
					Tree<String> result = new Tree<String>(goalStr, children);
					//System.out.println("Unary node: "+result);
					//result.setScore(score);
					return result;
				}
			}
		}
		System.err.println("Warning: could not find the optimal way to build state "+goalStr+" spanning from "+ start+ " to "+end+".");
		return null;
	}

  public double computeTightThresholds(List<String> sentence) {
  	clearArrays();
  	length = (short)sentence.size();
  	double score = 0;
  	Grammar curGrammar = null;
  	Lexicon curLexicon = null;
    //double[] pruningThreshold = {-6,-14,-14,-14,-14,-14,-14};//Double.NEGATIVE_INFINITY;//Math.log(1.0e-10);
  	//double[] pruningThreshold = {-6,-10,-10,-10,-10,-10,-10};//Double.NEGATIVE_INFINITY;//Math.log(1.0e-10);
  	//int startLevel = -1;
  	for (int level=startLevel; level<nLevels; level++){
  		curGrammar = grammarCascade[level-startLevel];
  		curLexicon = lexiconCascade[level-startLevel];

  		createArrays(level==startLevel,curGrammar.numStates,curGrammar.numSubStates,level,Double.NEGATIVE_INFINITY,false);

	    initializeChart(sentence,curLexicon,level<1,false);
	    if (level<1){
	    	doConstrainedViterbiInsideScores(curGrammar,level==startLevel); 
	    	score = viScore[0][length][0];
	    } else {
	    	doConstrainedViterbiSubstateInsideScores(curGrammar); 
	    	score = iScore[0][length][0][0];
	    }
	    if (score==Double.NEGATIVE_INFINITY) return -1;
      System.out.println("\nFound a parse for sentence with length "+length+". The LL is "+score+".");
      if (level<1){
      	voScore[0][length][0] = 0.0;
      	doConstrainedViterbiOutsideScores(curGrammar,level==startLevel);
  		} else {
      	oScore[0][length][0][0] = 0.0;
      	doConstrainedViterbiSubstateOutsideScores(curGrammar);
  		}
      double minThresh = -10;
      if (level>=0){
      	minThresh = getTightestThrehold(0,length,0, true, level);
      	if (minThresh == Double.NEGATIVE_INFINITY) return -1;
      	System.out.println("Can set the threshold for level "+level+" to "+minThresh);
      	maxThresholds[level] = Math.min(maxThresholds[level],minThresh);
      }
      pruneChart(-14, curGrammar.numSubStates, level);
      //pruneChart(pruningThreshold[level+1], curGrammar.numSubStates, grammar.numSubStates, level);
  	}
    return -1.0;

  }

	private double getTightestThrehold(int start, int end, int state, boolean canStartWithUnary, int level) {
		boolean posLevel = (end - start == 1);
		if (posLevel) return -1;
		double minChildren = Double.POSITIVE_INFINITY;
		if (!posLevel){
			if (canStartWithUnary){
				int cState = maxcChild[start][end][state];
		    if (cState != -1) {
		      return getTightestThrehold(start, end, cState, false,level);
		    } 
			}
			int split = maxcSplit[start][end][state];
			double lThresh = getTightestThrehold(start, split, maxcLeftChild[start][end][state], true,level);
	    double rThresh = getTightestThrehold(split, end, maxcRightChild[start][end][state], true,level);
	    minChildren = Math.min(lThresh,rThresh);
		}
    double sentenceProb = (level<1) ? viScore[0][length][0] : iScore[0][length][0][0];
    double maxThreshold = Double.NEGATIVE_INFINITY; 
		for (int substate=0; substate < numSubStatesArray[state]; substate++){
			double iS = (level<1) ? viScore[start][end][state] : iScore[start][end][state][substate];
			double oS = (level<1) ? voScore[start][end][state] : oScore[start][end][state][substate];
			if (iS==Double.NEGATIVE_INFINITY||oS==Double.NEGATIVE_INFINITY) continue;
			double posterior = iS + oS - sentenceProb;
			if (posterior > maxThreshold) maxThreshold = posterior;
		}
  	
    return Math.min(maxThreshold,minChildren); 
	}

	// the startIndex array is accessed as [parent][left][right] 
	// unlike the rule-score arrays which are accessed as [leftSubstate][rightSubstate][parenSubstate]
	// HACK: the startIndex for unary rules can be accessed by setting the right child to 0 -> since there are no rules with ROOT as right child
	public void incrementExpectedCounts(double[] probs, Grammar grammar, int[][][] startIndexGrammar, 
			Lexicon lexicon, int[][] startIndexLexicon, List<String> sentence, boolean hardCounts			) {
  	numSubStatesArray = grammar.numSubStates;
  	double tree_score = iScore[0][length][0][0];

		for (int start = 0; start < length; start++) {
			final int lastState = numSubStatesArray.length;
			String word = sentence.get(start);
    	int wordInd = wordNumberer.number(word);
    	String sig = lexicon.getCachedSignature(word, start);
    	int sigInd = wordNumberer.number(sig);
			for (int tag=0; tag<lastState; tag++){
    		if (grammar.isGrammarTag(tag)) continue;
				int startIndexWord = startIndexLexicon[wordInd][tag];
				int startIndexSig = startIndexLexicon[sigInd][tag];

				final int nSubStates = numSubStatesArray[tag];
	      for (short substate=0; substate<nSubStates; substate++) {
          //weight by the probability of seeing the tag and word together, given the sentence
	        double weight = 1;
          weight = iScore[start][start+1][tag][substate] / tree_score * oScore[start][start+1][tag][substate];
          probs[startIndexWord+substate] += weight;
          probs[startIndexSig+substate] += weight;
	      }
			}
		}
	
  	
  	for (int diff = 1; diff <= length; diff++) {
			for (int start = 0; start < (length - diff + 1); start++) {
				int end = start + diff;
				final int lastState = numSubStatesArray.length;
      	for (int pState=0; pState<lastState; pState++){
        	if (diff==1) continue; // there are no binary rules that span over 1 symbol only
        	//if (iScore[start][end][pState] == null) { continue; }
        	//if (!grammarTags[pState]) continue;
        	if (!allowedStates[start][end][pState]) continue;
					final int nParentSubStates = numSubStatesArray[pState];
					BinaryRule[] parentRules = grammar.splitRulesWithP(pState);
					for (int i = 0; i < parentRules.length; i++) {
						BinaryRule r = parentRules[i];
						int lState = r.leftChildState;
						int rState = r.rightChildState;
						
						int thisStartIndex = startIndexGrammar[pState][lState][rState];
						
						int narrowR = narrowRExtent[start][lState];
						boolean iPossibleL = (narrowR < end); // can this left constituent leave space for a right constituent?
						if (!iPossibleL) { continue; }
						
						int narrowL = narrowLExtent[end][rState];
						boolean iPossibleR = (narrowL >= narrowR); // can this right constituent fit next to the left constituent?
						if (!iPossibleR) { continue; }
						
						int min1 = narrowR;
						int min2 = wideLExtent[end][rState];
						int min = (min1 > min2 ? min1 : min2); // can this right constituent stretch far enough to reach the left constituent?
						if (min > narrowL) { continue; }
						
						int max1 = wideRExtent[start][lState];
						int max2 = narrowL;
						int max = (max1 < max2 ? max1 : max2); // can this left constituent stretch far enough to reach the right constituent?
						if (min > max) { continue; }
						
						// new: loop over all substates
						double[][][] scores = r.getScores2();
						for (int split = min; split <= max; split++) {
							if (!allowedStates[start][split][lState]) continue;
							if (!allowedStates[split][end][rState]) continue;
							int curInd = 0;
							
							for (int lp = 0; lp < scores.length; lp++) {
          			double lcIS = iScore[start][split][lState][lp];
								
								for (int rp = 0; rp < scores[0].length; rp++) {
									if (scores[lp][rp]==null) continue;
									if (lcIS == 0) { curInd++; continue; }
									double rcIS = iScore[split][end][rState][rp];
									if (rcIS == 0) { curInd++; continue; }

									for (int np = 0; np < nParentSubStates; np++) {
			        			if (!allowedSubStates[start][end][pState][np]) continue;
			        			double pOS = oScore[start][end][pState][np];
										if (pOS==0) { curInd++; continue; }
										
										double rS = scores[lp][rp][np];
										if (rS==0) { curInd++; continue; }
			        			
										double ruleCount = (hardCounts) ? 1 : (rS * lcIS / tree_score) * rcIS * pOS;
										probs[thisStartIndex + curInd] += ruleCount;
										curInd++;
 
									}
								}
							}
						}
					}
				}
      	final int lastStateU = numSubStatesArray.length;
      	for (int pState=0; pState<lastStateU; pState++){
      		//if (!grammarTags[pState]) continue;
      		//if (iScore[start][end][pState] == null) { continue; }
      		//if (!allowedStates[start][end][pState][0]) continue;
      		if (!allowedStates[start][end][pState]) continue;
					List<UnaryRule> unaries = grammar.getUnaryRulesByParent(pState);
					int nParentSubStates = numSubStatesArray[pState];
					double[] bestIScore = new double[nParentSubStates];
					double[] oldIScore = new double[nParentSubStates];
					for (int s=0; s<nParentSubStates; s++) bestIScore[s] = oldIScore[s] = iScore[start][end][pState][s];
					for (UnaryRule ur : unaries) {
						int cState = ur.childState;
						if ((pState == cState)) continue;// && (np == cp))continue;
						if (!allowedStates[start][end][cState]) continue;
						//new loop over all substates
						double[][] scores = ur.getScores2();
						int thisStartIndex = startIndexGrammar[pState][cState][0];
						if (thisStartIndex<0) continue; // a unary chain rule...
						int curInd = 0;
						for (int cp = 0; cp < scores.length; cp++) {
							if (scores[cp]==null) continue; 
							double cIS = iScore[start][end][cState][cp];
							if (cIS == 0) { curInd++; continue; }

							for (int np = 0; np < nParentSubStates; np++) {
	        			if (!allowedSubStates[start][end][pState][np]) continue;
								double rS = scores[cp][np];
								if (rS==0){ curInd++; continue; }

								double pOS = oScore[start][end][pState][np];
								
	        			double ruleCount = (hardCounts) ? 1 : (rS * cIS / tree_score) * pOS;
	        			probs[thisStartIndex + curInd] += ruleCount;
	        			curInd++;
							}
						}
					}
      	}
			}
		}
	}

	// the startIndex array is accessed as [parent][left][right] 
	// unlike the rule-score arrays which are accessed as [leftSubstate][rightSubstate][parenSubstate]
	// HACK: the startIndex for unary rules can be accessed by setting the right child to 0 -> since there are no rules with ROOT as right child
	public void incrementExpectedGoldCounts(double[] probs, Tree<StateSet> tree, Grammar grammar, 
			int[][][] startIndexGrammar, Lexicon lexicon, int[][] startIndexLexicon, boolean hardCounts, double tree_score, int tree_scale) {
  	
  	if (tree.isLeaf())
			return;
		if (tree.isPreTerminal()){
			StateSet parent = tree.getLabel();
			StateSet child = tree.getChildren().get(0).getLabel();
			
			String word = child.getWord();
    	int wordInd = wordNumberer.number(word);
    	String sig = lexicon.getCachedSignature(word, child.from);
    	int sigInd = wordNumberer.number(sig);
    	short tag = tree.getLabel().getState();
			int startIndexWord = startIndexLexicon[wordInd][tag];
			int startIndexSig = startIndexLexicon[sigInd][tag];

			final int nSubStates = numSubStatesArray[tag];
			double scalingFactor = Math.pow(GrammarTrainer.SCALE,	parent.getOScale()-tree_scale) / tree_score;
			double[] wordWeightFraction = lexicon.getConditionalWordScore(word, tag, true);
			double[] sigWeightFraction = lexicon.getConditionalSignatureScore(sig, tag, true);
			for (short substate=0; substate<nSubStates; substate++) {
        //weight by the probability of seeing the tag and word together, given the sentence
				double pIS = parent.getIScore(substate); // Parent outside score
				if (pIS==0) { continue; }
				double pOS = parent.getOScore(substate); // Parent outside score
				if (pOS==0) { continue; }
				double weight = 1;
				weight = hardCounts ? 1 : /*pIS*/ scalingFactor * pOS;
				probs[startIndexWord+substate] += weight*wordWeightFraction[substate];
        probs[startIndexSig+substate] += weight*sigWeightFraction[substate];
      }
			return;
		}
		List<Tree<StateSet>> children = tree.getChildren();
		StateSet parent = tree.getLabel();
		short parentState = parent.getState();
		int nParentSubStates = numSubStatesArray[parentState];
		//if (oScore[pStart][pEnd][parentState]==null) break;
		switch (children.size()) {
		case 0:
			// This is a leaf (a preterminal node, if we count the words themselves),
			// nothing to do
			break;
		case 1:
			// first check whether this is a unary chain!
			/*if (!children.get(0).isPreTerminal() && children.get(0).getChildren().size()==1){ // if so, skip the intermediate node
				children = children.get(0).getChildren();
			}	*/		
			StateSet child = children.get(0).getLabel();
			short childState = child.getState();
			int thisStartIndex = startIndexGrammar[parentState][childState][0];
			int curInd = 0;
			int nChildSubStates = numSubStatesArray[childState];
			UnaryRule urule = new UnaryRule(parentState, childState);
			double[][] oldUScores = grammar.getUnaryScore(urule); // rule score
			double scalingFactor = Math.pow(GrammarTrainer.SCALE,
					parent.getOScale()+child.getIScale()-tree_scale);
			if (scalingFactor==0){
				System.out.println("p: "+parent.getOScale()+" c: "+child.getIScale()+" t:"+tree_scale);
			}
			for (short i = 0; i < nChildSubStates; i++) {
				if (oldUScores[i]==null) continue;
				double cIS = child.getIScore(i);
				if (cIS==0) { curInd++; continue; }
				for (short j = 0; j < nParentSubStates; j++) {
					double pOS = parent.getOScore(j); // Parent outside score
					if (pOS==0) { curInd++; continue; }
					double rS = oldUScores[i][j];
					if (rS==0) { curInd++; continue; }
					if (tree_score==0)
						tree_score = 1;
					double ruleCount = hardCounts ? 1 : (rS * cIS / tree_score) * scalingFactor * pOS;
					probs[thisStartIndex + curInd++] += ruleCount;
				}
			}
			break;
		case 2:
			StateSet leftChild = children.get(0).getLabel();
			short lChildState = leftChild.getState();
			StateSet rightChild = children.get(1).getLabel();
			short rChildState = rightChild.getState();
			int nLeftChildSubStates = numSubStatesArray[lChildState];
			int nRightChildSubStates = numSubStatesArray[rChildState];
			thisStartIndex = startIndexGrammar[parentState][lChildState][rChildState];
			curInd = 0;
				//new double[nLeftChildSubStates][nRightChildSubStates][];
			BinaryRule brule = new BinaryRule(parentState, lChildState, rChildState);
			double[][][] oldBScores = grammar.getBinaryScore(brule); // rule score
			if (oldBScores==null){
				//rule was not in the grammar
				//parent.setIScores(iScores2);
				//break;
				oldBScores=new double[nLeftChildSubStates][nRightChildSubStates][nParentSubStates];
				ArrayUtil.fill(oldBScores,1.0);
			}
			scalingFactor = Math.pow(GrammarTrainer.SCALE,
					parent.getOScale()+leftChild.getIScale()+rightChild.getIScale()-tree_scale);
			if (scalingFactor==0){
				System.out.println("p: "+parent.getOScale()+" l: "+leftChild.getIScale()+" r:"+rightChild.getIScale()+" t:"+tree_scale);
			}
			for (short i = 0; i < nLeftChildSubStates; i++) {
				double lcIS = leftChild.getIScore(i);
				for (short j = 0; j < nRightChildSubStates; j++) {
					if (oldBScores[i][j]==null) continue;
					if (lcIS==0) { curInd++; continue; }
					double rcIS = rightChild.getIScore(j);
					if (rcIS==0) { curInd++; continue; }
					// allocate parent array
					for (short k = 0; k < nParentSubStates; k++) {
						double pOS = parent.getOScore(k); // Parent outside score
						if (pOS==0) { curInd++; continue; }
						double rS = oldBScores[i][j][k];
						if (rS==0) { curInd++; continue; }
						if (tree_score==0)
							tree_score = 1;
						double ruleCount = hardCounts ? 1 : (rS * lcIS / tree_score) * rcIS * scalingFactor * pOS;
						probs[thisStartIndex + curInd++] += ruleCount;
					}
				}
			}
			break;
		default:
			throw new Error("Malformed tree: more than two children");
		}
		
		for (Tree<StateSet> child : children) {
			incrementExpectedGoldCounts(probs, child, grammar, startIndexGrammar, lexicon, startIndexLexicon, hardCounts, tree_score, tree_scale);
		}
	}
  	
	
	public void doGoldInsideOutsideScores(Tree<StateSet> tree, List<String> sentence) {
  	Grammar curGrammar = grammarCascade[endLevel-startLevel+1];
  	Lexicon curLexicon = lexiconCascade[endLevel-startLevel+1];

  	//pruneChart(Double.POSITIVE_INFINITY/*pruningThreshold[level+1]*/, curGrammar.numSubStates, endLevel);
  	allowedStates = new boolean[length][length+1][numSubStatesArray.length];
    ensureGoldTreeSurvives(tree, endLevel);

    double initVal = 0;
    int level = isBaseline ? 1 : endLevel;
		createArrays(false/*false*/,curGrammar.numStates,curGrammar.numSubStates,level,initVal,false);
    
		//setGoldTreeCountsToOne(tree);
    initializeChart(sentence,curLexicon,false,true);
    doConstrainedInsideScores(curGrammar); 
    logLikelihood = Math.log(iScore[0][length][0][0]); // + (100*iScale[0][length][0]);
  	
    oScore[0][length][0][0] = 1.0;
    doConstrainedOutsideScores(curGrammar);

  }

	
}
