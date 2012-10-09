package edu.berkeley.nlp.PCFGLA;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import edu.berkeley.nlp.PCFGLA.smoothing.Smoother;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.Indexer;
import edu.berkeley.nlp.util.Numberer;
import edu.berkeley.nlp.util.ScalingTools;

/**
 * Simple default implementation of a lexicon, which scores word, tag pairs with
 * P(word|tag)
 * 
 * instead of dealing with words as strings we will map them to integers with a
 * wordIndexer. to further simplify things each tag will have its own mapping
 * from wordIndex to a tag-specific index so that we don't have to deal with
 * unobserved events
 * 
 * assumes that rare words have been replaced with some unknown word token
 */
public class SimpleLexicon implements java.io.Serializable, Lexicon {
	public IntegerIndexer[] tagWordIndexer;
	public double[][][] expectedCounts; // indexed by tag, substate, word
	public double[][][] scores; // indexed by tag, word, substate, substate

	public int[] wordCounter; // how many times each word occured
	// public boolean[] wordIsAmbiguous;

	/**
	 * A trick to allow loading of saved Lexicons even if the version has
	 * changed.
	 */
	private static final long serialVersionUID = 2L;
	/** The number of substates for each state */
	public short[] numSubStates;
	int numStates;
	int nWords;

	double threshold;
	boolean isLogarithmMode;
	boolean useVarDP = false;

	public Indexer<String> wordIndexer;
	Smoother smoother;

	// additions from the stanford parser which are needed for a better
	// unknown word model...
	/**
	 * We cache the last signature looked up, because it asks for the same one
	 * many times when an unknown word is encountered! (Note that under the
	 * current scheme, one unknown word, if seen sentence-initially and
	 * non-initially, will be parsed with two different signatures....)
	 */
	protected transient String lastSignature = "";
	protected transient int lastSentencePosition = -1;
	protected transient String lastWordToSignaturize = "";
	private int unknownLevel = 5; // different modes for unknown words, 5 is
									// english specific

	public void optimize() {
		for (int tag = 0; tag < expectedCounts.length; tag++) {
			for (int substate = 0; substate < numSubStates[tag]; substate++) {
				double mass = ArrayUtil.sum(expectedCounts[tag][substate]);

				double normalizer = (mass == 0) ? 0 : 1.0 / mass;
				for (int word = 0; word < expectedCounts[tag][substate].length; word++) {
					scores[tag][substate][word] = expectedCounts[tag][substate][word]
							* normalizer;
				}
			}
		}

		// smooth the scores
		if (smoother != null) {
			for (short tag = 0; tag < expectedCounts.length; tag++) {
				for (int word = 0; word < expectedCounts[tag][0].length; word++) {
					double[] res = new double[numSubStates[tag]];
					for (int substate = 0; substate < numSubStates[tag]; substate++) {
						res[substate] = scores[tag][substate][word];
					}
					smoother.smooth(tag, res);
					for (int substate = 0; substate < numSubStates[tag]; substate++) {
						scores[tag][substate][word] = res[substate];
					}
				}
			}
		}
	}

	/**
	 * Create a blank Lexicon object. Fill it by calling tallyStateSetTree for
	 * each training tree, then calling optimize().
	 * 
	 * @param numSubStates
	 */
	@SuppressWarnings("unchecked")
	public SimpleLexicon(short[] numSubStates, int smoothingCutoff,
			double[] smoothParam, Smoother smoother, double threshold,
			StateSetTreeList trainTrees) {
		this(numSubStates, threshold);
		init(trainTrees);
	}

	public SimpleLexicon(short[] numSubStates, double threshold) {
		this.numSubStates = numSubStates;
		this.threshold = threshold;
		this.wordIndexer = new Indexer<String>();
		this.numStates = numSubStates.length;
		this.isLogarithmMode = false;
		if (Corpus.myTreebank != Corpus.TreeBankType.WSJ
				|| Corpus.myTreebank == Corpus.TreeBankType.BROWN)
			unknownLevel = 4;

	}

	public double[] score(String word, short tag, int pos, boolean noSmoothing,
			boolean isSignature) {
		StateSet stateSet = new StateSet(tag, (short) 1, word, (short) pos,
				(short) (pos + 1));
		stateSet.wordIndex = -2;
		stateSet.sigIndex = -2;
		return score(stateSet, tag, noSmoothing, isSignature);
	}

	public double[] score(StateSet stateSet, short tag, boolean noSmoothing,
			boolean isSignature) {
		double[] res = new double[numSubStates[tag]];
		int globalWordIndex = stateSet.wordIndex;
		if (globalWordIndex == -2)
			globalWordIndex = stateSet.wordIndex = wordIndexer.indexOf(stateSet
					.getWord());
		if (globalWordIndex == -1)
			globalWordIndex = stateSet.sigIndex;
		if (globalWordIndex == -2)
			globalWordIndex = stateSet.sigIndex = wordIndexer
					.indexOf(getSignature(stateSet.getWord(), stateSet.from));
		if (globalWordIndex == -1) {
			System.out.println("unknown signature for word "
					+ stateSet.getWord());
			Arrays.fill(res, 0.001);
			return res;
		}

		int tagSpecificWordIndex = tagWordIndexer[tag].indexOf(globalWordIndex);
		if (tagSpecificWordIndex == -1) {
			if (isLogarithmMode)
				Arrays.fill(res, Double.NEGATIVE_INFINITY);// -80??Double.NEGATIVE_INFINITY);
				// else Arrays.fill(res, 1e-80);
			return res;
		}
		for (int i = 0; i < numSubStates[tag]; i++) {
			res[i] = scores[tag][i][tagSpecificWordIndex];
		}
		if (smoother != null)
			smoother.smooth(tag, res);
		return res;
	}

	/**
	 * Trains this lexicon on the Collection of trees.
	 */
	public void trainTree(Tree<StateSet> trainTree, double randomness,
			Lexicon oldLexicon, boolean secondHalf, boolean noSmoothing,
			int unusedUnkThreshold) {
		// scan data
		// for all substates that the word's preterminal tag has
		double sentenceScore = 0;
		if (randomness == -1) {
			sentenceScore = trainTree.getLabel().getIScore(0);
			if (sentenceScore == 0) {
				System.out
						.println("Something is wrong with this tree. I will skip it.");
				return;
			}
		}
		int sentenceScale = trainTree.getLabel().getIScale();

		List<StateSet> words = trainTree.getYield();
		List<StateSet> tags = trainTree.getPreTerminalYield();
		// for all words in sentence
		for (int position = 0; position < words.size(); position++) {

			int nSubStates = tags.get(position).numSubStates();
			short tag = tags.get(position).getState();

			String word = words.get(position).getWord();
			int globalWordIndex = wordIndexer.indexOf(word);
			int tagSpecificWordIndex = tagWordIndexer[tag]
					.indexOf(globalWordIndex);

			double[] oldLexiconScores = null;
			if (randomness == -1)
				oldLexiconScores = oldLexicon.score(word, tag, position,
						noSmoothing, false);

			StateSet currentState = tags.get(position);
			double scale = ScalingTools.calcScaleFactor(currentState
					.getOScale() - sentenceScale)
					/ sentenceScore;

			for (short substate = 0; substate < nSubStates; substate++) {
				double weight = 1;
				if (randomness == -1) {
					// weight by the probability of seeing the tag and word
					// together, given the sentence
					if (!Double.isInfinite(scale))
						weight = currentState.getOScore(substate)
								* oldLexiconScores[substate] * scale;
					else
						weight = Math.exp(Math.log(ScalingTools.SCALE)
								* (currentState.getOScale() - sentenceScale)
								- Math.log(sentenceScore)
								+ Math.log(currentState.getOScore(substate))
								+ Math.log(oldLexiconScores[substate]));
				} else if (randomness == 0) {
					// for the baseline
					weight = 1;
				} else {
					// add a bit of randomness
					weight = GrammarTrainer.RANDOM.nextDouble() * randomness
							/ 100.0 + 1.0;
				}
				if (weight == 0)
					continue;
				// tally in the tag with the given weight

				expectedCounts[tag][substate][tagSpecificWordIndex] += weight;
			}
		}

	}

	public void setUseVarDP(boolean useVarDP) {
		this.useVarDP = useVarDP;
	}

	/*
	 * assume that rare words have been replaced by their signature
	 */
	public void init(StateSetTreeList trainTrees) {
		for (Tree<StateSet> tree : trainTrees) {
			List<StateSet> words = tree.getYield();
			for (StateSet word : words) {
				String sig = word.getWord();
				wordIndexer.add(sig);
			}
		}
		tagWordIndexer = new IntegerIndexer[numStates];
		for (int tag = 0; tag < numStates; tag++) {
			tagWordIndexer[tag] = new IntegerIndexer(wordIndexer.size());
		}
		wordCounter = new int[wordIndexer.size()];
		for (Tree<StateSet> tree : trainTrees) {
			List<StateSet> tags = tree.getPreTerminalYield();
			List<StateSet> words = tree.getYield();
			int ind = 0;
			for (StateSet word : words) {
				String sig = word.getWord();
				wordCounter[wordIndexer.indexOf(sig)]++;
				tagWordIndexer[tags.get(ind).getState()].add(wordIndexer
						.indexOf(sig));
				ind++;
			}
		}
		expectedCounts = new double[numStates][][];
		scores = new double[numStates][][];
		for (int tag = 0; tag < numStates; tag++) {
			expectedCounts[tag] = new double[numSubStates[tag]][tagWordIndexer[tag]
					.size()];
			scores[tag] = new double[numSubStates[tag]][tagWordIndexer[tag]
					.size()];
		}
		nWords = wordIndexer.size();
		labelTrees(trainTrees);
	}

	public SimpleLexicon copyLexicon() {
		SimpleLexicon copy = new SimpleLexicon(numSubStates, threshold);
		copy.expectedCounts = new double[numStates][][];
		copy.scores = ArrayUtil.clone(scores);// new double[numStates][][];
		copy.tagWordIndexer = new IntegerIndexer[numStates];
		copy.wordIndexer = this.wordIndexer;
		for (int tag = 0; tag < numStates; tag++) {
			copy.tagWordIndexer[tag] = tagWordIndexer[tag].copy();
			copy.expectedCounts[tag] = new double[numSubStates[tag]][tagWordIndexer[tag]
					.size()];
			// copy.scores[tag] = new
			// double[numSubStates[tag]][tagWordIndexer[tag].size()];
		}
		copy.nWords = this.nWords;
		copy.smoother = this.smoother;
		copy.wordCounter = this.wordCounter.clone();
		// copy.wordIsAmbiguous = this.wordIsAmbiguous.clone();
		// copy.unkIndex = unkIndex;
		/*
		 * if (linearIndex!=null) copy.linearIndex =
		 * ArrayUtil.clone(linearIndex); if (toBeIgnored!=null) copy.toBeIgnored
		 * = toBeIgnored.clone();
		 */
		return copy;
	}

	public boolean isLogarithmMode() {
		return isLogarithmMode;
	}

	public void logarithmMode() {
		if (isLogarithmMode)
			return;
		for (int tag = 0; tag < scores.length; tag++) {
			for (int word = 0; word < scores[tag].length; word++) {
				for (int substate = 0; substate < scores[tag][word].length; substate++) {
					scores[tag][word][substate] = Math
							.log(scores[tag][word][substate]);
				}
			}
		}
		isLogarithmMode = true;
	}

	/**
	 * Split all substates in two, producing a new lexicon. The new Lexicon
	 * gives the same scores to words under both split versions of the tag.
	 * (Leon says: It may not be okay to use the same scores, but I think that
	 * symmetry is sufficiently broken in Grammar.splitAllStates to ignore the
	 * randomness here.)
	 * 
	 * @param randomness
	 *            (currently ignored)
	 * @param mode
	 *            0 or 1: previous value plus noise 2: just noise (for
	 *            log-linear grammars with cascading regularization)
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public SimpleLexicon splitAllStates(int[] counts,
			boolean moreSubstatesThanCounts, int mode) {
		SimpleLexicon splitLex = this.copyLexicon();

		short[] newNumSubStates = new short[numSubStates.length];
		newNumSubStates[0] = 1; // never split ROOT
		Random random = GrammarTrainer.RANDOM;
		for (short i = 1; i < numSubStates.length; i++) {
			// don't split a state into more substates than times it was
			// actaully seen
			// if (!moreSubstatesThanCounts && numSubStates[i]>=counts[i]) {
			// newNumSubStates[i]=numSubStates[i];
			// }
			// else{
			newNumSubStates[i] = (short) (numSubStates[i] * 2);
			// }
		}
		splitLex.numSubStates = newNumSubStates;
		double[][][] newScores = new double[scores.length][][];
		double[][][] newExpCounts = new double[scores.length][][];
		for (int tag = 0; tag < expectedCounts.length; tag++) {
			int nTagWords = tagWordIndexer[tag].size();
			// if (nWords==0) continue;
			newScores[tag] = new double[newNumSubStates[tag]][nTagWords];
			newExpCounts[tag] = new double[newNumSubStates[tag]][nTagWords];
			for (int substate = 0; substate < numSubStates[tag]; substate++) {
				for (int word = 0; word < expectedCounts[tag][substate].length; word++) {
					newScores[tag][2 * substate][word] = newScores[tag][2 * substate + 1][word] = scores[tag][substate][word];
					if (mode == 2)
						newScores[tag][2 * substate][word] = newScores[tag][2 * substate + 1][word] = 1.0 + random
								.nextDouble() / 100.0;
				}
			}
		}
		splitLex.scores = newScores;
		splitLex.expectedCounts = newExpCounts;
		return splitLex;
	}

	/**
	 * This routine returns a String that is the "signature" of the class of a
	 * word. For, example, it might represent whether it is a number of ends in
	 * -s. The strings returned by convention match the pattern UNK-.* , which
	 * is just assumed to not match any real word. Behavior depends on the
	 * unknownLevel (-uwm flag) passed in to the class. The recognized numbers
	 * are 1-5: 5 is fairly English-specific; 4, 3, and 2 look for various word
	 * features (digits, dashes, etc.) which are only vaguely English-specific;
	 * 1 uses the last two characters combined with a simple classification by
	 * capitalization.
	 * 
	 * @param word
	 *            The word to make a signature for
	 * @param loc
	 *            Its position in the sentence (mainly so sentence-initial
	 *            capitalized words can be treated differently)
	 * @return A String that is its signature (equivalence class)
	 */
	public String getNewSignature(String word, int loc) {
		// int unknownLevel = Options.get().useUnknownWordSignatures;
		StringBuffer sb = new StringBuffer("UNK");
		switch (unknownLevel) {

		case 5: {
			// Reformed Mar 2004 (cdm); hopefully much better now.
			// { -CAPS, -INITC ap, -LC lowercase, 0 } +
			// { -KNOWNLC, 0 } + [only for INITC]
			// { -NUM, 0 } +
			// { -DASH, 0 } +
			// { -last lowered char(s) if known discriminating suffix, 0}
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
			String lowered = word.toLowerCase();
			if (Character.isUpperCase(ch0) || Character.isTitleCase(ch0)) {
				if (loc == 0 && numCaps == 1) {
					sb.append("-INITC");
					if (isKnown(lowered)) {
						sb.append("-KNOWNLC");
					}
				} else {
					sb.append("-CAPS");
				}
			} else if (!Character.isLetter(ch0) && numCaps > 0) {
				sb.append("-CAPS");
			} else if (hasLower) { // (Character.isLowerCase(ch0)) {
				sb.append("-LC");
			}
			if (hasDigit) {
				sb.append("-NUM");
			}
			if (hasDash) {
				sb.append("-DASH");
			}
			if (lowered.endsWith("s") && wlen >= 3) {
				// here length 3, so you don't miss out on ones like 80s
				char ch2 = lowered.charAt(wlen - 2);
				// not -ess suffixes or greek/latin -us, -is
				if (ch2 != 's' && ch2 != 'i' && ch2 != 'u') {
					sb.append("-s");
				}
			} else if (word.length() >= 5 && !hasDash
					&& !(hasDigit && numCaps > 0)) {
				// don't do for very short words;
				// Implement common discriminating suffixes
				/*
				 * if (Corpus.myLanguage==Corpus.GERMAN){
				 * sb.append(lowered.substring(lowered.length()-1)); }else{
				 */
				if (lowered.endsWith("ed")) {
					sb.append("-ed");
				} else if (lowered.endsWith("ing")) {
					sb.append("-ing");
				} else if (lowered.endsWith("ion")) {
					sb.append("-ion");
				} else if (lowered.endsWith("er")) {
					sb.append("-er");
				} else if (lowered.endsWith("est")) {
					sb.append("-est");
				} else if (lowered.endsWith("ly")) {
					sb.append("-ly");
				} else if (lowered.endsWith("ity")) {
					sb.append("-ity");
				} else if (lowered.endsWith("y")) {
					sb.append("-y");
				} else if (lowered.endsWith("al")) {
					sb.append("-al");
					// } else if (lowered.endsWith("ble")) {
					// sb.append("-ble");
					// } else if (lowered.endsWith("e")) {
					// sb.append("-e");
				}
			}
			break;
		}

		case 4: {
			boolean hasDigit = false;
			boolean hasNonDigit = false;
			boolean hasLetter = false;
			boolean hasLower = false;
			boolean hasDash = false;
			boolean hasPeriod = false;
			boolean hasComma = false;
			for (int i = 0; i < word.length(); i++) {
				char ch = word.charAt(i);
				if (Character.isDigit(ch)) {
					hasDigit = true;
				} else {
					hasNonDigit = true;
					if (Character.isLetter(ch)) {
						hasLetter = true;
						if (Character.isLowerCase(ch)
								|| Character.isTitleCase(ch)) {
							hasLower = true;
						}
					} else {
						if (ch == '-') {
							hasDash = true;
						} else if (ch == '.') {
							hasPeriod = true;
						} else if (ch == ',') {
							hasComma = true;
						}
					}
				}
			}
			// 6 way on letters
			if (Character.isUpperCase(word.charAt(0))
					|| Character.isTitleCase(word.charAt(0))) {
				if (!hasLower) {
					sb.append("-AC");
				} else if (loc == 0) {
					sb.append("-SC");
				} else {
					sb.append("-C");
				}
			} else if (hasLower) {
				sb.append("-L");
			} else if (hasLetter) {
				sb.append("-U");
			} else {
				// no letter
				sb.append("-S");
			}
			// 3 way on number
			if (hasDigit && !hasNonDigit) {
				sb.append("-N");
			} else if (hasDigit) {
				sb.append("-n");
			}
			// binary on period, dash, comma
			if (hasDash) {
				sb.append("-H");
			}
			if (hasPeriod) {
				sb.append("-P");
			}
			if (hasComma) {
				sb.append("-C");
			}
			if (word.length() > 3) {
				// don't do for very short words: "yes" isn't an "-es" word
				// try doing to lower for further densening and skipping digits
				char ch = word.charAt(word.length() - 1);
				if (Character.isLetter(ch)) {
					sb.append("-");
					sb.append(Character.toLowerCase(ch));
				}
			}
			break;
		}

		case 3: {
			// This basically works right, except note that 'S' is applied to
			// all
			// capitalized letters in first word of sentence, not just first....
			sb.append("-");
			char lastClass = '-'; // i.e., nothing
			char newClass;
			int num = 0;
			for (int i = 0; i < word.length(); i++) {
				char ch = word.charAt(i);
				if (Character.isUpperCase(ch) || Character.isTitleCase(ch)) {
					if (loc == 0) {
						newClass = 'S';
					} else {
						newClass = 'L';
					}
				} else if (Character.isLetter(ch)) {
					newClass = 'l';
				} else if (Character.isDigit(ch)) {
					newClass = 'd';
				} else if (ch == '-') {
					newClass = 'h';
				} else if (ch == '.') {
					newClass = 'p';
				} else {
					newClass = 's';
				}
				if (newClass != lastClass) {
					lastClass = newClass;
					sb.append(lastClass);
					num = 1;
				} else {
					if (num < 2) {
						sb.append('+');
					}
					num++;
				}
			}
			if (word.length() > 3) {
				// don't do for very short words: "yes" isn't an "-es" word
				// try doing to lower for further densening and skipping digits
				char ch = Character.toLowerCase(word.charAt(word.length() - 1));
				sb.append('-');
				sb.append(ch);
			}
			break;
		}

		case 2: {
			// {-ALLC, -INIT, -UC, -LC, zero} +
			// {-DASH, zero} +
			// {-NUM, -DIG, zero} +
			// {lowerLastChar, zeroIfShort}
			boolean hasDigit = false;
			boolean hasNonDigit = false;
			boolean hasLower = false;
			for (int i = 0; i < word.length(); i++) {
				char ch = word.charAt(i);
				if (Character.isDigit(ch)) {
					hasDigit = true;
				} else {
					hasNonDigit = true;
					if (Character.isLetter(ch)) {
						if (Character.isLowerCase(ch)
								|| Character.isTitleCase(ch)) {
							hasLower = true;
						}
					}
				}
			}
			if (Character.isUpperCase(word.charAt(0))
					|| Character.isTitleCase(word.charAt(0))) {
				if (!hasLower) {
					sb.append("-ALLC");
				} else if (loc == 0) {
					sb.append("-INIT");
				} else {
					sb.append("-UC");
				}
			} else if (hasLower) { // if (Character.isLowerCase(word.charAt(0)))
									// {
				sb.append("-LC");
			}
			// no suffix = no (lowercase) letters
			if (word.indexOf('-') >= 0) {
				sb.append("-DASH");
			}
			if (hasDigit) {
				if (!hasNonDigit) {
					sb.append("-NUM");
				} else {
					sb.append("-DIG");
				}
			} else if (word.length() > 3) {
				// don't do for very short words: "yes" isn't an "-es" word
				// try doing to lower for further densening and skipping digits
				char ch = word.charAt(word.length() - 1);
				sb.append(Character.toLowerCase(ch));
			}
			// no suffix = short non-number, non-alphabetic
			break;
		}

		default:
			sb.append("-");
			sb.append(word.substring(Math.max(word.length() - 2, 0),
					word.length()));
			sb.append("-");
			if (Character.isLowerCase(word.charAt(0))) {
				sb.append("LOWER");
			} else {
				if (Character.isUpperCase(word.charAt(0))) {
					if (loc == 0) {
						sb.append("INIT");
					} else {
						sb.append("UPPER");
					}
				} else {
					sb.append("OTHER");
				}
			}
		} // end switch (unknownLevel)
			// System.err.println("Summarized " + word + " to " +
			// sb.toString());
		return sb.toString();
	} // end getSignature()

	@Override
	public String toString() {
		StringBuffer sb = new StringBuffer();
		Numberer tagNumberer = Numberer.getGlobalNumberer("tags");
		for (int tag = 0; tag < expectedCounts.length; tag++) {
			String tagS = (String) tagNumberer.object(tag);
			if (tagWordIndexer[tag].size() == 0)
				continue;
			for (int word = 0; word < scores[tag][0].length; word++) {
				sb.append(tagS + " "
						+ wordIndexer.get(tagWordIndexer[tag].get(word)) + " ");
				for (int sub = 0; sub < numSubStates[tag]; sub++) {
					sb.append(" " + scores[tag][sub][word]);
				}
				sb.append("\n");
			}
		}
		return sb.toString();
	}

	/**
	 * @param lowered
	 * @return
	 */
	private boolean isKnown(String word) {
		return wordIndexer.indexOf(word) != -1;
	}

	/**
	 * Returns the index of the signature of the word numbered wordIndex, where
	 * the signature is the String representation of unknown word features.
	 * Caches the last signature index returned.
	 */
	public String getSignature(String word, int sentencePosition) {
		if (word.equals(lastWordToSignaturize)
				&& sentencePosition == lastSentencePosition) {
			// System.err.println("Signature: cache mapped " + wordIndex +
			// " to " + lastSignatureIndex);
			return lastSignature;
		} else {
			String uwSig = getNewSignature(word, sentencePosition);
			lastSignature = uwSig;
			lastSentencePosition = sentencePosition;
			lastWordToSignaturize = word;
			return uwSig;
		}
	}

	/**
	 * @param mergeThesePairs
	 * @param mergeWeights
	 */
	public void mergeStates(boolean[][][] mergeThesePairs,
			double[][] mergeWeights) {
		short[] newNumSubStates = new short[numSubStates.length];
		short[][] mapping = new short[numSubStates.length][];
		// invariant: if partners[state][substate][0] == substate, it's the 1st
		// one
		short[][][] partners = new short[numSubStates.length][][];
		Grammar.calculateMergeArrays(mergeThesePairs, newNumSubStates, mapping,
				partners, numSubStates);

		double[][][] newScores = new double[scores.length][][];
		for (int tag = 0; tag < expectedCounts.length; tag++) {
			int nTagWords = tagWordIndexer[tag].size();
			newScores[tag] = new double[newNumSubStates[tag]][nTagWords];
			if (numSubStates[tag] == 1)
				continue;
			for (int word = 0; word < expectedCounts[tag][0].length; word++) {
				for (int i = 0; i < numSubStates[tag]; i = i + 2) {
					int nSplit = partners[tag][i].length;
					if (nSplit == 2) {
						double mergeWeightSum = mergeWeights[tag][partners[tag][i][0]]
								+ mergeWeights[tag][partners[tag][i][1]];
						if (mergeWeightSum == 0)
							mergeWeightSum = 1;
						newScores[tag][mapping[tag][i]][word] = ((mergeWeights[tag][partners[tag][i][0]] * scores[tag][partners[tag][i][0]][word]) + (mergeWeights[tag][partners[tag][i][1]] * scores[tag][partners[tag][i][1]][word]))
								/ mergeWeightSum;
					} else {
						newScores[tag][mapping[tag][i]][word] = scores[tag][i][word];
						newScores[tag][mapping[tag][i + 1]][word] = scores[tag][i + 1][word];
					}
				}
			}
		}
		this.numSubStates = newNumSubStates;
		this.scores = newScores;
		for (int tag = 0; tag < numStates; tag++) {
			this.expectedCounts[tag] = new double[newNumSubStates[tag]][tagWordIndexer[tag]
					.size()];
		}
	}

	public void removeUnlikelyTags(double threshold, double exponent) {
		for (int tag = 0; tag < scores.length; tag++) {
			for (int word = 0; word < scores[tag].length; word++) {
				for (int substate = 0; substate < scores[tag][word].length; substate++) {
					double p = scores[tag][word][substate];
					/*
					 * if (p<threshold) p = 0; else
					 */if (exponent != 1.0)
						p = Math.pow(p, exponent);
					scores[tag][word][substate] = p;
				}
			}
		}
	}

	// public void logarithmMode() {
	// logarithmMode = true;
	// }
	//
	// public boolean isLogarithmMode() {
	// return logarithmMode;
	// }

	public SimpleLexicon projectLexicon(double[] condProbs, int[][] mapping,
			int[][] toSubstateMapping) {
		short[] newNumSubStates = new short[numSubStates.length];
		for (int state = 0; state < numSubStates.length; state++) {
			newNumSubStates[state] = (short) toSubstateMapping[state][0];
		}
		SimpleLexicon newLexicon = this.copyLexicon();

		double[][][] newScores = new double[scores.length][][];

		for (short tag = 0; tag < expectedCounts.length; tag++) {
			newScores[tag] = new double[newNumSubStates[tag]][expectedCounts[tag][0].length];
			for (int word = 0; word < expectedCounts[tag][0].length; word++) {
				for (int substate = 0; substate < numSubStates[tag]; substate++) {
					newScores[tag][toSubstateMapping[tag][substate + 1]][word] += condProbs[mapping[tag][substate]]
							* scores[tag][substate][word];
				}
			}
		}
		newLexicon.numSubStates = newNumSubStates;
		newLexicon.scores = newScores;
		return newLexicon;
	}

	public Smoother getSmoother() {
		return smoother;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.HDPPCFG.LexiconInterface#getSmoothingParams()
	 */
	public double[] getSmoothingParams() {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.HDPPCFG.LexiconInterface#logarithmMode()
	 */

	public void setSmoother(Smoother smoother) {
		this.smoother = smoother;
	}

	public double getPruningThreshold() {
		return threshold;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.PCFGLA.Lexicon#scoreSignature(java.lang.String,
	 * short, int)
	 */
	public double[] scoreSignature(StateSet stateSet, int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.PCFGLA.Lexicon#scoreWord(java.lang.String, short)
	 */
	public double[] scoreWord(StateSet stateSet, int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	public void labelTrees(StateSetTreeList trainTrees) {
		for (Tree<StateSet> tree : trainTrees) {
			List<StateSet> words = tree.getYield();
			// List<StateSet> tags = tree.getPreTerminalYield();
			// int ind = 0;
			for (StateSet word : words) {
				word.wordIndex = wordIndexer.indexOf(word.getWord());
				word.sigIndex = -1;
				// short tag = tags.get(ind).getState();
				// // if (wordIsAmbiguous[word.wordIndex]) {
				// String sig = getSignature(word.getWord(), ind);
				// wordIndexer.add(sig);
				// word.sigIndex = (short)wordIndexer.indexOf(sig);
				// tagWordIndexer[tag].add(wordIndexer.indexOf(sig));
				// // }
				// // else { word.sigIndex = -1; }
				// ind++;
			}
		}

	}

	/*
	 * public void clearMapping() { toBeIgnored = null; linearIndex = null; }
	 */
	public static class IntegerIndexer implements Serializable {
		private int[] indexTo;
		private int[] indexFrom;
		private int n;

		IntegerIndexer(int capacity) {
			indexTo = new int[capacity];
			indexFrom = new int[capacity];
			Arrays.fill(indexTo, -1);
			Arrays.fill(indexFrom, -1);
			n = 0;
		}

		public void add(int i) {
			if (i == -1)
				return;
			if (indexTo[i] == -1) {
				indexTo[i] = n;
				indexFrom[n] = i;
				n++;
			}
		}

		public int get(int i) {
			if (i < indexFrom.length)
				return indexFrom[i];
			else
				return -1;
		}

		public int indexOf(int i) {
			if (i < indexTo.length)
				return indexTo[i];
			else
				return -1;
		}

		public int size() {
			return n;
		}

		public IntegerIndexer copy() {
			IntegerIndexer copy = new IntegerIndexer(indexFrom.length);
			copy.n = n;
			copy.indexFrom = this.indexFrom.clone();
			copy.indexTo = this.indexTo.clone();
			return copy;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.PCFGLA.Lexicon#computeScores()
	 */
	public void explicitlyComputeScores(int finalLevel) {
		// TODO Auto-generated method stub

	}

	public Counter<String> getWordCounter() {
		return null;
	}

	public void tieRareWordStats(int threshold) {
		return;
	}

}
