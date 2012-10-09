/**
 * 
 */
package edu.berkeley.nlp.discPCFG;

import edu.berkeley.nlp.classify.FeatureExtractor;
import edu.berkeley.nlp.util.Counter;

/**
 * @author adpauls
 * 
 */
public class LexiconFeatureExtractor implements
		FeatureExtractor<WordInSentence, LexiconFeature> {

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * edu.berkeley.nlp.classify.FeatureExtractor#extractFeatures(java.lang.
	 * Object)
	 */
	public Counter<LexiconFeature> extractFeatures(WordInSentence sentence) {

		int loc = sentence.getSecond();
		String word = sentence.getFirst().get(loc);
		Counter<LexiconFeature> counter = new Counter<LexiconFeature>();
		counter.incrementCount(new LexiconFeature(word), 1.0);
		// Reformed Mar 2004 (cdm); hopefully much better now.
		// { -CAPS, -INITC ap, -LC lowercase, 0 } +
		// { -KNOWNLC, 0 } + [only for INITC]
		// { -NUM, 0 } +
		// { -DASH, 0 } +
		// { -last lowered char(s) if known discriminating suffix, 0}
		int wlen = word.length();
		int numCaps = 0;
		boolean hasDigit = false;
		boolean hasDash = false;
		boolean hasLower = false;
		for (int i = 0; i < wlen; i++) {
			char ch = word.charAt(i);
			if (Character.isDigit(ch)) {
				hasDigit = true;
			} else if (ch == '-') {
				hasDash = true;
			} else if (Character.isLetter(ch)) {
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
		char ch0 = word.charAt(0);
		String lowered = word.toLowerCase();
		if (Character.isUpperCase(ch0) || Character.isTitleCase(ch0)) {
			if (loc == 0 && numCaps == 1) {
				counter.incrementCount(new LexiconFeature(
						LexiconFeature.MorphFeature.INIT_CAP), 1.0);
				// if (isKnown(lowered)) {
				// sb.incrementCount(LexiconFeature.KNOWNLC, 1.0);
				// }
			} else {
				counter.incrementCount(new LexiconFeature(
						LexiconFeature.MorphFeature.ALL_CAPS), 1.0);
			}
		} else if (!Character.isLetter(ch0) && numCaps > 0) {
			counter.incrementCount(new LexiconFeature(
					LexiconFeature.MorphFeature.ALL_CAPS), 1.0);
		} else if (hasLower) { // (Character.isLowerCase(ch0)) {
			counter.incrementCount(new LexiconFeature(
					LexiconFeature.MorphFeature.LOWER_CASE), 1.0);
		}
		if (hasDigit) {
			counter.incrementCount(new LexiconFeature(
					LexiconFeature.MorphFeature.HAS_DIGIT), 1.0);
		}
		if (hasDash) {
			counter.incrementCount(new LexiconFeature(
					LexiconFeature.MorphFeature.HAS_DASH), 1.0);
		}
		if (lowered.endsWith("s") && wlen >= 3) {
			// here length 3, so you don't miss out on ones like 80s
			char ch2 = lowered.charAt(wlen - 2);
			// not -ess suffixes or greek/latin -us, -is
			if (ch2 != 's' && ch2 != 'i' && ch2 != 'u') {
				counter.incrementCount(new LexiconFeature(
						LexiconFeature.MorphFeature.SUFF_S), 1.0);
			}
		} else if (word.length() >= 5 && !hasDash && !(hasDigit && numCaps > 0)) {
			// don't do for very short words;
			// Implement common discriminating suffixes
			/*
			 * if (Corpus.myLanguage==Corpus.GERMAN){
			 * sb.append(lowered.substring(lowered.length()-1)); }else{
			 */
			if (lowered.endsWith("ed")) {
				counter.incrementCount(new LexiconFeature(
						LexiconFeature.MorphFeature.SUFF_ED), 1.0);
			} else if (lowered.endsWith("ing")) {
				counter.incrementCount(new LexiconFeature(
						LexiconFeature.MorphFeature.SUFF_ING), 1.0);
			} else if (lowered.endsWith("ion")) {
				counter.incrementCount(new LexiconFeature(
						LexiconFeature.MorphFeature.SUFF_ION), 1.0);
			} else if (lowered.endsWith("er")) {
				counter.incrementCount(new LexiconFeature(
						LexiconFeature.MorphFeature.SUFF_ER), 1.0);
			} else if (lowered.endsWith("est")) {
				counter.incrementCount(new LexiconFeature(
						LexiconFeature.MorphFeature.SUFF_EST), 1.0);
			} else if (lowered.endsWith("ly")) {
				counter.incrementCount(new LexiconFeature(
						LexiconFeature.MorphFeature.SUFF_LY), 1.0);
			} else if (lowered.endsWith("ity")) {
				counter.incrementCount(new LexiconFeature(
						LexiconFeature.MorphFeature.SUFF_ITY), 1.0);
			} else if (lowered.endsWith("y")) {
				counter.incrementCount(new LexiconFeature(
						LexiconFeature.MorphFeature.SUFF_Y), 1.0);
			} else if (lowered.endsWith("al")) {
				counter.incrementCount(new LexiconFeature(
						LexiconFeature.MorphFeature.SUFF_AL), 1.0);
				// } else if (lowered.endsWith("ble")) {
				// sb.append("-ble");
				// } else if (lowered.endsWith("e")) {
				// sb.append("-e");
			}

		}
		return counter;
	}
}
