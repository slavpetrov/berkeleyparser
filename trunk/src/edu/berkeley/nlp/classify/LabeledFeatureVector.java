package edu.berkeley.nlp.classify;

/**
 * LabeledDatums add a label to the basic FeatureVector interface.
 * 
 * @author Dan Klein
 */
public interface LabeledFeatureVector<F,L> extends FeatureVector<F> {
  L getLabel();
}
