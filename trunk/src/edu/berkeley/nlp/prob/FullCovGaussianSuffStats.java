/**
 * 
 */
package edu.berkeley.nlp.prob;

import java.io.Serializable;
import java.util.Arrays;

import Jama.Matrix;

import edu.berkeley.nlp.HMM.MixtureSubphoneHMM;
import edu.berkeley.nlp.HMM.SubphoneHMM;
import edu.berkeley.nlp.math.SloppyMath;
import edu.berkeley.nlp.util.ArrayUtil;
import fig.basic.LogInfo;

/**
 * @author adpauls
 *
 */
public class FullCovGaussianSuffStats implements GaussianSuffStats{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 9043101737279155674L;
	private double[] sum;
	private double norm;
	private double[][] sumSq;

	/**
	 * @param gaussianDim
	 */
	public FullCovGaussianSuffStats(int gaussianDim) {
		norm = 0;
		sum = new double[gaussianDim];
		sumSq = new double[gaussianDim][gaussianDim];
	}
	
	private static String p(double[] x)
	{
		String s = new String("[");
		for (double y : x)
		{
			s = s + "," + y;
		}
		s = s + "]";
		return s;
	}
	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.HMM.GaussianSuffStats#add(double[])
	 */
	public void add(double[] x, double weight) {
		

		
			double[] weightedObs = ArrayUtil.multiply(x, weight);

			// first the means
//			if (MixtureSubphoneHMM.yyy) 
//				{
//				LogInfo.dbg("adding " /*+ ArrayUtil.toString(x)*/ + " with weight " + weight);
//				}
			ArrayUtil.addInPlace(sum, weightedObs);
			norm  += weight;

			// now variance
			double[][] scaledOuterProduct = null;



		scaledOuterProduct = FullCovGaussian.scaledOuterSelfProduct(x,
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
		FullCovGaussianSuffStats d = (FullCovGaussianSuffStats)stats;
		ArrayUtil.addInPlace(sum,d.sum);
		ArrayUtil.addInPlace(sumSq, d.sumSq);
		norm += d.norm;
		
	}
	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.HMM.GaussianSuffStats#estimate()
	 */
	public Gaussian estimate() {
		
		double[] mean = ArrayUtil.multiply(sum , SubphoneHMM.divide(1.0,
				norm));
		double normalizer = (norm == 0) ? 0
				: SubphoneHMM.divide(1.0, norm);
		assert !SloppyMath.isVeryDangerous(normalizer);
	

			double[][] cov = new double[mean.length][mean.length];
			for (int i = 0; i < mean.length; ++i) {
				for (int j = 0; j < mean.length; ++j) {
					// MUST have normalizer term in between the sums so that we
					// don't get overflow!!

					cov[i][j] = sumSq[i][j]
							- sum[j] * normalizer * sum[i];

					cov[i][j] *= normalizer;
				}
			}

			return new FullCovGaussian(mean, new Matrix(cov));
	

	}
	
	public GaussianSuffStats clone()
	{
		return new FullCovGaussianSuffStats(sum.length);
	}

}
