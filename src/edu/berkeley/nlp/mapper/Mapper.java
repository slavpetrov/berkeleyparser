package edu.berkeley.nlp.mapper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Utility Class for parallelizing a collection of items which need to be
 * parallized. To use this you must extends MapWorker<Item> which will process
 * an instance of Item. When you call doMapping(Iterator<Item>,bufSize), we take
 * bufSize eleems out of the iterator and distribute the processing of those
 * items, then take bufSize more, and so on.
 * 
 * @author aria42
 * 
 * @param <Item>
 */
public class Mapper<Item> {

	private int numWorkers;
	private MapWorkerFactory<Item> factory;

	public Mapper(MapWorkerFactory<Item> factory) {
		this.factory = factory;
		this.numWorkers = Runtime.getRuntime().availableProcessors();
	}

	public Mapper(final Class c) {
		this(new MapWorkerFactory<Item>() {
			public MapWorker<Item> newMapWorker() {
				try {
					return (MapWorker<Item>) c.newInstance();
				} catch (Exception e) {
					e.printStackTrace();
				}
				return null;
			}
		});
	}

	public void setNumWorkers(int numWorkers) {
		this.numWorkers = numWorkers;
	}

	public List<MapWorker<Item>> doMapping(List<Item> items) {

		List<MapWorker<Item>> workers = new ArrayList<MapWorker<Item>>();
		for (int i = 0; i < numWorkers; ++i) {
			MapWorker<Item> worker = factory.newMapWorker();
			workers.add(worker);
		}
		doMapping(items, workers);
		return workers;
	}

	private void doMapping(List<Item> items, List<MapWorker<Item>> workers) {
		ExecutorService executor = Executors.newFixedThreadPool(workers.size());
		for (int i = 0; i < workers.size(); ++i) {
			int start = (int) ((i / (double) workers.size()) * items.size());
			int end = (int) (((i + 1) / (double) workers.size()) * items.size());
			List<Item> localItems = items.subList(start, end);
			MapWorker<Item> worker = workers.get(i);
			worker.setItems(localItems);
			executor.execute(worker);
		}
		execute(executor);
		for (MapWorker<Item> worker : workers) {
			worker.reduce();
		}
	}

	private void execute(ExecutorService executor) {
		executor.shutdown();
		try {
			executor.awaitTermination(10000, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public List<MapWorker<Item>> doMapping(Iterator<Item> itemIt) {
		return doMapping(itemIt, 10000);
	}

	public List<MapWorker<Item>> doMapping(Iterator<Item> itemIt, int bufSize) {
		List<MapWorker<Item>> workers = new ArrayList<MapWorker<Item>>();
		int numProcessed = 0;
		for (int i = 0; i < numWorkers; ++i) {
			MapWorker<Item> worker = factory.newMapWorker();
			workers.add(worker);
		}
		while (itemIt.hasNext()) {
			List<Item> items = new ArrayList<Item>();
			for (int i = 0; i < bufSize; ++i) {
				if (!itemIt.hasNext())
					break;
				items.add(itemIt.next());
			}
			doMapping(items, workers);
			System.gc();
			numProcessed += bufSize;
			// System.out.println("[Mapper] done processing " + numProcessed);
		}
		return workers;
	}

	public Object getNumWorkers() {
		return numWorkers;
	}

	public static void main(String[] args) {
		class MyMapper extends MapWorker<Integer> {
			public void map(Integer item) {
				System.out.println("\tProcessing " + item);
			}
		}
		MapWorkerFactory<Integer> factory = new MapWorkerFactory<Integer>() {
			public MapWorker<Integer> newMapWorker() {
				return new MyMapper();
			}
		};
		Mapper<Integer> mapper = new Mapper<Integer>(factory);
		List<Integer> items = new ArrayList<Integer>();
		for (int i = 0; i < 10000; ++i) {
			items.add(i);
		}
		mapper.doMapping(items.iterator(), 10);
	}
}
