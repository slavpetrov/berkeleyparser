package edu.berkeley.nlp.PCFGLA;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import edu.berkeley.nlp.PCFGLA.TreeLabeler.Options;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees.PennTreeReader;

public class TreeDrawer {

	@Option(name = "-inputFile", usage = "Read input from this file instead of reading it from STDIN.")
	public String inputFile;

	public static void main(String[] args) throws IOException {
		OptionParser optParser = new OptionParser(Options.class);
		Options opts = (Options) optParser.parse(args, true);
		// provide feedback on command-line arguments
		System.err.println("Calling with " + optParser.getPassedInOptions());

		BufferedReader inputData = new BufferedReader(new InputStreamReader(new FileInputStream(opts.inputFile), "UTF-8"));
    	String line = "";
		Tree<String> tree = null;
		int i = 0;
    	while((line=inputData.readLine()) != null){
    		tree = PennTreeReader.parseEasy(line);
    		String fileName = i++ + ".png";
    		BerkeleyParser.writeTreeToImage(tree, fileName);
    	}
	}

}
