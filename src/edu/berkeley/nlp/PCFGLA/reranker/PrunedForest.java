/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author dburkett
 *
 */
public class PrunedForest implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 6385924093653251443L;

	private final List<String> sentence;
	
	private final Node[] nodes;
	private final BinaryEdge[] binaryEdges;
	private final UnaryEdge[] unaryEdges;
	
	private final double[] binaryEdgeBaseModelCost;
	private final double[] unaryEdgeBaseModelCost;
	private final double[] lexicalNodeBaseModelCost;
	private final double[] binaryEdgePruningScore;
	private final double[] unaryEdgePruningScore;
	
	private final int[][] binaryEdgesByNode;
	private final int[][] unaryEdgesByNode;
	private final int[] leftChildNodeIndices;
	private final int[] rightChildNodeIndices;
	private final int[] unaryChildNodeIndices;
	private final int rootNode;
	
	public PrunedForest(Node[] nodes, BinaryEdge[] binaryEdges, UnaryEdge[] unaryEdges,
			double[] binaryEdgeBaseModelCost, double[] unaryEdgeBaseModelCost, double[] lexicalNodeBaseModelCost,
			double[] binaryEdgePruningScore, double[] unaryEdgePruningScore, List<String> sentence) {
		this.sentence = sentence;
		this.nodes = nodes;
		this.binaryEdges = binaryEdges;
		this.unaryEdges = unaryEdges;
		this.binaryEdgeBaseModelCost = binaryEdgeBaseModelCost;
		this.unaryEdgeBaseModelCost = unaryEdgeBaseModelCost;
		this.lexicalNodeBaseModelCost = lexicalNodeBaseModelCost;
		this.binaryEdgePruningScore = binaryEdgePruningScore;
		this.unaryEdgePruningScore = unaryEdgePruningScore;
		
		Map<Node, Integer> nodeMap = buildNodeMap();
		binaryEdgesByNode = createBinaryByNodeList(nodeMap);
		unaryEdgesByNode = createUnaryByNodeList(nodeMap);

		leftChildNodeIndices = storeLeftChildIndices(nodeMap);
		rightChildNodeIndices = storeRightChildIndices(nodeMap);
		unaryChildNodeIndices = storeUnaryChildIndices(nodeMap);
		rootNode = nodeMap.get(new Node(0, sentence.size(), 0, 0));
	}

	/**
	 * Returns all the nodes in this forest, in topologically sorted order
	 */
	public Node[] getNodes() {
		return nodes;
	}
	
	public BinaryEdge[] getBinaryEdges() {
		return binaryEdges;
	}
	
	public UnaryEdge[] getUnaryEdges() {
		return unaryEdges;
	}
	
	public int[] getBinaryEdgesByNode(int nodeIndex) {
		return binaryEdgesByNode[nodeIndex];
	}
	
	public int[] getUnaryEdgesByNode(int nodeIndex) {
		return unaryEdgesByNode[nodeIndex];
	}

	public int getLeftChildNodeIndex(int binaryEdgeIndex) {
		return leftChildNodeIndices[binaryEdgeIndex];
	}

	public int getRightChildNodeIndex(int binaryEdgeIndex) {
		return rightChildNodeIndices[binaryEdgeIndex];
	}

	public int getUnaryChildNodeIndex(int unaryEdgeIndex) {
		return unaryChildNodeIndices[unaryEdgeIndex];
	}

	public int getRootNodeIndex() {
		return rootNode;
	}
	
	private int[][] createUnaryByNodeList(Map<Node, Integer> nodeMap) {
		List<List<Integer>> lists = makeListList(nodes.length);
		for (int ur=0; ur<unaryEdges.length; ur++) {
			Node parent = unaryEdges[ur].getParent();
			if (nodeMap.containsKey(parent)) {
				int index = nodeMap.get(parent);
				lists.get(index).add(ur);
			}
		}
		int[][] array = new int[nodes.length][];
		for (int i=0; i<nodes.length; i++) {
			array[i] = makeArray(lists.get(i));
		}
		return array;
	}

	private int[][] createBinaryByNodeList(Map<Node, Integer> nodeMap) {
		List<List<Integer>> lists = makeListList(nodes.length);
		for (int br=0; br<binaryEdges.length; br++) {
			Node parent = binaryEdges[br].getParent();
			if (nodeMap.containsKey(parent)) {
				int index = nodeMap.get(parent);
				lists.get(index).add(br);
			}
		}
		int[][] array = new int[nodes.length][];
		for (int i=0; i<nodes.length; i++) {
			array[i] = makeArray(lists.get(i));
		}
		return array;
	}
	
	private int[] makeArray(List<Integer> list) {
		int[] a = new int[list.size()];
		for (int i=0; i<a.length; i++) {
			a[i] = list.get(i);
		}
		return a;
	}

	private static List<List<Integer>> makeListList(int size) {
		List<List<Integer>> list = new ArrayList<List<Integer>>(size);
		for (int i=0; i<size; i++) {
			list.add(new ArrayList<Integer>());
		}
		return list;
	}

	private int[] storeUnaryChildIndices(Map<Node, Integer> nodeMap) {
		int[] a = new int[unaryEdges.length];
		for (int i=0; i<a.length; i++) {
			Node n = unaryEdges[i].getChild();
			a[i] = nodeMap.get(n);
		}
		return a;
	}

	private int[] storeRightChildIndices(Map<Node, Integer> nodeMap) {
		int[] a = new int[binaryEdges.length];
		for (int i=0; i<a.length; i++) {
			Node n = binaryEdges[i].getRightChild();
			a[i] = nodeMap.get(n);
		}
		return a;
	}

	private int[] storeLeftChildIndices(Map<Node, Integer> nodeMap) {
		int[] a = new int[binaryEdges.length];
		for (int i=0; i<a.length; i++) {
			Node n = binaryEdges[i].getLeftChild();
			a[i] = nodeMap.get(n);
		}
		return a;
	}

	private Map<Node, Integer> buildNodeMap() {
		HashMap<Node, Integer> map = new HashMap<Node, Integer>();
		for (int n=0; n<nodes.length; n++) {
			map.put(nodes[n], n);
		}
		return map;
	}

	public double getBinaryEdgeScore(int binaryEdgeIndex) {
		return binaryEdgeBaseModelCost[binaryEdgeIndex];
	}

	public double getUnaryEdgeScore(int unaryEdgeIndex) {
		return unaryEdgeBaseModelCost[unaryEdgeIndex];
	}
	
	public double getLexicalNodeScore(int nodeIndex) {
		return lexicalNodeBaseModelCost[nodeIndex];
	}
	
	public double getBinaryEdgePruningScore(int binaryEdgeIndex) {
		return binaryEdgePruningScore[binaryEdgeIndex];
	}

	public double getUnaryEdgePruningScore(int unaryEdgeIndex) {
		return unaryEdgePruningScore[unaryEdgeIndex];
	}

	public List<String> getSentence() {
		return sentence;
	}


}
