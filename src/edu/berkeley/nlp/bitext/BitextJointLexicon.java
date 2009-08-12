package edu.berkeley.nlp.bitext;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//import minus.model.Alignment;
import edu.berkeley.nlp.syntax.Tree;
//import edu.berkeley.nlp.parser.Lexicon;
import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.CounterMap;
import edu.berkeley.nlp.util.Interner;
import fig.basic.Pair;
import fig.basic.IOUtils;
import fig.basic.Option;
import fig.basic.StrUtils;

public class BitextJointLexicon implements BitextLexicon {

	@Option(gloss = "Directory of lexicon files.")
	public static String lexiconBase = "../bitextLexicon/execs/3.exec";

	@Option(gloss = "Left hand side POS tag file name.")
	public static String lhsPOSFile = "en.tags";

	@Option(gloss = "Right hand side POS tag file name.")
	public static String rhsPOSFile = "es.tags";

	@Option(gloss = "Alignment file name.")
	public static String alignmentFile = "es.tags_final";

	@Option(gloss = "Use precomputed POS tags instead of distributions over them.")
	public static boolean useGoldPOS = false;

	@Option(gloss = "Additional cost of considering two words to be generated independently.")
	public static double nullCost = .001;

	@Option(gloss = "Model of P(lhsWord, rhsWord | lhsTag, rhsTag).")
	public static String lhsToRhsLexicalModel = "enToEs.model";

	@Option(gloss = "Model of P(lhsWord, null | lhsTag, null).")
	public static String lhsToNullLexicalModel = "enToNull.model";

	@Option(gloss = "Model of P(null, rhsWord | null, rhsTag).")
	public static String rhsToNullLexicalModel = "esToNull.model";

	@Option(gloss = "Mixture component model of P(areAligned | lhsWord, rhsWord).")
	public static String areAlignedLexicalModel = "areAligned.model";

	@Option(gloss = "Improves the heuristic by maximizing over possible word alignments.")
	public static boolean maximizeOverAlignments = false;

	@Option(gloss = "LHS words are cost free (creates a directional model).")
	public static boolean lhsAreCostFree = true;

	private List<String> lhsSentence, rhsSentence;

	private List<String> lhsTags, rhsTags;

	private Map<List<String>, List<String>> lhsWordsToTags, rhsWordsToTags;

//	private Map<List<String>, Alignment> wordsToAlignments;

	private Counter<Pair<String, String>> areAlignedModel;

	private CounterMap<Pair<String, String>, Pair<String, String>> lhsToRhsModel;

	private CounterMap<String, String> lhsToNullModel, rhsToNullModel;

	private ProjectedLexicon lhsLexicon;

	private ProjectedLexicon rhsLexicon;

	public BitextJointLexicon() {
		initialize();
		if (useGoldPOS) {
			lhsWordsToTags = new HashMap<List<String>, List<String>>();
			rhsWordsToTags = new HashMap<List<String>, List<String>>();

			BufferedReader lhsWords = IOUtils.openInHard(BitextParserTester.leftInputsFile);
			BufferedReader rhsWords = IOUtils.openInHard(BitextParserTester.rightInputsFile);
			BufferedReader lhsTags = IOUtils.openInHard(lexiconBase + lhsPOSFile);
			BufferedReader rhsTags = IOUtils.openInHard(lexiconBase + rhsPOSFile);
			try {
				while (lhsWords.ready()) {
					List<String> lhsTag = Arrays.asList(StrUtils.split(lhsTags.readLine(), " "));
					List<String> lhs = Arrays.asList(StrUtils.split(lhsWords.readLine(), " "));
					List<String> rhsTag = Arrays.asList(StrUtils.split(rhsTags.readLine(), " "));
					List<String> rhs = Arrays.asList(StrUtils.split(rhsWords.readLine(), " "));

					lhsWordsToTags.put(lhs, lhsTag);
					rhsWordsToTags.put(rhs, rhsTag);
				}
			} catch (IOException e) {
				e.printStackTrace();
				throw new RuntimeException();
			}

		}

		// Read component models for joint production of lexical items.
		File nextFile;
		nextFile = new File(lexiconBase, lhsToRhsLexicalModel);
		BufferedReader lhsToRhsReader = IOUtils.openInHard(nextFile);
		nextFile = new File(lexiconBase, lhsToNullLexicalModel);
		BufferedReader lhsToNullReader = IOUtils.openInHard(nextFile);
		nextFile = new File(lexiconBase, rhsToNullLexicalModel);
		BufferedReader rhsToNullReader = IOUtils.openInHard(nextFile);
		nextFile = new File(lexiconBase, areAlignedLexicalModel);
		BufferedReader areAlignedReader = IOUtils.openInHard(nextFile);

		// Read models
		try {
			while (lhsToRhsReader.ready()) {
				String[] line = StrUtils.split(lhsToRhsReader.readLine(), "\t");
				assert line.length == 5;
				lhsToRhsModel.incrementCount(Pair.makePair(line[1], line[3]), Pair.makePair(
						line[0], line[2]), Double.valueOf(line[4]));
			}
			while (areAlignedReader.ready()) {
				String[] line = StrUtils.split(areAlignedReader.readLine(), "\t");
				assert line.length == 3;
				// TaggedWord lhsWord = TaggedWord.createTaggedWord(line[1], line[0]);
				// TaggedWord rhsWord = TaggedWord.createTaggedWord(line[3], line[2]);
				double prob = Double.valueOf(line[2]);
				areAlignedModel.incrementCount(Pair.makePair(line[0], line[1]), prob);
			}
			while (lhsToNullReader.ready()) {
				String[] line = StrUtils.split(lhsToNullReader.readLine(), "\t");
				assert line.length == 3;
				double prob = lhsAreCostFree ? 1.0 : Double.valueOf(line[2]);
				lhsToNullModel.incrementCount(line[1], line[0], prob);
			}
			while (rhsToNullReader.ready()) {
				String[] line = StrUtils.split(rhsToNullReader.readLine(), "\t");
				assert line.length == 3;
				double prob = Double.valueOf(line[2]);
				rhsToNullModel.incrementCount(line[1], line[0], prob);
			}
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException("Error loading lexicon models.");
		}
	}

	private void initialize() {
		lhsToRhsModel = new CounterMap<Pair<String, String>, Pair<String, String>>();
		areAlignedModel = new Counter<Pair<String, String>>();
		lhsToNullModel = new CounterMap<String, String>();
		rhsToNullModel = new CounterMap<String, String>();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.bitext.mix.BitextLexicon#setLhsInputSentence(java.util.List)
	 */
	public void setLhsInputSentence(List<String> sentence) {
		lhsSentence = sentence;
		if (useGoldPOS) {
			lhsTags = lhsWordsToTags.get(sentence);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.bitext.mix.BitextLexicon#setRhsInputSentence(java.util.List)
	 */
	public void setRhsInputSentence(List<String> sentence) {
		rhsSentence = sentence;
		if (useGoldPOS) {
			rhsTags = rhsWordsToTags.get(sentence);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.bitext.mix.BitextLexicon#getTagScores(int, int)
	 */
	public Counter<Pair<String, String>> getTagScores(int lhsPosition, int rhsPosition) {
		String lhs = lhsSentence.get(lhsPosition);
		String rhs = rhsSentence.get(rhsPosition);
		Pair<String, String> pair = new Pair<String, String>(lhs, rhs);
		Counter<Pair<String, String>> scores = new Counter<Pair<String, String>>();
		double probJoint = areAlignedModel.getCount(pair);
		double probIndep = 1.0 - probJoint;

		if (useGoldPOS) {
			String lhsTag = lhsTags.get(lhsPosition);
			String rhsTag = rhsTags.get(rhsPosition);

			Pair<String, String> tags = new Pair<String, String>(lhsTag, rhsTag);

			double joint = lhsToRhsModel.getCount(pair, tags);
			double indep = lhsToNullModel.getCount(lhs, lhsTag)
					* rhsToNullModel.getCount(rhs, rhsTag) * nullCost;
			scores.incrementCount(tags, probJoint * joint + probIndep * indep);
			// scores.normalize(); // This is only here to make this lexicon
			// equivalent to default.
		} else {
			// Add joint probabilities
			Counter<Pair<String, String>> jointScores = lhsToRhsModel.getCounter(pair);

			for (Pair<String, String> tags : jointScores.keySet()) {
				scores.incrementCount(tags, jointScores.getCount(tags) * probJoint);
			}

			// Add independent probabilities
			Counter<String> lhsScores = lhsToNullModel.getCounter(lhs);
			Counter<String> rhsScores = rhsToNullModel.getCounter(rhs);
			for (String lhsTag : lhsScores.keySet()) {
				for (String rhsTag : rhsScores.keySet()) {
					Pair<String, String> tags = new Pair<String, String>(lhsTag, rhsTag);
					double s = lhsScores.getCount(lhsTag) * rhsScores.getCount(rhsTag) * nullCost;
					scores.incrementCount(tags, s * probIndep);
				}
			}
		}
		return scores.toLogSpace();
	}

	public Lexicon getLhsLexicon() {
		if (lhsLexicon == null) {
			lhsLexicon = new ProjectedLexicon(true);
		}
		return lhsLexicon;
	}

	public Lexicon getRhsLexicon() {
		if (rhsLexicon == null) {
			rhsLexicon = new ProjectedLexicon(false);
		}
		return rhsLexicon;
	}

	public class ProjectedLexicon implements Lexicon {

		private static final long serialVersionUID = 1L;

		private List<String> sentence;

		private CounterMap<String, String> model;

		private boolean isLhs;

		public ProjectedLexicon(boolean isLhs) {
			this.isLhs = isLhs;
			this.model = new CounterMap<String, String>();
			CounterMap<String, String> nullModel = isLhs ? lhsToNullModel : rhsToNullModel;
			for (String key : nullModel.keySet()) {
				for (String val : nullModel.getCounter(key).keySet()) {
					model.incrementCount(key, val, 0.0);
				}
			}
		}

		public Counter<String> getTagScores(int loc) {
			if (!useGoldPOS) {
				return model.getCounter(sentence.get(loc));
			} else {
				String tag = isLhs ? lhsTags.get(loc) : rhsTags.get(loc);
				Counter<String> scores = new Counter<String>();
				double score = 0;
				if (maximizeOverAlignments) {
					score = -0.9 * Double.MAX_VALUE;
					int otherSentenceLength = isLhs ? rhsSentence.size() : lhsSentence.size();
					for (int otherLoc = 0; otherLoc < otherSentenceLength; otherLoc++) {
						Counter<Pair<String, String>> pairScores;
						pairScores = isLhs ? BitextJointLexicon.this.getTagScores(loc, otherLoc)
								: BitextJointLexicon.this.getTagScores(otherLoc, loc);
						score = Math.max(score, 0.5 * pairScores.totalCount());
					}
				}
				scores.incrementCount(tag, score);
				return scores;
			}
		}

		public boolean isKnown(String word) {
			return model.keySet().contains(word);
		}

		public void readData(BufferedReader reader) {
			throw new UnsupportedOperationException();
		}

		public void setInputSentence(List<String> sentence) {
			this.sentence = sentence;
			List<String> bitextSentence = isLhs ? lhsSentence : rhsSentence;
			if (!sentence.equals(bitextSentence)) {
				throw new RuntimeException("The bitext lexicon and a projection are out of sync.");
			}
		}

		public void train(Collection<Tree<String>> trees) {
			throw new UnsupportedOperationException();
		}

		public void writeData(BufferedWriter write) {
			throw new UnsupportedOperationException();
		}

	}
}
