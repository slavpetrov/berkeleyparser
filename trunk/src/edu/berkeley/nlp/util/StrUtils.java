package edu.berkeley.nlp.util;

import java.util.*;
import java.util.regex.*;
import java.lang.reflect.*;

public class StrUtils {
	public static String[] split(String s) {
		// Differs from Java's functions in that it returns String[0] on ""
		return split(s, " ");
	}

	public static String[] split(String s, String delim) {
		return isEmpty(s) ? new String[0] : s.split(delim);
	}

	// delim is treated as a string, rather than a list of delimiters
	public static List<String> splitByStr(String s, String delim) {
		if (isEmpty(s))
			return Collections.emptyList();
		ArrayList<String> tokens = new ArrayList<String>();
		int i = 0;
		while (i < s.length()) {
			int j = s.indexOf(delim, i);
			if (j == -1)
				break;
			tokens.add(s.substring(i, j));
			i = j + delim.length();
		}
		tokens.add(s.substring(i));
		return tokens;
	}

	// Return the first occurrence of c that doesn't have an escape character in
	// front of it starting at position i
	public static int indexOfIgnoreEscaped(String s, char c) {
		return indexOfIgnoreEscaped(s, c, 0);
	}

	public static int indexOfIgnoreEscaped(String s, char c, int i) {
		return indexOfIgnoreEscaped(s, "" + c, i);
	}

	// Find first occurrence of some non-escaped character in cs
	public static int indexOfIgnoreEscaped(String s, String cs, int i) {
		boolean escape = false;
		while (i < s.length()) {
			if (escape)
				escape = false;
			else {
				if (s.charAt(i) == '\\') // Enable escaping
					escape = true;
				else if (cs.indexOf(s.charAt(i)) != -1)
					return i;
			}
			i++;
		}
		return -1;
	}

	// Split, but mind the escaped delimiters.
	// Example: "a\ b c" => "a\ b" "c"
	public static List<String> splitIgnoreEscaped(String line, String delim) {
		// Split
		String[] tokens = StrUtils.split(line, delim);
		// But now, have to piece together the escaped delimiters
		List<String> newTokens = new ArrayList<String>();
		for (int i = 0; i < tokens.length; i++) {
			if (tokens[i].endsWith("\\") && i + 1 < tokens.length)
				tokens[i + 1] = tokens[i].substring(0, tokens[i].length() - 1)
						+ "\\" + delim + tokens[i + 1];
			else
				newTokens.add(tokens[i]);
		}
		return newTokens;
	}

	public static double[] doubleSplit(String s, String delim) {
		String[] tokens = split(s, delim);
		double[] data = new double[tokens.length];
		for (int i = 0; i < tokens.length; i++)
			data[i] = Double.parseDouble(tokens[i]);
		return data;
	}

	public static double[] doubleSplit(String s) {
		return doubleSplit(s, " ");
	}

	public static int[] intSplit(String s, String delim) {
		String[] tokens = split(s, delim);
		int[] data = new int[tokens.length];
		for (int i = 0; i < tokens.length; i++)
			data[i] = Integer.parseInt(tokens[i]);
		return data;
	}

	public static int[] intSplit(String s) {
		return intSplit(s, " ");
	}

	public static short[] shortSplit(String s, String delim) {
		String[] tokens = split(s, delim);
		short[] data = new short[tokens.length];
		for (int i = 0; i < tokens.length; i++)
			data[i] = Short.parseShort(tokens[i]);
		return data;
	}

	public static short[] shortSplit(String s) {
		return shortSplit(s, " ");
	}

	public static List<Integer> intSplitList(String s, String delim) {
		String[] tokens = split(s, delim);
		ArrayList<Integer> data = new ArrayList<Integer>(tokens.length);
		for (int i = 0; i < tokens.length; i++)
			data.add(Integer.parseInt(tokens[i]));
		return data;
	}

	public static List<Integer> intSplitList(String s) {
		return intSplitList(s, " ");
	}

	// Example: a=3 b=4
	public static Map<String, String> parseHashMap(String line,
			String keyValueDelim) {
		return parseHashMap(line, keyValueDelim, " ");
	}

	public static Map<String, String> parseHashMap(String line,
			String keyValueDelim, String entryDelim) {
		return parseHashMap(Arrays.asList(StrUtils.split(line, entryDelim)),
				keyValueDelim);
	}

	public static Map<String, String> parseHashMap(List<String> tokens,
			String keyValueDelim) {
		Map<String, String> map = new HashMap<String, String>();
		for (String token : tokens) {
			String[] kv = token.split(keyValueDelim);
			if (kv.length != 2)
				continue; // Be silent?
			map.put(kv[0], kv[1]);
		}
		return map;
	}

	// public static TDoubleMap<String> parseTDoubleMap(String line, String
	// keyValueDelim) {
	// return parseTDoubleMap(line, keyValueDelim, " ");
	// }
	// public static TDoubleMap<String> parseTDoubleMap(String line, String
	// keyValueDelim, String entryDelim) {
	// TDoubleMap<String> map = new TDoubleMap<String>();
	// String[] tokens = line.split(entryDelim);
	// for(String token : tokens) {
	// String[] kv = token.split(keyValueDelim);
	// if(kv.length != 2) continue; // Be silent?
	// map.put(kv[0], Double.parseDouble(kv[1]));
	// }
	// return map;
	// }

	public static <T> String join(double[] list) {
		return join(list, " ");
	}

	public static <T> String join(double[] list, String delim) {
		if (list == null)
			return "";
		List<Double> objs = new ArrayList<Double>();
		for (double x : list)
			objs.add(x);
		return join(objs, delim);
	}

	public static String join(int[] list) {
		return join(list, " ");
	}

	public static String join(int[] list, String delim) {
		if (list == null)
			return "";
		List<Integer> objs = new ArrayList<Integer>();
		for (int x : list)
			objs.add(x);
		return join(objs, delim);
	}

	public static String join(boolean[] list) {
		return join(list, " ");
	}

	public static String join(boolean[] list, String delim) {
		if (list == null)
			return "";
		List<Boolean> objs = new ArrayList<Boolean>();
		for (boolean x : list)
			objs.add(x);
		return join(objs, delim);
	}

	public static <T> String join(T[] objs) {
		if (objs == null)
			return "";
		return join(Arrays.asList(objs), " ");
	}

	public static <T> String join(T[] objs, int start, int end) {
		if (objs == null)
			return "";
		return join(Arrays.asList(objs), " ", start, end);
	}

	public static <T> String join(List<T> objs) {
		return join(objs, " ");
	}

	public static <T> String join(T[] objs, String delim) {
		if (objs == null)
			return "";
		return join(Arrays.asList(objs), delim);
	}

	public static <T> String join(List<T> objs, String delim) {
		if (objs == null)
			return "";
		return join(objs, delim, 0, objs.size());
	}

	public static <T> String join(List<T> objs, String delim, int start, int end) {
		if (objs == null)
			return "";
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (int i = start; i < end; i++) {
			if (!first)
				sb.append(delim);
			sb.append(objs.get(i));
			first = false;
		}
		return sb.toString();
	}

	public static <T> String join(Collection<T> objs, String delim) {
		if (objs == null)
			return "";
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (T x : objs) {
			if (!first)
				sb.append(delim);
			sb.append(x);
			first = false;
		}
		return sb.toString();
	}

	public static String join(int[] x, boolean withIndices,
			int magnitudeThreshold) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < x.length; i++) {
			if (Math.abs(x[i]) < magnitudeThreshold)
				continue;
			if (i > 0)
				sb.append(' ');
			if (withIndices)
				sb.append(i + ":");
			sb.append(x[i]);
		}
		return sb.toString();
	}

	public static String joinWithIndices(int[] x) {
		return join(x, true, 0);
	}

	public static String join(double[] x, boolean withIndices,
			double magnitudeThreshold) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < x.length; i++) {
			if (Math.abs(x[i]) < magnitudeThreshold)
				continue;
			if (i > 0)
				sb.append(' ');
			if (withIndices)
				sb.append(i + ":");
			sb.append(x[i]);
		}
		return sb.toString();
	}

	public static String joinWithIndices(double[] x) {
		return join(x, true, 0);
	}

	// Format a matrix with spaces and newlines
	public static <T> String join(T[][] data, String[] rowNames,
			String[] colNames) {
		int r0 = (colNames == null ? 0 : 1);
		int c0 = (rowNames == null ? 0 : 1);
		int R = data.length;
		int C = data[0].length;
		String[][] mat = new String[R + r0][C + r0];
		for (int r = 0; r < R; r++)
			for (int c = 0; c < C; c++)
				mat[r + r0][c + c0] = "" + data[r][c];
		if (rowNames != null)
			for (int r = 0; r < R; r++)
				mat[r + r0][0] = rowNames[r];
		if (colNames != null)
			for (int c = 0; c < C; c++)
				mat[0][c + c0] = colNames[c];
		int[] widths = new int[C + c0];
		for (int c = 0; c < C + c0; c++) {
			for (int r = 0; r < R + r0; r++) {
				if (mat[r][c] == null)
					mat[r][c] = "";
				widths[c] = Math.max(widths[c], mat[r][c].length());
			}
		}
		StringBuilder buf = new StringBuilder();
		for (int r = 0; r < R + r0; r++) {
			for (int c = 0; c < C + c0; c++) {
				String fmt = "%-" + (widths[c] + 1) + "s";
				buf.append(String.format(fmt, mat[r][c]));
			}
			buf.append("\n");
		}
		return buf.toString();
	}

	public static String toString(Object o) {
		return o == null ? null : o.toString();
	}

	public static boolean isEmpty(String s) {
		return s == null || s.equals("");
	}

	public static String repeat(String s, int n) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < n; i++)
			sb.append(s);
		return sb.toString();
	}

	// Regular expression
	public static Matcher match(String pattern, String s) {
		return Pattern.compile(pattern).matcher(s);
	}

	public static String[] format(String fmt, Object... is) {
		int n = Array.getLength(is[0]);
		String[] out = new String[n];
		for (int i = 0; i < n; i++) {
			Object[] args = new Object[is.length];
			for (int j = 0; j < is.length; j++)
				args[j] = Array.get(is[j], i);
			out[i] = String.format(fmt, args);
		}
		return out;
	}
}
