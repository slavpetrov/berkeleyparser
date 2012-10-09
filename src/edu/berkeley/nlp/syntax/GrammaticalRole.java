package edu.berkeley.nlp.syntax;

import java.io.StringReader;
import java.util.List;
import java.util.Set;

import edu.berkeley.nlp.util.IdentityHashSet;
import edu.berkeley.nlp.util.functional.FunctionalUtils;
import edu.berkeley.nlp.util.functional.Predicate;

/**
 * User: aria42 Date: Mar 24, 2009
 */
public enum GrammaticalRole {
	SUBJECT, OBJECT, OTHER, NONE, NULL;

	private static boolean isObject(TreePath<String> treePath) {
		for (TreePath.Transition<String> transition : treePath.getTransitions()) {
			if (transition.getDirection() != TreePath.Direction.UP) {
				return false;
			}
		}
		return true;
	}

	private static boolean isSubject(TreePath<String> treePath) {
		boolean hitS = false;
		for (TreePath.Transition<String> trans : treePath.getTransitions()) {
			Tree<String> toNode = trans.getToNode();
			TreePath.Direction dir = trans.getDirection();
			Tree<String> dest = trans.getToNode();
			if (dest.getLabel().startsWith("S")) {
				hitS = true;
				continue;
			}
			if (!hitS) {
				if (dir != TreePath.Direction.UP) {
					return false;
				}
			}
			if (hitS) {
				if (!(dir == TreePath.Direction.DOWN_RIGHT)) {
					return false;
				}
			}
		}
		return hitS;
	}

	public static GrammaticalRole findRole(final Tree<String> node,
			final Tree<String> root) {
		Set<Tree<String>> nodes = new IdentityHashSet<Tree<String>>(
				root.getPostOrderTraversal());
		if (!nodes.contains(node)) {
			return GrammaticalRole.NONE;
		}
		GrammaticalRole curRole = GrammaticalRole.OTHER;
		List<Tree<String>> vpNodes = FunctionalUtils.filter(nodes,
				new Predicate<Tree<String>>() {
					public Boolean apply(Tree<String> input) {
						return input.isPhrasal()
								&& input.getLabel().startsWith("VP");
					}
				});
		TreePathFinder<String> tpf = new TreePathFinder<String>(root);
		for (Tree<String> vpNode : vpNodes) {
			if (vpNode == node)
				continue;
			TreePath<String> tp = tpf.findPath(node, vpNode);
			if (isSubject(tp)) {
				return GrammaticalRole.SUBJECT;
			}
			if (isObject(tp)) {
				curRole = GrammaticalRole.OBJECT;
			}
		}
		return curRole;
	}

	public static void main(String[] args) {
		String treeStr = "(ROOT (S (NP (DT The) (NN dog)) (VP (VBD ran) (NN home))))";
		StringReader reader = new StringReader(treeStr);
		Tree<String> tree = new Trees.PennTreeReader(reader).next();
		Tree<String> subjNode = tree.getChildren().get(0).getChildren().get(0);
		Tree<String> objNode = tree.getChild(0).getChild(1).getChild(1);
		System.out.println("subjNode is "
				+ GrammaticalRole.findRole(subjNode, tree));
		System.out.println("objNode is "
				+ GrammaticalRole.findRole(objNode, tree));
	}
}
