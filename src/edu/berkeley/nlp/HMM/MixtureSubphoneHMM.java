/**
 * 
 */
package edu.berkeley.nlp.HMM;

import java.util.Arrays;
import java.util.List;

import edu.berkeley.nlp.prob.DiagCovGaussianSuffStats;
import edu.berkeley.nlp.prob.FullCovGaussianSuffStats;
import edu.berkeley.nlp.prob.Gaussian;
import edu.berkeley.nlp.prob.GaussianMixture;
import edu.berkeley.nlp.prob.GaussianMixtureSuffStats;
import edu.berkeley.nlp.prob.GaussianSuffStats;
import fig.basic.Indexer;
import fig.basic.LogInfo;

/**
 * @author adpauls
 * 
 */
public class MixtureSubphoneHMM extends SubphoneHMM {

	
	
	/**
	 * @param phoneIndexer
	 * @param gaussianDim
	 * @param rSeed
	 * @param fullGauss
	 * @param transitionSmooth
	 * @param meanSmooth
	 * @param varSmooth
	 * @param minVarSmooth
	 * @param emissionAttenuation
	 * @param print
	 * @param filename
	 */
	public MixtureSubphoneHMM(Indexer<Phone> phoneIndexer, int gaussianDim,
			int rSeed, boolean fullGauss, double transitionSmooth, double meanSmooth,
			double varSmooth, double minVarSmooth, double emissionAttenuation,
			boolean print, String filename) {
		super(phoneIndexer, gaussianDim, rSeed, fullGauss, transitionSmooth,
				meanSmooth, varSmooth, minVarSmooth, emissionAttenuation, print,
				filename);
		

	}

	/**
	 * 
	 */
	private static final long serialVersionUID = -156442246466973148L;
	
	public static boolean yyy = false;

	@Override
	protected void countEmissions(GaussianSuffStats[][] new_b,
			ITrainingProbChart probChart_, double[][] o, int T) {
		MixtureTrainingProbChart probChart = (MixtureTrainingProbChart) probChart_;
		for (int t = 1; t < T - 1; t++) {
			for (int phone : probChart.allowedPhonesAtTime(t)) {

				for (int substate = 0; substate < numSubstatesPerState[phone]; ++substate) {
					for (int mixture = 0; mixture < ((GaussianMixture)b[phone][substate]).getNumMixtures(); ++mixture) {
						if ((phone == Corpus.END_STATE || phone == Corpus.START_STATE)
								&& mixture > 0)
							break;
						double mixtureGamma = probChart.getMixtureGamma(phone, substate, t, mixture);

						assert FwdBwdChart.isProbability(mixtureGamma);
						yyy = (phone == 46 || phone == 13);
//						if (yyy) LogInfo.dbg(phone + "::" + substate + "::" + mixture + " at time " + t + " (" + T + ")");
						final GaussianMixtureSuffStats gaussianMixtureSuffStats = ((GaussianMixtureSuffStats) new_b[phone][substate]);
						gaussianMixtureSuffStats.add(o[t], mixtureGamma, mixture,o[t-1]);
						yyy = false;
						// double[] weightedObs = ArrayMath.multiply(o[t], gamma);
						//
						// // first the means
						// ArrayMath.addInPlace(new_b_sum[phone][substate], weightedObs);
						// new_b_normalize[phone][substate] += gamma;
						//
						// // now variance
						// double[][] scaledOuterProduct = null;
						// if (useFullGaussian) {
						// scaledOuterProduct = FullCovGaussian.scaledOuterSelfProduct(o[t],
						// gamma);
						// for (int i = 0; i < gaussianDim; ++i) {
						// ArrayMath.addInPlace(new_b_crossterms[phone][substate][i],
						// scaledOuterProduct[i]);
						// }
						// } else {
						//
						// double[] diagonal = ArrayMath.pairwiseMultiply(o[t], o[t]);
						// ArrayMath.addInPlace(new_b_crossterms_diag[phone][substate],
						// ArrayMath.multiply(diagonal, gamma));
						//
						// }

					}
				}
			}
		}
	}

	@Override
	protected ITrainingProbChart createTrainingChart() {
		// TODO Auto-generated method stub
		return new MixtureTrainingProbChart(this, emissionAttenuation);
	}

	@Override
	public void initializeModelFromStateSequence(List<int[]> stateSequences, List<double[][]> obsSequences, int numSubstates, int randomness) {
		// TODO Auto-generated method stub
		super.initializeModelFromStateSequence(stateSequences, obsSequences, numSubstates, randomness);
		
		//dummies, just so getNumMixtures returns the right amount
		b[0][0] = b[1][0] = new GaussianMixture(new Gaussian[1],new double[1]);
		for (int phone = 2; phone < numPhones; ++phone)
		{
			for (int substate = 0; substate < numSubstatesPerState[phone]; ++substate)
			{
				Gaussian gaussian = b[phone][substate];
				final int numMixtures = 2;
				Gaussian[] gaussians = new Gaussian[numMixtures];
				
				double[] weights = new double[numMixtures];
				for (int mixture = 0; mixture < numMixtures; ++mixture)
				{
					Gaussian newGaussian = gaussian.clone();
					weights[mixture] = 1.0 / numMixtures;
					
					newGaussian.setMean(randomizer.randPerturb(gaussian.getMean(), randomness));
					gaussians[mixture] = newGaussian;
					if (mixture == 1) newGaussian.setNoMean(true);
				}
				b[phone][substate] = new GaussianMixture(gaussians, weights); 
			}
		}
	}

	@Override
	protected GaussianSuffStats newGaussianSuffStats(int phone, int substate) {
//		GaussianSuffStats[] copy = new GaussianSuffStats[statsTemplate.length];
//		for (int i = 0; i < copy.length; ++i)
//		{
//			copy[i] = statsTemplate[i].clone();
//		}
//		return new GaussianMixtureSuffStats(copy);
		return b[phone][substate].newSuffStats();
	}

}
