/**
 * 
 */
package edu.berkeley.nlp.discPCFG;

/**
 * @author adpauls
 * 
 */
public class LexiconFeature {

	public static enum MorphFeature {
		HAS_DIGIT, HAS_DASH, INIT_CAP, KNOWNLC, ALL_CAPS, LOWER_CASE, SUFF_S, SUFF_ED, SUFF_ING, SUFF_ION, SUFF_ER, SUFF_EST, SUFF_LY, SUFF_ITY, SUFF_Y, SUFF_AL, ACTUAL_WORD;
	}

	private String actualWord = null;
	private MorphFeature morphFeature;

	LexiconFeature(MorphFeature morphFeature) {
		this.morphFeature = morphFeature;
	}

	LexiconFeature(String actualWord) {
		this.actualWord = actualWord;
	}

	public boolean equals(Object o) {
		if (!(o instanceof LexiconFeature))
			return false;
		LexiconFeature rhs = (LexiconFeature) o;
		return toString().equals(rhs.toString());
	}

	public int hashCode() {
		return toString().hashCode();
	}

	public String toString() {
		if (actualWord != null)
			return actualWord;
		else
			return "MORPH::" + morphFeature;
	}
}
