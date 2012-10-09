package edu.berkeley.nlp.syntax;

import edu.berkeley.nlp.ling.HeadFinder;
import edu.berkeley.nlp.ling.CollinsHeadFinder;
import edu.berkeley.nlp.util.CollectionUtils;
import edu.berkeley.nlp.util.Pair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: aria42 Date: Apr 15, 2009
 */
public class GrammaticalRelation {

	public static Pair<Tree<String>, Tree<String>> getSubjectObject(
			TreePathFinder<String> tpf, Tree<String> vpNode) {
		if (!vpNode.getLabel().startsWith("VP")) {
			throw new IllegalArgumentException(vpNode + " is not a VP");
		}
		List<Tree<String>> childs = vpNode.getChildren();
		// Subject
		Tree<String> subj = null;
		for (Tree<String> node : tpf.getRoot().getPostOrderTraversal()) {
			if (node.getLabel().startsWith("NP")) {
				Tree<String> lcaNode = tpf.findLowestCommonAncestor(node,
						vpNode);
				if (lcaNode.getLabel().startsWith("S")
						&& tpf.findParent(node) == lcaNode) {
					subj = node;
					break;
				}
			}
		}
		// Object
		Tree<String> obj = null;
		for (int c = 0; c < childs.size(); c++) {
			Tree<String> child = childs.get(c);
			if (child.isPhrasal() && child.getLabel().startsWith("NP")) {
				obj = child;
				break;
			}
		}
		if (subj == null || obj == null)
			return null;
		return Pair.newPair(subj, obj);
	}

	private final static Set<String> isVerbs = new HashSet<String>(
			CollectionUtils.makeList("is", "was"));

	public static List<Pair<Tree<String>, Tree<String>>> getPredicateNominativePairs(
			TreePathFinder<String> tpf, Tree<String> root, HeadFinder hf) {
		List<Tree<String>> vpNodes = new ArrayList();
		for (Tree<String> node : tpf.getRoot().getPostOrderTraversal()) {
			if (node.getLabel().startsWith("VP")) {
				Pair<String, String> p = HeadFinder.Utils
						.getHeadWordAndPartOfSpeechPair(hf, node);
				String headWord = p.getFirst();
				if (isVerbs.contains(headWord.toLowerCase())) {
					vpNodes.add(node);
				}
			}
		}
		List<Pair<Tree<String>, Tree<String>>> result = new ArrayList<Pair<Tree<String>, Tree<String>>>();
		for (Tree<String> vpNode : vpNodes) {
			Pair<Tree<String>, Tree<String>> treeTreePair = getSubjectObject(
					tpf, vpNode);
			if (treeTreePair != null)
				result.add(treeTreePair);
		}
		return result;
	}

	public static void main(String[] args) {
		Tree<String> t = Trees.PennTreeReader
				.parseEasy("(ROOT (S (NP (NNP John)) (VP (VBD was) (NP (DT a) (NN man)))))");
		TreePathFinder<String> tpf = new TreePathFinder<String>(t);
		System.out.println(getPredicateNominativePairs(tpf, t,
				new CollinsHeadFinder()));
	}

}
