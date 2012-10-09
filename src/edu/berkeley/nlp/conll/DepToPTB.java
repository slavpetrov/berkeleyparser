/**
 * 
 */
package edu.berkeley.nlp.conll;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import edu.berkeley.nlp.PCFGLA.Option;
import edu.berkeley.nlp.PCFGLA.OptionParser;
import edu.berkeley.nlp.syntax.Tree;

/**
 * @author petrov
 * 
 */
public class DepToPTB {

	public static class Options {

		@Option(name = "-in", required = true, usage = "Input File for Grammar (Required)")
		public String inFileName;

		@Option(name = "-finePOStags", usage = "Use fine POS tags (Default: false=coarse")
		public boolean useFinePOS = false;

	}

	public static void main(String[] args) {
		// String[] sentence = {
		// "1 The _ DT DT _ 4 NMOD _ _\n",
		// "2 luxury _ NN NN _ 4 NMOD _ _\n",
		// "3 auto _ NN NN _ 4 NMOD _ _\n",
		// "4 maker _ NN NN _ 7 SBJ _ _\n",
		// "5 last _ JJ JJ _ 6 NMOD _ _\n",
		// "6 year _ NN NN _ 7 VMOD _ _\n",
		// "7 sold _ VB VBD _ 0 ROOT _ _\n",
		// "8 1,214 _ CD CD _ 9 NMOD _ _\n",
		// "9 cars _ NN NNS _ 7 OBJ _ _\n",
		// "10 in _ IN IN _ 7 ADV _ _\n",
		// "11 the _ DT DT _ 12 NMOD _ _\n",
		// "12 U.S. _ NN NNP _ 10 PMOD _ _\n"};
		OptionParser optParser = new OptionParser(Options.class);
		Options opts = (Options) optParser.parse(args, true);
		// provide feedback on command-line arguments
		// System.out.println("Calling with " + optParser.getPassedInOptions());

		BufferedReader input = null;
		String fileName = opts.inFileName;
		try {
			input = new BufferedReader(new InputStreamReader(
					new FileInputStream(fileName), Charset.forName("UTF-8")));// GB18030")));
			String line = "";
			List<String> sentence = new ArrayList<String>();
			while ((line = input.readLine()) != null) {
				System.out.println(line);
				if (line.equals("")) {
					Tree<String> tree = turnIntoTree(sentence, opts.useFinePOS);
					System.out.println("( " + tree + ")");
					sentence = new LinkedList<String>();
				} else
					sentence.add(line);
			}

		} catch (Exception ex) {
			ex.printStackTrace();
		}

	}

	/**
	 * @param sentence
	 * @return
	 */
	private static Tree<String> turnIntoTree(List<String> sentence,
			boolean useFinePOS) {
		int posIndex = (useFinePOS) ? 4 : 3;
		int nWords = sentence.size();
		Tree[] trees = new Tree[nWords];
		List<Integer>[] childIndices = new List[nWords];
		int[] freeKids = new int[nWords];
		int[] parentIndices = new int[nWords];
		int rootIndex = -1;

		for (int i = 0; i < nWords; i++) {
			childIndices[i] = new LinkedList<Integer>();
		}

		for (int i = 0; i < nWords; i++) {
			String[] fields = sentence.get(i).split("\t");
			String word = fields[1];
			if (word.equals("(") || word.equals(")"))
				word = "LRB";
			Tree<String> child = new Tree<String>(word);
			List<Tree<String>> childList = new ArrayList<Tree<String>>(1);
			childList.add(child);
			String tag = fields[posIndex];
			if (tag.equals("(") || tag.equals(")"))
				tag = "LRB";
			trees[i] = new Tree<String>(tag, childList);
			int pIndex = Integer.parseInt(fields[6]) - 1;
			parentIndices[i] = pIndex;
			if (pIndex == -1)
				rootIndex = i;
			else
				childIndices[pIndex].add(i);
			childIndices[i].add(i);
		}

		if (nWords == 1)
			return trees[0];

		for (int i = 0; i < nWords; i++) {
			for (Integer c : childIndices[i]) {
				if (childIndices[c].size() == 1)
					freeKids[i]++;
			}
			freeKids[i]++; // because each tree is also its own child
		}

		while (childIndices[rootIndex].size() > 0) {
			for (int i = 0; i < nWords; i++) {
				if (childIndices[i].size() <= 1)
					continue;
				if (freeKids[i] == childIndices[i].size()) { // all its children
																// are free ->
																// attach them
					List<Tree<String>> childList = new ArrayList<Tree<String>>();
					for (Integer c : childIndices[i]) {
						childList.add(trees[c]);
					}
					Tree<String> newTree = new Tree<String>(trees[i].getLabel()
							+ "*", childList);
					trees[i] = newTree;
					if (parentIndices[i] >= 0)
						freeKids[parentIndices[i]]++;
					childIndices[i] = new LinkedList();
				}
			}
		}
		return trees[rootIndex];
	}

}
