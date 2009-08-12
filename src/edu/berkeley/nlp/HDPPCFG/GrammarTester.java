package edu.berkeley.nlp.HDPPCFG;

import edu.berkeley.nlp.parser.EnglishPennTreebankParseEvaluator;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees;
import edu.berkeley.nlp.util.Numberer;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.StringReader;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Reads in the Penn Treebank and generates N_GRAMMARS different grammars.
 *
 * @author Slav Petrov
 */
public class GrammarTester {

  public static final int MAX_SENTENCE_LENGTH = 40;

  public static void main(String[] args) {
  	if (args.length!=2) {
      System.out.println(
          "usage: java GrammarTester <path to WSJ corpus> " +
          "          <input file for grammar> \n");
      System.exit(2);
    }

    String pathWSJ = args[0];
    if (pathWSJ.equals("null"))
      pathWSJ = null;
    System.out.println("Loading WSJ corpus from "+pathWSJ+".");
    

    Corpus corpus = new Corpus(pathWSJ,1,1.0,false,false);
    List<Tree<String>> testTrees = corpus.getDevTestingTrees();//2300 to 2399 only for the final test
    //Trees.PennTreeReader reader = new Trees.PennTreeReader(new StringReader("((S (UN1 (UN2 (NP (DT the) (JJ quick) (JJ brown) (NN fox)))) (VP (VBD jumped) (PP (IN over) (NP (DT the) (JJ lazy) (NN dog)))) (. .)))"));
    //Trees.PennTreeReader reader = new Trees.PennTreeReader(new StringReader("((S (NP (NP (JJ Influential) (NNS members)) (PP (IN of) (NP (DT the) (NNP House) (NNP Ways) (CC and) (NNP Means) (NNP Committee)))) (VP (VBD introduced) (NP (NP (NN legislation)) (SBAR (WHNP (WDT that)) (S (VP (MD would) (VP (VB restrict) (SBAR (WHADVP (WRB how)) (S (NP (DT the) (JJ new) (NN savings-and-loan) (NN bailout) (NN agency)) (VP (MD can) (VP (VB raise) (NP (NN capital)))))) (, ,) (S (VP (VBG creating) (NP (NP (DT another) (JJ potential) (NN obstacle)) (PP (TO to) (NP (NP (NP (DT the) (NN government) (POS 's)) (NN sale)) (PP (IN of) (NP (JJ sick) (NNS thrifts)))))))))))))) (. .))))"));
    /*Trees.PennTreeReader reader = new Trees.PennTreeReader(new StringReader("((S (`` ``) (NP (PRP We)) (VP (VBP do) (RB n't) (VP (VB think) (PP (IN at) (NP (DT this) (NN point))) (SBAR (S (NP (NN anything)) (VP (VBZ needs) (S (VP (TO to) (VP (VB be) (VP (VBD said)))))))))) (. .)))"));
    Tree<String> testTreex = reader.next();
    ArrayList<Tree<String>> testTrees = new ArrayList<Tree<String>>();
    testTrees.add(testTreex);*/

    String inFileName = args[1];
    System.out.println("Loading grammar from "+inFileName+".");

    ParserData pData = ParserData.Load(inFileName);
    if (pData==null) {
      System.out.println("Failed to load grammar from file"+inFileName+".");
      System.exit(1);
    }
    Grammar grammar = pData.getGrammar();
    grammar.splitRules();
    LexiconInterface lexicon = pData.getLexicon();
    Numberer.setNumberers(pData.getNumbs());

/*    UnaryRule[] parentRules = grammar.getClosedUnaryRulesByParent(23);
		for (int i = 0; i < parentRules.length; i++) {
			UnaryRule r = parentRules[i];
			System.out.println(r+" "+r.parentState+" "+r.getScore(0,0));
		}
*/
    /*List<UnaryRule> parentRules = grammar.getUnaryRulesByChild(27);
		for (UnaryRule r : parentRules) {
			 System.out.println(r.parentState+" "+r.getScore(0,0));
		}*/

		ArrayParser parser = new ArrayParser(grammar, lexicon);

    EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String> eval = new EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String>(Collections.singleton("ROOT"), new HashSet<String>(Arrays.asList(new String[] {"''", "``", ".", ":", ","})));

    for (Tree<String> testTree : testTrees) {
      List<String> testSentence = testTree.getYield();
      if( testSentence.size() > MAX_SENTENCE_LENGTH ) continue;

      System.out.println("Original tree:\n"+Trees.PennTreeRenderer.render(testTree));
      Tree<String> parsedTree = parser.getBestParse(testSentence);
      //System.out.println(Trees.PennTreeRenderer.render(parsedTree));
      parsedTree = TreeAnnotations.unAnnotateTree(parsedTree);
      System.out.println("Parsing result:\n"+Trees.PennTreeRenderer.render(parsedTree));

      eval.evaluate(parsedTree, testTree);
    }
    eval.display(true);

    System.exit(0);
  }


}
