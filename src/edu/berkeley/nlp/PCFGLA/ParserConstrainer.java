package edu.berkeley.nlp.PCFGLA;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import edu.berkeley.nlp.PCFGLA.ConditionalTrainer.Options;
import edu.berkeley.nlp.syntax.SpanTree;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.Numberer;

public class ParserConstrainer implements Callable {

	StateSetTreeList stateSetTrees;

	Grammar grammar;

	Lexicon lexicon;

	SpanPredictor spanPredictor;

	String outBaseName;

	double threshold;

	String consName;

	boolean keepGoldTreeAlive;

	boolean useHierarchicalParser;

	static int treesPerBlock;

	int myID;

	public ParserConstrainer(StateSetTreeList stateSetTrees, Grammar grammar,
			Lexicon lexicon, SpanPredictor spanPredictor, String outBaseName,
			double threshold, boolean keepGoldTreeAlive, int myID, String cons,
			boolean useHierarchicalParser) {

		this.stateSetTrees = stateSetTrees;
		this.grammar = grammar;
		this.lexicon = lexicon;
		this.spanPredictor = spanPredictor;
		this.outBaseName = outBaseName;
		this.threshold = threshold;
		this.consName = cons;
		this.keepGoldTreeAlive = keepGoldTreeAlive;
		this.myID = myID;
		this.useHierarchicalParser = useHierarchicalParser;
	}

	public static void main(String[] args) {
		OptionParser optParser = new OptionParser(
				ConditionalTrainer.Options.class);
		ConditionalTrainer.Options opts = (ConditionalTrainer.Options) optParser
				.parse(args, false);

		// provide feedback on command-line arguments
		System.out.println("Calling Constrainer with "
				+ optParser.getPassedInOptions());

		String path = opts.path;
		// int lang = opts.lang;
		System.out.println("Loading trees from " + path
				+ " and using language " + opts.treebank);
		String testSetString = opts.section;
		boolean devTestSet = testSetString.equals("dev");
		boolean finalTestSet = testSetString.equals("final");
		boolean trainTestSet = testSetString.equals("train");
		System.out.println(" using " + testSetString + " test set");

		Corpus corpus = new Corpus(path, opts.treebank,
				opts.trainingFractionToKeep, !trainTestSet);
		List<Tree<String>> testTrees = null;
		if (devTestSet)
			testTrees = corpus.getDevTestingTrees();
		if (finalTestSet)
			testTrees = corpus.getFinalTestingTrees();
		if (trainTestSet)
			testTrees = corpus.getTrainTrees();

		boolean manualAnnotation = false;
		testTrees = Corpus.binarizeAndFilterTrees(testTrees,
				opts.verticalMarkovization, opts.horizontalMarkovization,
				opts.maxL, opts.binarization, manualAnnotation,
				GrammarTrainer.VERBOSE, opts.markUnaryParents);

		if (!devTestSet && opts.collapseUnaries)
			System.out.println("Collpasing unary chains.");
		testTrees = Corpus.filterTreesForConditional(testTrees,
				opts.filterAllUnaries, opts.filterStupidFrickinWHNP,
				!devTestSet && opts.collapseUnaries);

		boolean keepGoldAlive = opts.keepGoldTreeAlive || trainTestSet;

		String inFileName = opts.inFile;
		System.out.println("Loading grammar from " + inFileName + ".");
		ParserData pData = ParserData.Load(inFileName);
		if (pData == null) {
			System.out.println("Failed to load grammar from file " + inFileName
					+ ".");
			System.exit(1);
		}
		Grammar grammar = pData.getGrammar();
		grammar.splitRules();
		Lexicon lexicon = pData.getLexicon();
		lexicon.explicitlyComputeScores(grammar.finalLevel);
		SpanPredictor spanPredictor = pData.getSpanPredictor();

		if (opts.flattenParameters != 1.0) {
			System.out.println("Flattening parameters with exponent "
					+ opts.flattenParameters + " to reduce overconfidence.");
			grammar.removeUnlikelyRules(0, opts.flattenParameters);
			lexicon.removeUnlikelyTags(0, opts.flattenParameters);
		}

		Numberer.setNumberers(pData.getNumbs());
		Numberer tagNumberer = Numberer.getGlobalNumberer("tags");

		StateSetTreeList stateSetTrees = new StateSetTreeList(testTrees,
				grammar.numSubStates, false, tagNumberer);

		testTrees = null;
		String outBaseName = opts.outFileName;
		double threshold = Math.exp(opts.logT);

		int nChunks = opts.nChunks;
		int nTrees = stateSetTrees.size();
		System.out.println("There are " + nTrees + " trees in this set.");
		treesPerBlock = (int) Math.ceil(nTrees / (double) nChunks);
		System.out.println("Will store " + treesPerBlock
				+ " constraints per file, in " + nChunks + " files.");

		System.out.println("All states with posterior probability below "
				+ threshold + " will be pruned.");
		if (keepGoldAlive)
			System.out.println("But the gold tree will survive!");
		System.out.println("The constraints will be written to " + outBaseName
				+ ".");

		// split the trees into chunks
		StateSetTreeList[] trainingTrees = new StateSetTreeList[nChunks];

		for (int i = 0; i < nChunks; i++) {
			trainingTrees[i] = new StateSetTreeList();
		}
		int block = -1;
		int inBlock = 0;
		for (int i = 0; i < nTrees; i++) {
			if (i % treesPerBlock == 0) {
				block++;
				// System.out.println(inBlock);
				inBlock = 0;
			}
			trainingTrees[block].add(stateSetTrees.get(i));
			inBlock++;
		}
		for (int i = 0; i < nChunks; i++) {
			System.out.println("Process " + i + " has "
					+ trainingTrees[i].size() + " trees.");
		}
		stateSetTrees = null;
		ExecutorService pool = Executors.newFixedThreadPool(nChunks);
		Future[] submits = new Future[nChunks];

		ParserConstrainer thisThreadConstrainer = null;
		if (nChunks == 1)
			thisThreadConstrainer = new ParserConstrainer(trainingTrees[0],
					grammar, lexicon, spanPredictor, outBaseName, threshold,
					keepGoldAlive, 0, opts.cons, Options.hierarchicalChart);
		else {
			for (int i = 0; i < nChunks; i++) {
				ParserConstrainer constrainer = new ParserConstrainer(
						trainingTrees[i], grammar, lexicon, spanPredictor,
						outBaseName, threshold, keepGoldAlive, i, opts.cons,
						Options.hierarchicalChart);
				submits[i] = pool.submit(constrainer);
			}

			while (true) {
				boolean done = true;
				for (Future task : submits) {
					done &= task.isDone();
				}
				if (done)
					break;
			}
			// pool.shutdown();
		}
		try {
			PrintWriter outputData = (opts.outputLog == null) ? new PrintWriter(
					new OutputStreamWriter(System.out)) : new PrintWriter(
					new OutputStreamWriter(
							new FileOutputStream(opts.outputLog), "UTF-8"),
					true);

			for (int i = 0; i < nChunks; i++) {
				StringBuilder sb = null;
				if (nChunks == 1) {
					sb = thisThreadConstrainer.call();
				} else {
					sb = (StringBuilder) submits[i].get();
				}
				outputData.print(sb.toString());
			}

			if (opts.outputLog != null) {
				outputData.flush();
				outputData.close();
			}
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		System.out.println("Done computing constraints.");
	}

	/**
	 * 
	 * @param opts
	 */

	public StringBuilder call() {
		ConstrainedTwoChartsParser parser = (grammar instanceof HierarchicalAdaptiveGrammar) ? new ConstrainedHierarchicalTwoChartParser(
				grammar, lexicon, spanPredictor, grammar.finalLevel)
				: new ConstrainedTwoChartsParser(grammar, lexicon,
						spanPredictor);

		StringBuilder sb = new StringBuilder();
		int recentHistoryIndex = 0;
		// int sentenceNumber = 1;

		boolean[][][][][] recentHistory = new boolean[treesPerBlock][][][][];
		boolean[][][][][] myConstraints = null;
		boolean useCons = consName != null;

		if (useCons)
			myConstraints = loadData(consName + "-" + myID + ".data");
		boolean[][][][] cons = null;

		for (Tree<StateSet> testTree : stateSetTrees) {
			List<StateSet> yield = testTree.getYield();
			List<String> testSentence = new ArrayList<String>(yield.size());

			for (StateSet el : yield) {
				testSentence.add(el.getWord());
			}
			sb.append("\n" + (myID * treesPerBlock + recentHistoryIndex + 1)
					+ ". Length " + testSentence.size());

			if (useCons) {
				parser.projectConstraints(myConstraints[recentHistoryIndex],
						false);
				cons = myConstraints[recentHistoryIndex];
			}

			Tree<StateSet> sTree = null;
			if (keepGoldTreeAlive) {
				// System.out.println("keeping gold tree alive");
				sTree = testTree;
			}
			boolean[][][][] possibleStates = parser.getPossibleStates(
					testSentence, sTree, threshold, cons, sb);
			assert sTree == null || contains(possibleStates, sTree);

			if (useCons)
				myConstraints[recentHistoryIndex] = null;
			recentHistory[recentHistoryIndex++] = possibleStates;

			if (recentHistoryIndex % 1000 == 0)
				System.out.print(".");
			// sentenceNumber++;
			// if (recentHistoryIndex>0 && (recentHistoryIndex % treesPerBlock
			// == 0))
			// {
			// String fileName = outBaseName+"-"+blockIndex+".data";
			// saveData(recentHistory, fileName);
			// blockIndex++;
			// if (useCons && sentenceNumber<nTrees) myConstraints =
			// loadData(consName+"-"+blockIndex+".data");
			// recentHistory = new boolean[treesPerBlock][][][][];
			// recentHistoryIndex = 0;
			// }
		}

		// if (recentHistoryIndex!=0) {
		String fileName = outBaseName + "-" + myID + ".data";
		saveData(recentHistory, fileName);
		// }

		return sb;
	}

	/**
	 * @param possibleStates
	 * @param tree
	 * @return
	 */
	private boolean contains(boolean[][][][] possibleStates, Tree<StateSet> tree) {
		boolean[] bs = possibleStates[tree.getLabel().from][tree.getLabel().to][tree
				.getLabel().getState()];

		if (tree.isLeaf())
			return true;
		if (bs == null) {
			assert false;
			return false;
		}
		boolean hasTrue = false;
		for (boolean b : bs)
			hasTrue |= b;
		if (!hasTrue) {
			assert false;
			return false;
		}
		boolean allThere = true;
		for (Tree<StateSet> child : tree.getChildren()) {
			allThere &= contains(possibleStates, child);
		}
		return allThere;
	}

	public static boolean saveData(boolean[][][][][] data, String fileName) {
		try {
			// here's some code from online; it looks good and gzips the output!
			// there's a whole explanation at
			// http://www.ecst.csuchico.edu/~amk/foo/advjava/notes/serial.html
			// Create the necessary output streams to save the scribble.
			FileOutputStream fos = new FileOutputStream(fileName); // Save to
																	// file
			GZIPOutputStream gzos = new GZIPOutputStream(fos); // Compressed
			ObjectOutputStream out = new ObjectOutputStream(gzos); // Save
																	// objects
			out.writeObject(data); // Write the mix of grammars
			out.flush(); // Always flush the output.
			out.close(); // And close the stream.
			gzos.close();
			fos.close();
		} catch (IOException e) {
			System.out.println("IOException: " + e);
			return false;
		}
		return true;
	}

	public static boolean isGoldReachable(SpanTree<String> gold,
			List[][] possibleStates, Numberer tagNumberer) {

		boolean reachable = true;

		reachable = possibleStates[gold.getStart()][gold.getEnd()]
				.contains(tagNumberer.number(gold.getLabel()));

		if (reachable && (!gold.isLeaf())) {

			for (SpanTree<String> child : gold.getChildren()) {

				reachable = isGoldReachable(child, possibleStates, tagNumberer);

				if (!reachable)
					return false;

			}

		}

		if (!reachable) {

			System.out.println("Cannot reach state " + gold.getLabel()
					+ " spanning from " + gold.getStart() + " to "
					+ gold.getEnd() + ".");

		}

		return reachable;

	}

	public static SpanTree<String> convertToSpanTree(Tree<String> tree) {

		if (tree.isPreTerminal()) {

			return new SpanTree<String>(tree.getLabel());

		}

		if (tree.getChildren().size() > 2)
			System.out.println("Binarize properly first!");

		SpanTree<String> spanTree = new SpanTree<String>(tree.getLabel());

		List<SpanTree<String>> spanChildren = new ArrayList<SpanTree<String>>();
		for (Tree<String> child : tree.getChildren()) {

			SpanTree<String> spanChild = convertToSpanTree(child);

			spanChildren.add(spanChild);

		}

		spanTree.setChildren(spanChildren);

		return spanTree;

	}

	public static boolean[][][][][] loadData(String fileName) {
		boolean[][][][][] data = null;
		try {
			FileInputStream fis = new FileInputStream(fileName); // Load from
																	// file
			GZIPInputStream gzis = new GZIPInputStream(fis); // Compressed
			ObjectInputStream in = new ObjectInputStream(gzis); // Load objects
			data = (boolean[][][][][]) in.readObject(); // Read the mix of
														// grammars
			in.close(); // And close the stream.
			gzis.close();
			fis.close();
		} catch (IOException e) {
			System.out.println("IOException\n" + e);
			return null;
		} catch (ClassNotFoundException e) {
			System.out.println("Class not found!");
			return null;
		}
		return data;
	}

}
