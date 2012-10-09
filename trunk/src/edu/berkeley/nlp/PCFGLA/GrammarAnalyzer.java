/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.util.HashMap;

import edu.berkeley.nlp.PCFGLA.BerkeleyParser.Options;
import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.Numberer;

/**
 * @author petrov
 * 
 */
public class GrammarAnalyzer {

	public static class Options {
		@Option(name = "-in", required = true, usage = "Grammarfile")
		public String grFileName;

		@Option(name = "-t", usage = "Threshold for pruning unlikely rules")
		public double threshold = -1;
	}

	public static void main(String[] args) {
		OptionParser optParser = new OptionParser(Options.class);
		Options opts = (Options) optParser.parse(args, true);

		String inFileName = opts.grFileName;
		ParserData pData = ParserData.Load(inFileName);
		if (pData == null) {
			System.out.println("Failed to load grammar from file" + inFileName
					+ ".");
			System.exit(1);
		}
		Grammar grammar = pData.getGrammar();
		Lexicon lexicon = pData.getLexicon();
		Numberer.setNumberers(pData.getNumbs());

		if (opts.threshold > -1) {
			System.out.println("Remving rules with probability below "
					+ opts.threshold + ".");
			grammar.splitRules();
			grammar.removeUnlikelyRules(opts.threshold, 1.0);
			lexicon.removeUnlikelyTags(opts.threshold, 1.0);
		}

		int[] tagTotal = new int[grammar.numSubStates.length];
		int[] tagOne = new int[grammar.numSubStates.length];

		int[] gr = computeAndPrintCounts(grammar, tagTotal, tagOne);
		printStats("Grammar", gr);
		int[] lex = computeAndPrintCounts(lexicon, tagTotal, tagOne);
		printStats("Lexicon", lex);

		ArrayUtil.addInPlace(gr, lex);
		printStats("Together", gr);

		System.out.println("\nTag specific statistics (x=1):");
		Numberer tagNumberer = Numberer.getGlobalNumberer("tags");
		for (int i = 0; i < tagTotal.length; i++) {
			System.out.println(tagNumberer.object(i) + ": " + tagOne[i] + "/"
					+ tagTotal[i]);
		}

		System.exit(0);
	}

	public static int[] computeAndPrintCounts(Grammar gr, int[] tagTotal,
			int[] tagOne) {
		int nulledOut = 0, zero = 0, belowOne = 0, one = 0, aboveOne = 0, total = 0;
		int unaryEqualSlices = 0, binaryEqualSlices = 0, unarySlices = 0, binarySlices = 0;
		int unaryEqual = 0, binaryEqual = 0, unary = 0, binary = 0;
		for (int state = 0; state < gr.numStates; state++) {
			int nParentSubStates = gr.numSubStates[state];
			for (UnaryRule uRule : gr.getUnaryRulesByParent(state)) {
				int nChildSubStates = gr.numSubStates[uRule.childState];
				total += nChildSubStates * nParentSubStates;
				unary++;
				double[][] scores = uRule.getScores2();
				// boolean allTheSame=true;
				// double veryFirst = scores[0][0];
				for (int j = 0; j < nChildSubStates; j++) {
					unarySlices++;
					if (scores[j] == null) {
						nulledOut += nParentSubStates;
						continue;
					}
					boolean sliceTheSame = true;
					double first = scores[j][0];
					for (int i = 0; i < nParentSubStates; i++) {
						double p = scores[j][i];
						if (p == 0)
							zero++;
						else if (p == 1)
							one++;
						else if (p > 1)
							aboveOne++;
						else
							belowOne++;
						if (sliceTheSame && p != first)
							sliceTheSame = false;
						// if (allTheSame&&p!=veryFirst) allTheSame=false;
						tagTotal[state]++;
						if (p == 1)
							tagOne[state]++;
					}
					if (sliceTheSame)
						unaryEqualSlices++;
				}
				// if (allTheSame) unaryEqual++;
			}
			for (BinaryRule bRule : gr.splitRulesWithP(state)) {// gr.getBinaryRulesByParent(state)){
				double[][][] scores = bRule.getScores2();
				binary++;
				// boolean allTheSame=true;
				// double veryFirst = scores[0][0][0];
				for (int j = 0; j < scores.length; j++) {
					for (int k = 0; k < scores[j].length; k++) {
						total += nParentSubStates;
						binarySlices++;
						if (scores[j][k] == null) {
							nulledOut += nParentSubStates;
							continue;
						}
						boolean sliceTheSame = true;
						double first = scores[j][k][0];
						for (int i = 0; i < nParentSubStates; i++) {
							double p = scores[j][k][i];
							if (p == 0)
								zero++;
							else if (p == 1)
								one++;
							else if (p > 1)
								aboveOne++;
							else
								belowOne++;
							if (sliceTheSame && p != first)
								sliceTheSame = false;
							// if (allTheSame&&p!=veryFirst) allTheSame=false;
							tagTotal[state]++;
							if (p == 1)
								tagOne[state]++;
						}
						if (sliceTheSame)
							binaryEqualSlices++;
					}
				}
				// if (allTheSame) binaryEqual++;
			}
		}
		System.out.println("Same across parent: " + unaryEqualSlices + "/"
				+ unarySlices + " (unary), " + binaryEqualSlices + "/"
				+ binarySlices + " (binary)");
		System.out.println("All the same: " + unaryEqual + "/" + unary
				+ " (unary), " + binaryEqual + "/" + binary + " (binary)");
		return new int[] { nulledOut, zero, belowOne, one, aboveOne, total };
	}

	public static int[] computeAndPrintCounts(Lexicon lexicon, int[] tagTotal,
			int[] tagOne) {
		int zero = 0, belowOne = 0, one = 0, aboveOne = 0, total = 0;
		int equal = 0, pairs = 0;
		if (lexicon instanceof SophisticatedLexicon) {
			SophisticatedLexicon lex = (SophisticatedLexicon) lexicon;
			for (short tag = 0; tag < lex.numSubStates.length; tag++) {
				HashMap<String, double[]> tagMap = lex.wordToTagCounters[tag];
				if (tagMap == null)
					continue;
				for (String word : tagMap.keySet()) {
					double[] lexiconScores = lex.score(word, tag, 0, false,
							false);
					for (int i = 0; i < lexiconScores.length; i++) {
						total++;
						double p = lexiconScores[i];
						if (p == 0)
							zero++;
						else if (p == 1)
							one++;
						else if (p > 1)
							aboveOne++;
						else
							belowOne++;
						tagTotal[tag]++;
						if (p == 1)
							tagOne[tag]++;
					}
				}
			}
		} else {
			SimpleLexicon lex = (SimpleLexicon) lexicon;
			for (int tag = 0; tag < lex.scores.length; tag++) {
				if (lex.tagWordIndexer[tag].size() == 0)
					continue;
				for (int word = 0; word < lex.scores[tag][0].length; word++) {
					boolean allTheSame = true;
					double first = lex.scores[tag][0][word];
					pairs++;
					for (int substate = 0; substate < lex.numSubStates[tag]; substate++) {
						total++;
						double p = lex.scores[tag][substate][word];
						if (p == 0)
							zero++;
						else if (p == 1)
							one++;
						else if (p > 1)
							aboveOne++;
						else
							belowOne++;
						if (allTheSame && p != first)
							allTheSame = false;
						tagTotal[tag]++;
						if (p == 1)
							tagOne[tag]++;
					}
					if (allTheSame)
						equal++;
				}
			}
			System.out.println("Same across parent: " + equal + "/" + pairs);
		}
		return new int[] { 0, zero, belowOne, one, aboveOne, total };
	}

	public static void printStats(String title, int[] vals) {
		int nulledOut = vals[0], zero = vals[1], belowOne = vals[2], one = vals[3], aboveOne = vals[4], total = vals[5];
		System.out.println(title + " statistics:");
		System.out.println("Total rules:\t" + total);
		System.out.println("Total non-zero:\t"
				+ (belowOne + one + aboveOne + "\n"));

		System.out.println("Nulled out:\t" + nulledOut);
		System.out.println("x = 0:\t\t" + zero);
		System.out.println("0 < x < 1:\t" + belowOne);
		System.out.println("x = 1:\t\t" + one);
		System.out.println("x > 1:\t\t" + aboveOne + "\n");

	}

}
