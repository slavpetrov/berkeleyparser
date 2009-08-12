package edu.berkeley.nlp.HDPPCFG;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.math.SloppyMath;
import edu.berkeley.nlp.parser.EnglishPennTreebankParseEvaluator;
import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.PriorityQueue;

public class ConstrainedNbestMaxRuleSumPosParser extends ConstrainedMaxRuleSumPosParser {
	int N = 1;
	double ID = 0;
	
	// each entry in the priority queue has 4 elements:
	// {split, cChild, lChild, rChild}
	PriorityQueue<double[]> [][][] derivationChart;
	PriorityQueue [][][] nthBestScore;
	int[][][] foundDerivations;
	double[][][] scoreOfWorstDerivation;
	
	
	public ConstrainedNbestMaxRuleSumPosParser(Grammar gr, Lexicon lex, short[] nSub,
			double unaryPenalty, int n) {
		super(gr, lex, nSub, unaryPenalty);
		N = n;
		System.out.println("Will try to compute the best "+N+" parse trees.");
		
	}
	
	/** Assumes that inside and outside scores (sum version, not viterbi) have been computed.
	 *  In particular, the narrowRExtent and other arrays need not be updated.
	 */
	void doConstrainedMaxCScores(List<String> sentence) {
		/*  maxcScore = new double[length][length + 1][numStates];
		 maxcSplit = new int[length][length + 1][numStates];
		 maxcChild      = new int[length][length + 1][numStates];
		 maxcLeftChild  = new int[length][length + 1][numStates];
		 maxcRightChild = new int[length][length + 1][numStates];
		 */  derivationChart = new PriorityQueue[length][length+1][numStates];
		 nthBestScore = new PriorityQueue[length][length+1][numStates];
		 foundDerivations = new int[length][length+1][numStates];
		 scoreOfWorstDerivation = new double[length][length+1][numStates];
		 double logNormalizer = iScore[0][length][0][0];
		 for (int diff = 1; diff <= length; diff++) {
			 //System.out.print(diff + " ");
			 for (int start = 0; start < (length - diff + 1); start++) {
				 int end = start + diff;
				 /*        Arrays.fill(maxcSplit[start][end], -1);
				  Arrays.fill(maxcChild[start][end], -1);
				  Arrays.fill(maxcLeftChild[start][end], -1);
				  Arrays.fill(maxcRightChild[start][end], -1);*/
				 
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
				 for (int pState : possibleSt) {
					 derivationChart[start][end][pState] = new PriorityQueue<double[]>(); 
					 nthBestScore[start][end][pState] = new PriorityQueue();
					 nthBestScore[start][end][pState].add(null,Double.POSITIVE_INFINITY);
				 }
				 if (diff > 1) {
					 // diff > 1: Try binary rules
					 for (int pState : possibleSt) {
					   BinaryRule[] parentRules = grammar.splitRulesWithP(pState);
					   boolean[][] processedRules = new boolean[parentRules.length][end-start];
						 for (int i = 0; i < parentRules.length; i++) {
							 Arrays.fill(processedRules[i],false);
						 }
						 
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
								 boolean somethingChanged = false;
								 double constF = 0;
								 boolean sumOutLeft=false, sumOutRight=false;
								 
								 if (split==start+1 && hasLeftPOSChild) sumOutLeft = true;
								 if (split==end-1 && hasRightPOSChild) sumOutRight = true;
								 
								 boolean[] ruleIndicesToSum = getBinaryRulesToSum(pState,lState,rState,sumOutLeft,sumOutRight,i);
								 somethingChanged = false;
								 double[] gScore = new double[N], bestG=new double[N];
								 int[] bestLChild=new int[N], bestRChild=new int[N];
								 double[] bestLChildID=new double[N], bestRChildID=new double[N];
								 for (int i2 = 0; i2 < parentRules.length; i2++) {
									 if (!ruleIndicesToSum[i2]) continue;
									 if (processedRules[i2][split-start]) {
										 continue;
									 }
									 
									 double ruleScore = 0;
									 r = parentRules[i2];
									 lState = r.leftChildState;
									 rState = r.rightChildState;
									 scores = r.getScores2();
									 nLeftChildStates = numSubStatesArray[lState]; // == scores.length;
									 nRightChildStates = numSubStatesArray[rState]; // == scores[0].length;
									 nParentStates = numSubStatesArray[pState]; // == scores[0][0].length;
									 
									 if (iScore[start][split][lState] == null) continue;
									 if (iScore[split][end][rState] == null) continue;
									 if (foundDerivations[start][split][lState]==0) continue;
									 if (foundDerivations[split][end][rState]==0) continue;
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
									 constF = ruleScore * scale;
									 somethingChanged = true;
									 processedRules[i2][split-start] = true;
									 
									 if (sumOutLeft&&sumOutRight){
										 // when we are summing out both children, then both of them
										 // must be POS tags and therefore can have only one derivation
										 PriorityQueue<double[]> lChildren = derivationChart[start][split][lState];
										 double leftChildScore = lChildren.getPriority();
										 double[] lCa = lChildren.peek();
										 double leftChildID = lCa[4];
										 PriorityQueue<double[]> rChildren = derivationChart[split][end][rState];
										 double rightChildScore = rChildren.getPriority();
										 double[] rCa = rChildren.peek();
										 double rightChildID = rCa[4];
										 double tmp = constF * leftChildScore * rightChildScore;
										 gScore[0] += tmp;
										 if (tmp>bestG[0]){
											 bestG[0] = tmp;
											 bestLChild[0] = lState;
											 bestLChildID[0] = leftChildID;
											 bestRChild[0] = rState;
											 bestRChildID[0] = rightChildID;
										 }
									 }
									 else if (sumOutLeft){
										 // lChild is POS tag and therefore can have only one derivation
										 PriorityQueue<double[]> lChildren = derivationChart[start][split][lState];
										 double leftChildScore = lChildren.getPriority();
										 double[] lCa = lChildren.peek();
										 double leftChildID = lCa[4];
										 double tmp = constF * leftChildScore;
										 PriorityQueue<double[]> rChildren = derivationChart[split][end][rState].clone();
										 int I=0;
										 while (rChildren.hasNext() && I<N){
											 double rightChildScore = rChildren.getPriority();
											 double[] rCa = rChildren.next();
											 double rightChildID = rCa[4];
											 gScore[I] += tmp * rightChildScore;
											 if (tmp>bestG[I]){
												 bestG[I] = tmp;
												 bestLChild[I] = lState;
												 bestLChildID[I] = leftChildID;
												 bestRChild[I] = rState;
												 bestRChildID[I] = rightChildID;
											 }
											 I++;
										 }
									 }
									 else if (sumOutRight){
										 // rChild is POS tag and therefore can have only one derivation
										 PriorityQueue<double[]> rChildren = derivationChart[split][end][rState];
										 double rightChildScore = rChildren.getPriority();
										 double[] rCa = rChildren.peek();
										 double rightChildID = rCa[4];
										 double tmp = constF * rightChildScore;
										 PriorityQueue<double[]> lChildren = derivationChart[start][split][lState].clone();
										 int I=0;
										 while (lChildren.hasNext() && I<N){
											 double leftChildScore = lChildren.getPriority();
											 double[] lCa = lChildren.next();
											 double leftChildID = lCa[4];
											 gScore[I] += tmp * leftChildScore;
											 if (tmp>bestG[I]){
												 bestG[I] = tmp;
												 bestLChild[I] = lState;
												 bestLChildID[I] = leftChildID;
												 bestRChild[I] = rState;
												 bestRChildID[I] = rightChildID;
											 }
											 I++;
										 }
									 }
								 }
								 // we have filled the gScore array with upto N new ways
								 // of constructing this entry, now check how many of them are useful 
								 if (sumOutLeft||sumOutRight){
									 for (int I=0; I<N; I++){
										 if (gScore[I]<=0) break;
										 double scoreToBeat = -1.*nthBestScore[start][end][pState].getPriority();//scoreOfWorstDerivation[start][end][pState]; 
										 if (gScore[I] > scoreToBeat) {
											 double[] entry = {split, -1, bestLChild[I], bestRChild[I], ID++, bestLChildID[I], bestRChildID[I]};
											 derivationChart[start][end][pState].add(entry,gScore[I]);
											 nthBestScore[start][end][pState].add(null,-gScore[I]);
											 foundDerivations[start][end][pState]++;
											 if (foundDerivations[start][end][pState]>N){
												 nthBestScore[start][end][pState].next();
											 }
										 }
										 else{ break; }
									 }
								 }
								 else {
									 //don't want to sum out anything
									 if(!somethingChanged)continue;
									 PriorityQueue<double[]> lChildren = derivationChart[start][split][lState].clone();
									 boolean atLeastOneGood = true;
									 while (lChildren.hasNext()){
										 if (!atLeastOneGood) break;
										 double leftChildScore = lChildren.getPriority();
										 double tmp = constF * leftChildScore;
										 double[] lCa = lChildren.next();
										 double leftChildID = lCa[4];
										 PriorityQueue<double[]> rChildren = derivationChart[split][end][rState].clone();
										 atLeastOneGood = false;
										 while (rChildren.hasNext()){
											 double rightChildScore = rChildren.getPriority();
											 double[] rCa = rChildren.next();
											 double rightChildID = rCa[4];
											 double gScore1 =  tmp * rightChildScore;
											 if (gScore1<=0) continue;
											 double scoreToBeat = -1.*nthBestScore[start][end][pState].getPriority();//scoreOfWorstDerivation[start][end][pState]; 
											 if (gScore1 > scoreToBeat) {
												 atLeastOneGood=true;
												 double[] entry = {split, -1, lState, rState, ID++, leftChildID, rightChildID};
												 derivationChart[start][end][pState].add(entry,gScore1);
												 nthBestScore[start][end][pState].add(null,-gScore1);
												 foundDerivations[start][end][pState]++;
												 if (foundDerivations[start][end][pState]>N){
													 nthBestScore[start][end][pState].next();
													 //scoreOfWorstDerivation[start][end][pState] = derivationChart[start][end][pState].getPriority();
													 //nthBestScore[start][end][pState] = derivationChart[start][end][pState].getPriority();
												 }
											 } else{break;}
										 }
									 }
								 }
							 }
						 }
					 } 
				 }else { // diff == 1
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
						 double score = lexiconScores * scale;
						 double[] entry={-1,-1,-1,-1,-1,-1,-1};
						 derivationChart[start][end][tag].add(entry, score);
						 foundDerivations[start][end][tag]++;
						 scoreOfWorstDerivation[start][end][tag]=score;
						 nthBestScore[start][end][tag].add(null,-score);
					 }
				 }
				 // Try unary rules
				 // Replacement for maxcScore[start][end], which is updated in batch   
				 /*  double[] maxcScoreStartEnd = new double[numStates];
				  for (int i = 0; i < numStates; i++) {
				  maxcScoreStartEnd[i] = maxcScore[start][end][i];
				  }*/
				 PriorityQueue<double[]>[] derivationChartStartEnd = new PriorityQueue[numStates];   
				 for (int i = 0; i < numStates; i++) {
					 if (derivationChart[start][end][i]==null) continue;
					 derivationChartStartEnd[i] = derivationChart[start][end][i].clone();
				 }
				 for (int pState : possibleSt){
					 //UnaryRule[] unaries = grammar.getUnaryRulesByParent(pState).toArray(new UnaryRule[0]);
					 UnaryRule[] unaries = grammar.getClosedSumUnaryRulesByParent(pState);
					 boolean[] processedRules = new boolean[unaries.length];
				   Arrays.fill(processedRules,false);
					 for (int r = 0; r < unaries.length; r++) {
						 UnaryRule ur = unaries[r];

						 int cState = ur.childState;
						 boolean sumOutChild = ((end-start)==1 && !grammar.isGrammarTag(cState));
						 boolean[] ruleIndicesToSum = getUnaryRulesToSum(pState,sumOutChild,r);
						 double[] gScore = new double[N], bestG=new double[N];
						 int[] bestChild=new int[N];
						 double[] bestChildID=new double[N];
						 for (int r2 = 0; r2 < unaries.length; r2++) {
							 if (!ruleIndicesToSum[r2]) continue;
							 if (processedRules[r2]) continue;
							 processedRules[r2] = true; 
							 ur = unaries[r2];
							 cState = ur.childState;
							 
							 if (iScore[start][end][cState]==null) continue;
							 if (foundDerivations[start][end][cState]==0) continue;
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
							 double constF = ruleScore / unaryPenalty *scale;
							 if (sumOutChild){
								 // there is only one way to derive POS tags
								 PriorityQueue<double[]> Children = derivationChart[start][end][cState];
								 double childScore = Children.getPriority();
								 double[] Ca = Children.peek();
								 double childID = Ca[4];
								 double tmp = constF * childScore;
								 gScore[0] += tmp;
								 if (tmp>bestG[0]){
									 bestG[0] = tmp;
									 bestChild[0] = cState;
									 bestChildID[0] = childID;
								 }
							 }
							 else {
								 // do not sum it out
								 PriorityQueue<double[]> Children = derivationChart[start][end][cState].clone();
								 int I=0;
								 while (Children.hasNext() && I<N){
									 double childScore = Children.getPriority();
									 double[] Ca = Children.next();
									 double childID = Ca[4];
									 double tmp = constF * childScore;
									 if (gScore[I]!=0)
									 {
										 System.out.println("GOING TO OVERWRITE STH!!!");
									 }
									 gScore[I] = tmp;
									 bestChild[I] = cState;
									 bestChildID[I] = childID;
									 I++;
								 }
							 }
						 }
						 // we have filled the gScore array with upto N new ways
						 // of constructing this entry, now check how many of them are useful 
						 for (int I=0; I<N; I++){
							 if (gScore[I]<=0) break;
							 double scoreToBeat = -1.*nthBestScore[start][end][pState].getPriority();
							 if (gScore[I] > scoreToBeat) {
								 double[] entry = {-1,bestChild[I],-1,-1,ID++,bestChildID[I],-1};
								 derivationChartStartEnd[pState].add(entry,gScore[I]);
								 nthBestScore[start][end][pState].add(null,-gScore[I]);
								 foundDerivations[start][end][pState]++;
								 if (foundDerivations[start][end][pState]>N){
									 nthBestScore[start][end][pState].next();
								 }
							 } else{break;}
						 }
					 }
				 }
				 //maxcScore[start][end] = maxcScoreStartEnd;
				 derivationChart[start][end]=derivationChartStartEnd.clone();
			 }
		 }
	}
	
	public Tree<String> extractBestMaxRuleParse(int start, int end, double score, List<String> sentence ) {
		return extractBestMaxRuleParse1(start, end, 0, score,sentence);
	}
	
	/*
	 * Returns the best parse for state "state", potentially starting with a unary rule
	 */
	public Tree<String> extractBestMaxRuleParse1(int start, int end, int state, double score, List<String> sentence ) {
		if (derivationChart[start][end][state]==null || derivationChart[start][end][state].isEmpty()){
			return new Tree<String>("degenerate");
		}
		//System.out.println("Looking for tree with score "+score);
		//double myScore = derivationChart[start][end][state].getPriority();
		double[] entry = derivationChart[start][end][state].peek();
		double myID = entry[4];
		PriorityQueue<double[]> backup = derivationChart[start][end][state].clone();
		while (myID!=score) {
			
			//System.out.println(" looking for " +score +" have "+myScore);
			entry = derivationChart[start][end][state].next();
			//System.out.println(" pop start " + start + " end "+end +" state "+ state);
			//myScore = derivationChart[start][end][state].getPriority();
			myID = entry[4];//
		}
		derivationChart[start][end][state] = backup;
		if (myID!=score)
			System.out.println("still not equal. start " + start + " end "+end +" state "+ state);
		
		int cState = (int)entry[1];//maxcChild[start][end][state];
		if (cState == -1) {
			return extractBestMaxRuleParse2(start, end, state, score ,sentence);
		} else {
			//if (entry[4]!=-1) // signifies POS tag (end - start != 1) 
			//entry = derivationChart[start][end][state].next();
			List<Tree<String>> child = new ArrayList<Tree<String>>();
			child.add( extractBestMaxRuleParse2(start, end, cState, entry[5], sentence) );
			String stateStr = (String) tagNumberer.object(state);
			totalUsedUnaries++;
			//System.out.println("Adding a unary spanning from "+start+" to "+end+". P: "+stateStr+" C: "+child.get(0).getLabel());
			//return new Tree<String>(stateStr, child);
			short intermediateNode = grammar.getUnaryIntermediate((short)state,(short)cState);
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
	public Tree<String> extractBestMaxRuleParse2(int start, int end, int state, double score,List<String> sentence ) {
		List<Tree<String>> children = new ArrayList<Tree<String>>();
		String stateStr = (String)tagNumberer.object(state);//+""+start+""+end;
		boolean posLevel = (end - start == 1);
		if (posLevel) {
			children.add(new Tree<String>(sentence.get(start)));
		} else {
			if (derivationChart[start][end][state].isEmpty()){
				return new Tree<String>("degenerate2");
			}
			//double myScore = derivationChart[start][end][state].getPriority();
			double[] entry = derivationChart[start][end][state].peek();
			double myID = entry[4];//
			PriorityQueue<double[]> backup = derivationChart[start][end][state].clone();
			while (myID!=score) {
				//System.out.println(" looking for " +score +" have "+myScore);
				entry = derivationChart[start][end][state].next();
				//System.out.println(" pop2 start " + start + " end "+end +" state "+ state);
				//myScore = derivationChart[start][end][state].getPriority();
				//entry = derivationChart[start][end][state].peek();
				myID = entry[4];//
			}
			derivationChart[start][end][state] = backup;
//			if (myScore!=score)
			//    	System.out.println("still not equal2. start " + start + " end "+end +" state "+ state);
			int split = (int)entry[0];//maxcSplit[start][end][state];
			if (split == -1) {
				System.err.println("Warning: no symbol can generate the span from "+ start+ " to "+end+".");
				System.err.println("The state is supposed to be "+stateStr);
				System.err.println("The insideScores are "+Arrays.toString(iScore[start][end][state])+" and the outsideScores are " +Arrays.toString(oScore[start][end][state]));
				//System.err.println("The maxcScore is "+maxcScore[start][end][state]);
				return  new Tree<String>("ROOT");      
			}
			int lState = (int)entry[2];//maxcLeftChild[start][end][state];
			int rState = (int)entry[3];//maxcRightChild[start][end][state];
			Tree<String> leftChildTree = extractBestMaxRuleParse1(start, split, lState, entry[5], sentence);
			Tree<String> rightChildTree = extractBestMaxRuleParse1(split, end, rState, entry[6], sentence);
			children.add(leftChildTree);
			children.add(rightChildTree);
		}
		return new Tree<String>(stateStr, children);
	}
	
	public Tree<String> getBestConstrainedParse(List<String> sentence, List<Integer>[][] pStates) {
		return null;
	}
	
	public List<Tree<String>> getNBestConstrainedParses(List<String> sentence, List<Integer>[][] pStates, double[] treeLLs, Tree<String>[] sampledTrees){
		length = (short)sentence.size();
		ID=0;
		this.possibleStates = pStates;
		createArrays();
		initializeChart(sentence);
		
		doConstrainedInsideScores(); //change to 2
		
		
		Tree<String> bestTree = new Tree<String>("(ROOT)",new ArrayList<Tree<String>>());
		double score = iScore[0][length][0][0];
		ArrayList<Tree<String>> resultList = new ArrayList<Tree<String>>();
		int actualN = 0;
		int bestLLInd = 0;
		if (score > 0) {
			score = Math.log(score) + (100*iScale[0][length][0]);
			System.out.println("\nFound a parse for sentence with length "+length+". The LL is "+score+".");
			
			// get the sampled trees
			for (int i=0; i<sampledTrees.length; i++){
				Tree<String> sampledTree = drawSample(0,0,0,length);
				//System.out.println(sampledTree);
				sampledTrees[i]=sampledTree;
			}

			oScore[0][length][0][0] = 1.0;
			oScale[0][length][0]=0;
			doConstrainedOutsideScores(); //change to 2
			doConstrainedMaxCScores(sentence);
			
			
			//Tree<String> withoutRoot = extractBestMaxRuleParse(0, length, sentence);
			// add the root
			//ArrayList<Tree<String>> rootChild = new ArrayList<Tree<String>>();
			//rootChild.add(withoutRoot);
			//bestTree = new Tree<String>("ROOT",rootChild);
			actualN = foundDerivations[0][length][0];
			//int p = derivationChart[0][length][0].size();
			//p = derivationChart[0][length][1].size();
			System.out.println("Found "+actualN+" derivations.");
			/* while (derivationChart[0][length][0].hasNext()){
			 double pr = derivationChart[0][length][0].getPriority();
			 double[] entry = derivationChart[0][length][0].next();
			 System.out.println(pr + " " +Arrays.toString(entry));
			 }*/
			PriorityQueue<double[]> myCopy = derivationChart[0][length][0].clone();
			PriorityQueue<double[]> myScore = nthBestScore[0][length][0].clone();
			for (int i=0; i<Math.min(N,actualN); i++){
				//treeLLs[Math.min(N,actualN)-i-1] = Math.log(-1.0*myScore.getPriority());
				//treeLLs[i] = Math.log(-1.0*myScore.getPriority());
				if (myScore.hasNext()) myScore.next();
				
				double p = myCopy.getPriority();
				double[] entry = myCopy.next();
				double pr = entry[4];//myCopy.getPriority();
				//treeLLs[i] = p;
				//System.out.println("Looking for tree with ID "+pr+" and p "+p);
				bestTree = extractBestMaxRuleParse(0, length, pr, sentence);
				
				//System.out.println(bestTree);
				resultList.add(bestTree);
			}
		} 
		else{
			System.out.println("()\nDid NOT find a parse for sentence with length "+length+".");
		}
		bestTree = new Tree<String>("(ROOT)");
  	//resultList.get(0);
  		
	for (int i=0; i<N-Math.min(N,actualN); i++){
  	resultList.add(bestTree);
  }
	if (bestLLInd!=0){
		//System.out.println("Switched order. Tree " +bestLLInd+" has the best LL.");
	}
  StateSetTreeList resultStateSetTrees = new StateSetTreeList(resultList, numSubStatesArray, false, tagNumberer, false);
  ArrayParser llParser = new ArrayParser(grammar, lexicon);
		double maxLL = Double.NEGATIVE_INFINITY, ll=0;
		int ind = 0;
  
  double sumLL = 0;
	for (Tree<StateSet> tree : resultStateSetTrees){
		if (ind<actualN){
			llParser.doInsideScores(tree,false,false);  // Only inside scores are needed here
			ll = tree.getLabel().getIScore(0);
			ll *= Math.exp(100*tree.getLabel().getIScale());
			sumLL += ll;
		}
		else ll = 0;
		if (SloppyMath.isDangerous(ll)) ll = 0;
		treeLLs[ind] = ll;
		//Tree<String> outTree = TreeAnnotations.unAnnotateTree(resultList.get(ind));
  	//System.out.println(ll+"\n"+outTree);
		
		if (ll>maxLL){
			maxLL = ll;
			bestLLInd = ind;
		}
		ind++;
		}
	  double percentage = sumLL/Math.exp(score);
	  treeLLs[N] = percentage;
		System.out.println("Our "+Math.min(actualN,N)+" trees contribute to "+((int) (percentage * 10000))/100.0+" % of the mass.");
  	resultList.add(resultList.get(bestLLInd));
	
		return resultList;
	}
	
	
}
