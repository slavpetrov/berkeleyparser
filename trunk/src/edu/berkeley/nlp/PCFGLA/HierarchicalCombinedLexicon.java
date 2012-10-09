///**
// * 
// */
//package edu.berkeley.nlp.PCFGLA;
//
//import java.util.Arrays;
//import java.util.List;
//
//import edu.berkeley.nlp.PCFGLA.SimpleLexicon.IntegerIndexer;
//import edu.berkeley.nlp.PCFGLA.smoothing.Smoother;
//import edu.berkeley.nlp.syntax.StateSet;
//import edu.berkeley.nlp.syntax.Tree;
//import fig.basic.Indexer;
//
///**
// * @author petrov
// * each word's tagging probability is the sum of the (word,tag) score and the (signature,tag) score
// *
// */
//public class HierarchicalCombinedLexicon extends HierarchicalLexicon{
//	private static final long serialVersionUID = 1L;
//	protected int knownWordCount;
//	/**
//	 * @param numSubStates
//	 * @param threshold
//	 */
//	public HierarchicalCombinedLexicon(short[] numSubStates, int knownWordCount) {
//		super(numSubStates, 0);
//		this.knownWordCount = knownWordCount;
//	}
//	
//	public HierarchicalCombinedLexicon(short[] numSubStates, int smoothingCutoff, double[] smoothParam, 
//			Smoother smoother, StateSetTreeList trainTrees, int knownWordCount) {
//  	this(numSubStates, knownWordCount);
//  	init(trainTrees);
//  }
//
//
//  /**
//	 * @param previousLexicon
//	 */
//	public HierarchicalCombinedLexicon(SimpleLexicon previousLexicon, int knownWordCount) {
//		super(previousLexicon);
//		this.knownWordCount = knownWordCount;
//	}
//	
//	public HierarchicalCombinedLexicon newInstance() {
//		return new HierarchicalCombinedLexicon(this.numSubStates,this.knownWordCount);
//	}
//
////	public double[] score(String word, short tag, int loc, boolean noSmoothing, boolean isSignature) {
////		int globalWordIndex = wordIndexer.indexOf(word);
////		int globalSigIndex = wordIndexer.indexOf(getSignature(word, loc));
////		return score(globalWordIndex, globalSigIndex, tag, loc, noSmoothing, isSignature);
////	}
//	
//
//	
//	public double[] score(StateSet stateSet, short tag, boolean noSmoothing, boolean isSignature) {
////		String sig = getSignature(stateSet.getWord(), stateSet.from);
////		if (stateSet.sigIndex != wordIndexer.indexOf(sig));
////			System.out.println("problem, signatures dont match!");
//		if (stateSet.wordIndex == -2) {
//			String word = stateSet.getWord();
//			stateSet.wordIndex = (short)wordIndexer.indexOf(word);
//			stateSet.sigIndex = (short)wordIndexer.indexOf(getSignature(word,stateSet.from));
//		}
//		return score(stateSet.wordIndex, stateSet.sigIndex, tag, stateSet.from, noSmoothing, isSignature);
//	}
//	
//
//	public double[] score(int globalWordIndex, int globalSigIndex, short tag, int loc, boolean noSmoothing, boolean isSignature) {
//		double[] res = new double[numSubStates[tag]];
//		if (globalWordIndex!=-1) {
//			int tagSpecificWordIndex = tagWordIndexer[tag].indexOf(globalWordIndex);
//			if (tagSpecificWordIndex!=-1){
//				for (int i=0; i<numSubStates[tag]; i++){
//					res[i] = scores[tag][i][tagSpecificWordIndex]; 
//				}
//			}
//			else {
//				Arrays.fill(res, 1.0);
//			}
//		} else {
//				Arrays.fill(res, 1.0);
//		} 
//		if (globalWordIndex>=0 && wordCounter[globalWordIndex]>knownWordCount) return res;
//		if (globalSigIndex!=-1) {
//			int tagSpecificWordIndex = tagWordIndexer[tag].indexOf(globalSigIndex);
//			if (tagSpecificWordIndex!=-1){
//				for (int i=0; i<numSubStates[tag]; i++){
//					res[i] *= scores[tag][i][tagSpecificWordIndex]; 
//				}
//			}
//		} else{
//			System.out.println("unseen sig");
//		}
//		if (smoother!=null) smoother.smooth(tag,res);
//		return res;
//  } 
//
//	public double[] scoreWord(StateSet stateSet, int tag){
//		return scoreWord(stateSet.wordIndex, tag);
//	}
//	
//  public double[] scoreWord(String word, int tag) {
//		int globalWordIndex = wordIndexer.indexOf(word);
//		return scoreWord(globalWordIndex, tag);
//  }
//
//	public double[] scoreWord(int globalWordIndex, int tag){
//		double[] res = new double[numSubStates[tag]];
//		if (globalWordIndex!=-1) {
//			int tagSpecificWordIndex = tagWordIndexer[tag].indexOf(globalWordIndex);
//			if (tagSpecificWordIndex!=-1){
//				for (int i=0; i<numSubStates[tag]; i++){
//					res[i] = scores[tag][i][tagSpecificWordIndex]; 
//				}
//			}
//		} else {
//			Arrays.fill(res, 1.0);
//		}
//		return res;
//	}
//	  
//
//  public double[] scoreSignature(StateSet stateSet, int tag) {
//		return scoreSignature(stateSet.wordIndex, stateSet.sigIndex, tag);
//  }
//
//	
//	public double[] scoreSignature(String word, String sig, int tag) {
//		int globalWordIndex = wordIndexer.indexOf(word);
//		int globalSigIndex = wordIndexer.indexOf(sig);
//		return scoreSignature(globalWordIndex, globalSigIndex, tag);
//  }
//  	
//	public double[] scoreSignature(int globalWordIndex, int globalSigIndex, int tag) {
//		if (globalWordIndex>=0 && wordCounter[globalWordIndex]>knownWordCount) return null;
//  	double[] res = new double[numSubStates[tag]];
//		if (globalSigIndex!=-1) {
//			int tagSpecificWordIndex = tagWordIndexer[tag].indexOf(globalSigIndex);
//			if (tagSpecificWordIndex!=-1){
//				for (int i=0; i<numSubStates[tag]; i++){
//					res[i] += scores[tag][i][tagSpecificWordIndex]; 
//				}
//			}
//		} else{
//			System.out.println("unseen sig");
//		}
//		return res;
//  }
//  
//	public void labelTrees(StateSetTreeList trainTrees){
//  	for (Tree<StateSet> tree : trainTrees){
//  		List<StateSet> words = tree.getYield();
//  		List<StateSet> tags = tree.getPreTerminalYield();
//  		int ind = 0;
//  		for (StateSet word : words){
//  			word.wordIndex = (short)wordIndexer.indexOf(word.getWord());
//  			short tag = tags.get(ind).getState();
////				if (wordIsAmbiguous[word.wordIndex]) {
//					String sig = getSignature(word.getWord(), ind);
//					wordIndexer.add(sig);
//	  			word.sigIndex = (short)wordIndexer.indexOf(sig);
//					tagWordIndexer[tag].add(wordIndexer.indexOf(sig));
////				}
////				else { word.sigIndex = -1; }
//				ind++;
//  		}  		
//  	}
//
//	}
//	
//  public void init(StateSetTreeList trainTrees){
//  	for (Tree<StateSet> tree : trainTrees){
//  		List<StateSet> words = tree.getYield();
//  		List<StateSet> tags = tree.getPreTerminalYield();
//  		int ind = 0;
//  		for (StateSet word : words){
//				String sig = word.getWord();
//				wordIndexer.add(sig);
//				tagWordIndexer[tags.get(ind).getState()].add(wordIndexer.indexOf(sig));
//  			word.wordIndex = (short)wordIndexer.indexOf(sig);
//				ind++;
//  		}
//  	}
//  	wordCounter = new int[wordIndexer.size()];
//  	tagWordIndexer = new IntegerIndexer[numStates];
//  	for (int tag=0; tag<numStates; tag++){
//  		tagWordIndexer[tag] = new IntegerIndexer(wordIndexer.size());
//  	}
////  	int[] firstTag = new int[wordIndexer.size()];
//////  	wordIsAmbiguous = new boolean[wordIndexer.size()];
////  	for (Tree<StateSet> tree : trainTrees){
////  		List<StateSet> words = tree.getYield();
////  		List<StateSet> tags = tree.getPreTerminalYield();
////  		int ind = 0;
////  		for (StateSet word : words){
////  			short tag = tags.get(ind).getState();
////				ind++;
////				if (firstTag[word.wordIndex]==0) firstTag[word.wordIndex] = tag;
////				else if (firstTag[word.wordIndex] != tag) {
//////					wordIsAmbiguous[word.wordIndex] = true;
////				}
////  		}
////  	}
//  	labelTrees(trainTrees);
//  	expectedCounts = new double[numStates][][];
//  	scores = new double[numStates][][];
//  	for (int tag=0; tag<numStates; tag++){
//  		expectedCounts[tag] = new double[numSubStates[tag]][tagWordIndexer[tag].size()];
//  		scores[tag] = new double[numSubStates[tag]][tagWordIndexer[tag].size()];
//  	}
//  	nWords = wordIndexer.size();
//  }
//  
//
//
// }
