/**
 * 
 */
package edu.berkeley.nlp.prob;

import edu.berkeley.nlp.prob.Randomizer;

/**
 * @author adpauls
 *
 */
public class GaussianMixture implements Gaussian, java.io.Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Gaussian[] mixtures;
	private double[] weights;
	
	
	public GaussianMixture(Gaussian[] gaussians, double[] weights)
	{
		mixtures = gaussians;
		this.weights = weights;
		
//	
	}
	
	public Gaussian getGaussian(int mixture)
	{
		return mixtures[mixture];
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.prob.Gaussian#evalLogPdf(double[])
	 */
	public double evalLogPdf(double[] x) {
		assert false;
		return 0;
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.prob.Gaussian#evalPdf(double[])
	 */
	public double evalPdf(double[] x) {
		assert false;
		return 0;
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.prob.Gaussian#getCovariance()
	 */
	public double[][] getCovariance() {
		assert false;
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.prob.Gaussian#getMean()
	 */
	public double[] getMean() {
		assert false;
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.prob.Gaussian#isValid()
	 */
	public boolean isValid() {
		boolean valid = true;
		for (int mixture = 0; mixture < mixtures.length; ++mixture)
		{
			valid &= mixtures[mixture].isValid();
		}
		return valid;
	}
	
	public Gaussian clone()
	{
		assert false;
		return null;
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.prob.Gaussian#mergeGaussian(edu.berkeley.nlp.prob.Gaussian, double)
	 */
	public void mergeGaussian(Gaussian x, double w) {
		assert false;

	}
	public void setMean(double[] mean) {
		assert false;
		
	}

	public Gaussian[] splitInTwo(Randomizer randomizer, double rand) {
		Gaussian[] retVal = new Gaussian[2];
		for (int i = 0; i < 2; ++i)
		{
			Gaussian[] gaussians = new Gaussian[mixtures.length];
			for (int mixture = 0; mixture < mixtures.length; ++mixture)
			{
				
			
				
					Gaussian newGaussian = mixtures[mixture].clone();
					
					
					newGaussian.setMean(randomizer.randPerturb(newGaussian.getMean(), rand));
					gaussians[mixture] = newGaussian;
				
			}
			retVal[i] = new GaussianMixture(gaussians,weights);
		}
		return retVal;
	}

	/**
	 * @param toZ
	 * @return
	 */
	public double getMixtureWeight(int toZ) {
		return weights[toZ];
	}
	
	public int getNumMixtures()
	{
		return mixtures.length;
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.prob.Gaussian#newSuffStats()
	 */
	public GaussianSuffStats newSuffStats() {
		if (mixtures[0] == null)
		{
			//this is a dummy
			return null;
		}
		GaussianSuffStats[] stats = new GaussianSuffStats[mixtures.length];
		for (int mixture = 0; mixture < mixtures.length; ++mixture)
		{
			stats[mixture] = mixtures[mixture].newSuffStats();
		}
		return new GaussianMixtureSuffStats(stats);
		
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.prob.Gaussian#isNoMean()
	 */
	public boolean isNoMean() {
		// TODO Auto-generated method stub
		assert false : "Should not be called";
		return false;
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.prob.Gaussian#setNoMean(boolean)
	 */
	public void setNoMean(boolean noMean) {
		assert false : "Should not be called";
		// TODO Auto-generated method stub
		
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.prob.Gaussian#setPrevObservation(double[])
	 */
	public void setPrevObservation(double[] obs) {
	assert false;
		
	}
}
