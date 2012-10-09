package edu.berkeley.nlp.util;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;

public class TransformingSet<K, O> extends AbstractSet<O> {

	private Set<K> baseSet;

	private MyMethod<K, O> transform;

	/**
	 * @param baseSet
	 */
	public TransformingSet(Set<K> baseSet, MyMethod<K, O> transform) {
		super();
		this.baseSet = baseSet;
		this.transform = transform;
	}

	@Override
	public Iterator<O> iterator() {
		return new Iterators.TransformingIterator<K, O>(baseSet.iterator(),
				transform);
	}

	@Override
	public int size() {
		return baseSet.size();
	}

}
