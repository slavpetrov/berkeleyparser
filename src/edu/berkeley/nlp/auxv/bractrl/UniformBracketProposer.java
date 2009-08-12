///**
// * 
// */
//package edu.berkeley.nlp.auxv.bractrl;
//
//import java.util.Random;
//
//import edu.berkeley.nlp.auxv.BracketConstraints;
//import edu.berkeley.nlp.auxv.Utils;
//import edu.berkeley.nlp.auxv.BracketConstraints.BracketProposerView;
//import fig.basic.Pair;
//
//public class UniformBracketProposer implements BracketProposer
//{
//	private boolean allowsOverlap = false;
//  public Pair<Integer, Integer> next(Random rand, BracketProposerView currentConstraints)
//  {
//    int left = rand.nextInt(currentConstraints.getSentence().size());
//    int right = Utils.nextInt(rand, left, currentConstraints.getSentence().size());
//    return new Pair<Integer, Integer>(left, right);
//  }
//
//	/**
//	 * @return
//	 */
//	public boolean allowsOverlappingBrackets() {
//		return allowsOverlap;
//	}
//}