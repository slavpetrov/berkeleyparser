/**
 * 
 */
package edu.berkeley.nlp.bitext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import edu.berkeley.nlp.bitext.BitextRule.Type;
import edu.berkeley.nlp.bitext.GrammarStateFactory.GrammarState;
import fig.basic.Pair;

/**
 * @author Alexandre Bouchard
 * 
 * Generates from a bitext grammar
 * assume a grammar with only rules of the form
 * <X, Y> -> <term1, term2>
 * <X, Y> -> <X1 X2, Y1 Y2>
 * <X, Y> -> <X1 X2, Y2 Y1>
 * <X, Y> -> <X1 term, Y1>
 * <X, Y> -> <X1, Y1 term>
 *
 */
public class GenerateFromBitextGrammar {
	private int maxNSteps = 10;
	/**
	 * Try to generate a bi-sentence in less than maxNSteps
	 * If more steps would have been needed, stop and return null
	 */
	public Pair<List<String>, List<String>> generate() {
		GenerationState state = initialState();
		for (int i = 0; i < maxNSteps && state.hasNext(); i++)
			state = state.next();
		if (state.hasNext()) return null;
		return convert(state);
	}
	private Pair<List<String>, List<String>> convert(GenerationState state) {
		final List<String> s1 = new ArrayList<String>(), s2 = new ArrayList<String>();
		for (Item i : state.es) s1.add((String) i.contents);
		for (Item i : state.fs) s2.add((String) i.contents);
		return new Pair<List<String>, List<String>>(s1, s2);
	}
	private Pair<List<Item>, List<Item>> sample(GrammarState s1, GrammarState s2) {
		BitextRule sample = sampleBitextRule(s1, s2);
		if (sample.getType() == Type.BINARY_BINARY) {
			Item e1 = new Item(((BinaryRule) sample.getLeftRule()).leftChild()),
			   	 e2 = new Item(((BinaryRule) sample.getLeftRule()).rightChild());
			Item f1 = new Item(((BinaryRule) sample.getRightRule()).leftChild()),
			 		 f2 = new Item(((BinaryRule) sample.getRightRule()).rightChild());
			if (sample.isInverted()) {
				e1.friend = f1; f1.friend = e1; e2.friend = f2; f2.friend = e2;
			} else {
				e1.friend = f2; f1.friend = e2; e2.friend = f1; f2.friend = e1;
			}
			return new Pair<List<Item>, List<Item>>(Arrays.asList(e1, e2), Arrays.asList(f1, f2));
		}
		else if (sample.getType() == Type.BINARY_UNARY) {
			// how to find which production on the binary side is linked to the unary production?
			// TODO: one of them should be a terminal
			return null;
		}
		else if (sample.getType() == Type.UNARY_BINARY) {
			// how to find which production on the binary side is linked to the unary production?
			// TODO: one of them should be a terminal
			return null;
		}
		else throw new RuntimeException();
	}
	private BitextRule sampleBitextRule(GrammarState s1, GrammarState s2) {
		// TODO
		return null;
	}
	private static GenerationState initialState() {
		// TODO: find the S symbol
		return null;
	}
	/**
	 * The generation state contains a list of items on both sides,
	 * a mix of terminals and symbols that have not been expanded yet
	 */
	private class GenerationState {
		private final List<Item> es, fs;
		private GenerationState(final List<Item> es, final List<Item> fs) {
			this.es = es; this.fs = fs;
		}
		private boolean hasNext() { return (findNonTerminal() != null); }
		/**
		 * Pick a pair of aligned nonterminal and create a new state where everything
		 * is the same except that the pair that was picked is expanded
		 */
		private GenerationState next() {
			Item i1 = findNonTerminal();
			Item i2 = i1.friend;
			Pair<List<Item>, List<Item>> sample = sample(i1.symbol(), i2.symbol());
			return new GenerationState(buildNextSent(es, i1, i2, sample),
																 buildNextSent(fs, i1, i2, sample));
		}
		private Item findNonTerminal() {
			for (Item ei : es) if (ei.isNonTerminal()) return ei;
			for (Item fi : fs) if (fi.isNonTerminal()) return fi;
			return null;
		}
	}
	private static class Item {
		private Item friend = null; // pointer to it's aligned symbol in the other sentence
		private final Object contents;
		private GrammarState symbol() { return (GrammarState) contents; }
		private Item(Object contents) { this.contents = contents; }
		private boolean isNonTerminal() { return contents instanceof GrammarState; }
	}
	private static List<Item> buildNextSent(List<Item> current, Item i1, Item i2, 
			Pair<List<Item>, List<Item>> step)
	{
		List<Item> nextSent = new ArrayList<Item>();
		for (Item item : current)
		{
			if (item != i1 && item != i2) nextSent.add(item);
			else if (item == i1) nextSent.addAll(step.getFirst());
			else if (item == i2) nextSent.addAll(step.getSecond());
			else throw new RuntimeException();
		}
		return nextSent;
	}
}
