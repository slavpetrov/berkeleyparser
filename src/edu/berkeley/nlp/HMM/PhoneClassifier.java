/**
 * 
 */
package edu.berkeley.nlp.HMM;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import edu.berkeley.nlp.PCFGLA.Option;
import edu.berkeley.nlp.PCFGLA.OptionParser;
import edu.berkeley.nlp.util.ArrayUtil;

/**
 * @author petrov
 * could probably be done more elegantly but this will need to suffice
 */
public class PhoneClassifier {
	protected final double SCALE = Math.exp(100);

	PhoneIndexer phoneIndexer;
	SubphoneHMM hmm;
	
	protected double[] prior;
	protected double[][][] alphas;
	protected double[] alphasScale;
	protected int T;
	protected int myLength;
	protected double[][] obs;
	
	PhoneClassifier(SubphoneHMM hmm, PhoneIndexer phoneIndexer, double[] prior){
		this.hmm = hmm;
		this.phoneIndexer = phoneIndexer;
		this.prior = prior;
		myLength = -1;
	}
	
	public Phone classify(double[][] obs){
		init(obs);
		calculateAlphas();
		
		// find the maximum
		int bestPhone = -1;
		double bestLikelihood = 0;
		for (int phone=2; phone<hmm.numPhones; phone++) {
			if (alphas[T-1][phone][0]>bestLikelihood){
				bestPhone = phone;
				bestLikelihood = alphas[T-1][phone][0];
			}
		}
		
		return phoneIndexer.get(bestPhone);
	}
	
	/**
	 * computes the forward-scores b(i,t) for states at times t with emission O
	 * under the current model
	 * 
	 * @param o
	 *          the emission O
	 * @return forward scores b(i,t)
	 */
	protected void calculateAlphas() {

		// Basis
		for (int toPhone=0; toPhone<hmm.numPhones; toPhone++) {
			for (int toSubstate = 0; toSubstate < hmm.numSubstatesPerState[toPhone]; ++toSubstate) {
				alphas[0][toPhone][toSubstate] = prior[toPhone]; // should be set to the prior probability of that phone
			}
		}
		alphasScale[0] = 0.0;
		
		// Induction
		for (int t = 1; t < T-1; t++) {
			final double[][] alphat = alphas[t];
			double max = 0;

			for (int toPhone=2; toPhone<hmm.numPhones; toPhone++) {

				for (int toSubstate = 0; toSubstate < hmm.numSubstatesPerState[toPhone]; ++toSubstate) {
					double alpha = 0.0;
					int fromPhone = toPhone; // cannot transition

					for (int fromSubstate = 0; fromSubstate < hmm.numSubstatesPerState[fromPhone]; ++fromSubstate) {
						
						double previousAlpha = alphas[t - 1][fromPhone][fromSubstate];
//							if (previousAlpha==0) continue;
						double leaveStateProb = hmm.c[toPhone][fromPhone][fromSubstate];
						alpha += previousAlpha
								* hmm.a[toSubstate][toPhone][fromPhone][fromSubstate]
								* leaveStateProb;
					}
					if (t < T && alpha>0){
						double obsLik = hmm.b[toPhone][toSubstate].evalPdf(obs[t-1]);
						alpha *= obsLik;
					}
					if (alpha > max)
						max = alpha;
				
					alphat[toPhone][toSubstate] = alpha;
				}
			}
			assert max > 0 : "No alpha path for for time " + t;
			
			int logScale = 0;
			double scale = 1.0;
			while (max > SCALE) {
				max /= SCALE;
				scale *= SCALE;
				logScale += 1;
			}
			while (max > 0.0 && max < 1.0 / SCALE) {
				max *= SCALE;
				scale /= SCALE;
				logScale -= 1;
			}
			if (logScale != 0) {
				for (int toPhone = 0; toPhone < hmm.numPhones; ++toPhone) {
					for (int toSubstate = 0; toSubstate < hmm.numSubstatesPerState[toPhone]; ++toSubstate) {
						alphat[toPhone][toSubstate] /= scale;
					}
				}
			}
			alphasScale[t] = alphasScale[t - 1] + logScale;
		}

		// do the final state where we sum up everything
		for (int toPhone=2; toPhone<hmm.numPhones; toPhone++) {
			double alpha = 0.0;
			int fromPhone = toPhone; // cannot transition

			for (int fromSubstate = 0; fromSubstate < hmm.numSubstatesPerState[fromPhone]; ++fromSubstate) {
				
				double previousAlpha = alphas[T - 2][fromPhone][fromSubstate];
//				double leaveStateProb = hmm.c[toPhone][fromPhone][fromSubstate];
				alpha += previousAlpha;
//						* hmm.a[toSubstate][toPhone][fromPhone][fromSubstate]
//						* leaveStateProb;
			}
			alphas[T-1][toPhone][0] = alpha;
		}

	
	}

	
	private void init(double[][] obs){
		this.obs = obs;
		this.T = obs.length+2;
		if (T > myLength) {
			alphas = new double[T][hmm.numPhones][hmm.maxNumSubstates];
			alphasScale = new double[T];
			myLength = T;
		} else {
			// experiment!!! we dont really need to fill them with 0s since we overwrite the relevant entries
//			ArrayUtil.fill(alphas, T, 0);
//			Arrays.fill(alphasScale, 0);
		}

	}

	
	public static class Options {
		@Option(name = "-corpus", usage = "Which corpus to use: TIMIT, CHE, or DUMMY")
		public Corpus.CorpusType corpusType = Corpus.CorpusType.TIMIT;

		@Option(name = "-path", usage = "Path to corpus")
		public File corpusPath = null;

		@Option(name = "-in", usage = "Load this model and test it")
		public String inName = null;
		
		@Option(name = "-transitionExponent", usage = "Take the transition probabilities to this power in viterbi")
		public double transitionExponent = 1.0;
		
		@Option(name = "-phoneClassification", usage = "Do phone classification (rather than recognition)")
		public boolean phoneClassification = false;

	}

	public static void main(String[] argv) {

		OptionParser optionParser = new OptionParser(PhoneClassifier.Options.class);
		PhoneClassifier.Options opts = (PhoneClassifier.Options) optionParser.parse(argv,true, false);
		System.err.println("Calling with " + optionParser.getPassedInOptions());
		
		Corpus corpus = new Corpus(opts.corpusPath.getPath(), opts.corpusType, opts.phoneClassification, false, 39, false);
		System.out.println("Loading a model from " + opts.inName);
		SubphoneHMM hmm = SubphoneHMM.Load(opts.inName);
		PhoneIndexer phoneIndexer = (PhoneIndexer)hmm.phoneIndexer;

		System.out.println("Using an exponent of " + opts.transitionExponent
				+ " to boost the transition probabilities.");
		hmm.boostTransitionProbabilities(opts.transitionExponent, 1.0);

		List<List<Phone>> phoneSequencesAsObjects = corpus.getPhoneSequencesTrain();
		phoneSequencesAsObjects = PhoneIndexer.getCollapsedPhoneLists(phoneSequencesAsObjects, true, 1);
		List<int[]> phoneSequences = phoneIndexer.indexSequences(phoneSequencesAsObjects);
		double[] prior = new double[phoneIndexer.size()];
		double total = 0;
		for (int seq = 0; seq < phoneSequences.size(); ++seq) {
			int[] phoneSeq = phoneSequences.get(seq);
			for (int i=1; i<phoneSeq.length-1; i++){
				prior[phoneSeq[i]]++;
				total++;
			}
		}
		ArrayUtil.multiplyInPlace(prior, 1.0/total);
		System.out.println(Arrays.toString(prior));
		
		phoneSequencesAsObjects = corpus.getPhoneSequencesDev();
		phoneSequences = phoneIndexer.indexSequences(phoneSequencesAsObjects);
		List<double[][]> obsList = corpus.getObsListsDev();
		
		PhoneClassifier phoneClassifier = new PhoneClassifier(hmm,phoneIndexer,prior);
		double totalPhones = 0, correct = 0;
		for (int seq = 0; seq < phoneSequences.size(); ++seq) {
			System.out.print(".");
			int[] phoneSeq = phoneSequences.get(seq);
			double[][] obsSeq = obsList.get(seq);
			int lastPhone = phoneSeq[1], curPhone = -1;
			List<double[]> tmp = new LinkedList<double[]>();
			for (int i=1; i<phoneSeq.length-1; i++){
				curPhone = phoneSeq[i];
				if (curPhone!=lastPhone){ // segment is over
					double[][] obs = (double[][]) tmp.toArray(new double[0][0]);
					Phone guessedPhone = AccuracyCalc.mapDownPhone(phoneClassifier.classify(obs));
					Phone goldPhone = AccuracyCalc.mapDownPhone(phoneIndexer.get(lastPhone));
//					System.out.print(goldPhone+" "+guessedPhone);
					if (AccuracyCalc.staticEqual39(goldPhone, guessedPhone, phoneIndexer)) correct++;
//					System.out.print(correct+"\n");
					totalPhones++;
					tmp = new LinkedList<double[]>();
				}
				tmp.add(obsSeq[i]);
				lastPhone = curPhone;
			}
		}
		System.out.print("\nPhone classifier: "+(int)correct+"/"+(int)totalPhones+" = ");
		correct = ((int)(correct/totalPhones*1000+0.5))/10.0;
		System.out.println(correct+"% correct.");

	}
	
	
	
	
	
}
