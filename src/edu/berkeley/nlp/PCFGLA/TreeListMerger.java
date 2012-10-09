///**
// * 
// */
//package edu.berkeley.nlp.PCFGLA;
//
//import java.io.FileInputStream;
//import java.io.FileOutputStream;
//import java.io.InputStreamReader;
//import java.io.OutputStreamWriter;
//import java.io.PrintWriter;
//import java.util.LinkedList;
//
//import edu.berkeley.nlp.PCFGLA.TreeReranker.Options;
//import edu.berkeley.nlp.syntax.Tree;
//import edu.berkeley.nlp.syntax.Trees.PennTreeReader;
//
///**
// * @author petrov
// *
// */
//public class TreeListMerger {
//	@Option(name = "-inputFile", usage = "Input File for Parse Trees.")
//	public String inputFile;
//
//	@Option(name = "-outputFile", usage = "Output File for Merged List")
//	public String outputFile;
//	
//	@Option(name = "-nGrammars", usage = "Number of grammars")
//	public int nGrammars;
//
//	public static void main(String[] args) {
//		OptionParser optParser = new OptionParser(Options.class);
//		Options opts = (Options) optParser.parse(args, true);
//		// provide feedback on command-line arguments
//		System.err.println("Calling with " + optParser.getPassedInOptions());
//
//    try {
//    	PennTreeReader[] treeReaders = new PennTreeReader[opts.nGrammars];
//    	PrintWriter outputData = (opts.outputFile==null) ? new PrintWriter(new OutputStreamWriter(System.out)) : new PrintWriter(new OutputStreamWriter(new FileOutputStream(opts.outputFile), "UTF-8"), true);
//    	
//  		for (int i=0; i<opts.nGrammars; i++){
//	    	InputStreamReader inputData = (opts.inputFile==null) ? new InputStreamReader(System.in) : new InputStreamReader(new FileInputStream(opts.inputFile), "UTF-8");
//	    	treeReaders[i] = new PennTreeReader(inputData);
//  		}
//  		
//    	Tree<String> tree = null;
//    	while(treeReaders[opts.nGrammars-1].hasNext()){
//    		LinkedList<Tree<String>> uniqueList;
//    		for (int i=0; i<opts.nGrammars; i++){
//    			tree = treeReaders[i].next(); 
//	    		if (tree.getYield().get(0).equals("")){ // empty tree -> parse failure
//	    			outputData.write("()\n");
//	    			continue;
//	    		}
//    		}
//      }
//    } catch (Exception ex) {
//      ex.printStackTrace();
//    }
//    System.exit(0);
//	}
// }
