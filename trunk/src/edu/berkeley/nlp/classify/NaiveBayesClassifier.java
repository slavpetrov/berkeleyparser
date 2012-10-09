package edu.berkeley.nlp.classify;

import java.util.ArrayList;
import java.util.List;

import edu.berkeley.nlp.math.SloppyMath;
import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.CounterMap;

public class NaiveBayesClassifier<I, F, L> implements
		ProbabilisticClassifier<I, L> {

	private CounterMap<L, F> featureProbs;
	private Counter<F> backoffProbs;
	private Counter<L> labelProbs;
	private FeatureExtractor<I, F> featureExtractor;
	private double alpha = 0.1;

	public static class Factory<I, F, L> implements
			ProbabilisticClassifierFactory<I, L> {

		private FeatureExtractor<I, F> featureExtractor;

		public Factory(FeatureExtractor<I, F> featureExtractor) {
			this.featureExtractor = featureExtractor;
		}

		public ProbabilisticClassifier<I, L> trainClassifier(
				List<LabeledInstance<I, L>> trainingData) {
			CounterMap<L, F> featureProbs = new CounterMap<L, F>();
			Counter<F> backoffProbs = new Counter<F>();
			Counter<L> labelProbs = new Counter<L>();
			for (LabeledInstance<I, L> instance : trainingData) {
				L label = instance.getLabel();
				labelProbs.incrementCount(label, 1.0);
				I inst = instance.getInput();
				Counter<F> featCounts = featureExtractor.extractFeatures(inst);
				for (F feat : featCounts.keySet()) {
					double count = featCounts.getCount(feat);
					backoffProbs.incrementCount(feat, count);
					featureProbs.incrementCount(label, feat, count);
				}
			}
			featureProbs.normalize();
			labelProbs.normalize();
			backoffProbs.normalize();
			return new NaiveBayesClassifier<I, F, L>(featureProbs,
					backoffProbs, labelProbs, featureExtractor);
		}

	}

	public Counter<L> getProbabilities(I instance) {
		Counter<L> posteriors = new Counter<L>();
		List<Double> logPosteriorsUnnormed = new ArrayList<Double>();
		for (L label : labelProbs.keySet()) {
			double logPrior = Math.log(labelProbs.getCount(label));
			double logPosteriorUnnorm = logPrior;
			Counter<F> featCounts = featureExtractor.extractFeatures(instance);
			for (F feat : featCounts.keySet()) {
				double count = featCounts.getCount(feat);
				logPosteriorUnnorm += count
						* Math.log(getFeatureProb(feat, label));
			}
			logPosteriorsUnnormed.add(logPosteriorUnnorm);
			posteriors.setCount(label, logPosteriorUnnorm);
		}
		double logPosteriorNorm = SloppyMath.logAdd(logPosteriorsUnnormed);
		for (L label : labelProbs.keySet()) {
			double logPosteriorUnnorm = posteriors.getCount(label);
			double logPosterior = logPosteriorUnnorm - logPosteriorNorm;
			double posterior = Math.exp(logPosterior);
			posteriors.setCount(label, posterior);
		}
		// TODO Auto-generated method stub
		return posteriors;
	}

	private double getFeatureProb(F feat, L label) {
		double mleProb = featureProbs.getCount(label, feat);
		double backoffProb = backoffProbs.getCount(feat);
		return (1 - alpha) * mleProb + alpha * backoffProb;
	}

	public L getLabel(I instance) {
		// TODO Auto-generated method stub
		return getProbabilities(instance).argMax();
	}

	public NaiveBayesClassifier(CounterMap<L, F> featureProbs,
			Counter<F> backoffProbs, Counter<L> labelProbs,
			FeatureExtractor<I, F> featureExtractor) {
		super();
		this.featureProbs = featureProbs;
		this.backoffProbs = backoffProbs;
		this.labelProbs = labelProbs;
		this.featureExtractor = featureExtractor;
	}

}
