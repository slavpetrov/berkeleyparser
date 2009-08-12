/**
 * 
 */
package edu.berkeley.nlp.prob;

import Jama.Matrix;
import edu.berkeley.nlp.HMM.SubphoneHMM;
import edu.berkeley.nlp.math.SloppyMath;
import edu.berkeley.nlp.util.ArrayUtil;

/**
 * @author adpauls
 *
 */
public class DiagCovGaussianSuffStats implements GaussianSuffStats{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 6710171092940229767L;
	private double[] sum;
	private double[] sumSq;
	private double norm;

	/**
	 * @param gaussianDim
	 */
	public DiagCovGaussianSuffStats(int gaussianDim) {
		norm = 0;
		sum = new double[gaussianDim];
		sumSq = new double[gaussianDim];
	}
	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.HMM.GaussianSuffStats#add(double[])
	 */
	public void add(double[] x, double weight) {
		

		
			double[] weightedObs = ArrayUtil.multiply(x, weight);

			// first the means
			ArrayUtil.addInPlace(sum, weightedObs);
			norm  += weight;

			// now variance
			double[][] scaledOuterProduct = null;


				double[] diagonal = ArrayUtil.pairwiseMultiply(x, x);
				ArrayUtil.addInPlace(sumSq,
						ArrayUtil.multiply(diagonal, weight));


	}
	

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.HMM.GaussianSuffStats#add(edu.berkeley.nlp.HMM.GaussianSuffStats)
	 */
	public void add(GaussianSuffStats stats) {
		DiagCovGaussianSuffStats d = (DiagCovGaussianSuffStats)stats;
		ArrayUtil.addInPlace(sum,d.sum);
		ArrayUtil.addInPlace(sumSq, d.sumSq);
		norm += d.norm;
		
	}
	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.HMM.GaussianSuffStats#estimate()
	 */
	public Gaussian estimate() {
		
		double[] mean = ArrayUtil.multiply(this.sum, SubphoneHMM.divide(1.0,
				norm));
		double normalizer = (norm == 0) ? 0
				: SubphoneHMM.divide(1.0, norm);
		assert !SloppyMath.isVeryDangerous(normalizer);
	
			double[] variance = new double[mean.length];
			for (int i = 0; i < mean.length; ++i) {

				variance[i] = sumSq[i]
						- (sum[i] * normalizer * sum[i]);
				variance[i] *= normalizer;
			}

			DiagonalCovGaussian newEmission = new DiagonalCovGaussian(mean,
					variance);
			return newEmission;
		}
	
	public GaussianSuffStats clone()
	{
		return new DiagCovGaussianSuffStats(sum.length);
	}
	
	

}
