package edu.berkeley.nlp.bitext;

import fig.basic.LogInfo;
import fig.basic.Option;
import fig.basic.OptionsParser;
import fig.exec.Execution;

/**
 * @author denero
 * 
 */
public class Main {

	@Option(gloss = "Whether to run the factorization component.")
	public static boolean runFactorizer = false;

	@Option(gloss = "Whether to run the bitext parser component.")
	public static boolean runParser = true;

	@Option(gloss = "Whether to output frivolous details.")
	public static boolean verbose = false;

	public static void main(String[] args) {
//		OptionsParser.register("main", Main.class);
//		OptionsParser.register("parser", BitextParserTester.class);
//		OptionsParser.register("factorizer", BitextGrammarFactorizer.class);
//		OptionsParser.register("mosek", MosekSolver.class);
//		OptionsParser.register("lexicon", BitextJointLexicon.class);
//		OptionsParser.register("scorer", EdgeScorer.class);
		Execution.init(args, Main.class, BitextParserTester.class, BitextGrammarFactorizer.class,
				MosekSolver.class, BitextJointLexicon.class, EdgeScorer.class);
		
		// TODO Move the main call inside the try block
		new Main();
		try {
		} catch (Throwable t) {
			Execution.raiseException(t);
		}
		Execution.finish();
	}
	
	private Main() {
		if (runFactorizer) {
			LogInfo.track("Factoring the grammar");
			BitextGrammarFactorizer.main();
			LogInfo.end_track();
		}
		if (runParser) {
			LogInfo.track("Parsing test sentences");
			BitextParserTester.main();
			LogInfo.end_track();
		}
	}
	

}
