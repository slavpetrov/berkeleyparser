package edu.berkeley.nlp.math;

import edu.berkeley.nlp.util.Logger;

public class ExponentiatedGradientMinimizer implements GradientMinimizer {
	private final static double EPS = 1e-10;

	private final Normalizer normalizer;
	private final int maxIterations;
	private final double stepSizeMultiplier;

	public ExponentiatedGradientMinimizer(Normalizer normalizer,
			int maxIterations, double stepSizeMultiplier) {
		this.normalizer = normalizer;
		this.maxIterations = maxIterations;
		this.stepSizeMultiplier = stepSizeMultiplier;
	}

	public double[] minimize(DifferentiableFunction function, double[] initial,
			double tolerance, boolean project) {
		return null;
	}

	public double[] minimize(DifferentiableFunction function, double[] initial,
			double tolerance) {
		double[] guess = DoubleArrays.clone(initial);
		for (int iteration = 0; iteration < maxIterations; iteration++) {
			double[] derivatives = function.derivativeAt(guess);
			double value = function.valueAt(guess);
			double[] direction = DoubleArrays.multiply(derivatives,
					-stepSizeMultiplier);
			double[] scale = DoubleArrays.exponentiate(direction);
			double[] newGuess = normalizer.normalize(DoubleArrays
					.pointwiseMultiply(guess, scale));
			double newValue = function.valueAt(newGuess);
			Logger.i()
					.logs(String
							.format("Exponentiated gradient: iteration %d (max %d) completed with value %f",
									iteration + 1, maxIterations, newValue));
			// if (converged(value, newValue, tolerance)) {
			// return newGuess;
			// }
			guess = newGuess;
		}
		return guess;
	}

	private boolean converged(double value, double nextValue, double tolerance) {
		if (value == nextValue)
			return true;
		double valueChange = Math.abs(nextValue - value);
		double valueAverage = Math.abs(nextValue + value + EPS) / 2.0;
		if (valueChange / valueAverage < tolerance)
			return true;
		return false;
	}
}
