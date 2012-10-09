/**
 * 
 */
package edu.berkeley.nlp.discPCFG;

import java.io.Serializable;
import java.util.List;

import edu.berkeley.nlp.PCFGLA.BinaryRule;
import edu.berkeley.nlp.PCFGLA.ConditionalTrainer;
import edu.berkeley.nlp.PCFGLA.Grammar;
import edu.berkeley.nlp.PCFGLA.SimpleLexicon;
import edu.berkeley.nlp.PCFGLA.SpanPredictor;
import edu.berkeley.nlp.PCFGLA.UnaryRule;
import edu.berkeley.nlp.math.SloppyMath;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.util.ArrayUtil;

/**
 * @author petrov
 * 
 */
public class DefaultLinearizer implements Linearizer, Serializable {
	Grammar grammar;
	SimpleLexicon lexicon;
	SpanPredictor spanPredictor;

	int[][] linearIndex;
	int nGrammarWeights, nLexiconWeights, nSpanWeights;
	int nWords, startSpanWeights;
	int nSubstates;
	int nClasses;

	int startIndexPrevious, startIndexNext;
	int startIndexFirst, startIndexLast;
	int startIndexBeginPair, startIndexEndPair;
	int startIndexPunctuation;

	double[] lastProbs;

	private static final long serialVersionUID = 2L;

	public DefaultLinearizer() {
	}

	/**
	 * @param grammar
	 * @param lexicon
	 * @param threshold
	 */
	public DefaultLinearizer(Grammar grammar, SimpleLexicon lexicon,
			SpanPredictor sp) {
		this.grammar = grammar;
		this.lexicon = lexicon;
		this.spanPredictor = sp;
		this.nSubstates = (int) ArrayUtil.max(grammar.numSubStates);
		init();
	}

	protected void init() {
		double[] tmp = null;
		if (!ConditionalTrainer.Options.lockGrammar) {
			tmp = getLinearizedGrammar(true);
			tmp = getLinearizedLexicon(true);
		}
		tmp = getLinearizedSpanPredictor(true);
	}

	public void delinearizeSpanPredictor(double[] probs) {
		if (spanPredictor == null)
			return;
		int ind = startSpanWeights, nDangerous = 0;

		if (spanPredictor.useFirstAndLast) {
			double[][] tmp = spanPredictor.firstWordScore;
			for (int i = 0; i < tmp.length; i++) {
				for (int c = 0; c < tmp[0].length; c++) {
					double val = probs[ind++];
					if (Math.abs(val) > 300) {
						nDangerous++;
						continue;
					}
					val = Math.exp(val);
					tmp[i][c] = val;
				}
			}

			tmp = spanPredictor.lastWordScore;
			for (int i = 0; i < tmp.length; i++) {
				for (int c = 0; c < tmp[0].length; c++) {
					double val = probs[ind++];
					if (Math.abs(val) > 300) {
						nDangerous++;
						continue;
					}
					val = Math.exp(val);
					tmp[i][c] = val;
				}
			}
		}

		if (spanPredictor.usePreviousAndNext) {
			double[][] tmp = spanPredictor.previousWordScore;
			for (int i = 0; i < tmp.length; i++) {
				for (int c = 0; c < tmp[0].length; c++) {
					double val = probs[ind++];
					if (Math.abs(val) > 300) {
						nDangerous++;
						continue;
					}
					val = Math.exp(val);
					tmp[i][c] = val;
				}
			}

			tmp = spanPredictor.nextWordScore;
			for (int i = 0; i < tmp.length; i++) {
				for (int c = 0; c < tmp[0].length; c++) {
					double val = probs[ind++];
					if (Math.abs(val) > 300) {
						nDangerous++;
						continue;
					}
					val = Math.exp(val);
					tmp[i][c] = val;
				}
			}
		}

		if (spanPredictor.useBeginAndEndPairs) {
			double[][] tmp = spanPredictor.beginPairScore;
			for (int i = 0; i < tmp.length; i++) {
				for (int c = 0; c < tmp[0].length; c++) {
					double val = probs[ind++];
					if (Math.abs(val) > 300) {
						nDangerous++;
						continue;
					}
					val = Math.exp(val);
					tmp[i][c] = val;
				}
			}

			tmp = spanPredictor.endPairScore;
			for (int i = 0; i < tmp.length; i++) {
				for (int c = 0; c < tmp[0].length; c++) {
					double val = probs[ind++];
					if (Math.abs(val) > 300) {
						nDangerous++;
						continue;
					}
					val = Math.exp(val);
					tmp[i][c] = val;
				}
			}
		}

		if (spanPredictor.usePunctuation) {
			double[][] tmp = spanPredictor.punctuationScores;
			for (int i = 0; i < tmp.length; i++) {
				for (int c = 0; c < tmp[0].length; c++) {
					double val = probs[ind++];
					if (Math.abs(val) > 300) {
						nDangerous++;
						continue;
					}
					val = Math.exp(val);
					tmp[i][c] = val;
				}
			}
		}

		if (nDangerous > 0)
			System.out
					.println("Ignored "
							+ nDangerous
							+ " proposed span feature weights since they were dangerous.");

	}

	public void delinearizeGrammar(double[] probs) {
		int nDangerous = 0;
		for (BinaryRule bRule : grammar.binaryRuleMap.keySet()) {
			int ind = bRule.identifier;// startIndex[ruleIndexer.indexOf(bRule)];
			double[][][] scores = bRule.getScores2();
			for (int j = 0; j < scores.length; j++) {
				for (int k = 0; k < scores[j].length; k++) {
					if (scores[j][k] != null) {
						for (int l = 0; l < scores[j][k].length; l++) {
							double val = Math.exp(probs[ind++]);
							if (SloppyMath.isVeryDangerous(val)) {
								System.out.println("dangerous value for rule "
										+ bRule + " " + probs[ind - 1]);
								val = 0;
								nDangerous++;
								// continue;
							}
							scores[j][k][l] = val;
						}
					}
				}
			}
		}
		if (nDangerous > 0)
			System.out
					.println("Left "
							+ nDangerous
							+ " binary rule weights unchanged since the proposed weight was dangerous.");

		nDangerous = 0;
		for (UnaryRule uRule : grammar.unaryRuleMap.keySet()) {
			int ind = uRule.identifier;// startIndex[ruleIndexer.indexOf(uRule)];
			if (uRule.childState == uRule.parentState)
				continue;
			double[][] scores = uRule.getScores2();
			for (int j = 0; j < scores.length; j++) {
				if (scores[j] != null) {
					for (int k = 0; k < scores[j].length; k++) {
						double val = Math.exp(probs[ind++]); // probs[ind++]
						if (SloppyMath.isVeryDangerous(val)) {
							System.out.println("dangerous value for rule "
									+ uRule + " " + probs[ind - 1]);
							val = 0;
							nDangerous++;
							// continue;
						}
						scores[j][k] = val;
					}
				}
			}
		}
		if (nDangerous > 0)
			System.out
					.println("Left "
							+ nDangerous
							+ " unary rule weights unchanged since the proposed weight was dangerous.");

		grammar.closedSumRulesWithParent = grammar.closedViterbiRulesWithParent = grammar.unaryRulesWithParent;
		grammar.closedSumRulesWithChild = grammar.closedViterbiRulesWithChild = grammar.unaryRulesWithC;
		// computePairsOfUnaries();
		grammar.clearUnaryIntermediates();
		grammar.makeCRArrays();
		// return grammar;
	}

	public double[] getLinearizedGrammar(boolean update) {
		if (update) {
			// int nRules = grammar.binaryRuleMap.size() +
			// grammar.unaryRuleMap.size();

			nGrammarWeights = 0;
			for (BinaryRule bRule : grammar.binaryRuleMap.keySet()) {
				// ruleIndexer.add(bRule);
				if (!grammar.isGrammarTag[bRule.parentState]) {
					System.out.println("Incorrect grammar tag");
				}
				bRule.identifier = nGrammarWeights;
				// ruleIndexer.indexOf(bRule);
				// startIndex[bRule.identifier] = ;
				double[][][] scores = bRule.getScores2();
				for (int j = 0; j < scores.length; j++) {
					for (int k = 0; k < scores[j].length; k++) {
						if (scores[j][k] != null) {
							nGrammarWeights += scores[j][k].length;
						}
					}
				}
			}
			for (UnaryRule uRule : grammar.unaryRuleMap.keySet()) {
				// ruleIndexer.add(uRule);
				uRule.identifier = nGrammarWeights;
				// ruleIndexer.indexOf(uRule);
				// startIndex[uRule.identifier] = nGrammarWeights;
				double[][] scores = uRule.getScores2();
				for (int j = 0; j < scores.length; j++) {
					if (scores[j] != null) {
						nGrammarWeights += scores[j].length;
					}
				}
			}
		}
		double[] logProbs = new double[nGrammarWeights];

		for (BinaryRule bRule : grammar.binaryRuleMap.keySet()) {
			int ind = bRule.identifier;
			double[][][] scores = bRule.getScores2();
			for (int j = 0; j < scores.length; j++) {
				for (int k = 0; k < scores[j].length; k++) {
					if (scores[j][k] != null) {
						for (int l = 0; l < scores[j][k].length; l++) {
							double val = Math.log(scores[j][k][l]);
							if (val == Double.NEGATIVE_INFINITY) {
								// toBeIgnored[ind] = true;
								// val=Double.MIN_VALUE;
							}
							logProbs[ind++] = val;
						}
					}
				}
			}
		}

		for (UnaryRule uRule : grammar.unaryRuleMap.keySet()) {
			int ind = uRule.identifier;
			if (uRule.childState == uRule.parentState)
				continue;
			double[][] scores = uRule.getScores2();
			for (int j = 0; j < scores.length; j++) {
				if (scores[j] != null) {
					for (int k = 0; k < scores[j].length; k++) {
						double val = Math.log(scores[j][k]);
						if (val == Double.NEGATIVE_INFINITY) {
							// toBeIgnored[ind] = true;
							// val=Double.MIN_VALUE;
						}
						logProbs[ind++] = val;
					}
				}
			}
		}

		return logProbs;
	}

	public void delinearizeLexicon(double[] logProbs) {
		int nDangerous = 0;
		for (short tag = 0; tag < lexicon.scores.length; tag++) {
			for (int word = 0; word < lexicon.scores[tag][0].length; word++) {
				int index = linearIndex[tag][word];
				for (int substate = 0; substate < lexicon.numSubStates[tag]; substate++) {
					double val = Math.exp(logProbs[index++]);
					if (SloppyMath.isVeryDangerous(val)) {
						System.out
								.println("dangerous value when delinearizng lexicon "
										+ lexicon.scores[tag][substate][word]);
						System.out.println("Word "
								+ lexicon.wordIndexer
										.get(lexicon.tagWordIndexer[tag]
												.get(word)) + " tag "
								+ logProbs[index - 1]);
						val = 0;
						nDangerous++;
						// continue;
					}
					lexicon.scores[tag][substate][word] = val;
				}
			}
		}
		if (nDangerous > 0)
			System.out
					.println("Left "
							+ nDangerous
							+ " lexicon weights unchanged since the proposed weight was dangerous.");
		// return lexicon;
	}

	public double[] getLinearizedLexicon() {
		return getLinearizedLexicon(false);
	}

	public double[] getLinearizedLexicon(boolean update) {
		if (update) {
			nLexiconWeights = 0;
			for (short tag = 0; tag < lexicon.scores.length; tag++) {
				for (int word = 0; word < lexicon.scores[tag][0].length; word++) {
					nLexiconWeights += lexicon.numSubStates[tag];
				}
			}
		}
		double[] logProbs = new double[nLexiconWeights];
		if (update)
			linearIndex = new int[lexicon.expectedCounts.length][];

		int index = 0;
		for (short tag = 0; tag < lexicon.scores.length; tag++) {
			if (update)
				linearIndex[tag] = new int[lexicon.scores[tag][0].length];
			for (int word = 0; word < lexicon.scores[tag][0].length; word++) {
				if (update)
					linearIndex[tag][word] = index + nGrammarWeights;
				for (int substate = 0; substate < lexicon.numSubStates[tag]; substate++) {
					double val = Math.log(lexicon.scores[tag][substate][word]);
					if (val == Double.NEGATIVE_INFINITY) {
						// toBeIgnored[index] = true;
						// val=Double.MIN_VALUE;
					}
					logProbs[index++] = val;
				}
			}
		}
		return logProbs;
	}

	//
	// public int getLinearIndex(Rule rule){
	// return startIndex[ruleIndexer.indexOf(rule)];
	// }

	public int getLinearIndex(String word, int tag) {
		return getLinearIndex(lexicon.wordIndexer.indexOf(word), tag);
	}

	public int getLinearIndex(int globalWordIndex, int tag) {
		int tagSpecificWordIndex = lexicon.tagWordIndexer[tag]
				.indexOf(globalWordIndex);
		if (tagSpecificWordIndex == -1)
			return -1;
		return linearIndex[tag][tagSpecificWordIndex];
	}

	public int dimension() {
		return nGrammarWeights + nLexiconWeights + nSpanWeights;
	}

	public void increment(double[] counts, StateSet stateSet, int tag,
			double[] weights, boolean isGold) {
		int globalSigIndex = stateSet.sigIndex;
		if (globalSigIndex != -1) {
			int startIndexWord = getLinearIndex(globalSigIndex, tag);
			if (startIndexWord >= 0) { // System.out.println("incrementing scores for unseen signature tag");
				for (int i = 0; i < nSubstates; i++) {
					if (isGold)
						counts[startIndexWord++] += weights[i];
					else
						counts[startIndexWord++] -= weights[i];
				}
			}
		}

		int startIndexWord = getLinearIndex(stateSet.wordIndex, tag);
		if (startIndexWord >= 0) {
			for (int i = 0; i < nSubstates; i++) {
				if (isGold)
					counts[startIndexWord++] += weights[i];
				else
					counts[startIndexWord++] -= weights[i];
				weights[i] = 0;
			}
		} else {
			for (int i = 0; i < nSubstates; i++) {
				weights[i] = 0;
			}
		}
	}

	public void increment(double[] counts, UnaryRule rule, double[] weights,
			boolean isGold) {
		int thisStartIndex = rule.identifier;
		int curInd = 0;
		int nSubstatesParent = (rule.parentState == 0) ? 1 : nSubstates;
		for (int cp = 0; cp < nSubstates; cp++) {
			// if (scores[cp]==null) continue;
			for (int np = 0; np < nSubstatesParent; np++) {
				if (isGold)
					counts[thisStartIndex++] += weights[curInd];
				else
					counts[thisStartIndex++] -= weights[curInd];
				weights[curInd++] = 0;
			}
		}
	}

	public void increment(double[] counts, BinaryRule rule, double[] weights,
			boolean isGold) {
		int thisStartIndex = rule.identifier;

		int curInd = 0;
		for (int lp = 0; lp < nSubstates; lp++) {
			for (int rp = 0; rp < nSubstates; rp++) {
				// if (scores[cp]==null) continue;
				for (int np = 0; np < nSubstates; np++) {
					if (isGold)
						counts[thisStartIndex++] += weights[curInd];
					else
						counts[thisStartIndex++] -= weights[curInd];
					weights[curInd++] = 0;
				}
			}
		}
	}

	public void delinearizeWeights(double[] logWeights) {
		int nGrZ = 0, nLexZ = 0, nSpZ = 0;

		int tmpI = 0;
		if (!ConditionalTrainer.Options.lockGrammar) {
			for (int i = 0; i < nGrammarWeights; i++) {
				double val = logWeights[tmpI++];
				if (val == 0)
					nGrZ++;
			}
			delinearizeGrammar(logWeights);

			for (int i = 0; i < nLexiconWeights; i++) {
				double val = logWeights[tmpI++];
				if (val == 0)
					nLexZ++;
			}
			delinearizeLexicon(logWeights);
		}

		for (int i = 0; i < nSpanWeights; i++) {
			double val = logWeights[tmpI++];
			if (val == 0)
				nSpZ++;
		}
		delinearizeSpanPredictor(logWeights);

		lastProbs = logWeights.clone();

		System.out.println("Proposed vector has " + (nGrZ + nLexZ + nSpZ) + "/"
				+ (nGrammarWeights + nLexiconWeights + nSpanWeights)
				+ " zeros [grammar: " + nGrZ + "/" + nGrammarWeights
				+ ", lexicon: " + nLexZ + "/" + nLexiconWeights + ", span: "
				+ nSpZ + "/" + nSpanWeights + "].");
	}

	public double[] getLinearizedSpanPredictor(boolean update) {
		if (spanPredictor == null) {
			nSpanWeights = 0;
			return new double[0];
		}
		nWords = spanPredictor.nWords;
		nSpanWeights = spanPredictor.nFeatures;
		startSpanWeights = nGrammarWeights + nLexiconWeights;
		nClasses = spanPredictor.getNClasses();
		double[] logProbs = new double[nSpanWeights];
		int ind = 0;

		if (update) {
			startIndexFirst = startSpanWeights;
			startIndexLast = startIndexFirst + (nWords * nClasses);

			startIndexPrevious = (spanPredictor.useFirstAndLast) ? startIndexFirst
					+ (2 * nWords * nClasses)
					: startIndexFirst;
			startIndexNext = startIndexPrevious + (nWords * nClasses);

			startIndexBeginPair = (spanPredictor.usePreviousAndNext) ? startIndexPrevious
					+ (2 * nWords * nClasses)
					: startIndexPrevious;
			startIndexEndPair = (spanPredictor.useBeginAndEndPairs) ? startIndexBeginPair
					+ (spanPredictor.beginPairScore.length * nClasses)
					: startIndexBeginPair;

			startIndexPunctuation = (spanPredictor.useBeginAndEndPairs) ? startIndexBeginPair
					+ ((spanPredictor.beginPairScore.length + spanPredictor.endPairScore.length) * nClasses)
					: startIndexBeginPair;
		}

		if (spanPredictor.useFirstAndLast) {
			double[][] tmp = spanPredictor.firstWordScore;
			for (int i = 0; i < tmp.length; i++) {
				for (int c = 0; c < tmp[0].length; c++) {
					logProbs[ind++] = Math.log(tmp[i][c]);
				}
			}

			tmp = spanPredictor.lastWordScore;
			for (int i = 0; i < tmp.length; i++) {
				for (int c = 0; c < tmp[0].length; c++) {
					logProbs[ind++] = Math.log(tmp[i][c]);
				}
			}
		}

		if (spanPredictor.usePreviousAndNext) {
			double[][] tmp = spanPredictor.previousWordScore;
			for (int i = 0; i < tmp.length; i++) {
				for (int c = 0; c < tmp[0].length; c++) {
					logProbs[ind++] = Math.log(tmp[i][c]);
				}
			}

			tmp = spanPredictor.nextWordScore;
			for (int i = 0; i < tmp.length; i++) {
				for (int c = 0; c < tmp[0].length; c++) {
					logProbs[ind++] = Math.log(tmp[i][c]);
				}
			}
		}

		if (spanPredictor.useBeginAndEndPairs) {
			double[][] tmp = spanPredictor.beginPairScore;
			for (int i = 0; i < tmp.length; i++) {
				for (int c = 0; c < tmp[0].length; c++) {
					logProbs[ind++] = Math.log(tmp[i][c]);
				}
			}

			tmp = spanPredictor.endPairScore;
			for (int i = 0; i < tmp.length; i++) {
				for (int c = 0; c < tmp[0].length; c++) {
					logProbs[ind++] = Math.log(tmp[i][c]);
				}
			}
		}

		if (spanPredictor.usePunctuation) {
			double[][] tmp = spanPredictor.punctuationScores;
			for (int i = 0; i < tmp.length; i++) {
				for (int c = 0; c < tmp[0].length; c++) {
					logProbs[ind++] = Math.log(tmp[i][c]);
				}
			}
		}

		return logProbs;
	}

	public double[] getLinearizedWeights() {
		double[] initialGrammarWeights = (ConditionalTrainer.Options.lockGrammar) ? new double[0]
				: getLinearizedGrammar();
		double[] initialLexiconWeights = (ConditionalTrainer.Options.lockGrammar) ? new double[0]
				: getLinearizedLexicon();
		double[] initialSpanWeights = getLinearizedSpanPredictor();

		double[] curWeights = new double[dimension()];
		int j = 0;
		for (int i = 0; i < initialGrammarWeights.length; i++)
			curWeights[j++] = initialGrammarWeights[i];
		for (int i = 0; i < initialLexiconWeights.length; i++)
			curWeights[j++] = initialLexiconWeights[i];
		for (int i = 0; i < initialSpanWeights.length; i++)
			curWeights[j++] = initialSpanWeights[i];

		return curWeights;
	}

	public Grammar getGrammar() {
		return grammar;
	}

	public SimpleLexicon getLexicon() {
		return lexicon;
	}

	public SpanPredictor getSpanPredictor() {
		return spanPredictor;
	}

	public double[] getLinearizedGrammar() {
		return getLinearizedGrammar(false);
	}

	public void increment(double[] counts, List<StateSet> sentence,
			double[][][] weights, boolean isGold) {
		int length = sentence.size();
		int firstIndex, lastIndex;
		int previousIndex = -1, nextIndex = -1;

		if (spanPredictor.usePunctuation) {
			int[][] punctSignatures = spanPredictor
					.getPunctuationSignatures(sentence);
			for (int start = 0; start <= length - spanPredictor.minSpanLength; start++) {
				for (int end = start + spanPredictor.minSpanLength; end <= length; end++) {
					int sig = punctSignatures[start][end];
					if (sig == -1)
						continue;
					sig *= nClasses;
					for (int c = 0; c < nClasses; c++) {
						counts[startIndexPunctuation + sig + c] -= weights[start][end][c];
					}
				}
			}
		}

		for (int start = 0; start <= length - spanPredictor.minSpanLength; start++) {
			StateSet stateSet = sentence.get(start);
			firstIndex = (stateSet.sigIndex < 0) ? stateSet.wordIndex
					: stateSet.sigIndex;
			if (spanPredictor.useOnlyWords)
				firstIndex = stateSet.wordIndex;

			double[] total = new double[nClasses];
			for (int end = start + spanPredictor.minSpanLength; end <= length; end++) {
				for (int c = 0; c < total.length; c++) {
					total[c] += weights[start][end][c];
				}
			}

			int firstI = startSpanWeights + (firstIndex * nClasses);
			int prevI = startIndexPrevious + (previousIndex * nClasses);
			for (int c = 0; c < total.length; c++) {
				double t = total[c];
				if (t == 0)
					continue;

				if (spanPredictor.useFirstAndLast) {
					counts[firstI + c] -= t;
				}
				if (spanPredictor.usePreviousAndNext && previousIndex != -1) {
					counts[prevI + c] -= t;
				}
			}
			if (spanPredictor.useBeginAndEndPairs && previousIndex != -1) {
				int beginI = (spanPredictor.getBeginIndex(previousIndex,
						firstIndex) * nClasses);
				if (beginI >= 0) {
					beginI += startIndexBeginPair;
					for (int c = 0; c < total.length; c++) {
						double t = total[c];
						if (t == 0)
							continue;
						counts[beginI + c] -= t;
					}
				}
			}
			previousIndex = firstIndex;
		}

		for (int end = length; end >= spanPredictor.minSpanLength; end--) {
			StateSet stateSet = sentence.get(end - 1);
			lastIndex = (stateSet.sigIndex < 0) ? stateSet.wordIndex
					: stateSet.sigIndex;
			if (spanPredictor.useOnlyWords)
				lastIndex = stateSet.wordIndex;

			double[] total = new double[spanPredictor.getNClasses()];
			for (int start = 0; start <= end - spanPredictor.minSpanLength; start++) {
				for (int c = 0; c < total.length; c++) {
					total[c] += weights[start][end][c];
				}
			}

			int lastI = startIndexLast + (lastIndex * nClasses);
			int nextI = startIndexNext + (nextIndex * nClasses);
			for (int c = 0; c < total.length; c++) {
				if (spanPredictor.useFirstAndLast) {
					counts[lastI + c] -= total[c];
				}
				if (spanPredictor.usePreviousAndNext && nextIndex != -1) {
					counts[nextI + c] -= total[c];
				}
			}
			if (spanPredictor.useBeginAndEndPairs && nextIndex != -1) {
				int endI = spanPredictor.getEndIndex(lastIndex, nextIndex)
						* nClasses;
				if (endI >= 0) {
					endI += startIndexEndPair;
					for (int c = 0; c < total.length; c++) {
						counts[endI + c] -= total[c];
					}
				}
			}
			nextIndex = lastIndex;
		}

	}

	public double[] getLinearizedSpanPredictor() {
		return getLinearizedSpanPredictor(false);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.classify.Linearizer#getNGrammarWeights()
	 */
	public int getNGrammarWeights() {
		return nGrammarWeights;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.classify.Linearizer#getNLexiconWeights()
	 */
	public int getNLexiconWeights() {
		return nLexiconWeights;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.classify.Linearizer#getNSpanWeights()
	 */
	public int getNSpanWeights() {
		return nSpanWeights;
	}

}
