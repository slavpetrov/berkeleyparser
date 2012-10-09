/**
 * 
 */
package edu.berkeley.nlp.math;

/**
 * @author petrov Orthant-Wise L-BFGS
 * 
 */
public class OW_LBFGSMinimizer extends LBFGSMinimizer implements
		GradientMinimizer {

	/**
	 * @param iterations
	 */
	public OW_LBFGSMinimizer(int iterations) {
		super(iterations);
	}

	public double[] minimize(DifferentiableRegularizableFunction function,
			double[] initial, double tolerance) {
		BacktrackingLineSearcher lineSearcher = new BacktrackingLineSearcher();
		lineSearcher.sufficientDecreaseConstant = 0;
		double[] guess = DoubleArrays.clone(initial);
		for (int iteration = 0; iteration < maxIterations; iteration++) {
			double[] derivative = function.derivativeAt(guess);
			double value = function.valueAt(guess);
			double[] direction = getSearchDirection(function.dimension(),
					derivative);
			double[] unregularizedDerivative = function
					.unregularizedDerivativeAt(guess);

			double[] orthant = getOrthant(initial, derivative);
			DoubleArrays.project(direction, derivative);// orthant);//

			// p^k: project search direction onto orthant defined by gradient
			DoubleArrays.scale(direction, -1.0);

			// System.out.println(" Derivative is: "+DoubleArrays.toString(derivative,
			// 100));
			// DoubleArrays.assign(direction, derivative);
			// System.out.println(" Looking in direction: "+DoubleArrays.toString(direction,
			// 100));
			if (iteration == 0)
				lineSearcher.stepSizeMultiplier = initialStepSizeMultiplier;
			else
				lineSearcher.stepSizeMultiplier = stepSizeMultiplier;
			double[] nextGuess = lineSearcher.minimize(function, guess,
					direction, true);
			double nextValue = function.valueAt(nextGuess);
			// double[] nextDerivative = function.derivativeAt(nextGuess);
			double[] unregularizedNextDerivative = function
					.unregularizedDerivativeAt(nextGuess);

			System.out.printf("Iteration %d ended with value %.6f\n",
					iteration, nextValue);

			if (iteration >= minIterations
					&& converged(value, nextValue, tolerance))
				return nextGuess;

			// update with unregularized derivatives!
			updateHistories(guess, nextGuess, unregularizedDerivative,
					unregularizedNextDerivative);
			guess = nextGuess;
			value = nextValue;
			// derivative = nextDerivative;
			unregularizedDerivative = unregularizedNextDerivative;
			if (iterCallbackFunction != null) {
				iterCallbackFunction.callback(guess, iteration);
			}
		}
		// System.err.println("LBFGSMinimizer.minimize: Exceeded maxIterations without converging.");
		return guess;
	}

	/**
	 * @param initial
	 * @param derivative
	 * @return
	 */
	private double[] getOrthant(double[] initial, double[] derivative) {
		double[] orthant = new double[initial.length];
		for (int i = 0; i < initial.length; i++) {
			if (initial[i] != 0)
				orthant[i] = Math.signum(initial[i]);
			else
				orthant[i] = Math.signum(-derivative[i]);
		}
		return orthant;
	}

}
