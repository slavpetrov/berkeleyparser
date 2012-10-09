package edu.berkeley.nlp.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Iterates over a list of lists, returning the (a list containing) the
 * correspondings element in each list at each iteration. Assumes each list has
 * the same length.
 * 
 * For example,
 * 
 * @author adpauls
 * 
 * @param <T>
 */
public class CorrespondingIterable<T> implements Iterable<List<T>> {

	private Iterable<T>[] iterables;

	public CorrespondingIterable(Iterable<T>... iterables) {
		this.iterables = iterables;
	}

	private class ThisIterator implements Iterator<List<T>> {

		List<Iterator<T>> iters;

		public ThisIterator(Iterable<T>... iterables) {
			this.iters = new ArrayList<Iterator<T>>();
			for (Iterable<T> iterable : iterables) {
				this.iters.add(iterable.iterator());
			}
		}

		public boolean hasNext() {
			boolean allTrue = true;
			boolean someTrue = false;
			for (Iterator<T> iter : iters) {
				final boolean hasNext = iter.hasNext();
				allTrue &= hasNext;
				someTrue |= hasNext;
			}
			if (someTrue && !allTrue)
				throw new IllegalStateException(this.getClass().getName()
						+ " must have same length");
			return allTrue;
		}

		public List<T> next() {
			List<T> retVal = new ArrayList<T>();
			for (Iterator<T> iter : iters) {
				retVal.add(iter.next());
			}
			return retVal;
		}

		public void remove() {
			throw new UnsupportedOperationException();

		}
	}

	public Iterator<List<T>> iterator() {
		return new ThisIterator(this.iterables);
	}

}
