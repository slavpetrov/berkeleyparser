package edu.berkeley.nlp.util;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

/**
 * Created by IntelliJ IDEA. User: aria42 Date: Oct 7, 2008 Time: 1:06:08 PM
 */
public class SmartMapUtils {

	/**
	 * Groups <code>objs</code> by the field <code>field</code>. Tries to find
	 * public method getField, ignoring case, then to directly access the field
	 * if that fails.
	 * 
	 * @param objs
	 * @param field
	 * @return
	 */
	public static <K, O> Map<K, List<O>> groupBy(Iterable<O> objs, String field)
			throws Exception {
		Class c = objs.iterator().next().getClass();
		Method[] methods = c.getDeclaredMethods();
		String trgMethName = "get" + field;
		Method trgMeth = null;
		Field trgField = null;
		for (Method m : methods) {
			if (m.getName().equalsIgnoreCase(trgMethName)) {
				trgMeth = m;
				break;
			}
		}
		if (trgMeth == null) {
			Field[] fields = c.getDeclaredFields();
			for (Field f : fields) {
				if (f.getName().equalsIgnoreCase(field)) {
					trgField = f;
					break;
				}
			}
		}
		if (trgMeth == null && trgField == null) {
			throw new RuntimeException(
					"Couldn't find field or method to access " + field);
		}
		Map<K, List<O>> map = new HashMap<K, List<O>>();
		for (O obj : objs) {
			K key = null;
			try {
				if (trgMeth != null) {
					key = (K) trgMeth.invoke(obj);
				} else {
					key = (K) trgField.get(obj);
				}
			} catch (Exception e) {
				throw new RuntimeException();
			}
			CollectionUtils.addToValueList(map, key, obj);
		}
		return map;
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

		public String toString() {
			return "Person(" + name + ")";
		}
	}

	public static void main(String[] args) throws Exception {
		List<Person> objs = CollectionUtils.makeList(new Person("david"),
				new Person("davs"), new Person("maria"), new Person("marshia"));
		Map<String, List<Person>> grouped = groupBy(objs, "prefix");
		System.out.printf("groupd: %s", grouped);
	}
}
