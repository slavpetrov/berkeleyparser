/**
 * 
 */
package edu.berkeley.nlp.auxv;

import edu.berkeley.nlp.PCFGLA.BinaryRule;
import edu.berkeley.nlp.PCFGLA.SimpleLexicon;
import edu.berkeley.nlp.PCFGLA.Grammar;
import edu.berkeley.nlp.PCFGLA.UnaryRule;
import edu.berkeley.nlp.discPCFG.DefaultLinearizer;
import edu.berkeley.nlp.discPCFG.Linearizer;
import edu.berkeley.nlp.syntax.StateSet;

/**
 * stores the sufficient statistics in one long array
 * @author petrov
 *
 */
public class VectorizedSuffStat implements SuffStat{

	public Linearizer linearizer;
	double[] counts;

	public double[] toArray(){
		return counts;
	}
	
	public VectorizedSuffStat(Linearizer lin) {
		linearizer = lin;
		counts = new double[linearizer.dimension()];
	}
	
	public final void inc(StateSet word, int tag, double[] weights){
		linearizer.increment(counts, word, tag, weights, true);
	}
	
	public final void inc(UnaryRule rule, double[] weights){
		linearizer.increment(counts, rule, weights, true);
	}

	public final void inc(BinaryRule rule, double[] weights){
		linearizer.increment(counts, rule, weights, true);
	}
	
	public VectorizedSuffStat(Grammar gr, SimpleLexicon lex) {
		linearizer = new DefaultLinearizer(gr, lex, null);
		counts = new double[linearizer.dimension()];
	}
	
	public void add(SuffStat other) {
		if (!(other instanceof VectorizedSuffStat)) throw new RuntimeException("Can only add other VectorizedSuffStat.");
		VectorizedSuffStat v_other = (VectorizedSuffStat) other;
		if (v_other.counts.length != this.counts.length) throw new RuntimeException();
		for (int i=0; i<counts.length; i++){
			counts[i] += v_other.counts[i]; 
		}
	}

	public VectorizedSuffStat newInstance() {
		return new VectorizedSuffStat(this.linearizer);
	}

	public void times(double scalar) {
		for (int i=0; i<counts.length; i++){
			counts[i] *= scalar;
		}
	}
}
