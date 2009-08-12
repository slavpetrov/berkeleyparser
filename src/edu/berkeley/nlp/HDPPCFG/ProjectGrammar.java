package edu.berkeley.nlp.HDPPCFG;

import java.util.Arrays;
import java.util.List;
import java.util.Map;


import edu.berkeley.nlp.syntax.Trees;
import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.CommandLineUtils;
import edu.berkeley.nlp.util.Numberer;

public class ProjectGrammar {

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {
		if (args.length<2) {
			System.out.println(
					"usage: java ProjectGrammar " +
					"               -i     Input File for Grammar (Required)\n"
				 +"								-f		 Filter rules with prob under f (Default: -1)"
				 +"               -o     Output File for Grammar (Required)\n"
			);
			System.exit(2);
		}
		// provide feedback on command-line arguments
		System.out.print("Running with arguments:  ");
		for (String arg : args) {
			System.out.print(" '"+arg+"'");
		}
		System.out.println("");
		
		// parse the input arguments
		Map<String, String> input = CommandLineUtils.simpleCommandLineParser(args);
		
		double filter = Double.parseDouble(CommandLineUtils.getValueOrUseDefault(input, "-f","-1"));
		
		String outFileName = CommandLineUtils.getValueOrUseDefault(input, "-o", null);
		
		String inFileName = CommandLineUtils.getValueOrUseDefault(input, "-i", null);
		if (inFileName==null) {
			throw new Error("Did not provide a grammar.");
		}
		System.out.println("Loading grammar from "+inFileName+".");
		
		ParserData pData = ParserData.Load(inFileName);
		if (pData==null) {
			System.out.println("Failed to load grammar from file"+inFileName+".");
			System.exit(1);
		}
		Grammar grammar = pData.getGrammar();
		grammar.splitRules();
		LexiconInterface lexicon = pData.getLexicon();
		if (filter>0){
			double power = 1.0;
			//System.out.println("Removing rules with probability less than "+filter);
			grammar.removeUnlikelyRules(filter,power);
			//System.out.println("And also tags with probability below "+filter);
			lexicon.removeUnlikelyTags(filter);
		}
		Numberer.setNumberers(pData.getNumbs());
		
		//System.out.println("DT Split tree:\n"+Trees.PennTreeRenderer.render(grammar.splitTrees[Numberer.getGlobalNumberer("tags").number("DT")]));
    int[][] fromMapping = grammar.computeMapping(1);
    int[][] toSubstateMapping = grammar.computeSubstateMapping(1);
    int[][] toMapping = grammar.computeToMapping(1,toSubstateMapping);
		double[] condProbs = grammar.computeConditionalProbabilities(fromMapping,toMapping);
//		System.out.println(Arrays.toString(condProbs));
		Grammar newGrammar = //grammar.projectTo0LevelGrammar(condProbs,fromMapping,toMapping);
		 grammar.projectGrammar(condProbs,fromMapping,toSubstateMapping);

		//System.out.print(lexicon);
		LexiconInterface newLexicon = lexicon.projectLexicon(condProbs,fromMapping,toSubstateMapping);
		//System.out.println("newLexicon");
		//System.out.print(lexicon);
		
		short[] numSubStatesArray = new short[grammar.numSubStates.length];
		Arrays.fill(numSubStatesArray,(short)1);
    ParserData pDataNew = new ParserData(newLexicon, newGrammar, Numberer.getNumberers(), numSubStatesArray, pData.v_markov, pData.h_markov, pData.bin);

    System.out.println("Saving grammar to "+outFileName+".");
    if (pDataNew.Save(outFileName)) System.out.println("Saving successful.");
    else System.out.println("Saving failed!");

    
    System.out.print(grammar);
		System.out.println("newGrammar");
		newGrammar.splitRules();
		System.out.print(newGrammar);
		
	}


}
