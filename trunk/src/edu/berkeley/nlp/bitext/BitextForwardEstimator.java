package edu.berkeley.nlp.bitext;

import java.util.List;

import edu.berkeley.nlp.util.Counter;
import fig.basic.Pair;
import fig.basic.LogInfo;

public class BitextForwardEstimator {

	private final BitextLexicon lex;

	private List<String> lhsSentence, rhsSentence;

	private double[][][] scores;

	private int[][][] alignments;

	private double[][][][] scoreSums;

	public BitextForwardEstimator(BitextLexicon biLex) {
		lex = biLex;
	}

	public double score(BitextEdge edge) {
		int i, j, k, l;
		i = edge.getLeftStart();
		j = edge.getLeftEnd();
		k = edge.getRightStart();
		l = edge.getRightEnd();

		return scoreSums[i][j][k][l];
	}

	private void computeForwardScores() {
		int i, j, rhsPos;
		int lhsLength = lhsSentence.size();
		int rhsLength = rhsSentence.size();

		for (rhsPos = 0; rhsPos < rhsLength; rhsPos++) {
			double maxOverI = Double.NEGATIVE_INFINITY; // Unreachable score
			int alignmentOverI = -1;
			for (i = 0; i < lhsLength; i++) {
				if (i > 0) {
					double newScoreI = lexScore(i - 1, rhsPos);
					if (maxOverI < newScoreI) {
						maxOverI = newScoreI;
						alignmentOverI = i - 1;
					}
				}
				double maxOverJ = maxOverI;
				int alignmentOverJ = alignmentOverI;

				// Set scores with no right gap
				scores[i][lhsLength][rhsPos] = (maxOverI == Double.NEGATIVE_INFINITY) ? 0 : maxOverI;
				alignments[i][lhsLength][rhsPos] = alignmentOverI;

				for (j = lhsLength; j > i; j--) {
					if (j < lhsLength) {
						double newScoreJ = lexScore(j, rhsPos);
						if (maxOverJ < newScoreJ) {
							maxOverJ = newScoreJ;
							alignmentOverJ = j;
						}
					}
					
					// Set scores
					scores[i][j][rhsPos] = (maxOverJ == Double.NEGATIVE_INFINITY) ? 0 : maxOverI;
					scores[i][j][rhsPos] = maxOverJ;
					alignments[i][j][rhsPos] = alignmentOverJ;
				}
			}
		}
	}

	private double lexScore(int lhsPosition, int rhsPosition) {
		// These should be cached or recomputed
		Counter<Pair<String, String>> tags = lex.getTagScores(lhsPosition, rhsPosition);
		double min = 0;
		for (Pair<String, String> tag : tags.keySet()) {
			min = Math.min(min, tags.getCount(tag));
		}
		return min;
	}

	private void sumForwardScores() {
		int lhsLength = lhsSentence.size();
		int rhsLength = rhsSentence.size();

		for (int i = 0; i < lhsLength; i++) {
			for (int j = lhsLength; j > i; j--) {
				double sumOverK = 0;
				for (int k = 0; k < rhsLength; k++) {
					if (k > 0) {
						sumOverK += scores[i][j][k - 1];
					}
					double sumOverL = sumOverK;
					for (int l = rhsLength; l > k; l--) {
						if (l < rhsLength) {
							sumOverL += scores[i][j][l];
						}
						scoreSums[i][j][k][l] = sumOverL;
					}
				}
			}
		}
	}

	public void setInput(List<String> leftInput, List<String> rightInput) {
		this.lhsSentence = leftInput;
		this.rhsSentence = rightInput;
		if (BitextJointLexicon.lhsAreCostFree == false) {
			throw new RuntimeException(
					"Computing the lexical estimate requires that lhsAreCostFree == true.");
		}

		initialize();
		LogInfo.track("Computing lexical heuristic");
		computeForwardScores();
		LogInfo.end_track();
		LogInfo.track("Compiling lexical heuristic");
		sumForwardScores();
		LogInfo.end_track();
	}

	private void initialize() {
		int lhsLength = lhsSentence.size();
		int rhsLength = rhsSentence.size();

		scores = new double[lhsLength][lhsLength + 1][rhsLength];
		alignments = new int[lhsLength][lhsLength + 1][rhsLength];
		scoreSums = new double[lhsLength][lhsLength + 1][rhsLength][rhsLength + 1];
	}

}
