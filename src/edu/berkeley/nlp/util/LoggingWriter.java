package edu.berkeley.nlp.util;

import java.io.IOException;
import java.io.Writer;

public class LoggingWriter extends Writer {
	private boolean logss;

	private boolean chompNewLine = false;

	public LoggingWriter(boolean logss) {
		this.logss = logss;
	}

	public LoggingWriter(boolean logss, boolean chompNewLIne) {
		this.logss = logss;
		this.chompNewLine = chompNewLIne;
	}

	@Override
	public void close() throws IOException {
		// TODO Auto-generated method stub

	}

	@Override
	public void flush() throws IOException {

	}

	@Override
	public void write(char[] cbuf, int off, int len) throws IOException {
		String string = new String(cbuf, off, len);
		if (chompNewLine && string.endsWith("\n")) {
			string = string.substring(0, string.length() - 1);
		}
		if (logss) {
			Logger.i().logss(string);
		} else {
			Logger.i().logs(string);

		}
	}

	@Override
	public void write(String str) throws IOException {
		if (logss) {
			Logger.i().logss(str);
		} else {
			Logger.i().logs(str);

		}
	}
}
