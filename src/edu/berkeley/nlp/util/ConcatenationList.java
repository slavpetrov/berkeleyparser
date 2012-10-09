package edu.berkeley.nlp.util;

import java.util.AbstractList;
import java.util.List;

/**
 * Concatenates two lists without building a new one. Unmodifiable.
 * 
 * @author adampauls
 * 
 * @param <K>
 */
public class ConcatenationList<K> extends AbstractList<K> {

	private List<List<K>> lists;

	private int size = 0;
	private int[] cumulativeSize;

	public ConcatenationList(List<List<K>> lists) {
		this.lists = lists;
		cumulativeSize = new int[lists.size()];
		int i = 0;
		for (List<K> set : lists) {
			cumulativeSize[i++] = i == 0 ? 0 : cumulativeSize[i - 1]
					+ set.size();
			size += set.size();
		}
	}

	@Override
	public int size() {
		return size;

	}

	@Override
	public K get(int arg0) {
		K k = binarySearch(lists.size() / 2, arg0);
		return k;
	}

	private K binarySearch(int listIndex, int i) {
		if (i >= cumulativeSize[listIndex]
				&& (listIndex == lists.size() || i < cumulativeSize[listIndex + 1])) {
			return lists.get(listIndex).get(i - cumulativeSize[listIndex]);
		}
		if (i >= cumulativeSize[listIndex + 1])
			return binarySearch((lists.size() - listIndex) / 2, i);
		assert i < cumulativeSize[listIndex];
		return binarySearch(listIndex / 2, i);
	}

}