/**
 *
 */
package edu.berkeley.nlp.speech.decoder.dict;

import java.util.List;
import java.util.Map;

import edu.berkeley.nlp.HMM.Phone;
import fig.basic.Indexer;

/**
 * @author aria42
 *
 */
public interface PronounciationDictionary {

	public int[] getPhonemes(String word) ;
	public Map<String, int[]> getDictionary() ;
	public Indexer<Phone> getPhoneIndexer();

}
