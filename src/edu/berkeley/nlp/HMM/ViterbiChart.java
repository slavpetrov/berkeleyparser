/**
 * 
 */
package edu.berkeley.nlp.HMM;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import edu.berkeley.nlp.prob.Gaussian;
import edu.berkeley.nlp.util.ArrayUtil;

class ViterbiChart {

	
//		private final double emissionAttenuation;

		
		private double[][][] deltas; // alphas[t][q_t][q_substate] =

		// p(y_0,...,p_t,q_t)

	

		/** Backpointers */
		private int[][][] phiPhone;

		private int[][][] phiSubstate;

		/** Sequence of vector valued observations (i.e. the acoustics) */
		private double[][] observations;
		
				/** The length of the observations sequence */
		private int T;

		
		private final double[][][][] logTransitions;
		private final Gaussian[][] logEmissions;
		
		private double myLength;

		private SubphoneHMM hmm;
		private double phoneInsertionPenalty;
		
		public ViterbiChart(SubphoneHMM hmm, double phoneInsertionPenalty, double emissionAttenuation) {
			this.hmm = hmm;
			this.phoneInsertionPenalty = phoneInsertionPenalty;
			this.logEmissions = hmm.b;
			this.logTransitions = new double[hmm.maxNumSubstates][hmm.numPhones][hmm.numPhones][hmm.maxNumSubstates];
//			this.emissionAttenuation = emissionAttenuation;

			for (int fromPhone = 0; fromPhone < hmm.numPhones; ++fromPhone) {
				for (int j = 0; j < hmm.numSubstatesPerState[fromPhone]; ++j) {
					for (int toPhone = 0; toPhone < hmm.numPhones; ++toPhone) {
						for (int k = 0; k < hmm.numSubstatesPerState[toPhone]; ++k) {
							logTransitions[k][toPhone][fromPhone][j] = Math.log(hmm.a[k][toPhone][fromPhone][j]*hmm.c[toPhone][fromPhone][j]); 
						}
					}
				}
			}

			
			myLength = 0;
		}

		public void init(double[][] obs) {
			observations = obs;

			T = obs.length;
			if (T > myLength) {
				deltas = new double[T][hmm.numPhones][hmm.maxNumSubstates];
				phiPhone = new int[T][hmm.numPhones][hmm.maxNumSubstates];
				phiSubstate = new int[T][hmm.numPhones][hmm.maxNumSubstates];


				myLength = T;
				ArrayUtil.fill(deltas, T, Double.NEGATIVE_INFINITY);
			} else {

				ArrayUtil.fill(deltas, T, Double.NEGATIVE_INFINITY);
				ArrayUtil.fill(phiPhone, T, -1);
				ArrayUtil.fill(phiSubstate, T, -1);


			}


		}

		/**
		 * 
		 */
		public List<Phone> calc() {

			deltas[0][hmm.phoneIndexer.indexOf(Corpus.START_PHONE)][0] = 0.0;//1.0

			// induction
			for (int t = 1; t < T; ++t) {
//				double max = Double.NEGATIVE_INFINITY;
				for (int toPhone = 1; toPhone < hmm.numPhones; ++toPhone) {
//					if (t==1 && toPhone!=2) continue;
					for (int toSubstate = 0; toSubstate < hmm.numSubstatesPerState[toPhone]; ++toSubstate) {

						double maxPathProb = Double.NEGATIVE_INFINITY;
						int maxPathPhone = -1;
						int maxPathSubstate = -1;
						for (int fromPhone = 0; fromPhone <hmm. numPhones; ++fromPhone) {

							for (int fromSubstate = 0; fromSubstate < hmm.numSubstatesPerState[fromPhone]; ++fromSubstate) {

								double prevDelta = deltas[t - 1][fromPhone][fromSubstate];
								if (prevDelta==Double.NEGATIVE_INFINITY) continue;
								double currPathProb = prevDelta	+ logTransitions[toSubstate][toPhone][fromPhone][fromSubstate];
								if (toPhone != fromPhone)
								{
									currPathProb -= phoneInsertionPenalty;
								}
								if (currPathProb > maxPathProb) {
									maxPathProb = currPathProb;
									maxPathPhone = fromPhone;
									maxPathSubstate = fromSubstate;
								}
							}
						}
						double delta = maxPathProb;
						
						if (toPhone!=1 && t<T-1 && delta>Double.NEGATIVE_INFINITY){
//							delta *=  hmm.b[toPhone][toSubstate].evalPdf(observations[t]);
							if (!TimitTester.staticOnlySamePrev || maxPathPhone == toPhone)
								logEmissions[toPhone][toSubstate].setPrevObservation(observations[t-1]);
							else
								logEmissions[toPhone][toSubstate].setPrevObservation(null);
							double logEmission = logEmissions[toPhone][toSubstate].evalLogPdf(observations[t]);
//							if (emissionAttenuation!=0) logEmission *= emissionAttenuation;
							delta += logEmission;
						}
						deltas[t][toPhone][toSubstate] = delta;
//						if (delta > max)
//							max = delta;
						
						phiPhone[t][toPhone][toSubstate] = maxPathPhone;
						phiSubstate[t][toPhone][toSubstate] = maxPathSubstate;
					}
				}


			}


			List<Phone> viterbiSequence = new ArrayList<Phone>(T);

			int prevPhone = hmm.phoneIndexer.indexOf(Corpus.END_PHONE);
			int prevSubstate = 0;
			viterbiSequence.add(new AnnotatedPhone(hmm.phoneIndexer.get(prevPhone),
					prevSubstate));

			// retrace backpointers

			for (int t = T - 1; t > 0; --t) {
				int tmp = prevPhone;
				prevPhone = phiPhone[t][tmp][prevSubstate];
				prevSubstate = phiSubstate[t][tmp][prevSubstate];
				viterbiSequence.add(new AnnotatedPhone(hmm.phoneIndexer.get(prevPhone),
						prevSubstate));

			}
			Collections.reverse(viterbiSequence);
			return viterbiSequence;

		}

		

	}