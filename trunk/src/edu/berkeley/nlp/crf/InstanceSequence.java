package edu.berkeley.nlp.crf;

public interface InstanceSequence<V, E, L> {
	int getSequenceLength();

	V getVertexInstance(int index);

	E getEdgeInstance(int index, L previousLabel);
}
