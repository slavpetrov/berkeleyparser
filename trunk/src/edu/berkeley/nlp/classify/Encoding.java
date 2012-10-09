package edu.berkeley.nlp.classify;

import java.io.Serializable;

import edu.berkeley.nlp.util.Indexer;

/**
 * The Encoding maintains correspondences between the various representions of
 * the data, labels, and features. The external representations of labels and
 * features are object-based. The functions getLabelIndex() and
 * getFeatureIndex() can be used to translate those objects to integer
 * representatiosn: numbers between 0 and getNumLabels() or getNumFeatures()
 * (exclusive). The inverses of this map are the getLabel() and getFeature()
 * functions.
 */
public class Encoding<F, L> implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6349512704632759684L;
	Indexer<F> featureIndexer;
	Indexer<L> labelIndexer;

	public int getNumFeatures() {
		return featureIndexer.size();
	}

	public boolean hasFeature(F feature) {
		return featureIndexer.contains(feature);
	}

	public int getFeatureIndex(F feature) {
		return featureIndexer.indexOf(feature);
	}

	public F getFeature(int featureIndex) {
		return featureIndexer.getObject(featureIndex);
	}

	public int getNumLabels() {
		return labelIndexer.size();
	}

	public int getLabelIndex(L label) {
		return labelIndexer.indexOf(label);
	}

	public L getLabel(int labelIndex) {
		return labelIndexer.getObject(labelIndex);
	}

	public Encoding(Indexer<F> featureIndexer, Indexer<L> labelIndexer) {
		this.featureIndexer = featureIndexer;
		this.labelIndexer = labelIndexer;
	}
}