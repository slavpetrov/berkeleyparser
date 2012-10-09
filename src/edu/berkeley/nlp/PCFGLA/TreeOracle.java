/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import edu.berkeley.nlp.parser.EnglishPennTreebankParseEvaluator;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees.PennTreeReader;

/**
 * @author petrov
 * 
 */
public class TreeOracle {

	public static class Options {
		@Option(name = "-nbestFile", usage = "File with nbest lists")
		public String nbestFile;

		@Option(name = "-goldFile", usage = "File with gold trees")
		public String goldFile;
	}

	public static void main(String[] args) {
		OptionParser optParser = new OptionParser(Options.class);
		Options opts = (Options) optParser.parse(args, true);
		// provide feedback on command-line arguments
		System.err.println("Calling with " + optParser.getPassedInOptions());

		int totalTrees = 0;
		int goldTrees = 0;
		EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String> eval = new EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String>(
				new HashSet<String>(Arrays.asList(new String[] { "ROOT",
						"PSEUDO" })),
				new HashSet<String>(Arrays.asList(new String[] { "''", "``",
						".", ":", "," })));

		try {
			BufferedReader nbestData = new BufferedReader(
					new InputStreamReader(new FileInputStream(opts.nbestFile),
							"UTF-8"));
			BufferedReader goldData = new BufferedReader(new InputStreamReader(
					new FileInputStream(opts.goldFile), "UTF-8"));

			String line = "";
			List<Tree<String>> nbestList = new LinkedList<Tree<String>>();
			while ((line = nbestData.readLine()) != null) {
				Tree<String> tree = PennTreeReader.parseEasy(line);
				if (line.equals("\n") || tree == null
						|| tree.getYield().get(0).equals("")) { // done with the
																// block
					Tree<String> bestTree = null;
					double bestF1 = -1;
					Tree<String> goldTree = PennTreeReader.parseEasy(goldData
							.readLine());
					// System.err.println(goldTree);
					for (Tree<String> candidateTree : nbestList) {
						// System.err.println(candidateTree);
						if (candidateTree.getYield().size() == 0)
							continue;
						double f1 = eval.evaluate(candidateTree, goldTree,
								false);
						totalTrees++;
						if (f1 > bestF1) {
							bestF1 = f1;
							bestTree = candidateTree;
						}
					}
					if (bestTree == null) {
						System.out.println("(())");
					} else {
						System.out.println(bestTree);
						goldTrees++;
					}
					nbestList = new LinkedList<Tree<String>>();
				} else {
					nbestList.add(tree);
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}

		System.err.println("Average nbest list length:" + (double) totalTrees
				/ (double) goldTrees);
		System.exit(0);

	}

}
