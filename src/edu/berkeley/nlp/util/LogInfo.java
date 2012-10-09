package edu.berkeley.nlp.util;

import java.io.*;
import java.lang.reflect.Method;
import java.util.*;

/**
 * The logging output has a tree structure, where each node is a line of output,
 * and the depth of a node is its indent level. A run is the sequence of
 * children of some node. A subset of the lines in the run will get printed.
 * 
 * WARNING: not thread safe.
 */
public class LogInfo {
	public static void track(String format, Object... args) {
		track(String.format(format, args), false);
	}

	public static void track(Object o) {
		track(o, false);
	}

	public synchronized static void track_methods(Object o, String[] methodNames)
			throws Exception {
		for (String methodName : methodNames) {
			track_method(o, methodName);
		}
	}

	/**
	 * Invokes a method and wraps it's invocation in a track. The method must be
	 * public and not take any arguments.
	 * 
	 * @param o
	 *            Object to call method on
	 * @param methodName
	 *            name of method to invoke
	 * @throws Exception
	 */
	public synchronized static void track_method(Object o, String methodName,
			Object... args) throws Exception {
		Class<? extends Object> c = o.getClass();
		Method[] methods = c.getDeclaredMethods();
		Method targetMethod = null;
		for (Method m : methods) {
			if (m.getName().equals(methodName)) {
				targetMethod = m;
				break;
			}
		}
		if (targetMethod == null) {
			String msg = String.format("Couldn't find method %d in class %s",
					methodName, c.getName());
			throw new IllegalArgumentException(msg);
		}
		LogInfo.track(methodName);
		targetMethod.invoke(o, args);
		LogInfo.end_track();
	}

	public synchronized static void track_method(Object o, String methodName)
			throws Exception {
		track_method(o, methodName, new Object[0]);
	}

	public synchronized static void track(Object o, boolean printAllLines) {
		track(o, printAllLines, false);
	}

	public synchronized static void track(Object o, boolean printAllChildLines,
			boolean printIfParentPrinted) {
		if (indWithin()) {
			if (printIfParentPrinted && parentPrinted())
				thisRun().forcePrint();
			if (thisRun().shouldPrint()) {
				print(o);
				buf.append(" {\n"); // Open the block.

				childRun().init();
				childRun().printAllLines = printAllChildLines;
			} else {
				stoppedIndLevel = indLevel;
				maxIndLevel = -maxIndLevel; // Prevent children from outputting.
			}
		}

		indLevel++;
	}

	// Convenient way to end and return a value
	public static <T> T end_track(T x) {
		end_track();
		return x;
	}

	public synchronized static void end_track() {
		indLevel--;

		if (stoppedIndLevel == indLevel) {
			stoppedIndLevel = -1;
			maxIndLevel = -maxIndLevel; // Restore indent level.
		}

		if (indWithin()) {
			if (thisRun().newLine()) { // Note that we pay for the line only at
										// the end
				// Finish up child level.
				indLevel++;
				int n = thisRun().numOmitted();
				if (n > 0)
					print("... " + n + " lines omitted ...\n");
				indLevel--;
				childRun().finish();

				if (buf.length() > 0) // Nothing was printed, because buf hasn't
										// been emptied.
					buf.delete(0, buf.length()); // Just pretend we didn't open
													// the block.
				else
					// Something indented was printed.
					print("}"); // Close the block.

				// Print time
				StopWatch ct = childRun().watch;
				if (ct.ms > 1000) {
					rawPrint(" [" + ct);
					if (indLevel > 0) {
						StopWatch tt = thisRun().watch;
						rawPrint(", cum. "
								+ new StopWatch(tt.getCurrTimeLong()));
					}
					rawPrint("]");
				}
				rawPrint("\n");
			}
		}
	}

	// Normal printing
	public static void logs(String format, Object... args) {
		logs(String.format(format, args));
	}

	public synchronized static void logs(Object o) {
		if (forcePrint || (indWithin() && thisRun().newLine()))
			printLines(o);
	}

	// Always print
	public synchronized static void logsForce(String format, Object... args) {
		printLines(String.format(format, args));
	}

	public synchronized static void logsForce(Object o) {
		thisRun().newLine();
		printLines(o);
	}

	// Print if parent printed
	public static void logss(String format, Object... args) {
		logss(String.format(format, args));
	}

	public synchronized static void logss(Object o) {
		if (parentPrinted())
			thisRun().forcePrint();
		logs(o);
	}

	private static boolean parentPrinted() {
		// Parent must have been a track, so its run information has not been
		// updated yet. Therefore, shouldPrint() is valid.
		return indLevel == 0
				|| (indLevel <= maxIndLevel && parentIndWithin() && parentRun()
						.shouldPrint());
	}

	// Log different types of information
	@Deprecated
	public static void dbg(String format, Object... args) {
		dbg(String.format(format, args));
	}

	public static void dbgs(String format, Object... args) {
		dbg(String.format(format, args));
	}

	public static void dbg(Object o) {
		logss("DBG: " + o);
	}

	public static void rants(String format, Object... args) {
		rant(String.format(format, args));
	}

	public static void rant(Object o) {
		logss("RANT: " + o);
	}

	@Deprecated
	public static void error(String format, Object... args) {
		error(String.format(format, args));
	}

	public static void errors(String format, Object... args) {
		error(String.format(format, args));
	}

	public static void error(Object o) {
		if (numErrors < maxPrintErrors)
			print("ERROR: " + o + "\n");
		numErrors++;
	}

	@Deprecated
	public static void warning(String format, Object... args) {
		warning(String.format(format, args));
	}

	public static void warnings(String format, Object... args) {
		warning(String.format(format, args));
	}

	public static void warning(Object o) {
		print("WARNING: " + o + "\n");
		numWarnings++;
	}

	public static void fails(String format, Object... args) {
		fail(String.format(format, args));
	}

	public static void fail(Object o) {
		throw Exceptions.bad(o);
	}

	// Print random things
	public static void printProgStatus() {
		logs("PROG_STATUS: time = " + watch.stop() + ", memory = "
				+ SysInfoUtils.getUsedMemoryStr());
	}

	public static <T> void printList(String s, String lines) {
		printList(s, Arrays.asList(lines.split("\n")));
	}

	public static <T> void printList(String s, List<T> items) {
		track(s, true);
		for (T x : items)
			logs(x);
		end_track();
	}

	// //////////////////////////////////////////////////////////

	// Options
	@Option(gloss = "Maximum indent level.")
	public static int maxIndLevel = 10;
	@Option(gloss = "Maximum number of milliseconds between consecutive lines of output.")
	public static int msPerLine = 1000;
	@Option(gloss = "File to write log.")
	public static String file = "";
	@Option(gloss = "Whether to output to the console.", name = "stdout")
	public static boolean writeToStdout = true;
	@Option(gloss = "Dummy placeholder for a comment")
	static public String note = "";
	@Option(gloss = "Force printing from logs*")
	static public boolean forcePrint;
	@Option(gloss = "Maximum number of errors (via error()) to print")
	static public int maxPrintErrors = 10000;

	static {
		updateStdStreams();
	}

	public static void updateStdStreams() {
		try {
			stdin = CharEncUtils.getReader(System.in);
			stdout = CharEncUtils.getWriter(System.out);
			stderr = CharEncUtils.getWriter(System.err);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static void init() {
		// Write to file, stdout?
		if (!file.equals("")) {
			fout = IOUtils.openOutHard(file);
		}
		if (writeToStdout)
			out = stdout;
	}

	private static LogRun parentRun() {
		return runs.get(indLevel - 1);
	}

	private static LogRun thisRun() {
		return runs.get(indLevel);
	}

	private static LogRun childRun() {
		return runs.get(indLevel + 1);
	}

	// If we were to print a new line, should we print?
	private static boolean indWithin() {
		return indLevel <= maxIndLevel;
	}

	private static boolean parentIndWithin() {
		return indLevel - 1 <= maxIndLevel;
	}

	// Internal: don't use these functions directly

	private static void rawPrint(Object o) {
		if (out != null) {
			out.print(o);
			out.flush();
		}
		if (fout != null) {
			fout.print(o);
			fout.flush();
		}
	}

	// Print with indent; flush the buffer as necessary
	private static void print(Object o) {
		rawPrint(buf);
		buf.delete(0, buf.length());
		for (int i = 0; i < indLevel; i++)
			rawPrint("  ");
		rawPrint(o);
	}

	// If there are new lines, put indents before them
	private static void printLines(Object o) {
		if (o == null)
			o = "null";
		String s = StrUtils.toString(o);
		if (s.indexOf('\n') == -1)
			print(s + "\n");
		else
			for (String t : StrUtils.split(s, "\n"))
				print(t + "\n");
	}

	public static StopWatch getWatch() {
		return watch;
	}

	public static int getNumErrors() {
		return numErrors;
	}

	public static int getNumWarnings() {
		return numWarnings;
	}

	public static BufferedReader stdin;
	public static PrintWriter stdout, stderr;

	// Private state.
	static PrintWriter out, fout;
	static int indLevel; // Current indent level.
	static int stoppedIndLevel; // At what level did we stop printing
	static StringBuilder buf; // The buffer to be flushed out the next time
								// _logs is called.
	static ArrayList<LogRun> runs; // Indent level -> state
	static StopWatch watch; // StopWatch that starts at the beginning of the
							// program
	static int numErrors; // Number of errors made
	static int numWarnings; // Number of warnings

	// Default setup
	static {
		buf = new StringBuilder();
		indLevel = 0;
		stoppedIndLevel = -1;

		runs = new ArrayList<LogRun>(128);
		for (int i = 0; i < 128; i++)
			runs.add(new LogRun());
		watch = new StopWatch();
		watch.start();

		out = stdout;
	}
}

/**
 * A run is a sequence of lines of text, some of which are printed. Stores the
 * state associated with a run.
 */
class LogRun {
	public LogRun() {
		watch = new StopWatch();
		init();
	}

	void init() {
		numLines = 0;
		numLinesPrinted = 0;
		nextLineToPrint = 0;
		printAllLines = false;
		watch.reset();
		watch.start();
	}

	void finish() {
		// Make it clear that this run is not printed.
		// Otherwise, logss might think its
		// parent was printed when it really wasn't.
		nextLineToPrint = -1;
		watch.stop();
	}

	void forcePrint() {
		forcePrint = true;
	}

	boolean shouldPrint() {
		return forcePrint || nextLineToPrint == numLines;
	}

	int numOmitted() {
		return numLines - numLinesPrinted;
	}

	/**
	 * Decide whether to print the next line. If yes, then you must print it.
	 * 
	 * @return Whether the next line should be printed.
	 */
	boolean newLine() {
		boolean p = shouldPrint();
		numLines++;
		if (!p)
			return false; // Assume forcePrint == false

		// Ok, we're going to print this line.
		numLinesPrinted++;

		// Decide next line to print.
		int msPerLine = LogInfo.msPerLine;
		if (numLines <= 2 || // Print first few lines anyway.
				msPerLine == 0 || // Print everything.
				printAllLines || // Print every line in this run (by fiat).
				forcePrint) // Force-printed things shouldn't affect timing.
			nextLineToPrint++;
		else {
			long elapsed_ms = watch.getCurrTimeLong();
			if (elapsed_ms == 0) { // No time has elapsed.
				// This usually applies in the beginning of a run when we have
				// no idea how long things are going to take
				nextLineToPrint *= 2; // Exponentially increase time between
										// lines.
			} else
				// Try to maintain the number of lines per second.
				nextLineToPrint += (int) Math.max((double) numLines * msPerLine
						/ elapsed_ms, 1);
		}
		forcePrint = false;

		return true;
	}

	int numLines; // Number of lines that we've gone through so far in this run.
	int numLinesPrinted; // Number of lines actually printed.
	int nextLineToPrint; // Next line to be printed (lines are 0-based).
	StopWatch watch; // Keeps track of time spent on this run.
	boolean printAllLines; // Whether or not to force the printing of each line.
	boolean forcePrint; // Whether to print out the next item (is reset
						// afterwards).
}
