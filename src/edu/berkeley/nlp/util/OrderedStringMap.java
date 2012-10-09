package edu.berkeley.nlp.util;

import java.io.*;
import java.util.*;

/**
 * An OrderedMap for mapping strings to strings.
 */
public class OrderedStringMap extends OrderedMap<String, String> {
	public OrderedStringMap() {
	}

	public OrderedStringMap(OrderedStringMap map) {
		for (String key : map.keys())
			put(key, map.get(key));
	}

	public static OrderedStringMap fromFile(String path) throws IOException {
		return fromFile(new File(path));
	}

	public static OrderedStringMap fromFile(File path) throws IOException {
		OrderedStringMap map = new OrderedStringMap();
		map.read(path);
		return map;
	}

	public void put(String key, Object val) {
		super.put(key, StrUtils.toString(val));
	}

	public void read(String path) throws IOException {
		read(new File(path));
	}

	public void read(File path) throws IOException {
		BufferedReader in = IOUtils.openIn(path);
		read(in);
		in.close();
	}

	public void read(BufferedReader r) throws IOException {
		clear();
		String line;
		while ((line = r.readLine()) != null) {
			StringTokenizer st = new StringTokenizer(line, "\t");
			if (!st.hasMoreTokens())
				continue; // Skip blank lines
			String key = st.nextToken();
			String val = st.hasMoreTokens() ? st.nextToken() : null;
			put(key, val);
		}
	}

	public boolean readEasy(String path) {
		if (StrUtils.isEmpty(path))
			return false;
		return readEasy(new File(path));
	}

	public boolean readEasy(File path) {
		if (path == null)
			return false;
		try {
			read(path);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	public void readHard(String path) {
		readHard(new File(path));
	}

	public void readHard(File path) {
		try {
			read(path);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
}
