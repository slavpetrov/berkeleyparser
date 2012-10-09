package edu.berkeley.nlp.math;

/**
 * User: aria42 Date: Mar 10, 2009
 */
public interface Regularizer {
	/**
	 * Return Regularizer value and in-place grad update scaled by constant c
	 * 
	 * @param weights
	 * @param grad
	 * @return
	 */
	public double update(double[] weights, double[] grad, double c);

	public double val(double[] weights, double c);
}
