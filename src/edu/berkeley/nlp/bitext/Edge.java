package edu.berkeley.nlp.bitext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import edu.berkeley.nlp.syntax.Tree;
//import edu.berkeley.nlp.parser.GrammarStateFactory;
import edu.berkeley.nlp.bitext.GrammarStateFactory.GrammarState;

public class Edge {

	private final GrammarState state;
	private final int start, end;	
	private Backtrace backtrace;
	private boolean discovered = false;
	private double insideScore;
	private double outsideScore;
	private double score;
		
	private static interface Backtrace { }
	
	private static class WordBacktrace implements Backtrace{ 
		String word;		
		public WordBacktrace(String word) { this.word = word; }
	}
	
	private static class UnaryBacktrace implements Backtrace {
		Edge child;
		public UnaryBacktrace(Edge child) { this.child = child; }
	}
	
	private static class BinaryBacktrace implements Backtrace {
		Edge left, right;
		public BinaryBacktrace(Edge left, Edge right) {
			this.left = left; this.right = right;
		}
	}
	
	public void setBacktrace(String word) {
		this.backtrace = new WordBacktrace(word);
	}
	
	public void setBacktrace(Edge child) {
		this.backtrace = new UnaryBacktrace(child);
	}
	
	public void setBacktrace(Edge left, Edge right) {
		this.backtrace = new BinaryBacktrace(left, right);
	}
	
	public Backtrace getBacktrace() { return backtrace; }
	
	public void setBacktrace(Backtrace backtrace) { 
		this.backtrace = backtrace;
	}
	
	public Edge(GrammarState state, int start, int end) {
		this.state = state;
		this.start = start;
		this.end = end;
	}
	
	public GrammarState state() { return state; }
	public String label() { return state.label(); }
	public int start() { return start; }
	public int end() { return end; }
		
	public double getScore() {
		return score;
	}

	
	
	public Tree<String> getTree() {
  	if (this.backtrace instanceof WordBacktrace) {
  		String word = ((WordBacktrace) this.backtrace).word;
  		List<Tree<String>> child = new ArrayList<Tree<String>>();
  		child.add(new Tree<String>(word));
  		return new Tree<String>(state().label(), child);
  	}
  	if (this.backtrace instanceof UnaryBacktrace) {
  		Tree<String> child = ((UnaryBacktrace) this.backtrace).child.getTree();
  		return new Tree<String>(state().label(), Collections.singletonList(child));
  	}
  	if (this.backtrace instanceof BinaryBacktrace) {
  		Tree<String> left = ((BinaryBacktrace) this.backtrace).left.getTree();
  		Tree<String> right = ((BinaryBacktrace) this.backtrace).right.getTree();
  		List<Tree<String>> children = new ArrayList<Tree<String>>();
  		children.add(left); children.add(right);
  		return new Tree<String>(state().label(), children);
  	}
  	throw new RuntimeException("Illegal backtrace");
  }

	public boolean isDiscovered() {
		return discovered;
	}

	public void setDiscovered(boolean discovered) {
		this.discovered = discovered;
	}
	
	public String toString() {
		return String.format("%s-[%d,%d]",state.label(),start(),end());
	}
	
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof Edge))
			return false;
		Edge other = (Edge) o;
		return this.start == other.start &&
			   this.end == other.end &&
			   this.state == other.state;
	}
	
	public int hashCode() {
		int code = 0;
		code += 2 * this.start;
		code += 5 * this.end;
		code += 19 * this.state.hashCode();
		return code;
	}
	

	public double getInsideScore() {
		return insideScore;
	}

	public void setInsideScore(double insideScore) {
		this.insideScore = insideScore;
	}

	public double getOutsideScore() {
		return outsideScore;
	}

	public void setOutsideScore(double outsideScore) {
		this.outsideScore = outsideScore;
	}
	
	public void setScore(double score) {
		this.score = score;
	}

	
	

}
