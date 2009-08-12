/**
 * 
 */
package edu.berkeley.nlp.prob;

import java.util.Random;

/**
 * @author adpauls
 *
 */
public  class Randomizer implements java.io.Serializable
{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Random randomGenerator;
	public Randomizer(int randSeed)
	{
		randomGenerator = new Random(randSeed);
		
	}


public double[] randPerturb(double[] val, double randomness) {
	if (randomness == 0)
		return val.clone();
	double[] res = new double[val.length];
	for (int i = 0; i < val.length; i++) {
		res[i] = val[i]
				* (1.0 + ((randomGenerator.nextDouble() - 0.5) * randomness / 100.0));
	}
	return res;
}


/**
 * @return
 */
public double nextDouble() {
	return randomGenerator.nextDouble();
}
}