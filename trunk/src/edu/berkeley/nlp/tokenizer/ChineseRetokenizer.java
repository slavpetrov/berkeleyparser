package edu.berkeley.nlp.tokenizer;

import java.util.LinkedList;
import java.util.List;

public class ChineseRetokenizer implements LineTokenizer {
	public List<String> tokenizeLine(String line) {
		String replaced = replaceChars(line);
		String[] tokens = replaced.split(" ");
		boolean rightDoubleQuote = false;
		boolean rightSingleQuote = false;
		LinkedList<String> newTokens = new LinkedList<String>();
		for (int i = 0; i < tokens.length; i++) {
			String tok = tokens[i];
			if (tok.equals("\"")) {
				newTokens.add(rightDoubleQuote ? "”" : "“");
				rightDoubleQuote = !rightDoubleQuote;
			} else if (tok.equals("'")) {
				newTokens.add(rightSingleQuote ? "’" : "‘");
				rightSingleQuote = !rightSingleQuote;
			} else if (tok.equals(".")) {
				if (i == tokens.length - 1) {
					newTokens.add("。");
				} else {
					newTokens.add("，");
				}
			} else {
				newTokens.add(tok);
			}
		}

		return newTokens;
	}

	private String replaceChars(String line) {
		String s = line;
		// s = s.replace('(', '（');
		// s = s.replace(')', '）');
		// s = s.replace('{', '〈');
		// s = s.replace('}', '〉');
		// s = s.replace(',', '、');
		// s = s.replace('-', '—');
		// s = s.replace('?', '？');
		// s = s.replace('!', '！');
		// s = s.replace(':', '：');
		// s = s.replace(';', '；');
		return s;
	}

}
