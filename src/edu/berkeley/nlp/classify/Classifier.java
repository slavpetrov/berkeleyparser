package edu.berkeley.nlp.classify;

/**
 * Classifiers assign labels to instances.
 *
 * @author Dan Klein
 */
public interface Classifier<I,L> {
  L getLabel(I instance);
}
