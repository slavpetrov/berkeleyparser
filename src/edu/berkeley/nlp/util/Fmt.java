package edu.berkeley.nlp.util;

import java.text.SimpleDateFormat;

/**
 * Formatting class. I'm really lazy. D() is a family of default functions for
 * formatting various types of objects.
 */
public class Fmt {
	public static String D(double x) {
		if (Math.abs(x - (int) x) < 1e-40) // An integer (probably)
			return "" + (int) x;
		if (Math.abs(x) < 1e-3) // Scientific notation (close to 0)
			return String.format("%.2e", x);
		return String.format("%.3f", x);
	}

	public static String D(boolean[] x) {
		return StrUtils.join(x);
	}

	public static String D(int[] x) {
		return StrUtils.join(x);
	}

	public static String D(double[] x) {
		return D(x, " ");
	}

	public static String D(double[] xs, String delim) {
		StringBuilder sb = new StringBuilder();
		for (double x : xs) {
			if (sb.length() > 0)
				sb.append(delim);
			sb.append(Fmt.D(x));
		}
		return sb.toString();
	}

	// Print out only first N
	public static String D(double[] x, int firstN) {
		if (firstN >= x.length)
			return D(x);
		return D(ListUtils.subArray(x, 0, firstN)) + " ...("
				+ (x.length - firstN) + " more)";
	}

	public static String D(double[][] x) {
		return D(x, " ");
	}

	public static String D(double[][] xs, String delim) {
		StringBuilder sb = new StringBuilder();
		for (double[] x : xs) {
			if (sb.length() > 0)
				sb.append(delim);
			sb.append(Fmt.D(x));
		}
		return sb.toString();
	}

	public static String D(TDoubleMap map) {
		return D(map, 20);
	}

	public static String D(TDoubleMap map, int numTop) {
		return MapUtils.topNToString(map, numTop);
	}

	public static String D(Object o) {
		if (o instanceof double[])
			return Fmt.D((double[]) o);
		if (o instanceof double[][])
			return Fmt.D((double[][]) o);
		if (o instanceof double[][][])
			return Fmt.D(o);
		throw Exceptions.unknownCase;
	}

	public static String bytesToString(long b) {
		double gb = (double) b / (1024 * 1024 * 1024);
		if (gb >= 1)
			return gb >= 10 ? (int) gb + "G" : NumUtils.round(gb, 1) + "G";
		double mb = (double) b / (1024 * 1024);
		if (mb >= 1)
			return mb >= 10 ? (int) mb + "M" : NumUtils.round(mb, 1) + "M";
		double kb = (double) b / (1024);
		if (kb >= 1)
			return kb >= 10 ? (int) kb + "K" : NumUtils.round(kb, 1) + "K";
		return b + "";
	}

	public static String formatEasyDateTime(long t) {
		return new SimpleDateFormat("MM/dd HH:mm").format(t);
	}
}
