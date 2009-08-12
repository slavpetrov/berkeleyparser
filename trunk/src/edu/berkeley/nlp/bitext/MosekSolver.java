package edu.berkeley.nlp.bitext;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import edu.berkeley.nlp.util.CollectionUtils;
import edu.berkeley.nlp.util.Counter;
import fig.basic.Indexer;
import fig.basic.IOUtils;
import fig.basic.LogInfo;
import fig.basic.Option;
import fig.exec.Execution;


public class MosekSolver {

	@Option(gloss="Location of mosek (i.e., .../bin/mosek).")
	public static String mosekPath = "C:\\mosek\\4\\tools\\platform\\win\\bin\\mosek";
	
	private String rowFile ;
	private PrintStream rowOut ;

	private String colFile ;
	private PrintStream colOut ;

	private String rhsFile ;
	private PrintStream rhsOut ;
	
	private String boundFile ;
	private PrintStream boundOut ;

	private String qsecFile ;
	private PrintStream qsecOut ;
	
	private int numConstraints = 0;

	private Indexer varIndexer = new Indexer();
	private Counter varCoefs = new Counter();
	
	private boolean minimize = true;
	private boolean sawBound = false;
	private boolean sawQSection = false;
		
	private double maxTime = Double.POSITIVE_INFINITY;
	private int maxIters = 400;
	private int numProcessors = 1;
	
	public void setNumProcessors(int numProcessors) {
		this.numProcessors = numProcessors;
	}
	
	public void setMaxTime(double maxTime) {
		this.maxTime = maxTime;
	}
	
	public void setMaxIters(int maxIters) {
		this.maxIters = maxIters;
	}
	
	public void maximize() {
		minimize = false;
	}
	
	public void minimize() {
		minimize = true;
	}
	
	private int getVarIndex(Object var) {
		int index = varIndexer.indexOf(var);
		if (index == -1) {
			index = varIndexer.size();
			varIndexer.add(var);
		}
		return index;
	}
	
	private String getTempFile(String suffix) {
		File tempFile;
		try {
			tempFile = File.createTempFile("linearprog", suffix);
			tempFile.deleteOnExit();
			return tempFile.getAbsolutePath();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

	public MosekSolver() {

		try {
			rowFile = getTempFile("row");
			rowOut = new PrintStream(rowFile);

			colFile = getTempFile("col");
			colOut = new PrintStream(colFile);

			rhsFile = getTempFile("rhs");
			rhsOut = new PrintStream(rhsFile);
			
			boundFile = getTempFile("bound");
			boundOut = new PrintStream(boundFile);
			
			qsecFile = getTempFile("qsec");
			qsecOut = new PrintStream(qsecFile);
			
		} catch (Exception e) {
			e.printStackTrace();
		}

		rowOut.println("ROWS");
		rowOut.printf("%-1s%-3s%s\n","","N","obj");
		colOut.println("COLUMNS");
		rhsOut.println("RHS");
		qsecOut.printf("%-14s%s\n","QSECTION","obj");
		boundOut.println("BOUNDS");
	}
	
	/**
	 * Add coef * x * y to objective
	 * @param coef
	 * @param x
	 * @param y
	 */
	public void addQuadraticObjectiveTerm(double coef, Object x, Object y) {
		int xIndex = getVarIndex(x);
		int yIndex = getVarIndex(y);
		String xName = String.format("x%d",xIndex);
		String yName = String.format("x%d",yIndex);
		qsecOut.printf("%-4s%-10s%-10s%s\n", "", xName, yName, 2.0 * coef);
		sawQSection = true;
	}
	
	public void addLowerBound(Object x, double lowerBound) {
		int index = getVarIndex(x);
		String xName = String.format("x%d", index);
		boundOut.printf(" %-3s%-10s%-10s%f\n","LO","bounds",xName,lowerBound);
		sawBound = true;
	}
	
	public void addUpperBound(Object x, double upperBound) {
		int index = getVarIndex(x);
		String xName = String.format("x%d", index);
		boundOut.printf(" %-3s%-10s%-10s%f\n","UP","bounds",xName,upperBound);
		sawBound = true;
	}

	/**
	 * Add the constraints sum_i coefs[i] * vars[i] <= rhs
	 * @param coefs
	 * @param vars
	 * @param rhs
	 */
	public void addLessThanConsraint(double[] coefs, Object[] vars, double rhs) {
		numConstraints++;
		String constName = String.format("c%d",numConstraints);		
		rowOut.printf("%-1s%-3s%s\n","","E",constName);		
		commonConstraintStuff(coefs, vars, rhs);
	}

	private void commonConstraintStuff(double[] coefs, Object[] vars, double rhs) {
		String constName = String.format("c%d",numConstraints);
		
		for (int i=0; i < vars.length; ++i) {
			Object var = vars[i];			
			int varIndex = getVarIndex(var);
			double coef = coefs[i];
			String varName = String.format("x%d", varIndex);
			colOut.printf("%-4s%-10s%-10s%f\n","",varName, constName, coef);
		}
			
		rhsOut.printf("%-4s%-10s%-10s%f\n","","rhs",constName, rhs);
	}

	/**
	 * Add the constraints sum_i coefs[i] * vars[i] >= rhs
	 * @param coefs
	 * @param vars
	 * @param rhs
	 */
	public void addGreaterThanConstraint(double[] coefs, Object[] vars, double rhs) {
		assert coefs.length == vars.length ;
		numConstraints++;
		String constName = String.format("c%d",numConstraints);
		rowOut.printf("%-1s%-3s%s\n","","G",constName);
		commonConstraintStuff(coefs, vars, rhs);
	}

	/**
	 * Add the constraints sum_i coefs[i] * vars[i] = rhs
	 * @param coefs
	 * @param vars
	 * @param rhs
	 */
	public void addEqualityConstraint(double[] coefs, Object[] vars, double rhs) {
		numConstraints++;
		String constName = String.format("c%d",numConstraints);		
		rowOut.printf("%-1s%-3s%s\n","","E",constName);		
		commonConstraintStuff(coefs, vars, rhs);
	}

	public void addLinearObjectiveTerm(Object var, double coef) {
		varCoefs.incrementCount(var, coef);
	}

	private void copy(String file, PrintWriter probOut) {
		List<String> lines = new ArrayList<String>();
		try {
			BufferedReader br = new BufferedReader(new FileReader(file));
			try {
				while (true) {
					String line = br.readLine();
					if (line == null) {
						break;
					}
					//out.println(line);
					lines.add(line);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Collections.sort(lines, new Comparator<String>() {

			public int compare(String s1, String s2) {
				boolean isSpace1 = Character.isSpaceChar(s1.charAt(0));
				boolean isSpace2 = Character.isSpaceChar(s2.charAt(0));
				
				if (isSpace1 && !isSpace2) {
					return 1;
				}
				if (isSpace2 && !isSpace1) {
					return -1;
				}
				return s1.compareTo(s2);
			
			}
			
		});
		for (String line: lines) {
			probOut.println(line);
		}
	}
	
	private Counter readSolution(BufferedReader reader) {
		Counter solution = new Counter();
		try {
			boolean startVars = false;
			while (true) {
				String line = reader.readLine();
				if (line == null) {
					break;
				}
				if (line.startsWith("VARIABLES")) {
					startVars = true;
					reader.readLine();
					continue;
				}
				if (startVars) {
					String[] fields = line.split("\\s+");
					int varIndex = Integer.parseInt( fields[1].substring(1) );
					Object var = varIndexer.get(varIndex);
					double val = Double.parseDouble( fields[3] );
					solution.setCount(var, val);
				}
			} 
		} catch(Exception e) {
			e.printStackTrace();
		}
		return solution;
	}
	
	public Counter solve() {

	
		addObjective();
		flushFiles();

		try {
			String probFile = Execution.getFile("mosekProblem");
			PrintWriter probOut = IOUtils.openOutEasy(probFile);
			
			writeProblem(probOut);
			
			String solFile = Execution.getFile("mosekSolution");
			
			List<String> cmd = CollectionUtils.makeList(mosekPath, "-itro", solFile); 
			
			if (maxTime < Double.POSITIVE_INFINITY) {
				cmd.add("-d");
				cmd.add("MSK_DPAR_OPTIMIZER_MAX_TIME");
				cmd.add(""+maxTime);
			}
			
			cmd.add("-d");
			cmd.add("MSK_IPAR_INTPNT_MAX_ITERATIONS");
			cmd.add("" + maxIters);
			
			if (numProcessors > 1) {
				cmd.add("-d");
				cmd.add("MSK_IPAR_INTPNT_NUM_THREADS");
				cmd.add("" + numProcessors);
			}
			
//			cmd.add("-d");
//			cmd.add("MSK_DPAR_INTPNT_TOL_INFEAS");
//			cmd.add("" + 1.0e-2);
			
			cmd.add(probFile);
			
			
			ProcessBuilder processBuilder = new ProcessBuilder(cmd);
			processBuilder.redirectErrorStream(true);
			Process process = processBuilder.start();
			BufferedReader processReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			
			LogInfo.logs("Launching: " + cmd);
			LogInfo.logs("=== MOSEK Output ===");
			printOutput(processReader);
		
			BufferedReader solReader = new BufferedReader(new FileReader(solFile));
			return readSolution(solReader);
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(1);
		}
		
		throw new RuntimeException("Shouldn't be Here!");
	}

	private void printOutput(BufferedReader processReader) throws IOException {
		while (true) {
			String line = processReader.readLine();
			if (line==null) {
				break;
			}
			LogInfo.logs(line);
		}
	}
	
	

	private void writeProblem(PrintWriter probOut) {
		probOut.println("NAME PROBLEM");
		probOut.println("OBJSENSE");
		probOut.printf("%-4s%s\n", "", (minimize ? "MIN" : "MAX"));
		
		copy(rowFile, probOut);
		copy(colFile, probOut);
		copy(rhsFile, probOut);
		
		if (sawBound) {
			copy(boundFile, probOut);
		}
		
		if (sawQSection) {
			copy(qsecFile, probOut);
		}
		
		probOut.println("ENDATA");
		probOut.flush();
	}

	private void flushFiles() {
		rowOut.flush();
		colOut.flush();
		rhsOut.flush();
		qsecOut.flush();
		boundOut.flush();
	}

	private void addObjective() {
		for (Object var: varCoefs.keySet()) {
			double coef = varCoefs.getCount(var);
			int index = getVarIndex(var);
			String varName = String.format("x%d",index);
			colOut.printf("%-4s%-10s%-10s%f\n","", varName, "obj", coef);
		}
	}

	
	public static void main(String[] args) {
		MosekSolver lpSolver = new MosekSolver();
		Object[] vars = {"a","b"};
		double[] coefs = {1.0,1.0};
		double rhs = 1.0;
		// min a^2 + b^2
		// s.t a+b >= 1.0
		// Should Give: 
		lpSolver.addGreaterThanConstraint(coefs, vars, rhs);
		lpSolver.addQuadraticObjectiveTerm(1.0, "a", "a");
		lpSolver.addQuadraticObjectiveTerm(1.0, "b", "b");
		lpSolver.minimize();
		Counter sol = lpSolver.solve();
		System.out.println(sol);
	}

}
