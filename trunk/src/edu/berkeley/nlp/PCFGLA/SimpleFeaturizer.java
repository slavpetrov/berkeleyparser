package edu.berkeley.nlp.PCFGLA;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author dlwh
 */
public class SimpleFeaturizer implements Featurizer,Serializable {
  // how rare before we generate word shape features
  private final int uncommonThreshold;
  // how rare before we don't generate indicator features.
  private final int rareThreshold;

  public SimpleFeaturizer(int uncommonThreshold, int rareThreshold) {
    this.uncommonThreshold = uncommonThreshold;
    this.rareThreshold = rareThreshold;
  }

  public List<String>[] featurize(String word, int tag, int numSubstates, int wordCount, int tagWordCount) {
    List<String> templates = fillTemplates(word,wordCount, tagWordCount);

    List<String>[] ret = new List[numSubstates];
    String coarsePrefix = "#"+tag+":";
    for(int sub = 0; sub < ret.length; ++sub) {
      String finePrefix = coarsePrefix+ "sub-"+sub + ":";
      ret[sub] = new ArrayList<String>();
      for(String template: templates) {
        ret[sub].add(coarsePrefix + template);
        ret[sub].add(finePrefix + template);
      }
    }

    return ret;
  }

  public List<String> fillTemplates(String word, int wordCount, int tagWordCount) {
    ArrayList<String> templates = new ArrayList<String>();
    if(wordCount > rareThreshold) {
      if(tagWordCount > 0) templates.add(word);
      if(wordCount > uncommonThreshold)
        return templates;
    }
    int wlen = word.length();
    int numCaps = 0;
    boolean hasDigit = false;
    boolean hasDash = false;
    boolean hasLower = false;
    boolean hasLetter = false;
    for (int i = 0; i < wlen; i++) {
      char ch = word.charAt(i);
      if (Character.isDigit(ch)) {
        hasDigit = true;
      } else if (ch == '-') {
        hasDash = true;
      } else if (Character.isLetter(ch)) {
        hasLetter = true;
        if (Character.isLowerCase(ch)) {
          hasLower = true;
        } else if (Character.isTitleCase(ch)) {
          hasLower = true;
          numCaps++;
        } else {
          numCaps++;
        }
      }
    }
    if(hasLetter)
      templates.add("shape:LETTER");
    else
      templates.add("shape:NOLETTER");
    char ch0 = word.charAt(0);
    String lowered = word.toLowerCase();
    if (Character.isUpperCase(ch0) || Character.isTitleCase(ch0)) {
      /* TODO: fix this stuff
      if (loc == 0 && numCaps == 1) {
        templates.add("shape:INITC");
        if (isKnown(lowered)) {
          templates.add("shape:KNOWNLC");
        }
      } else
       * 
       */
      {
        templates.add("shape:CAPS");
      }
    } else if (!Character.isLetter(ch0) && numCaps > 0) {
      templates.add("shape:CAPS");
    } else if (hasLower) { // (Character.isLowerCase(ch0)) {
      templates.add("shape:LC");
    }
    if (hasDigit) {
      templates.add("shape:NUM");
    }
    if (hasDash) {
      templates.add("shape:DASH");
    }
    if (lowered.endsWith("s") && wlen >= 3) {
      // here length 3, so you don't miss out on ones like 80s
      char ch2 = lowered.charAt(wlen - 2);
      // not -ess suffixes or greek/latin -us, -is
      if (ch2 != 's' && ch2 != 'i' && ch2 != 'u') {
        templates.add("shape:s");
      }
    } else if (word.length() >= 5 && !hasDash && !(hasDigit && numCaps > 0)) {
      // don't do for very short words;
      // Implement common discriminating suffixes
/*          	if (Corpus.myLanguage==Corpus.GERMAN){
              sb.append(lowered.substring(lowered.length()-1));
            }else{*/
      if (lowered.endsWith("ed")) {
        templates.add("shape:ed");
      } else if (lowered.endsWith("ing")) {
        templates.add("shape:ing");
      } else if (lowered.endsWith("ion")) {
        templates.add("shape:ion");
      } else if (lowered.endsWith("er")) {
        templates.add("shape:er");
      } else if (lowered.endsWith("est")) {
        templates.add("shape:est");
      } else if (lowered.endsWith("ly")) {
        templates.add("shape:ly");
      } else if (lowered.endsWith("ity")) {
        templates.add("shape:ity");
      } else if (lowered.endsWith("y")) {
        templates.add("shape:y");
      } else if (lowered.endsWith("al")) {
        templates.add("shape:al");
        // } else if (lowered.endsWith("ble")) {
        // templates.add("shape:ble");
        // } else if (lowered.endsWith("e")) {
        // templates.add("shape:e");
      }
    }
    return templates;
  }



}
