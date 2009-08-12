/**
 * 
 */
package edu.berkeley.nlp.HMM;

import java.util.ArrayList;
import java.util.List;

import edu.berkeley.nlp.math.SloppyMath;
import fig.basic.LogInfo;

/**
 * @author petrov
 *
 */
public class MaxRuleDecoder implements Decoder{

	private SubphoneHMM hmm;
	private int[][] maxChild; // [t][toPhone] -> fromPhone: stores the best child (at time t-1) of toPhone (at time t)
	private double[][] maxScore;
	private double insertionPenalty;
//	private double emissionAttenuation;
	
	public MaxRuleDecoder(SubphoneHMM hmm, double insertionPenalty, double emissionAttenuation)
	{
		this.hmm = hmm;
		this.insertionPenalty = Math.exp(-insertionPenalty);
//		this.emissionAttenuation = emissionAttenuation;
	}
	
	public List<List<Phone>> decode(List<double[][]> obsSequences) {
		LogInfo.track("maxRuleDecode");
		List<List<Phone>> retVal = new ArrayList<List<Phone>>();

		PosteriorProbChart probChart = new PosteriorProbChart(hmm);
		int k = 0;
		for (double[][] o : obsSequences) {
			LogInfo.logs("sequence" + k++);
			final int T = o.length;

			probChart.init(o);
			probChart.calc();
			maxChild = new int[T][hmm.numPhones];
			maxScore = new double[T][hmm.numPhones];
			maxScore[0][0] = 1.0;
			double z = probChart.getUnscaledLikelihood();
			double z_scale = probChart.alphasScale[T - 1];

			for (int t = 0; t < T-1; ++t) {
				for (int toPhone : probChart.allowedPhonesAtTime(t+1)) {
					double bestScore = 0;
					int bestChild = -1;
					double normalizer = 0;
					for (int toSubstate = 0; toSubstate < hmm.numSubstatesPerState[toPhone]; ++toSubstate) {
						normalizer += probChart.getGamma(toPhone, toSubstate, t+1);
					}
					if (SloppyMath.isDangerous(normalizer)) normalizer = 1;
					for (int fromPhone : probChart.allowedPhonesAtTime(t)) {
						if (maxScore[t][fromPhone] == 0.0) continue;
						double ruleScore = 0;
						for (int fromSubstate = 0; fromSubstate < hmm.numSubstatesPerState[fromPhone]; ++fromSubstate) {
							double alpha_s = probChart.alphas[t][fromPhone][fromSubstate];
							double alpha_scale = probChart.alphasScale[t];
							for (int toSubstate = 0; toSubstate < hmm.numSubstatesPerState[toPhone]; ++toSubstate) {
								double beta_s = probChart.betas[t+1][toPhone][toSubstate];
								double beta_scale = probChart.betasScale[t+1];
								double rule_s = hmm.c[toPhone][fromPhone][fromSubstate]*hmm.a[toSubstate][toPhone][fromPhone][fromSubstate];
								double obsProb = (t == T - 2) ? 1.0 : probChart.obsLikelihoods[t+1][toPhone][toSubstate];
								double unscaled_posterior = alpha_s / z * beta_s * rule_s * obsProb;
								double posterior_scale = alpha_scale + beta_scale - z_scale;
								double exp_scale = probChart.getScaleFactor(posterior_scale);
							
								double gamma =  unscaled_posterior * exp_scale;
								if (Double.isInfinite(exp_scale)) gamma = 0;
								
								ruleScore += gamma;

							}
						}
						
						// gives exactly the same result as the other one
//						double normalizer = 0;
//						for (int fromSubstate = 0; fromSubstate < hmm.numSubstatesPerState[fromPhone]; ++fromSubstate) {
//							normalizer += probChart.getGamma(fromPhone, fromSubstate, t);
//						}
//						if (SloppyMath.isDangerous(normalizer)) normalizer = 1;
						ruleScore *= maxScore[t][fromPhone];
						
						ruleScore /= normalizer;
						if (fromPhone!=toPhone) ruleScore *= insertionPenalty;
						if (ruleScore>bestScore){
							bestScore = ruleScore;
							bestChild = fromPhone;
						}
					}
					maxChild[t+1][toPhone] = bestChild;
					maxScore[t+1][toPhone] = bestScore;
				}
			}
			List<Phone> currPhones = new ArrayList<Phone>();
			currPhones.add(Corpus.END_PHONE);
			int lastPhoneIndex = 1;
			for (int t = T-1; t >= 0; t--) {
				lastPhoneIndex = maxChild[t][lastPhoneIndex];
				currPhones.add(0,hmm.phoneIndexer.get(lastPhoneIndex));
			}
			retVal.add(currPhones);
		}
			LogInfo.end_track();
		return retVal;
	}
	
}
