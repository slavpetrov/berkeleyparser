package edu.berkeley.nlp.HDPPCFG.vardp;

// P(child (sub)state | parent substate)
public interface UnaryDiscreteDistribCollection {
  // child (sub)state, parent substate
  public void update(double[][] counts);
  public double[][] getScores();
}
