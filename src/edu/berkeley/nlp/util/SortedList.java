package edu.berkeley.nlp.util;

import java.util.ArrayList;
import java.util.Comparator;

public class SortedList<E> extends ArrayList<E> {

	private Comparator<? super E> comp;

	public SortedList(Comparator<? super E> comp) {
		this.comp = comp;
	}

	@Override
	public boolean add(E o) {
		// if (super.contains(o)) return false;
		super.add(o);
		// Collections.sort(this, comp);
		for (int index = super.size() - 2; index >= 0; --index) {
			E e = get(index);
			int compare = comp.compare(o, e);

			if (compare >= 0) {
				set(index + 1, o);
				break;
			} else {
				set(index + 1, e);
				if (index == 0)
					set(index, o);
			}
		}
		return true;
	}

	@Override
	public void add(int index, E element) {
		throw new UnsupportedOperationException();
	}

}
