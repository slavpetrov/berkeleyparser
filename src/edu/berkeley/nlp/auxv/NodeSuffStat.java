/**
 * 
 */
package edu.berkeley.nlp.auxv;

import java.util.ArrayList;
import java.util.List;

import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;

/**
 * Keeps track of expected counts for the node suff. stat. of a given tree
 * Used by AuxVarInsideOutside to compute marginal likelihood
 * 
 * @author Alexandre Bouchard
 * 
 */
public class NodeSuffStat 
{
	private final Tree<StateSet> tree;
	private final Tree<Stats> stats;
	
	public NodeSuffStat(Tree<StateSet> tree)
	{
		this.tree = tree.shallowClone();
		this.stats = initStats(tree);
	}
	public Tree<StateSet> getTree() { return tree.shallowClone(); }
  public double posteriorDerivationLogPr()
  {
  	double sumLogPr = 0.0;
  	for (Tree<Stats> stat : stats.getPreOrderTraversal())
  		if (!stat.isPreTerminal() && !stat.isLeaf())
  			sumLogPr += Math.log(stat.getLabel().numerator/stat.getLabel().denominator);
  	return sumLogPr;
  }
	public void update(InsideOutside io) { update(io, tree, stats); }
	private Tree<Stats> initStats(Tree<StateSet> treeOfInterest)
	{
		List<Tree<Stats>> children = new ArrayList<Tree<Stats>>();
		for (Tree<StateSet> child : treeOfInterest.getChildren())
			children.add(initStats(child));
		return new Tree<Stats>(new Stats(), children);
	}
	private void update(InsideOutside io, Tree<StateSet> treeOfInterest, Tree<Stats> stats)
	{
		int nChildren = treeOfInterest.getChildren().size();
		assert nChildren == stats.getChildren().size();
		if (treeOfInterest.isPreTerminal() || treeOfInterest.isLeaf()) return;
		// denominator
		StateSet parent = treeOfInterest.getLabel();
		double cPr = io.stateSetPosterior(parent);
		if (!isApproxPr(cPr))
			throw new RuntimeException("Should be pr: " + cPr);
		stats.getLabel().denominator += cPr;
		// numerator
		StateSet left = treeOfInterest.getChildren().get(0).getLabel();
		if (nChildren == 1)
		{
			final double pr = io.stateSetPosterior(parent, left);
			if (!isApproxPr(pr)) 
				throw new RuntimeException();
			stats.getLabel().numerator += pr;
		}
		else if (nChildren == 2)
		{
			StateSet right = treeOfInterest.getChildren().get(1).getLabel();
			final double pr = io.stateSetPosterior(parent, left, right);
			if (!isApproxPr(pr)) throw new RuntimeException();
			stats.getLabel().numerator += pr;
		}
		else throw new RuntimeException();
		// recursion
		for (int i = 0; i < nChildren; i++)
			update(io, treeOfInterest.getChildren().get(i), stats.getChildren().get(i)); 
	}
	private class Stats { private double numerator = 0.0, denominator = 0.0; }
	
	public static final double SLACK = 0.0000001;
	private boolean isApproxPr(double d)
	{
		return 0 <= (d + SLACK)  && (d - SLACK) <= 1.0;
	}
}
