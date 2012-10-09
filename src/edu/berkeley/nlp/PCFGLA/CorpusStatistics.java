/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.*;
import edu.berkeley.nlp.util.PriorityQueue;

/**
 * CorpusStatistics calculates symbol counts for a corpus.
 * 
 * @author leon
 * 
 */
public class CorpusStatistics {
	// i dont know how to initialize shorts...
	short zero = 0, one = 1;

	int[] counts;
	Collection<Tree<StateSet>> trees;
	Counter<UnaryRule> unaryRuleCounter;
	Counter<BinaryRule> binaryRuleCounter;

	int[] contexts;
	CounterMap<Integer, String> posCounter;

	/**
	 * Count statistics for a collection of StateSet trees.
	 */
	public CorpusStatistics(Numberer tagNumberer,
			Collection<Tree<StateSet>> trees) {
		counts = new int[tagNumberer.objects().size()];
		this.trees = trees;
		unaryRuleCounter = new Counter<UnaryRule>();
		binaryRuleCounter = new Counter<BinaryRule>();
		contexts = new int[tagNumberer.objects().size()];
		posCounter = new CounterMap<Integer, String>();
	}

	public void countSymbols() {
		for (Tree<StateSet> tree : trees) {
			addCount(tree);
		}
	}

	private void addCount(Tree<StateSet> tree) {
		counts[tree.getLabel().getState()] += 1.0;
		if (!tree.isPreTerminal()) {
			for (Tree<StateSet> child : tree.getChildren()) {
				addCount(child);
			}
		}
	}

	/*
	 * Counts how many different 'things' (non-terminals or terminals for the
	 * POS) appear under a given nonterminal symbol. Currently POS and other
	 * nonterminals are handled the same way. We might to change that.
	 */

	public void countRuleParents() {
		for (Tree<StateSet> tree : trees) {
			addParent(tree);
		}
		for (BinaryRule br : binaryRuleCounter.keySet()) {
			contexts[br.parentState]++;
			contexts[br.leftChildState]++;
			contexts[br.rightChildState]++;
		}
		for (UnaryRule ur : unaryRuleCounter.keySet()) {
			contexts[ur.parentState]++;
			contexts[ur.childState]++;
		}
		for (int i = 0; i < contexts.length; i++) {
			Counter<String> tempC = posCounter.getCounter(i);
			contexts[i] += tempC.size();

		}
	}

	public int[] getContextCounts() {
		return contexts;
	}

	private void addParent(Tree<StateSet> tree) {
		short parentState = tree.getLabel().getState();
		counts[parentState] += 1.0;
		if (!tree.isPreTerminal()) {
			if (tree.getChildren().size() == 1) {
				UnaryRule r = new UnaryRule(parentState, tree.getChildren()
						.get(0).getLabel().getState(), new double[1][1]);
				unaryRuleCounter.incrementCount(r, 1.0);
			} else {
				BinaryRule r = new BinaryRule(parentState, tree.getChildren()
						.get(0).getLabel().getState(), tree.getChildren()
						.get(1).getLabel().getState(), new double[1][1][1]);
				binaryRuleCounter.incrementCount(r, 1.0);
			}
			for (Tree<StateSet> child : tree.getChildren()) {
				addParent(child);
			}
		} else {
			posCounter.incrementCount((int) parentState, tree.getChildren()
					.get(0).getLabel().getWord(), 1.0);
		}
	}

	/**
	 * Get the number of times each state appeared.
	 * 
	 * @return
	 */
	public int[] getSymbolCounts() {
		countSymbols();
		return counts;
	}

	public void printStateCountArray(Numberer tagNumberer, int[] array) {
		PriorityQueue<String> pq = new PriorityQueue<String>(array.length);
		for (int i = 0; i < array.length; i++) {
			pq.add((String) tagNumberer.object(i), array[i]);
			// System.out.println(i+". "+(String)tagNumberer.object(i)+"\t "+symbolCounter.getCount(i,0));
		}
		int i = 0;
		while (pq.hasNext()) {
			i++;
			int p = (int) pq.getPriority();
			System.out.println(i + ". " + pq.next() + "\t " + p);
		}
	}

}
