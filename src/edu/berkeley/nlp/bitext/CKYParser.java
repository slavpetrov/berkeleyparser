package edu.berkeley.nlp.bitext;


import java.util.Arrays;
import java.util.Collections;
import java.util.List;


//import edu.berkeley.nlp.parser.Parser;
//import edu.berkeley.nlp.parser.UnaryRule;
import edu.berkeley.nlp.bitext.GrammarStateFactory.GrammarState;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.CollectionUtils;
import edu.berkeley.nlp.util.Counter;


public class CKYParser implements Parser {

	private final Grammar grammar;
	private final Lexicon lexicon;
	private List<String> sentence;
	private final UnaryGrammarProjection unaryProjection;

	private  double[][][] iScores;
	private  double[][][] oScores; 

	private  int[][] narrowRExtent;
	private  int[][] narrowLExtent;
	private  int[][] wideRExtent;
	private  int[][] wideLExtent;
	
	private double bestRootIScore ;

	/**
	 * Have done outside scores for the current parse?
	 */
	private boolean didOutsideScores;

	private final int INIT_MAX_SENT_LENNGTH = 51;

	public CKYParser(Grammar grammar, Lexicon lexicon) {
		this.grammar = grammar;
		this.lexicon  = lexicon;
		this.unaryProjection =  new UnaryGrammarProjection(grammar);
		setMaxSentenceLength(INIT_MAX_SENT_LENNGTH);
	}

	public void setMaxSentenceLength(int maxLength) {
		
		int numStates = grammar.getNumStates();

		iScores = new double[maxLength+1][maxLength+1][];
		oScores = new double[maxLength+1][maxLength+1][];
		for (int start=0; start <= maxLength+1; ++start) {
			for (int end=start+1; end <= maxLength; ++end) {
				iScores[start][end] = new double[numStates];
				oScores[start][end] = new double[numStates];
			}
		}
		
		
//		iScores = new double[maxLength+1][maxLength+1][numStates];
//		oScores = new double[maxLength+1][maxLength+1][numStates];
		narrowRExtent = new int[maxLength+1][numStates];
		narrowLExtent = new int[maxLength+1][numStates];
		wideRExtent = new int[maxLength+1][numStates];
		wideLExtent = new int[maxLength+1][numStates];
	}

	private void unaryProjection(int start, int end) {
		for (int state=0; state < grammar.getNumStates(); ++state) {
			double iScore = iScores[start][end][state];
			if (iScore == Double.NEGATIVE_INFINITY) {
				continue;
			}
			List<UnaryRule> unarys = unaryProjection.getClosedUnarysByChild(state);			
			for (UnaryRule ur: unarys) {
				int parent = ur.parent().id();
				double ruleScore = ur.getScore();
				double curIScore = iScores[start][end][parent];
				double newIScore = iScore + ruleScore;
				if (newIScore > curIScore) {
					iScores[start][end][parent] = newIScore;					
					if (curIScore == Double.NEGATIVE_INFINITY) {
						if (start > narrowLExtent[end][parent]) {
							narrowLExtent[end][parent] = start;
							wideLExtent[end][parent] = start;
						} else {
							if (start < wideLExtent[end][parent]) {
								wideLExtent[end][parent] = start;
							}
						}
						if (end < narrowRExtent[start][parent]) {
							narrowRExtent[start][parent] = end;
							wideRExtent[start][parent] = end;
						} else if (end > wideRExtent[start][parent]) {
							wideRExtent[start][parent] = end;
						}
					}
				}
			}
		}
	}

	private void binaryProjection(int start, int end) {		
		leftBinaryProjection(start, end);
		rightBinaryProjection(start, end);
	}

	
	private void rightBinaryProjection(int start, int end) {
		int numStates = grammar.getNumStates();
		for (int rightState=0; rightState < numStates; ++rightState) {
			int narrowL = narrowLExtent[end][rightState];
			boolean iPossibleR = (narrowL > start);
			if (!iPossibleR) {
				continue;
			}
			for (BinaryRule br: grammar.getBinarysByRightChild(rightState)) {
				int leftState = br.leftChild().id();
				int narrowR = narrowRExtent[start][leftState];
				boolean iPossibleL = (narrowR <= narrowL);
				if (!iPossibleL) {
					continue;
				}
				int min1 = narrowR;
				int min2 = wideLExtent[end][rightState];
				int min = (min1 > min2 ? min1 : min2);
				if (min > narrowL) {
					continue;
				}
				int max1 = wideRExtent[start][leftState];
				int max2 = narrowL;
				int max = (max1 < max2 ? max1 : max2);
				if (min > max) {
					continue;
				}
				double pS = br.getScore();
				int parentState = br.parent().id();
				double oldIScore = iScores[start][end][parentState];
				double bestIScore = oldIScore;
				boolean foundBetter; // always initialized below
				for (int split = min; split <= max; split++) {
					double lS = iScores[start][split][leftState];
					if (lS == Double.NEGATIVE_INFINITY) {
						continue;
					}
					double rS = iScores[split][end][rightState];
					if (rS == Double.NEGATIVE_INFINITY) {
						continue;
					}
					double tot = pS + lS + rS;
					if (tot > bestIScore) {
						bestIScore = tot;
					}
				}
				foundBetter = bestIScore > oldIScore;
				if (foundBetter) {
					iScores[start][end][parentState] = bestIScore;
					if (oldIScore == Double.NEGATIVE_INFINITY) {
						if (start > narrowLExtent[end][parentState]) {
							narrowLExtent[end][parentState] = start;
							wideLExtent[end][parentState] = start;
						} else {
							if (start < wideLExtent[end][parentState]) {
								wideLExtent[end][parentState] = start;
							}
						}
						if (end < narrowRExtent[start][parentState]) {
							narrowRExtent[start][parentState] = end;
							wideRExtent[start][parentState] = end;
						} else {
							if (end > wideRExtent[start][parentState]) {
								wideRExtent[start][parentState] = end;
							}
						}
					}
				}
			}


		}

	}

	private void leftBinaryProjection(int start, int end) {
		int numStates = grammar.getNumStates();
		for (int leftState=0; leftState < numStates; ++leftState) {
			int narrowR = narrowRExtent[start][leftState];
			boolean iPossibleL = (narrowR < end); // can this left constituent leave space for a right constituent?
			if (!iPossibleL) {
				continue;
			}
			BinaryRule[] binarys = grammar.getBinarysByLeftChild(leftState);
			for (BinaryRule br: binarys) {
				int rightState = br.rightChild().id();
				int narrowL = narrowLExtent[end][rightState];
				boolean iPossibleR = (narrowL >= narrowR); // can this right constituent fit next to the left constituent?
				if (!iPossibleR) {
					continue;
				}
				int min1 = narrowR;
				int min2 = wideLExtent[end][rightState];
				int min = (min1 > min2 ? min1 : min2);
				if (min > narrowL) { // can this right constituent stretch far enough to reach the left constituent?
					continue;
				}
				int max1 = wideRExtent[start][leftState];
				int max2 = narrowL;
				int max = (max1 < max2 ? max1 : max2);
				if (min > max) { // can this left constituent stretch far enough to reach the right constituent?
					continue;
				}
				double pS = br.getScore();
				int parentState = br.parent().id();
				double oldIScore = iScores[start][end][parentState];
				double bestIScore = oldIScore;
				boolean foundBetter;  // always set below for this rule
				for (int split = min; split <= max; split++) { 
					double lS = iScores[start][split][leftState];
					if (lS == Double.NEGATIVE_INFINITY) {
						continue;
					}
					double rS = iScores[split][end][rightState];
					if (rS == Double.NEGATIVE_INFINITY) {
						continue;
					}
					double tot = pS + lS + rS;
					if (tot > bestIScore) {
						bestIScore = tot;
					}
				}
				foundBetter = bestIScore > oldIScore;
				if (foundBetter) { // this way of making "parentState" is better than previous
					iScores[start][end][parentState] = bestIScore;
					if (oldIScore == Double.NEGATIVE_INFINITY) {
						if (start > narrowLExtent[end][parentState]) {
							narrowLExtent[end][parentState] = start;
							wideLExtent[end][parentState] = start;
						} else {
							if (start < wideLExtent[end][parentState]) {
								wideLExtent[end][parentState] = start;
							}
						}
						if (end < narrowRExtent[start][parentState]) {
							narrowRExtent[start][parentState] = end;
							wideRExtent[start][parentState] = end;
						} else {
							if (end > wideRExtent[start][parentState]) {
								wideRExtent[start][parentState] = end;
							}
						}
					}
				}	            
			}

		}
	}
	
	public void parseWithoutExtract(List<String> sentence) {
		initialize(sentence);		
		doInsideScores();
	}

	public Tree<String> parse(List<String> sentence) {
		initialize(sentence);		
		doInsideScores();
		// we only do outside scores when asked for them		
		GrammarState goalState = grammar.getRootState();
		Tree<String> tree = extractBestParse(goalState, 0, sentence.size());
		tree = unaryProjection.addUnaryChains(tree);		
		return tree;
	}

	private void doOutsideScores() {
		// so we don't call this function again
		if (didOutsideScores) {
			return;
		}
		
		this.didOutsideScores = true;
		
		int rootId = grammar.getRootState().id();
		oScores[0][sentence.size()][rootId] = 0.0;

		for (int diff=sentence.size(); diff > 0; --diff) {
			for (int start=0; start+diff <= sentence.size(); ++start) {
				int end = start + diff;
				// Unaries			
				for (int s=0; s < grammar.getNumStates(); ++s) {
					double oScore = oScores[start][end][s];
					if ( Double.isInfinite(oScore) ) {
						continue;
					}							
					for (UnaryRule ur: unaryProjection.getClosedUnarysByParent(s)) {
						int child = ur.child().id();
						if ( Double.isInfinite(iScores[start][end][child]) ) {
							continue;
						}
						double curTotal = ur.getScore() + oScores[start][end][s];
						if (curTotal > oScores[start][end][child]) {
							oScores[start][end][child] = curTotal;
						}
					}
				}				
				// Left Binarys 
				for (int s=0; s < grammar.getNumStates(); ++s) {
					int min1 = narrowRExtent[start][s];
					if (end < min1) {
						continue;
					}
					int leftChild = s;
					BinaryRule[] leftBinarys = grammar.getBinarysByLeftChild(s);
					for (BinaryRule br: leftBinarys) {
						int parent = br.parent().id();
						int rightChild = br.rightChild().id();
						double oS = oScores[start][end][parent];
						if (oS == Float.NEGATIVE_INFINITY) {
							continue;
						}
						int max1 = narrowLExtent[end][rightChild];
						if (max1 < min1) {
							continue;
						}
						int min = min1;
						int max = max1;
						if (max - min > 2) {
							int min2 = wideLExtent[end][rightChild];
							min = (min1 > min2 ? min1 : min2);
							if (max1 < min) {
								continue;
							}
							int max2 = wideRExtent[start][s];
							max = (max1 < max2 ? max1 : max2);
							if (max < min) {
								continue;
							}
						}
						double pS = br.getScore();
						for (int split = min; split <= max; split++) {
							double lS = iScores[start][split][leftChild];
							if (lS == Double.NEGATIVE_INFINITY) {
								continue;
							}
							double rS = iScores[split][end][rightChild];
							if (rS == Float.NEGATIVE_INFINITY) {
								continue;
							}
							double totL = pS + rS + oS;
							if (totL > oScores[start][split][leftChild]) {
								oScores[start][split][leftChild] = totL;
							}
							double totR = pS + lS + oS;
							if (totR > oScores[split][end][rightChild]) {
								oScores[split][end][rightChild] = totR;
							}
						}
					}
				}
				// Right Binarys
				for (int s=0; s < grammar.getNumStates(); ++s) {
					int max1 = narrowLExtent[end][s];
					if (max1 < start) {
						continue;
					}
					BinaryRule[] binarys = grammar.getBinarysByRightChild(s);
					int rightChild = s;
					for (BinaryRule br: binarys) {	
						int parent = br.parent().id();
						int leftChild = br.leftChild().id();
						double oS = oScores[start][end][parent];
						if (oS == Float.NEGATIVE_INFINITY) {
							continue;
						}
						int min1 = narrowRExtent[start][leftChild];
						if (max1 < min1) {
							continue;
						}
						int min = min1;
						int max = max1;
						if (max - min > 2) {
							int min2 = wideLExtent[end][rightChild];
							min = (min1 > min2 ? min1 : min2);
							if (max1 < min) {
								continue;
							}
							int max2 = wideRExtent[start][leftChild];
							max = (max1 < max2 ? max1 : max2);
							if (max < min) {
								continue;
							}
							double pS = br.getScore();
							for (int split = min; split <= max; split++) {
								double lS = iScores[start][split][leftChild];
								if (lS == Double.NEGATIVE_INFINITY) {
									continue;
								}
								double rS = iScores[split][end][rightChild];
								if (rS == Float.NEGATIVE_INFINITY) {
									continue;
								}
								double totL = pS + rS + oS;
								if (totL > oScores[start][split][leftChild]) {
									oScores[start][split][leftChild] = totL;
								}
								double totR = pS + lS + oS;
								if (totR > oScores[split][end][rightChild]) {
									oScores[split][end][rightChild] = totR;
								}
							}

						}
					}
				}

			}
		}		
	}

	public double[][][] getInsideScores(List<String> sentence){
		parseWithoutExtract(sentence);
		return iScores;
	}
	
	private void doInsideScores() {
		for (int diff=2; diff <= sentence.size(); ++diff) {
			for (int start=0; start+diff <= sentence.size(); ++start) {
				int end = start+diff;			
				binaryProjection(start, end);
				unaryProjection(start, end);
			}
		}
		bestRootIScore = iScores[0][sentence.size()][grammar.getRootState().id()];
	}
	
	public double getBestParseScore() {
		return bestRootIScore;
	}

	private Tree<String> extractBestParse(GrammarState goalState, int start, int end) {
		if (grammar.isTag(goalState)) {			
			List<Tree<String>> children = Collections.singletonList( new Tree<String>(sentence.get(start)) );
			Tree<String> tree = new Tree<String> (goalState.label(),  children);
			return tree;
		}
		double targetIScore = iScores[start][end][goalState.id()];		
		if (Double.isInfinite(targetIScore)) {
			throw new RuntimeException("Trying to find unfound parse");
		}
		// check binarys first
		BinaryRule[] binarys = grammar.getBinarysByParent(goalState);
		for (int split=start+1; split < end; ++split) {			
			for (BinaryRule br: binarys) {
				GrammarState leftState = br.leftChild();
				double leftIScore = iScores[start][split][leftState.id()];
				GrammarState rightState = br.rightChild();
				double rightIScore = iScores[split][end][rightState.id()];
				double guessIScore = br.getScore() + leftIScore + rightIScore;
				if (matches(guessIScore, targetIScore)) {
					Tree<String> leftTree = extractBestParse(leftState, start, split);
					Tree<String> rightTree = extractBestParse(rightState, split, end);
					List<Tree<String>> children = CollectionUtils.makeList( leftTree, rightTree );
					return new Tree<String>(goalState.label(), children); 
				}
			}
		}
		//check unarys
		List<UnaryRule> unarys = unaryProjection.getClosedUnarysByParent(goalState.id());
		for (UnaryRule ur: unarys) {
			GrammarState childState = ur.child();
			double guessIScore = ur.getScore() + iScores[start][end][childState.id()];			
			if (!goalState.equals(childState) && matches(guessIScore, targetIScore)) {				
				Tree<String> child = extractBestParse(childState, start, end);
				return new Tree<String>(goalState.label(), Collections.singletonList( child ) );
			}
		}					
		throw new RuntimeException("Unable to find parse for " + goalState + " on ["+start+","+end+"]");
	}

	//private final static double EPSILON = 1.0e-10;

	private boolean matches(double guess, double target) {
		return guess == target;
	}

	private void initialize(List<String> sentence) {
		this.didOutsideScores = false;
		this.sentence = sentence;
		this.lexicon.setInputSentence(sentence);
		
//		setMaxSentenceLength(sentence.size());

		for (int loc=0; loc < sentence.size(); ++loc) {
			Arrays.fill(narrowLExtent[loc], -1); // the rightmost left with state s ending at i that we can get is the beginning
			Arrays.fill(wideLExtent[loc], sentence.size() + 1); // the leftmost left with state s ending at i that we can get is the end
			Arrays.fill(narrowRExtent[loc], sentence.size() + 1); // the leftmost right with state s starting at i that we can get is the end
			Arrays.fill(wideRExtent[loc], -1); // the rightmost right with state s starting at i that we can get is the beginning
		}

		for (int i=0; i < sentence.size(); ++i) {

			// Clear the iScores and oScores
			for (int j=i+1; j <= sentence.size(); ++j) {
				Arrays.fill( iScores[i][j], Double.NEGATIVE_INFINITY );
				Arrays.fill( oScores[i][j], Double.NEGATIVE_INFINITY);
			}

			Counter<String> tagScores = lexicon.getTagScores(i);

			// Initialize Tag Scores
			for (String tag: tagScores.keySet()) {
				double score = tagScores.getCount(tag);
				GrammarState tagState = grammar.getState(tag);
				int state = tagState.id();
				assert grammar.isTag(tagState);
				iScores[i][i+1][tagState.id()] = score;					
				narrowRExtent[i][state] = i + 1;
				narrowLExtent[i+1][state] = i;
				wideRExtent[i][state] = i + 1;
				wideLExtent[i+1][state] = i ;
			}	

			unaryProjection(i,i+1);
		}



	}

	public double getInsideScore(GrammarState state, int start, int end) {
		double iScore = iScores[start][end][state.id()];
		return  iScore;
	}

	public double getOutsideScore(GrammarState state, int start, int end) {
		if (!didOutsideScores) {
			doOutsideScores();
		}
		double oScore = oScores[start][end][state.id()];  
		return oScore;
	}

	public double[][][] getInsideScores() {
		return iScores;
	}

	public double[][][] getOutsideScores() {
		if (!didOutsideScores) {
			doOutsideScores();
		}
		return oScores;
	}

}
