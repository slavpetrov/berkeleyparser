package edu.berkeley.nlp.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TrieWithBackPointers<K, V> extends Trie<K, V> {

	@Override
	protected Trie<K, V> newTrie(boolean useIdentity2, K first, V v2,
			Trie<K, V> trie) {
		return new TrieWithBackPointers<K, V>(useIdentity2, first, v2, trie);
	}

	private Trie<K, V> backPointer;

	private K k;

	public TrieWithBackPointers(boolean useIdentity) {
		this(useIdentity, null, null, null);
	}

	public TrieWithBackPointers() {
		this(false, null, null, null);
	}

	private TrieWithBackPointers(boolean useIdentity, K k, V v,
			Trie<K, V> backPointer) {
		super(useIdentity, k, v, backPointer);
		this.backPointer = backPointer;
		this.k = k;

	}

	public Trie<K, V> getPreviousTrie() {
		return backPointer;
	}

	@Override
	public TrieWithBackPointers<K, V> getNextTrie(K k) {
		return (TrieWithBackPointers<K, V>) super.getNextTrie(k);
	}

	public List<K> retraceBackPointers() {

		List<K> list = new ArrayList<K>();
		retraceFromNodeHelper(list);
		Collections.reverse(list);
		return list;
	}

	private void retraceFromNodeHelper(List<K> list) {
		if (k == null)
			return;
		list.add(k);

		((TrieWithBackPointers<K, V>) backPointer).retraceFromNodeHelper(list);
	}

}