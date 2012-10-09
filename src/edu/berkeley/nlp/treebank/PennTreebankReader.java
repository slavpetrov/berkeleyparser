package edu.berkeley.nlp.treebank;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees;
import edu.berkeley.nlp.util.ConcatenationIterator;

/**
 * @author Dan Klein
 */
public class PennTreebankReader {

	static class TreeCollection extends AbstractCollection<Tree<String>> {

		List<File> files;

		static class TreeIteratorIterator implements
				Iterator<Iterator<Tree<String>>> {
			Iterator<File> fileIterator;
			Iterator<Tree<String>> nextTreeIterator;

			public boolean hasNext() {
				return nextTreeIterator != null;
			}

			public Iterator<Tree<String>> next() {
				Iterator<Tree<String>> currentTreeIterator = nextTreeIterator;
				advance();
				return currentTreeIterator;
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}

			private void advance() {
				nextTreeIterator = null;
				while (nextTreeIterator == null && fileIterator.hasNext()) {
					try {
						File file = fileIterator.next();
						nextTreeIterator = new Trees.PennTreeReader(
								new BufferedReader(new FileReader(file)));
					} catch (FileNotFoundException e) {
					}
				}
			}

			TreeIteratorIterator(List<File> files) {
				this.fileIterator = files.iterator();
				advance();
			}
		}

		@Override
		public Iterator<Tree<String>> iterator() {
			return new ConcatenationIterator<Tree<String>>(
					new TreeIteratorIterator(files));
		}

		@Override
		public int size() {
			int size = 0;
			Iterator i = iterator();
			while (i.hasNext()) {
				size++;
				i.next();
			}
			return size;
		}

		private List<File> getFilesUnder(String path, FileFilter fileFilter) {
			File root = new File(path);
			List<File> files = new ArrayList<File>();
			addFilesUnder(root, files, fileFilter);
			return files;
		}

		private void addFilesUnder(File root, List<File> files,
				FileFilter fileFilter) {
			if (!fileFilter.accept(root))
				return;
			if (root.isFile()) {
				files.add(root);
				return;
			}
			if (root.isDirectory()) {
				File[] children = root.listFiles();
				for (int i = 0; i < children.length; i++) {
					File child = children[i];
					addFilesUnder(child, files, fileFilter);
				}
			}
		}

		public TreeCollection(String path, int lowFileNum, int highFileNum) {
			this(path, lowFileNum, highFileNum, ".mrg");
		}

		public TreeCollection(String path, int lowFileNum, int highFileNum,
				String suffix) {
			FileFilter fileFilter = new NumberRangeFileFilter(suffix,
					lowFileNum, highFileNum, true);
			this.files = getFilesUnder(path, fileFilter);
			Collections.sort(this.files);
		}

	}

	public static Collection<Tree<String>> readTrees(String path) {
		return readTrees(path, -1, Integer.MAX_VALUE);
	}

	public static Collection<Tree<String>> readTrees(String path,
			int lowFileNum, int highFileNumber) {
		return new TreeCollection(path, lowFileNum, highFileNumber);
	}

	public static void main(String[] args) {
		Collection<Tree<String>> trees = readTrees(args[0]);
		for (Tree<String> tree : trees) {
			tree = (new Trees.StandardTreeNormalizer()).transformTree(tree);
			System.out.println(Trees.PennTreeRenderer.render(tree));
		}
	}

}