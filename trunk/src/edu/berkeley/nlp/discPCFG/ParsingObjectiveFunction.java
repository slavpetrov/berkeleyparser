/**
 * 
 */
package edu.berkeley.nlp.discPCFG;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.GZIPInputStream;

import edu.berkeley.nlp.PCFGLA.ArrayParser;
import edu.berkeley.nlp.PCFGLA.Binarization;
import edu.berkeley.nlp.PCFGLA.ConditionalTrainer;
import edu.berkeley.nlp.PCFGLA.ConstrainedHierarchicalTwoChartParser;
import edu.berkeley.nlp.PCFGLA.ConstrainedTwoChartsParser;
import edu.berkeley.nlp.PCFGLA.Grammar;
import edu.berkeley.nlp.PCFGLA.Lexicon;
import edu.berkeley.nlp.PCFGLA.ParserData;
import edu.berkeley.nlp.PCFGLA.SimpleLexicon;
import edu.berkeley.nlp.PCFGLA.SpanPredictor;
import edu.berkeley.nlp.PCFGLA.StateSetTreeList;
import edu.berkeley.nlp.math.SloppyMath;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.Numberer;

/**
 * @author petrov
 * 
 */
public class ParsingObjectiveFunction implements ObjectiveFunction {

	public static final int NO_REGULARIZATION = 0;
	public static final int L1_REGULARIZATION = 1;
	public static final int L2_REGULARIZATION = 2;

	Grammar grammar;
	SimpleLexicon lexicon;
	SpanPredictor spanPredictor;
	Linearizer linearizer;

	int myRegularization;
	double sigma;

	double lastValue;
	double[] lastDerivative;
	double[] lastUnregularizedDerivative;
	double[] x;

	int dimension;

	int nGrammarWeights, nLexiconWeights, nSpanWeights;

	int nProcesses;
	String consBaseName;

	StateSetTreeList[] trainingTrees;
	ExecutorService pool;
	Calculator[] tasks;
	double bestObjectiveSoFar;
	String outFileName;

	double[] spanGoldCounts;

	public int dimension() {
		return dimension;
	}

	public double valueAt(double[] x) {
		ensureCache(x);
		return lastValue;
	}

	public double[] derivativeAt(double[] x) {
		ensureCache(x);
		return lastDerivative;
	}

	public double[] unregularizedDerivativeAt(double[] x) {
		ensureCache(x);
		return lastUnregularizedDerivative;
	}

	private void ensureCache(double[] proposed_x) {
		if (requiresUpdate(proposed_x)) {

			linearizer.delinearizeWeights(proposed_x);
			grammar = linearizer.getGrammar();
			lexicon = linearizer.getLexicon();
			spanPredictor = linearizer.getSpanPredictor();

			if (this.x == null)
				this.x = proposed_x.clone();
			else {
				for (int xi = 0; xi < x.length; xi++) {
					this.x[xi] = proposed_x[xi];
				}
			}

			System.out.print("Task: ");

			Future[] submits = new Future[nProcesses];
			// pool =
			// Executors.newCachedThreadPool();//newSingleThreadExecutor();//newFixedThreadPool(nProcesses);
			if (nProcesses > 1) {
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
			}

			// accumulate
			double objective = 0;
			int nUnparasble = 0, nIncorrectLL = 0;
			double[] derivatives = new double[dimension];
			for (int i = 0; i < nProcesses; i++) {
				Counts counts = null;
				if (nProcesses == 1) {
					counts = tasks[0].call();
				} else {
					try {
						counts = (Counts) submits[i].get();
					} catch (ExecutionException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						System.out.println(e.getMessage());
						System.out.println(e.getLocalizedMessage());
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				objective += counts.myObjective;// tasks[i].getMyObjective();
				for (int j = 0; j < dimension; j++) {
					derivatives[j] += counts.myDerivatives[j];
				}
				nUnparasble += counts.unparsableTrees;
				nIncorrectLL += counts.incorrectLLTrees;
			}

			if (spanPredictor != null) {
				// System.out.println("donwscaling span derivatives");
				int offset = dimension - spanGoldCounts.length;
				double total = 0;
				for (int rule = 0; rule < spanGoldCounts.length; rule++) {
					// System.out.println(derivatives[offset+rule]+" "+spanGoldCounts[rule]);
					total += derivatives[offset + rule];
					derivatives[offset + rule] += spanGoldCounts[rule];
					// if (derivatives[offset+rule]!=0)
					// System.out.println("cant count! rule "+rule+" "+derivatives[offset+rule]+" "+spanGoldCounts[rule]);
					// derivatives[offset+rule] = 0;
					if (SloppyMath.isVeryDangerous(derivatives[offset + rule]))
						System.out.print(derivatives[offset + rule] + " ");
				}
				System.out.println(total);
			}

			System.out.print(" done. ");
			if (nUnparasble > 0)
				System.out.println(nUnparasble + " trees were not parsable.");
			if (nIncorrectLL > 0)
				System.out.println(nIncorrectLL
						+ " trees had a higher gold LL than all LL.");

			// pool.shutdown();
			System.out.print("\nThe objective was " + objective);
			// double[] derivatives = computeDerivatives(expectedGCounts,
			// expectedCounts);
			lastUnregularizedDerivative = derivatives.clone();

			switch (myRegularization) {
			case L2_REGULARIZATION:
				objective = l2_regularize(objective, derivatives);
				System.out.print(" and is " + objective
						+ " after L2 regularization");
				break;
			case L1_REGULARIZATION:
				objective = l1_regularize(objective, derivatives);
				System.out.print(" and is " + objective
						+ " after L1 regularization");
			default:
				break;
			}
			System.out.print(".\n");

			objective *= -1.0; // flip sign since we are working with a
								// minimizer rather than with a maximizer
			for (int index = 0; index < derivatives.length; index++) {
				// 'x' and 'derivatives' have same layout
				derivatives[index] *= -1.0;
				lastUnregularizedDerivative[index] *= -1.0;
			}

			lastValue = objective;
			lastDerivative = derivatives;
			//
			// for (int i=0; i<50; i++){
			// System.out.print(derivatives[derivatives.length-1-i]+" ");
			// }
			//
			if (objective < bestObjectiveSoFar
					&& !ConditionalTrainer.Options.dontSaveGrammarsAfterEachIteration) {
				bestObjectiveSoFar = objective;
				ParserData pData = new ParserData(lexicon, grammar,
						spanPredictor, Numberer.getNumberers(),
						grammar.numSubStates, 1, 0, Binarization.RIGHT);
				double val = objective;
				if (val != 0.0) {
					while (Math.abs(val) < 10000)
						val *= 10.0;
				}
				int value = (int) val;
				System.out.println("Saving grammar to " + outFileName + "-"
						+ value + ".");
				if (!pData.Save(outFileName + "-" + value))
					System.out.println("Saving failed!");

			}

		}
	}

	private boolean requiresUpdate(double[] proposed_x) {
		if (this.x == null)
			return true;
		for (int i = 0; i < x.length; i++) {
			if (proposed_x[i] == Double.NaN) {
				System.out.println("Optimizer proposed " + x[i]);
				proposed_x[i] = Double.NEGATIVE_INFINITY;
			}
			if (this.x[i] != proposed_x[i])
				return true;
		}
		return false;
	}

	class Counts {
		double myObjective;
		double[] myDerivatives;
		int unparsableTrees, incorrectLLTrees;

		public Counts(double myObjective, double[] myDerivatives, int unpars,
				int incorr) {
			this.myObjective = myObjective;
			this.myDerivatives = myDerivatives;
			this.unparsableTrees = unpars;
			this.incorrectLLTrees = incorr;
		}
	}

	class Calculator implements Callable {
		// int nGrWeights;
		ArrayParser gParser;
		ConstrainedTwoChartsParser eParser;
		StateSetTreeList myTrees;
		String consName;
		int myID;
		int nCounts;
		Counts myCounts;
		boolean[][][][][] myConstraints;
		int unparsableTrees, incorrectLLTrees;
		boolean doNotProjectConstraints;
		double[] myDerivatives;

		Calculator(StateSetTreeList myT, String consN, int i, Grammar gr,
				Lexicon lex, SpanPredictor sp, int dimension, boolean notProject) {
			// this.nGrWeights = nGrWeights;
			this.nCounts = dimension;
			this.consName = consN;
			this.myTrees = myT;
			this.doNotProjectConstraints = notProject;
			this.myID = i;
			gParser = new ArrayParser(gr, lex);
			eParser = newEParser(gr, lex, sp);
		}

		/**
		 * @param gr
		 * @param lex
		 * @param boost
		 * @return
		 */
		protected ConstrainedTwoChartsParser newEParser(Grammar gr,
				Lexicon lex, SpanPredictor sp) {
			if (!ConditionalTrainer.Options.hierarchicalChart)
				return new ConstrainedTwoChartsParser(gr, lex, sp);
			return new ConstrainedHierarchicalTwoChartParser(gr, lex, sp,
					gr.finalLevel);
		}

		protected void loadConstraints() {
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
				if (!doNotProjectConstraints)
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

		/**
		 * The most important part of the classifier learning process! This
		 * method determines, for the given weight vector x, what the (negative)
		 * log conditional likelihood of the data is, as well as the derivatives
		 * of that likelihood wrt each weight parameter.
		 */
		public Counts call() {
			double myObjective = 0;
			myDerivatives = new double[dimension];
			// double[] myDerivatives = new double[nCounts];
			unparsableTrees = 0;
			incorrectLLTrees = 0;

			if (myConstraints == null)
				loadConstraints();

			int i = -1;
			int block = 0;
			double totalBias = 0;
			for (Tree<StateSet> stateSetTree : myTrees) {
				i++;
				List<StateSet> yield = stateSetTree.getYield();

				boolean noSmoothing = false /* true */, debugOutput = false;

				// parse the sentence
				boolean[][][][] cons = null;
				if (consName != null) {
					cons = myConstraints[i];
					if (cons.length != yield.size()) {
						System.out.println("My ID: " + myID + ", block: "
								+ block + ", sentence: " + i);
						System.out.println("Sentence length (" + yield.size()
								+ ") and constraints length (" + cons.length
								+ ") do not match!");
						System.exit(-1);
					}
				}
				double allLL = eParser.doConstrainedInsideOutsideScores(yield,
						cons, noSmoothing, null, null, false);

				// compute the ll of the gold tree
				double goldLL = (ConditionalTrainer.Options.hierarchicalChart) ? eParser
						.doInsideOutsideScores(stateSetTree, noSmoothing,
								debugOutput, eParser.spanScores) : gParser
						.doInsideOutsideScores(stateSetTree, noSmoothing,
								debugOutput, eParser.spanScores);

				if (i % 500 == 0)
					System.out.print(".");

				if (!sanityCheckLLs(goldLL, allLL, stateSetTree)) {
					myObjective += -1000;
					continue;
				}

				if (false) { // compute exhaustive iS/oS to get exact
								// expectations and then compute bias
					double[] myExpectedCounts = new double[myDerivatives.length];
					eParser.incrementExpectedCounts(linearizer,
							myExpectedCounts, yield);

					double[] myExactExpectedCounts = new double[myDerivatives.length];
					double exactLL = eParser.doConstrainedInsideOutsideScores(
							yield, null, noSmoothing, null, null, false);
					eParser.incrementExpectedCounts(linearizer,
							myExactExpectedCounts, yield);

					double bias = 0;
					for (int ii = 0; ii < myDerivatives.length; ii++) {
						double diff = myExpectedCounts[ii]
								- myExactExpectedCounts[ii];
						bias += diff * diff;
					}
					totalBias += bias;
					System.out.println(allLL + "\t" + exactLL + "\t" + bias);
				}

				eParser.incrementExpectedCounts(linearizer, myDerivatives,
						yield);
				if (ConditionalTrainer.Options.hierarchicalChart)
					eParser.incrementExpectedGoldCounts(linearizer,
							myDerivatives, stateSetTree);
				else
					gParser.incrementExpectedGoldCounts(linearizer,
							myDerivatives, stateSetTree);
				myObjective += (goldLL - allLL);

				// System.out.println(stateSetTree);
				// double old = gParser.doInsideOutsideScores(stateSetTree,
				// noSmoothing, debugOutput, eParser.spanScores);
				// double old2 = eParser.doInsideOutsideScores(stateSetTree,
				// noSmoothing, debugOutput, eParser.spanScores);
				// System.out.println(stateSetTree);
			}

			myCounts = new Counts(myObjective, myDerivatives, unparsableTrees,
					incorrectLLTrees);

			totalBias /= myTrees.size();
			System.out.println("\nAverage bias: " + totalBias + "\n");
			System.out.print(" " + myID + " ");
			return myCounts;
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

		/**
		 * @param goldLL
		 * @param allLL
		 * @param stateSetTree
		 * @return
		 */
		protected boolean sanityCheckLLs(double goldLL, double allLL,
				Tree<StateSet> stateSetTree) {
			if (SloppyMath.isVeryDangerous(allLL)
					|| SloppyMath.isVeryDangerous(goldLL)) {
				unparsableTrees++;
				return false;
			}

			if (goldLL - allLL > 1.0e-4) {
				System.out.println("Something is wrong! The gold LL is "
						+ goldLL + " and the all LL is " + allLL);// +"\n"+sentence+"\n"+stateSetTree);
				System.out.println(stateSetTree);
				incorrectLLTrees++;
				return false;
			}
			return true;
		}
	}

	public double l2_regularize(double objective, double[] derivatives) {
		// Incorporate penalty terms (regularization) into the objective and
		// derivatives
		if (SloppyMath.isVeryDangerous(objective))
			return objective;
		double sigma2 = sigma * sigma;
		double penalty = 0.0;
		for (int index = 0; index < x.length; index++) {
			// if (lastX[index]==10000 || Double.isInfinite(lastX[index]))
			// continue;
			penalty += x[index] * x[index];
		}
		// System.out.print(" penalty="+penalty);
		objective -= penalty / (2 * sigma2);

		for (int index = 0; index < x.length; index++) {
			// 'x' and 'derivatives' have same layout
			// if (lastX[index]==10000 || Double.isInfinite(lastX[index]))
			// continue;
			derivatives[index] -= x[index] / sigma2;
			if (SloppyMath.isVeryDangerous(derivatives[index])) {
				System.out
						.println("Setting regularized derivative to zero because it is Inf.");
				derivatives[index] = 0;
			}
		}
		return objective;

	}

	public double l1_regularize(double objective, double[] derivatives) {
		// Incorporate penalty terms (regularization) into the objective and
		// derivatives
		if (SloppyMath.isVeryDangerous(objective))
			return objective;
		double sigma2 = sigma * sigma;
		double sigma2span = 1;// (sigma-2)*(sigma-2);
		double sigma2lex = sigma2;// 1;//1;//(sigma-2)*(sigma-2);
		int ind = 0;

		int penaltyGr = 0, penaltyLex = 0, penaltySpan = 0;
		for (int i = 0; i < nGrammarWeights; i++) {
			penaltyGr += Math.abs(x[ind++]);
		}
		penaltyGr /= (2 * sigma2);
		for (int i = 0; i < nLexiconWeights; i++) {
			penaltyLex += Math.abs(x[ind++]);
		}
		penaltyLex /= (2 * sigma2lex);
		for (int i = 0; i < nSpanWeights; i++) {
			penaltySpan += Math.abs(x[ind++]);
		}
		penaltySpan /= (2 * sigma2span);

		objective -= (penaltyGr + penaltyLex + penaltySpan);

		int index = 0;
		for (int i = 0; i < nGrammarWeights; i++) {
			double mySigma = sigma2;

			if (x[index] < 0)
				derivatives[index] -= -1.0 / mySigma;
			else if (x[index] > 0)
				derivatives[index] -= 1.0 / mySigma;
			else {
				if (derivatives[index] < -1.0 / mySigma)
					derivatives[index] -= 1.0 / mySigma;
				else if (derivatives[index] > 1.0 / mySigma)
					derivatives[index] -= -1.0 / mySigma;
				else {
					derivatives[index] = 0;
					lastUnregularizedDerivative[index] = 0;
				} // probably already 0;
			}

			if (SloppyMath.isVeryDangerous(derivatives[index])
					|| Math.abs(derivatives[index]) > 1.0e10) {
				System.out
						.println("Setting regularized derivative to zero because it is "
								+ derivatives[index]);
				derivatives[index] = 0;
				lastUnregularizedDerivative[index] = 0;
			}
			index++;
		}
		for (int i = 0; i < nLexiconWeights; i++) {
			double mySigma = sigma2lex;

			if (x[index] < 0)
				derivatives[index] -= -1.0 / mySigma;
			else if (x[index] > 0)
				derivatives[index] -= 1.0 / mySigma;
			else {
				if (derivatives[index] < -1.0 / mySigma)
					derivatives[index] -= 1.0 / mySigma;
				else if (derivatives[index] > 1.0 / mySigma)
					derivatives[index] -= -1.0 / mySigma;
				else {
					derivatives[index] = 0;
					lastUnregularizedDerivative[index] = 0;
				} // probably already 0;
			}

			if (SloppyMath.isVeryDangerous(derivatives[index])
					|| Math.abs(derivatives[index]) > 1.0e10) {
				System.out
						.println("Setting regularized derivative to zero because it is "
								+ derivatives[index]);
				derivatives[index] = 0;
				lastUnregularizedDerivative[index] = 0;
			}
			index++;
		}
		for (int i = 0; i < nSpanWeights; i++) {
			double mySigma = sigma2span;

			if (x[index] < 0)
				derivatives[index] -= -1.0 / mySigma;
			else if (x[index] > 0)
				derivatives[index] -= 1.0 / mySigma;
			else {
				if (derivatives[index] < -1.0 / mySigma)
					derivatives[index] -= 1.0 / mySigma;
				else if (derivatives[index] > 1.0 / mySigma)
					derivatives[index] -= -1.0 / mySigma;
				else {
					derivatives[index] = 0;
					lastUnregularizedDerivative[index] = 0;
				} // probably already 0;
			}

			if (SloppyMath.isVeryDangerous(derivatives[index])
					|| Math.abs(derivatives[index]) > 1.0e10) {
				System.out
						.println("Setting regularized derivative to zero because it is "
								+ derivatives[index]);
				derivatives[index] = 0;
				lastUnregularizedDerivative[index] = 0;
			}
			index++;
		}

		return objective;

	}

	//
	// public double[] computeDerivatives(double[] expectedGoldCounts, double[]
	// expectedCounts){
	// double[] derivatives = new double[dimension()];
	//
	// int nDangerous = 0;
	// if (spanPredictor!=null){
	// int offset = dimension - spanGoldCounts.length;
	// for (int rule=0; rule<spanGoldCounts.length; rule++){
	// expectedGoldCounts[offset+rule] = spanGoldCounts[rule];
	// }
	// }
	// for (int rule=0; rule<derivatives.length;rule++){
	// derivatives[rule] = (expectedGoldCounts[rule]-expectedCounts[rule]);
	// if
	// (SloppyMath.isVeryDangerous(derivatives[rule])||Math.abs(derivatives[rule])>1.0e10){
	// nDangerous++;
	// System.out.println("Setting derivative to zero because it is "+expectedGoldCounts[rule]+" - "+expectedCounts[rule]+" = "+derivatives[rule]);
	// derivatives[rule] = 0;
	// }
	// }
	//
	// if (nDangerous>0)
	// System.out.println("Set "+nDangerous+" derivatives to 0 since they were dangerous.");
	// return derivatives;
	// }

	public ParsingObjectiveFunction() {
	}

	public ParsingObjectiveFunction(Linearizer linearizer,
			StateSetTreeList trainTrees, double sigma, int regularization,
			String consName, int nProc, String outName,
			boolean doNotProjectConstraints, boolean combinedLexicon) {
		this.sigma = sigma;
		this.myRegularization = regularization;
		this.grammar = linearizer.getGrammar();// .copyGrammar();
		this.lexicon = linearizer.getLexicon();// .copyLexicon();
		this.spanPredictor = linearizer.getSpanPredictor();
		this.linearizer = linearizer;
		this.outFileName = outName;
		this.dimension = linearizer.dimension();

		nGrammarWeights = linearizer.getNGrammarWeights();
		nLexiconWeights = linearizer.getNLexiconWeights();
		nSpanWeights = linearizer.getNSpanWeights();

		if (spanPredictor != null)
			this.spanGoldCounts = spanPredictor
					.countGoldSpanFeatures(trainTrees);

		int nTreesPerBlock = trainTrees.size() / nProc;
		this.consBaseName = consName;
		boolean[][][][][] tmp = edu.berkeley.nlp.PCFGLA.ParserConstrainer
				.loadData(consName + "-0.data");
		if (tmp != null)
			nTreesPerBlock = tmp.length;

		// split the trees into chunks
		this.nProcesses = nProc;
		trainingTrees = new StateSetTreeList[nProcesses];
		// allowedStates = new ArrayList[nProcesses];
		for (int i = 0; i < nProcesses; i++) {
			trainingTrees[i] = new StateSetTreeList();
			// allowedStates[i] = new ArrayList<boolean[][][][]>();
		}
		int block = -1;
		int inBlock = 0;
		for (int i = 0; i < trainTrees.size(); i++) {
			if (i % nTreesPerBlock == 0) {
				block++;
				// System.out.println(inBlock);
				inBlock = 0;
			}
			trainingTrees[block % nProcesses].add(trainTrees.get(i));
			inBlock++;
			// if (cons!=null)
			// allowedStates[i%nProcesses].add(ArrayUtil.clone(cons[i]));
		}
		for (int i = 0; i < nProcesses; i++) {
			System.out.println("Process " + i + " has "
					+ trainingTrees[i].size() + " trees.");
		}
		trainTrees = null;
		pool = Executors.newFixedThreadPool(nProcesses);// CachedThreadPool();

		tasks = new Calculator[nProcesses];
		for (int i = 0; i < nProcesses; i++) {
			tasks[i] = newCalculator(doNotProjectConstraints, i);
		}

		this.bestObjectiveSoFar = Double.POSITIVE_INFINITY;
	}

	public void shutdown() {
		pool.shutdown();
	}

	/**
	 * @param doNotProjectConstraints
	 * @param i
	 * @return
	 */
	protected Calculator newCalculator(boolean doNotProjectConstraints, int i) {
		return new Calculator(trainingTrees[i], consBaseName, i, grammar,
				lexicon, spanPredictor, dimension, doNotProjectConstraints);
	}

	public double[] getCurrentWeights() {
		return linearizer.getLinearizedWeights();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * edu.berkeley.nlp.classify.ObjectiveFunction#getLogProbabilities(edu.berkeley
	 * .nlp.classify.EncodedDatum, double[], edu.berkeley.nlp.classify.Encoding,
	 * edu.berkeley.nlp.classify.IndexLinearizer)
	 */
	public <F, L> double[] getLogProbabilities(EncodedDatum datum,
			double[] weights, Encoding<F, L> encoding,
			IndexLinearizer indexLinearizer) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * @param newSigma
	 */
	public void setSigma(double newSigma) {
		sigma = newSigma;
		x = null;
		bestObjectiveSoFar = Double.POSITIVE_INFINITY;
	}
}
