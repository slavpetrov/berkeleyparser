package edu.berkeley.nlp.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

public class ConcatenationIterable<T> implements Iterable<T> {

	private Collection<? extends Iterable<T>> iterableColl;

	public ConcatenationIterable(Collection<? extends Iterable<T>> iterableColl) {
		this.iterableColl = iterableColl;
	}

	public ConcatenationIterable(Iterable<T>... iterables) {
		this.iterableColl = Arrays.asList(iterables);
	}

	public Iterator<T> iterator() {
		Collection<Iterator<T>> itColl = new ArrayList<Iterator<T>>();
		for (Iterable<T> iterable : iterableColl) {
			itColl.add(iterable.iterator());
		}
		return new ConcatenationIterator(itColl);
	}

}
