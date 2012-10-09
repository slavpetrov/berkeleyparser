/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.util.Numberer;

/**
 * Essentially equivalent to a List<Tree<StateSet>>, but each Tree<StateSet> is
 * re-built every time from the corresponding Tree<String>. This saves a lot of
 * memory at the expense of some time. Most of the code is contained in the
 * subclass StringTreeListIterator.
 * 
 * Beware of the behavior of hasNext(), which deallocates the current tree (the
 * last one returned by next()). This is PRESUMABLY when the current tree is no
 * longer needed, but be careful.
 * 
 * @author Romain Thibaux
 */
public class StateSetTreeList extends AbstractCollection<Tree<StateSet>> {
	List<Tree<StateSet>> trees;
	static short zero = 0, one = 1;

	/*
	 * Allocate the inside and outside score arrays for the whole tree
	 */
	void allocate(Tree<StateSet> tree) {
		tree.getLabel().allocate();
		for (Tree<StateSet> child : tree.getChildren()) {
			allocate(child);
		}
	}

	/*
	 * Deallocate the inside and outside score arrays for the whole tree
	 */
	void deallocate(Tree<StateSet> tree) {
		tree.getLabel().deallocate();
		for (Tree<StateSet> child : tree.getChildren()) {
			deallocate(child);
		}
	}

	/*
	 * create a deep copy of this object
	 */
	public StateSetTreeList copy() {
		StateSetTreeList copy = new StateSetTreeList();
		for (Tree<StateSet> tree : trees) {
			copy.add(copyTree(tree));
		}
		return copy;
	}

	/**
	 * @param tree
	 * @return
	 */
	private Tree<StateSet> copyTree(Tree<StateSet> tree) {
		ArrayList<Tree<StateSet>> newChildren = new ArrayList<Tree<StateSet>>(
				tree.getChildren().size());
		for (Tree<StateSet> child : tree.getChildren()) {
			newChildren.add(copyTree(child));
		}
		return new Tree<StateSet>(tree.getLabel().copy(), newChildren);
	}

	public class StateSetTreeListIterator implements Iterator<Tree<StateSet>> {
		Iterator<Tree<StateSet>> stringTreeListIterator;
		Tree<StateSet> currentTree;

		public StateSetTreeListIterator() {
			stringTreeListIterator = trees.iterator();
			currentTree = null;
		}

		public boolean hasNext() {
			// A somewhat crappy API, the tree is deallocated when hasNext() is
			// called,
			// which is PRESUMABLY when the current tree is no longer needed.
			if (currentTree != null) {
				deallocate(currentTree);
			}
			return stringTreeListIterator.hasNext();
		}

		public Tree<StateSet> next() {
			currentTree = stringTreeListIterator.next();
			// allocate(currentTree);
			return currentTree;
		}

		public void remove() {
			stringTreeListIterator.remove();
		}
	}

	/**
	 * 
	 * @param trees
	 * @param numStates
	 * @param allSplitTheSame
	 *            This should be true only if all states are being split the
	 *            same number of times. This number is taken from numStates[0].
	 * @param tagNumberer
	 * @param dontSplitTags
	 */
	public StateSetTreeList(List<Tree<String>> trees, short[] numStates,
			boolean allSplitTheSame, Numberer tagNumberer) {
		this.trees = new ArrayList<Tree<StateSet>>();
		for (Tree<String> tree : trees) {
			this.trees.add(stringTreeToStatesetTree(tree, numStates,
					allSplitTheSame, tagNumberer));
			tree = null;
		}
	}

	public StateSetTreeList(StateSetTreeList treeList, short[] numStates,
			boolean constant) {
		this.trees = new ArrayList<Tree<StateSet>>();
		for (Tree<StateSet> tree : treeList.trees) {
			this.trees.add(resizeStateSetTree(tree, numStates, constant));
		}
	}

	public StateSetTreeList() {
		this.trees = new ArrayList<Tree<StateSet>>();
	}

	public boolean add(Tree<StateSet> tree) {
		return trees.add(tree);
	}

	public Tree<StateSet> get(int i) {
		return trees.get(i);
	}

	public int size() {
		return trees.size();
	}

	public boolean isEmpty() {
		return trees.isEmpty();
	}

	/*
	 * An iterator over the StateSet trees (which are re-built on the fly)
	 */
	public Iterator<Tree<StateSet>> iterator() {
		return new StateSetTreeListIterator();
	}

	/**
	 * Convert a single Tree[String] to Tree[StateSet]
	 * 
	 * @param tree
	 * @param numStates
	 * @param tagNumberer
	 * @return
	 */
	public static Tree<StateSet> stringTreeToStatesetTree(Tree<String> tree,
			short[] numStates, boolean allSplitTheSame, Numberer tagNumberer) {
		Tree<StateSet> result = stringTreeToStatesetTree(tree, numStates,
				allSplitTheSame, tagNumberer, false, 0, tree.getYield().size());
		// set the positions properly:
		List<StateSet> words = result.getYield();
		// for all words in sentence
		for (short position = 0; position < words.size(); position++) {
			words.get(position).from = position;
			words.get(position).to = (short) (position + 1);
		}
		return result;
	}

	private static Tree<StateSet> stringTreeToStatesetTree(Tree<String> tree,
			short[] numStates, boolean allSplitTheSame, Numberer tagNumberer,
			boolean splitRoot, int from, int to) {
		if (tree.isLeaf()) {
			StateSet newState = new StateSet(zero, one, tree.getLabel()
					.intern(), (short) from, (short) to);
			return new Tree<StateSet>(newState);
		}
		short label = (short) tagNumberer.number(tree.getLabel());
		if (label < 0)
			label = 0;
		// System.out.println(label + " " +tree.getLabel());
		if (label >= numStates.length) {
			// System.err.println("Have never seen this state before: "+tree.getLabel());
			// StateSet newState = new StateSet(zero, one,
			// tree.getLabel().intern(),(short)from,(short)to);
			// return new Tree<StateSet>(newState);
		}
		short nodeNumStates = (allSplitTheSame || numStates.length <= label) ? numStates[0]
				: numStates[label];
		if (!splitRoot)
			nodeNumStates = 1;
		StateSet newState = new StateSet(label, nodeNumStates, null,
				(short) from, (short) to);
		Tree<StateSet> newTree = new Tree<StateSet>(newState);
		List<Tree<StateSet>> newChildren = new ArrayList<Tree<StateSet>>();
		for (Tree<String> child : tree.getChildren()) {
			short length = (short) child.getYield().size();
			Tree<StateSet> newChild = stringTreeToStatesetTree(child,
					numStates, allSplitTheSame, tagNumberer, true, from, from
							+ length);
			from += length;
			newChildren.add(newChild);
		}
		newTree.setChildren(newChildren);
		return newTree;
	}

	private static Tree<StateSet> resizeStateSetTree(Tree<StateSet> tree,
			short[] numStates, boolean constant) {
		if (tree.isLeaf()) {
			return tree;
		}
		short state = tree.getLabel().getState();
		short newNumStates = constant ? numStates[0] : numStates[state];
		StateSet newState = new StateSet(tree.getLabel(), newNumStates);
		Tree<StateSet> newTree = new Tree<StateSet>(newState);
		List<Tree<StateSet>> newChildren = new ArrayList<Tree<StateSet>>();
		for (Tree<StateSet> child : tree.getChildren()) {
			newChildren.add(resizeStateSetTree(child, numStates, constant));
		}
		newTree.setChildren(newChildren);
		return newTree;
	}

	/**
	 * @param trainTrees
	 * @param tagNumberer
	 */
	public static void initializeTagNumberer(List<Tree<String>> trees,
			Numberer tagNumberer) {
		short[] nSub = new short[2];
		nSub[0] = 1;
		nSub[1] = 1;
		for (Tree<String> tree : trees) {
			Tree<StateSet> tmp = stringTreeToStatesetTree(tree, nSub, true,
					tagNumberer);
		}
	}
}
