package edu.berkeley.nlp.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * PriorityQueue with explicit double priority values. Larger doubles are higher
 * priorities. BinaryHeap-backed.
 * 
 * @author Dan Klein
 * @author Christopher Manning For each entry, uses ~ 24 (entry) + 16?
 *         (Map.Entry) + 4 (List entry) = 44 bytes?
 */
public class GeneralPriorityQueue<E> implements PriorityQueueInterface<E>,
		Serializable {

	/**
	 * An <code>Entry</code> stores an object in the queue along with its
	 * current location (array position) and priority. uses ~ 8 (self) + 4 (key
	 * ptr) + 4 (index) + 8 (priority) = 24 bytes?
	 */
	public static final class Entry<E> implements Serializable {
		public E key;

		public int index;

		public double priority;

		@Override
		public String toString() {
			return key + " at " + index + " (" + priority + ")";
		}
	}

	public boolean hasNext() {
		return size() > 0;
	}

	public E next() {

		final E removeFirst = removeFirst();
		return removeFirst;
	}

	public void remove() {
		throw new UnsupportedOperationException();
	}

	/**
	 * <code>indexToEntry</code> maps linear array locations (not priorities) to
	 * heap entries.
	 */
	private List<Entry<E>> indexToEntry;

	/**
	 * <code>keyToEntry</code> maps heap objects to their heap entries.
	 */
	private Map<E, Entry<E>> keyToEntry;

	public GeneralPriorityQueue<E> deepCopy() {
		GeneralPriorityQueue<E> pq = new GeneralPriorityQueue<E>();
		for (Entry<E> entry : indexToEntry) {
			pq.setPriority(entry.key, entry.priority);
		}
		return pq;
	}

	private Entry<E> parent(Entry<E> entry) {
		int index = entry.index;
		return (index > 0 ? getEntry((index - 1) / 2) : null);
	}

	private Entry<E> leftChild(Entry<E> entry) {
		int leftIndex = entry.index * 2 + 1;
		return (leftIndex < size() ? getEntry(leftIndex) : null);
	}

	private Entry<E> rightChild(Entry<E> entry) {
		int index = entry.index;
		int rightIndex = index * 2 + 2;
		return (rightIndex < size() ? getEntry(rightIndex) : null);
	}

	private int compare(Entry<E> entryA, Entry<E> entryB) {
		return compare(entryA.priority, entryB.priority);
	}

	protected int compare(double a, double b) {
		double diff = a - b;
		if (diff > 0.0) {
			return 1;
		}
		if (diff < 0.0) {
			return -1;
		}
		return 0;
	}

	/**
	 * Structural swap of two entries.
	 * 
	 * @param entryA
	 * @param entryB
	 */
	private void swap(Entry<E> entryA, Entry<E> entryB) {
		int indexA = entryA.index;
		int indexB = entryB.index;
		entryA.index = indexB;
		entryB.index = indexA;
		indexToEntry.set(indexA, entryB);
		indexToEntry.set(indexB, entryA);
	}

	/**
	 * Remove the last element of the heap (last in the index array).
	 */
	private void removeLastEntry() {
		Entry entry = indexToEntry.remove(size() - 1);
		keyToEntry.remove(entry.key);
	}

	/**
	 * Get the entry by key (null if none).
	 */
	protected Entry<E> getEntry(Object key) {
		Entry<E> entry = keyToEntry.get(key);
		return entry;
	}

	/**
	 * Get entry by index, exception if none.
	 */
	private Entry<E> getEntry(int index) {
		Entry<E> entry = indexToEntry.get(index);
		return entry;
	}

	protected Entry<E> makeEntry(E key) {
		Entry<E> entry = new Entry<E>();
		entry.index = size();
		entry.key = key;
		entry.priority = Double.NEGATIVE_INFINITY;
		indexToEntry.add(entry);
		keyToEntry.put(key, entry);
		return entry;
	}

	/**
	 * iterative heapify up: move item o at index up until correctly placed
	 */
	protected void heapifyUp(Entry<E> entry) {
		while (true) {
			if (entry.index == 0) {
				break;
			}
			Entry<E> parentEntry = parent(entry);
			if (compare(entry, parentEntry) <= 0) {
				break;
			}
			swap(entry, parentEntry);
		}
	}

	/**
	 * On the assumption that leftChild(entry) and rightChild(entry) satisfy the
	 * heap property, make sure that the heap at entry satisfies this property
	 * by possibly percolating the element o downwards. I've replaced the
	 * obvious recursive formulation with an iterative one to gain (marginal)
	 * speed
	 */
	private void heapifyDown(Entry<E> entry) {
		Entry<E> currentEntry = entry;
		Entry<E> bestEntry = null;

		do {
			bestEntry = currentEntry;

			Entry<E> leftEntry = leftChild(currentEntry);
			if (leftEntry != null) {
				if (compare(bestEntry, leftEntry) < 0) {
					bestEntry = leftEntry;
				}
			}

			Entry<E> rightEntry = rightChild(currentEntry);
			if (rightEntry != null) {
				if (compare(bestEntry, rightEntry) < 0) {
					bestEntry = rightEntry;
				}
			}

			if (bestEntry != currentEntry) {
				// Swap min and current
				swap(bestEntry, currentEntry);
				// at start of next loop, we set currentIndex to largestIndex
				// this indexation now holds current, so it is unchanged
			}
		} while (bestEntry != currentEntry);
		// System.err.println("Done with heapify down");
		// verify();
	}

	private void heapify(Entry<E> entry) {
		heapifyUp(entry);
		heapifyDown(entry);
	}

	/**
	 * Finds the object with the highest priority, removes it, and returns it.
	 * 
	 * @return the object with highest priority
	 */
	public E removeFirst() {
		E first = getFirst();
		removeKey(first);
		return first;
	}

	/**
	 * Finds the object with the highest priority and returns it, without
	 * modifying the queue.
	 * 
	 * @return the object with minimum key
	 */
	public E getFirst() {
		if (isEmpty())
			throw new NoSuchElementException();
		return getEntry(0).key;
	}

	/**
	 * Searches for the object in the queue and returns it. May be useful if you
	 * can create a new object that is .equals() to an object in the queue but
	 * is not actually identical, or if you want to modify an object that is in
	 * the queue.
	 * 
	 * @return null if the object is not in the queue, otherwise returns the
	 *         object.
	 */
	public E getObject(E key) {
		if (!containsKey(key))
			return null;
		Entry<E> e = getEntry(key);
		return e.key;
	}

	/**
	 * Get the priority of a key -- if the key is not in the queue,
	 * Double.NEGATIVE_INFINITY is returned.
	 * 
	 * @param key
	 * @return
	 */
	public double getPriority(E key) {
		Entry entry = getEntry(key);
		if (entry == null) {
			return Double.NEGATIVE_INFINITY;
		}
		return entry.priority;
	}

	public double removeKey(E key) {
		Entry<E> entry = getEntry(key);
		if (entry == null) {
			return Double.NEGATIVE_INFINITY;
		}
		removeEntry(entry);
		return entry.priority;
	}

	private void removeEntry(Entry<E> entry) {
		Entry<E> lastEntry = getLastEntry();
		if (entry != lastEntry) {
			swap(entry, lastEntry);
			removeLastEntry();
			heapify(lastEntry);
		} else {
			removeLastEntry();
		}
		return;
	}

	private Entry<E> getLastEntry() {
		return getEntry(size() - 1);
	}

	/**
	 * Promotes a key in the queue, adding it if it wasn't there already. If the
	 * specified priority is worse than the current priority, nothing happens.
	 * Faster than add if you don't care about whether the key is new.
	 * 
	 * @param key
	 *            an <code>Object</code> value
	 * @return whether the priority actually improved.
	 */
	public boolean relaxPriority(E key, double priority) {
		Entry<E> entry = getEntry(key);
		if (entry == null) {
			entry = makeEntry(key);
		}
		if (compare(priority, entry.priority) <= 0) {
			return false;
		}
		entry.priority = priority;
		heapifyUp(entry);
		return true;
	}

	/**
	 * Demotes a key in the queue, adding it if it wasn't there already. If the
	 * specified priority is better than the current priority, nothing happens.
	 * If you decrease the priority on a non-present key, it will get added, but
	 * at its old implicit priority of Double.NEGATIVE_INFINITY.
	 * 
	 * @param key
	 *            an <code>Object</code> value
	 * @return whether the priority actually improved.
	 */
	public boolean decreasePriority(E key, double priority) {
		Entry<E> entry = getEntry(key);
		if (entry == null) {
			entry = makeEntry(key);
		}
		if (compare(priority, entry.priority) >= 0) {
			return false;
		}
		entry.priority = priority;
		heapifyDown(entry);
		return true;
	}

	/**
	 * Changes a priority, either up or down, adding the key it if it wasn't
	 * there already.
	 * 
	 * @param key
	 *            an <code>Object</code> value
	 */
	public void setPriority(E key, double priority) {
		Entry<E> entry = getEntry(key);

		if (entry == null) {
			entry = makeEntry(key);
		} else {
			if (entry.key != key) {

				entry.key = key;
				keyToEntry.put(key, entry);
			}
		}

		if (compare(priority, entry.priority) == 0) {
			return;
		}

		entry.priority = priority;
		heapify(entry);

		// isValid(entry);
	}

	/**
	 * @param entry
	 * @param count
	 */
	private boolean isValid(Entry<E> entry) {
		int count = 0;
		for (int i = 0; i < indexToEntry.size(); ++i) {
			if (indexToEntry.get(i).key == entry.key)
				count++;
		}
		assert count == 1;
		return count == 1;
	}

	/**
	 * Checks if the queue is empty.
	 * 
	 * @return a <code>boolean</code> value
	 */
	public boolean isEmpty() {
		return indexToEntry.isEmpty();
	}

	/**
	 * Get the number of elements in the queue.
	 * 
	 * @return queue size
	 */
	public int size() {
		return indexToEntry.size();
	}

	public List<E> toSortedList() {
		List<E> sortedList = new ArrayList<E>(size());
		GeneralPriorityQueue<E> queue = deepCopy();
		while (queue.hasNext()) {
			sortedList.add(queue.next());
		}
		return sortedList;
	}

	public Iterator<E> iterator() {
		return Collections.unmodifiableCollection(toSortedList()).iterator();
	}

	/**
	 * Clears the queue.
	 */
	public void clear() {
		indexToEntry.clear();
		keyToEntry.clear();
	}

	// private void verify() {
	// for (int i = 0; i < indexToEntry.size(); i++) {
	// if (i != 0) {
	// // check ordering
	// if (compare(getEntry(i), parent(getEntry(i))) < 0) {
	// System.err.println("Error in the ordering of the heap! ("+i+")");
	// System.exit(0);
	// }
	// }
	// // check placement
	// if (i != ((Entry)indexToEntry.get(i)).index)
	// System.err.println("Error in placement in the heap!");
	// }
	// }

	@Override
	public String toString() {
		List<E> sortedKeys = toSortedList();
		StringBuffer sb = new StringBuffer("[");
		for (Iterator<E> keyI = sortedKeys.iterator(); keyI.hasNext();) {
			E key = keyI.next();
			sb.append(key);
			sb.append("=");
			sb.append(getPriority(key));
			if (keyI.hasNext()) {
				sb.append(", ");
			}
		}
		sb.append("]");
		return sb.toString();
	}

	public String toVerticalString() {
		List<E> sortedKeys = toSortedList();
		StringBuffer sb = new StringBuffer();
		for (Iterator<E> keyI = sortedKeys.iterator(); keyI.hasNext();) {
			E key = keyI.next();
			sb.append(key);
			sb.append(" : ");
			sb.append(getPriority(key));
			if (keyI.hasNext()) {
				sb.append("\n");
			}
		}
		return sb.toString();
	}

	public double getPriority() {
		return getPriority(getFirst());
	}

	public boolean containsKey(E e) {
		return keyToEntry.containsKey(e);
	}

	public String toString(int maxKeysToPrint) {
		GeneralPriorityQueue<E> pq = deepCopy();
		StringBuilder sb = new StringBuilder("[");
		int numKeysPrinted = 0;
		while (numKeysPrinted < maxKeysToPrint && !pq.isEmpty()) {
			double priority = pq.getPriority();
			E element = pq.removeFirst();
			sb.append(element.toString());
			sb.append(" : ");
			sb.append(priority);
			if (numKeysPrinted < size() - 1)
				// sb.append("\n");
				sb.append(", ");
			numKeysPrinted++;
		}
		if (numKeysPrinted < size())
			sb.append("...");
		sb.append("]");
		return sb.toString();
	}

	public GeneralPriorityQueue() {
		this(new MapFactory.HashMapFactory<E, Entry<E>>());
	}

	public GeneralPriorityQueue(MapFactory<E, Entry<E>> mapFactory) {
		indexToEntry = new ArrayList<Entry<E>>();
		keyToEntry = mapFactory.buildMap();
	}

	public static void main(String[] args) {
		GeneralPriorityQueue<String> queue = new GeneralPriorityQueue<String>();
		queue.setPriority("a", 1.0);
		System.out.println("Added a:1 " + queue);
		queue.setPriority("b", 2.0);
		System.out.println("Added b:2 " + queue);
		queue.setPriority("c", 1.5);
		System.out.println("Added c:1.5 " + queue);
		queue.setPriority("a", 3.0);
		System.out.println("Increased a to 3 " + queue);
		queue.setPriority("b", 0.0);
		System.out.println("Decreased b to 0 " + queue);
		System.out.println("removeFirst()=" + queue.next());
		System.out.println("queue=" + queue);
		System.out.println("removeFirst()=" + queue.next());
		System.out.println("queue=" + queue);
		System.out.println("removeFirst()=" + queue.next());
		System.out.println("queue=" + queue);
	}

	public void put(E key, double priority) {

		setPriority(key, priority);

	}

	public E peek() {
		return getFirst();
	}

}