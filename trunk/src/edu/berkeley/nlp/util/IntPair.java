package edu.berkeley.nlp.util;

import java.io.Serializable;

public class IntPair implements Serializable {
	private static final long serialVersionUID = 42;

	public IntPair() {
	}

	public IntPair(int first, int second) {
		this.first = first;
		this.second = second;
	}

	@Override
	public String toString() {
		return first + "," + second;
	}

	@Override
	public int hashCode() {
		return 29 * first + second;
	}

	@Override
	public boolean equals(Object o) {
		IntPair p = (IntPair) o;
		return first == p.first && second == p.second;
	}

	public int first, second;
}
