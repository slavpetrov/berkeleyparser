/**
 * 
 */
package edu.berkeley.nlp.io;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Iterator;
import java.util.List;

import edu.berkeley.nlp.util.StringUtils;

/**
 * Tokenizer implementation that conforms to the Penn Treebank tokenization
 * conventions. This tokenizer is a Java implementation of Professor Chris
 * Manning's Flex tokenizer, pgtt-treebank.l. It reads raw text and outputs
 * tokens as edu.stanford.nlp.trees.Words in the Penn treebank format. It can
 * optionally return carriage returns as tokens.
 * 
 * @author Teg Grenager (grenager@stanford.edu)
 */
public class PTBTokenizer extends AbstractTokenizer {

	// whether carriage returns should be returned as tokens
	private boolean tokenizeCRs;
	// the underlying lexer
	PTBLexer lexer;

	/**
	 * Constructs a new PTBTokenizer that treats carriage returns as normal
	 * whitespace. No source is specified, so hasNext() will return false.
	 */
	public PTBTokenizer() {
		this(false);
	}

	/**
	 * Constructs a new PTBTokenizer that optionally returns carriage returns as
	 * their own token. CRs come back as Words whose text is the value of
	 * <code>PTBLexer.cr</code>.
	 */
	public PTBTokenizer(boolean tokenizeCRs) {
		this.tokenizeCRs = tokenizeCRs;
	}

	/**
	 * Constructs a new PTBTokenizer that treats carriage returns as normal
	 * whitespace.
	 */
	public PTBTokenizer(Reader r) {
		this(r, false);
	}

	/**
	 * Constructs a new PTBTokenizer that optionally returns carriage returns as
	 * their own token. CRs come back as Words whose text is the value of
	 * <code>PTBLexer.cr</code>.
	 */
	public PTBTokenizer(Reader r, boolean tokenizeCRs) {
		this.tokenizeCRs = tokenizeCRs;
		setSource(r);
	}

	/**
	 * Get the next valid Word from the lexer if possible.
	 */
	@Override
	protected Object getNext() {
		if (lexer == null) {
			return null;
		}
		Object token = null;
		try {
			token = lexer.next();
			// get rid of CRs if necessary
			while (!tokenizeCRs && PTBLexer.cr.equals(token))
				token = lexer.next();
		} catch (Exception e) {
			nextToken = null;
		}
		return token;
	}

	/**
	 * Reads a file from the argument and prints its tokens one per line. This
	 * is mainly as a testing aid, but it can also be quite useful standalone to
	 * turn a corpus into a one token per line file of tokens.
	 * <p>
	 * Usage: <code>java edu.stanford.nlp.process.PTBTokenizer filename
	 *  </code>
	 * 
	 * @param args
	 *            Command line arguments
	 */
	public static void main(String[] args) throws IOException {
		if (args.length < 1) {
			System.err.println("usage: java edu.berkeley.nlp.io."
					+ "PTBTokenizer [-cr] filename");
			return;
		}
		PTBTokenizer tokenizer = new PTBTokenizer(new FileReader(
				args[args.length - 1]), "-cr".equals(args[0]));
		List words = tokenizer.tokenize();
		for (int i = 0; i < words.size(); i++)
			System.out.println(words.get(i));
	}

	/**
	 * Sets the source of this Tokenizer to be the Reader r.
	 */
	public void setSource(Reader r) {
		lexer = new PTBLexer(r);
	}

	/**
	 * Returns a presentable version of the given PTB-tokenized text. PTB
	 * tokenization splits up punctuation and does various other things that
	 * makes simply joining the tokens with spaces look bad. So join the tokens
	 * with space and run it through this method to produce nice looking text.
	 * It's not perfect, but it works pretty well.
	 */
	public static String ptb2Text(String ptbText) {
		StringBuffer sb = new StringBuffer(ptbText.length()); // probably an
																// overestimate
		PTB2TextLexer lexer = new PTB2TextLexer(new StringReader(ptbText));
		String token;
		try {
			while ((token = lexer.next()) != null)
				sb.append(token);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return (sb.toString());
	}

	/**
	 * Returns a presentable version of the given PTB-tokenized words. Pass in a
	 * List of Words or Strings, or a Document and this method will join the
	 * words with spaces and call {@link #ptb2Text(String) } on the output. This
	 * method will check if the elements in the list are subtypes of Word, and
	 * if so, it will take the word() values to prevent additional text from
	 * creeping in (e.g., POS tags). Otherwise the toString value will be used.
	 */
	public static String ptb2Text(List ptbWords) {
		for (int i = 0; i < ptbWords.size(); i++)
			if (ptbWords.get(i) instanceof String)
				ptbWords.set(i, (ptbWords.get(i)));

		return (ptb2Text(StringUtils.join(ptbWords)));
	}

	public static TokenizerFactory factory() {
		return new PTBTokenizerFactory();
	}

	public static TokenizerFactory factory(boolean tokenizeCRs) {
		return new PTBTokenizerFactory(tokenizeCRs);
	}

	static class PTBTokenizerFactory implements TokenizerFactory {

		protected boolean tokenizeCRs;

		/**
		 * Constructs a new PTBTokenizerFactory that treats carriage returns as
		 * normal whitespace.
		 */
		public PTBTokenizerFactory() {
			this(false);
		}

		/**
		 * Constructs a new PTBTokenizer that optionally returns carriage
		 * returns as their own token. CRs come back as Words whose text is the
		 * value of <code>PTBLexer.cr</code>.
		 */
		public PTBTokenizerFactory(boolean tokenizeCRs) {
			this.tokenizeCRs = tokenizeCRs;
		}

		public Iterator getIterator(Reader r) {
			return getTokenizer(r);
		}

		public Tokenizer getTokenizer(Reader r) {
			return new PTBTokenizer(r, tokenizeCRs);
		}

	}

}
