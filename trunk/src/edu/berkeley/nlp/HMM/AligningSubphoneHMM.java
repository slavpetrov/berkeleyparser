/**
 * 
 */
package edu.berkeley.nlp.HMM;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.berkeley.nlp.HMM.TimitTester.MergeThreshType;
import edu.berkeley.nlp.HMM.TimitTester.MergingType;
import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.prob.GaussianSuffStats;
import fig.basic.Indexer;

/**
 * @author adpauls
 * 
 */
public class AligningSubphoneHMM extends SubphoneHMM {

	@Override
	public void mergeModel(List<int[]> phoneObjectSequences, List<double[][]> acousticObservationSequences, double percentage, MergingType mergingType, MergeThreshType mergeThresType) {
		//find an alignment with viterbi decoding and merge on that
		List<int[]> alignedSequences = new ArrayList<int[]>();
		AligningTrainingProbChart chart = new AligningTrainingProbChart(this, pruneThresh,emissionAttenuation);
		for (int i = 0; i < phoneObjectSequences.size(); ++i)
		{
			int[] bestAlignment = chart.getBestAlignment(phoneObjectSequences.get(i),
					acousticObservationSequences.get(i),i);
//			for (int x : phoneObjectSequences.get(i)) {
//				System.out.print(x + ",");
//
//			}
//			System.out.print(" :: ");
//			
//			for (int x : bestAlignment) {
//				System.out.print(x + ",");
//			}
//			System.out.println();
			alignedSequences.add(bestAlignment);
		}
		super.mergeModel(alignedSequences, acousticObservationSequences, percentage, mergingType, mergeThresType);
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = -2909881732307560848L;
	private int[][][] nextPhonesMap;
	
	private int pruneThresh;
	
	 List<int[]> uncollapsedPhoneSequences;

	/**
	 * @param phoneIndexer
	 * @param gaussianDim
	 * @param rSeed
	 * @param fullGauss
	 */
	public AligningSubphoneHMM(PhoneIndexer phoneIndexer, int gaussianDim,
			int rSeed, boolean fullGauss, double smoothingFactor, double meanSmooth, double varSmooth, int pruneThresh, double minVarSmooth,double emissionAttenuation) {
		super(phoneIndexer, gaussianDim, rSeed, fullGauss, smoothingFactor, meanSmooth,varSmooth, minVarSmooth,emissionAttenuation,false,null);
		this.pruneThresh = pruneThresh;
		// TODO Auto-generated constructor stub
	}

	@Override
	public void initializeModelFromStateSequence(List<int[]> stateSequences,
			List<double[][]> obsSequences, int numSubstates, int randomness) {

		super.initializeModelFromStateSequence(stateSequences, obsSequences, numSubstates, randomness);

		nextPhonesMap = new int[stateSequences.size()][numPhones][];
		for (int seq = 0; seq < stateSequences.size(); ++seq) {
			Map<Integer, Set<Integer>> nextPhonesTmp = new HashMap<Integer, Set<Integer>>();
			for (int i = 0; i < stateSequences.get(seq).length - 1; ++i) {
				int phone = stateSequences.get(seq)[i];
				if (!nextPhonesTmp.containsKey(phone))
					nextPhonesTmp.put(phone, new HashSet<Integer>());
				nextPhonesTmp.get(phone).add(stateSequences.get(seq)[i + 1]);
			}
			for (int phone = 0; phone < numPhones; ++phone) {

				Set<Integer> nextSet = nextPhonesTmp.containsKey(phone) ? nextPhonesTmp
						.get(phone) : new HashSet<Integer>();
boolean specialState = true || (phone == Corpus.END_STATE || phone == Corpus.START_STATE);
				//					 can always transition to the same phone, unless you are the start or end state
				int size = specialState ? nextSet.size() : nextSet.size() + 1;
				nextPhonesMap[seq][phone] = new int[size];
				int i = 0;
				if (!specialState)
				{
					nextPhonesMap[seq][phone][i++] = phone;
				}
				for (int nextPhone : nextSet) {
					nextPhonesMap[seq][phone][i++] = nextPhone;
				}

			}
		}

	}
	@Override
	protected void countEmissions(GaussianSuffStats[][] new_b, ITrainingProbChart probChart2, final double[][] o, final int T) {
		AligningTrainingProbChart probChart = (AligningTrainingProbChart)probChart2;
		for (int t = 1; t < T - 1; t++) {
			for (int n : probChart.allowedIndicesAtTime(t)) {

				int phone = probChart.phoneSequence[n];
				for (int substate = 0; substate < numSubstatesPerState[phone]; ++substate) {
					double gamma = probChart.getGamma(n, substate, t);

					assert FwdBwdChart.isProbability(gamma);
				

					assert FwdBwdChart.isProbability(gamma);
					new_b[phone][substate].add(o[t], gamma);
//					double[] weightedObs = ArrayMath.multiply(o[t], gamma);
//
//					// first the means
//					ArrayMath.addInPlace(new_b_sum[phone][substate],
//							weightedObs);
//					new_b_normalize[phone][substate] += gamma;
//
//					// now variance
//					double[][] scaledOuterProduct = null;
//					if (useFullGaussian) {
//						scaledOuterProduct = FullCovGaussian
//								.scaledOuterSelfProduct(o[t], gamma);
//						for (int i = 0; i < gaussianDim; ++i) {
//							ArrayMath.addInPlace(
//									new_b_crossterms[phone][substate][i],
//									scaledOuterProduct[i]);
//						}
//					} else {
//
//						double[] diagonal = ArrayMath
//								.pairwiseMultiply(o[t], o[t]);
//						ArrayMath.addInPlace(
//								new_b_crossterms_diag[phone][substate], ArrayMath
//										.multiply(diagonal, gamma));
//
//					}

				

				}
			}
		}
	}
@Override
	protected void countTransitions(double[][][][] new_a, double[][][] new_c, double[][] new_c_normalize, ITrainingProbChart probChart2, final int T) {
	AligningTrainingProbChart probChart = (AligningTrainingProbChart)probChart2;
		for (int t = 0; t < T - 1; t++) {
			for (int fromN : probChart.allowedIndicesAtTime(t)) {
				if (probChart.pruneAlpha(fromN,t)) continue;
				// int fromPhone = phoneSeq[n];
				for (int toN : probChart.allowedNextIndicesAtTime(t,
						fromN)) {
					// int toPhone = phoneSeq[toN];
					double[][] probs = probChart.getProbability(t, fromN,
							toN);
					int fromPhone = probChart.phoneSequence[fromN];
					int toPhone = probChart.phoneSequence[toN];
					for (int fromSubstate = 0; fromSubstate < numSubstatesPerState[fromPhone]; ++fromSubstate) {
						for (int toSubstate = 0; toSubstate < numSubstatesPerState[toPhone]; ++toSubstate) {
							double prob = probs[fromSubstate][toSubstate];
							assert FwdBwdChart.isProbability(prob);

							new_a[toSubstate][toPhone][fromPhone][fromSubstate] += prob;
							new_c[toPhone][fromPhone][fromSubstate] += prob;
							new_c_normalize[fromPhone][fromSubstate] += prob;
						}
					}
				}
			}
		}
	}



	@Override
	protected ITrainingProbChart createTrainingChart() {
		return new AligningTrainingProbChart(this, pruneThresh,emissionAttenuation);
	}

	/**
	 * @return
	 */
	public int[][] getNextPhonesMap(int seq) {
//	when distributed, we must reconstruct the original sequence number
//		int offset = ((Integer)DataHolder.get(id, OFFSET_KEY)).intValue();
		int fullSeq = seq;// + offset;
		return nextPhonesMap[fullSeq];
	}

	public void setUncollapsedPhoneSequences(List<int[]> uncollapsedPhoneSequences) {
		this.uncollapsedPhoneSequences = uncollapsedPhoneSequences;
	}

	/**
	 * @param seq
	 * @return
	 */
	public int[] getUncollapsedPhoneSeq(int seq) {
//	when distributed, we must reconstruct the original sequence number
//		int offset = ((Integer)DataHolder.get(id, OFFSET_KEY)).intValue();
		int fullSeq = seq;// + offset;
		return uncollapsedPhoneSequences.get(fullSeq);
	}

}
