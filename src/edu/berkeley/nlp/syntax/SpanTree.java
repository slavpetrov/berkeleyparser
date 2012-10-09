package edu.berkeley.nlp.syntax;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SpanTree<L> {
	int start, end;
	L label;
	List<SpanTree<L>> children;

	// inheritance should take care of that but i am too stupid... slav
	public List<SpanTree<L>> getChildren() {
		return children;
	}

	public boolean isLeaf() {
		return getChildren().isEmpty();
	}

	public boolean isPreTerminal() {
		return getChildren().size() == 1 && getChildren().get(0).isLeaf();
	}

	public void setChildren(List<SpanTree<L>> c) {
		this.children = c;
	}

	public SpanTree(L label) {
		this.label = label;
		this.children = Collections.emptyList();
		this.start = this.end = -1;
	}

	public L getLabel() {
		return label;
	}

	public int getEnd() {
		return end;
	}

	public void setEnd(int end) {
		this.end = end;
	}

	public int getStart() {
		return start;
	}

	public void setStart(int start) {
		this.start = start;
	}

	/*
	 * public SpanTree(L label, List<SpanTree<L>> children, int start, int end)
	 * { super(label, children); // TODO Auto-generated constructor stub
	 * this.start = start; this.end = end; }
	 */

	public void setSpans() {
		List<L> yield = new ArrayList<L>();
		setSpansHelper(this, yield);
	}

	public void setSpansHelper(SpanTree<L> tree, List<L> yield) {
		if (tree.isLeaf()) {
			int pos = yield.size();
			yield.add(tree.getLabel());
			tree.setStart(pos);
			tree.setEnd(pos + 1);
			return;
		}
		List<SpanTree<L>> children = tree.getChildren();
		for (SpanTree<L> child : children) {
			setSpansHelper(child, yield);
		}
		SpanTree<L> child1 = children.get(0);
		SpanTree<L> child2 = children.get(children.size() - 1);

		tree.setStart(child1.getStart());
		tree.setEnd(child2.getEnd());
	}

}
