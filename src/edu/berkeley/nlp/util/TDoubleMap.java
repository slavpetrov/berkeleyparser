package edu.berkeley.nlp.util;

import static edu.berkeley.nlp.util.LogInfo.*;
import java.io.*;
import java.util.*;

/**
 * Provides a map from objects to doubles. Motivation: provides a specialized
 * data structure for mapping objects to doubles which is both fast and space
 * efficient. Feature 1: You can switch between two representations of the map:
 * - Sorted list (lookups involve binary search) - Hash table with linear
 * probing (lookups involve hashing) Feature 2: Sometimes, we want several maps
 * with the same set of keys. If we lock the map, we can share the same keys
 * between several maps, which saves space.
 * 
 * Note: in the sorted list, we first sort the keys by hash code, and then for
 * equal hash code, we sort by the objects values. We hope that hash code
 * collisions will be rare enough that we won't have to resort to comparing
 * objects.
 * 
 * Typical usage: - Construct a map using a hash table. - To save space, switch
 * to a sorted list representation.
 * 
 * Will get runtime exception if try to used sorted list and keys are not
 * comparable.
 * 
 * TODO: support remove operation.
 */
public class TDoubleMap<T> extends AbstractTMap<T> implements
		Iterable<TDoubleMap<T>.Entry>, Serializable {
	protected static final long serialVersionUID = 42;

	public TDoubleMap() {
		this(AbstractTMap.defaultFunctionality, defaultExpectedSize);
	}

	public TDoubleMap(Functionality<T> keyFunc) {
		this(keyFunc, defaultExpectedSize);
	}

	public TDoubleMap(int expectedSize) {
		this(AbstractTMap.defaultFunctionality, expectedSize);
	}

	// If keys are locked, we can share the same keys.
	public TDoubleMap(AbstractTMap<T> map) {
		this(map.keyFunc);
		this.mapType = map.mapType;
		this.locked = map.locked;
		this.num = map.num;
		this.keys = map.locked ? map.keys : (T[]) map.keys.clone(); // Share
																	// keys!
																	// CHECKED
		if (map instanceof TDoubleMap)
			this.values = (double[]) ((TDoubleMap<T>) map).values.clone();
		else
			this.values = new double[keys.length];
	}

	/**
	 * expectedSize: expected number of entries we're going to have in the map.
	 */
	public TDoubleMap(Functionality<T> keyFunc, int expectedSize) {
		this.keyFunc = keyFunc;
		this.mapType = MapType.HASH_TABLE;
		this.locked = false;
		this.num = 0;
		allocate(getCapacity(num, false));
		this.numCollisions = 0;
	}

	// Main operations
	public boolean containsKey(T key) {
		return find(key, false) != -1;
	}

	public double get(T key, double defaultValue) {
		int i = find(key, false);
		return i == -1 ? defaultValue : values[i];
	}

	public double getWithErrorMsg(T key, double defaultValue) {
		int i = find(key, false);
		if (i == -1)
			errors("%s not in map, using %f", key, defaultValue);
		return i == -1 ? defaultValue : values[i];
	}

	public double getSure(T key) {
		// Throw exception if key doesn't exist.
		int i = find(key, false);
		if (i == -1)
			throw new RuntimeException("Missing key: " + key);
		return values[i];
	}

	public void put(T key, double value) {
		assert !Double.isNaN(value);
		int i = find(key, true);
		keys[i] = key;
		values[i] = value;
	}

	public void put(T key, double value, boolean keepHigher) {
		assert !Double.isNaN(value);
		int i = find(key, true);
		keys[i] = key;
		if (keepHigher && values[i] > value)
			return;
		values[i] = value;
	}

	public void incr(T key, double dValue) {
		int i = find(key, true);
		keys[i] = key;
		if (Double.isNaN(values[i]))
			values[i] = dValue; // New value
		else
			values[i] += dValue;
	}

	public void scale(T key, double dValue) {
		int i = find(key, true);
		if (i == -1)
			return;
		values[i] *= dValue;
	}

	public int size() {
		return num;
	}

	public int capacity() {
		return keys.length;
	}

	/*
	 * public void clear() { // Keep the same capacity num = 0; for(int i = 0; i
	 * < keys.length; i++) keys[i] = null; }
	 */
	public void gut() {
		values = null;
	} // Save memory

	// Simple operations on values
	// Implement them here for maximum efficiency.
	public double sum() {
		double sum = 0;
		for (int i = 0; i < keys.length; i++)
			if (keys[i] != null)
				sum += values[i];
		return sum;
	}

	public void putAll(double value) {
		for (int i = 0; i < keys.length; i++)
			if (keys[i] != null)
				values[i] = value;
	}

	public void incrAll(double dValue) {
		for (int i = 0; i < keys.length; i++)
			if (keys[i] != null)
				values[i] += dValue;
	}

	public void multAll(double dValue) {
		for (int i = 0; i < keys.length; i++)
			if (keys[i] != null)
				values[i] *= dValue;
	}

	// Return the key with the maximum value
	public T argmax() {
		int besti = -1;
		for (int i = 0; i < keys.length; i++)
			if (keys[i] != null && (besti == -1 || values[i] > values[besti]))
				besti = i;
		return besti == -1 ? null : keys[besti];
	}

	// Return the maximum value
	public double max() {
		int besti = -1;
		for (int i = 0; i < keys.length; i++)
			if (keys[i] != null && (besti == -1 || values[i] > values[besti]))
				besti = i;
		return besti == -1 ? Double.NEGATIVE_INFINITY : values[besti];
	}

	// For each (key, value) in map, increment this's key by factor*value
	public void incrMap(TDoubleMap<T> map, double factor) {
		for (int i = 0; i < map.keys.length; i++)
			if (map.keys[i] != null)
				incr(map.keys[i], factor * map.values[i]);
	}

	// If keys are locked, we can share the same keys.
	public TDoubleMap<T> copy() {
		TDoubleMap<T> newMap = new TDoubleMap<T>(keyFunc);
		newMap.mapType = mapType;
		newMap.locked = locked;
		newMap.num = num;
		newMap.keys = locked ? keys : (T[]) keys.clone(); // Share keys! CHECKED
		newMap.values = (double[]) values.clone();
		return newMap;
	}

	// Return a map with only keys in the set
	public TDoubleMap<T> restrict(Set<T> set) {
		TDoubleMap<T> newMap = new TDoubleMap<T>(keyFunc);
		newMap.mapType = mapType;
		if (mapType == MapType.SORTED_LIST) {
			allocate(getCapacity(num, false));
			for (int i = 0; i < keys.length; i++) {
				if (set.contains(keys[i])) {
					newMap.keys[newMap.num] = keys[i];
					newMap.values[newMap.num] = values[i];
					newMap.num++;
				}
			}
		} else if (mapType == MapType.HASH_TABLE) {
			for (int i = 0; i < keys.length; i++)
				if (keys[i] != null && set.contains(keys[i]))
					newMap.put(keys[i], values[i]);
		}
		newMap.locked = locked;
		return newMap;
	}

	// For sorting the entries.
	// Warning: this class has the overhead of the parent class
	private class FullEntry implements Comparable<FullEntry> {
		private FullEntry(T key, double value) {
			this.key = key;
			this.value = value;
		}

		public int compareTo(FullEntry e) {
			int h1 = hash(key);
			int h2 = hash(e.key);
			if (h1 != h2)
				return h1 - h2;
			return ((Comparable) key).compareTo((Comparable) e.key);
		}

		private final T key;
		private final double value;
	}

	// Compare by value.
	public class EntryValueComparator implements Comparator<Entry> {
		public int compare(Entry e1, Entry e2) {
			return Double.compare(values[e1.i], values[e2.i]);
		}
	}

	public EntryValueComparator entryValueComparator() {
		return new EntryValueComparator();
	}

	// For iterating.
	public class Entry {
		private Entry(int i) {
			this.i = i;
		}

		public T getKey() {
			return keys[i];
		}

		public double getValue() {
			return values[i];
		}

		public void setValue(double newValue) {
			values[i] = newValue;
		}

		private final int i;
	}

	public void lock() {
		locked = true;
	}

	public void switchToSortedList() {
		switchMapType(MapType.SORTED_LIST);
	}

	public void switchToHashTable() {
		switchMapType(MapType.HASH_TABLE);
	}

	// //////////////////////////////////////////////////////////

	public class EntrySet extends AbstractSet<Entry> {
		public Iterator<Entry> iterator() {
			return new EntryIterator();
		}

		public int size() {
			return num;
		}

		public boolean contains(Object o) {
			throw new UnsupportedOperationException();
		}

		public boolean remove(Object o) {
			throw new UnsupportedOperationException();
		}

		public void clear() {
			throw new UnsupportedOperationException();
		}
	}

	public class KeySet extends AbstractSet<T> {
		public Iterator<T> iterator() {
			return new KeyIterator();
		}

		public int size() {
			return num;
		}

		public boolean contains(Object o) {
			return containsKey((T) o);
		} // CHECKED

		public boolean remove(Object o) {
			throw new UnsupportedOperationException();
		}

		public void clear() {
			throw new UnsupportedOperationException();
		}
	}

	public class ValueCollection extends AbstractCollection<Double> {
		public Iterator<Double> iterator() {
			return new ValueIterator();
		}

		public int size() {
			return num;
		}

		public boolean contains(Object o) {
			throw new UnsupportedOperationException();
		}

		public void clear() {
			throw new UnsupportedOperationException();
		}
	}

	public EntryIterator iterator() {
		return new EntryIterator();
	}

	public EntrySet entrySet() {
		return new EntrySet();
	}

	public KeySet keySet() {
		return new KeySet();
	}

	public ValueCollection values() {
		return new ValueCollection();
	}

	// WARNING: no checks that this iterator is only used when
	// the map is not being structurally changed
	private class EntryIterator extends MapIterator<Entry> {
		public Entry next() {
			return new Entry(nextIndex());
		}
	}

	private class KeyIterator extends MapIterator<T> {
		public T next() {
			return keys[nextIndex()];
		}
	}

	private class ValueIterator extends MapIterator<Double> {
		public Double next() {
			return values[nextIndex()];
		}
	}

	private abstract class MapIterator<E> implements Iterator<E> {
		public MapIterator() {
			if (mapType == MapType.SORTED_LIST)
				end = size();
			else
				end = capacity();
			next = -1;
			nextIndex();
		}

		public boolean hasNext() {
			return next < end;
		}

		int nextIndex() {
			int curr = next;
			do {
				next++;
			} while (next < end && keys[next] == null);
			return curr;
		}

		public void remove() {
			throw new UnsupportedOperationException();
		}

		private int next, end;
	}

	// //////////////////////////////////////////////////////////

	/**
	 * How much capacity do we need for this type of map, given that we want n
	 * elements. compact: whether we want to save space and don't plan on
	 * growing.
	 */
	private int getCapacity(int n, boolean compact) {
		int capacity;
		if (mapType == MapType.SORTED_LIST)
			capacity = compact ? n : n * growFactor;
		else if (mapType == MapType.HASH_TABLE) {
			capacity = n * growFactor + 2; // Make sure there's enough room for
											// n+2 more entries
		} else
			throw new RuntimeException("Internal bug");
		return Math.max(capacity, 1);
	}

	/**
	 * Convert the map to the given type.
	 */
	private void switchMapType(MapType newMapType) {
		assert !locked;

		// System.out.println("switchMapType(" + newMapType + ", " + compact +
		// ")");

		// Save old keys and values, allocate space
		T[] oldKeys = keys;
		double[] oldValues = values;
		mapType = newMapType;
		allocate(getCapacity(num, true));
		numCollisions = 0;

		if (newMapType == MapType.SORTED_LIST) {
			// Sort the keys
			List<FullEntry> entries = new ArrayList<FullEntry>(num);
			for (int i = 0; i < oldKeys.length; i++)
				if (oldKeys[i] != null)
					entries.add(new FullEntry(oldKeys[i], oldValues[i]));
			Collections.sort(entries);

			// Populate the sorted list
			for (int i = 0; i < num; i++) {
				keys[i] = entries.get(i).key;
				values[i] = entries.get(i).value;
			}
		} else if (mapType == MapType.HASH_TABLE) {
			// Populate the hash table
			num = 0;
			for (int i = 0; i < oldKeys.length; i++) {
				if (oldKeys[i] != null)
					put(oldKeys[i], oldValues[i]);
			}
		}
	}

	/**
	 * Return the first index i for which the target key is less than or equal
	 * to key i (00001111). Should insert target key at position i. If target is
	 * larger than all of the elements, return size().
	 */
	private int binarySearch(T targetKey) {
		int targetHash = hash(targetKey);
		int l = 0, u = num;
		while (l < u) {
			// System.out.println(l);
			int m = (l + u) >> 1;
			int keyHash = hash(keys[m]);
			if (targetHash < keyHash
					|| (targetHash == keyHash && ((Comparable) targetKey)
							.compareTo((Comparable) keys[m]) <= 0))
				u = m;
			else
				l = m + 1;
		}
		return l;
	}

	// Modified hash (taken from HashMap.java).
	private int hash(T x) {
		int h = x.hashCode();
		h += ~(h << 9);
		h ^= (h >>> 14);
		h += (h << 4);
		h ^= (h >>> 10);
		if (h < 0)
			h = -h; // New
		return h;
	}

	/**
	 * Modify is whether to make room for the new key if it doesn't exist. If a
	 * new entry is created, the value at that position will be Double.NaN.
	 * Here's where all the magic happens.
	 */
	private int find(T key, boolean modify) {
		// System.out.println("find " + key + " " + modify + " " + mapType + " "
		// + capacity());

		if (mapType == MapType.SORTED_LIST) {
			// Binary search
			int i = binarySearch(key);
			if (i < num && keys[i] != null && key.equals(keys[i]))
				return i;
			if (modify) {
				if (locked)
					throw new RuntimeException("Cannot make new entry for "
							+ key + ", because map is locked");

				if (num == capacity())
					changeSortedListCapacity(getCapacity(num + 1, false));

				// Shift everything forward
				for (int j = num; j > i; j--) {
					keys[j] = keys[j - 1];
					values[j] = values[j - 1];
				}
				num++;
				values[i] = Double.NaN;
				return i;
			} else
				return -1;
		} else if (mapType == MapType.HASH_TABLE) {
			int capacity = capacity();
			int keyHash = hash(key);
			int i = keyHash % capacity;
			if (i < 0)
				i = -i; // Arbitrary transformation

			// Make sure big enough
			if (!locked && modify
					&& (num > loadFactor * capacity || capacity <= num + 1)) {
				/*
				 * if(locked) throw new
				 * RuntimeException("Cannot make new entry for " + key +
				 * ", because map is locked");
				 */

				switchMapType(MapType.HASH_TABLE);
				return find(key, modify);
			}

			// System.out.println("!!! " + keyHash + " " + capacity);
			if (num == capacity)
				throw new RuntimeException("Hash table is full: " + capacity);
			while (keys[i] != null && !keys[i].equals(key)) { // Collision
				// Warning: infinite loop if the hash table is full
				// (but this shouldn't happen based on the check above)
				i++;
				numCollisions++;
				if (i == capacity)
					i = 0;
			}
			if (keys[i] != null) { // Found
				assert key.equals(keys[i]);
				return i;
			}
			if (modify) { // Not found
				num++;
				values[i] = Double.NaN;
				return i;
			} else
				return -1;
		} else
			throw new RuntimeException("Internal bug: " + mapType);
	}

	private void allocate(int n) {
		keys = keyFunc.createArray(n);
		values = new double[n];
	}

	// Resize the sorted list to the new capacity.
	private void changeSortedListCapacity(int newCapacity) {
		assert mapType == MapType.SORTED_LIST;
		assert newCapacity >= num;
		T[] oldKeys = keys;
		double[] oldValues = values;
		allocate(newCapacity);
		System.arraycopy(oldKeys, 0, keys, 0, num);
		System.arraycopy(oldValues, 0, values, 0, num);
	}

	// Check consistency of data structure.
	private void repCheck() {
		assert capacity() > 0;
		if (mapType == MapType.SORTED_LIST) {
			assert num <= capacity();
			for (int i = 1; i < num; i++) { // Make sure keys are sorted.
				int h1 = hash(keys[i - 1]);
				int h2 = hash(keys[i]);
				assert h1 <= h2;
				if (h1 == h2)
					assert ((Comparable) keys[i - 1])
							.compareTo((Comparable) keys[i]) < 0;
			}
		}
	}

	public void debugDump() {
		LogInfo.logsForce("--------------------");
		LogInfo.logsForce("mapType = " + mapType);
		LogInfo.logsForce("locked = " + locked);
		LogInfo.logsForce("size/capacity = " + size() + "/" + capacity());
		LogInfo.logsForce("numCollisions = " + numCollisions);
		/*
		 * for(int i = 0; i < keys.length; i++) {
		 * System.out.printf("[%d] %s (%d) => %f\n", i, keys[i], (keys[i] ==
		 * null ? 0 : keys[i].hashCode()), values[i]); }
		 */
	}

	/**
	 * Format: mapType, num, (key, value) pairs
	 */
	private void writeObject(ObjectOutputStream out) throws IOException {
		out.writeObject(mapType);
		out.writeInt(num);
		for (Entry e : this) {
			out.writeObject(e.getKey());
			out.writeDouble(e.getValue());
		}
	}

	private void readObject(ObjectInputStream in) throws IOException,
			ClassNotFoundException {
		this.mapType = (MapType) in.readObject();
		this.num = 0;
		this.locked = false;

		int n = in.readInt();
		allocate(getCapacity(n, true));

		for (int i = 0; i < n; i++) {
			T key = keyFunc.intern((T) in.readObject()); // CHECKED
			double value = in.readDouble();
			if (mapType == MapType.SORTED_LIST) {
				// Assume keys and values serialized in sorted order
				keys[num] = key;
				values[num] = value;
				num++;
			} else if (mapType == MapType.HASH_TABLE) {
				put(key, value);
			}
		}
	}

	// Construct a map from a list of key, value, key value arguments.
	public static <T> TDoubleMap newMap(Object... args) {
		if (args.length % 2 != 0)
			throw Exceptions.bad;
		TDoubleMap map = new TDoubleMap();
		for (int i = 0; i < args.length; i += 2) {
			T key = (T) args[i];
			Object value = args[i + 1];
			if (value instanceof Integer)
				value = (double) ((Integer) value);
			map.put((T) args[i], (Double) value);
		}
		return map;
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		for (TDoubleMap<T>.Entry entry : entrySet()) {
			sb.append(entry.getKey() + ":" + entry.getValue() + ", ");
		}
		sb.append("]");
		return sb.toString();
	}

	private double[] values;
}
