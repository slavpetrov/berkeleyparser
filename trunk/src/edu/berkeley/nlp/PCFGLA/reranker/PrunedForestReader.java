/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.Arrays;

import edu.berkeley.nlp.util.Logger;
import fig.basic.IOUtils;

/**
 * @author dburkett
 *
 */
public class PrunedForestReader {
	private final File folder;
	private final String[] files;
	
	private ObjectInputStream currentStream = null;
	private int currentIndex = -1;
	private boolean currentStreamHasNext = false;
	
	public PrunedForestReader(File folder, final String filenameFilter) {
		this.folder = folder;
		files = folder.list(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.contains(filenameFilter);
			}
		});
		Arrays.sort(files);
		openNextStream();
	}
	
	public void reset() {
		currentIndex = -1;
		openNextStream();
	}

	private void openNextStream() {
		try {
			if (currentStream != null) {
				currentStream.close();
				currentStream = null;
			}
			currentIndex++;
			if (currentIndex < files.length) {
				currentStream = IOUtils.openObjIn(new File(folder, files[currentIndex]));
				currentStreamHasNext = currentStream.readBoolean();
			}
		} catch(IOException e) {
			Logger.err("Error opening forest file: " + e);
			currentStream = null;
		}
	}

	public PrunedForest getNextForest() {
		try {
			while (!currentStreamHasNext && currentStream != null) {
				openNextStream();
			}
			if (currentStream == null) {
				return null;
			}
			PrunedForest nextForest = (PrunedForest)currentStream.readObject();
			currentStreamHasNext = currentStream.readBoolean();
			return nextForest;
		} catch(Exception e) {
			Logger.err("Error reading forest object: " + e);
			return null;
		}
	}

}
