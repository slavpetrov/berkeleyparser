/**
 * 
 */
package edu.berkeley.nlp.HMM;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.berkeley.nlp.io.PerlIOFuncs;
import edu.berkeley.nlp.speech.features.CepstralFeatureExtractor;
import fig.basic.Pair;
import fig.basic.IOUtils;
import fig.basic.LogInfo;

/**
 * @author petrov
 * 
 */
public class Corpus {

	public enum CorpusType {
		TIMIT, CHE, DUMMY;
	}

	String[] phones48 = { "pau", "epi", "h# ", "iy ", "ih ", "eh ", "ey ", "ae ",
			"aa ", "aw ", "ay ", "ah ", "ao ", "oy ", "ow ", "uh ", "uw ", "ux ",
			"er ", "ax ", "ix ", "axr", "b  ", "d  ", "g  ", "p  ", "t  ", "k  ",
			"dx ", "jh ", "ch ", "s  ", "sh ", "z  ", "zh ", "f  ", "th ", "v  ",
			"dh ", "m  ", "n  ", "ng ", "em ", "en ", "eng", "nx ", "l  ", "r  " };

	String[] phones39 = { "pau", "pau", "epi", "h#", "iy", "ih", "eh", "aw",
			"ey", "ae", "aa", "aw", "ay", "ah", "ao", "oy", "ow", "aa", "h#", "uh",
			"uw", "ux", "er", "ax", "ix", "axr", "b", "d", "g", "p", "t", "p", "k",
			"dx", "jh", "ch", "s", "sh", "z", "sh", "dh", "zh", "f", "th", "v", "dh",
			"pau", "pau" };

	private List<List<Phone>> phoneSequencesTrain;

	private List<double[][]> obsListsTrain;

	private List<List<String>> wordSequencesTrain;

	private List<List<Phone>> phoneSequencesDev;

	private List<double[][]> obsListsDev;

	private List<List<String>> wordSequencesDev;

	private List<List<Phone>> phoneSequencesTest;

	private List<double[][]> obsListsTest;

	private List<List<String>> wordSequencesTest;

	private int gaussianDim;

	private PhoneIndexer phoneIndexer;

	public int[] mapping = { 1, 1, 2, 3, 4, 5, 6, 10, 7, 8, 9, 10, 11, 12, 13,
			14, 15, 9, 3, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 26, 28, 29,
			30, 31, 32, 33, 34, 33, 39, 35, 36, 37, 38, 39, 1, 1 };

	String path;

	/**
	 * Each sequence starts and ends with these symbols the corresponding
	 * observations are empty
	 */
	public static final int START_STATE = 0;

	public static final int END_STATE = 1;

	public static final Phone START_PHONE = new Phone("*START*");

	public static final Phone END_PHONE = new Phone("*END*");

	private boolean forClassification; // creates a small hmm for phone

	public Corpus(String path, CorpusType corpusType, boolean forClassification,
			boolean onlyTest, int gaussianDim, boolean useWav) {
		this.forClassification = forClassification;
		phoneIndexer = new PhoneIndexer();
		phoneIndexer.add(START_PHONE);
		phoneIndexer.add(END_PHONE);
		this.gaussianDim = gaussianDim;
		// for (int i=0; i<48; i++){
		// phoneIndexer.add(new Phone(phones48[i]));
		// }

		this.path = path;
		switch (corpusType) {
		case DUMMY:
			loadDummyExamples();
			break;
		case TIMIT:
			loadTimitData(onlyTest, useWav);
			break;
		case CHE:
			loadCheData();
			break;
		default:
			throw new RuntimeException("Unknown corpus type " + corpusType);
		}

	}

	/**
	 * 
	 */
	private void loadTimitData(boolean onlyTest, boolean useWav) {

		boolean isTraining = true;
		Pair<List<List<String>>, Pair<List<double[][]>, List<List<Phone>>>> pair = null;

		if (!onlyTest) {
			pair = readTimitData("train", isTraining, useWav);
			obsListsTrain = pair.getSecond().getFirst();
			phoneSequencesTrain = pair.getSecond().getSecond();
			wordSequencesTrain = pair.getFirst();
		}

		pair = readTimitData("dev", !isTraining, useWav);
		obsListsDev = pair.getSecond().getFirst();
		phoneSequencesDev = pair.getSecond().getSecond();
		wordSequencesDev = pair.getFirst();

		pair = readTimitData("test", !isTraining, useWav);
		obsListsTest = pair.getSecond().getFirst();
		phoneSequencesTest = pair.getSecond().getSecond();
		wordSequencesTest = pair.getFirst();
	}

	private Pair<List<List<String>>, Pair<List<double[][]>, List<List<Phone>>>> readTimitData(
			String subpath, boolean train, final boolean useWav) {

		FilenameFilter phoneFileFilter = null;

		phoneFileFilter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.endsWith(useWav ? "phn" : "pho61");
			}
		};

		SortedSet<File> phoneFiles = new TreeSet<File>(getFilesUnder(path + "/"
				+ subpath, phoneFileFilter));
		int nFiles = phoneFiles.size();
		LogInfo.logs("There are " + nFiles + " mfcc files and " + phoneFiles.size()
				+ " label files.");

		List<double[][]> obsLists = new ArrayList<double[][]>(nFiles);
		List<List<Phone>> phoneSequences = new ArrayList<List<Phone>>(nFiles);
		List<List<String>> wordLists = useWav ? new ArrayList<List<String>>()
				: null;

		if (phoneFiles.size() == 0) {
			throw new RuntimeException(
					"Could not find any TIMIT phone files on path " + subpath);
		}
		for (File labelFile : phoneFiles) {
			List<Phone> phoneLabels = new ArrayList<Phone>();
			try {
				BufferedReader input = new BufferedReader(new FileReader(labelFile));

				phoneLabels.add(START_PHONE);
				if (useWav)
					readPhnFile(phoneLabels, input);
				else
					readPhoFile(phoneLabels, input);
				phoneLabels.add(END_PHONE);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}

			String base = labelFile.getAbsolutePath();
			base = base.substring(0, base.lastIndexOf("."));
			double[][] observations = null;
			if (useWav) {
				observations = readWavFile(phoneLabels, base);
				readTimitTranscriptFile(wordLists, base);
			} else {
				File dataFile = new File(base + ".mfcc");

				observations = readMfccFile(phoneLabels, dataFile);
			}

			if (forClassification) { // break up the utterances into individual
																// phones
				Phone lastPhone = null, curPhone = null;
				List<double[]> tmpObs = new LinkedList<double[]>();
				tmpObs.add(null);
				List<Phone> tmpLabels = new LinkedList<Phone>();
				tmpLabels.add(START_PHONE);
				for (int i = 1; i < phoneLabels.size(); i++) {
					curPhone = PhoneIndexer.mapDown(phoneLabels.get(i));
					if (lastPhone == null)
						lastPhone = curPhone;
					if (!curPhone.equals(lastPhone)) { // segment finished
						tmpObs.add(null);
						tmpLabels.add(END_PHONE);
						double[][] obs = (double[][]) tmpObs.toArray(new double[0][0]);

						phoneSequences.add(tmpLabels);
						obsLists.add(obs);

						tmpObs = new LinkedList<double[]>();
						tmpObs.add(null);
						tmpLabels = new LinkedList<Phone>();
						tmpLabels.add(START_PHONE);
					}
					tmpObs.add(observations[i]);
					tmpLabels.add(curPhone);
					lastPhone = curPhone;
				}
			} else {
				phoneSequences.add(phoneLabels);
				obsLists.add(observations);
			}
		}
		return new Pair<List<List<String>>, Pair<List<double[][]>, List<List<Phone>>>>(
				wordLists, new Pair<List<double[][]>, List<List<Phone>>>(obsLists,
						phoneSequences));
	}

	private void readTimitTranscriptFile(List<List<String>> wordLists, String base) {
		List<String> transcriptLines = IOUtils.readLinesHard(base + ".txt");
		String line = transcriptLines.get(0);
		line = line.substring(line.indexOf(" "));
		line = line.substring(line.indexOf(" "));
		line = line.trim();
		wordLists.add(Arrays.asList(line.split(" ")));
	}

	private double[][] readWavFile(List<Phone> phoneLabels, String base) {
		CepstralFeatureExtractor cfe = new CepstralFeatureExtractor();
		double[][] observations2 = null;
		try {
			observations2 = cfe.getFeatures(base + ".ms.wav");
// assert observations2.length == observations.length - 2;
		
		} catch (Exception e) {
		throw new RuntimeException(e);
		}
		assert observations2 != null;
//		LogInfo.logss("Have " + phoneLabels.size() + " and " + observations2.length);
		while (phoneLabels.size() > observations2.length + 2)
		{
			phoneLabels.remove(phoneLabels.size() - 2);
		}
		while (phoneLabels.size() < observations2.length + 2)
		{
			
			phoneLabels.add(phoneLabels.size() - 2, new Phone("#h"));
		}
		assert phoneLabels.size() == observations2.length + 2;
		
		double[][] observations = new double[phoneLabels.size()][gaussianDim];
		for (int i = 0; i < observations2.length; ++i)
		{
			observations[i+1] = observations2[i];
		}
	
		observations[0] = null; 										// these are the start and end
																								// tokens
		observations[phoneLabels.size()-1] = null;  // just making sure there are no
																								// bugs elsewhere
// assert observations.length == phoneLabels.size();
		return observations;
	}

	private void readPhoFile(List<Phone> phoneLabels, BufferedReader input)
			throws IOException {
		String line = null;
		
		while ((line = input.readLine()) != null) {
			line = line.trim();
			Phone phone = new Phone(line);
			phoneIndexer.add(phone);
			phoneLabels.add(phone);
		}
	}

	private void readPhnFile(List<Phone> phoneLabels, BufferedReader input)
			throws IOException {
		String line;
		int last = -1;
		int first = -1;
		int sum = 0;
		while ((line = input.readLine()) != null) {
			line = line.trim();
			String[] data = line.split(" ");
			int start = Integer.parseInt(data[0]);
			int end = Integer.parseInt(data[1]);
			if (first < 0 && start != 0) start = first = 0;
			String p = data[2].trim();
			if (p.equals("ax-h"))
				p = "ax";
			Phone phone = new Phone(p);
			phoneIndexer.add(phone);
			final long repetitions = (Math.round((double) end / 160.0) - Math
								.round((double) start / 160.0));
//			LogInfo.logss("Doing " + repetitions + " reps for " + start + " " + end);
			last = end;
			sum += repetitions;
			for (int i = 0; i < repetitions; ++i)
			{
				phoneLabels.add(phone);
				
			}
		
		}
//		LogInfo.logss("Last is " + (double)last / 400 + " and sum is " + sum);

	}

	private double[][] readMfccFile(List<Phone> phoneLabels, File dataFile) {
		int nElements = 0;
		double[][] observations = new double[phoneLabels.size()][gaussianDim];
		observations[0] = null; // these are the start and end tokens
		observations[phoneLabels.size() - 1] = null; // just making sure there are
																									// no bugs elsewhere

		int mfccDim = 0, frame = 1;
		final int dataDim = 39;
		try {
			DataInputStream inData = new DataInputStream(
					new FileInputStream(dataFile));
			nElements = inData.available() / 8; // since each double takes 8 bytes

			while (nElements > 0) {
				double x = inData.readDouble();
				if (mfccDim < gaussianDim)
					observations[frame][mfccDim] = x;
				mfccDim++;
				if (mfccDim == dataDim) {
					mfccDim = 0;
					frame++;
				}
				nElements--;
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return observations;
	}

	/**
	 * 
	 */
	private void loadDummyExamples() {
		int exampleNumber = 0;
		List<Phone> phones = null;
		double[][] observations = null;
		phoneIndexer = new PhoneIndexer();
		phoneIndexer.add(START_PHONE);
		phoneIndexer.add(END_PHONE);

		String[] phoneString = null;
		String[] obsString = null;
		Random r = new Random();
		r.setSeed(2L);

		switch (exampleNumber) {
		case 0:
			phoneString = new String[2];
			obsString = new String[2];
			phoneString[0] = "a,a,a,b,b,b,b,a,a";
			obsString[0] = "1.0,1.0,2.0,3.0,3.0,3.0,4.0,1.0,11.0";
			phoneString[1] = "a,b,b,b,b,a,a";
			obsString[1] = "1.0,2.0,2.0,2.0,4.0,1.0,1.0";
			gaussianDim = 1;
			// if (true){
			// observations.add(new double[] { 1.0, r.nextDouble() });
			// observations.add(new double[] { 1.0, r.nextDouble() });
			// observations.add(new double[] { 2.0, r.nextDouble() });
			// observations.add(new double[] { 3.0, r.nextDouble() });
			// observations.add(new double[] { 3.0, r.nextDouble() });
			// observations.add(new double[] { 3.0, r.nextDouble() });
			// observations.add(new double[] { 4.0, r.nextDouble() });
			// observations.add(new double[] { 1.0, r.nextDouble() });
			// observations.add(new double[] { 1.0, r.nextDouble() });
			// gaussianDim = 2;
			// }
			// else{
			// observations.add(new double[] { 1.0});
			// observations.add(new double[] { 1.0});
			// observations.add(new double[] { 1.1});
			// observations.add(new double[] { 3.0});
			// observations.add(new double[] { 3.0});
			// observations.add(new double[] { 3.0});
			// observations.add(new double[] { 3.1});
			// observations.add(new double[] { 1.0});
			// observations.add(new double[] { 1.0});
			break;
		default:

		}
		phoneSequencesTrain = new ArrayList<List<Phone>>();
		obsListsTrain = new ArrayList<double[][]>();
		for (int seq = 0; seq < phoneString.length; seq++) {
			String[] phoneList = phoneString[seq].split(",");
			int nEl = phoneList.length;
			phones = new ArrayList<Phone>();
			phones.add(START_PHONE);
			// phones[0] = phoneIndexer.indexOf(START_PHONE);
			for (int i = 0; i < nEl; i++) {
				Phone phone = new Phone(phoneList[i]);
				phoneIndexer.add(phone);
				phones.add(phone);
				// phones[i+1] = phoneIndexer.indexOf(phone);
			}
			// phones[nEl+1] = phoneIndexer.indexOf(END_PHONE);
			phones.add(END_PHONE);
			String[] obsList = obsString[seq].split(",");
			observations = new double[nEl + 2][gaussianDim];
			observations[0] = null;
			int frame = 1, mfccDim = 0;
			for (int i = 0; i < obsList.length; i++) {
				observations[frame][mfccDim++] = Double.parseDouble(obsList[i]);
				if (mfccDim == gaussianDim) {
					mfccDim = 0;
					frame++;
				}
			}
			observations[nEl + 1] = null;

			phoneSequencesTrain.add(phones);
			obsListsTrain.add(observations);
		}
	}

	private List<File> getFilesUnder(String path, FilenameFilter mfccFilter) {
		File root = new File(path);
		List<File> files = new ArrayList<File>();
		addFilesUnder(root, files, mfccFilter);
		return files;
	}

	private void addFilesUnder(File root, List<File> files,
			FilenameFilter fileFilter) {
		if (root.isFile()) {
			if (!fileFilter.accept(root, root.getName()))
				return;
			files.add(root);
			return;
		}
		if (root.isDirectory()) {
			File[] children = root.listFiles();
			for (int i = 0; i < children.length; i++) {
				File child = children[i];
				addFilesUnder(child, files, fileFilter);
			}
		}
	}

	/**
	 * @return the gaussianDim
	 */
	public int getGaussianDim() {
		return gaussianDim;
	}

	/**
	 * @return the phoneIndexer
	 */
	public PhoneIndexer getPhoneIndexer() {
		return phoneIndexer;
	}

	public void setPhoneIndexer(PhoneIndexer ph) {
		this.phoneIndexer = ph;
	}

	/**
	 * @return the obsListsDev
	 */
	public List<double[][]> getObsListsDev() {
		return obsListsDev;
	}

	/**
	 * @return the obsListsTest
	 */
	public List<double[][]> getObsListsTest() {
		return obsListsTest;
	}

	/**
	 * @return the obsListsTrain
	 */
	public List<double[][]> getObsListsTrain() {
		return obsListsTrain;
	}

	/**
	 * @return the phoneSequencesDev
	 */
	public List<List<Phone>> getPhoneSequencesDev() {
		return phoneSequencesDev;
	}

	/**
	 * @return the phoneSequencesTest
	 */
	public List<List<Phone>> getPhoneSequencesTest() {
		return phoneSequencesTest;
	}

	/**
	 * @return the phoneSequencesTrain
	 */
	public List<List<Phone>> getPhoneSequencesTrain() {
		return phoneSequencesTrain;
	}

	/**
	 * 
	 */
	private void loadCheData() {
		FilenameFilter mfccFilter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.endsWith("features");
			}
		};
		// FilenameFilter ph48Filter = null;
		// if (train)
		// ph48Filter = new FilenameFilter() { public boolean accept(File dir,
		// String name) { return name.endsWith("pho48"); } };
		// else
		// ph48Filter = new FilenameFilter() { public boolean accept(File dir,
		// String name) { return name.endsWith("pho39"); } };
		FilenameFilter phoneFileFilter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.endsWith("wordlabels");
			}
		};
		File dictFile = new File(path + "/dictionary");
		List<File> mfccFiles = getFilesUnder(path + "/", mfccFilter);
		List<File> phoneFiles = getFilesUnder(path + "/", phoneFileFilter);
		int nFiles = mfccFiles.size();
		LogInfo.logs("There are " + nFiles + " mfcc files and " + phoneFiles.size()
				+ " label files.");

		List<double[][]> obsLists = new ArrayList<double[][]>(nFiles);
		List<List<Phone>> phoneSequences = new ArrayList<List<Phone>>(nFiles);
		for (int i = 0; i < mfccFiles.size(); ++i)

		{

			File featFile = mfccFiles.get(i);
			File phoneFile = phoneFiles.get(i);
			obsLists.addAll(readFeatureFile(featFile, phoneFile));
			phoneSequences.addAll(readTranscriptFiles(phoneFile, dictFile));

		}
		int totalNumSeqs = obsLists.size();
		int trainEdn = (int) Math.round(0.6 * totalNumSeqs);
		this.obsListsTrain = obsLists.subList(0, trainEdn);
		this.phoneSequencesTrain = phoneSequences.subList(0, trainEdn);
		int devEnd = (int) Math.round(0.8 * totalNumSeqs);
		this.obsListsDev = obsLists.subList(trainEdn, devEnd);
		this.phoneSequencesTrain = phoneSequences.subList(trainEdn, devEnd);
		this.obsListsTrain = obsLists.subList(devEnd, totalNumSeqs);
		this.phoneSequencesTrain = phoneSequences.subList(devEnd, totalNumSeqs);

	}
	
	public class Box<V>
	{
		V v;
		public Box(V v)
		{
			this.v = v;
		}
		
		public V getVal()
		{
			return v;
		}
		
		public void setVal(V v)
		{
			this.v = v;
		}
		

	}

	/**
	 * @param transFile
	 * @param dictFile
	 * @return
	 */
	private List<List<Phone>> readTranscriptFiles(File transFile, File dictFile) {
		final Map<String, List<Phone>> dict = new HashMap<String, List<Phone>>();
		PerlIOFuncs.diamond(dictFile, new PerlIOFuncs.LineCallback() {

			public PerlIOFuncs.ControlStatement handleLine(String line) {
				line = PerlIOFuncs.chomp(line);
				String[] F = line.split("\\s+");
				String word = F[0];
				List<Phone> phoneSeq = new ArrayList<Phone>();
				phoneSeq.add(START_PHONE);
				for (int i = 1; i < F.length; ++i) {
					phoneSeq.add(new Phone(F[i]));
				}
				// XXX TODO handle multiple pronounciataions!
				dict.put(word, phoneSeq);
				return PerlIOFuncs.ControlStatement.next;

			}

		});

		final List<List<Phone>> retVal = new ArrayList<List<Phone>>();
		final Pattern utteranceBeingPattern = Pattern
				.compile("_(\\d+)_(\\d+)\\.lab");
		final Pattern unescapeQuotePattern = Pattern.compile("\\\\'");
		List<Phone> currSeq = new ArrayList<Phone>();
		final Box<List<Phone>> currSeqBox = new Box<List<Phone>>(currSeq);
		PerlIOFuncs.diamond(transFile, new PerlIOFuncs.LineCallback() {

			public PerlIOFuncs.ControlStatement handleLine(String line) {
				line = PerlIOFuncs.chomp(line);
				if (line.equals("."))
					return PerlIOFuncs.ControlStatement.next;
				Matcher matcher = utteranceBeingPattern.matcher(line);
				if (matcher.find()) {
					if (!currSeqBox.getVal().isEmpty())
						retVal.add(currSeqBox.getVal());
					currSeqBox.setVal(new ArrayList<Phone>());
				} else {
					Matcher quoteMatcher = unescapeQuotePattern.matcher(line);
					line = quoteMatcher.replaceAll("'");
					currSeqBox.getVal().addAll(dict.get(line));
				}
				return PerlIOFuncs.ControlStatement.next;
			}
		});
		return retVal;
	}

	/**
	 * @param featFile
	 * @param transFile
	 * @return
	 */
	private List<double[][]> readFeatureFile(File featFile, File transFile) {
		final List<Pair<Integer, Integer>> utteranceFrames = new ArrayList<Pair<Integer, Integer>>();
		final Pattern utteranceBeingPattern = Pattern
				.compile("_(\\d+)_(\\d+)\\.lab");

		PerlIOFuncs.diamond(transFile, new PerlIOFuncs.LineCallback() {

			public PerlIOFuncs.ControlStatement handleLine(String line) {
				line = PerlIOFuncs.chomp(line);

				Matcher matcher = utteranceBeingPattern.matcher(line);
				if (matcher.find()) {
					utteranceFrames.add(new Pair<Integer, Integer>(Integer
							.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))));
				}
				return PerlIOFuncs.ControlStatement.next;

			}
		});

		final List<List<double[]>> obsSequences = new ArrayList<List<double[]>>();
		final Box<List<double[]>> currObsSequenceBox = new Box<List<double[]>>(
				new ArrayList<double[]>());
		final Iterator<Pair<Integer, Integer>> utteranceFameIter = utteranceFrames
				.iterator();
		final Box<Pair<Integer, Integer>> currFrameBox = new Box<Pair<Integer, Integer>>(
				utteranceFameIter.next());

		// box the t
		final int t[] = new int[1];
		t[0] = -1;
		PerlIOFuncs.diamond(featFile, new PerlIOFuncs.LineCallback() {

			public PerlIOFuncs.ControlStatement handleLine(String line) {
				line = PerlIOFuncs.chomp(line);
				if (line.startsWith("-"))
					return PerlIOFuncs.ControlStatement.next;
				t[0]++;
				if (t[0] > currFrameBox.getVal().getSecond()) {
					if (!currObsSequenceBox.getVal().isEmpty())
						obsSequences.add(currObsSequenceBox.getVal());
					currObsSequenceBox.setVal(new ArrayList<double[]>());
					if (!utteranceFameIter.hasNext())
						return PerlIOFuncs.ControlStatement.last;
					currFrameBox.setVal(utteranceFameIter.next());
				}
				if (t[0] < currFrameBox.getVal().getFirst())
					return PerlIOFuncs.ControlStatement.next;

				String[] F = line.split("\\s+");
				double[] acousticFeatures = new double[F.length - 1];
				gaussianDim = acousticFeatures.length;
				for (int i = 1; i < F.length; ++i) {
					acousticFeatures[i - 1] = Double.parseDouble(F[i]);
				}
				currObsSequenceBox.getVal().add(acousticFeatures);
				return PerlIOFuncs.ControlStatement.next;
			}
		});

		List<double[][]> asArrays = new ArrayList<double[][]>();
		for (List<double[]> x : obsSequences) {
			double[][] tmp = new double[x.size()][];
			int i = 0;
			for (double[] y : x) {
				tmp[i++] = y;
			}
			asArrays.add(tmp);
		}
		return asArrays;

	}

}
