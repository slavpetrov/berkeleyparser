/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.berkeley.nlp.PCFGLA.Grammar;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.Lists;
import edu.berkeley.nlp.util.Numberer;

/**
 * @author dburkett
 *
 */
public class BaseModel {
	private final Numberer stateNumberer;
	private final short[] numSubStatesArray;
	private final int[][] unaryIntermediateStates;
	private final boolean[] isGrammarState;
	private final short[] baseStates;
	private final boolean[] isAntecedentState;
	private final boolean[] hasAntecedent;
	private final int[][] antecedentRelations;
	private final boolean[] hasEmptyChildren;
	private final int[][] emptyChildren;
	
	public final static int NONEIndex = -2;
	
	public BaseModel(Grammar baseGrammar) {
		this.unaryIntermediateStates = baseGrammar.getClosedSumPaths();
		this.isGrammarState = baseGrammar.isGrammarTag;
		this.stateNumberer = baseGrammar.getTagNumberer();
		this.numSubStatesArray = baseGrammar.numSubStates;
		this.baseStates = constructBaseStatesArray();
		this.isAntecedentState = constructIsAntecedentArray();
		this.hasAntecedent = constructHasAntecedentArray();
		int[][] antecedentTypes = constructAntecedentTypeArray();
		int[][] antecedentRequirements = constructAntecedentRequirementArray();
		this.antecedentRelations = constructAntecedentRelationArray(antecedentTypes, antecedentRequirements);
		this.hasEmptyChildren = constructHasNoneArray();
		this.emptyChildren = constructEmptyChildArray();
	}

	private int[][] constructEmptyChildArray() {
		int[][] emptyChildArray = new int[stateNumberer.size()][];
		for (int s=0; s<stateNumberer.size(); s++) {
			if (hasEmptyChildren[s]) {
				List<Integer> children = new ArrayList<Integer>();
				String label = (String)stateNumberer.object(s);
				int p = label.indexOf('~');
				int childrenAdded = 0;
				while (p >= 0) {
					int q = label.indexOf('~', p+1);
					int index = Integer.parseInt(label.substring(p+1, q)) + childrenAdded;
					while (children.size() < index) {
						children.add(-1);
					}
					p = q+2;
					q = getNextLabelDelimiter(label, p);
					String childLabel = label.substring(p, q);
					int childState = NONEIndex;
					if (!childLabel.equals("NONE")) {
						childState = stateNumberer.number(childLabel + "^g");
					}
					children.add(childState);
					int bracketCount = 1;
					while (bracketCount > 0 && p < label.length()) {
						if (label.charAt(p) == '[') {
							bracketCount++;
						}
						if (label.charAt(p) == ']') {
							bracketCount--;
						}
						p++;
					}
					p = label.indexOf('~', p);
				}
				emptyChildArray[s] = Lists.toPrimitiveArray(children);
			}
		}
		return emptyChildArray;
	}

	private boolean[] constructHasNoneArray() {
		boolean[] hasNoneArray = new boolean[stateNumberer.size()];
		for (int s=0; s<stateNumberer.size(); s++) {
			if (isSyntheticState(s)) {
				continue;
			}
			String label = (String)stateNumberer.object(s);
			hasNoneArray[s] = label.contains("NONE");
		}
		return hasNoneArray;
	}

	private int[][] constructAntecedentRelationArray(int[][] antecedentTypes, int[][] antecedentRequirements) {
		int[][] antecedentRelationArray = new int[stateNumberer.size()][];
		for (int s=0; s<stateNumberer.size(); s++) {
			if (isAntecedentState[s]) {
				antecedentRelationArray[s] = new int[stateNumberer.size()];
				Arrays.fill(antecedentRelationArray[s], -1);
				for (int t=0; t<stateNumberer.size(); t++) {
					if (hasAntecedent[t]) {
						for (int ant : antecedentTypes[s]) {
							for (int req : antecedentRequirements[t]) {
								if (ant == req) {
									antecedentRelationArray[s][t] = ant;
								}
							}
						}
					}
				}
			}
		}
		return antecedentRelationArray;
	}

	private int[][] constructAntecedentRequirementArray() {
		int[][] antecedentTypeArray = new int[stateNumberer.size()][];
		for (int s=0; s<antecedentTypeArray.length; s++) {
			if (hasAntecedent[s]) {
				List<Integer> types = new ArrayList<Integer>();
				String label = (String)stateNumberer.object(s);
				if (label.contains("+XX")) {
					int p = label.indexOf("+XX");
					int q = Math.max(label.lastIndexOf('[', p), label.lastIndexOf('(', p));
					String baseLabel = label.substring(q+1, getNextLabelDelimiter(label, q));
					types.add(stateNumberer.number(baseLabel + "^g"));
				}
				int p = label.length();
				while (p > 0) {
					p = label.lastIndexOf("_XX]", p-1);
					if (p > 0) {
						int q = label.lastIndexOf("~[NONE~0~[", p);
						q = Math.max(label.lastIndexOf('[', q), label.lastIndexOf('(', q));
						String baseLabel = label.substring(q+1, getNextLabelDelimiter(label, q));
						types.add(stateNumberer.number(baseLabel + "^g"));
					}
					antecedentTypeArray[s] = Lists.toPrimitiveArray(types);
				}
			}
		}
		return antecedentTypeArray;
	}

	private int[][] constructAntecedentTypeArray() {
		int[][] antecedentTypeArray = new int[stateNumberer.size()][];
		for (int s=0; s<antecedentTypeArray.length; s++) {
			if (isAntecedentState[s]) {
				String label = (String)stateNumberer.object(s);
				if (label.endsWith("_XX")) {
					String baseLabel = label.substring(0, label.length()-3);
					if (baseLabel.startsWith("WH")) {
						baseLabel = baseLabel.substring(2);
					}
					antecedentTypeArray[s] = new int[] { stateNumberer.number(baseLabel + "^g") };
				}
				else {
					List<Integer> types = new ArrayList<Integer>();
					int p = label.length();
					while (p > 0) {
						p = label.lastIndexOf("_XX~", p-1);
						if (p > 0) {
							int q = Math.max(label.lastIndexOf('[', p), label.lastIndexOf('(', p));
							String baseLabel = label.substring(q+1, getNextLabelDelimiter(label, q));
							if (baseLabel.startsWith("WH")) {
								baseLabel = baseLabel.substring(2);
							}
							types.add(stateNumberer.number(baseLabel + "^g"));
						}
					}
					antecedentTypeArray[s] = Lists.toPrimitiveArray(types);
				}
			}
		}
		return antecedentTypeArray;
	}

	private int getNextLabelDelimiter(String label, int startPoint) {
		int index = Integer.MAX_VALUE;
		int index1 = label.indexOf('_', startPoint);
		if (index1 >=0) 
			index = Math.min(index, index1);
		int index2 = label.indexOf('+', startPoint);
		if (index2 >=0)
			index = Math.min(index, index2);
		int index3 = label.indexOf('~', startPoint);
		if (index3 >=0)
			index = Math.min(index, index3);
		if (index == Integer.MAX_VALUE)
			return -1;
		return index;
	}

	private boolean[] constructHasAntecedentArray() {
		boolean[] hasAntecedentArray = new boolean[stateNumberer.size()];
		for (int s=0; s<hasAntecedentArray.length; s++) {
			if (isSyntheticState(s)) {
				continue;
			}
			String label = (String)stateNumberer.object(s);
			hasAntecedentArray[s] = (label.contains("_XX]") || label.contains("+XX"));
		}
		return hasAntecedentArray;
	}

	private boolean[] constructIsAntecedentArray() {
		boolean[] isAntecedentArray = new boolean[stateNumberer.size()];
		for (int s=0; s<isAntecedentArray.length; s++) {
			if (isSyntheticState(s)) {
				continue;
			}
			String label = (String)stateNumberer.object(s);
			isAntecedentArray[s] = (label.endsWith("_XX") || label.contains("_XX~"));
		}
		return isAntecedentArray;
	}

	private short[] constructBaseStatesArray() {
		short[] baseStatesArray = new short[stateNumberer.size()];
		for (int s=0; s<baseStatesArray.length; s++) {
			if (!isGrammarState[s]) {
				baseStatesArray[s] = (short)s;
			} else {
				String label = (String)stateNumberer.object(s);
				String baseLabel = label;
				int cutpoint = getNextLabelDelimiter(baseLabel, 0);
				if (cutpoint >= 0) {
					baseLabel = label.substring(0, cutpoint) + "^g";
				}
				baseStatesArray[s] = (short)stateNumberer.number(baseLabel);
			}
		}
		return baseStatesArray;
	}

	public Tree<String> relabelStates(Tree<Node> stateTree, List<String> sentence) {
		if (stateTree == null) {
			return null;
		}
		Node node = stateTree.getLabel();
		String label = (String)stateNumberer.object(node.state);
		if (label.endsWith("^g")) label = label.substring(0,label.length()-2);
		List<Tree<String>> children = new ArrayList<Tree<String>>(stateTree.isLeaf() ? 1 : stateTree.getChildren().size());
		if (stateTree.isLeaf()) {
			children.add(new Tree<String>(sentence.get(node.startIndex)));
			return new Tree<String>(label, children);
		}
		for (Tree<Node> child : stateTree.getChildren()) {
			children.add(relabelStates(child, sentence));
		}
		if (children.size() == 1) { // Restoring intermediate unary from chain if applicable
			int intermediateState = unaryIntermediateStates[node.state][stateTree.getChildren().get(0).getLabel().state];
			if (intermediateState>0){
				List<Tree<String>> restoredChild = new ArrayList<Tree<String>>();
				String label2 = (String)stateNumberer.object(intermediateState);
				if (label2.endsWith("^g")) label2 = label2.substring(0,label2.length()-2);
				restoredChild.add(new Tree<String>(label2, children));
				return new Tree<String>(label, restoredChild);
			}
		}
		return new Tree<String>(label, children);
	}

	public boolean isPosTag(int state) {
		return !isGrammarState[state];
	}

	public boolean isSyntheticState(int state) {
		return ((String)stateNumberer.object(state)).startsWith("@");
	}

	public String getNodeLabel(int state) {
		if (state == -1)
			return "UNK";
		if (state == NONEIndex)
			return "NONE";
		String label = (String)stateNumberer.object(state);
		if (label.endsWith("^g")) label = label.substring(0,label.length()-2);
		return label;
	}

	public int getState(String label) {
		if (stateNumberer.hasSeen(label))
			return stateNumberer.number(label);
		else if (stateNumberer.hasSeen(label + "^g"))
			return stateNumberer.number(label + "^g");
		else
			return -1;
	}

	public short getNumSubstates(int state) {
		if (state >= 0 && state < numSubStatesArray.length)
			return numSubStatesArray[state];
		else
			return 1;
	}
	
	public Node getIntermediateCoarseNode(UnaryEdge edge) {
		int intermediateState = unaryIntermediateStates[edge.parentState][edge.childState];
		if (intermediateState > 0) {
			return new Node(edge.startIndex, edge.stopIndex, intermediateState);
		}
		return null;
	}
	
	public short getBaseState(int state) {
		return baseStates[state];
	}
	
	public boolean isAntecedentState(int state) {
		return isAntecedentState[state];
	}
	
	public boolean hasAntecedent(int state) {
		return hasAntecedent[state];
	}
	
	public boolean couldBeAntecedent(int antecedentState, int anaphorState) {
		if (isAntecedentState[antecedentState]) {
			return antecedentRelations[antecedentState][anaphorState] >= 0;
		}
		return false;
	}
	
	public int getMatchedState(int antecedentState, int anaphorState) {
		if (isAntecedentState[antecedentState]) {
			return antecedentRelations[antecedentState][anaphorState];
		}
		return -1;
	}
	
	public boolean hasEmptyChildren(int state) {
		return hasEmptyChildren[state];
	}
	
	public List<Integer> getEmptyChildList(int state) {
		if (!hasEmptyChildren[state]) {
			return new ArrayList<Integer>();
		}
		List<Integer> justEmpties = new ArrayList<Integer>(emptyChildren[state].length);
		for (int child : emptyChildren[state]) {
			if (child >= 0 || child == NONEIndex) {
				justEmpties.add(child);
			}
		}
		return justEmpties;
	}
	
	public int[] getFullChildList(int state, int[] observedChildren) {
		if (!hasEmptyChildren[state]) {
			return observedChildren;
		}
		List<Integer> fullList = new ArrayList<Integer>();
		int observedIndex = 0;
		for (int i=0; i<emptyChildren[state].length; i++) {
			if (emptyChildren[state][i] < 0 && emptyChildren[state][i] != NONEIndex) {
				if (observedIndex < observedChildren.length) {
					fullList.add(observedChildren[observedIndex++]);
				}
			} else {
				fullList.add(emptyChildren[state][i]);
			}
		}
		for ( ; observedIndex<observedChildren.length; observedIndex++) {
			fullList.add(observedChildren[observedIndex]);
		}
		return Lists.toPrimitiveArray(fullList);
	}
}
