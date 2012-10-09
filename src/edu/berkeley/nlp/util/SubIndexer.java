package edu.berkeley.nlp.util;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maintains a two-way map between a set of (objects, substate) pairs and
 * contiguous integers from 0 to the number of objects. It also maintains a map
 * from (object, substate) pairs to contiguous integers. Use get(i) to look up
 * object i, and indexOf(object) to look up the index of an object. This class
 * was modified to accomodate objects that can have several substates. It is
 * assumed that if an object is added several times, it will be with the same
 * number of substates.
 * 
 * @author Dan Klein
 * @author Romain Thibaux
 */
public class SubIndexer<E> extends AbstractList<E> {
	List<E> objects;
	// Index from 0 to objects.size()-1 of a given object:
	Map<E, Integer> indexes;
	// Sub-indexes (indexes of substates) of the object whose index is i are [
	// subindexes(i), subindexes(i+1) [
	List<Integer> subindexes;

	/**
	 * Return the object with the given index
	 * 
	 * @param index
	 */
	@Override
	public E get(int index) {
		return objects.get(index);
	}

	/**
	 * Returns the number of objects indexed (not the total number of substates)
	 */
	@Override
	public int size() {
		return objects.size();
	}

	/**
	 * Returns the total number of substates
	 */
	public int totalSize() {
		return subindexes.get(subindexes.size() - 1);
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

	public int subindexBegin(int index) {
		return subindexes.get(index);
	}

	public int subindexEnd(int index) {
		return subindexes.get(index + 1);
	}

	/**
	 * Constant time override for contains.
	 */
	@Override
	public boolean contains(Object o) {
		return indexes.keySet().contains(o);
	}

	/**
	 * Add an element to the indexer. If the element is already in the indexer,
	 * the indexer is unchanged (and false is returned).
	 * 
	 * @param e
	 * @return
	 */
	public boolean add(E e, int numSubstates) {
		if (contains(e))
			return false;
		objects.add(e);
		indexes.put(e, size() - 1);
		Integer previousSubindex = subindexes.get(subindexes.size() - 1);
		subindexes.add(previousSubindex + numSubstates);
		return true;
	}

	public SubIndexer() {
		objects = new ArrayList<E>();
		indexes = new HashMap<E, Integer>();
		subindexes = new ArrayList<Integer>();
		subindexes.add(0);
	}
}
