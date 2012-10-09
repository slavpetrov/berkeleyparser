/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.util.Arrays;
import java.util.List;

import edu.berkeley.nlp.PCFGLA.smoothing.Smoother;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;

/**
 * @author petrov
 * 
 */
public class HierarchicalFullyConnectedLexicon extends HierarchicalLexicon {

	private static final long serialVersionUID = 1L;
	protected int knownWordCount;

	/**
	 * @param numSubStates
	 * @param threshold
	 */
	public HierarchicalFullyConnectedLexicon(short[] numSubStates,
			int knownWordCount) {
		super(numSubStates, 0);
		this.knownWordCount = knownWordCount;
	}

	public HierarchicalFullyConnectedLexicon(short[] numSubStates,
			int smoothingCutoff, double[] smoothParam, Smoother smoother,
			StateSetTreeList trainTrees, int knownWordCount) {
		this(numSubStates, knownWordCount);
		init(trainTrees);
	}

	/**
	 * @param previousLexicon
	 */
	public HierarchicalFullyConnectedLexicon(SimpleLexicon previousLexicon,
			int knownWordCount) {
		super(previousLexicon);
		this.knownWordCount = knownWordCount;
	}

	public HierarchicalFullyConnectedLexicon newInstance() {
		return new HierarchicalFullyConnectedLexicon(this.numSubStates,
				this.knownWordCount);
	}

	public void init(StateSetTreeList trainTrees) {
		for (Tree<StateSet> tree : trainTrees) {
			List<StateSet> words = tree.getYield();
			for (StateSet word : words) {
				String sig = word.getWord();
				wordIndexer.add(sig);
			}
		}
		wordCounter = new int[wordIndexer.size()];
		for (Tree<StateSet> tree : trainTrees) {
			List<StateSet> words = tree.getYield();
			int ind = 0;
			for (StateSet word : words) {
				String wordString = word.getWord();
				wordCounter[wordIndexer.indexOf(wordString)]++;

				String sig = getSignature(word.getWord(), ind++);
				wordIndexer.add(sig);
			}
		}

		tagWordIndexer = new IntegerIndexer[numStates];
		for (int tag = 0; tag < numStates; tag++) {
			tagWordIndexer[tag] = new IntegerIndexer(wordIndexer.size());
		}

		labelTrees(trainTrees);

		boolean[] lexTag = new boolean[numStates];
		for (Tree<StateSet> tree : trainTrees) {
			List<StateSet> words = tree.getYield();
			List<StateSet> tags = tree.getPreTerminalYield();
			int ind = 0;
			for (StateSet word : words) {
				int tag = tags.get(ind).getState();
				tagWordIndexer[tag].add(new Integer(word.wordIndex));
				tagWordIndexer[tag].add(new Integer(word.sigIndex));
				lexTag[tag] = true;
				ind++;
			}
		}

		expectedCounts = new double[numStates][][];
		scores = new double[numStates][][];
		for (int tag = 0; tag < numStates; tag++) {
			if (!lexTag[tag]) {
				tagWordIndexer[tag] = null;
				continue;
			}
			// else tagWordIndexer[tag] = tagIndexer;
			// expectedCounts[tag] = new
			// double[numSubStates[tag]][tagWordIndexer[tag].size()];
			scores[tag] = new double[numSubStates[tag]][tagWordIndexer[tag]
					.size()];
		}
		nWords = wordIndexer.size();
	}

	public double[] score(int globalWordIndex, int globalSigIndex, short tag,
			int loc, boolean noSmoothing, boolean isSignature) {
		double[] res = new double[numSubStates[tag]];
		if (globalWordIndex != -1) {
			int tagSpecificWordIndex = tagWordIndexer[tag]
					.indexOf(globalWordIndex);
			if (tagSpecificWordIndex != -1) {
				for (int i = 0; i < numSubStates[tag]; i++) {
					res[i] = scores[tag][i][tagSpecificWordIndex];
				}
			} else {
				Arrays.fill(res, 1.0);
			}
		} else {
			Arrays.fill(res, 1.0);
		}
		if (globalWordIndex >= 0
				&& (wordCounter[globalWordIndex] > knownWordCount)) {
			// if (globalSigIndex!=-1)
			// System.out.println("Problem: frequent word has signature!");
			return res;
		}
		if (globalSigIndex != -1) {
			int tagSpecificWordIndex = tagWordIndexer[tag]
					.indexOf(globalSigIndex);
			if (tagSpecificWordIndex != -1) {
				for (int i = 0; i < numSubStates[tag]; i++) {
					res[i] *= scores[tag][i][tagSpecificWordIndex];
				}
				// } else{
				// System.out.println("unseen sig-tag pair");
			}
			// } else{
			// System.out.println("unseen sig");
		}
		// if (smoother!=null) smoother.smooth(tag,res);
		return res;
	}

	public double[] score(StateSet stateSet, short tag, boolean noSmoothing,
			boolean isSignature) {
		if (stateSet.wordIndex == -2) {
			String word = stateSet.getWord();
			if (isSignature) {
				stateSet.wordIndex = -1;
				stateSet.sigIndex = wordIndexer.indexOf(word);
			} else {
				stateSet.wordIndex = wordIndexer.indexOf(word);
				// if (stateSet.wordIndex > wordCounter.length){
				// System.out.println("no count for this word: "+(String)wordIndexer.get(tagWordIndexer[tag].get(stateSet.wordIndex)));
				// stateSet.sigIndex = -1;
				// } else {
				if ((stateSet.wordIndex >= 0 && (wordCounter[stateSet.wordIndex] > knownWordCount))
						|| noSmoothing)
					stateSet.sigIndex = -1;
				else if (knownWordCount > 0)
					stateSet.sigIndex = wordIndexer.indexOf(getSignature(word,
							stateSet.from));
				else
					stateSet.wordIndex = wordIndexer.indexOf(getSignature(word,
							stateSet.from));
			}
			// }
		}
		return score(stateSet.wordIndex, stateSet.sigIndex, tag, stateSet.from,
				noSmoothing, isSignature);
	}

	public void labelTrees(StateSetTreeList trainTrees) {
		for (Tree<StateSet> tree : trainTrees) {
			List<StateSet> words = tree.getYield();
			List<StateSet> tags = tree.getPreTerminalYield();
			int ind = 0;
			for (StateSet word : words) {
				word.wordIndex = wordIndexer.indexOf(word.getWord());
				if (word.wordIndex < 0 || word.wordIndex >= wordCounter.length) {
					System.out.println("Have never seen this word before: "
							+ word.getWord() + " " + word.wordIndex);
					System.out.println(tree);
				} else if (wordCounter[word.wordIndex] <= knownWordCount) {
					short tag = tags.get(ind).getState();
					String sig = getSignature(word.getWord(), ind);
					wordIndexer.add(sig);
					word.sigIndex = wordIndexer.indexOf(sig);
					tagWordIndexer[tag].add(wordIndexer.indexOf(sig));
				} else
					word.sigIndex = -1;
				ind++;
			}
		}
	}

}
