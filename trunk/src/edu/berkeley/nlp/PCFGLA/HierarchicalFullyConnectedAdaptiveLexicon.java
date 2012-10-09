/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.util.Arrays;

import edu.berkeley.nlp.PCFGLA.SimpleLexicon.IntegerIndexer;
import edu.berkeley.nlp.PCFGLA.smoothing.Smoother;
import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.Numberer;
import edu.berkeley.nlp.util.PriorityQueue;

/**
 * @author petrov
 * 
 */
public class HierarchicalFullyConnectedAdaptiveLexicon extends
		HierarchicalFullyConnectedLexicon {
	private static final long serialVersionUID = 1L;
	public HierarchicalAdaptiveLexicalRule[][] rules;

	public HierarchicalFullyConnectedAdaptiveLexicon(short[] numSubStates,
			int smoothingCutoff, double[] smoothParam, Smoother smoother,
			StateSetTreeList trainTrees, int knownWordCount) {
		super(numSubStates, knownWordCount);
		super.init(trainTrees);
		init();
	}

	public HierarchicalFullyConnectedAdaptiveLexicon(short[] numSubStates,
			int knownWordCount) {
		super(numSubStates, knownWordCount);
	}

	private void init() {
		this.scores = null;
		this.hierarchicalScores = null;
		this.finalLevels = null;
		rules = new HierarchicalAdaptiveLexicalRule[numStates][];
		for (int tag = 0; tag < numStates; tag++) {
			if (tagWordIndexer[tag] == null) {
				rules[tag] = new HierarchicalAdaptiveLexicalRule[0];
				continue;
			}
			rules[tag] = new HierarchicalAdaptiveLexicalRule[tagWordIndexer[tag]
					.size()];
			for (int word = 0; word < rules[tag].length; word++) {
				rules[tag][word] = new HierarchicalAdaptiveLexicalRule();
			}
		}

	}

	public double[] score(int globalWordIndex, int globalSigIndex, short tag,
			int loc, boolean noSmoothing, boolean isSignature) {
		double[] res = new double[numSubStates[tag]];
		if (tagWordIndexer[tag] == null)
			return res;
		if (globalWordIndex != -1) {
			int tagSpecificWordIndex = tagWordIndexer[tag]
					.indexOf(globalWordIndex);
			if (tagSpecificWordIndex != -1) {
				for (int i = 0; i < numSubStates[tag]; i++) {
					res[i] = rules[tag][tagSpecificWordIndex].scores[i];
				}
			} else if (knownWordCount > 0) {
				Arrays.fill(res, 1.0);
			}
		} else if (knownWordCount > 0) {
			Arrays.fill(res, 1.0);
		}
		if (globalWordIndex >= 0
				&& /* globalWordIndex<wordCounter.length && */(wordCounter[globalWordIndex] > knownWordCount)) {
			if (smoother != null) {
				// smoother.smooth(tag,res);
				// double max = ArrayMath.max(res) / 1000;
				// for (int i=0; i< res.length; i++){
				// if (res[i] < max) res[i] += max;
				// }
			}
			return res;
		}
		if (globalSigIndex > -1) {
			int tagSpecificWordIndex = tagWordIndexer[tag]
					.indexOf(globalSigIndex);
			if (tagSpecificWordIndex != -1) {
				for (int i = 0; i < numSubStates[tag]; i++) {
					res[i] *= rules[tag][tagSpecificWordIndex].scores[i];
				}
				// } else{
				// System.out.println("unseen sig-tag pair");
			}
			// } else{
			// System.out.println("unseen sig");
		}
		if (smoother != null) {
			// smoother.smooth(tag,res);
			// double max = ArrayMath.max(res) / 1000;
			// for (int i=0; i< res.length; i++){
			// if (res[i] < max) res[i] += max;
			// }

		}
		return res;
	}

	public HierarchicalLexicon copyLexicon() {
		// HierarchicalLexicon copy = newInstance();
		// copy.expectedCounts = new double[numStates][][];
		// copy.scores = ArrayUtil.clone(scores);//new double[numStates][][];
		// copy.hierarchicalScores = this.hierarchicalScores;
		// copy.tagWordIndexer = new IntegerIndexer[numStates];
		// copy.wordIndexer = this.wordIndexer;
		// for (int tag=0; tag<numStates; tag++){
		// copy.tagWordIndexer[tag] = tagWordIndexer[tag].copy();
		// copy.expectedCounts[tag] = new
		// double[numSubStates[tag]][tagWordIndexer[tag].size()];
		// }
		// if (this.wordCounter!=null) copy.wordCounter =
		// this.wordCounter.clone();
		// // if (this.wordIsAmbiguous!=null) copy.wordIsAmbiguous =
		// this.wordIsAmbiguous.clone();
		// copy.nWords = this.nWords;
		// copy.smoother = this.smoother;
		// if (finalLevels!=null) copy.finalLevels =
		// ArrayUtil.clone(this.finalLevels);
		// return copy;
		return this;
	}

	public HierarchicalLexicon splitAllStates(int[] counts,
			boolean moreSubstatesThanCounts, int mode) {
		int finalLevel = (int) (Math.log((int) ArrayUtil.max(numSubStates)) / Math
				.log(2)) + 1;
		for (int tag = 0; tag < numStates; tag++) {
			numSubStates[tag] *= 2;
			for (int word = 0; word < rules[tag].length; word++) {
				rules[tag][word].splitRule(numSubStates[tag]);
				rules[tag][word].explicitlyComputeScores(finalLevel, false);
			}
		}
		return this;
	}

	public void mergeLexicon() {
		int removedParam = 0;
		for (int tag = 0; tag < numStates; tag++) {
			for (int word = 0; word < rules[tag].length; word++) {
				removedParam += rules[tag][word].mergeRule();
			}
		}
		System.out.println("Removed " + removedParam
				+ " parameters from the lexicon.");
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();
		Numberer tagNumberer = Numberer.getGlobalNumberer("tags");
		for (int tag = 0; tag < rules.length; tag++) {
			int[] counts = new int[7];
			String tagS = (String) tagNumberer.object(tag);
			if (rules[tag].length == 0)
				continue;
			for (int word = 0; word < rules[tag].length; word++) {
				sb.append(tagS + " "
						+ wordIndexer.get(tagWordIndexer[tag].get(word)) + "\n");
				sb.append(rules[tag][word].toString());
				sb.append("\n\n");
				counts[rules[tag][word].hierarchy.getDepth()]++;
			}
			System.out.print(tagNumberer.object(tag)
					+ ", lexical rules per level: ");
			for (int i = 1; i < 6; i++) {
				System.out.print(counts[i] + " ");
			}
			System.out.print("\n");

		}
		return sb.toString();
	}

	public void explicitlyComputeScores(int finalLevel) {
		for (short tag = 0; tag < rules.length; tag++) {
			for (int word = 0; word < rules[tag].length; word++) {
				rules[tag][word].explicitlyComputeScores(finalLevel, false);
			}
		}
	}

}
