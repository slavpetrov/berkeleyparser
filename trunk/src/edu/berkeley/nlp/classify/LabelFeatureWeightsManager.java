package edu.berkeley.nlp.classify;

import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.Indexer;

/**
 * When you have features that are annotated with labels, use this as a feature
 * manager.
 * 
 * @author aria42
 * 
 */
public class LabelFeatureWeightsManager<L> {

	private FeatureManager featManager;
	private Indexer<L> labels;

	public LabelFeatureWeightsManager(FeatureManager featManager,
			Indexer<L> labels) {
		this.featManager = featManager;
		this.labels = labels;
		if (!featManager.isLocked()) {
			throw new IllegalArgumentException("Feature manager must be locked");
		}
	}

	public int getFeatureLabelWeightIndex(int featureIndex, int labelIndex) {
		assert labelIndex < labels.size() && labelIndex >= 0;
		assert featureIndex < featManager.getNumFeatures() && featureIndex >= 0;
		return featureIndex + featManager.getNumFeatures() * labelIndex;
	}

	public int getWeightIndex(Feature feat, L label) {
		return getFeatureLabelWeightIndex(feat.getIndex(),
				labels.indexOf(label));
	}

	public int getWeightIndex(String feat, L label) {
		int featIndex = featManager.getFeature(feat).getIndex();
		int labelIndex = labels.indexOf(label);
		return getFeatureLabelWeightIndex(featIndex, labelIndex);
	}

	public int getNumWeights() {
		return featManager.getNumFeatures() * labels.size();
	}

	public Feature getBaseFeature(int weightIndex) {
		return featManager.getFeature(weightIndex
				% featManager.getNumFeatures());
	}

	public L getLabel(int weightIndex) {
		return labels.getObject(weightIndex / featManager.getNumFeatures());
	}

	public Counter<String> getWeightsCounter(double[] weights) {
		Counter<String> counts = new Counter<String>();
		for (L label : labels) {
			for (int i = 0; i < featManager.getNumFeatures(); ++i) {
				Feature feat = featManager.getFeature(i);
				int index = getWeightIndex(feat, label);
				String labelFeat = String.format("%s && %s", label, feat);
				counts.setCount(labelFeat, weights[index]);
			}
		}
		return counts;
	}
}
