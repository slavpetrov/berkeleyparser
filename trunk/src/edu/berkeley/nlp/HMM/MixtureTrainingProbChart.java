/**
 * 
 */
package edu.berkeley.nlp.HMM;

import edu.berkeley.nlp.math.SloppyMath;
import edu.berkeley.nlp.prob.GaussianMixture;

class MixtureTrainingProbChart extends MixtureFwdBwdChart implements ITrainingProbChart{

	// p(q_t|y)
	// private final double[][][][][] edgePosteriors; //
	// edgePosteriors[t][q_t][substate_t][q_t+1][substate+1] = p(q_t,q_t+1|y)

	// private final double[] cachedGammaDenoms;
	// private final double[] cachedProbDenoms;

	/** Sequence of vector valued observations (i.e. the acoustics) */
	protected int[] phoneSequence;
	
	public MixtureTrainingProbChart(SubphoneHMM hmm, double emissionAttenuation) {
		super(hmm, emissionAttenuation);
		
	}

	public void init(int[] phoneInd, double[][] obs, int seq) {
		super.init(obs);
		phoneSequence = phoneInd;
	}
	
	public double getMixtureGamma(int phone, int substate, int t, int mixture)
	{
		return gammas[t][phone][substate][mixture];
	}

	// initArray(alphas);
	// initArray(betas);
	// initArray(gammas);

	/**
	 * computes the probability P(X_t = s_i, X_t+1 = s_j | O, m). NOTE: Assumes
	 * that the transition s_i -> s_j exists in the training sequence
	 * 
	 * @param t
	 *          time t
	 * @param fromState
	 *          the state s_i
	 * @param toState
	 *          the state s_j
	 * @param o
	 *          the emissions o
	 * @param alphasN
	 *          the forward-scores for o
	 * @param betasN
	 *          the backward-scores for o
	 * @param allowedStates
	 * @return P
	 */
	public double[][] getProbability(int t, int fromState, int toState) {
		// return edgePosteriors[t][fromState][fromSubstate][toState][toSubstate];

		double z = getUnscaledLikelihood();
		double z_scale = alphasScale[T - 1];
		double[][] res = new double[hmm.numSubstatesPerState[fromState]][hmm.numSubstatesPerState[toState]];
		for (int fromSubstate = 0; fromSubstate < hmm.numSubstatesPerState[fromState]; ++fromSubstate) {
			for (int fromMixture = 0; fromMixture < ((GaussianMixture)hmm.b[fromState][fromSubstate]).getNumMixtures(); ++fromMixture) {
				if ((fromState == Corpus.END_STATE || fromState == Corpus.START_STATE)
						&& fromMixture > 0)
					break;

				double alpha_s = alphas[t][fromState][fromSubstate][fromMixture];
				double alpha_scale = alphasScale[t];

				double leaveStateProb = hmm.c[toState][fromState][fromSubstate];
				for (int toSubstate = 0; toSubstate < hmm.numSubstatesPerState[toState]; ++toSubstate) {
					for (int toMixture = 0; toMixture < ((GaussianMixture)hmm.b[toState][toSubstate]).getNumMixtures(); ++toMixture) {
						if ((toState == Corpus.END_STATE || toState == Corpus.START_STATE)
								&& toMixture > 0)
							break;
						double beta_t = betas[t + 1][toState][toSubstate][toMixture];
						double beta_scale = betasScale[t + 1];
						double obsProb = (t == T - 2) ? 1.0
								: obsLikelihoods[t + 1][toState][toSubstate][toMixture];
						// .evalPdf(observations[t + 1]);
						double unscaled_posterior = alpha_s * leaveStateProb
								* hmm.a[toSubstate][toState][fromState][fromSubstate] / z
								* beta_t * obsProb;
						double posterior_scale = alpha_scale + beta_scale - z_scale;
						double exp_scale = getScaleFactor(posterior_scale);
						double edgePosterior = unscaled_posterior * exp_scale;
						if (Double.isInfinite(exp_scale)) {
							edgePosterior = 0;
						}
						assert FwdBwdChart.isProbability(edgePosterior);
						res[fromSubstate][toSubstate] += edgePosterior;
					}
				}
			}
		}
		return res;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.HMM.SubphoneHMM.FwdBwdChart#allowedPhonesAtTime(int)
	 */
	@Override
	public int[] allowedPhonesAtTime(int t) {
		return new int[] { phoneSequence[t] };
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.HMM.FwdBwdChart#allowedNextPhonesAtTime(int)
	 */
	@Override
	public int[] allowedNextPhonesAtTime(int t, int fromPhone) {
		return new int[] { phoneSequence[t + 1] };
	}
	
	

}