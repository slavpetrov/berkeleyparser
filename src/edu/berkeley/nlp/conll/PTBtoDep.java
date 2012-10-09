/**
 * 
 */
package edu.berkeley.nlp.conll;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import edu.berkeley.nlp.PCFGLA.Option;
import edu.berkeley.nlp.PCFGLA.OptionParser;
import edu.berkeley.nlp.PCFGLA.GrammarTrainer.Options;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees.PennTreeReader;

/**
 * @author petrov
 * 
 */
public class PTBtoDep {

	public static class Options {

		@Option(name = "-in", required = true, usage = "Input File for Trees (Required)")
		public String inFileName;

	}

	public static void main(String[] args) {
		OptionParser optParser = new OptionParser(Options.class);
		Options opts = (Options) optParser.parse(args, true);
		// provide feedback on command-line arguments
		// System.out.println("Calling with " + optParser.getPassedInOptions());

		String fileName = opts.inFileName;
		try {
			PennTreeReader treeReader = new PennTreeReader(
					new InputStreamReader(new FileInputStream(fileName),
							Charset.forName("UTF-8")));// GB18030")));
			while (treeReader.hasNext()) {
				Tree<String> rootedTree = treeReader.next();
				// if (rootedTree.getChildren().size()>1)
				// System.err.println(rootedTree);
				if (rootedTree.getLabel().equals("ROOT"))
					rootedTree = rootedTree.getChildren().get(0);
				printDependencies(rootedTree);
			}

		} catch (Exception ex) {
			ex.printStackTrace();
		}

	}

	/**
	 * @param sentence
	 */
	private static void printDependencies(Tree<String> tree) {
		// System.out.println(tree);
		if (tree.getYield().size() <= 1) {
			System.out.println(0 + "\t_\t_\t_");
			return;
		}
		int thisHead = findHead(tree);
		int nWordsFound = printDependencies(tree, thisHead, 0, 0);
		int nWords = tree.getYield().size();
		while (nWords < nWordsFound) {
			nWordsFound++;
			System.out.println(0 + "\t_\t_\t_");
			System.err.println("too short");
		}
		System.out.println("");
	}

	private static int printDependencies(Tree<String> tree, int parent,
			int previousWords, int parentOfParent) {
		for (Tree<String> child : tree.getChildren()) {
			if (previousWords == parent - 1) { // we are at the parent of this
												// (sub)tree
				System.out.println(parentOfParent + "\t_\t_\t_");
				// (previousWords+1) +
				// "\t" + child.getChildren().get(0).getLabel() +
				// "\t" + child.getLabel()+
				// "\t" + parentOfParent);
				if (child.getYield().size() > 1)
					System.err.println(child);
				previousWords++;
			} else if (child.isPreTerminal()) {
				System.out.println(parent + "\t_\t_\t_");
				// (previousWords+1) +
				// "\t" + child.getChildren().get(0).getLabel() +
				// "\t" + child.getLabel()+
				// "\t" + parent);
				previousWords++;
			} else {
				int thisHead = previousWords + findHead(child);
				printDependencies(child, thisHead, previousWords, parent);
				previousWords += child.getYield().size();
			}
		}
		return previousWords;

	}

	/**
	 * @param tree
	 * @return
	 */
	private static int findHead(Tree<String> tree) {
		String headLabel = tree.getLabel();
		headLabel = headLabel.substring(0, headLabel.length() - 1);// cut off
																	// the *
		int headIndex = -2;
		int previousWords = 0;
		for (Tree<String> child : tree.getChildren()) {
			if (child.isPreTerminal() && child.getLabel().equals(headLabel)) { // found
																				// a
																				// potential
																				// head
				headIndex = previousWords;
				previousWords++;
			} else
				previousWords += child.getYield().size();
		}
		return headIndex + 1; // +1 since indices start with 1
	}

}
