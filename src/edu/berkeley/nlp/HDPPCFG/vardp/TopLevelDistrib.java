package edu.berkeley.nlp.HDPPCFG.vardp;

/**
 * A distribution over (sub)states, used to tie together conditional
 * distributions farther down in the hierarchy.
 */
public interface TopLevelDistrib {
  // For direct update (not correct optimization)
  public void updateDirect(double[] counts);
  public double[] getProbs();
  public void setProbs(double[] probs);
  public int dim(); // Return number of substates
}
