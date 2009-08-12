/**
 * 
 */
package edu.berkeley.nlp.speech.phonedecoder;

/**
 *
 * @author John DeNero
 */
public interface PhonePruner {
	/**
	 * Returns chart pruning decisions based on a phone posterior matrix.
	 * 
	 * 1 == do not prune (keep) this element of the phone chart
	 * 
	 * double[time][phone][substate]
	 * 
	 * @param posteriors
	 * @return
	 */
	public boolean[][][] prune(double[][][] posteriors, boolean[][][] prunedChart);
	
}
