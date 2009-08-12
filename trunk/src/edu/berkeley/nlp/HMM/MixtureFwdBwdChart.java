/**
 * 
 */
package edu.berkeley.nlp.HMM;

import java.util.Arrays;

import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.math.SloppyMath;
import edu.berkeley.nlp.prob.Gaussian;
import edu.berkeley.nlp.prob.GaussianMixture;
import edu.berkeley.nlp.util.ArrayUtil;
import fig.basic.LogInfo;

abstract class MixtureFwdBwdChart {

	// private final double emissionAttenuation;
	// SCALING
	protected final double SCALE = Math.exp(100);

	// Note: e^709 is the largest double java can handle.

	// TODO: we could get rid of the 2nd dimension since it is observed in our
	// data
	protected double[][][][] alphas; // alphas[t][q_t][q_substate][z_t] =

	// p(y_0,...,p_t,q_t)

	protected double[] alphasScale;

	protected double[][][][] betas; // betas[t][q_t][q_substate][z_t] =

	// p(y_t+1,...,y_T|q_t)

	protected double[] betasScale;

	protected double[][][][] obsLikelihoods; // the observation likelihoods

	protected double[][][][] gammas; // nodePosteriors[t][q_t][q_substate][z_t] =

	/** Sequence of vector valued observations (i.e. the acoustics) */
	protected double[][] observations;

	/** The length of the observations sequence */
	protected int T;

	protected int myLength;

	protected SubphoneHMM hmm;

	public MixtureFwdBwdChart(SubphoneHMM hmm, double emissionAttenuation) {
		// this.emissionAttenuation = emissionAttenuation;
		this.hmm = hmm;

	}

	public void init(double[][] obs) {
		observations = obs;
		T = obs.length;
		if (T > myLength) {
			alphas = new double[T][hmm.numPhones][hmm.maxNumSubstates][2];
			betas = new double[T][hmm.numPhones][hmm.maxNumSubstates][2];
			gammas = new double[T][hmm.numPhones][hmm.maxNumSubstates][2];
			obsLikelihoods = new double[T][hmm.numPhones][hmm.maxNumSubstates][2];
			alphasScale = new double[T];
			betasScale = new double[T];
			myLength = T;
		} else {
			// experiment!!! we dont really need to fill them with 0s since we
			// overwrite the relevant entries
			// ArrayUtil.fill(alphas, T, 0);
			// ArrayUtil.fill(betas, T, 0);
			// ArrayUtil.fill(gammas, T, 0);
			// ArrayUtil.fill(obsLikelihoods, T, 0);
			// Arrays.fill(alphasScale, 0);
			// Arrays.fill(betasScale, 0);
		}

	}

	abstract protected int[] allowedPhonesAtTime(int t);

	abstract protected int[] allowedNextPhonesAtTime(int t, int fromPhone);

	// assumes that calc() has been called before
	public double getUnscaledLikelihood() {
		return alphas[T - 1][hmm.phoneIndexer.indexOf(Corpus.END_PHONE)][0][0];
	}

	public double getLogLikelihood() {
		double scaleFactor = alphasScale[T - 1];
		return Math
				.log(alphas[T - 1][hmm.phoneIndexer.indexOf(Corpus.END_PHONE)][0][0])
				+ (100 * scaleFactor);
	}

	/**
	 * computes the forward-scores b(i,t) for states at times t with emission O
	 * under the current model
	 * 
	 * @param o
	 *          the emission O
	 * @return forward scores b(i,t)
	 */
	protected void calculateAlphas() {

		// Basis
		Arrays
				.fill(alphas[0][hmm.phoneIndexer.indexOf(Corpus.START_PHONE)][0], 1.0);
		alphasScale[0] = 0.0;

		// Induction
		for (int t = 1; t < T; t++) {
			final double[][][] alphat = alphas[t];
			final double[][][] obsLikelihoods_t = obsLikelihoods[t];
			double max = 0;

			for (int toPhone : allowedPhonesAtTime(t)) {

				for (int toSubstate = 0; toSubstate < hmm.numSubstatesPerState[toPhone]; ++toSubstate) {
					for (int toMixture = 0; toMixture < ((GaussianMixture) hmm.b[toPhone][toSubstate])
							.getNumMixtures(); ++toMixture) {
						if (toPhone == Corpus.END_STATE && toMixture > 0)
							break;
						double alpha = 0.0;
						for (int fromPhone : allowedPhonesAtTime(t - 1)) {

							for (int fromSubstate = 0; fromSubstate < hmm.numSubstatesPerState[fromPhone]; ++fromSubstate) {
								for (int fromMixture = 0; fromMixture < ((GaussianMixture) hmm.b[fromPhone][fromSubstate])
										.getNumMixtures(); ++fromMixture) {
									if (fromPhone == Corpus.START_STATE && fromMixture > 0)
										break;
									double previousAlpha = alphas[t - 1][fromPhone][fromSubstate][fromMixture];
									// if (previousAlpha==0) continue;
									double leaveStateProb = hmm.c[toPhone][fromPhone][fromSubstate];
									alpha += previousAlpha
											* hmm.a[toSubstate][toPhone][fromPhone][fromSubstate]
											* leaveStateProb;
								}
							}
						}

						if (t < T - 1 && alpha > 0 && toPhone != 1) {
							final GaussianMixture gaussianMixture = ((GaussianMixture) hmm.b[toPhone][toSubstate]);
							final Gaussian gaussian = gaussianMixture.getGaussian(toMixture);
							 double mixtureWeight = gaussianMixture
									.getMixtureWeight(toMixture);
							if (t==1)
							{
								//in first  state, all emission comes from current state
								mixtureWeight = toMixture == 0 ? 1.0 : 0.0;
							}
							double obsLik = -1;
							if (toMixture == 1 && t > 1) {
								gaussian.setMean(observations[t-1]);
								obsLik = gaussian.evalPdf(
										observations[t]);
								gaussian.setMean(null);
							} 
							else if (toMixture == 1)
							{
								obsLik = 0.0;
							}
							else
							{
								obsLik = gaussian.evalPdf(
										observations[t]);
							}
								
								
//							if (toPhone == 46) LogInfo.dbg("Substate:" + toSubstate + "::" + obsLik + "::" + mixtureWeight + " for mixture " + toMixture + " prev:" + allowedPhonesAtTime(t-1)[0] + " next:" + allowedPhonesAtTime(t+1)[0]);
							obsLik *= mixtureWeight;

							// if (emissionAttenuation!=0) obsLik = Math.pow(obsLik,
							// emissionAttenuation);
							obsLikelihoods_t[toPhone][toSubstate][toMixture] = obsLik;
							// assert obsLik > 0 && !Double.isInfinite(obsLik);
							alpha *= obsLik;
						}
						if (alpha > max)
							max = alpha;

						alphat[toPhone][toSubstate][toMixture] = alpha;
					}
				}
			}
			assert max > 0 : "No alpha path for for time " + t;

			int logScale = 0;
			double scale = 1.0;
			while (max > SCALE) {
				max /= SCALE;
				scale *= SCALE;
				logScale += 1;
			}
			while (max > 0.0 && max < 1.0 / SCALE) {
				max *= SCALE;
				scale /= SCALE;
				logScale -= 1;
			}
			if (logScale != 0) {
				for (int toPhone : allowedPhonesAtTime(t)) {
					// for (int toPhone = 0; toPhone < hmm.numPhones; ++toPhone) {

					for (int toSubstate = 0; toSubstate < hmm.numSubstatesPerState[toPhone]; ++toSubstate) {
						for (int mixture = 0; mixture < ((GaussianMixture) hmm.b[toPhone][toSubstate])
								.getNumMixtures(); ++mixture) {

							alphat[toPhone][toSubstate][mixture] /= scale;
						}
					}
				}
			}

			alphasScale[t] = alphasScale[t - 1] + logScale;
		}
	}

	/**
	 * computes the backward-scores b(i,t) for states at times t with emission O
	 * under the current model
	 * 
	 * @param o
	 *          the emission O
	 * @return backward scores b(i,t)
	 */
	protected void calculateBetas() {
		// Basis
		Arrays.fill(betas[T - 1][hmm.phoneIndexer.indexOf(Corpus.END_PHONE)][0],
				1.0);
		betasScale[T - 1] = 0;
		// Induction

		for (int t = T - 2; t >= 0; t--) {
			final double[][][] betat = betas[t];
			final double[][][] betatp1 = betas[t + 1];
			final double[][][] obsLikelihoods_t = obsLikelihoods[t + 1];

			double max = Double.NEGATIVE_INFINITY;
			for (int fromPhone : allowedPhonesAtTime(t)) {

				for (int fromSubstate = 0; fromSubstate < hmm.numSubstatesPerState[fromPhone]; ++fromSubstate) {
					for (int fromMixture = 0; fromMixture < ((GaussianMixture) hmm.b[fromPhone][fromSubstate])
							.getNumMixtures(); ++fromMixture) {
						if (fromPhone == Corpus.START_STATE && fromMixture > 0)
							break;
						double beta = 0.0;
						for (int toPhone : allowedPhonesAtTime(t + 1)) {
							double leaveStateProb = hmm.c[toPhone][fromPhone][fromSubstate];
							for (int toSubstate = 0; toSubstate < hmm.numSubstatesPerState[toPhone]; ++toSubstate) {
								for (int toMixture = 0; toMixture < ((GaussianMixture) hmm.b[toPhone][toSubstate])
										.getNumMixtures(); ++toMixture) {
									if (toPhone == Corpus.END_STATE && toMixture > 0)
										break;
									double previousBeta = betatp1[toPhone][toSubstate][toMixture];

									double obsProb = (t == T - 2) ? 1.0
											: obsLikelihoods_t[toPhone][toSubstate][toMixture];
									// assert obsProb > 0 && !Double.isInfinite(obsProb);
									beta += previousBeta * leaveStateProb
											* hmm.a[toSubstate][toPhone][fromPhone][fromSubstate]
											* obsProb;

								}
							}
						}
						if (beta > max)
							max = beta;

						betat[fromPhone][fromSubstate][fromMixture] = beta;

					}
				}
			}
			assert max > 0 : "No beta path found for time " + t;
			int logScale = 0;
			double scale = 1.0;
			while (max > SCALE) {
				max /= SCALE;
				scale *= SCALE;
				logScale += 1;
			}
			while (max > 0.0 && max < 1.0 / SCALE) {
				max *= SCALE;
				scale /= SCALE;
				logScale -= 1;
			}
			if (logScale != 0) {
				// for (int fromPhone = 0; fromPhone < hmm.numPhones; ++fromPhone) {
				for (int fromPhone : allowedPhonesAtTime(t)) {
					for (int fromSubstate = 0; fromSubstate < hmm.numSubstatesPerState[fromPhone]; ++fromSubstate) {
						for (int mixture = 0; mixture < ((GaussianMixture) hmm.b[fromPhone][fromSubstate])
								.getNumMixtures(); ++mixture) {
							betat[fromPhone][fromSubstate][mixture] /= scale;
						}
					}
				}
			}
			betasScale[t] = betasScale[t + 1] + logScale;
		}
	}

	protected double getScaleFactor(double logScale) {
		return SubphoneHMM.calcScaleFactor(logScale, SCALE);
	}

	protected void calculateGammas() {
		double z = getUnscaledLikelihood();
		double z_scale = alphasScale[T - 1];

		for (int t = 0; t < T - 1; ++t) {
			for (int fromPhone : allowedPhonesAtTime(t)) {
				for (int fromSubstate = 0; fromSubstate < hmm.numSubstatesPerState[fromPhone]; ++fromSubstate) {
					for (int mixture = 0; mixture < ((GaussianMixture) hmm.b[fromPhone][fromSubstate])
							.getNumMixtures(); ++mixture) {
						if (fromPhone == Corpus.START_STATE && mixture > 0)
							break;
						if (fromPhone == Corpus.END_STATE && mixture > 0)
							break;
						double alpha_s = alphas[t][fromPhone][fromSubstate][mixture];
						double alpha_scale = alphasScale[t];
						double beta_s = betas[t][fromPhone][fromSubstate][mixture];
						double beta_scale = betasScale[t];
						double unscaled_posterior = alpha_s / z * beta_s;
						double posterior_scale = alpha_scale + beta_scale - z_scale;
						double exp_scale = getScaleFactor(posterior_scale);

						double gamma = unscaled_posterior * exp_scale;
						if (Double.isInfinite(exp_scale)) {
							gamma = 0;
						}
						assert FwdBwdChart.isProbability(gamma) : "Gamma is " + gamma;
						gammas[t][fromPhone][fromSubstate][mixture] = gamma;
					}
				}
			}
		}
	}

	public double getGamma(int phone, int substate, int t) {
		double x = 0;
		for (int mixture = 0; mixture < ((GaussianMixture) hmm.b[phone][substate])
				.getNumMixtures(); ++mixture) {
			if (phone == Corpus.START_STATE && mixture > 0)
				break;
			if (phone == Corpus.END_STATE && mixture > 0)
				break;
			x += gammas[t][phone][substate][mixture];
		}
		return x;
	}

	public void calc() {
		calculateAlphas();
		calculateBetas();
		assert Math.abs(1.0
				- betas[0][hmm.phoneIndexer.indexOf(Corpus.START_PHONE)][0][0]
				/ alphas[T - 1][hmm.phoneIndexer.indexOf(Corpus.END_PHONE)][0][0]) < 1e-5;
		verifyLikelihood();
		calculateGammas();
	}

	private void verifyLikelihood() {
		double z = getUnscaledLikelihood();
		double z_scale = alphasScale[T - 1];
		for (int t = T - 1; t >= 0; --t) {
			double sum = 0.0;
			for (int phone : allowedPhonesAtTime(t)) {

				for (int substate = 0; substate < hmm.numSubstatesPerState[phone]; ++substate) {
					for (int mixture = 0; mixture < ((GaussianMixture) hmm.b[phone][substate])
							.getNumMixtures(); ++mixture) {
						if ((phone == Corpus.END_STATE || phone == Corpus.START_STATE)
								&& mixture > 0)
							break;
						double alpha_s = alphas[t][phone][substate][mixture];

						double alpha_scale = alphasScale[t];
						double beta_s = betas[t][phone][substate][mixture];

						double beta_scale = betasScale[t];
						double unscaled_posterior = alpha_s / z * beta_s;
						double posterior_scale = alpha_scale + beta_scale - z_scale;
						double exp_scale = getScaleFactor(posterior_scale);

						double x = unscaled_posterior * exp_scale;
						// if (SloppyMath.isVeryDangerous(x))
						// {
						//						
						// }
						sum += x;
					}
				}
			}
			if (Math.abs(sum - 1.0) > 0.01) {

				LogInfo.error("Sum for t=" + t + "::" + sum + "(" + T + ")");
				// break;

			}
			// System.out.println("Sum for t=" + t + ":" + sum);
		}
	}

	public int getMostLikelySubstate(int t, int q_t) {
		int argMax = -1;
		double max = Double.NEGATIVE_INFINITY;
		for (int substate = 0; substate < hmm.numSubstatesPerState[q_t]; ++substate) {
			final double gamma = getGamma(q_t, substate, t);
			if (gamma > max) {
				max = gamma;
				argMax = substate;
			}
		}
		return argMax;
	}

}