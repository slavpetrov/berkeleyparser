package edu.berkeley.nlp.PCFGLA;


import edu.berkeley.nlp.PCFGLA.SimpleLexicon.IntegerIndexer;
import edu.berkeley.nlp.PCFGLA.smoothing.Smoother;
import edu.berkeley.nlp.math.DifferentiableFunction;
import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.math.LBFGSMinimizer;
import edu.berkeley.nlp.math.SloppyMath;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.Indexer;
import edu.berkeley.nlp.util.Numberer;
import edu.berkeley.nlp.util.ScalingTools;

import java.io.Serializable;
import java.util.*;

/**
 *
 * @author dlwh
 */
public class FeaturizedLexicon implements Lexicon, Serializable {
	private double[][][] expectedCounts; // indexed by tag, substate, word
	private double[][][] scores; // indexed by tag, substate, word
  private double[][] normalizers; // indexed by tag, substate

	public int[] wordCounter; // how many times each word occured

  /** A trick to allow loading of saved Lexicons even if the version has changed. */
  private static final long serialVersionUID = 2L;
  /** The number of substates for each state */
  public short[] numSubStates;
  int numStates;
  int nWords;

  double threshold;
  boolean isLogarithmMode;
  boolean useVarDP = false;
  private Indexer<String> wordIndexer = new Indexer<String>();
  public int[][][][] indexedFeatures; // tag, substate, substate list of features
  Smoother smoother;
  private Featurizer featurizer;
  private Indexer<String> featureIndex = new Indexer<String>();
  private double[] featureWeights;
  private IntegerIndexer[] tagWordIndexer;
  private double regularizationConstant = 1.0;

  /** Create a blank Lexicon object.  Fill it by
   * calling tallyStateSetTree for each training tree, then
   * calling optimize().
   *
   * @param numSubStates
   */
  @SuppressWarnings("unchecked")
	public FeaturizedLexicon(short[] numSubStates, Featurizer featurizer, StateSetTreeList trainTrees) {
  	this(numSubStates, featurizer);
  	init(trainTrees);
  }

  public FeaturizedLexicon(short[] numSubStates, Featurizer featurizer) {
  	this.numSubStates = numSubStates;
    this.wordIndexer = new Indexer<String>();
    this.numStates = numSubStates.length;
    this.isLogarithmMode = false;
    this.featurizer = featurizer;
    minimizer.setMaxIterations(500);
  }

  transient private LBFGSMinimizer minimizer = new LBFGSMinimizer();

  public LBFGSMinimizer getMinimizer() {
    if(minimizer == null)
      minimizer = new LBFGSMinimizer();
    return minimizer;
  }

  private double[][][] projectWeightsToScores(double[] weights) {
    final double[][][] thetas = new double[numStates][][];
    for(int tag = 0; tag < numStates; tag++) {
      thetas[tag] = new double[numSubStates[tag]][];
      for(int substate = 0; substate < expectedCounts[tag].length; ++substate) {
        thetas[tag][substate] = new double[tagWordIndexer[tag].size()];
        for(int word = 0; word < expectedCounts[tag][substate].length; ++word) {
          double score = 0.0;
          for(int i = 0; i < indexedFeatures[tag][substate][word].length; ++i) {
            score += weights[indexedFeatures[tag][substate][word][i]];
          }
          thetas[tag][substate][word] = score;
        }
        // TODO this should not be updated here.
        normalizers[tag][substate] = SloppyMath.logAdd(thetas[tag][substate]);
        DoubleArrays.subtractInPlace(thetas[tag][substate], normalizers[tag][substate]);
        thetas[tag][substate] = DoubleArrays.exponentiate(thetas[tag][substate]);
      }
    }

    return thetas;
  }

  // the m-step objective
  private DifferentiableFunction objective(final double[][][] expectedCounts) { // tag, substate
    final double[][] eTotals = new double[expectedCounts.length][];
    for(int tag = 0; tag < numStates; tag++) {
      eTotals[tag] = new double[numSubStates[tag]];
      for(int substate = 0; substate < expectedCounts[tag].length; ++substate) {
        for(int word = 0; word < expectedCounts[tag][substate].length; ++word) {
          eTotals[tag][substate] += expectedCounts[tag][substate][word];
        }
      }
    }


    return new DifferentiableFunction() {

      public double[] derivativeAt(double[] x) {
        double[] gradient = new double[x.length];
        double[][][] thetas = projectWeightsToScores(x);
        for(int tag = 0; tag < numStates; tag++) {
          for(int substate = 0; substate < expectedCounts[tag].length; ++substate) {
            double logTotal = Math.log(eTotals[tag][substate]);
            for(int word = 0; word < expectedCounts[tag][substate].length; ++word) {
              double e = expectedCounts[tag][substate][word];
              double lT = Math.log(thetas[tag][substate][word]);
              double margin = e - Math.exp(logTotal + lT);

              for(int i = 0; i < indexedFeatures[tag][substate][word].length; ++i) {
                int f = indexedFeatures[tag][substate][word][i];
                // we're doing negative gradient because we're maximizing.
                gradient[f] -= margin;
              }
            }
          }
        }

        return DoubleArrays.add(gradient, DoubleArrays.multiply(x, regularizationConstant));
      }

      public int dimension() {
        return featureWeights.length;
      }

      public double valueAt(double[] x) {
        double[][][] thetas = projectWeightsToScores(x);
        double logProb = 0.0;
        for(int tag = 0; tag < numStates; tag++) {
          for(int substate = 0; substate < expectedCounts[tag].length; ++substate) {
            for(int word = 0; word < expectedCounts[tag][substate].length; ++word) {
              logProb += expectedCounts[tag][substate][word]  * Math.log(thetas[tag][substate][word]);
            }
          }
        }
        return -logProb + regularizationValue(featureWeights);
      }
    };
  }

  // Should be called whenever the number of features changes.
  private void refeaturize() {
    indexedFeatures = new int[numStates][][][];
    for (int tag = 0; tag < numStates; tag++) {
      indexedFeatures[tag] = new int[numSubStates[tag]][tagWordIndexer[tag].size()][];
      // index all the features for each word seen with this tag.
      for (int tagSpecificIndex = 0; tagSpecificIndex < tagWordIndexer[tag].size(); ++tagSpecificIndex) {
        int globalWordIndex = tagWordIndexer[tag].get(tagSpecificIndex);
        String word = wordIndexer.getObject(tagSpecificIndex);
        List<String>[] features = featurizer.featurize(word, tag, numSubStates[tag], wordCounter[globalWordIndex]);
        for (int state = 0; state < numSubStates[tag]; ++state) {
          int[] indices = new int[features[state].size()];
          for (int i = 0; i < indices.length; ++i) {
            indices[i] = featureIndex.getIndex(features[state].get(i));
          }
          indexedFeatures[tag][state][tagSpecificIndex] = indices;
        }
      }
    }
  }

  private double regularizationValue(double[] weights) {
    return DoubleArrays.innerProduct(weights, weights) * 0.5 * regularizationConstant;
  }

  public void optimize() {
    LBFGSMinimizer minimizer = getMinimizer();
    DifferentiableFunction objective = objective(expectedCounts);
    featureWeights = minimizer.minimize(objective, featureWeights, 1E-5, true);
    scores = projectWeightsToScores(featureWeights);
  }

  public double[] score(String word, short tag, int pos, boolean noSmoothing, boolean isSignature) {
  	StateSet stateSet = new StateSet(tag, (short)1, word, (short)pos, (short)(pos+1));
  	stateSet.wordIndex = -2;
  	stateSet.sigIndex = -2;
  	return score(stateSet,tag,noSmoothing,isSignature);
  }

  public double[] score(StateSet stateSet, short tag, boolean noSmoothing, boolean isSignature) {
		double[] res = new double[numSubStates[tag]];
		int globalWordIndex = stateSet.wordIndex;
		if (globalWordIndex < 1)
			globalWordIndex = stateSet.wordIndex = wordIndexer.indexOf(stateSet.getWord());

		int tagSpecificWordIndex = globalWordIndex >= 0 ? tagWordIndexer[tag].indexOf(globalWordIndex) : -1;
		if (tagSpecificWordIndex==-1) {  // unknown word for this tag.
      // TODO: make 5 configurable.
      if(globalWordIndex >= 0 && wordCounter[globalWordIndex] >= 5) { // not rare, so it can't be this tag.
        if (isLogarithmMode) Arrays.fill(res, Double.NEGATIVE_INFINITY);
      } else {
        List<String>[] features = featurizer.featurize(stateSet.getWord(), tag, numSubStates[tag], wordCounter[globalWordIndex]);
        for(int state = 0; state < numSubStates[tag]; ++state) {
          double score = 0.0;
          for(String feature: features[state]) {
            int index = featureIndex.getIndex(feature);
            if(index >= 0)
              score += featureWeights[index];
          }
          if(isLogarithmMode()) {
            res[state] = score - normalizers[tag][state];
          } else {
            res[state] = Math.exp(score - normalizers[tag][state]);
          }
        }
      }
		} else { // we've scored this word:
      for (int i=0; i<numSubStates[tag]; i++){
        res[i] = scores[tag][i][tagSpecificWordIndex];
      }
    }
		return res;
  }

  // no signatures
  public String getSignature(String word, int sentencePosition) {
    return word;
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

    /*
     * assume that rare words have been replaced by their signature
     */
  public void init(StateSetTreeList trainTrees){
  	for (Tree<StateSet> tree : trainTrees){
  		List<StateSet> words = tree.getYield();
  		for (StateSet word : words){
				String sig = word.getWord();
				wordIndexer.add(sig);
  		}
  	}
  	tagWordIndexer = new IntegerIndexer[numStates];
  	for (int tag=0; tag<numStates; tag++){
  		tagWordIndexer[tag] = new IntegerIndexer(wordIndexer.size());
  	}
  	wordCounter = new int[wordIndexer.size()];
  	for (Tree<StateSet> tree : trainTrees){
  		List<StateSet> tags = tree.getPreTerminalYield();
  		List<StateSet> words = tree.getYield();
  		int ind = 0;
  		for (StateSet word : words){
				String sig = word.getWord();
				wordCounter[wordIndexer.indexOf(sig)]++;
				tagWordIndexer[tags.get(ind).getState()].add(wordIndexer.indexOf(sig));
  			ind++;
  		}
  	}
  	expectedCounts = new double[numStates][][];
  	scores = new double[numStates][][];
  	normalizers = new double[numStates][];
    for (int tag = 0; tag < numStates; tag++) {
      expectedCounts[tag] = new double[numSubStates[tag]][tagWordIndexer[tag].size()];
      normalizers[tag] = new double[numSubStates[tag]];
      scores[tag] = new double[numSubStates[tag]][tagWordIndexer[tag].size()];
    }
    refeaturize();

    featureWeights = new double[featureIndex.size()];

  	nWords = wordIndexer.size();
  	labelTrees(trainTrees);
  }

  public void labelTrees(StateSetTreeList trainTrees){
  	for (Tree<StateSet> tree : trainTrees){
  		List<StateSet> words = tree.getYield();
  		for (StateSet word : words){
  			word.wordIndex = wordIndexer.indexOf(word.getWord());
  			word.sigIndex = -1;
  		}
  	}

	}

  public double[] scoreWord(StateSet stateSet, int tag) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  public double[] scoreSignature(StateSet stateSet, int tag) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  public void trainTree(Tree<StateSet> trainTree, double randomness, Lexicon oldLexicon, boolean secondHalf, boolean noSmoothing, int unkThreshold) {
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
      double scale = ScalingTools.calcScaleFactor(currentState.getOScale()-sentenceScale) / sentenceScore;

      for (short substate=0; substate<nSubStates; substate++) {
        double weight = 1;
        if (randomness == -1) {
          //weight by the probability of seeing the tag and word together, given the sentence
          if (!Double.isInfinite(scale))
            weight = currentState.getOScore(substate) * oldLexiconScores[substate] * scale;
          else
          	weight = Math.exp(Math.log(ScalingTools.SCALE)
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

	  		expectedCounts[tag][substate][tagSpecificWordIndex] += weight;
      }
  	}
  }

  public void setSmoother(Smoother smoother) {
    this.smoother = smoother;
  }

	public FeaturizedLexicon splitAllStates(int[] counts, boolean moreSubstatesThanCounts, int mode) {
		FeaturizedLexicon splitLex = this.copyLexicon();

		short[] newNumSubStates = new short[numSubStates.length];
		newNumSubStates[0] = 1; // never split ROOT
		Random random = GrammarTrainer.RANDOM;
		for (short i = 1; i < numSubStates.length; i++) {
			// don't split a state into more substates than times it was actaully seen
//			if (!moreSubstatesThanCounts && numSubStates[i]>=counts[i]) {
//				newNumSubStates[i]=numSubStates[i];
//				}
//			else{
				newNumSubStates[i] = (short)(numSubStates[i] * 2);
//			}
		}
		splitLex.numSubStates = newNumSubStates;
		double[][][] newScores = new double[scores.length][][];
		double[][][] newExpCounts = new double[scores.length][][];
  	for (int tag=0; tag<expectedCounts.length; tag++){
  		int nTagWords = tagWordIndexer[tag].size();
//  		if (nWords==0) continue;
  		newScores[tag] = new double[newNumSubStates[tag]][nTagWords];
  		newExpCounts[tag] = new double[newNumSubStates[tag]][nTagWords];
  		for (int substate=0; substate<numSubStates[tag]; substate++){
  			for (int word=0; word<expectedCounts[tag][substate].length; word++){
	  			newScores[tag][2*substate][word] = newScores[tag][2*substate+1][word] = scores[tag][substate][word];
	  			if (mode==2)
	  				newScores[tag][2*substate][word] = newScores[tag][2*substate+1][word] = 1.0+random.nextDouble()/100.0;
	  		}
  		}
  	}
  	splitLex.scores = newScores;
  	splitLex.expectedCounts = newExpCounts;
    splitLex.refeaturize();
		return splitLex;
	}

  public void mergeStates(boolean[][][] mergeThesePairs, double[][] mergeWeights) {
		short[] newNumSubStates = new short[numSubStates.length];
		short[][] mapping = new short[numSubStates.length][];
		//invariant: if partners[state][substate][0] == substate, it's the 1st one
		short[][][] partners = new short[numSubStates.length][][];
		Grammar.calculateMergeArrays(mergeThesePairs,newNumSubStates,mapping,partners,numSubStates);

		double[][][] newScores = new double[scores.length][][];
		for (int tag=0; tag<expectedCounts.length; tag++){
  		int nTagWords = tagWordIndexer[tag].size();
  		newScores[tag] = new double[newNumSubStates[tag]][nTagWords];
  		if (numSubStates[tag]==1) continue;
			for (int word=0; word<expectedCounts[tag][0].length; word++){
				for (int i=0; i<numSubStates[tag]; i=i+2) {
					int nSplit=partners[tag][i].length;
					if (nSplit==2) {
						double mergeWeightSum = mergeWeights[tag][partners[tag][i][0]] + mergeWeights[tag][partners[tag][i][1]];
						if (mergeWeightSum==0) mergeWeightSum = 1;
						newScores[tag][mapping[tag][i]][word] =
							((mergeWeights[tag][partners[tag][i][0]] * scores[tag][partners[tag][i][0]][word])+
							 (mergeWeights[tag][partners[tag][i][1]] * scores[tag][partners[tag][i][1]][word])) / mergeWeightSum;
					} else {
						newScores[tag][mapping[tag][i]][word] = scores[tag][i][word];
						newScores[tag][mapping[tag][i+1]][word] = scores[tag][i+1][word];
					}
				}
			}
  	}
  	this.numSubStates = newNumSubStates;
  	this.scores = newScores;
  	for (int tag=0; tag<numStates; tag++){
  		this.expectedCounts[tag] = new double[newNumSubStates[tag]][tagWordIndexer[tag].size()];
  	}
    refeaturize();
  }

  public Smoother getSmoother() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  public double[] getSmoothingParams() {
    throw new UnsupportedOperationException("Not supported yet.");
  }


  public FeaturizedLexicon projectLexicon(double[] condProbs, int[][] mapping, int[][] toSubstateMapping) {
		short[] newNumSubStates = new short[numSubStates.length];
		for (int state=0; state<numSubStates.length; state++){
			newNumSubStates[state] = (short)toSubstateMapping[state][0];
		}
		FeaturizedLexicon newLexicon = this.copyLexicon();

		double[][][] newScores = new double[scores.length][][];


  	for (short tag=0; tag<expectedCounts.length; tag++){
  		newScores[tag] = new double[newNumSubStates[tag]][expectedCounts[tag][0].length];
  		for (int word=0; word<expectedCounts[tag][0].length; word++){
  			for (int substate=0; substate<numSubStates[tag]; substate++){
  				newScores[tag][toSubstateMapping[tag][substate+1]][word] +=
  					condProbs[mapping[tag][substate]]*scores[tag][substate][word];
  			}
			}
		}
  	newLexicon.numSubStates = newNumSubStates;
  	newLexicon.scores = newScores;
    newLexicon.refeaturize();
		return newLexicon;
	}

  public FeaturizedLexicon copyLexicon() {
    FeaturizedLexicon copy = new FeaturizedLexicon(numSubStates,featurizer);
    copy.expectedCounts = new double[numStates][][];
    copy.scores = ArrayUtil.clone(scores);//new double[numStates][][];
    copy.tagWordIndexer = new IntegerIndexer[numStates];
    copy.wordIndexer = this.wordIndexer;
    for (int tag=0; tag<numStates; tag++){
      copy.tagWordIndexer[tag] = tagWordIndexer[tag].copy();
      copy.expectedCounts[tag] = new double[numSubStates[tag]][tagWordIndexer[tag].size()];
    }
    copy.nWords = this.nWords;
    copy.smoother = this.smoother;
    copy.wordCounter = this.wordCounter.clone();
    //  	copy.wordIsAmbiguous = this.wordIsAmbiguous.clone();
    //  	copy.unkIndex = unkIndex;
/*  	if (linearIndex!=null) copy.linearIndex = ArrayUtil.clone(linearIndex);
    if (toBeIgnored!=null) copy.toBeIgnored = toBeIgnored.clone();*/
    return copy;
  }

  public void removeUnlikelyTags(double threshold, double exponent) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  public double getPruningThreshold() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  public void tieRareWordStats(int threshold) {
    return;
  }

  public Counter<String> getWordCounter() {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  public void explicitlyComputeScores(int finalLevel) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

}
