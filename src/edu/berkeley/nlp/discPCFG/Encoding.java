/**
 * 
 */
package edu.berkeley.nlp.discPCFG;

import edu.berkeley.nlp.util.Indexer;
import edu.berkeley.nlp.util.SubIndexer;

/**
 * The Encoding maintains correspondences between the various representions of
 * the data, labels, and features. The external representations of labels and
 * features are object-based. The functions getLabelIndex() and
 * getFeatureIndex() can be used to translate those objects to integer
 * representatiosn: numbers between 0 and getNumLabels() or getNumFeatures()
 * (exclusive). The inverses of this map are the getLabel() and getFeature()
 * functions.
 */
public class Encoding<F, L> {
	Indexer<F> featureIndexer;
	SubIndexer<L> labelIndexer;

	public int getNumFeatures() {
		return featureIndexer.size();
	}

	public int getFeatureIndex(F feature) {
		return featureIndexer.indexOf(feature);
	}

	public F getFeature(int featureIndex) {
		return featureIndexer.get(featureIndex);
	}

	/** Number of labels (not total number of substates) */
	public int getNumLabels() {
		return labelIndexer.size();
	}

	/** Total number of substates */
	public int getNumSubLabels() {
		return labelIndexer.totalSize();
	}

	public int getLabelIndex(L label) {
		return labelIndexer.indexOf(label);
	}

	public int getLabelSubindexBegin(int labelIndex) {
		return labelIndexer.subindexBegin(labelIndex);
	}

	public int getLabelSubindexEnd(int labelIndex) {
		return labelIndexer.subindexEnd(labelIndex);
	}

	public L getLabel(int labelIndex) {
		return labelIndexer.get(labelIndex);
	}

	public Encoding(Indexer<F> featureIndexer, SubIndexer<L> labelIndexer) {
		this.featureIndexer = featureIndexer;
		this.labelIndexer = labelIndexer;
	}
}
