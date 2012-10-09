package edu.berkeley.nlp.math;

/**
 * User: aria42 Date: Mar 10, 2009
 */
public class L2Regularizer implements Regularizer {
	double sigmaSquared = 1.0;

	public L2Regularizer(double sigmaSquared) {
		this.sigmaSquared = sigmaSquared;
	}

	public L2Regularizer() {

	}

	public double getSigmaSquared() {
		return sigmaSquared;
	}

	public void setSigmaSquared(double sigmaSquared) {
		this.sigmaSquared = sigmaSquared;
	}

	public double update(double[] weights, double[] grad, double c) {
		double l2 = 0.0;
		for (int w = 0; w < weights.length; w++) {
			double weight = weights[w];
			l2 += c * weight * weight / sigmaSquared;
			grad[w] += c * 2.0 * weight / sigmaSquared;
		}
		return l2;
	}

	public double val(double[] weights, double c) {
		double l2 = 0.0;
		for (int w = 0; w < weights.length; w++) {
			double weight = weights[w];
			l2 += weight * weight / sigmaSquared;
		}
		return l2;
	}
}
