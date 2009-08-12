/**
 * 
 */
package edu.berkeley.nlp.prob;

import javax.naming.OperationNotSupportedException;

import edu.berkeley.nlp.HMM.MixtureSubphoneHMM;
import edu.berkeley.nlp.HMM.SubphoneHMM;
import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.util.ArrayUtil;
import fig.basic.LogInfo;

/**
 * @author adpauls
 *
 */
public class NoMeanFullCovGaussianSuffStats implements GaussianSuffStats{
	
		
	/**
	 * 
	 */
	private static final long serialVersionUID = -7534832657426830557L;
	private double norm;
	private double[] tmpdiff;
	private double[][] sumSq;
	
	public NoMeanFullCovGaussianSuffStats(int gaussianDim)
	{
		tmpdiff = new double[gaussianDim];
		sumSq = new double[gaussianDim][gaussianDim];
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.HMM.GaussianSuffStats#add(double[])
	 */
	public void add(double[] x, double weight) {
		assert false : "Not implemented";
	}
		public void add(double[] x, double[] prevX, double weight) {
		
		

			// first the means
			
			norm  += weight;

			// now variance
			double[][] scaledOuterProduct = null;

			ArrayUtil.subtract(x,prevX, tmpdiff);
			if (MixtureSubphoneHMM.yyy) 
			{
			LogInfo.dbg("adding " + /*p(x) + */" with weight " + weight);
			}

		scaledOuterProduct = FullCovGaussian.scaledOuterSelfProduct(tmpdiff,
				weight);
		for (int i = 0; i < x.length; ++i) {
			ArrayUtil.addInPlace(sumSq[i],
					scaledOuterProduct[i]);
		}


		
	}
	

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.HMM.GaussianSuffStats#add(edu.berkeley.nlp.HMM.GaussianSuffStats)
	 */
	public void add(GaussianSuffStats stats) {
		final NoMeanFullCovGaussianSuffStats noMeanFullCovGaussianSuffStats = ((NoMeanFullCovGaussianSuffStats)stats);
		ArrayUtil.addInPlace(sumSq, noMeanFullCovGaussianSuffStats.sumSq);
		norm += noMeanFullCovGaussianSuffStats.norm;
		
	}
	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.HMM.GaussianSuffStats#estimate()
	 */
	public Gaussian estimate() {
		final FullCovGaussian fullCovGaussian = new FullCovGaussian(null, ArrayUtil.multiply(sumSq, SubphoneHMM.divide(1.0, norm)));
		fullCovGaussian.setNoMean(true);
		return fullCovGaussian;
	}
	
	public GaussianSuffStats clone()
	{
		assert false;
		return new NoMeanFullCovGaussianSuffStats(tmpdiff.length);
	}

}
