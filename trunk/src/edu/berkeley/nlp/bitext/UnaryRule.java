package edu.berkeley.nlp.bitext;

import edu.berkeley.nlp.bitext.GrammarStateFactory.GrammarState;


public class UnaryRule extends Rule {
	
	private final GrammarState child;
	
	public UnaryRule(GrammarState parent, GrammarState child) {
		super(parent);
		this.child = child;
	}
	
	public GrammarState child() { return child; }

	public String	toString() {
		return parent + " => " + child;
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof UnaryRule)) {
			return false;
		}
		UnaryRule other = (UnaryRule) o;
		return this.parent.equals(other.parent) && this.child.equals(other.child);
	}
	
	public int hashCode() {
		int code = 0;
		code += 3 * parent.hashCode();
		code += 7 * child.hashCode();
		return code;
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
	}

}
