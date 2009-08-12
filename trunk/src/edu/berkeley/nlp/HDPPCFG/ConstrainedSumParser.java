package edu.berkeley.nlp.HDPPCFG;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.syntax.Tree;

/**
 * 
 * @author Romain Thibaux
 * 
 * An extension of ConstrainedArrayParser that computes the scores P(w_{i:j}|A), whose
 * computation involves a sum, rather than the Viterbi scores, which involve a max.
 * This is used by the Labelled Recall parser (maximizes the expected number of correct
 * symbols) and the Max-Rule parser (maximizes the expected number of correct rules, ie
 * all 3 symbols correct).  
 *
 */
	
public abstract class ConstrainedSumParser extends ConstrainedArrayParser {

  public ConstrainedSumParser(Grammar gr, LexiconInterface lex, short[] nSub) {
    super(gr, lex, nSub);
    
    
  }
  
  void doConstrainedInsideScores() {
    //double[] oldIScores = new double[maxNSubStates];
  	//int smallestScale = 10, largestScale = -10;
      for (int diff = 1; diff <= length; diff++) {
    	//smallestScale = 10; largestScale = -10;
      //System.out.print(diff + " ");
      for (int start = 0; start < (length - diff + 1); start++) {
        int end = start + diff;
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
        
        //List<Integer> possibleSt = new ArrayList<Integer>(); for (int i = 0; i<numStates; i++){possibleSt.add(i); }
        for (int pState : possibleSt) {
        	if (diff==1) continue; // there are no binary rules that span over 1 symbol only
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
            int nLeftChildStates = numSubStatesArray[lState];
            int nRightChildStates = numSubStatesArray[rState];
            int nParentStates = numSubStatesArray[pState];
        		for (int split = min; split <= max; split++) {
              Arrays.fill(scoresToAdd,0.0);
              boolean somethingChanged = false;
	            for (int lp = 0; lp < nLeftChildStates; lp++) {
	            	for (int rp = 0; rp < nRightChildStates; rp++) {
	            		if (scores[lp][rp]==null) continue;
            			if (iScore[start][split][lState] == null) continue;
            			if (iScore[split][end][rState] == null) continue;
            			
            			double lS = iScore[start][split][lState][lp];
            			if (lS == 0) continue;
            			double rS = iScore[split][end][rState][rp];
            			if (rS == 0) continue;
            			
            			for (int np = 0; np < nParentStates; np++) {
            				double pS = scores[lp][rp][np];
            				scoresToAdd[np] += pS * lS * rS; 
            				somethingChanged = true;
            			}
            		}
            	}
	            if (!somethingChanged) continue;
		          boolean firstTime = false;
		        	int parentScale = iScale[start][end][pState];
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
		          }
		          for (int np = 0; np < nParentStates; np++) {
                if (firstTime) iScore[start][end][pState][np] = scoresToAdd[np];
                else if (scoresToAdd[np] > 0) {
                	iScore[start][end][pState][np] += scoresToAdd[np];
		          	}
		          }
		          //iScale[start][end][pState] = currentScale;
	            iScale[start][end][pState] = scaleArray(iScore[start][end][pState],iScale[start][end][pState]);
          		if (firstTime) {
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
        for (int pState : possibleSt){
          // Should be: Closure under sum-product:
          UnaryRule[] unaries = grammar.getClosedSumUnaryRulesByParent(pState);
          //UnaryRule[] unaries = grammar.getUnaryRulesByParent(pState).toArray(new UnaryRule[0]);
          for (int r = 0; r < unaries.length; r++) {
            UnaryRule ur = unaries[r];
            int cState = ur.childState;
            if (iScore[start][end][cState]==null) continue;
            if ((pState == cState)) continue;// && (np == cp))continue;
            //new loop over all substates
          	//System.out.println("Rule "+r+" out of "+unaries.length+" "+ur);
            double[][] scores = ur.getScores2();
            int nChildStates = numSubStatesArray[cState];//scores[0].length;
            int nParentStates = numSubStatesArray[pState];//scores[0].length;
            Arrays.fill(scoresToAdd,0.0);
            boolean somethingChanged = false;
            for (int cp = 0; cp < scores.length; cp++) {
              if (scores[cp]==null) continue;
              double iS = iScore[start][end][cState][cp];
              if (iS == 0) continue;
              for (int np = 0; np < nParentStates; np++) {
                double pS = scores[cp][np];
                scoresToAdd[np] += iS * pS;
                somethingChanged = true;
              }
            }
            if (!somethingChanged) continue;
            boolean firstTime = false;
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
	          }
            for (int np = 0; np < nParentStates; np++) {
              if (firstTime) {
              	iScore[start][end][pState][np] = scoresToAdd[np];
              }
	            else if (scoresToAdd[np] > 0) {
	            	iScore[start][end][pState][np] += scoresToAdd[np];
	            }
            }
            //iScale[start][end][pState] = currentScale;
            iScale[start][end][pState] = scaleArray(iScore[start][end][pState],iScale[start][end][pState]);
            if (firstTime){
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

  /** Fills in the oScore array of each category over each span
   *  of length 2 or more. This version computes the posterior
   *  outside scores, not the Viterbi outside scores.
   */
  
  void doConstrainedOutsideScores() {
    for (int diff = length; diff >= 1; diff--) {
      for (int start = 0; start + diff <= length; start++) {
        int end = start + diff;
        // do unaries
        List<Integer> possibleParentSt = null;
        if (noConstrains){
        	possibleParentSt = new ArrayList<Integer>();
        	for (int i=0; i<numSubStatesArray.length; i++){
        		possibleParentSt.add(i);
        	}
        }
        else{
        	possibleParentSt = possibleStates[start][end];
        }
        
        //List<Integer> possibleParentSt = new ArrayList<Integer>(); for (int i = 0; i<numStates; i++){possibleParentSt.add(i); }
        for (int pState : possibleParentSt){
          if (oScore[start][end][pState] == null) { continue; }
          
          // Should be: Closure under sum-product:
          UnaryRule[] rules = grammar.getClosedSumUnaryRulesByParent(pState);
          //UnaryRule[] rules = grammar.getClosedViterbiUnaryRulesByParent(pState);
          // For now:
          //UnaryRule[] rules = grammar.getUnaryRulesByParent(pState).toArray(new UnaryRule[0]);
          for (int r = 0; r < rules.length; r++) {
            UnaryRule ur = rules[r];
            int cState = ur.childState;
            if (oScore[start][end][cState] == null) { continue; }
            if ((pState == cState)) continue;// && (np == cp))continue;
            
            double[][] scores = ur.getScores2();
            int nParentStates = numSubStatesArray[pState];
            int nChildStates = scores.length;
            Arrays.fill(scoresToAdd,0.0);
            boolean somethingChanged = false;
            for (int cp = 0; cp < nChildStates; cp++) {
              if (scores[cp]==null) continue;
              for (int np = 0; np < nParentStates; np++) {
                double oS = oScore[start][end][pState][np];
                if (oS == 0) continue;
                double pS = scores[cp][np];
                if (pS == 0) continue;
                double tot = oS * pS;
                scoresToAdd[cp] += tot;
                somethingChanged = true;
              }
            }
            if (!somethingChanged) continue;
            // check first whether there was a change at all
            boolean firstTime = false;
            int currentScale = oScale[start][end][pState];
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
	          }
            for (int cp=0; cp<nChildStates; cp++){
              if (firstTime) oScore[start][end][cState][cp] = scoresToAdd[cp];
              else if (scoresToAdd[cp] > 0) oScore[start][end][cState][cp] += scoresToAdd[cp];
            }
            //oScale[start][end][cState] = currentScale;
            oScale[start][end][cState] = scaleArray(oScore[start][end][cState],oScale[start][end][cState]);
          }
        }
        // do binaries
        //for (int lState = 0; lState < numStates; lState++) {
        for (int pState : possibleParentSt) {
        	if (oScore[start][end][pState] == null) { continue; }
          BinaryRule[] rules = grammar.splitRulesWithP(pState);
          
          //BinaryRule[] rules = grammar.splitRulesWithLC(lState);
          for (int r = 0; r < rules.length; r++) {
            BinaryRule br = rules[r];
            int lState = br.leftChildState;
            int min1 = narrowRExtent[start][lState];
            if (end < min1) { continue; }
            
            if (oScore[start][end][pState]==null) {continue;}
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
            int nParentChildStates = numSubStatesArray[pState];
            int nLeftChildStates = numSubStatesArray[lState];
            int nRightChildStates = numSubStatesArray[rState];
            for (int split = min; split <= max; split++) {
              if (oScore[start][split][lState] == null) continue;
              if (oScore[split][end][rState] == null) continue;
              Arrays.fill(scoresToAdd,0.0);
              boolean somethingChanged = false;
              double[] rightScores = new double[nRightChildStates];
              for (int lp=0; lp<nLeftChildStates; lp++){
              	double lS = iScore[start][split][lState][lp];
                if (lS == 0) { continue; }
              	for (int rp=0; rp<nRightChildStates; rp++){
                  if (scores[lp][rp]==null) continue;
                  double rS = iScore[split][end][rState][rp];
                  if (rS == 0) { continue; }

                  for (int np=0; np<nParentChildStates; np++){
                    double oS = oScore[start][end][pState][np];
                    if (oS == 0) continue;
                    double pS = scores[lp][rp][np];
                    if (pS == 0) continue;
                    scoresToAdd[lp] += pS * rS * oS;
                    rightScores[rp] += pS * lS * oS;
                    somethingChanged = true;
                  }
                }
              }
              if (!somethingChanged) continue;
              boolean firstTime = false;
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
  	          }
              for (int cp=0; cp<nLeftChildStates; cp++){
                if (firstTime) 
                	oScore[start][split][lState][cp] = scoresToAdd[cp];
                else if (scoresToAdd[cp] > 0) oScore[start][split][lState][cp] += scoresToAdd[cp];
              }
              //oScale[start][split][lState] = currentScale;
              oScale[start][split][lState] = scaleArray(oScore[start][split][lState],oScale[start][split][lState]);

              currentScale = parentScale+iScale[start][split][lState];
              firstTime = false;
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
  	          }
              for (int cp=0; cp<nRightChildStates; cp++){
                if (firstTime) oScore[split][end][rState][cp] = rightScores[cp];
                else if (rightScores[cp] > 0) oScore[split][end][rState][cp] += rightScores[cp];
              }
              //oScale[split][end][rState] = currentScale;
              oScale[split][end][rState] = scaleArray(oScore[split][end][rState],oScale[split][end][rState]);
            }
          }
        }
      }
    }
  }
  
  void doConstrainedInsideScores2() {
  	throw new Error("Optimize me first!");
//    for (int diff = 1; diff <= length; diff++) {
//      //System.out.print(diff + " ");
//      for (int start = 0; start < (length - diff + 1); start++) {
//        int end = start + diff;
//        List<Integer> possibleSt = possibleStates[start][end];
//        for (int pState : possibleSt) {
//          BinaryRule[] parentRules = grammar.splitRulesWithP(pState);
//          for (int i = 0; i < parentRules.length; i++) {
//            BinaryRule r = parentRules[i];
//            int lState = r.leftChildState;
//            int rState = r.rightChildState;
//
//            int narrowR = narrowRExtent[start][lState];
//            boolean iPossibleL = (narrowR < end); // can this left constituent leave space for a right constituent?
//            if (!iPossibleL) { continue; }
//            
//            int narrowL = narrowLExtent[end][rState];
//            boolean iPossibleR = (narrowL >= narrowR); // can this right constituent fit next to the left constituent?
//            if (!iPossibleR) { continue; }
//            
//            int min1 = narrowR;
//            int min2 = wideLExtent[end][rState];
//            int min = (min1 > min2 ? min1 : min2); // can this right constituent stretch far enough to reach the left constituent?
//            if (min > narrowL) { continue; }
//            
//            int max1 = wideRExtent[start][lState];
//            int max2 = narrowL;
//            int max = (max1 < max2 ? max1 : max2); // can this left constituent stretch far enough to reach the right constituent?
//            if (min > max) { continue; }
//            
//            double[][][] scores = r.getScores2();
//            int nLeftChildStates = scores.length;
//            int nRightChildStates = scores[0].length;
//            double[] binaryScoresToAdd = new double[nLeftChildStates*nRightChildStates*(max-min+1)+1]; // Could be inside the for(np) loop
//            for (int np = 0; np < scores[0][0].length; np++) {
//              Arrays.fill(binaryScoresToAdd, Double.NEGATIVE_INFINITY);
//              double oldIScore = iScore[start][end][pState][np];
//              binaryScoresToAdd[binaryScoresToAdd.length-1] = oldIScore;
//              for (int split = min; split <= max; split++) {
//                if (iScore[start][split][lState] == null) continue;
//                if (iScore[split][end][rState] == null) continue;
//                
//                for (int lp = 0; lp < nLeftChildStates; lp++) {
//                  double lS = iScore[start][split][lState][lp];
//                  if (lS == Double.NEGATIVE_INFINITY) continue;
//                  
//                  for (int rp = 0; rp < nRightChildStates; rp++) {
//                    double rS = iScore[split][end][rState][rp];
//                    if (rS == Double.NEGATIVE_INFINITY) continue;
//                    if (scores[lp][rp]==null) continue;
//                    double pS = scores[lp][rp][np];
//                    binaryScoresToAdd[(split-min)*nLeftChildStates*nRightChildStates
//                                      + lp*nRightChildStates + rp] = pS + lS + rS;
//                  }
//                }
//              }
//              double newIScore = SloppyMath.logAdd(binaryScoresToAdd);
//              if (newIScore > oldIScore) { // tests that we have added a non-null contribution to the score
//                // than previous
//                iScore[start][end][pState][np] = newIScore;
//                if (oldIScore == Double.NEGATIVE_INFINITY) {
//                  if (start > narrowLExtent[end][pState]) {
//                    narrowLExtent[end][pState] = start;
//                    wideLExtent[end][pState] = start;
//                  } else {
//                    if (start < wideLExtent[end][pState]) {
//                      wideLExtent[end][pState] = start;
//                    }
//                  }
//                  if (end < narrowRExtent[start][pState]) {
//                    narrowRExtent[start][pState] = end;
//                    wideRExtent[start][pState] = end;
//                  } else {
//                    if (end > wideRExtent[start][pState]) {
//                      wideRExtent[start][pState] = end;
//                    }
//                  }
//                }
//              }
//            }
//          }
//        }
//        double[][] iScore2 = grammar.matrixVectorPreMultiply(iScore[start][end],
//            grammar.getSumProductClosedUnaryRulesByParent(), possibleSt);
//        // Workaround for the Part-of-Speech problem (bug?)
//        for ( int state = 0; state < grammar.numStates; state++ ) {
//          if ( iScore[start][end][state] != null ) {
//            if ( iScore2[state] == null ) {
//              iScore2[state] = iScore[start][end][state];
//            }
//          }
//        }
//        for_each_possible_state:
//        for ( int pState : possibleSt ){
//          for ( int np = 0; np < grammar.numSubStates[pState]; np++ ) {
//            if ( iScore[start][end][pState][np] > Double.NEGATIVE_INFINITY ) {
//              continue for_each_possible_state;
//            }
//          }
//          // All substates of pState had iScore equal to -Inf
//          for ( int np = 0; np < grammar.numSubStates[pState]; np++ ) {
//            if ( iScore2[pState][np] > Double.NEGATIVE_INFINITY ) {
//              if (start > narrowLExtent[end][pState]) {
//                narrowLExtent[end][pState] = start;
//                wideLExtent[end][pState] = start;
//              } else {
//                if (start < wideLExtent[end][pState]) {
//                  wideLExtent[end][pState] = start;
//                }
//              }
//              if (end < narrowRExtent[start][pState]) {
//                narrowRExtent[start][pState] = end;
//                wideRExtent[start][pState] = end;
//              } else {
//                if (end > wideRExtent[start][pState]) {
//                  wideRExtent[start][pState] = end;
//                }
//              }
//              break;
//            }
//          }
//        }
//        iScore[start][end] = iScore2;
//      }
//    }
  }

  /** Fills in the oScore array of each category over each span
   *  of length 2 or more. This version computes the posterior
   *  outside scores, not the Viterbi outside scores.
   */
  
  void doConstrainedOutsideScores2() {
  	throw new Error("Optimize me first!");
// 	
//    for (int diff = length; diff >= 1; diff--) {
//      for (int start = 0; start + diff <= length; start++) {
//        int end = start + diff;
//        // do unaries
//        List<Integer> possibleParentSt = possibleStates[start][end];
//        double[][] oScore2 = grammar.matrixVectorPostMultiply(
//            grammar.getSumProductClosedUnaryRulesByParent(), oScore[start][end], possibleParentSt);
//        // Workaround for the Part-of-Speech problem (bug?)
//        for ( int state = 0; state < grammar.numStates; state++ ) {
//          if ( oScore[start][end][state] != null ) {
//            if ( oScore2[state] == null ) {
//              oScore2[state] = oScore[start][end][state];
//            }
//          }
//        }
//        oScore[start][end] = oScore2;
//        // do binaries
//        for (int lState = 0; lState < numStates; lState++) {
//          int min1 = narrowRExtent[start][lState];
//          if (end < min1) { continue; }
//          
//          BinaryRule[] rules = grammar.splitRulesWithLC(lState);
//          for (int r = 0; r < rules.length; r++) {
//            BinaryRule br = rules[r];
//            
//            if (oScore[start][end][br.parentState]==null) {continue;}
//            int rState = br.rightChildState;
//            int max1 = narrowLExtent[end][rState];
//            if (max1 < min1) { continue; }
//            
//            int min = min1;
//            int max = max1;
//            if (max - min > 2) {
//              int min2 = wideLExtent[end][rState];
//              min = (min1 > min2 ? min1 : min2);
//              if (max1 < min) { continue; }
//              int max2 = wideRExtent[start][lState];
//              max = (max1 < max2 ? max1 : max2);
//              if (max < min) { continue; }
//            }
//            
//            double[][][] scores = br.getScores2();
//            int nParentChildStates = scores[0][0].length;
//            int nLeftChildStates = scores.length;
//            int nRightChildStates = scores[0].length;
//            for (int split = min; split <= max; split++) {
//              if (oScore[start][split][lState] == null) continue;
//              if (oScore[split][end][rState] == null) continue;
//              double[] binaryLScoresToAdd = new double[nParentChildStates*nRightChildStates+1];
//              double[][] binaryRScoresToAdd = new double[nRightChildStates][nParentChildStates*nLeftChildStates+1];
//              ArrayUtil.fill(binaryRScoresToAdd, Double.NEGATIVE_INFINITY);
//              for (int rp=0; rp<nRightChildStates; rp++) {
//                double oldROScore = oScore[split][end][rState][rp];
//                binaryRScoresToAdd[rp][binaryRScoresToAdd[rp].length-1] = oldROScore;
//              }
//              for (int lp=0; lp<nLeftChildStates; lp++){
//                Arrays.fill(binaryLScoresToAdd, Double.NEGATIVE_INFINITY);
//                double oldLOScore = oScore[start][split][lState][lp];
//                binaryLScoresToAdd[binaryLScoresToAdd.length-1] = oldLOScore;
//
//                double lS = iScore[start][split][lState][lp];
//                if (lS == Double.NEGATIVE_INFINITY) { continue; }
//                for (int rp=0; rp<nRightChildStates; rp++){
//                  double rS = iScore[split][end][rState][rp];
//                  if (rS == Double.NEGATIVE_INFINITY) { continue; }
//                  if (scores[lp][rp]==null) continue;
//                  for (int np=0; np<nParentChildStates; np++){
//                    double oS = oScore[start][end][br.parentState][np];
//                    double pS = scores[np][lp][rp];
//                    binaryLScoresToAdd[rp*nParentChildStates + np] = pS + rS + oS;
//                    binaryRScoresToAdd[rp][lp*nParentChildStates + np] = pS + lS + oS;
//                  }
//                }
//                double newOScore = SloppyMath.logAdd(binaryLScoresToAdd);
//                oScore[start][split][lState][lp] = newOScore;
//              }
//              for (int rp=0; rp<nRightChildStates; rp++) {
//                oScore[split][end][rState][rp] = SloppyMath.logAdd(binaryRScoresToAdd[rp]);              
//              }
//            }
//          }
//        }
//      }
//    }
  }
  public int scaleArray(double[] scores, int previousScale){
	  int logScale = 0;
	  double scale = 1.0;
	  double max = DoubleArrays.max(scores);
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
	  if ((max!=0) && DoubleArrays.max(scores)==0){System.out.println("Undeflow when scaling oScores!");}
	  return previousScale + logScale;
  }

  public void scaleArrayToScale(double[] scores, int previousScale, int newScale){
	  int scaleDiff = previousScale-newScale;
	  if (scaleDiff == 0) return; // nothing to do
	  double scale = Math.pow(GrammarTrainer.SCALE,scaleDiff);
	  if (Math.abs(scale)>=8){
	  	// under-/overflow...
	  	Arrays.fill(scores,0.0);
	  	return;
	  }
/*	  if (scale==0) {
	  	//System.out.println("Scaling problem..");
		  for (int i = 0; i < scores.length; i++) {
	      scores[i] = scale;
	    }
		  return;
	  }*/
	  for (int i = 0; i < scores.length; i++) {
      scores[i] *= scale;
    }
  }
  
	void initializeChart(List<String> sentence) {
		// for simplicity the lexicon will store words and tags as strings,
		// while the grammar will be using integers -> Numberer()
		int start = 0;
		int end = start+1;
		for (String word : sentence) {
			end = start+1;
			//for (short tag : lexicon.getAllTags()){
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
				for (int tag : possibleSt){
          if (grammar.isGrammarTag(tag)) continue;
          //System.out.println("Initializing");
				narrowRExtent[start][tag] = end;
				narrowLExtent[end][tag] = start;
				wideRExtent[start][tag] = end;
				wideLExtent[end][tag] = start;
				double[] lexiconScores = lexicon.score(word,(short)tag,start,false,false);
				iScale[start][end][tag] = scaleArray(lexiconScores,0); 
				for (short n=0; n<lexiconScores.length; n++){
					double prob = lexiconScores[n];
					iScore[start][end][tag][n] = prob;
				}
/*				if (start==1){
					System.out.println(word+" +TAG "+(String)tagNumberer.object(tag)+" "+Arrays.toString(lexiconScores));
				}*/
			}
			start++;
		}
	}
	
	protected void createArrays() {
		// zero out some stuff first in case we recently ran out of memory and are reallocating
		clearArrays();
		
		// allocate just the parts of iScore and oScore used (end > start, etc.)
		//    System.out.println("initializing iScore arrays with length " + length + " and numStates " + numStates);
		iScore = new double[length][length + 1][][];
		oScore = new double[length][length + 1][][];
		iScale = new int[length][length + 1][];
		oScale = new int[length][length + 1][];
		for (int start = 0; start < length; start++) { // initialize for all POS tags so that we can use the lexicon
			int end = start+1;
			iScore[start][end] = new double[numStates][];
			oScore[start][end] = new double[numStates][];
			iScale[start][end] = new int[numStates];
			oScale[start][end] = new int[numStates];
			Arrays.fill(iScale[start][end], Integer.MIN_VALUE);
			Arrays.fill(oScale[start][end], Integer.MIN_VALUE);        
			for (int state = 0; state < numStates; state++){
				iScore[start][end][state] = new double[numSubStatesArray[state]];
				oScore[start][end][state] = new double[numSubStatesArray[state]];
			}
		}
		
		for (int start = 0; start < length; start++) {
			for (int end = start + 2; end <= length; end++) {
				iScore[start][end] = new double[numStates][];
				oScore[start][end] = new double[numStates][];
				iScale[start][end] = new int[numStates];
				oScale[start][end] = new int[numStates];
				Arrays.fill(iScale[start][end], Integer.MIN_VALUE);
				Arrays.fill(oScale[start][end], Integer.MIN_VALUE);        
				List<Integer> pStates  = null;
        if (noConstrains){
        	pStates  = new ArrayList<Integer>();
        	for (int i=0; i<numSubStatesArray.length; i++){
        		pStates .add(i);
        	}
        }
        else{
        	pStates  = possibleStates[start][end];
        }

				for (int state : pStates){
					iScore[start][end][state] = new double[numSubStatesArray[state]];
					oScore[start][end][state] = new double[numSubStatesArray[state]];
					Arrays.fill(iScore[start][end][state], 0);
					Arrays.fill(oScore[start][end][state], 0);
				}
        if (start==0 && end==length ) {
          if (pStates.size()==0)
            System.out.println("no states span the entire tree!");
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

		public Tree<String> drawSample(int parentState, int parentSubstate, int start, int end){
			String parentString = (String)tagNumberer.object(parentState);
			if (end-start==1 && !grammar.isGrammarTag(parentState)){
				// we are at the part of speech level and have a POS tag as parent
				Tree<String> word = new Tree<String>("xxx");
				ArrayList<Tree<String>> child = new ArrayList<Tree<String>>();
				child.add(word);
				return new Tree<String>(parentString,child);
			}
			double sampleMass = Math.random()*iScore[start][end][parentState][parentSubstate];
			int sampleScale = iScale[start][end][parentState];
    	BinaryRule[] parentRules = grammar.splitRulesWithP(parentState);
    	int lChild=0, rChild=0, lChildSub=0, rChildSub=0, split=0;
    	double cumMass=0;
    	boolean foundIt = false;
    	binaryRuleSearch:
    	for (int i = 0; i < parentRules.length; i++) {
      	BinaryRule rule = parentRules[i];
      	lChild = rule.leftChildState;
      	rChild = rule.rightChildState;
      	double[][][] ruleScores = rule.getScores2();
      	for (lChildSub=0; lChildSub<ruleScores.length; lChildSub++){
      		for (rChildSub=0; rChildSub<ruleScores[lChildSub].length; rChildSub++){
      			if (ruleScores[lChildSub][rChildSub]==null) continue;
      			for (split=start+1; split<end; split++){
      				if (iScore[start][split][lChild]==null) continue;
      				if (iScore[split][end][rChild]==null) continue;
      				int lScale = iScale[start][split][lChild];
      				double lFactor = (lScale==sampleScale) ? 
      										iScore[start][split][lChild][lChildSub]:
      											iScore[start][split][lChild][lChildSub] * Math.pow(GrammarTrainer.SCALE,lScale-sampleScale);	
      					                             
      				int rScale = iScale[split][end][rChild];
      				double rFactor = (rScale==sampleScale) ? 
      						iScore[split][end][rChild][rChildSub] :
      							iScore[split][end][rChild][rChildSub] * Math.pow(GrammarTrainer.SCALE,lScale-sampleScale);	

      				cumMass += ruleScores[lChildSub][rChildSub][parentSubstate]*lFactor*rFactor;
      					
      				if (cumMass>sampleMass){
      					foundIt=true;
      					break binaryRuleSearch;
      				}
      			}
      		}
      	}
      }
    	if (foundIt){
    		// a binary rule will do
	      Tree<String> lChildTree = drawSample(lChild,lChildSub,start,split);
	      Tree<String> rChildTree = drawSample(rChild,rChildSub,split,end);
				ArrayList<Tree<String>> children = new ArrayList<Tree<String>>();
				children.add(lChildTree);
				children.add(rChildTree);
				return new Tree<String>(parentString,children);
    	}
    	// need to sample a unary rule
    	
      UnaryRule[] unaries = grammar.getClosedSumUnaryRulesByParent(parentState);
      unaryRuleSearch:
      for (int r = 0; r < unaries.length; r++) {
      	UnaryRule rule = unaries[r];
      	lChild = rule.childState;
      	double[][] ruleScores = rule.getScores2();
      	for (lChildSub=0; lChildSub<ruleScores.length; lChildSub++){
    			if (ruleScores[lChildSub]==null) continue;
    			if (iScore[start][end][lChild]==null) continue;
    			int lScale = iScale[start][end][lChild];
  				double lFactor = (lScale==sampleScale) ? 
  										iScore[start][end][lChild][lChildSub]:
  											iScore[start][end][lChild][lChildSub] * Math.pow(GrammarTrainer.SCALE,lScale-sampleScale);	
  				cumMass += ruleScores[lChildSub][parentSubstate]*lFactor;
  				if (cumMass>sampleMass){
  					foundIt=true;
  					break unaryRuleSearch;
  				}
    		}
      }
      if (foundIt){
      	//found a unary
				Tree<String> child = drawSample(lChild,lChildSub,start,end);
				ArrayList<Tree<String>> childs = new ArrayList<Tree<String>>();
				childs.add(child);
				return new Tree<String>(parentString,childs);
      }
      System.err.println("Parent "+parentString+" from "+start+" to "+end+" with sampleSum "+sampleMass+" and iScore "+iScore[start][end][parentState][parentSubstate]);
      System.err.println("This is impossible to sample!");      
			return null;
		}
	
	
  
}
