/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import edu.berkeley.nlp.PCFGLA.smoothing.Smoother;
import edu.berkeley.nlp.syntax.Trees.PennTreeRenderer;
import edu.berkeley.nlp.util.PriorityQueue;

/**
 * @author petrov
 * 
 */
public class HierarchicalAdaptiveGrammar extends HierarchicalGrammar {

	private static final long serialVersionUID = 1L;

	public HierarchicalAdaptiveGrammar(short[] nSubStates,
			boolean findClosedPaths, Smoother smoother, Grammar oldGrammar,
			double thresh) {
		super(nSubStates, findClosedPaths, smoother, oldGrammar, thresh);
	}

	public HierarchicalAdaptiveGrammar(Grammar gr) {
		super(gr.numSubStates, gr.findClosedPaths, gr.smoother, gr,
				gr.threshold);

		for (BinaryRule oldRule : gr.binaryRuleMap.keySet()) {
			HierarchicalAdaptiveBinaryRule newRule = new HierarchicalAdaptiveBinaryRule(
					oldRule);
			addBinary(newRule);
		}
		for (UnaryRule oldRule : gr.unaryRuleMap.keySet()) {
			HierarchicalAdaptiveUnaryRule newRule = new HierarchicalAdaptiveUnaryRule(
					oldRule);
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

	public HierarchicalAdaptiveGrammar newInstance(short[] newNumSubStates) {
		return new HierarchicalAdaptiveGrammar(newNumSubStates,
				this.findClosedPaths, this.smoother, this, this.threshold);
	}

	void printLevelCounts() {
		int nBinaryParams = 0, nUnaryParams = 0, nBinaryFringeParams = 0, nUnaryFringeParams = 0;
		PriorityQueue<HierarchicalAdaptiveBinaryRule> pQb = new PriorityQueue<HierarchicalAdaptiveBinaryRule>();
		PriorityQueue<HierarchicalAdaptiveUnaryRule> pQu = new PriorityQueue<HierarchicalAdaptiveUnaryRule>();
		for (int state = 0; state < numStates; state++) {
			int[] counts = new int[8];
			BinaryRule[] parentRules = this.splitRulesWithP(state);
			if (parentRules.length == 0)
				continue;
			double totalParamState = 0, totalRulesState = 0;
			for (int i = 0; i < parentRules.length; i++) {
				HierarchicalAdaptiveBinaryRule r = (HierarchicalAdaptiveBinaryRule) parentRules[i];
				// PennTreeRenderer.render(r.hierarchy);
				counts[r.hierarchy.getDepth()]++;
				nBinaryParams += r.countNonZeroFeatures();
				int n = r.countNonZeroFringeFeatures();
				nBinaryFringeParams += n;
				pQb.add(r, n);
				totalParamState += n;
				totalRulesState++;
			}
			System.out.print(tagNumberer.object(state)
					+ ", binary rules per level: ");

			for (int i = 1; i < 8; i++) {
				System.out.print(counts[i] + " ");
			}
			System.out.print(" with \n " + tagNumberer.object(state) + "\t"
					+ totalParamState / totalRulesState
					+ "\n parameters on average :'\n");
		}
		// for (int i=0; i<6; i++){
		// System.out.println(counts[i]+" binary rules are split upto level "+i);
		// counts[i] = 0;
		// }
		for (int state = 0; state < numStates; state++) {
			int[] counts = new int[8];
			UnaryRule[] unaries = this.getClosedSumUnaryRulesByParent(state);
			// this.getClosedSumUnaryRulesByParent(state);//
			if (unaries.length == 0)
				continue;
			for (int r = 0; r < unaries.length; r++) {
				HierarchicalAdaptiveUnaryRule ur = (HierarchicalAdaptiveUnaryRule) unaries[r];
				// ur.toString();
				// PennTreeRenderer.render(ur.hierarchy);
				counts[ur.hierarchy.getDepth()]++;
				nUnaryParams += ur.countNonZeroFeatures();
				int n = ur.countNonZeroFringeFeatures();
				nUnaryFringeParams += n;
				// totalParamState+=n;
				// totalRulesState++;
				pQu.add(ur, n);
			}
			System.out.print(tagNumberer.object(state)
					+ ", unary rules per level: ");
			for (int i = 1; i < 8; i++) {
				System.out.print(counts[i] + " ");
			}
			System.out.print("\n");
		}
		System.out.println("There are " + nBinaryParams
				+ " binary features, of which " + nBinaryFringeParams
				+ " are on the fringe.");
		System.out.println("There are " + nUnaryParams
				+ " unary features, of which " + nUnaryFringeParams
				+ " are on the fringe.");
		System.out.println("There are " + (nBinaryParams + nUnaryParams)
				+ " total features, of which "
				+ (nBinaryFringeParams + nUnaryFringeParams)
				+ " are on the fringe.");
		while (pQb.hasNext()) {
			HierarchicalAdaptiveBinaryRule r = pQb.next();
			System.out.println(r.toStringShort() + "\t"
					+ r.countNonZeroFringeFeatures());
		}
		while (pQu.hasNext()) {
			HierarchicalAdaptiveUnaryRule r = pQu.next();
			System.out.println(r.toStringShort() + "\t"
					+ r.countNonZeroFringeFeatures());
		}
		// for (int i=0; i<6; i++){
		// System.out.println(counts[i]+" unary rules are split upto level "+i);
		// }

	}

}
