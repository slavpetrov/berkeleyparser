package edu.berkeley.nlp.crf;

public interface LabeledInstanceSequence<V, E, L> extends
		InstanceSequence<V, E, L> {
	L getGoldLabel(int index);
}
