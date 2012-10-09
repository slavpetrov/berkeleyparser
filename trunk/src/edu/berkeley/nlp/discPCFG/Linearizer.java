/**
 * 
 */
package edu.berkeley.nlp.discPCFG;

import java.util.List;

import edu.berkeley.nlp.PCFGLA.BinaryRule;
import edu.berkeley.nlp.PCFGLA.Grammar;
import edu.berkeley.nlp.PCFGLA.Rule;
import edu.berkeley.nlp.PCFGLA.SimpleLexicon;
import edu.berkeley.nlp.PCFGLA.SpanPredictor;
import edu.berkeley.nlp.PCFGLA.UnaryRule;
import edu.berkeley.nlp.syntax.StateSet;

/**
 * @author petrov
 * 
 */
public interface Linearizer {
	public double[] getLinearizedGrammar();

	public double[] getLinearizedLexicon();

	public double[] getLinearizedSpanPredictor();

	public double[] getLinearizedWeights();

	public void delinearizeWeights(double[] logWeights);

	public Grammar getGrammar();

	public SimpleLexicon getLexicon();

	public SpanPredictor getSpanPredictor();

	public void increment(double[] counts, StateSet stateSet, int tag,
			double[] weights, boolean isGold);

	public void increment(double[] counts, UnaryRule rule, double[] weights,
			boolean isGold);

	public void increment(double[] counts, BinaryRule rule, double[] weights,
			boolean isGold);

	public void increment(double[] counts, List<StateSet> sentence,
			double[][][] weights, boolean isGold);

	public int dimension();

	public int getNGrammarWeights();

	public int getNLexiconWeights();

	public int getNSpanWeights();
}
