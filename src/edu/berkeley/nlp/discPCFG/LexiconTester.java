/**
 * 
 */
package edu.berkeley.nlp.discPCFG;

import java.util.ArrayList;
import java.util.List;

import edu.berkeley.nlp.classify.LabeledInstance;
import edu.berkeley.nlp.classify.MaximumEntropyClassifier;
import edu.berkeley.nlp.math.DoubleArrays;

/**
 * @author adpauls
 * 
 */
public class LexiconTester {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// create datums
		int k = 2;
		double[] dummyWeights = DoubleArrays.constantArray(1.0 / k, k);
		LabeledInstance<WordInSentence, String> datum1 = new LabeledInstance<WordInSentence, String>(
				"NN", new WordInSentence("The cats died", 1));
		LabeledInstance<WordInSentence, String> datum2 = new LabeledInstance<WordInSentence, String>(
				"VB", new WordInSentence("I killing the cat", 1));
		LabeledInstance<WordInSentence, String> datum3 = new LabeledInstance<WordInSentence, String>(
				"NN", new WordInSentence("A cats killed me", 1));
		LabeledInstance<WordInSentence, String> datum4 = new LabeledInstance<WordInSentence, String>(
				"NN", new WordInSentence("The cats lived", 1));

		// create training set
		List<LabeledInstance<WordInSentence, String>> trainingData = new ArrayList<LabeledInstance<WordInSentence, String>>();
		trainingData.add(datum1);
		trainingData.add(datum2);
		trainingData.add(datum3);

		// create test set
		List<LabeledInstance<WordInSentence, String>> testData = new ArrayList<LabeledInstance<WordInSentence, String>>();
		testData.add(datum4);

		// build classifier
		LexiconFeatureExtractor featureExtractor = new LexiconFeatureExtractor();
		MaximumEntropyClassifier.Factory<WordInSentence, LexiconFeature, String> maximumEntropyClassifierFactory = new MaximumEntropyClassifier.Factory<WordInSentence, LexiconFeature, String>(
				1.0, 20, featureExtractor);
		MaximumEntropyClassifier<WordInSentence, LexiconFeature, String> maximumEntropyClassifier = (MaximumEntropyClassifier<WordInSentence, LexiconFeature, String>) maximumEntropyClassifierFactory
				.trainClassifier(trainingData);
		System.out.println("Probabilities on test instance: "
				+ maximumEntropyClassifier.getProbabilities(datum4.getInput()));

	}

}
