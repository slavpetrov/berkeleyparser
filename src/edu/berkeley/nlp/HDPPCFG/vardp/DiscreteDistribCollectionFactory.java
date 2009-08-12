package edu.berkeley.nlp.HDPPCFG.vardp;

/**
 * Specifies the parameters for a state/substate PCFG.
 * We need these type of objects:
 *  - top-level distribution over states
 *  - top-level distribution over substates
 *  - top-level distribution over words
 *  - conditioned on parent substate, distribution over words
 *  - conditioned on parent substate, distribution over child state(s)
 *  - conditioned on parent substate, distribution over child substate(s)
 * See MLECollectionFactory and DirichletCollectionFactory.
 */
public interface DiscreteDistribCollectionFactory {
  public void estimationMethodChanged();

  // n = number of states
  public TopLevelDistrib newTopLevelState(int n);

  // n = number of substates
  public TopLevelDistrib newTopLevelSubstate(int n);

  // Number of words will be passed in later.
  public TopLevelWordDistrib newTopLevelWord();

  // nw = number of words
  public DiscreteDistrib newWord(TopLevelWordDistrib top, int nw);

  // nc = number of child states, np = number of parent substates
  public UnaryDiscreteDistribCollection newUnaryState(
      TopLevelDistrib top, int nc, int np);

  // nc1, nc2 = number of left/right child states,
  // np = number of parent substates
  public BinaryDiscreteDistribCollection newBinaryState(
      TopLevelDistrib top1, TopLevelDistrib top2,
      int nc1, int nc2, int np);

  // nc = number of child substates, np = number of parent substates
  public UnaryDiscreteDistribCollection newUnarySubstate(
      TopLevelDistrib top, int nc, int np);

  // nc1, nc2 = number of left/right child substates,
  // np = number of parent substates
  public BinaryDiscreteDistribCollection newBinarySubstate(
      TopLevelDistrib top1, TopLevelDistrib top2,
      int nc1, int nc2, int np);
  
  // binary distribution over unary and binary rules
  public DiscreteDistrib newRule();
}
