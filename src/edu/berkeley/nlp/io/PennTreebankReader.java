package edu.berkeley.nlp.io;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees;
import edu.berkeley.nlp.util.ConcatenationIterator;

/**
 * @author Dan Klein
 */
public class PennTreebankReader {

	static class TreeCollection extends AbstractCollection<Tree<String>> {

		List<File> files;
		Charset charset;

		static class TreeIteratorIterator implements
				Iterator<Iterator<Tree<String>>> {
			Iterator<File> fileIterator;
			Iterator<Tree<String>> nextTreeIterator;
			Charset charset;
			BufferedReader currentFileReader, lastReader, readerToClose;

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
					File file = fileIterator.next();
					// System.out.println(file);
					try {
						if (readerToClose != null) {
							// System.out.println("closing "+lastReader.toString());
							readerToClose.close();
						}
						readerToClose = lastReader;
						lastReader = currentFileReader;
						// currentFileReader = new BufferedReader(
						// new InputStreamReader(new FileInputStream(file),
						// this.charset));
						// nextTreeIterator = new
						// Trees.PennTreeReader(currentFileReader);
						nextTreeIterator = new Trees.PennTreeReader(
								new BufferedReader(
										new InputStreamReader(
												new FileInputStream(file),
												this.charset)));
					} catch (FileNotFoundException e) {
					} catch (UnsupportedCharsetException e) {
						throw new Error("Unsupported charset in file "
								+ file.getPath());
					} catch (IOException e) {
						new Error("Error closing file handle");
					}
				}
				if (readerToClose != null) {
					try {
						readerToClose.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}

			TreeIteratorIterator(List<File> files, Charset charset) {
				this.fileIterator = files.iterator();
				this.charset = charset;
				advance();
			}
		}

		@Override
		public Iterator<Tree<String>> iterator() {
			return new ConcatenationIterator<Tree<String>>(
					new TreeIteratorIterator(files, this.charset));
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
				SortedSet<File> children = new TreeSet<File>(Arrays.asList(root
						.listFiles()));
				for (File child : children) {

					addFilesUnder(child, files, fileFilter);
				}
			}
		}

		public TreeCollection(String path, int lowFileNum, int highFileNum,
				Charset charset) {
			FileFilter fileFilter = new NumberRangeFileFilter(".mrg",
					lowFileNum, highFileNum, true);
			this.files = getFilesUnder(path, fileFilter);
			// for (File f : files) System.out.println(f.toString());
			this.charset = charset;
		}

		public TreeCollection(String path, int lowFileNum, int highFileNum,
				String charsetName) {
			this(path, lowFileNum, highFileNum, Charset.forName(charsetName));
		}

		public TreeCollection(String path, int lowFileNum, int highFileNum) {
			this(path, lowFileNum, highFileNum, Charset.defaultCharset());
		}
	}

	public static Collection<Tree<String>> readTrees(String path,
			Charset charset) {
		return readTrees(path, -1, Integer.MAX_VALUE, charset);
	}

	public static Collection<Tree<String>> readTrees(String path,
			int lowFileNum, int highFileNumber, Charset charset) {
		return new TreeCollection(path, lowFileNum, highFileNumber, charset);
	}

	public static void main(String[] args) {
		Collection<Tree<String>> trees = readTrees(args[0],
				Charset.defaultCharset());
		for (Tree<String> tree : trees) {
			tree = (new Trees.StandardTreeNormalizer()).transformTree(tree);
			System.out.println(Trees.PennTreeRenderer.render(tree));
		}
	}

}
