/**
 * 
 */
package edu.berkeley.nlp.speech.phonedecoder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import edu.berkeley.nlp.HMM.AcousticModel;
import edu.berkeley.nlp.HMM.Corpus;
import edu.berkeley.nlp.HMM.Phone;
import fig.basic.Indexer;
import edu.berkeley.nlp.util.Lists;
import fig.basic.Fmt;
import fig.basic.IOUtils;
import fig.basic.LogInfo;
import fig.basic.Option;
import fig.exec.Execution;

/**
 * A variational phone decoder for coarse-to-fine pruning experiments.
 * 
 * @author John DeNero
 */
public class Main implements Runnable {

	public class PhoneSequence {
		double[][] observations;

		List<Phone> reference;

		double[][] phonePosteriors;

		double[][][] subPhonePosteriors;

		public PhoneSequence(double[][] observations, List<Phone> reference) {
			super();
			this.observations = observations;
			this.reference = reference;
		}
	}

	// Options
	@Option(gloss = "Test corpus directory", required = true)
	public String corpusDir;

	@Option(gloss = "Max number of input sentences", required = true)
	public int numSentences = Integer.MAX_VALUE;

	@Option(gloss = "Suffix of MFCC files")
	public String mfccSuffix = ".mfcc";

	@Option(gloss = "Suffix of reference files")
	public String referenceSuffix = ".ph48";

	@Option(gloss = "Dimension of the MFCC vector")
	public int mfccDimension = 39;

	@Option(gloss = "Path to the phone model", required = true)
	public String hmmModelPath;

	@Option(gloss = "Test the interface to the word decoder")
	public boolean testChartInterface = false;

	@Option(gloss = "Posterior thresholds for pruning states (negative log space).")
	public List<Integer> pruneThresholds = Lists.newList(2, 3, 4, 5, 8, 10, 12,
			15, 16, 18, 20, 22, 24, 26, 28, 30, 35, 40, 50, 60, 70, 80, 90, 100, 150,
			250, 350, 450, 500, 1000);

	@Option(gloss = "Output too much information (rarely used)")
	public boolean verbose = false;

	@Option(gloss = "Use test set instead of dev set")
	public boolean useTestSet = false;

	@Option(gloss = "Come up with pruning matrix for subphones instead of phones")
	public boolean subphonePruning = false;

	@Option(gloss = "Scaling denominiator for displaying confusion matrix")
	public int confScalingFactor = 1; // 

	@Option(gloss = "Use confusions for posterior pruning")
	public boolean confusionPruning = false;

	private int[][] confusionMatrix;

	public static void main(String[] args) {
		Main main = new Main();
		Execution.init(args, main);
		main.run();
		Execution.finish();
	}

	public void run() {
		List<PhoneSequence> sequences = readObservations();
		AcousticModel model = new AcousticModel.SubphoneHMMWrapper(hmmModelPath);
		if (subphonePruning) {
			evaluateSubphonePruning(sequences, model);
		} else {
			evaluatePhonePruning(sequences, model);
		}
	}

	public void evaluateSubphonePruning(List<PhoneSequence> sequences,
			AcousticModel model) {

		PhonePosteriorChart.ChartFromPhoneDecoder chart = new PhonePosteriorChart.ChartFromPhoneDecoder(
				new ExhaustiveDecoder.Factory());
		chart.setModel(model);
		Indexer<Phone> indexer = model.getPhoneIndexer();

		int obsNumber = 0;
		int n = sequences.size();
		int numFrames = 0;
		int[] pruneErrors = new int[pruneThresholds.size()];
		int[] pruned = new int[pruneThresholds.size()];
		int numPhones = model.getPhoneIndexer().size();
		model.getTotalStates();
		confusionMatrix = new int[numPhones][numPhones];

		LogInfo.track("Decoding for phone classification accuracy");
		decodeAll(new VariationalDecoder(model), sequences);
		double phoneClassAccuracy = getPhoneAccuracy(model, sequences);
		LogInfo.logss("Phone Class Acc.: %.3f\n", phoneClassAccuracy);
		LogInfo.end_track();

		LogInfo.track("Constructing pruners");
		ThresholdPruner confPruner = new ThresholdPruner.Confusions(
				confusionMatrix, .045);
		ThresholdPruner threshPruner = new ThresholdPruner.SimplePruner();
		confPruner.writeObject();
		LogInfo.end_track();
		
		LogInfo.track("Decoding sequences for pruning tests");
		for (PhoneSequence seq : sequences) {
			double[][] obs = seq.observations;
			numFrames += obs.length - 2;
			LogInfo.logs("Decoding %d/%d (length %d)...", ++obsNumber, n, obs.length);
			chart.setInput(obs);
			double[][][] posteriors = chart.allocateChart();
			chart.fillPosteriors(posteriors);

			// Collect pruning errors
			ThresholdPruner pruner = confusionPruning ? confPruner : threshPruner;
			for (int i = 0; i < pruneErrors.length; i++) {
				double threshold = Math.exp(-1.0 * pruneThresholds.get(i));
				pruner.setThreshold(threshold);
				boolean[][][] keepers = pruner.prune(posteriors, null);

				pruneErrors[i] += tallyErrors(seq.reference, keepers, indexer);
				pruned[i] += tallyPrune(keepers);
			}
		}
		LogInfo.end_track();

		// Output pruning error rates
		for (int i = 0; i < pruneErrors.length; i++) {
			double threshold = Math.exp(-1.0 * pruneThresholds.get(i));
			Execution.putOutput("threshold" + i, threshold);
			double errorRate = (double) pruneErrors[i] / numFrames;
			double pruneRate = (double) pruned[i] / numFrames
					/ model.getTotalStates();
			Execution.putOutput("errorRate" + 1, errorRate);
			LogInfo.logss(
					"Error rate %8.5f%% at threshold %.2e pruning %5.2f%% cells",
					errorRate * 100, threshold, pruneRate * 100);
		}

		outputConfusionMatrix(model.getPhoneIndexer());

		if (testChartInterface)
			testPhonePosteriorChartInterface(sequences.get(0).observations, model);
	}

	private double getPhoneAccuracy(AcousticModel model, List<PhoneSequence> seqs) {
		int total = 0, numCorrect = 0;
		for (PhoneSequence seq : seqs) {
			double[][] posteriors = seq.phonePosteriors;

			int numPhones = model.getPhoneIndexer().size();
			List<Phone> truePhones = seq.reference;
			int curPhone = -1;
			int startFrame = -1;
			int endFrame = -1;
			for (int i = 1; i < truePhones.size() - 1; ++i) {
				int framePhone = model.getPhoneIndexer().indexOf(truePhones.get(i));
				if (framePhone != curPhone) {
					if (curPhone != -1) {
						endFrame = i;
						int bestPhone = -1;
						double bestPhoneLogProb = Double.NEGATIVE_INFINITY;
						for (int phone = 0; phone < numPhones; ++phone) {
							double phoneLogProb = 0.0;
							for (int frame = startFrame; frame < endFrame; ++frame) {
								phoneLogProb += Math.log(posteriors[frame][phone]);
								assert phoneLogProb <= 1.0e-8;
								if (phoneLogProb == Double.NEGATIVE_INFINITY)
									break;
							}
							if (phoneLogProb > bestPhoneLogProb) {
								bestPhoneLogProb = phoneLogProb;
								bestPhone = phone;
							}
						}
						if (bestPhone == curPhone) {
							numCorrect++;
						}
						confusionMatrix[curPhone][bestPhone]++;

						total++;
					}
					curPhone = framePhone;
					startFrame = i;
				}
			}
		}
		double acc = (double) numCorrect / (double) total;
		Execution.putOutput("PhoneAccuracy", acc);
		return acc;
	}

	/**
	 * @param phoneIndexer
	 */
	private void outputConfusionMatrix(Indexer<Phone> phoneIndexer) {
		int size = phoneIndexer.size();
		String[] labels = new String[size];
		int maxLength = 0;
		for (int i = 0; i < size; i++) {
			labels[i] = phoneIndexer.get(i).getLabel();
			maxLength = Math.max(maxLength, labels[i].length());
		}

		LogInfo.logss("Confusion Matrix:");
		LogInfo.logss(formatMatrix(labels, maxLength, size, "|"));

		String confFile = Execution.getFile("confusionMatrix.csv");
		PrintWriter writer = IOUtils.openOutHard(confFile);
		writer.write(formatMatrix(labels, maxLength, size, ","));
		writer.close();
	}

	/**
	 * @param labels
	 * @param max
	 * @param size
	 * @param i
	 * @return
	 */
	private String formatMatrix(String[] labels, int max, int size, String delimit) {
		int width = 3;

		StringWriter matrix = new StringWriter();
		PrintWriter out = new PrintWriter(matrix);
		for (int i = 0; i < max; i++) {
			out.printf("\n%" + max + "s  ", "");
			for (int j = 0; j < size; j++) {
				int padding = max - labels[j].length();
				char c = (i >= padding) ? labels[j].charAt(i - padding) : ' ';
				out.printf("   %c  ", c);
			}
		}
		out.append("\n");

		for (int i = 0; i < size; i++) {
			out.printf("\n%" + max + "s %s", labels[i], delimit);
			for (int j = 0; j < size; j++) {
				int scaled = confusionMatrix[i][j] / confScalingFactor;
				out.printf(" %" + width + "d %s", scaled, delimit);
			}
		}
		return matrix.toString();
	}

	/**
	 * @param keepers
	 * @param theshold
	 * @return
	 */
	private int tallyPrune(boolean[][][] keepers) {
		int pruned = 0;
		for (int i = 1; i + 1 < keepers.length; i++) { // Only count non-start
			for (int j = 0; j < keepers[i].length; j++) {
				for (int k = 0; k < keepers[i][j].length; k++) {
					if (!keepers[i][j][k])
						pruned += 1;
				}
			}
		}
		return pruned;
	}

	private int tallyErrors(List<Phone> goldSequence, boolean[][][] keepers,
			Indexer<Phone> indexer) {
		int errors = 0;

		for (int i = 1; i + 1 < goldSequence.size(); i++) {
			int gold = indexer.indexOf(goldSequence.get(i));
			boolean passed = false;
			for (int j = 0; j < keepers[i][gold].length && !passed; j++) {
				passed = passed || keepers[i][gold][j];
			}
			errors += (passed) ? 0 : 1;
		}

		return errors;
	}

	/**
	 * @param m
	 * @param ds
	 * 
	 */
	private void testPhonePosteriorChartInterface(double[][] obs, AcousticModel m) {
		LogInfo.track("Testing posterior chart object");
		// Test PhonePosteriorChart interface
		int maxSubstates = m.getMaxNumberOfSubstates();
		int numPhones = m.getPhoneIndexer().size();
		// for (int p = 0; p < numPhones; p++) {
		// maxSubstates = Math.max(maxSubstates, m.getNumStates(p));
		// }
		double[][][] posteriors = new double[obs.length][numPhones][maxSubstates];

		SubphoneDecoder.Factory f = new ExhaustiveDecoder.Factory();
		PhonePosteriorChart c = new PhonePosteriorChart.ChartFromPhoneDecoder(f);
		c.setModel(m);
		c.setInput(obs);
		c.fillPosteriors(posteriors);
		for (int i = 0; i < obs.length; i++) {
			LogInfo.logss("Sample: " + Fmt.D(posteriors[i][3]));
		}
		LogInfo.end_track();
	}

	private void evaluatePhonePruning(List<PhoneSequence> sequences,
			AcousticModel model) {
		VariationalDecoder decoder = new VariationalDecoder(model);
		Indexer<Phone> indexer = model.getPhoneIndexer();

		int obsNumber = 0;
		int n = sequences.size();
		int numFrames = 0;
		int[] pruneErrors = new int[pruneThresholds.size()];
		int[] pruned = new int[pruneThresholds.size()];
		int numPhones = model.getPhoneIndexer().size();
		model.getTotalStates();
		confusionMatrix = new int[numPhones][numPhones];

		decodeAll(decoder, sequences);

		double phoneClassAccuracy = getPhoneAccuracy(model, sequences);
		LogInfo.logss("Phone Class Acc.: %.3f\n", phoneClassAccuracy);

		for (PhoneSequence seq : sequences) {
			numFrames += seq.observations.length - 2;
			double[][] posteriors = seq.phonePosteriors;

			// Collect pruning errors
			for (int i = 0; i < pruneErrors.length; i++) {
				double threshold = Math.exp(-1.0 * pruneThresholds.get(i));
				boolean[][] keepers = prunePhones(posteriors, threshold);

				pruneErrors[i] += tallyErrors(seq.reference, keepers, indexer);
				pruned[i] += tallyPrune(keepers);
			}
		}
		LogInfo.end_track();

		// Output pruning error rates
		for (int i = 0; i < pruneErrors.length; i++) {
			double threshold = Math.exp(-1.0 * pruneThresholds.get(i));
			Execution.putOutput("threshold" + i, threshold);
			double errorRate = (double) pruneErrors[i] / numFrames;
			double pruneRate = (double) pruned[i] / numFrames
					/ model.getTotalStates();
			Execution.putOutput("errorRate" + 1, errorRate);
			LogInfo.logss(
					"Error rate %8.5f%% at threshold %.2e pruning %5.2f%% cells",
					errorRate * 100, threshold, pruneRate * 100);
		}

		outputConfusionMatrix(model.getPhoneIndexer());

	}

	/**
	 * @param keepers
	 * @return
	 */
	private int tallyPrune(boolean[][] keepers) {
		// TODO Auto-generated method stub
		throw new RuntimeException("Unimplemented");
	}

	/**
	 * @param reference
	 * @param keepers
	 * @param indexer
	 * @return
	 */
	private int tallyErrors(List<Phone> reference, boolean[][] keepers,
			Indexer<Phone> indexer) {
		// TODO Auto-generated method stub
		throw new RuntimeException("Unimplemented");
	}

	/**
	 * @param posteriors
	 * @param threshold
	 * @return
	 */
	private boolean[][] prunePhones(double[][] posteriors, double threshold) {
		boolean[][] keepers = new boolean[posteriors.length][];
		for (int i = 0; i < posteriors.length; i++) {
			double[] post = posteriors[i];
			keepers[i] = new boolean[post.length];
			for (int j = 0; j < post.length; j++) {
				if (post[j] > threshold)
					keepers[i][j] = true;
			}
		}
		return keepers;
	}

	private void decodeAll(VariationalDecoder decoder,
			List<PhoneSequence> sequences) {
		LogInfo.track("Decoding test sentences");
		int n = 1;
		for (PhoneSequence seq : sequences) {
			LogInfo.logs("Decoding %d/%d", n++, sequences.size());
			seq.phonePosteriors = decoder.getPhonePosteriors(seq.observations);
		}
		LogInfo.end_track();
	}

	private List<PhoneSequence> readObservations() {

		LogInfo.track("Reading observations and phone sequences");
		Corpus corpus = new Corpus(corpusDir, Corpus.CorpusType.TIMIT, false, true,
				39, false);
		List<List<Phone>> goldPhoneSequences = useTestSet ? corpus
				.getPhoneSequencesTest() : corpus.getPhoneSequencesDev();
		List<double[][]> observations = useTestSet ? corpus.getObsListsTest()
				: corpus.getObsListsDev();
		Iterator<List<Phone>> refIterator = goldPhoneSequences.iterator();
		Iterator<double[][]> featureIterator = observations.iterator();

		List<PhoneSequence> data = new ArrayList<PhoneSequence>();

		int sentenceNumber = 0;
		while (featureIterator.hasNext() && refIterator.hasNext()
				&& sentenceNumber++ < numSentences) {
			data.add(new PhoneSequence(featureIterator.next(), refIterator.next()));
		}
		LogInfo.end_track();
		return data;
	}

}
