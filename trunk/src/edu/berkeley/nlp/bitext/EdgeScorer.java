package edu.berkeley.nlp.bitext;

import java.util.List;

//import edu.berkeley.nlp.parser.Grammar;
//import edu.berkeley.nlp.parser.Lexicon;
//import edu.berkeley.nlp.parser.cky.CKYParser;
import fig.basic.Option;

public class EdgeScorer {

	@Option(gloss = "Use the forward estimate of the RHS grammar.")
	public static boolean includeLeftScore = true;

	@Option(gloss = "Use the forward estimate of the RHS grammar.")
	public static boolean includeRightScore = true;

	@Option(gloss = "Outside heuristic: take max of the forward estimates instead of summing.")
	public static boolean useMaxNotSum = false;

	@Option(gloss = "Use lexical heuristic to score edges.")
	public static boolean computeLexicalHeuristic = false;

	@Option(gloss = "Whether to use the A* estimate.")
	public static boolean useHeuristic = false;

	private final CKYParser leftParser, rightParser;

	private final BitextForwardEstimator lexicalEstimator;

	public EdgeScorer(BitextGrammar bitextGrammar, BitextLexicon biLex) {
		Grammar leftGrammar = bitextGrammar.getLeftGrammar();
		Grammar rightGrammar = bitextGrammar.getRightGrammar();
		Lexicon leftLexicon, rightLexicon;
		leftLexicon = biLex.getLhsLexicon();
		rightLexicon = biLex.getRhsLexicon();
		this.leftParser = new CKYParser(leftGrammar, leftLexicon);
		this.rightParser = new CKYParser(rightGrammar, rightLexicon);
		lexicalEstimator = new BitextForwardEstimator(biLex);
	}

	public void setInput(List<String> leftInput, List<String> rightInput) {
		this.leftParser.parseWithoutExtract(leftInput);
		this.rightParser.parseWithoutExtract(rightInput);
		if (computeLexicalHeuristic) {
			this.lexicalEstimator.setInput(leftInput, rightInput);
		}
	}

	public void score(BitextEdge edge) {
		double outsideScore = 0.0;
		if(useHeuristic) {
			outsideScore += monolingualOutsideScore(edge);
		}
		if (computeLexicalHeuristic) {
			outsideScore += lexicalEstimator.score(edge);
		}
		edge.setOutsideScore(outsideScore);
	}

	private double monolingualOutsideScore(BitextEdge edge) {
		double leftOScore = 0;
		double rightOScore = 0;

		if (includeLeftScore) {
			leftOScore = leftParser.getOutsideScore(edge.getLeftState(), edge.getLeftStart(),
					edge.getLeftEnd());
		}
		if (includeRightScore) {
			rightOScore = rightParser.getOutsideScore(edge.getRightState(), edge
					.getRightStart(), edge.getRightEnd());
		}

		if (useMaxNotSum) {
			return (Math.min(leftOScore, rightOScore));
		} else {
			return (leftOScore + rightOScore);
		}
	}

}
