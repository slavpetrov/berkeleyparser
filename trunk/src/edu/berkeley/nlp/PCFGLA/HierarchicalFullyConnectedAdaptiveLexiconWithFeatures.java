/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.util.Arrays;
import java.util.List;

import edu.berkeley.nlp.PCFGLA.smoothing.Smoother;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.StateSetWithFeatures;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.Indexer;
import edu.berkeley.nlp.util.Numberer;
import edu.berkeley.nlp.util.Pair;
import edu.berkeley.nlp.util.PriorityQueue;

/**
 * @author petrov
 * 
 */
public class HierarchicalFullyConnectedAdaptiveLexiconWithFeatures extends
		HierarchicalFullyConnectedAdaptiveLexicon {

	private static final long serialVersionUID = 1L;
	Indexer<String> featureIndexer;
	SimpleLexicon simpleLex;
	private final int minFeatureCount = 50;

	public HierarchicalFullyConnectedAdaptiveLexiconWithFeatures(
			short[] numSubStates, int smoothingCutoff, double[] smoothParam,
			Smoother smoother, StateSetTreeList trainTrees, int knownWordCount) {
		super(numSubStates, knownWordCount);// smoothingCutoff, smoothParam,
											// smoother, trainTrees,
											// knownWordCount);
		simpleLex = new SimpleLexicon(numSubStates, -1);
		init(trainTrees);
		// super.init(trainTrees);

	}

	// public HierarchicalFullyConnectedAdaptiveLexiconWithFeatures
	// newInstance() {
	// return new
	// HierarchicalFullyConnectedAdaptiveLexiconWithFeatures(this.numSubStates,this.knownWordCount);
	// }

	@Override
	public void init(StateSetTreeList trainTrees) {
		for (Tree<StateSet> tree : trainTrees) {
			List<StateSet> words = tree.getYield();
			for (StateSet word : words) {
				String sig = word.getWord();
				wordIndexer.add(sig);
			}
		}
		wordCounter = new int[wordIndexer.size()];
		Counter<String> ixCounter = new Counter<String>();
		featureIndexer = new Indexer<String>();
		for (Tree<StateSet> tree : trainTrees) {
			List<StateSet> words = tree.getYield();
			int ind = 0;
			for (StateSet word : words) {
				String wordString = word.getWord();
				wordCounter[wordIndexer.indexOf(wordString)]++;

				String sig = getSignature(word.getWord(), ind++);
				wordIndexer.add(sig);
				tallyWordFeatures(word.getWord(), ixCounter);
			}
		}

		featureIndexer = new Indexer<String>();
		for (String word : ixCounter.keySet()) {
			if (ixCounter.getCount(word) >= minFeatureCount) {
				System.out.println("keeping: \t" + word);
				featureIndexer.add(word);
			} else
				System.out.println("too rare:\t" + word);

		}

		simpleLex.wordCounter = wordCounter;
		labelTrees(trainTrees);

		tagWordIndexer = new IntegerIndexer[numStates];
		for (int tag = 0; tag < numStates; tag++) {
			tagWordIndexer[tag] = new IntegerIndexer(featureIndexer.size());
		}

		boolean[] lexTag = new boolean[numStates];
		for (Tree<StateSet> tree : trainTrees) {
			List<StateSet> words = tree.getYield();
			List<StateSet> tags = tree.getPreTerminalYield();
			int ind = 0;
			for (StateSet word : words) {
				int tag = tags.get(ind).getState();
				StateSetWithFeatures wordF = (StateSetWithFeatures) word;

				for (Integer f : wordF.features) {
					tagWordIndexer[tag].add(f);
				}
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

	/**
	 * @param word
	 * @param ixCounter
	 */
	private void tallyWordFeatures(String word, Counter<String> ixCounter) {
		int length = word.length();
		if (length > 4) {
			for (int i = 1; i < 4; i++) {
				// String prefix = "PREF-"+word.substring(0,i);
				// featureIndexer.add(prefix);
				// ixCounter.incrementCount(prefix, 1.0);
				String suffix = "SUFF-" + word.substring(length - i);
				featureIndexer.add(suffix);
				ixCounter.incrementCount(suffix, 1.0);
			}
		}
	}

	public StateSet tallyFeatures(StateSet stateSet, boolean update) {
		String word = stateSet.getWord();
		String lowered = word.toLowerCase();
		int loc = stateSet.from;
		String sig = simpleLex.getNewSignature(word, loc);
		StateSetWithFeatures newStateSet = new StateSetWithFeatures(stateSet);
		if (update)
			featureIndexer.add(sig);
		newStateSet.features.add(featureIndexer.indexOf(sig));

		if (update)
			featureIndexer.add("UNK");
		newStateSet.features.add(featureIndexer.indexOf("UNK"));
		int length = word.length();
		if (length > 4) {
			for (int i = 1; i < 4; i++) {
				// String prefix = "PREF-"+lowered.substring(0,i);
				// int prefInd = featureIndexer.indexOf(prefix);
				// if (prefInd>=0)
				// newStateSet.features.add(prefInd);
				String suffix = "SUFF-" + lowered.substring(length - i);
				int suffInd = featureIndexer.indexOf(suffix);
				if (suffInd >= 0)
					newStateSet.features.add(suffInd);
			}
		}
		int wlen = word.length();
		int numCaps = 0;
		boolean hasDigit = false;
		boolean hasDash = false;
		boolean hasLower = false;
		for (int i = 0; i < wlen; i++) {
			char ch = word.charAt(i);
			if (Character.isDigit(ch)) {
				hasDigit = true;
			} else if (ch == '-') {
				hasDash = true;
			} else if (Character.isLetter(ch)) {
				if (Character.isLowerCase(ch)) {
					hasLower = true;
				} else if (Character.isTitleCase(ch)) {
					hasLower = true;
					numCaps++;
				} else {
					numCaps++;
				}
			}
		}
		char ch0 = word.charAt(0);
		if (Character.isUpperCase(ch0) || Character.isTitleCase(ch0)) {
			if (loc == 0 && numCaps == 1) {
				if (update)
					featureIndexer.add("INITC");
				newStateSet.features.add(featureIndexer.indexOf("INITC"));
				// if (isKnown(lowered)) {
				// sb.append("-KNOWNLC");
				// }
			} else {
				if (update)
					featureIndexer.add("CAPS");
				newStateSet.features.add(featureIndexer.indexOf("CAPS"));
			}
		} else if (!Character.isLetter(ch0) && numCaps > 0) {
			if (update)
				featureIndexer.add("CAPS");
			newStateSet.features.add(featureIndexer.indexOf("CAPS"));
		} else if (hasLower) { // (Character.isLowerCase(ch0)) {
			if (update)
				featureIndexer.add("LC");
			newStateSet.features.add(featureIndexer.indexOf("LC"));
		}
		if (hasDigit) {
			if (update)
				featureIndexer.add("NUM");
			newStateSet.features.add(featureIndexer.indexOf("NUM"));
		}
		if (hasDash) {
			if (update)
				featureIndexer.add("DASH");
			newStateSet.features.add(featureIndexer.indexOf("DASH"));
		}
		if (lowered.endsWith("s") && wlen >= 3) {
			// here length 3, so you don't miss out on ones like 80s
			char ch2 = lowered.charAt(wlen - 2);
			// not -ess suffixes or greek/latin -us, -is
			if (ch2 != 's' && ch2 != 'i' && ch2 != 'u') {
				if (update)
					featureIndexer.add("s");
				newStateSet.features.add(featureIndexer.indexOf("s"));
			}
		} else if (word.length() >= 5 && !hasDash && !(hasDigit && numCaps > 0)) {
			// don't do for very short words;
			// Implement common discriminating suffixes
			/*
			 * if (Corpus.myLanguage==Corpus.GERMAN){
			 * sb.append(lowered.substring(lowered.length()-1)); }else{
			 */
			// if (lowered.endsWith("ed")) {
			// sb.append("-ed");
			// } else if (lowered.endsWith("ing")) {
			// sb.append("-ing");
			// } else if (lowered.endsWith("ion")) {
			// sb.append("-ion");
			// } else if (lowered.endsWith("er")) {
			// sb.append("-er");
			// } else if (lowered.endsWith("est")) {
			// sb.append("-est");
			// } else if (lowered.endsWith("ly")) {
			// sb.append("-ly");
			// } else if (lowered.endsWith("ity")) {
			// sb.append("-ity");
			// } else if (lowered.endsWith("y")) {
			// sb.append("-y");
			// } else if (lowered.endsWith("al")) {
			// sb.append("-al");
			// } else if (lowered.endsWith("ble")) {
			// sb.append("-ble");
			// } else if (lowered.endsWith("e")) {
			// sb.append("-e");
		}
		return newStateSet;
	}

	@Override
	public void labelTrees(StateSetTreeList trainTrees) {
		for (Tree<StateSet> tree : trainTrees) {
			// List<StateSet> words = tree.getYield();
			int ind = 0;
			for (Tree<StateSet> word : tree.getTerminals()) {
				StateSetWithFeatures wordF = new StateSetWithFeatures(
						word.getLabel());
				// wordF.wordIndex = wordIndexer.indexOf(word.getWord());
				if (wordF.wordIndex < 0
						|| wordF.wordIndex >= wordCounter.length) {
					System.out.println("Have never seen this word before: "
							+ wordF.getWord() + " " + wordF.wordIndex);
					System.out.println(tree);
				} else if (wordCounter[wordF.wordIndex] <= knownWordCount) {
					wordF = (StateSetWithFeatures) tallyFeatures(wordF, false);
				} else
					wordF.sigIndex = -1;
				featureIndexer.add(wordF.getWord());
				wordF.features.add(featureIndexer.indexOf(wordF.getWord()));
				word.setLabel(wordF);
				ind++;
			}
		}
	}

	// StateSetWithFeatures lastStateSet;

	@Override
	public double[] score(StateSet stateSet, short tag, boolean noSmoothing,
			boolean isSignature) {
		double[] res = new double[numSubStates[tag]];
		Arrays.fill(res, 1);
		StateSetWithFeatures stateSetF = null;
		if (stateSet.wordIndex == -2) {
			stateSetF = new StateSetWithFeatures(stateSet);
			int wordIndex = wordIndexer.indexOf(stateSet.getWord());
			if (wordIndex < 0
					|| (wordIndex >= 0 && (wordCounter[wordIndex] <= knownWordCount))) {
				stateSetF = (StateSetWithFeatures) tallyFeatures(stateSet,
						false);
			}
			int f = featureIndexer.indexOf(stateSet.getWord());
			if (f >= 0)
				stateSetF.features.add(f);
			// stateSetF.wordIndex = -3;
			// stateSet = lastStateSet;
			// } else if (stateSet.wordIndex == -3){
			// stateSet = lastStateSet;
		} else {
			stateSetF = (StateSetWithFeatures) stateSet;
		}

		boolean noFeat = true;
		for (int f : stateSetF.features) {
			// if (f>tagWordIndexer[tag].size())
			// System.out.println("hier");
			if (f < 0)
				continue;
			int tagF = tagWordIndexer[tag].indexOf(f);
			if (tagF < 0)
				continue;

			noFeat = false;
			double[] resF = rules[tag][tagF].scores;
			for (int i = 0; i < res.length; i++) {
				res[i] *= resF[i];
			}
		}
		// if (noFeat) {
		// System.out.println("No features for word "+stateSet.getWord()+" "+wordIndexer.indexOf(stateSet.getWord()));
		// }
		return res;
	}

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		Numberer tagNumberer = Numberer.getGlobalNumberer("tags");
		PriorityQueue<Pair<Integer, Integer>> pQ = new PriorityQueue<Pair<Integer, Integer>>();
		for (int tag = 0; tag < rules.length; tag++) {
			int[] counts = new int[6];
			String tagS = (String) tagNumberer.object(tag);
			if (rules[tag].length == 0)
				continue;
			for (int word = 0; word < featureIndexer.size(); word++) {
				int wordT = tagWordIndexer[tag].indexOf(word);
				if (wordT < 0)
					continue;
				String w = featureIndexer.get(word);
				if (w.length() > 4 && w.substring(0, 4).equals("SUFF")) {
					pQ.add(new Pair(tag, word), rules[tag][wordT].scores[0]);
				}
			}
		}
		while (pQ.hasNext()) {
			Pair<Integer, Integer> p = pQ.next();
			int word = p.getSecond();
			int tag = p.getFirst();
			String tagS = (String) tagNumberer.object(tag);
			int wordT = tagWordIndexer[tag].indexOf(word);
			sb.append(tagS + " " + featureIndexer.get(word) + "\n");
			sb.append(rules[tag][wordT].toString());
			sb.append("\n\n");
		}
		sb.append("-----------Start unsorted----------\n");
		for (int tag = 0; tag < rules.length; tag++) {
			int[] counts = new int[6];
			String tagS = (String) tagNumberer.object(tag);
			if (rules[tag].length == 0)
				continue;
			for (int word = 0; word < featureIndexer.size(); word++) {
				int wordT = tagWordIndexer[tag].indexOf(word);
				if (wordT < 0)
					continue;
				sb.append(tagS + " " + featureIndexer.get(word) + "\n");
				sb.append(rules[tag][wordT].toString());
				sb.append("\n\n");
				counts[rules[tag][wordT].hierarchy.getDepth()]++;
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

}
