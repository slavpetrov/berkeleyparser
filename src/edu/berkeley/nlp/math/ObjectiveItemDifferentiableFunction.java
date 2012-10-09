package edu.berkeley.nlp.math;

/**
 * User: aria42 Date: Mar 10, 2009
 */
public interface ObjectiveItemDifferentiableFunction<I> {

	public void setWeights(double[] weights);

	public double update(I item, double[] grad);

	public int dimension();
}
