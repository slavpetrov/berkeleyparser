package edu.berkeley.nlp.bitext;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class GrammarStateFactory implements Serializable {
	
	private final Map<String, GrammarState> stateInterner = new HashMap<String, GrammarState>();
	private final List<GrammarState> grammarStates = new ArrayList<GrammarState>();
	private boolean locked = false;
	
	public GrammarState getGrammarState(int id) {
		if (id >= grammarStates.size()) return null;
		return grammarStates.get(id);
	}
	
	public GrammarState getState(String state) 
	{
		GrammarState gState = stateInterner.get(state);
		if (gState == null) {
			if (locked) {
				throw new RuntimeException("Can't add a grammar state once locked");
			}
			gState = new GrammarState(state, stateInterner.size());
			grammarStates.add(gState);
			stateInterner.put(state, gState);
		}
		return gState;
	}
	
	public int getNumGrammarStates() {
		return stateInterner.size();
	}
	
	/**
	 * Each <code>GrammarState</code> encapsulates the name
	 * of a grammar state <code>"NP"</code> or <code>"DT"</code> as 
	 * well as a unique <code>int</code> identifier.
	 * @author aria42
	 *
	 */
	public static class GrammarState implements Comparable<GrammarState>, Serializable {

		private final String label;
		private final int id;

		GrammarState(String label, int id) {
			this.label = label;
			this.id = id;
		}
		
		public int compareTo(GrammarState other) {
			return other.id() - this.id();
		}

		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (!(o instanceof GrammarState)) {
				return false;
			}
			GrammarState other = (GrammarState) o;
			return this.id == other.id;
		}

		public int hashCode() {
			return this.label.hashCode();
		}

		public String toString() { return label; }

		public String label() { return label; }
		public int id() { return id; }
	}

	public void lock() {
		locked = true;
		
	}
}