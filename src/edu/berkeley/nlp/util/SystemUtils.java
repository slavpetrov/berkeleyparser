package edu.berkeley.nlp.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class SystemUtils {

	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface SkipMemoryCount {
	}

	/**
	 * Counts the memory of the object and everything it points to. Does not
	 * count static fields.
	 * 
	 * @param o
	 * @return
	 */
	public static long countApproximateMemoryUsage(Object... o) {
		try {
			final IdentityHashSet<Object> identityHashSet = new IdentityHashSet<Object>();
			long sum = 0;
			for (Object obj : o) {
				sum += countApproximateMemoryUsage(obj, identityHashSet);
			}
			return sum;
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			throw new RuntimeException(e);

		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			throw new RuntimeException(e);

		}
	}

	private static long countApproximateMemoryUsage(Object o,
			IdentityHashSet<Object> identityHashSet)
			throws IllegalArgumentException, IllegalAccessException {
		if (o == null || identityHashSet.contains(o))
			return 0;
		identityHashSet.add(o);
		Class thisType = o.getClass();
		// object overhead is 8 bytes
		long sum = 8;

		if (thisType.isArray()) {
			if (thisType == byte[].class) {
				sum += 1 * ((byte[]) o).length;
			} else if (thisType == boolean[].class) {
				sum += 1 * ((boolean[]) o).length;
			} else if (thisType == short[].class) {
				sum += 2 * ((short[]) o).length;
			} else if (thisType == char[].class) {
				sum += 2 * ((char[]) o).length;
			} else if (thisType == int[].class) {
				sum += 4 * ((int[]) o).length;
			} else if (thisType == double[].class) {
				sum += 8 * ((double[]) o).length;
			} else if (thisType == long[].class) {
				sum += 8 * ((long[]) o).length;
			} else if (thisType == float[].class) {
				sum += 4 * ((float[]) o).length;
			} else {
				// an array objects
				for (Object o2 : (Object[]) o) {
					sum += countApproximateMemoryUsage(o2, identityHashSet);
				}
			}
		}

		long fieldSum = 0;
		List<Class> list = new ArrayList<Class>();
		Class c = o.getClass();
		list.add(c);
		while (c.getSuperclass() != null) {
			c = c.getSuperclass();
			list.add(c);
		}
		for (Class cl : list) {
			for (Field field : cl.getDeclaredFields()) {
				if (java.lang.reflect.Modifier.isStatic(field.getModifiers()))
					continue;
				final Class<?> type = field.getType();
				field.setAccessible(true);
				if (type.isPrimitive()) {
					if (type == long.class || type == double.class) {
						fieldSum += 8;
					} else {
						fieldSum += 4;
					}
				} else {
					// for the pointer
					fieldSum += 4;
					if (!field.isSynthetic()
							&& !field
									.isAnnotationPresent(SkipMemoryCount.class)) {
						final Object object = field.get(o);
						if (Thread.currentThread().getStackTrace().length > 50) {
							//
							throw new OutOfMemoryError(
									"measuring memory usage used too much stack");
						}
						sum += countApproximateMemoryUsage(object,
								identityHashSet);
					}

				}

			}
		}

		if (fieldSum % 8 != 0) {
			fieldSum += 4;
		}
		return sum + fieldSum;

	}

	public static void main(String[] argv) {

		final Object testObject = new Object() {
			short[] x = new short[100];

			String[] y = new String[] { "xx", "yy" };
		};
		Object testObject2 = new Object() {

			Object o = testObject;
		};

		System.out
				.println(countApproximateMemoryUsage(testObject, testObject2));
		System.out.println(countApproximateMemoryUsage(new Integer(1)));

	}

}
