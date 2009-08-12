/**
 * 
 */
package edu.berkeley.nlp.prob;

import java.io.Serializable;

import Jama.Matrix;

import edu.berkeley.nlp.HMM.TimitTester;
import edu.berkeley.nlp.prob.Randomizer;
import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.math.SloppyMath;

public class DiagonalCovGaussian implements Gaussian, Serializable{
		 double[] mean;

		 double[] varianceInverse;
		 double[] variance;
		 
		 double covDet;
		 double[] diff;
		 double[] scratch;
		 double[] scratchCov;
		 private double[] prevObservation;

		 
		 double logNormalization ;
		 private static final long serialVersionUID = 1L;
		 
		
		public DiagonalCovGaussian(double[] mean, double[] variance) {
			this.mean = mean;
			diff = new double[mean.length];
			this.variance = variance;
			this.varianceInverse = ArrayUtil.inverse(variance);
			this.covDet = ArrayUtil.product(variance);
			this.logNormalization = -(double)mean.length / 2.0 *  Math.log(2.0 * Math.PI)  + -0.5 * Math.log(covDet);
			//assert !SloppyMath.isVeryDangerous(this.logNormalization);
			//assert covDet > 0.0;
		}
		public DiagonalCovGaussian(double[] mean, double[][] covariance) {
			this(mean,getDiagonal(covariance));
		}
		
		public boolean isValid()
		{
			return covDet > 0.0 && !SloppyMath.isVeryDangerous(logNormalization) && !ArrayUtil.hasNaN(varianceInverse);
		}
		
		/**
		 * updates the mean of the old gaussian but keeps the rest
		 * @param mean
		 * @param oldGauss
		 */
		public DiagonalCovGaussian(double[] mean, DiagonalCovGaussian oldGauss) {
			this.mean = mean;
			diff = new double[mean.length];
			this.variance = oldGauss.variance;
			this.varianceInverse = oldGauss.varianceInverse;
			this.covDet = oldGauss.covDet;
			//assert covDet > 0.0;
			this.logNormalization = oldGauss.logNormalization;
		}

		/**
		 * clone this gaussian
		 */
		public DiagonalCovGaussian clone(){
			return new DiagonalCovGaussian(this.mean.clone(), this.variance.clone());
		}
		
		public void mergeGaussian(Gaussian x, double myWeight){
			DiagonalCovGaussian y = (DiagonalCovGaussian)x;
			// linearly combine means
			double[] otherMeanScaled = ArrayUtil.multiply(y.mean, 1.0-myWeight);
			ArrayUtil.multiplyInPlace(this.mean, myWeight);
			ArrayUtil.addInPlace(this.mean, otherMeanScaled);
			
			// linearly combine variances
			double[] otherVarScaled = ArrayUtil.multiply(y.variance, 1.0-myWeight);
			ArrayUtil.multiplyInPlace(this.variance, myWeight);
			ArrayUtil.addInPlace(this.variance, otherVarScaled);
			
			// update the dependend parameters
			this.varianceInverse = ArrayUtil.inverse(variance);
			this.covDet = ArrayUtil.product(variance);
			//assert covDet > 0.0;
			setLogNorm(covDet);
			//assert !SloppyMath.isVeryDangerous(this.logNormalization);

		}
		private void setLogNorm(double covDet) {
			this.logNormalization = -(double)mean.length / 2.0 *  Math.log(2.0 * Math.PI)  + -0.5 * Math.log(covDet);
		}

		public double evalPdf(double[] x) {
			double num = Math.exp(evalLogPdf(x));
//			if (num < 1e-200)
//			{
//				System.out.println("Warning: small gaussina prob");
//			}
			return num;
//		  double cutOff = 10.0;
//			double[] diff = DoubleArrays.subtract(x, mean);
////			if (true) {
////				double sum = DoubleArrays.sum(diff);
////				return 1/(1+Math.exp(-1.0*sum));
////			}
//
////			if (covDet == 0.0) {
////				return SubphoneHMM.arrayEquals(x, mean) ? 1.0 : 0.0;
////				// } else if(varianceHasInf()){
//				// return arrayEquals(x, mean) ? 1.0 : 0.0;
////			} else {
//				
//				double expPart = Math.exp(-0.5 * computeExponent(diff, varianceInverse));
////						* DoubleArrays.innerProduct(diff, DoubleArrays.pointwiseMultiply(
////								varianceInverse, diff)));
//				double prob = normalization * expPart;
//				if (prob>cutOff) {
//					System.out.println("Cutoff kicks in");
//					prob = cutOff;
//				}
//				return prob;
//			}
		}

		public String toString() {
			StringBuffer sb = new StringBuffer("[");
			for (int i = 0; i < mean.length; ++i) {
				sb.append("(" + mean[i] + "," + 1.0 / varianceInverse[i] + "),");
			}
			sb.append("]");
			return sb.toString();
		}

		/**
		 * @return the mean
		 */
		public double[] getMean() {
			return mean;
		}

		public double evalLogPdf(double[] x) {

//			if (logNormalization==1) logNormalization = Math.log(normalization);
			
//			if (scratch == null)
			if (scratch == null) scratch = new double[mean.length];
			
			if (scratchCov == null)
				scratchCov = new double[mean.length];
			// if (noMean && !xxx) {init(mean,new Matrix(I(mean.length))); xxx = true;}
			if (TimitTester.staticPrevMeanSmooth > 0.0 && prevObservation != null) {
				ArrayUtil.multiply(prevObservation, TimitTester.staticPrevMeanSmooth,
						diff);
				ArrayUtil.addInPlace(diff, mean);
				ArrayUtil.multiplyInPlace(diff,
						1.0 / (1.0 + TimitTester.staticPrevMeanSmooth));
				ArrayUtil.subtract(x, diff, diff);
			}

			else {
				ArrayUtil.subtract(x, mean, diff);
			}
			double exponent = -1;
			if (TimitTester.staticPrevVarSmooth > 0.0 && prevObservation != null) {
				ArrayUtil.subtract(prevObservation, mean, scratch);
				scaledSelfProduct(scratch, TimitTester.staticPrevVarSmooth,
						scratchCov);

//				ArrayUtil.subtractInPlace(scratchCov, covInverse.getArray());
//				ArrayUtil.multiplyInPlace(scratchCov, -1.0
//						/ (1.0 + TimitTester.staticPrevVarSmooth));
//				exponent = computeD_Sigma_D(diff, scratchCov);
//				setLogNorm(new Matrix(scratchCov).det());
				ArrayUtil.addInPlace(scratchCov, variance);
				setLogNorm(ArrayUtil.product(scratchCov));
				invert(scratchCov);
				exponent = computeExponent(diff, scratchCov);
				
				// DoubleArrays.add(covInverse.getArray(), 1);
			} else {
				exponent = computeExponent(diff, varianceInverse);
			}

//			if (covDet == 0.0) {
//				return SubphoneHMM.arrayEquals(x, mean) ? 0.0 : Double.NEGATIVE_INFINITY;
//				// } else if(varianceHasInf()){
//				// return arrayEquals(x, mean) ? 1.0 : 0.0;
//			} else {
				
				double expPart = (-0.5 * computeExponent(diff, varianceInverse));
//						* DoubleArrays.innerProduct(diff, DoubleArrays.pointwiseMultiply(
//								varianceInverse, diff)));
				double prob = logNormalization + expPart;
//				if (prob>cutOff) {
//					System.out.println("Cutoff kicks in");
//					prob = cutOff;
//				}
				assert prob > Double.NEGATIVE_INFINITY ;
				if (TimitTester.staticEmissionAttenuation!=0) prob *= TimitTester.staticEmissionAttenuation;
				return prob;
//			}
		}
		
		/**
		 * @param scratchCov2
		 */
		private static void invert(double[] scratchCov2) {
			for (int i = 0; i < scratchCov2.length; ++i)
			{
				scratchCov2[i] = 1.0 / scratchCov2[i];
			}
			
		}
		public static double computeExponent(double[] diff, double[] varInv){
			double res = 0;
			for (int i=0; i<diff.length; i++){
				res += diff[i]*diff[i]*varInv[i];
			}
			return res;
		}

		/* (non-Javadoc)
		 * @see edu.berkeley.nlp.HMM.Gaussian#getCovariance()
		 */
		public double[][] getCovariance() {
			double[][] retVal = new double[mean.length][mean.length];
			for (int i = 0; i < mean.length; ++i)
			{
				retVal[i][i] = variance[i];
			}
			return retVal;
		}
		
		public static double[] getDiagonal(double[][] x)
		{
			double[] retVal = new double[x.length];
			for (int i = 0; i < x.length; ++i)
			{
				retVal[i] = x[i][i];
			}
			return retVal;
		}
		/* (non-Javadoc)
		 * @see edu.berkeley.nlp.prob.Gaussian#setMean(double[])
		 */
		public void setMean(double[] mean) {
			assert this.mean.length == mean.length;
			this.mean = mean;
			
		}
		public Gaussian[] splitInTwo(Randomizer randomizer, double rand) {
			Gaussian[] retVal = new Gaussian[2];
			for (int i = 0; i < 2; ++i)
			{
				retVal[i] = new DiagonalCovGaussian(
					randomizer.randPerturb(getMean(), rand),
					this);
			}
			return retVal;
		}
		
		
		public GaussianSuffStats newSuffStats() {

		return null;
		}
		
		private boolean noMean = false;
		/* (non-Javadoc)
		 * @see edu.berkeley.nlp.prob.Gaussian#isNoMean()
		 */
		public boolean isNoMean() {
			return noMean;
		}
		/* (non-Javadoc)
		 * @see edu.berkeley.nlp.prob.Gaussian#setNoMean()
		 */
		public void setNoMean(boolean noMean) {
		this.noMean = noMean;
			
		}
		public double[] getPrevObservation() {
			return prevObservation;
		}
		public void setPrevObservation(double[] prevObservation) {
			this.prevObservation = prevObservation;
		}
		
public static void scaledSelfProduct(double[] x, double weight, double[] res)
{
	final int dim = x.length;
	weight =  Math.sqrt(weight);

	for (int i = 0; i < dim; i++) {
		for (int j = i; j < dim; j++) {
			res[i] = (weight * x[i])
					* (weight * x[i]);
		}
	}
}

		
	}