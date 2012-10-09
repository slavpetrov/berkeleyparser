package edu.berkeley.nlp.classify;

import java.util.Collection;
import java.util.List;

import edu.berkeley.nlp.math.CachingDifferentiableFunction;
import edu.berkeley.nlp.math.GradientMinimizer;
import edu.berkeley.nlp.math.LBFGSMinimizer;
import edu.berkeley.nlp.util.CollectionUtils;
import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.Pair;

public class LinearRegression<I> {

	private FeatureExtractor<I, String> featureExtractor;
	private double[] weights;
	private FeatureManager featureManager;

	public static class Factory<I> {

		double[] weights;
		FeatureManager featureManager;
		FeatureExtractor<I, String> featureExtractor;
		Collection<Pair<I, Double>> trainingData;

		public Factory(FeatureExtractor<I, String> featureExtractor) {
			this.featureExtractor = featureExtractor;
			this.featureManager = new FeatureManager();
		}

		private Counter<Feature> getFeatures(I input) {
			Counter<String> strCounts = featureExtractor.extractFeatures(input);
			Counter<Feature> featCounts = new Counter<Feature>();
			for (String f : strCounts.keySet()) {
				double count = strCounts.getCount(f);
				Feature feat = featureManager.getFeature(f);
				featCounts.setCount(feat, count);
			}
			return featCounts;
		}

		private double getScore(Counter<Feature> featureCounts) {
			double score = 0.0;
			for (Feature feat : featureCounts.keySet()) {
				double count = featureCounts.getCount(feat);
				score += count * weights[feat.getIndex()];
			}
			return score;
		}

		private class ObjectiveFunction extends CachingDifferentiableFunction {

			@Override
			protected Pair<Double, double[]> calculate(double[] x) {
				weights = x;

				double objective = 0.0;
				double[] gradient = new double[dimension()];

				for (Pair<I, Double> datum : trainingData) {
					I input = datum.getFirst();
					Counter<Feature> featCounts = getFeatures(input);
					double guessResponse = getScore(featCounts);
					double goldResponse = datum.getSecond();
					double diff = (guessResponse - goldResponse);
					objective += 0.5 * diff * diff;
					for (Feature feat : featCounts.keySet()) {
						double count = featCounts.getCount(feat);
						gradient[feat.getIndex()] += count * diff;
					}
				}

				// TODO Auto-generated method stub
				return Pair.newPair(objective, gradient);
			}

			@Override
			public int dimension() {
				// TODO Auto-generated method stub
				return featureManager.getNumFeatures();
			}

			public double[] unregularizedDerivativeAt(double[] x) {
				// TODO Auto-generated method stub
				return null;
			}

		}

		private void extractAllFeatures() {
			for (Pair<I, Double> datum : trainingData) {
				Counter<String> counts = featureExtractor.extractFeatures(datum
						.getFirst());
				for (String f : counts.keySet()) {
					featureManager.getFeature(f);
				}
			}
			featureManager.lock();
		}

		private String examineWeights() {
			Counter<Feature> counts = new Counter<Feature>();
			for (int i = 0; i < weights.length; ++i) {
				Feature feat = featureManager.getFeature(i);
				counts.setCount(feat, weights[i]);
			}
			return counts.toString();
		}

		public LinearRegression<I> train(
				Collection<Pair<I, Double>> trainingData) {
			this.trainingData = trainingData;
			extractAllFeatures();
			ObjectiveFunction objFn = new ObjectiveFunction();
			GradientMinimizer gradMinimizer = new LBFGSMinimizer();
			double[] initial = new double[objFn.dimension()];
			this.weights = gradMinimizer.minimize(objFn, initial, 1.0e-4);
			return new LinearRegression<I>(featureExtractor, featureManager,
					weights);
		}

	}

	private LinearRegression(FeatureExtractor<I, String> featureExtractor,
			FeatureManager featureManager, double[] weights) {
		this.featureExtractor = featureExtractor;
		this.featureManager = featureManager;
		this.weights = weights;
	}

	public double getResponse(I input) {
		Counter<String> featCounts = featureExtractor.extractFeatures(input);
		double score = 0.0;
		for (String f : featCounts.keySet()) {
			double count = featCounts.getCount(f);
			Feature feat = featureManager.getFeature(f);
			score += count * weights[feat.getIndex()];
		}
		return score;
	}

	public static void main(String[] args) {
		List<String> elem1 = CollectionUtils.makeList("a", "b", "c");
		List<String> elem2 = CollectionUtils.makeList("a", "b");
		Pair<List<String>, Double> d1 = Pair.newPair(elem1, 3.0);
		Pair<List<String>, Double> d2 = Pair.newPair(elem2, 2.0);
		FeatureExtractor<List<String>, String> featExtractor = new FeatureExtractor<List<String>, String>() {

			public Counter<String> extractFeatures(List<String> instance) {
				Counter<String> counts = new Counter<String>();
				for (String elem : instance) {
					counts.incrementCount(elem, 1.0);
				}
				// TODO Auto-generated method stub
				return counts;
			}
		};
		LinearRegression.Factory<List<String>> factory = new LinearRegression.Factory<List<String>>(
				featExtractor);
		List<Pair<List<String>, Double>> datums = CollectionUtils.makeList(d1,
				d2);
		LinearRegression<List<String>> linearRegressionModel = factory
				.train(datums);
		double guess = linearRegressionModel.getResponse(elem1);
		System.out.println("guess: " + guess);
	}

}
