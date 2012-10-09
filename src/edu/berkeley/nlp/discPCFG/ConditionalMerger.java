/**
 * 
 */
package edu.berkeley.nlp.discPCFG;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.GZIPInputStream;

import edu.berkeley.nlp.PCFGLA.ArrayParser;
import edu.berkeley.nlp.PCFGLA.Binarization;
import edu.berkeley.nlp.PCFGLA.ConstrainedTwoChartsParser;
import edu.berkeley.nlp.PCFGLA.Grammar;
import edu.berkeley.nlp.PCFGLA.GrammarMerger;
import edu.berkeley.nlp.PCFGLA.Lexicon;
import edu.berkeley.nlp.PCFGLA.ParserData;
import edu.berkeley.nlp.PCFGLA.StateSetTreeList;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.Numberer;

/**
 * @author petrov
 * 
 */
public class ConditionalMerger {
	int nProcesses;
	String consBaseName;
	Grammar grammar;
	Lexicon lexicon;
	double mergingPercentage;
	String outFileName;

	StateSetTreeList[] trainingTrees;
	ExecutorService pool;
	Merger[] tasks;
	double[][] mergeWeights;

	class Merger implements Callable {
		ArrayParser gParser;
		ConstrainedTwoChartsParser eParser;
		StateSetTreeList myTrees;
		String consName;
		int myID;
		int nCounts;
		boolean[][][][][] myConstraints;
		int unparsableTrees, incorrectLLTrees;
		double[][] mergeWeights;

		Merger(StateSetTreeList myT, String consN, int i, Grammar gr,
				Lexicon lex, double[][] mergeWeights) {
			this.consName = consN;
			this.myTrees = myT;
			this.myID = i;
			this.mergeWeights = mergeWeights;
			gParser = new ArrayParser(gr, lex);
			eParser = new ConstrainedTwoChartsParser(gr, lex, null);
		}

		private void loadConstraints() {
			myConstraints = new boolean[myTrees.size()][][][][];
			boolean[][][][][] curBlock = null;
			int block = 0;
			int i = 0;
			if (consName == null)
				return;
			for (int tree = 0; tree < myTrees.size(); tree++) {
				if (curBlock == null || i >= curBlock.length) {
					int blockNumber = ((block * nProcesses) + myID);
					curBlock = loadData(consName + "-" + blockNumber + ".data");
					block++;
					i = 0;
					System.out.print(".");
				}
				eParser.projectConstraints(curBlock[i], false);
				myConstraints[tree] = curBlock[i];
				i++;
				if (myConstraints[tree].length != myTrees.get(tree).getYield()
						.size()) {
					System.out.println("My ID: " + myID + ", block: " + block
							+ ", sentence: " + i);
					System.out
							.println("Sentence length and constraints length do not match!");
					myConstraints[tree] = null;
				}
			}

		}

		public double[][][] call() {
			if (myConstraints == null)
				loadConstraints();
			double[][][] deltas = new double[grammar.numStates][mergeWeights[0].length][mergeWeights[0].length];
			int i = -1;
			int block = 0;
			for (Tree<StateSet> stateSetTree : myTrees) {
				i++;
				boolean noSmoothing = true, debugOutput = false, hardCounts = false;
				gParser.doInsideOutsideScores(stateSetTree, noSmoothing,
						debugOutput);

				// parse the sentence
				List<StateSet> yield = stateSetTree.getYield();
				List<String> sentence = new ArrayList<String>(yield.size());
				for (StateSet el : yield) {
					sentence.add(el.getWord());
				}
				boolean[][][][] cons = null;
				if (consName != null) {
					cons = myConstraints[i];
					if (cons.length != sentence.size()) {
						System.out.println("My ID: " + myID + ", block: "
								+ block + ", sentence: " + i);
						System.out.println("Sentence length ("
								+ sentence.size()
								+ ") and constraints length (" + cons.length
								+ ") do not match!");
						System.exit(-1);
					}
				}
				eParser.doConstrainedInsideOutsideScores(yield, cons,
						noSmoothing, stateSetTree, null, false);

				eParser.tallyConditionalLoss(stateSetTree, deltas, mergeWeights);

				if (i % 100 == 0)
					System.out.print(".");
			}

			System.out.print(" " + myID + " ");
			return deltas;
		}

		public boolean[][][][][] loadData(String fileName) {
			boolean[][][][][] data = null;
			try {
				FileInputStream fis = new FileInputStream(fileName); // Load
																		// from
																		// file
				GZIPInputStream gzis = new GZIPInputStream(fis); // Compressed
				ObjectInputStream in = new ObjectInputStream(gzis); // Load
																	// objects
				data = (boolean[][][][][]) in.readObject(); // Read the mix of
															// grammars
				in.close(); // And close the stream.
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

	/**
	 * @param processes
	 * @param consBaseName
	 * @param trainingTrees
	 */
	public ConditionalMerger(int processes, String consBaseName,
			StateSetTreeList trainTrees, Grammar gr, Lexicon lex,
			double mergingPercentage, String outFileName) {
		this.nProcesses = processes;
		this.consBaseName = consBaseName;
		this.grammar = gr;// .copyGrammar();
		this.lexicon = lex;// .copyLexicon();
		this.mergingPercentage = mergingPercentage;
		this.outFileName = outFileName;

		int nTreesPerBlock = trainTrees.size() / processes;
		this.consBaseName = consBaseName;
		boolean[][][][][] tmp = edu.berkeley.nlp.PCFGLA.ParserConstrainer
				.loadData(consBaseName + "-0.data");
		if (tmp != null)
			nTreesPerBlock = tmp.length;

		// first compute the generative merging criterion
		mergeWeights = GrammarMerger.computeMergeWeights(grammar, lexicon,
				trainTrees);
		double[][][] deltas = GrammarMerger.computeDeltas(grammar, lexicon,
				mergeWeights, trainTrees);
		boolean[][][] mergeThesePairs = GrammarMerger.determineMergePairs(
				deltas, false, mergingPercentage, grammar);
		Grammar tmpGrammar = grammar.copyGrammar(true);
		Lexicon tmpLexicon = lexicon.copyLexicon();
		tmpGrammar = GrammarMerger.doTheMerges(tmpGrammar, tmpLexicon,
				mergeThesePairs, mergeWeights);
		System.out.println("Generative merging criterion gives:");
		GrammarMerger.printMergingStatistics(grammar, tmpGrammar);
		mergeWeights = GrammarMerger.computeMergeWeights(grammar, lexicon,
				trainTrees);

		// split the trees into chunks
		trainingTrees = new StateSetTreeList[nProcesses];
		for (int i = 0; i < nProcesses; i++) {
			trainingTrees[i] = new StateSetTreeList();
		}
		int block = -1;
		int inBlock = 0;
		for (int i = 0; i < trainTrees.size(); i++) {
			if (i % nTreesPerBlock == 0) {
				block++;
				System.out.println(inBlock);
				inBlock = 0;
			}
			trainingTrees[block % nProcesses].add(trainTrees.get(i));
			inBlock++;
		}
		trainTrees = null;
		pool = Executors.newFixedThreadPool(nProcesses);// CachedThreadPool();

		tasks = new Merger[nProcesses];
		for (int i = 0; i < nProcesses; i++) {
			tasks[i] = new Merger(trainingTrees[i], consBaseName, i, grammar,
					lexicon, mergeWeights);
		}

	}

	public void mergeGrammarAndLexicon() {
		System.out.print("Task: ");
		Future[] submits = new Future[nProcesses];
		for (int i = 0; i < nProcesses; i++) {
			Future submit = pool.submit(tasks[i]);// execute(tasks[i]);
			submits[i] = submit;
		}

		while (true) {
			boolean done = true;
			for (Future task : submits) {
				done &= task.isDone();
			}
			if (done)
				break;
		}

		// accumulate
		double[][][] deltas = new double[grammar.numStates][mergeWeights[0].length][mergeWeights[0].length];
		for (int i = 0; i < nProcesses; i++) {
			double[][][] counts = null;
			try {
				counts = (double[][][]) submits[i].get();
			} catch (ExecutionException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			for (int a = 0; a < deltas.length; a++) {
				for (int b = 0; b < deltas[0].length; b++) {
					for (int c = 0; c < deltas[0][0].length; c++) {
						deltas[a][b][c] += counts[a][b][c];
					}
				}
			}
		}
		System.out.print(" done. ");
		System.out.println("Conditional merging criterion gives:");
		boolean[][][] mergeThesePairs = GrammarMerger.determineMergePairs(
				deltas, false, mergingPercentage, grammar);
		Grammar newGrammar = GrammarMerger.doTheMerges(grammar, lexicon,
				mergeThesePairs, mergeWeights);
		GrammarMerger.printMergingStatistics(grammar, newGrammar);

		ParserData pData = new ParserData(lexicon, newGrammar, null,
				Numberer.getNumberers(), newGrammar.numSubStates, 1, 0,
				Binarization.RIGHT);
		System.out.println("Saving grammar to " + outFileName + ".");
		if (pData.Save(outFileName + "-merged"))
			System.out.println("Saving successful.");
		else
			System.out.println("Saving failed!");

	}

}
