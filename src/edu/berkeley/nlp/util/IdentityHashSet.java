package edu.berkeley.nlp.util;

import java.util.*;

public class IdentityHashSet<E> extends AbstractSet<E> {
	private final Map<E, E> map = new IdentityHashMap();

	public IdentityHashSet() {
	}

	public IdentityHashSet(Collection<? extends E> c) {
		for (E o : c)
			add(o);
	}

	public int size() {
		return map.size();
	}

	public boolean contains(Object o) {
		return map.containsKey(o);
	}

	public Iterator iterator() {
		return map.keySet().iterator();
	}

	public boolean add(E o) {
		return map.put(o, o) == null;
	}

	public boolean remove(Object o) {
		return map.remove(o) != null;
	}

	public void clear() {
		map.clear();
	}
}
