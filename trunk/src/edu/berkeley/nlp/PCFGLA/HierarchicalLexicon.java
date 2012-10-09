/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.Numberer;

/**
 * @author petrov
 * 
 */
public class HierarchicalLexicon extends SimpleLexicon {

	private static final long serialVersionUID = 1L;
	public List<double[]>[][] hierarchicalScores; // for each tag, word store a
													// list of hiearchical
													// features
	public int[][] finalLevels;

	/**
	 * @param numSubStates
	 * @param threshold
	 */
	public HierarchicalLexicon(short[] numSubStates, double threshold) {
		super(numSubStates, threshold);
		hierarchicalScores = new List[numStates][];
	}

	public HierarchicalLexicon(SimpleLexicon lex) {
		super(lex.numSubStates, lex.threshold);
		this.expectedCounts = new double[numStates][][];
		this.tagWordIndexer = new IntegerIndexer[numStates];
		this.wordIndexer = lex.wordIndexer;
		this.wordCounter = lex.wordCounter;
		// this.wordIsAmbiguous = lex.wordIsAmbiguous;
		for (int tag = 0; tag < numStates; tag++) {
			this.tagWordIndexer[tag] = lex.tagWordIndexer[tag].copy();
		}
		this.nWords = lex.nWords;
		this.smoother = lex.smoother;
		makeHiearchicalScores(lex.scores);
		this.scores = null;
	}

	// assume for now that the scores being passed in are from an unsplit
	// baseline grammar
	private void makeHiearchicalScores(double[][][] scores) {
		hierarchicalScores = new List[numStates][];
		finalLevels = new int[numStates][];
		for (int tag = 0; tag < numStates; tag++) {
			int words = tagWordIndexer[tag].size();
			hierarchicalScores[tag] = new List[words];
			finalLevels[tag] = new int[words];
			for (int word = 0; word < words; word++) {
				hierarchicalScores[tag][word] = new ArrayList<double[]>();
				double[] score = { Math.log(scores[tag][0][word]) };
				hierarchicalScores[tag][word].add(score);
				// finalLevels[tag][word]=0; // already initialized to 0
			}
		}
	}

	public void explicitlyComputeScores(int finalLevel) {
		this.scores = new double[numStates][][];
		int nSubstates = (int) Math.pow(2, finalLevel);
		// int[] divisors = new int[nSubstates];//finalLevel+1];
		// for (int i=0; i<=finalLevel; i++){
		// int div = (int)Math.pow(2, finalLevel-i);
		// divisors[div] = div;
		// }

		for (int tag = 0; tag < numStates; tag++) {
			int words = hierarchicalScores[tag].length;
			this.scores[tag] = new double[nSubstates][words];
			for (int word = 0; word < words; word++) {
				List<double[]> scoreHierarchy = hierarchicalScores[tag][word];
				for (int level = 0; level <= finalLevel; level++) {
					if (level > finalLevels[tag][word])
						continue;
					double[] scoresThisLevel = scoreHierarchy.get(level);
					int divisor = nSubstates / scoresThisLevel.length; // divisors[level];
					for (int substate = 0; substate < nSubstates; substate++) {
						this.scores[tag][substate][word] += scoresThisLevel[substate
								/ divisor];
					}
				}
				for (int substate = 0; substate < nSubstates; substate++) {
					this.scores[tag][substate][word] = Math
							.exp(scores[tag][substate][word]);
				}
			}
		}
	}

	public HierarchicalLexicon splitAllStates(int[] counts,
			boolean moreSubstatesThanCounts, int mode) {
		short[] newNumSubStates = new short[numSubStates.length];
		newNumSubStates[0] = 1;
		for (short i = 1; i < numSubStates.length; i++) {
			// don't split a state into more substates than times it was
			// actaully seen
			if (!moreSubstatesThanCounts && numSubStates[i] >= counts[i]) {
				newNumSubStates[i] = numSubStates[i];
			} else {
				newNumSubStates[i] = (short) (numSubStates[i] * 2);
			}
		}
		HierarchicalLexicon newLex = newInstance();
		newLex.numSubStates = newNumSubStates;
		Random random = GrammarTrainer.RANDOM;
		newLex.expectedCounts = new double[numStates][][];
		newLex.tagWordIndexer = new IntegerIndexer[numStates];
		newLex.wordIndexer = this.wordIndexer;
		for (int tag = 0; tag < numStates; tag++) {
			newLex.tagWordIndexer[tag] = tagWordIndexer[tag].copy();
		}
		newLex.nWords = this.nWords;
		newLex.smoother = this.smoother;
		List<double[]>[][] hS = new List[numStates][];
		newLex.finalLevels = new int[numStates][];
		// int[] nSubstates = new int[finalLevel+1];
		// for (int i=0; i<=finalLevel; i++){
		// nSubstates[i] = (int)Math.pow(2, i);
		// }
		for (int tag = 0; tag < numStates; tag++) {
			int words = tagWordIndexer[tag].size();
			hS[tag] = new List[words];
			newLex.finalLevels[tag] = new int[words];
			for (int word = 0; word < words; word++) {
				hS[tag][word] = new ArrayList<double[]>();
				for (double[] scores : hierarchicalScores[tag][word]) {
					hS[tag][word].add(scores.clone());
				}
				int fLevel = this.finalLevels[tag][word] + 1;
				int nSub = (int) Math.pow(2, fLevel);
				if (nSub > newNumSubStates[tag])
					continue;
				double[] newScores = new double[nSub];
				for (int i = 0; i < newScores.length; i++) {
					newScores[i] = random.nextDouble() / 100.0;
				}
				hS[tag][word].add(newScores);
				newLex.finalLevels[tag][word] = fLevel;
			}
		}
		newLex.scores = null;
		newLex.hierarchicalScores = hS;
		newLex.wordCounter = wordCounter;
		// newLex.wordIsAmbiguous = wordIsAmbiguous;
		return newLex;
	}

	/**
	 * @return
	 */
	public HierarchicalLexicon newInstance() {
		return new HierarchicalLexicon(this.numSubStates, this.threshold);
	}

	public int getFinalLevel(int globalWordIndex, int tag) {
		int tagSpecificWordIndex = tagWordIndexer[tag].indexOf(globalWordIndex);
		return finalLevels[tag][tagSpecificWordIndex];
	}

	public void mergeLexicon() {
		int nRemovedParam = 0, nRemovedArrays = 0;
		for (int tag = 0; tag < numStates; tag++) {
			int words = hierarchicalScores[tag].length;
			for (int word = 0; word < words; word++) {
				List<double[]> scoreHierarchy = hierarchicalScores[tag][word];
				int level = finalLevels[tag][word];
				double[] scoresThisLevel = scoreHierarchy.get(level);
				if (scoresThisLevel == null)
					continue;
				boolean allZero = true;
				for (int substate = 0; substate < scoresThisLevel.length; substate++) {
					allZero = allZero && scoresThisLevel[substate] == 0;
				}
				if (allZero) {
					scoreHierarchy.remove(level);
					finalLevels[tag][word]--;
					nRemovedParam += scoresThisLevel.length;
					nRemovedArrays++;
				}
			}
		}
		System.out.println("Removed " + nRemovedParam
				+ " parameters in the lexicon by setting " + nRemovedArrays
				+ " arrays to null.");
	}

	public double[] getLastLevel(int tag, int word) {
		return hierarchicalScores[tag][word].get(finalLevels[tag][word]);
	}

	public HierarchicalLexicon copyLexicon() {
		HierarchicalLexicon copy = newInstance();
		copy.expectedCounts = new double[numStates][][];
		copy.scores = ArrayUtil.clone(scores);// new double[numStates][][];
		copy.hierarchicalScores = this.hierarchicalScores;
		copy.tagWordIndexer = new IntegerIndexer[numStates];
		copy.wordIndexer = this.wordIndexer;
		for (int tag = 0; tag < numStates; tag++) {
			copy.tagWordIndexer[tag] = tagWordIndexer[tag].copy();
			copy.expectedCounts[tag] = new double[numSubStates[tag]][tagWordIndexer[tag]
					.size()];
		}
		if (this.wordCounter != null)
			copy.wordCounter = this.wordCounter.clone();
		// if (this.wordIsAmbiguous!=null) copy.wordIsAmbiguous =
		// this.wordIsAmbiguous.clone();
		copy.nWords = this.nWords;
		copy.smoother = this.smoother;
		if (finalLevels != null)
			copy.finalLevels = ArrayUtil.clone(this.finalLevels);
		return copy;
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();
		Numberer tagNumberer = Numberer.getGlobalNumberer("tags");
		for (int tag = 0; tag < scores.length; tag++) {
			int[] counts = new int[6];
			String tagS = (String) tagNumberer.object(tag);
			if (tagWordIndexer[tag].size() == 0)
				continue;
			for (int word = 0; word < scores[tag][0].length; word++) {
				sb.append(tagS + " "
						+ wordIndexer.get(tagWordIndexer[tag].get(word)) + " ");
				for (int sub = 0; sub < numSubStates[tag]; sub++) {
					sb.append(" " + scores[tag][sub][word]);
				}
				for (double[] d : hierarchicalScores[tag][word]) {
					sb.append("\n" + Arrays.toString(d));
				}
				counts[finalLevels[tag][word]]++;
				sb.append("\n\n");
			}
			System.out.print(tagNumberer.object(tag)
					+ ", word,tag pairs per level: ");
			for (int i = 1; i < 6; i++) {
				System.out.print(counts[i] + " ");
			}
			System.out.print("\n");
		}
		return sb.toString();
	}

}
