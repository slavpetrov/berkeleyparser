/**
 * 
 */
package edu.berkeley.nlp.speech.decoder;

import java.util.ArrayList;
import java.util.List;

import edu.berkeley.nlp.HMM.AcousticModel;
import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.speech.decoder.LexiconTrie.TrieState;
import edu.berkeley.nlp.speech.decoder.dict.CMUDict;
import edu.berkeley.nlp.speech.decoder.dict.PronounciationDictionary;

/**
 * @author aria42
 *
 */
public class SloppyBeamViterbiDecoder {

//	private static interface Scored {
//		public double getScore() ;
//	}
//
//	private static class Beam<T extends Scored> {
//
//		int beamSize = 1000;
//		double beamFraction = 0.1;
//		List<T> items = new ArrayList<T>();
//
//		public List<T> getItems() { 
//			return items;
//		}
//
//		public boolean add(T t) {
//			if (items.size() < beamSize) {
//				items.add(t);
//				return true;
//			}
//			return insert(t);									
//		}
//
//		private boolean insert(T t) {
//			T last = items.get(items.size()-1);
//			if (t.getScore() <= last.getScore()) {
//				return false;
//			}
//			for (int i=0; i < items.size(); ++i) {
//				T cur = items.get(i);
//				if (t.getScore() > cur.getScore()) {
//					items.add(i, t);
//					items.remove(i+1);
//					return true;
//				}
//			}						
//			throw new IllegalStateException("t: " + t.getScore());
//		}
//
//
//	}
//
//	private static class Hypothesis implements Scored {
//		double score = 0.0;
//		int trieState;
//		int subPhone;
//		List<String> words = new ArrayList<String>();
//		public Hypothesis(int trieState, int subPhone) {
//			this.trieState = trieState;
//			this.subPhone = subPhone;
//		}
//
//		public double getScore() {
//			return score;
//		}
//
//		public String toString() {
//			return String.format("Hyp(%d,%d,%s)", trieState, subPhone, words.toString());
//		}
//	}
//
//	AcousticModel acousticModel ;
//	LexiconTrie lexTrie ;
//
//	// All Derived from the abobve
//	int maxObsLength  = 10000;
//	int numTrieStates;
//	int maxNumSubphone;
//	int numBasePhone;
//	int startPhone;
//	int endPhone;
//
//	// Obs. Specific
//	double[][] obs ;
//	// length of observation
//	int T ; 
//
//	public SloppyBeamViterbiDecoder(AcousticModel acousticModel, LexiconTrie lexTrie) {
//		this.acousticModel = acousticModel;
//		this.lexTrie = lexTrie;
//
//		// How many base phones
//		numBasePhone = acousticModel.getPhoneIndexer().size();
//		maxNumSubphone = acousticModel.getMaxNumberOfSubstates();
//		numTrieStates = lexTrie.getNumTrieStates();
//
//		this.startPhone = acousticModel.getStartPhoneIndex();
//		this.endPhone = acousticModel.getEndPhoneIndex();
//	}
//
//	public List<String> decode(double[][] obs) {
//
//		// Current
//		Beam<Hypothesis> curHyps = new Beam<Hypothesis>();
//
//		this.T = obs.length + 1;
//
//
//		// Initial Hypothesis
//		// ===================	
//		//  
//		TrieState[] roots = lexTrie.getRootStates();
//		for (int sub=0; sub < acousticModel.getNumStates(startPhone); ++sub) {
//			Hypothesis hyp = new Hypothesis(roots[startPhone].index, sub);
//			curHyps.add(hyp);						
//		}
//
//		//  Variable Names
//		//  ===============
//		//  s = Trie State
//		//  t = Time Frame
//		//  p = phone
//		//  sub = sub-phone
//
//		for (int t=0; t+1 < T; ++t)  {
//			// Loop over hypothesis
//			Beam<Hypothesis> nextHyps = new Beam<Hypothesis>();
//			for (Hypothesis hyp: curHyps.getItems()) {
//				double curScore = hyp.score;
//				TrieState state = lexTrie.states[hyp.trieState];
//				assert state != null;
//				int phone = state.phone;
//				int subPhone = hyp.subPhone;
//				int numSubs = acousticModel.getNumStates(phone);
//
//
//				//	(1) Change only subphone of curphone
//				//  Only uses HMM params
//				if (phone != startPhone &&  phone != endPhone) {
//					for (int nextSub=0; nextSub < numSubs; ++nextSub) {
//						double obsScore = acousticModel.getObservationScore(phone, nextSub, obs[t]);
//						double transScore = acousticModel.getTransitionScore(phone, subPhone, phone, nextSub);
//						Hypothesis newHyp = new Hypothesis(state.index, nextSub);
//						newHyp.score = (transScore + obsScore + curScore); 
//						nextHyps.add(newHyp);						
//					}
//				}
//
//				//		(2) (Must) Change phone, step in Lexicon state 
//				//    Also change substate
//				for (int nextPhone=0; nextPhone < numBasePhone; ++nextPhone) {
//					TrieState succ = state.successors[nextPhone];
//					if (succ == null) { // State doesn't exist in trie
//						continue;
//					}
//					double stateTrans = state.scores[nextPhone];
//					int numNextSubs = acousticModel.getNumStates(nextPhone);
//					for (int nextSub=0; nextSub < numNextSubs; ++nextSub) {								
//						double acoustTrans = acousticModel.getTransitionScore(phone, subPhone, nextPhone, nextSub);
//						double acoustObs = acousticModel.getObservationScore(nextPhone, nextSub, obs[t]);
//
//						Hypothesis newHyp = new Hypothesis(succ.index, nextSub);
//						newHyp.score = (stateTrans + acoustTrans + acoustObs + curScore);
//						if (!succ.isTerminal()) {
//							nextHyps.add(newHyp);
//						}
//
//						// (3) The current state has word(s) associated with
//						// it. Consider ending the current word, and looping
//						// up to approriate Trie root
//						if (succ.hasWords()) {										
//							int numWords = succ.getWordScoreSum();
//							assert numWords > 0; // Should have something
//							TrieState root = lexTrie.getRootStates()[nextPhone];
//							// Loop Over Words ending in this state
//							for (int w=0; w < numWords; ++w) {
//								String word = succ.scoredWords[w];
//								// We've Chosen this Word
//								// TODO: Figure out what this should be
//								double wordScore = succ.getFinishWordScore(w);
//								// Take a hit for ending the word, also for the
//								// LM score would go
//								double newScore = newHyp.score + wordScore;
//								newHyp = new Hypothesis(root.index, nextSub);
//								newHyp.score = newScore;
//								newHyp.words = new ArrayList<String>(newHyp.words);
//								newHyp.words.add(word);
//								nextHyps.add(hyp);
//							}
//						}
//					}
//				}				
//			}
//			curHyps = nextHyps;
//		}
//		
//		System.out.println("Score: " + curHyps.getItems().get(0).score);
//
//		for (Hypothesis hyp: curHyps.getItems()) {
//			List<String> sent = hyp.words;
//			if (!sent.isEmpty()) {
//					return sent;
//			}
//		}
//		
//		throw new IllegalStateException();
//	}
//
//	public static void main(String[] args) {
//		long start = System.currentTimeMillis();
//		AcousticModel acousticModel = new AcousticModel.SubphoneHMMWrapper(args[0]);
//		PronounciationDictionary pDict = new CMUDict(args[1], acousticModel.getPhoneIndexer());
//		long stop = System.currentTimeMillis();
//		double secs = (stop-start) / 1.0e3;
//		System.out.printf("time to load: %.3f secs\n", secs);
//
//		double[][] obs = new double[1000][39];
//		LexiconTrie lexTrie = new LexiconTrie(pDict);		
//		SloppyBeamViterbiDecoder decoder = new SloppyBeamViterbiDecoder(acousticModel, lexTrie);
//
//		start = System.currentTimeMillis();
//		for (int i=0; i < 10; ++i) {
//			List<String> sent = decoder.decode(obs);
//			System.out.println(sent);
//		}
//
//		System.out.println(DoubleArrays.toString(obs[0]));
//		stop = System.currentTimeMillis();
//		secs = (stop-start) / 1000.0;
//		System.out.printf("time to decode: %.3f secs\n", secs);
//	}
//

}

