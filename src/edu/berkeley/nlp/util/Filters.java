package edu.berkeley.nlp.util;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * Filters contains some simple implementations of the Filter interface.
 * 
 * @author Christopher Manning
 * @version 1.0
 */
public class Filters {

	/**
	 * Nothing to instantiate
	 */
	private Filters() {
	}

	/**
	 * The acceptFilter accepts everything.
	 */
	public static Filter acceptFilter() {
		return new CategoricalFilter(true);
	}

	public static <T> Filter<T> acceptFilter(Class<T> name) {
		return new CategoricalFilter<T>(true);
	}

	public static <T> Filter<T> acceptFilter(T dummyObject) {
		return new CategoricalFilter<T>(true);
	}

	/**
	 * The rejectFilter accepts nothing.
	 */
	public static Filter rejectFilter() {
		return new CategoricalFilter(false);
	}

	private static final class CategoricalFilter<T> implements Filter<T> {

		private final boolean judgment;

		private CategoricalFilter(boolean judgment) {
			this.judgment = judgment;
		}

		/**
		 * Checks if the given object passes the filter.
		 * 
		 * @param obj
		 *            an object to test
		 */
		public boolean accept(T obj) {
			return judgment;
		}
	}

	/**
	 * The collectionAcceptFilter accepts a certain collection.
	 */
	public static <T> Filter<T> collectionAcceptFilter(T[] objs) {
		return new CollectionAcceptFilter<T>(Arrays.asList(objs), true);
	}

	/**
	 * The collectionAcceptFilter accepts a certain collection.
	 */
	public static <T> Filter<T> collectionAcceptFilter(Collection<T> objs) {
		return new CollectionAcceptFilter<T>(objs, true);
	}

	/**
	 * The collectionRejectFilter rejects a certain collection.
	 */
	public static <T> Filter<T> collectionRejectFilter(T[] objs) {
		return new CollectionAcceptFilter<T>(Arrays.asList(objs), false);
	}

	/**
	 * The collectionRejectFilter rejects a certain collection.
	 */
	public static <T> Filter<T> collectionRejectFilter(Collection<T> objs) {
		return new CollectionAcceptFilter<T>(objs, false);
	}

	private static final class CollectionAcceptFilter<T> implements Filter<T>,
			Serializable {

		private static final long serialVersionUID = 1L;
		private final Collection<T> args;
		private final boolean judgment;

		private CollectionAcceptFilter(Collection<T> c, boolean judgment) {
			this.args = new HashSet<T>(c);
			this.judgment = judgment;
		}

		/**
		 * Checks if the given object passes the filter.
		 * 
		 * @param obj
		 *            an object to test
		 */
		public boolean accept(T obj) {
			if (args.contains(obj)) {
				return judgment;
			} else {
				return !judgment;
			}
		}
	}

	/**
	 * Filter that accepts only when both filters accept (AND).
	 */
	public static <T> Filter<T> andFilter(Filter<T> f1, Filter<T> f2) {
		return (new CombinedFilter<T>(f1, f2, true));
	}

	/**
	 * Filter that accepts when either filter accepts (OR).
	 */
	public static <T> Filter<T> orFilter(Filter<T> f1, Filter<T> f2) {
		return (new CombinedFilter<T>(f1, f2, false));
	}

	/**
	 * Conjunction or disjunction of two filters.
	 */
	private static class CombinedFilter<T> implements Filter<T> {
		private Filter<T> f1, f2;
		private boolean conjunction; // and vs. or

		public CombinedFilter(Filter<T> f1, Filter<T> f2, boolean conjunction) {
			this.f1 = f1;
			this.f2 = f2;
			this.conjunction = conjunction;
		}

		public boolean accept(T o) {
			if (conjunction) {
				return (f1.accept(o) && f2.accept(o));
			}
			return (f1.accept(o) || f2.accept(o));
		}
	}

	/**
	 * Filter that does the opposite of given filter (NOT).
	 */
	public static <T> Filter<T> notFilter(Filter<T> filter) {
		return (new NegatedFilter<T>(filter));
	}

	/**
	 * Filter that's either negated or normal as specified.
	 */
	public static <T> Filter<T> switchedFilter(Filter<T> filter, boolean negated) {
		return (new NegatedFilter<T>(filter, negated));
	}

	/**
	 * Negation of a filter.
	 */
	private static class NegatedFilter<T> implements Filter<T> {
		private Filter<T> filter;
		private boolean negated;

		public NegatedFilter(Filter<T> filter, boolean negated) {
			this.filter = filter;
			this.negated = negated;
		}

		public NegatedFilter(Filter<T> filter) {
			this(filter, true);
		}

		public boolean accept(T o) {
			return (negated ^ filter.accept(o)); // xor
		}
	}

	/**
	 * Applies the given filter to each of the given elems, and returns the list
	 * of elems that were accepted. The runtime type of the returned array is
	 * the same as the passed in array.
	 */
	public static <T> Object[] filter(T[] elems, Filter<T> filter) {
		List<T> filtered = new ArrayList<T>();
		for (int i = 0; i < elems.length; i++) {
			if (filter.accept(elems[i])) {
				filtered.add(elems[i]);
			}
		}
		return (filtered.toArray((Object[]) Array.newInstance(elems.getClass()
				.getComponentType(), filtered.size())));
	}

	/**
	 * Removes all elems in the given Collection that aren't accepted by the
	 * given Filter.
	 */
	public static <T> void retainAll(Collection<T> elems, Filter<T> filter) {
		for (T elem : elems) {
			if (!filter.accept(elem)) {
				elems.remove(elem);
			}
		}
	}

}