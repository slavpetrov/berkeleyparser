/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.util.ArrayList;
import java.util.List;

import edu.berkeley.nlp.util.PriorityQueue;

/**
 * @author petrov
 * 
 */
public class LazyList {
	List<HyperEdge> sortedBegining;
	PriorityQueue<HyperEdge> fringe;
	int nSorted, nFringe;
	boolean[] grammarTags;

	// double bestScore;

	public LazyList(boolean[] tags) {
		sortedBegining = new ArrayList<HyperEdge>();
		fringe = new PriorityQueue<HyperEdge>();
		nSorted = 0;
		nFringe = 0;
		grammarTags = tags;
		// bestScore = Double.NEGATIVE_INFINITY;
	}

	public int sortedListSize() {
		return nSorted;
	}

	public void addToFringe(HyperEdge el) {
		fringe.add(el, el.score);
		nFringe++;
	}

	// public void addToSorted(HyperEdge el){
	// sortedBegining.add(el);
	// nSorted++;
	// }

	public HyperEdge getKbest(int k) {
		if (k > nSorted) {
			System.out.println("Don't have this element yet");
			return null;
			// } else if (k==nSorted&&(k==0||!fringe.hasNext())){
			// return null;
		} else if (k == nSorted) { // extract the next best
			expandNextBest();
		}
		if (k == nSorted)
			return null;
		return sortedBegining.get(k);
	}

	public void expandNextBest() {
		while (fringe.hasNext()) {
			HyperEdge edge = fringe.next();
			boolean isNew = true;
			for (HyperEdge alreadyIn : sortedBegining) {
				if (alreadyIn.differsInPOSatMost(edge, grammarTags)) {
					isNew = false;
					break;
				}
			}
			if (isNew) {
				sortedBegining.add(edge);
				nFringe--;
				nSorted++;
				break;
			}
		}
	}

}
