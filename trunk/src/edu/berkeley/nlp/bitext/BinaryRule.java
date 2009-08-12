package edu.berkeley.nlp.bitext;

import edu.berkeley.nlp.bitext.GrammarStateFactory.GrammarState;



public class BinaryRule extends Rule {

	private final GrammarState lChild, rChild;
	
	
	public BinaryRule(GrammarState parent, GrammarState leftChild,  GrammarState rightChild) 
	{
		super(parent);
		this.lChild = leftChild;
		this.rChild = rightChild;
	}
	
	
	@Override
	public String toString() { 
		return parent + " => "  + lChild + " " + rChild;
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof BinaryRule)) {
			return false;
		}
		BinaryRule other = (BinaryRule) o;
		return this.parent.equals(other.parent)  &&
			   this.lChild.equals(other.lChild) &&
			   this.rChild.equals(other.rChild);
	}
	
	public int hashCode() {
		int code = 0;
		code = 3 * parent.hashCode();
		code += 13 * lChild.hashCode();
		code += 10 * rChild.hashCode();
		return code;
	}
	
	public GrammarState leftChild() { return lChild; }
	public GrammarState rightChild() { return rChild; } 
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
