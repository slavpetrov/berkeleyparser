/**
 * 
 */
package edu.berkeley.nlp.speech.decoder;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import edu.berkeley.nlp.io.PennTreebankReader;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees;
import edu.berkeley.nlp.syntax.Trees.TreeTransformer;
import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.PriorityQueue;

/**
 * @author aria42
 *
 */
public class UnigramLanguageModel implements LanguageModel {
	
	private Counter<String> wordCounts ;
		
	public UnigramLanguageModel(Counter<String> wordCounts) {
		this.wordCounts = new Counter<String>(wordCounts);
	}
	
	public void prune(int N) {
		PriorityQueue<String> wordPQ = wordCounts.asPriorityQueue();
		wordCounts = new Counter<String>();
		for (int i=0; i < N && wordPQ.hasNext(); ++i) {
			double count = wordPQ.getPriority();
			String word = wordPQ.next();			
			wordCounts.setCount(word, count);
		}
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.speech.decoder.LanguageModel#getScore(java.lang.String, java.util.List)
	 */
	public double getScore(String word, List<String> history) {
		double count = wordCounts.getCount(word);
		return count / wordCounts.totalCount();
	}
	
	public static UnigramLanguageModel getTreebankUnigramModel(String treebankPath) {		
		Collection<Tree<String>> trees = PennTreebankReader.readTrees(treebankPath, 0, Integer.MAX_VALUE, Charset.defaultCharset());
		TreeTransformer<String> transformer = new Trees.StandardTreeNormalizer();
		Counter<String> wordCounts = new Counter<String>();
		for (Tree<String> tree: trees) {
			tree = transformer.transformTree(tree);
			for (String word: tree.getYield()) {
				word = word.toLowerCase();
				wordCounts.incrementCount(word, 1.0);
			}
		}
		return new UnigramLanguageModel(wordCounts);
	}

	/* (non-Javadoc)
	 * @see edu.berkeley.nlp.speech.decoder.LanguageModel#getSupport()
	 */
	public Set<String> getSupport() {
		return wordCounts.keySet();
	}

}
