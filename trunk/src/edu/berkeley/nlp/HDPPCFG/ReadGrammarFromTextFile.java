/**
 * 
 */
package edu.berkeley.nlp.HDPPCFG;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.berkeley.nlp.HDPPCFG.smoothing.NoSmoothing;
import edu.berkeley.nlp.HDPPCFG.smoothing.Smoother;
import edu.berkeley.nlp.HDPPCFG.sparsity.AllowAllTransitions;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.Numberer;

/**
 * @author petrov
 *
 */
public class ReadGrammarFromTextFile {
	
	
	public static void main(String[] args) {
		
		if (args.length<2) {
      System.out.println(
          "usage: java ReadGrammarFromTextFile <input file name> <output file name> \n " +
          "reads in a text file and writes a serialized grammar file."
                	);
      System.exit(2);
    }

		String inFileName = args[0];
		String outFileName = args[1];
		
		BufferedReader input = null;
		try {
		    input = new BufferedReader(new InputStreamReader(new FileInputStream(inFileName), Charset.defaultCharset()));
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		String line;
		
		Grammar grammar = null;
		Lexicon lexicon = null;
		short[] numSubStatesArray = null;
		boolean[] isGrammarTag = null;
		int numStates = -1;
		Numberer tagNumberer =  Numberer.getGlobalNumberer("tags");
		Numberer wordNumberer = Numberer.getGlobalNumberer("words");
		HashMap[][] lexiconProbs = null;
		Map<BinaryRule, BinaryRule> binaryRuleMap = new HashMap<BinaryRule, BinaryRule>();
		Map<UnaryRule, UnaryRule> unaryRuleMap = new HashMap<UnaryRule, UnaryRule>(); 

		double[] smoothParams = {0.0,0.0};
		Smoother smoother = new NoSmoothing();    
		int section=0;
		// 0 -> numSubStates, 1 -> lexicon, 2 -> unaries, 3 -> binaries
		try {
			line = input.readLine();
			numStates = Integer.parseInt(line);
			numSubStatesArray = new short[numStates];
			isGrammarTag = new boolean[numStates];
			lexiconProbs = new HashMap[numStates][];
			while ((line = input.readLine()) != null) {
				if (line.equals("")) {
					section++;
					continue;
				}
				String[] fields = line.split("\t");
				String[] tmp = fields[0].split(" ");
				int state = tagNumberer.number(tmp[0]);
				int substate = Integer.parseInt(tmp[1]);
				
				switch (section) {
					case 0:
						numSubStatesArray[state] = (short)substate;
						break;
					case 1:  // lexicon
						String word = fields[1];
						wordNumberer.number(word);
						double prob = Double.parseDouble(fields[2]);
						if (lexiconProbs[state]==null) lexiconProbs[state] = new HashMap[numSubStatesArray[state]];
						if (lexiconProbs[state][substate]==null) lexiconProbs[state][substate] = new HashMap<String,Double>();
						lexiconProbs[state][substate].put(word, prob);
						isGrammarTag[state] = false;
						break;
					case 2: // unaries
						tmp = fields[1].split(" ");
						int childState = tagNumberer.number(tmp[0]);
						int childSubstate = Integer.parseInt(tmp[1]);
						UnaryRule ur = new UnaryRule((short)state,(short)childState);
						UnaryRule fromMap = unaryRuleMap.get(ur);
						prob = Double.parseDouble(fields[2]);
						if (fromMap == null){
							double[][] scores = new double[numSubStatesArray[childState]][numSubStatesArray[state]];
							scores[childSubstate][substate] = prob;
							ur.setScores2(scores);
							unaryRuleMap.put(ur, ur);
						}
						else {
							fromMap.setScore(substate, childSubstate, prob);
						}
						isGrammarTag[state] = true;
						break;
					case 3: // binaries
						tmp = fields[1].split(" ");
						int lCState = tagNumberer.number(tmp[0]);
						int lCSubstate = Integer.parseInt(tmp[1]);
						int rCState = tagNumberer.number(tmp[2]);
						int rCSubstate = Integer.parseInt(tmp[3]);
						BinaryRule br = new BinaryRule((short)state,(short)lCState,(short)rCState);
						BinaryRule fromMap2 = binaryRuleMap.get(br);
						prob = Double.parseDouble(fields[2]);
						if (fromMap2 == null){
							double[][][] scores = new double[numSubStatesArray[lCState]][numSubStatesArray[rCState]][numSubStatesArray[state]];
							scores[lCSubstate][rCSubstate][substate] = prob;
							br.setScores2(scores);
							binaryRuleMap.put(br, br);
						}
						else {
							fromMap2.setScore(substate, lCSubstate, rCSubstate, prob);
						}
						isGrammarTag[state] = true;
						break;
					default:
						break;
				}
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		}
		
		// convert the hashmap representation to a conditional lexicon representation
		lexicon = new Lexicon(numSubStatesArray,Lexicon.DEFAULT_SMOOTHING_CUTOFF,smoothParams, smoother,-1);
		lexicon.isConditional = true;
		double[][][] condWeights = new double[wordNumberer.total()][numStates][];
		for (int w=0; w<condWeights.length; w++){
			String word = (String)wordNumberer.object(w);
			lexicon.wordCounter.incrementCount(word, 1.0);
			for (int state=0; state<numStates; state++){
				if (lexiconProbs[state]==null) continue;
				double[] scores = new double[numSubStatesArray[state]];
				boolean atLeastOne = false;
				for (int substate=0; substate<numSubStatesArray[state]; substate++){
					if (lexiconProbs[state][substate]==null) continue;
					Object val = lexiconProbs[state][substate].get(word);
					if (val==null) continue;
					scores[substate] = (Double)val;
					atLeastOne = true;
				}
				if (atLeastOne) condWeights[w][state] = scores;
			}
		}
		lexicon.conditionalWeights = condWeights;
		
		
		grammar = new Grammar(numSubStatesArray, true, new AllowAllTransitions(), smoother, null, -1);
		for (UnaryRule ur : unaryRuleMap.keySet()){
			grammar.addUnary(ur);
		}
		for (BinaryRule br : binaryRuleMap.keySet()){
			grammar.addBinary(br);
		}
		grammar.isGrammarTag = isGrammarTag;
		grammar.computePairsOfUnaries();
		
		ParserData pData = new ParserData(lexicon, grammar, Numberer.getNumberers(), numSubStatesArray, 1, 0, Binarization.RIGHT);

    System.out.println("Saving grammar to "+outFileName+".");
    if (pData.Save(outFileName)) System.out.println("Saving successful.");
    else System.out.println("Saving failed!");

    
	}
	
	
}
