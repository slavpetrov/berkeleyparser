/**
 * 
 */
package edu.berkeley.nlp.speech.phonedecoder;

import java.util.ArrayList;
import java.util.List;

import edu.berkeley.nlp.HMM.AcousticModel;
import edu.berkeley.nlp.HMM.Phone;
import fig.basic.Indexer;

/**
 * 
 * @author John DeNero
 */
public class SubphoneIndexer {

	private List<Integer> phoneIndices = new ArrayList<Integer>();

	private List<Integer> substates = new ArrayList<Integer>();

	private int[][] subphoneMap;

	private int start, end;

	private List<Integer> specialStates;

	/**
	 * @param model
	 */
	public SubphoneIndexer(AcousticModel m) {
		Indexer<Phone> phoneIndexer = m.getPhoneIndexer();
		subphoneMap = new int[phoneIndexer.size()][];
		for (int phone = 0; phone < phoneIndexer.size(); phone++) {
			subphoneMap[phone] = new int[m.getNumStates(phone)];
			for (int substate = 0; substate < m.getNumStates(phone); substate++) {
				int index = phoneIndices.size();
				phoneIndices.add(phone);
				substates.add(substate);
				subphoneMap[phone][substate] = index;

				if (phone == m.getEndPhoneIndex()) {
					assert (substate == 0);
					end = index;
				} else if (phone == m.getStartPhoneIndex()) {
					assert (substate == 0);
					start = index;
				}
			}
		}

		specialStates = new ArrayList<Integer>();
		specialStates.add(start);
		specialStates.add(end);
	}

	public int size() {
		return phoneIndices.size();
	}

	public int getStartStateIndex() {
		return start;
	}

	public int getEndStateIndex() {
		return end;
	}

	/**
	 * @param pair
	 * @return
	 */
	public int indexOf(int phone, int substate) {
		return subphoneMap[phone][substate];
	}

	public int getPhone(int subphone) {
		return phoneIndices.get(subphone);
	}

	public int getSubstate(int subphone) {
		return substates.get(subphone);
	}

	public String toString() {
		return phoneIndices.toString();
	}

	/**
	 * @return
	 */
	public List<Integer> getSpecialStates() {
		return specialStates;
	}
}
