/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.berkeley.nlp.PCFGLA.ConditionalTrainer.Options;

/**
 * @author petrov
 * 
 */
public class ConditionalPipeline {

	public static boolean initializeWithZero = true;

	public static void main(String[] args) {
		OptionParser optParser = new OptionParser(
				ConditionalTrainer.Options.class);
		Options opts = (Options) optParser.parse(args, true);
		// provide feedback on command-line arguments
		// System.out.println("Calling with " + optParser.getPassedInOptions());

		String dirName = opts.outDir;
		String baseName = "split_";
		File directory = new File(dirName);
		if (!directory.mkdir())
			System.out.println("Failed to make directory.");
		File nextFile = null;

		// first train an x-bar generative grammar
		List<String> baselineArgsList = new ArrayList<String>(
				Arrays.asList(new String[] { "-path", opts.path, "-trfr",
						"" + opts.trainingFractionToKeep, "-treebank",
						opts.treebank + "", "-out",
						dirName + "/" + "base_gen.gr", "-baseline", "-maxL",
						opts.maxL + "", "-b", opts.binarization + "" }));
		if (opts.markUnaryParents)
			baselineArgsList.add("-markUnaryParents");
		if (opts.markUnaryParents)
			baselineArgsList.add("-filterStupidFrickinWHNP");
		if (opts.collapseUnaries)
			baselineArgsList.add("-collapseUnaries");
		String[] baselineArgs = baselineArgsList.toArray(new String[] {});

		nextFile = new File(dirName + "/" + "base_gen.gr");
		if (opts.initializeDir == null) {
			if (!nextFile.exists() || opts.dontLoad)
				ConditionalTrainer.main(baselineArgs);
			else
				System.out.println("Skipping this step since "
						+ nextFile.toString() + " already exists.");

			// now compute constraints with x-bar generative grammar
			String[] consArgsTrain = addOptions(args, new String[] { "-out",
					dirName + "/" + baseName + "0", "-in",
					dirName + "/" + "base_gen.gr", "-outputLog",
					dirName + "/" + baseName + "0.cons.log" });
			nextFile = new File(dirName + "/" + baseName + "0-0.data");
			if (nextFile.exists() && !opts.dontLoad)
				System.out.println("Skipping this step since "
						+ nextFile.toString() + " already exists.");
			else {
				ParserConstrainer.main(consArgsTrain);
				consArgsTrain = addOptions(args, new String[] { "-out",
						dirName + "/" + baseName + "0_dev", "-in",
						dirName + "/" + "base_gen.gr", "-section", "dev",
						"-nChunks", "1", "-outputLog",
						dirName + "/" + baseName + "0_dev.cons.log" });
				ParserConstrainer.main(consArgsTrain);
				consArgsTrain = addOptions(args, new String[] { "-out",
						dirName + "/" + baseName + "0_test", "-in",
						dirName + "/" + "base_gen.gr", "-section", "final",
						"-nChunks", "1", "-outputLog",
						dirName + "/" + baseName + "0_test.cons.log" });
				ParserConstrainer.main(consArgsTrain);
			}
		}
		// then train an x-bar generative grammar with the simple lexicon
		nextFile = new File(dirName + "/" + baseName + "0.gr");

		String[] baselineCondArgs = null;
		if (opts.initializeDir != null) {
			baselineCondArgs = addOptions(args,
					new String[] { "-out", nextFile.toString(), /* "-baseline", */
							"-cons", opts.initializeDir + "/" + baseName + "0",
							"-in",
							opts.initializeDir + "/" + baseName + "0.gr",
							"-doNOTprojectConstraints", "-noSplit",
							"-doConditional" });// ,
		} else {
			baselineCondArgs = addOptions(args,
					new String[] { "-out", nextFile.toString(), /* "-baseline", */
							"-cons", dirName + "/" + baseName + "0",
							initializeWithZero ? "-initializeZero" : "",
							"-doNOTprojectConstraints", "-noSplit",
							"-doConditional" });// ,
		}
		if (!nextFile.exists() || opts.dontLoad) {
			ConditionalTrainer.main(baselineCondArgs);
			if (opts.testAll) {
				System.out
						.println("Testing all grammars to determine which one was the best and should be split next");
				String[] testArgs = new String[] { "-doNOTprojectConstraints",
						"-cons", dirName + "/" + baseName + "0_dev-0.data",
						"-testAll", "-path", opts.path, "-in",
						baseName + "0.gr", "-filePath", opts.outDir,
						"-treebank", opts.treebank + "", "-maxL",
						opts.maxL + "", "-parser", "plain", "-nProcess",
						opts.nProcess + "" };
				GrammarTester.main(testArgs);
			}
		} else
			System.out.println("Skipping this step since "
					+ nextFile.toString() + " already exists.");

		// loop:
		for (int split = 1; split <= 6; split++) {
			System.out.println("\n\nIn " + split + ". Split-Iteration.");

			String previousGrammar = dirName + "/" + baseName + (split - 1);
			String currentGrammar = dirName + "/" + baseName + split;

			// split grammar and train it

			String[] trainArgs = null;
			if (opts.initializeDir == null) {
				nextFile = new File(currentGrammar + ".gr");
				trainArgs = addOptions(args, new String[] { "-in",
						previousGrammar + ".gr", "-doConditional", "-cons",
						previousGrammar, "-out", nextFile.toString() });// ,
																		// "-sigma",
																		// Math.pow(split,1.5)+""});"
																		// +
			} else {
				nextFile = new File(currentGrammar + ".gr");
				trainArgs = addOptions(args, new String[] { "-in",
						opts.initializeDir + "/" + baseName + (split) + ".gr",
						"-doConditional", "-noSplit", "-cons",
						opts.initializeDir + "/" + baseName + (split - 1),
						"-out", nextFile.toString() });// , "-sigma",
														// Math.pow(split,1.5)+""});"
														// +

			}
			if (!nextFile.exists() || opts.dontLoad) {
				ConditionalTrainer.main(trainArgs);
				if (opts.testAll) {
					System.out
							.println("Testing all grammars to determine which one was the best and should be split next");
					String[] testArgs = new String[] {
							"-cons",
							dirName + "/" + baseName + (split - 1)
									+ "_dev-0.data", "-testAll", "-path",
							opts.path, "-in", baseName + split + ".gr",
							"-filePath", opts.outDir, "-treebank",
							opts.treebank + "", "-maxL", opts.maxL + "",
							"-parser", "plain", "-nProcess", opts.nProcess + "" };
					GrammarTester.main(testArgs);
				}
			} else
				System.out.println("Skipping this step since "
						+ nextFile.toString() + " already exists.");

			// compute constraints with new grammar
			if (opts.initializeDir == null) {
				nextFile = new File(currentGrammar + "-0.data");
				if (nextFile.exists() && !opts.dontLoad) {
					System.out.println("Skipping this step since "
							+ nextFile.toString() + " already exists.");
				} else {
					String[] consArgs = addOptions(args, new String[] {
							"-cons", previousGrammar, "-out", currentGrammar,
							"-in", currentGrammar + ".gr", "-outputLog",
							currentGrammar + ".cons.log" });
					ParserConstrainer.main(consArgs);
					consArgs = addOptions(args, new String[] { "-cons",
							previousGrammar + "_dev", "-out",
							currentGrammar + "_dev", "-in",
							currentGrammar + ".gr", "-section", "dev",
							"-nChunks", "1", "-outputLog",
							currentGrammar + "_dev.cons.log" });
					ParserConstrainer.main(consArgs);
					consArgs = addOptions(args, new String[] { "-cons",
							previousGrammar + "_test", "-out",
							currentGrammar + "_test", "-in",
							currentGrammar + ".gr", "-section", "final",
							"-nChunks", "1", "-outputLog",
							currentGrammar + "_test.cons.log" });
				}

			}
		}

		System.exit(0);

	}

	private static String[] addOptions(String[] a, String[] b) {
		String[] res = new String[a.length + b.length];
		for (int i = 0; i < a.length; i++) {
			res[i] = a[i];
		}
		for (int i = 0; i < b.length; i++) {
			res[i + a.length] = b[i];
		}
		return res;
	}

}
