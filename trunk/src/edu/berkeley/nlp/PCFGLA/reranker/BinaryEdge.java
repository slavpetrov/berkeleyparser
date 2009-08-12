/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import java.io.Serializable;

/**
 * @author dburkett
 *
 */
public class BinaryEdge  implements Serializable {
    /**
	   * 
	   */
	  private static final long serialVersionUID = 8137321225327266421L;
	
		public final int startIndex;
		public final int stopIndex;
		public final int parentState;
		public final int parentSubstate;
		public final int leftState;
		public final int leftSubstate;
		public final int rightState;
		public final int rightSubstate;
		public final int splitIndex;
		
		public BinaryEdge(int startIndex, int stopIndex, int parentState, int parentSubstate,
				int leftState, int leftSubstate, int rightState, int rightSubstate, int splitIndex) {
			this.startIndex = startIndex;
			this.stopIndex = stopIndex;
			this.parentState = parentState;
			this.parentSubstate = parentSubstate;
			this.leftState = leftState;
			this.leftSubstate = leftSubstate;
			this.rightState = rightState;
			this.rightSubstate = rightSubstate;
			this.splitIndex = splitIndex;
		}
		
		public BinaryEdge(int[] edgeArray) {
			this(edgeArray[0], edgeArray[1], edgeArray[2], edgeArray[3], edgeArray[4], edgeArray[5],
					edgeArray[6], edgeArray[7], edgeArray[8]);
		}
		
		public int[] toArray() {
			return new int[] { startIndex, stopIndex, parentState, parentSubstate, leftState, leftSubstate,
					rightState, rightSubstate, splitIndex};
		}

		public Node getParent() {
			return new Node(startIndex, stopIndex, parentState, parentSubstate);
		}

		public Node getLeftChild() {
			return new Node(startIndex, splitIndex, leftState, leftSubstate);
		}

		public Node getRightChild() {
			return new Node(splitIndex, stopIndex, rightState, rightSubstate);
		}
		
		/* (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof BinaryEdge)) {
				return false;
			}
			BinaryEdge be = (BinaryEdge)obj;
			return startIndex == be.startIndex &&
						 stopIndex == be.stopIndex &&
						 parentState == be.parentState &&
						 parentSubstate == be.parentSubstate &&
						 leftState == be.leftState &&
						 leftSubstate == be.leftSubstate &&
						 rightState == be.rightState &&
						 rightSubstate == be.rightSubstate &&
						 splitIndex == be.splitIndex;
		}
		
		/* (non-Javadoc)
		 * @see java.lang.Object#hashCode()
		 */
		@Override
		public int hashCode() {
			final int prime = 39;
			int result = startIndex;
			result = prime * result + stopIndex;
			result = prime * result + parentState;
			result = prime * result + parentSubstate;
			result = prime * result + leftState;
			result = prime * result + leftSubstate;
			result = prime * result + rightState;
			result = prime * result + rightSubstate;
			result = prime * result + splitIndex;
			return result;
		}
		
		@Override
		public String toString() {
			return "[BinaryEdge span: "+startIndex+"-"+splitIndex+"-"+stopIndex+" parent: "+parentState+"-"+parentSubstate+
			" leftchild: "+leftState+"-"+leftSubstate+" rightchild: "+rightState+"-"+rightSubstate+"]";
		}
}
