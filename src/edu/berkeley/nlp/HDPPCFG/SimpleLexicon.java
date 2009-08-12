package edu.berkeley.nlp.HDPPCFG;

import edu.berkeley.nlp.HDPPCFG.smoothing.Smoother;
import edu.berkeley.nlp.HDPPCFG.vardp.DirichletCollectionFactory;
import edu.berkeley.nlp.HDPPCFG.vardp.DiscreteDistrib;
import edu.berkeley.nlp.HDPPCFG.vardp.DiscreteDistribCollectionFactory;
import edu.berkeley.nlp.HDPPCFG.vardp.MLECollectionFactory;
import edu.berkeley.nlp.HDPPCFG.vardp.TopLevelDistrib;
import edu.berkeley.nlp.HDPPCFG.vardp.TopLevelWordDistrib;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.math.SloppyMath;
import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.Counter;
import fig.basic.Indexer;
import edu.berkeley.nlp.util.Numberer;
import fig.basic.Pair;
import edu.berkeley.nlp.util.PriorityQueue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.*;

/**
 * Simple default implementation of a lexicon, 
 * which scores word, tag pairs with P(word|tag)
 *
 * instead of dealing with words as strings we will map them to integers
 * with a wordIndexer. to further simplify things each tag will have 
 * its own mapping from wordIndex to a tag-specific index so that we
 * don't have to deal with unobserved events
 * 
 * assumes that rare words have been replaced with some unknown word token
 */
public class SimpleLexicon implements java.io.Serializable, LexiconInterface {
	Indexer<Integer>[] tagWordIndexer;
	double[][][] expectedCounts; 
	public double[][][] scores; 
	DiscreteDistrib[][] params; // indexed by tag, substate
	
//	public static final String UNKTOKEN = "*UNK*";
//	int unkIndex;
	
  /** A trick to allow loading of saved Lexicons even if the version has changed. */
  private static final long serialVersionUID = 2L;
  /** The number of substates for each state */
  short[] numSubStates;
  int numStates;
  int nWords;
  
  double threshold;
  boolean isLogarithmMode;
  boolean useVarDP = false;
  
  Indexer<String> wordIndexer;
  Smoother smoother;
  
  
  // additions from the stanford parser which are needed for a better 
  // unknown word model...
  /**
   * We cache the last signature looked up, because it asks for the same one
   * many times when an unknown word is encountered!  (Note that under the
   * current scheme, one unknown word, if seen sentence-initially and
   * non-initially, will be parsed with two different signatures....)
   */
  protected transient String lastSignature = "";
  protected transient int lastSentencePosition = -1;
  protected transient String lastWordToSignaturize = "";
  private int unknownLevel = 5; //different modes for unknown words, 5 is english specific
  /**
   * A POS tag has to have been attributed to more than this number of word
   * types before it is regarded as an open-class tag.  Unknown words will
   * only possibly be tagged as open-class tags (unless flexiTag is on).
   */
  public static int openClassTypesThreshold = 50;

  /**
   * Start to aggregate signature-tag pairs only for words unseen in the first
   * this fraction of the data.
   */

  public void optimize(DiscreteDistribCollectionFactory ddcFactory,
      TopLevelWordDistrib[] topDistribs) {
   	if (useVarDP){
   		fig.basic.LogInfo.logs("Using Percy's updates for the lexicon.");
   		// doesn't use the word level distribution
    	for (int tag=0; tag<expectedCounts.length; tag++){
    		for (int substate=0; substate<numSubStates[tag]; substate++){
    			// Create new parameters if they don't exist
          if(params[tag][substate]==null)
            params[tag][substate] = ddcFactory.newWord(topDistribs[tag], tagWordIndexer[tag].size());
          // Update parameters
          params[tag][substate].update(expectedCounts[tag][substate]);
          // Get scores to parse with next round
          scores[tag][substate] = params[tag][substate].getScores();
    		}
    	}
   	}
  	
   	else {
	  	for (int tag=0; tag<expectedCounts.length; tag++){
	  		for (int substate=0; substate<numSubStates[tag]; substate++){
	  			double mass = ArrayUtil.sum(expectedCounts[tag][substate]);
	
	  			double normalizer = (mass==0) ? 0 : 1.0/mass;
		  		for (int word=0; word<expectedCounts[tag][substate].length; word++){
		  			scores[tag][substate][word] = expectedCounts[tag][substate][word]*normalizer;
		  		}
	  		}
	  	}
  	}
   	
   	// smooth the scores
   	if (smoother!=null){
	  	for (short tag=0; tag<expectedCounts.length; tag++){
	  		for (int word=0; word<expectedCounts[tag][0].length; word++){
	  			double[] res = new double[numSubStates[tag]];
	  			for (int substate=0; substate<numSubStates[tag]; substate++){
	  				res[substate] = scores[tag][substate][word];
	  			}
	  			smoother.smooth(tag,res);
	  			for (int substate=0; substate<numSubStates[tag]; substate++){
	  				scores[tag][substate][word] = res[substate];
	  			}
	  		}
	  	}
  	}
  }

  /** Create a blank Lexicon object.  Fill it by
   * calling tallyStateSetTree for each training tree, then
   * calling optimize().
   * 
   * @param numSubStates
   */
  @SuppressWarnings("unchecked")
	public SimpleLexicon(short[] numSubStates, int smoothingCutoff, double[] smoothParam, 
			Smoother smoother, double threshold, StateSetTreeList trainTrees, int minWordCount) {
  	this(numSubStates,smoothingCutoff,smoothParam,smoother,threshold);
  	init(trainTrees,minWordCount);
  }

	public SimpleLexicon(short[] numSubStates, int smoothingCutoff, double[] smoothParam, 
			Smoother smoother, double threshold) {
  	this.numSubStates = numSubStates;
    this.threshold = threshold;
    this.wordIndexer = new Indexer<String>();
    this.numStates = numSubStates.length;
    this.isLogarithmMode = false;
  }

  public double[] score(String word, short tag, int loc, boolean noSmoothing, boolean isSignature) {
		double[] res = new double[numSubStates[tag]];
		int globalWordIndex = wordIndexer.indexOf(word);
//		boolean knownWord = false;
//		String sig = getSignature(word, loc);
		if (globalWordIndex==-1) globalWordIndex = wordIndexer.indexOf(getSignature(word, loc));
//		else knownWord = true;
		int tagSpecificWordIndex = tagWordIndexer[tag].indexOf(globalWordIndex);
		if (tagSpecificWordIndex==-1){
//			if (!knownWord){
//				tagSpecificWordIndex = tagWordIndexer[tag].indexOf(unkIndex);
//			} else { // we have seen this word, just not with this tag
				if (isLogarithmMode) Arrays.fill(res, Double.NEGATIVE_INFINITY);
				else Arrays.fill(res, 0);
				//if (isLogarithmMode) Arrays.fill(res, -80);//Double.NEGATIVE_INFINITY);
				//else Arrays.fill(res, 1e-80);
				return res;
//			}
		}
		for (int i=0; i<numSubStates[tag]; i++){
			res[i] = scores[tag][i][tagSpecificWordIndex]; 
		}
		return res;
  } 

  public void trainTree(Tree<StateSet> trainTree, double randomness, LexiconInterface oldLexicon,
  		boolean secondHalf, boolean noSmoothing) {
    trainTree(trainTree, randomness, oldLexicon, secondHalf, noSmoothing, 1);
  }

  /**
   * Trains this lexicon on the Collection of trees.
   */
  public void trainTree(Tree<StateSet> trainTree, double randomness, LexiconInterface oldLexicon,
  		boolean secondHalf, boolean noSmoothing, double dataFactor) {
  	// scan data
    //for all substates that the word's preterminal tag has
    double sentenceScore = 0;
    if (randomness == -1){
      sentenceScore = trainTree.getLabel().getIScore(0);
  		if (sentenceScore==0){
  			System.out.println("Something is wrong with this tree. I will skip it.");
  			return;
  		}
    }
    int sentenceScale = trainTree.getLabel().getIScale();
  	
  	List<StateSet> words = trainTree.getYield();
  	List<StateSet> tags = trainTree.getPreTerminalYield();
  	//for all words in sentence
  	for (int position = 0; position < words.size(); position++) {
  		
  		int nSubStates = tags.get(position).numSubStates();
  		short tag = tags.get(position).getState();
  		
  		String word = words.get(position).getWord();
  		int globalWordIndex = wordIndexer.indexOf(word);
  		int tagSpecificWordIndex = tagWordIndexer[tag].indexOf(globalWordIndex);
  		
  		double[] oldLexiconScores = null;
  		if (randomness==-1)
  			oldLexiconScores = oldLexicon.score(word,tag,position,noSmoothing,false);
  		
  		StateSet currentState = tags.get(position);
      double scale = Math.pow(GrammarTrainer.SCALE,currentState.getOScale()-sentenceScale) / sentenceScore;
  		
      for (short substate=0; substate<nSubStates; substate++) {
        double weight = 1;
        if (randomness == -1) {
          //weight by the probability of seeing the tag and word together, given the sentence
          if (!Double.isInfinite(scale))
            weight = currentState.getOScore(substate) * oldLexiconScores[substate] * scale;
          else
          	weight = Math.exp(Math.log(GrammarTrainer.SCALE)
								* (currentState.getOScale() - sentenceScale)
								- Math.log(sentenceScore)
								+ Math.log(currentState.getOScore(substate))
								+ Math.log(oldLexiconScores[substate]));
        }
        else if (randomness==0){
          // for the baseline
          weight = 1;
        }
        else {
          //add a bit of randomness
          weight = GrammarTrainer.RANDOM.nextDouble()*randomness/100.0+1.0;
        }
        if (weight==0)
        	continue;
        //tally in the tag with the given weight
        
	  		expectedCounts[tag][substate][tagSpecificWordIndex] += weight * dataFactor;
      }
  	}
  }

  int wi(int tag, String word) {
    int i = wordIndexer.indexOf(word);
    //System.out.println(tag + " " + word + " " + i);
    if(i == -1) return -1;
    //System.out.println("  " + tagWordIndexer[tag].indexOf(i));
    return tagWordIndexer[tag].indexOf(i);
  }

  public void trainTreeG7() {
    fig.basic.LogInfo.logs("SimpleLexicon.trainTreeG7()");
    // doesn't use the word level distribution
    for (int tag=0; tag<expectedCounts.length; tag++){
      System.out.println("Tag " + tag + " " + tagWordIndexer[tag].size());
      //if(tagWordIndexer[tag].size() > 0)
        //System.out.println(tagWordIndexer[tag].get(0) + " " + tagWordIndexer[tag].get(1));
      for (int substate=0; substate<numSubStates[tag]; substate++){
        double[] counts = expectedCounts[tag][substate];
        int w;
        w = wi(tag, "a"+substate); if(w != -1) counts[w] = 100;
        w = wi(tag, "b"+substate); if(w != -1) counts[w] = 100;
        w = wi(tag, "c"+substate); if(w != -1) counts[w] = 100;
        w = wi(tag, "d"+substate); if(w != -1) counts[w] = 100;
      }
   	}
  }
  

  public void setUseVarDP(boolean useVarDP) {
		this.useVarDP = useVarDP;
	}

  /*
   * replace all words with count < threshold with their unknown word signature
   * and also initialize the counters
   */
  public void init(StateSetTreeList trainTrees, int threshold){
  	Counter<String> wordCounts = new Counter<String>();
  	for (Tree<StateSet> tree : trainTrees){
  		List<StateSet> words = tree.getYield();
  		for (StateSet word : words){
  			String wordString = word.getWord();
  			wordCounts.incrementCount(wordString, 1.0);
  			if (wordCounts.getCount(wordString)>threshold) 
  				wordIndexer.add(wordString);
  		}
  	}
  	// replace the rare words and also add the others to the appropriate numberers
//  	unkIndex = wordNumberer.number(UNKTOKEN);
  	tagWordIndexer = new Indexer[numStates];
  	for (int tag=0; tag<numStates; tag++){
  		tagWordIndexer[tag] = new Indexer<Integer>();
//  		tagWordIndexer[tag].add(unkIndex);
  	}
  	for (Tree<StateSet> tree : trainTrees){
  		List<StateSet> words = tree.getYield();
  		List<StateSet> tags = tree.getPreTerminalYield();
  		int ind = 0;
  		for (StateSet word : words){
				String sig = word.getWord();
  			if (wordCounts.getCount(sig) <= threshold){
  				sig = getSignature(word.getWord(),ind);
  				word.setWord(sig);
  			}
				wordIndexer.add(sig);
//  			else {
  				tagWordIndexer[tags.get(ind).getState()].add(wordIndexer.indexOf(sig));
//  			}
  			ind++;
  		}
  	}
  	expectedCounts = new double[numStates][][];
  	scores = new double[numStates][][];
  	params = new DiscreteDistrib[numStates][];
  	for (int tag=0; tag<numStates; tag++){
  		expectedCounts[tag] = new double[numSubStates[tag]][tagWordIndexer[tag].size()];
  		scores[tag] = new double[numSubStates[tag]][tagWordIndexer[tag].size()];
  		params[tag] = new DiscreteDistrib[numSubStates[tag]];
  	}
  	nWords = wordIndexer.size();
  }
  
  
  
  public SimpleLexicon copyLexicon(){
  	SimpleLexicon copy = new SimpleLexicon(numSubStates,-1,null,null,-1);
  	copy.expectedCounts = new double[numStates][][];
  	copy.scores = new double[numStates][][];
  	copy.tagWordIndexer = new Indexer[numStates];
  	copy.wordIndexer = this.wordIndexer;
  	copy.params = new DiscreteDistrib[numStates][];
  	for (int tag=0; tag<numStates; tag++){
  		copy.tagWordIndexer[tag] = new Indexer<Integer>();
  		copy.tagWordIndexer[tag].addAll(tagWordIndexer[tag]);
  		copy.expectedCounts[tag] = new double[numSubStates[tag]][tagWordIndexer[tag].size()];
  		copy.scores[tag] = new double[numSubStates[tag]][tagWordIndexer[tag].size()];
  		copy.params[tag] = new DiscreteDistrib[numSubStates[tag]];
  	}
  	copy.nWords = this.nWords;
  	copy.smoother = this.smoother;
//  	copy.unkIndex = unkIndex;
  	return copy;
  }
  
	public boolean isLogarithmMode() {
		return isLogarithmMode;
	}
	public void logarithmMode() {
		if (isLogarithmMode) return;
  	for (int tag=0; tag<scores.length; tag++){
  		for (int word=0; word<scores[tag].length; word++){
  			for (int substate=0; substate<scores[tag][word].length; substate++){
  				scores[tag][word][substate] = Math.log(scores[tag][word][substate]); 
  			}
  		}
  	}
  	isLogarithmMode = true;
	}

  
  
  
  
	/**
	 * Split all substates in two, producing a new lexicon. The new Lexicon gives
	 * the same scores to words under both split versions of the tag. (Leon says:
	 * It may not be okay to use the same scores, but I think that symmetry is
	 * sufficiently broken in Grammar.splitAllStates to ignore the randomness
	 * here.)
	 * 
	 * @param randomness
	 *          (currently ignored)
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public Lexicon splitAllStates(int[] counts, boolean moreSubstatesThanCounts) {
//		short[] newNumSubStates = new short[numSubStates.length];
//		newNumSubStates[0] = 1; // never split ROOT
//		for (short i = 1; i < numSubStates.length; i++) {
//			// don't split a state into more substates than times it was actaully seen
////			if (!moreSubstatesThanCounts && numSubStates[i]>=counts[i]) { 
////				newNumSubStates[i]=numSubStates[i];
////				} 
////			else{
//				newNumSubStates[i] = (short)(numSubStates[i] * 2);
////			}
//		}
//		Lexicon lexicon = new SimpleLexicon(newNumSubStates, this.smoothingCutoff, smooth, smoother, this.threshold);
//		// copy and alter all data structures
//		lexicon.wordToTagCounters = new HashMap[numSubStates.length];
//		lexicon.unseenWordToTagCounters = new HashMap[numSubStates.length];
//		for (int tag = 0; tag < wordToTagCounters.length; tag++) {
//			if (wordToTagCounters[tag]!=null) {
//				lexicon.wordToTagCounters[tag] = new HashMap<String, double[]>();
//				for (String word : wordToTagCounters[tag].keySet()) {
//					lexicon.wordToTagCounters[tag].put(word,
//							new double[newNumSubStates[tag]]);
//					for (int substate = 0; substate < wordToTagCounters[tag].get(word).length; substate++) {
//						int splitFactor = 2;
//						if (newNumSubStates[tag]==numSubStates[tag]) { splitFactor = 1; } 
//						for (int i = 0; i < splitFactor; i++) {
//							lexicon.wordToTagCounters[tag].get(word)[substate * splitFactor + i] = (1.f/splitFactor) * wordToTagCounters[tag]
//									.get(word)[substate];
//						}
//					}
//				}
//			}
//		}
//		for (int tag = 0; tag < unseenWordToTagCounters.length; tag++) {
//			if (unseenWordToTagCounters[tag]!=null) {
//				lexicon.unseenWordToTagCounters[tag] = new HashMap<String,double[]>();
//				for (String word : unseenWordToTagCounters[tag].keySet()) {
//					lexicon.unseenWordToTagCounters[tag].put(word,new double[newNumSubStates[tag]]);
//					for (int substate = 0; substate < unseenWordToTagCounters[tag].get(word).length; substate++) {
//						int splitFactor = 2;
//						if (newNumSubStates[tag]==numSubStates[tag]) { splitFactor = 1; } 
//						for (int i = 0; i < splitFactor; i++) {
//							lexicon.unseenWordToTagCounters[tag].get(word)[substate * splitFactor + i] = (1.f/splitFactor) * unseenWordToTagCounters[tag]
//									.get(word)[substate];
//						}
//					}
//				}
//			}
//		}
//		lexicon.totalWordTypes = totalWordTypes;
//		lexicon.totalTokens = totalTokens;
//		lexicon.totalUnseenTokens = totalUnseenTokens;
//		lexicon.totalWords = totalWords;
//		lexicon.smoother = smoother;
//		lexicon.typeTagCounter = new double[typeTagCounter.length][];
//		lexicon.tagCounter = new double[tagCounter.length][];
//		lexicon.unseenTagCounter = new double[unseenTagCounter.length][]; 
//		lexicon.simpleTagCounter = new double[tagCounter.length];
//		for (int tag = 0; tag < typeTagCounter.length; tag++) {
//			lexicon.typeTagCounter[tag] = new double[newNumSubStates[tag]];
//			lexicon.tagCounter[tag] = new double[newNumSubStates[tag]];
//			lexicon.unseenTagCounter[tag] = new double[newNumSubStates[tag]];
//			lexicon.simpleTagCounter[tag] = simpleTagCounter[tag];
//			for (int substate = 0; substate < typeTagCounter[tag].length; substate++) {
//				int splitFactor = 2;
//				if (newNumSubStates[tag]==numSubStates[tag]) { splitFactor = 1; } 
//				for (int i = 0; i < splitFactor; i++) {
//					lexicon.typeTagCounter[tag][substate * splitFactor + i] = (1.f/splitFactor)*typeTagCounter[tag][substate];
//					lexicon.tagCounter[tag][substate * splitFactor + i] = (1.f/splitFactor)*tagCounter[tag][substate];
//					lexicon.unseenTagCounter[tag][substate * splitFactor + i] = (1.f/splitFactor)*unseenTagCounter[tag][substate];
//				}
//			}
//		}
//		lexicon.allTags = new HashSet<Short>(allTags);
//		lexicon.wordCounter = new Counter<String>();
//		for (String word : wordCounter.keySet()) {
//			lexicon.wordCounter.setCount(word, wordCounter.getCount(word));
//		}
//		lexicon.smoothingCutoff = smoothingCutoff;
//		lexicon.addXSmoothing = addXSmoothing;
//		lexicon.smoothInUnknownsThreshold = smoothInUnknownsThreshold;
//		
//		lexicon.wordNumberer = wordNumberer;
//		return lexicon;
		return null;
	}
	
  /**
   * This routine returns a String that is the "signature" of the class of a
   * word.
   * For, example, it might represent whether it is a number of ends in -s.
   * The strings returned by convention match the pattern UNK-.* , which
   * is just assumed to not match any real word.
   * Behavior depends on the unknownLevel (-uwm flag) passed in to the class.
   * The recognized numbers are 1-5: 5 is fairly English-specific; 4, 3, and 2
   * look for various word features (digits, dashes, etc.) which are only
   * vaguely English-specific; 1 uses the last two characters combined with 
   * a simple classification by capitalization.
   *
   * @param word The word to make a signature for
   * @param loc  Its position in the sentence (mainly so sentence-initial
   *             capitalized words can be treated differently)
   * @return A String that is its signature (equivalence class)
   */
  protected String getSignature(String word, int loc) {
    //    int unknownLevel = Options.get().useUnknownWordSignatures;
    StringBuffer sb = new StringBuffer("UNK");
    switch (unknownLevel) {

      case 5:
        {
          // Reformed Mar 2004 (cdm); hopefully much better now.
          // { -CAPS, -INITC ap, -LC lowercase, 0 } +
          // { -KNOWNLC, 0 } +          [only for INITC]
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
          } else if (word.length() >= 5 && !hasDash && !(hasDigit && numCaps > 0)) {
            // don't do for very short words;
            // Implement common discriminating suffixes
/*          	if (Corpus.myLanguage==Corpus.GERMAN){
          		sb.append(lowered.substring(lowered.length()-1));
          	}else{*/
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

      case 4:
        {
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
                if (Character.isLowerCase(ch) || Character.isTitleCase(ch)) {
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
          if (Character.isUpperCase(word.charAt(0)) || Character.isTitleCase(word.charAt(0))) {
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

      case 3:
        {
          // This basically works right, except note that 'S' is applied to all
          // capitalized letters in first word of sentence, not just first....
          sb.append("-");
          char lastClass = '-';  // i.e., nothing
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

      case 2:
        {
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
                if (Character.isLowerCase(ch) || Character.isTitleCase(ch)) {
                  hasLower = true;
                }
              }
            }
          }
          if (Character.isUpperCase(word.charAt(0)) || Character.isTitleCase(word.charAt(0))) {
            if (!hasLower) {
              sb.append("-ALLC");
            } else if (loc == 0) {
              sb.append("-INIT");
            } else {
              sb.append("-UC");
            }
          } else if (hasLower) {   // if (Character.isLowerCase(word.charAt(0))) {
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
        sb.append(word.substring(Math.max(word.length() - 2, 0), word.length()));
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
    // System.err.println("Summarized " + word + " to " + sb.toString());
    return sb.toString();
  } // end getSignature()

  
  public String toString() {
  	StringBuffer sb = new StringBuffer();
  	Numberer tagNumberer = Numberer.getGlobalNumberer("tags");
  	for (int tag=0; tag<expectedCounts.length; tag++){
  		String tagS = (String)tagNumberer.object(tag);
  		for (int word=0; word<scores[tag][0].length; word++){
  			sb.append(tagS+" "+ wordIndexer.get(tagWordIndexer[tag].get(word))+" ");	
  			for (int sub=0; sub<numSubStates[tag]; sub++){
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
		return wordIndexer.indexOf(word)!=-1;
	}

	/**
   * Returns the index of the signature of the word numbered wordIndex,
   * where the signature is the String representation of unknown word
   * features.  Caches the last signature index returned.
   */
  protected String getCachedSignature(String word, int sentencePosition) {
  	if (word.equals(lastWordToSignaturize) && sentencePosition == lastSentencePosition) {
  		// System.err.println("Signature: cache mapped " + wordIndex + " to " + lastSignatureIndex);
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
   * Merge states, combining information about words we have seen.  THIS DOES NOT
   * UPDATE INFORMATION FOR UNSEEN WORDS!  For that, retrain the Lexicon!
   * 
   * @param mergeThesePairs
   * @param mergeWeights
   */
  public void mergeStates(boolean[][][] mergeThesePairs, double[][] mergeWeights) {
//		short[] newNumSubStates = new short[numSubStates.length];
//		short[][] mapping = new short[numSubStates.length][];
//		//invariant: if partners[state][substate][0] == substate, it's the 1st one
//		short[][][] partners = new short[numSubStates.length][][];
//		Grammar.calculateMergeArrays(mergeThesePairs,newNumSubStates,mapping,partners,numSubStates);
//		
//		for (int tag=0; tag<mergeThesePairs.length; tag++) {
//			//update wordToTagCounters
//			if (wordToTagCounters[tag]!=null) {
//				for (String word : wordToTagCounters[tag].keySet()) {
//					double[] scores = wordToTagCounters[tag].get(word);
//					double[] newScores = new double[newNumSubStates[tag]];
//					for (int i=0; i<numSubStates[tag]; i++) {
//						short nSplit= (short)partners[tag][i].length;
//						if (nSplit==2) {
//							newScores[mapping[tag][i]] = scores[partners[tag][i][0]]+scores[partners[tag][i][1]];
//						} else {
//							newScores[mapping[tag][i]] = scores[i];
//						}
//					}
//					wordToTagCounters[tag].put(word,newScores);
//				}
//			}
//			//update tag counter
//			double[] newTagCounter = new double[newNumSubStates[tag]];
//			for (int i=0; i<numSubStates[tag]; i++) {
//				if (partners[tag][i].length==2) {
//					newTagCounter[mapping[tag][i]] = tagCounter[tag][partners[tag][i][0]]
//							+ tagCounter[tag][partners[tag][i][1]];
//				} else {
//					newTagCounter[mapping[tag][i]] = tagCounter[tag][i];
//				}
//			}
//			tagCounter[tag] = newTagCounter;
//		}
//
//	  numSubStates = newNumSubStates;
  }
  
  
  public void removeUnlikelyTags(double threshold){
  	//System.out.print("Removing unlikely tags...");
  }
  
  
//  public void logarithmMode() {
//  	logarithmMode = true;
//  }
//
//	public boolean isLogarithmMode() {
//		return logarithmMode;
//	}

	public SimpleLexicon projectLexicon(double[] condProbs, int[][] mapping, int[][] toSubstateMapping) {
//		short[] newNumSubStates = new short[numSubStates.length];
//		for (int state=0; state<numSubStates.length; state++){
//			newNumSubStates[state] = (short)toSubstateMapping[state][0];
//		}
//		SimpleLexicon newLexicon = new SimpleLexicon(newNumSubStates, this.smoothingCutoff, this.smooth, this.smoother, this.threshold);
//
//		double[][] newTagCounter = new double[newNumSubStates.length][];
//		double[][] newUnseenTagCounter = new double[newNumSubStates.length][];
//		if (!isConditional){
//			for (int tag=0; tag<numSubStates.length; tag++) {
//				//update tag counters
//				newTagCounter[tag] = new double[newNumSubStates[tag]];
//				newUnseenTagCounter[tag] = new double[newNumSubStates[tag]];
//				for (int substate=0; substate<numSubStates[tag]; substate++) {
//					newTagCounter[tag][toSubstateMapping[tag][substate+1]] += condProbs[mapping[tag][substate]]*tagCounter[tag][substate];
//				}
//				for (int substate=0; substate<numSubStates[tag]; substate++) {
//					newUnseenTagCounter[tag][toSubstateMapping[tag][substate+1]] += condProbs[mapping[tag][substate]]*unseenTagCounter[tag][substate];
//				}
//				//update wordToTagCounters
//				if (wordToTagCounters[tag]!=null) {
//					newLexicon.wordToTagCounters[tag] = new HashMap<String,double[]>();
//					for (String word : wordToTagCounters[tag].keySet()) {
//						double[] scores = wordToTagCounters[tag].get(word);
//						double[] newScores = new double[newNumSubStates[tag]];
//						for (int i=0; i<numSubStates[tag]; i++) {
//							newScores[toSubstateMapping[tag][i+1]] += condProbs[mapping[tag][i]]*scores[i];
//						}
//						newLexicon.wordToTagCounters[tag].put(word,newScores);
//					}
//				}
//				//update wordToTagCounters
//				if (unseenWordToTagCounters[tag]!=null) {
//					newLexicon.unseenWordToTagCounters[tag] = new HashMap<String,double[]>();
//					for (String word : unseenWordToTagCounters[tag].keySet()) {
//						double[] scores = unseenWordToTagCounters[tag].get(word);
//						double[] newScores = new double[newNumSubStates[tag]];
//						for (int i=0; i<numSubStates[tag]; i++) {
//							newScores[toSubstateMapping[tag][i+1]] += condProbs[mapping[tag][i]]*scores[i];
//						}
//						newLexicon.unseenWordToTagCounters[tag].put(word,newScores);
//					}
//				}
//			}
//		}
//		else{	
//			double[][][] newCondWeights = new double[conditionalWeights.length][conditionalWeights[0].length][];
//			for (int w=0; w<newCondWeights.length; w++){
//				if (conditionalWeights[w]==null) continue;
//				for (int tag=0; tag<numSubStates.length; tag++){
//					if (conditionalWeights[w][tag]==null) continue;
//					newCondWeights[w][tag] = new double[newNumSubStates[tag]];
//					for (int substate=0; substate<numSubStates[tag]; substate++) {
//						newCondWeights[w][tag][toSubstateMapping[tag][substate+1]] += condProbs[mapping[tag][substate]]*conditionalWeights[w][tag][substate];
//					}
//					
//				}
//			}
//			newLexicon.conditionalWeights = newCondWeights;
//			newLexicon.isConditional = true;
//		}
//
//		newLexicon.totalWordTypes = totalWordTypes;
//		newLexicon.totalTokens = totalTokens;
//		newLexicon.totalUnseenTokens = totalUnseenTokens;
//		newLexicon.totalWords = totalWords;
//		newLexicon.smoother = smoother;
//		newLexicon.allTags = new HashSet<Short>(allTags);
//		newLexicon.wordCounter = new Counter<String>();
//		for (String word : wordCounter.keySet()) {
//			newLexicon.wordCounter.setCount(word, wordCounter.getCount(word));
//		}
//		newLexicon.smoothingCutoff = smoothingCutoff;
//		newLexicon.addXSmoothing = addXSmoothing;
//		newLexicon.smoothInUnknownsThreshold = smoothInUnknownsThreshold;
//
//		newLexicon.tagCounter = newTagCounter;
//		newLexicon.unseenTagCounter = newUnseenTagCounter;
//		newLexicon.numSubStates = newNumSubStates;
//		newLexicon.wordNumberer = wordNumberer;
//		return newLexicon;
		return null;
	}
	
//	public SimpleLexicon copyLexicon() {
//		short[] newNumSubStates = numSubStates.clone();
//		SimpleLexicon newLexicon = new SimpleLexicon(newNumSubStates, this.smoothingCutoff, this.smooth, this.smoother, this.threshold);
//
//		double[][] newTagCounter = ArrayUtil.copyArray(tagCounter);
//		double[][] newUnseenTagCounter = ArrayUtil.copyArray(unseenTagCounter);
//		for (int tag=0; tag<numSubStates.length; tag++) {
//			if (wordToTagCounters[tag]!=null) {
//				newLexicon.wordToTagCounters[tag] = new HashMap<String,double[]>();
//				for (String word : wordToTagCounters[tag].keySet()) {
//					double[] scores = wordToTagCounters[tag].get(word);
//					double[] newScores = scores.clone();
//					newLexicon.wordToTagCounters[tag].put(word,newScores);
//				}
//			}
//			//update wordToTagCounters
//			if (unseenWordToTagCounters[tag]!=null) {
//				newLexicon.unseenWordToTagCounters[tag] = new HashMap<String,double[]>();
//				for (String word : unseenWordToTagCounters[tag].keySet()) {
//					double[] scores = unseenWordToTagCounters[tag].get(word);
//					double[] newScores = scores.clone();
//					newLexicon.unseenWordToTagCounters[tag].put(word,newScores);
//				}
//			}
//		}
//
//		if (conditionalWeights!=null) newLexicon.conditionalWeights = conditionalWeights.clone();
//		newLexicon.isConditional = isConditional;
//		newLexicon.totalWordTypes = totalWordTypes;
//		newLexicon.totalTokens = totalTokens;
//		newLexicon.totalUnseenTokens = totalUnseenTokens;
//		newLexicon.totalWords = totalWords;
//		newLexicon.smoother = smoother;
//		newLexicon.allTags = new HashSet<Short>(allTags);
//		newLexicon.wordCounter = new Counter<String>();
//		for (String word : wordCounter.keySet()) {
//			newLexicon.wordCounter.setCount(word, wordCounter.getCount(word));
//		}
//		newLexicon.smoothingCutoff = smoothingCutoff;
//		newLexicon.addXSmoothing = addXSmoothing;
//		newLexicon.smoothInUnknownsThreshold = smoothInUnknownsThreshold;
//
//		newLexicon.tagCounter = newTagCounter;
//		newLexicon.unseenTagCounter = newUnseenTagCounter;
//		newLexicon.numSubStates = newNumSubStates;
//		
//		newLexicon.wordNumberer = this.wordNumberer;
//		return newLexicon;
//		return null;
//	}

	public Smoother getSmoother() {
		return smoother;
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.HDPPCFG.LexiconInterface#getSmoothingParams()
	 */
	public double[] getSmoothingParams() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.HDPPCFG.LexiconInterface#logarithmMode()
	 */

	public void setSmoother(Smoother smoother) {
		this.smoother = smoother;
	}


	
	
}
