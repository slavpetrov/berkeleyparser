/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import edu.berkeley.nlp.util.Numberer;

/**
 * @author petrov
 * 
 */
public class WriteGrammarToTextFile {

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		if (args.length < 2) {
			System.out
					.println("usage: java -cp berkeleyParser.jar edu/berkeley/nlp/parser/WriteGrammarToTextFile <grammar> <output file name> [<threshold>] \n "
							+ "reads in a serialized grammar file and writes it to a text file.");
			System.exit(2);
		}

		String inFileName = args[0];
		String outName = args[1];

		System.out.println("Loading grammar from file " + inFileName + ".");
		ParserData pData = ParserData.Load(inFileName);
		if (pData == null) {
			System.out.println("Failed to load grammar from file" + inFileName
					+ ".");
			System.exit(1);
		}

		Grammar grammar = pData.getGrammar();
		// if (grammar instanceof HierarchicalGrammar)
		// grammar = (HierarchicalGrammar)grammar;
		Lexicon lexicon = pData.getLexicon();
		Numberer.setNumberers(pData.getNumbs());
		Numberer tagNumberer = Numberer.getGlobalNumberer("tags");
		grammar.splitRules();
		SpanPredictor spanPredictor = pData.getSpanPredictor();

		if (args.length > 2) {
			double filter = Double.parseDouble(args[2]);
			grammar.removeUnlikelyRules(filter, 1.0);
			lexicon.removeUnlikelyTags(filter, 1.0);
		}

		System.out.println("Writing output to files " + outName + ".xxx");
		Writer output = null;
		try {
			output = new BufferedWriter(new FileWriter(outName + ".grammar"));
			// output.write(grammar.toString());
			grammar.writeData(output);
			if (output != null)
				output.close();
			output = new BufferedWriter(new FileWriter(outName + ".splits"));
			// output.write(grammar.toString());
			grammar.writeSplitTrees(output);
			if (output != null)
				output.close();
			output = new BufferedWriter(new FileWriter(outName + ".lexicon"));
			output.write(lexicon.toString());
			if (output != null)
				output.close();
			output = new BufferedWriter(new FileWriter(outName + ".words"));
			for (String word : ((SophisticatedLexicon) lexicon).wordCounter
					.keySet())
				output.write(word + "\n");
			if (output != null)
				output.close();
			if (spanPredictor != null) {
				SimpleLexicon lex = (SimpleLexicon) lexicon;
				output = new BufferedWriter(new FileWriter(outName + ".span"));
				output.write(spanPredictor.toString(lex.wordIndexer));
				if (output != null)
					output.close();
			}

		} catch (IOException ex) {
			ex.printStackTrace();
		}

	}

}
