/**
 * 
 */
package edu.berkeley.nlp.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * @author adpauls
 * 
 */
public class PerlIOFuncs {

	public enum ControlStatement {
		next, last, redo;
	}

	public static String chomp(String s) {
		String lineSep = System.getProperty("line.separator");
		if (s.endsWith(lineSep)) {
			return s.substring(s.length() - lineSep.length());
		} else {
			return s;
		}
	}

	public static interface LineCallback {
		ControlStatement handleLine(String line);
	}

	public static void diamond(File file, LineCallback c) {
		try {
			BufferedReader r = new BufferedReader(new FileReader(file));
			String line = null;
			while ((line = r.readLine()) != null) {
				ControlStatement cont = c.handleLine(line);
				switch (cont) {
				case next:
					continue;
				case redo:
					continue;
				case last:
					break;
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}
}
