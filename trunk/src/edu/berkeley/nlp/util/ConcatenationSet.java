package edu.berkeley.nlp.util;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

/**
 * Union of two sets (sort of). Doesn't remove duplicates. Unmodifiable.
 * 
 * @author adampauls
 * 
 * @param <K>
 */
public class ConcatenationSet<K> extends AbstractSet<K> {
	@Override
	public boolean contains(Object arg0) {
		for (Set<K> set : sets) {
			if (set.contains(arg0))
				return true;
		}
		return false;
	}

	private Collection<Set<K>> sets;

	private int size = 0;

	public ConcatenationSet(Collection<Set<K>> sets) {
		this.sets = sets;
		for (Set<K> set : sets) {

			size += set.size();
		}
	}

	@Override
	public Iterator<K> iterator() {
		return new ConcatenationIterable<K>(sets).iterator();
	}

	@Override
	public int size() {
		return size;

	}

}