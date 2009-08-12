package edu.berkeley.nlp.HDPPCFG;

import edu.berkeley.nlp.syntax.Tree;
import java.util.*;

public interface ConstrainedParser {
	public List<Tree<String>> getNBestConstrainedParses(List<String> sentence, List<Integer>[][] constraints, double[] treeLLs, Tree<String>[] sampledTrees);
	public Tree<String> getBestConstrainedParse(List<String> sentence, List<Integer>[][] constraints);
	public void printStateAndRuleTallies();
	public void printUnaryStats();
	public void setNoConstraints(boolean noConstraints);
	public Tree<String>[] getSampledTrees(List<String> sentence, List<Integer>[][] pStates, int n);
	public double getLogLikelihood();
}
