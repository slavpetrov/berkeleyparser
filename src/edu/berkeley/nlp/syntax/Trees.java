package edu.berkeley.nlp.syntax;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import edu.berkeley.nlp.util.CollectionUtils;
import edu.berkeley.nlp.util.Filter;
import edu.berkeley.nlp.util.Pair;
import edu.berkeley.nlp.util.StrUtils;

/**
 * Tools for displaying, reading, and modifying trees.
 * 
 * @author Dan Klein
 */
public class Trees {

	public static interface TreeTransformer<E> {
		Tree<E> transformTree(Tree<E> tree);
	}

	public static class PunctuationStripper implements TreeTransformer<String> {
		public Tree<String> transformTree(Tree<String> tree) {
			return Trees.spliceNodes(tree, new Filter<String>() {
				public boolean accept(String t) {
					return (t.equals("."));
				}
			});
		}
	}

	public static <L> Tree<L> findNode(Tree<L> root, Filter<L> filter) {
		if (filter.accept(root.getLabel()))
			return root;
		for (Tree<L> node : root.getChildren()) {
			Tree<L> ret = findNode(node, filter);
			if (ret != null)
				return ret;
		}
		return null;
	}

	public static class FunctionNodeStripper implements TreeTransformer<String> {
		public Tree<String> transformTree(Tree<String> tree) {
			final String transformedLabel = transformLabel(tree);
			if (tree.isLeaf()) {
				return tree.shallowCloneJustRoot();
			}
			final List<Tree<String>> transformedChildren = new ArrayList<Tree<String>>();
			for (final Tree<String> child : tree.getChildren()) {
				transformedChildren.add(transformTree(child));
			}
			return new Tree<String>(transformedLabel, transformedChildren);
		}

		/**
		 * @param tree
		 * @return
		 */
		public static String transformLabel(Tree<String> tree) {
			String transformedLabel = tree.getLabel();
			int cutIndex = transformedLabel.indexOf('-');
			int cutIndex2 = transformedLabel.indexOf('=');
			final int cutIndex3 = transformedLabel.indexOf('^');
			if (cutIndex3 > 0 && (cutIndex3 < cutIndex2 || cutIndex2 == -1))
				cutIndex2 = cutIndex3;
			if (cutIndex2 > 0 && (cutIndex2 < cutIndex || cutIndex <= 0))
				cutIndex = cutIndex2;
			if (cutIndex > 0 && !tree.isLeaf()) {
				transformedLabel = new String(transformedLabel.substring(0,
						cutIndex));
			}
			return transformedLabel;
		}
	}

	public static class EmptyNodeStripper implements TreeTransformer<String> {
		public Tree<String> transformTree(Tree<String> tree) {
			final String label = tree.getLabel();
			if (label.equals("-NONE-")) {
				return null;
			}
			if (tree.isLeaf()) {
				return new Tree<String>(label);
			}
			final List<Tree<String>> children = tree.getChildren();
			final List<Tree<String>> transformedChildren = new ArrayList<Tree<String>>();
			for (final Tree<String> child : children) {
				final Tree<String> transformedChild = transformTree(child);
				if (transformedChild != null)
					transformedChildren.add(transformedChild);
			}
			if (transformedChildren.size() == 0)
				return null;
			return new Tree<String>(label, transformedChildren);
		}
	}

	public static class XOverXRemover<E> implements TreeTransformer<E> {
		public Tree<E> transformTree(Tree<E> tree) {
			final E label = tree.getLabel();
			List<Tree<E>> children = tree.getChildren();
			while (children.size() == 1 && !children.get(0).isLeaf()
					&& label.equals(children.get(0).getLabel())) {
				children = children.get(0).getChildren();
			}
			final List<Tree<E>> transformedChildren = new ArrayList<Tree<E>>();
			for (final Tree<E> child : children) {
				transformedChildren.add(transformTree(child));
			}
			return new Tree<E>(label, transformedChildren);
		}
	}

	public static class CoindexingStripper implements TreeTransformer<String> {
		public Tree<String> transformTree(Tree<String> tree) {
			final String transformedLabel = transformLabel(tree);
			if (tree.getLabel().equals("-NONE-")) {
				List<Tree<String>> child = new ArrayList<Tree<String>>();
				child.add(new Tree<String>(transformLabel(tree.getChild(0))));
				return new Tree<String>(tree.getLabel(), child);
			}
			if (tree.isLeaf()) {
				return tree.shallowCloneJustRoot();
			}
			final List<Tree<String>> transformedChildren = new ArrayList<Tree<String>>();
			for (final Tree<String> child : tree.getChildren()) {
				transformedChildren.add(transformTree(child));
			}
			return new Tree<String>(transformedLabel, transformedChildren);
		}

		/**
		 * @param tree
		 * @return
		 */
		public static String transformLabel(Tree<String> tree) {
			String label = tree.getLabel();
			if (label.equals("0"))
				return label;
			return label.replaceAll("\\d+", "XX");
		}
	}

	public static class EmptyNodeRestorer implements TreeTransformer<String> {
		public Tree<String> transformTree(Tree<String> tree) {
			final String label = tree.getLabel();
			if (tree.isLeaf()) {
				return new Tree<String>(label);
			}
			final List<Tree<String>> children = tree.getChildren();
			final List<Tree<String>> transformedChildren = new LinkedList<Tree<String>>();
			for (final Tree<String> child : children) {
				final Tree<String> transformedChild = transformTree(child);
				transformedChildren.add(transformedChild);
			}
			String newLabel = label.replace('_', '-').replace('+', '=');
			newLabel = addChildren(newLabel, transformedChildren);
			return new Tree<String>(newLabel, transformedChildren);
		}

		private String addChildren(String currentLabel,
				List<Tree<String>> children) {
			String newLabel = currentLabel;
			while (newLabel.contains("~")) {
				assert (newLabel.lastIndexOf(']') == newLabel.length() - 1);
				int bracketCount = 1;
				int p;
				for (p = newLabel.length() - 2; p >= 0 && bracketCount > 0; p--) {
					if (newLabel.charAt(p) == ']') {
						bracketCount++;
					}
					if (newLabel.charAt(p) == '[') {
						bracketCount--;
					}
				}
				assert (p > 0);
				assert (newLabel.charAt(p) == '~');
				int q = newLabel.lastIndexOf('~', p - 1);
				int childIndex = Integer.parseInt(newLabel.substring(q + 1, p));
				String childString = newLabel.substring(p + 2,
						newLabel.length() - 1);
				List<Tree<String>> newChildren = new LinkedList<Tree<String>>();
				String childLabel = addChildren(childString, newChildren);
				int indexToUse = Math.min(childIndex, children.size());
				if (childLabel.equals("NONE")) {
					childLabel = "-NONE-";
				}
				children.add(indexToUse, new Tree<String>(childLabel,
						newChildren));
				newLabel = newLabel.substring(0, q);
			}
			return newLabel;
		}
	}

	public static class EmptyNodeRelabeler implements TreeTransformer<String> {
		public Tree<String> transformTree(Tree<String> tree) {
			final String label = tree.getLabel();
			if (tree.isLeaf()) {
				return new Tree<String>(label);
			}
			String newLabel = replaceNumbers(label);
			if (label.equals("-NONE-")) {
				String childLabel = replaceNumbers(tree.getChildren().get(0)
						.getLabel());
				return new Tree<String>("NONE~0~[" + childLabel + "]");
			}
			final List<Tree<String>> children = tree.getChildren();
			final List<Tree<String>> transformedChildren = new ArrayList<Tree<String>>();
			int index = 0;
			for (final Tree<String> child : children) {
				final Tree<String> transformedChild = transformTree(child);
				if (transformedChild.getLabel().contains("NONE~")
						&& transformedChild.isLeaf()) {
					newLabel = newLabel + "~" + index + "~["
							+ transformedChild.getLabel() + "]";
				} else {
					transformedChildren.add(transformedChild);
					index++;
				}
			}
			newLabel = newLabel.replace('-', '_').replace('=', '+');
			return new Tree<String>(newLabel, transformedChildren);
		}

		private static String replaceNumbers(String label) {
			if (label.equals("0"))
				return label;
			return label.replaceAll("\\d+", "XX");
		}
	}

	public static class CompoundTreeTransformer<T> implements
			TreeTransformer<T> {

		Collection<TreeTransformer<T>> transformers;

		public CompoundTreeTransformer(
				Collection<TreeTransformer<T>> transformers) {
			this.transformers = transformers;
		}

		public CompoundTreeTransformer() {
			this(new ArrayList<TreeTransformer<T>>());
		}

		public void addTransformer(TreeTransformer<T> transformer) {
			transformers.add(transformer);
		}

		public Tree<T> transformTree(Tree<T> tree) {
			for (TreeTransformer<T> trans : transformers) {
				tree = trans.transformTree(tree);
			}
			return tree;
		}
	}

	public static class StandardTreeNormalizer implements
			TreeTransformer<String> {
		EmptyNodeStripper emptyNodeStripper = new EmptyNodeStripper();
		XOverXRemover<String> xOverXRemover = new XOverXRemover<String>();
		FunctionNodeStripper functionNodeStripper = new FunctionNodeStripper();

		public Tree<String> transformTree(Tree<String> tree) {
			tree = functionNodeStripper.transformTree(tree);
			tree = emptyNodeStripper.transformTree(tree);
			tree = xOverXRemover.transformTree(tree);
			return tree;
		}
	}

	public static class FunctionLabelRetainingTreeNormalizer implements
			TreeTransformer<String> {
		EmptyNodeStripper emptyNodeStripper = new EmptyNodeStripper();
		XOverXRemover<String> xOverXRemover = new XOverXRemover<String>();

		public Tree<String> transformTree(Tree<String> tree) {
			tree = emptyNodeStripper.transformTree(tree);
			tree = xOverXRemover.transformTree(tree);
			return tree;
		}
	}

	public static class PennTreeReader implements Iterator<Tree<String>> {
		public static String ROOT_LABEL = "ROOT";

		PushbackReader in;
		Tree<String> nextTree;
		int num = 0;
		int treeNum = 0;

		private boolean lowercase = false;

		private String name = "";
		private String currTreeName;

		public boolean hasNext() {
			return (nextTree != null);
		}

		private static int x = 0;

		public Tree<String> next() {
			if (!hasNext())
				throw new NoSuchElementException();
			final Tree<String> tree = nextTree;
			// System.out.println(tree);
			nextTree = readRootTree();

			return tree;
		}

		private Tree<String> readRootTree() {
			currTreeName = name + ":" + treeNum;
			try {
				readWhiteSpace();
				if (!isLeftParen(peek()))
					return null;
				treeNum++;
				return readTree(true);
			} catch (final IOException e) {
				throw new RuntimeException(e);
			}

		}

		private Tree<String> readTree(boolean isRoot) throws IOException {
			readLeftParen();
			String label = readLabel();
			if (label.length() == 0 && isRoot)
				label = ROOT_LABEL;
			if (isRightParen(peek())) {
				// special case where terminal item is surround by brackets e.g.
				// '(1)'
				readRightParen();
				return new Tree<String>(label);
			}
			final List<Tree<String>> children = readChildren();
			readRightParen();
			if (!lowercase || children.size() > 0) {
				return isRoot ? new NamedTree<String>(label, children,
						currTreeName) : new Tree<String>(label, children);
			} else {
				return isRoot ? new NamedTree<String>(label.toLowerCase()
						.intern(), children, currTreeName) : new Tree<String>(
						label.toLowerCase().intern(), children);
			}
		}

		private String readLabel() throws IOException {
			readWhiteSpace();
			return readText(false);
		}

		private String readText(boolean atLeastOne) throws IOException {
			final StringBuilder sb = new StringBuilder();
			int ch = in.read();
			while (atLeastOne
					|| (!isWhiteSpace(ch) && !isLeftParen(ch)
							&& !isRightParen(ch) && ch != -1)) {
				sb.append((char) ch);
				ch = in.read();
				atLeastOne = false;
			}

			in.unread(ch);
			return sb.toString().intern();
		}

		private List<Tree<String>> readChildren() throws IOException {
			readWhiteSpace();
			final List<Tree<String>> children = new ArrayList<Tree<String>>();
			while (!isRightParen(peek()) || children.size() == 0) {
				readWhiteSpace();
				if (isLeftParen(peek())) {
					if (isTextParen()) {
						children.add(readLeaf());
					} else {
						children.add(readTree(false));
					}
				} else if (peek() == 65535) {
					int peek = peek();
					throw new RuntimeException(
							"Unmatched parentheses in tree input.");
				} else {
					children.add(readLeaf());
				}
				readWhiteSpace();
			}
			return children;
		}

		private boolean isTextParen() throws IOException {
			final int next = in.read();
			final int postnext = in.read();
			boolean isText = isLeftParen(next) && isRightParen(postnext);
			in.unread(postnext);
			in.unread(next);
			return isText;
		}

		private int peek() throws IOException {
			final int ch = in.read();
			in.unread(ch);
			return ch;
		}

		private Tree<String> readLeaf() throws IOException {
			String label = readText(true);
			if (lowercase)
				label = label.toLowerCase();
			return new Tree<String>(label.intern());
		}

		private void readLeftParen() throws IOException {
			// System.out.println("Read left.");
			readWhiteSpace();
			final int ch = in.read();
			if (!isLeftParen(ch))
				throw new RuntimeException("Format error reading tree.");
		}

		private void readRightParen() throws IOException {
			// System.out.println("Read right.");
			readWhiteSpace();
			final int ch = in.read();
			if (!isRightParen(ch))
				throw new RuntimeException("Format error reading tree.");
		}

		private void readWhiteSpace() throws IOException {
			int ch = in.read();
			while (isWhiteSpace(ch)) {
				ch = in.read();
			}
			in.unread(ch);
		}

		private boolean isWhiteSpace(int ch) {
			return (ch == ' ' || ch == '\t' || ch == '\f' || ch == '\r' || ch == '\n');
		}

		private boolean isLeftParen(int ch) {
			return ch == '(';
		}

		private boolean isRightParen(int ch) {
			return ch == ')';
		}

		public void remove() {
			throw new UnsupportedOperationException();
		}

		public PennTreeReader(Reader in) {
			this(in, "", false);
		}

		public PennTreeReader(Reader in, String name) {
			this(in, name, false);
		}

		public PennTreeReader(Reader in, String name, boolean lowercase) {
			this.lowercase = lowercase;
			this.name = name;
			this.in = new PushbackReader(in, 2);
			nextTree = readRootTree();
		}

		public PennTreeReader(Reader in, boolean lowercase) {
			this(in, "", lowercase);
		}

		/**
		 * Reads a tree on a single line and returns null if there was a
		 * problem.
		 * 
		 * @param lowercase
		 */
		public static Tree<String> parseEasy(String treeString,
				boolean lowercase) {
			try {
				return parseHard(treeString, lowercase);
			} catch (RuntimeException e) {
				return null;
			}
		}

		/**
		 * Reads a tree on a single line and returns null if there was a
		 * problem.
		 * 
		 * @param treeString
		 */
		public static Tree<String> parseEasy(String treeString) {
			return parseEasy(treeString, false);
		}

		private static Tree<String> parseHard(String treeString,
				boolean lowercase) {
			StringReader sr = new StringReader(treeString);
			PennTreeReader reader = new Trees.PennTreeReader(sr, lowercase);
			return reader.next();
		}
	}

	/**
	 * Renderer for pretty-printing trees according to the Penn Treebank
	 * indenting guidelines (mutliline). Adapted from code originally written by
	 * Dan Klein and modified by Chris Manning.
	 */
	public static class PennTreeRenderer {

		/**
		 * Print the tree as done in Penn Treebank merged files. The formatting
		 * should be exactly the same, but we don't print the trailing
		 * whitespace found in Penn Treebank trees. The basic deviation from a
		 * bracketed indented tree is to in general collapse the printing of
		 * adjacent preterminals onto one line of tags and words. Additional
		 * complexities are that conjunctions (tag CC) are not collapsed in this
		 * way, and that the unlabeled outer brackets are collapsed onto the
		 * same line as the next bracket down.
		 */
		public static <L> String render(Tree<L> tree) {
			final StringBuilder sb = new StringBuilder();
			renderTree(tree, 0, false, false, false, true, sb);
			sb.append('\n');
			return sb.toString();
		}

		/**
		 * Display a node, implementing Penn Treebank style layout
		 */
		private static <L> void renderTree(Tree<L> tree, int indent,
				boolean parentLabelNull, boolean firstSibling,
				boolean leftSiblingPreTerminal, boolean topLevel,
				StringBuilder sb) {
			// the condition for staying on the same line in Penn Treebank
			final boolean suppressIndent = (parentLabelNull
					|| (firstSibling && tree.isPreTerminal()) || (leftSiblingPreTerminal
					&& tree.isPreTerminal() && (tree.getLabel() == null || !tree
					.getLabel().toString().startsWith("CC"))));
			if (suppressIndent) {
				sb.append(' ');
			} else {
				if (!topLevel) {
					sb.append('\n');
				}
				for (int i = 0; i < indent; i++) {
					sb.append("  ");
				}
			}
			if (tree.isLeaf() || tree.isPreTerminal()) {
				renderFlat(tree, sb);
				return;
			}
			sb.append('(');
			sb.append(tree.getLabel());
			renderChildren(tree.getChildren(), indent + 1,
					tree.getLabel() == null
							|| tree.getLabel().toString() == null, sb);
			sb.append(')');
		}

		private static <L> void renderFlat(Tree<L> tree, StringBuilder sb) {
			if (tree.isLeaf()) {
				sb.append(tree.getLabel().toString());
				return;
			}
			sb.append('(');
			if (tree.getLabel() == null)
				sb.append("<null>");
			else
				sb.append(tree.getLabel().toString());
			sb.append(' ');
			sb.append(tree.getChildren().get(0).getLabel().toString());
			sb.append(')');
		}

		private static <L> void renderChildren(List<Tree<L>> children,
				int indent, boolean parentLabelNull, StringBuilder sb) {
			boolean firstSibling = true;
			boolean leftSibIsPreTerm = true; // counts as true at beginning
			for (final Tree<L> child : children) {
				renderTree(child, indent, parentLabelNull, firstSibling,
						leftSibIsPreTerm, false, sb);
				leftSibIsPreTerm = child.isPreTerminal();
				// CC is a special case
				if (child.getLabel() != null
						&& child.getLabel().toString().startsWith("CC")) {
					leftSibIsPreTerm = false;
				}
				firstSibling = false;
			}
		}
	}

	public static void main(String[] args) {
		// Basic Test
		String parse = "((S (NP (DT the) (JJ quick) (JJ brown) (NN fox)) (VP (VBD jumped) (PP (IN over) (NP (DT the) (JJ lazy) (NN dog)))) (. .)))";
		if (args.length > 0) {
			parse = StrUtils.join(args);
		}
		final PennTreeReader reader = new PennTreeReader(
				new StringReader(parse));
		final Tree<String> tree = reader.next();
		System.out.println(PennTreeRenderer.render(tree));
		System.out.println(tree);

		// Robustness Tests
		if (args.length == 0) {
			System.out.println("Testing robustness");
			String unbalanced1 = "((S (NP (DT the) (JJ quick) (JJ brown) (NN fox)) (VP (VBD jumped) (PP (IN over) (NP (DT the) (JJ lazy) (NN dog)))) (. .))";
			String unbalanced2 = "((S (NP (DT the) (JJ quick) (JJ brown) (NN fox))) (VP (VBD jumped) (PP (IN over) (NP (DT the) (JJ lazy) (NN dog)))) (. .)))";
			System.out.println("\nMissing a paren:");
			System.out.println(unbalanced1);
			System.out.println(PennTreeReader.parseEasy(unbalanced1, false));
			System.out.println("\nExtra paren:");
			System.out.println(unbalanced2);
			System.out.println(PennTreeReader.parseEasy(unbalanced2, false));
			String parens = "((S (NP (DT the) (SYM () (JJ quick) (JJ brown) (SYM )) (NN fox)) (VP (VBD jumped) (PP (IN over) (NP (DT the) (JJ lazy) (NN dog)))) (. .)))";
			System.out.println("\nParens as characters:");
			System.out.println(parens);
			System.out.println(PennTreeReader.parseEasy(parens, false));
		}
	}

	/**
	 * Splices out all nodes which match the provided filter.
	 * 
	 * @param tree
	 * @param filter
	 * @return
	 */
	public static <L> Tree<L> spliceNodes(Tree<L> tree, Filter<L> filter) {
		return spliceNodes(tree, filter, true);
	}

	private static <L> Tree<L> spliceNodes(Tree<L> tree, Filter<L> filter,
			boolean splice) {
		final List<Tree<L>> rootList = spliceNodesHelper(tree, filter, splice);
		if (rootList.size() > 1)
			throw new IllegalArgumentException(
					"spliceNodes: no unique root after splicing");
		if (rootList.size() < 1)
			return null;
		return rootList.get(0);
	}

	public static <L> Tree<L> deleteNodes(Tree<L> tree, Filter<L> filter) {
		return spliceNodes(tree, filter, false);
	}

	/**
	 * Splices out all nodes which match the provided filter, with a map from
	 * the original tree nodes to the spliced tree nodes.
	 * 
	 * @param tree
	 * @param filter
	 * @return
	 */
	public static <L> Map<Tree<L>, Tree<L>> spliceNodesWithMap(Tree<L> tree,
			Filter<L> filter) {
		Map<Tree<L>, Tree<L>> nodeMap = new IdentityHashMap<Tree<L>, Tree<L>>();
		final List<Tree<L>> rootList = spliceNodesWithMapHelper(tree, filter,
				nodeMap);
		if (rootList.size() > 1)
			throw new IllegalArgumentException(
					"spliceNodes: no unique root after splicing");
		if (rootList.size() < 1)
			return null;
		return nodeMap;
	}

	private static <L> List<Tree<L>> spliceNodesWithMapHelper(Tree<L> tree,
			Filter<L> filter, Map<Tree<L>, Tree<L>> nodeMap) {
		final List<Tree<L>> splicedChildren = new ArrayList<Tree<L>>();
		for (final Tree<L> child : tree.getChildren()) {
			final List<Tree<L>> splicedChildList = spliceNodesWithMapHelper(
					child, filter, nodeMap);
			splicedChildren.addAll(splicedChildList);
		}

		if (filter.accept(tree.getLabel())) {
			nodeMap.put(tree, null);
			return splicedChildren;
		}
		final Tree<L> newTree = tree.shallowCloneJustRoot();
		newTree.setChildren(splicedChildren);
		nodeMap.put(tree, newTree);
		return Collections.singletonList(newTree);
	}

	public static <L> Tree<L> asTree(List<L> leaves, L root) {
		final Tree<L> t = new Tree<L>(root);
		final List<Tree<L>> children = new ArrayList<Tree<L>>(leaves.size());
		for (final L leaf : leaves) {
			children.add(new Tree<L>(leaf));
		}
		t.setChildren(children);
		return t;
	}

	public static <T> Tree<String> stringTree(Tree<T> tree) {
		final String root = tree.getLabel().toString();
		final List<Tree<String>> children = new ArrayList<Tree<String>>();
		final Tree<String> newTree = new Tree<String>(root, children);
		for (final Tree<T> child : tree.getChildren()) {
			children.add(stringTree(child));
		}
		return newTree;
	}

	public static <T> Tree<T> truncateAtDepth(Tree<T> tree, int depth) {
		if (depth == 0) {
			return new Tree<T>(tree.getLabel());
		}
		final List<Tree<T>> children = new ArrayList<Tree<T>>();
		final Tree<T> newTree = new Tree<T>(tree.getLabel(), children);
		for (final Tree<T> child : tree.getChildren()) {
			children.add(truncateAtDepth(child, depth - 1));
		}
		return newTree;
	}

	private static <L> List<Tree<L>> spliceNodesHelper(Tree<L> tree,
			Filter<L> filter, boolean splice) {
		final List<Tree<L>> splicedChildren = new ArrayList<Tree<L>>();
		for (final Tree<L> child : tree.getChildren()) {
			final List<Tree<L>> splicedChildList = spliceNodesHelper(child,
					filter, splice);
			splicedChildren.addAll(splicedChildList);
		}

		if (filter.accept(tree.getLabel()) && !tree.isLeaf())
			return splice ? splicedChildren : new ArrayList<Tree<L>>();
		// assert !(tree.getLabel().equals("NP") && splicedChildren.isEmpty());
		final Tree<L> newTree = tree.shallowCloneJustRoot();
		newTree.setChildren(splicedChildren);
		return Collections.singletonList(newTree);
	}

	public static Tree<String> stripLeaves(Tree<String> tree) {
		if (tree.isLeaf()) {
			throw new RuntimeException("Can't strip leaves from "
					+ tree.toString());
		}
		if (tree.getChildren().get(0).isLeaf()) {
			// Base case; preterminals become terminals.
			return new Tree<String>(tree.getLabel());
		} else {
			final List<Tree<String>> children = new ArrayList<Tree<String>>();
			final Tree<String> newTree = new Tree<String>(tree.getLabel());
			for (final Tree<String> child : tree.getChildren()) {
				children.add(stripLeaves(child));
			}
			newTree.setChildren(children);
			return newTree;
		}
	}

	public static <T> int getMaxBranchingFactor(Tree<T> tree) {
		int max = tree.getChildren().size();
		for (final Tree<T> child : tree.getChildren()) {
			max = Math.max(max, getMaxBranchingFactor(child));
		}
		return max;
	}

	public static <T> Tree<T> buildTree(T rootLabel,
			Map<T, List<T>> parent2ChildrenMap) {
		List<T> childrenLabels = CollectionUtils.getValueList(
				parent2ChildrenMap, rootLabel);
		List<Tree<T>> children = new ArrayList<Tree<T>>();
		for (T c : childrenLabels) {
			Tree<T> node = buildTree(c, parent2ChildrenMap);
			children.add(node);
		}
		return new Tree<T>(rootLabel, children);
	}

	public interface LabelTransformer<S, T> {
		public T transform(Tree<S> node);
	}

	public static <S, T> Tree<T> transformLabels(Tree<S> origTree,
			LabelTransformer<S, T> labelTransform) {
		T newLabel = labelTransform.transform(origTree);
		if (origTree.isLeaf()) {
			return new Tree<T>(newLabel);
		}
		List<Tree<T>> children = new ArrayList<Tree<T>>();
		for (Tree<S> child : origTree.getChildren()) {
			children.add(transformLabels(child, labelTransform));
		}
		return new Tree<T>(newLabel, children);
	}

	public static class SortingTransformer<T> implements TreeTransformer<T> {

		Comparator<? super T> comparator;

		public SortingTransformer(Comparator<? super T> comparator) {
			this.comparator = comparator;
		}

		public Tree<T> transformTree(Tree<T> t) {
			List<Tree<T>> children = new ArrayList<Tree<T>>();
			for (Tree<T> child : t.getChildren()) {
				children.add(transformTree(child));
			}
			Collections.sort(children, new Comparator<Tree<T>>() {
				public int compare(Tree<T> tTree, Tree<T> tTree1) {
					return comparator.compare(tTree.getLabel(),
							tTree1.getLabel());
				}
			});
			return new Tree<T>(t.getLabel(), children);
		}
	}

	public static <T> List<Pair<T, T>> getParentChildPairs(Tree<T> tree) {
		List<Pair<T, T>> rv = new ArrayList<Pair<T, T>>();
		for (Tree<T> parent : tree.getPostOrderTraversal()) {
			for (Tree<T> child : parent.getChildren()) {
				rv.add(Pair.newPair(parent.getLabel(), child.getLabel()));
			}
		}
		return rv;
	}

}
