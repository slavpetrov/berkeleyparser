package edu.berkeley.nlp.PCFGLA;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.berkeley.nlp.PCFGLA.smoothing.Smoother;
import edu.berkeley.nlp.math.SloppyMath;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.Numberer;
import edu.berkeley.nlp.util.PriorityQueue;
import edu.berkeley.nlp.util.ScalingTools;

/**
 * Simple default implementation of a lexicon, which scores word, tag pairs with
 * a smoothed estimate of P(tag|word)/P(tag).
 * 
 * for simplicity the lexicon will store words and tags as strings, while the
 * grammar will be using integers -> Numberer()
 */
public class SophisticatedLexicon implements java.io.Serializable, Lexicon {
	/** A count of strings with tags. Indexed by state, word, and substate. */
	HashMap<String, double[]>[] wordToTagCounters = null;
	HashMap<String, double[]>[] unseenWordToTagCounters = null;
	double totalWordTypes = 0.0;
	double totalTokens = 0.0;
	double totalUnseenTokens = 0.0;
	double totalWords = 0.0;
	/**
	 * A count of how many different words each full tag has been seen with.
	 * Indexed by state and substate
	 */
	double[][] typeTagCounter;
	/**
	 * A count of tag (state + subState) occurrences. Indexed by state and
	 * substate
	 */
	double[][] tagCounter;
	double[][] unseenTagCounter;
	double[] simpleTagCounter;
	/** The set of preterminal tags */
	Set<Short> allTags = new HashSet<Short>();
	/** The count of how often each word as been seen */
	Counter<String> wordCounter = new Counter<String>();
	/**
	 * A trick to allow loading of saved Lexicons even if the version has
	 * changed.
	 */
	private static final long serialVersionUID = 2L;
	/** The number of substates for each state */
	short[] numSubStates;

	/** Word-tag pairs that occur less are smoothed. */
	int smoothingCutoff;
	/** The default smoothing cutoff. */
	public static int DEFAULT_SMOOTHING_CUTOFF = 10;
	/** Add X smoothing for P(word) */
	double addXSmoothing = 1.0;

	Smoother smoother;
	double threshold;

	boolean isConditional;
	double[][][] conditionalWeights; // wordIndex, tag, substate -> weight
	Numberer wordNumberer;

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
	/**
	 * A POS tag has to have been attributed to more than this number of word
	 * types before it is regarded as an open-class tag. Unknown words will only
	 * possibly be tagged as open-class tags (unless flexiTag is on).
	 */
	public static int openClassTypesThreshold = 50;

	/**
	 * Start to aggregate signature-tag pairs only for words unseen in the first
	 * this fraction of the data.
	 */
	public static double fractionBeforeUnseenCounting = 0.5; // -> secondHalf
	// protected transient Set<IntTaggedWord> sigs=new HashSet<IntTaggedWord>();
	/**
	 * Has counts for taggings in terms of unseen signatures. The IntTagWords
	 * are for (tag,sig), (tag,null), (null,sig), (null,null). (None for basic
	 * UNK if there are signatures.)
	 */
	protected static final int nullWord = -1;
	protected static final short nullTag = -1;
	double smoothInUnknownsThreshold = 100;
	double[] smooth = null; // {1.0, 1.0};

	/**
	 * If logarithmMode is true, then all scores are returned as log
	 * probabilities. Otherwise, they are returned as probabilities.
	 */
	boolean logarithmMode = false;

	/** Get the nonterminal tags */
	public Set<Short> getAllTags() {
		return allTags;
	}

	public boolean isKnown(String word) {
		return wordCounter.keySet().contains(word);
	}

	public void writeData(Writer w) throws IOException {
		PrintWriter out = new PrintWriter(w);
		Numberer n = Numberer.getGlobalNumberer("tags");

		// word counter (c_W)
		out.print("WORD-COUNTER (c_W):\n");
		PriorityQueue<String> pq = wordCounter.asPriorityQueue();
		while (pq.hasNext()) {
			int priority = (int) Math.round(pq.getPriority());
			String element = pq.next();
			out.print(element + " " + priority + "\n");
		}

		out.print("--------------------------------------------------\n");
		out.print("TAG-COUNTER (c_T):\n");
		for (int state = 0; state < tagCounter.length; state++) {
			String tagState = (String) n.object(state);
			for (int substate = 0; substate < tagCounter[state].length; substate++) {
				double prob = tagCounter[state][substate];
				if (prob == 0)
					continue;
				out.print(tagState + "_" + substate + " " + prob + "\n");
			}
		}

		out.print("--------------------------------------------------\n");
		out.print("UNSEEN-TAG-COUNTER (c_T):\n");
		for (int state = 0; state < unseenTagCounter.length; state++) {
			String tagState = (String) n.object(state);
			for (int substate = 0; substate < unseenTagCounter[state].length; substate++) {
				double prob = unseenTagCounter[state][substate];
				if (prob == 0)
					continue;
				out.print(tagState + "_" + substate + " " + prob + "\n");
			}
		}

		out.print("--------------------------------------------------\n");
		out.print("TAG-AND-WORD-COUNTER (c_TW):\n");
		for (int tag = 0; tag < wordToTagCounters.length; tag++) {
			if (wordToTagCounters[tag] == null)
				continue;
			String tagState = (String) n.object(tag);
			for (String word : wordToTagCounters[tag].keySet()) {
				out.print(tagState + " " + word + " "
						+ Arrays.toString(wordToTagCounters[tag].get(word))
						+ "\n");
			}
		}

		out.print("--------------------------------------------------\n");
		out.print("UNSEEN-TAG-AND-SIGNATURE-COUNTER (c_TW):\n");
		for (int tag = 0; tag < unseenWordToTagCounters.length; tag++) {
			if (unseenWordToTagCounters[tag] == null)
				continue;
			String tagState = (String) n.object(tag);
			for (String word : unseenWordToTagCounters[tag].keySet()) {
				out.print(tagState
						+ " "
						+ word
						+ " "
						+ Arrays.toString(unseenWordToTagCounters[tag]
								.get(word)) + "\n");
			}
		}

		out.flush();
	}

	public String toString() {
		Numberer n = Numberer.getGlobalNumberer("tags");
		StringBuilder sb = new StringBuilder();
		// word counter (c_W)
		/*
		 * sb.append("WORD-COUNTER (c_W):\n"); PriorityQueue<String> pq =
		 * wordCounter.asPriorityQueue(); while (pq.hasNext()) { int priority =
		 * (int)Math.round(pq.getPriority()); String element = pq.next();
		 * sb.append(element +" "+priority+"\n"); }
		 * 
		 * sb.append("--------------------------------------------------\n");
		 * sb.append("TAG-COUNTER (c_T):\n"); for (int state=0;
		 * state<tagCounter.length; state++){ String tagState =
		 * (String)n.object(state); for (int substate=0;
		 * substate<tagCounter[state].length; substate++){ double prob =
		 * tagCounter[state][substate]; if (prob==0) continue;
		 * sb.append(tagState +"_"+substate +" "+prob+"\n"); } }
		 * 
		 * sb.append("--------------------------------------------------\n");
		 * sb.append("UNSEEN-TAG-COUNTER (c_T):\n"); for (int state=0;
		 * state<unseenTagCounter.length; state++){ String tagState =
		 * (String)n.object(state); for (int substate=0;
		 * substate<unseenTagCounter[state].length; substate++){ double prob =
		 * unseenTagCounter[state][substate]; if (prob==0) continue;
		 * sb.append(tagState +"_"+substate +" "+prob+"\n"); } }
		 * 
		 * sb.append("--------------------------------------------------\n");
		 * sb.append("TAG-AND-WORD-COUNTER (c_TW):\n");
		 */for (int tag = 0; tag < wordToTagCounters.length; tag++) {
			String tagState = (String) n.object(tag);
			if (wordToTagCounters[tag] != null) {
				for (String word : wordToTagCounters[tag].keySet()) {
					double[] scores = score(word, (short) tag, 0, false, false);
					sb.append(tagState + " " + word + " "
							+ Arrays.toString(scores) + "\n");
				}
			}
			if (unseenWordToTagCounters[tag] != null) {
				for (String word : unseenWordToTagCounters[tag].keySet()) {
					double[] scores = score(word, (short) tag, 0, false, true);
					sb.append(tagState + " " + word + " "
							+ Arrays.toString(scores) + "\n");
				}
			}
		}

		/*
		 * sb.append("--------------------------------------------------\n");
		 * sb.append("UNSEEN-TAG-AND-SIGNATURE-COUNTER (c_TW):\n"); for (int
		 * tag=0; tag<unseenWordToTagCounters.length; tag++){ if
		 * (unseenWordToTagCounters[tag]==null) continue; String tagState =
		 * (String)n.object(tag); for (String word :
		 * unseenWordToTagCounters[tag].keySet()){
		 * sb.append(tagState+" "+word+" "
		 * +Arrays.toString(unseenWordToTagCounters[tag].get(word))+"\n"); } }
		 */

		return sb.toString();
	}

	public String toString_old() {
		String s = "";
		for (String w : wordCounter.keySet()) {
			s += w + "\n";
		}
		String t = "";
		/*
		 * for (int i = 1; i < numSubStates.length; i++){ if
		 * (wordToTagCounters[i]!=null) { t = t + "\nTAG:" + i; for (String w :
		 * wordToTagCounters[i].keySet()){ t = t +
		 * "\n"+w+": "+Arrays.toString(wordToTagCounters[i].get(w)); break; } }
		 * }
		 */
		return s + ArrayUtil.toString(tagCounter) + "\n" + t;
	}

	public void newMstep() {
		return;
		// // overwrite tagCounter to contain P(T)
		// double total = totalTokens + totalUnseenTokens;
		// for (int state=0; state<tagCounter.length; state++){
		// for (int substate=0; substate<tagCounter[state].length; substate++){
		// //tagCounter[state][substate] = (tagCounter[state][substate] +
		// unseenTagCounter[state][substate])/total;
		// }
		// }
		//
		//
		// // overwrite wordToTagCounters to contain P(W|T)
		// HashMap<String, double[]>[] probCounter = new
		// HashMap[numSubStates.length];
		// for (int tag=0; tag<wordToTagCounters.length; tag++){
		// double sum = 0;
		// if (wordToTagCounters[tag]==null) continue;
		// probCounter[tag] = new HashMap<String,double[]>();
		// for (String word : wordToTagCounters[tag].keySet()){
		// double[] probs = wordToTagCounters[tag].get(word);
		// for (int substate=0; substate<probs.length; substate++){
		// probs[substate] /= (tagCounter[tag][substate]);
		// sum += probs[substate];
		// }
		// probCounter[tag].put(word,probs);
		// }
		// if (unseenWordToTagCounters[tag]==null) continue;
		// for (String word : unseenWordToTagCounters[tag].keySet()){
		// double c_S = wordCounter.getCount(word);
		// double[] probs = unseenWordToTagCounters[tag].get(word);
		// for (int substate=0; substate<probs.length; substate++){
		// probs[substate] /= (tagCounter[tag][substate]*c_S);
		// sum += probs[substate];
		// }
		// probCounter[tag].put(word,probs);
		// }
		// /*for (String word : probCounter[tag].keySet()){
		// double[] probs = probCounter[tag].get(word);
		// for (int substate=0; substate<probs.length; substate++){
		// probs[substate] /= sum;
		// }
		// probCounter[tag].put(word,probs);
		// }*/
		// }
		// wordToTagCounters = probCounter;
	}

	public double[] score2(String word, short tag, int loc, boolean noSmoothing) {
		if (wordToTagCounters[tag] == null) // this is not a lexical category
			return new double[numSubStates[tag]];
		double[] resultArray = new double[numSubStates[tag]];
		double cW = wordCounter.getCount(word);
		if (cW > 0) {
			if (wordToTagCounters[tag] != null) { // this is a lexical category
				resultArray = wordToTagCounters[tag].get(word);
				if (resultArray != null) { // we have seen this word with this
											// tag
					return resultArray;
				}
			}
			return new double[numSubStates[tag]];
		}
		String sig = getCachedSignature(word, loc);
		resultArray = wordToTagCounters[tag].get(sig);
		if (resultArray != null) { // we have seen this signature with this tag
			return resultArray;
		}
		// we have never seen the word or its signature
		// System.err.println("We have never seen the word "+word+" or its signature "+sig+" with this tag. Returning prob 0.");
		return new double[numSubStates[tag]];

	}

	/**
	 * <p>
	 * This condenses counting arrays into essential statistics. It is used
	 * after all calls to tallyStateSetTree and before any getScore calls.
	 * <p>
	 * Currently the trees are taken into account immediately, so this does
	 * nothing, but in the future this may contain some precomputation
	 */
	public void optimize() {
		// make up the set of which tags are preterminal tags
		for (short i = 0; i < wordToTagCounters.length; i++) {
			if (wordToTagCounters[i] != null) {
				allTags.add(i);
			}
		}
		// remove the unlikely ones
		removeUnlikelyTags(threshold, -1.0);
		// // add MMT randomization if necessary
		// if
		// (randomInitializationType==Grammar.RandomInitializationType.INITIALIZE_LIKE_MMT)
		// {
		// Random r = new Random();
		// for (short tag=0; tag<wordToTagCounters.length; tag++) {
		// if (wordToTagCounters[tag]==null)
		// continue;
		// for (String word : wordToTagCounters[tag].keySet()) {
		// double[] localCounter = wordToTagCounters[tag].get(word);
		// if (localCounter==null)
		// continue;
		// for (short substate =0; substate<localCounter.length; substate++) {
		// double oldValue = localCounter[substate];
		// localCounter[substate] *= Grammar.generateMMTRandomNumber(r);
		// double delta = localCounter[substate]-oldValue;
		// //fix all other counters
		// wordCounter.incrementCount(word,delta);
		// tagCounter[tag][substate] += delta;
		// totalTokens += delta;
		// }
		// }
		// }
		// }
	}

	/**
	 * Create a blank Lexicon object. Fill it by calling tallyStateSetTree for
	 * each training tree, then calling optimize().
	 * 
	 * @param numSubStates
	 */
	@SuppressWarnings("unchecked")
	public SophisticatedLexicon(short[] numSubStates, int smoothingCutoff,
			double[] smoothParam, Smoother smoother, double threshold) {
		this.numSubStates = numSubStates;
		this.smoothingCutoff = smoothingCutoff;
		this.smooth = smoothParam;
		this.smoother = smoother;
		wordToTagCounters = new HashMap[numSubStates.length];
		unseenWordToTagCounters = new HashMap[numSubStates.length];
		tagCounter = new double[numSubStates.length][];
		unseenTagCounter = new double[numSubStates.length][];
		typeTagCounter = new double[numSubStates.length][];
		simpleTagCounter = new double[numSubStates.length];
		for (int i = 0; i < numSubStates.length; i++) {
			tagCounter[i] = new double[numSubStates[i]];
			unseenTagCounter[i] = new double[numSubStates[i]];
			typeTagCounter[i] = new double[numSubStates[i]];
		}
		this.threshold = threshold;
		this.wordNumberer = Numberer.getGlobalNumberer("words");
		if (!(Corpus.myTreebank == Corpus.TreeBankType.WSJ
				|| Corpus.myTreebank == Corpus.TreeBankType.BROWN || Corpus.myTreebank == Corpus.TreeBankType.SINGLEFILE))
			unknownLevel = 4;
	}

	public void printTagCounter(Numberer tagNumberer) {
		PriorityQueue<String> pq = new PriorityQueue<String>(tagCounter.length);
		for (int i = 0; i < tagCounter.length; i++) {
			pq.add((String) tagNumberer.object(i), tagCounter[i][0]);
			// System.out.println(i+". "+(String)tagNumberer.object(i)+"\t "+symbolCounter.getCount(i,0));
		}
		int i = 0;
		while (pq.hasNext()) {
			i++;
			int p = (int) pq.getPriority();
			System.out.println(i + ". " + pq.next() + "\t " + p);
		}
	}

	/**
	 * Split all substates in two, producing a new lexicon. The new Lexicon
	 * gives the same scores to words under both split versions of the tag.
	 * (Leon says: It may not be okay to use the same scores, but I think that
	 * symmetry is sufficiently broken in Grammar.splitAllStates to ignore the
	 * randomness here.)
	 * 
	 * @param randomness
	 *            , mode (currently ignored)
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public SophisticatedLexicon splitAllStates(int[] counts,
			boolean moreSubstatesThanCounts, int mode) {
		short[] newNumSubStates = new short[numSubStates.length];
		newNumSubStates[0] = 1; // never split ROOT
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
		SophisticatedLexicon lexicon = new SophisticatedLexicon(
				newNumSubStates, this.smoothingCutoff, smooth, smoother,
				this.threshold);
		// copy and alter all data structures
		lexicon.wordToTagCounters = new HashMap[numSubStates.length];
		lexicon.unseenWordToTagCounters = new HashMap[numSubStates.length];
		for (int tag = 0; tag < wordToTagCounters.length; tag++) {
			if (wordToTagCounters[tag] != null) {
				lexicon.wordToTagCounters[tag] = new HashMap<String, double[]>();
				for (String word : wordToTagCounters[tag].keySet()) {
					lexicon.wordToTagCounters[tag].put(word,
							new double[newNumSubStates[tag]]);
					for (int substate = 0; substate < wordToTagCounters[tag]
							.get(word).length; substate++) {
						int splitFactor = 2;
						if (newNumSubStates[tag] == numSubStates[tag]) {
							splitFactor = 1;
						}
						for (int i = 0; i < splitFactor; i++) {
							lexicon.wordToTagCounters[tag].get(word)[substate
									* splitFactor + i] = (1.f / splitFactor)
									* wordToTagCounters[tag].get(word)[substate];
						}
					}
				}
			}
		}
		for (int tag = 0; tag < unseenWordToTagCounters.length; tag++) {
			if (unseenWordToTagCounters[tag] != null) {
				lexicon.unseenWordToTagCounters[tag] = new HashMap<String, double[]>();
				for (String word : unseenWordToTagCounters[tag].keySet()) {
					lexicon.unseenWordToTagCounters[tag].put(word,
							new double[newNumSubStates[tag]]);
					for (int substate = 0; substate < unseenWordToTagCounters[tag]
							.get(word).length; substate++) {
						int splitFactor = 2;
						if (newNumSubStates[tag] == numSubStates[tag]) {
							splitFactor = 1;
						}
						for (int i = 0; i < splitFactor; i++) {
							lexicon.unseenWordToTagCounters[tag].get(word)[substate
									* splitFactor + i] = (1.f / splitFactor)
									* unseenWordToTagCounters[tag].get(word)[substate];
						}
					}
				}
			}
		}
		lexicon.totalWordTypes = totalWordTypes;
		lexicon.totalTokens = totalTokens;
		lexicon.totalUnseenTokens = totalUnseenTokens;
		lexicon.totalWords = totalWords;
		lexicon.smoother = smoother;
		lexicon.typeTagCounter = new double[typeTagCounter.length][];
		lexicon.tagCounter = new double[tagCounter.length][];
		lexicon.unseenTagCounter = new double[unseenTagCounter.length][];
		lexicon.simpleTagCounter = new double[tagCounter.length];
		for (int tag = 0; tag < typeTagCounter.length; tag++) {
			lexicon.typeTagCounter[tag] = new double[newNumSubStates[tag]];
			lexicon.tagCounter[tag] = new double[newNumSubStates[tag]];
			lexicon.unseenTagCounter[tag] = new double[newNumSubStates[tag]];
			lexicon.simpleTagCounter[tag] = simpleTagCounter[tag];
			for (int substate = 0; substate < typeTagCounter[tag].length; substate++) {
				int splitFactor = 2;
				if (newNumSubStates[tag] == numSubStates[tag]) {
					splitFactor = 1;
				}
				for (int i = 0; i < splitFactor; i++) {
					lexicon.typeTagCounter[tag][substate * splitFactor + i] = (1.f / splitFactor)
							* typeTagCounter[tag][substate];
					lexicon.tagCounter[tag][substate * splitFactor + i] = (1.f / splitFactor)
							* tagCounter[tag][substate];
					lexicon.unseenTagCounter[tag][substate * splitFactor + i] = (1.f / splitFactor)
							* unseenTagCounter[tag][substate];
				}
			}
		}
		lexicon.allTags = new HashSet<Short>(allTags);
		lexicon.wordCounter = new Counter<String>();
		for (String word : wordCounter.keySet()) {
			lexicon.wordCounter.setCount(word, wordCounter.getCount(word));
		}
		lexicon.smoothingCutoff = smoothingCutoff;
		lexicon.addXSmoothing = addXSmoothing;
		lexicon.smoothInUnknownsThreshold = smoothInUnknownsThreshold;

		lexicon.wordNumberer = wordNumberer;
		return lexicon;
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
	public String getSignature(String word, int loc) {
		// int unknownLevel = Options.get().useUnknownWordSignatures;
		StringBuffer sb = new StringBuffer("UNK");

		if (word.length() == 0)
			return sb.toString();

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

	public double[] score(StateSet stateSet, short tag, boolean noSmoothing,
			boolean isSignature) {
		return score(stateSet.getWord(), tag, stateSet.from, noSmoothing,
				isSignature);
	}

	/**
	 * Get the score of this word with this tag (as an IntTaggedWord) at this
	 * loc. (Presumably an estimate of P(word | tag).)
	 * <p>
	 * <i>Implementation documentation:</i> Seen: c_W = count(W) c_TW =
	 * count(T,W) c_T = count(T) c_Tunseen = count(T) among new words in 2nd
	 * half total = count(seen words) totalUnseen = count("unseen" words) p_T_U
	 * = Pmle(T|"unseen") pb_T_W = P(T|W). If (c_W > smoothInUnknownsThreshold)
	 * = c_TW/c_W Else (if not smart mutation) pb_T_W = bayes prior smooth[1]
	 * with p_T_U p_T= Pmle(T) p_W = Pmle(W) pb_W_T = pb_T_W * p_W / p_T [Bayes
	 * rule] Note that this doesn't really properly reserve mass to unknowns.
	 * 
	 * Unseen: c_TS = count(T,Sig|Unseen) c_S = count(Sig) c_T = count(T|Unseen)
	 * c_U = totalUnseen above p_T_U = Pmle(T|Unseen) pb_T_S = Bayes smooth of
	 * Pmle(T|S) with P(T|Unseen) [smooth[0]] pb_W_T = P(W|T) inverted
	 * 
	 * @param iTW
	 *            An IntTaggedWord pairing a word and POS tag
	 * @param loc
	 *            The position in the sentence. <i>In the default implementation
	 *            this is used only for unknown words to change their
	 *            probability distribution when sentence initial
	 * @return A double valued score, usually P(word|tag)
	 */
	public double[] score(String word, short tag, int loc, boolean noSmoothing,
			boolean isSignature) {
		if (isConditional)
			return scoreConditional(word, tag, loc, noSmoothing, isSignature);
		double c_W = wordCounter.getCount(word);
		double pb_W_T = 0; // always set below

		// simulate no smoothing
		// smooth[0] = 0.0; smooth[1] = 0.0;

		double[] resultArray = new double[numSubStates[tag]];

		for (int substate = 0; substate < numSubStates[tag]; substate++) {
			boolean seen = (c_W > 0.0);
			if (!isSignature && (seen || noSmoothing)) {
				// known word model for P(T|W)
				double c_tag = tagCounter[tag][substate];
				double c_T = c_tag;// seenCounter.getCount(iTW);
				if (c_T == 0)
					continue;

				double c_TW = 0;
				if (wordToTagCounters[tag] != null
						&& wordToTagCounters[tag].get(word) != null) {
					c_TW = wordToTagCounters[tag].get(word)[substate];
				}
				// if (c_TW==0) continue;

				double c_Tunseen = unseenTagCounter[tag][substate];
				double total = totalTokens;
				double totalUnseen = totalUnseenTokens;

				double p_T_U = (totalUnseen == 0) ? 1 : c_Tunseen / totalUnseen;
				double pb_T_W; // always set below

				// System.err.println("c_W is " + c_W + " THRESH is " +
				// smoothInUnknownsThreshold + " mle = " + (c_TW/c_W));
				if (c_W > smoothInUnknownsThreshold || noSmoothing) {
					// we've seen the word enough times to have confidence in
					// its tagging
					if (noSmoothing && c_W == 0)
						pb_T_W = c_TW / 1;
					else
						pb_T_W = (c_TW + 0.0001 * p_T_U) / (c_W + 0.0001);
					// pb_T_W = c_TW / c_W;
					// System.out.println("c_TW "+c_TW+" c_W "+c_W);
				} else {
					// we haven't seen the word enough times to have confidence
					// in its tagging
					pb_T_W = (c_TW + smooth[1] * p_T_U) / (c_W + smooth[1]);
					// System.out.println("smoothed c_TW "+c_TW+" c_W "+c_W);
				}
				if (pb_T_W == 0)
					continue;
				// Sometimes we run up against unknown tags. This should only
				// happen
				// when we're calculating the likelihood for a given tree, not
				// when
				// we're parsing. In that case, return a LL of 0.

				// NO NO NO, this is wrong, slav
				// if (c_T==0) {
				// resultArray[substate] = 1;
				// continue;
				// }

				double p_T = (c_T / total);
				double p_W = (c_W / total);
				pb_W_T = pb_T_W * p_W / p_T;

			} else {

				// test against simple Chinese lexical constants
				if (Corpus.myTreebank == Corpus.TreeBankType.CHINESE) {
					Numberer tagNumberer = Numberer.getGlobalNumberer("tags");
					double prob;
					if (word.matches(ChineseLexicon.dateMatch)) {
						// EncodingPrintWriter.out.println("Date match for " +
						// word,encoding);
						if (tag == tagNumberer.number("NT")) { // (tag.equals("NT"))
																// {
							prob = 1.0;
						} else {
							prob = 0.0;
						}
						Arrays.fill(resultArray, prob);
						return resultArray;
					} else if (word.matches(ChineseLexicon.numberMatch)) {
						// EncodingPrintWriter.out.println("Number match for " +
						// word,encoding);
						if (tag == tagNumberer.number("CD") /* tag.equals("CD") */
								&& (!word.matches(ChineseLexicon.ordinalMatch))) {
							prob = 1.0;
						} else if (tag == tagNumberer.number("OD") /*
																	 * tag.equals
																	 * ("OD")
																	 */
								&& word.matches(ChineseLexicon.ordinalMatch)) {
							prob = 1.0;
						} else {
							prob = 0.0;
						}
						Arrays.fill(resultArray, prob);
						return resultArray;
					} else if (word.matches(ChineseLexicon.properNameMatch)) {
						// EncodingPrintWriter.out.println("Proper name match for "
						// + word,encoding);
						if (tag == tagNumberer.number("NR")) { // tag.equals("NR"))
																// {
							prob = 1.0;
						} else {
							prob = 0.0;
						}
						Arrays.fill(resultArray, prob);
						return resultArray;
					}
				}

				// unknown word model for P(T|S)
				String sig = (isSignature) ? word : getCachedSignature(word,
						loc);

				// iTW.word = sig;
				// double c_TS = unSeenCounter.getCount(iTW);
				double c_TS = 0;
				if (unseenWordToTagCounters[tag] != null
						&& unseenWordToTagCounters[tag].get(sig) != null) {
					c_TS = unseenWordToTagCounters[tag].get(sig)[substate];
				}
				// if (c_TS == 0) continue;

				// how often did we see this signature
				double c_S = wordCounter.getCount(sig);
				double c_U = totalUnseenTokens;
				double total = totalTokens; // seenCounter.getCount(iTW);
				double c_T = unseenTagCounter[tag][substate];// unSeenCounter.getCount(iTW);
				double c_Tseen = tagCounter[tag][substate]; // seenCounter.getCount(iTW);
				double p_T_U = c_T / c_U;

				if (unknownLevel == 0) {
					c_TS = 0;
					c_S = 0;
				}
				// System.out.println(" sig " + sig
				// +" c_TS "+c_TS+" p_T_U "+p_T_U+" c_S "+c_S);
				// smooth[0]=10;
				double pb_T_S = (c_TS + smooth[0] * p_T_U) / (c_S + smooth[0]);

				double p_T = (c_Tseen / total);
				double p_W = 1.0 / total;
				// if we've never before seen this tag, then just say the
				// probability is 1
				/*
				 * if (p_T == 0) { resultArray[substate] = 1; continue; }
				 */pb_W_T = pb_T_S * p_W / p_T;
			}

			// give very low scores when needed, but try to avoid -Infinity
			if (pb_W_T == 0) {// NOT sure whether this is a good idea - slav
				resultArray[substate] = 1e-87;
			} else {
				resultArray[substate] = pb_W_T;
			}

		}
		smoother.smooth(tag, resultArray);

		if (logarithmMode) {
			for (int i = 0; i < resultArray.length; i++) {
				resultArray[i] = Math.log(resultArray[i]);
				if (Double.isNaN(resultArray[i]))
					resultArray[i] = Double.NEGATIVE_INFINITY;
			}
		}
		/*
		 * double power = 1.0; // raise to the power for (int i=0;
		 * i<resultArray.length; i++) { resultArray[i] =
		 * Math.pow(resultArray[i],power); }
		 */

		return resultArray;
	} // end score()

	/*
	 * public void tune(Collection<Tree> trees) { double bestScore =
	 * Double.NEGATIVE_INFINITY; double[] bestSmooth = {0.0, 0.0}; for
	 * (smooth[0] = 1; smooth[0] <= 1; smooth[0] *= 2.0) {//64 for (smooth[1] =
	 * 0.2; smooth[1] <= 0.2; smooth[1] *= 2.0) {//3 //for (smooth[0]=0.5;
	 * smooth[0]<=64; smooth[0] *= 2.0) {//64 //for (smooth[1]=0.1;
	 * smooth[1]<=12.8; smooth[1] *= 2.0) {//3 double score = 0.0; //score =
	 * scoreAll(trees); if (Test.verbose) {
	 * System.out.println("Tuning lexicon: s0 " + smooth[0] + " s1 " + smooth[1]
	 * + " is " + score + " " + trees.size() + " trees."); } if (score >
	 * bestScore) { System.arraycopy(smooth, 0, bestSmooth, 0, smooth.length);
	 * bestScore = score; } } } System.arraycopy(bestSmooth, 0, smooth, 0,
	 * bestSmooth.length); if (smartMutation) { smooth[0] = 8.0; //smooth[1] =
	 * 1.6; //smooth[0] = 0.5; smooth[1] = 0.1; } if (Test.unseenSmooth > 0.0) {
	 * smooth[0] = Test.unseenSmooth; } if (Test.verbose) {
	 * System.out.println("Tuning selected smoothUnseen " + smooth[0] +
	 * " smoothSeen " + smooth[1] + " at " + bestScore); } }
	 */

	public Counter<String> getWordCounter() {
		return wordCounter;
	}

	public void tieRareWordStats(int threshold) {
		for (int ni = 0; ni < numSubStates.length; ni++) {
			double unseenTagTokens = 0;
			for (int si = 0; si < numSubStates[ni]; si++) {
				unseenTagTokens += unseenTagCounter[ni][si];
			}
			if (unseenTagTokens == 0) {
				continue;
			}
			for (Map.Entry<String, double[]> wordToTagEntry : wordToTagCounters[ni]
					.entrySet()) {
				String word = wordToTagEntry.getKey();
				double[] substateCounter = wordToTagEntry.getValue();
				if (wordCounter.getCount(word) < threshold + 0.5) {
					double wordTagTokens = 0;
					for (int si = 0; si < numSubStates[ni]; si++) {
						wordTagTokens += substateCounter[si];
					}
					for (int si = 0; si < numSubStates[ni]; si++) {
						substateCounter[si] = unseenTagCounter[ni][si]
								* wordTagTokens / unseenTagTokens;
					}
				}
			}
		}
	}

	/**
	 * Trains this lexicon on the Collection of trees.
	 */
	public void trainTree(Tree<StateSet> trainTree, double randomness,
			Lexicon oldLexicon, boolean secondHalf, boolean noSmoothing,
			int threshold) {
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
		if (words.size() != tags.size()) {
			System.out.println("Yield an preterminal yield do not match!");
			System.out.println(words.toString());
			System.out.println(tags.toString());
		}

		Counter<String> oldWordCounter = null;
		if (oldLexicon != null) {
			oldWordCounter = oldLexicon.getWordCounter();
		}
		// for all words in sentence
		for (int position = 0; position < words.size(); position++) {
			totalWords++;
			String word = words.get(position).getWord();
			int nSubStates = tags.get(position).numSubStates();
			short tag = tags.get(position).getState();

			String sig = getCachedSignature(word, position);
			wordCounter.incrementCount(sig, 0);

			if (unseenWordToTagCounters[tag] == null) {
				unseenWordToTagCounters[tag] = new HashMap<String, double[]>();
			}
			double[] substateCounter2 = unseenWordToTagCounters[tag].get(sig);
			if (substateCounter2 == null) {
				// System.out.print("Sig "+sig+" word "+ word+" pos "+position);
				substateCounter2 = new double[numSubStates[tag]];
				unseenWordToTagCounters[tag].put(sig, substateCounter2);
			}

			// guarantee that the wordToTagCounter element exists so we can
			// tally the combination
			if (wordToTagCounters[tag] == null) {
				wordToTagCounters[tag] = new HashMap<String, double[]>();
			}
			double[] substateCounter = wordToTagCounters[tag].get(word);
			if (substateCounter == null) {
				substateCounter = new double[numSubStates[tag]];
				wordToTagCounters[tag].put(word, substateCounter);
			}

			double[] oldLexiconScores = null;
			if (randomness == -1) {
				oldLexiconScores = oldLexicon.score(word, tag, position,
						noSmoothing, false);
			}

			StateSet currentState = tags.get(position);
			double scale = ScalingTools.calcScaleFactor(currentState
					.getOScale() - sentenceScale)
					/ sentenceScore;
			// double weightSum = 0;

			for (short substate = 0; substate < nSubStates; substate++) {
				double weight = 1;
				if (randomness == -1) {
					// weight by the probability of seeing the tag and word
					// together, given the sentence
					if (!Double.isInfinite(scale)) {
						weight = currentState.getOScore(substate)
								* oldLexiconScores[substate] * scale;
					} else {
						weight = Math.exp(Math.log(ScalingTools.SCALE)
								* (currentState.getOScale() - sentenceScale)
								- Math.log(sentenceScore)
								+ Math.log(currentState.getOScore(substate))
								+ Math.log(oldLexiconScores[substate]));
					}
					// weightSum+=weight;
				} else if (randomness == 0) {
					// for the baseline
					weight = 1;
				} else {
					// add a bit of randomness
					weight = GrammarTrainer.RANDOM.nextDouble() * randomness
							/ 100.0 + 1.0;
				}
				if (weight == 0) {
					continue;
				}
				// tally in the tag with the given weight
				substateCounter[substate] += weight;
				// update the counters
				tagCounter[tag][substate] += weight;
				wordCounter.incrementCount(word, weight);
				totalTokens += weight;

				if (Double.isNaN(totalTokens)) {
					throw new Error(
							"totalTokens is NaN: this would fail if we let it continue!");
				}

				if (oldLexicon != null
						&& oldWordCounter.getCount(word) < threshold + 0.5) {
					wordCounter.incrementCount(sig, weight);
					substateCounter2[substate] += weight;
					unseenTagCounter[tag][substate] += weight;
					totalUnseenTokens += weight;
				}
				// if (secondHalf) {
				// // start doing this once we're halfway through the trees
				// // it's an entirely unknown word
				// if (wordCounter.getCount(word) < 2) {
				// wordCounter.incrementCount(sig, weight);
				//
				// if (unseenWordToTagCounters[tag] == null) {
				// unseenWordToTagCounters[tag] = new HashMap<String,
				// double[]>();
				// }
				// substateCounter = unseenWordToTagCounters[tag].get(sig);
				// if (substateCounter == null) {
				// //System.out.print("Sig "+sig+" word "+
				// word+" pos "+position);
				// substateCounter = new double[numSubStates[tag]];
				// unseenWordToTagCounters[tag].put(sig, substateCounter);
				// }
				//
				// substateCounter[substate] += weight;
				// unseenTagCounter[tag][substate] += weight;
				// totalUnseenTokens += weight;
				// } else {
				// }
				// }
			}
		}
	}

	/**
	 * Returns the index of the signature of the word numbered wordIndex, where
	 * the signature is the String representation of unknown word features.
	 * Caches the last signature index returned.
	 */
	protected String getCachedSignature(String word, int sentencePosition) {
		if (word == null)
			return lastWordToSignaturize;
		if (word.equals(lastWordToSignaturize)
				&& sentencePosition == lastSentencePosition) {
			// System.err.println("Signature: cache mapped " + wordIndex +
			// " to " + lastSignatureIndex);
			return lastSignature;
		} else {
			String uwSig = getSignature(word, sentencePosition);
			lastSignature = uwSig;
			lastSentencePosition = sentencePosition;
			lastWordToSignaturize = word;
			return uwSig;
		}
	}

	/**
	 * Merge states, combining information about words we have seen. THIS DOES
	 * NOT UPDATE INFORMATION FOR UNSEEN WORDS! For that, retrain the Lexicon!
	 * 
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

		for (int tag = 0; tag < mergeThesePairs.length; tag++) {
			// update wordToTagCounters
			if (wordToTagCounters[tag] != null) {
				for (String word : wordToTagCounters[tag].keySet()) {
					double[] scores = wordToTagCounters[tag].get(word);
					double[] newScores = new double[newNumSubStates[tag]];
					for (int i = 0; i < numSubStates[tag]; i++) {
						short nSplit = (short) partners[tag][i].length;
						if (nSplit == 2) {
							newScores[mapping[tag][i]] = scores[partners[tag][i][0]]
									+ scores[partners[tag][i][1]];
						} else {
							newScores[mapping[tag][i]] = scores[i];
						}
					}
					wordToTagCounters[tag].put(word, newScores);
				}
			}
			// update tag counter
			double[] newTagCounter = new double[newNumSubStates[tag]];
			for (int i = 0; i < numSubStates[tag]; i++) {
				if (partners[tag][i].length == 2) {
					newTagCounter[mapping[tag][i]] = tagCounter[tag][partners[tag][i][0]]
							+ tagCounter[tag][partners[tag][i][1]];
				} else {
					newTagCounter[mapping[tag][i]] = tagCounter[tag][i];
				}
			}
			tagCounter[tag] = newTagCounter;
		}

		numSubStates = newNumSubStates;
	}

	public Map<String, double[][]> getUnseenScores() {
		Map<String, double[][]> map = new HashMap<String, double[][]>();
		for (int tag = 0; tag < unseenWordToTagCounters.length; tag++) {
			if (unseenWordToTagCounters[tag] != null) {
				for (String sig : unseenWordToTagCounters[tag].keySet()) {
					double[][] sigScores = map.get(sig);
					if (sigScores == null) {
						sigScores = new double[numSubStates.length][];
						map.put(sig, sigScores);
					}
					sigScores[tag] = new double[numSubStates[tag]];
					for (int substate = 0; substate < numSubStates[tag]; substate++) {
						double c_TS = 0;
						if (unseenWordToTagCounters[tag].get(sig) != null) {
							c_TS = unseenWordToTagCounters[tag].get(sig)[substate];
						}

						// how often did we see this signature
						double c_S = wordCounter.getCount(sig);
						double c_U = totalUnseenTokens;
						double total = totalTokens; // seenCounter.getCount(iTW);
						double c_T = unseenTagCounter[tag][substate];// unSeenCounter.getCount(iTW);
						double c_Tseen = tagCounter[tag][substate]; // seenCounter.getCount(iTW);
						double p_T_U = c_T / c_U;

						if (unknownLevel == 0) {
							c_TS = 0;
							c_S = 0;
						}
						double pb_T_S = (c_TS + smooth[0] * p_T_U)
								/ (c_S + smooth[0]);

						double p_T = (c_Tseen / total);
						double p_W = 1.0 / total;
						// if we've never before seen this tag, then just say
						// the probability is 1
						if (p_T == 0) {
							sigScores[tag][substate] = 1;
							continue;
						}
						double pb_W_T = pb_T_S * p_W / p_T;
						sigScores[tag][substate] = pb_W_T;
					}
				}
			}
		}
		return map;
	}

	public void removeUnlikelyTags(double threshold, double exponent) {
		// System.out.print("Removing unlikely tags...");
		if (isLogarithmMode())
			threshold = Math.log(threshold);
		int removed = 0, total = 0;
		if (isConditional) {
			for (int i = 0; i < conditionalWeights.length; i++) {
				for (int j = 0; j < conditionalWeights[i].length; j++) {
					if (conditionalWeights[i][j] == null)
						continue;
					for (int k = 0; k < conditionalWeights[i][j].length; k++) {
						total++;
						if (conditionalWeights[i][j][k] < threshold) {
							conditionalWeights[i][j][k] = 0;
							removed++;
						}
					}
				}
			}
		} else {
			for (int tag = 0; tag < numSubStates.length; tag++) {
				double[] c_TW;
				if (wordToTagCounters[tag] != null) {
					for (String word : wordToTagCounters[tag].keySet()) {
						c_TW = wordToTagCounters[tag].get(word);
						for (int substate = 0; substate < numSubStates[tag]; substate++) {
							total++;
							if (c_TW[substate] < threshold) {
								c_TW[substate] = 0;
								removed++;
							}
						}
					}
				}
			}
			/*
			 * if (unseenWordToTagCounters[tag]!=null){ for (String word :
			 * unseenWordToTagCounters[tag].keySet()){ c_TW =
			 * unseenWordToTagCounters[tag].get(word); for (int substate=0;
			 * substate<numSubStates[tag]; substate++) { // if
			 * (c_TW[substate]<threshold) c_TW[substate] = 0; } } }
			 */
			// double[] c_tag = tagCounter[tag];
			// double[] c_Tunseen = unseenTagCounter[tag];
			// for (int substate=0; substate<numSubStates[tag]; substate++) {
			// if (c_tag[substate]<threshold) c_tag[substate] = 0;
			// if (c_Tunseen[substate]<threshold) c_Tunseen[substate] = 0;
			// }
		}
		// System.out.print(" done.\n Removed "+removed+" word tag combinations out of "+total+".");
	}

	public void logarithmMode() {
		logarithmMode = true;
	}

	public boolean isLogarithmMode() {
		return logarithmMode;
	}

	public SophisticatedLexicon projectLexicon(double[] condProbs,
			int[][] mapping, int[][] toSubstateMapping) {
		short[] newNumSubStates = new short[numSubStates.length];
		for (int state = 0; state < numSubStates.length; state++) {
			newNumSubStates[state] = (short) toSubstateMapping[state][0];
		}
		Smoother newSmoother = this.smoother.copy();
		newSmoother.updateWeights(toSubstateMapping);
		SophisticatedLexicon newLexicon = new SophisticatedLexicon(
				newNumSubStates, this.smoothingCutoff, this.smooth,
				newSmoother, this.threshold);

		double[][] newTagCounter = new double[newNumSubStates.length][];
		double[][] newUnseenTagCounter = new double[newNumSubStates.length][];
		if (!isConditional) {
			for (int tag = 0; tag < numSubStates.length; tag++) {
				// update tag counters
				newTagCounter[tag] = new double[newNumSubStates[tag]];
				newUnseenTagCounter[tag] = new double[newNumSubStates[tag]];
				for (int substate = 0; substate < numSubStates[tag]; substate++) {
					newTagCounter[tag][toSubstateMapping[tag][substate + 1]] += condProbs[mapping[tag][substate]]
							* tagCounter[tag][substate];
				}
				for (int substate = 0; substate < numSubStates[tag]; substate++) {
					newUnseenTagCounter[tag][toSubstateMapping[tag][substate + 1]] += condProbs[mapping[tag][substate]]
							* unseenTagCounter[tag][substate];
				}
				// update wordToTagCounters
				if (wordToTagCounters[tag] != null) {
					newLexicon.wordToTagCounters[tag] = new HashMap<String, double[]>();
					for (String word : wordToTagCounters[tag].keySet()) {
						double[] scores = wordToTagCounters[tag].get(word);
						double[] newScores = new double[newNumSubStates[tag]];
						for (int i = 0; i < numSubStates[tag]; i++) {
							newScores[toSubstateMapping[tag][i + 1]] += condProbs[mapping[tag][i]]
									* scores[i];
						}
						newLexicon.wordToTagCounters[tag].put(word, newScores);
					}
				}
				// update wordToTagCounters
				if (unseenWordToTagCounters[tag] != null) {
					newLexicon.unseenWordToTagCounters[tag] = new HashMap<String, double[]>();
					for (String word : unseenWordToTagCounters[tag].keySet()) {
						double[] scores = unseenWordToTagCounters[tag]
								.get(word);
						double[] newScores = new double[newNumSubStates[tag]];
						for (int i = 0; i < numSubStates[tag]; i++) {
							newScores[toSubstateMapping[tag][i + 1]] += condProbs[mapping[tag][i]]
									* scores[i];
						}
						newLexicon.unseenWordToTagCounters[tag].put(word,
								newScores);
					}
				}
			}
		} else {
			double[][][] newCondWeights = new double[conditionalWeights.length][conditionalWeights[0].length][];
			for (int w = 0; w < newCondWeights.length; w++) {
				if (conditionalWeights[w] == null)
					continue;
				for (int tag = 0; tag < numSubStates.length; tag++) {
					if (conditionalWeights[w][tag] == null)
						continue;
					newCondWeights[w][tag] = new double[newNumSubStates[tag]];
					for (int substate = 0; substate < numSubStates[tag]; substate++) {
						newCondWeights[w][tag][toSubstateMapping[tag][substate + 1]] += condProbs[mapping[tag][substate]]
								* conditionalWeights[w][tag][substate];
					}

				}
			}
			newLexicon.conditionalWeights = newCondWeights;
			newLexicon.isConditional = true;
		}

		newLexicon.totalWordTypes = totalWordTypes;
		newLexicon.totalTokens = totalTokens;
		newLexicon.totalUnseenTokens = totalUnseenTokens;
		newLexicon.totalWords = totalWords;
		// newLexicon.smoother = smoother;
		newLexicon.allTags = new HashSet<Short>(allTags);
		newLexicon.wordCounter = new Counter<String>();
		for (String word : wordCounter.keySet()) {
			newLexicon.wordCounter.setCount(word, wordCounter.getCount(word));
		}
		newLexicon.smoothingCutoff = smoothingCutoff;
		newLexicon.addXSmoothing = addXSmoothing;
		newLexicon.smoothInUnknownsThreshold = smoothInUnknownsThreshold;

		newLexicon.tagCounter = newTagCounter;
		newLexicon.unseenTagCounter = newUnseenTagCounter;
		newLexicon.numSubStates = newNumSubStates;
		newLexicon.wordNumberer = wordNumberer;
		newLexicon.unknownLevel = unknownLevel;
		return newLexicon;
	}

	public SophisticatedLexicon copyLexicon() {
		short[] newNumSubStates = numSubStates.clone();
		SophisticatedLexicon newLexicon = new SophisticatedLexicon(
				newNumSubStates, this.smoothingCutoff, this.smooth,
				this.smoother, this.threshold);

		double[][] newTagCounter = ArrayUtil.copy(tagCounter);
		double[][] newUnseenTagCounter = ArrayUtil.copy(unseenTagCounter);
		for (int tag = 0; tag < numSubStates.length; tag++) {
			if (wordToTagCounters[tag] != null) {
				newLexicon.wordToTagCounters[tag] = new HashMap<String, double[]>();
				for (String word : wordToTagCounters[tag].keySet()) {
					double[] scores = wordToTagCounters[tag].get(word);
					double[] newScores = scores.clone();
					newLexicon.wordToTagCounters[tag].put(word, newScores);
				}
			}
			// update wordToTagCounters
			if (unseenWordToTagCounters[tag] != null) {
				newLexicon.unseenWordToTagCounters[tag] = new HashMap<String, double[]>();
				for (String word : unseenWordToTagCounters[tag].keySet()) {
					double[] scores = unseenWordToTagCounters[tag].get(word);
					double[] newScores = scores.clone();
					newLexicon.unseenWordToTagCounters[tag]
							.put(word, newScores);
				}
			}
		}

		if (conditionalWeights != null)
			newLexicon.conditionalWeights = conditionalWeights.clone();
		newLexicon.isConditional = isConditional;
		newLexicon.totalWordTypes = totalWordTypes;
		newLexicon.totalTokens = totalTokens;
		newLexicon.totalUnseenTokens = totalUnseenTokens;
		newLexicon.totalWords = totalWords;
		newLexicon.smoother = smoother;
		newLexicon.allTags = new HashSet<Short>(allTags);
		newLexicon.wordCounter = new Counter<String>();
		for (String word : wordCounter.keySet()) {
			newLexicon.wordCounter.setCount(word, wordCounter.getCount(word));
		}
		newLexicon.smoothingCutoff = smoothingCutoff;
		newLexicon.addXSmoothing = addXSmoothing;
		newLexicon.smoothInUnknownsThreshold = smoothInUnknownsThreshold;

		newLexicon.tagCounter = newTagCounter;
		newLexicon.unseenTagCounter = newUnseenTagCounter;
		newLexicon.numSubStates = newNumSubStates;

		newLexicon.wordNumberer = this.wordNumberer;
		newLexicon.unknownLevel = this.unknownLevel;
		return newLexicon;
	}

	public int getNumberOfEntries() {
		int nEntries = 0;
		if (conditionalWeights == null) {
			// indicates first time use:
			for (String word : wordCounter.keySet()) { // has all words AND also
														// the signatures
				wordNumberer.number(word);
			}
		}
		for (int tag = 0; tag < wordToTagCounters.length; tag++) {
			if (wordToTagCounters[tag] != null) {
				nEntries += wordToTagCounters[tag].size() * numSubStates[tag];
				if (conditionalWeights == null) {
					for (String word : wordToTagCounters[tag].keySet())
						wordNumberer.number(word);
				}
			}
			if (unseenWordToTagCounters[tag] != null) {
				nEntries += unseenWordToTagCounters[tag].size()
						* numSubStates[tag];
				if (conditionalWeights == null) {
					for (String word : unseenWordToTagCounters[tag].keySet())
						wordNumberer.number(word);
				}
			}
		}
		if (conditionalWeights == null) {
			conditionalWeights = new double[wordNumberer.total()][numSubStates.length][];
		}
		return nEntries;
	}

	// public Pair<double[],int[][]> getLinearizedLexicon(){
	// return getLinearizedLexicon(getNumberOfEntries());
	// }
	//
	// public Pair<double[],int[][]> getLinearizedLexicon(int n){
	// if (isConditional) {
	// System.out.println("Do not have the functionality to linearize a conditional lexicon!");
	// return new Pair<double[],int[][]>(null,null);
	// }
	// double[] probs = new double[n];
	// int[][] startIndex = new int[wordNumberer.total()][numSubStates.length];
	// ArrayUtil.fill(startIndex,Integer.MIN_VALUE);
	// int ind = 0;
	// for (int tag=0; tag<wordToTagCounters.length; tag++){
	// if (wordToTagCounters[tag]!=null) {
	// for (String word : wordToTagCounters[tag].keySet()){
	// double[] scores = score(word,(short)tag,0,false,false);
	// startIndex[wordNumberer.number(word)][tag] = ind;
	// for (int i=0; i<scores.length; i++){
	// probs[ind++] = Math.log(scores[i]);
	// }
	// }
	// }
	// if (unseenWordToTagCounters[tag]!=null) {
	// for (String word : unseenWordToTagCounters[tag].keySet()){
	// double[] scores = score(word,(short)tag,0,false,true);
	// startIndex[wordNumberer.number(word)][tag] = ind;
	// for (int i=0; i<scores.length; i++){
	// probs[ind++] = Math.log(scores[i]);
	// }
	// }
	// }
	// }
	// //tagCounter = null;
	//
	// return new Pair<double[],int[][]>(probs,startIndex);
	// }

	public void delinearizeLexicon(double[] probs) {
		int ind = 0;
		// Numberer wordNumberer = Numberer.getGlobalNumberer("words");
		for (int tag = 0; tag < wordToTagCounters.length; tag++) {
			if (wordToTagCounters[tag] != null) {
				for (String word : wordToTagCounters[tag].keySet()) {
					double[] scores = new double[numSubStates[tag]];
					for (int i = 0; i < scores.length; i++) {
						double val = probs[ind++];// Math.exp(); //probs[ind++]
						val = (val == -1000) ? 0 : Math.exp(val);
						if (SloppyMath.isVeryDangerous(val)) {
							if (Double.isNaN(probs[ind - 1]))
								val = 1.0e-50;
							else
								val = probs[ind - 1];
							// System.out.println("word " +word+" tag "+tag);
							// System.out.println("Optimizer proposed Inf. Setting to probs: "
							// +val);
						}
						scores[i] = val;
					}
					conditionalWeights[wordNumberer.number(word)][tag] = scores;
				}
			}
			if (unseenWordToTagCounters[tag] != null) {
				for (String word : unseenWordToTagCounters[tag].keySet()) {
					double[] scores = new double[numSubStates[tag]];
					for (int i = 0; i < scores.length; i++) {
						double val = probs[ind++];// Math.exp(); //probs[ind++]
						val = (val == -1000) ? 0 : Math.exp(val);
						if (SloppyMath.isVeryDangerous(val)) {
							if (Double.isNaN(probs[ind - 1]))
								val = 1.0e-50;
							else
								val = probs[ind - 1];
							// System.out.println("word " +word+" tag "+tag);
							// System.out.println("Optimizer proposed Inf. Setting to probs: "
							// +val);
						}
						scores[i] = val;
					}
					conditionalWeights[wordNumberer.number(word)][tag] = scores;
				}
			}
		}
		this.isConditional = true;
	}

	public void setConditional(boolean b) {
		this.isConditional = b;
	}

	public double[] scoreConditional(String word, short tag, int loc,
			boolean noSmoothing, boolean isSignature) {
		if (isSignature)
			return getConditionalSignatureScore(word, tag, noSmoothing);
		else if (!isKnown(word))
			return getConditionalSignatureScore(getCachedSignature(word, loc),
					tag, noSmoothing);
		// else if(!isKnown(word)) return getConditionalSignatureScore("#UNK#",
		// tag, noSmoothing);
		// else if(isKnown(word))return getConditionalSignatureScore(word, tag,
		// noSmoothing);
		double[] resultArray = new double[numSubStates[tag]];
		double[] wordScore = getConditionalWordScore(word, tag, noSmoothing);
		String sig = getCachedSignature(word, loc);
		double[] sigScore = getConditionalSignatureScore(sig, tag, noSmoothing);
		for (int i = 0; i < resultArray.length; i++) {
			resultArray[i] = wordScore[i] + sigScore[i];
		}
		return resultArray;
	}

	public double[] getConditionalSignatureScore(String sig, short tag,
			boolean noSmoothing) {
		double[] resultArray = new double[numSubStates[tag]];
		int ind = wordNumberer.number(sig);
		if (ind >= conditionalWeights.length) {
			System.out
					.println(" We have a problem! sig " + sig + " ind " + ind);
			return resultArray;
		}
		double[] tmpArray = conditionalWeights[ind][tag];
		if (tmpArray != null) {
			for (int i = 0; i < resultArray.length; i++) {
				resultArray[i] += tmpArray[i];
			}
		}
		if (this.isLogarithmMode()) {
			for (int i = 0; i < resultArray.length; i++) {
				resultArray[i] = Math.log(resultArray[i]);
			}
		}
		return resultArray;
	}

	public double[] getConditionalWordScore(String word, short tag,
			boolean noSmoothing) {
		double[] resultArray = new double[numSubStates[tag]];
		int ind = wordNumberer.number(word);
		double[] tmpArray = conditionalWeights[ind][tag];
		if (tmpArray != null) {
			for (int i = 0; i < resultArray.length; i++) {
				resultArray[i] = tmpArray[i];
			}
		}
		if (this.isLogarithmMode()) {
			for (int i = 0; i < resultArray.length; i++) {
				resultArray[i] = Math.log(resultArray[i]);
			}
		}

		return resultArray;
	}

	class ChineseLexicon implements Serializable {
		private static final long serialVersionUID = 1L;

		private static final String encoding = "GB18030"; // used only for
															// debugging

		/*
		 * These strings are stored in ascii-stype Unicode encoding. To edit
		 * them, either use the Unicode codes or use native2ascii or a similar
		 * program to convert the file into a Chinese encoding, then convert
		 * back.
		 */
		public static final String dateMatch = ".*[\u5e74\u6708\u65e5\u53f7]$";
		public static final String numberMatch = ".*[\uff10\uff11\uff12\uff13\uff14\uff15\uff16\uff17\uff18\uff19\uff11\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d\u5341\u767e\u5343\u4e07\u4ebf].*";
		public static final String ordinalMatch = "^\u7b2c.*";
		public static final String properNameMatch = ".*\u00b7.*";
	}

	public void setSmoother(Smoother smoother) {
		this.smoother = smoother;
	}

	public Smoother getSmoother() {
		return smoother;
	}

	public double[] getSmoothingParams() {
		return smooth;
	}

	public double getPruningThreshold() {
		return threshold;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.PCFGLA.Lexicon#getLinearizedLexicon()
	 */
	public double[] getLinearizedLexicon() {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.PCFGLA.Lexicon#getLinearIndex(java.lang.String,
	 * int)
	 */
	public int getLinearIndex(String word, int tag) {
		// TODO Auto-generated method stub
		return 0;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.PCFGLA.Lexicon#clearMapping()
	 */
	public void clearMapping() {
		// TODO Auto-generated method stub

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.PCFGLA.Lexicon#scoreSignature(java.lang.String,
	 * int, int)
	 */
	public double[] scoreSignature(StateSet stateSet, int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.PCFGLA.Lexicon#scoreWord(java.lang.String, int)
	 */
	public double[] scoreWord(StateSet stateSet, int tag) {
		// TODO Auto-generated method stub
		return null;
	}

	public void explicitlyComputeScores(int finalLevel) {
		// TODO Auto-generated method stub

	}

	@SuppressWarnings("unchecked")
	public SophisticatedLexicon remapStates(Numberer thisNumberer,
			Numberer newNumberer) {
		SophisticatedLexicon remappedLexicon = copyLexicon();
		remappedLexicon.wordToTagCounters = new HashMap[newNumberer.size()];
		remappedLexicon.unseenWordToTagCounters = new HashMap[newNumberer
				.size()];
		remappedLexicon.typeTagCounter = new double[newNumberer.size()][];
		remappedLexicon.tagCounter = new double[newNumberer.size()][];
		remappedLexicon.unseenTagCounter = new double[newNumberer.size()][];
		remappedLexicon.simpleTagCounter = new double[newNumberer.size()];
		remappedLexicon.allTags = new HashSet<Short>();
		remappedLexicon.numSubStates = new short[newNumberer.size()];
		remappedLexicon.smoother = smoother.remapStates(thisNumberer,
				newNumberer);
		if (conditionalWeights != null) {
			for (int w = 0; w < conditionalWeights.length; w++) {
				remappedLexicon.conditionalWeights[w] = new double[newNumberer
						.size()][];
			}
		}
		for (short s = 0; s < newNumberer.size(); s++) {
			short translatedState = translateState(s, newNumberer, thisNumberer);
			if (translatedState >= 0) {
				remappedLexicon.wordToTagCounters[s] = wordToTagCounters[translatedState];
				remappedLexicon.unseenWordToTagCounters[s] = unseenWordToTagCounters[translatedState];
				remappedLexicon.typeTagCounter[s] = typeTagCounter[translatedState];
				remappedLexicon.tagCounter[s] = tagCounter[translatedState];
				remappedLexicon.unseenTagCounter[s] = unseenTagCounter[translatedState];
				remappedLexicon.simpleTagCounter[s] = simpleTagCounter[translatedState];
				if (allTags.contains(translatedState))
					remappedLexicon.allTags.add(s);
				remappedLexicon.numSubStates[s] = numSubStates[translatedState];
				if (conditionalWeights != null) {
					for (int w = 0; w < conditionalWeights[w].length; w++) {
						remappedLexicon.conditionalWeights[w][s] = conditionalWeights[w][translatedState];
					}
				}
			} else {
				remappedLexicon.wordToTagCounters[s] = new HashMap<String, double[]>();
				remappedLexicon.unseenWordToTagCounters[s] = new HashMap<String, double[]>();
				remappedLexicon.typeTagCounter[s] = new double[1];
				remappedLexicon.tagCounter[s] = new double[1];
				remappedLexicon.unseenTagCounter[s] = new double[1];
				remappedLexicon.numSubStates[s] = 1;
			}
		}
		return remappedLexicon;
	}

	private short translateState(int state, Numberer baseNumberer,
			Numberer translationNumberer) {
		Object object = baseNumberer.object(state);
		if (translationNumberer.hasSeen(object)) {
			return (short) translationNumberer.number(object);
		} else {
			return (short) -1;
		}
	}

}
