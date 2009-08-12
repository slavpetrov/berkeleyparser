package edu.berkeley.nlp.bitext;

import java.util.List;


import edu.berkeley.nlp.util.Counter;
import fig.basic.Pair;

public interface BitextLexicon {

	public abstract void setLhsInputSentence(List<String> sentence);

	public abstract void setRhsInputSentence(List<String> sentence);

	public abstract Counter<Pair<String, String>> getTagScores(int lhsPosition,
			int rhsPosition);

	public abstract Lexicon getLhsLexicon();
	public abstract Lexicon getRhsLexicon();
}