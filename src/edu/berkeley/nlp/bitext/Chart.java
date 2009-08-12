package edu.berkeley.nlp.bitext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//import edu.berkeley.nlp.parser.Grammar;
import edu.berkeley.nlp.bitext.GrammarStateFactory.GrammarState;
import edu.berkeley.nlp.util.CollectionUtils;

public class Chart {
	
	private final static int MAX_LENGTH = 40;
	
	private Grammar grammar;
	private List[][] edgesByLeftIndexAndState;
	private List[][] edgesByRightIndexAndState;
	private EdgeInterner edgeInterner;

	public Chart(Grammar grammar) {
		this.grammar = grammar;
		edgesByLeftIndexAndState = new List[MAX_LENGTH+1][grammar.getNumStates()];
		edgesByRightIndexAndState = new List[MAX_LENGTH+1][grammar.getNumStates()];		
		this.edgeInterner = new EdgeInterner(MAX_LENGTH+1, grammar.getNumStates());
		
	}
	
	public void clear() {
		
		edgeInterner.clear();
		
		for (int i=0; i < MAX_LENGTH+1; ++i) {
			for (int j=0; j < grammar.getNumStates(); ++j) {
				List leftEdges = edgesByLeftIndexAndState[i][j];
				if (  leftEdges != null ) {
					leftEdges.clear();
				}
				List rightEdges = edgesByRightIndexAndState[i][j];
				if (  rightEdges != null ) {
					rightEdges.clear();
				}
			}
		}
	}
	
	public boolean containsEdge(Edge edge) {
		return edgeInterner.getInterned(edge) != null;
	}
	
	private void addLeftIndex(Edge edge) {		
		List edges = edgesByLeftIndexAndState[edge.start()][edge.state().id()];
		if (edges == null) {
			edges = new ArrayList();
			edgesByLeftIndexAndState[edge.start()][edge.state().id()] = edges;			
		}
		edges.add(edge);		
	}
	
	private void addRightIndex(Edge edge) {		
		List edges = edgesByRightIndexAndState[edge.end()][edge.state().id()];
		if (edges == null) {
			edges = new ArrayList();
			edgesByRightIndexAndState[edge.end()][edge.state().id()] = edges;			
		}
		edges.add(edge);		
	} 
	

	public boolean addEdge(Edge edge) {
		
		Edge curEdge = edgeInterner.getInterned(edge);
		if (curEdge != null) {
			if (curEdge.getScore() >= edge.getScore()) {
				return false;
			}
		}
		addLeftIndex(edge);
		addRightIndex(edge);
		edgeInterner.overrideIntern(edge);
		return true;		
	}
		
	private static final List<Edge> emptyEdges = Collections.unmodifiableList( new ArrayList<Edge>() );
	
	public List<Edge> getEdgesByRightIndex(GrammarState state, int end) {
		List<Edge> edges = edgesByRightIndexAndState[end][state.id()];
		if (edges != null) {
			return edges;
		}
		return emptyEdges;
	}
	
	public List<Edge> getEdgesByLeftIndex(GrammarState state, int start) {
		List<Edge> edges = edgesByLeftIndexAndState[start][state.id()];
		if (edges != null) {
			return edges;
		}
		return emptyEdges;
	}
}




