/**
 * 
 */
package edu.berkeley.nlp.discPCFG;

import java.io.Serializable;

import edu.berkeley.nlp.PCFGLA.BinaryRule;
import edu.berkeley.nlp.PCFGLA.Grammar;
import edu.berkeley.nlp.PCFGLA.HierarchicalBinaryRule;
import edu.berkeley.nlp.PCFGLA.HierarchicalGrammar;
import edu.berkeley.nlp.PCFGLA.HierarchicalLexicon;
import edu.berkeley.nlp.PCFGLA.HierarchicalUnaryRule;
import edu.berkeley.nlp.PCFGLA.Rule;
import edu.berkeley.nlp.PCFGLA.SimpleLexicon;
import edu.berkeley.nlp.PCFGLA.SpanPredictor;
import edu.berkeley.nlp.PCFGLA.UnaryRule;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.math.SloppyMath;
import edu.berkeley.nlp.util.ArrayUtil;

/**
 * similar to cascading linearizer but doesnt compute the grammars explicitly
 * instead uses hierarchical rules and merges back unused splits
 * 
 * @author petrov
 * 
 */
public class HierarchicalLinearizer extends DefaultLinearizer {

	private static final long serialVersionUID = 1L;

	HierarchicalGrammar grammar;
	HierarchicalLexicon lexicon;

	int finalLevel;
	int[][] lexiconMapping;
	int[][][] unaryMapping;
	int[][][][] binaryMapping;

	public HierarchicalLinearizer() {
	}

	/**
	 * @param grammar
	 * @param lexicon
	 */
	public HierarchicalLinearizer(Grammar grammar, SimpleLexicon lexicon,
			SpanPredictor sp, int fLevel) {
		this.grammar = (HierarchicalGrammar) grammar;
		this.lexicon = (HierarchicalLexicon) lexicon;
		this.spanPredictor = sp;
		this.finalLevel = fLevel;
		this.nSubstates = (int) ArrayUtil.max(grammar.numSubStates);
		init();
		computeMappings();
	}

	protected void computeMappings() {
		lexiconMapping = new int[finalLevel + 1][nSubstates];
		unaryMapping = new int[finalLevel + 1][nSubstates][nSubstates];
		binaryMapping = new int[finalLevel + 1][nSubstates][nSubstates][nSubstates];

		int[] divisors = new int[finalLevel + 1];
		for (int i = 0; i <= finalLevel; i++) {
			divisors[i] = (int) Math.pow(2, finalLevel - i);
		}

		for (int level = 1; level <= finalLevel; level++) {
			int div = divisors[level];
			int l = (int) Math.pow(2, level);
			int[][] tmpU = new int[l][l];
			int[][][] tmpB = new int[l][l][l];
			int indU = 0, indB = 0;
			for (int i = 0; i < l; i++) {
				for (int j = 0; j < l; j++) {
					tmpU[i][j] = indU++;
					for (int k = 0; k < l; k++) {
						tmpB[i][j][k] = indB++;
					}
				}
			}
			for (int i = 0; i < nSubstates; i++) {
				lexiconMapping[level][i] = i / div;
				for (int j = 0; j < nSubstates; j++) {
					unaryMapping[level][i][j] = tmpU[i / div][j / div];
					for (int k = 0; k < nSubstates; k++) {
						binaryMapping[level][i][j][k] = tmpB[i / div][j / div][k
								/ div];
					}
				}
			}
		}
	}

	// public void delinearizeSpanPredictor(double[] logProbs) {
	//
	// }

	public void delinearizeGrammar(double[] probs) {
		int nDangerous = 0;
		for (BinaryRule bRule : grammar.binaryRuleMap.keySet()) {
			HierarchicalBinaryRule hRule = (HierarchicalBinaryRule) bRule;
			int ind = hRule.identifier;// startIndex[ruleIndexer.indexOf(hRule)];
			double[][][] scores = hRule.getLastLevel();
			for (int j = 0; j < scores.length; j++) {
				for (int k = 0; k < scores[j].length; k++) {
					if (scores[j][k] != null) {
						for (int l = 0; l < scores[j][k].length; l++) {
							double val = probs[ind++];
							if (SloppyMath.isVeryDangerous(val)) {
								nDangerous++;
								continue;
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
			HierarchicalUnaryRule hRule = (HierarchicalUnaryRule) uRule;
			int ind = hRule.identifier;// startIndex[ruleIndexer.indexOf(hRule)];
			if (uRule.childState == uRule.parentState)
				continue;
			double[][] scores = hRule.getLastLevel();
			for (int j = 0; j < scores.length; j++) {
				if (scores[j] != null) {
					for (int k = 0; k < scores[j].length; k++) {
						double val = probs[ind++];
						if (SloppyMath.isVeryDangerous(val)) {
							nDangerous++;
							continue;
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

		grammar.explicitlyComputeScores(finalLevel);
		grammar.closedSumRulesWithParent = grammar.closedViterbiRulesWithParent = grammar.unaryRulesWithParent;
		grammar.closedSumRulesWithChild = grammar.closedViterbiRulesWithChild = grammar.unaryRulesWithC;
		// computePairsOfUnaries();
		grammar.clearUnaryIntermediates();
		grammar.makeCRArrays();
		// return grammar;
	}

	public void delinearizeLexicon(double[] logProbs) {
		int nDangerous = 0;
		for (short tag = 0; tag < lexicon.hierarchicalScores.length; tag++) {
			for (int word = 0; word < lexicon.hierarchicalScores[tag].length; word++) {
				int index = linearIndex[tag][word];
				double[] vals = lexicon.getLastLevel(tag, word);
				for (int substate = 0; substate < vals.length; substate++) {
					double val = logProbs[index++];
					if (SloppyMath.isVeryDangerous(val)) {
						nDangerous++;
						continue;
					}
					vals[substate] = val;
				}
			}
		}
		if (nDangerous > 0)
			System.out
					.println("Left "
							+ nDangerous
							+ " lexicon weights unchanged since the proposed weight was dangerous.");
		lexicon.explicitlyComputeScores(finalLevel);
		// System.out.println(lexicon);
		// return lexicon;
	}

	public double[] getLinearizedGrammar(boolean update) {
		if (update) {
			// int nRules = grammar.binaryRuleMap.size() +
			// grammar.unaryRuleMap.size();
			// startIndex = new int[nRules];

			nGrammarWeights = 0;
			for (BinaryRule bRule : grammar.binaryRuleMap.keySet()) {
				HierarchicalBinaryRule hRule = (HierarchicalBinaryRule) bRule;
				// ruleIndexer.add(hRule);
				if (!grammar.isGrammarTag[bRule.parentState]) {
					System.out.println("Incorrect grammar tag");
				}
				bRule.identifier = nGrammarWeights;
				double[][][] scores = hRule.getLastLevel();
				for (int j = 0; j < scores.length; j++) {
					for (int k = 0; k < scores[j].length; k++) {
						if (scores[j][k] != null) {
							nGrammarWeights += scores[j][k].length;
						}
					}
				}
			}
			for (UnaryRule uRule : grammar.unaryRuleMap.keySet()) {
				HierarchicalUnaryRule hRule = (HierarchicalUnaryRule) uRule;
				// ruleIndexer.add(hRule);
				// startIndex[ruleIndexer.indexOf(uRule)] = nGrammarWeights;
				uRule.identifier = nGrammarWeights;
				double[][] scores = hRule.getLastLevel();
				for (int j = 0; j < scores.length; j++) {
					if (scores[j] != null) {
						nGrammarWeights += scores[j].length;
					}
				}
			}
		}
		double[] logProbs = new double[nGrammarWeights];

		for (BinaryRule bRule : grammar.binaryRuleMap.keySet()) {
			HierarchicalBinaryRule hRule = (HierarchicalBinaryRule) bRule;
			int ind = hRule.identifier;// startIndex[ruleIndexer.indexOf(hRule)];
			double[][][] scores = hRule.getLastLevel();
			for (int j = 0; j < scores.length; j++) {
				for (int k = 0; k < scores[j].length; k++) {
					if (scores[j][k] != null) {
						for (int l = 0; l < scores[j][k].length; l++) {
							double val = scores[j][k][l];
							logProbs[ind++] = val;
						}
					}
				}
			}
		}

		for (UnaryRule uRule : grammar.unaryRuleMap.keySet()) {
			HierarchicalUnaryRule hRule = (HierarchicalUnaryRule) uRule;
			int ind = hRule.identifier;// startIndex[ruleIndexer.indexOf(hRule)];
			if (uRule.childState == uRule.parentState)
				continue;
			double[][] scores = hRule.getLastLevel();
			for (int j = 0; j < scores.length; j++) {
				if (scores[j] != null) {
					for (int k = 0; k < scores[j].length; k++) {
						double val = scores[j][k];
						logProbs[ind++] = val;
					}
				}
			}
		}
		return logProbs;
	}

	public double[] getLinearizedLexicon(boolean update) {
		if (update) {
			nLexiconWeights = 0;
			int[] substates = new int[finalLevel + 1];
			for (int i = 0; i <= finalLevel; i++)
				substates[i] = (int) Math.pow(2, i);
			for (short tag = 0; tag < lexicon.hierarchicalScores.length; tag++) {
				for (int word = 0; word < lexicon.hierarchicalScores[tag].length; word++) {
					nLexiconWeights += lexicon.getLastLevel(tag, word).length;
				}
			}
		}
		double[] logProbs = new double[nLexiconWeights];
		if (update)
			linearIndex = new int[lexicon.hierarchicalScores.length][];

		int index = 0;
		for (short tag = 0; tag < lexicon.hierarchicalScores.length; tag++) {
			if (update)
				linearIndex[tag] = new int[lexicon.hierarchicalScores[tag].length];
			for (int word = 0; word < lexicon.hierarchicalScores[tag].length; word++) {
				if (update)
					linearIndex[tag][word] = index + nGrammarWeights;
				double[] vals = lexicon.getLastLevel(tag, word);
				for (int substate = 0; substate < vals.length; substate++) {
					double val = vals[substate];
					logProbs[index++] = val;
				}
			}
		}
		if (index != logProbs.length)
			System.out.println("unequal length in lexicon");

		return logProbs;
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
			if (startIndexWord >= 0) {
				int finalLevel = lexicon.getFinalLevel(globalSigIndex, tag);
				for (int i = 0; i < nSubstates; i++) {
					if (isGold)
						counts[startIndexWord + lexiconMapping[finalLevel][i]] += weights[i];
					else
						counts[startIndexWord + lexiconMapping[finalLevel][i]] -= weights[i];
				}
			}
		}
		int globalWordIndex = stateSet.wordIndex;
		int startIndexWord = getLinearIndex(globalWordIndex, tag);
		if (startIndexWord >= 0) {
			int finalLevel = lexicon.getFinalLevel(globalWordIndex, tag);
			for (int i = 0; i < nSubstates; i++) {
				if (isGold)
					counts[startIndexWord + lexiconMapping[finalLevel][i]] += weights[i];
				else
					counts[startIndexWord + lexiconMapping[finalLevel][i]] -= weights[i];
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
		HierarchicalUnaryRule hr = (HierarchicalUnaryRule) rule;
		int thisStartIndex = hr.identifier;
		int finalLevel = hr.lastLevel;
		int curInd = 0;
		if (rule.parentState == 0) {
			for (int cp = 0; cp < nSubstates; cp++) {
				double val = weights[curInd];
				if (val > 0) {
					if (isGold)
						counts[thisStartIndex + lexiconMapping[finalLevel][cp]] += val;
					else
						counts[thisStartIndex + lexiconMapping[finalLevel][cp]] -= val;
					weights[curInd] = 0;
				}
				curInd++;
			}
			return;
		}

		for (int cp = 0; cp < nSubstates; cp++) {
			// if (scores[cp]==null) continue;
			for (int np = 0; np < nSubstates; np++) {
				double val = weights[curInd];
				if (val > 0) {
					if (isGold)
						counts[thisStartIndex
								+ unaryMapping[finalLevel][cp][np]] += val;
					else
						counts[thisStartIndex
								+ unaryMapping[finalLevel][cp][np]] -= val;
					weights[curInd] = 0;
				}
				curInd++;
			}
		}
	}

	public void increment(double[] counts, BinaryRule rule, double[] weights,
			boolean isGold) {
		HierarchicalBinaryRule hr = (HierarchicalBinaryRule) rule;
		int thisStartIndex = hr.identifier;
		int finalLevel = hr.lastLevel;
		int curInd = 0;
		for (int lp = 0; lp < nSubstates; lp++) {
			for (int rp = 0; rp < nSubstates; rp++) {
				// if (scores[cp]==null) continue;
				for (int np = 0; np < nSubstates; np++) {
					double val = weights[curInd];
					if (val > 0) {
						if (isGold)
							counts[thisStartIndex
									+ binaryMapping[finalLevel][lp][rp][np]] += val;
						else
							counts[thisStartIndex
									+ binaryMapping[finalLevel][lp][rp][np]] -= val;
						weights[curInd] = 0;
					}
					curInd++;
				}
			}
		}
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

}
