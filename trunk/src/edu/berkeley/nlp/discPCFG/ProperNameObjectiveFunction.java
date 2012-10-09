package edu.berkeley.nlp.discPCFG;

import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.math.SloppyMath;
import edu.berkeley.nlp.util.Pair;

/**
 * @author petrov
 *
 */

/**
 * This is the MaximumEntropy objective function: the (negative) log conditional
 * likelihood of the training data, possibly with a penalty for large weights.
 * Note that this objective get MINIMIZED so it's the negative of the objective
 * we normally think of.
 */
public class ProperNameObjectiveFunction<F, L> implements ObjectiveFunction {
	IndexLinearizer indexLinearizer;
	Encoding<F, L> encoding;
	EncodedDatum[] data;
	double[] x;

	double sigma;

	double lastValue;
	double[] lastDerivative;
	double[] lastX;
	boolean isUpToDate;

	public void shutdown() {

	}

	public void updateGoldCountsNextRound() {

	}

	public int dimension() {
		return indexLinearizer.getNumLinearIndexes();
	}

	public double valueAt(double[] x) {
		ensureCache(x);
		isUpToDate = false;
		return lastValue;
	}

	public double[] derivativeAt(double[] x) {
		ensureCache(x);
		isUpToDate = false;
		return lastDerivative;
	}

	public double[] unregularizedDerivativeAt(double[] x) {
		return null;
	}

	private void ensureCache(double[] x) {
		if (!isUpToDate) { // requiresUpdate(lastX, x)) {
			this.x = x;
			Pair<Double, double[]> currentValueAndDerivative = calculate();
			lastValue = currentValueAndDerivative.getFirst();
			lastDerivative = currentValueAndDerivative.getSecond();
			lastX = x;
		}
	}

	/*
	 * private boolean requiresUpdate(double[] lastX, double[] x) { if (lastX ==
	 * null) return true; for (int i = 0; i < x.length; i++) { if (lastX[i] !=
	 * x[i]) return true; } return false; }
	 */

	public void setX(double[] x) {
		this.x = x;
	}

	public void isUpToDate(boolean b) {
		isUpToDate = b;
	}

	/**
	 * The most important part of the classifier learning process! This method
	 * determines, for the given weight vector x, what the (negative) log
	 * conditional likelihood of the data is, as well as the derivatives of that
	 * likelihood wrt each weight parameter.
	 */
	public Pair<Double, double[]> calculate() {
		double objective = 0.0;
		System.out.println("In Calculate...");

		double[] derivatives = DoubleArrays.constantArray(0.0, dimension());
		int numSubLabels = encoding.getNumSubLabels();
		int numData = data.length;
		for (int l = 0; l < numData; ++l) {
			EncodedDatum datum = data[l];
			double[] logProbabilities = getLogProbabilities(datum, x, encoding,
					indexLinearizer);
			int C = datum.getLabelIndex();
			double[] labelWeights = datum.getWeights();
			int numSubstatesC = labelWeights.length;
			int substate0 = encoding.getLabelSubindexBegin(C);
			for (int c = 0; c < numSubstatesC; c++) { // For each substate of
														// label C
				objective -= labelWeights[c] * logProbabilities[substate0 + c];
			}
			// Convert to probabilities:
			double[] probabilities = new double[numSubLabels];
			double sum = 0.0;
			for (int c = 0; c < numSubLabels; ++c) { // For each substate
				probabilities[c] = Math.exp(logProbabilities[c]);
				sum += probabilities[c];
			}
			if (Math.abs(sum - 1.0) > 1e-3) {
				System.err.println("Probabilities do not sum to 1!");
			}
			// Compute derivatives:
			for (int i = 0; i < datum.getNumActiveFeatures(); ++i) {
				int featureIndex = datum.getFeatureIndex(i);
				double featureCount = datum.getFeatureCount(i);
				for (int c = 0; c < numSubLabels; ++c) { // For each substate
					int index = indexLinearizer.getLinearIndex(featureIndex, c);
					derivatives[index] += featureCount * probabilities[c];
				}
				for (int c = 0; c < numSubstatesC; c++) { // For each substate
															// of label C
					int index = indexLinearizer.getLinearIndex(featureIndex,
							substate0 + c);
					derivatives[index] -= labelWeights[c] * featureCount;
				}
			}
		}

		// Incorporate penalty terms (regularization) into the objective and
		// derivatives
		double sigma2 = sigma * sigma;
		double penalty = 0.0;
		for (int index = 0; index < x.length; ++index) {
			penalty += x[index] * x[index];
		}
		objective += penalty / (2 * sigma2);

		for (int index = 0; index < x.length; ++index) {
			// 'x' and 'derivatives' have same layout
			derivatives[index] += x[index] / sigma2;
		}
		return new Pair<Double, double[]>(objective, derivatives);
	}

	/**
	 * Calculate the log probabilities of each class, for the given datum
	 * (feature bundle).
	 */
	public <F, L> double[] getLogProbabilities(EncodedDatum datum,
			double[] weights, Encoding<F, L> encoding,
			IndexLinearizer indexLinearizer) {
		// Compute unnormalized log probabilities
		int numSubLabels = encoding.getNumSubLabels();
		double[] logProbabilities = DoubleArrays.constantArray(0.0,
				numSubLabels);
		for (int i = 0; i < datum.getNumActiveFeatures(); i++) {
			int featureIndex = datum.getFeatureIndex(i);
			double featureCount = datum.getFeatureCount(i);
			for (int j = 0; j < numSubLabels; j++) {
				int index = indexLinearizer.getLinearIndex(featureIndex, j);
				double weight = weights[index];
				logProbabilities[j] += weight * featureCount;
			}
		}
		// Normalize
		double logNormalizer = SloppyMath.logAdd(logProbabilities);
		for (int i = 0; i < numSubLabels; i++) {
			logProbabilities[i] -= logNormalizer;
		}

		return logProbabilities;
	}

	public ProperNameObjectiveFunction(Encoding<F, L> encoding,
			EncodedDatum[] data, IndexLinearizer indexLinearizer, double sigma) {
		this.indexLinearizer = indexLinearizer;
		this.encoding = encoding;
		this.data = data;
		this.sigma = sigma;
	}
}
