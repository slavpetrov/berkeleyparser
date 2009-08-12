/**
 * 
 */
package edu.berkeley.nlp.HMM;



class PosteriorProbChart extends FwdBwdChart {



	// p(q_t|y)
	// private final double[][][][][] edgePosteriors; //
	// edgePosteriors[t][q_t][substate_t][q_t+1][substate+1] = p(q_t,q_t+1|y)

	// private final double[] cachedGammaDenoms;
	// private final double[] cachedProbDenoms;


	private final int[] cachedAllowedPhones;

	public PosteriorProbChart(SubphoneHMM hmm) {
		super(hmm, 0);
	
		cachedAllowedPhones = new int[hmm.numPhones-2];
		for (int i = 2; i < hmm.numPhones; ++i)
		{
			cachedAllowedPhones[i-2] = i;
		}

	}
	
	public void calcAlphasAndBetas() {
		calculateAlphas();
		calculateBetas();
	}	

	public void calcAlphas() {
		calculateAlphas();
	}	

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.HMM.SubphoneHMM.FwdBwdChart#allowedPhonesAtTime(int)
	 */
	@Override
	protected int[] allowedPhonesAtTime(int t) {
		if (t == 0) return new int[]{Corpus.START_STATE};
		if (t == T - 1) return   new int[]{Corpus.END_STATE};
		return cachedAllowedPhones;
	}



	@Override
	public void init(double[][] obs) {
		// TODO Auto-generated method stub
		super.init(obs);
	}



	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.HMM.FwdBwdChart#allowedNextPhonesAtTime(int)
	 */
	@Override
	protected int[] allowedNextPhonesAtTime(int t, int fromPhone) {
	return allowedPhonesAtTime(t);
	}

	



	

	
}