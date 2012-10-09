package edu.berkeley.nlp.PCFGLA;

import java.util.Map;

import edu.berkeley.nlp.PCFGLA.smoothing.SmoothAcrossParentBits;
import edu.berkeley.nlp.PCFGLA.smoothing.Smoother;
import edu.berkeley.nlp.util.CommandLineUtils;

public class GrammarSmoother {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if (args.length < 1) {
			System.out
					.println("usage: java GrammarSmoother \n"
							+ "\t\t  -i       Input File for Grammar (Required)\n"
							+ "\t\t  -o       Output File for Smoothed Grammar (Required)\n"
							+ "\t\t  -smooth  Type of grammar smoothing used.  This takes the maximum likelihood rule\n"
							+ "               probabilities and smooths them with each other.  Current options are\n"
							+ "               'NoSmoothing', 'SmoothAcrossParentSubstate', and 'SmoothAcrossParentBits'.\n"
							+ "\t\t  -grsm    Grammar smoothing parameter, in range [0,1].  (Default: 0.1)\n");
			System.exit(2);
		}
		// provide feedback on command-line arguments
		System.out.print("Running with arguments:  ");
		for (String arg : args) {
			System.out.print(" '" + arg + "'");
		}
		System.out.println("");

		// parse the input arguments
		Map<String, String> input = CommandLineUtils
				.simpleCommandLineParser(args);

		String outFileName = CommandLineUtils.getValueOrUseDefault(input, "-o",
				null);
		String inFileName = CommandLineUtils.getValueOrUseDefault(input, "-i",
				null);
		System.out.println("Loading grammar from " + inFileName + ".");

		ParserData pData = ParserData.Load(inFileName);
		if (pData == null) {
			System.out.println("Failed to load grammar from file" + inFileName
					+ ".");
			System.exit(1);
		}

		Grammar gr = pData.getGrammar();

		// double grammarSmoothing =
		// Double.parseDouble(CommandLineUtils.getValueOrUseDefault(input,
		// "-grsm","0.1"));
		// String smootherStr = CommandLineUtils.getValueOrUseDefault(input,
		// "-smooth", "NoSmoothing");

		Smoother grSmoother = new SmoothAcrossParentBits(0.01, gr.splitTrees);
		Smoother lexSmoother = new SmoothAcrossParentBits(0.1, gr.splitTrees);

		gr.setSmoother(grSmoother);
		// gr.smooth();
		pData.gr = gr;
		Lexicon lex = pData.getLexicon();
		lex.setSmoother(lexSmoother);
		pData.lex = lex;
		if (pData.Save(outFileName))
			System.out.println("Saving successful.");
		else
			System.out.println("Saving failed!");

		System.exit(0);

	}

}
