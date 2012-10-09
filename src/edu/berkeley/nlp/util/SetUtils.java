package edu.berkeley.nlp.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * User: aria42 Date: Mar 29, 2009
 */
public class SetUtils {
	public static <K> Set<K> intersection(Factory<Set<K>> fact,
			Collection<K>... sets) {
		Set<K> result = fact.newInstance();
		if (sets.length == 0)
			return result;
		result.addAll(sets[0]);
		for (int i = 1; i < sets.length; i++) {
			result.retainAll(sets[i]);
		}
		return result;
	}

	public static <K> Set<K> intersection(Collection<K>... sets) {
		return intersection(new Factory.DefaultFactory<Set<K>>(HashSet.class),
				sets);
	}

}
