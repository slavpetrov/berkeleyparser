/**
 * 
 */
package edu.berkeley.nlp.HDPPCFG;

import edu.berkeley.nlp.HDPPCFG.smoothing.Smoother;
import edu.berkeley.nlp.HDPPCFG.vardp.DiscreteDistribCollectionFactory;
import edu.berkeley.nlp.HDPPCFG.vardp.TopLevelWordDistrib;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;


/**
 * @author petrov
 *
 */
public interface LexiconInterface {
	public void trainTree(Tree<StateSet> trainTree, double randomness, LexiconInterface oldLexicon, boolean secondHalf, boolean noSmoothing);
	public double[] score(String word, short tag, int loc, boolean noSmoothing, boolean isSignature);
	public Lexicon splitAllStates(int[] counts, boolean moreSubstatesThanCounts);
	public void optimize(DiscreteDistribCollectionFactory ddcFactory, TopLevelWordDistrib[] topDistribs);
  public void mergeStates(boolean[][][] mergeThesePairs, double[][] mergeWeights);
  public void setSmoother(Smoother smoother);
  public Smoother getSmoother();
  public double[] getSmoothingParams();
  public void logarithmMode();
  public boolean isLogarithmMode();
  public LexiconInterface copyLexicon();
  public void removeUnlikelyTags(double filter);
  public LexiconInterface projectLexicon(double[] condProbs, int[][] mapping, int[][] toSubstateMapping);
  public void setUseVarDP(boolean useVarDP);
}
