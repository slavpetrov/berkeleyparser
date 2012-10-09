package edu.berkeley.nlp.util;

import java.util.*;
import java.lang.ThreadLocal;

/**
 * 4/2/09: StopWatchSet should be re-entrant (can call begin("foo") twice) and
 * thread-safe.
 */
public class StopWatchSet {
	// For measuring time of certain types of events.
	// Shared across all threads.
	private static Map<String, StopWatch> stopWatches = new LinkedHashMap<String, StopWatch>();

	// A stack of stop-watches (one per thread)
	private static ThreadLocal<LinkedList<Pair<String, StopWatch>>> lastStopWatches = new ThreadLocal() {
		protected LinkedList<Pair<String, StopWatch>> initialValue() {
			return new LinkedList();
		}
	};

	public synchronized static StopWatch getWatch(String s) {
		return MapUtils.getMut(stopWatches, s, new StopWatch());
	}

	public static void begin(String s) {
		// Create a new stop watch for reentrance and thread safety
		lastStopWatches.get().addLast(new Pair(s, new StopWatch().start()));
	}

	public static void end() {
		Pair<String, StopWatch> pair = lastStopWatches.get().removeLast();
		pair.getSecond().stop();
		// Add it
		synchronized (stopWatches) {
			getWatch(pair.getFirst()).add(pair.getSecond());
		}
	}

	public synchronized static OrderedStringMap getStats() {
		OrderedStringMap map = new OrderedStringMap();
		for (String key : stopWatches.keySet()) {
			StopWatch watch = getWatch(key);
			map.put(key, watch + " ("
					+ new StopWatch(watch.n == 0 ? 0 : watch.ms / watch.n)
					+ " x " + watch.n + ")");
		}
		return map;
	}
}
