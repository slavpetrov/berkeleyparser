package edu.berkeley.nlp.auxv;

/**
 * The result of posterior PCFG computations needed to train Grammars
 * 
 * Basically, a bunch of expected counts
 * @author Alexandre Bouchard
 *
 */
public interface SuffStat
{
	/**
	 * Multiply in place all the expected counts by the given scalar
	 * @param scalar
	 */
  public void times(double scalar);
  /**
	 * Add the expected counts of the other suffstat to this (in place)
	 * Leave the other untouched
	 * @param scalar
	 */
  public void add(SuffStat other);
  /**
   * A factory method: create a fresh, empty SuffStat of the current type,
   * leaving the caller untouched
   * @return
   */
  public SuffStat newInstance();
}
