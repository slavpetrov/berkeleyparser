package edu.berkeley.nlp.bitext;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import edu.berkeley.nlp.util.Counter;
import fig.basic.Pair;
import fig.basic.IOUtils;
import fig.basic.LogInfo;
import fig.basic.Option;
import fig.exec.Execution;

/**
 * Reads a bitext grammar with {lhs, rhs, weights} files. Factorizes weights for
 * the left and right grammar, writes {lhsWeights, rhsWeights} to the same root
 * as the {lhs, rhs, weights} files.
 * 
 * @author Aria Haghighi
 * 
 */
public class BitextGrammarFactorizer {

	@Option(gloss = "Root name of the grammar files to be generated.")
	public static String grammarWriteRoot = "grammar";

	@Option(gloss = "Coefficient in the optimization problem for projection probabilities.")
	public static double phiCoefficient = .0001;

	@Option(gloss = "Location of the grammar files to be read.")
	public static String grammarReadRoot;

	@Option(gloss = "Optimizes with quadratic slack terms.")
	public static boolean useQuadraticSlack = false;

	@Option(gloss = "Optimizes with quadratic projection objective.")
	public static boolean useQuadraticForPhi = false;

	@Option(gloss = "Old experimental framework uses a linear objective.")
	public static boolean oldObjectives = false;

	@Option(gloss = "Penalize gaps on the lhs more than the rhs.")
	public static boolean useLopsidedObjective = false;

	@Option(gloss = "Allow for negative gap.")
	public static boolean allowNegativeGap = false;

	@Option(gloss = "Project a single monolingual grammar via max.")
	public static boolean useMonolingualMaxProgram = false;

	@Option(gloss = "Prevents inadmissibility by rounding errors in Monolingual max program.")
	public static double admissibilityMargin = 0.00; // 0.00001 changes things
																								// significantly

	private MosekSolver solver = new MosekSolver();

	private String lhsWeightsFile;

	private String rhsWeightsFile;

	/**
	 * Optimization Wrapper For a Rule Object, must distingusih which grammar it
	 * comes from (left or right) since there may be a left S-> NP VP as well as a
	 * right one.
	 * 
	 * @author Aria Haghighi
	 * 
	 */
	private static class RuleVar {
		private String rule;

		private boolean left;

		public RuleVar(String rule, boolean left) {
			super();
			this.rule = rule;
			this.left = left;
		}

		@Override
		public int hashCode() {
			final int PRIME = 31;
			int result = 1;
			result = PRIME * result + (left ? 1231 : 1237);
			result = PRIME * result + ((rule == null) ? 0 : rule.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			final RuleVar other = (RuleVar) obj;
			if (left != other.left)
				return false;
			if (rule == null) {
				if (other.rule != null)
					return false;
			} else if (!rule.equals(other.rule))
				return false;
			return true;
		}

		public String toString() {
			return String.format("%s [%s]", rule, left);
		}
	}

	/**
	 * Strips the indexing annotation (S NP[0] NP[1]) ==> S NP VP
	 * 
	 * @param line
	 * @return
	 */
	private String getRule(String line) {
		line = line.replaceAll("\\(", "");
		line = line.replaceAll("\\)", "");
		line = line.replaceAll("\\[\\d+\\]", "");
		return line;
	}

	/**
	 * Factor Bitext Grammar. Assumes {lhs,rhs,weights}Reader point to the
	 * respective grammar file.
	 * 
	 * @param lhsReader
	 * @param rhsReader
	 * @param weightsReader
	 */
	@SuppressWarnings("unchecked")
	public void factor(BufferedReader lhsReader, BufferedReader rhsReader,
			BufferedReader weightsReader) {

		Counter<RuleVar> varsCounter = new Counter<RuleVar>();
		Counter<RuleVar> lvarsCounter = new Counter<RuleVar>();

		List<Pair<String, String>> rules = new ArrayList<Pair<String, String>>();
		List<Double> weights = new ArrayList<Double>();

		LogInfo.track("Reading bitext grammar");
		try {
			while (true) {
				String lhs = lhsReader.readLine();
				String rhs = rhsReader.readLine();
				String weightLine = weightsReader.readLine();

				if (lhs == null || rhs == null || weightLine == null) {
					break;
				}

				lhs = getRule(lhs);
				rhs = getRule(rhs);
				double weight = Math.log(Double.parseDouble(weightLine));

				rules.add(new Pair<String, String>(lhs, rhs));
				weights.add(weight);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		LogInfo.end_track();

		LogInfo.track("Writing the optimization problem");
		for (int i = 0; i < rules.size(); ++i) {
			String lhs = rules.get(i).getFirst();
			String rhs = rules.get(i).getSecond();
			double weight = weights.get(i);

			RuleVar lhsVar = new RuleVar(lhs, true);
			RuleVar rhsVar = new RuleVar(rhs, false);

			// Object slackVar = new Object();

			if (oldObjectives) {
				if (useQuadraticSlack) {
					Object slackVar = new Object();
					double[] slackCoefs = { 1.0, 1.0, 1.0 };
					Object[] slackVars = { lhsVar, rhsVar, slackVar };
					solver.addEqualityConstraint(slackCoefs, slackVars, weight);

					Object positiveSlack = new Object();
					double[] positiveSlackCoefs = { 1.0, -1.0 };
					Object[] positiveSlackVars = { positiveSlack, slackVar };
					solver.addGreaterThanConstraint(positiveSlackCoefs, positiveSlackVars, 0.0);
					solver.addQuadraticObjectiveTerm(1.0, positiveSlack, positiveSlack);
				} else {
					Object[] vars = { lhsVar, rhsVar };
					double[] coefs = { 1.0, 1.0 };
					solver.addGreaterThanConstraint(coefs, vars, weight);
				}
			} else { // New objective function
				if (useMonolingualMaxProgram) {
					double[] coef = { 1.0 };
					Object[] lvar = { lhsVar };
					Object[] rvar = { rhsVar };
					solver.addGreaterThanConstraint(coef, lvar, weight + admissibilityMargin);
					// Admissibility gap prevents inadmissibility from rounding.
					solver.addEqualityConstraint(coef, rvar, 0.0);
					if (!varsCounter.containsKey(lhsVar)) {
						solver.addLinearObjectiveTerm(lhsVar, 1.0);
					}
				} else {
					if (allowNegativeGap) {
						// Constraint with slack
						Object gapVar = new Object();
						Object slackVar = new Object();
						double[] gapCoefs = { 1.0, 1.0, -1.0, 1.0 };
						Object[] constraintVars = { lhsVar, rhsVar, gapVar, slackVar };
						solver.addEqualityConstraint(gapCoefs, constraintVars, weight);

						// Make sure only one slack constraint is active
						double[] slackCoefs = { -1.0, 1.0 };
						Object[] slackVars = { gapVar, slackVar };
						solver.addEqualityConstraint(slackCoefs, slackVars, 0.0);
					} else {
						Object gapVar = new Object();
						double[] gapCoefs = { 1.0, 1.0, -1.0 };
						Object[] constraintVars = { lhsVar, rhsVar, gapVar };
						solver.addEqualityConstraint(gapCoefs, constraintVars, weight);
						solver.addQuadraticObjectiveTerm(1.0, gapVar, gapVar);
					}
				}
			}

			varsCounter.incrementCount(lhsVar, 1.0);
			varsCounter.incrementCount(rhsVar, 1.0);
			lvarsCounter.incrementCount(lhsVar, 1.0);
		}

		for (RuleVar ruleVar : varsCounter.keySet()) {
			if (oldObjectives) {
				double count = varsCounter.getCount(ruleVar);
				if (useQuadraticForPhi) {
					solver.addQuadraticObjectiveTerm(phiCoefficient, ruleVar, ruleVar);
					// Just changed this to -1 after running it
				} else {
					if (useLopsidedObjective && lvarsCounter.containsKey(ruleVar)) {
						solver.addLinearObjectiveTerm(ruleVar, 100000 * count);
					} else {
						solver.addLinearObjectiveTerm(ruleVar, count);
					}
				}
			}

			// Bounds [-infty, 0]
			solver.addLowerBound(ruleVar, -10000.0);
			solver.addUpperBound(ruleVar, 0.0);
		}
		solver.minimize();
		// Solve this sucka faster
		// solver.setNumProcessors(2);
		LogInfo.end_track();

		LogInfo.track("Solving the optimization problem");
		Counter solution = solver.solve();
		LogInfo.end_track();

		LogInfo.track("Writing the solution");
		PrintWriter lhsWriter = IOUtils.openOutHard(lhsWeightsFile);
		PrintWriter rhsWriter = IOUtils.openOutHard(rhsWeightsFile);

		for (int i = 0; i < rules.size(); ++i) {
			String lhs = rules.get(i).getFirst();
			String rhs = rules.get(i).getSecond();

			RuleVar lhsVar = new RuleVar(lhs, true);
			RuleVar rhsVar = new RuleVar(rhs, false);

			assert solution.containsKey(lhsVar) : String.format("Don't have %s\n", lhsVar);
			assert solution.containsKey(rhsVar) : String.format("Don't have %s\n", rhsVar);
			double lhsWeight = solution.getCount(lhsVar);
			double rhsWeight = solution.getCount(rhsVar);

			lhsWriter.printf("%f\n", lhsWeight);
			rhsWriter.printf("%f\n", rhsWeight);

		}
		lhsWriter.flush();
		rhsWriter.flush();
		lhsWriter.close();
		rhsWriter.close();
		LogInfo.end_track();

		// double slackSum = 0.0;
		// for (Object key: solution.keySet()) {
		// double count = solution.getCount(key);
		// if (!(key instanceof RuleVar)) {
		// slackSum += Math.exp(-count);
		// }
		// }
		// System.out.println("Slack Sum Log Probs: " + slackSum);
	}

	/**
	 * java edu.berkeley.nlp.bitext.mix.BitextGrammarFactorizer leftMarkov/final
	 * 
	 * Assumes leftMarkov/final.{lhs, rhs, weights} exists and all have the same
	 * number of lines writes leftMarkov/final.lhsWeights and
	 * leftMarkov/final.rhsWeights files which contain factored rule weights for
	 * the left and right grammar.
	 * 
	 * @param args
	 */
	public static void main() {

		String lhs = new File(grammarReadRoot + ".lhs").getAbsolutePath();
		String rhs = new File(grammarReadRoot + ".rhs").getAbsolutePath();
		String weights = new File(grammarReadRoot + ".weights").getAbsolutePath();

		// Create symlinks to input files for easy parsing later.
		String writeRoot = Execution.getFile(grammarWriteRoot);
		IOUtils.createSymLink(lhs, writeRoot + ".lhs");
		IOUtils.createSymLink(rhs, writeRoot + ".rhs");
		IOUtils.createSymLink(weights, writeRoot + ".weights");

		// Open input files.
		BufferedReader lhsReader = IOUtils.openInHard(lhs);
		BufferedReader rhsReader = IOUtils.openInHard(rhs);
		BufferedReader weightsReader = IOUtils.openInHard(weights);
		BitextGrammarFactorizer bgf = new BitextGrammarFactorizer();

		// Open output files.
		bgf.lhsWeightsFile = writeRoot + ".lhsWeights";
		bgf.rhsWeightsFile = writeRoot + ".rhsWeights";

		// Factor
		bgf.factor(lhsReader, rhsReader, weightsReader);

		// Clean up
		try {
			lhsReader.close();
			rhsReader.close();
			weightsReader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

}
