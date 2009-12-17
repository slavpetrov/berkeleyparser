package edu.berkeley.nlp.math;

/**
 */
public interface DifferentiableFunction extends Function {
  double[] derivativeAt(double[] x);
}
