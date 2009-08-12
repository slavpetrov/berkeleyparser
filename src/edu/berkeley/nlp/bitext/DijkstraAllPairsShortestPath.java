package edu.berkeley.nlp.bitext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.berkeley.nlp.util.CollectionUtils;
import fig.basic.Pair;
import fig.basic.PriorityQueue;

public class DijkstraAllPairsShortestPath<Node> implements AllPairShortestPath<Node> {
	
	private Set<Node> nodes;
	private Map<Node, List<Node>> forwardEdges;
	private Map< Pair<Node,Node>, Double > edgeCostMap;
	
	
	private List<Node> extractPathRec( Map<Node, Node> previousMap, Node node) {
		if (node == null) {
			return new ArrayList<Node>();
		}
		Node prevNode = previousMap.get(node);
		List<Node> path = extractPathRec(previousMap, prevNode);
		path.add(node);
		return path;
	}
	
	private List<Node> extractPath( Map<Node, Node> previousMap, Node node) {
		List<Node> path = extractPathRec(previousMap, node);
		
		return path;
	}
	
	private Map< Node, Pair<Double, List<Node>>> getShortestPath(Node source) {
				
		PriorityQueue<Node> nodePQ = new PriorityQueue<Node>(  );			
		Map<Node, Node> previousMap = new HashMap<Node, Node>();
		Map<Node, Double> distMap = new HashMap<Node, Double>();
		
		for (Node node: nodes) {
//			nodePQ.setPriority( node, Double.NEGATIVE_INFINITY );
			distMap.put( node, Double.POSITIVE_INFINITY );
		}
		
		distMap.put( source, 0.0 );
//		nodePQ.setPriority( source, 0.0 );
		
		while ( !nodePQ.isEmpty() ) {						
			Node node = null;//nodePQ.getFirst();
			double nodeDist = -nodePQ.getPriority();
			nodePQ.next();
										
			for (Node nextNode: CollectionUtils.getValueList(forwardEdges, node)) {
				double nextNodeDist = distMap.get(nextNode);
				Pair<Node, Node> edge = new Pair<Node, Node>(node, nextNode);
				double edgeWeight = edgeCostMap.get(edge);
				
				if ( nodeDist + edgeWeight < nextNodeDist ) {
					double newDist = nodeDist + edgeWeight;
//					nodePQ.setPriority( nextNode, -newDist );
					previousMap.put( nextNode, node );
					distMap.put( nextNode, newDist );
				}					
			}				
		}
		
		Map< Node, Pair<Double, List<Node>>> shortestPaths = new HashMap< Node, Pair<Double, List<Node>>>();
				
		for (Node node: nodes) {
			if (node.equals(source)) continue;
			double dist = distMap.get(node);
			if (dist == Double.POSITIVE_INFINITY) continue;			
			List<Node> path = extractPath(previousMap, node);
			shortestPaths.put( node,  new Pair<Double, List<Node>>(dist, path) );
		}
		
		return shortestPaths;
	}

	public Map<Pair<Node, Node>, Pair<Double, List<Node>>>
		   getShortestPaths(Map< Pair<Node,Node>, Double > edgeCostMap) {
		
		this.edgeCostMap = edgeCostMap;
		this.nodes = new HashSet<Node>();
		this.forwardEdges = new HashMap<Node, List<Node>>();
		
		for (Pair<Node, Node> edge: edgeCostMap.keySet()) {
			Node source = edge.getFirst();
			Node sink = edge.getSecond();
			nodes.add( source );
			nodes.add( sink );
			CollectionUtils.addToValueList( forwardEdges, source, sink );
		}
		
		Map< Pair<Node,Node>, Pair<Double, List<Node>> > allShortestPaths = new HashMap< Pair<Node, Node>, Pair<Double, List<Node>>>();
						
		for (Node source: nodes) {			
			Map< Node, Pair<Double, List<Node>> > shortestPaths = getShortestPath(source);
			for (Map.Entry<Node, Pair<Double, List<Node>> > entry: shortestPaths.entrySet()) {				
				Node target = entry.getKey();
				allShortestPaths.put ( new Pair<Node,Node>(source, target),  entry.getValue() );
			}			
		}
		
		return allShortestPaths;
	}
	
	public static void main(String[] args) {
		String a = "a";
		String b = "b";
		
		AllPairShortestPath<String> apsp = new DijkstraAllPairsShortestPath<String>();
		Map< Pair<String,String>, Double > edgeCostMap = new HashMap< Pair<String, String>, Double>();
		edgeCostMap.put( new Pair<String,String>(a,b), 1.0 );
			
		System.out.println(apsp.getShortestPaths(edgeCostMap));
		
	}
}
