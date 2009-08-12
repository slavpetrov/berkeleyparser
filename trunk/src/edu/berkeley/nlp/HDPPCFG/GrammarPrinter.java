/**
 * 
 */
package edu.berkeley.nlp.HDPPCFG;

import edu.berkeley.nlp.util.Numberer;

/**
 * @author leon
 *
 */
public class GrammarPrinter {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		if(args.length!=1) {
			System.out.println("usage: java GrammarPrinter <grammar file>");
			System.exit(1);
		}
		String inFileName = args[0];
    ParserData pData = ParserData.Load(inFileName);
    if (pData==null) {
      System.out.println("Failed to load grammar from file"+inFileName+".");
      System.exit(1);
    }
    Numberer.setNumberers(pData.getNumbs());
    Numberer tagNumberer =  Numberer.getGlobalNumberer("tags");

    Grammar grammar = pData.getGrammar();
    grammar.splitRules();
    System.out.print(grammar.toString());
	}

}
