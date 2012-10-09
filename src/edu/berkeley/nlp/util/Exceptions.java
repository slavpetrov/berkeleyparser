package edu.berkeley.nlp.util;

public class Exceptions {
	public static RuntimeException bad = new RuntimeException("BAD");

	public static RuntimeException bad(Object o) {
		return new RuntimeException("" + o);
	}

	public static RuntimeException bad(String fmt, Object... args) {
		return new RuntimeException(String.format(fmt, args));
	}

	public static RuntimeException unknownCase(Object o) {
		return new RuntimeException("Unknown case: " + o);
	}

	public static RuntimeException unsupported(Object o) {
		return new RuntimeException("Function is unsupported:" + o);
	}

	public static RuntimeException unsupported = new RuntimeException(
			"Function is unsupported");
	public static RuntimeException unimplemented = new RuntimeException(
			"Function has not been implemented");
	public static RuntimeException unknownCase = new RuntimeException(
			"Unknown case");

	// Replacement for assert
	public static void enforce(boolean b, Object... o) {
		if (!b)
			throw bad(StrUtils.join(o));
	}
}
