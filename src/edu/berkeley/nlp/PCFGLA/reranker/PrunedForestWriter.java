/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;

import edu.berkeley.nlp.util.Logger;
import fig.basic.IOUtils;

/**
 * @author dburkett
 *
 */
public class PrunedForestWriter {
	private final File folder;
	private final String baseFilename;
	private final int forestsPerFile;
	private final boolean overwrite;
	
	private int forestsWritten = 0;
	private ObjectOutputStream currentOutputStream = null;
	
	public PrunedForestWriter(File folder, String baseFilename, int forestsPerFile, boolean overwrite) {
		this.folder = folder;
		this.baseFilename = baseFilename;
		this.forestsPerFile = forestsPerFile;
		this.overwrite = overwrite;
		if (folder.exists() && !folder.isDirectory()) {
			if (overwrite) {
				if (!folder.delete()) {
					throw new IllegalArgumentException("Cannot remove file: " + folder);
				}
			}
			else {
				throw new IllegalArgumentException("File already exists: " + folder);
			}
		}
		if (!folder.exists() && !folder.mkdir()) {
			throw new IllegalArgumentException("Cannot create directory: " + folder);
		}
	}
	
	public void writeForest(PrunedForest forest) {
		try {
			if (currentOutputStream == null) {
				openOutputStream();
			}
			currentOutputStream.writeBoolean(true);
			currentOutputStream.writeObject(forest);
			forestsWritten++;
			if (forestsWritten % forestsPerFile == 0) {
				closeOutputStream();
			}
		} catch(IOException e) {
			Logger.err("Error writing forest: " + e);
		}
	}
	
	private void openOutputStream() throws IOException {
		int fileIndex = forestsWritten / forestsPerFile;
		File outputFile = new File(folder, baseFilename.replace(".", pad(fileIndex, 4)+"."));
		if (outputFile.exists()) {
			if (overwrite) {
				if (!outputFile.delete()) {
					throw new IOException("Cannot delete file: " + outputFile);
				}
			} else {
				throw new IOException("File already exists: " + outputFile);
			}
		}
		currentOutputStream = IOUtils.openObjOut(outputFile);
	}

	private String pad(int val, int length) {
		String s = new Integer(val).toString();
		for (int i=s.length(); i<length; i++) {
			s = "0" + s;
		}
		return s;
	}

	public void closeOutputStream() {
		if (currentOutputStream == null) return;
		try {
			currentOutputStream.writeBoolean(false);
			currentOutputStream.close();
			currentOutputStream = null;
		}
		catch(IOException e) {
			Logger.err("Error closing output stream: " + e);
		}
	}
}
