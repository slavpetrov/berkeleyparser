package edu.berkeley.nlp.bitext;

import edu.berkeley.nlp.bitext.GrammarStateFactory.GrammarState;


public abstract class Rule {

	protected final GrammarState parent;
	protected double score = Double.NaN;


	protected Rule(GrammarState parentLabel) {
		this.parent = parentLabel;
	}

	public GrammarState parent() { return parent; }
	public void setScore(double score) { this.score = score; }
	public double getScore() { 
		assert !Double.isNaN(score) : " Score not set ";
		return this.score; 
	} 

	/**
	 * @param args
	 */
	public static void main(String[] args) {

	}

}
