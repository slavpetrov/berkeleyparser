package edu.berkeley.nlp.util;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Class representing a sparse array. A <code>double[]</code> and
 * <code>int[]</code> arrays store a sparse reprsentation of a double array.
 * Arrays are grown behind the scene when necessary. Setting/getting a count
 * takes time O(log n), where n is the number of non-zero elements.
 * 
 * @author aria42
 * 
 */
public class SparseArray<T> implements Serializable {

	private static final long serialVersionUID = 42L;

	T[] values = (T[]) new Object[0];
	int[] indices = new int[0];
	int length = 0;

	private void grow() {
		int curSize = values.length;
		int newSize = curSize + 10;

		T[] newData = (T[]) new Object[newSize];
		System.arraycopy(values, 0, newData, 0, curSize);
		values = newData;
		int[] newIndices = new int[newSize];
		System.arraycopy(indices, 0, newIndices, 0, curSize);
		for (int i = curSize; i < newIndices.length; ++i) {
			newIndices[i] = Integer.MAX_VALUE;
		}
		indices = newIndices;
	}

	public T get(int index) {
		int res = Arrays.binarySearch(indices, index);
		if (res >= 0 && res < length) {
			return values[res];
		}
		return null;
	}

	public int size() {
		return length;
	}

	public void put(int index0, T x) {
		int res = Arrays.binarySearch(indices, index0);
		// Greater than everything
		if (res >= 0 && res < length) {
			values[res] = x;
			return;
		}
		if (length + 1 >= values.length) {
			grow();
		}
		// In the middle
		int insertionPoint = -(res + 1);
		assert insertionPoint >= 0 && insertionPoint <= length : String.format(
				"length: %d insertion: %d", length, insertionPoint);
		// Shift The Stuff After
		System.arraycopy(values, insertionPoint, values, insertionPoint + 1,
				length - insertionPoint);
		System.arraycopy(indices, insertionPoint, indices, insertionPoint + 1,
				length - insertionPoint);
		indices[insertionPoint] = index0;
		values[insertionPoint] = x;
		length++;
	}

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("{ ");
		for (int i = 0; i < length; ++i) {
			builder.append(String.format("%d : %.5f", indices[i], values[i]));
			builder.append(" ");
		}
		builder.append(" }");
		return builder.toString();
	}

	public String toString(Indexer<?> indexer) {
		StringBuilder builder = new StringBuilder();
		builder.append("{ ");
		for (int i = 0; i < length; ++i) {
			builder.append(String.format("%s : %.5f",
					indexer.getObject(indices[i]), values[i]));
			builder.append(" ");
		}
		builder.append("}");
		return builder.toString();
	}

	public static void main(String[] args) {
		SparseArray<Double> sv = new SparseArray<Double>();
		sv.put(0, 1.0);
		sv.put(1, 2.0);
		sv.put(4, -2.0);
		System.out.println(sv);
	}

}
