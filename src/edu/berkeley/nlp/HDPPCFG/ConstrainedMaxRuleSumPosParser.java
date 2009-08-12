package edu.berkeley.nlp.HDPPCFG;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.math.SloppyMath;
import edu.berkeley.nlp.util.PriorityQueue;

/**
 * 
 * @author Romain Thibaux
 * 
 * Max-Rule parser. This is to rules what the labelled recall parser is to symbols.
 * It maximizes the expected number of correct rules. This is a conceptually simple
 * extension of the labelled recall algorithm ('Parsing Algorithms and Metrics',
 * Joshua Goodman, 1996), and also equivalent to the approximate algorithm of
 * Matsuzaki, Miyao and Tsujii (2005), which they arrive to as the solution of
 * a variational approximation.
 * 
 * When the probability of a rule or state is evaluated, we sum out the annotations.
 *
 */

public class ConstrainedMaxRuleSumPosParser extends ConstrainedSumParser {

  // maxcScore does not have substate information since these are marginalized out 
  protected double[][][] maxcScore;  // start, end, state --> logProb
  protected double[][][] maxsScore;  // start, end, state --> logProb
  protected int[][][] maxcSplit;  // start, end, state -> split position
  protected int[][][] maxcChild;  // start, end, state -> unary child (if any)
  protected int[][][] maxcLeftChild;  // start, end, state -> left child
  protected int[][][] maxcRightChild;  // start, end, state -> right child
  protected double unaryPenalty;
  private boolean sumOutPOS;
  private int posTAG;
  
  public ConstrainedMaxRuleSumPosParser(Grammar gr, Lexicon lex, short[] nSub, double unaryPenalty) {
    super(gr, lex, nSub);
    System.out.println("The unary penalty for parsing is "+unaryPenalty+".");
    this.unaryPenalty = unaryPenalty;
    sumOutPOS = true;//true;
    posTAG = tagNumberer.number("XX");
    
  }

  /** Assumes that inside and outside scores (sum version, not viterbi) have been computed.
   *  In particular, the narrowRExtent and other arrays need not be updated.
   */
  void doConstrainedMaxCScores(List<String> sentence) {
    maxcScore = new double[length][length + 1][numStates];
    maxcSplit = new int[length][length + 1][numStates];
    maxcChild      = new int[length][length + 1][numStates];
    maxcLeftChild  = new int[length][length + 1][numStates];
    maxcRightChild = new int[length][length + 1][numStates];
    if (sumOutPOS) maxsScore = new double[length][length + 1][numStates];
    
    double logNormalizer = iScore[0][length][0][0];
    for (int diff = 1; diff <= length; diff++) {
      //System.out.print(diff + " ");
      for (int start = 0; start < (length - diff + 1); start++) {
        int end = start + diff;
        Arrays.fill(maxcSplit[start][end], -1);
        Arrays.fill(maxcChild[start][end], -1);
        Arrays.fill(maxcLeftChild[start][end], -1);
        Arrays.fill(maxcRightChild[start][end], -1);
        //List<Integer> possibleSt = possibleStates[start][end];
        List<Integer> possibleSt = null;
        if (noConstrains){
        	possibleSt = new ArrayList<Integer>();
        	for (int i=0; i<numSubStatesArray.length; i++){
        		possibleSt.add(i);
        	}
        }
        else{
        	possibleSt = possibleStates[start][end];
        }
        if (diff > 1) {
          // diff > 1: Try binary rules
          for (int pState : possibleSt) {
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
              // TODO switch order of loops for efficiency
              double[][][] scores = r.getScores2();
              int nLeftChildStates = numSubStatesArray[lState]; // == scores.length;
              int nRightChildStates = numSubStatesArray[rState]; // == scores[0].length;
              int nParentStates = numSubStatesArray[pState]; // == scores[0][0].length;
              boolean hasLeftPOSChild = !grammar.isGrammarTag(lState);
              boolean hasRightPOSChild = !grammar.isGrammarTag(rState);
              for (int split = min; split <= max; split++) {
              	boolean sumOutLeft=false, sumOutRight=false;
              	
              	if (split==start+1 && hasLeftPOSChild) sumOutLeft = true;
              	if (split==end-1 && hasRightPOSChild) sumOutRight = true;
              	
              	boolean[] ruleIndicesToSum = getBinaryRulesToSum(pState,lState,rState,sumOutLeft,sumOutRight,i);
              	double gScore = 0, bestG=0;
              	int bestLChild=0,bestRChild=0;
              	for (int i2 = 0; i2 < parentRules.length; i2++) {
              		if (!ruleIndicesToSum[i2]) continue;
              		double ruleScore = 0;
              		r = parentRules[i2];
              		
              		lState = r.leftChildState;
              		rState = r.rightChildState;
              		
              		narrowR = narrowRExtent[start][lState];
              		iPossibleL = (narrowR < end); // can this left constituent leave space for a right constituent?
              		if (!iPossibleL) { continue; }
              		
              		narrowL = narrowLExtent[end][rState];
              		iPossibleR = (narrowL >= narrowR); // can this right constituent fit next to the left constituent?
              		if (!iPossibleR) { continue; }
              		
              		min1 = narrowR;
              		min2 = wideLExtent[end][rState];
              		min = (min1 > min2 ? min1 : min2); // can this right constituent stretch far enough to reach the left constituent?
              		if (min > narrowL) { continue; }
              		
              		max1 = wideRExtent[start][lState];
              		max2 = narrowL;
              		max = (max1 < max2 ? max1 : max2); // can this left constituent stretch far enough to reach the right constituent?
              		if (min > max) { continue; }
              		
              		scores = r.getScores2();
              		nLeftChildStates = numSubStatesArray[lState]; // == scores.length;
              		nRightChildStates = numSubStatesArray[rState]; // == scores[0].length;
              		nParentStates = numSubStatesArray[pState]; // == scores[0][0].length;
              		
              		if (iScore[start][split][lState] == null) continue;
              		if (iScore[split][end][rState] == null) continue;
              		for (int lp = 0; lp < nLeftChildStates; lp++) {
              			double lIS = iScore[start][split][lState][lp];
              			if (lIS == 0) continue;
              			
              			for (int rp = 0; rp < nRightChildStates; rp++) {
              				if (scores[lp][rp]==null) continue;
              				double rIS = iScore[split][end][rState][rp];
              				if (rIS == 0) continue;
              				for (int np = 0; np < nParentStates; np++) {
              					double pOS = oScore[start][end][pState][np];
              					if (pOS == 0) continue;
              					double ruleS = scores[lp][rp][np];
              					if (ruleS == 0) continue;
              					ruleScore += (pOS * ruleS * lIS * rIS) / logNormalizer;
              				}
              			}
              		}
              		double scale = Math.pow(GrammarTrainer.SCALE,
              				oScale[start][end][pState]+iScale[start][split][lState]+
              				iScale[split][end][rState]-iScale[0][length][0]);
              		double leftChildScore = maxcScore[start][split][lState];
              		double rightChildScore = maxcScore[split][end][rState];
              		double tmp = ruleScore * leftChildScore * rightChildScore * scale;
              		gScore += tmp;
              		if (tmp>bestG){
              			bestG = tmp;
              			bestLChild = lState;
              			bestRChild = rState;
              		}
              	}
              	if (sumOutLeft) lState = posTAG;
              	if (sumOutRight) rState = posTAG;
              	if (gScore > maxcScore[start][end][pState]) {
              		maxcScore[start][end][pState] = gScore;
              		maxcSplit[start][end][pState] = split;
              		maxcLeftChild[start][end][pState] = bestLChild; //lState;
              		maxcRightChild[start][end][pState] = bestRChild; //rState;
              	}
              }   
            }
          }
        } else { // diff == 1
          // We treat TAG --> word exactly as if it was a unary rule, except the score of the rule is
          // given by the lexicon rather than the grammar and that we allow another unary on top of it.
          //for (int tag : lexicon.getAllTags()){
        	for (int tag : possibleSt){
  				  int nTagStates = numSubStatesArray[tag];
            String word = sentence.get(start);
            //System.out.print("Attempting");
            if (grammar.isGrammarTag(tag)) continue;
            //System.out.println("Computing maxcScore for span " +start + " to "+end);
            double[] lexiconScoreArray = lexicon.score(word, (short) tag, start, false,false);
            double lexiconScores = 0;
            for (int tp = 0; tp < nTagStates; tp++) {
              double pOS = oScore[start][end][tag][tp];
              double ruleS = lexiconScoreArray[tp];
              lexiconScores += (pOS * ruleS) / logNormalizer; // The inside score of a word is 0.0f
            }
            double scale = Math.pow(GrammarTrainer.SCALE,
            		oScale[start][end][tag]-iScale[0][length][0]);
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
        
        for (int pState : possibleSt){
          //UnaryRule[] unaries = grammar.getUnaryRulesByParent(pState).toArray(new UnaryRule[0]);
        	UnaryRule[] unaries = grammar.getClosedSumUnaryRulesByParent(pState);
          for (int r = 0; r < unaries.length; r++) {
            UnaryRule ur = unaries[r];
            int cState = ur.childState;
            boolean sumOutChild = ((end-start)==1 && !grammar.isGrammarTag(cState));
            boolean[] ruleIndicesToSum = getUnaryRulesToSum(pState,sumOutChild,r);
            double gScore = 0, bestG = 0;
            int bestChild = 0;
            for (int r2 = 0; r2 < unaries.length; r2++) {
              if (!ruleIndicesToSum[r2]) continue;
            	ur = unaries[r2];
              cState = ur.childState;
	            if (iScore[start][end][cState]==null) continue;
	            if ((pState == cState)) continue;// && (np == cp))continue;
	            //new loop over all substates
	            double[][] scores = ur.getScores2();
	            int nChildStates = numSubStatesArray[cState]; // == scores.length;
	            int nParentStates = numSubStatesArray[pState]; // == scores[0].length;
	            double ruleScore = 0;
	            for (int cp = 0; cp < nChildStates; cp++) {
	              double cIS = iScore[start][end][cState][cp];
	              if (cIS == 0) continue;
	              if (scores[cp]==null) continue;
	              for (int np = 0; np < nParentStates; np++) {
	                double pOS = oScore[start][end][pState][np];
	                double ruleS = scores[cp][np];
	                if (ruleS == 0) continue;
	                ruleScore += (pOS * ruleS * cIS) / logNormalizer;
	              }
	            }
	            // log_threshold is a penalty on unaries, to control precision
	            double scale = Math.pow(GrammarTrainer.SCALE,
	            		oScale[start][end][pState]+iScale[start][end][cState]
	            		-iScale[0][length][0]);
	            double childScore = maxcScore[start][end][cState];
	            double tmp = ruleScore / unaryPenalty * childScore * scale;
	            gScore += tmp;
	            if (tmp>bestG){
	            	bestG = tmp;
	            	bestChild = cState;
	            }
            }
            if (sumOutChild) cState = posTAG;
          	if (gScore > maxcScoreStartEnd[pState]) {
              maxcScoreStartEnd[pState] = gScore;
              maxcChild[start][end][pState] = bestChild;//cState;
            }
          }
        }
        maxcScore[start][end] = maxcScoreStartEnd;
      }
    }
  }
  
  public boolean[] getBinaryRulesToSum(int pState, int lState, int rState, boolean sumOutLeft, boolean sumOutRight, int ind) {
  	boolean sumOutBoth = sumOutLeft&sumOutRight;
    boolean nothingToSumOut = !(sumOutLeft||sumOutRight);
    
  	BinaryRule[] parentRules = grammar.splitRulesWithP(pState);
    boolean[] resultList = new boolean[parentRules.length];
    Arrays.fill(resultList,false);
    if (nothingToSumOut){
    	resultList[ind] = true;
    }
    else{
	    for (int i = 0; i < parentRules.length; i++) {
	      BinaryRule r = parentRules[i];
	      if (sumOutBoth && !grammar.isGrammarTag(r.leftChildState) && !grammar.isGrammarTag(r.rightChildState)) resultList[i] = true;
	      else if (sumOutLeft && r.rightChildState==rState && !grammar.isGrammarTag(r.leftChildState)) resultList[i] = true;
	      else if (sumOutRight && r.leftChildState==lState && !grammar.isGrammarTag(r.rightChildState)) resultList[i] = true;
	    }
  	}
		return resultList;
	}

  boolean[] getUnaryRulesToSum(int pState, boolean sumOutChild, int ind) {
    
  	UnaryRule[] parentRules = grammar.getClosedSumUnaryRulesByParent(pState);
    boolean[] resultList = new boolean[parentRules.length];
    Arrays.fill(resultList,false);
    if (!sumOutChild){
    	resultList[ind] = true;
    }
    else{
	    for (int i = 0; i < parentRules.length; i++) {
	      UnaryRule r = parentRules[i];
	      if (!grammar.isGrammarTag(r.childState)) resultList[i] = true;
	    }
  	}
		return resultList;
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
    if (state==posTAG) return extractBestMaxRuleParse2(start, end, state, sentence);
  	int cState = maxcChild[start][end][state];
    if (cState == -1) {
      return extractBestMaxRuleParse2(start, end, state, sentence);
    } else {
      List<Tree<String>> child = new ArrayList<Tree<String>>();
      child.add( extractBestMaxRuleParse2(start, end, cState, sentence) );
      String stateStr = (String) tagNumberer.object(state);
      totalUsedUnaries++;
      //System.out.println("Adding a unary spanning from "+start+" to "+end+". P: "+stateStr+" C: "+child.get(0).getLabel());
      if (cState==posTAG) return new Tree<String>(stateStr, child);
      short intermediateNode = grammar.getUnaryIntermediate((short)state,(short)cState);
      if (intermediateNode==0){
      	System.out.println("Added a bad unary from "+start+" to "+end+". P: "+stateStr+" C: "+child.get(0).getLabel());
      }
      if (intermediateNode>0){
        List<Tree<String>> restoredChild = new ArrayList<Tree<String>>();
        nTimesRestoredUnaries++;
        String stateStr2 = (String)tagNumberer.object(intermediateNode);
        restoredChild.add(new Tree<String>(stateStr2, child));
        System.out.println("Restored a unary from "+start+" to "+end+": "+stateStr+" -> "+stateStr2+" -> "+child.get(0).getLabel());
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
    	if (state == posTAG) children.add(new Tree<String>(sentence.get(start)));
    	else if (grammar.isGrammarTag(state)){
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
  
  /*
  void initializeChart(List<String> sentence) {
    super.initializeChart(sentence);
    int start = 0;
    int end = start+1;
    for (String word : sentence) {
      end = start+1;
      for (short tag : lexicon.getAllTags()){
        for (short n=0; n<numSubStatesArray[tag]; n++){
          double prob = lexicon.score(word,tag,n,start,false);
          iScore[start][end][tag][n] = prob;
        }
      }
      start++;
    }
  }*/
  
  public Tree<String> getBestConstrainedParse(List<String> sentence, List<Integer>[][] pStates) {
    length = (short)sentence.size();
    this.possibleStates = pStates;
    createArrays();
    initializeChart(sentence);

    doConstrainedInsideScores(); //change to 2

    Tree<String> bestTree = new Tree<String>("ROOT");
    double score = iScore[0][length][0][0];
    if (score > 0) {
    	score = Math.log(score) + (100*iScale[0][length][0]);
      System.out.println("\nFound a parse for sentence with length "+length+". The LL is "+score+".");
      
      oScore[0][length][0][0] = 1.0;
      oScale[0][length][0]=0;
      doConstrainedOutsideScores(); //change to 2
      doConstrainedMaxCScores(sentence);
      
      //Tree<String> withoutRoot = extractBestMaxRuleParse(0, length, sentence);
      // add the root
      //ArrayList<Tree<String>> rootChild = new ArrayList<Tree<String>>();
      //rootChild.add(withoutRoot);
      //bestTree = new Tree<String>("ROOT",rootChild);
      bestTree = extractBestMaxRuleParse(0, length, sentence);
      
      maxcScore = null;
      maxcSplit = null;
      maxcChild = null;
      maxcLeftChild = null;
      maxcRightChild = null;
      //System.out.print(bestTree);
    } else {
      System.out.println("()\nDid NOT find a parse for sentence with length "+length+".");
    }

    return bestTree;
  }
  
  @SuppressWarnings("unchecked")
	public List<Integer>[][] getPossibleStates(List<String> sentence, double threshold){
    length = (short)sentence.size();
    createArrays();
    initializeChart(sentence);
    
    doConstrainedInsideScores(); //change to 2

    double sentenceScore = iScore[0][length][0][0];
    int sentenceScale = iScale[0][length][0];
		double logSentenceProb = Math.log(sentenceScore) + (100*sentenceScale);
		if (sentenceScore > 0) {
			System.out.println("\nFound a parse for sentence with length " + length
					+ ". The LL is " + logSentenceProb + ".");
		} else {
			System.out.println("Did NOT find a parse for sentence with length "
					+ length + ".");
		}
    oScore[0][length][0][0] = 1.0;
    oScale[0][length][0]=0;
    doConstrainedOutsideScores(); //change to 2

		List<Integer>[][] possibleStates = new ArrayList[length + 1][length + 1];

		int unprunedStates = 0;
		int prunedStates = 0;

		for (int diff = 1; diff <= length; diff++) {
			for (int start = 0; start < (length - diff + 1); start++) {
				int end = start + diff;
				possibleStates[start][end] = new ArrayList<Integer>();
				for (int state = 0; state < numStates; state++) {
					double posterior = 0; 
					double scale = Math.pow(GrammarTrainer.SCALE,
								iScale[start][end][state]+oScale[start][end][state]-
																	sentenceScale);
					for (int substate=0; substate<numSubStatesArray[state]; substate++){
				  	posterior += (iScore[start][end][state][substate]*
				  														oScore[start][end][state][substate]/
				  														sentenceScore)*scale;
				  }
					if (posterior>0) {
						unprunedStates++;
					}
					if (posterior > threshold) {
						possibleStates[start][end].add(new Integer(state));
						prunedStates++;
						// if ((start==0)&&(end==length) )System.out.println(start+" "+end+"
						// "+state);
						// System.out.println("i "+iScore[start][end][state]+" o
						// "+oScore[start][end][state]+" v-pos: "+viterbiPosterior);
					}
				}
			}
		}
		System.out.print("Down to " + prunedStates + " states from "
				+ unprunedStates + ". ");
		return possibleStates;


	}

}
