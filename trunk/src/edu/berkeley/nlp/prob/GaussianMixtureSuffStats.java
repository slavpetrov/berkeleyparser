/**
 * 
 */
package edu.berkeley.nlp.prob;

import edu.berkeley.nlp.HMM.MixtureSubphoneHMM;
import edu.berkeley.nlp.HMM.SubphoneHMM;
import fig.basic.LogInfo;

/**
 * @author adpauls
 * 
 */
public class GaussianMixtureSuffStats implements GaussianSuffStats {
	GaussianSuffStats[] mixtures;

	MultinomSuffStats mixtureWeights;

	public GaussianMixtureSuffStats(GaussianSuffStats[] stats) {
		mixtures = stats;
		mixtureWeights = new MultinomSuffStats(stats.length);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.HMM.GaussianSuffStats#add(double[], double)
	 */
	public void add(double[] x, double weight) {
		assert false;

	}

	public void add(double[] x, double weight, int mixture, double[] prevX) {
//		if (MixtureSubphoneHMM.yyy) LogInfo.dbg("adding weight " + weight + " to mixture " + mixture);
		final GaussianSuffStats gaussianSuffStats = mixtures[mixture];
		if (prevX != null){
		if (gaussianSuffStats instanceof NoMeanFullCovGaussianSuffStats ) {
			 ((NoMeanFullCovGaussianSuffStats) gaussianSuffStats)
					.add(x, prevX, weight);
		} else {
			gaussianSuffStats.add(x, weight);
		}
		addMixtureWeight(weight, mixture);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.HMM.GaussianSuffStats#add(edu.berkeley.nlp.HMM.GaussianSuffStats)
	 */
	public void add(GaussianSuffStats stats) {
		GaussianMixtureSuffStats d = (GaussianMixtureSuffStats) stats;
		for (int mixture = 0; mixture < d.mixtures.length; ++mixture) {
			mixtures[mixture].add(d.mixtures[mixture]);
			mixtureWeights.add(d.mixtureWeights.weights[mixture], mixture);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.HMM.GaussianSuffStats#estimate()
	 */
	public Gaussian estimate() {
		Gaussian[] gaussians = new Gaussian[mixtures.length];
		for (int i = 0; i < gaussians.length; ++i) {
			gaussians[i] = mixtures[i].estimate();
		}
		final double[] newWeights = mixtureWeights.estimate();
		return new GaussianMixture(gaussians, newWeights);
	}

	/**
	 * @param gamma
	 * @param mixture
	 */
	private void addMixtureWeight(double gamma, int mixture) {
		mixtureWeights.add(gamma, mixture);

	}

	@Override
	public GaussianSuffStats clone() {
		assert false;
		return null;
	}

}
