package edu.berkeley.nlp.syntax;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.berkeley.nlp.util.CollectionUtils;
import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.Factory;

/**
 * Assumes the type V is hashable
 * 
 * @author adampauls
 * 
 * @param <V>
 */
public class UnaryClosureComputer<V> {

	public static class Edge<V> {

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;

			result = prime * result + ((child == null) ? 0 : child.hashCode());
			result = prime * result
					+ ((parent == null) ? 0 : parent.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Edge other = (Edge) obj;

			if (child == null) {
				if (other.child != null)
					return false;
			} else if (!child.equals(other.child))
				return false;
			if (parent == null) {
				if (other.parent != null)
					return false;
			} else if (!parent.equals(other.parent))
				return false;
			return true;
		}

		public void setParent(V parent) {
			this.parent = parent;
		}

		public void setChild(V child) {
			this.child = child;
		}

		private V parent;

		private V child;

		private double score;

		private Edge(V parent, V child) {
			this.parent = parent;
			this.child = child;
		}

		public V getParent() {
			return parent;
		}

		public V getChild() {
			return child;
		}

		public double getScore() {
			return score;
		}

		public void setScore(double d) {
			score = d;
		}

	}

	private Factory<Edge> unaryRuleFactory = new Factory<Edge>() {

		public Edge newInstance(Object... args) {
			return new Edge(args[0], args[1]);
		}
	};

	Map<V, List<Edge<V>>> closedUnaryRulesByChild = new HashMap<V, List<Edge<V>>>();

	Map<V, List<Edge<V>>> closedUnaryRulesByParent = new HashMap<V, List<Edge<V>>>();

	Map<Edge<V>, List<V>> pathMap = new HashMap<Edge<V>, List<V>>();

	Set<Edge<V>> unaryRules = new HashSet<Edge<V>>();

	private boolean sumInsteadOfMultipy;

	/**
	 * First is parent, second is child;
	 * 
	 * @return
	 */
	public Map<V, List<Edge<V>>> getAllClosedRulesByChildren() {
		return closedUnaryRulesByChild;
	}

	public List<Edge<V>> getClosedUnaryRulesByChild(V child) {
		return CollectionUtils.getValueList(closedUnaryRulesByChild, child);
	}

	public List<Edge<V>> getClosedUnaryRulesByParent(V parent) {
		return CollectionUtils.getValueList(closedUnaryRulesByParent, parent);
	}

	public List<V> getPath(Edge unaryRule) {
		return pathMap.get(unaryRule);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (V parent : closedUnaryRulesByParent.keySet()) {
			for (Edge unaryRule : getClosedUnaryRulesByParent(parent)) {
				List<V> path = getPath(unaryRule);
				// if (path.size() == 2) continue;
				sb.append(unaryRule);
				sb.append("  ");
				sb.append(path);
				sb.append("\n");
			}
		}
		return sb.toString();
	}

	public UnaryClosureComputer(boolean sumInsteadOfMultiply) {
		this.sumInsteadOfMultipy = sumInsteadOfMultiply;
	}

	public void add(V parent, V child, double score) {
		final Edge edge = new Edge(parent, child);
		edge.setScore(score);
		unaryRules.add(edge);
	}

	public void solve() {
		Map<Edge<V>, List<V>> closureMap = computeUnaryClosure(unaryRules);
		for (Edge<V> unaryRule : closureMap.keySet()) {
			addUnary(unaryRule, closureMap.get(unaryRule));
		}
	}

	private void addUnary(Edge<V> unaryRule, List<V> path) {
		CollectionUtils.addToValueList(closedUnaryRulesByChild,
				unaryRule.getChild(), unaryRule);
		CollectionUtils.addToValueList(closedUnaryRulesByParent,
				unaryRule.getParent(), unaryRule);
		pathMap.put(unaryRule, path);
	}

	private Map<Edge<V>, List<V>> computeUnaryClosure(
			Collection<Edge<V>> unaryRules) {

		Map<Edge<V>, V> intermediateStates = new HashMap<Edge<V>, V>();
		Counter<Edge<V>> pathCosts = new Counter<Edge<V>>();
		Map<V, List<Edge<V>>> closedUnaryRulesByChild = new HashMap<V, List<Edge<V>>>();
		Map<V, List<Edge<V>>> closedUnaryRulesByParent = new HashMap<V, List<Edge<V>>>();

		Set<V> states = new HashSet<V>();

		for (Edge<V> unaryRule : unaryRules) {
			relax(pathCosts, intermediateStates, closedUnaryRulesByChild,
					closedUnaryRulesByParent, unaryRule, null,
					unaryRule.getScore());
			states.add(unaryRule.getParent());
			states.add(unaryRule.getChild());
		}

		for (V intermediateState : states) {
			List<Edge<V>> incomingRules = closedUnaryRulesByChild
					.get(intermediateState);
			List<Edge<V>> outgoingRules = closedUnaryRulesByParent
					.get(intermediateState);
			if (incomingRules == null || outgoingRules == null)
				continue;
			for (Edge<V> incomingRule : incomingRules) {
				for (Edge<V> outgoingRule : outgoingRules) {
					Edge<V> rule = unaryRuleFactory.newInstance(
							incomingRule.getParent(), outgoingRule.getChild());
					double newScore = combinePathCosts(pathCosts, incomingRule,
							outgoingRule);
					relax(pathCosts, intermediateStates,
							closedUnaryRulesByChild, closedUnaryRulesByParent,
							rule, intermediateState, newScore);
				}
			}
		}

		for (V state : states)

		{
			Edge<V> selfLoopRule = unaryRuleFactory.newInstance(state, state);
			relax(pathCosts, intermediateStates, closedUnaryRulesByChild,
					closedUnaryRulesByParent, selfLoopRule, null, 0.0);
		}

		Map<Edge<V>, List<V>> closureMap = new HashMap<Edge<V>, List<V>>();

		for (Edge<V> unaryRule : pathCosts.keySet()) {
			unaryRule.setScore(pathCosts.getCount(unaryRule));
			List<V> path = extractPath(unaryRule, intermediateStates);
			closureMap.put(unaryRule, path);
		}

		return closureMap;

	}

	/**
	 * @param pathCosts
	 * @param incomingRule
	 * @param outgoingRule
	 * @return
	 */
	private double combinePathCosts(Counter<Edge<V>> pathCosts,
			Edge<V> incomingRule, Edge<V> outgoingRule) {
		return this.sumInsteadOfMultipy ? (pathCosts.getCount(incomingRule) + pathCosts
				.getCount(outgoingRule))
				: (pathCosts.getCount(incomingRule) * pathCosts
						.getCount(outgoingRule));
	}

	private List<V> extractPath(Edge<V> unaryRule,
			Map<Edge<V>, V> intermediateStates) {
		List<V> path = new ArrayList<V>();
		path.add(unaryRule.getParent());
		V intermediateState = intermediateStates.get(unaryRule);
		if (intermediateState != null) {
			List<V> parentPath = extractPath(unaryRuleFactory.newInstance(
					unaryRule.getParent(), intermediateState),
					intermediateStates);
			for (int i = 1; i < parentPath.size() - 1; i++) {
				V state = parentPath.get(i);
				path.add(state);
			}
			path.add(intermediateState);
			List<V> childPath = extractPath(
					unaryRuleFactory.newInstance(intermediateState,
							unaryRule.getChild()), intermediateStates);
			for (int i = 1; i < childPath.size() - 1; i++) {
				V state = childPath.get(i);
				path.add(state);
			}
		}
		if (path.size() == 1 && unaryRule.getParent() == unaryRule.getChild())
			return path;
		path.add(unaryRule.getChild());
		return path;
	}

	private void relax(Counter<Edge<V>> pathCosts,
			Map<Edge<V>, V> intermediateStates,
			Map<V, List<Edge<V>>> closedUnaryRulesByChild,
			Map<V, List<Edge<V>>> closedUnaryRulesByParent, Edge<V> unaryRule,
			V intermediateState, double newScore) {
		if (intermediateState != null
				&& (intermediateState.equals(unaryRule.getParent()) || intermediateState
						.equals(unaryRule.getChild())))
			return;
		boolean isNewRule = !pathCosts.containsKey(unaryRule);
		double oldScore = (isNewRule ? Double.NEGATIVE_INFINITY : pathCosts
				.getCount(unaryRule));
		if (oldScore > newScore)
			return;
		if (isNewRule) {
			CollectionUtils.addToValueList(closedUnaryRulesByChild,
					unaryRule.getChild(), unaryRule);
			CollectionUtils.addToValueList(closedUnaryRulesByParent,
					unaryRule.getParent(), unaryRule);
		}
		pathCosts.setCount(unaryRule, newScore);
		intermediateStates.put(unaryRule, intermediateState);
	}

	public double getProb(V parent, V child) {
		if (parent == child)
			return 0.0;
		final List<Edge<V>> byParent = closedUnaryRulesByParent.get(parent);
		if (byParent == null)
			return Double.POSITIVE_INFINITY;
		int childIndex = byParent.indexOf(unaryRuleFactory.newInstance(parent,
				child));
		if (childIndex < 0)
			return Double.POSITIVE_INFINITY;
		final Edge<V> unaryRule = byParent.get(childIndex);

		return unaryRule.getScore();
	}

}
