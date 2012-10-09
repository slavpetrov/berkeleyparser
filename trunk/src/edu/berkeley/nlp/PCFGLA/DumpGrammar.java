/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

import edu.berkeley.nlp.util.Numberer;

/**
 * @author petrov
 * 
 */
public class DumpGrammar {

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		if (args.length < 2) {
			System.out
					.println("usage: java -cp berkeleyParser.jar edu/berkeley/nlp/parser/DumpGrammar <grammar> <output file name> [<threshold>] \n "
							+ "reads in a serialized grammar file and writes it to a text file.");
			System.exit(2);
		}

		String inFileName = args[0];
		String outName = args[1];

		System.out.println("Loading grammar from file " + inFileName + ".");
		ParserData pData = ParserData.Load(inFileName);
		if (pData == null) {
			System.out.println("Failed to load grammar from file" + inFileName
					+ ".");
			System.exit(1);
		}

		Grammar grammar = pData.getGrammar();
		// if (grammar instanceof HierarchicalGrammar)
		// grammar = (HierarchicalGrammar)grammar;
		SophisticatedLexicon lexicon = (SophisticatedLexicon) pData
				.getLexicon();

		Numberer.setNumberers(pData.getNumbs());
		dumpGrammar(outName, grammar, lexicon);

	}

	/**
	 * @param args
	 * @param outName
	 * @param pData
	 * @param grammar
	 * @param lexicon
	 */
	public static void dumpGrammar(String outName, Grammar grammar,
			SophisticatedLexicon lexicon) {
		Numberer tagNumberer = Numberer.getGlobalNumberer("tags");
		grammar.splitRules();

		Numberer n = Numberer.getGlobalNumberer("tags");

		System.out.println("Writing output to files " + outName + ".xxx");
		PrintWriter out, outN;
		try {

			// write binary rules
			out = new PrintWriter(new BufferedWriter(new FileWriter(outName
					+ ".binary")));
			outN = new PrintWriter(new BufferedWriter(new FileWriter(outName
					+ ".num.binary")));

			for (int state = 0; state < grammar.numStates; state++) {
				BinaryRule[] parentRules = grammar.splitRulesWithP(state);
				for (int i = 0; i < parentRules.length; i++) {
					int number = 0;
					BinaryRule r = parentRules[i];
					double[][][] scores = r.getScores2();

					String lState = (String) n.object(r.leftChildState);
					if (lState.endsWith("^g"))
						lState = lState.substring(0, lState.length() - 2);
					String rState = (String) n.object(r.rightChildState);
					if (rState.endsWith("^g"))
						rState = rState.substring(0, rState.length() - 2);
					String pState = (String) n.object(r.parentState);
					if (pState.endsWith("^g"))
						pState = pState.substring(0, pState.length() - 2);
					StringBuilder sb = new StringBuilder();

					for (int lS = 0; lS < scores.length; lS++) {
						for (int rS = 0; rS < scores[lS].length; rS++) {
							if (scores[lS][rS] == null)
								continue;
							for (int pS = 0; pS < scores[lS][rS].length; pS++) {
								double p = scores[lS][rS][pS];
								if (p > 0) {
									sb.append(pState + "_" + pS + " " + lState
											+ "_" + lS + " " + rState + "_"
											+ rS + " " + p + "\n");
									number++;
								}
							}
						}
					}
					out.print(sb.toString());
					outN.print(number + "\n");
				}
			}
			out.flush();
			outN.flush();
			out.close();
			outN.close();

			// write unary rules
			out = new PrintWriter(new BufferedWriter(new FileWriter(outName
					+ ".unary")));
			outN = new PrintWriter(new BufferedWriter(new FileWriter(outName
					+ ".num.unary")));

			for (int state = 0; state < grammar.numStates; state++) {
				UnaryRule[] unaries = grammar
						.getClosedViterbiUnaryRulesByParent(state);
				for (int r = 0; r < unaries.length; r++) {
					int number = 0;
					UnaryRule ur = unaries[r];
					double[][] scores = ur.getScores2();

					String cState = (String) n.object(ur.childState);
					if (cState.endsWith("^g"))
						cState = cState.substring(0, cState.length() - 2);
					String pState = (String) n.object(ur.parentState);
					if (pState.endsWith("^g"))
						pState = pState.substring(0, pState.length() - 2);
					StringBuilder sb = new StringBuilder();

					for (int cS = 0; cS < scores.length; cS++) {
						if (scores[cS] == null)
							continue;
						for (int pS = 0; pS < scores[cS].length; pS++) {
							double p = scores[cS][pS];
							if (p > 0) {
								sb.append(pState + "_" + pS + " " + cState
										+ "_" + cS + " " + p + "\n");
								number++;
							}
						}
					}
					out.print(sb.toString());
					outN.print(number + "\n");
				}
			}
			out.flush();
			outN.flush();
			out.close();
			outN.close();

			// split trees
			grammar.writeSplitTrees(new BufferedWriter(new FileWriter(outName
					+ ".hierarchy")));

			// numbsubstates
			out = new PrintWriter(new BufferedWriter(new FileWriter(outName
					+ ".numstates")));
			for (int state = 0; state < grammar.numStates; state++) {
				String tag = (String) tagNumberer.object(state);
				if (tag.endsWith("^g"))
					tag = tag.substring(0, tag.length() - 2);
				out.write(tag + "\t" + grammar.numSubStates[state] + "\n");
			}
			out.flush();
			out.close();

			// lexicon
			out = new PrintWriter(new BufferedWriter(new FileWriter(outName
					+ ".lexicon")));
			out.write(lexicon.toString());
			out.flush();
			out.close();

			// words
			out = new PrintWriter(new BufferedWriter(new FileWriter(outName
					+ ".words")));
			for (String word : lexicon.wordCounter.keySet())
				out.write(word + "\n");
			out.flush();
			out.close();

		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

}
