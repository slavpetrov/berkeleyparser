package edu.berkeley.nlp.classify;

import java.util.List;

/**
 * Probabilistic classifier factories construct probabilistic classifiers from training instances.
 */
public interface ProbabilisticClassifierFactory<I,L> {
  ProbabilisticClassifier<I,L> trainClassifier(List<LabeledInstance<I,L>> trainingData);
}
