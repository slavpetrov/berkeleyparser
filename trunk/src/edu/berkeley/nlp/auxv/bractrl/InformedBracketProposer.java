/**
 * 
 */
package edu.berkeley.nlp.auxv.bractrl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import edu.berkeley.nlp.auxv.BracketConstraints;
import edu.berkeley.nlp.auxv.BracketConstraints.BracketProposerView;
import fig.basic.NumUtils;
import fig.basic.Pair;
import fig.prob.SampleUtils;

public class InformedBracketProposer implements BracketProposer
  {
    private Map<List<String>, double [][]> knownPosteriors = new HashMap<List<String>, double[][]>();
    private double optEffExp = 1.0, pesEffExp = 1.0, statExp = 1.0;
    private double smoothing = 1.0;
    public InformedBracketProposer(double optEffExp, double pesEffExp, double statExp)
    {
      this.optEffExp = optEffExp; this.pesEffExp = pesEffExp; 
      this.statExp = statExp;
    }
    public double getOptEffExp() 
    {
			return optEffExp;
		}
		public void setOptEffExp(double optEffExp) 
		{
			this.optEffExp = optEffExp;
		}
		public double getPesEffExp() 
		{
			return pesEffExp;
		}
		public void setPesEffExp(double pesEffExp) 
		{
			this.pesEffExp = pesEffExp;
		}
		public double getStatExp() 
		{
			return statExp;
		}
		public void setStatExp(double statExp) 
		{
			this.statExp = statExp;
		}
		public void put(List<String> sentence, double [][] bracketPosterior)
    {
      double [][] copy = new double[sentence.size()][sentence.size() + 1];
      for (int i = 0; i < sentence.size(); i++)
        for (int j = i + 1; j <= sentence.size(); j++)
          copy[i][j] = bracketPosterior[i][j];
      List<String> sentenceCopy = new ArrayList<String>(sentence);
      knownPosteriors.put(sentenceCopy, copy);
    }
    private double [] linearizedDistribution(BracketProposerView currentConstraints)
    {
      double [][] optEfficiencyScores = efficiencyScores(currentConstraints.optimisticParsingComplexities(),
      		currentConstraints.optimisticParsingComplexity());
      double [][] pesEfficiencyScores = efficiencyScores(currentConstraints.pessimisticParsingComplexities(),
      		currentConstraints.pessimisticParsingComplexity());
      double [][] statisticalScores = knownPosteriors.get(currentConstraints.getSentence());
      int length = currentConstraints.getSentence().size();
      double [] linearizedProbs = new double[length + length * length];
      for (int left = 0; left < length; left++)
        for (int right = left + 1; right < length + 1; right++)
          linearizedProbs[left + right * length] = 
            f(optEfficiencyScores[left][right], pesEfficiencyScores[left][right], 
              (statisticalScores == null ? 1.0 : statisticalScores[left][right]));
      NumUtils.normalize(linearizedProbs);
      return linearizedProbs;
    }
    public double [][] distribution(BracketProposerView currentConstraints)
    {
      int length = currentConstraints.getSentence().size();
      double [] linearized = linearizedDistribution(currentConstraints);
      double [][] result = new double[length][length + 1];
      for (int i = 0; i < linearized.length; i++)
        result[delinearizeLeft(i, length)][delinearizeRight(i, length)] =
          linearized[i];
      return result;
    }
    private static int delinearizeLeft(int linearIndex, int length) { return linearIndex % length; }
    private static int delinearizeRight(int linearIndex, int length) { return linearIndex / length; }
    private static Pair<Integer, Integer> delinearize(int linearIndex, int length)
    {
      return new Pair<Integer, Integer>(delinearizeLeft(linearIndex, length), delinearizeRight(linearIndex, length));
    }
    public Pair<Integer, Integer> next(Random rand, BracketProposerView currentConstraints)
    {
    	double [] prs = linearizedDistribution(currentConstraints);
//    	try {
	      int linearIndex = SampleUtils.sampleMultinomial(rand, prs);
	      return delinearize(linearIndex, currentConstraints.getSentence().size());
//     	} catch(Exception e) { 
//     		linearizedDistribution(currentConstraints);
//    		throw new RuntimeException(e);
//    	}
    }
    /**
     * Computes a score that encourages the brackets that seem to be such
     * that computational gains will be incurred if they are on
     * Two factors will be combined in evaluating the computational gains:
     * 	- pessimistic hypothesis (all previous brackets were rejected)
     *  - optimistic hypothesis (all previous brackets were accepted)
     * @param currentConstraints
     * @return
     */
    private double[][] efficiencyScores(double [][] parsingComplexities, double initialParsingComplex)
    {
      for (int i = 0; i < parsingComplexities.length; i++)
        for (int j = i + 1; j < parsingComplexities[0].length; j++)
        {
        	assert (initialParsingComplex >= parsingComplexities[i][j]) : 
        		"Init complex=" + initialParsingComplex + ",parsingComplexities[i][j]=" + parsingComplexities[i][j];
        	assert (initialParsingComplex > 0.0);
      		parsingComplexities[i][j] = 
            (initialParsingComplex - parsingComplexities[i][j]) /
            	initialParsingComplex;
        }
      return parsingComplexities;
    }
    private double f(double optScore, double pesScore, double statisticalScore)
    {
    	assert optScore >= 0 && pesScore >= 0 && statisticalScore >= 0;
      return 	Math.pow(optScore + smoothing, optEffExp) * 
      				Math.pow(pesScore + smoothing, pesEffExp) * 
      				Math.pow(statisticalScore + smoothing, statExp);
    }
    @Override
    public String toString() 
    {
      return "Informed bracket proposer: large span opt exp=" + getOptEffExp() + 
      		", pes=" + getPesEffExp() + ", likely span exp=" + getStatExp();
    }
		/**
		 * @return
		 */
		public boolean allowsOverlappingBrackets() {
			return true;
		}
  }