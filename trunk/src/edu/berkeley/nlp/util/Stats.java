package edu.berkeley.nlp.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Stats {

	double sum = 0.0;
	double max = Double.NEGATIVE_INFINITY;
	double min = Double.POSITIVE_INFINITY;
	double sumSquared = 0.0;
	boolean doQuartile;
	List<Double> elems;
	int count = 0;

	public Stats(boolean doQuatrtitle) {
		this.doQuartile = doQuatrtitle;
	}

	public Stats() {
		this(false);
	}

	public double getSum() {
		return sum;
	}

	public void observe(double x) {
		sum += x;
		max = Math.max(max, x);
		min = Math.min(min, x);
		sumSquared += x * x;
		count++;
		if (doQuartile) {
			if (elems == null) {
				elems = new ArrayList<Double>();
			}
			elems.add(x);
		}
	}

	public List<Double> getQuantiles(int n) {
		if (!doQuartile) {
			throw new IllegalStateException();
		}
		double frac = 1.0 / n;
		Collections.sort(elems);
		List<Double> quantiles = new ArrayList<Double>();
		for (int i = 0; i < n; ++i) {
			double farToGo = (i + 1) * frac;
			assert farToGo <= 1.0;
			int pos = (int) (farToGo * (elems.size() - 1));
			assert pos < elems.size() : "illegal pos " + pos;
			quantiles.add(elems.get(pos));
		}
		return quantiles;
	}

	public double getMax() {
		return max;
	}

	public double getMin() {
		return min;
	}

	public double getAverage() {
		return sum / count;
	}

	public double getVariance() {
		return sumSquared / count - Math.pow(getAverage(), 2.0);
	}

	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append(String.format(
				"min: %.3f max: %.3f avg: %.3f var: %.3f n: %d", min, max,
				getAverage(), getVariance(), count));
		return builder.toString();
	}

}
