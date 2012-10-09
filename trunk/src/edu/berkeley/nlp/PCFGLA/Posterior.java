/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.io.Serializable;

import edu.berkeley.nlp.util.ArrayUtil;

/**
 * @author petrov
 * 
 */
public class Posterior implements Serializable {
	/**
	 * 
	 */
	// private static final long serialVersionUID = 1L;
	double[][][][] iScore;
	double[][][][] oScore;
	int[][][] iScale;
	int[][][] oScale;
	boolean[][][] allowedStates;

	Posterior(double[][][][] iS, double[][][][] oS, int[][][] i, int[][][] o,
			boolean[][][] a) {
		iScore = ArrayUtil.clone(iS);
		oScore = ArrayUtil.clone(oS);
		iScale = (i != null) ? ArrayUtil.clone(i) : null;
		oScale = (o != null) ? ArrayUtil.clone(o) : null;
		allowedStates = ArrayUtil.clone(a);
		// if (i!=null) System.err.println("in constructor " +iScale.length);
	}

	// public boolean Save(String fileName) {
	// try {
	// ObjectOutputStream out = new ObjectOutputStream(new GZIPOutputStream(new
	// FileOutputStream(fileName)));
	// out.writeObject(this);
	// out.flush();
	// out.close();
	// } catch (IOException e) {
	// System.out.println("IOException: "+e);
	// return false;
	// }
	// return true;
	// }

	// public static Posterior Load(String fileName) {
	// Posterior posterior = null;
	// try {
	// FileInputStream fis = new FileInputStream(fileName); // Load from file
	// GZIPInputStream gzis = new GZIPInputStream(fis); // Compressed
	// ObjectInputStream in = new ObjectInputStream(gzis); // Load objects
	// posterior = (Posterior)in.readObject(); // Read the mix of grammars
	// in.close(); // And close the stream.
	// gzis.close();
	// fis.close();
	// } catch (IOException e) {
	// System.out.println("IOException\n"+e);
	// return null;
	// } catch (ClassNotFoundException e) {
	// System.out.println("Class not found!");
	// return null;
	// }
	// return posterior;
	// }

}
