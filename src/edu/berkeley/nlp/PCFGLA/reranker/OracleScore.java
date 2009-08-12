/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import java.util.Arrays;

import edu.berkeley.nlp.util.ArrayUtil;

/**
 * @author dburkett
 *
 */
public class OracleScore {
	int[] values;
	int offset;
	
	public OracleScore() {
		offset = 0;
		values = new int[] { 0 };
	}
	
	private OracleScore(int[] values, int offset) {
		this.values = values;
		this.offset = offset;
	}
	
	public int val(int d) {
		if (d < offset || d >= offset + values.length) {
			return -1;
		}
		return values[d-offset];
	}
	
	public OracleScore shift(int shiftAmount, boolean valueIncrement) {
		int[] newValues = ArrayUtil.add(values, wrap(valueIncrement));
		return new OracleScore(newValues, offset+shiftAmount);
	}

	public static OracleScore multiply(OracleScore f1, OracleScore f2) {
		if (f1 == null || f2 == null) return null;
		int newOffset = f1.offset+f2.offset;
		int newLength = f1.values.length + f2.values.length - 1;
		int[] newValues = new int[newLength];
		Arrays.fill(newValues, 0);
		for (int i=f1.offset; i<f1.offset+f1.values.length; i++) {
			for (int j=f2.offset; j<f2.offset+f2.values.length; j++) {
				if (f1.val(i) >=0 && f2.val(j) >= 0) {
					newValues[i+j-newOffset] = Math.max(newValues[i+j-newOffset], f1.val(i)+f2.val(j));
				}
			}
		}
		return new OracleScore(newValues, newOffset);
	}

	public static OracleScore add(OracleScore f1, OracleScore f2) {
		if (f1 == null) return f2;
		if (f2 == null) return f1;
		int newOffset = Math.min(f1.offset,f2.offset);
		int newLength = Math.max(f1.offset+f1.values.length, f2.offset+f2.values.length) - newOffset;
		int[] newValues = new int[newLength];
		for (int i=0; i<newValues.length; i++) {
			newValues[i] = Math.max(f1.val(i+newOffset), f2.val(i+newOffset));
		}
		return new OracleScore(newValues, newOffset);
	}
	
	private int wrap(boolean val) {
		return val ? 1 : 0;
	}

	public int getBestF1Size(int numGoldNodes) {
		int bestT = -1;
		double bestF1 = -1;
		for (int t=offset; t<offset+values.length; t++) {
			double f1 = (2.0*val(t)-2) / (t+numGoldNodes-2);
			if (f1 > bestF1) {
				bestT = t;
				bestF1 = f1;
			}
		}
		return bestT;
	}

	public static int checkAttainableAndFindSplit(OracleScore f1, OracleScore f2, int t, int val) {
		if (f1 == null || f2 == null) {
			return -1;
		}
		for (int t1 = f1.offset; t1<f1.offset+f1.values.length; t1++) {
			if (t1 > t) continue;
			int t2 = t - t1;
			if (t2 < f2.offset || t2 >= f2.offset+f2.values.length) continue;
			if (f1.val(t1) + f2.val(t2) == val) {
				return t1;
			}
		}
		return -1;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		String desc = "Score: ";
		for (int t=offset; t<offset+values.length; t++) {
			desc = desc + t +"=" + val(t) + " ";
		}
		return desc.trim();
	}
}
