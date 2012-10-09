package edu.berkeley.nlp.util;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IOUtil {

	public static File createTempDirectory(String prefix) throws IOException {
		File tempFile = File.createTempFile(prefix, "");
		if (!tempFile.delete())
			throw new IOException();
		if (!tempFile.mkdir())
			throw new IOException();
		return tempFile;
	}

	/**
	 * 
	 * @param dir
	 * @param filePrefix
	 * @param fileExt
	 * @param recursive
	 * @return
	 */
	public static List<File> getFilesUnder(final String dir, // Directory
			final String filePrefix, // Prefix for files
			final String fileExt, // Extension of files
			final boolean recursive) {
		List<File> files = new ArrayList<File>();
		File dirFile = new File(dir);
		if (!dirFile.exists())
			return files;
		if (!dirFile.isDirectory())
			return Collections.singletonList(dirFile);
		for (File f : dirFile.listFiles()) {
			if (f.isDirectory()) {
				if (recursive)
					files.addAll(getFilesUnder(f.getAbsolutePath(), filePrefix,
							fileExt, recursive));
				continue;
			}
			String name = f.getName();
			if (name.startsWith(filePrefix) && name.endsWith(fileExt)) {
				files.add(f);
			}
		}
		return files;
	}

	public static FileFilter getFileFilter(final String prefix, final String ext) {
		return new FileFilter() {
			public boolean accept(File pathname) {
				// TODO Auto-generated method stub
				String name = pathname.getName();
				return name.startsWith(prefix) && name.endsWith(ext);
			}
		};
	}

	public static boolean deleteDir(File dir) {
		if (dir.isDirectory()) {
			String[] children = dir.list();
			for (int i = 0; i < children.length; i++) {
				boolean success = deleteDir(new File(dir, children[i]));
				if (!success) {
					return false;
				}
			}
		}

		// The directory is now empty so delete it
		return dir.delete();
	}

	public static FileReader fileReaderHard(String filename) {
		try {
			return new FileReader(filename);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			System.exit(0);
		}
		throw new IllegalStateException();
	}

	public static String getPath(String parentPath, String childName) {
		return new File(parentPath, childName).getPath();
	}
}
