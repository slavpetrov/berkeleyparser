/**
 * 
 */
package edu.berkeley.nlp.auxv.bractrl;

import java.util.Random;

import edu.berkeley.nlp.auxv.BracketConstraints;
import fig.basic.Pair;

public interface BracketProposer 
{
  public Pair<Integer, Integer> next(Random rand, BracketConstraints.BracketProposerView<String> currentConstraints);
  /**
   * Some proposers want a things to be always disjoint brackets,
   * some don't, give the corresponding datastructure
   * @return
   */
  public boolean allowsOverlappingBrackets();
}