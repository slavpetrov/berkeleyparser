/**
 * 
 */
package edu.berkeley.nlp.auxv.bractrl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import edu.berkeley.nlp.auxv.BracketConstraints.BracketProposerView;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import fig.basic.NumUtils;
import fig.basic.Pair;
import fig.prob.SampleUtils;

/**
 * @author Alexandre Bouchard
 *
 */
public class BracketsFromGoldTree implements BracketProposer {
	
	private final List<Pair<Integer, Integer>> allowedBrackets;
	
	public BracketsFromGoldTree(final List<Pair<Integer, Integer>> allowedBrackets) {
		this.allowedBrackets = allowedBrackets;
	}
	public BracketsFromGoldTree(Tree<StateSet> goldTree)
	{
		allowedBrackets = new ArrayList<Pair<Integer,Integer>>();
		for (Tree<StateSet> stateSet : goldTree.getPostOrderTraversal())
		{
			int from = stateSet.getLabel().from, to = stateSet.getLabel().to;
			allowedBrackets.add(new Pair<Integer, Integer>(from, to));
		}
	}

	public boolean allowsOverlappingBrackets() { return false; }

	/**
	 * Pick one of the allowed bracket left at random, with pr proportional to
	 * its efficiency score raised to a 1/temperature 
	 * 
	 * @param rand
	 * @param currentConstraints
	 * @return
	 */
	public Pair<Integer, Integer> next(Random rand,
			BracketProposerView<String> currentConstraints) 
	{
		// 1- check which constraints are left
		Set<Pair<Integer, Integer>> constraintsLeft = new HashSet<Pair<Integer,Integer>>();
		constraintsLeft.addAll(allowedBrackets);
		for (Pair<Integer, Integer> usedBra : currentConstraints.getConstraints())
		{
			if (!constraintsLeft.contains(usedBra)) throw new RuntimeException();
			constraintsLeft.remove(usedBra);
		}
		if (constraintsLeft.size() == 0) 
			throw new RuntimeException();  // CRASHES HERE
		// 2- assign scores proportional to the complexity gain. (only pess for now)
		double [] prs = new double[constraintsLeft.size()];
		List<Pair<Integer, Integer>> constraintsList = new ArrayList<Pair<Integer,Integer>>();
		constraintsList.addAll(constraintsLeft);
		double sentL = currentConstraints.getSentence().size();
		for (int i = 0; i < constraintsLeft.size(); i++)
		{
			double cConstrL = constraintsList.get(i).getSecond() - constraintsList.get(i).getFirst();
			prs[i] = 1 + sentL - Math.max(sentL, sentL - cConstrL);
		}
		NumUtils.normalize(prs);
		return constraintsList.get(SampleUtils.sampleMultinomial(rand, prs));
	}

}
