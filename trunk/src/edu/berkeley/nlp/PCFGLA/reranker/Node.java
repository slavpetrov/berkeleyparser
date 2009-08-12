/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import java.io.Serializable;

/**
 * @author dburkett
 *
 */
public class Node implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1626248889143036295L;
	
	public final int startIndex;
	public final int stopIndex;
	public final int state;
	public final int substate;
	
	public Node(int startIndex, int stopIndex, int state, int substate) {
		this.startIndex = startIndex;
		this.stopIndex = stopIndex;
		this.state = state;
		this.substate = substate;
	}
	
	public Node(int startIndex, int stopIndex, int state) {
		this.startIndex = startIndex;
		this.stopIndex = stopIndex;
		this.state = state;
		this.substate = -1;
	}

	public Node coarseNode() {
		return new Node(startIndex, stopIndex, state, -1);
	}
	
	public boolean isCoarseNode() {
		return substate == -1;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof Node)) {
			return false;
		}
		Node n = (Node)obj;
		return startIndex == n.startIndex &&
					 stopIndex == n.stopIndex &&
					 state == n.state &&
					 substate == n.substate;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	@Override
	public int hashCode() {
		final int prime = 39;
		int result = startIndex;
		result = prime * result + stopIndex;
		result = prime * result + state;
		result = prime * result + substate;
		return result;
	}
	
	@Override
	public String toString() {
		if (isCoarseNode()) {
			return "[Node span: "+startIndex+"-"+stopIndex+" state: "+state+"]";
		} else {
			return "[Node span: "+startIndex+"-"+stopIndex+" state: "+state+"-"+substate+"]";
		}
	}
}
