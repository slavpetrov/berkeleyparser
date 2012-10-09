package edu.berkeley.nlp.math;

public class DoubleMatrices {
	/**
	 * Returns the matrix transpose. Assumes a non-empty rectangular matrix.
	 */
	public static double[][] transpose(double[][] A) {
		double[][] B = new double[A[0].length][A.length];
		for (int i = 0; i < B.length; i++) {
			for (int j = 0; j < B[0].length; j++) {
				B[i][j] = A[j][i];
			}
		}
		return B;
	}

	/**
	 * Extracts column j and returns it as a single array. Assumes a non-empty
	 * rectangular matrix.
	 */
	public static double[] getColumnVector(double[][] A, int j) {
		double[] v = new double[A.length];
		for (int i = 0; i < v.length; i++) {
			v[i] = A[i][j];
		}
		return v;
	}

	/**
	 * Returns the standard matrix product of two double matrices.
	 */
	public static double[][] product(double[][] A, double[][] B) {
		if (A[0].length != B.length)
			throw new RuntimeException("cols in A (" + A.length
					+ ") differs from rows in B ( " + B.length + ")");
		double[][] C = new double[A.length][B[0].length];
		for (int i = 0; i < A.length; i++) {
			for (int j = 0; j < B[0].length; j++) {
				C[i][j] = DoubleArrays.innerProduct(A[i],
						DoubleMatrices.getColumnVector(B, j));
			}
		}
		return C;
	}

	public static double add(double[][] m) {
		double sum = 0.0;
		for (double[] row : m) {
			sum += DoubleArrays.add(row);
		}
		return sum;
	}

	/**
	 * Returns the matrix product v * B (v is treated as a row vector)
	 */
	public static double[] product(double[] v, double[][] B) {
		double[][] A = new double[1][];
		A[0] = v;
		return product(A, B)[0];
	}

	/**
	 * Returns the matrix product A * v (v is treated as a column vector)
	 */
	public static double[] product(double[][] A, double[] v) {
		double[][] B = new double[1][];
		B[0] = v;
		return transpose(product(A, transpose(B)))[0];
	}

	public static void normalizeEachRow(double[][] M) {
		for (double[] row : M) {
			DoubleArrays.probabilisticNormalize(row);
		}
	}

	public static void scale(double[][] M, double scale) {
		for (double[] row : M) {
			DoubleArrays.scale(row, scale);
		}
	}

	public static double[][] constantMatrix(double c, int m, int n) {
		double[][] M = new double[m][n];
		for (int i = 0; i < m; i++) {
			for (int j = 0; j < n; j++) {
				M[i][j] = c;
			}
		}
		return M;
	}
}
