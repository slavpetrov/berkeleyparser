package edu.berkeley.nlp.bitext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

//import edu.berkeley.nlp.parser.BinaryRule;
//import edu.berkeley.nlp.parser.Grammar;
//import edu.berkeley.nlp.parser.Rule;
//import edu.berkeley.nlp.parser.UnaryRule;
import fig.basic.Pair;
import fig.basic.IOUtils;
import fig.basic.LogInfo;
import fig.basic.Option;
import fig.basic.StrUtils;
import fig.exec.Execution;

public class BitextParserTester {

	/**
	 * Detects whether a rule (e.g. X Y[0] Z[1]) is inverted
	 */
	private static Pattern invertedPattern = Pattern.compile(".*\\[1\\].*\\[0\\].*");

	@Option(gloss = "Location of the grammar files: <grammar>.{lhs, rhs, weights...}")
	public static String grammar;

	@Option(gloss = "File of LHS sentences to parse.")
	public static String leftInputsFile;

	@Option(gloss = "File of RHS sentences to parse.")
	public static String rightInputsFile;

	@Option(gloss = "File containing alignments (if any).")
	public static String alignmentsFile;

	@Option(gloss = "Number of sentences to parse.")
	public static int numSentences = 6;

	@Option(gloss = "Maximum length of parsed sentence (max of LHS and RHS).")
	public static int maxLength = 1001;

	@Option(gloss = "Whether to use a separate joint lexicon.")
	public static boolean useLexicon = false;

	@Option(gloss = "Whether to use a monolingual pruning.")
	public static boolean pruneMono = true;

	@Option(gloss = "Whether to exclude repeat test sentences.")
	public static boolean excludeRepeats = true;
	
	@Option(gloss = "Whether to use chart parser (instead of agenda parser.")
	public static boolean useChartParser = true;

	private static List<List<String>> getInputs(String file) {
		try {
			BufferedReader reader = IOUtils.openInHard(file);
			List<List<String>> inputs = new ArrayList<List<String>>();
			while (true) {
				String line = reader.readLine();
				if (line == null) {
					break;
				}
				List<String> tokens = Arrays.asList(StrUtils.split(line, " "));
				inputs.add(tokens);
			}
			return inputs;
		} catch (Exception e) {
			e.printStackTrace();
		}

		throw new RuntimeException();
	}

	private static Rule addRule(String line, Grammar grammar) {
		line = line.replaceAll("\\(", "");
		line = line.replaceAll("\\)", "");
		line = line.replaceAll("\\[\\d+\\]", "");
		String[] states = line.split("\\s+");

		// Unary
		if (states.length == 2) {
			UnaryRule ur = grammar.addUnaryRule(states[0], states[1]);
			grammar.setScore(ur, 0.0);
			return ur;
		}
		// Binary
		else if (states.length == 3) {
			BinaryRule br = grammar.addBinaryRule(states[0], states[1], states[2]);
			grammar.setScore(br, 0.0);
			return br;
		}
		throw new RuntimeException();
	}

	/**
	 * Build BitextGrammar object assumes root.{lhs, rhs, lhsWeights, rhsWeights,
	 * weights} exists.
	 * 
	 * @param root
	 * @return
	 */
	public static BitextGrammar getBitextGrammar(String root) {
		BufferedReader lhsReader = IOUtils.openInHard(root + ".lhs");
		BufferedReader rhsReader = IOUtils.openInHard(root + ".rhs");
		BufferedReader weightsReader = IOUtils.openInHard(root + ".weights");
//		BufferedReader lhsWeightsReader = IOUtils.openInHard(root + ".lhsWeights");
//		BufferedReader rhsWeightsReader = IOUtils.openInHard(root + ".rhsWeights");

		Grammar leftGrammar = new Grammar();
		Grammar rightGrammar = new Grammar();
		List<Pair<Rule, Rule>> rules = new ArrayList<Pair<Rule, Rule>>();
		List<Double> weights = new ArrayList<Double>();
		List<Boolean> inverteds = new ArrayList<Boolean>();

		while (true) {
			try {
				String lhsLine = lhsReader.readLine();
				String rhsLine = rhsReader.readLine();
				String weightsLine = weightsReader.readLine();
//				String lhsWeightsLine = lhsWeightsReader.readLine();
//				String rhsWeightsLine = rhsWeightsReader.readLine();

				if (lhsLine == null) {
					break;
				}

				boolean inverted = invertedPattern.matcher(rhsLine).matches();
				Rule lhs = addRule(lhsLine, leftGrammar);
				Rule rhs = addRule(rhsLine, rightGrammar);
				double weight = Math.log(Double.parseDouble(weightsLine));

				double lhsWeight = 0;//Double.NaN;//Double.parseDouble(lhsWeightsLine);
				double rhsWeight = 0;//Double.NaN;//Double.parseDouble(rhsWeightsLine);
				leftGrammar.setScore(lhs, lhsWeight);
				rightGrammar.setScore(rhs, rhsWeight);

				rules.add(new Pair<Rule, Rule>(lhs, rhs));
				weights.add(weight);
				inverteds.add(inverted);

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		// Assume all things parsed end in a sentence
		UnaryRule leftUR = leftGrammar.addUnaryRule("ROOT", "S");
		UnaryRule rightUR = rightGrammar.addUnaryRule("ROOT", "S");
		leftGrammar.setScore(leftUR, 0.0);
		rightGrammar.setScore(rightUR, 0.0);

		rules.add(new Pair<Rule, Rule>(leftUR, rightUR));
		weights.add(0.0);
		inverteds.add(false);

		leftGrammar.lock();
		rightGrammar.lock();

		BitextGrammar biGrammar = new BitextGrammar(leftGrammar, rightGrammar);

		for (int i = 0; i < rules.size(); ++i) {
			Rule lhs = rules.get(i).getFirst();
			Rule rhs = rules.get(i).getSecond();
			boolean inverted = inverteds.get(i);
			double weight = weights.get(i);

			BitextRule bitextRule = null;
			if (lhs instanceof BinaryRule && rhs instanceof BinaryRule) {
				bitextRule = new BitextRule((BinaryRule) lhs, (BinaryRule) rhs, inverted);
			} else {
				bitextRule = new BitextRule(lhs, rhs);
			}
			bitextRule.setScore(weight);
			biGrammar.addRule(bitextRule);
		}
		LogInfo.logsForce("Number of state pairs: " + biGrammar.getNumberStatePairs());
		return biGrammar;
	}

	private static List<Pair<List<String>, List<String>>> getPairedInputs(
			List<List<String>> lInputs, List<List<String>> rInputs, int maxLength) {
		List<Pair<List<String>, List<String>>> pairedInputs = new ArrayList<Pair<List<String>, List<String>>>();

		int numSentences = lInputs.size();
		for (int i = 0; i < numSentences; ++i) {
			List<String> lInput = lInputs.get(i);
			List<String> rInput = rInputs.get(i);
			if (lInput.size() > maxLength || rInput.size() > maxLength) {
				continue;
			}
			Pair<List<String>, List<String>> pairInput = new Pair<List<String>, List<String>>(
					lInput, rInput);
			if(excludeRepeats && pairedInputs.contains(pairInput)) {
				continue;
			}
			pairedInputs.add(pairInput);
		}

		return pairedInputs;
	}

	private static class Stats {
		private double sum = 0.0;

		private int count = 0;

		public void addObservation(double x) {
			sum += x;
			count++;
		}

		public double sum() {
			return sum;
		}

		public double average() {
			if (count == 0) {
				return 0.0;
			}
			return sum / count;
		}
	}

	/**
	 * @param ranFactorizer
	 *          whether or not we just ran the factorizer
	 */
	public static void main() {
		if (Main.runFactorizer) {
			// Use the grammar files the factorizer just generated.
			grammar = Execution.getFile(BitextGrammarFactorizer.grammarWriteRoot);
		}
		LogInfo.track("Building bitext grammar");
		BitextGrammar biGrammar = getBitextGrammar(grammar);
		LogInfo.end_track();

		LogInfo.track("Building lexicon");
		BitextLexicon lexicon;
		if (useLexicon) {
			lexicon = new BitextJointLexicon();
		} else {
			lexicon = BitextIndependentLexicon.createDefaultLexicon();
		}
		LogInfo.end_track();

		LogInfo.track("Initializing parser");
		BitextParser biParser = null;
		if (useChartParser) 
			biParser = new BitextChartParser(biGrammar, lexicon, pruneMono, alignmentsFile!=null);
		else 
			biParser = new BitextAgendaParser(biGrammar, lexicon);
		LogInfo.end_track();

		LogInfo.track("Reading test sentences");
		List<List<String>> lInputs = getInputs(leftInputsFile);
		List<List<String>> rInputs = getInputs(rightInputsFile);

		lInputs = lInputs.subList(0, Math.min(lInputs.size(), numSentences));
		rInputs = rInputs.subList(0, Math.min(lInputs.size(), numSentences));

		List<Pair<List<String>, List<String>>> pairedInputs = getPairedInputs(lInputs,
				rInputs, maxLength);
		LogInfo.end_track();
		LogInfo.logs("Loaded " + pairedInputs.size() + " sentence(s).");
		
		List<Alignment> alignments = null;
		if (alignmentsFile!=null){
			LogInfo.track("Reading alignments");
			alignments = loadAlignmets();
			LogInfo.end_track();
		}

		int totalEdgesPopped = 0;
//		Stopwatch stopwatch = new Stopwatch();

		Map<Integer, Stats> edgeCounts = new HashMap<Integer, Stats>();

		int index = 0;
		for (Pair<List<String>, List<String>> pairInput : pairedInputs) {
			if (index==0||index==1||index==2||index==3) { index++; continue; }
			List<String> lInput = pairInput.getFirst();
			List<String> rInput = pairInput.getSecond();
			Alignment alignment = (alignments!=null) ? alignments.get(index) : null;
			index++;
			for (int i=0; i<10; i++){
				LogInfo.logsForce("[Parsing]:");
				LogInfo.logsForce("Left Input: " + lInput);
				LogInfo.logsForce("Right Input: " + rInput);
//				stopwatch.start();
				biParser.parse(lInput, rInput, alignment);
//				stopwatch.stop();
//				LogInfo.logsForce("Done...Took " + stopwatch.getLastElapsedTime() + " sec. to parse ");
			}
			int numEdgesPopped = biParser.getNumEdgesPopped();
			if (numEdgesPopped == 0) {
				continue;
			}
			totalEdgesPopped += numEdgesPopped;
			int maxLen = Math.max(lInput.size(), rInput.size());
			Stats stats = edgeCounts.get(maxLen);
			if (stats == null) {
				stats = new Stats();
				edgeCounts.put(maxLen, stats);
			}
			stats.addObservation(numEdgesPopped);
		}

//		writeHistogram(edgeCounts);
		LogInfo.logs("Total Edges Popped: " + totalEdgesPopped);
		double averagePopped = totalEdgesPopped / (double) numSentences;
		LogInfo.logs("Average Popped Per Sentence: " + averagePopped);
//		double averageTime = stopwatch.getTotalElapsedTime() / numSentences;
//		LogInfo.logs("Average Time Per Sentence: " + averageTime);
	}

	/**
	 * @param alignmentsFile2
	 * @param numSentences2
	 * @return
	 */
	private static List<Alignment> loadAlignmets() {
		List<Alignment> alignments = new ArrayList<Alignment>(numSentences);
		BufferedReader in = IOUtils.openInEasy(alignmentsFile);
		String line = "";
		try {
			while ((line = in.readLine()) != null){
				Alignment alignment = new Alignment(line);
				alignments.add(alignment);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return alignments;
	}

	private static void writeHistogram(Map<Integer, Stats> edgeCounts) {
		String histogramName = Execution.getFile("histogram");
		PrintWriter histogramStream = IOUtils.openOutHard(histogramName);
		List<Integer> keys = new ArrayList<Integer>(edgeCounts.keySet());
		Collections.sort(keys);
		for (Integer key : keys) {
			Stats stats = edgeCounts.get(key);
			histogramStream.printf("%s %f\n", key, stats.average());
		}
		histogramStream.close();
	}

}
