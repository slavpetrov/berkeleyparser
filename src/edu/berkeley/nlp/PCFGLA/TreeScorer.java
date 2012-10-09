/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees.PennTreeReader;
import edu.berkeley.nlp.util.Numberer;
import edu.berkeley.nlp.util.ScalingTools;

/**
 * @author petrov Takes an unannotated tree a returns the log-likelihood of all
 *         derivations corresponding to the given tree.
 * 
 */
public class TreeScorer {

	public static class Options {

		@Option(name = "-gr", required = true, usage = "Input File for Grammar (Required)\n")
		public String inFileName;

		@Option(name = "-inputFile", usage = "Read input from this file instead of reading it from STDIN.")
		public String inputFile;

		@Option(name = "-outputFile", usage = "Store output in this file instead of printing it to STDOUT.")
		public String outputFile;

		@Option(name = "-printStats", usage = "Compute and print subcategory usage statistics")
		public boolean printStats;

	}

	public static void main(String[] args) {
		OptionParser optParser = new OptionParser(Options.class);
		Options opts = (Options) optParser.parse(args, true);
		// provide feedback on command-line arguments
		System.err.println("Calling with " + optParser.getPassedInOptions());

		String inFileName = opts.inFileName;
		if (inFileName == null) {
			throw new Error("Did not provide a grammar.");
		}
		System.err.println("Loading grammar from " + inFileName + ".");

		ParserData pData = ParserData.Load(inFileName);
		if (pData == null) {
			System.out.println("Failed to load grammar from file" + inFileName
					+ ".");
			System.exit(1);
		}
		Grammar grammar = pData.getGrammar();
		grammar.splitRules();
		SophisticatedLexicon lexicon = (SophisticatedLexicon) pData
				.getLexicon();
		ArrayParser parser = new ArrayParser(grammar, lexicon);

		Numberer.setNumberers(pData.getNumbs());
		Numberer tagNumberer = Numberer.getGlobalNumberer("tags");
		short[] numSubstates = grammar.numSubStates;

		double[][] cumulativePosteriors = null;
		if (opts.printStats) {
			cumulativePosteriors = new double[numSubstates.length][];
			for (int state = 0; state < numSubstates.length; state++) {
				cumulativePosteriors[state] = new double[numSubstates[state]];
			}
		}

		try {
			BufferedReader inputData = (opts.inputFile == null) ? new BufferedReader(
					new InputStreamReader(System.in)) : new BufferedReader(
					new InputStreamReader(new FileInputStream(opts.inputFile),
							"UTF-8"));
			PrintWriter outputData = (opts.outputFile == null) ? new PrintWriter(
					new OutputStreamWriter(System.out)) : new PrintWriter(
					new OutputStreamWriter(
							new FileOutputStream(opts.outputFile), "UTF-8"),
					true);

			Tree<String> tree = null;
			String line = "";
			while ((line = inputData.readLine()) != null) {
				if (line.equals("")) {
					outputData.write("\n");
					continue;
				}
				tree = PennTreeReader.parseEasy(line);
				if (tree.getYield().get(0).equals("")) { // empty tree -> parse
															// failure
					outputData.write("()\n");
					continue;
				}
				tree = TreeAnnotations.processTree(tree, pData.v_markov,
						pData.h_markov, pData.bin, false);
				Tree<StateSet> stateSetTree = StateSetTreeList
						.stringTreeToStatesetTree(tree, numSubstates, false,
								tagNumberer);
				allocate(stateSetTree);
				if (opts.printStats) {
					parser.doInsideOutsideScores(stateSetTree, false, false);
					parser.countPosteriors(cumulativePosteriors, stateSetTree,
							stateSetTree.getLabel().getIScore(0), stateSetTree
									.getLabel().getIScale());
				} else {
					try {
						parser.doInsideScores(stateSetTree, false, false, null);
					} catch (Exception e) {
					}
				}
				double logScore = Math
						.log(stateSetTree.getLabel().getIScore(0))
						+ (stateSetTree.getLabel().getIScale() * ScalingTools.LOGSCALE);
				outputData.write(logScore + "\n");
				outputData.flush();
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		if (opts.printStats) {
			for (int state = 0; state < numSubstates.length; state++) {
				String tagname = (String) tagNumberer.object(state);
				if (tagname.endsWith("^g"))
					tagname = tagname.substring(0, tagname.length() - 2);
				Arrays.sort(cumulativePosteriors[state]);
				System.out.print(tagname);
				for (int substate = cumulativePosteriors[state].length - 1; substate >= 0; substate--) {
					System.out.print("\t"
							+ cumulativePosteriors[state][substate]);
				}
				System.out.print("\n");
			}
		}
		System.exit(0);
	}

	/*
	 * Allocate the inside and outside score arrays for the whole tree
	 */
	static void allocate(Tree<StateSet> tree) {
		tree.getLabel().allocate();
		for (Tree<StateSet> child : tree.getChildren()) {
			allocate(child);
		}
	}

}
