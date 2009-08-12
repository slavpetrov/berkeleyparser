/**
 * 
 */
package edu.berkeley.nlp.speech.phonedecoder;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.berkeley.nlp.math.DoubleArrays;
import fig.basic.IOUtils;
import fig.basic.NumUtils;
import fig.exec.Execution;

/**
 * Heuristics for editing posteriors to improve pruning results.
 * 
 * Key ideas:
 * <ul>
 * <li> Certain phones are often confused (e.g., "aa" & "ay"); when a phone is
 * predicted, similar phones should also be retained for the next round.
 * 
 * <li> The time that one phone ends and another begins is fuzzy; when a phone
 * boundary is detected, the latter phone should be retained in the chart for
 * the earlier phone and vise versa (useful at word decoding time?).
 * 
 * <li> The relative order of posteriors are more reliable than their magnitude;
 * perhaps we should use top-k lists instead of absolute posterior thresholding
 * to prune.
 * 
 * <li> Perhaps transition matrices without a boosted diagonal would work better
 * for this task after confusions are propagated.
 * </ul>
 * 
 * @author John DeNero
 */
public abstract class ThresholdPruner implements Serializable, PhonePruner {
	double threshold;

	public ThresholdPruner(double threshold) {
		super();
		this.threshold = threshold;
	}

	public ThresholdPruner() {
		this(0);
	}

	public void setThreshold(double threshold) {
		this.threshold = threshold;
	}

	abstract void writeObject();

	/**
	 * @param posteriors
	 * @param prunedChart
	 * @return
	 */
	protected boolean[][][] thresholdPosteriors(double[][][] posteriors,
			boolean[][][] prunedChart) {
		boolean build = prunedChart == null;
		if (build)
			prunedChart = new boolean[posteriors.length][][];
		for (int t = 0; t < posteriors.length; t++) {
			double[][] dist = posteriors[t];
			if (build)
				prunedChart[t] = new boolean[dist.length][];
			for (int phone = 0; phone < posteriors[t].length; phone++) {
				double[] subdist = dist[phone];
				if (build)
					prunedChart[t][phone] = new boolean[subdist.length];
				for (int substate = 0; substate < posteriors[t][phone].length; substate++) {
					prunedChart[t][phone][substate] = subdist[substate] >= threshold;
				}
			}
		}
		return prunedChart;
	}

	public static class SimplePruner extends ThresholdPruner {

		private static final long serialVersionUID = 1L;

		public SimplePruner(double threshold) {
			super(threshold);
		}

		public SimplePruner() {
			super();
		}

		public void writeObject() {
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see edu.berkeley.nlp.speech.phonedecoder.PhonePruner#prune(double[][][],
		 *      boolean[][][])
		 */
		public boolean[][][] prune(double[][][] posteriors,
				boolean[][][] prunedChart) {
			return thresholdPosteriors(posteriors, prunedChart);
		}
	}

	public static class Confusions extends ThresholdPruner {

		private static final long serialVersionUID = 1L;

		private static final String FILE_NAME = "ConfusionPruner.bin";

		List<List<Integer>> commonConfusions;

		/**
		 * @param threshold
		 *          threshold for posterior decoding
		 * @param confMatrix
		 *          confusion matrix of guesses vs. gold under posterior decoding
		 * @param confThreshold
		 *          threshold used to compute confusion pairs
		 */
		public Confusions(int[][] confMatrix, double confThreshold) {
			commonConfusions = fromRatioOfTotalGuesses(confMatrix, confThreshold);
		}

		private List<List<Integer>> fromRatioOfTotalGuesses(
				int[][] confusionMatrix, double threshold) {
			List<List<Integer>> allConfusions = new ArrayList<List<Integer>>();
			for (int guess = 0; guess < confusionMatrix.length; guess++) {
				int total = DoubleArrays.add(confusionMatrix[guess]);
				ArrayList<Integer> confusions = new ArrayList<Integer>();
				allConfusions.add(confusions);
				if (total == 0)
					continue;
				for (int gold = 0; gold < confusionMatrix.length; gold++) {
					if (guess == gold)
						continue;
					// If more than theshold of your guess "ay"s were in fact "ai"s...
					if ((double) confusionMatrix[guess][gold] / total > threshold) {
						confusions.add(gold);
					}
				}
			}
			return allConfusions;
		}

		public double[][][] boostPosteriors(double[][][] posteriors) {
			return boostPosteriors2(posteriors);
		}

		// TODO Make this a boosting function, not a ceiling function
		public double[][][] boostPosteriors1(double[][][] posteriors) {
			double[][][] newPost = new double[posteriors.length][][];
			for (int t = 0; t < posteriors.length; t++) {
				double[][] dist = NumUtils.copy(posteriors[t]);
				newPost[t] = dist;
				for (int phone = 0; phone < dist.length; phone++) {
					double max = DoubleArrays.max(posteriors[t][phone]);
					Arrays.fill(dist[phone], Math.max(max, dist[phone][0]));
					for (int other : commonConfusions.get(phone)) {
						double othermax = Math.max(max, DoubleArrays.max(dist[other]));
						Arrays.fill(dist[other], othermax);
					}
				}
			}
			return newPost;
		}

		// TODO Make this a boosting function, not a ceiling function
		public double[][][] boostPosteriors2(double[][][] posteriors) {
			double[][][] newPost = new double[posteriors.length][][];
			for (int t = 0; t < posteriors.length; t++) {
				double[][] dist = NumUtils.copy(posteriors[t]);
				newPost[t] = dist;
				for (int phone = 0; phone < dist.length; phone++) {
					double max = DoubleArrays.max(dist[phone]);
					for (int other : commonConfusions.get(phone)) {
						max = Math.max(max, DoubleArrays.max(dist[other]));
					}
					for (int subphone = 0; subphone < dist[phone].length; subphone++) {
						dist[phone][subphone] = mean(posteriors[t][phone][subphone], max);
					}
				}
			}
			return newPost;
		}

		private double mean(double d, double max) {
			return Math.sqrt(max * d);
		}

		public void writeObject() {
			String file = Execution.getFile(FILE_NAME);
			IOUtils.writeObjFileHard(file, this);
		}

		public static Confusions loadFromPrevExec(String execdir) {
			File f = new File(execdir, FILE_NAME);
			return (Confusions) IOUtils.readObjFileHard(f.getPath());
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see edu.berkeley.nlp.speech.phonedecoder.PhonePruner#prune(double[][][],
		 *      boolean[][][])
		 */
		public boolean[][][] prune(double[][][] posteriors,
				boolean[][][] prunedChart) {
			double[][][] newPosteriors = boostPosteriors(posteriors);
			return thresholdPosteriors(newPosteriors, prunedChart);
		}
	}

}
