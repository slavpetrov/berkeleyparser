/**
 * 
 */
package edu.berkeley.nlp.scripts;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import edu.berkeley.nlp.PCFGLA.Binarization;
import edu.berkeley.nlp.PCFGLA.Grammar;
import edu.berkeley.nlp.PCFGLA.Lexicon;
import edu.berkeley.nlp.PCFGLA.Option;
import edu.berkeley.nlp.PCFGLA.OptionParser;
import edu.berkeley.nlp.PCFGLA.ParserData;
import edu.berkeley.nlp.PCFGLA.SophisticatedLexicon;
import edu.berkeley.nlp.PCFGLA.StateSetTreeList;
import edu.berkeley.nlp.PCFGLA.smoothing.NoSmoothing;
import edu.berkeley.nlp.PCFGLA.smoothing.SmoothAcrossParentSubstate;
import edu.berkeley.nlp.PCFGLA.smoothing.Smoother;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees.PennTreeReader;
import edu.berkeley.nlp.util.Numberer;

/**
 * @author petrov
 * 
 */
public class ObservedGrammarExtractor {

	public static class Options {

		@Option(name = "-out", required = true, usage = "Output File for Grammar (Required)")
		public String outFileName;

		@Option(name = "-path", usage = "Path to Corpus File (Default: null)")
		public String path = null;

		@Option(name = "-smooth", usage = "Smooth the grammar if possible")
		public boolean smooth = false;

	}

	public static void main(String[] args) {
		OptionParser optParser = new OptionParser(Options.class);
		Options opts = (Options) optParser.parse(args, true);

		List<Tree<String>> trainTrees = loadTrees(opts.path);
		ParserData pData = createGrammar(trainTrees, opts.smooth);

		if (pData.Save(opts.outFileName))
			System.out.println("Saved grammar.");
		else
			System.out.println("Saving failed!");
		System.exit(0);
	}

	static Numberer tagNumberer;
	static List<Numberer> substateNumberers;

	private static ParserData createGrammar(List<Tree<String>> trainTrees,
			boolean smooth) {
		tagNumberer = Numberer.getGlobalNumberer("tags");
		substateNumberers = new ArrayList<Numberer>();

		short[] numSubStates = countSymbols(trainTrees);

		List<Tree<String>> trainTreesNoAnnotation = stripOffAnnotation(trainTrees);
		StateSetTreeList stateSetTrees = new StateSetTreeList(
				trainTreesNoAnnotation, numSubStates, false, tagNumberer);

		Grammar grammar = new Grammar(numSubStates, false, new NoSmoothing(),
				null, -1);
		Lexicon lexicon = new SophisticatedLexicon(numSubStates,
				SophisticatedLexicon.DEFAULT_SMOOTHING_CUTOFF, new double[] {
						0.5, 0.1 }, new NoSmoothing(), 0);

		if (smooth) {
			System.out.println("Will smooth the grammar.");
			Smoother grSmoother = new SmoothAcrossParentSubstate(0.01);
			Smoother lexSmoother = new SmoothAcrossParentSubstate(0.1);
			grammar.setSmoother(grSmoother);
			lexicon.setSmoother(lexSmoother);
		}

		System.out.print("Creating grammar...");
		int index = 0;
		boolean secondHalf = false;
		int nTrees = trainTrees.size();
		for (Tree<StateSet> stateSetTree : stateSetTrees) {
			Tree<String> tree = trainTrees.get(index++);
			secondHalf = (index > nTrees / 2.0);
			setScores(stateSetTree, tree);
			lexicon.trainTree(stateSetTree, 0, null, secondHalf, false, 4);
			grammar.tallyStateSetTree(stateSetTree, grammar);
		}
		lexicon.optimize();
		grammar.optimize(0);
		System.out.println("done.");

		ParserData pData = new ParserData(lexicon, grammar, null,
				Numberer.getNumberers(), numSubStates, 1, 0, Binarization.RIGHT);

		return pData;

	}

	private static void setScores(Tree<StateSet> stateSetTree, Tree<String> tree) {
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

	private static List<Tree<String>> stripOffAnnotation(
			List<Tree<String>> trainTrees) {
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

	private static short[] countSymbols(List<Tree<String>> trainTrees) {
		System.out.print("Counting symbols...");
		for (Tree<String> tree : trainTrees) {
			processTree(tree);
		}
		short[] numSubStates = new short[tagNumberer.total()];
		for (int substate = 0; substate < numSubStates.length; substate++) {
			numSubStates[substate] = (short) substateNumberers.get(substate)
					.total();
		}
		System.out.println("done.");
		for (int tag = 0; tag < tagNumberer.size(); tag++) {
			System.out.println((String) tagNumberer.object(tag) + "\t"
					+ numSubStates[tag]);
		}
		return numSubStates;
	}

	private static void processTree(Tree<String> tree) {
		String[] labelParts = splitLabel(tree.getLabel());
		int state = tagNumberer.number(labelParts[0]);

		if (state >= substateNumberers.size()) {
			substateNumberers.add(new Numberer());
		}
		substateNumberers.get(state).number(labelParts[1]);

		for (Tree<String> child : tree.getChildren()) {
			if (!child.isLeaf())
				processTree(child);
		}

	}

	private static String[] splitLabel(String label) {
		int breakPoint = label.indexOf("-");
		String substateString = (breakPoint < 0) ? "" : label
				.substring(breakPoint);
		String stateString = (breakPoint < 0) ? label : label.substring(0,
				breakPoint);
		return new String[] { stateString, substateString };
	}

	private static List<Tree<String>> loadTrees(String inputFile) {
		System.out.print("Loading trees...");
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
		System.out.println("done.");
		return trainTrees;
	}

}
