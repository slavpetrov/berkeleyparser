package edu.berkeley.nlp.util;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Union of two maps (sort of). Doesn't handle duplicate keys (keys in both
 * maps). Unmodifiable.
 * 
 * @author adampauls
 * 
 * @param <K>
 */
public class ConcatenationMap<K, V> extends AbstractMap<K, V> {
	private Set<Entry<K, V>> entrySet;

	@Override
	public Set<Entry<K, V>> entrySet() {
		if (entrySet == null) {
			List<Set<Entry<K, V>>> list = new ArrayList<Set<Entry<K, V>>>();
			for (Map<K, V> map : maps) {
				list.add(map.entrySet());
			}
			entrySet = new ConcatenationSet<Entry<K, V>>(list);
		}
		return entrySet;
	}

	@Override
	public V get(Object arg0) {
		for (Map<K, V> map : maps) {
			V v = map.get(arg0);
			if (v != null)
				return v;
		}
		return null;
	}

	@Override
	public boolean containsKey(Object arg0) {
		for (Map<K, V> map : maps) {
			if (map.containsKey(arg0))
				return true;
		}
		return false;
	}

	private Collection<Map<K, V>> maps;

	private int size = 0;

	public ConcatenationMap(Collection<Map<K, V>> maps) {
		this.maps = maps;
		for (Map<K, V> set : maps) {

			size += set.size();
		}
	}

	@Override
	public int size() {
		return size;

	}

}