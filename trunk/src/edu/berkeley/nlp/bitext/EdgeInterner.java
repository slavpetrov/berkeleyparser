package edu.berkeley.nlp.bitext;

import java.util.Arrays;



public class EdgeInterner {

	private final Edge[][][] edges;

	public EdgeInterner(int sentLength, int numStates) {
		edges = new Edge[sentLength+1][sentLength+1][numStates];
	}

	public Edge getInterned(Edge edge) {		
		return edges[edge.start()][edge.end()][edge.state().id()];
	}

	public Edge intern(Edge edge) {
		Edge internedEdge = getInterned(edge);
		if (internedEdge == null) {
			edges[edge.start()][edge.end()][edge.state().id()] = edge;
			return edge;
		}
		return internedEdge;
	}

	public void remove(Edge edge) {
		edges[edge.start()][edge.end()][edge.state().id()] = null;
	}

	public void overrideIntern(Edge edge) {
		edges[edge.start()][edge.end()][edge.state().id()] = edge;
	}

	public void clear() {
		for (int i=0; i < edges.length; ++i) {
				for (int j=i+1; j < edges[i].length; ++j) {
					Arrays.fill(edges[i][j], null);
				}
		}
	}

}
