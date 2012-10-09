package edu.berkeley.nlp.util;

import edu.berkeley.nlp.util.functional.Function;
import edu.berkeley.nlp.util.functional.Predicate;

import java.util.*;

/**
 * Created by IntelliJ IDEA. User: aria42 Date: Dec 30, 2008
 */
public class LazyIterable<T, I> implements Iterable<T> {

	private Iterable<I> inputIterable;
	private Function<I, ? extends T> factory;
	private int cacheSize;
	private Predicate<T> outputPred;
	private Set<I> rejectedInputs;

	public LazyIterable(Iterable<I> inputIterable,
			Function<I, ? extends T> factory, Predicate<T> outputPred,
			int cacheSize) {
		this.inputIterable = inputIterable;
		this.factory = factory;
		this.cacheSize = cacheSize;
		this.outputPred = outputPred;
		this.rejectedInputs = new HashSet<I>();
	}

	private class MyIterator implements Iterator<T> {

		private Iterator<I> inputIt;
		private Queue<T> cache;

		void ensure() {
			// if (cache == null) cache = new ArrayDeque<T>();
			if (!cache.isEmpty())
				return;
			while (cache.size() < cacheSize && inputIt.hasNext()) {
				T next = nextInternal();
				cache.add(next);
			}
		}

		T nextInternal() {
			while (inputIt.hasNext()) {
				I input = inputIt.next();
				if (rejectedInputs.contains(input)) {
					continue;
				}
				T output = factory.apply(input);
				if (outputPred.apply(output)) {
					return output;
				} else {
					rejectedInputs.add(input);
				}
			}
			return null;
		}

		MyIterator() {
			inputIt = inputIterable.iterator();
		}

		public boolean hasNext() {
			return inputIt.hasNext() || !cache.isEmpty();
		}

		public T next() {
			ensure();
			return cache.poll();
		}

		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	public Iterator<T> iterator() {
		return new MyIterator();
	}

	public static void main(String[] args) {
		List<String> arr = CollectionUtils.makeList("Aria is cool", "Isn't he");
		Function<String, String[]> factory = new Function<String, String[]>() {
			public String[] apply(String input) {
				return input.split("\\s+");
			}
		};
		Iterable<String[]> iterable = new LazyIterable<String[], String>(arr,
				factory, null, 10);
		for (String[] strings : iterable) {
			System.out.println(Arrays.deepToString(strings));
		}
	}
}
