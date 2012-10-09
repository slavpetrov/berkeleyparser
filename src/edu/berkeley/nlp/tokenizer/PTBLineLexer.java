/**
 * 
 */
package edu.berkeley.nlp.tokenizer;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import edu.berkeley.nlp.util.IOUtils;
import edu.berkeley.nlp.util.Iterators;
import edu.berkeley.nlp.util.StringUtils;

/**
 * Similar to PTBLexer. However, instead of reading from a Reader this class is
 * given a line and returns a list of tokenized Strings.
 * 
 * @author petrov
 * 
 */
public class PTBLineLexer extends PTBLexer implements LineTokenizer {

	public PTBLineLexer() {
		super((java.io.Reader) null);
	}

	public List<String> tokenize(String line) {
		PTBTokenizer toker = new PTBTokenizer(new StringReader(line), true);
		List<?> elems = toker.tokenize();
		List<String> toks = new ArrayList<String>();
		for (Object o : elems) {
			toks.add(o.toString());
		}
		return toks;
	}

	public List<String> tokenizeLine(String line) throws IOException {
		LinkedList<String> tokenized = new LinkedList<String>();
		int nEl = line.length();
		char[] array = line.toCharArray();
		yy_buffer = line.toCharArray();// new char[nEl+1];
		// for(int i=0;i<nEl;i++) yy_buffer[i] = array[i];
		// yy_buffer[nEl] = (char)YYEOF;
		yy_startRead = 0;
		yy_endRead = yy_buffer.length;
		yy_atBOL = true;
		yy_atEOF = false;
		yy_currentPos = yy_markedPos = yy_pushbackPos = 0;
		yyline = yychar = yycolumn = 0;
		yy_lexical_state = YYINITIAL;
		while (yy_markedPos < yy_endRead)
			tokenized.add(next());
		return tokenized;
	}

	private boolean yy_refill() throws java.io.IOException {
		return true;
	}

	public static void main(String[] argv) {
		PTBLineLexer tokenizer = new PTBLineLexer();
		try {
			for (String line : Iterators.able(IOUtils.lineIterator(argv[0]))) {
				final List<String> tokenizeLine = tokenizer.tokenizeLine(line);
				if (tokenizeLine.get(tokenizeLine.size() - 1) == null)
					tokenizeLine.remove(tokenizeLine.size() - 1);
				System.out.println(StringUtils.join(tokenizeLine));
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			throw new RuntimeException(e);

		}
	}

}
