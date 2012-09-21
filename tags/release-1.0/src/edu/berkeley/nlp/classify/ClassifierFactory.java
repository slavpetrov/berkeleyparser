package edu.berkeley.nlp.classify;

import java.util.List;

/**
 * Classifier factories construct classifiers from training instances.
 */
public interface ClassifierFactory<I,L> {
  Classifier<I,L> trainClassifier(List<LabeledInstance<I,L>> trainingData);
}
