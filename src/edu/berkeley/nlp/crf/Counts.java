package edu.berkeley.nlp.crf;

import java.util.ArrayList;
import java.util.List;

import edu.berkeley.nlp.classify.Encoding;
import edu.berkeley.nlp.classify.FeatureExtractor;
import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.Logger;
import edu.berkeley.nlp.util.Pair;

public class Counts<V, E, F, L> {
	private final Encoding<F, L> encoding;
	private final FeatureExtractor<V, F> vertexExtractor;
	private final FeatureExtractor<E, F> edgeExtractor;
	private final Inference<V, E, F, L> inf;

	public Counts(Encoding<F, L> encoding,
			FeatureExtractor<V, F> vertexExtractor,
			FeatureExtractor<E, F> edgeExtractor) {
		this.encoding = encoding;
		this.vertexExtractor = vertexExtractor;
		this.edgeExtractor = edgeExtractor;
		this.inf = new Inference<V, E, F, L>(encoding, vertexExtractor,
				edgeExtractor);
	}

	public List<Counter<F>> getEmpiricalCounts(
			List<? extends LabeledInstanceSequence<V, E, L>> sequences) {
		int numLabels = encoding.getNumLabels();
		List<Counter<F>> counts = new ArrayList<Counter<F>>(numLabels);
		for (int l = 0; l < numLabels; l++) {
			counts.add(new Counter<F>());
		}
		for (LabeledInstanceSequence<V, E, L> s : sequences) {
			for (int i = 0; i < s.getSequenceLength(); i++) {
				Counter<F> vertexFeatures = vertexExtractor.extractFeatures(s
						.getVertexInstance(i));
				int goldLabelIndex = encoding.getLabelIndex(s.getGoldLabel(i));
				counts.get(goldLabelIndex).incrementAll(vertexFeatures);
				if (i > 0) {
					Counter<F> edgeFeatures = edgeExtractor.extractFeatures(s
							.getEdgeInstance(i, s.getGoldLabel(i - 1)));
					counts.get(goldLabelIndex).incrementAll(edgeFeatures);
				}
			}
		}
		return counts;
	}

	public Pair<Double, List<Counter<F>>> getLogNormalizationAndExpectedCounts(
			List<? extends InstanceSequence<V, E, L>> sequences, double[] w) {
		int numLabels = encoding.getNumLabels();
		List<Counter<F>> counts = new ArrayList<Counter<F>>(numLabels);
		for (int l = 0; l < numLabels; l++) {
			counts.add(new Counter<F>());
		}
		double totalLogZ = 0.0;
		Logger.startTrack("Computing expected counts");
		int index = 0;
		for (InstanceSequence<V, E, L> s : sequences) {
			double[][] alpha = inf.getAlphas(s, w);
			double[][] beta = inf.getBetas(s, w);
			totalLogZ += Math.log(inf.getNormalizationConstant(alpha, beta));
			double[][] vertexPosteriors = inf.getVertexPosteriors(alpha, beta);
			double[][][] edgePosteriors = inf.getEdgePosteriors(s, w, alpha,
					beta);
			for (int i = 0; i < s.getSequenceLength(); i++) {
				Counter<F> vertexFeatures = vertexExtractor.extractFeatures(s
						.getVertexInstance(i));
				for (int l = 0; l < numLabels; l++) {
					counts.get(l).incrementAll(
							vertexFeatures.scaledClone(vertexPosteriors[i][l]));
				}
				if (i > 0) {
					for (int pl = 0; pl < numLabels; pl++) {
						Counter<F> edgeFeatures = edgeExtractor
								.extractFeatures(s.getEdgeInstance(i,
										encoding.getLabel(pl)));
						for (int cl = 0; cl < numLabels; cl++) {
							counts.get(cl)
									.incrementAll(
											edgeFeatures
													.scaledClone(edgePosteriors[i][pl][cl]));
						}
					}
				}
			}
			Logger.logs("Processed %d/%d sentences", ++index, sequences.size());
		}
		Logger.endTrack();
		return Pair.makePair(totalLogZ, counts);
	}

}
