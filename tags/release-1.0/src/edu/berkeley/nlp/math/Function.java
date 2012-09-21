package edu.berkeley.nlp.math;

/**
 */
public interface Function {
  int dimension();
  double valueAt(double[] x);
}
