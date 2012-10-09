package edu.berkeley.nlp.treebank;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees.TreeTransformer;
import edu.berkeley.nlp.util.Logger;
import edu.berkeley.nlp.util.StopWatch;

public class TreebankFetcher {

	private final List<edu.berkeley.nlp.syntax.Trees.TreeTransformer<String>> transformers = new ArrayList<TreeTransformer<String>>();
	private int maxLength = Integer.MAX_VALUE;
	private int minLength = 0;
	private int maxTrees = Integer.MAX_VALUE;
	private final boolean verbose;

	public TreebankFetcher(boolean verbose) {
		this.verbose = verbose;
	}

	public TreebankFetcher() {
		this(false);
	}

	public void setMaxLength(int maxLength) {
		this.maxLength = maxLength;
	}

	public void setMinLength(int minLength) {
		this.minLength = minLength;
	}

	public void setMaxTrees(int maxTrees) {
		this.maxTrees = maxTrees;
	}

	public void addTransformer(TreeTransformer<String> transformer) {
		transformers.add(transformer);
	}

	public Iterable<Tree<String>> getTrees(String path) {
		return getTrees(path, -1, Integer.MAX_VALUE);
	}

	public Iterable<Tree<String>> getTrees(String path, String ext) {
		return getTrees(path, ext, -1, Integer.MAX_VALUE);
	}

	public Iterable<Tree<String>> getTrees(String path, int start, int stop) {
		return getTrees(path, "mrg", start, stop);
	}

	public Iterable<Tree<String>> getTrees(String path, String ext, int start,
			int stop) {
		StopWatch stopwatch = new StopWatch();
		if (verbose) {
			Logger.i().logs("Loading Trees from %s [%d,%d]...", path, start,
					stop);
			System.err.flush();
			stopwatch.start();
		}
		final Collection<Tree<String>> rawTrees = PennTreebankReader.readTrees(
				path, start * 100, stop * 100);
		if (verbose) {
			stopwatch.accumStop();
			Logger.i().logs("Done loaded %d trees in %.3f seconds\n",
					rawTrees.size(), stopwatch.ms);
			Logger.i().logs("Applying transformers %s...\n",
					transformers.toString());
			stopwatch.reset();
			stopwatch.start();
		}

		return new Iterable<Tree<String>>() {

			public Iterator<Tree<String>> iterator() {
				final Iterator<Tree<String>> rawIt = rawTrees.iterator();
				;
				return new Iterator<Tree<String>>() {

					Tree<String> nextTree = null;
					int count = 0;

					public boolean hasNext() {
						// TODO Auto-generated method stub
						if (count >= maxTrees) {
							return false;
						}
						queueNext();
						return nextTree != null;
					}

					public Tree<String> next() {
						queueNext();
						Tree<String> retTree = nextTree;
						nextTree = null;
						++count;
						return retTree;
					}

					private void queueNext() {
						if (nextTree != null) {
							return;
						}
						if (!rawIt.hasNext()) {
							return;
						}
						Tree<String> tree = rawIt.next();
						for (TreeTransformer<String> transformer : transformers) {
							tree = transformer.transformTree(tree);
						}
						if (tree.getYield().size() > maxLength) {
							queueNext();
							return;
						}
						if (tree.getYield().size() < minLength) {
							queueNext();
							return;
						}
						nextTree = tree;
					}

					public void remove() {
						// TODO Auto-generated method stub
						throw new RuntimeException();
					}

				};
			}
		};
		// for (Tree<String> tree: rawTrees) {
		// for (TreeTransformer<String> transformer: transformers) {
		// tree = transformer.transformTree(tree);
		// }
		// if (tree.getYield().size() > maxLength) {
		// continue;
		// }
		//
		// if (tree.getYield().size() < minLength) {
		// continue;
		// }
		// trees.add(tree);
		// if (trees.size() >= maxTrees) {
		// break;
		// }
		// }
		// if (verbose) {
		// stopwatch.accumStop();
		// LogInfo.logs("Transformed %d trees in %.3f seconds\n", trees.size(),
		// stopwatch.ms);
		// }
		// return trees;
	}

}