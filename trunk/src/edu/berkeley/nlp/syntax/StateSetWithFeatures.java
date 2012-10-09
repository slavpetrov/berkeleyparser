/**
 * 
 */
package edu.berkeley.nlp.syntax;

import java.util.ArrayList;
import java.util.List;

/**
 * @author petrov
 * 
 */
public class StateSetWithFeatures extends StateSet {

	public List<Integer> features;

	public StateSetWithFeatures(short state, short nSubStates) {
		super(state, nSubStates);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param stateSet
	 */
	public StateSetWithFeatures(StateSet stateSet) {
		super(stateSet.state, stateSet.numSubStates);
		this.from = stateSet.from;
		this.to = stateSet.to;
		this.word = stateSet.word;
		this.wordIndex = stateSet.wordIndex;
		this.features = new ArrayList<Integer>();
	}

}
