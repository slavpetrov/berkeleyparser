/**
 * 
 */
package edu.berkeley.nlp.speech.decoder;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import edu.berkeley.nlp.HMM.AcousticModel;
import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.speech.decoder.LexiconTrie.Successor;
import edu.berkeley.nlp.speech.decoder.LexiconTrie.TrieState;
import edu.berkeley.nlp.speech.decoder.dict.CMUDict;
import edu.berkeley.nlp.speech.decoder.dict.PronounciationDictionary;
//import edu.berkeley.nlp.speech.decoder.dict.TimitDict;
import edu.berkeley.nlp.speech.phonedecoder.ExhaustiveDecoder;
import edu.berkeley.nlp.speech.phonedecoder.PhonePosteriorChart;
import edu.berkeley.nlp.speech.phonedecoder.PhonePruner;
import edu.berkeley.nlp.speech.phonedecoder.ThresholdPruner;
import fig.basic.Option;
import fig.exec.Execution;

/**
 * @author aria42
 *
 */
public class SloppyExhaustiveViterbiDecoder {

	AcousticModel acousticModel ; 
	LexiconTrie lexTrie ;
	LanguageModel langModel ;
	PhonePosteriorChart posteriorChart  ;
	PhonePruner phonePruner ;
	int silencePhone ;

	// All Derived from the abobve
	int maxObsLength  = 210;
	int numTrieStates ;
	int maxNumSubphone ;
	int numBasePhone ; //# un-annotated phones (50)
	int startPhone;
	int endPhone;

	float[][][] forwardScores ;
	float[][][] obsScoresCached ;	
	double[][][] phoneSubstatePosteriors ;
	boolean[][][] unprunedCoarseStates ;

	// Obs. Specific
	double[][] obs;

	// length of observation  
	int T; 


	public SloppyExhaustiveViterbiDecoder(AcousticModel acousticModel, LexiconTrie lexTrie) { 
		this.acousticModel = acousticModel;
		this.lexTrie = lexTrie;

		// How many base phones
		numBasePhone = acousticModel.getPhoneIndexer().size();
		maxNumSubphone = acousticModel.getMaxNumberOfSubstates();
		numTrieStates = lexTrie.getNumTrieStates();
		silencePhone = 2;

		this.startPhone = acousticModel.getStartPhoneIndex();
		this.endPhone = acousticModel.getEndPhoneIndex();

		// Create Arrays
		System.err.println("About to call createArrays()");
		createArrays();
		System.err.println("Done createArrays()");
		phonePruner = new ThresholdPruner.SimplePruner(1.0e-8);
		posteriorChart = new PhonePosteriorChart.ChartFromPhoneDecoder(new ExhaustiveDecoder.Factory());
		posteriorChart.setModel(acousticModel);
	}

	private void createArrays() {
		forwardScores = new float[maxObsLength][numTrieStates][];
		obsScoresCached = new float[maxObsLength][numBasePhone][];
		phoneSubstatePosteriors = new double[maxObsLength][numBasePhone][maxNumSubphone];
		for (int t=0; t < maxObsLength; ++t) {
			for (int ph=0; ph < numBasePhone; ++ph) {
				int numstates = acousticModel.getNumStates(ph);
				obsScoresCached[t][ph] = new float[numstates];				
			}
			for (int s=0; s < numTrieStates; ++s) {
				int numstates = acousticModel.getNumStates(lexTrie.states.get(s).phone);
				forwardScores[t][s] = new float[numstates];		
			}
		}
	}

	private void clearArrays() {
		for (int t=0; t < T; ++t) {
			for (int s=0; s < numTrieStates; ++s) {
				Arrays.fill(forwardScores[t][s], Float.NEGATIVE_INFINITY);				
			}
		}
	}

	private void cacheObservationScores() {
		for (int i=0; i < obs.length; ++i) {
			for (int ph=0; ph < numBasePhone; ++ph) {
				int numSubs = acousticModel.getNumStates(ph);
				for (int sub=0; sub < numSubs; ++sub) {
					if (ph != startPhone && ph != endPhone) {
						obsScoresCached[i][ph][sub] = (float) acousticModel.getObservationScore(ph, sub, obs[i]);
					}
				}
			}			
		}
	}

	public List<String> decode(double[][] obs) {
		this.obs = obs;
		this.T = obs.length + 1;

		// Make sure HMM used only once
		cacheObservationScores();

		// Do Coarse projection where
		// we ignore lexicon trie states
		if (Runner.useCoarse) {
			posteriorChart.setInput(obs);		
			posteriorChart.fillPosteriors(phoneSubstatePosteriors);			
		}
		unprunedCoarseStates = (Runner.useCoarse ? phonePruner.prune(phoneSubstatePosteriors, null) : null);


		// Do forward pass with 
		// pruned state space
		forwardPass();
		System.out.println(getBestScore());
		// Loop Over Root Tries to find best one
		return decodePass();
	}

	private boolean approxEquals(double guess, double gold) {
		return Math.abs(guess-gold) < 1.0e-5;
	}

	private List<String> decodePass() {
		List<String> sent = new ArrayList<String>();
		double goalScore = Double.NEGATIVE_INFINITY;
		TrieState goalState = null;
		int goalSub = -1;

		List<TrieState> roots = lexTrie.getRootStates();
		for (int ph=0; ph < numBasePhone; ++ph) {
			TrieState root = roots.get(ph);
			int numSubs = acousticModel.getNumStates(ph);
			for (int sub=0; sub < numSubs; ++sub) {
				double score = forwardScores[T-1][root.index][sub];
				if (score > goalScore) {
					goalScore = score;
					goalState = root;
					goalSub = sub;
				}
			}
		}

		for (int t=T-2; t > 0; --t) {

			if (lexTrie.isRootState(goalState)) {				
				// Epsilon Transition Back
				boolean foundPath = false;
				short phone = goalState.phone;
				List<TrieState> states = lexTrie.statesByPhone[phone];
				for (TrieState state: states) {
					if (foundPath) {
						break;
					}										
					if (!state.hasWords()) {
						continue;
					}										
					for (int w=0; w < state.getNumWords(); ++w) {
						if (foundPath) {
							break;
						}					
						String word = state.getWord(w);
						double wordScore = state.getFinishWordScore(w);
						// Take a hit for ending the word							
						double newScore = forwardScores[t][state.index][goalSub] + wordScore;
						if (approxEquals(newScore, goalScore)) {
							goalState = state;
							goalScore = forwardScores[t][state.index][goalSub];
							foundPath = true;
							sent.add(word);
						}
					}				
				}
			}
			// Change sub only
			boolean foundPath = false;
			int numSubs = acousticModel.getNumStates(goalState.phone);			
			for (int sub=0; sub < numSubs; ++sub) {
				// transition sub -> goalSub
				double newScore = forwardScores[t][goalState.index][sub] + 
				Math.log(acousticModel.getTransitionScore(goalState.phone, sub, goalState.phone, goalSub));
				if (approxEquals(newScore, goalScore)) {
					goalScore = forwardScores[t][goalState.index][sub];
					goalSub = sub;
					foundPath = true;
					break;
				}
			}

			if (foundPath) {
				continue;
			}

			// Take Trie Step back
			TrieState state = goalState.prev;
			numSubs = acousticModel.getNumStates(state.phone);
			for (int sub=0; sub < numSubs; ++sub) {
				double newScore = forwardScores[t][state.index][sub] +
				Math.log(acousticModel.getTransitionScore(goalState.phone, sub, goalState.phone, goalSub)) +
				obsScoresCached[t][state.phone][sub] + state.getPhoneSuccessor(goalState.phone).score;
				if (approxEquals(newScore, goalScore)) {
					goalScore = forwardScores[t][goalState.index][sub];
					goalState = state;
					goalSub = sub;
					foundPath = true;
					break;
				}
			}
			assert foundPath;
		}
		return sent;
	}

	public double getBestScore() {
		List<TrieState> roots = lexTrie.getRootStates();
		double max = Double.NEGATIVE_INFINITY;
		for (int ph=0; ph < numBasePhone; ++ph) {
			TrieState root = roots.get(ph);
			int numSubs = acousticModel.getNumStates(ph);
			for (int sub=0; sub < numSubs; ++sub) {
				double score = forwardScores[T-1][root.index][sub];
				if (score > max) {
					max = score;
				}
			}
		}
		return max;
	}

	private boolean prunedState(int t, int p) {
		return Runner.useCoarse && unprunedCoarseStates[t][p] == null;
	}

	private boolean prunedState(int t, int p, int sub) {
		if (prunedState(t, p)) {
			return true;
		}
		return Runner.useCoarse && !unprunedCoarseStates[t][p][sub];
	}



	/**
	 * Log-Scale Exhaustive Forward Pass 
	 * 
	 * @param unprunedCoarseStates keepState[t][ph][sub] means
	 * we should explore a (t,state,sub) projection
	 * which is finer than (t,ph,sub) 
	 *
	 */
	public void forwardPass() {

		// Init
		clearArrays();
		initForwardScores();

		//  Variable Names
		//  ===============
		//  s = Trie State
		//  t = Time Frame
		//  p = phone
		//  sub = sub-phone

		for (int t=0; t+1 < T; ++t)  {
			for (int phone=0; phone < numBasePhone; ++phone) {


				int numSubs = acousticModel.getNumStates(phone);

				List<TrieState> states = lexTrie.statesByPhone[phone];

				if (states == null) { 
					assert phone == startPhone || phone == endPhone;
					continue; 
				}

				// No substates 
				// with this phone were kept
				if (prunedState(t, phone)) {
					continue;
				}

				for (int sub=0; sub < numSubs; ++sub) {

					// Don't loop over if pruned
					if (prunedState(t, phone,sub)) {
						continue;
					}

					for (TrieState state: states) {

						// We are extending hypothesis
						// so if it has no successors, don't bother
						if (state.isTerminal()) {
							continue;
						}

						double prevScore = forwardScores[t][state.index][sub];

						// Don't Bother with this branch
						if (prevScore == Double.NEGATIVE_INFINITY) {
							continue;
						}

						// (t, s, sub) hypothesis 

						// (1) Change only subphone of curphone.
						//     Only uses HMM params.
						if (phone != startPhone &&  phone != endPhone) {
							for (int nextSub=0; nextSub < numSubs; ++nextSub) {						
								double obsScore = obsScoresCached[t][phone][nextSub];
								double transScore = Math.log(acousticModel.getTransitionScore(phone, sub, phone, nextSub));
								float newScore = (float) (prevScore + transScore + obsScore);
								forwardScores[t+1][state.index][nextSub] = Math.max(forwardScores[t+1][state.index][nextSub], newScore);
							}
						}


						//(2) (Must) Change phone, step in Lexicon state 
						//    Also change substate
						for (int nextPhone=0; nextPhone < numBasePhone; ++nextPhone) {
							if (phone == nextPhone) {
								continue;
							}
							Successor succ = state.getPhoneSuccessor(nextPhone);
							if (succ == null) { // State doesn't exist in trie
								continue;
							}
							double stateTrans = succ.score;
							int numNextSubs = acousticModel.getNumStates(nextPhone);
							for (int nextSub=0; nextSub < numNextSubs; ++nextSub) {								
								double acoustTrans = Math.log(acousticModel.getTransitionScore(phone, sub, nextPhone, nextSub));
								double acoustObs = obsScoresCached[t][nextPhone][nextSub];
								float newScore = (float)(stateTrans + acoustTrans + acoustObs + prevScore);
								forwardScores[t+1][succ.state.index][nextSub] = Math.max(forwardScores[t+1][succ.state.index][nextSub], newScore);

								// (3) The current state has word(s) associated with
								// it. Consider ending the current word, and looping
								// up to approriate Trie root
								if (succ.state.hasWords()) {										
									int numWords = succ.state.getNumWords();
									assert numWords > 0; // Should have something
									TrieState root = lexTrie.getRootStates().get(nextPhone);
									// Loop Over Words ending in this state
									for (int w=0; w < numWords; ++w) {
										// We've Chosen this Word										
										double wordScore = succ.state.getFinishWordScore(w);
										// Take a hit for ending the word, also for the
										// LM score would go 
										newScore = (float)(newScore + wordScore);
										forwardScores[t+1][root.index][nextSub] = Math.max(forwardScores[t+1][root.index][nextSub], newScore);
									}
								}

							}
						}
					}
				}
			}
		}
	}

	private void initForwardScores() {
		// There is a fixed start trieState, but there 
		// may be many sub-start phones  
		List<TrieState> roots = lexTrie.getRootStates();
		for (int sub=0; sub < acousticModel.getNumStates(startPhone); ++sub) {
			forwardScores[0][roots.get(startPhone).index][sub] = 0.0f;
		}
	}

	public static void main(String[] args) {		
		Runner runner = new Runner();						
		Execution.init(args, runner);
		runner.run();
		Execution.finish();
	}

	public static class Runner {

		@Option(required=true)
		public String pDictPath ;
		@Option(required=true)
		public String model;
		@Option()
		public static boolean useCoarse = false;
		@Option(required=true)
		public static String path;
		@Option(required=true)
		public static String treebankPath;

		private double[][] read(String file) {
			List<double[]>	rows = new ArrayList<double[]>();
			try {
				BufferedReader br = new BufferedReader(new FileReader(file));
				int index = 0;
				double[] row = new double[39];
				rows.add(row);
				while (true) {
					String line = br.readLine();
					if (line == null) {
						break;
					}
					double x = Double.parseDouble(line);
					row[index++] = x;
					if (index == 39) {
						row = new double[39];
						index = 0;
						rows.add(row);
					}
				}
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			double[][] obs = new double[rows.size()][39];
			for (int i=0; i < rows.size(); ++i) { 
				obs[i] = rows.get(i);
			}
			return obs;
		}

		public void run() {
			long start = System.currentTimeMillis();
			AcousticModel acousticModel = new AcousticModel.SubphoneHMMWrapper(model);
			PronounciationDictionary pDict = /*(pDictPath.toLowerCase().contains("timit") ? 
						new TimitDict(pDictPath, acousticModel.getPhoneIndexer()) :*/
						new CMUDict(pDictPath, acousticModel.getPhoneIndexer());
			double[][] obs = read(path);
			long stop = System.currentTimeMillis();
			double secs = (stop-start) / 1.0e3;			
			System.out.printf("time to load: %.3f secs\n", secs);

			UnigramLanguageModel languageModel = UnigramLanguageModel.getTreebankUnigramModel(treebankPath);//new UniformUnigramLanguageModel(pDict.getDictionary().keySet());
			languageModel.prune(200);
			LexiconTrie lexTrie = new LexiconTrie(pDict, languageModel);		
			SloppyExhaustiveViterbiDecoder decoder = new SloppyExhaustiveViterbiDecoder(acousticModel, lexTrie);

			useCoarse = false;
			start = System.currentTimeMillis();
			List<String> sent = decoder.decode(obs);
			System.out.println(sent);
			stop = System.currentTimeMillis();
			double coarseSecs = (stop-start) / 1000.0;
			double coarseMax = decoder.getBestScore();
			System.out.printf("Coarse: %.3f\n secs.  Score: %.5f\n",coarseSecs,coarseMax);
		}
	}

}
