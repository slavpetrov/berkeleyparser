package edu.berkeley.nlp.util;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

public class Trie<K, V> implements Map<List<K>, V> {

	public static class TrieMapFactory<K, V> extends MapFactory<List<K>, V> {

		private boolean identity;

		public TrieMapFactory(boolean identity) {
			this.identity = identity;
		}

		@Override
		public Map<List<K>, V> buildMap() {
			return new Trie<K, V>(identity);
		}

	}

	public class SuffixLengthIterable implements Iterable<List<K>> {

		private final int suffixLength;

		public SuffixLengthIterable(int suffixLength) {
			this.suffixLength = suffixLength;
		}

		public Iterator<List<K>> iterator() {
			return new SuffixLengthIterator();
		}

		public class SuffixLengthIterator implements Iterator<List<K>> {

			private final Iterator<List<K>> allIter;

			private List<K> next;

			private List<K> curr;

			public SuffixLengthIterator() {
				allIter = Trie.this.setIterator();
				advanceIter();
				curr = next;
			}

			private void advanceIter() {
				curr = next;
				while (allIter.hasNext()) {
					final List<K> currNext = allIter.next();
					if (currNext.size() == suffixLength) {
						next = currNext;
					}
				}
			}

			public boolean hasNext() {
				return next != null;
			}

			public List<K> next() {
				if (!hasNext())
					throw new UnsupportedOperationException();
				advanceIter();
				return curr;
			}

			public void remove() {
				throw new UnsupportedOperationException();

			}

		}

	}

	private Map<K, Trie<K, V>> map;

	private V v;

	private int size;

	private final boolean useIdentity;

	public Trie(boolean useIdentity) {
		this(useIdentity, null, null, null);
	}

	public Trie() {
		this(false, null, null, null);
	}

	public static int id = 0;

	protected Trie(boolean useIdentity, K k, V v, Trie<K, V> backPointer) {
		this.useIdentity = useIdentity;
		// this.useIdentity = false;
		this.v = v;
		map = useIdentity ? new IdentityHashMap<K, Trie<K, V>>()
				: new HashMap<K, Trie<K, V>>(3);
		id++;
	}

	public Trie<K, V> getNextTrie(K k) {

		return map.get(k);

	}

	public Trie<K, V> getPartialList(List<K> ts) {
		if (ts.isEmpty())
			return null;
		K t = ts.get(0);
		final Trie<K, V> trie = map.get(t);

		if (trie == null)
			return null;
		if (ts.size() == 1) {
			return trie;
		} else {
			return trie.getPartialList(ts.subList(1, ts.size()));
		}
	}

	// public void compactify()
	// {
	// if (map.size() == 0)
	// {
	// map = Collections.emptyMap();
	// }
	// if (map.size() == 1)
	// {
	// Map.Entry<K, Trie<K,V>> entry = map.entrySet().iterator().next();
	// map = Collections.singletonMap(entry.getKey(),entry.getValue());
	// }
	// else if (map.size() > 1)
	// {
	// if (useIdentity)
	// {
	// Map<K,Trie<K,V>> tmp = new IdentityHashMap<K, Trie<K,V>>(map.size());
	// tmp.putAll(map);
	// map = tmp;
	// }
	// else
	// {
	// Map<K,Trie<K,V>> tmp = new HashMap<K,Trie<K,V>>(map);
	// map = tmp;
	// }
	// }
	// for (Map.Entry<K, Trie<K,V>> entry : map.entrySet())
	// {
	// entry.getValue().compactify();
	// }
	// }

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("[");
		boolean added = false;
		for (List<K> t : this.keySet()) {
			if (added)
				sb.append(",");
			sb.append(t);
			sb.append("=");
			sb.append(this.get(t));
			added = true;
		}
		sb.append("]");
		return sb.toString();
	}

	public V put(List<K> k, V v) {

		put(k, v, 0);
		return null;
	}

	private void put(List<K> k, V v, int start) {
		final K first = k.get(start);
		Trie<K, V> trie = map.get(first);
		if (trie == null) {

			if (k.size() - start == 1) {
				map.put(first, newTrie(useIdentity, first, v, this));
			} else {
				trie = newTrie(useIdentity, first, null, this);

				map.put(first, trie);
				trie.put(k, v, start + 1);
			}

		} else {

			if (k.size() - start == 1) {
				trie.v = v;
			} else {
				trie.put(k, v, start + 1);
			}
		}
	}

	protected Trie<K, V> newTrie(boolean useIdentity2, K first, V v2,
			Trie<K, V> trie) {
		return new Trie<K, V>(useIdentity2, first, v2, trie);
	}

	public void putAll(Map<? extends List<K>, ? extends V> c) {

		for (final List<K> e : c.keySet()) {
			put(e, c.get(e));
		}

	}

	public void clear() {
		map.clear();
		size = 0;
	}

	public Iterable<K> nextElements() {
		return Iterators.able(map.keySet().iterator());
	}

	@SuppressWarnings("unchecked")
	public boolean containsKey(Object o) {
		return get(o) != null;
	}

	public boolean isEmpty() {
		return map.isEmpty();
	}

	private MyIterator setIterator() {
		return new MyIterator();
	}

	private class MyIterator implements Iterator<List<K>> {

		private boolean hasNext;

		private boolean activeTokenAlreadyReturned = true;

		private boolean alreadyAdvanced;

		private K currToken;

		private MyIterator currSuffixIter;

		private final Iterator<K> tokenIter;

		private Map getMap() {
			return map;
		}

		public MyIterator() {
			tokenIter = map.keySet().iterator();
			advanceIter();

			alreadyAdvanced = true;

		}

		private void advanceIter() {

			if (currToken == null) {
				if (!tokenIter.hasNext()) {
					alreadyAdvanced = true;
					hasNext = false;
					return;
				}
				currToken = tokenIter.next();
				final Trie<K, V> trie = map.get(currToken);
				currSuffixIter = trie == null ? null : trie.setIterator();
				if (trie.v != null) {
					activeTokenAlreadyReturned = false;
					hasNext = true;
					alreadyAdvanced = true;
					return;
				}

			}
			final Trie<K, V> trie = map.get(currToken);
			if (currSuffixIter == null) {
				currSuffixIter = trie == null ? null : trie.setIterator();
			}
			if (currSuffixIter == null || !currSuffixIter.hasNext()) {
				currToken = null;
				advanceIter();
			} else {
				hasNext = true;
			}
			alreadyAdvanced = true;
			return;

		}

		public boolean hasNext() {
			if (!alreadyAdvanced)
				advanceIter();

			return hasNext;
		}

		public List<K> next() {

			if (!hasNext()) {
				throw new NoSuchElementException();
			}
			alreadyAdvanced = false;
			List<K> retVal = null;
			if (!activeTokenAlreadyReturned) {
				activeTokenAlreadyReturned = true;

				return Collections.singletonList(currToken);
			}

			retVal = new ArrayList<K>();
			retVal.add(currToken);
			retVal.addAll(currSuffixIter.next());

			return retVal;
		}

		public void remove() {
			throw new UnsupportedOperationException();

		}

	}

	public Iterable<List<K>> bySuffixLength(int suffixLength) {
		return new SuffixLengthIterable(suffixLength);
	}

	/**
	 * Always return null.
	 */
	public V remove(Object o) {
		if (!(o instanceof List))
			return null;
		List<K> k = (List<K>) o;
		final K first = k.get(0);
		if (k.size() == 1) {
			final Trie<K, V> remove = map.get(first);
			if (remove == null)
				return null;
			remove.v = null;
			if (remove.isEmpty())
				map.remove(first);
			return null;

		}
		final Trie<K, V> trie = map.get(first);
		if (trie == null)
			return null;

		trie.remove(k.subList(1, k.size()));
		if (trie.isEmpty() && trie.v == null)
			map.remove(first);
		return null;

	}

	public int size() {
		return size;
	}

	public Object[] toArray() {
		throw new UnsupportedOperationException();
	}

	public <E> E[] toArray(E[] a) {
		throw new UnsupportedOperationException();
	}

	public static void main(String[] argv) {
		final Trie<String, Boolean> tree = new Trie<String, Boolean>();
		tree.put(Arrays.asList("c", "a"), true);

		tree.put(Arrays.asList("c"), true);
		tree.put(Arrays.asList("a", "b", "c"), true);
		tree.put(Arrays.asList("a", "b"), true);
		tree.put(Arrays.asList("a", "b", "c"), true);
		tree.put(Arrays.asList("a", "b", "x", "y", "z", "c"), true);
		tree.put(Arrays.asList("a", "b", "y", "c"), true);
		tree.put(Arrays.asList("a", "d", "c"), true);
		tree.put(Arrays.asList("a", "d", "e"), true);
		tree.put(Arrays.asList("1", "2", "3", "4", "t", "v"), true);
		tree.put(Arrays.asList("1", "2", "3"), true);
		tree.put(Arrays.asList("1", "2", "3", "4"), true);

		tree.put(Arrays.asList("1", "2", "3", "4", "t"), true);
		tree.put(Arrays.asList("4", "5", "6"), true);
		Iterator<List<String>> iter = tree.keySet().iterator();
		while (iter.hasNext()) {
			List<String> list = iter.next();
			System.out.println(list);
		}
		tree.remove(Arrays.asList("1", "2", "3", "4", "t"));
		System.out.println();
		iter = tree.keySet().iterator();
		while (iter.hasNext()) {
			List<String> list = iter.next();
			System.out.println(list);
		}
		tree.remove(Arrays.asList("1", "2", "3", "4"));
		System.out.println();
		iter = tree.keySet().iterator();
		while (iter.hasNext()) {
			List<String> list = iter.next();
			System.out.println(list);
		}
		tree.remove(Arrays.asList("1", "2", "3", "4", "t", "v"));
		System.out.println();
		iter = tree.keySet().iterator();
		while (iter.hasNext()) {
			List<String> list = iter.next();
			System.out.println(list);
		}
	}

	public boolean containsElement(K t) {
		return map.containsKey(t);
	}

	public boolean containsValue(Object value) {
		throw new UnsupportedOperationException();
	}

	public Set<java.util.Map.Entry<List<K>, V>> entrySet() {
		throw new UnsupportedOperationException();
	}

	@SuppressWarnings("unchecked")
	public V get(Object key) {

		if (!(key instanceof List))
			return null;
		final List<K> l = (List<K>) key;
		return get(l, 0);

	}

	private V get(List<K> l, int start) {
		final K first = l.get(start);
		final Trie<K, V> trie = map.get(first);
		if (trie == null)
			return null;
		if (start == l.size() - 1)
			return trie.v;

		return trie.get(l, start + 1);
	}

	public Set<List<K>> keySet() {
		return new AbstractSet<List<K>>() {

			@Override
			public Iterator<List<K>> iterator() {
				return setIterator();
			}

			@Override
			public int size() {
				return size;
			}

		};
	}

	public Collection<V> values() {
		throw new UnsupportedOperationException();
	}

	public V getNodeValue() {
		return v;
	}

}