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

import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees.PennTreeReader;
import edu.berkeley.nlp.util.Numberer;
import edu.berkeley.nlp.util.PriorityQueue;
import edu.berkeley.nlp.util.ScalingTools;

/**
 * @author petrov Takes an unannotated tree a returns the log-likelihood of all
 *         derivations corresponding to the given tree, under a number of
 *         different grammars.
 */
public class TreeReranker {

	public static class Options {

		@Option(name = "-grammar", required = true, usage = "Input Files for Grammar")
		public String inFileName;

		@Option(name = "-inputFile", usage = "Input File for Parse Trees.")
		public String inputFile;

		@Option(name = "-outputFile", usage = "Store output in this file instead of printing it to STDOUT.")
		public String outputFile;

		@Option(name = "-nGrammars", usage = "Number of grammars")
		public int nGrammars;

		@Option(name = "-kBest", usage = "Print the k best trees")
		public int kbest = 1;

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

		ArrayParser[] parsers = new ArrayParser[opts.nGrammars];
		short[][] numSubstates = new short[opts.nGrammars][];
		ParserData[] pData = new ParserData[opts.nGrammars];
		Numberer[] tagNumberer = new Numberer[opts.nGrammars];

		int v_markov = 1, h_markov = 0;
		Binarization bin = Binarization.RIGHT;

		for (int i = 0; i < opts.nGrammars; i++) {
			System.err.println("Loading grammar from " + inFileName + "."
					+ (i + 1));
			pData[i] = ParserData.Load(inFileName + "." + (i + 1));
			if (pData == null) {
				System.out.println("Failed to load grammar from file"
						+ inFileName + ".");
				System.exit(1);
			}
			Grammar grammar = pData[i].getGrammar();
			grammar.splitRules();
			SophisticatedLexicon lexicon = (SophisticatedLexicon) pData[i]
					.getLexicon();
			parsers[i] = new ArrayParser(grammar, lexicon);
			numSubstates[i] = grammar.numSubStates;

			v_markov = pData[i].v_markov;
			h_markov = pData[i].h_markov;
			bin = pData[i].bin;
			Numberer.setNumberers(pData[i].getNumbs());
			tagNumberer[i] = Numberer.getGlobalNumberer("tags");
		}

		try {
			BufferedReader inputData = (opts.inputFile == null) ? new BufferedReader(
					new InputStreamReader(System.in)) : new BufferedReader(
					new InputStreamReader(new FileInputStream(opts.inputFile),
							"UTF-8"));
			// PennTreeReader treeReader = new PennTreeReader(inputData);
			PrintWriter outputData = (opts.outputFile == null) ? new PrintWriter(
					new OutputStreamWriter(System.out)) : new PrintWriter(
					new OutputStreamWriter(
							new FileOutputStream(opts.outputFile), "UTF-8"),
					true);

			Tree<String> tree = null;
			String line = "";
			double bestScore = Double.NEGATIVE_INFINITY;
			Tree<String> bestTree = null;
			PriorityQueue<Tree<String>> pQ = new PriorityQueue<Tree<String>>();
			int index = 1;
			while ((line = inputData.readLine()) != null) {
				tree = PennTreeReader.parseEasy(line);
				if (line.equals("\n") || tree == null
						|| tree.getYield().get(0).equals("")) { // done with the
																// block
					if (bestTree == null) {
						outputData.write("(())\n");
					} else {
						if (opts.kbest == 1) {
							outputData.write(bestTree + "\n");
						} else {
							int nTrees = Math.min(opts.kbest, pQ.size());
							outputData.write(nTrees + "\t" + opts.inputFile
									+ "-" + (index++) + "\n");
							for (int i = 0; i < nTrees; i++) {
								double p = pQ.getPriority();
								outputData.write(p + "\n" + pQ.next() + "\n");
							}
							outputData.write("\n");
						}
					}
					outputData.flush();

					bestScore = Double.NEGATIVE_INFINITY;
					bestTree = null;
					pQ = new PriorityQueue<Tree<String>>();
					System.err.println("Picked best tree.");
					continue;
				}

				Tree<String> processedTree = TreeAnnotations.processTree(tree,
						v_markov, h_markov, bin, false);
				double[] logScores = new double[opts.nGrammars];
				for (int i = 0; i < opts.nGrammars; i++) {
					Tree<StateSet> stateSetTree = StateSetTreeList
							.stringTreeToStatesetTree(processedTree,
									numSubstates[i], false, tagNumberer[i]);
					allocate(stateSetTree);
					parsers[i].doInsideScores(stateSetTree, false, false, null);
					logScores[i] = Math.log(stateSetTree.getLabel()
							.getIScore(0))
							+ (stateSetTree.getLabel().getIScale() * ScalingTools.LOGSCALE);
				}
				// double totalScore = SloppyMath.logAdd(logScores);
				double totalScore = DoubleArrays.add(logScores);// /opts.nGrammars;

				if (opts.kbest > 1 && totalScore != Double.NEGATIVE_INFINITY) {
					pQ.add(tree, totalScore);
				}
				if (totalScore > bestScore) {
					// System.err.println(totalScore);
					bestScore = totalScore;
					bestTree = tree;
				}
			}
			outputData.flush();
			outputData.close();
		} catch (Exception ex) {
			ex.printStackTrace();
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
