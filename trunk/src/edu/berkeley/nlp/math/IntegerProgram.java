package edu.berkeley.nlp.math;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import edu.berkeley.nlp.util.IOUtils;
import edu.berkeley.nlp.util.Option;
import edu.berkeley.nlp.util.StrUtils;
import edu.berkeley.nlp.util.Utils;

public class IntegerProgram {

	@Option(gloss = "Location of the lp_solve executable")
	public static String lpSolveLocation = "/opt/local/bin/lp_solve";
	@Option(gloss = "Maximum time for the solver (disaster strikes if it doesn't finish.")
	public static double maxIntegerProgramTime = -1.0;
	@Option(gloss = "Relaxes integer constraint on variables.")
	public static boolean relaxIntegerConstraint = false;
	@Option(gloss = "Don't optimize; used for profiling.")
	public static boolean dontOptimize = false;
	@Option(gloss = "Don't delete ilp files; used for testing.")
	public static boolean dontDeleteFiles = false;
	@Option(gloss = "Branch and bound depth limit (0 = no limit).")
	public static int depthLimit = 0;

	private int numVars;
	private List<String> constraints;
	private List<String> integerVariables;
	private double[] objectiveCoefficients;
	private double[] lp_solve_solution;
	private double lp_solve_objective_value;
	private boolean maximize;
	private boolean optimized;

	public IntegerProgram() {
		constraints = new ArrayList<String>();
		integerVariables = new ArrayList<String>();
	}

	public void setToMaximize() {
		maximize = true;
	}

	public double objectiveValue() {
		if (!optimized)
			optimize();
		return lp_solve_objective_value;
	}

	public double[] solution() {
		if (!optimized)
			optimize();
		return lp_solve_solution;
	}

	public void suggestSolution(double[] solution) {
	}

	public void cleanUp() {
	}

	public void addObjectiveWeights(List<Integer> indices, List<Double> weights) {
		for (int i = 0; i < indices.size(); i++) {
			addObjectiveWeight(indices.get(i), weights.get(i));
		}
	}

	private void addObjectiveWeight(int pos, double val) {
		if (objectiveCoefficients == null)
			objectiveCoefficients = new double[numVars];
		if (objectiveCoefficients.length < numVars) {
			double[] oldCoef = objectiveCoefficients;
			objectiveCoefficients = new double[numVars];
			System.arraycopy(oldCoef, 0, objectiveCoefficients, 0,
					oldCoef.length);
		}
		objectiveCoefficients[pos] = val;
	}

	public void addObjectiveWeights(int[] indices, double[] weights) {
		for (int i = 0; i < indices.length; i++) {
			addObjectiveWeight(indices[i], weights[i]);
		}
	}

	public void addEqualityConstraint(int var, double weight, double rhs) {
		int[] vars = new int[1];
		double[] weights = new double[1];
		vars[0] = var;
		weights[0] = weight;
		addEqualityConstraint(vars, weights, rhs);
	}

	public void addEqualityConstraint(int[] indices, double[] weights,
			double rhs) {
		addConstraint(indices, weights, rhs, "=");
	}

	public void addLessThanConstraint(int var, double weight, double rhs) {
		int[] vars = new int[1];
		double[] weights = new double[1];
		vars[0] = var;
		weights[0] = weight;
		addLessThanConstraint(vars, weights, rhs);
	}

	public void addLessThanConstraint(int[] indices, double[] weights,
			double rhs) {
		addConstraint(indices, weights, rhs, "<=");
	}

	public void addConstraint(int[] indices, double[] weights, double rhs,
			String op) {
		StringBuilder sb = new StringBuilder();
		assert (indices.length == weights.length);
		for (int i = 0; i < indices.length; i++) {
			sb.append("+ ");
			sb.append(weights[i]);
			sb.append(" ");
			sb.append(var(indices[i]));
			sb.append(" ");
		}
		sb.append(op);
		sb.append(" ");
		sb.append(rhs);
		sb.append(";");
		constraints.add(sb.toString());
	}

	public void addBoundedVars(int k, double lower, double upper) {
		for (int i = numVars; i < k + numVars; i++) {
			integerVariables.add(var(i));
			if (lower != 0)
				addLessThanConstraint(i, -1, lower);
			addLessThanConstraint(i, 1, upper);
		}
		numVars += k;
	}

	private static String var(int i) {
		return "x" + i;
	}

	/* Heavy lifting */

	/**
	 * Optimizes by writing to a file.
	 */
	public void optimize() {
		if (dontOptimize) {
			lp_solve_objective_value = 0;
			lp_solve_solution = new double[numVars];
			optimized = true;
		} else {
			try {
				File temp = File.createTempFile("ilp-", ".mps");
				if (dontDeleteFiles)
					System.err.println("[IntPgrm] " + temp.getPath());
				writeProgram(temp);
				executeLPSolve(temp.getAbsolutePath());
				if (!dontDeleteFiles)
					temp.delete();
				optimized = true;
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	private void writeProgram(File temp) {
		PrintWriter out = IOUtils.openOutHard(temp);

		writeObjective(out);
		for (String c : constraints) {
			out.println(c);
		}
		String intCons = "int " + StrUtils.join(integerVariables, ", ") + ";";
		if (!relaxIntegerConstraint)
			out.println(intCons);
		out.close();
	}

	private void writeObjective(PrintWriter out) {
		out.print(maximize ? "max: " : "min: ");
		assert numVars == objectiveCoefficients.length;
		for (int i = 0; i < numVars; i++) {
			out.print("+ ");
			out.print(objectiveCoefficients[i]);
			out.print(" ");
			out.print(var(i));
			out.print(" ");
		}
		out.print(";\n");
	}

	/**
	 * Solves the problem with lp-solve and store solution in this object.
	 */
	private void executeLPSolve(String problemPath) {
		StringBuilder command = new StringBuilder();
		command.append(lpSolveLocation + " ");
		// command.append(" -fmps ");
		if (maxIntegerProgramTime > 0) {
			command.append("-timeout " + maxIntegerProgramTime + " ");
		}
		command.append("-depth " + depthLimit + " ");
		command.append(problemPath);

		StringWriter output = new StringWriter();
		PrintWriter out = new PrintWriter(output);
		StringWriter error = new StringWriter();
		PrintWriter err = new PrintWriter(error);
		Utils.systemHard(command.toString(), out, err);
		BufferedReader reader = new BufferedReader(new StringReader(
				output.toString()));
		lp_solve_solution = new double[numVars];
		int var = 0;
		try {
			while (reader.ready()) {
				String next = reader.readLine();
				if (next == null)
					return;
				if (next.startsWith("x")) {
					String[] parts = next.trim().split("\\s+");
					if (parts[0].equals("x"))
						continue; // Weird output from lp_solve
					assert (parts[0].equals(var(var)));
					double val = Double.parseDouble(parts[1]);
					lp_solve_solution[var] = val;
					var++;
				} else if (next.startsWith("Value of objective function")) {
					String[] parts = next.split("\\s+");
					lp_solve_objective_value = Double
							.parseDouble(parts[parts.length - 1]);
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/* Testing */

	public static void main(String[] args) {
		dontDeleteFiles = true;
		IntegerProgram ip = new IntegerProgram();
		ip.addBoundedVars(3, 0, 1);
		ip.addObjectiveWeight(0, 10);
		ip.addObjectiveWeight(1, -5);
		ip.addObjectiveWeight(2, 1);
		int[] cvars = { 0, 1, 2 };
		double[] cweights = { 1, 0, 1 };
		ip.setToMaximize();
		ip.addEqualityConstraint(cvars, cweights, 1);
		double[] sol = ip.solution();
		for (int i = 0; i < sol.length; i++) {
			System.out.println("x" + i + ":\t" + sol[i]);
		}
		System.out.println("obj:\t" + ip.objectiveValue());
	}

	/**
	 * combined = left * right
	 */
	public void addAndConstraint(int combined, int left, int right) {
		int[] vars1 = { combined, left };
		int[] vars2 = { combined, right };
		double[] weights = { 1.0, -1.0 };
		addLessThanConstraint(vars1, weights, 0);
		addLessThanConstraint(vars2, weights, 0);
	}

}
