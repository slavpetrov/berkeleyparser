/**
 * 
 */
package edu.berkeley.nlp.scripts;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import edu.berkeley.nlp.PCFGLA.Grammar;
import edu.berkeley.nlp.PCFGLA.StateSetTreeList;
import edu.berkeley.nlp.PCFGLA.smoothing.NoSmoothing;
import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.parser.EnglishPennTreebankParseEvaluator;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees;
import edu.berkeley.nlp.syntax.Trees.PennTreeReader;
import edu.berkeley.nlp.util.Filter;
import edu.berkeley.nlp.util.Numberer;

/**
 * Takes a treebank with observed split categories and puts it into our format
 * 
 * @author petrov
 * 
 */
public class GermanSharedTask {

	Numberer tagNumberer;
	List<Numberer> substateNumberers;

	public Grammar extractGrammar(List<Tree<String>> trainTrees) {
		tagNumberer = Numberer.getGlobalNumberer("tags");
		substateNumberers = new ArrayList<Numberer>();

		short[] numSubStates = countSymbols(trainTrees);

		List<Tree<String>> trainTreesNoGF = stripOffGF(trainTrees);
		StateSetTreeList stateSetTrees = new StateSetTreeList(trainTreesNoGF,
				numSubStates, false, tagNumberer);

		Grammar grammar = createGrammar(stateSetTrees, trainTrees, numSubStates);

		return grammar;
	}

	private void checkGrammar(Grammar grammar, List<Tree<String>> trainTrees,
			List<Tree<String>> goldTrees) {
		EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String> eval = new EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String>(
				new HashSet<String>(Arrays.asList(new String[] { "ROOT",
						"PSEUDO" })),
				new HashSet<String>(Arrays.asList(new String[] { "''", "``",
						".", ":", "," })));

		List<Tree<String>> trainTreesNoGF = stripOffGF(trainTrees);
		StateSetTreeList stateSetTrees = new StateSetTreeList(trainTreesNoGF,
				grammar.numSubStates, false, tagNumberer);

		int index = 0;
		for (Tree<StateSet> stateSetTree : stateSetTrees) {
			Tree<String> goldTree = goldTrees.get(index++);
			while (goldTree.getYield().size() != stateSetTree.getYield().size()
					&& index <= goldTrees.size()) {
				goldTree = goldTrees.get(index++);
			}

			List<String> goldPOS = goldTree.getPreTerminalYield();

			Tree<String> labeledTree = guessGF(stateSetTree, grammar, goldPOS);
			Tree<String> debinarizedTree = Trees.spliceNodes(labeledTree,
					new Filter<String>() {
						public boolean accept(String s) {
							return s.startsWith("@");
						}
					});

			Tree<String> goldDebTree = Trees.spliceNodes(goldTree,
					new Filter<String>() {
						public boolean accept(String s) {
							return s.startsWith("@");
						}
					});
			eval.evaluate(goldDebTree, debinarizedTree);
			int t = 1;
			t++;
		}
		eval.display(true);
	}

	private void labelTrees(Grammar grammar, List<Tree<String>> trainTrees,
			List<List<String>> goldPOStags) {
		List<Tree<String>> trainTreesNoGF = stripOffGF(trainTrees);
		StateSetTreeList stateSetTrees = new StateSetTreeList(trainTreesNoGF,
				grammar.numSubStates, false, tagNumberer);

		int index = 0;
		for (Tree<StateSet> stateSetTree : stateSetTrees) {
			List<String> goldPOS = goldPOStags.get(index++);

			Tree<String> labeledTree = guessGF(stateSetTree, grammar, goldPOS);

			Tree<String> debinarizedTree = Trees.spliceNodes(labeledTree,
					new Filter<String>() {
						public boolean accept(String s) {
							return s.startsWith("@");
						}
					});

			System.out.println(debinarizedTree + "\n");
		}

	}

	/**
	 * @param stateSetTree
	 * @param grammar
	 * @param goldPOS
	 * @return
	 */
	private Tree<String> guessGF(Tree<StateSet> stateSetTree, Grammar grammar,
			List<String> goldPOS) {
		doInsideScores(stateSetTree, grammar, goldPOS);
		return extractBestViterbiDerivation(grammar, stateSetTree, 0);
	}

	private List<Tree<String>> stripOffGF(List<Tree<String>> trainTrees) {
		List<Tree<String>> trainTreesNoGF = new ArrayList<Tree<String>>(
				trainTrees.size());
		for (Tree<String> tree : trainTrees) {
			trainTreesNoGF.add(tree.shallowClone());
		}
		for (Tree<String> tree : trainTreesNoGF) {
			for (Tree<String> node : tree.getPostOrderTraversal()) {
				if (tree.isLeaf())
					continue;
				String label = node.getLabel();
				int cutIndex = label.indexOf('-');
				if (cutIndex != -1)
					label = label.substring(0, cutIndex);
				node.setLabel(label);
			}
		}
		return trainTreesNoGF;
	}

	private Grammar createGrammar(StateSetTreeList stateSetTrees,
			List<Tree<String>> trainTrees, short[] numSubStates) {
		Grammar grammar = new Grammar(numSubStates, false, new NoSmoothing(),
				null, -1);
		int index = 0;
		for (Tree<StateSet> stateSetTree : stateSetTrees) {
			Tree<String> tree = trainTrees.get(index++);
			setScores(stateSetTree, tree);
			grammar.tallyStateSetTree(stateSetTree, grammar);
		}
		grammar.optimize(0); // M Step
		return grammar;
	}

	private void setScores(Tree<StateSet> stateSetTree, Tree<String> tree) {
		if (tree.isLeaf())
			return;
		String[] labels = splitLabel(tree.getLabel());
		StateSet stateSet = stateSetTree.getLabel();
		int substate = substateNumberers.get(stateSet.getState()).number(
				labels[1]);
		stateSet.setIScore(substate, 1.0);
		stateSet.setIScale(0);
		stateSet.setOScore(substate, 1.0);
		stateSet.setOScale(0);

		int nChildren = tree.getChildren().size();
		if (nChildren != stateSetTree.getChildren().size())
			System.err.println("Mismatch!");
		for (int i = 0; i < nChildren; i++) {
			setScores(stateSetTree.getChildren().get(i), tree.getChildren()
					.get(i));
		}
	}

	private short[] countSymbols(List<Tree<String>> trainTrees) {
		for (Tree<String> tree : trainTrees) {
			processTree(tree);
		}
		short[] numSubStates = new short[tagNumberer.total()];
		for (int substate = 0; substate < numSubStates.length; substate++) {
			numSubStates[substate] = (short) substateNumberers.get(substate)
					.total();
		}
		return numSubStates;
	}

	private void processTree(Tree<String> tree) {
		String[] labels = splitLabel(tree.getLabel());
		int state = tagNumberer.number(labels[0]);

		if (state >= substateNumberers.size()) {
			substateNumberers.add(new Numberer());
		}
		substateNumberers.get(state).number(labels[1]);

		for (Tree<String> child : tree.getChildren()) {
			if (!child.isLeaf())
				processTree(child);
		}

	}

	/**
	 * @param label
	 * @return
	 */
	private String[] splitLabel(String label) {
		String[] labels = label.split("-");
		if (labels.length == 1)
			labels = new String[] { labels[0], "" };
		return labels;
	}

	Tree<String> extractBestViterbiDerivation(Grammar grammar,
			Tree<StateSet> tree, int substate) {
		if (tree.isLeaf())
			return new Tree<String>(tree.getLabel().getWord());
		if (substate == -1)
			substate = 0;
		if (tree.isPreTerminal()) {
			ArrayList<Tree<String>> child = new ArrayList<Tree<String>>();
			child.add(extractBestViterbiDerivation(grammar, tree.getChildren()
					.get(0), -1));
			int state = tree.getLabel().getState();
			String goalStr = (String) tagNumberer.object(state);
			String gfStr = (String) substateNumberers.get(state).object(
					substate);
			if (!gfStr.equals(""))
				goalStr = goalStr + "-" + gfStr;
			return new Tree<String>(goalStr, child);
		}

		StateSet node = tree.getLabel();
		short pState = node.getState();

		ArrayList<Tree<String>> newChildren = new ArrayList<Tree<String>>();
		List<Tree<StateSet>> children = tree.getChildren();

		double myScore = node.getIScore(substate);
		if (myScore == Double.NEGATIVE_INFINITY) {
			myScore = DoubleArrays.max(node.getIScores());
			substate = DoubleArrays.argMax(node.getIScores());
		}
		switch (children.size()) {
		case 1:
			StateSet child = children.get(0).getLabel();
			short cState = child.getState();
			int nChildStates = child.numSubStates();
			double[][] uscores = grammar.getUnaryScore(pState, cState);
			int childIndex = -1;
			for (int j = 0; j < nChildStates; j++) {
				if (childIndex != -1)
					break;
				if (uscores[j] != null) {
					double cS = child.getIScore(j);
					if (cS == 0)
						continue;
					double rS = uscores[j][substate]; // rule score
					if (rS == 0)
						continue;
					double res = rS * cS;
					if (matches(res, myScore)) {
						childIndex = j;
					}
				}
			}
			newChildren.add(extractBestViterbiDerivation(grammar,
					children.get(0), childIndex));
			break;
		case 2:
			StateSet leftChild = children.get(0).getLabel();
			StateSet rightChild = children.get(1).getLabel();
			int nLeftChildStates = leftChild.numSubStates();
			int nRightChildStates = rightChild.numSubStates();
			short lState = leftChild.getState();
			short rState = rightChild.getState();
			double[][][] bscores = grammar.getBinaryScore(pState, lState,
					rState);
			int lChildIndex = -1,
			rChildIndex = -1;
			for (int j = 0; j < nLeftChildStates; j++) {
				if (lChildIndex != -1 && rChildIndex != -1)
					break;
				double lcS = leftChild.getIScore(j);
				if (lcS == 0)
					continue;
				for (int k = 0; k < nRightChildStates; k++) {
					if (lChildIndex != -1 && rChildIndex != -1)
						break;
					double rcS = rightChild.getIScore(k);
					if (rcS == 0)
						continue;
					if (bscores[j][k] != null) { // check whether one of the
													// parents can produce these
													// kids
						double rS = bscores[j][k][substate];
						if (rS == 0)
							continue;
						double res = rS * lcS * rcS;
						if (matches(myScore, res)) {
							lChildIndex = j;
							rChildIndex = k;
						}
					}
				}
			}
			newChildren.add(extractBestViterbiDerivation(grammar,
					children.get(0), lChildIndex));
			newChildren.add(extractBestViterbiDerivation(grammar,
					children.get(1), rChildIndex));
			break;
		default:
			throw new Error("Malformed tree: more than two children");
		}

		int state = node.getState();
		String parentString = (String) tagNumberer.object(state);
		if (parentString.endsWith("^g"))
			parentString = parentString.substring(0, parentString.length() - 2);
		String gfStr = (String) substateNumberers.get(state).object(substate);
		if (!gfStr.equals(""))
			parentString = parentString + "-" + gfStr;

		return new Tree<String>(parentString, newChildren);
	}

	protected boolean matches(double x, double y) {
		return (Math.abs(x - y) / (Math.abs(x) + Math.abs(y) + 1e-10) < 1.0e-4);
	}

	void doInsideScores(Tree<StateSet> tree, Grammar grammar,
			List<String> goldPOS) {
		if (tree.isLeaf()) {
			return;
		}
		List<Tree<StateSet>> children = tree.getChildren();
		for (Tree<StateSet> child : children) {
			if (!child.isLeaf())
				doInsideScores(child, grammar, goldPOS);
		}
		StateSet parent = tree.getLabel();
		short pState = parent.getState();
		int nParentStates = parent.numSubStates();
		if (tree.isPreTerminal()) {
			// Plays a role similar to initializeChart()
			String POS = goldPOS.get(parent.from);
			String[] labels = splitLabel(POS);
			int substate = 0;
			if (pState < grammar.numStates) {
				substate = substateNumberers.get(pState).number(labels[1]);
				if (substate >= grammar.numSubStates[pState]) {
					System.err.println("Have never seen this POS: " + POS);
					substate = 0;
				}
			} else {
				parent = new StateSet((short) (grammar.numStates - 1),
						(short) 1);
				tree.setLabel(parent);
			}
			parent.setIScore(substate, 1.0);
			parent.scaleIScores(0);
		} else {
			switch (children.size()) {
			case 0:
				break;
			case 1:
				StateSet child = children.get(0).getLabel();
				short cState = child.getState();
				int nChildStates = child.numSubStates();
				double[][] uscores = grammar.getUnaryScore(pState, cState);
				double[] iScores = new double[nParentStates];
				boolean foundOne = false;
				for (int j = 0; j < nChildStates; j++) {
					if (uscores[j] != null) { // check whether one of the
												// parents can produce this
												// child
						double cS = child.getIScore(j);
						if (cS == 0)
							continue;
						for (int i = 0; i < nParentStates; i++) {
							double rS = uscores[j][i]; // rule score
							if (rS == 0)
								continue;
							double res = rS * cS;
							/*
							 * if (res == 0) {
							 * System.out.println("Prevented an underflow: rS "
							 * +rS+" cS "+cS); res = Double.MIN_VALUE; }
							 */
							iScores[i] += res;
							foundOne = true;
						}
					}
				}

				parent.setIScores(iScores);
				parent.scaleIScores(child.getIScale());
				break;
			case 2:
				StateSet leftChild = children.get(0).getLabel();
				StateSet rightChild = children.get(1).getLabel();
				int nLeftChildStates = leftChild.numSubStates();
				int nRightChildStates = rightChild.numSubStates();
				short lState = leftChild.getState();
				short rState = rightChild.getState();
				double[][][] bscores = grammar.getBinaryScore(pState, lState,
						rState);
				double[] iScores2 = new double[nParentStates];
				boolean foundOne2 = false;
				for (int j = 0; j < nLeftChildStates; j++) {
					double lcS = leftChild.getIScore(j);
					if (lcS == 0)
						continue;
					for (int k = 0; k < nRightChildStates; k++) {
						double rcS = rightChild.getIScore(k);
						if (rcS == 0)
							continue;
						if (bscores[j][k] != null) { // check whether one of the
														// parents can produce
														// these kids
							for (int i = 0; i < nParentStates; i++) {
								double rS = bscores[j][k][i];
								if (rS == 0)
									continue;
								double res = rS * lcS * rcS;
								/*
								 * if (res == 0) {
								 * System.out.println("Prevented an underflow: rS "
								 * +rS+" lcS "+lcS+" rcS "+rcS); res =
								 * Double.MIN_VALUE; }
								 */
								iScores2[i] += res;
								foundOne2 = true;
							}
						}
					}
				}

				parent.setIScores(iScores2);
				parent.scaleIScores(leftChild.getIScale()
						+ rightChild.getIScale());
				break;
			default:
				throw new Error("Malformed tree: more than two children");
			}
		}
	}

	private static List<Tree<String>> loadTrees(String inputFile) {
		InputStreamReader inputData = null;
		try {
			inputData = new InputStreamReader(new FileInputStream(inputFile),
					"UTF-8");
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		PennTreeReader treeReader = new PennTreeReader(inputData);

		List<Tree<String>> trainTrees = new ArrayList<Tree<String>>();
		Tree<String> tree = null;
		while (treeReader.hasNext()) {
			tree = treeReader.next();
			// trainTrees.add(TreeAnnotations.processTree(tree, 1, 0,
			// Binarization.LEFT, false, false, false));
			trainTrees.add(tree);
		}
		return trainTrees;
	}

	public static void main(String[] args) {
		String inputFile = args[0];
		List<Tree<String>> trainTrees = loadTrees(inputFile);

		GermanSharedTask grEx = new GermanSharedTask();
		Grammar grammar = grEx.extractGrammar(trainTrees);

		inputFile = "/Users/petrov/Data/german_st/tueba/tueba_tmp";
		List<Tree<String>> testTrees = loadTrees(inputFile);
		inputFile = "/Users/petrov/Data/german_st/tueba/data02.mrg";
		List<Tree<String>> goldTrees = loadTrees(inputFile);
		List<List<String>> goldPOS = new ArrayList<List<String>>(
				goldTrees.size());
		for (Tree<String> t : goldTrees) {
			goldPOS.add(t.getPreTerminalYield());
		}
		grEx.checkGrammar(grammar, testTrees, goldTrees);
		// grEx.labelTrees(grammar, testTrees, goldPOS);
	}

}
