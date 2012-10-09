package edu.berkeley.nlp.PCFGLA;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.MapFactory;

public class BinaryCounterTable implements Serializable {
	/**
	 * Based on Counter.
	 * 
	 * A map from objects to doubles. Includes convenience methods for getting,
	 * setting, and incrementing element counts. Objects not in the counter will
	 * return a count of zero. The counter is backed by a HashMap (unless
	 * specified otherwise with the MapFactory constructor).
	 * 
	 * @author Slav Petrov
	 */
	private static final long serialVersionUID = 1L;
	Map<BinaryRule, double[][][]> entries;
	short[] numSubStates;
	BinaryRule searchKey;

	/**
	 * The elements in the counter.
	 * 
	 * @return set of keys
	 */
	public Set<BinaryRule> keySet() {
		return entries.keySet();
	}

	/**
	 * The number of entries in the counter (not the total count -- use
	 * totalCount() instead).
	 */
	public int size() {
		return entries.size();
	}

	/**
	 * True if there are no entries in the counter (false does not mean
	 * totalCount > 0)
	 */
	public boolean isEmpty() {
		return size() == 0;
	}

	/**
	 * Returns whether the counter contains the given key. Note that this is the
	 * way to distinguish keys which are in the counter with count zero, and
	 * those which are not in the counter (and will therefore return count zero
	 * from getCount().
	 * 
	 * @param key
	 * @return whether the counter contains the key
	 */
	public boolean containsKey(BinaryRule key) {
		return entries.containsKey(key);
	}

	/**
	 * Get the count of the element, or zero if the element is not in the
	 * counter. Can return null!
	 * 
	 * @param key
	 * @return
	 */
	public double[][][] getCount(BinaryRule key) {
		double[][][] value = entries.get(key);
		return value;
	}

	public double[][][] getCount(short pState, short lState, short rState) {
		searchKey.setNodes(pState, lState, rState);
		double[][][] value = entries.get(searchKey);
		return value;
	}

	/**
	 * Set the count for the given key, clobbering any previous count.
	 * 
	 * @param key
	 * @param count
	 */
	public void setCount(BinaryRule key, double[][][] counts) {
		entries.put(key, counts);
	}

	/**
	 * Increment a key's count by the given amount. Assumes for efficiency that
	 * the arrays have the same size.
	 * 
	 * @param key
	 * @param increment
	 */
	public void incrementCount(BinaryRule key, double[][][] increment) {
		double[][][] current = getCount(key);
		if (current == null) {
			setCount(key, increment);
			return;
		}
		for (int i = 0; i < current.length; i++) {
			for (int j = 0; j < current[i].length; j++) {
				// test if increment[i][j] is null or zero, in which case
				// we needn't add it
				if (increment[i][j] == null)
					continue;
				// allocate more space as needed
				if (current[i][j] == null)
					current[i][j] = new double[increment[i][j].length];
				// if we've gotten here, then both current and increment
				// have correct arrays in index i
				for (int k = 0; k < current[i][j].length; k++) {
					current[i][j][k] += increment[i][j][k];
				}
			}
		}
		setCount(key, current);
	}

	public void incrementCount(BinaryRule key, double increment) {
		double[][][] current = getCount(key);
		if (current == null) {
			double[][][] tmp = key.getScores2();
			current = new double[tmp.length][tmp[0].length][tmp[0][0].length];
			ArrayUtil.fill(current, increment);
			setCount(key, current);
			return;
		}
		for (int i = 0; i < current.length; i++) {
			for (int j = 0; j < current[i].length; j++) {
				if (current[i][j] == null)
					current[i][j] = new double[numSubStates[key
							.getParentState()]];
				for (int k = 0; k < current[i][j].length; k++) {
					current[i][j][k] += increment;
				}
			}
		}
		setCount(key, current);
	}

	public BinaryCounterTable(short[] numSubStates) {
		this(new MapFactory.HashMapFactory<BinaryRule, double[][][]>(),
				numSubStates);
	}

	public BinaryCounterTable(MapFactory<BinaryRule, double[][][]> mf,
			short[] numSubStates) {
		entries = mf.buildMap();
		searchKey = new BinaryRule((short) 0, (short) 0, (short) 0);
		this.numSubStates = numSubStates;
	}

	public static void main(String[] args) {
		Counter<String> counter = new Counter<String>();
		System.out.println(counter);
		counter.incrementCount("planets", 7);
		System.out.println(counter);
		counter.incrementCount("planets", 1);
		System.out.println(counter);
		counter.setCount("suns", 1);
		System.out.println(counter);
		counter.setCount("aliens", 0);
		System.out.println(counter);
		System.out.println(counter.toString(2));
		System.out.println("Total: " + counter.totalCount());
	}
}
