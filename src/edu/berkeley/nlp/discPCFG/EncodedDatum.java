/**
 * 
 */
package edu.berkeley.nlp.discPCFG;

import edu.berkeley.nlp.util.Counter;

/**
 * EncodedDatums are sparse representations of (labeled) feature count vectors
 * for a given data point. Use getNumActiveFeatures() to see how many features
 * have non-zero count in a datum. Then, use getFeatureIndex() and
 * getFeatureCount() to retreive the number and count of each non-zero feature.
 * Use getLabelIndex() to get the label's number.
 */
public class EncodedDatum {

	public static <F, L> EncodedDatum encodeDatum(Encoding<F, L> encoding,
			Counter<F> features) {
		return encodeLabeledDatum(encoding, features, null, null);
	}

	public static <F, L> EncodedDatum encodeLabeledDatum(
			Encoding<F, L> encoding, Counter<F> features, L label,
			double[] weights) {
		Counter<F> knownFeatures = new Counter<F>();
		for (F feature : features.keySet()) {
			if (encoding.getFeatureIndex(feature) < 0)
				continue;
			knownFeatures.incrementCount(feature, features.getCount(feature));
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
		int labelIndex = encoding.getLabelIndex(label);
		EncodedDatum encodedDatum = new EncodedDatum(labelIndex,
				featureIndexes, featureCounts, weights);
		return encodedDatum;
	}

	int labelIndex;
	int[] featureIndexes;
	double[] featureCounts;
	double[] weights; // the probability of each substate of the label (allows
						// partial labeling)

	public int getLabelIndex() {
		return labelIndex;
	}

	public double[] getWeights() {
		return weights;
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
			double[] featureCounts, double[] weights) {
		this.labelIndex = labelIndex;
		this.featureIndexes = featureIndexes;
		this.featureCounts = featureCounts;
		this.weights = weights;
	}
}