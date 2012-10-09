package edu.berkeley.nlp.math;

public interface DifferentiableRegularizableFunction extends
		DifferentiableFunction {

	double[] unregularizedDerivativeAt(double[] x);

}
