package edu.berkeley.nlp.util;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maintains a two-way map between a set of objects and contiguous integers from
 * 0 to the number of objects. Use get(i) to look up object i, and
 * indexOf(object) to look up the index of an object.
 * 
 * @author Dan Klein
 */
public class Indexer<E> extends AbstractList<E> implements Serializable {
	private static final long serialVersionUID = -8769544079136550516L;

	protected List<E> objects;

	protected Map<E, Integer> indexes;

	protected boolean locked = false;

	@Override
	public void clear() {
		objects.clear();
		indexes.clear();
	}

	public void lock() {
		this.locked = true;
	}

	public void unlock() {
		this.locked = false;
	}

	/**
	 * Return the object with the given index
	 * 
	 * @param index
	 */
	@Override
	@Deprecated
	public E get(int index) {
		return objects.get(index);
	}

	public E getObject(int index) {
		return objects.get(index);
	}

	/**
	 * @author aria42
	 */
	@Override
	public boolean add(E elem) {
		if (locked)
			throw new IllegalStateException("Tried to add to locked indexer");
		if (contains(elem)) {
			return false;
		}
		indexes.put(elem, size());
		objects.add(elem);
		return true;
	}

	/**
	 * Returns the number of objects indexed.
	 */
	@Override
	public int size() {
		return objects.size();
	}

	/**
	 * Returns the index of the given object, or -1 if the object is not present
	 * in the indexer.
	 * 
	 * @param o
	 * @return
	 */
	@Override
	public int indexOf(Object o) {
		Integer index = indexes.get(o);
		if (index == null)
			return -1;
		return index;
	}

	/**
	 * Constant time override for contains.
	 */
	@Override
	public boolean contains(Object o) {
		return indexes.keySet().contains(o);
	}

	// Return the index of the element
	// If doesn't exist, add it.
	public int getIndex(E e) {
		if (e == null)
			return -1;
		Integer index = indexes.get(e);
		if (index == null) {
			if (locked)
				return -1;
			index = size();
			objects.add(e);
			indexes.put(e, index);
		}
		return index;
	}

	public Indexer() {
		objects = new ArrayList<E>();
		indexes = new HashMap<E, Integer>();
	}

	public Indexer(Collection<? extends E> c) {
		this();
		for (E a : c)
			getIndex(a);
	}

	// Not really safe; trust them not to modify it
	public List<E> getObjects() {
		return objects;
	}

	public E[] getObjects(int[] is) {
		if (size() == 0)
			return null; // throw Exceptions.bad("Can't instantiate array");
		int n = is.length;
		Class c = objects.get(0).getClass();
		E[] os = (E[]) Array.newInstance(c, n);
		for (int i = 0; i < n; i++)
			os[i] = is[i] == -1 ? null : getObject(is[i]);
		return os;
	}
}
