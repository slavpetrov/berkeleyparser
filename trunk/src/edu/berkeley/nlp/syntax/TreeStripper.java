package edu.berkeley.nlp.syntax;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;

import edu.berkeley.nlp.util.Iterators;
import edu.berkeley.nlp.util.StrUtils;

public class TreeStripper {

	public static void main(String argv[]) throws FileNotFoundException {
		BufferedReader inputData = (argv.length == 0) ? new BufferedReader(
				new InputStreamReader(System.in)) : new BufferedReader(
				new InputStreamReader(new FileInputStream(argv[0])));
		for (Tree<String> tree : Iterators.able(new Trees.PennTreeReader(
				inputData))) {
			tree = new Trees.EmptyNodeStripper().transformTree(tree);
			System.out.println(StrUtils.join(tree.getYield()));
		}
	}

}
