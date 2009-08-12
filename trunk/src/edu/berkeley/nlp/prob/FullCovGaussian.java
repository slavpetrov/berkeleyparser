/**
 * 
 */
package edu.berkeley.nlp.prob;

import java.io.Serializable;

import edu.berkeley.nlp.HMM.TimitTester;
import edu.berkeley.nlp.prob.Randomizer;
import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.math.SloppyMath;
import fig.basic.LogInfo;
import Jama.LUDecomposition;
import Jama.Matrix;

public class FullCovGaussian implements Gaussian, Serializable {
	double[] mean;

	Matrix covariance;

	double covDet;

	double[][] scratchCov;

	Matrix covInverse;

	double[] diag;

	private double[] diff;

	private double[] scratch;

	private double[] prevObservation;

	private int dim;

	double logNormalization;

	private static final long serialVersionUID = 1L;

	public FullCovGaussian(double[] mean, FullCovGaussian oldGauss) {
		this.mean = mean;
		if (mean == null)
			dim = oldGauss.dimension();
		else
			dim = mean.length;
		diff = new double[dim];
		scratch = new double[dim];
		scratchCov = new double[dim][dim];
		this.covariance = oldGauss.covariance;
		this.covDet = oldGauss.covDet;

		this.covInverse = oldGauss.covInverse;
		this.logNormalization = oldGauss.logNormalization;
		this.diag = extractDiagonal(covInverse);

	}

	public FullCovGaussian(double[] mean, Matrix covariance) {
		this.mean = mean;
		init(mean, covariance);

	}

	private void init(double[] mean, Matrix covariance) {
		if (mean == null)
			dim = covariance.getArray().length;
		else
			dim = mean.length;
		diff = new double[dim];
		this.covariance = covariance;

		setInverse(covariance);

		covDet = covariance.det();

		setLogNorm(covDet);
		// assert isValid();
	}

	private void setLogNorm(double covDet) {
		assert !SloppyMath.isVeryDangerous(covDet);
		this.logNormalization = -(double) dim / 2.0 * Math.log(2.0 * Math.PI)
				+ -0.5 * Math.log(covDet);
	}

	public FullCovGaussian(double[] mean, double[][] covariance) {
		this.mean = mean;
		init(mean, new Matrix(covariance));
		// this.covariance = new Matrix(covariance);
		// diff = new double[mean.length];
		// setInverse(new Matrix(covariance));
		//
		// covDet = this.covariance.det();
		//	
		// this.logNormalization = -(double)mean.length / 2.0 * Math.log(2.0 *
		// Math.PI) + -0.5 * Math.log(covDet);

	}

	private void setInverse(Matrix covariance) {
		try {
			this.covInverse = covariance.inverse();
			this.diag = extractDiagonal(covInverse);
		} catch (RuntimeException e) {
			if (e.getMessage().contains("Matrix is singular")) {
				covInverse = null;
			} else {
				throw e;
			}
		}
	}

	/**
	 * @param covInverse2
	 * @return
	 */
	private double[] extractDiagonal(Matrix covInverse) {
		if (covInverse == null)
			return null;
		double[][] covI = covInverse.getArray();
		double[] diag = new double[covI.length];
		for (int i = 0; i < covI.length; i++) {
			diag[i] = covI[i][i];
		}
		return diag;
	}

	public boolean isValid() {
		final boolean b = covDet > 0.0
				&& !SloppyMath.isVeryDangerous(logNormalization) && covInverse != null;
		assert b;
		return b;
	}

	public void makeBlockDiagonal(Matrix covMat) {
		double[][] cov = covMat.getArray();
		for (int i = 13; i < cov.length; i++) {
			for (int j = 0; j < 13; j++) {
				cov[i][j] = 0;
			}
		}
		for (int i = 0; i < 13; i++) {
			for (int j = 13; j < cov.length; j++) {
				cov[i][j] = 0;
			}
		}
		for (int i = 13; i < 26; i++) {
			for (int j = 26; j < cov.length; j++) {
				cov[i][j] = 0;
			}
		}
		for (int i = 26; i < cov.length; i++) {
			for (int j = 13; j < 26; j++) {
				cov[i][j] = 0;
			}
		}

	}

	/**
	 * clone this gaussian
	 */
	public FullCovGaussian clone() {
		final FullCovGaussian fullCovGaussian = new FullCovGaussian(this.mean
				.clone(), this.covariance.copy());
		fullCovGaussian.setNoMean(this.noMean);
		return fullCovGaussian;
	}

	public void mergeGaussian(Gaussian x, double myWeight) {
		FullCovGaussian y = (FullCovGaussian) x;
		// linearly combine means
		double[] otherMeanScaled = ArrayUtil.multiply(y.mean, 1.0 - myWeight);
		ArrayUtil.multiplyInPlace(this.mean, myWeight);
		ArrayUtil.addInPlace(this.mean, otherMeanScaled);

		// and also the covariances
		Matrix otherCovScaled = y.covariance.times(1.0 - myWeight);
		this.covariance.timesEquals(myWeight);
		this.covariance.plusEquals(otherCovScaled);

		setInverse(covariance);

		this.covDet = this.covariance.det();

		this.logNormalization = -(double) dim / 2.0 * Math.log(2.0 * Math.PI)
				+ -0.5 * Math.log(covDet);
		assert !SloppyMath.isVeryDangerous(this.logNormalization);
	}

	public static double[][] diagVarianceToCovariance(double[] variance) {
		double[][] cov = new double[variance.length][variance.length];
		for (int i = 0; i < variance.length; ++i) {
			cov[i][i] = variance[i];
		}
		return cov;
	}

	public double evalPdf(double[] x) {
		double logY = evalLogPdf(x);
		if (logY == Double.NEGATIVE_INFINITY)
			return 0;
		double y = Math.exp(logY);
		if (y > 1000) {
			LogInfo.warning("big prob for gaussian: " + y);
		}

		return y;

	}

	/**
	 * computes the exponent for evalPdf diff' * matrix * diff
	 * 
	 * @param diff
	 * @param array
	 * @return
	 */
	public static double computeD_Sigma_D(double[] diff, double[][] matrix) {
		double res = 0;
		for (int i = 0; i < matrix.length; i++) {
			double tmp = 0;
			for (int j = 0; j < matrix.length; j++) {
				tmp += matrix[i][j] * diff[j];
			}
			res += tmp * diff[i];
		}
		return res;
	}

	public static Matrix vectorToMatrix(double[] v) {

		double[][] tmp = new double[1][];
		tmp[0] = v;
		return new Matrix(tmp);

	}

	/**
	 * @return
	 */
	public int dimension() {
		return dim;
	}

	/**
	 * @return the mean
	 */
	public double[] getMean() {
		return mean;
	}

	/**
	 * computes gamma*(diff*diff')
	 * 
	 * @param diff
	 * @param gamma
	 * @return
	 */
	public static double[][] scaledOuterSelfProduct(double[] diff, double gamma) {
		final int dim = diff.length;

		double[][] result = new double[dim][dim];

		scaledOuterSelfProduct(diff, gamma, result);
		return result;

	}

	public static void scaledOuterSelfProduct(double[] diff, double gamma,
			double[][] result) {
		final int dim = diff.length;
		double sqrtGamma = Math.sqrt(gamma);

		for (int i = 0; i < dim; i++) {
			for (int j = i; j < dim; j++) {
				result[i][j] = result[j][i] = (sqrtGamma * diff[i])
						* (sqrtGamma * diff[j]);
			}
		}

	}

	static double[][] I(int gaussianDim) {
		double[][] retVal = new double[gaussianDim][gaussianDim];
		for (int i = 0; i < gaussianDim; ++i) {
			retVal[i][i] = 1;
		}
		return retVal;
	}

	static double[][] I;

	private boolean xxx = false;

	public double evalLogPdf(double[] x) {
		if (diff == null)
			diff = new double[dim];
		if (scratch == null)
			scratch = new double[dim];
		
		if (scratchCov == null)
			scratchCov = new double[dim][dim];
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
			scaledOuterSelfProduct(scratch, TimitTester.staticPrevVarSmooth,
					scratchCov);

//			ArrayUtil.subtractInPlace(scratchCov, covInverse.getArray());
//			ArrayUtil.multiplyInPlace(scratchCov, -1.0
//					/ (1.0 + TimitTester.staticPrevVarSmooth));
//			exponent = computeD_Sigma_D(diff, scratchCov);
//			setLogNorm(new Matrix(scratchCov).det());
			ArrayUtil.addInPlace(scratchCov, covariance.getArray());
			Matrix m = new Matrix(scratchCov);
			setLogNorm(m.det());
			exponent = computeD_Sigma_D(diff, m.inverse().getArray());
			
			// DoubleArrays.add(covInverse.getArray(), 1);
		} else {
			exponent = computeD_Sigma_D(diff, covInverse.getArray());
		}
		// if (noMean) ArrayMath.multiplyInPlace(diff, 1.2);

		

		double prob = logNormalization + (-0.5 * exponent);
//		if (TimitTester.staticEmissionAttenuation == 1.0) LogInfo.logss(prob);

		assert prob > Double.NEGATIVE_INFINITY;
		if (TimitTester.staticEmissionAttenuation != 0)
			prob *= TimitTester.staticEmissionAttenuation;
		return prob;
	}

	/**
	 * @param diff
	 * @return
	 */
	private double computeApproximateDist(double[] diff) {
		if (diag == null)
			return 0;
		double res = 0;
		for (int i = 0; i < diag.length; i++) {
			res += diag[i] * diff[i] * diff[i];
		}
		return res;
	}

	public double[][] getCovariance() {
		return covariance.getArray();
	}

	public void setMean(double[] mean) {
		assert mean == null || dim == mean.length;
		this.mean = mean;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.prob.Gaussian#splitInTwo()
	 */
	public Gaussian[] splitInTwo(Randomizer randomizer, double rand) {
		Gaussian[] retVal = new Gaussian[2];
		for (int i = 0; i < 2; ++i) {
			retVal[i] = new FullCovGaussian(randomizer.randPerturb(getMean(), rand),
					this);
		}
		return retVal;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.prob.Gaussian#newSuffStats()
	 */
	public GaussianSuffStats newSuffStats() {
		return noMean ? new NoMeanFullCovGaussianSuffStats(dim)
				: new FullCovGaussianSuffStats(dim);
	}

	private boolean noMean = false;

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.prob.Gaussian#isNoMean()
	 */
	public boolean isNoMean() {
		return noMean;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.prob.Gaussian#setNoMean(boolean)
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
	
	public static double trace(double[][] matrix)
	{
		double x = 0;
		for (int i = 0; i < matrix.length; ++i)
		{
			x += matrix[i][i];
		}
		return x;
	}

}