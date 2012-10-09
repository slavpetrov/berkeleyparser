/*
 * @(#)BufferedReader.java	1.33 04/01/12
 *
 * Copyright 2004 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package edu.berkeley.nlp.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/**
 * 
 * @author adpauls
 */

public class EfficientBufferedReader extends BufferedReader {

	public EfficientBufferedReader(Reader in) {
		super(in);
		// TODO Auto-generated constructor stub
	}

	/**
	 * Returns true if end of file reached. Otherwise reads a line in to the
	 * provided StringBuffer
	 * 
	 * @param sb
	 * @return
	 * @throws IOException
	 */
	public boolean readLineToBuffer(StringBuilder sb) throws IOException

	{
		sb.delete(0, sb.length());

		while (true) {
			int c = read();
			if (c == -1)
				return true;
			else if (c == '\n')
				break;
			if (c != '\r')
				sb.append((char) c);
		}
		return false;
	}

}
