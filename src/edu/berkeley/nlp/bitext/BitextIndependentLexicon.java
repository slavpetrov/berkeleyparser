package edu.berkeley.nlp.bitext;

import java.util.List;

//import edu.berkeley.nlp.parser.DefaultLexicon;
//import edu.berkeley.nlp.parser.Lexicon;
import edu.berkeley.nlp.util.Counter;
import fig.basic.Pair;

public class BitextIndependentLexicon implements BitextLexicon {
	private Lexicon lhsLex;

	private Lexicon rhsLex;

	public BitextIndependentLexicon(Lexicon lhsLex, Lexicon rhsLex) {
		super();
		this.rhsLex = rhsLex;
		this.lhsLex = lhsLex;
	}

	public Counter<Pair<String, String>> getTagScores(int lhsPosition, int rhsPosition) {
		Counter<Pair<String, String>> scores = new Counter<Pair<String, String>>();

		// Add independent probabilities
		Counter<String> lhsScores = lhsLex.getTagScores(lhsPosition);
		Counter<String> rhsScores = rhsLex.getTagScores(rhsPosition);
		for (String lhsTag : lhsScores.keySet()) {
			for (String rhsTag : rhsScores.keySet()) {
				Pair<String, String> tags = Pair.makePair(lhsTag, rhsTag);
				double s = lhsScores.getCount(lhsTag) + rhsScores.getCount(rhsTag);
				scores.incrementCount(tags, s);
			}
		}
		return scores;
	}

	public void setLhsInputSentence(List<String> sentence) {
		lhsLex.setInputSentence(sentence);
	}

	public void setRhsInputSentence(List<String> sentence) {
		rhsLex.setInputSentence(sentence);
	}

	public static BitextLexicon createDefaultLexicon() {
		return (new BitextIndependentLexicon(new DefaultLexicon(), new DefaultLexicon()));
	}

	public Lexicon getLhsLexicon() {
		return lhsLex;
	}

	public Lexicon getRhsLexicon() {
		return rhsLex;
	}
}
