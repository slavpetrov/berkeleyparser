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
import java.util.ArrayList;
import java.util.List;

import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees;
import edu.berkeley.nlp.syntax.Trees.PennTreeReader;
import edu.berkeley.nlp.syntax.Trees.PennTreeRenderer;
import edu.berkeley.nlp.util.Numberer;

/**
 * @author petrov
 *
 */
public class TreeLabeler {

	public static class Options {

		@Option(name = "-gr", usage = "Input File for Grammar (Required)\n")
		public String inFileName;

		@Option(name = "-labelLevel", usage = "Parse with projected grammar from this level (yielding 2^level substates) (Default: -1 = input grammar)")
		public int labelLevel = -1;

		@Option(name = "-scores", usage = "Output inside scores. (Default: false)")
		public boolean scores;

		@Option(name = "-getYield", usage = "Get the sentences only")
		public boolean getyield;

		@Option(name = "-labelOnlyPOS", usage = "Labels only the POS categories")
		public boolean labelOnlyPOS;

		@Option(name = "-onlyConfidence", usage = "Output only confidence measure, i.e. tree likelihood: P(T|w) (Default: false)")
		public boolean onlyConfidence;

		@Option(name = "-maxLength", usage = "Remove sentences that are longer than this (doesn't print an empty line)")
		public int maxLength = 1000;		

		@Option(name = "-inputFile", usage = "Read input from this file instead of reading it from STDIN.")
		public String inputFile;

		@Option(name = "-outputFile", usage = "Store output in this file instead of printing it to STDOUT.")
		public String outputFile;

		@Option(name = "-prettyPrint", usage = "Print in human readable form rather than one tree per line")
		public boolean prettyPrint;

		@Option(name = "-getPOSandYield", usage = "Get POS and words in CoNLL format")
		public boolean getPOSandYield;

		@Option(name = "-annotateTrees", usage = "Binarize and annotate trees")
		public boolean annotateTrees;

		@Option(name = "-escapeChars", usage = "Escape parantheses and backslashes")
		public boolean escapeChars;

		@Option(name = "-keepFunctionLabels", usage = "Retain function labels")
		public boolean keepFunctionLabels;

		@Option(name = "-horizontalMarkovization", usage = "Level of horizontal Markovization (Default: 0, i.e. no sibling information)")
		public int h_markov = 0;    

		@Option(name = "-verticalMarkovization", usage = "Level of vertical Markovization (Default: 1, i.e. no parent information)")
		public int v_markov = 1;    

		@Option(name = "-b", usage = "LEFT/RIGHT Binarization (Default: RIGHT)")
		public Binarization binarization = Binarization.RIGHT;

	}


	/**
	 * @param grammar
	 * @param lexicon
	 * @param labelLevel
	 */
	Grammar grammar;
	SophisticatedLexicon lexicon;
	ArrayParser labeler;
	CoarseToFineMaxRuleParser parser;
	Numberer tagNumberer;
	Binarization binarization;

	public TreeLabeler(Grammar grammar, SophisticatedLexicon lexicon, int labelLevel, Binarization bin) {
		if (labelLevel==-1){
			this.grammar = grammar.copyGrammar(false);
			this.lexicon = lexicon.copyLexicon();
		} else { // need to project
			int[][] fromMapping = grammar.computeMapping(1);
			int[][] toSubstateMapping = grammar.computeSubstateMapping(labelLevel);
			int[][] toMapping = grammar.computeToMapping(labelLevel,toSubstateMapping);
			double[] condProbs = grammar.computeConditionalProbabilities(fromMapping,toMapping);

			this.grammar = grammar.projectGrammar(condProbs,fromMapping,toSubstateMapping);
			this.lexicon = lexicon.projectLexicon(condProbs,fromMapping,toSubstateMapping);
			this.grammar.splitRules();
			double filter = 1.0e-10;
			this.grammar.removeUnlikelyRules(filter,1.0);
			this.lexicon.removeUnlikelyTags(filter,1.0);
		}
		this.grammar.logarithmMode();
		this.lexicon.logarithmMode();
		this.labeler = new ArrayParser(this.grammar, this.lexicon);
		this.parser = new CoarseToFineMaxRuleParser(grammar, lexicon, 
				1,-1,true,false, false, false, false, false, true);      
		this.tagNumberer = Numberer.getGlobalNumberer("tags");
		this.binarization = bin;
	}


	public static void main(String[] args) {
		OptionParser optParser = new OptionParser(Options.class);
		Options opts = (Options) optParser.parse(args, true);
		// provide feedback on command-line arguments
		System.err.println("Calling with " + optParser.getPassedInOptions());

		String inFileName = opts.inFileName;
		Grammar grammar = null;
		SophisticatedLexicon lexicon = null;
		TreeLabeler treeLabeler = null;
		boolean labelTree = false;
		ParserData pData = null;
		short[] numSubstates = null;
		if (inFileName==null) {
			System.err.println("Did not provide a grammar.");
		}
		else {
			labelTree = true;
			System.err.println("Loading grammar from "+inFileName+".");

			pData = ParserData.Load(inFileName);
			if (pData==null) {
				System.out.println("Failed to load grammar from file"+inFileName+".");
				System.exit(1);
			}
			grammar = pData.getGrammar();
			grammar.splitRules();
			lexicon = (SophisticatedLexicon)pData.getLexicon();

			Numberer.setNumberers(pData.getNumbs());

			int labelLevel = opts.labelLevel;
			if (labelLevel!=-1) System.err.println("Labeling with projected grammar from level "+labelLevel+".");
			treeLabeler = new TreeLabeler(grammar, lexicon, labelLevel, pData.bin);
			numSubstates = treeLabeler.grammar.numSubStates;

		}
		Numberer tagNumberer =  Numberer.getGlobalNumberer("tags");

		Trees.TreeTransformer<String> treeTransformer = (opts.keepFunctionLabels) ? new Trees.FunctionLabelRetainingTreeNormalizer() : new Trees.StandardTreeNormalizer();
		try{
	    	BufferedReader inputData = (opts.inputFile==null) ? new BufferedReader(new InputStreamReader(System.in)) : new BufferedReader(new InputStreamReader(new FileInputStream(opts.inputFile), "UTF-8"));
			PrintWriter outputData = (opts.outputFile==null) ? new PrintWriter(new OutputStreamWriter(System.out)) : new PrintWriter(new OutputStreamWriter(new FileOutputStream(opts.outputFile), "UTF-8"), true);

			Tree<String> tree = null;
	    	String line = "";
	    	while((line=inputData.readLine()) != null){
	    		if (line.equals("")) {
	    			outputData.write("\n");
	    			continue;
	    		}
	    		if (line.equals("(())")) {
	    			outputData.write("(())\n");
	    			continue;
	    		}
	    		tree = PennTreeReader.parseEasy(line);
	    		if (tree==null) continue;
	    		if (tree.getYield().get(0).equals("")){ // empty tree -> parse failure
	    			outputData.write("(())\n");
	    			continue;
	    		}
				if (tree.getChildren().size() == 0 || tree.getChildren().get(0).getLabel().equals("(") || tree.getYield().get(0).equals("")){ // empty tree -> parse failure
					outputData.write("(())\n");
					continue;
				}
				if (tree.getYield().size() > opts.maxLength) continue;

				if (!labelTree){
					if (opts.getyield){
						List<String> words = tree.getYield();
						for (String word : words){
							outputData.write(word+" ");
						}
						outputData.write("\n");
					} else if (opts.annotateTrees) {
						tree = TreeAnnotations.processTree(tree, opts.v_markov, opts.h_markov, opts.binarization, false);
						outputData.write(tree+"\n");
					}
					else {
						Tree<String> normalizedTree = treeTransformer.transformTree(tree);
						if (opts.getPOSandYield){
							List<Tree<String>> leafs = normalizedTree.getPreTerminals();
							for (Tree<String> leaf : leafs){
								outputData.write(leaf.getChild(0).getLabel()+"\t"+leaf.getLabel()+"\n");
							}
							outputData.write("\n");
						}
						else {
							if (opts.escapeChars) {
								outputData.write(normalizedTree.toEscapedString()+"\n");
							} else {
								outputData.write(normalizedTree+"\n");
							}
						}
					}
					continue;
				}


				tree = TreeAnnotations.processTree(tree,pData.v_markov, pData.h_markov,pData.bin,false);
				List<String> sentence = tree.getYield();
				Tree<StateSet> stateSetTree = StateSetTreeList.stringTreeToStatesetTree(tree, numSubstates, false, tagNumberer);
				allocate(stateSetTree);
				Tree<String> labeledTree = treeLabeler.label(stateSetTree, sentence, opts.scores, opts.labelOnlyPOS);
				if (opts.onlyConfidence) {
					double treeLL = stateSetTree.getLabel().getIScore(0);
					outputData.write(treeLL+"\n");
					outputData.flush();
					continue;
				}

				if (labeledTree!=null && labeledTree.getChildren().size()>0) {
					if (opts.prettyPrint){
						outputData.write(PennTreeRenderer.render(labeledTree)+"\n");
					} else {
						if (opts.labelOnlyPOS){
							labeledTree = TreeAnnotations.debinarizeTree(labeledTree);
						}
						if (opts.escapeChars) {
							outputData.write("( "+labeledTree.getChildren().get(0).toEscapedString()+")\n");
						} else {
							outputData.write("( "+labeledTree.getChildren().get(0)+")\n");
						}

					}
				}
				else {
					if (opts.labelOnlyPOS){
						List<Tree<String>> pos = tree.getPreTerminals();
						tree = TreeAnnotations.unAnnotateTree(tree, opts.keepFunctionLabels);
						for (Tree<String> tag : pos){
							String t = tag.getLabel();
							t = t + "-0";
							tag.setLabel(t);
						}
						if (opts.escapeChars) {
							outputData.write("( "+tree.getChildren().get(0).toEscapedString()+")\n");
						} else {
							outputData.write("( "+tree.getChildren().get(0)+")\n");
						}
					} else {
						outputData.write("(())\n");
					}
				}
				outputData.flush();
			}
			outputData.close();
		}catch (Exception ex) {
			ex.printStackTrace();
		}
		System.exit(0);
	}



	/**
	 * @param stateSetTree
	 * @return
	 */
	private Tree<String> label(Tree<StateSet> stateSetTree, List<String> sentence, boolean outputScores, boolean labelOnlyPOS) {
		Tree<String> tree = labeler.getBestViterbiDerivation(stateSetTree,outputScores, labelOnlyPOS);
		//		if (tree==null){ // max-rule tree had no viterbi derivation
		//			tree = parser.getBestConstrainedParse(sentence, null);
		//			tree = TreeAnnotations.processTree(tree,1, 0, binarization,false);
		////			System.out.println(tree);
		//			stateSetTree = StateSetTreeList.stringTreeToStatesetTree(tree, this.grammar.numSubStates, false, tagNumberer);
		//			allocate(stateSetTree);
		//			tree = labeler.getBestViterbiDerivation(stateSetTree,outputScores);
		//		}
		return tree;
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
