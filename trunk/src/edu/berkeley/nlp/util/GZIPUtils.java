package edu.berkeley.nlp.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Just like IOUtils, but reads and writes .gz files. File name extensions are
 * required!
 * 
 * THIS IS NOW OBSOLETE, THANK GOODNESS!
 * 
 * @author denero
 */
public class GZIPUtils {
	private GZIPUtils() {
	} // No instantiation

	// openIn
	public static BufferedReader openIn(String path) throws IOException {
		return openIn(new File(path));
	}

	public static BufferedReader openIn(File path) throws IOException {
		return new BufferedReader(CharEncUtils.getReader(getInputStream(path)));
	}

	public static InputStream getInputStream(String path) throws IOException {
		return getInputStream(new File(path));
	}

	public static InputStream getInputStream(File path) throws IOException {
		InputStream instream;
		if (path.getName().endsWith(".gz")) {
			instream = new GZIPInputStream(new FileInputStream(path));
		} else {
			instream = new FileInputStream(path);
		}
		return instream;
	}

	public static BufferedReader openInEasy(String path) {
		if (StrUtils.isEmpty(path))
			return null;
		return openInEasy(new File(path));
	}

	public static BufferedReader openInEasy(File path) {
		if (path == null)
			return null;
		try {
			return openIn(path);
		} catch (Exception e) {
			return null;
		}
	}

	public static BufferedReader openInHard(String path) {
		return openInHard(new File(path));
	}

	public static BufferedReader openInHard(File path) {
		try {
			return openIn(path);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// openOut
	public static PrintWriter openOut(String path) throws IOException {
		return openOut(new File(path));
	}

	public static PrintWriter openOut(File path) throws IOException {
		OutputStream outstream;
		if (path.getName().endsWith(".gz")) {
			outstream = new GZIPOutputStream(new FileOutputStream(path));
		} else {
			outstream = new FileOutputStream(path);
		}
		return new PrintWriter(CharEncUtils.getWriter(outstream));
	}

	public static PrintWriter openOutEasy(String path) {
		if (StrUtils.isEmpty(path))
			return null;
		return openOutEasy(new File(path));
	}

	public static PrintWriter openOutEasy(File path) {
		if (path == null)
			return null;
		try {
			return openOut(path);
		} catch (Exception e) {
			return null;
		}
	}

	public static PrintWriter openOutHard(String path) {
		return openOutHard(new File(path));
	}

	public static PrintWriter openOutHard(File path) {
		try {
			return openOut(path);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
