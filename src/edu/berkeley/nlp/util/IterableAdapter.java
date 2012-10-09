package edu.berkeley.nlp.util;

import java.util.Iterator;

public class IterableAdapter {

	public static interface Convertor<S, T> {
		public T convert(S s);
	}

	public static <S, T> Iterable<T> adapt(final Iterable<S> iterable,
			final Convertor<S, T> convertor) {
		return new Iterable<T>() {
			public Iterator<T> iterator() {
				final Iterator<S> origIt = iterable.iterator();
				return new Iterator<T>() {

					public boolean hasNext() {
						return origIt.hasNext();
					}

					public T next() {
						// TODO Auto-generated method stub
						return convertor.convert(origIt.next());
					}

					public void remove() {
						// TODO Auto-generated method stub
						origIt.remove();
					}

				};
			}

		};
	}

}
