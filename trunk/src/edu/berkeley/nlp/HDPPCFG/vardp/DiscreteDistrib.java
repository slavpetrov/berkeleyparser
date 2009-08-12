package edu.berkeley.nlp.HDPPCFG.vardp;

public interface DiscreteDistrib {
  public void update(double[] counts);
  public double[] getScores();
}

