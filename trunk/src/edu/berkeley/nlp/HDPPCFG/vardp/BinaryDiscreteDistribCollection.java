package edu.berkeley.nlp.HDPPCFG.vardp;

// P(left child (sub)state, right child (sub)state | parent substate)
public interface BinaryDiscreteDistribCollection {
  // left child (sub)state, right child (sub)state, parent substate
  public void update(double[][][] counts);
  public double[][][] getScores();
}
