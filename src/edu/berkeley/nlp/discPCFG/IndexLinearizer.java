/**
 * 
 */
package edu.berkeley.nlp.discPCFG;

/**
 * @author petrov
 *
 */
/**
 * The IndexLinearizer maintains the linearization of the two-dimensional
 * features-by-labels pair space. This is because, while we might think about
 * lambdas and derivatives as being indexed by a feature-label pair, the
 * optimization code expects one long vector for lambdas and derivatives. To go
 * from a pair featureIndex, labelIndex to a single pairIndex, use
 * getLinearIndex().
 */
public class IndexLinearizer {
	int numFeatures;
	int numLabels;

	public int getNumLinearIndexes() {
		return numFeatures * numLabels;
	}

	public int getLinearIndex(int featureIndex, int labelIndex) {
		return labelIndex + featureIndex * numLabels;
	}

	public int getFeatureIndex(int linearIndex) {
		return linearIndex / numLabels;
	}

	public int getLabelIndex(int linearIndex) {
		return linearIndex % numLabels;
	}

	public IndexLinearizer(int numFeatures, int numLabels) {
		this.numFeatures = numFeatures;
		this.numLabels = numLabels;
	}
}
