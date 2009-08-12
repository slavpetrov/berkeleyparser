/**
 * 
 */
package edu.berkeley.nlp.prob;

import edu.berkeley.nlp.HMM.SubphoneHMM;
import edu.berkeley.nlp.math.DoubleArrays;
import fig.basic.LogInfo;

/**
 * @author adpauls
 *
 */
public class MultinomSuffStats {
	
	double[] weights;
	double norm;
	
	public MultinomSuffStats(int n)
	{
		weights = new double[n];
		norm = 0.0;
	}
	
	public void add(double weight, int i)
	{
		weights[i] += weight;
//		LogInfo.dbg("adding weight " + weight + ":" + i + "::" + this);
		norm += weight;
	}
	
	public double[] estimate()
	{
//		assert norm != 0.0;
//		return new double[]{0.5,0.5};
		return DoubleArrays.multiply(weights,SubphoneHMM.divide(1.0 , norm));
	}
	
	

}
