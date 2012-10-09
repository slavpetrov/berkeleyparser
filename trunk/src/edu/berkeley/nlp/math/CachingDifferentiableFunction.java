package edu.berkeley.nlp.math;

import java.util.Arrays;

import edu.berkeley.nlp.util.Pair;

public abstract class CachingDifferentiableFunction implements
		DifferentiableFunction {

	double[] lastX;
	double[] lastGradient;
	double lastValue;

	protected abstract Pair<Double, double[]> calculate(double[] x);

	private void ensureCache(double[] x) {
		if (!isCached(x)) {
			Pair<Double, double[]> result = calculate(x);
			lastValue = result.getFirst();
			lastX = DoubleArrays.clone(x);
			lastGradient = DoubleArrays.clone(result.getSecond());
		}
	}

	protected boolean isCached(double[] x) {
		if (lastX == null) {
			return false;
		}
		return Arrays.equals(x, lastX);
	}

	public void clearCache() {
		lastX = null;
		lastGradient = null;
	}

	public double[] derivativeAt(double[] x) {
		ensureCache(x);
		return lastGradient;
	}

	public double valueAt(double[] x) {
		ensureCache(x);
		return lastValue;
	}

	public abstract int dimension();

}
