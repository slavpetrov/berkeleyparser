package edu.berkeley.nlp.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Concatenates an iterator over iterators into one long iterator.
 * 
 * @author Dan Klein
 */
public class ConcatenationIterator<E> implements Iterator<E> {

	Iterator<Iterator<E>> sourceIterators;
	Iterator<E> currentIterator;
	Iterator<E> lastIteratorToReturn;

	public boolean hasNext() {
		if (currentIterator.hasNext())
			return true;
		return false;
	}

	public E next() {
		if (currentIterator.hasNext()) {
			E e = currentIterator.next();
			lastIteratorToReturn = currentIterator;
			advance();
			return e;
		}
		throw new NoSuchElementException();
	}

	private void advance() {
		while (!currentIterator.hasNext() && sourceIterators.hasNext()) {
			currentIterator = sourceIterators.next();
		}
	}

	public void remove() {
		if (lastIteratorToReturn == null)
			throw new IllegalStateException();
		currentIterator.remove();
	}

	public ConcatenationIterator(Iterator<Iterator<E>> sourceIterators) {
		this.sourceIterators = sourceIterators;
		this.currentIterator = (new ArrayList<E>()).iterator();
		this.lastIteratorToReturn = null;
		advance();
	}

	public ConcatenationIterator(Collection<Iterator<E>> iteratorCollection) {
		this(iteratorCollection.iterator());
	}

	public static void main(String[] args) {
		List<String> list0 = Collections.emptyList();
		List<String> list1 = Arrays.asList("a b c d".split(" "));
		List<String> list2 = Arrays.asList("e f".split(" "));
		List<Iterator<String>> iterators = new ArrayList<Iterator<String>>();
		iterators.add(list1.iterator());
		iterators.add(list0.iterator());
		iterators.add(list2.iterator());
		iterators.add(list0.iterator());
		Iterator<String> iterator = new ConcatenationIterator<String>(iterators);
		while (iterator.hasNext()) {
			System.out.println(iterator.next());
		}
	}
}
