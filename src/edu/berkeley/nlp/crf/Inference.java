package edu.berkeley.nlp.crf;

import java.io.Serializable;

import edu.berkeley.nlp.classify.Encoding;
import edu.berkeley.nlp.classify.FeatureExtractor;
import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.math.DoubleMatrices;
import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.Pair;
import edu.berkeley.nlp.util.PriorityQueue;

public class Inference<V, E, F, L> implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1948395432745606240L;

	private final Encoding<F, L> encoding;
	private final ScoreCalculator<V, E, F, L> scoreCalculator;

	public Inference(Encoding<F, L> encoding,
			FeatureExtractor<V, F> vertexExtractor,
			FeatureExtractor<E, F> edgeExtractor) {
		this.encoding = encoding;
		this.scoreCalculator = new ScoreCalculator<V, E, F, L>(encoding,
				vertexExtractor, edgeExtractor);
	}

	public double[][] getAlphas(InstanceSequence<V, E, L> sequence, double[] w) {
		int n = sequence.getSequenceLength();
		double[][] alpha = new double[n][];
		alpha[0] = scoreCalculator.getVertexScores(sequence, 0, w);
		for (int i = 1; i < n; i++) {
			double[][] scoreMatrix = scoreCalculator.getScoreMatrix(sequence,
					i, w);
			alpha[i] = DoubleMatrices.product(alpha[i - 1], scoreMatrix);
		}
		return alpha;
	}

	public double[][] getBetas(InstanceSequence<V, E, L> sequence, double[] w) {
		int n = sequence.getSequenceLength();
		double[][] beta = new double[n][];
		beta[n - 1] = DoubleArrays.constantArray(1.0, encoding.getNumLabels());
		for (int i = n - 2; i >= 0; i--) {
			double[][] scoreMatrix = scoreCalculator.getScoreMatrix(sequence,
					i + 1, w);
			beta[i] = DoubleMatrices.product(scoreMatrix, beta[i + 1]);
		}
		return beta;
	}

	public Pair<int[][][][], double[][][]> getKBestChartAndBacktrace(
			InstanceSequence<V, E, L> sequence, double[] w, int k) {
		int n = sequence.getSequenceLength();
		int numLabels = encoding.getNumLabels();
		int[][][][] bestLabels = new int[n][numLabels][][];
		double[][][] bestScores = new double[n][numLabels][];
		double[] startScores = scoreCalculator.getLinearVertexScores(sequence,
				0, w);
		for (int l = 0; l < numLabels; l++) {
			bestScores[0][l] = new double[] { startScores[l] };
			bestLabels[0][l] = new int[][] { new int[] { -1, 0 } };
		}
		for (int i = 1; i < n; i++) {
			double[][] scoreMatrix = scoreCalculator.getLinearScoreMatrix(
					sequence, i, w);
			for (int l = 0; l < numLabels; l++) {
				PriorityQueue<Pair<Integer, Integer>> pq = new PriorityQueue<Pair<Integer, Integer>>();
				for (int pl = 0; pl < numLabels; pl++) {
					double edgeScore = scoreMatrix[pl][l];
					for (int c = 0; c < bestScores[i - 1][pl].length; c++) {
						double totalScore = edgeScore
								+ bestScores[i - 1][pl][c];
						pq.add(Pair.makePair(pl, c), totalScore);
					}
				}
				int cands = Math.min(k, pq.size());
				bestScores[i][l] = new double[cands];
				bestLabels[i][l] = new int[cands][2];
				for (int c = 0; c < cands; c++) {
					bestScores[i][l][c] = pq.getPriority();
					Pair<Integer, Integer> backtrace = pq.next();
					bestLabels[i][l][c][0] = backtrace.getFirst();
					bestLabels[i][l][c][1] = backtrace.getSecond();
				}
			}
		}
		return Pair.makePair(bestLabels, bestScores);
	}

	public double[][] getVertexPosteriors(double[][] alpha, double[][] beta) {
		double[][] p = new double[alpha.length][encoding.getNumLabels()];
		for (int i = 0; i < p.length; i++) {
			for (int l = 0; l < p[i].length; l++) {
				p[i][l] = alpha[i][l] * beta[i][l];
			}
			ArrayUtil.normalize(p[i]);
		}
		return p;
	}

	public double[][][] getEdgePosteriors(InstanceSequence<V, E, L> sequence,
			double[] w, double[][] alpha, double[][] beta) {
		int numLabels = encoding.getNumLabels();
		int n = sequence.getSequenceLength();
		double[][][] p = new double[n][numLabels][numLabels];
		for (int i = 1; i < p.length; i++) {
			double[][] scoreMatrix = scoreCalculator.getScoreMatrix(sequence,
					i, w);
			for (int lp = 0; lp < numLabels; lp++) {
				for (int lc = 0; lc < numLabels; lc++) {
					p[i][lp][lc] = alpha[i - 1][lp] * scoreMatrix[lp][lc]
							* beta[i][lc];
				}
			}
			ArrayUtil.normalize(p[i]);
		}
		return p;
	}

	public double getNormalizationConstant(double[][] alpha, double[][] beta) {
		int anyIndex = 0;
		double[] p = new double[alpha[anyIndex].length];
		for (int l = 0; l < p.length; l++) {
			p[l] = alpha[anyIndex][l] * beta[anyIndex][l];
		}
		return ArrayUtil.sum(p);
	}
}
