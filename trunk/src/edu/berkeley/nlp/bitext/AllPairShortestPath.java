package edu.berkeley.nlp.bitext;

import java.util.List;
import java.util.Map;

import fig.basic.Pair;

public interface AllPairShortestPath<Node> {
	
	public Map< Pair<Node, Node>, Pair<Double, List<Node>> > getShortestPaths( Map< Pair<Node, Node>, Double > edgeCostMap) ;
	
		
}
