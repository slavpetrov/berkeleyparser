/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import java.io.Serializable;

/**
 * @author dburkett
 *
 */
public class UnaryEdge implements Serializable {
		/**
	   * 
	   */
	  private static final long serialVersionUID = 6926477000743115602L;
	
		public final int startIndex;
		public final int stopIndex;
		public final int parentState;
		public final int parentSubstate;
		public final int childState;
		public final int childSubstate;
		
		public UnaryEdge(int startIndex, int stopIndex, int parentState, int parentSubstate,
				int childState, int childSubstate) {
			this.startIndex = startIndex;
			this.stopIndex = stopIndex;
			this.parentState = parentState;
			this.parentSubstate = parentSubstate;
			this.childState = childState;
			this.childSubstate = childSubstate;
		}
		
		public UnaryEdge(int[] edgeArray) {
			this(edgeArray[0], edgeArray[1], edgeArray[2], edgeArray[3], edgeArray[4], edgeArray[5]);
		}
		
		public int[] toArray() {
			return new int[] { startIndex, stopIndex, parentState, parentSubstate, childState, childSubstate };
		}

		public Node getParent() {
			return new Node(startIndex, stopIndex, parentState, parentSubstate);
		}

		public Node getChild() {
			return new Node(startIndex, stopIndex, childState, childSubstate);
		}
		
		/* (non-Javadoc)
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof UnaryEdge)) {
				return false;
			}
			UnaryEdge ue = (UnaryEdge)obj;
			return startIndex == ue.startIndex &&
						 stopIndex == ue.stopIndex &&
						 parentState == ue.parentState &&
						 parentSubstate == ue.parentSubstate &&
						 childState == ue.childState &&
						 childSubstate == ue.childSubstate;
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
			result = prime * result + childState;
			result = prime * result + childSubstate;
			return result;
		}
		
		@Override
		public String toString() {
			return "[UnaryEdge span: "+startIndex+"-"+stopIndex+" parent: "+parentState+"-"+parentSubstate+
			" child: "+childState+"-"+childSubstate+"]";
		}

}
