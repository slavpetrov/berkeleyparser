package edu.berkeley.nlp.util;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * The MapFactory is a mechanism for specifying what kind of map is to be used
 * by some object. For example, if you want a Counter which is backed by an
 * IdentityHashMap instead of the defaul HashMap, you can pass in an
 * IdentityHashMapFactory.
 * 
 * @author Dan Klein
 */

public abstract class SetFactory<K> implements Serializable {

	public static class HashSetFactory<K> extends SetFactory<K> {
		private static final long serialVersionUID = 1L;

		@Override
		public Set<K> buildSet() {
			return new HashSet<K>();
		}
	}

	public static class IdentityHashMapFactory<K> extends SetFactory<K> {
		private static final long serialVersionUID = 1L;

		@Override
		public Set<K> buildSet() {
			return new IdentityHashSet<K>();
		}
	}

	public static class TreeMapFactory<K> extends SetFactory<K> {
		private static final long serialVersionUID = 1L;

		@Override
		public Set<K> buildSet() {
			return new TreeSet<K>();
		}
	}

	public abstract Set<K> buildSet();
}
