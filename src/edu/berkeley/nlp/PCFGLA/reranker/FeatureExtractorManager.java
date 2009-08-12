/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import java.io.Serializable;

import fig.basic.Indexer;

/**
 * Class for holding local and non-local feature extractors, and maintaining a global index of
 * features.
 * 
 * @author rafferty
 *
 */
public class FeatureExtractorManager {
	private LocalFeatureExtractor locExtractor;
	private NonlocalFeatureExtractor nonLocExtractor;
	private AnticipatedFeatureExtractor antExtractor;
	Indexer<Feature> features;
	
	
	public FeatureExtractorManager () { }
	/**
	 * Creates a new feature extractor manager with local and nonlocal feature extractors.
	 * TODO: this may not be the best way to do this.
	 * @param locExtractor
	 * @param nonLocExtractor
	 */
	public FeatureExtractorManager(BaseModel baseModel) {
		features = new Indexer<Feature>();
		features.add(new ScoreFeature());
		this.locExtractor = new LocalFeatureExtractors(features, baseModel);
		this.nonLocExtractor = new NonlocalFeatureExtractors(features, baseModel);
		//this.locExtractor = new CheatingLocalFeatureExtractor(new OracleTreeFinder(baseModel), baseModel, features);
		//this.nonLocExtractor = new DummyFeatureExtractor();
		this.antExtractor = null;
	}
	
	public FeatureExtractorManager(BaseModel baseModel, Indexer<Feature> featureIndexer) {
		features = featureIndexer;
		this.locExtractor = new LocalFeatureExtractors(features, baseModel);
		this.nonLocExtractor = new NonlocalFeatureExtractors(features, baseModel);
		//this.locExtractor = new CheatingLocalFeatureExtractor(new OracleTreeFinder(baseModel), baseModel, featureIndexer);
		//this.nonLocExtractor = new DummyFeatureExtractor();
		this.antExtractor = null;
	}
	
	public FeatureExtractorManager(Indexer<Feature> features, LocalFeatureExtractor locExtractor,
			NonlocalFeatureExtractor nonLocExtractor) {
		this.features = features;
		this.locExtractor = locExtractor;
		this.nonLocExtractor = nonLocExtractor;
		this.antExtractor = null;
	}
	
	/**
	 * Returns the total number of local and non-local features
	 * @return
	 */
	public int getTotalNumFeatures() {
		return features.size();
	}
	
	public Feature getFeatureByNumber(int featureNumber) {
		return features.getObject(featureNumber);
	}
	
	public LocalFeatureExtractor getLocalFeatureExtractor() {
		return this.locExtractor;
	}
	
	public NonlocalFeatureExtractor getNonlocalFeatureExtractor() {
		return this.nonLocExtractor;
	}
	
	public AnticipatedFeatureExtractor getAnticipatedFeatureExtractor() {
		return this.antExtractor;
	}
	
	public boolean hasAnticipatedFeatureExtractor() {
		return this.antExtractor != null;
	}

	public static interface Feature extends Serializable { 	}

	/** Solely for serializability...*/
	public static class ScoreFeature implements Feature {

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L; 	}

}
