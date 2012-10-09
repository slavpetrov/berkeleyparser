/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.io.IOException;
import java.io.Writer;

import edu.berkeley.nlp.PCFGLA.smoothing.Smoother;

/**
 * @author petrov
 * 
 */
public class HierarchicalGrammar extends Grammar {
	/**
	 * @param nSubStates
	 * @param findClosedPaths
	 * @param smoother
	 * @param oldGrammar
	 * @param thresh
	 */
	public HierarchicalGrammar(short[] nSubStates, boolean findClosedPaths,
			Smoother smoother, Grammar oldGrammar, double thresh) {
		super(nSubStates, findClosedPaths, smoother, oldGrammar, thresh);
	}

	private static final long serialVersionUID = 1L;

	public HierarchicalGrammar(Grammar gr) {
		super(gr.numSubStates, gr.findClosedPaths, gr.smoother, gr,
				gr.threshold);

		for (BinaryRule oldRule : gr.binaryRuleMap.keySet()) {
			HierarchicalBinaryRule newRule = new HierarchicalBinaryRule(oldRule);
			addBinary(newRule);
		}
		for (UnaryRule oldRule : gr.unaryRuleMap.keySet()) {
			HierarchicalUnaryRule newRule = new HierarchicalUnaryRule(oldRule);
			addUnary(newRule);
		}
		if (true) {
			closedSumRulesWithParent = closedViterbiRulesWithParent = unaryRulesWithParent;
			closedSumRulesWithChild = closedViterbiRulesWithChild = unaryRulesWithC;
		} else
			computePairsOfUnaries();
		makeCRArrays();
		isGrammarTag = gr.isGrammarTag;
	}

	@Override
	public void splitRules() {
		explicitlyComputeScores(finalLevel);
		super.splitRules();
	}

	public void explicitlyComputeScores(int finalLevel) {
		for (BinaryRule oldRule : binaryRuleMap.keySet()) {
			HierarchicalBinaryRule newRule = (HierarchicalBinaryRule) oldRule;
			newRule.explicitlyComputeScores(finalLevel, numSubStates);
		}
		for (UnaryRule oldRule : unaryRuleMap.keySet()) {
			HierarchicalUnaryRule newRule = (HierarchicalUnaryRule) oldRule;
			newRule.explicitlyComputeScores(finalLevel, numSubStates);
		}
	}

	@Override
	public HierarchicalGrammar splitAllStates(double randomness, int[] counts,
			boolean moreSubstatesThanCounts, int mode) {
		short[] newNumSubStates = new short[numSubStates.length];
		newNumSubStates[0] = 1;
		for (short i = 1; i < numSubStates.length; i++) {
			// don't split a state into more substates than times it was
			// actaully seen
			if (!moreSubstatesThanCounts && numSubStates[i] >= counts[i]) {
				newNumSubStates[i] = numSubStates[i];
			} else {
				newNumSubStates[i] = (short) (numSubStates[i] * 2);
			}
		}

		HierarchicalGrammar newGrammar = newInstance(newNumSubStates);// HierarchicalGrammar(newNumSubStates,this.findClosedPaths,this.smoother,this,this.threshold);

		for (BinaryRule oldRule : binaryRuleMap.keySet()) {
			HierarchicalBinaryRule newRule = (HierarchicalBinaryRule) oldRule;
			newGrammar.addBinary(newRule.splitRule(numSubStates,
					newGrammar.numSubStates, GrammarTrainer.RANDOM, randomness,
					true, mode));
		}
		for (UnaryRule oldRule : unaryRuleMap.keySet()) {
			HierarchicalUnaryRule newRule = (HierarchicalUnaryRule) oldRule;
			newGrammar.addUnary(newRule.splitRule(numSubStates,
					newGrammar.numSubStates, GrammarTrainer.RANDOM, randomness,
					true, mode));
		}
		if (true) {
			newGrammar.closedSumRulesWithParent = newGrammar.closedViterbiRulesWithParent = newGrammar.unaryRulesWithParent;
			newGrammar.closedSumRulesWithChild = newGrammar.closedViterbiRulesWithChild = newGrammar.unaryRulesWithC;
		} else
			newGrammar.computePairsOfUnaries();
		newGrammar.makeCRArrays();
		newGrammar.isGrammarTag = this.isGrammarTag;

		return newGrammar;
	}

	public HierarchicalGrammar newInstance(short[] newNumSubStates) {
		return new HierarchicalGrammar(newNumSubStates, this.findClosedPaths,
				this.smoother, this, this.threshold);
	}

	public void mergeGrammar() {
		int nBinaryMerged = 0, nUnaryMerged = 0;
		for (BinaryRule oldRule : binaryRuleMap.keySet()) {
			HierarchicalBinaryRule newRule = (HierarchicalBinaryRule) oldRule;
			nBinaryMerged += newRule.mergeRule();
		}
		for (UnaryRule oldRule : unaryRuleMap.keySet()) {
			HierarchicalUnaryRule newRule = (HierarchicalUnaryRule) oldRule;
			nUnaryMerged += newRule.mergeRule();
		}
		System.out.println("Removed " + nBinaryMerged + " binary and "
				+ nUnaryMerged + " unary parameters.");
	}

	@Override
	public HierarchicalGrammar copyGrammar(boolean noUnaryChains) {
		short[] newNumSubStates = numSubStates.clone();

		HierarchicalGrammar grammar = newInstance(newNumSubStates);
		for (BinaryRule oldRule : binaryRuleMap.keySet()) {
			// HierarchicalBinaryRule newRule = new BinaryRule(oldRule);
			// grammar.addBinary(newRule);
			grammar.addBinary(oldRule);
		}
		for (UnaryRule oldRule : unaryRuleMap.keySet()) {
			// UnaryRule newRule = new UnaryRule(oldRule);
			// grammar.addUnary(newRule);
			grammar.addUnary(oldRule);
		}
		if (noUnaryChains) {
			closedSumRulesWithParent = closedViterbiRulesWithParent = unaryRulesWithParent;
			closedSumRulesWithChild = closedViterbiRulesWithChild = unaryRulesWithC;

		} else
			grammar.computePairsOfUnaries();
		grammar.makeCRArrays();
		grammar.isGrammarTag = this.isGrammarTag;
		/*
		 * grammar.ruleIndexer = ruleIndexer; grammar.startIndex = startIndex;
		 * grammar.nEntries = nEntries; grammar.toBeIgnored = toBeIgnored;
		 */
		return grammar;
	}

	@Override
	public String toString() {
		printLevelCounts();
		return super.toString();
	}

	void printLevelCounts() {
		int nBinaryParams = 0, nUnaryParams = 0, nBinaryFringeParams = 0, nUnaryFringeParams = 0;
		for (int state = 0; state < numStates; state++) {
			int[] counts = new int[6];
			BinaryRule[] parentRules = this.splitRulesWithP(state);
			if (parentRules.length == 0)
				continue;
			for (int i = 0; i < parentRules.length; i++) {
				HierarchicalBinaryRule r = (HierarchicalBinaryRule) parentRules[i];
				counts[r.lastLevel]++;
				nBinaryParams += r.countNonZeroFeatures();
				// nBinaryFringeParams += r.nParam;
			}
			System.out.print(tagNumberer.object(state)
					+ ", binary rules per level: ");
			for (int i = 1; i < 6; i++) {
				System.out.print(counts[i] + " ");
			}
			System.out.print("\n");
		}
		// for (int i=0; i<6; i++){
		// System.out.println(counts[i]+" binary rules are split upto level "+i);
		// counts[i] = 0;
		// }
		for (int state = 0; state < numStates; state++) {
			int[] counts = new int[6];
			UnaryRule[] unaries = this.getClosedSumUnaryRulesByParent(state);
			// this.getClosedSumUnaryRulesByParent(state);//
			if (unaries.length == 0)
				continue;
			for (int r = 0; r < unaries.length; r++) {
				HierarchicalUnaryRule ur = (HierarchicalUnaryRule) unaries[r];
				counts[ur.lastLevel]++;
				nUnaryParams += ur.countNonZeroFeatures();
				// nUnaryFringeParams += ur.nParam;

			}
			System.out.print(tagNumberer.object(state)
					+ ", unary rules per level: ");
			for (int i = 1; i < 6; i++) {
				System.out.print(counts[i] + " ");
			}
			System.out.print("\n");
		}
		System.out.println("There are " + nBinaryParams + " binary features");// ,
																				// of
																				// which
																				// "+ nBinaryFringeParams+"
																				// are
																				// on
																				// the
																				// fringe.");
		System.out.println("There are " + nUnaryParams + " unary features");// ,
																			// of
																			// which
																			// "+ nUnaryFringeParams+"
																			// are
																			// on
																			// the
																			// fringe.");
	}

	@Override
	public void writeData(Writer w) throws IOException {
		printLevelCounts();
		super.writeData(w);
	}

}
