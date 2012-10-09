package edu.berkeley.nlp.util;

public class Maxer<T> {
	private double max = Double.NEGATIVE_INFINITY;
	private T argMax = null;

	public String toString() {
		return argMax.toString() + ": " + Fmt.D(max);
	}

	public void observe(T t, double val) {
		if (val > max) {
			max = val;
			argMax = t;
		}
	}

	public double getMax() {
		return max;
	}

	public T argMax() {
		return argMax;
	}
}
