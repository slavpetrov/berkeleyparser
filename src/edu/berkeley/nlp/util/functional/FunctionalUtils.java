package edu.berkeley.nlp.util.functional;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import edu.berkeley.nlp.util.CollectionUtils;
import edu.berkeley.nlp.util.Factory;
import edu.berkeley.nlp.util.LazyIterable;
import edu.berkeley.nlp.util.Pair;

/**
 * Collection of Functional Utilities you'd find in any functional programming
 * language. Things like map, filter, reduce, etc..
 * 
 * Created by IntelliJ IDEA. User: aria42 Date: Oct 7, 2008 Time: 1:06:08 PM
 */
public class FunctionalUtils {

	public static <T> List<T> take(Iterator<T> it, int n) {
		List<T> result = new ArrayList<T>();
		for (int i = 0; i < n && it.hasNext(); ++i) {
			result.add(it.next());
		}
		return result;
	}

	private static Method getMethod(Class c, String field) {
		Method[] methods = c.getDeclaredMethods();
		String trgMethName = "get" + field;
		Method trgMeth = null;
		for (Method m : methods) {
			if (m.getName().equalsIgnoreCase(trgMethName)
					|| m.getName().equalsIgnoreCase(field)) {
				return m;
			}
		}
		return null;
	}

	private static Field getField(Class c, String fieldName) {
		Field[] fields = c.getDeclaredFields();
		for (Field f : fields) {
			if (f.getName().equalsIgnoreCase(fieldName)) {
				return f;
			}
		}
		return null;
	}

	public static <T> Pair<T, Double> findMax(Iterable<T> xs,
			Function<T, Double> fn) {
		double max = Double.NEGATIVE_INFINITY;
		T argMax = null;
		for (T x : xs) {
			double val = fn.apply(x);
			if (val > max) {
				max = val;
				argMax = x;
			}
		}
		return Pair.newPair(argMax, max);
	}

	public static <T> Pair<T, Double> findMin(Iterable<T> xs,
			Function<T, Double> fn) {
		double min = Double.POSITIVE_INFINITY;
		T argMin = null;
		for (T x : xs) {
			double val = fn.apply(x);
			if (val < min) {
				min = val;
				argMin = x;
			}
		}
		return Pair.newPair(argMin, min);
	}

	public static <K, I, V> Map<K, V> compose(Map<K, I> map, Function<I, V> fn) {
		return map(map, fn, (Predicate<K>) Predicates.getTruePredicate(),
				new HashMap<K, V>());
	}

	public static <K, I, V> Map<K, V> compose(Map<K, I> map, Function<I, V> fn,
			Predicate<K> pred) {
		return map(map, fn, pred, new HashMap<K, V>());
	}

	public static <C> List make(Factory<C> factory, int k) {
		List<C> insts = new ArrayList<C>();
		for (int i = 0; i < k; i++) {
			insts.add(factory.newInstance());
		}
		// Fuck you cvs
		return insts;
	}

	public static <K, I, V> Map<K, V> map(Map<K, I> map, Function<I, V> fn,
			Predicate<K> pred, Map<K, V> resultMap) {
		for (Map.Entry<K, I> entry : map.entrySet()) {
			K key = entry.getKey();
			I inter = entry.getValue();
			if (pred.apply(key))
				resultMap.put(key, fn.apply(inter));
		}
		return resultMap;
	}

	public static <I, O> Map<I, O> mapPairs(Iterable<I> lst, Function<I, O> fn) {
		return mapPairs(lst, fn, new HashMap<I, O>());
	}

	public static <I, O> Map<I, O> mapPairs(Iterable<I> lst, Function<I, O> fn,
			Map<I, O> resultMap) {
		for (I input : lst) {
			O output = fn.apply(input);
			resultMap.put(input, output);
		}
		return resultMap;
	}

	public static <I, O> List<O> map(Iterable<I> lst, Function<I, O> fn) {
		return map(lst, fn, (Predicate<O>) Predicates.getTruePredicate());
	}

	public static <I, O> Iterable<O> lazyMap(Iterable<I> lst, Function<I, O> fn) {
		return lazyMap(lst, fn, (Predicate<O>) Predicates.getTruePredicate());
	}

	public static <I, O> Iterable<O> lazyMap(Iterable<I> lst,
			Function<I, O> fn, Predicate<O> pred) {
		return new LazyIterable<O, I>(lst, fn, pred, 20);
	}

	public static <I, O> List<O> flatMap(Iterable<I> lst,
			Function<I, List<O>> fn) {
		Predicate<List<O>> p = Predicates.getTruePredicate();
		return flatMap(lst, fn, p);
	}

	public static <I, O> List<O> flatMap(Iterable<I> lst,
			Function<I, List<O>> fn, Predicate<List<O>> pred) {
		List<List<O>> lstOfLsts = map(lst, fn, pred);
		List<O> init = new ArrayList<O>();
		return reduce(lstOfLsts, init,
				new Function<Pair<List<O>, List<O>>, List<O>>() {
					public List<O> apply(Pair<List<O>, List<O>> input) {
						List<O> result = input.getFirst();
						result.addAll(input.getSecond());
						return result;
					}
				});
	}

	public static <I, O> O reduce(Iterable<I> inputs, O initial,
			Function<Pair<O, I>, O> fn) {
		O output = initial;
		for (I input : inputs) {
			output = fn.apply(Pair.newPair(output, input));
		}
		return output;
	}

	public static <I, O> List<O> map(Iterable<I> lst, Function<I, O> fn,
			Predicate<O> pred) {
		List<O> outputs = new ArrayList();
		for (I input : lst) {
			O output = fn.apply(input);
			if (pred.apply(output)) {
				outputs.add(output);
			}
		}
		return outputs;
	}

	public static <I> List<I> filter(final Iterable<I> lst,
			final Predicate<I> pred) {
		List<I> ret = new ArrayList<I>();
		for (I input : lst) {
			if (pred.apply(input))
				ret.add(input);
		}
		return ret;
	}

	public static <O, T> Function getAccessor(String field, Class c) {
		final Method trgMeth = getMethod(c, field);
		final Field trgField = getField(c, field);
		if (trgMeth == null && trgField == null) {
			throw new RuntimeException(
					"Couldn't find field or method to access " + field);
		}
		return new Function<O, T>() {
			public T apply(O input) {
				try {
					return (T) (trgMeth != null ? trgMeth.invoke(input)
							: trgField.get(input));
				} catch (Exception e) {
					e.printStackTrace();
					throw new RuntimeException(
							"Error accessing Method or target");
				}
			}
		};
	}

	public static <K, O> Map<K, Collection<O>> groupBy(Iterable<O> objs,
			Function<O, K> groupFn) {
		return groupBy(objs, groupFn, new Factory<Collection<O>>() {
			public Collection<O> newInstance(Object... args) {
				return new ArrayList<O>();
			}
		});
	}

	public static <K, O> Map<K, Collection<O>> groupBy(Iterable<O> objs,
			String field) {
		return groupBy(objs,
				getAccessor(field, objs.iterator().next().getClass()));
	}

	/**
	 * Groups <code>objs</code> by the field <code>field</code>. Tries to find
	 * public method getField, ignoring case, then to directly access the field
	 * if that fails.
	 * 
	 * @param objs
	 * @param field
	 * @return
	 */
	public static <K, O, C extends Collection<O>> Map<K, C> groupBy(
			Iterable<O> objs, Function<O, K> groupFn, final Factory<C> fact) {
		Iterator<O> it = objs.iterator();
		if (!it.hasNext())
			return new HashMap<K, C>();
		Map<K, C> map = new HashMap<K, C>();
		for (O obj : objs) {
			K key = null;
			try {
				key = groupFn.apply(obj);
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
			CollectionUtils.addToValueCollection(map, key, obj, fact);
		}
		return map;
	}

	public static <T> T first(Iterable<T> objs, Predicate<T> pred) {
		for (T obj : objs) {
			if (pred.apply(obj))
				return obj;
		}
		return null;
	}

	public static <O, K> List<O> filter(Iterable<O> coll, final String field,
			final K value) throws Exception {
		Iterator<O> it = coll.iterator();
		if (!it.hasNext())
			return new ArrayList<O>();
		Class c = it.next().getClass();
		final Method m = getMethod(c, field);
		final Field f = getField(c, field);
		return filter(coll, new Predicate<O>() {
			public Boolean apply(O input) {
				try {
					K inputVal = (K) (m != null ? m.invoke(input) : f
							.get(input));
					return inputVal.equals(value);
				} catch (Exception e) {
				}
				return false;
			}
		});
	}

	public static List<Integer> range(int n) {
		List<Integer> result = new ArrayList<Integer>();
		for (int i = 0; i < n; i++) {
			result.add(i);
		}
		return result;
	}

	/**
	 * Testing Purposes
	 */
	private static class Person {
		public String prefix;
		public String name;

		public Person(String name) {
			this.name = name;
			this.prefix = name.substring(0, 3);
		}

		@Override
		public String toString() {
			return "Person(" + name + ")";
		}
	}

	public static <T> T find(Iterable<T> elems, Predicate<T> pred) {
		for (T elem : elems) {
			if (pred.apply(elem))
				return elem;
		}
		return null;
	}

	public static void main(String[] args) throws Exception {
		List<Person> objs = CollectionUtils.makeList(new Person("david"),
				new Person("davs"), new Person("maria"), new Person("marshia"));
		Map<String, Collection<Person>> grouped = groupBy(objs,
				getAccessor("prefix", Person.class));
		System.out.printf("groupd: %s", grouped);
	}
}
