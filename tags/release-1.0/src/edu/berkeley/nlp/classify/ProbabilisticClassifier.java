package edu.berkeley.nlp.classify;

import edu.berkeley.nlp.util.Counter;

/**
 * Probabilistic classifiers assign distributions over labels to instances.
 *
 * @author Dan Klein
 */
public interface ProbabilisticClassifier<I,L> extends Classifier<I,L> {
  Counter<L> getProbabilities(I instance);
}
