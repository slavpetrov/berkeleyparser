package edu.berkeley.nlp.crf;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import edu.berkeley.nlp.classify.Encoding;
import edu.berkeley.nlp.classify.FeatureExtractor;
import edu.berkeley.nlp.math.DifferentiableFunction;
import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.math.LBFGSMinimizer;
import edu.berkeley.nlp.util.Indexer;
import edu.berkeley.nlp.util.Lists;
import edu.berkeley.nlp.util.Logger;
import edu.berkeley.nlp.util.Pair;
import edu.berkeley.nlp.util.PriorityQueue;

public class ChainCRFTagger<V, E, L> implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 9165167851374358823L;

	private final Encoding<?, L> encoding;
	private final Inference<V, E, ?, L> inf;
	private final double[] w;

	public ChainCRFTagger(Encoding<?, L> encoding, Inference<V, E, ?, L> inf,
			double[] w) {
		this.encoding = encoding;
		this.inf = inf;
		this.w = w;
	}

	public List<L> getViterbiLabelSequence(InstanceSequence<V, E, L> s) {
		return getTopKLabelSequencesAndScores(s, 1).get(0).getFirst();
	}

	public List<Pair<List<L>, Double>> getTopKLabelSequencesAndScores(
			InstanceSequence<V, E, L> s, int k) {
		Pair<int[][][][], double[][][]> chart = inf.getKBestChartAndBacktrace(
				s, w, k);
		List<Pair<List<L>, Double>> sentences = new ArrayList<Pair<List<L>, Double>>(
				k);
		int n = s.getSequenceLength();
		PriorityQueue<Pair<Integer, Integer>> rankedScores = buildRankedScoreQueue(chart
				.getSecond()[n - 1]);
		for (int i = 0; i < k && rankedScores.hasNext(); i++) {
			double score = rankedScores.getPriority();
			Pair<Integer, Integer> chain = rankedScores.next();
			sentences.add(Pair.makePair(
					rebuildChain(chart.getFirst(), chain.getFirst(),
							chain.getSecond()), score));
		}

		return sentences;
	}

	private List<L> rebuildChain(int[][][][] backtrace, int endLabel,
			int endCandidate) {
		int n = backtrace.length;
		List<L> l = new ArrayList<L>(n);
		int currentLabel = endLabel;
		int currentCandidate = endCandidate;
		for (int i = n - 1; i >= 0; i--) {
			l.add(encoding.getLabel(currentLabel));
			int nextLabel = backtrace[i][currentLabel][currentCandidate][0];
			currentCandidate = backtrace[i][currentLabel][currentCandidate][1];
			currentLabel = nextLabel;
		}
		assert (currentLabel == -1 && currentCandidate == 0);
		Lists.reverse(l);
		return l;
	}

	private PriorityQueue<Pair<Integer, Integer>> buildRankedScoreQueue(
			double[][] scores) {
		PriorityQueue<Pair<Integer, Integer>> pq = new PriorityQueue<Pair<Integer, Integer>>();
		for (int l = 0; l < scores.length; l++) {
			for (int c = 0; c < scores[l].length; c++) {
				pq.add(Pair.makePair(l, c), scores[l][c]);
			}
		}
		return pq;
	}

	public static class Factory<V, E, F, L> {
		private final FeatureExtractor<V, F> vertexExtractor;
		private final FeatureExtractor<E, F> edgeExtractor;
		private final double sigma;
		private final int iterations;

		public Factory(FeatureExtractor<V, F> vertexExtractor,
				FeatureExtractor<E, F> edgeExtractor, double sigma,
				int iterations) {
			this.vertexExtractor = vertexExtractor;
			this.edgeExtractor = edgeExtractor;
			this.sigma = sigma;
			this.iterations = iterations;
		}

		public ChainCRFTagger<V, E, L> trainTagger(
				List<? extends LabeledInstanceSequence<V, E, L>> trainingData) {
			Encoding<F, L> encoding = buildEncoding(trainingData);
			DifferentiableFunction objective = new CRFObjectiveFunction<V, E, F, L>(
					trainingData, encoding, vertexExtractor, edgeExtractor,
					sigma);
			LBFGSMinimizer minimizer = new LBFGSMinimizer(iterations);
			Logger.startTrack("Training with LBFGS");
			double[] w = minimizer.minimize(
					objective,
					DoubleArrays.constantArray(0.0, encoding.getNumFeatures()
							* encoding.getNumLabels()), 1e-4, true);
			Logger.endTrack();
			return new ChainCRFTagger<V, E, L>(encoding,
					new Inference<V, E, F, L>(encoding, vertexExtractor,
							edgeExtractor), w);
		}

		private Encoding<F, L> buildEncoding(
				List<? extends LabeledInstanceSequence<V, E, L>> trainingData) {
			Indexer<F> featureIndexer = new Indexer<F>();
			Indexer<L> labelIndexer = new Indexer<L>();
			for (LabeledInstanceSequence<V, E, L> s : trainingData) {
				for (int i = 0; i < s.getSequenceLength(); i++) {
					labelIndexer.add(s.getGoldLabel(i));
				}
			}
			for (LabeledInstanceSequence<V, E, L> s : trainingData) {
				for (int i = 0; i < s.getSequenceLength(); i++) {
					featureIndexer.addAll(vertexExtractor.extractFeatures(
							s.getVertexInstance(i)).keySet());
					if (i > 0) {
						for (int l = 0; l < labelIndexer.size(); l++) {
							featureIndexer.addAll(edgeExtractor
									.extractFeatures(
											s.getEdgeInstance(i,
													labelIndexer.getObject(l)))
									.keySet());
						}
					}
				}
			}
			return new Encoding<F, L>(featureIndexer, labelIndexer);
		}
	}

}
