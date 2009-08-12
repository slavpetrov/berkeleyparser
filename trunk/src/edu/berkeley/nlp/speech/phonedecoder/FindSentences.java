/**
 * 
 */
package edu.berkeley.nlp.speech.phonedecoder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import edu.berkeley.nlp.HMM.AcousticModel;
import edu.berkeley.nlp.HMM.Corpus;
import edu.berkeley.nlp.HMM.Phone;
import edu.berkeley.nlp.HMM.PhoneIndexer;
import edu.berkeley.nlp.speech.decoder.dict.CMUDict;
import edu.berkeley.nlp.speech.decoder.dict.PronounciationDictionary;
import edu.berkeley.nlp.util.Lists;
import fig.basic.IOUtils;
import fig.basic.LogInfo;
import fig.basic.Option;
import fig.exec.Execution;

/**
 * A variational phone decoder for coarse-to-fine pruning experiments.
 * 
 * @author John DeNero
 */
public class FindSentences implements Runnable {

	/**
	 * 
	 * @author John DeNero
	 */
	public class PhoneSequence {
		double[][] observations;

		List<Phone> reference;

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

	@Option(gloss = "Scaling denominiator for displaying confusion matrix")
	public int confScalingFactor = 1; // 

	@Option(gloss = "Load posterior editor from previous execution")
	public String confusionExecution = null;

	@Option(gloss = "File with all sentences")
	public String sentenceFile;

	@Option(gloss = "Path to pronounciation dictionary")
	public String pDictionaryPath;

	public static void main(String[] args) {
		FindSentences main = new FindSentences();
		Execution.init(args, main);
		main.run();
		Execution.finish();
	}

	public void run() {
		List<PhoneSequence> sequences = readObservations();
		List<String> sentences = IOUtils.readLinesHard(sentenceFile);
		PhoneIndexer indexer = new PhoneIndexer();
		for (PhoneSequence seq : sequences) {
			for (Phone p : seq.reference) {
				if (!indexer.contains(p))
					indexer.add(p);
			}
		}
		PronounciationDictionary pDict = new CMUDict(pDictionaryPath, indexer);
		SentenceFinder sentFinder = new SentenceFinder(pDict, sentences);
		for (PhoneSequence phoneSequence : sequences) {
			String sent = sentFinder.getSentence(phoneSequence.reference);
			if (sent == null) {
				// LogInfo.error("No sentence for: " + phoneSequence.reference);
			} else {
				LogInfo.logss("Found: " + sent);
				 LogInfo.logss("Seqnc: " + sentFinder.collapse(phoneSequence.reference));
			}
		}
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
