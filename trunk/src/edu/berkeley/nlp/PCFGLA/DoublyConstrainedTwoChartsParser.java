/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.berkeley.nlp.auxv.ConstrainedInsideOutside;
import edu.berkeley.nlp.auxv.Edge;
import edu.berkeley.nlp.auxv.SuffStat;
import edu.berkeley.nlp.auxv.VectorizedSuffStat;
import edu.berkeley.nlp.discPCFG.Linearizer;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.math.SloppyMath;
import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.ScalingTools;

/**
 * @author petrov
 *
 */
public class DoublyConstrainedTwoChartsParser extends ConstrainedTwoChartsParser implements ConstrainedInsideOutside{

	double logLikelihood;
	
  /**
	 * @param gr
	 * @param lex
	 */
	public DoublyConstrainedTwoChartsParser(Grammar gr, Lexicon lex) {
		super(gr, lex, null);
	}


	public void setConstraints(boolean[][][][] allowed, int l){
		scrubArrays();
		length = (short)l;
		if (allowed!=null) allowedSubStates = allowed;
		else setConstraints(null, false);
	}

	public double doConstrainedInsideOutsideScores(List<StateSet> sentence, boolean[][] allowedBrackets){
  	
//  	boolean[][][][] allowed = null;
  	List<String> posTags = null;
  	boolean viterbi = false;
  	boolean noSmoothing = false;
  	
  	if (sentence.size()!=length){
  		System.out.println("length mismtach");
  		System.exit(0);
  	}
  	
//  	length = (short)sentence.size();
  		  	
  	createArrays();
  	scrubArrays();
  	
    
		initializeChart(sentence,noSmoothing,posTags);
    long start = System.currentTimeMillis();

    doConstrainedInsideScores(viterbi, allowedBrackets); 

    long finish = System.currentTimeMillis();
    
    System.out.println("InsideScores time: " + (finish - start));

    logLikelihood = Math.log(iScorePostU[0][length][0][0])+ (ScalingTools.LOGSCALE*iScale[0][length][0]);
//    if ((10*iScale[0][length][0])!=0)
//    	System.out.println("scale "+iScale[0][length][0]);
//  	System.out.println("Found a parse for sentence with length "+length+". The LL is "+logLikelihood+".");
    
    oScorePreU[0][length][0][0] = 1.0;
    oScale[0][length][0] = 0;
    doConstrainedOutsideScores(viterbi, allowedBrackets);
    return logLikelihood;
  }
  
  
  int nEdges = 0;
  int nConstituents = 0;
  int skip1 = 0, skip2 = 0;
  int tried=0;
  void doConstrainedInsideScores(final boolean viterbi, boolean[][] allowedBrackets) {
    double initVal = 0;
    nEdges=0;
    nConstituents=0;
    skip1=0; skip2=0; tried=0;
  	//int smallestScale = 10, largestScale = -10;
      for (int diff = 1; diff <= length; diff++) {
    	//smallestScale = 10; largestScale = -10;
      //System.out.print(diff + " ");
      for (int start = 0; start < (length - diff + 1); start++) {
        int end = start + diff;
        if (!allowedBrackets[start][end]) { skip1++; continue;}
        tried++;
      	for (int pState=0; pState<numSubStatesArray.length; pState++){
        	if (diff==1) continue; // there are no binary rules that span over 1 symbol only
        	if (allowedSubStates[start][end][pState]==null) continue;
        	BinaryRule[] parentRules = grammar.splitRulesWithP(pState);
          int nParentStates = numSubStatesArray[pState];
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

//            int min = start+1; int max = end-1;
            // TODO switch order of loops for efficiency
            double[][][] scores = r.getScores2();
            int nLeftChildStates = numSubStatesArray[lState];
            int nRightChildStates = numSubStatesArray[rState];

	        	for (int split = min; split <= max; split++) {
	      			boolean changeThisRound = false;
	            if (!allowedBrackets[start][split]) { skip2++; continue; }
	            if (!allowedBrackets[split][end]) { skip2++; continue; }
	        		if (allowedSubStates[start][split][lState] == null) continue;
	      			if (allowedSubStates[split][end][rState] == null) continue;

	            for (int lp = 0; lp < nLeftChildStates; lp++) {
	        			double lS = iScorePostU[start][split][lState][lp];
	        			if (lS == initVal) continue;
	            	
	        			for (int rp = 0; rp < nRightChildStates; rp++) {
	            		if (scores[lp][rp]==null) continue;
	          			double rS = iScorePostU[split][end][rState][rp];
	          			if (rS == initVal) continue;

	          			for (int np = 0; np < nParentStates; np++) {
	          				if (!allowedSubStates[start][end][pState][np]) continue;
            				double pS = scores[lp][rp][np];
            				if (pS == initVal) continue;
		
            				double thisRound = pS*lS*rS;

            				if (viterbi){
            					unscaledScoresToAdd[np] = Math.max(unscaledScoresToAdd[np],thisRound);
            				} else {
            					unscaledScoresToAdd[np] += thisRound;
            				}
            				nEdges++;
            				
            				somethingChanged = true;
            				changeThisRound = true;
            			}
            		}
            	}
	            if (!changeThisRound) continue;
		          //boolean firstTime = false;
		        	int parentScale = iScale[start][end][pState];
		          int currentScale = iScale[start][split][lState]+iScale[split][end][rState];
		          currentScale = ScalingTools.scaleArray(unscaledScoresToAdd,currentScale);

		          if (parentScale!=currentScale) {
  	          	if (parentScale==Integer.MIN_VALUE){ // first time to build this span
  	          		iScale[start][end][pState] = currentScale;
  	          	} else {
		          		int newScale = Math.max(currentScale,parentScale);
		          		ScalingTools.scaleArrayToScale(unscaledScoresToAdd,currentScale,newScale);
		          		ScalingTools.scaleArrayToScale(iScorePreU[start][end][pState],parentScale,newScale);
		          		iScale[start][end][pState] = newScale;
  	          	}
		          }
        			for (int np = 0; np < nParentStates; np++) {
        				if (viterbi){
        					iScorePreU[start][end][pState][np] = Math.max(iScorePreU[start][end][pState][np],unscaledScoresToAdd[np]);
        				} else {
        					iScorePreU[start][end][pState][np] += unscaledScoresToAdd[np];
        				}
        			}
        			Arrays.fill(unscaledScoresToAdd,0);
        		}
          }
          if (somethingChanged) {
          	nConstituents++;
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
      	// now do the unaries
      	for (int pState=0; pState<numSubStatesArray.length; pState++){
        	if (allowedSubStates[start][end][pState] == null) continue;
        	if (iScorePreU[start][end][pState] == null) continue;
          // Should be: Closure under sum-product:
          UnaryRule[] unaries = grammar.getClosedSumUnaryRulesByParent(pState);
          //UnaryRule[] unaries = grammar.getUnaryRulesByParent(pState).toArray(new UnaryRule[0]);

          int nParentStates = numSubStatesArray[pState];//scores[0].length;
        	int parentScale = iScale[start][end][pState];
        	int scaleBeforeUnaries = parentScale;
          boolean somethingChanged = false;
          
          for (int r = 0; r < unaries.length; r++) {
          	UnaryRule ur = unaries[r];
            int cState = ur.childState;
            if ((pState == cState)) continue;
            
            if (allowedSubStates[start][end][cState]==null) continue;
            if (iScorePreU[start][end][cState] == null) continue;

            double[][] scores = ur.getScores2();
            boolean changeThisRound = false;
            int nChildStates = numSubStatesArray[cState];//scores[0].length;
            for (int cp = 0; cp < nChildStates; cp++) {
              if (scores[cp]==null) continue;
              double iS = iScorePreU[start][end][cState][cp];
              if (iS == initVal) continue;

              for (int np = 0; np < nParentStates; np++) {
                if (!allowedSubStates[start][end][pState][np]) continue;
                double pS = scores[cp][np];
                if (pS == initVal) continue;

	              double thisRound = iS*pS;

        				if (viterbi){
        					unscaledScoresToAdd[np] = Math.max(unscaledScoresToAdd[np],thisRound);
        				} else {
        					unscaledScoresToAdd[np] += thisRound;
        				}
        				nEdges++;
	              somethingChanged = true;
	              changeThisRound = true;
              }
            }
            if (!changeThisRound) continue;
            nConstituents++;
	          //boolean firstTime = false;
	          int currentScale = iScale[start][end][cState];
	          currentScale = ScalingTools.scaleArray(unscaledScoresToAdd,currentScale);
	          if (parentScale!=currentScale) {
	          	if (parentScale==Integer.MIN_VALUE){ // first time to build this span
	          		parentScale = currentScale;
	          	} else {
	          		int newScale = Math.max(currentScale,parentScale);
	          		ScalingTools.scaleArrayToScale(unscaledScoresToAdd,currentScale,newScale);
	          		ScalingTools.scaleArrayToScale(iScorePostU[start][end][pState],parentScale,newScale);
	          		parentScale = newScale;
	          	}
	          }
      			for (int np = 0; np < nParentStates; np++) {
      				if (viterbi){
      					iScorePostU[start][end][pState][np] = Math.max(iScorePostU[start][end][pState][np],unscaledScoresToAdd[np]);
      				} else {
      					iScorePostU[start][end][pState][np] += unscaledScoresToAdd[np];
      				}
      			}
      			Arrays.fill(unscaledScoresToAdd,0);
          }
          if (somethingChanged){
        		int newScale = Math.max(scaleBeforeUnaries,parentScale);
        		ScalingTools.scaleArrayToScale(iScorePreU[start][end][pState],scaleBeforeUnaries,newScale);
        		ScalingTools.scaleArrayToScale(iScorePostU[start][end][pState],parentScale,newScale);
        		iScale[start][end][pState] = newScale;
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
          // in any case copy/add the scores from before
          for (int np = 0; np < nParentStates; np++) {
          	double val = iScorePreU[start][end][pState][np];
	      	  if (val>0) {
	      	  	if (viterbi){
	      	  		iScorePostU[start][end][pState][np] = Math.max(iScorePostU[start][end][pState][np],val);
	      	  	} else {
	      	  		iScorePostU[start][end][pState][np] += val;
	      	  	}
	      	  }
	      	}  
        }
      }
    }
  }
  
  void doConstrainedOutsideScores(final boolean viterbi, boolean[][] allowedBrackets) {
  	double initVal = 0;
//  	Arrays.fill(scoresToAdd,initVal);
    for (int diff = length; diff >= 1; diff--) {
  		for (int start = 0; start + diff <= length; start++) {
  			int end = start + diff;
        if (!allowedBrackets[start][end]) continue;
  			// do unaries
      	for (int cState=0; cState<numSubStatesArray.length; cState++){
  				if (allowedSubStates[start][end][cState]==null) continue;
  				if (iScorePostU[start][end][cState]==null) continue;
  				// Should be: Closure under sum-product:
//  				UnaryRule[] rules = grammar.getClosedSumUnaryRulesByParent(pState);
  				UnaryRule[] rules = grammar.getClosedSumUnaryRulesByChild(cState);
					//UnaryRule[] rules = grammar.getClosedViterbiUnaryRulesByParent(pState);
  				// For now:
  				//UnaryRule[] rules = grammar.getUnaryRulesByChild(cState).toArray(new UnaryRule[0]);
					int nChildStates = numSubStatesArray[cState];
					boolean somethingChanged = false;
        	int childScale = oScale[start][end][cState];
        	int scaleBeforeUnaries = childScale;

  				for (int r = 0; r < rules.length; r++) {
  					UnaryRule ur = rules[r];
  					int pState = ur.parentState;
  					if ((pState == cState)) continue;
  					if (allowedSubStates[start][end][pState]==null) continue;
  					if (iScorePostU[start][end][pState]==null) continue;

  					int nParentStates = numSubStatesArray[pState];

  					double[][] scores = ur.getScores2();
  					boolean changeThisRound = false;
  					for (int cp = 0; cp < nChildStates; cp++) {
  						if (scores[cp]==null) continue;
  						if (!allowedSubStates[start][end][cState][cp]) continue;
  						for (int np = 0; np < nParentStates; np++) {
  							if (!allowedSubStates[start][end][pState][np]) continue;
  							double pS = scores[cp][np];
  							if (pS == initVal) continue;

  							double oS = oScorePreU[start][end][pState][np];
  							if (oS == initVal) continue;

  							double thisRound = oS*pS;

        				if (viterbi){
        					unscaledScoresToAdd[cp] = Math.max(unscaledScoresToAdd[cp],thisRound);
        				} else {
        					unscaledScoresToAdd[cp] += thisRound;
        				}
  							
  							somethingChanged = true;
  							changeThisRound = true;
  						}
  					}
						if (!changeThisRound) continue;
		        int currentScale = oScale[start][end][pState];
		        currentScale = ScalingTools.scaleArray(unscaledScoresToAdd,currentScale);
		        if (childScale!=currentScale) {
		        	if (childScale==Integer.MIN_VALUE){ // first time to build this span
		        		childScale = currentScale;
		        	} else {
		        		int newScale = Math.max(currentScale,childScale);
		        		ScalingTools.scaleArrayToScale(unscaledScoresToAdd,currentScale,newScale);
		        		ScalingTools.scaleArrayToScale(oScorePostU[start][end][cState],childScale,newScale);
		        		childScale = newScale;
		        	}
		        }
		  			for (int cp = 0; cp < nChildStates; cp++) {
      				if (viterbi){
      					oScorePostU[start][end][cState][cp] = Math.max(oScorePostU[start][end][cState][cp],unscaledScoresToAdd[cp]);
      				} else {
      					oScorePostU[start][end][cState][cp] += unscaledScoresToAdd[cp];
      				}
		  			}
		        Arrays.fill(unscaledScoresToAdd,initVal);
  				}
	        if (somethingChanged){
	      		int newScale = Math.max(scaleBeforeUnaries,childScale);
	      		ScalingTools.scaleArrayToScale(oScorePreU[start][end][cState],scaleBeforeUnaries,newScale);
	      		ScalingTools.scaleArrayToScale(oScorePostU[start][end][cState],childScale,newScale);
	      		oScale[start][end][cState] = newScale;
	        }
	  			// copy/add the entries where the unaries where not useful
  				for (int cp=0; cp<nChildStates; cp++){
		        double val = oScorePreU[start][end][cState][cp];
  					if (val>0) {
  						if (viterbi){
  							oScorePostU[start][end][cState][cp] = Math.max(oScorePostU[start][end][cState][cp], val);
  						} else {
  							oScorePostU[start][end][cState][cp] += val;
  						}
  					}
        	}
				}
  			
  			
  			// do binaries
      	if (diff==1) continue; // there is no space for a binary
      	for (int pState=0; pState<numSubStatesArray.length; pState++){
          if (allowedSubStates[start][end][pState] == null) continue;
        	final int nParentChildStates = numSubStatesArray[pState];
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
    	        if (!allowedBrackets[start][split]) continue;
    	        if (!allowedBrackets[split][end]) continue;
    	        if (allowedSubStates[start][split][lState] == null) continue;
              if (allowedSubStates[split][end][rState] == null) continue;

              boolean somethingChanged = false;
              for (int lp=0; lp<nLeftChildStates; lp++){
              	double lS = iScorePostU[start][split][lState][lp];
//              	if (lS==0) continue;
              	
              	for (int rp=0; rp<nRightChildStates; rp++){
                  if (scores[lp][rp]==null) continue;
                  double rS = iScorePostU[split][end][rState][rp];
//                  if (rS==0) continue;
                	
                  for (int np=0; np<nParentChildStates; np++){
                    double pS = scores[lp][rp][np];
                    if (pS == initVal) continue;

                    double oS = oScorePostU[start][end][pState][np];
                    if (oS == initVal) continue;
//                    if (!allowedSubStates[start][end][pState][np]) continue;

                    double thisRoundL = pS*rS*oS;
                    double thisRoundR = pS*lS*oS;
                    
            				if (viterbi){
            					scoresToAdd[lp] = Math.max(scoresToAdd[lp],thisRoundL);
            					unscaledScoresToAdd[rp] = Math.max(unscaledScoresToAdd[rp],thisRoundR);
            				} else {
            					scoresToAdd[lp] += thisRoundL; 
            					unscaledScoresToAdd[rp] += thisRoundR;
            				}
                    somethingChanged = true;
                  }
                }
              }
              if (!somethingChanged) continue;

              if (DoubleArrays.max(scoresToAdd)!=0){//oScale[start][end][pState]!=Integer.MIN_VALUE && iScale[split][end][rState]!=Integer.MIN_VALUE){
	              int leftScale = oScale[start][split][lState];
	              int currentScale = oScale[start][end][pState]+iScale[split][end][rState];
	              currentScale = ScalingTools.scaleArray(scoresToAdd,currentScale);
	  	          if (leftScale!=currentScale) {
	  	          	if (leftScale==Integer.MIN_VALUE){ // first time to build this span
	  	          		oScale[start][split][lState] = currentScale;
	  	          	} else {
	  	          	  int newScale = Math.max(currentScale,leftScale);
		          		  ScalingTools.scaleArrayToScale(scoresToAdd,currentScale,newScale);
		          		  ScalingTools.scaleArrayToScale(oScorePreU[start][split][lState],leftScale,newScale);
		          		  oScale[start][split][lState] = newScale;
	  	          	}
	  	          }
	              for (int cp=0; cp<nLeftChildStates; cp++){
	                if (scoresToAdd[cp] > initVal){
            				if (viterbi){
            					oScorePreU[start][split][lState][cp] = Math.max(oScorePreU[start][split][lState][cp],scoresToAdd[cp]);
            				} else {
            					oScorePreU[start][split][lState][cp] += scoresToAdd[cp];
            				}
	                }
	              }
	              Arrays.fill(scoresToAdd, 0);
              }
              
              if (DoubleArrays.max(unscaledScoresToAdd)!=0){//oScale[start][end][pState]!=Integer.MIN_VALUE && iScale[start][split][lState]!=Integer.MIN_VALUE){
	              int rightScale = oScale[split][end][rState];
	              int currentScale = oScale[start][end][pState]+iScale[start][split][lState];
	              currentScale = ScalingTools.scaleArray(unscaledScoresToAdd,currentScale);
	  	          if (rightScale!=currentScale) {
	  	          	if (rightScale==Integer.MIN_VALUE){ // first time to build this span
	  	          		oScale[split][end][rState] = currentScale;
	  	          	} else {
			          		int newScale = Math.max(currentScale,rightScale);
			          		ScalingTools.scaleArrayToScale(unscaledScoresToAdd,currentScale,newScale);
			          		ScalingTools.scaleArrayToScale(oScorePreU[split][end][rState],rightScale,newScale);
			          		oScale[split][end][rState] = newScale;
	  	          	}
	  	          }
	              for (int cp=0; cp<nRightChildStates; cp++){
	              	if (unscaledScoresToAdd[cp] > initVal) {
            				if (viterbi){
            					oScorePreU[split][end][rState][cp] = Math.max(oScorePreU[split][end][rState][cp],unscaledScoresToAdd[cp]);
            				} else {
            					oScorePreU[split][end][rState][cp] += unscaledScoresToAdd[cp];
            				}
	              	}
	              }
	              Arrays.fill(unscaledScoresToAdd, 0);
            	}	
            }
          }
        }
      }
    }
  }
  
	protected List<StateSet> convertToTestSet(List<String> testSentence) {
		ArrayList<StateSet> list = new ArrayList<StateSet>(testSentence.size());
		short ind = 0;
		for (String word : testSentence){
	  	StateSet stateSet = new StateSet((short)-1, (short)1, word, ind, (short)(ind+1));
	  	ind++;
	  	stateSet.wordIndex = -2;
	  	stateSet.sigIndex = -1;
			list.add(stateSet);
		}
		return list;
	}

 


	public boolean compute(List<String> sentence, SuffStat suffStat) {
		boolean[][] spanAllowed = new boolean[sentence.size()][sentence.size()+1];
		ArrayUtil.fill(spanAllowed,true);
    return compute(sentence, suffStat, spanAllowed);
	}



	/* 
	 * works currently only for unsplit (x-bar) grammars
	 */
	public boolean compute(List<String> sentence, SuffStat suffStat, boolean[][] spanAllowed) {
		if (!(suffStat instanceof VectorizedSuffStat)) new Error ("Can only compute VectorizedSuffStat.");
		int total = 0, on=0;
		for (int a=0; a<spanAllowed.length; a++){
			for (int b=a+1; b<spanAllowed[0].length; b++){
				if (spanAllowed[a][b]) on++;
				total++;
			}
		}
		System.out.println(on+"/"+total+" brackets are on.");
		VectorizedSuffStat v_suffStat = (VectorizedSuffStat) suffStat;
		List<StateSet> stateSetSentence = convertToTestSet(sentence);
		doConstrainedInsideOutsideScores(stateSetSentence, spanAllowed);
		double tree_score = iScorePostU[0][length][0][0];
		if (SloppyMath.isDangerous(tree_score)) return false;
		incrementExpectedCounts(stateSetSentence, v_suffStat, spanAllowed);
		suffStat = v_suffStat;
		System.out.println("Touched "+nEdges+" edges and "+nConstituents+" constituents. Skipped "+skip1+" and "+skip2+". Tried "+tried);
		return true;
	}
	
	public static class UnderflowException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		public UnderflowException() { 
			super("Training tree has zero probability" +
				" - presumably underflow!"); 
			}
	}
		
	public void incrementExpectedCounts(List<StateSet> sentence, VectorizedSuffStat suffStat, boolean[][] allowedBrackets) {
  	double tree_score = iScorePostU[0][length][0][0];
  	int tree_scale = iScale[0][length][0];
  	if (SloppyMath.isDangerous(tree_score)){
  		throw new UnderflowException();
  	}

		for (int start = 0; start < length; start++) {
			final int lastState = numSubStatesArray.length;
			StateSet currentStateSet = sentence.get(start);

			for (int tag=0; tag<lastState; tag++){
    		if (grammar.isGrammarTag(tag)) continue;
      	if (allowedSubStates[start][start+1][tag] == null) continue;
  			double scalingFactor = ScalingTools.calcScaleFactor(
  					oScale[start][start+1][tag]+
  					iScale[start][start+1][tag]-
  					tree_scale);
		  	if (scalingFactor==0){
		  		continue;
		  	}

		  	final int nSubStates = numSubStatesArray[tag];
//	      if (!combinedLexicon){
			  	for (short substate=0; substate<nSubStates; substate++) {
	          //weight by the probability of seeing the tag and word together, given the sentence
		        double iS = iScorePreU[start][start+1][tag][substate];
		        if (iS==0) continue;
		        double oS = oScorePostU[start][start+1][tag][substate];
		        if (oS==0) continue;
		        double weight = iS / tree_score * scalingFactor * oS;
		        if (weight>1.01){
		        	System.out.println("overflow when counting tags? "+weight);
		        	weight = 0;
		        }
	          tmpCountsArray[substate] = weight; 
		      }
			  	suffStat.inc(currentStateSet, tag, tmpCountsArray); //probs[startIndexWord+substate] += weight;
			}
		}
	
  	
  	for (int diff = 1; diff <= length; diff++) {
			for (int start = 0; start < (length - diff + 1); start++) {
				int end = start + diff;
        if (!allowedBrackets[start][end]) continue;

				final int lastState = numSubStatesArray.length;
      	for (short pState=0; pState<lastState; pState++){
        	if (diff==1) continue; // there are no binary rules that span over 1 symbol only
        	if (allowedSubStates[start][end][pState] == null) continue;
					final int nParentSubStates = numSubStatesArray[pState];
					BinaryRule[] parentRules = grammar.splitRulesWithP(pState);
					for (int i = 0; i < parentRules.length; i++) {
						BinaryRule r = parentRules[i];
						short lState = r.leftChildState;
						short rState = r.rightChildState;
						
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
						
						double[][][] scores = r.getScores2();
						boolean foundSomething = false;

						for (int split = min; split <= max; split++) {
							if (!allowedBrackets[start][split]) continue;
							if (!allowedBrackets[split][end]) continue;

							if (allowedSubStates[start][split][lState] == null) continue;
							if (allowedSubStates[split][end][rState] == null) continue;
							double scalingFactor = ScalingTools.calcScaleFactor(
									oScale[start][end][pState]+
									iScale[start][split][lState]+
									iScale[split][end][rState]-tree_scale);
					  	
							if (scalingFactor==0){ continue; }

							int curInd = 0;
							for (int lp = 0; lp < scores.length; lp++) {
          			double lcIS = iScorePostU[start][split][lState][lp];
								if (lcIS == 0) { curInd += scores[0].length * nParentSubStates; continue; }

								double tmpA = lcIS / tree_score;
								
								for (int rp = 0; rp < scores[0].length; rp++) {
									if (scores[lp][rp]==null) continue;
									double rcIS = iScorePostU[split][end][rState][rp];
									
									if (rcIS == 0) { curInd += nParentSubStates; continue; }
			        		
									double tmpB = tmpA * rcIS * scalingFactor;
									
									for (int np = 0; np < nParentSubStates; np++) {
										double pOS = oScorePostU[start][end][pState][np];
										if (pOS==0) { curInd++; continue; }
										
										double rS = scores[lp][rp][np];
										double ruleCount = rS * tmpB * pOS;
										
										if (ruleCount==0) { curInd++; continue; }
										else if (ruleCount>1.01){
						        	System.out.println("overflow when counting binary rules? "+ruleCount);
						        	ruleCount=0;
						        }

										tmpCountsArray[curInd++] += ruleCount;
										foundSomething = true;
									}
								}
							}
						}
						if (!foundSomething) continue; // nothing changed this round
						suffStat.inc(r, tmpCountsArray);
					}
				}
      	final int lastStateU = numSubStatesArray.length;
      	for (short pState=0; pState<lastStateU; pState++){
      		if (allowedSubStates[start][end][pState] == null) continue;

//					List<UnaryRule> unaries = grammar.getUnaryRulesByParent(pState);
					int nParentSubStates = numSubStatesArray[pState];
          UnaryRule[] unaries = grammar.getClosedSumUnaryRulesByParent(pState);
					for (UnaryRule ur : unaries) {
						short cState = ur.childState;
						if ((pState == cState)) continue;// && (np == cp))continue;
						if (allowedSubStates[start][end][cState] == null) continue;
	    			double scalingFactor = ScalingTools.calcScaleFactor(
	    					oScale[start][end][pState]+iScale[start][end][cState]-tree_scale);
				  	
	    			if (scalingFactor==0){ continue; }

						double[][] scores = ur.getScores2();

						int curInd = 0;
						for (int cp = 0; cp < scores.length; cp++) {
							if (scores[cp]==null) continue; 
							double cIS = iScorePreU[start][end][cState][cp];

							if (cIS == 0) { curInd += nParentSubStates; continue; }

							double tmpA = cIS / tree_score * scalingFactor;
							
							for (int np = 0; np < nParentSubStates; np++) {
								double pOS = oScorePreU[start][end][pState][np];
								if (pOS==0){ curInd++; continue; }

								double rS = scores[cp][np];

								double ruleCount = rS * tmpA * pOS;
								if (ruleCount==0) { curInd++; continue; }
								else if (ruleCount>1.01){
				        	System.out.println("overflow when counting unary rules? "+ruleCount);
				        	ruleCount=0;
				        }

								tmpCountsArray[curInd++] = ruleCount; 
							}
						}
						suffStat.inc(ur, tmpCountsArray); //probs[thisStartIndex + curInd-1] += ruleCount;
					}
      	}
			}
		}
	}



	/* 
	 * returns the log-probability of the last parsed sentence (to prevent underflow) 
	 */
	public double logSentencePr() {
		return logLikelihood;
	}



	/* 
	 * Posterior probability of binary production
	 */
	public double stateSetPosterior(StateSet parent, StateSet left, StateSet right) {
		int start = parent.from;
		int end = parent.to;
		int split = left.to;
		int pState = parent.getState();
		int lState = left.getState();
		int rState = right.getState();
		
		double tree_score = iScorePostU[0][length][0][0];
//		System.out.println("tree_score:" + tree_score);

   	double scalingFactor = ScalingTools.calcScaleFactor(
				oScale[start][end][pState]+iScale[start][split][lState]+iScale[split][end][rState]-iScale[0][length][0]);
		if (SloppyMath.isDangerous(scalingFactor)) return 0;
		
		double oS = oScorePostU[start][end][pState][0];
		double lS = iScorePostU[start][split][lState][0];
		double rS = iScorePostU[split][end][rState][0];

//		System.out.println(oS + "," + lS + "," + rS + "," + scalingFactor);
		
   	double posterior = lS / tree_score * rS * scalingFactor * oS;
  	// zeros are fine, we will average many of them in auxv
//  	if (SloppyMath.isDangerous(posterior)){
//  		System.out.println("Dangerous binary posterior: "+posterior);
//  		posterior = 0;
//  	}
   	
   	
  	return posterior;
	}



	/* 
	 * Posterior probability of unary production
	 */
	public double stateSetPosterior(StateSet parent, StateSet child) {
		int start = parent.from;
		int end = parent.to;
		int pState = parent.getState();
		int cState = child.getState();
		
		double scalingFactor = ScalingTools.calcScaleFactor(
				oScale[start][end][pState]+iScale[start][end][cState]-iScale[0][length][0]);
		if (SloppyMath.isDangerous(scalingFactor)) return 0;

		double tree_score = iScorePostU[0][length][0][0];
		
		double iS = iScorePreU[start][end][cState][0];
		double oS = oScorePreU[start][end][pState][0];

   	double posterior = iS / tree_score * oS * scalingFactor;
   	
  	// zeros are fine, we will average many of them in auxv
//  	if (SloppyMath.isDangerous(posterior)){
//  		System.out.println("Dangerous unary posterior: "+posterior);
//  		posterior = 0;
//  	}

  	return posterior;
	}



	/* 
	 * Node posterior
	 */
	public double stateSetPosterior(StateSet stateSet) {
		int start = stateSet.from;
		int end = stateSet.to;
		int state = stateSet.getState();
		
		double scalingFactor = ScalingTools.calcScaleFactor(
				oScale[start][end][state]+iScale[start][end][state]-iScale[0][length][0]);
		if (SloppyMath.isDangerous(scalingFactor)) return 0;
		
		double tree_score = iScorePostU[0][length][0][0];
		double iS = iScorePostU[start][end][state][0];
		double oS = oScorePostU[start][end][state][0];

  	double tmp = Math.max(iS*oScorePreU[start][end][state][0], iScorePreU[start][end][state][0]*oS);

  	double posterior = tmp / tree_score * scalingFactor;
  	// zeros are fine, we will average many of them in auxv
//  	if (SloppyMath.isDangerous(posterior)){
//  		System.out.println("Dangerous state posterior: "+posterior);
//  		posterior = 0;
//  	}
  	return posterior;
	}


}
