//package edu.berkeley.nlp.bitext;
//
//
//public class BitextUnaryRule extends BitextRule {
//	
//	private final UnaryRule leftUnaryRule, rightUnaryRule;
//	
//
//	public BitextUnaryRule(final UnaryRule leftUnaryRule, final UnaryRule rightUnaryRule) {
//		this.leftUnaryRule = leftUnaryRule;
//		this.rightUnaryRule = rightUnaryRule;
//	}
//
//	public UnaryRule getLeftUnaryRule() {
//		return leftUnaryRule;
//	}
//
//	public UnaryRule getRightUnaryRule() {
//		return rightUnaryRule;
//	}
//	
//	public String toString() {
//		
//		return String.format("%s || %s => %s || %s", leftUnaryRule.parent(), 
//				  	rightUnaryRule.parent(), leftUnaryRule.child(), rightUnaryRule.child());
//		
//	}
//
//}
