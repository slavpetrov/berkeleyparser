package edu.berkeley.nlp.classify;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.berkeley.nlp.math.DifferentiableFunction;
import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.math.LBFGSMinimizer;
import edu.berkeley.nlp.math.SloppyMath;
import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.Indexer;
import edu.berkeley.nlp.util.Logger;
import edu.berkeley.nlp.util.Pair;

/**
 * Maximum entropy classifier for assignment 2.
 * 
 * @author Dan Klein
 */
public class MaximumEntropyClassifier<I, F, L> implements
		ProbabilisticClassifier<I, L>, Serializable {

	private static final long serialVersionUID = 1L;

	/**
	 * Factory for training MaximumEntropyClassifiers.
	 */
	public static class Factory<I, F, L> implements
			ProbabilisticClassifierFactory<I, L> {

		double sigma;
		int iterations;
		FeatureExtractor<I, F> featureExtractor;

		public ProbabilisticClassifier<I, L> trainClassifier(
				List<LabeledInstance<I, L>> trainingData) {
			return trainClassifier(trainingData, true);
		}

		public ProbabilisticClassifier<I, L> trainClassifier(
				List<LabeledInstance<I, L>> trainingData, boolean verbose) {
			// build data encodings so the inner loops can be efficient
			if (verbose)
				Logger.i().startTrack("Building encoding");
			Encoding<F, L> encoding = buildEncoding(trainingData);
			IndexLinearizer indexLinearizer = buildIndexLinearizer(encoding);
			double[] initialWeights = buildInitialWeights(indexLinearizer);
			EncodedDatum[] data = encodeData(trainingData, encoding);
			if (verbose)
				Logger.i().endTrack();

			// build a minimizer object
			LBFGSMinimizer minimizer = new LBFGSMinimizer(iterations);
			// build the objective function for this data
			DifferentiableFunction objective = new ObjectiveFunction<F, L>(
					encoding, data, indexLinearizer, sigma);

			// learn our voting weights
			if (verbose)
				Logger.i().startTrack("Training weights");
			double[] weights = minimizer.minimize(objective, initialWeights,
					1e-4, verbose);
			if (verbose)
				Logger.i().endTrack();

			// build a classifer using these weights (and the data encodings)
			return new MaximumEntropyClassifier<I, F, L>(weights, encoding,
					indexLinearizer, featureExtractor);
		}

		private double[] buildInitialWeights(IndexLinearizer indexLinearizer) {
			return DoubleArrays.constantArray(0.0,
					indexLinearizer.getNumLinearIndexes());
		}

		private IndexLinearizer buildIndexLinearizer(Encoding<F, L> encoding) {
			return new IndexLinearizer(encoding.getNumFeatures(),
					encoding.getNumLabels());
		}

		private Encoding<F, L> buildEncoding(List<LabeledInstance<I, L>> data) {
			Indexer<F> featureIndexer = new Indexer<F>();
			Indexer<L> labelIndexer = new Indexer<L>();
			for (LabeledInstance<I, L> labeledInstance : data) {
				L label = labeledInstance.getLabel();
				Counter<F> features = featureExtractor
						.extractFeatures(labeledInstance.getInput());
				LabeledFeatureVector<F, L> labeledDatum = new BasicLabeledFeatureVector<F, L>(
						label, features);
				labelIndexer.getIndex(labeledDatum.getLabel());
				for (F feature : labeledDatum.getFeatures().keySet()) {
					featureIndexer.getIndex(feature);
				}
			}
			return new Encoding<F, L>(featureIndexer, labelIndexer);
		}

		private EncodedDatum[] encodeData(List<LabeledInstance<I, L>> data,
				Encoding<F, L> encoding) {
			EncodedDatum[] encodedData = new EncodedDatum[data.size()];
			for (int i = 0; i < data.size(); i++) {
				LabeledInstance<I, L> labeledInstance = data.get(i);
				L label = labeledInstance.getLabel();
				Counter<F> features = featureExtractor
						.extractFeatures(labeledInstance.getInput());
				LabeledFeatureVector<F, L> labeledFeatureVector = new BasicLabeledFeatureVector<F, L>(
						label, features);
				encodedData[i] = EncodedDatum.encodeLabeledDatum(
						labeledFeatureVector, encoding);
			}
			return encodedData;
		}

		/**
		 * Sigma controls the variance on the prior / penalty term. 1.0 is a
		 * reasonable value for large problems, bigger sigma means LESS
		 * smoothing. Zero sigma is a special indicator that no smoothing is to
		 * be done.
		 * <p/>
		 * Iterations determines the maximum number of iterations the
		 * optimization code can take before stopping.
		 */
		public Factory(double sigma, int iterations,
				FeatureExtractor<I, F> featureExtractor) {
			this.sigma = sigma;
			this.iterations = iterations;
			this.featureExtractor = featureExtractor;
		}
	}

	/**
	 * This is the MaximumEntropy objective function: the (negative) log
	 * conditional likelihood of the training data, possibly with a penalty for
	 * large weights. Note that this objective get MINIMIZED so it's the
	 * negative of the objective we normally think of.
	 */
	public static class ObjectiveFunction<F, L> implements
			DifferentiableFunction {
		IndexLinearizer indexLinearizer;
		Encoding<F, L> encoding;
		EncodedDatum[] data;

		double sigma;

		double lastValue;
		double[] lastDerivative;
		double[] lastX;

		public int dimension() {
			return indexLinearizer.getNumLinearIndexes();
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

		/**
		 * The most important part of the classifier learning process! This
		 * method determines, for the given weight vector x, what the (negative)
		 * log conditional likelihood of the data is, as well as the derivatives
		 * of that likelihood wrt each weight parameter.
		 */
		private Pair<Double, double[]> calculate(double[] x) {
			double objective = 0.0;
			double[] derivatives = DoubleArrays.constantArray(0.0, dimension());

			double[] classActivations = new double[encoding.getNumLabels()];
			double[] classPosteriors = new double[encoding.getNumLabels()];

			for (EncodedDatum datum : data) {
				// For each datum we get the activation for each class
				// and then the posteriors
				int numActiveFeatures = datum.getNumActiveFeatures();
				for (int labelIndex = 0; labelIndex < encoding.getNumLabels(); ++labelIndex) {
					double activation = 0.0;
					for (int num = 0; num < numActiveFeatures; ++num) {
						int featureIndex = datum.getFeatureIndex(num);
						double featureCount = datum.getFeatureCount(num);
						int linearFeatureIndex = indexLinearizer
								.getLinearIndex(featureIndex, labelIndex);
						activation += x[linearFeatureIndex] * featureCount;
					}
					classActivations[labelIndex] = activation;
				}
				double logSumActivation = SloppyMath.logAdd(classActivations);
				int correctLabelIndex = datum.getLabelIndex();
				// Log Prob
				objective += (classActivations[correctLabelIndex] - logSumActivation);
				// Class Posteriors
				for (int labelIndex = 0; labelIndex < encoding.getNumLabels(); ++labelIndex) {
					classPosteriors[labelIndex] = SloppyMath
							.exp(classActivations[labelIndex]
									- logSumActivation);
				}
				// Derivative: Feature Expectations
				for (int num = 0; num < numActiveFeatures; ++num) {
					int featureIndex = datum.getFeatureIndex(num);
					int correctLinearFeatureIndex = indexLinearizer
							.getLinearIndex(featureIndex, correctLabelIndex);
					double featureCount = datum.getFeatureCount(num);
					derivatives[correctLinearFeatureIndex] += featureCount;
					for (int labelIndex = 0; labelIndex < encoding
							.getNumLabels(); ++labelIndex) {
						int linearFeatureIndex = indexLinearizer
								.getLinearIndex(featureIndex, labelIndex);
						double classProb = classPosteriors[labelIndex];
						derivatives[linearFeatureIndex] -= classProb
								* featureCount;
					}
				}
			}

			// Scale by -1 since we are minimizing negative log-liklihood
			objective *= -1;
			DoubleArrays.scale(derivatives, -1);

			// L2 Penalty
			for (int i = 0; i < x.length; ++i) {
				double weight = x[i];
				objective += (weight * weight) / (2 * sigma * sigma);
				derivatives[i] += (weight) / (sigma * sigma);
			}

			return new Pair<Double, double[]>(objective, derivatives);
		}

		public ObjectiveFunction(Encoding<F, L> encoding, EncodedDatum[] data,
				IndexLinearizer indexLinearizer, double sigma) {
			this.indexLinearizer = indexLinearizer;
			this.encoding = encoding;
			this.data = data;
			this.sigma = sigma;
		}

		public double[] unregularizedDerivativeAt(double[] x) {
			// TODO Auto-generated method stub
			return null;
		}
	}

	/**
	 * EncodedDatums are sparse representations of (labeled) feature count
	 * vectors for a given data point. Use getNumActiveFeatures() to see how
	 * many features have non-zero count in a datum. Then, use getFeatureIndex()
	 * and getFeatureCount() to retreive the number and count of each non-zero
	 * feature. Use getLabelIndex() to get the label's number.
	 */
	public static class EncodedDatum {

		public static <F, L> EncodedDatum encodeDatum(
				FeatureVector<F> featureVector, Encoding<F, L> encoding) {
			Counter<F> features = featureVector.getFeatures();
			Counter<F> knownFeatures = new Counter<F>();
			for (F feature : features.keySet()) {
				if (encoding.getFeatureIndex(feature) < 0)
					continue;
				knownFeatures.incrementCount(feature,
						features.getCount(feature));
			}
			int numActiveFeatures = knownFeatures.keySet().size();
			int[] featureIndexes = new int[numActiveFeatures];
			double[] featureCounts = new double[knownFeatures.keySet().size()];
			int i = 0;
			for (F feature : knownFeatures.keySet()) {
				int index = encoding.getFeatureIndex(feature);
				double count = knownFeatures.getCount(feature);
				featureIndexes[i] = index;
				featureCounts[i] = count;
				i++;
			}
			EncodedDatum encodedDatum = new EncodedDatum(-1, featureIndexes,
					featureCounts);
			return encodedDatum;
		}

		public static <F, L> EncodedDatum encodeLabeledDatum(
				LabeledFeatureVector<F, L> labeledDatum, Encoding<F, L> encoding) {
			EncodedDatum encodedDatum = encodeDatum(labeledDatum, encoding);
			encodedDatum.labelIndex = encoding.getLabelIndex(labeledDatum
					.getLabel());
			return encodedDatum;
		}

		int labelIndex;
		int[] featureIndexes;
		double[] featureCounts;

		public int getLabelIndex() {
			return labelIndex;
		}

		public int getNumActiveFeatures() {
			return featureCounts.length;
		}

		public int getFeatureIndex(int num) {
			return featureIndexes[num];
		}

		public double getFeatureCount(int num) {
			return featureCounts[num];
		}

		public EncodedDatum(int labelIndex, int[] featureIndexes,
				double[] featureCounts) {
			this.labelIndex = labelIndex;
			this.featureIndexes = featureIndexes;
			this.featureCounts = featureCounts;
		}
	}

	private double[] weights;
	private Encoding<F, L> encoding;
	private IndexLinearizer indexLinearizer;
	private transient FeatureExtractor<I, F> featureExtractor;

	/**
	 * 
	 */
	public void setFeatureExtractor(FeatureExtractor<I, F> featureExtractor) {
		this.featureExtractor = featureExtractor;
	}

	/**
	 * Calculate the log probabilities of each class, for the given datum
	 * (feature bundle). Note that the weighted votes (refered to as
	 * activations) are *almost* log probabilities, but need to be normalized.
	 */
	private static <F, L> double[] getLogProbabilities(EncodedDatum datum,
			double[] weights, Encoding<F, L> encoding,
			IndexLinearizer indexLinearizer) {

		double[] logProbabilities = new double[encoding.getNumLabels()];
		for (int labelIndex = 0; labelIndex < encoding.getNumLabels(); ++labelIndex) {
			for (int num = 0; num < datum.getNumActiveFeatures(); ++num) {
				int featureIndex = datum.getFeatureIndex(num);
				double featureCount = datum.getFeatureCount(num);
				int linearFeatureIndex = indexLinearizer.getLinearIndex(
						featureIndex, labelIndex);
				logProbabilities[labelIndex] += weights[linearFeatureIndex]
						* featureCount;
			}
		}

		double logSumProb = SloppyMath.logAdd(logProbabilities);
		for (int labelIndex = 0; labelIndex < encoding.getNumLabels(); ++labelIndex) {
			logProbabilities[labelIndex] -= logSumProb;
		}

		return logProbabilities;
	}

	public Counter<L> getProbabilities(I input) {
		FeatureVector<F> featureVector = new BasicFeatureVector<F>(
				featureExtractor.extractFeatures(input));
		return getProbabilities(featureVector);
	}

	private Counter<L> getProbabilities(FeatureVector<F> featureVector) {
		EncodedDatum encodedDatum = EncodedDatum.encodeDatum(featureVector,
				encoding);
		double[] logProbabilities = getLogProbabilities(encodedDatum, weights,
				encoding, indexLinearizer);
		return logProbabiltyArrayToProbabiltyCounter(logProbabilities);
	}

	private Counter<L> logProbabiltyArrayToProbabiltyCounter(
			double[] logProbabilities) {
		Counter<L> probabiltyCounter = new Counter<L>();
		for (int labelIndex = 0; labelIndex < logProbabilities.length; labelIndex++) {
			double logProbability = logProbabilities[labelIndex];
			double probability = Math.exp(logProbability);
			L label = encoding.getLabel(labelIndex);
			probabiltyCounter.setCount(label, probability);
		}
		return probabiltyCounter;
	}

	public L getLabel(I input) {
		return getProbabilities(input).argMax();
	}

	public MaximumEntropyClassifier(double[] weights, Encoding<F, L> encoding,
			IndexLinearizer indexLinearizer,
			FeatureExtractor<I, F> featureExtractor) {
		this.weights = weights;
		this.encoding = encoding;
		this.indexLinearizer = indexLinearizer;
		this.featureExtractor = featureExtractor;
	}

	public static void main(String[] args) {
		// Execution.init(args);
		// create datums
		LabeledInstance<String[], String> datum1 = new LabeledInstance<String[], String>(
				"cat", new String[] { "fuzzy", "claws", "small" });
		LabeledInstance<String[], String> datum2 = new LabeledInstance<String[], String>(
				"bear", new String[] { "fuzzy", "claws", "big" });
		LabeledInstance<String[], String> datum3 = new LabeledInstance<String[], String>(
				"cat", new String[] { "claws", "medium" });
		LabeledInstance<String[], String> datum4 = new LabeledInstance<String[], String>(
				"cat", new String[] { "claws", "small" });

		// create training set
		List<LabeledInstance<String[], String>> trainingData = new ArrayList<LabeledInstance<String[], String>>();
		trainingData.add(datum1);
		trainingData.add(datum2);
		trainingData.add(datum3);

		// create test set
		List<LabeledInstance<String[], String>> testData = new ArrayList<LabeledInstance<String[], String>>();
		testData.add(datum4);

		// build classifier
		FeatureExtractor<String[], String> featureExtractor = new FeatureExtractor<String[], String>() {
			/**
			 * 
			 */
			private static final long serialVersionUID = 8296036312980792350L;

			public Counter<String> extractFeatures(String[] featureArray) {
				return new Counter<String>(Arrays.asList(featureArray));
			}
		};
		MaximumEntropyClassifier.Factory<String[], String, String> maximumEntropyClassifierFactory = new MaximumEntropyClassifier.Factory<String[], String, String>(
				1.0, 20, featureExtractor);
		ProbabilisticClassifier<String[], String> maximumEntropyClassifier = maximumEntropyClassifierFactory
				.trainClassifier(trainingData);
		System.out.println("Probabilities on test instance: "
				+ maximumEntropyClassifier.getProbabilities(datum4.getInput()));
		// Execution.finish();
	}
}
