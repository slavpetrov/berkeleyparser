/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import edu.berkeley.nlp.math.SloppyMath;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.Indexer;
import edu.berkeley.nlp.util.Numberer;
import edu.berkeley.nlp.util.Pair;
import edu.berkeley.nlp.util.PriorityQueue;

/**
 * @author petrov
 * 
 */
public class SpanPredictor implements Serializable {

	private static final long serialVersionUID = 1L;

	public final boolean useFirstAndLast;
	public final boolean usePreviousAndNext; // can only be on if
												// useFirstAndLast is on
	public final boolean useBeginAndEndPairs;
	public final boolean useSyntheticClass;

	public final boolean usePunctuation;
	Indexer<String> punctuationSignatures;
	boolean[] isPunctuation;

	public final boolean useOnlyWords = true;

	// public final boolean useCapitalization;
	public final int minFeatureFrequency;

	public final int minSpanLength = 3;

	public double[][] firstWordScore;
	public double[][] lastWordScore;
	public double[][] previousWordScore;
	public double[][] nextWordScore;

	public double[][] beginPairScore;
	public double[][] endPairScore;
	private HashMap<Pair<Integer, Integer>, Integer> beginMap;
	private HashMap<Pair<Integer, Integer>, Integer> endMap;

	public double[][] punctuationScores;

	public int nWords;
	public int nFeatures;
	private int[] stateClass;
	private int nClasses;
	private Indexer<String> wordIndexer;

	// public int startIndexPrevious, startIndexBegin;

	public SpanPredictor(int nWords, StateSetTreeList trainTrees,
			Numberer tagNumberer, Indexer<String> wordIndexer) {
		this.useFirstAndLast = ConditionalTrainer.Options.useFirstAndLast;
		this.usePreviousAndNext = ConditionalTrainer.Options.usePreviousAndNext;
		this.useBeginAndEndPairs = ConditionalTrainer.Options.useBeginAndEndPairs;
		this.useSyntheticClass = ConditionalTrainer.Options.useSyntheticClass;
		this.usePunctuation = ConditionalTrainer.Options.usePunctuation;
		this.minFeatureFrequency = ConditionalTrainer.Options.minFeatureFrequency;

		this.wordIndexer = wordIndexer;
		this.nWords = nWords;
		this.nFeatures = 0;
		if (useSyntheticClass) {
			System.out
					.println("Distinguishing between real and synthetic classes.");
			stateClass = new int[tagNumberer.total()];
			for (int i = 0; i < tagNumberer.total(); i++) {
				String state = (String) tagNumberer.object(i);
				if (state.charAt(0) == '@')
					stateClass[i] = 1; // synthetic
			}
			nClasses = 2;
		} else {
			stateClass = new int[tagNumberer.total()];
			nClasses = 1;
		}
		if (useFirstAndLast) {
			firstWordScore = new double[nWords][nClasses];
			lastWordScore = new double[nWords][nClasses];
			ArrayUtil.fill(firstWordScore, 1);
			ArrayUtil.fill(lastWordScore, 1);
			this.nFeatures += 2 * nWords * nClasses;
		}
		if (usePreviousAndNext) {
			previousWordScore = new double[nWords][nClasses];
			nextWordScore = new double[nWords][nClasses];
			ArrayUtil.fill(previousWordScore, 1);
			ArrayUtil.fill(nextWordScore, 1);
			this.nFeatures += 2 * nWords * nClasses;
		}
		if (useBeginAndEndPairs) {
			initPairs(trainTrees);
		}
		if (usePunctuation) {
			initPunctuations(trainTrees);
		}
	}

	private void initPairs(StateSetTreeList trainTrees) {
		beginMap = new HashMap<Pair<Integer, Integer>, Integer>();
		endMap = new HashMap<Pair<Integer, Integer>, Integer>();
		Counter<Pair<Integer, Integer>> beginPairCounter = new Counter<Pair<Integer, Integer>>();
		Counter<Pair<Integer, Integer>> endPairCounter = new Counter<Pair<Integer, Integer>>();
		int beginPairs = 0, endPairs = 0;
		for (Tree<StateSet> tree : trainTrees) {
			List<StateSet> words = tree.getYield();
			StateSet stateSet = words.get(0);
			int prevIndex = (stateSet.sigIndex < 0) ? stateSet.wordIndex
					: stateSet.sigIndex;
			if (useOnlyWords)
				prevIndex = stateSet.wordIndex;
			int currIndex = -1;
			for (int i = 1; i <= words.size() - minSpanLength; i++) {
				stateSet = words.get(i);
				currIndex = (stateSet.sigIndex < 0) ? stateSet.wordIndex
						: stateSet.sigIndex;
				if (useOnlyWords)
					currIndex = stateSet.wordIndex;
				Pair<Integer, Integer> pair = new Pair<Integer, Integer>(
						prevIndex, currIndex);
				beginPairCounter.incrementCount(pair, 1.0);
				if (!beginMap.containsKey(pair))
					beginMap.put(pair, beginPairs++);
				prevIndex = currIndex;
			}
			if (words.size() < minSpanLength)
				continue;
			stateSet = words.get(minSpanLength - 1);
			prevIndex = (stateSet.sigIndex < 0) ? stateSet.wordIndex
					: stateSet.sigIndex;
			if (useOnlyWords)
				currIndex = stateSet.wordIndex;
			for (int i = minSpanLength; i < words.size(); i++) {
				stateSet = words.get(i);
				currIndex = (stateSet.sigIndex < 0) ? stateSet.wordIndex
						: stateSet.sigIndex;
				if (useOnlyWords)
					currIndex = stateSet.wordIndex;

				Pair<Integer, Integer> pair = new Pair<Integer, Integer>(
						prevIndex, currIndex);
				endPairCounter.incrementCount(pair, 1.0);
				if (!endMap.containsKey(pair))
					endMap.put(pair, endPairs++);
				prevIndex = currIndex;
			}
		}
		HashMap<Pair<Integer, Integer>, Integer> newBeginMap = new HashMap<Pair<Integer, Integer>, Integer>();
		HashMap<Pair<Integer, Integer>, Integer> newEndMap = new HashMap<Pair<Integer, Integer>, Integer>();

		int newBeginPairs = 0;
		for (Pair<Integer, Integer> pair : beginMap.keySet()) {
			if (beginPairCounter.getCount(pair) >= minFeatureFrequency) {
				newBeginMap.put(pair, newBeginPairs++);
			}
		}
		beginMap = newBeginMap;
		beginPairs = newBeginPairs;

		int newEndPairs = 0;
		for (Pair<Integer, Integer> pair : endMap.keySet()) {
			if (endPairCounter.getCount(pair) >= minFeatureFrequency) {
				newEndMap.put(pair, newEndPairs++);
			}
		}
		endMap = newEndMap;
		endPairs = newEndPairs;

		beginPairScore = new double[beginPairs][nClasses];
		endPairScore = new double[endPairs][nClasses];
		nFeatures += (beginPairs + endPairs) * nClasses;
		System.out.println("There were " + beginPairs
				+ " begin-pair types and " + endPairs + " end-pair types.");
	}

	public double[] scoreSpan(int previousIndex, int firstIndex, int lastIndex,
			int followingIndex) {
		double[] result = new double[nClasses];
		Arrays.fill(result, 1);
		if (firstIndex < 0 || lastIndex < 0) {
			// System.out.println("unseen index when scoring span: "+firstIndex+" "+lastIndex);
			return result;
		}
		for (int c = 0; c < nClasses; c++) {
			// if (c==1) continue;
			if (useFirstAndLast)
				result[c] *= firstWordScore[firstIndex][c]
						* lastWordScore[lastIndex][c];
			if (usePreviousAndNext) {
				if (previousIndex >= 0)
					result[c] *= previousWordScore[previousIndex][c];
				if (followingIndex >= 0)
					result[c] *= nextWordScore[followingIndex][c];
			}
			if (useBeginAndEndPairs) {
				if (previousIndex >= 0) {
					int index = getBeginIndex(previousIndex, firstIndex);
					if (index >= 0)
						result[c] *= beginPairScore[index][c];
				}
				if (followingIndex >= 0) {
					int index = getEndIndex(lastIndex, followingIndex);
					if (index >= 0)
						result[c] *= endPairScore[index][c];
				}
			}
			if (SloppyMath.isDangerous(result[c])) {
				System.out
						.println("Dangerous span prediction set to 1, since it was "
								+ result);
				result[c] = 1;
			}
		}
		return result;
	}

	public double[][][] predictSpans(List<StateSet> sentence) {
		int previousIndex = -1, firstIndex, lastIndex, followingIndex = -1;
		int length = sentence.size();
		double[][][] spanScores = new double[length][length + 1][nClasses];
		// all spans of size <=minSpanLength are ok
		for (int start = 0; start < length; start++) {
			for (int end = start + 1; end < start + minSpanLength
					&& end <= length; end++) {
				for (int clas = 0; clas < nClasses; clas++) {
					spanScores[start][end][clas] = 1;
				}
			}
		}
		for (int start = 0; start <= length - minSpanLength; start++) {
			StateSet stateSet = sentence.get(start);
			firstIndex = (stateSet.sigIndex < 0) ? stateSet.wordIndex
					: stateSet.sigIndex;
			if (useOnlyWords)
				firstIndex = stateSet.wordIndex;
			for (int end = start + minSpanLength; end <= length; end++) {
				stateSet = sentence.get(end - 1);
				lastIndex = (stateSet.sigIndex < 0) ? stateSet.wordIndex
						: stateSet.sigIndex;
				if (useOnlyWords)
					lastIndex = stateSet.wordIndex;
				if (end < length) {
					stateSet = sentence.get(end);
					followingIndex = (stateSet.sigIndex < 0) ? stateSet.wordIndex
							: stateSet.sigIndex;
					if (useOnlyWords)
						followingIndex = stateSet.wordIndex;
				} else {
					followingIndex = -1;
				}
				spanScores[start][end] = scoreSpan(previousIndex, firstIndex,
						lastIndex, followingIndex);
			}
			previousIndex = firstIndex;
		}
		if (usePunctuation) {
			int[][] punctSignatures = getPunctuationSignatures(sentence);
			for (int start = 0; start <= length - minSpanLength; start++) {
				for (int end = start + minSpanLength; end <= length; end++) {
					int sig = punctSignatures[start][end];
					if (sig == -1)
						continue;
					for (int c = 0; c < nClasses; c++) {
						spanScores[start][end][c] *= punctuationScores[sig][c];
					}
				}
			}
		}

		return spanScores;
	}

	public double[] countGoldSpanFeatures(StateSetTreeList trainTrees) {
		int[][] firstWordCount = null, lastWordCount = null;
		int[][] previousWordCount = null, nextWordCount = null;
		int[][] beginPairsCount = null, endPairsCount = null;
		int[][] punctuationCount = null, punctuationSig = null;

		if (useFirstAndLast) {
			firstWordCount = new int[nWords][nClasses];
			lastWordCount = new int[nWords][nClasses];
		}
		if (usePreviousAndNext) {
			previousWordCount = new int[nWords][nClasses];
			nextWordCount = new int[nWords][nClasses];
		}
		if (useBeginAndEndPairs) {
			beginPairsCount = new int[beginPairScore.length][nClasses];
			endPairsCount = new int[endPairScore.length][nClasses];
		}
		if (usePunctuation) {
			punctuationCount = new int[punctuationSignatures.size()][nClasses];

		}

		for (Tree<StateSet> tree : trainTrees) {
			List<StateSet> words = tree.getYield();
			if (usePunctuation)
				punctuationSig = getPunctuationSignatures(words);
			countGoldSpanFeaturesHelper(tree, words, firstWordCount,
					lastWordCount, previousWordCount, nextWordCount,
					beginPairsCount, endPairsCount, punctuationCount,
					punctuationSig);
		}

		double[] res = new double[nFeatures];
		int index = 0;
		if (useFirstAndLast) {
			int firstSum = 0, lastSum = 0;
			for (int c = 0; c < nWords; c++) {
				firstSum += ArrayUtil.sum(firstWordCount[c]);
				lastSum += ArrayUtil.sum(lastWordCount[c]);
			}
			System.out.println("Number of first words: " + firstSum);
			System.out.println("Number of last words: " + lastSum);
			for (int i = 0; i < nWords; i++) {
				for (int c = 0; c < nClasses; c++) {
					res[index++] = firstWordCount[i][c];
				}
			}
			for (int i = 0; i < nWords; i++) {
				for (int c = 0; c < nClasses; c++) {
					res[index++] = lastWordCount[i][c];
				}
			}
		}
		if (usePreviousAndNext) {
			int prevSum = 0, nextSum = 0;
			for (int c = 0; c < nWords; c++) {
				prevSum += ArrayUtil.sum(previousWordCount[c]);
				nextSum += ArrayUtil.sum(nextWordCount[c]);
			}
			System.out.println("Number of previous words: " + prevSum);
			System.out.println("Number of next words: " + nextSum);
			for (int i = 0; i < nWords; i++) {
				for (int c = 0; c < nClasses; c++) {
					res[index++] = previousWordCount[i][c];
				}
			}
			for (int i = 0; i < nWords; i++) {
				for (int c = 0; c < nClasses; c++) {
					res[index++] = nextWordCount[i][c];
				}
			}
		}
		if (useBeginAndEndPairs) {
			int beginSum = 0, endSum = 0;
			for (int i = 0; i < beginPairsCount.length; i++) {
				beginSum += ArrayUtil.sum(beginPairsCount[i]);
			}
			for (int i = 0; i < endPairsCount.length; i++) {
				endSum += ArrayUtil.sum(endPairsCount[i]);
			}
			System.out.println("Number of begin pairs: " + beginSum);
			System.out.println("Number of end pairs: " + endSum);
			for (int i = 0; i < beginPairsCount.length; i++) {
				for (int c = 0; c < nClasses; c++) {
					res[index++] = beginPairsCount[i][c];
				}
			}
			for (int i = 0; i < endPairsCount.length; i++) {
				for (int c = 0; c < nClasses; c++) {
					res[index++] = endPairsCount[i][c];
				}
			}
		}
		if (usePunctuation) {
			for (int i = 0; i < punctuationCount.length; i++) {
				for (int c = 0; c < nClasses; c++) {
					res[index++] = punctuationCount[i][c];
				}
			}
		}
		return res;
	}

	private void countGoldSpanFeaturesHelper(Tree<StateSet> tree,
			List<StateSet> words, int[][] firstWordCount,
			int[][] lastWordCount, int[][] previousWordCount,
			int[][] nextWordCount, int[][] beginPairsCount,
			int[][] endPairsCount, int[][] punctuationCount,
			int[][] punctuationSignatures) {
		StateSet node = tree.getLabel();
		if (node.to - node.from < minSpanLength)
			return;

		short state = node.getState();
		int thisClass = stateClass[state];

		StateSet stateSet = words.get(node.from);
		int firstWord = (stateSet.sigIndex < 0) ? stateSet.wordIndex
				: stateSet.sigIndex;
		if (useOnlyWords)
			firstWord = stateSet.wordIndex;
		stateSet = words.get(node.to - 1);
		int lastWord = (stateSet.sigIndex < 0) ? stateSet.wordIndex
				: stateSet.sigIndex;
		if (useOnlyWords)
			lastWord = stateSet.wordIndex;

		int previousWord = 0, nextWord = 0;
		if (node.from > 0) {
			stateSet = words.get(node.from - 1);
			previousWord = (stateSet.sigIndex < 0) ? stateSet.wordIndex
					: stateSet.sigIndex;
			if (useOnlyWords)
				previousWord = stateSet.wordIndex;
		}
		if (node.to < words.size()) {
			stateSet = words.get(node.to);
			nextWord = (stateSet.sigIndex < 0) ? stateSet.wordIndex
					: stateSet.sigIndex;
			if (useOnlyWords)
				nextWord = stateSet.wordIndex;
		}

		if (useFirstAndLast) {
			firstWordCount[firstWord][thisClass]++;
			lastWordCount[lastWord][thisClass]++;
		}
		if (usePreviousAndNext) {
			if (node.from > 0)
				previousWordCount[previousWord][thisClass]++;
			if (node.to < words.size())
				nextWordCount[nextWord][thisClass]++;
		}
		if (useBeginAndEndPairs) {
			if (node.from > 0) {
				int beginIndex = getBeginIndex(previousWord, firstWord);
				if (beginIndex >= 0)
					beginPairsCount[beginIndex][thisClass]++;
			}
			if (node.to < words.size()) {
				int endIndex = getEndIndex(lastWord, nextWord);
				if (endIndex >= 0)
					endPairsCount[endIndex][thisClass]++;
			}
		}
		if (usePunctuation) {
			int punctSig = punctuationSignatures[node.from][node.to];
			if (punctSig >= 0)
				punctuationCount[punctSig][thisClass]++;
		}

		for (Tree<StateSet> child : tree.getChildren()) {
			countGoldSpanFeaturesHelper(child, words, firstWordCount,
					lastWordCount, previousWordCount, nextWordCount,
					beginPairsCount, endPairsCount, punctuationCount,
					punctuationSignatures);
		}

	}

	private void initPunctuations(StateSetTreeList trainTrees) {
		punctuationSignatures = new Indexer<String>();
		isPunctuation = new boolean[nWords];
		Counter<String> punctSigCounter = new Counter<String>();
		for (int word = 0; word < nWords; word++) {
			isPunctuation[word] = isPunctuation(wordIndexer.get(word));
		}
		for (Tree<StateSet> tree : trainTrees) {
			getPunctuationSignatures(tree.getYield(), true, punctSigCounter);
		}

		Indexer<String> newPunctuationSignatures = new Indexer<String>();
		for (String sig : punctSigCounter.keySet()) {
			if (punctSigCounter.getCount(sig) >= minFeatureFrequency)
				newPunctuationSignatures.add(sig);
		}
		punctuationSignatures = newPunctuationSignatures;
		punctuationScores = new double[punctuationSignatures.size()][nClasses];
		ArrayUtil.fill(punctuationScores, 1);
		nFeatures += nClasses * punctuationScores.length;
	}

	private boolean isPunctuation(String word) {
		if (word.length() > 2)
			return false;
		if (Character.isLetterOrDigit(word.charAt(0)))
			return false;
		if (word.length() == 1)
			return true;
		return !Character.isLetterOrDigit(word.charAt(1));
	}

	private int appendItem(StringBuilder sb, String maskedWord, int nWordsBefore) {
		if (maskedWord != X) {
			sb.append(maskedWord);
			nWordsBefore = 0;
		} else if (nWordsBefore == 0) {
			sb.append("x");
			nWordsBefore++;
		} else if (nWordsBefore == 1) {
			sb.append("+");
			nWordsBefore++;
		}
		return nWordsBefore;
	}

	public int[][] getPunctuationSignatures(List<StateSet> sentence) {
		return getPunctuationSignatures(sentence, false, null);
	}

	private final String X = "x".intern();

	// replace words with x and leave only punctuation, collapse xx,xxx,xxxx,...
	// to x+
	public int[][] getPunctuationSignatures(List<StateSet> sentence,
			boolean update, Counter<String> punctSigCounter) {
		int length = sentence.size();
		String[] masked = new String[length];
		for (int i = 0; i < length; i++) {
			StateSet thisStateSet = sentence.get(i);
			masked[i] = (thisStateSet.wordIndex > 0 && isPunctuation[thisStateSet.wordIndex]) ? thisStateSet
					.getWord() : X;
		}

		int[][] result = new int[length][length + 1];
		ArrayUtil.fill(result, -1);
		for (int start = 0; start <= length - minSpanLength; start++) {
			StringBuilder sb = new StringBuilder();
			String prev = "";
			if (start <= 1)
				sb.append("<S>");
			int nWordsBefore = 0;
			if (start > 0) {
				appendItem(sb, masked[start - 1], nWordsBefore);
			}
			sb.append("[");
			nWordsBefore = appendItem(sb, masked[start], 0);
			for (int end = start + minSpanLength; end <= length; end++) {
				nWordsBefore = appendItem(sb, masked[end - 1], nWordsBefore);
				prev = sb.toString();
				sb.append("]");
				if (end < length) {
					appendItem(sb, masked[end], 0);
				}
				if (end < length - 1) {
					sb.append("<E>");
				}
				String sig = sb.toString();
				if (update) {
					punctuationSignatures.add(sig);
					punctSigCounter.incrementCount(sig, 1.0);
				}
				result[start][end] = punctuationSignatures.indexOf(sig);
				sb = new StringBuilder(prev);
			}
		}
		return result;
	}

	@Override
	public String toString() {
		return toString(null);
	}

	public String toString(Indexer<String> wordIndexer) {
		StringBuffer sb = new StringBuffer();
		if (useFirstAndLast || usePreviousAndNext) {
			sb.append("word");
			if (useFirstAndLast)
				sb.append("\tfirst\t\tlast\t");
			if (usePreviousAndNext)
				sb.append("\tprevious\tfollowing");
			sb.append("\n");

			for (int word = 0; word < nWords; word++) {
				String w = (wordIndexer != null) ? wordIndexer.get(word) : word
						+ "";
				sb.append(w);
				if (useFirstAndLast)
					sb.append("\t" + Arrays.toString(firstWordScore[word])
							+ "\t" + Arrays.toString(lastWordScore[word]));
				if (usePreviousAndNext)
					sb.append("\t" + Arrays.toString(previousWordScore[word])
							+ "\t" + Arrays.toString(nextWordScore[word]));
				sb.append("\n");
			}
			if (useFirstAndLast) {
				PriorityQueue<String> pQf = new PriorityQueue<String>();
				PriorityQueue<String> pQl = new PriorityQueue<String>();
				PriorityQueue<String> pQp = null;
				PriorityQueue<String> pQn = null;
				if (usePreviousAndNext) {
					pQp = new PriorityQueue<String>();
					pQn = new PriorityQueue<String>();
				}
				for (int word = 0; word < nWords; word++) {
					String w = (wordIndexer != null) ? wordIndexer.get(word)
							: word + "";
					pQf.add(w, firstWordScore[word][0]);
					pQl.add(w, lastWordScore[word][0]);
					if (usePreviousAndNext) {
						pQp.add(w, previousWordScore[word][0]);
						pQn.add(w, nextWordScore[word][0]);
					}
				}
				sb.append("First word weights\tLast word weights");
				if (usePreviousAndNext) {
					sb.append("\tPrevious word weights\tNext word weights");
				}
				sb.append("\n");
				while (pQf.hasNext()) {
					double weight = pQf.getPriority();
					sb.append(pQf.next() + " " + weight + "\t");
					weight = pQl.getPriority();
					sb.append(pQl.next() + " " + weight + "\t");
					if (usePreviousAndNext) {
						weight = pQp.getPriority();
						sb.append(pQp.next() + " " + weight + "\t");
						weight = pQn.getPriority();
						sb.append(pQn.next() + " " + weight);
					}
					sb.append("\n");
				}
			}
		}
		if (useBeginAndEndPairs) {
			sb.append("Begin pairs\t\t\t\tEnd pairs\n");
			PriorityQueue<String> pQb = new PriorityQueue<String>();
			PriorityQueue<String> pQe = new PriorityQueue<String>();
			for (Pair p : beginMap.keySet()) {
				String w1 = wordIndexer.get((Integer) p.getFirst());
				String w2 = wordIndexer.get((Integer) p.getSecond());
				pQb.add("(" + w1 + " | " + w2 + "),",
						beginPairScore[beginMap.get(p)][0]);
			}
			for (Pair p : endMap.keySet()) {
				String w1 = wordIndexer.get((Integer) p.getFirst());
				String w2 = wordIndexer.get((Integer) p.getSecond());
				pQe.add("(" + w1 + " | " + w2 + "),",
						endPairScore[endMap.get(p)][0]);
			}
			while (pQb.hasNext() || pQe.hasNext()) {
				double weight = 0;
				if (pQb.hasNext()) {
					weight = pQb.getPriority();
					sb.append(pQb.next() + " " + weight + "\t");
				} else
					sb.append("\t\t\t\t");
				if (pQe.hasNext()) {
					weight = pQe.getPriority();
					sb.append(pQe.next() + " " + weight + "\n");
				} else
					sb.append("\n");
			}
		}
		if (usePunctuation) {
			sb.append("Punctuation features:\n");
			PriorityQueue<String> pQp = new PriorityQueue<String>();
			for (int f = 0; f < punctuationSignatures.size(); f++) {
				String w = punctuationSignatures.get(f);
				pQp.add(w, punctuationScores[f][0]);
			}
			while (pQp.hasNext()) {
				double weight = pQp.getPriority();
				String word = pQp.next();
				sb.append(word + "\t");
				if (word.length() < 8)
					sb.append("\t");
				sb.append(+weight + "\n");
			}
		}
		return sb.toString();
	}

	public int getBeginIndex(int previousIndex, int currIndex) {
		Pair<Integer, Integer> pair = new Pair<Integer, Integer>(previousIndex,
				currIndex);
		if (!beginMap.containsKey(pair))
			return -1;
		return beginMap.get(pair);
	}

	public int getEndIndex(int previousIndex, int currIndex) {
		Pair<Integer, Integer> pair = new Pair<Integer, Integer>(previousIndex,
				currIndex);
		if (!endMap.containsKey(pair))
			return -1;
		return endMap.get(pair);
	}

	/**
	 * @return the stateClass
	 */
	public int[] getStateClass() {
		return stateClass;
	}

	/**
	 * @return the nClasses
	 */
	public final int getNClasses() {
		return nClasses;
	}

	// public class FeatureBundle{
	// public int firstWord;
	// public int lastWord;
	// public int previousWord;
	// public int nextWord;
	//
	// public int beginPair;
	// public int endPair;
	//
	//
	// }

}
