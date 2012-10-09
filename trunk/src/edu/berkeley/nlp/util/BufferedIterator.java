package edu.berkeley.nlp.util;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

public class BufferedIterator<T> implements Iterator<T> {

	private Iterator<T> it;
	private Queue<T> buffer;
	private int numToBuffer;

	public BufferedIterator(Iterator<T> it, int numToBuffer) {
		this.it = it;
		this.buffer = new LinkedList<T>();
		this.numToBuffer = numToBuffer;
		refill();
	}

	public BufferedIterator(Iterator<T> it) {
		this(it, 100);
	}

	public boolean hasNext() {
		// TODO Auto-generated method stub
		if (!buffer.isEmpty())
			return false;
		return it.hasNext();
	}

	public T next() {
		// TODO Auto-generated method stub
		if (buffer.isEmpty()) {
			refill();
		}
		if (buffer.isEmpty()) {
			throw new RuntimeException();
		}
		return buffer.remove();
	}

	private void refill() {
		for (int i = 0; i < numToBuffer; ++i) {
			if (it.hasNext()) {
				buffer.add(it.next());
			}
		}
	}

	public void remove() {
		// TODO Auto-generated method stub

	}

}
