package edu.berkeley.nlp.util;

import static edu.berkeley.nlp.util.LogInfo.errors;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Maps (object, object) pairs to doubles. Based on TDoubleMap. It's useful when
 * the number of second objects for a fixed first string is small. Most of the
 * operations in this class parallel that of TDoubleMap<T>, but just applied to
 * two keys. The implementation is essentially dispatching down to
 * TDoubleMap<T>. Typical usage: conditional probability table.
 */
public class T2DoubleMap<S, T> extends AbstractT2Map implements
		Iterable<Map.Entry<S, TDoubleMap<T>>>, Serializable {
	protected static final long serialVersionUID = 42;

	public T2DoubleMap() {
		this.keyFunc = AbstractTMap.defaultFunctionality;
	}

	public T2DoubleMap(AbstractTMap.Functionality<T> keyFunc) {
		this.keyFunc = keyFunc;
	}

	public <V> void initKeys(AbstractT2Map map) {
		this.locked = map.locked;

		// HACK: BAD dependencies
		if (map instanceof T2DoubleMap) {
			for (Map.Entry<S, TDoubleMap<T>> e : (T2DoubleMap<S, T>) map)
				put(e.getKey(), new TDoubleMap<T>(e.getValue()));
		} else if (map instanceof T2VMap) { // Not exactly right: need to check
											// type of V
			for (Map.Entry<S, TVMap<T, V>> e : ((T2VMap<S, T, V>) map))
				put(e.getKey(), new TDoubleMap<T>(e.getValue()));
		} else
			throw new RuntimeException("");
	}

	// Main operations
	public boolean containsKey(S key1, T key2) {
		TDoubleMap<T> map = getMap(key1, false);
		return map != null && map.containsKey(key2);
	}

	public double get(S key1, T key2, double defaultValue) {
		TDoubleMap<T> map = getMap(key1, false);
		return map == null ? defaultValue : map.get(key2, defaultValue);
	}

	public double getWithErrorMsg(S key1, T key2, double defaultValue) {
		TDoubleMap<T> map = getMap(key1, false);
		if (map == null)
			errors("(%s, %s) not in map, using %f", key1, key2, defaultValue);
		return map == null ? defaultValue : map.get(key2, defaultValue);
	}

	public double getSure(S key1, T key2) {
		// Throw exception if key doesn't exist.
		TDoubleMap<T> map = getMap(key1, false);
		if (map == null)
			throw new RuntimeException("Missing key: " + key1);
		return map.getSure(key2);
	}

	public void put(S key1, TDoubleMap<T> map) { // Risky
		if (locked)
			throw new RuntimeException("Cannot make new entry for " + key1
					+ ", because map is locked");
		maps.put(key1, map);
	}

	public void put(S key1, T key2, double value) {
		TDoubleMap<T> map = getMap(key1, true);
		map.put(key2, value);
	}

	public void incr(S key1, T key2, double dValue) {
		TDoubleMap<T> map = getMap(key1, true);
		map.incr(key2, dValue);
	}

	@Override
	public int size() {
		return maps.size();
	}

	// Return number of entries
	public int totalSize() {
		int n = 0;
		for (TDoubleMap<T> map : maps.values())
			n += map.size();
		return n;
	}

	public void gut() {
		for (TDoubleMap<T> map : maps.values())
			map.gut();
	}

	public Iterator<Map.Entry<S, TDoubleMap<T>>> iterator() {
		return maps.entrySet().iterator();
	}

	public Set<Map.Entry<S, TDoubleMap<T>>> entrySet() {
		return maps.entrySet();
	}

	public Set<S> keySet() {
		return maps.keySet();
	}

	public Collection<TDoubleMap<T>> values() {
		return maps.values();
	}

	// If keys are locked, we can share the same keys.
	public T2DoubleMap<S, T> copy() {
		return copy(newMap());
	}

	public T2DoubleMap<S, T> copy(T2DoubleMap<S, T> newMap) {
		newMap.locked = locked;
		for (Map.Entry<S, TDoubleMap<T>> e : maps.entrySet())
			newMap.maps.put(e.getKey(), e.getValue().copy());
		return newMap;
	}

	public T2DoubleMap<S, T> restrict(Set<S> set1, Set<T> set2) {
		return restrict(newMap(), set1, set2);
	}

	public T2DoubleMap<S, T> restrict(T2DoubleMap<S, T> newMap, Set<S> set1,
			Set<T> set2) {
		newMap.locked = locked;
		for (Map.Entry<S, TDoubleMap<T>> e : maps.entrySet())
			if (set1.contains(e.getKey()))
				newMap.maps.put(e.getKey(), e.getValue().restrict(set2));
		return newMap;
	}

	public T2DoubleMap<T, S> reverse(T2DoubleMap<T, S> newMap) { // Return a map
																	// with
																	// (key2,
																	// key1)
																	// pairs
		for (Map.Entry<S, TDoubleMap<T>> e1 : maps.entrySet()) {
			S key1 = e1.getKey();
			TDoubleMap<T> map = e1.getValue();
			for (TDoubleMap<T>.Entry e2 : map) {
				T key2 = e2.getKey();
				double value = e2.getValue();
				newMap.put(key2, key1, value);
			}
		}
		return newMap;
	}

	@Override
	public void lock() {
		for (TDoubleMap<T> map : maps.values())
			map.lock();
	}

	@Override
	public void switchToSortedList() {
		for (TDoubleMap<T> map : maps.values())
			map.switchToSortedList();
	}

	public void switchToHashTable() {
		for (TDoubleMap<T> map : maps.values())
			map.switchToHashTable();
	}

	protected T2DoubleMap<S, T> newMap() {
		return new T2DoubleMap<S, T>(keyFunc);
	}

	// //////////////////////////////////////////////////////////

	public TDoubleMap<T> getMap(S key1, boolean modify) {
		if (key1 == lastKey)
			return lastMap;

		TDoubleMap<T> map = maps.get(key1);
		if (map != null)
			return map;
		if (modify) {
			if (locked)
				throw new RuntimeException("Cannot make new entry for " + key1
						+ ", because map is locked");
			maps.put(key1, map = new TDoubleMap<T>(keyFunc));

			lastKey = key1;
			lastMap = map;
			return map;
		} else
			return null;
	}

	// //////////////////////////////////////////////////////////

	private Map<S, TDoubleMap<T>> maps = new HashMap<S, TDoubleMap<T>>();
	private S lastKey; // Cache last access
	private TDoubleMap<T> lastMap; // Cache last access
}
