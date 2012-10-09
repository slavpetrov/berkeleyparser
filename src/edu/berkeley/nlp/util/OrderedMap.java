package edu.berkeley.nlp.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A container for mapping objects to objects. Keep track of the order of the
 * objects (as they were inserted into the data structure). No duplicate
 * elements allowed.
 */
public class OrderedMap<S, T> {
	private ArrayList<S> keys = new ArrayList<S>();
	private Map<S, T> map = new HashMap<S, T>();

	public OrderedMap() {
	}

	public OrderedMap(OrderedMap<S, T> map) {
		for (S key : map.keys())
			put(key, map.get(key));
	}

	public void clear() {
		keys.clear();
		map.clear();
	}

	public void log(String title) {
		LogInfo.track(title, true);
		for (S key : keys())
			LogInfo.logs(key + "\t" + get(key));
		LogInfo.end_track();
	}

	public void put(S key) {
		put(key, null);
	}

	public void putAtEnd(S key) {
		put(key, get(key));
	}

	public void removeAt(int i) {
		S key = keys.get(i);
		keys.remove(i);
		map.remove(key);
	}

	public void reput(S key, T val) { // Don't affect order (but insert if key
										// doesn't exist)
		if (!map.containsKey(key))
			put(key, val);
		else
			map.put(key, val);
	}

	public void put(S key, T val) {
		// If key already exists, we replace its value and move it to the end of
		// the list.
		if (map.containsKey(key))
			keys.remove(key); // Remove last occurrence of key
		keys.add(key);
		map.put(key, val);
	}

	public int size() {
		return keys.size();
	}

	public boolean containsKey(S key) {
		return map.containsKey(key);
	}

	public T get(S key) {
		return map.get(key);
	}

	public T get(S key, T defaultVal) {
		return MapUtils.get(map, key, defaultVal);
	}

	public Set<S> keySet() {
		return map.keySet();
	}

	public List<S> keys() {
		return keys;
	}

	// Values {
	public ValueCollection values() {
		return new ValueCollection();
	}

	public class ValueCollection extends AbstractCollection<T> {
		@Override
		public Iterator<T> iterator() {
			return new ValueIterator();
		}

		@Override
		public int size() {
			return size();
		}

		@Override
		public boolean contains(Object o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void clear() {
			throw new UnsupportedOperationException();
		}
	}

	private class ValueIterator implements Iterator<T> {
		public ValueIterator() {
			next = 0;
		}

		public boolean hasNext() {
			return next < size();
		}

		public T next() {
			return map.get(keys.get(next++));
		}

		public void remove() {
			throw new UnsupportedOperationException();
		}

		private int next;
	}

	// }

	/**
	 * Output each entry in the HashMap on a line separated by a tab.
	 */
	public void print(PrintWriter out) {
		for (S key : keys) {
			print(out, key, map.get(key));
		}
		out.flush();
	}

	public void print(String path) throws IOException {
		print(new File(path));
	}

	public void printHard(String path) {
		PrintWriter out = IOUtils.openOutHard(path);
		print(out);
		out.close();
	}

	public void print(File path) throws IOException {
		PrintWriter out = IOUtils.openOut(path);
		print(out);
		out.close();
	}

	public String print() {
		StringWriter sw = new StringWriter();
		print(new PrintWriter(sw));
		return sw.toString();
	}

	void print(PrintWriter out, S key, T val) {
		out.println(key + (val == null ? "" : "\t" + val));
	}

	public boolean printEasy(String path) {
		if (StrUtils.isEmpty(path))
			return false;
		return printEasy(new File(path));
	}

	public boolean printEasy(File path) {
		if (path == null)
			return false;
		try {
			PrintWriter out = IOUtils.openOut(path);
			print(out);
			out.close();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (S key : keys) {
			sb.append(key + " " + map.get(key) + "\n");
		}
		return sb.toString();
	}

}
