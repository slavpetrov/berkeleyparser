package edu.berkeley.nlp.util;

import java.io.*;
import java.util.*;

public class Utils {
	// Create a random from another mother random.
	// This is useful when a program needs to use randomness
	// for two tasks, each of which requires an unknown
	// number random draws. For partial reproducibility.
	public static Random randRandom(Random random) {
		return new Random(random.nextInt(Integer.MAX_VALUE));
	}

	public static boolean equals(Object o1, Object o2) {
		if (o1 == null)
			return o2 == null;
		return o1.equals(o2);
	}

	// Suppose we get values from various sources (possibly some null).
	// We want to make sure these values are all the same and retrieve that
	// value.
	// Usage:
	// int value = -1;
	// for(double newValue : source)
	// value = Utils.setEqual(value, newValue);
	public static int setEqual(int oldValue, int newValue) {
		return setEqual(oldValue, newValue, -1);
	}

	public static int setEqual(int oldValue, int newValue, int nullValue) {
		if (oldValue == nullValue)
			return newValue;
		if (newValue == nullValue)
			return oldValue;
		if (oldValue != newValue)
			throw Exceptions.bad("Mis-match: %d %d", oldValue, newValue);
		return newValue;
	}

	public static void sleep(int ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
		}
	}

	public static Properties loadProperties(String path) {
		Properties properties = new Properties();
		try {
			properties.load(new FileInputStream(path));
			return properties;
		} catch (IOException e) {
			throw new RuntimeException("Cannot open " + path);
		}
	}

	public static boolean createSymLink(String src, String dest) {
		try {
			// -n: if destination is a symbolic link (to a directory), can't
			// overwrite it
			String cmd = String.format("ln -sn '%s' '%s'", src, dest);
			try {
				return Runtime.getRuntime().exec(cmd).waitFor() == 0;
			} catch (InterruptedException e) {
				return false;
			}
		} catch (IOException e) {
			return false;
		}
	}

	// Get stack traces
	// Include the top max stack traces
	// Stop when reach stopClassPrefix
	public static String getStackTrace(Throwable t, int max,
			String stopClassPrefix) {
		StringBuilder sb = new StringBuilder();
		for (StackTraceElement e : t.getStackTrace()) {
			if (max-- <= 0)
				break;
			if (stopClassPrefix != null
					&& e.getClassName().startsWith(stopClassPrefix))
				break;
			sb.append(e);
			sb.append('\n');
		}
		return sb.toString();
	}

	public static String getStackTrace(Throwable t) {
		return getStackTrace(t, Integer.MAX_VALUE, null);
	}

	public static String getStackTrace(Throwable t, int max) {
		return getStackTrace(t, max, null);
	}

	public static String getStackTrace(Throwable t, String classPrefix) {
		return getStackTrace(t, Integer.MAX_VALUE, classPrefix);
	}

	public static int parseIntEasy(String s, int defaultValue) {
		if (s == null)
			return defaultValue;
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	public static long parseLongEasy(String s, long defaultValue) {
		if (s == null)
			return defaultValue;
		try {
			return Long.parseLong(s);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	public static double parseDoubleEasy(String s) {
		return parseDoubleEasy(s, Double.NaN);
	}

	public static double parseDoubleEasy(String s, double defaultValue) {
		if (s == null)
			return defaultValue;
		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	public static boolean parseBooleanEasy(String s, boolean defaultValue) {
		if (s == null)
			return defaultValue;
		try {
			return Boolean.parseBoolean(s);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	public static int parseIntHard(String s) {
		try {
			return Integer.parseInt(s);
		} catch (NumberFormatException e) {
			throw new RuntimeException("Invalid format: " + s);
		}
	}

	public static double parseDoubleHard(String s) {
		try {
			return Double.parseDouble(s);
		} catch (NumberFormatException e) {
			throw new RuntimeException("Invalid format: " + s);
		}
	}

	public static boolean parseBooleanHard(String s) {
		try {
			return Boolean.parseBoolean(s);
		} catch (NumberFormatException e) {
			throw new RuntimeException("Invalid format: " + s);
		}
	}

	public static Object parseEnum(Class c, String s) {
		s = s.toLowerCase();
		for (Object o : c.getEnumConstants())
			if (o.toString().toLowerCase().equals(s))
				return o;
		return null;
	}

	// Convert Integer/Double object to a double
	public static double toDouble(Object o) {
		if (o instanceof Double)
			return (Double) o;
		if (o instanceof Integer)
			return (double) ((Integer) o);
		throw Exceptions.bad("Can't convert to double: " + o);
	}

	// Return number of seconds
	// 1d2h4m2s
	public static int parseTimeLength(String s) {
		if (StrUtils.isEmpty(s))
			return 0;
		int sum = 0;
		int n = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (Character.isDigit(c))
				n = n * 10 + Integer.parseInt(c + "");
			else if (c == 'd') {
				sum += n * 60 * 60 * 24;
				n = 0;
			} else if (c == 'h') {
				sum += n * 60 * 60;
				n = 0;
			} else if (c == 'm') {
				sum += n * 60;
				n = 0;
			} else if (c == 's') {
				sum += n;
				n = 0;
			}
		}
		return sum;
	}

	// Run shell commands
	public static Process openSystem(String cmd) throws IOException {
		return Runtime.getRuntime().exec(new String[] { "sh", "-c", cmd });
	}

	public static Process openSystemLogin(String cmd) throws IOException {
		return Runtime.getRuntime().exec(
				new String[] { "bash", "--login", "-c", cmd });
	}

	public static int closeSystem(String cmd, Process p)
			throws InterruptedException {
		return p.waitFor();
	}

	public static int closeSystemEasy(String cmd, Process p) {
		try {
			return closeSystem(cmd, p);
		} catch (InterruptedException e) {
			return -1;
		}
	}

	public static void closeSystemHard(String cmd, Process p) {
		try {
			int status = closeSystem(cmd, p);
			if (status != 0)
				throw new RuntimeException("Failed: '" + cmd
						+ "' returned status " + status);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	// Run the command
	// Assume command takes no stdin
	// Dump command's output to stdout, stderr
	public static boolean system(String cmd, Writer finalOut, Writer finalErr)
			throws IOException, InterruptedException {
		Process p = openSystem(cmd);
		p.getOutputStream().close();
		BufferedReader in = CharEncUtils.getReader(p.getInputStream());
		if (finalOut != null)
			IOUtils.copy(in, finalOut);
		in.close();
		BufferedReader err = CharEncUtils.getReader(p.getErrorStream());
		if (finalErr != null)
			IOUtils.copy(err, finalErr);
		err.close();
		return closeSystem(cmd, p) == 0;
	}

	public static boolean system(String cmd, OutputStream finalOut,
			OutputStream finalErr) throws IOException, InterruptedException {
		Process p = openSystem(cmd);
		p.getOutputStream().close();
		InputStream in = p.getInputStream();
		if (finalOut != null)
			IOUtils.copy(in, finalOut);
		in.close();
		InputStream err = p.getErrorStream();
		if (finalErr != null)
			IOUtils.copy(err, finalErr);
		err.close();
		return closeSystem(cmd, p) == 0;
	}

	public static boolean system(String cmd) throws IOException,
			InterruptedException {
		return system(cmd, System.out, System.err);
	}

	public static boolean systemLogin(String cmd, OutputStream finalOut,
			OutputStream finalErr) throws IOException, InterruptedException {
		Process p = openSystemLogin(cmd);
		p.getOutputStream().close();
		InputStream in = p.getInputStream();
		if (finalOut != null)
			IOUtils.copy(in, finalOut);
		in.close();
		InputStream err = p.getErrorStream();
		if (finalErr != null)
			IOUtils.copy(err, finalErr);
		err.close();
		return closeSystem(cmd, p) == 0;
	}

	public static boolean systemLogin(String cmd) throws IOException,
			InterruptedException {
		return systemLogin(cmd, System.out, System.err);
	}

	public static void systemHard(String cmd, Writer finalOut, Writer finalErr) {
		try {
			if (!system(cmd, finalOut, finalErr))
				throw new RuntimeException(cmd + " had non-zero exit status");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static void systemHard(String cmd, OutputStream finalOut,
			OutputStream finalErr) {
		try {
			if (!system(cmd, finalOut, finalErr))
				throw new RuntimeException(cmd + " had non-zero exit status");
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static void systemHard(String cmd) {
		systemHard(cmd, System.out, System.err);
	}

	public static boolean systemEasy(String cmd, Writer finalOut,
			Writer finalErr) {
		try {
			return system(cmd, finalOut, finalErr);
		} catch (Exception e) {
			return false;
		}
	}

	public static boolean systemEasy(String cmd, OutputStream finalOut,
			OutputStream finalErr) {
		try {
			return system(cmd, finalOut, finalErr);
		} catch (Exception e) {
			return false;
		}
	}

	public static boolean systemEasy(String cmd) {
		return systemEasy(cmd, System.out, System.err);
	}

	// Return stdout as string
	public static String systemGetStringOutput(String cmd) throws IOException,
			InterruptedException {
		StringWriter sw = new StringWriter();
		if (!system(cmd, sw, LogInfo.stderr))
			return null;
		return sw.toString();
	}

	public static String systemGetStringOutputEasy(String cmd) {
		try {
			return systemGetStringOutput(cmd);
		} catch (Exception e) {
			return null;
		}
	}

	public static String makeRunCommandInDir(String cmd, String dir) {
		return String.format("cd %s && (%s)", dir, cmd);
	}
}
