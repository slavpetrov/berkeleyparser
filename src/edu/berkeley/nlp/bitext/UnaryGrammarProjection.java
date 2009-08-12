package edu.berkeley.nlp.bitext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
//
//import edu.berkeley.nlp.parser.Grammar;
//import edu.berkeley.nlp.parser.Rule;
//import edu.berkeley.nlp.parser.UnaryRule;
import edu.berkeley.nlp.bitext.GrammarStateFactory.GrammarState;
import edu.berkeley.nlp.syntax.Tree;
import fig.basic.Pair;

public class UnaryGrammarProjection {
	
	private Grammar grammar;

	List<List<UnaryRule>> closedUnaryRulesByChild = new ArrayList<List<UnaryRule>>();
	public List<List<UnaryRule>> closedUnaryRulesByParent = new ArrayList<List<UnaryRule>>();
    private Map<UnaryRule, List<GrammarState>> pathMap = new HashMap<UnaryRule, List<GrammarState>>();
    
    private static <R>  void  grow(List<R> lst, int size) {
		for (int i=lst.size(); i < size; ++i) {
			lst.add(null);
		}		
	}
	
	private static <R extends Rule> void addToRuleIndex(List<List<R>> ruleLists, GrammarState state, R rule) {
		grow(ruleLists, state.id()+1);
		List<R> ruleList = ruleLists.get(state.id());
		if (ruleList == null) {
			ruleList = new ArrayList<R>();
			ruleLists.set(state.id(), ruleList);
		}
		ruleList.add(rule);
	}
	
	
	public UnaryGrammarProjection(Grammar grammar) {
		this.grammar = grammar;
		
		//Dijkstra Shortest Path
		Map< Pair<GrammarState, GrammarState>, Double > edgeCostMap = new HashMap< Pair<GrammarState, GrammarState>, Double>(); 
		for (UnaryRule ur: grammar.getUnarys()) {
			Pair<GrammarState, GrammarState> edge = new Pair<GrammarState, GrammarState>(ur.parent(), ur.child());
			edgeCostMap.put( edge, -ur.getScore() );
		}
		AllPairShortestPath<GrammarState> apsp = new DijkstraAllPairsShortestPath<GrammarState>();
		Map< Pair<GrammarState, GrammarState>, Pair<Double, List<GrammarState>>> allShortestPaths = apsp.getShortestPaths(edgeCostMap);
		
		for (Map.Entry< Pair<GrammarState,GrammarState>,  Pair<Double, List<GrammarState>> > entry: allShortestPaths.entrySet()) {
			GrammarState parent = entry.getKey().getFirst();
			GrammarState child = entry.getKey().getSecond();
			UnaryRule ur = new UnaryRule(parent, child);
			double score = -entry.getValue().getFirst();
			assert score <= 0;
			ur.setScore(score);
			List<GrammarState> path = entry.getValue().getSecond();
			pathMap.put(ur, path);
			addToRuleIndex(closedUnaryRulesByParent, parent, ur);
			addToRuleIndex(closedUnaryRulesByChild, child, ur);
		}				
	}
	
	List<UnaryRule> emptyList = Collections.unmodifiableList( new ArrayList<UnaryRule>() ); 
	
	public List<UnaryRule> getClosedUnarysByParent(int parentId) {
		if (parentId  >= closedUnaryRulesByParent.size()) {
			return emptyList;
		}		
		List<UnaryRule> unarys = closedUnaryRulesByParent.get(parentId);
		if (unarys == null) return emptyList;
		return unarys;
	}
	
	public List<UnaryRule> getClosedUnarysByParent(GrammarState parent) {
		return getClosedUnarysByParent(parent.id());
	}
	
		
	public List<UnaryRule> getClosedUnarysByChild(int childId) {
		if (childId >= closedUnaryRulesByChild.size()) {
			return emptyList;
		}
		List<UnaryRule> unarys = closedUnaryRulesByChild.get(childId);
		if (unarys == null) return emptyList;
		return closedUnaryRulesByChild.get(childId);	
	}
	
	public List<UnaryRule> getClosedUnarysByChild(GrammarState child) {
		return getClosedUnarysByChild(child.id());		
	}
		
	public Tree<String> addUnaryChains(Tree<String> tree) {
		return addUnaryChainsRec(tree);		
	}
	
	public Tree<String> addUnaryChainsRec(Tree<String> tree) {
//		List<Tree<String>> children = tree.getChildren();
//		String parentLabel = tree.getLabel();
//		if (tree.isLeaf() || tree.isPreTerminal()) {
//			return tree;
//		}				
//		if (children.size() > 2) {
//			List<Tree<String>> transformedChildren = new ArrayList<Tree<String>>();
//			for (Tree<String> child: transformedChildren) {
//				transformedChildren.add( addUnaryChainsRec(child) );
//			}
//			return new Tree<String>(parentLabel, transformedChildren);
//		}
//		String childLabel = children.get(0).getLabel();
//		UnaryRule ur = grammar.getUnaryRule(parentLabel, childLabel);
//		List<GrammarState> path = pathMap.get(ur);
//		Tree<String> curChild = children.get(0);
//		// Everything but the first and last elems
//		// in the path. In reverse order
//		for (int i = path.size()-2 ; i > 0  ; --i) {
//			String label = path.get(i).label();
//			curChild = new Tree<String>( label, Collections.singletonList(curChild) );
//		}
//		return new Tree<String>( parentLabel, Collections.singletonList(curChild) );
		if (tree.isPreTerminal()) {
			return tree;
		}
		List<Tree<String>> transformedChildren = new ArrayList<Tree<String>>();
		for (Tree<String> child: tree.getChildren()) {
			child = addUnaryChainsRec(child);
			transformedChildren.add( child );
		}
		if (tree.getChildren().size() > 1) {
			return new Tree<String>(tree.getLabel(), transformedChildren);
		}
		// Unary		
		Tree<String> child = transformedChildren.get(0);
		String childLabel = child.getLabel();
		String parentLabel = tree.getLabel();
		UnaryRule ur = grammar.getUnaryRule(parentLabel, childLabel);
		List<GrammarState> path = pathMap.get(ur);
		for (int i = path.size()-2 ; i >  0  ; --i) {
			String label = path.get(i).label();
			child = new Tree<String>( label, Collections.singletonList(child) );
		}
		return new Tree<String>( parentLabel, Collections.singletonList(child) );
	}
	
	public List<GrammarState> getBestPath(GrammarState parent, GrammarState child) {
		UnaryRule ur = new UnaryRule(parent,child);
		return pathMap.get(ur);
	}
	
	public static void main(String[] args) {
		
	}
	
}
