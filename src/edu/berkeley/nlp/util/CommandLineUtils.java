package edu.berkeley.nlp.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Utilities for managing command line flags and arguments.
 * 
 * @author Dan Klein
 */
public class CommandLineUtils {
	/**
	 * Simple method which turns an array of command line arguments into a map,
	 * where each token starting with a '-' is a key and the following non '-'
	 * initial token, if there is one, is the value. For example, '-size 5
	 * -verbose' will produce keys (-size,5) and (-verbose,null).
	 */
	public static Map<String, String> simpleCommandLineParser(String[] args) {
		Map<String, String> map = new HashMap<String, String>();
		for (int i = 0; i <= args.length; i++) {
			String key = (i > 0 ? args[i - 1] : null);
			String value = (i < args.length ? args[i] : null);// .toLowerCase();
			if (key == null || key.startsWith("-")) {
				if (value != null && value.startsWith("-"))
					value = null;
				if (key != null || value != null)
					map.put(key, value);

			}
		}
		return map;
	}

	/**
	 * Simple method to look up a key in an argument map. Returns the
	 * defaultValue if the argument is not specified in the map.
	 */
	public static String getValueOrUseDefault(Map<String, String> args,
			String key, String defaultValue) {
		if (args.containsKey(key))
			return args.get(key);
		return defaultValue;
	}
}
