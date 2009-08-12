package edu.berkeley.nlp.bitext;

//import edu.berkeley.nlp.parser.BinaryRule;
//import edu.berkeley.nlp.parser.GrammarStateFactory;
//import edu.berkeley.nlp.parser.Rule;
//import edu.berkeley.nlp.parser.UnaryRule;

public class BitextRule {

	private Rule lRule, rRule;
	double score ;
	private boolean inverted;
	public int parentIndex, childIndex, lChildIndex, rChildIndex;
	
	private final Type type;
	
	public static enum Type {
		BINARY_BINARY, UNARY_UNARY, BINARY_UNARY, UNARY_BINARY;
		private static Type getType(BitextRule bitextRule) {
			
			if (bitextRule.lRule instanceof UnaryRule && bitextRule.rRule instanceof UnaryRule) {
				return UNARY_UNARY;
			}
			if (bitextRule.lRule instanceof BinaryRule && bitextRule.rRule instanceof UnaryRule) {
				return BINARY_UNARY;
			}
			if (bitextRule.lRule instanceof BinaryRule && bitextRule.rRule instanceof BinaryRule) {
				return BINARY_BINARY;
			}
			if (bitextRule.lRule instanceof UnaryRule && bitextRule.rRule instanceof BinaryRule) {
				return UNARY_BINARY;
			}
			
			throw new RuntimeException("Unknown rules: " + bitextRule.lRule + " " + bitextRule.rRule);
		}
	}
	
	public BitextRule(BinaryRule lRule, BinaryRule rRule, boolean inverted) {
		this(lRule, rRule);
		this.inverted = inverted;		
	}

	public BitextRule(Rule lRule, Rule rRule) {
		this.lRule = lRule;
		this.rRule = rRule;
		this.type = Type.getType(this);
		this.childIndex = -1;
		this.lChildIndex = -1;
		this.rChildIndex = -1;
	}
	
	public Rule getLeftRule() {
		return lRule;
	}
	
	public Rule getRightRule() {
		return rRule;
	}
	
	public double getScore() {
		return score;
	}
	
	public void setScore(double score) {
		this.score = score;
	}
	
	public Type getType() {
		return type;
	}
	
	public boolean isInverted() {
		return inverted;
	}

	@Override
	public int hashCode() {
		final int PRIME = 31;
		int result = 1;
		result = PRIME * result + (inverted ? 1231 : 1237);
		result = PRIME * result + ((lRule == null) ? 0 : lRule.hashCode());
		result = PRIME * result + ((rRule == null) ? 0 : rRule.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final BitextRule other = (BitextRule) obj;
		if (inverted != other.inverted)
			return false;
		if (lRule == null) {
			if (other.lRule != null)
				return false;
		} else if (!lRule.equals(other.lRule))
			return false;
		if (rRule == null) {
			if (other.rRule != null)
				return false;
		} else if (!rRule.equals(other.rRule))
			return false;
		return true;
	}
	
	public String toString() {
		if (type != Type.BINARY_BINARY) {
			return String.format("BitextRule(%s || %s [type:%s])", lRule, rRule, type);
		}
		return String.format("BitextRule(%s || %s [inv:%s])",lRule, rRule, inverted);
	}
	
	public static void main(String[] args) {
		GrammarStateFactory gsf = new GrammarStateFactory();
		BinaryRule br = new BinaryRule(gsf.getState("A"), gsf.getState("B"), gsf.getState("C"));
		UnaryRule ur = new UnaryRule(gsf.getState("A'"), gsf.getState("B'"));
		BitextRule biRule = new BitextRule(br, ur);
		System.out.println(biRule);
	}
}
