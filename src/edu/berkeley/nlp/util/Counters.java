package edu.berkeley.nlp.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import edu.berkeley.nlp.math.SloppyMath;

/**
 * @author Dan Klein
 */
public class Counters {
	public static <E> Counter<E> normalize(Counter<E> counter) {
		Counter<E> normalizedCounter = new Counter<E>();
		double total = counter.totalCount();
		for (E key : counter.keySet()) {
			normalizedCounter.setCount(key, counter.getCount(key) / total);
		}
		return normalizedCounter;
	}

	public static <T> Counter<T> counterFromCollection(Iterable<T> iterable) {
		Counter<T> counts = new Counter<T>();
		for (T t : iterable) {
			counts.incrementCount(t, 1.0);
		}
		return counts;
	}

	public static <E, C extends Iterable<?>> Counter<E> counterFromData(
			Iterable<? extends Collection<E>> iterable) {
		Counter<E> counts = new Counter<E>();
		for (Collection<E> coll : iterable) {
			counts.incrementAll(coll, 1.0);
		}
		return counts;
	}

	public static <K, V> CounterMap<K, V> conditionalNormalize(
			CounterMap<K, V> counterMap) {
		CounterMap<K, V> normalizedCounterMap = new CounterMap<K, V>();
		for (K key : counterMap.keySet()) {
			Counter<V> normalizedSubCounter = normalize(counterMap
					.getCounter(key));
			for (V value : normalizedSubCounter.keySet()) {
				double count = normalizedSubCounter.getCount(value);
				normalizedCounterMap.setCount(key, value, count);
			}
		}
		return normalizedCounterMap;
	}

	public static <K> double l2Norm(Counter<K> counts) {
		double sum = 0.0;
		for (Map.Entry<K, Double> entry : counts.getEntrySet()) {
			double count = entry.getValue();
			sum += count * count;
		}
		return Math.sqrt(sum);
	}

	public static <K> Counter<K> l2Normalize(Counter<K> counts) {
		Counter<K> normalizedCounts = new Counter<K>();
		double norm = 0.0;
		for (Map.Entry<K, Double> entry : counts.getEntrySet()) {
			double count = entry.getValue();
			norm += count * count;
		}
		norm = Math.sqrt(norm);
		if (norm == 0.0) {
			return normalizedCounts;
		}
		for (K key : counts.keySet()) {
			double count = counts.getCount(key);
			normalizedCounts.setCount(key, count / norm);
		}
		return normalizedCounts;
	}

	public static <L> List<L> sortedKeys(final Counter<L> counts) {
		List<L> keys = new ArrayList<L>();
		keys.addAll(counts.keySet());
		Collections.sort(keys, new Comparator<L>() {

			public int compare(L arg0, L arg1) {
				double diff = counts.getCount(arg1) - counts.getCount(arg0);
				if (diff < 0) {
					return -1;
				}
				if (diff == 0.0) {
					return 0;
				}
				return 1;
			}

		});
		return keys;
	}

	public static <K> Counter<K> exponentiate(Counter<K> counts) {
		Counter<K> exponentiated = new Counter<K>();
		for (Map.Entry<K, Double> entry : counts.entrySet()) {
			exponentiated.setCount(entry.getKey(), Math.exp(entry.getValue()));
		}
		return exponentiated;
	}

	public static <K> void exponentiateInPlace(Counter<K> counts) {
		for (Map.Entry<K, Double> entry : counts.entrySet()) {
			entry.setValue(Math.exp(entry.getValue()));
		}
	}

	public static <K> void logInPlace(Counter<K> counts) {
		for (Map.Entry<K, Double> entry : counts.entrySet()) {
			entry.setValue(Math.log(entry.getValue()));
		}
	}

	/**
	 * 
	 * @param logScores
	 * @param <K>
	 */
	public static <K> void makeProbsFromLogScoresInPlace(Counter<K> logScores) {
		double logSum = SloppyMath.logAdd(logScores);
		for (Map.Entry<K, Double> entry : logScores.entrySet()) {
			double logScore = entry.getValue();
			double prob = Math.exp(logScore - logSum);
			entry.setValue(prob);
		}
		logScores.setDirty(true);
	}
}
