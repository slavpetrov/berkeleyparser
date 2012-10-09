package edu.berkeley.nlp.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

public class CharEncUtils {
	// private static String charEncoding = "ISO-8859-1";
	private static String charEncoding = "UTF-8";

	public static String getCharEncoding() {
		return charEncoding;
	}

	public static void setCharEncoding(String charEncoding) {
		if (StrUtils.isEmpty(charEncoding))
			return;
		CharEncUtils.charEncoding = charEncoding;
		LogInfo.updateStdStreams();
	}

	public static BufferedReader getReader(InputStream in) throws IOException {
		return new BufferedReader(new InputStreamReader(in, getCharEncoding()));
	}

	public static PrintWriter getWriter(OutputStream out) throws IOException {
		return new PrintWriter(new OutputStreamWriter(out, getCharEncoding()),
				true);
	}
}
