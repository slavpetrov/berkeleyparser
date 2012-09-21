package edu.berkeley.nlp.PCFGLA;

import java.util.List;

/**
 *
 *
 *
 * @author dlwh
 */
public interface Featurizer {
  List<String>[] featurize(String word, int tag, int numSubstates, int wordCount, int tagWordCount);
}
