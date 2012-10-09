package edu.berkeley.nlp.syntax;

import java.util.List;

public class TreePath<L> {

	public enum Direction {
		UP, DOWN, DOWN_LEFT, DOWN_RIGHT;
	}

	public static class Transition<L> {

		private Tree<L> fromNode;
		private Tree<L> toNode;
		private Direction direction;

		public Transition(Tree<L> fromNode, Tree<L> toNode, Direction direction) {
			this.fromNode = fromNode;
			this.toNode = toNode;
			this.direction = direction;
		}

		public Tree<L> getFromNode() {
			return fromNode;
		}

		public Tree<L> getToNode() {
			return toNode;
		}

		public Direction getDirection() {
			return direction;
		}
	}

	private Tree<L> startNode;
	private Tree<L> endNode;
	private List<Transition<L>> transitions;

	public TreePath(List<Transition<L>> transitions) {
		if (transitions == null || transitions.size() == 0) {
			throw new IllegalArgumentException(
					"Cannot have empty transitions list");
		}
		this.transitions = transitions;
		startNode = transitions.get(0).fromNode;
		endNode = transitions.get(transitions.size() - 1).toNode;
	}

	public Tree<L> getStartNode() {
		return startNode;
	}

	public Tree<L> getEndNode() {
		return endNode;
	}

	public List<Transition<L>> getTransitions() {
		return transitions;
	}

	private String cacheStr = null;

	@Override
	public String toString() {
		if (cacheStr == null) {
			StringBuilder sb = new StringBuilder();
			sb.append("[ ");
			for (Transition<L> transition : transitions) {
				sb.append(transition.fromNode.getLabel() + " "
						+ transition.direction + " ");
			}
			sb.append(endNode.getLabel() + " ]");
			cacheStr = sb.toString();
		}
		return cacheStr;
	}
}
