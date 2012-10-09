package edu.berkeley.nlp.treebank;

import java.io.File;
import java.io.FileFilter;

/**
 * Accepts files based on the (last) number in their filename. Optionally
 * restricts based on extensions, as well.
 * 
 * @author Dan Klein
 */
class NumberRangeFileFilter implements FileFilter {
	int highFileNum;
	int lowFileNum;
	String extension;
	boolean recurse;

	public boolean accept(File pathname) {
		if (pathname.isDirectory())
			return recurse;
		String name = pathname.getName();
		if (!name.endsWith(extension))
			return false;
		int lastNumberIndex = getLastNumberIndex(name);
		if (lastNumberIndex == -1)
			return false;
		int numEndLoc = lastNumberIndex + 1;
		int numStartLoc = getLastNonNumberIndex(name, lastNumberIndex) + 1;
		int fileNum = Integer.parseInt(name.substring(numStartLoc, numEndLoc));
		if (fileNum >= lowFileNum && fileNum <= highFileNum)
			return true;
		return false;
	}

	private int getLastNonNumberIndex(String name, int lastNumberIndex) {
		int index = lastNumberIndex - 1;
		while (index >= 0 && Character.isDigit(name.charAt(index))) {
			index--;
		}
		if (index < -1)
			return -1;
		return index;
	}

	private int getLastNumberIndex(String name) {
		int index = name.length() - 1;
		while (index >= 0 && !Character.isDigit(name.charAt(index))) {
			index--;
		}
		return index;
	}

	public NumberRangeFileFilter(String extension, int lowFileNum,
			int highFileNum, boolean recurse) {
		this.highFileNum = highFileNum;
		this.lowFileNum = lowFileNum;
		this.extension = extension;
		this.recurse = recurse;
	}
}
