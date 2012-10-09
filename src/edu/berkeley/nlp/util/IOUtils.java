package edu.berkeley.nlp.util;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class IOUtils {
	// Listing files in a directory
	public static List<File> getFilesUnder(String path, FileFilter fileFilter) {
		File root = new File(path);
		List<File> files = new ArrayList<File>();
		addFilesUnder(root, files, fileFilter);
		return files;
	}

	private static void addFilesUnder(File root, List<File> files,
			FileFilter fileFilter) {
		if (!fileFilter.accept(root))
			return;
		if (root.isFile()) {
			files.add(root);
			return;
		}
		if (root.isDirectory()) {
			File[] children = root.listFiles();
			for (int i = 0; i < children.length; i++) {
				File child = children[i];
				addFilesUnder(child, files, fileFilter);
			}
		}
	}

	// }

	public static File createTempFileHard(String prefix, String suffix) {
		try {
			return File.createTempFile(prefix, suffix);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static boolean createNewFileEasy(String path) {
		if (path == null)
			return false;
		try {
			new File(path).createNewFile();
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	public static boolean createNewFileIfNotExistsEasy(String path) {
		if (path == null)
			return false;
		if (new File(path).isFile())
			return true;
		return createNewFileEasy(path);
	}

	public static boolean createNewDirIfNotExistsEasy(String path) {
		if (path == null)
			return false;
		if (new File(path).isDirectory())
			return true;
		return new File(path).mkdir();
	}

	// Printing stuff straight to a file {
	@Deprecated
	public static <T> void filePrintList(String file, Collection<T> c)
			throws IOException {
		PrintWriter out = openOut(file);
		for (T x : c)
			out.println(x);
		out.close();
	}

	@Deprecated
	public static void filePrintf(String file, String format, Object... args)
			throws IOException {
		PrintWriter out = openOut(file);
		out.println(String.format(format, args));
		out.close();
	}

	@Deprecated
	public static void filePrintln(String file, Object o) throws IOException {
		PrintWriter out = openOut(file);
		out.println(o);
		out.close();
	}

	@Deprecated
	public static void filePrintlnEasy(String file, Object o) {
		PrintWriter out = openOutEasy(file);
		if (out == null)
			return;
		out.println(o);
		out.close();
	}

	// }

	// Opening files {
	// openBinIn
	public static ObjectInputStream openBinIn(String path) throws IOException {
		return openBinIn(new File(path));
	}

	public static ObjectInputStream openBinIn(File path) throws IOException {
		return new ObjectInputStream(new FileInputStream(path));
	}

	public static ObjectInputStream openBinInEasy(String path) {
		if (StrUtils.isEmpty(path))
			return null;
		return openBinInEasy(new File(path));
	}

	public static ObjectInputStream openBinInEasy(File path) {
		if (path == null)
			return null;
		try {
			return openBinIn(path);
		} catch (Exception e) {
			return null;
		}
	}

	public static ObjectInputStream openBinInHard(String path) {
		return openBinInHard(new File(path));
	}

	public static ObjectInputStream openBinInHard(File path) {
		try {
			return openBinIn(path);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// openIn

	public static BufferedReader openInGZip(String path) throws IOException {
		GZIPInputStream is = new GZIPInputStream(new FileInputStream(path));
		return new BufferedReader(new InputStreamReader(is));
	}

	public static BufferedReader openIn(String path) throws IOException {
		return openIn(new File(path));
	}

	public static BufferedReader openIn(File path) throws IOException {
		InputStream is = new FileInputStream(path);
		if (path.getName().endsWith(".gz"))
			is = new GZIPInputStream(is);
		return new BufferedReader(CharEncUtils.getReader(is));
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

	// openBinOut
	public static ObjectOutputStream openBinOut(String path) throws IOException {
		return openBinOut(new File(path));
	}

	public static ObjectOutputStream openBinOut(File path) throws IOException {
		return new ObjectOutputStream(new FileOutputStream(path));
	}

	public static ObjectOutputStream openBinOutEasy(String path) {
		if (StrUtils.isEmpty(path))
			return null;
		return openBinOutEasy(new File(path));
	}

	public static ObjectOutputStream openBinOutEasy(File path) {
		if (path == null)
			return null;
		try {
			return openBinOut(path);
		} catch (Exception e) {
			return null;
		}
	}

	public static ObjectOutputStream openBinOutHard(String path) {
		return openBinOutHard(new File(path));
	}

	public static ObjectOutputStream openBinOutHard(File path) {
		try {
			return openBinOut(path);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static PrintWriter openOutAppend(String path) throws IOException {
		return openOutAppend(new File(path));
	}

	public static PrintWriter openOutAppend(File path) throws IOException {
		return new PrintWriter(CharEncUtils.getWriter(new FileOutputStream(
				path, true)));
	}

	public static PrintWriter openOutAppendEasy(String path) {
		if (StrUtils.isEmpty(path))
			return null;
		return openOutAppendEasy(new File(path));
	}

	public static PrintWriter openOutAppendEasy(File path) {
		if (path == null)
			return null;
		try {
			return openOutAppend(path);
		} catch (Exception e) {
			return null;
		}
	}

	public static PrintWriter openOutAppendHard(String path) {
		return openOutAppendHard(new File(path));
	}

	public static PrintWriter openOutAppendHard(File path) {
		try {
			return openOutAppend(path);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// openOut
	public static PrintWriter openOut(String path) throws IOException {
		return openOut(new File(path));
	}

	public static PrintWriter openOut(File path) throws IOException {
		OutputStream os = new FileOutputStream(path);
		if (path.getName().endsWith(".gz"))
			os = new GZIPOutputStream(os);
		return new PrintWriter(CharEncUtils.getWriter(os));
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

	// }

	// Java binary serialization {
	// openObjIn
	public static ObjectInputStream openObjIn(String path) throws IOException {
		return openObjIn(new File(path));
	}

	public static ObjectInputStream openObjIn(File path) throws IOException {
		InputStream fis = new BufferedInputStream(new FileInputStream(path));
		return path.getName().endsWith(".gz") ? new ObjectInputStream(
				new GZIPInputStream(fis)) : new ObjectInputStream(fis);
	}

	// openObjOut
	public static ObjectOutputStream openObjOut(String path) throws IOException {
		return openObjOut(new File(path));
	}

	public static ObjectOutputStream openObjOut(File path) throws IOException {
		OutputStream fos = new BufferedOutputStream(new FileOutputStream(path));
		return path.getName().endsWith(".gz") ? new ObjectOutputStream(
				new GZIPOutputStream(fos)) : new ObjectOutputStream(fos);
	}

	// readObjFile
	public static Object readObjFile(String path) throws IOException,
			ClassNotFoundException {
		return readObjFile(new File(path));
	}

	public static Object readObjFile(File path) throws IOException,
			ClassNotFoundException {
		ObjectInputStream in = openObjIn(path);
		Object obj = in.readObject();
		in.close();
		return obj;
	}

	public static Object readObjFileEasy(String path) {
		if (StrUtils.isEmpty(path))
			return null;
		return readObjFileEasy(new File(path));
	}

	public static Object readObjFileEasy(File path) {
		if (path == null)
			return null;
		try {
			return readObjFile(path);
		} catch (Exception e) {
			return null;
		}
	}

	public static Object readObjFileHard(String path) {
		return readObjFileHard(new File(path));
	}

	public static Object readObjFileHard(File path) {
		try {
			return readObjFile(path);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// writeObjFile
	public static void writeObjFile(String path, Object obj) throws IOException {
		writeObjFile(path, obj);
	}

	public static void writeObjFile(File path, Object obj) throws IOException {
		ObjectOutputStream out = openObjOut(path);
		out.writeObject(obj);
		out.close();
	}

	public static boolean writeObjFileEasy(String path, Object obj) {
		if (StrUtils.isEmpty(path))
			return false;
		return writeObjFileEasy(new File(path), obj);
	}

	public static boolean writeObjFileEasy(File path, Object obj) {
		if (path == null)
			return false;
		try {
			writeObjFile(path, obj);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public static void writeObjFileHard(String path, Object obj) {
		writeObjFileHard(new File(path), obj);
	}

	public static void writeObjFileHard(File path, Object obj) {
		try {
			writeObjFile(path, obj);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// }

	public static boolean closeEasy(BufferedReader in) {
		try {
			in.close();
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	// Copying files {
	// Return number of bytes copied
	public static int copy(InputStream in, OutputStream out) throws IOException {
		byte[] buf = new byte[16384];
		int total = 0, n;
		while ((n = in.read(buf)) != -1) {
			total += n;
			out.write(buf, 0, n);
		}
		out.flush();
		return total;
	}

	// Return number of characters copied
	public static int copy(Reader in, Writer out) throws IOException {
		char[] buf = new char[16384];
		int total = 0, n;
		while ((n = in.read(buf)) != -1) {
			total += n;
			out.write(buf, 0, n);
		}
		out.flush();
		return total;
	}

	// }

	// File path operations {
	public static boolean createSymLink(String src, String dest) {
		try {
			String cmd = String.format("ln -s %s %s", src, dest);
			Runtime.getRuntime().exec(cmd);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	public static boolean purgePath(File oldPath) {
		// Ok, this isn't really purging because I'm paranoid.
		// Move <directory> to <directory>-purged[-<i>]
		for (int i = 0; i < 1000; i++) {
			File newPath = new File(oldPath.getParent(), oldPath.getName()
					+ ".purged" + (i == 0 ? "" : "-" + i));
			if (newPath.exists())
				continue;
			return oldPath.renameTo(newPath);
		}
		return false;
	}

	public static String stripFileExt(String file) {
		int i = file.lastIndexOf('.');
		if (i == -1)
			return file;
		return file.substring(0, i);
	}

	public static String getFileExt(String file) {
		int i = file.lastIndexOf('.');
		if (i == -1)
			return "";
		return file.substring(i + 1);
	}

	// }

	private static String removeComment(String line) {
		int i = -1;
		while ((i = line.indexOf("#", i + 1)) != -1) { // Look for comment
														// character
			if (i == 0 || line.charAt(i - 1) != '\\') // Make sure not escaped
				break;
		}
		return i == -1 ? line : line.substring(0, i);
	}

	private static String removeTrailingSpace(String line) {
		for (int i = line.length() - 1; i >= 0; i--)
			if (!Character.isWhitespace(line.charAt(i)))
				return line.substring(0, i + 1);
		return "";
	}

	// Program mode is where comments (#) and blank lines are skipped
	// and trailing \'s are combined into one line.
	// Anything after the first non-escaped # is a comment.
	public static interface LineMunger {
		public void beforeLine(boolean isContinuation);

		public void afterFullLine(String line); // Called when a full line is
												// read.
	}

	public static void doProgramLines(BufferedReader in, LineMunger lineMunger)
			throws IOException {
		String line;
		String carry = "";
		while (true) {
			if (lineMunger != null)
				lineMunger.beforeLine(carry.length() > 0);
			line = in.readLine();
			if (line == null)
				break;

			line = removeComment(line);
			line = removeTrailingSpace(line);
			if (line.endsWith("\\"))
				carry += line.substring(0, line.length() - 1);
			else {
				lineMunger.afterFullLine(carry + line);
				carry = "";
			}
			if (line.equals("") || line.startsWith("#"))
				continue;
		}
		if (carry.length() > 0)
			lineMunger.afterFullLine(carry);
	}

	public static void doProgramLines(String path, LineMunger lineMunger)
			throws IOException {
		BufferedReader in = openIn(path);
		doProgramLines(in, lineMunger);
		in.close();
	}

	public static class LineListMaker implements LineMunger {
		private List<String> lines = new ArrayList<String>();

		public void beforeLine(boolean isContinuation) {
		}

		public void afterFullLine(String line) {
			lines.add(line);
		}

		public List<String> getLines() {
			return lines;
		}
	}

	public static List<String> readProgramLines(String path) throws IOException {
		LineListMaker maker = new LineListMaker();
		doProgramLines(path, maker);
		return maker.getLines();
	}

	public static List<String> readProgramLinesHard(String path) {
		try {
			return readProgramLines(path);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	// Ordinary read lines function
	public static List<String> readLines(String path) throws IOException {

		BufferedReader in = IOUtils.openIn(path);
		List<String> list = readLines(in);
		in.close();
		return list;
	}

	public static void writeLines(String path, List<String> lines)
			throws IOException {
		PrintWriter out = IOUtils.openOut(path);
		for (String line : lines) {
			out.println(line);
		}
		out.close();
	}

	public static void writeLinesHard(String path, List<String> lines) {
		try {
			PrintWriter out = IOUtils.openOut(path);
			for (String line : lines) {
				out.println(line);
			}
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static Iterator<String> lineIterator(String path) throws IOException {
		final BufferedReader reader = IOUtils.openIn(path);
		return new Iterator<String>() {

			private String line;

			public boolean hasNext() {
				// TODO Auto-generated method stub
				try {
					return nextLine();
				} catch (Exception e) {
					e.printStackTrace();
				}
				return false;
			}

			private boolean nextLine() throws IOException {
				if (line != null) {
					return true;
				}
				line = reader.readLine();
				return line != null;
			}

			public String next() {
				// TODO Auto-generated method stub
				try {
					nextLine();
					String retLine = line;
					line = null;
					return retLine;
				} catch (IOException e) {
					throw new RuntimeException();
				}
			}

			public void remove() {
				// TODO Auto-generated method stub
				throw new RuntimeException("remove() not supported");
			}

		};
	}

	public static List<String> readLines(BufferedReader in) throws IOException {
		String line;
		List<String> lines = new ArrayList<String>();
		while ((line = in.readLine()) != null)
			lines.add(line);
		return lines;
	}

	public static List<String> readLinesEasy(String path) {
		try {
			return readLines(path);
		} catch (IOException e) {
			return Collections.EMPTY_LIST;
		}
	}

	public static List<String> readLinesHard(String path) {
		try {
			return readLines(path);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	// Return the first line, null if it doesn't exist
	public static String readLine(String path) throws IOException {
		BufferedReader in = IOUtils.openIn(path);
		String line = in.readLine();
		in.close();
		return line;
	}

	public static String readLineEasy(String path) {
		try {
			return readLine(path);
		} catch (IOException e) {
			return null;
		}
	}

	public static void printLines(String path, List lines) throws IOException {
		PrintWriter out = IOUtils.openOut(path);
		printLines(out, lines);
		out.close();
	}

	public static void printLinesHard(String path, List lines) {
		try {
			printLines(path, lines);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static boolean printLinesEasy(String path, List lines) {
		try {
			printLines(path, lines);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	public static void printLines(PrintWriter out, List lines) {
		for (Object line : lines)
			out.println(StrUtils.toString(line));
	}

	public static int readBigEndianInt(InputStream in) throws IOException {
		int a = in.read();
		if (a == -1)
			throw new IOException("EOF");
		int b = in.read();
		if (b == -1)
			throw new IOException("EOF");
		int c = in.read();
		if (c == -1)
			throw new IOException("EOF");
		int d = in.read();
		if (d == -1)
			throw new IOException("EOF");
		return (a << 24) + (b << 16) + (c << 8) + d;
	}
}
