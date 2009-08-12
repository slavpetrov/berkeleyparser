/**
 * 
 */
package edu.berkeley.nlp.auxv.bractrl;

import java.util.List;
import java.util.Random;

import edu.berkeley.nlp.PCFGLA.StateSetTreeList;
import edu.berkeley.nlp.auxv.BracketConstraints.BracketProposerView;
import edu.berkeley.nlp.discPCFG.Linearizer;
import fig.basic.Option;

/**
 * @author Alexandre Bouchard
 *
 */
public class BracketNumberControllers 
{
	// temporary hack
	// cause: 
//	public SamplingObjectiveFunction(Linearizer linearizer, 
//			StateSetTreeList trainStateSetTrees, double sigma, int regularize, 
//			String cons, int process, String grammarLocation, boolean b, boolean c, 
//			int maxLengthForExact, BracketNumberControllers braNumberControllers) {
//		super(linearizer, trainStateSetTrees, sigma, regularize, cons, process, 
//				grammarLocation, b, c);
//		this.maxLengthForExact = maxLengthForExact;
//		this.braNumberControllers = braNumberControllers;
//	}
	// the call of super should be after the field init.. crap
	public static final BracketNumberControllers instance = new BracketNumberControllers();
	
	@Option public Implementation implementation = Implementation.LINEAR;
	@Option public double proportion = 0.4;
	public static enum Implementation { SIMPLE, LINEAR }
	public BracketNumberController newBracketNumberController()
	{
		if (implementation == Implementation.SIMPLE)
			return new SimpleBracketNumberController(nBrackets, 0);
		else if (implementation == Implementation.LINEAR)
			return new LinearBracketNumber(proportion);
		else throw new RuntimeException();
	}
	@Option public int nBrackets = 2;
	public static class SimpleBracketNumberController 
		implements BracketNumberController
	{
	  private final int n, threshold;
	  public SimpleBracketNumberController(int n, int threshold)
	  {
	    this.n = n;
	    this.threshold = threshold;
	  }
	  public int initialNumberOfBrackets(Random rand, List<String> sentence)
	  {
	    return (sentence.size() > threshold ? n : 0);
	  }
	  public int numberOfBracketsToRemove(Random rand, 
	  		BracketProposerView currentConstraints)
	  {
	    return Math.max(0, currentConstraints.getNumberOfConstraints() - n); 
	  }
	}
	public static class LinearBracketNumber implements BracketNumberController
	{
		private final double proportion;
		public LinearBracketNumber(double prop) { this.proportion = prop; }
		public int nBra(int sentSize) 
		{ 
			return (int) Math.max(0, ((double) sentSize - 2.0) * proportion); 
		}
		public int initialNumberOfBrackets(Random rand, List<String> sentence) 
		{
			return nBra(sentence.size());
		}
		public int numberOfBracketsToRemove(Random rand, BracketProposerView currentConstraints) 
		{
			return Math.max(0, currentConstraints.getNumberOfConstraints() - 
					nBra(currentConstraints.getSentence().size()));
		}
	}
}
