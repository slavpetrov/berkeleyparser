package edu.berkeley.nlp.speech.decoder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.berkeley.nlp.HMM.AcousticModel;
import edu.berkeley.nlp.HMM.PhoneIndexer;
import edu.berkeley.nlp.speech.decoder.dict.CMUDict;
import edu.berkeley.nlp.speech.decoder.dict.PronounciationDictionary;
import fig.basic.Pair;


/**
 * @author aria42
 *
 */
class LexiconTrie {

	List<TrieState> roots ;
	int numBasePhones ;	
	List<TrieState> states;
	List<TrieState>[] statesByPhone ;
	LanguageModel langModel;
	PhoneIndexer phoneIndexer;

	class Successor {
		TrieState state ;
		float score;
		public Successor(TrieState succ, float score) {
			super();
			this.state = succ;
			this.score = score;
		}
		public String toString() {
			return Pair.newPair(state, score).toString();
		}
	}

	static class ScoredWord {
		String word;
		double score;
		public ScoredWord(String word, double finishScore) {
			super();
			this.word = word;
			this.score = finishScore;
		}			
	}

	class TrieState {

		short phone = -1;
		double sumWordScores = 0; 	
		int index ;

		List<ScoredWord> scoredWords ;  							
		List<Successor> successors ;
		TrieState prev;

		private TrieState(short phone, TrieState prev) {
			this.phone = phone;
			this.prev = prev;
			this.index = states.size();
			states.add(this);
		}

		public boolean isRoot() {
			return index < numBasePhones;
		}

		public double getWordScoreSum() {
			return sumWordScores;
		}

		public int getNumWords() {
			return (scoredWords == null ? 0 : scoredWords.size());
		}

		public boolean hasWords() {
			return scoredWords != null && !scoredWords.isEmpty();
		}

		private void addSuccessor(TrieState succ, float score) {
			if (successors == null) {
				successors = new ArrayList<Successor>();
			}
			successors.add(new Successor(succ, score));
		}

		public Successor getPhoneSuccessor(int phone) {
			if(successors != null) {
				for (Successor succ: successors) {
					if (succ.state.phone == phone) {
						return succ;
					}
				}
			}
			return null;
		}

		private void addWord(String word) {
			if (scoredWords == null) {
				scoredWords = new ArrayList<ScoredWord>();
			}
			double score = langModel.getScore(word, new ArrayList<String>());
			scoredWords.add(new ScoredWord(word, score));
		}

		public boolean isTerminal() {
			return successors == null || successors.isEmpty();
		}
		/**
		 * If we choose to end at the current state,
		 * we have to correct for all the words in
		 * the current states descendants. 
		 * 
		 * @param wordIndex
		 * @return
		 */
		public float getFinishWordScore(int wordIndex) {
			assert hasWords();
			return (float) Math.log(scoredWords.get(wordIndex).score / sumWordScores);
		}

		/**
		 * @param w
		 * @return
		 */
		public String getWord(int w) {
			return scoredWords.get(w).word;
		}

		public String toString() {
			return "TrieState("+phoneIndexer.get(phone) +")";
		}
	}

	public LexiconTrie(PronounciationDictionary pDict, LanguageModel langModel) {
		
		Set<String> dictWords = pDict.getDictionary().keySet();
		Iterator<String> it = dictWords.iterator();
		while (it.hasNext()) {
			String dictWord = it.next();
			if (!langModel.getSupport().contains(dictWord)) {
				it.remove();
			}
		}
		
		this.langModel = langModel;
		numBasePhones = pDict.getPhoneIndexer().size();
		roots = new ArrayList<TrieState>(numBasePhones);
		this.phoneIndexer = (PhoneIndexer) pDict.getPhoneIndexer();

		// Make Roots
		// They share the same successors
		// and transition scores
		states = new ArrayList<TrieState>();
		List<Successor> rootSuccs = new ArrayList<Successor>();
		for (int ph=0; ph < numBasePhones; ++ph) {
			TrieState root = new TrieState((short) ph, null);
			roots.add(root);
			root.successors = rootSuccs;	
		}

		create(pDict);	
		
		scoreWordTransitions(roots.get(0));

		statesByPhone = new List[numBasePhones];
		for (int ph=0; ph < numBasePhones; ++ph) {
			statesByPhone[ph] = new ArrayList<TrieState>();
		}
		List<TrieState> copyStates = new ArrayList<TrieState>(states);
		for (TrieState state: copyStates) {		
			addSilence(state);
		}
		for (TrieState state: states) {
			statesByPhone[state.phone].add(state);
		}
		System.err.println("Lexicon Trie has " + pDict.getDictionary().keySet().size() + " words");
		System.err.println("Lexicon Trie has " + states.size() + " states");

	}

	private void create(PronounciationDictionary pDict) {		
		for (Map.Entry<String, int[]> entry: pDict.getDictionary().entrySet()) {
			String word = entry.getKey();
			int[] phoneIndices = entry.getValue();
			insert(word, phoneIndices);
		}
	}

	private void insert(String word, int[] phoneIndices) {
		int numPhones = phoneIndices.length;
		TrieState prev = null;
		TrieState state = roots.get(0);
		for (int i=0; i < numPhones; ++i) {			
			int ph = phoneIndices[i];						
			Successor succ = state.getPhoneSuccessor(ph);
			if (succ == null) {
				state.addSuccessor(new TrieState((short) ph, prev), 0.0f);
			}
			prev = state;
			state = state.getPhoneSuccessor(ph).state;
		}
		state.addWord(word);
	}


	private double  scoreWordTransitions(TrieState trieState) {
		double sum = 0;
		if (trieState.hasWords()) {	
			for (int i=0; i < trieState.getNumWords(); ++i) {
				ScoredWord scoredWord = trieState.scoredWords.get(i);				
				sum += scoredWord.score;
			}			
		}

		if (trieState.isTerminal()) {
			trieState.sumWordScores = sum;
			return sum;
		}

		for (Successor succ: trieState.successors) {
			if (succ != null) {
				sum += scoreWordTransitions(succ.state);
			}
		}

		trieState.sumWordScores = sum;
		for(Successor succ: trieState.successors) {
			double prob = (double) succ.state.sumWordScores / (double) sum;
			succ.score = (float) Math.log(prob);
		}

		return sum;
	}

	public List<TrieState> getRootStates() {
		return roots;
	}

	public int getNumTrieStates() {
		return states.size();		
	}
	
	private void addSilence(TrieState state) {
		if(state.isTerminal()) {
			return;
		}
		TrieState silState = new TrieState((short) 2, state);
		silState.successors =  new ArrayList<Successor>(state.successors) ;
		state.addSuccessor(silState, 0.0f);		
	}

	public static void main(String[] args) {
		Runtime r = Runtime.getRuntime();
		long bytes = r.totalMemory();
		double megabytes =  (bytes / 1.0e6);
		System.out.println("MBs: " + megabytes);

		AcousticModel acousticModel = new AcousticModel.SubphoneHMMWrapper(args[0]);		
		PronounciationDictionary pDict = new CMUDict(args[1],acousticModel.getPhoneIndexer());

		bytes = r.totalMemory();
		megabytes =  (bytes / 1.0e6);
		System.out.println("MBs: " + megabytes);

		LanguageModel langModel = new UniformUnigramLanguageModel(pDict.getDictionary().keySet());
		LexiconTrie lexTrie = new LexiconTrie(pDict, langModel);
		pDict = null; 

		bytes = r.totalMemory();
		megabytes =  (bytes / 1.0e6);
		System.out.println("MBs: " + megabytes);
	}

	/**
	 * @param goalState
	 */
	public boolean isRootState(int goalState) {
		return goalState <  numBasePhones;		
	}
	
	public boolean isRootState(TrieState goalState) {
		return isRootState(goalState.index);		
	}
	
	
}
