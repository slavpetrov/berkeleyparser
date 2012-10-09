package edu.berkeley.nlp.util;

import java.io.Serializable;

public class Freezer implements Serializable {
	private static final long serialVersionUID = 1L;
	private boolean frozen = false;
	private String owner;

	public Freezer(Object owner) {
		this.owner = owner.toString();
	}

	public void freeze() {
		frozen = true;
	}

	public boolean checkEasy() {
		return frozen;
	}

	public void checkHard() {
		if (frozen) {
			throw new RuntimeException("Attempt to edit " + owner
					+ " while it was frozen.");
		}
	}

	public void check() {
		checkHard();
	}
}
