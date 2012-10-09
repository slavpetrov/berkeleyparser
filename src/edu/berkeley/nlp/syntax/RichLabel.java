package edu.berkeley.nlp.syntax;

import edu.berkeley.nlp.ling.HeadFinder;
import edu.berkeley.nlp.ling.CollinsHeadFinder;
import edu.berkeley.nlp.util.Pair;

import java.util.List;
import java.util.ArrayList;
import java.io.StringReader;

/**
 * Created by IntelliJ IDEA. User: aria42 Date: Oct 25, 2008 Time: 4:04:53 PM
 */
public class RichLabel {
	private String headWord;
	private String headTag;
	private int start;
	private int stop;
	private int headIndex;
	private String label;
	private Tree<String> origNode;

	public int getSpanSize() {
		return stop - start;
	}

	public int getHeadIndex() {
		return headIndex;
	}

	public void setHeadIndex(int headIndex) {
		this.headIndex = headIndex;
	}

	public String getHeadWord() {
		return headWord;
	}

	public void setHeadWord(String headWord) {
		this.headWord = headWord;
	}

	public String getHeadTag() {
		return headTag;
	}

	public void setHeadTag(String headTag) {
		this.headTag = headTag;
	}

	public int getStart() {
		return start;
	}

	public void setStart(int start) {
		this.start = start;
	}

	public int getStop() {
		return stop;
	}

	public void setStop(int stop) {
		this.stop = stop;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public Tree<String> getOriginalNode() {
		return origNode;
	}

	public void setOriginalNode(Tree<String> origNode) {
		this.origNode = origNode;
	}

	@Override
	public String toString() {
		return String.format("%s(%s[%d]-%s)[%d,%d]", label, headWord,
				headIndex, headTag, start, stop);
	}

	private static final CollinsHeadFinder cf = new CollinsHeadFinder();

	public static Tree<RichLabel> getRichTree(Tree<String> tree) {
		return getRichTree(tree, cf);
	}

	public static Tree<RichLabel> getRichTree(Tree<String> tree,
			HeadFinder headFinder) {
		return buildRecursive(tree, headFinder, 0);
	}

	private static Pair<String, String> getHeadWordTag(Tree<String> tree,
			HeadFinder headFinder) {
		if (tree.isPreTerminal()) {
			Tree<String> term = tree.getChildren().get(0);
			return Pair.newPair(term.getLabel(), tree.getLabel());
		}
		if (tree.isLeaf()) {
			return Pair.newPair(tree.getLabel(), null);
		}
		Tree<String> head = headFinder.determineHead(tree);
		return getHeadWordTag(head, headFinder);
	}

	private static Tree<RichLabel> buildRecursive(Tree<String> tree,
			HeadFinder headFinder, int start) {
		RichLabel label = new RichLabel();
		label.setStart(start);
		label.setStop(start + tree.getYield().size());
		label.setLabel(tree.getLabel());
		label.setOriginalNode(tree);
		Pair<String, String> headWordTagPair = getHeadWordTag(tree, headFinder);
		label.setHeadWord(headWordTagPair.getFirst());
		label.setHeadTag(headWordTagPair.getSecond());
		int offset = start;
		List<Tree<RichLabel>> richChildren = new ArrayList<Tree<RichLabel>>();
		for (Tree<String> child : tree.getChildren()) {
			Tree<RichLabel> richChild = buildRecursive(child, headFinder,
					offset);
			richChildren.add(richChild);
			offset += child.getYield().size();
		}
		// Head Index
		if (tree.isPhrasal()) {
			Tree<String> headChild = headFinder.determineHead(tree);
			for (Tree<RichLabel> child : richChildren) {
				if (child.getLabel().origNode == headChild) {
					label.setHeadIndex(child.getLabel().getHeadIndex());
				}
			}
		} else {
			label.setHeadIndex(label.start);
		}
		return new Tree<RichLabel>(label, richChildren);
	}

	public static void main(String[] args) {
		String tStr = "((S (NP (DT The) (NN man)) (VP (VBD ran) (PP (IN down) (NP (DT the) (NNS stairs))))))";
		Tree<String> t = new Trees.PennTreeReader(new StringReader(tStr))
				.next();
		System.out.println("Rich Tree: "
				+ getRichTree(t, new CollinsHeadFinder()));
	}
}
