/**
 * 
 */
package edu.berkeley.nlp.HMM;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.berkeley.nlp.math.SloppyMath;
import edu.berkeley.nlp.prob.Gaussian;
import edu.berkeley.nlp.util.ArrayUtil;
import fig.basic.LogInfo;

/**
 * @author adpauls
 * 
 */
public class AligningTrainingProbChart extends TrainingProbChart { // implements
																																		// TrainingChart{

	private int pruneThresh;
	public AligningTrainingProbChart(SubphoneHMM hmm, int pruneThresh, double emissionAttenuation) {
		super(hmm,emissionAttenuation);
		this.pruneThresh = pruneThresh;

	}

	private int seq;
	private int[][] nextPhones;

	private int N;

	private int maxN = 0;

	// private Map<Integer, List<Integer>> phoneToIndexMap = new HashMap<Integer,
	// List<Integer>>();;

	@Override
	public void init(int[] phoneInd, double[][] obs, int seq) {

		super.init(phoneInd, obs, seq);
		
		this.seq = seq;
		if (seq < 0)
		{
			nextPhones = new int[T][hmm.numPhones];
			for (int t = 0; t < T; ++t)
			{
				for (int p = 0; p < hmm.numPhones; ++p)
				{
					nextPhones[t][p] = p;
				}
			}
		}
		else
		{
		nextPhones = ((AligningSubphoneHMM) hmm).getNextPhonesMap(seq);
		}
		uncollapsedPhoneSeq = ((AligningSubphoneHMM) hmm).getUncollapsedPhoneSeq(seq);
		N = phoneSequence.length;
		// phoneToIndexMap.clear();
		// for (int n = 0; n < N; ++n) {
		// int phone = phoneSequence[n];
		// if (!phoneToIndexMap.containsKey(phone))
		// phoneToIndexMap.put(phone, new LinkedList<Integer>());
		// phoneToIndexMap.get(phone).add(n);
		// }

		if (T > myLength || N > maxN || myLength > alphas.length || alphas.length > bestAlpha.length) {
			myLength = Math.max(myLength, T);
			maxN = Math.max(maxN, N);
			alphas = new double[myLength][maxN][hmm.maxNumSubstates];
			betas = new double[myLength][maxN][hmm.maxNumSubstates];
			gammas = new double[myLength][maxN][hmm.maxNumSubstates];
			bestAlpha = new double[myLength];
			bestAlphaIndex = new int[myLength];
			bestBeta = new double[myLength];
			bestBetaIndex = new int[myLength];
			ArrayUtil.fill(gammas, T, 0);
			ArrayUtil.fill(obsLikelihoods, T, -1);
			Arrays.fill(alphasScale, 0);
			Arrays.fill(betasScale, 0);
		

		} else {
			ArrayUtil.fill(alphas, T, 0);
			ArrayUtil.fill(betas, T, 0);
			Arrays.fill(bestAlpha,0);
			Arrays.fill(bestAlphaIndex,0);
			Arrays.fill(bestBeta,0);
			Arrays.fill(bestBetaIndex,0);
			
			// ArrayUtil.fill(alphas, myLength, 0);
			// ArrayUtil.fill(betas, myLength, 0);
			ArrayUtil.fill(gammas, T, 0);
			ArrayUtil.fill(obsLikelihoods, T, -1);
			Arrays.fill(alphasScale, 0);
			Arrays.fill(betasScale, 0);
		}
		

		

		// initArray(alphas);
		// initArray(betas);
		// initArray(gammas);
	}

	// SCALING
	//private final double SCALE = Math.exp(100);

	// Note: e^709 is the largest double java can handle.

	// TODO: we could get rid of the 2nd dimension since it is observed in our
	// data
	//protected double[][][] alphasN; // alphas[t][q_t][q_substate] =

	// p(y_0,...,p_t,q_t)

	
	//protected double[][][] betasN; // betas[t][q_t][q_substate] =

	// p(y_t+1,...,y_T|q_t)
	
	protected double[] bestAlpha; //best[t]
	protected int[] bestAlphaIndex; //bestN[t]
	protected double[] bestBeta; //best[t]
	protected int[] bestBetaIndex; //bestN[t]

	


	/**
	 * computes the forward-scores b(i,t) for states at times t with emission O
	 * under the current model
	 * 
	 * @param o
	 *          the emission O
	 * @return forward scores b(i,t)
	 */
	protected void calculateAlphas() {

//		System.out.println();
//		for (int i = 0; i < uncollapsedPhoneSeq.length; ++i)
//		{
//			System.out.print(i + ":" + uncollapsedPhoneSeq[i] + ",");
//		}
//		System.out.println();
		// Basis
		alphas[0][0][0] = 1.0;
		alphasScale[0] = 0.0;
		bestAlpha[0] = 1.0;
		bestAlphaIndex[0] = hmm.phoneIndexer.indexOf(Corpus.START_PHONE);
		// Induction
		for (int t = 1; t < T; t++) {
			final double[][] alphat = alphas[t];
			final double[][] obsLikelihoods_t = obsLikelihoods[t];
			double max = Double.NEGATIVE_INFINITY;
			int maxN = -1;

			for (int toN : allowedIndicesAtTime(t)) {
				//System.out.println("Considering " + toN + " at time " + t);
				if (toN == 0)
					continue;
				assert !(toN == N-1 && t != T-1);
				if (pruneAlpha(toN,t))
				{
					continue;
				}
				int toPhone = phoneSequence[toN];
				for (int toSubstate = 0; toSubstate < hmm.numSubstatesPerState[toPhone]; ++toSubstate) {
					double alpha = 0.0;
					for (int fromN : allowedPrevIndicesAtTime(t, toN)) {
						//System.out.println("Considering prev " + fromN + " for "  + toN + " at time " + t);
					
						assert !(fromN == 0 && t !=1);
						assert fromN != N-1;
						int fromPhone = phoneSequence[fromN];
						for (int fromSubstate = 0; fromSubstate < hmm.numSubstatesPerState[fromPhone]; ++fromSubstate) {
//							double leaveStateProb = (toPhone == fromPhone) ? 1.0 - hmm.c[toPhone][fromPhone][fromSubstate]
//									: hmm.c[toPhone][fromPhone][fromSubstate];
							double prevAlpha = alphas[t - 1][fromN][fromSubstate];
							if (prevAlpha==0) continue;
							double leaveStateProb = hmm.c[toPhone][fromPhone][fromSubstate];
							alpha += prevAlpha
									* hmm.a[toSubstate][toPhone][fromPhone][fromSubstate]
									* leaveStateProb;
							assert !SloppyMath.isVeryDangerous(alpha);
						}
					}

					if (t < T - 1) {
						double obsLik = obsLikelihoods_t[toPhone][toSubstate];
						
						if (obsLik < 0.0)
						{
							obsLik = hmm.b[toPhone][toSubstate].evalPdf(observations[t]);
							
						
							obsLikelihoods_t[toPhone][toSubstate] = obsLik;
						}
						alpha *= obsLik;
					}
					
					if (alpha > max)
					{
						max = alpha;
						maxN = toN;
					}
					alphat[toN][toSubstate] = alpha;
				}
			}
			//assert max > 0;
			if (max <= 0)
			{
				throw new PathNotFoundException("Alpha path not found at time " + t + " for sequence " + seq);
			
			}
			
			//make sure our best hypothesis never goes backwards
			if (bestAlphaIndex[t-1] > maxN)
			{
				maxN = bestAlphaIndex[t-1];
				
			}
//			double maxAlpha = Math.min(max,bestAlpha[t-1]);
			
			//System.out.println("Best alpha is " + maxN);
			bestAlphaIndex[t] = maxN;
//			bestAlpha[t] = maxAlpha;
		
			int logScale = 0;
			double scale = 1.0;
			//	System.out.println("Scaling using " + max);
			while (max > SCALE) {
				max /= SCALE;
				scale *= SCALE;
				logScale += 1;
			}
			while (max > 0.0 && max < 1.0 / SCALE) {
				max *= SCALE;
				scale /= SCALE;
				logScale -= 1;
			}
			if (logScale != 0) {
				for (int n : allowedIndicesAtTime(t)) {

					for (int toSubstate = 0; toSubstate < hmm.numSubstatesPerState[phoneSequence[n]]; ++toSubstate) {
						alphat[n][toSubstate] /= scale;
//						bestAlpha[t] /= scale;
					}
				}
			}

			alphasScale[t] = alphasScale[t - 1] + logScale;
		}

	}
	

	private int[] uncollapsedPhoneSeq;

	/**
	 * @param toN
	 * @return
	 */
	boolean pruneAlpha(int toN, int t) {
		return false;
//		if (t < pruneThresh || (T-t) < pruneThresh)
//			return false;
//		//if (Math.abs(bestAlphaIndex[t - 1] - toN) < pruneThresh) {
//		if (Math.abs(uncollapsedPhoneSeq[t] - toN) >= pruneThresh) return true;
//		for (int i = 0; i < pruneThresh; ++i)
//		{
//			if (uncollapsedPhoneSeq[t-i] == toN) return false;
//			else if (uncollapsedPhoneSeq[t+i] == toN) return false;
//			
//		}
//		return true;
//		if (Math.abs(uncollapsedPhoneSeq[t] - toN) < pruneThresh){
//			return false;
//		} else {
//			return true;
//		}
		// double max = ArrayMath.max(alphasN[t -1][toN]);
		// return (max / bestAlpha[t-1] < pruneThresh);

	}

	private boolean pruneBeta(int toN, int t) {
		return pruneAlpha(toN,t);
//		if (t > T - 3)
//			return false;
//		//if (Math.abs(bestBetaIndex[t + 1] - toN) < pruneThresh) {
//		if (Math.abs(uncollapsedPhoneSeq[t] - toN) < pruneThresh){
//		return false;
//		} else {
//			return true;
//		}
		// double max = ArrayMath.max(betasN[t +1][toN]);
		// if (max / bestBeta[t+1] < pruneThresh)
		// {
		// return true;
		// }
		// else
		// {
		// return false;
		//		}

	}



	/**
	 * @param t2
	 * @param toN
	 * @return
	 */
	private int[] allowedPrevIndicesAtTime(int t, int toN) {
		assert (t > 0);
//	beginning or end
		if (t== T-1) return new int[]{N-2}; 
		if (t == 1) 	return new int[]{0};
			
		//can only transition to start state ( = 0) if t2 == T-2 
		if (toN == 1) return new int[]{1};
		
		t = t-1;
		

		//special cases for beginning and end states
//		if (t == 0)
//		{
//			return new int[]{0};
//		}
//		else if (t == T-1)
//		{
//			return new int[]{N-1};
//		}
		
		
		int j = Math.max(t -pruneThresh,1);
		int end = uncollapsedPhoneSeq[j];
		
		while (end < N-(T-t)) end++;
		boolean doThisN =  end <= toN;
		boolean doPrevN = end  < toN;
		if (doPrevN && doThisN) return new int[]{toN,toN-1};
		else if (doThisN) return new int[]{toN};
		else if (doPrevN) return new int[]{toN-1};
		assert false;
		return new int[]{};
	}

	/**
	 * @param t2
	 * @param toN
	 * @return
	 */
	int[] allowedNextIndicesAtTime(int t, int fromN) {
		

		assert (t < T - 1);
		//beginning or end
		if (t == T-2) return new int[]{N-1};
		if (t == 0) return new int[]{1};
		//can only transition to end state ( = N-1) if t2 == T-2 
		if (fromN == N-2) return new int[]{fromN};
		t = t+1;
		

		//special cases for beginning and end states
//		if (t == 0)
//		{
//			return new int[]{0};
//		}
//		else if (t == T-1)
//		{
//			return new int[]{N-1};
//		}
		
		
		int j = Math.min(t +pruneThresh,T-2);
		int end = uncollapsedPhoneSeq[j];
		
		while (end > t) --end;
		boolean doThisN =  end >= fromN;
		boolean doNextN = end  > fromN;
		if (doNextN && doThisN) return new int[]{fromN,fromN+1};
		else if (doThisN) return new int[]{fromN};
		else if (doNextN) return new int[]{fromN+1};
		assert false;
		return new int[]{};
		
	}

	/**
	 * computes the backward-scores b(i,t) for states at times t with emission O
	 * under the current model
	 * 
	 * @param o
	 *          the emission O
	 * @return backward scores b(i,t)
	 */
	protected void calculateBetas() {
		// Basis
//		betas[T - 1][hmm.phoneIndexer.indexOf(Corpus.END_PHONE)][0] = 1.0;
		betas[T - 1][N - 1][0] = 1.0;
		betasScale[T - 1] = 0;
		bestBetaIndex[T-1] = N-1;
		bestBeta[T-1] = 1.0;

		// Induction

		for (int t = T - 2; t >= 0; t--) {
			final double[][] betat = betas[t];
			final double[][] obsLikelihoods_t = obsLikelihoods[t + 1];

			double max = Double.NEGATIVE_INFINITY;
			for (int fromN : allowedIndicesAtTime(t)) {
				//System.out.println("Considering  " + fromN + " at time " + t);
				if (fromN == N - 1)
					continue;
				assert !(fromN == 0 && t !=0);
				if (pruneBeta(fromN,t)) continue;
				int fromPhone = phoneSequence[fromN];
				for (int fromSubstate = 0; fromSubstate < hmm.numSubstatesPerState[fromPhone]; ++fromSubstate) {
					double beta = 0.0;
					for (int toN : allowedNextIndicesAtTime(t, fromN)) {
						//System.out.println("Considering next " + toN + " for "  + fromN + " at time " + t);
						assert !(toN == N-1 && t !=T-2);
						assert toN != 0;
						int toPhone = phoneSequence[toN];
						double leaveStateProb = hmm.c[toPhone][fromPhone][fromSubstate];
						
				
						for (int toSubstate = 0; toSubstate < hmm.numSubstatesPerState[toPhone]; ++toSubstate) {

							double obsLik = obsLikelihoods_t[toPhone][toSubstate];
							if (t == T-2)
							{
								obsLik = 1.0;
							}
							else if (obsLik < 0.0)
							{
								obsLik = hmm.b[toPhone][toSubstate].evalPdf(observations[t+1]);
								
							
								obsLikelihoods_t[toPhone][toSubstate] = obsLik;
							}
							

							double prevBeta = betas[t + 1][toN][toSubstate];
							if (prevBeta==0) continue;
							beta += prevBeta * leaveStateProb
									* hmm.a[toSubstate][toPhone][fromPhone][fromSubstate]
									* obsLik;
							assert !SloppyMath.isVeryDangerous(beta);
						}
					}
					if (beta > max)

					{
						max = beta;
						maxN = fromN;
					}
					betat[fromN][fromSubstate] = beta;

				}

			}
			assert max > 0;
//		make sure our best hypothesis never goes backwards
			maxN = Math.min(maxN, bestBetaIndex[t+1]);
			//System.out.println("Best beta is " + maxN);
//			double maxBeta = Math.min(max,bestBeta[t+1]);
//			bestBeta[t] = maxBeta;
			bestBetaIndex[t] = maxN;
			int logScale = 0;
			double scale = 1.0;
			while (max > SCALE) {
				max /= SCALE;
				scale *= SCALE;
				logScale += 1;
			}
			while (max > 0.0 && max < 1.0 / SCALE) {
				max *= SCALE;
				scale /= SCALE;
				logScale -= 1;
			}
			if (logScale != 0) {
				for (int n : allowedIndicesAtTime(t)) {

					for (int fromSubstate = 0; fromSubstate < hmm.numSubstatesPerState[phoneSequence[n]]; ++fromSubstate) {
						betat[n][fromSubstate] /= scale;
//						bestBeta[t] /= scale;
					}
				}
			}
			betasScale[t] = betasScale[t + 1] + logScale;

		}
		
		verifyLikelihood();


	}
	
	public double getLogLikelihood() {
		double scaleFactor = alphasScale[T-1];
		return Math.log(alphas[T - 1][N-1][0]) + (100*scaleFactor);
	}
	
	public double getUnscaledLikelihood() {
		return alphas[T - 1][N-1][0];
	}
	
	protected void calculateGammas() {
		double z = getUnscaledLikelihood();
		double z_scale = alphasScale[T - 1];

		for (int t = 0; t < T-1; ++t) {
			int totalThisStep = 0;
			//for (int fromN = 0; fromN < N; ++fromN){
			for (int fromN : allowedIndicesAtTime(t)) {
				if (fromN == 0)
					continue;
				if (pruneAlpha(fromN,t)) continue;
				int fromPhone = phoneSequence[fromN];
				for (int fromSubstate = 0; fromSubstate < hmm.numSubstatesPerState[fromPhone]; ++fromSubstate) {
					double alpha_s = alphas[t][fromN][fromSubstate];
					
					if (alpha_s==0) continue;
					double alpha_scale = alphasScale[t];
					double beta_s = betas[t][fromN][fromSubstate];
					if (beta_s==0) continue;
//					assert !(alpha_s!= 0 && beta_s == 0);
//					assert !(alpha_s== 0 && beta_s != 0);
					
					double beta_scale = betasScale[t];
					double unscaled_posterior = alpha_s / z * beta_s;
					double posterior_scale = alpha_scale + beta_scale - z_scale;
					double exp_scale = getScaleFactor(posterior_scale);
					
					double gamma =  unscaled_posterior * exp_scale;
				//	assert !Double.isInfinite(exp_scale) : unscaled_posterior + "::" + alpha_scale + ":" +beta_scale + ":" + z_scale + ":" + exp_scale;
					if (Double.isInfinite(exp_scale)) gamma = 0.0;
					assert isProbability(gamma) : "Gamma is " + gamma;
					gammas[t][fromN][fromSubstate] += gamma;
					if (gamma>1.01 || gammas[t][fromN][fromSubstate]>1.01){
						LogInfo.error("Gamma is " + gamma);
					}
					totalThisStep += gamma;
				}
			}
			assert isProbability(totalThisStep);
			if (totalThisStep>1.01){
				LogInfo.error("Prob. of this time slice is above 1.");
			}

		}
	}
	
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.HMM.FwdBwdChart#allowedPhonesAtTime(int)
	 */

	protected int[] allowedIndicesAtTime(int t) {
		int toN = uncollapsedPhoneSeq[t];

		//special cases for beginning and end states
		if (t == 0)
		{
			return new int[]{0};
		}
		else if (t == T-1)
		{
			return new int[]{N-1};
		}
		int next =0;
		int prev =0;
		int k = Math.max(t-pruneThresh, 1);
		if ( uncollapsedPhoneSeq[k] != toN) {
			prev = uncollapsedPhoneSeq[k] - toN;
			
		
			while (prev + toN < N-(T-t) ) ++prev;
		}
		int j = Math.min(t +pruneThresh,T-2);
		if (uncollapsedPhoneSeq[j] != toN) {
			next = uncollapsedPhoneSeq[j] - toN;
			
			while (next + toN > t ) --next;
		}
		int[] retVal = new int[next - prev + 1];
		for (int i = prev; i <= next; ++i)
		{
			retVal[i-prev] = i+toN;
		}
		return retVal;
//		int start;
//		int end;
//		if (t < N - 1) {
//			start = 1;
//			end = t + 1;
//		} else if (T - t <= N - 1) {
//			start = N - (T - t);
//			end = N - 1;
//		} else {
//			start = 1;
//			end = N - 1;
//		}
//		int[] retVal = new int[end - start];
//		for (int i = 0; i < (end - start); ++i) {
//			retVal[i] = start + i;
//		}
//		return retVal;
	}

	
	private int[] cachedAllowedPhones;

	/*
	 * (non-Javadoc)
	 * 
	 * @see edu.berkeley.nlp.HMM.SubphoneHMM.FwdBwdChart#allowedPhonesAtTime(int)
	 */

	public int[] allowedPhonesAtTime(int t) {
		assert false;
//		if (cachedAllowedPhones == null) {
//			cachedAllowedPhones = new int[hmm.numPhones];
//			for (int i = 0; i < hmm.numPhones; ++i) {
//				cachedAllowedPhones[i] = i;
//			}
//		}
//		return cachedAllowedPhones;
		Set<Integer> tmp = new HashSet<Integer>(hmm.numPhones);
		for (int n : allowedIndicesAtTime(t))
		{
			tmp.add(phoneSequence[n]);
		}
		int[] retVal = new int[tmp.size()];
		int i = 0;
		for (int phone : tmp)
		{
			retVal[i++] = phone;
		}
		return retVal;
	}

	@Override
	public int[] allowedNextPhonesAtTime(int t, int fromPhone) {
		assert false;
		return t == T - 2 ? new int[]{hmm.phoneIndexer.indexOf(Corpus.END_PHONE)} : nextPhones[fromPhone];
	}

	
	public double[][] getProbability(int t, int fromN, int toN) {
		// return edgePosteriors[t][fromState][fromSubstate][toState][toSubstate];

		double z = getUnscaledLikelihood();
		double z_scale = alphasScale[T - 1];
		int fromPhone = phoneSequence[fromN];
		int toPhone = phoneSequence[toN];
		double[][] res = new double[hmm.numSubstatesPerState[fromPhone]][hmm.numSubstatesPerState[toPhone]];
		//for (int fromN : allowedIndicesAtTime(t)) {
//			if (pruneAlpha(fromN,  t)) continue;
			//int fromPhone = phoneSequence[fromN];
		//	if (fromPhone!=fromState) continue;
			for (int fromSubstate = 0; fromSubstate < hmm.numSubstatesPerState[fromPhone]; ++fromSubstate) {

			//	for (int toN : allowedNextIndicesAtTime(t, fromN)) {
			//		int toPhone = phoneSequence[toN];
			//		if (toPhone!=toState) continue;

					double alpha_s = alphas[t][fromN][fromSubstate];
					if (alpha_s==0) continue;
					double alpha_scale = alphasScale[t];
					double leaveStateProb = hmm.c[toPhone][fromPhone][fromSubstate];
					
					for (int toSubstate = 0; toSubstate < hmm.numSubstatesPerState[toPhone]; ++toSubstate) {
						double beta_t = betas[t + 1][toN][toSubstate];
						if (beta_t==0) continue;
						double beta_scale = betasScale[t + 1];
						double obsLik = obsLikelihoods[t+1][toPhone][toSubstate];
						if (t == T-2)
						{
							obsLik = 1.0;
						}
						else if (obsLik < 0.0)
						{
							obsLik = hmm.b[toPhone][toSubstate].evalPdf(observations[t+1]);
							
						
							obsLikelihoods[t+1][toPhone][toSubstate] = obsLik;
						}
						//.evalPdf(observations[t + 1]);
						double unscaled_posterior = alpha_s * leaveStateProb
								* hmm.a[toSubstate][toPhone][fromPhone][fromSubstate] / z * beta_t
								* obsLik;
						double posterior_scale = alpha_scale + beta_scale - z_scale;
						double exp_scale = getScaleFactor(posterior_scale);
						double edgePosterior = unscaled_posterior * exp_scale;
						if (Double.isInfinite(exp_scale)) edgePosterior = 0.0;
						assert !SloppyMath.isVeryDangerous(edgePosterior);
						res[fromSubstate][toSubstate] += edgePosterior;
					}
				}
//			}
//		}
		return res;
	}
	
	public void verifyLikelihood() {
		double z = getUnscaledLikelihood();
		double z_scale = alphasScale[T - 1];
		for (int t = T-1; t >= 0; --t) {
			double sum = 0.0;
			for (int n = 0; n < N; ++n) {
			
				for (int substate = 0; substate < hmm.numSubstatesPerState[phoneSequence[n]]; ++substate) {

					double alpha_s = alphas[t][n][substate];
					
					double alpha_scale = alphasScale[t];
					double beta_s = betas[t][n][substate];
					
					double beta_scale = betasScale[t];
					double unscaled_posterior = alpha_s / z * beta_s;
					double posterior_scale = alpha_scale + beta_scale - z_scale;
					double exp_scale = getScaleFactor(posterior_scale);

					double x = unscaled_posterior * exp_scale;
//					if (SloppyMath.isVeryDangerous(x))
//					{
//						
//					}
					sum += x;

				}
			}
			if (Math.abs(sum - 1.0) > 0.01)
			{	
				
				LogInfo.error("Sum for t=" + t +"::" + seq +  ":" + sum);
				break;
				
			}
			//System.out.println("Sum for t=" + t + ":" + sum);
		}
	}
	
	public int[]  getBestAlignment(int[] phoneObjectSequences, double[][] acousticObservationSequences, int seq)
	{
		
		AligningViterbiChart chart = new AligningViterbiChart(hmm);
		
		
			init(phoneObjectSequences, acousticObservationSequences, seq);
			chart.init();
		
		return chart.calc();
	}
	
	private class AligningViterbiChart {
		
		// TODO this should be merging with the ViterbiChart implementation

		private double[][][] deltas; // alphas[t][q_t][q_substate] =

		// p(y_0,...,p_t,q_t)

		

		/** Backpointers */
		private int[][][] phiPhone;

		private int[][][] phiSubstate;

		
		
		

		
		private final double[][][][] logTransitions;
		private final Gaussian[][] logEmissions;
		
		

		private SubphoneHMM hmm;
		
		public AligningViterbiChart(SubphoneHMM hmm) {
			this.hmm = hmm;
			this.logEmissions = hmm.b;
			this.logTransitions = new double[hmm.maxNumSubstates][hmm.numPhones][hmm.numPhones][hmm.maxNumSubstates];

			for (int fromPhone = 0; fromPhone < hmm.numPhones; ++fromPhone) {
				for (int j = 0; j < hmm.numSubstatesPerState[fromPhone]; ++j) {
					for (int toPhone = 0; toPhone < hmm.numPhones; ++toPhone) {
						for (int k = 0; k < hmm.numSubstatesPerState[toPhone]; ++k) {
							logTransitions[k][toPhone][fromPhone][j] = Math.log(hmm.a[k][toPhone][fromPhone][j]*hmm.c[toPhone][fromPhone][j]); 
						}
					}
				}
			}

			
		
		}

		public void init() {
			
			
				deltas = new double[T][N][hmm.maxNumSubstates];
				phiPhone = new int[T][N][hmm.maxNumSubstates];
				phiSubstate = new int[T][N][hmm.maxNumSubstates];

				

				
			

				
				ArrayUtil.fill(deltas, T, Double.NEGATIVE_INFINITY);
				ArrayUtil.fill(phiPhone, T, -1);
				ArrayUtil.fill(phiSubstate, T, -1);


			


		}

		/**
		 * 
		 */
		public int[] calc() {

			deltas[0][0][0] = 0.0;//1.0

			// induction
			for (int t = 1; t < T; ++t) {
//				double max = Double.NEGATIVE_INFINITY;

				for (int toN : allowedIndicesAtTime(t)) {
					if (toN == 0)
						continue;
//					if (pruneAlpha(toN,t))
//					{
//						continue;
//					}
					int toPhone = phoneSequence[toN];
//					if (t==1 && toPhone!=2) continue;
					for (int toSubstate = 0; toSubstate < hmm.numSubstatesPerState[toPhone]; ++toSubstate) {

						double maxPathProb = Double.NEGATIVE_INFINITY;
						int maxPathPhone = -1;
						int maxPathSubstate = -1;
						for (int fromN : allowedPrevIndicesAtTime(t, toN)) {
							int fromPhone = phoneSequence[fromN];

							for (int fromSubstate = 0; fromSubstate < hmm.numSubstatesPerState[fromPhone]; ++fromSubstate) {
//								double leaveStateProb = hmm.c[toPhone][fromPhone][fromSubstate];
//								double currPathProb = deltas[t - 1][fromPhone][fromSubstate]
//										* hmm.a[toSubstate][toPhone][fromPhone][fromSubstate]
//										* leaveStateProb;

								double prevDelta = deltas[t - 1][fromN][fromSubstate];
								if (prevDelta==Double.NEGATIVE_INFINITY) continue;
								double currPathProb = prevDelta	+ logTransitions[toSubstate][toPhone][fromPhone][fromSubstate];

								if (currPathProb > maxPathProb) {
									maxPathProb = currPathProb;
									maxPathPhone = fromN;
									maxPathSubstate = fromSubstate;
								}
							}
						}
						double delta = maxPathProb;
						//assert delta > Double.NEGATIVE_INFINITY;
						if (toPhone!=1 && t<T-1 && delta>Double.NEGATIVE_INFINITY)
//							delta *=  hmm.b[toPhone][toSubstate].evalPdf(observations[t]);
							delta +=  logEmissions[toPhone][toSubstate].evalLogPdf(observations[t]);
					//	assert delta > Double.NEGATIVE_INFINITY;
						deltas[t][toN][toSubstate] = delta;
//						if (delta > max)
//							max = delta;
				//		assert maxPathPhone >=0;
				//		assert maxPathSubstate >= 0;
						phiPhone[t][toN][toSubstate] = maxPathPhone;
						phiSubstate[t][toN][toSubstate] = maxPathSubstate;
					}
				}

			}


			int[] viterbiSequence = new int[T];

			int prevPhoneIndex = N-1;
			int prevSubstate = 0;
			viterbiSequence[T-1] = phoneSequence[prevPhoneIndex];

			// retrace backpointers

			for (int t = T - 1; t > 0; --t) {
				int tmp = prevPhoneIndex;
				prevPhoneIndex = phiPhone[t][tmp][prevSubstate];
				prevSubstate = phiSubstate[t][tmp][prevSubstate];
				viterbiSequence[t-1] = phoneSequence[prevPhoneIndex];

			}
			
			return viterbiSequence;

		
		}
	}

//	@Override
//	public void calc() {
//		// TODO Auto-generated method stub
//		super.calc();
//		
//	}
	
	
	
	
}