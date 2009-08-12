/**
 * 
 */
package edu.berkeley.nlp.prob;

import edu.berkeley.nlp.prob.Randomizer;

/**
 * @author petrov
 *
 */
public interface Gaussian {
	public double[] getMean();
	public void setMean(double[] mean);
	public double[][] getCovariance();
	public double evalPdf(double[] x);
	public double evalLogPdf(double[] x);
	public void mergeGaussian(Gaussian x, double w);
	public Gaussian clone();
	
	public void setNoMean(boolean noMean);
	public boolean isNoMean();
	
	public Gaussian[] splitInTwo(Randomizer randomizer, double rand);
	
	public boolean isValid();
	
	public GaussianSuffStats newSuffStats();
	
	public void setPrevObservation(double[] obs);
	
	
}
