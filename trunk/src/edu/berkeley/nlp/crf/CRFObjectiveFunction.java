package edu.berkeley.nlp.crf;

import java.util.List;
import java.util.Map;

import edu.berkeley.nlp.classify.Encoding;
import edu.berkeley.nlp.classify.FeatureExtractor;
import edu.berkeley.nlp.classify.IndexLinearizer;
import edu.berkeley.nlp.math.DifferentiableFunction;
import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.Pair;

public class CRFObjectiveFunction<V, E, F, L> implements DifferentiableFunction {
	private final List<? extends LabeledInstanceSequence<V, E, L>> trainingData;
	private final Encoding<F, L> encoding;
	private final Counts<V, E, F, L> counts;
	private final IndexLinearizer il;
	private final double sigma;

	double lastValue;
	double[] lastDerivative;
	double[] lastX;

	public CRFObjectiveFunction(
			List<? extends LabeledInstanceSequence<V, E, L>> trainingData,
			Encoding<F, L> encoding, FeatureExtractor<V, F> vertexExtractor,
			FeatureExtractor<E, F> edgeExtractor, double sigma) {
		this.trainingData = trainingData;
		this.encoding = encoding;
		this.counts = new Counts<V, E, F, L>(encoding, vertexExtractor,
				edgeExtractor);
		this.il = new IndexLinearizer(encoding.getNumFeatures(),
				encoding.getNumLabels());
		this.sigma = sigma;
	}

	public int dimension() {
		return il.getNumLinearIndexes();
	}

	public double valueAt(double[] x) {
		ensureCache(x);
		return lastValue;
	}

	public double[] derivativeAt(double[] x) {
		ensureCache(x);
		return lastDerivative;
	}

	private void ensureCache(double[] x) {
		if (requiresUpdate(lastX, x)) {
			Pair<Double, double[]> currentValueAndDerivative = calculate(x);
			lastValue = currentValueAndDerivative.getFirst();
			lastDerivative = currentValueAndDerivative.getSecond();
			lastX = x;
		}
	}

	private boolean requiresUpdate(double[] lastX, double[] x) {
		if (lastX == null)
			return true;
		for (int i = 0; i < x.length; i++) {
			if (lastX[i] != x[i])
				return true;
		}
		return false;
	}

	private Pair<Double, double[]> calculate(double[] x) {
		double objective = 0.0;
		double[] derivatives = new double[dimension()];
		List<Counter<F>> empiricalCounts = counts
				.getEmpiricalCounts(trainingData);
		for (int l = 0; l < empiricalCounts.size(); l++) {
			for (Map.Entry<F, Double> entry : empiricalCounts.get(l).entrySet()) {
				int index = il.getLinearIndex(
						encoding.getFeatureIndex(entry.getKey()), l);
				objective -= entry.getValue() * x[index];
				derivatives[index] -= entry.getValue();
			}
		}
		Pair<Double, List<Counter<F>>> results = counts
				.getLogNormalizationAndExpectedCounts(trainingData, x);
		objective += results.getFirst();
		List<Counter<F>> expectedCounts = results.getSecond();
		for (int l = 0; l < expectedCounts.size(); l++) {
			for (Map.Entry<F, Double> entry : expectedCounts.get(l).entrySet()) {
				int index = il.getLinearIndex(
						encoding.getFeatureIndex(entry.getKey()), l);
				derivatives[index] += entry.getValue();
			}
		}
		for (int i = 0; i < x.length; ++i) {
			double weight = x[i];
			objective += (weight * weight) / (2 * sigma * sigma);
			derivatives[i] += (weight) / (sigma * sigma);
		}
		return Pair.makePair(objective, derivatives);
	}
}
