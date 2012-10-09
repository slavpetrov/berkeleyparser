package edu.berkeley.nlp.io;

/**
 * Constructs FeatureLabel as a String with a corresponding BEGIN and END
 * position.
 * 
 * @author Marie-Catherine de Marneffe
 */
public class FeatureLabelTokenFactory implements
		LexedTokenFactory<FeatureLabel> {

	/**
	 * Constructs FeatureLabel as a String with a corresponding BEGIN and END
	 * position. (Does not take substr).
	 */
	public FeatureLabel makeToken(String str, int begin, int length) {
		FeatureLabel fl = new FeatureLabel();
		fl.setWord(str);
		fl.setCurrent(str);
		fl.setBeginPosition(begin);
		fl.setEndPosition(begin + length);
		return fl;
	}

}
