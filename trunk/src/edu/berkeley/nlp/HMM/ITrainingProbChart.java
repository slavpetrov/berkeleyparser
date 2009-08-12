/**
 * 
 */
package edu.berkeley.nlp.HMM;

/**
 * @author adpauls
 *
 */
public interface ITrainingProbChart {

	

	/**
	 * @return
	 */
	double getLogLikelihood();

	/**
	 * 
	 */
	void calc();

	/**
	 * @param is
	 * @param ds
	 * @param seq
	 */
	void init(int[] is, double[][] ds, int seq);

	/**
	 * @param t
	 * @return
	 */
	int[] allowedPhonesAtTime(int t);

	/**
	 * @param t
	 * @param fromPhone
	 * @return
	 */
	int[] allowedNextPhonesAtTime(int t, int fromPhone);

	/**
	 * @param t
	 * @param fromPhone
	 * @param toPhone
	 * @return
	 */
	double[][] getProbability(int t, int fromPhone, int toPhone);

}
