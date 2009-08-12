/**
 * 
 */
package edu.berkeley.nlp.HMM;

import java.util.List;

/**
 * @author adpauls
 *
 */
public interface Decoder {
	
	List<List<Phone>> decode(List<double[][]> obsSequences);

}
