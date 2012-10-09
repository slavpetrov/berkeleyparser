package edu.berkeley.nlp.util;

import static edu.berkeley.nlp.util.LogInfo.errors;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Maps (object, object) pairs to objects. Based on T2VMap. It's useful when the
 * number of second objects for a fixed first string is small. Most of the
 * operations in this class parallel that of T2VMap<T>, but just applied to two
 * keys. The implementation is essentially dispatching down to T2VMap<T>.
 * Typical usage: conditional probability table.
 */
public class T2VMap<S, T, V> extends AbstractT2Map implements
		Iterable<Map.Entry<S, TVMap<T, V>>>, Serializable {
	protected static final long serialVersionUID = 42;

	public T2VMap() {
		this.keyFunc = AbstractTMap.defaultFunctionality;
		this.valueFunc = AbstractTMap.defaultFunctionality;
	}

	public T2VMap(AbstractTMap.Functionality<T> keyFunc,
			AbstractTMap.Functionality<V> valueFunc) {
		this.keyFunc = keyFunc;
		this.valueFunc = valueFunc;
	}

	public void initKeys(AbstractT2Map map) {
		this.locked = map.locked;

		// HACK: BAD dependencies
		if (map instanceof T2DoubleMap) {
			for (Map.Entry<S, TDoubleMap<T>> e : (T2DoubleMap<S, T>) map)
				put(e.getKey(), new TVMap<T, V>(e.getValue(), valueFunc));
		} else if (map instanceof T2VMap) { // Not exactly right: need to check
											// type of V
			for (Map.Entry<S, TVMap<T, V>> e : ((T2VMap<S, T, V>) map))
				put(e.getKey(), new TVMap<T, V>(e.getValue(), valueFunc));
		} else
			throw new RuntimeException("");
	}

	// Main operations
	public boolean containsKey(S key1, T key2) {
		TVMap<T, V> map = getMap(key1, false);
		return map != null && map.containsKey(key2);
	}

	public V get(S key1, T key2, V defaultValue) {
		TVMap<T, V> map = getMap(key1, false);
		return map == null ? defaultValue : map.get(key2, defaultValue);
	}

	public V getWithErrorMsg(S key1, T key2, V defaultValue) {
		TVMap<T, V> map = getMap(key1, false);
		if (map == null)
			errors("(%s, %s) not in map, using %f", key1, key2, defaultValue);
		return map == null ? defaultValue : map.get(key2, defaultValue);
	}

	public V getSure(S key1, T key2) {
		// Throw exception if key doesn't exist.
		TVMap<T, V> map = getMap(key1, false);
		if (map == null)
			throw new RuntimeException("Missing key: " + key1);
		return map.getSure(key2);
	}

	public void put(S key1, TVMap<T, V> map) { // Risky
		if (locked)
			throw new RuntimeException("Cannot make new entry for " + key1
					+ ", because map is locked");
		maps.put(key1, map);
	}

	public void put(S key1, T key2, V value) {
		TVMap<T, V> map = getMap(key1, true);
		map.put(key2, value);
	}

	@Override
	public int size() {
		return maps.size();
	}

	// Return number of entries
	public int totalSize() {
		int n = 0;
		for (TVMap<T, V> map : maps.values())
			n += map.size();
		return n;
	}

	public void gut() {
		for (TVMap<T, V> map : maps.values())
			map.gut();
	}

	public Iterator<Map.Entry<S, TVMap<T, V>>> iterator() {
		return maps.entrySet().iterator();
	}

	public Set<Map.Entry<S, TVMap<T, V>>> entrySet() {
		return maps.entrySet();
	}

	public Set<S> keySet() {
		return maps.keySet();
	}

	public Collection<TVMap<T, V>> values() {
		return maps.values();
	}

	// If keys are locked, we can share the same keys.
	public T2VMap<S, T, V> copy() {
		return copy(newMap());
	}

	public T2VMap<S, T, V> copy(T2VMap<S, T, V> newMap) {
		newMap.locked = locked;
		for (Map.Entry<S, TVMap<T, V>> e : maps.entrySet())
			newMap.maps.put(e.getKey(), e.getValue().copy());
		return newMap;
	}

	public T2VMap<S, T, V> restrict(Set<S> set1, Set<T> set2) {
		return restrict(newMap(), set1, set2);
	}

	public T2VMap<S, T, V> restrict(T2VMap<S, T, V> newMap, Set<S> set1,
			Set<T> set2) {
		newMap.locked = locked;
		for (Map.Entry<S, TVMap<T, V>> e : maps.entrySet())
			if (set1.contains(e.getKey()))
				newMap.maps.put(e.getKey(), e.getValue().restrict(set2));
		return newMap;
	}

	public T2VMap<T, S, V> reverse(T2VMap<T, S, V> newMap) { // Return a map
																// with (key2,
																// key1) pairs
		for (Map.Entry<S, TVMap<T, V>> e1 : maps.entrySet()) {
			S key1 = e1.getKey();
			TVMap<T, V> map = e1.getValue();
			for (TVMap<T, V>.Entry e2 : map) {
				T key2 = e2.getKey();
				V value = e2.getValue();
				newMap.put(key2, key1, value);
			}
		}
		return newMap;
	}

	@Override
	public void lock() {
		for (TVMap<T, V> map : maps.values())
			map.lock();
	}

	@Override
	public void switchToSortedList() {
		for (TVMap<T, V> map : maps.values())
			map.switchToSortedList();
	}

	public void switchToHashTable() {
		for (TVMap<T, V> map : maps.values())
			map.switchToHashTable();
	}

	protected T2VMap<S, T, V> newMap() {
		return new T2VMap<S, T, V>(keyFunc, valueFunc);
	}

	// //////////////////////////////////////////////////////////

	public TVMap<T, V> getMap(S key1, boolean modify) {
		if (key1 == lastKey)
			return lastMap;

		TVMap<T, V> map = maps.get(key1);
		if (map != null)
			return map;
		if (modify) {
			if (locked)
				throw new RuntimeException("Cannot make new entry for " + key1
						+ ", because map is locked");
			maps.put(key1, map = new TVMap<T, V>(keyFunc, valueFunc));

			lastKey = key1;
			lastMap = map;
			return map;
		} else
			return null;
	}

	// //////////////////////////////////////////////////////////

	private Map<S, TVMap<T, V>> maps = new HashMap<S, TVMap<T, V>>();
	private S lastKey; // Cache last access
	private TVMap<T, V> lastMap; // Cache last access
	protected TVMap.Functionality<V> valueFunc;
}
