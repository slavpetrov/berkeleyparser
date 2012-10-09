package edu.berkeley.nlp.syntax;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import edu.berkeley.nlp.util.CollectionUtils;
import edu.berkeley.nlp.util.MapFactory;
import edu.berkeley.nlp.util.MyMethod;
import edu.berkeley.nlp.util.Pair;

/**
 * Represent linguistic trees, with each node consisting of a label and a list
 * of children.
 * 
 * @author Dan Klein
 * 
 *         Added function to get a map of subtrees to constituents.
 */
public class Tree<L> implements Serializable, Comparable<Tree<L>>,
		Iterable<Tree<L>> {

	private static final long serialVersionUID = 1L;

	L label;

	List<Tree<L>> children;

	public void setChild(int i, Tree<L> child) {
		children.set(i, child);
	}

	public void setChildren(List<Tree<L>> c) {
		this.children = c;
	}

	public List<Tree<L>> getChildren() {
		return children;
	}

	public Tree<L> getChild(int i) {
		return children.get(i);
	}

	public L getLabel() {
		return label;
	}

	public boolean isLeaf() {
		return getChildren().isEmpty();
	}

	public boolean isPreTerminal() {
		return getChildren().size() == 1 && getChildren().get(0).isLeaf();
	}

	public List<L> getYield() {
		List<L> yield = new ArrayList<L>();
		appendYield(this, yield);
		return yield;
	}

	public Collection<Constituent<L>> getConstituentCollection() {
		Collection<Constituent<L>> constituents = new ArrayList<Constituent<L>>();
		appendConstituent(this, constituents, 0);
		return constituents;
	}

	/**
	 * John: I changed this from a hash map because it was broken as a HashMap.
	 */
	public Map<Tree<L>, Constituent<L>> getConstituents() {
		Map<Tree<L>, Constituent<L>> constituents = new IdentityHashMap<Tree<L>, Constituent<L>>();
		appendConstituent(this, constituents, 0);
		return constituents;
	}

	public Map<Pair<Integer, Integer>, List<Tree<L>>> getSpanMap() {
		Map<Tree<L>, Constituent<L>> cMap = getConstituents();
		Map<Pair<Integer, Integer>, List<Tree<L>>> spanMap = new HashMap();
		for (Map.Entry<Tree<L>, Constituent<L>> entry : cMap.entrySet()) {
			Tree<L> t = entry.getKey();
			Constituent<L> c = entry.getValue();
			Pair<Integer, Integer> span = Pair.newPair(c.getStart(),
					c.getEnd() + 1);
			CollectionUtils.addToValueList(spanMap, span, t);
		}
		for (List<Tree<L>> trees : spanMap.values()) {
			Collections.sort(trees, new Comparator<Tree<L>>() {
				public int compare(Tree<L> t1, Tree<L> t2) {
					return t2.getDepth() - t1.getDepth();
				}
			});
		}
		return spanMap;
	}

	public Map<Tree<L>, Constituent<L>> getConstituents(MapFactory mf) {
		Map<Tree<L>, Constituent<L>> constituents = mf.buildMap();
		appendConstituent(this, constituents, 0);
		return constituents;
	}

	private static <L> int appendConstituent(Tree<L> tree,
			Map<Tree<L>, Constituent<L>> constituents, int index) {
		if (tree.isLeaf()) {
			Constituent<L> c = new Constituent<L>(tree.getLabel(), index, index);
			constituents.put(tree, c);
			return 1; // Length of a leaf constituent
		} else {
			int nextIndex = index;
			for (Tree<L> kid : tree.getChildren()) {
				nextIndex += appendConstituent(kid, constituents, nextIndex);
			}
			Constituent<L> c = new Constituent<L>(tree.getLabel(), index,
					nextIndex - 1);
			constituents.put(tree, c);
			return nextIndex - index; // Length of a leaf constituent
		}
	}

	private static <L> int appendConstituent(Tree<L> tree,
			Collection<Constituent<L>> constituents, int index) {
		if (tree.isLeaf() || tree.isPreTerminal()) {
			Constituent<L> c = new Constituent<L>(tree.getLabel(), index, index);
			constituents.add(c);
			return 1; // Length of a leaf constituent
		} else {
			int nextIndex = index;
			for (Tree<L> kid : tree.getChildren()) {
				nextIndex += appendConstituent(kid, constituents, nextIndex);
			}
			Constituent<L> c = new Constituent<L>(tree.getLabel(), index,
					nextIndex - 1);
			constituents.add(c);
			return nextIndex - index; // Length of a leaf constituent
		}
	}

	private static <L> void appendNonTerminals(Tree<L> tree, List<Tree<L>> yield) {
		if (tree.isLeaf()) {

			return;
		}
		yield.add(tree);
		for (Tree<L> child : tree.getChildren()) {
			appendNonTerminals(child, yield);
		}
	}

	public List<Tree<L>> getTerminals() {
		List<Tree<L>> yield = new ArrayList<Tree<L>>();
		appendTerminals(this, yield);
		return yield;
	}

	public List<Tree<L>> getNonTerminals() {
		List<Tree<L>> yield = new ArrayList<Tree<L>>();
		appendNonTerminals(this, yield);
		return yield;
	}

	private static <L> void appendTerminals(Tree<L> tree, List<Tree<L>> yield) {
		if (tree.isLeaf()) {
			yield.add(tree);
			return;
		}
		for (Tree<L> child : tree.getChildren()) {
			appendTerminals(child, yield);
		}
	}

	/**
	 * Clone the structure of the tree. Unfortunately, the new labels are copied
	 * by reference from the current tree.
	 * 
	 * @return
	 */
	public Tree<L> shallowClone() {
		ArrayList<Tree<L>> newChildren = new ArrayList<Tree<L>>(children.size());
		for (Tree<L> child : children) {
			newChildren.add(child.shallowClone());
		}
		return new Tree<L>(label, newChildren);
	}

	/**
	 * Return a clone of just the root node of this tree (with no children)
	 * 
	 * @return
	 */
	public Tree<L> shallowCloneJustRoot() {

		return new Tree<L>(label);
	}

	private static <L> void appendYield(Tree<L> tree, List<L> yield) {
		if (tree.isLeaf()) {
			yield.add(tree.getLabel());
			return;
		}
		for (Tree<L> child : tree.getChildren()) {
			appendYield(child, yield);
		}
	}

	public List<L> getPreTerminalYield() {
		List<L> yield = new ArrayList<L>();
		appendPreTerminalYield(this, yield);
		return yield;
	}

	public List<L> getTerminalYield() {
		List<Tree<L>> terms = getTerminals();
		List<L> yield = new ArrayList<L>();
		for (Tree<L> term : terms) {
			yield.add(term.getLabel());
		}
		return yield;
	}

	public List<Tree<L>> getPreTerminals() {
		List<Tree<L>> preterms = new ArrayList<Tree<L>>();
		appendPreTerminals(this, preterms);
		return preterms;
	}

	public List<Tree<L>> getTreesOfDepth(int depth) {
		List<Tree<L>> trees = new ArrayList<Tree<L>>();
		appendTreesOfDepth(this, trees, depth);
		return trees;
	}

	private static <L> void appendPreTerminalYield(Tree<L> tree, List<L> yield) {
		if (tree.isPreTerminal()) {
			yield.add(tree.getLabel());
			return;
		}
		for (Tree<L> child : tree.getChildren()) {
			appendPreTerminalYield(child, yield);
		}
	}

	private static <L> void appendPreTerminals(Tree<L> tree, List<Tree<L>> yield) {
		if (tree.isPreTerminal()) {
			yield.add(tree);
			return;
		}
		for (Tree<L> child : tree.getChildren()) {
			appendPreTerminals(child, yield);
		}
	}

	private static <L> void appendTreesOfDepth(Tree<L> tree,
			List<Tree<L>> yield, int depth) {
		if (tree.getDepth() == depth) {
			yield.add(tree);
			return;
		}
		for (Tree<L> child : tree.getChildren()) {
			appendTreesOfDepth(child, yield, depth);
		}
	}

	public List<Tree<L>> getPreOrderTraversal() {
		ArrayList<Tree<L>> traversal = new ArrayList<Tree<L>>();
		traversalHelper(this, traversal, true);
		return traversal;
	}

	public List<Tree<L>> getPostOrderTraversal() {
		ArrayList<Tree<L>> traversal = new ArrayList<Tree<L>>();
		traversalHelper(this, traversal, false);
		return traversal;
	}

	private static <L> void traversalHelper(Tree<L> tree,
			List<Tree<L>> traversal, boolean preOrder) {
		if (preOrder)
			traversal.add(tree);
		for (Tree<L> child : tree.getChildren()) {
			traversalHelper(child, traversal, preOrder);
		}
		if (!preOrder)
			traversal.add(tree);
	}

	public int getDepth() {
		int maxDepth = 0;
		for (Tree<L> child : children) {
			int depth = child.getDepth();
			if (depth > maxDepth)
				maxDepth = depth;
		}
		return maxDepth + 1;
	}

	public int size() {
		int sum = 0;
		for (Tree<L> child : children) {
			sum += child.size();
		}
		return sum + 1;
	}

	public List<Tree<L>> getAtDepth(int depth) {
		List<Tree<L>> yield = new ArrayList<Tree<L>>();
		appendAtDepth(depth, this, yield);
		return yield;
	}

	private static <L> void appendAtDepth(int depth, Tree<L> tree,
			List<Tree<L>> yield) {
		if (depth < 0)
			return;
		if (depth == 0) {
			yield.add(tree);
			return;
		}
		for (Tree<L> child : tree.getChildren()) {
			appendAtDepth(depth - 1, child, yield);
		}
	}

	public void setLabel(L label) {
		this.label = label;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		toStringBuilder(sb);
		return sb.toString();
	}

	public void toStringBuilder(StringBuilder sb) {
		if (!isLeaf())
			sb.append('(');
		if (getLabel() != null) {
			sb.append(getLabel());
		}
		if (!isLeaf()) {
			for (Tree<L> child : getChildren()) {
				sb.append(' ');
				child.toStringBuilder(sb);
			}
			sb.append(')');
		}
	}

	/**
	 * Same as toString(), but escapes terminals like so: ( becomes -LRB- )
	 * becomes -RRB- \ becomes -BACKSLASH- ("\" does not occur in PTB; this is
	 * our own convention) This is useful because otherwise it's hard to tell a
	 * "(" terminal from the tree's bracket structure, or tell an escaping \
	 * from a literal.
	 */
	public String toEscapedString() {
		StringBuilder sb = new StringBuilder();
		toStringBuilderEscaped(sb);
		return sb.toString();
	}

	public void toStringBuilderEscaped(StringBuilder sb) {
		if (!isLeaf())
			sb.append('(');
		if (getLabel() != null) {
			if (isLeaf()) {
				String escapedLabel = getLabel().toString();
				escapedLabel = escapedLabel.replaceAll("\\(", "-LRB-");
				escapedLabel = escapedLabel.replaceAll("\\)", "-RRB-");
				escapedLabel = escapedLabel.replaceAll("\\\\", "-BACKSLASH-");
				sb.append(escapedLabel);
			} else {
				sb.append(getLabel());
			}
		}
		if (!isLeaf()) {
			for (Tree<L> child : getChildren()) {
				sb.append(' ');
				child.toStringBuilderEscaped(sb);
			}
			sb.append(')');
		}
	}

	public Tree(L label, List<Tree<L>> children) {
		this.label = label;
		this.children = children;
	}

	public Tree(L label) {
		this.label = label;
		this.children = Collections.emptyList();
	}

	/**
	 * Get the set of all subtrees inside the tree by returning a tree rooted at
	 * each node. These are <i>not</i> copies, but all share structure. The tree
	 * is regarded as a subtree of itself.
	 * 
	 * @return the <code>Set</code> of all subtrees in the tree.
	 */
	public Set<Tree<L>> subTrees() {
		return (Set<Tree<L>>) subTrees(new HashSet<Tree<L>>());
	}

	/**
	 * Get the list of all subtrees inside the tree by returning a tree rooted
	 * at each node. These are <i>not</i> copies, but all share structure. The
	 * tree is regarded as a subtree of itself.
	 * 
	 * @return the <code>List</code> of all subtrees in the tree.
	 */
	public List<Tree<L>> subTreeList() {
		return (List<Tree<L>>) subTrees(new ArrayList<Tree<L>>());
	}

	/**
	 * Add the set of all subtrees inside a tree (including the tree itself) to
	 * the given <code>Collection</code>.
	 * 
	 * @param n
	 *            A collection of nodes to which the subtrees will be added
	 * @return The collection parameter with the subtrees added
	 */
	public Collection<Tree<L>> subTrees(Collection<Tree<L>> n) {
		n.add(this);
		List<Tree<L>> kids = getChildren();
		for (Tree<L> kid : kids) {
			kid.subTrees(n);
		}
		return n;
	}

	/**
	 * Returns an iterator over the nodes of the tree. This method implements
	 * the <code>iterator()</code> method required by the
	 * <code>Collections</code> interface. It does a preorder (children after
	 * node) traversal of the tree. (A possible extension to the class at some
	 * point would be to allow different traversal orderings via variant
	 * iterators.)
	 * 
	 * @return An iterator over the nodes of the tree
	 */
	public Iterator<Tree<L>> iterator() {
		return new TreeIterator();
	}

	private class TreeIterator implements Iterator<Tree<L>> {

		private List<Tree<L>> treeStack;

		private TreeIterator() {
			treeStack = new ArrayList<Tree<L>>();
			treeStack.add(Tree.this);
		}

		public boolean hasNext() {
			return (!treeStack.isEmpty());
		}

		public Tree<L> next() {
			int lastIndex = treeStack.size() - 1;
			Tree<L> tr = treeStack.remove(lastIndex);
			List<Tree<L>> kids = tr.getChildren();
			// so that we can efficiently use one List, we reverse them
			for (int i = kids.size() - 1; i >= 0; i--) {
				treeStack.add(kids.get(i));
			}
			return tr;
		}

		/**
		 * Not supported
		 */
		public void remove() {
			throw new UnsupportedOperationException();
		}

	}

	/**
	 * Applies a transformation to all labels in the tree and returns the
	 * resulting tree.
	 * 
	 * @param <O>
	 *            Output type of the transformation
	 * @param trans
	 *            The transformation to apply
	 * @return Transformed tree
	 */
	public <O> Tree<O> transformNodes(MyMethod<L, O> trans) {
		ArrayList<Tree<O>> newChildren = new ArrayList<Tree<O>>(children.size());
		for (Tree<L> child : children) {
			newChildren.add(child.transformNodes(trans));
		}
		return new Tree<O>(trans.call(label), newChildren);
	}

	/**
	 * Applies a transformation to all nodes in the tree and returns the
	 * resulting tree. Different from <code>transformNodes</code> in that you
	 * get the full node and not just the label
	 * 
	 * @param <O>
	 * @param trans
	 * @return
	 */
	public <O> Tree<O> transformNodesUsingNode(MyMethod<Tree<L>, O> trans) {
		ArrayList<Tree<O>> newChildren = new ArrayList<Tree<O>>(children.size());
		O newLabel = trans.call(this);
		for (Tree<L> child : children) {
			newChildren.add(child.transformNodesUsingNode(trans));
		}
		return new Tree<O>(newLabel, newChildren);
	}

	public <O> Tree<O> transformNodesUsingNodePostOrder(
			MyMethod<Tree<L>, O> trans) {
		ArrayList<Tree<O>> newChildren = new ArrayList<Tree<O>>(children.size());
		for (Tree<L> child : children) {
			newChildren.add(child.transformNodesUsingNode(trans));
		}
		O newLabel = trans.call(this);
		return new Tree<O>(newLabel, newChildren);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((label == null) ? 0 : label.hashCode());
		for (Tree<L> child : children) {
			result = prime * result + ((child == null) ? 0 : child.hashCode());
		}
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
		if (!(obj instanceof Tree))
			return false;
		final Tree<L> other = (Tree<L>) obj;
		if (!this.label.equals(other.label))
			return false;
		if (this.getChildren().size() != other.getChildren().size())
			return false;
		for (int i = 0; i < getChildren().size(); ++i) {

			if (!getChildren().get(i).equals(other.getChildren().get(i)))
				return false;
		}
		return true;

	}

	public int compareTo(Tree<L> o) {
		if (!(o.getLabel() instanceof Comparable && getLabel() instanceof Comparable))
			throw new IllegalArgumentException("Tree labels are not comparable");
		int cmp = ((Comparable) o.getLabel()).compareTo(getLabel());
		if (cmp != 0)
			return cmp;
		int cmp2 = Double.compare(this.getChildren().size(), o.getChildren()
				.size());
		if (cmp2 != 0)
			return cmp2;
		for (int i = 0; i < getChildren().size(); ++i) {

			int cmp3 = getChildren().get(i).compareTo(o.getChildren().get(i));
			if (cmp3 != 0)
				return cmp3;
		}
		return 0;

	}

	public boolean isPhrasal() {
		return getYield().size() > 1;
	}

	public Constituent<L> getLeastCommonAncestorConstituent(int i, int j) {
		final List<L> yield = getYield();
		final Constituent<L> leastCommonAncestorConstituentHelper = getLeastCommonAncestorConstituentHelper(
				this, 0, yield.size(), i, j);

		return leastCommonAncestorConstituentHelper;
	}

	public Tree<L> getTopTreeForSpan(int i, int j) {
		final List<L> yield = getYield();
		return getTopTreeForSpanHelper(this, 0, yield.size(), i, j);
	}

	private static <L> Tree<L> getTopTreeForSpanHelper(Tree<L> tree, int start,
			int end, int i, int j) {

		assert i <= j;
		if (start == i && end == j) {
			assert tree.getLabel().toString().matches("\\w+");
			return tree;
		}

		Queue<Tree<L>> queue = new LinkedList<Tree<L>>();
		queue.addAll(tree.getChildren());
		int currStart = start;
		while (!queue.isEmpty()) {
			Tree<L> remove = queue.remove();
			List<L> currYield = remove.getYield();
			final int currEnd = currStart + currYield.size();
			if (currStart <= i && currEnd >= j)
				return getTopTreeForSpanHelper(remove, currStart, currEnd, i, j);
			currStart += currYield.size();
		}
		return null;
	}

	private static <L> Constituent<L> getLeastCommonAncestorConstituentHelper(
			Tree<L> tree, int start, int end, int i, int j) {

		if (start == i && end == j)
			return new Constituent<L>(tree.getLabel(), start, end);

		Queue<Tree<L>> queue = new LinkedList<Tree<L>>();
		queue.addAll(tree.getChildren());
		int currStart = start;
		while (!queue.isEmpty()) {
			Tree<L> remove = queue.remove();
			List<L> currYield = remove.getYield();
			final int currEnd = currStart + currYield.size();
			if (currStart <= i && currEnd >= j) {
				final Constituent<L> leastCommonAncestorConstituentHelper = getLeastCommonAncestorConstituentHelper(
						remove, currStart, currEnd, i, j);
				if (leastCommonAncestorConstituentHelper != null)
					return leastCommonAncestorConstituentHelper;
				else
					break;
			}
			currStart += currYield.size();
		}
		return new Constituent<L>(tree.getLabel(), start, end);
	}

	public boolean hasUnariesOtherThanRoot() {
		assert children.size() == 1;
		return hasUnariesHelper(children.get(0));

	}

	private boolean hasUnariesHelper(Tree<L> tree) {
		if (tree.isPreTerminal())
			return false;
		if (tree.getChildren().size() == 1)
			return true;
		for (Tree<L> child : tree.getChildren()) {
			if (hasUnariesHelper(child))
				return true;
		}
		return false;
	}

	public boolean hasUnaryChain() {
		return hasUnaryChainHelper(this, false);
	}

	private boolean hasUnaryChainHelper(Tree<L> tree, boolean unaryAbove) {
		boolean result = false;
		if (tree.getChildren().size() == 1) {
			if (unaryAbove)
				return true;
			else if (tree.getChildren().get(0).isPreTerminal())
				return false;
			else
				return hasUnaryChainHelper(tree.getChildren().get(0), true);
		} else {
			for (Tree<L> child : tree.getChildren()) {
				if (!child.isPreTerminal())
					result = result || hasUnaryChainHelper(child, false);
			}
		}
		return result;
	}

	public void removeUnaryChains() {
		removeUnaryChainHelper(this, null);
	}

	private void removeUnaryChainHelper(Tree<L> tree, Tree<L> parent) {
		if (tree.isLeaf())
			return;
		if (tree.getChildren().size() == 1 && !tree.isPreTerminal()) {
			if (parent != null) {
				tree = tree.getChildren().get(0);
				parent.getChildren().set(0, tree);
				removeUnaryChainHelper(tree, parent);
			} else
				removeUnaryChainHelper(tree.getChildren().get(0), tree);
		} else {
			for (Tree<L> child : tree.getChildren()) {
				if (!child.isPreTerminal())
					removeUnaryChainHelper(child, null);
			}
		}
	}

}
