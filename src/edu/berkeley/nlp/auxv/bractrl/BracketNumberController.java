/**
 * 
 */
package edu.berkeley.nlp.auxv.bractrl;

import java.util.List;
import java.util.Random;

import edu.berkeley.nlp.auxv.BracketConstraints;

public interface BracketNumberController
{
  public int initialNumberOfBrackets(Random rand, List<String> sentence);
  public int numberOfBracketsToRemove(Random rand, 
  		BracketConstraints.BracketProposerView currentConstraints);
}