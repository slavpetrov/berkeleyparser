package edu.berkeley.nlp.auxv;

import java.util.List;

import fig.basic.Pair;

/**
 * Keeps track of bracket constraints on a forest of parse trees above a 
 * given sentence.
 * 
 * A bracket constraint have a sign: it can be either positive (true) or negative (false)
 * 
 * A positive bracket constraint means that all trees should have this bracket
 * A negative bracket constraint means that no tree should have this bracket
 * 
 * Regardless of the signs of the stored bracket 
 * constraints, they are non-overlapping
 * 
 * There are two users of this class, each with different privileges.
 * 
 * -The BracketProposer is not allowed to see whether the constraints are positive
 * or negative (to preserve the correctness of the limiting distribution). It
 * view this class thru BracketProposerView
 * 
 * -The sampler uses the other methods to check consistancy of new proposed 
 * constraints and possibly add it, remove constraints and update their sign.
 * Finally, it compiles the constraints (using their signs) into an efficient
 * representation used by the inside outside algorithm to prune search
 * 
 * Note: by "optimistic" method, we mean that it ignores the sign of the 
 * constraints, or more precisely, pretend they are all positive. By "pessimistic"
 * we mean that it pretend they are all negative. 
 * Warning: A catch here is that if an optimistic switch is off in one of 
 * parsing complexity-related method, it does not mean that the computation is
 * done pessimistically, but rather "realistically"/oracle, i.e. by looking at 
 * the actual sign of the brackets
 * 
 * TODO: work with {-1, 0, 1} analogy instead
 * 
 * @author Alexandre Bouchard
 *
 * @param <T>
 */
public interface BracketConstraints<T>
{ 
  /**
   * If optimistic is true, the sign constraints will be ignored, more 
   * precisely all the bracket constraints will be considered positive
   * 
   * If false, compute the parsing using the signs (i.e. false does not mean
   * pessimistic)
   * @param optimistic
   * @return
   */
  public int parsingComplexity(boolean optimistic);
  
  /**
   * Remove the bra constraint [left, right), if any
   * @param left
   * @param right
   * @return Whether the provided span indeed corresponded to a bracket
   */
  public boolean removeBracket(int left, int right);
  /**
   * If the implementation does not allow overlaps,
   * 
   * 	The update is successful iff the [newLeft, newRight) does not cross any
   * 	existing stored bracket, regardless of their sign
   * 
   * Otherwise, always return true
   * 
   * @param newLeft
   * @param newRight
   * @param newIsPositive
   * @return was the update successful?
   */
  public boolean updateBracket(int newLeft, int newRight, boolean newIsPositive);
  /**
   * This is used by the sampler to know where to prune.  This uses the signs
   * of the bracket constraints
   * @return an array s.t. i,j is true iff the span [i, j) is consitent with
   * all the (signed) bracket constraints
   */
  public boolean [][] compile();
  public int nActive();
  public BracketProposerView<T> getView();
	  
  /**
   * Only the view is revealed to the proposers
   * @author Alexandre Bouchard
   *
   * @param <T>
   */
  public static interface BracketProposerView<T>
  {
    public List<T> getSentence();
    /**
     * Does not count the sentence-constraint
     * @return
     */
    public int getNumberOfConstraints();
    public List<Pair<Integer, Integer>> getConstraints();   
    /**
     * A few methods to assess the computational gain of adding spans
     * @return position i,j tells an estimate of the parsing complexity if the (positive)
     * constraint [i,j) were to be added
     */
    public int optimisticParsingComplexity();

    public double [][] optimisticParsingComplexities();
    public double [][] pessimisticParsingComplexities();
    public double pessimisticParsingComplexity();
    public boolean optimisticIsSpanAllowed(int left, int right);
    /**
     * Check whether there is a bracket constraint on [left, right)
     * (without regard if it is positive or negative)
     * @param left
     * @param right
     * @return
     */
    public boolean containsBracketConstraint(int left, int right);
  }
}

