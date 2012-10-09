package edu.berkeley.nlp.syntax;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Tool for finding path relationships between nodes in a tree
 * 
 * @author David Burkett
 */

public class TreePathFinder<L> {
	private Tree<L> root;
	private IdentityHashMap<Tree<L>, List<Tree<L>>> pathsFromRoot;

	public Tree<L> getRoot() {
		return root;
	}

	public TreePathFinder(Tree<L> tree) {
		root = tree;
		pathsFromRoot = new IdentityHashMap<Tree<L>, List<Tree<L>>>(tree
				.getPreOrderTraversal().size());
		constructPaths(root, new ArrayList<Tree<L>>());
	}

	private void constructPaths(Tree<L> node, List<Tree<L>> path) {
		path.add(node);
		pathsFromRoot.put(node, path);
		for (Tree<L> child : node.getChildren()) {
			ArrayList<Tree<L>> childPath = new ArrayList<Tree<L>>(
					path.size() + 1);
			childPath.addAll(path);
			constructPaths(child, childPath);
		}
	}

	public Tree<L> findParent(Tree<L> node) {
		if (!pathsFromRoot.containsKey(node)) {
			throw new IllegalArgumentException(
					"Tree must be node in the tree used to initialize the TreePathFinder");
		}
		if (node == root) {
			return null;
		}
		List<Tree<L>> path = pathsFromRoot.get(node);
		return path.get(path.size() - 2);
	}

	public TreePath<L> findPath(Tree<L> start, Tree<L> end) {
		validateInput(start, end);
		List<TreePath.Transition<L>> transitions = new ArrayList<TreePath.Transition<L>>();
		if (start != end) {
			List<Tree<L>> startPath = pathsFromRoot.get(start);
			List<Tree<L>> endPath = pathsFromRoot.get(end);

			// Find root of common subtree
			int rootIndex = findRootIndex(startPath, endPath);

			// Transitions from start node up to root of common subtree
			for (int i = startPath.size() - 1; i > rootIndex; i--) {
				transitions.add(new TreePath.Transition<L>(startPath.get(i),
						startPath.get(i - 1), TreePath.Direction.UP));
			}

			// First transition down from root of common subtree (directional if
			// there have been up transitions)
			if (rootIndex < endPath.size() - 1) {
				TreePath.Direction postRootDirection = TreePath.Direction.DOWN;
				if (rootIndex < startPath.size() - 1) {
					postRootDirection = TreePath.Direction.DOWN_RIGHT;
					for (Tree<L> rootChild : startPath.get(rootIndex)
							.getChildren()) {
						if (startPath.get(rootIndex + 1) == rootChild) {
							break;
						}
						if (endPath.get(rootIndex + 1) == rootChild) {
							postRootDirection = TreePath.Direction.DOWN_LEFT;
							break;
						}
					}
				}
				transitions.add(new TreePath.Transition<L>(endPath
						.get(rootIndex), endPath.get(rootIndex + 1),
						postRootDirection));
			}

			// Remaining transitions down to end node
			for (int i = rootIndex + 1; i < endPath.size() - 1; i++) {
				transitions.add(new TreePath.Transition<L>(endPath.get(i),
						endPath.get(i + 1), TreePath.Direction.DOWN));
			}
		}
		return new TreePath<L>(transitions);
	}

	private int findRootIndex(List<Tree<L>> startPath, List<Tree<L>> endPath) {
		int rootIndex = 0;
		for (Tree<L> node : startPath) {
			if (rootIndex == endPath.size() || node != endPath.get(rootIndex)) {
				break;
			}
			rootIndex++;
		}
		rootIndex--;
		return rootIndex;
	}

	public Tree<L> findLowestCommonAncestor(Tree<L> start, Tree<L> end) {
		validateInput(start, end);
		if (start == end)
			return start;
		List<Tree<L>> startPath = pathsFromRoot.get(start);
		List<Tree<L>> endPath = pathsFromRoot.get(end);
		int rootIndex = findRootIndex(startPath, endPath);
		return startPath.get(rootIndex);
	}

	private void validateInput(Tree<L> start, Tree<L> end) {
		if (start == null || end == null) {
			throw new IllegalArgumentException("Cannot provide null trees");
		}
		if (!pathsFromRoot.containsKey(start)
				|| !pathsFromRoot.containsKey(end)) {
			throw new IllegalArgumentException(
					"Both trees must be nodes in the tree used to initialize the TreePathFinder");
		}
	}
}
