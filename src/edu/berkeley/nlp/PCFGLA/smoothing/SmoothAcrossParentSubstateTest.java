/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.smoothing;

import java.io.Serializable;

import edu.berkeley.nlp.PCFGLA.BinaryCounterTable;
import edu.berkeley.nlp.PCFGLA.BinaryRule;
import edu.berkeley.nlp.PCFGLA.UnaryCounterTable;
import edu.berkeley.nlp.PCFGLA.UnaryRule;

/**
 * @author leon
 * 
 */
public class SmoothAcrossParentSubstateTest {

	public SmoothAcrossParentSubstateTest() {
		// CLASS IS BROKEN
	}

	/*
	 * Test method for
	 * 'edu.berkeley.nlp.PCFGLA.smoothing.SmoothAcrossParentSubstate.smooth(UnaryCounterTable,
	 * BinaryCounterTable)'
	 */
	public void testSmooth() {
		short pState = 1, lState = 2, rState = 3;
		short[] numSubStates = { 3, 3, 3, 3 };
		double[][] uScores = { { 1, 0, 0 }, { 1, 1, 1 }, { 0, 0, 1 } };
		double[][] targetUScores = { { 0.75, 0.25, 0.5 }, { 0.75, 0.5, 0.75 },
				{ 0.5, 0.25, 0.75 } };
		double[][][] bScores = { { { 1, 0, 0 }, { 0, 1, 0 }, { 0, 0, 1 } },
				{ { 0, 1, 0 }, { 0, 0, 0 }, { 0, 0, 0 } },
				{ { 0, 0, 0 }, { 0, 0, 0 }, { 0, 0, 0 } } };
		double[][][] targetBScores = {
				{ { 0.5, 0.25, 0 }, { 0, 0.5, 0 }, { 0, 0, 0.5 } },
				{ { 0.25, 0.5, 0 }, { 0, 0.25, 0 }, { 0, 0, 0.25 } },
				{ { 0.25, 0.25, 0 }, { 0, 0.25, 0 }, { 0, 0, 0.25 } } };

		UnaryRule ur = new UnaryRule(pState, lState, uScores);
		BinaryRule br = new BinaryRule(pState, lState, rState, bScores);
		Smoother sm = new SmoothAcrossParentSubstate(0.5);
		UnaryCounterTable unaryCounter = new UnaryCounterTable(numSubStates);
		unaryCounter.setCount(ur, uScores);
		BinaryCounterTable binaryCounter = new BinaryCounterTable(numSubStates);
		binaryCounter.setCount(br, bScores);
		sm.smooth(unaryCounter, binaryCounter);

		double[][] newUScores = unaryCounter.getCount(ur);
		for (int i = 0; i < newUScores.length; i++) {
			for (int j = 0; j < newUScores[i].length; j++) {
				// assertEquals(newUScores[i][j],targetUScores[i][j],0.0001);
			}
		}
		double[][][] newBScores = binaryCounter.getCount(br);
		for (int i = 0; i < newBScores.length; i++) {
			for (int j = 0; j < newBScores[i].length; j++) {
				for (int k = 0; k < newBScores[i][j].length; k++) {
					// assertEquals(newBScores[i][j][k],targetBScores[i][j][k],0.0001);
				}
			}
		}
	}

}
