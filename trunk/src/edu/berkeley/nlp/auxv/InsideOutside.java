package edu.berkeley.nlp.auxv;

import java.util.List;

import edu.berkeley.nlp.syntax.StateSet;

/**
 * Interface for inside outside algorithms
 * @author Alexandre Bouchard
 *
 */
public interface InsideOutside
{
  /**
   * Posterior of the given bracket, again for the last sentence that was given
   * to compute()
   * 
   * entry i,j should be the expectation under the posterior distribution of 
   * the indicator that at least one of the constituents in the tree yields 
   * symbols [i,j).  It is therefore a probability measure.  
   * @return
   */
  public double [][] getBracketPosteriors();
  /**
   * Compute inside outside of the given sentence.
   * 
   * The expected counts are added to the provided running suffstat
   * 
   * @param sentence
   * @param suffStat
   * @return true iff the sentence has positive pr
   */
  public boolean compute(List<String> sentence, SuffStat suffStat);
  /** 
   * Expectation under posterior[count(parent -> left, right)]
   * 
   * support is on : 	left.right == right.left
   * and 							left.left == parent.left
   * and 							right.right == parent.right
   * 
   * this means the probability that 
   * 			the tree fragment r -> p q  yields [i,k)
   * 	and	the left subtree yields [i,j)
   *  and	the right subtree yields [j,k)
   * 
   * where 	left = symbol p yields [i,j)
   * 				right = symbol q yields [j,k)
   * 				parent = symbol r yields [i,k)
   * 
   */
  public double stateSetPosterior(StateSet parent, StateSet left, StateSet right);
  /**
   * The same for unary chains
   */
  public double stateSetPosterior(StateSet parent, StateSet child);
  /**
   * Expected count of a single node (will be the denominator when computing 
   * posterior condition node probabilities)
   */
  public double stateSetPosterior(StateSet parent);
}
