package edu.berkeley.nlp.util;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

public class IdentityHashSet<E> extends AbstractSet<E> {
	private final Map<E, E> map = new IdentityHashMap();

	public IdentityHashSet() {
	}

	public IdentityHashSet(Collection<? extends E> c) {
		for (E o : c)
			add(o);
	}

	@Override
	public int size() {
		return map.size();
	}

	@Override
	public boolean contains(Object o) {
		return map.containsKey(o);
	}

	@Override
	public Iterator iterator() {
		return map.keySet().iterator();
	}

	@Override
	public boolean add(E o) {
		return map.put(o, o) == null;
	}

	@Override
	public boolean remove(Object o) {
		return map.remove(o) != null;
	}

	@Override
	public void clear() {
		map.clear();
	}
}
