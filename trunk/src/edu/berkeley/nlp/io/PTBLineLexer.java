/**
 * 
 */
package edu.berkeley.nlp.io;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Similar to PTBLexer. However, instead of reading from a Reader this class is
 * given a line and returns a list of tokenized Strings.
 * 
 * @author petrov
 * 
 */
public class PTBLineLexer extends PTBLexer {

	public PTBLineLexer() {
		super((java.io.Reader) null);
	}

	public List<String> tokenizeLine(String line) throws IOException {
		LinkedList<String> tokenized = new LinkedList<String>();
		if (line == null)
			return tokenized;
		int nEl = line.length();
		char[] array = line.toCharArray();
		zzBuffer = line.toCharArray();// new char[nEl+1];
		// for(int i=0;i<nEl;i++) yy_buffer[i] = array[i];
		// yy_buffer[nEl] = (char)YYEOF;
		zzStartRead = 0;
		zzEndRead = zzBuffer.length;
		zzAtBOL = true;
		zzAtEOF = false;
		zzCurrentPos = zzMarkedPos = zzPushbackPos = 0;
		yyline = yychar = yycolumn = 0;
		zzLexicalState = YYINITIAL;
		while (zzMarkedPos < zzEndRead) {
			FeatureLabel token = next();
			if (token != null)
				tokenized.add(token.word());
		}
		return tokenized;
	}

	private boolean zzRefill() throws java.io.IOException {
		return true;
	}

}
