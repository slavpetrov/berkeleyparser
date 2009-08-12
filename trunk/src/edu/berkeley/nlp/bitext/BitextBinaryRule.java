//package edu.berkeley.nlp.bitext;
//
//
//public class BitextBinaryRule extends BitextRule {
//	
//	private final BinaryRule leftBinaryRule, rightBinaryRule;
//	private final boolean inverted;
//
//	
//	public BitextBinaryRule(final BinaryRule leftBinaryRule, final BinaryRule rightBinaryRule, final boolean inverted) {		
//		this.leftBinaryRule = leftBinaryRule;
//		this.rightBinaryRule = rightBinaryRule;
//		this.inverted = inverted;
//	}
//	
//	public boolean isInverted() {
//		return inverted;
//	}
//
//	public BinaryRule getLeftBinaryRule() {
//		return leftBinaryRule;
//	}
//
//	public BinaryRule getRightBinaryRule() {
//		return rightBinaryRule;
//	}
//
//	@Override
//	public int hashCode() {
//		final int PRIME = 31;
//		int result = 1;
//		result = PRIME * result + (inverted ? 1231 : 1237);
//		result = PRIME * result + ((leftBinaryRule == null) ? 0 : leftBinaryRule.hashCode());
//		result = PRIME * result + ((rightBinaryRule == null) ? 0 : rightBinaryRule.hashCode());
//		return result;
//	}
//
//	@Override
//	public boolean equals(Object obj) {
//		if (this == obj)
//			return true;
//		if (obj == null)
//			return false;
//		if (getClass() != obj.getClass())
//			return false;
//		final BitextBinaryRule other = (BitextBinaryRule) obj;
//		if (inverted != other.inverted)
//			return false;
//		if (leftBinaryRule == null) {
//			if (other.leftBinaryRule != null)
//				return false;
//		} else if (!leftBinaryRule.equals(other.leftBinaryRule))
//			return false;
//		if (rightBinaryRule == null) {
//			if (other.rightBinaryRule != null)
//				return false;
//		} else if (!rightBinaryRule.equals(other.rightBinaryRule))
//			return false;
//		return true;
//	}
//	
//	
//	public String toString() {
//		
//		StringBuilder sb = new StringBuilder();
//		
//		sb.append( String.format("%s || %s =>  %s %s || %s %s",
//								leftBinaryRule.parent(), rightBinaryRule.parent(),
//								leftBinaryRule.leftChild(), leftBinaryRule.rightChild(),
//								rightBinaryRule.leftChild(), rightBinaryRule.rightChild()));
//		
//		if (isInverted()) {
//			sb.append(" [inverted] ");
//		}
//							
//		return sb.toString();
//	}
//	
//
//
//}
