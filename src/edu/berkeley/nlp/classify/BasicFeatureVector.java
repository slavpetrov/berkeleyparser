package edu.berkeley.nlp.classify;

import edu.berkeley.nlp.util.Counter;

/**
 * Simple Class implementing <code>FeatureVector<F></code>
 * 
 * @author aria42
 * 
 * @param <F>
 *            Type for Feature
 */

public class BasicFeatureVector<F> implements FeatureVector<F> {
	public final Counter<F> features;

	public BasicFeatureVector(Counter<F> features) {
		this.features = features;
	}

	public Counter<F> getFeatures() {
		return features;
	}
}
