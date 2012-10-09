/**
 * 
 */
package edu.berkeley.nlp.discPCFG;

import java.util.List;

import edu.berkeley.nlp.PCFGLA.BinaryRule;
import edu.berkeley.nlp.PCFGLA.Grammar;
import edu.berkeley.nlp.PCFGLA.HierarchicalAdaptiveBinaryRule;
import edu.berkeley.nlp.PCFGLA.HierarchicalAdaptiveGrammar;
import edu.berkeley.nlp.PCFGLA.HierarchicalAdaptiveLexicalRule;
import edu.berkeley.nlp.PCFGLA.HierarchicalAdaptiveUnaryRule;
import edu.berkeley.nlp.PCFGLA.HierarchicalFullyConnectedAdaptiveLexicon;
import edu.berkeley.nlp.PCFGLA.SimpleLexicon;
import edu.berkeley.nlp.PCFGLA.SpanPredictor;
import edu.berkeley.nlp.PCFGLA.UnaryRule;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.StateSetWithFeatures;
import edu.berkeley.nlp.util.ArrayUtil;

/**
 * @author petrov
 * 
 */
public class HiearchicalAdaptiveLinearizer extends HierarchicalLinearizer {
	private static final long serialVersionUID = 1L;

	HierarchicalAdaptiveGrammar grammar;
	HierarchicalFullyConnectedAdaptiveLexicon lexicon;

	public HiearchicalAdaptiveLinearizer(Grammar grammar,
			SimpleLexicon lexicon, SpanPredictor sp, int fLevel) {
		this.grammar = (HierarchicalAdaptiveGrammar) grammar;
		lexicon.explicitlyComputeScores(fLevel);
		grammar.closedSumRulesWithParent = grammar.closedViterbiRulesWithParent = grammar.unaryRulesWithParent;
		grammar.closedSumRulesWithChild = grammar.closedViterbiRulesWithChild = grammar.unaryRulesWithC;
		grammar.clearUnaryIntermediates();
		grammar.makeCRArrays();

		this.lexicon = (HierarchicalFullyConnectedAdaptiveLexicon) lexicon;
		this.spanPredictor = sp;
		this.finalLevel = fLevel;
		this.nSubstates = (int) ArrayUtil.max(grammar.numSubStates);
		init();
		computeMappings();

	}

	@Override
	public SimpleLexicon getLexicon() {
		return lexicon;
	}

	@Override
	public Grammar getGrammar() {
		return grammar;
	}

	@Override
	public double[] getLinearizedLexicon(boolean update) {
		if (update) {
			nLexiconWeights = 0;
			for (short tag = 0; tag < lexicon.rules.length; tag++) {
				for (int word = 0; word < lexicon.rules[tag].length; word++) {
					lexicon.rules[tag][word].identifier = nLexiconWeights
							+ nGrammarWeights;
					nLexiconWeights += lexicon.rules[tag][word].getFinalLevel()
							.size(); // lexicon.rules[tag][word].nParam;
				}
			}
		}
		double[] logProbs = new double[nLexiconWeights];
		// if (update) linearIndex = new int[lexicon.rules.length][];

		int index = 0;
		for (short tag = 0; tag < lexicon.rules.length; tag++) {
			// if (update) linearIndex[tag] = new
			// int[lexicon.rules[tag].length];
			for (int word = 0; word < lexicon.rules[tag].length; word++) {
				// if (update) linearIndex[tag][word] = index + nGrammarWeights;
				List<Double> vals = lexicon.rules[tag][word].getFinalLevel();
				for (Double val : vals) {
					logProbs[index++] = val;
				}
			}
		}
		if (index != logProbs.length)
			System.out.println("unequal length in lexicon");

		return logProbs;
	}

	public void delinearizeLexicon(double[] logProbs, boolean usingOnlyLastLevel) {
		for (short tag = 0; tag < lexicon.rules.length; tag++) {
			for (int word = 0; word < lexicon.rules[tag].length; word++) {
				lexicon.rules[tag][word].updateScores(logProbs);
				lexicon.rules[tag][word].explicitlyComputeScores(finalLevel,
						usingOnlyLastLevel);
			}
		}
	}

	@Override
	public void delinearizeLexicon(double[] logProbs) {
		for (short tag = 0; tag < lexicon.rules.length; tag++) {
			for (int word = 0; word < lexicon.rules[tag].length; word++) {
				lexicon.rules[tag][word].updateScores(logProbs);
				lexicon.rules[tag][word].explicitlyComputeScores(finalLevel,
						false);
			}
		}
	}

	@Override
	public void increment(double[] counts, StateSet stateSet, int tag,
			double[] weights, boolean isGold) {
		if (!(stateSet instanceof StateSetWithFeatures)) {
			int globalSigIndex = stateSet.sigIndex;
			if (globalSigIndex != -1) {
				int tagSpecificWordIndex = lexicon.tagWordIndexer[tag]
						.indexOf(globalSigIndex);
				if (tagSpecificWordIndex >= 0) {
					HierarchicalAdaptiveLexicalRule rule = lexicon.rules[tag][tagSpecificWordIndex];
					int startIndexWord = rule.identifier;
					short[] mapping = rule.mapping;
					for (int i = 0; i < nSubstates; i++) {
						if (isGold)
							counts[startIndexWord + mapping[i]] += weights[i];
						else
							counts[startIndexWord + mapping[i]] -= weights[i];
					}
				}
			}
			int globalWordIndex = stateSet.wordIndex;
			int tagSpecificWordIndex = lexicon.tagWordIndexer[tag]
					.indexOf(globalWordIndex);
			if (tagSpecificWordIndex < 0) {
				for (int i = 0; i < nSubstates; i++) {
					weights[i] = 0;
				}
			} else {
				HierarchicalAdaptiveLexicalRule rule = lexicon.rules[tag][tagSpecificWordIndex];
				int startIndexWord = rule.identifier;
				short[] mapping = rule.mapping;
				for (int i = 0; i < nSubstates; i++) {
					if (isGold)
						counts[startIndexWord + mapping[i]] += weights[i];
					else
						counts[startIndexWord + mapping[i]] -= weights[i];
					weights[i] = 0;
				}
			}
		} else {
			StateSetWithFeatures stateSetF = (StateSetWithFeatures) stateSet;
			for (int f : stateSetF.features) {
				if (f < 0)
					continue;
				int tagF = lexicon.tagWordIndexer[tag].indexOf(f);
				if (tagF < 0)
					continue;

				HierarchicalAdaptiveLexicalRule rule = lexicon.rules[tag][tagF];
				int startIndexWord = rule.identifier;
				short[] mapping = rule.mapping;
				for (int i = 0; i < nSubstates; i++) {
					if (isGold)
						counts[startIndexWord + mapping[i]] += weights[i];
					else
						counts[startIndexWord + mapping[i]] -= weights[i];
				}
			}
			for (int i = 0; i < nSubstates; i++) {
				weights[i] = 0;
			}
		}
	}

	@Override
	public void increment(double[] counts, BinaryRule rule, double[] weights,
			boolean isGold) {
		HierarchicalAdaptiveBinaryRule hr = (HierarchicalAdaptiveBinaryRule) rule;
		int thisStartIndex = hr.identifier;
		if (true) {
			for (int curInd = 0; curInd < hr.nParam; curInd++) {
				double val = weights[curInd];
				if (val > 0) {
					weights[curInd] = 0;
					if (isGold)
						counts[thisStartIndex + curInd] += val;
					else
						counts[thisStartIndex + curInd] -= val;
				}
				// System.out.println(counts[thisStartIndex + curInd]);
			}
		} else {
			int curInd = 0;
			for (int lp = 0; lp < nSubstates; lp++) {
				for (int rp = 0; rp < nSubstates; rp++) {
					// if (scores[cp]==null) continue;
					for (int np = 0; np < nSubstates; np++) {
						double val = weights[curInd];
						short mapping[][][] = hr.mapping;
						if (val > 0) {
							counts[thisStartIndex + mapping[lp][rp][np]] += val;
							weights[curInd] = 0;
						}
						curInd++;
					}
				}
			}
		}
	}

	@Override
	public void increment(double[] counts, UnaryRule rule, double[] weights,
			boolean isGold) {
		HierarchicalAdaptiveUnaryRule hr = (HierarchicalAdaptiveUnaryRule) rule;
		int thisStartIndex = hr.identifier;
		if (true) {
			// if (hr.parentState==0)
			// System.out.println("letss ee");
			for (int curInd = 0; curInd < hr.nParam; curInd++) {
				double val = weights[curInd];
				if (val > 0) {
					weights[curInd] = 0;
					if (isGold)
						counts[thisStartIndex + curInd] += val;
					else
						counts[thisStartIndex + curInd] -= val;
				}
				// System.out.println(counts[thisStartIndex + curInd]);
			}
		} else {
			int curInd = 0;
			if (rule.parentState == -1) {
				for (int cp = 0; cp < nSubstates; cp++) {
					double val = weights[curInd];
					short[][] mapping = hr.mapping;
					if (val > 0) {
						if (isGold)
							counts[thisStartIndex + mapping[cp][0]] += val;
						else
							counts[thisStartIndex + mapping[cp][0]] -= val;
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
					short[][] mapping = hr.mapping;
					if (val > 0) {
						if (isGold)
							counts[thisStartIndex + mapping[cp][np]] += val;
						else
							counts[thisStartIndex + mapping[cp][np]] -= val;
						weights[curInd] = 0;
					}
					curInd++;
				}
			}
		}
	}

	@Override
	public void delinearizeGrammar(double[] probs) {
		int nDangerous = 0;
		for (BinaryRule bRule : grammar.binaryRuleMap.keySet()) {
			HierarchicalAdaptiveBinaryRule hRule = (HierarchicalAdaptiveBinaryRule) bRule;
			hRule.updateScores(probs);
		}
		if (nDangerous > 0)
			System.out
					.println("Left "
							+ nDangerous
							+ " binary rule weights unchanged since the proposed weight was dangerous.");

		nDangerous = 0;
		for (UnaryRule uRule : grammar.unaryRuleMap.keySet()) {
			HierarchicalAdaptiveUnaryRule hRule = (HierarchicalAdaptiveUnaryRule) uRule;
			hRule.updateScores(probs);
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

	@Override
	public double[] getLinearizedGrammar(boolean update) {
		if (update) {
			// int nRules = grammar.binaryRuleMap.size() +
			// grammar.unaryRuleMap.size();
			// startIndex = new int[nRules];

			nGrammarWeights = 0;
			for (BinaryRule bRule : grammar.binaryRuleMap.keySet()) {
				HierarchicalAdaptiveBinaryRule hRule = (HierarchicalAdaptiveBinaryRule) bRule;
				if (!grammar.isGrammarTag[bRule.parentState]) {
					System.out.println("Incorrect grammar tag");
				}
				bRule.identifier = nGrammarWeights;
				nGrammarWeights += hRule.nParam;
			}
			for (UnaryRule uRule : grammar.unaryRuleMap.keySet()) {
				HierarchicalAdaptiveUnaryRule hRule = (HierarchicalAdaptiveUnaryRule) uRule;
				uRule.identifier = nGrammarWeights;
				nGrammarWeights += hRule.nParam;
			}
		}
		double[] logProbs = new double[nGrammarWeights];

		for (BinaryRule bRule : grammar.binaryRuleMap.keySet()) {
			HierarchicalAdaptiveBinaryRule hRule = (HierarchicalAdaptiveBinaryRule) bRule;
			int ind = hRule.identifier;// startIndex[ruleIndexer.indexOf(hRule)];
			List<Double> vals = hRule.getFinalLevel();
			for (Double val : vals) {
				logProbs[ind++] = val;
			}
		}

		for (UnaryRule uRule : grammar.unaryRuleMap.keySet()) {
			HierarchicalAdaptiveUnaryRule hRule = (HierarchicalAdaptiveUnaryRule) uRule;
			int ind = hRule.identifier;// startIndex[ruleIndexer.indexOf(hRule)];
			if (uRule.childState == uRule.parentState)
				continue;
			List<Double> vals = hRule.getFinalLevel();
			for (Double val : vals) {
				logProbs[ind++] = val;
			}
		}
		return logProbs;
	}

	public void delinearizeLexiconWeights(double[] logWeights) {
		int nGrZ = 0, nLexZ = 0, nSpZ = 0;

		int tmpI = 0;
		for (int i = 0; i < nGrammarWeights; i++) {
			double val = logWeights[tmpI++];
			if (val == 0)
				nGrZ++;
		}

		for (int i = 0; i < nLexiconWeights; i++) {
			double val = logWeights[tmpI++];
			if (val == 0)
				nLexZ++;
		}
		delinearizeLexicon(logWeights, true);
	}

}
