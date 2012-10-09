package edu.berkeley.nlp.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * A version of Counter that does not create Double objects.
 * 
 * This should most certainly be rewritten.
 * 
 * John
 */
public class FastCounter<E> implements Serializable {

	private static final long serialVersionUID = 1L;
	TDoubleMap<E> entries = new TDoubleMap<E>();

	/**
	 * The elements in the counter.
	 * 
	 * @return set of keys
	 */
	public Set<E> keySet() {
		return entries.keySet();
	}

	public void multAll(double dValue) {
		entries.multAll(dValue);
	}

	/**
	 * The number of entries in the counter (not the total count -- use
	 * totalCount() instead).
	 */
	public int size() {
		return entries.size();
	}

	/**
	 * True if there are no entries in the counter (false does not mean
	 * totalCount > 0)
	 */
	public boolean isEmpty() {
		return size() == 0;
	}

	/**
	 * Returns whether the counter contains the given key. Note that this is the
	 * way to distinguish keys which are in the counter with count zero, and
	 * those which are not in the counter (and will therefore return count zero
	 * from getCount().
	 * 
	 * @param key
	 * @return whether the counter contains the key
	 */
	public boolean containsKey(E key) {
		return entries.containsKey(key);
	}

	/**
	 * Get the count of the element, or zero if the element is not in the
	 * counter.
	 * 
	 * @param key
	 * @return
	 */
	public double getCount(E key) {
		return entries.get(key, 0.0);
	}

	/**
	 * Destructively normalize this Counter in place.
	 */
	public void normalize() {
		entries.multAll(1.0 / entries.sum());
	}

	/**
	 * Set the count for the given key, clobbering any previous count.
	 * 
	 * @param key
	 * @param count
	 */
	public void setCount(E key, double count) {
		entries.put(key, count);
	}

	/**
	 * Increment a key's count by the given amount.
	 * 
	 * @param key
	 * @param increment
	 */
	public void incrementCount(E key, double increment) {
		entries.incr(key, increment);
	}

	/**
	 * Increment each element in a given collection by a given amount.
	 */
	public void incrementAll(Collection<? extends E> collection, double count) {
		for (E key : collection) {
			incrementCount(key, count);
		}
	}

	public <T extends E> void incrementAll(Counter<T> counter) {
		for (T key : counter.keySet()) {
			double count = counter.getCount(key);
			incrementCount(key, count);
		}
	}

	public <T extends E> void incrementAll(FastCounter<T> counter) {
		for (T key : counter.keySet()) {
			double count = counter.getCount(key);
			incrementCount(key, count);
		}
	}

	/**
	 * Finds the total of all counts in the counter. This implementation
	 * iterates through the entire counter every time this method is called.
	 * 
	 * @return the counter's total
	 */
	public double totalCount() {
		return entries.sum();
	}

	public List<E> getSortedKeys() {
		PriorityQueue<E> pq = this.asPriorityQueue();
		List<E> keys = new ArrayList<E>();
		while (pq.hasNext()) {
			keys.add(pq.next());
		}
		return keys;
	}

	/**
	 * Finds the key with maximum count. This is a linear operation, and ties
	 * are broken arbitrarily.
	 * 
	 * @return a key with minumum count
	 */
	public E argMax() {
		return entries.argmax();
	}

	public double min() {
		return maxMinHelp(false);
	}

	public double max() {
		return maxMinHelp(true);
	}

	private double maxMinHelp(boolean max) {
		double maxCount = max ? Double.NEGATIVE_INFINITY
				: Double.POSITIVE_INFINITY;

		for (E key : entries.keySet()) {
			double val = entries.getSure(key);
			if ((max && val > maxCount) || (!max && val < maxCount)) {
				maxCount = val;
			}
		}
		return maxCount;
	}

	/**
	 * Returns a string representation with the keys ordered by decreasing
	 * counts.
	 * 
	 * @return string representation
	 */
	@Override
	public String toString() {
		return toString(keySet().size());
	}

	/**
	 * Returns a string representation which includes no more than the
	 * maxKeysToPrint elements with largest counts.
	 * 
	 * @param maxKeysToPrint
	 * @return partial string representation
	 */
	public String toString(int maxKeysToPrint) {
		return asPriorityQueue().toString(maxKeysToPrint, false);
	}

	/**
	 * Builds a priority queue whose elements are the counter's elements, and
	 * whose priorities are those elements' counts in the counter.
	 */
	public PriorityQueue<E> asPriorityQueue() {
		PriorityQueue<E> pq = new PriorityQueue<E>(entries.size());
		for (E key : entries.keySet()) {
			pq.add(key, entries.getSure(key));
		}
		return pq;
	}

	/**
	 * Warning: all priorities are the negative of their counts in the counter
	 * here
	 * 
	 * @return
	 */
	public PriorityQueue<E> asMinPriorityQueue() {
		PriorityQueue<E> pq = new PriorityQueue<E>(entries.size());
		for (E key : entries.keySet()) {
			pq.add(key, -1.0 * entries.getSure(key));
		}
		return pq;
	}

	public void pruneKeysBelowThreshold(double cutoff) {
		Iterator<E> it = entries.keySet().iterator();
		Set<E> remaining = new HashSet<E>();
		while (it.hasNext()) {
			E key = it.next();
			double val = entries.getSure(key);
			if (val >= cutoff)
				remaining.add(key);
		}
		entries = entries.restrict(remaining);
	}

	public void clear() {
		entries.gut();
	}

	public void keepTopNKeys(int keepN) {
		keepKeysHelper(keepN, true);
	}

	public void keepBottomNKeys(int keepN) {
		keepKeysHelper(keepN, false);
	}

	private void keepKeysHelper(int keepN, boolean top) {
		Counter<E> tmp = new Counter<E>();

		int n = 0;
		for (E e : Iterators.able(top ? asPriorityQueue()
				: asMinPriorityQueue())) {

			if (n <= keepN)
				tmp.setCount(e, getCount(e));
			n++;

		}
		clear();
		incrementAll(tmp);
	}

	/**
	 * Sets all counts to the given value, but does not remove any keys
	 */
	public void setAllCounts(double val) {
		for (E e : keySet()) {
			setCount(e, val);
		}
	}

	public void switchToSortedList() {
		entries.switchToSortedList();
	}

	public void switchToHashTable() {
		entries.switchToHashTable();
	}

	public static void main(String[] args) {
		FastCounter<String> counter = new FastCounter<String>();
		System.out.println(counter);
		counter.incrementCount("planets", 7);
		System.out.println(counter);
		counter.incrementCount("planets", 1);
		System.out.println(counter);
		counter.setCount("suns", 1);
		System.out.println(counter);
		counter.setCount("aliens", 0.5);
		System.out.println(counter);
		System.out.println(counter.toString(2));
		System.out.println("Total: " + counter.totalCount());
		counter.pruneKeysBelowThreshold(0.6);
		System.out.println(counter);
		System.out.println(counter.totalCount());

		System.out.println("Waiting for profiler...");
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		System.out.println("Done.");

		// Speed and memory tests
		FastCounter<Integer> fast = new FastCounter<Integer>();
		Counter<Integer> baseline = new Counter<Integer>();
		StopWatch watch = new StopWatch();
		Random r = new Random();
		int size = 50000000;

		watch.start();
		for (int i = 0; i < size; i++) {
			fast.incrementCount(r.nextInt(size / 10), 1);
		}
		watch.stop();
		System.out.println("Fast: " + watch.toString());

		try {
			Thread.sleep(5000);
			System.out.println("Waiting for profiler...");
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		watch.reset();
		watch.start();
		fast.entries.switchToSortedList();
		watch.stop();
		System.out.println("Switching: " + watch.toString());

		try {
			Thread.sleep(5000);
			System.out.println("Waiting for profiler...");
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		watch.reset();
		watch.start();
		for (int i = 0; i < size; i++) {
			baseline.incrementCount(r.nextInt(size / 10), 1);
		}
		watch.stop();
		System.out.println("Baseline: " + watch.toString());
	}

}
