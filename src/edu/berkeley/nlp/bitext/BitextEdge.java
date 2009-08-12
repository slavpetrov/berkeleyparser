package edu.berkeley.nlp.bitext;

import edu.berkeley.nlp.bitext.GrammarStateFactory.GrammarState;

public class BitextEdge {
	
	private int leftStart, leftEnd;
	private int rightStart, rightEnd;
	private GrammarState leftState, rightState;
	
	private double insideScore, outsideScore;
			
	public double getScore() {
		return insideScore + outsideScore;
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

	@Override
	public int hashCode() {
		final int PRIME = 31;
		int result = 1;
		result = PRIME * result + leftEnd;
		result = PRIME * result + leftStart;
		result = PRIME * result + ((leftState == null) ? 0 : leftState.hashCode());
		result = PRIME * result + rightEnd;
		result = PRIME * result + rightStart;
		result = PRIME * result + ((rightState == null) ? 0 : rightState.hashCode());
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
		final BitextEdge other = (BitextEdge) obj;
		if (leftEnd != other.leftEnd)
			return false;
		if (leftStart != other.leftStart)
			return false;
		if (leftState == null) {
			if (other.leftState != null) {
				return false;
			}
		} else if (!(leftState == other.leftState)) {
			return false;
		}
		if (rightEnd != other.rightEnd)
			return false;
		if (rightStart != other.rightStart)
			return false;
		if (rightState == null) {
			if (other.rightState != null)
				return false;
		} else if (!(rightState == other.rightState)) {
			return false;
		}
		return true;
	}

	public BitextEdge(int leftStart, int leftEnd, int rightStart, int rightEnd, GrammarState leftState, GrammarState rightState) {
		this.leftStart = leftStart;
		this.leftEnd = leftEnd;
		this.rightStart = rightStart;
		this.rightEnd = rightEnd;
		this.leftState = leftState;
		this.rightState = rightState;
	}
	
	public int getLeftEnd() {
		return leftEnd;
	}
	public int getLeftStart() {
		return leftStart;
	}
	public GrammarState getLeftState() {
		return leftState;
	}
	public int getRightEnd() {
		return rightEnd;
	}
	public int getRightStart() {
		return rightStart;
	}
	public GrammarState getRightState() {
		return rightState;
	}
	
	public String toString() {
		return String.format("%s-[%d,%d] || %s-[%d,%d]",
							leftState,leftStart,leftEnd,
							rightState,rightStart,rightEnd);
				
	}

}
