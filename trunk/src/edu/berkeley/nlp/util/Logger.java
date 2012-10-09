package edu.berkeley.nlp.util;

import java.io.PrintStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.util.Stack;

public class Logger {

	public static interface LogInterface {
		public void logs(String s, Object... args);

		public void logss(String s);

		public void startTrack(String s);

		public void endTrack();

		public void dbg(String s);

		public void err(String s);

		public void err(String s, Object... args);

		public void warn(String s);

		public void warn(String string, Object... args);

		public void logss(String string, Object... args);

	}

	public static class FigLogger implements LogInterface {

		public void dbg(String s) {
			LogInfo.dbg(s);
		}

		public void endTrack() {
			LogInfo.end_track();
		}

		public void err(String s) {
			LogInfo.error(s);
		}

		public void err(String s, Object... args) {
			LogInfo.error(s, args);
		}

		public void logs(String s, Object... args) {
			LogInfo.logs(s, args);
		}

		public void logss(String s) {
			LogInfo.logss(s);
		}

		public void logss(String string, Object... args) {
			LogInfo.logss(string, args);
		}

		public void startTrack(String s) {
			LogInfo.track(s);
		}

		public void warn(String s) {
			LogInfo.warning(s);
		}

		public void warn(String string, Object... args) {
			LogInfo.warning(string, args);
		}
	}

	public static class SystemLogger implements LogInterface {

		private PrintStream out;
		private PrintStream err;
		private int trackLevel = 0;
		private boolean debug = true;

		public SystemLogger(PrintStream out, PrintStream err) {
			this.out = out;
			this.err = err;
		}

		public void close() {
			if (out != null) {
				out.close();
			}
			if (err != null) {
				err.close();
			}
		}

		public SystemLogger(String outFile, String errFile)
				throws FileNotFoundException {
			this(outFile != null ? new PrintStream(
					new FileOutputStream(outFile)) : null,
					errFile != null ? new PrintStream(new FileOutputStream(
							errFile)) : null);
		}

		public SystemLogger() {
			this(System.out, System.err);
		}

		private Stack<Long> trackStartTimes = new Stack<Long>();

		private String getIndentPrefix() {
			StringBuilder builder = new StringBuilder();
			for (int i = 0; i < trackLevel; ++i) {
				builder.append("\t");
			}
			return builder.toString();
		}

		private void output(PrintStream out, String txt) {
			if (out == null)
				return;
			String[] lines = txt.split("\n");
			String prefix = getIndentPrefix();
			for (String line : lines) {
				out.println(prefix + line);
			}
		}

		public void dbg(String s) {
			if (debug)
				output(out, "[dbg] " + s);
		}

		private String timeString(double milliSecs) {
			String timeStr = "";
			int hours = (int) (milliSecs / (1000 * 60 * 60));
			if (hours > 0) {
				milliSecs -= hours * 1000 * 60 * 60;
				timeStr += hours + "h";
			}
			int mins = (int) (milliSecs / (1000 * 60));
			if (mins > 0) {
				milliSecs -= mins * 1000.0 * 60.0;
				timeStr += mins + "m";
			}
			int secs = (int) (milliSecs / 1000.0);
			// if (secs > 0) {
			// milliSecs -= secs * 1000.0;
			timeStr += secs + "s";
			// }

			return timeStr;
		}

		public void endTrack() {
			String timeStr = null;
			synchronized (this) {
				trackLevel--;
				double milliSecs = System.currentTimeMillis()
						- trackStartTimes.pop();
				timeStr = timeString(milliSecs);
			}
			output(out, "} " + (timeStr != null ? "[" + timeStr + "]" : ""));
		}

		public void err(String s) {
			err.println(s);
		}

		public void logs(String s) {
			output(out, s);
		}

		public void logss(String s) {
			output(out, s);
		}

		public void startTrack(String s) {
			output(out, s + " {");
			synchronized (this) {
				trackLevel++;
				trackStartTimes.push(System.currentTimeMillis());
			}
		}

		public void warn(String s) {
			output(err, "[warn] " + s);
		}

		public void logs(String s, Object... args) {
			logs(String.format(s, args));
		}

		public void err(String s, Object... args) {
			output(err, "[err] " + String.format(s, args));
		}

		public void warn(String string, Object... args) {
			warn(String.format(string, args));
		}

		public void logss(String string, Object... args) {
			logss(String.format(string, args));
		}
	}

	public static class CompoundLogger implements LogInterface {
		private LogInterface[] loggers;

		public CompoundLogger(LogInterface... loggers) {
			this.loggers = loggers;
		}

		public void logs(String s, Object... args) {
			for (LogInterface logger : loggers) {
				logger.logs(s, args);
			}
		}

		public void logss(String s) {
			for (LogInterface logger : loggers) {
				logger.logss(s);
			}
		}

		public void startTrack(String s) {
			for (LogInterface logger : loggers) {
				logger.startTrack(s);
			}
		}

		public void endTrack() {
			for (LogInterface logger : loggers) {
				logger.endTrack();
			}
		}

		public void dbg(String s) {
			for (LogInterface logger : loggers) {
				logger.dbg(s);
			}
		}

		public void err(String s) {
			for (LogInterface logger : loggers) {
				logger.err(s);
			}
		}

		public void err(String s, Object... args) {
			for (LogInterface logger : loggers) {
				logger.err(s, args);
			}
		}

		public void warn(String s) {
			for (LogInterface logger : loggers) {
				logger.warn(s);
			}
		}

		public void warn(String string, Object... args) {
			for (LogInterface logger : loggers) {
				logger.warn(string, args);
			}
		}

		public void logss(String string, Object... args) {
			for (LogInterface logger : loggers) {
				logger.logss(string, args);
			}
		}
	}

	public synchronized static void setGlobalLogger(LogInterface logger) {
		instance = logger;
	}

	public synchronized static LogInterface getGlobalLogger() {
		return instance;
	}

	private static LogInterface instance = new SystemLogger();

	public static LogInterface i() {
		return instance;
	}

	public static void setFig() {
		setLogger(new FigLogger());
	}

	public static void setLogger(LogInterface i) {
		instance = i;
	}

	public static void logs(String s) {
		i().logs(s);
	}

	// Static Logger Methods
	public static void logs(String s, Object... args) {
		i().logs(s, args);
	}

	public static void logss(String s) {
		i().logss(s);
	}

	public static void startTrack(String s, Object... args) {
		i().startTrack(String.format(s, args));
	}

	public static void endTrack() {
		i().endTrack();
	}

	public static void dbg(String s) {
		i().dbg(s);
	}

	public static void err(String s) {
		i().err(s);
	}

	public static void err(String s, Object... args) {
		i().err(s, args);
	}

	public static void warn(String s) {
		i().warn(s);
	}

	public static void warn(String string, Object... args) {
		i().warn(string, args);
	}

	public static void logss(String string, Object... args) {
		i().logss(string, args);
	}

}
