/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.PriorityQueue;

/**
 * @author petrov
 * 
 */
public class MultiThreadedParserWrapper {
	int nThreads;
	boolean allBusy;
	PriorityQueue<List<Tree<String>>> queue;
	int lastReturned, lastSubmitted;
	ConstrainedArrayParser[] parsers;
	ExecutorService pool;
	Future[] submits;

	public MultiThreadedParserWrapper(ConstrainedArrayParser parser, int threads) {
		nThreads = threads;
		pool = Executors.newFixedThreadPool(nThreads);
		submits = new Future[nThreads];
		parsers = new ConstrainedArrayParser[nThreads];
		queue = new PriorityQueue<List<Tree<String>>>();

		parsers[0] = parser;
		parsers[0].setID(0, queue);
		for (int i = 0; i < nThreads; i++) {
			parsers[i] = parser.newInstance();
			parsers[i].setID(i, queue);
		}
		lastSubmitted = 0;
		lastReturned = -1;
	}

	public boolean isDone() {

		return (lastSubmitted - 1 == lastReturned);
	}

	public boolean hasNext() {
		// synchronized(queue) {
		if (!queue.hasNext())
			return false;
		double next = -queue.getPriority();
		return next == lastReturned + 1;
		// }
	}

	public List<Tree<String>> getNext() {
		if (!hasNext())
			return null;
		lastReturned++;
		synchronized (queue) {
			List<Tree<String>> result = queue.next();
			queue.notifyAll();
			return result;
		}
	}

	public void parseThisSentence(List<String> sentence) {
		// go through threads and submit this sentence to the next available
		// thread, after getting the threads last result
		synchronized (queue) {
			while (true) {
				for (int i = 0; i < nThreads; i++) {
					if (submits[i] == null || submits[i].isDone()) {
						parsers[i].setNextSentence(sentence, lastSubmitted++);
						// System.out.println(queue.size());
						// System.out.println(lastSubmitted);
						submits[i] = pool.submit(parsers[i]);
						return;
					}
				}
				try {
					queue.wait();
				} catch (InterruptedException ignored) {
				}
			}

		}
	}

}
