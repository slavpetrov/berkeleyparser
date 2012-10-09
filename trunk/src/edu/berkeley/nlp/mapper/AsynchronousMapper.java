package edu.berkeley.nlp.mapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import edu.berkeley.nlp.util.functional.FunctionalUtils;

/**
 * User: aria42 Date: Feb 19, 2009
 */
public class AsynchronousMapper {
	public static <T> void doMapping(Collection<T> elems,
			List<? extends SimpleMapper<T>> mappers) {
		if (elems.isEmpty())
			return;
		final BlockingQueue<T> queue = new ArrayBlockingQueue<T>(elems.size(),
				true, elems);
		class Worker implements Runnable {
			SimpleMapper mapper;
			int numCompleted = 0;

			public Worker(SimpleMapper mapper) {
				this.mapper = mapper;
			}

			public void run() {
				while (!queue.isEmpty()) {
					try {
						T elem = queue.poll(1000, TimeUnit.MILLISECONDS);
						if (elem == null)
							break;
						mapper.map(elem);
						numCompleted++;
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
		ExecutorService es = Executors.newFixedThreadPool(mappers.size());
		List<Worker> workers = new ArrayList<Worker>();
		for (SimpleMapper<T> mapper : mappers) {
			Worker worker = new Worker(mapper);
			workers.add(worker);
			es.execute(worker);
		}
		es.shutdown();
		try {
			es.awaitTermination(100000, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		int totalCompleted = 0;
		for (Worker worker : workers) {
			totalCompleted += worker.numCompleted;
		}
		if (totalCompleted < elems.size()) {
			throw new RuntimeException("Completed only " + totalCompleted
					+ " out of " + elems.size() + " tasks!");
		}
		if (!queue.isEmpty()) {
			throw new RuntimeException();
		}
	}

	public static <T> void doMapping(Iterator<T> it, int batchSize,
			List<? extends SimpleMapper<T>> mappers) {
		while (it.hasNext()) {
			List<T> items = FunctionalUtils.take(it, batchSize);
			doMapping(items, mappers);
		}
	}
}
