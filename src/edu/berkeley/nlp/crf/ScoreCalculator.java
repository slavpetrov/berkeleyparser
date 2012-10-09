package edu.berkeley.nlp.crf;

import java.io.Serializable;

import edu.berkeley.nlp.classify.Encoding;
import edu.berkeley.nlp.classify.FeatureExtractor;
import edu.berkeley.nlp.classify.IndexLinearizer;
import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.Counter;

public class ScoreCalculator<V, E, F, L> implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6864706229279071608L;

	private final Encoding<F, L> encoding;
	private final FeatureExtractor<V, F> vertexExtractor;
	private final FeatureExtractor<E, F> edgeExtractor;
	private final IndexLinearizer il;

	public ScoreCalculator(Encoding<F, L> encoding,
			FeatureExtractor<V, F> vertexExtractor,
			FeatureExtractor<E, F> edgeExtractor) {
		this.encoding = encoding;
		this.vertexExtractor = vertexExtractor;
		this.edgeExtractor = edgeExtractor;
		this.il = new IndexLinearizer(encoding.getNumFeatures(),
				encoding.getNumLabels());
	}

	public double[][] getScoreMatrix(InstanceSequence<V, E, L> sequence,
			int index, double[] w) {
		double[][] M = getLinearScoreMatrix(sequence, index, w);
		for (int i = 0; i < M.length; i++) {
			M[i] = ArrayUtil.exp(M[i]);
		}
		return M;
	}

	public double[] getVertexScores(InstanceSequence<V, E, L> sequence,
			int index, double[] w) {
		return ArrayUtil.exp(getLinearVertexScores(sequence, index, w));
	}

	public double[][] getLinearScoreMatrix(InstanceSequence<V, E, L> sequence,
			int index, double[] w) {
		int numLabels = encoding.getNumLabels();
		double[][] M = new double[numLabels][numLabels];
		Counter<F> vertexFeatures = vertexExtractor.extractFeatures(sequence
				.getVertexInstance(index));
		for (int vc = 0; vc < numLabels; vc++) {
			double vertexScore = dotProduct(vertexFeatures, vc, w);
			for (int vp = 0; vp < numLabels; vp++) {
				L previousLabel = encoding.getLabel(vp);
				Counter<F> edgeFeatures = edgeExtractor
						.extractFeatures(sequence.getEdgeInstance(index,
								previousLabel));
				double edgeScore = dotProduct(edgeFeatures, vc, w);
				M[vp][vc] = vertexScore + edgeScore;
			}
		}
		return M;
	}

	public double[] getLinearVertexScores(InstanceSequence<V, E, L> sequence,
			int index, double[] w) {
		int numLabels = encoding.getNumLabels();
		double[] s = new double[numLabels];
		Counter<F> vertexFeatures = vertexExtractor.extractFeatures(sequence
				.getVertexInstance(index));
		for (int vc = 0; vc < numLabels; vc++) {
			double vertexScore = dotProduct(vertexFeatures, vc, w);
			s[vc] = vertexScore;
		}
		return s;
	}

	private double dotProduct(Counter<F> features, int labelIndex, double[] w) {
		double val = 0.0;
		for (F feature : features.keySet()) {
			if (encoding.hasFeature(feature)) {
				int featureIndex = encoding.getFeatureIndex(feature);
				int linearIndex = il.getLinearIndex(featureIndex, labelIndex);
				val += features.getCount(feature) * w[linearIndex];
			}
		}
		return val;
	}
}
