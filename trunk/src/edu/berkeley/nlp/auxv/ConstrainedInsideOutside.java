/**
 * 
 */
package edu.berkeley.nlp.auxv;

import java.util.List;

/**
 * @author Alexandre Bouchard
 *
 */
public interface ConstrainedInsideOutside  extends InsideOutside
{
  /**
   * Execute the inside outside algorithm with constraints on the 
   * brackets
   * 
   * Calling compute(List, SuffStat) should be equivalent to calling
   * compute(List, SuffStat, boolean [][]) with the third argument
   * equals to true everywhere
   * @param sentence
   * @param suffStat
   */
  public boolean compute(List<String> sentence, SuffStat suffStat, 
      boolean [][] spanAllowed);
}
