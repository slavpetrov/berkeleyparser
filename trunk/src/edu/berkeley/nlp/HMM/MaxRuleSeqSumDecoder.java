/**
 * 
 */
package edu.berkeley.nlp.HMM;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import edu.berkeley.nlp.math.SloppyMath;
import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.MapFactory;
import edu.berkeley.nlp.util.PriorityQueue;

/**
 * @author petrov
 *
 */
public class MaxRuleSeqSumDecoder implements Decoder{

	private SubphoneHMM hmm;
//	private int[][] maxChild; // [t][toPhone] -> fromPhone: stores the best child (at time t-1) of toPhone (at time t)
//	private double[][] maxScore;
	
	private double insertionPenalty;
//	private double emissionAttenuation;
	
	
	public MaxRuleSeqSumDecoder(SubphoneHMM hmm, double insertionPenalty, double emissionAttenuation)
	{
		this.hmm = hmm;
		this.insertionPenalty = Math.exp(-insertionPenalty);
//		this.emissionAttenuation = emissionAttenuation;
	}
	
	private static class ScoredSeq extends LinkedList<Integer> 
	{

		
		/**
		 * @param c
		 */
		public ScoredSeq(Collection<? extends Integer> c) {
			super(c);
			// TODO Auto-generated constructor stub
		}

		/**
		 * 
		 */
		private static final long serialVersionUID = -8515482406974203578L;

		/**
		 * 
		 */
		public ScoredSeq() {
			super();
			// TODO Auto-generated constructor stub
		}
		
	


}
	private static class MyPriorityQueue extends PriorityQueue<ScoredSeq>
	{
		 /**
		 * 
		 */
		public MyPriorityQueue() {
			super();
			// TODO Auto-generated constructor stub
		}

		/**
		 * @param capacity
		 */
		public MyPriorityQueue(int capacity) {
			super(capacity);
			// TODO Auto-generated constructor stub
		}

		/**
		 * 
		 */
		private static final long serialVersionUID = -7257911764771919395L;

		/**
	   * Returns a counter whose keys are the elements in this priority queue, and
	   * whose counts are the priorities in this queue.  In the event there are
	   * multiple instances of the same element in the queue, the counter's count
	   * will be the sum of the instances' priorities.
	   *
	   * @return
	   */
	  public SCounter asCounter(MapFactory<ScoredSeq,Double> factory) {
	    PriorityQueue<ScoredSeq> pq = clone();
	    SCounter counter = new SCounter(factory);
	    while (pq.hasNext()) {
	      double priority = pq.getPriority();
	      ScoredSeq element = pq.next();
	      counter.incrementCount(element, priority);
	    }
	    return counter;
	  }
	}
	
	private static class SCounter extends Counter<ScoredSeq>
	{

		/**
		 * 
		 */
		public SCounter() {
			super();
			// TODO Auto-generated constructor stub
		}

		/**
		 * @param collection
		 */
		public SCounter(Collection<? extends ScoredSeq> collection) {
			super(collection);
			// TODO Auto-generated constructor stub
		}

		/**
		 * @param counter
		 */
		public SCounter(Counter<? extends ScoredSeq> counter) {
			super(counter);
			// TODO Auto-generated constructor stub
		}

		/**
		 * @param mf
		 */
		public SCounter(MapFactory<ScoredSeq, Double> mf) {
			super(mf);
			// TODO Auto-generated constructor stub
		}
		
	}
	

	
	
	public List<List<Phone>> decode(List<double[][]> obsSequences) {
		System.out.println("Starting max-rule seq sum decoding");
		

		PosteriorProbChart probChart = new PosteriorProbChart(hmm);
		List<List<Phone>> retVal = new ArrayList<List<Phone>>();
		final int beamWidth = 5;
		double[] ruleScores = new double[hmm.numPhones];
		//MapFactory<ScoredSeq,Double> mapFactory = new MapFactory.TreeMapFactory<ScoredSeq, Double>();
	MapFactory<ScoredSeq,Double> mapFactory = new MapFactory.HashMapFactory<ScoredSeq, Double>();
	//	PriorityQueue<LinkedList<Integer>> prev = new PriorityQueue<LinkedList<Integer>>();
		for (double[][] o : obsSequences) {
			System.out.print(".");
			final int T = o.length;
			

			probChart.init(o);
			probChart.calc();
		
			double z = probChart.getUnscaledLikelihood();
			double z_scale = probChart.alphasScale[T - 1];
			ScoredSeq start = new ScoredSeq();
			start.add(Corpus.START_STATE);
			MyPriorityQueue[] prev = new MyPriorityQueue[T];
			prev[0] = new MyPriorityQueue();
			
			prev[0].add(start, -1.0);
			

//			for (int t = 0; t < T-1; ++t) {
				for (int t = 0; t < 10; ++t) {
				//System.out.println(t);
				
				SCounter[] counters = new SCounter[prev.length];
				for (int i = 0; i < counters.length; ++i)
					{
					if (prev[i] != null)
					counters[i] = prev[i].asCounter(mapFactory);
					}
				MyPriorityQueue[] curr = new MyPriorityQueue[T];
				for (int toPhone : probChart.allowedPhonesAtTime(t+1)) {
					
					double normalizer = 0;
					for (int toSubstate = 0; toSubstate < hmm.numSubstatesPerState[toPhone]; ++toSubstate) {
						normalizer += probChart.getGamma(toPhone, toSubstate, t+1);
					}
					if (SloppyMath.isDangerous(normalizer)) normalizer = 1;
					
					Arrays.fill(ruleScores,-1.0);
					for (int len = 0; len < counters.length; ++len)
					{
						if (counters[len] == null) continue;
					
					for (ScoredSeq scoredSeq : counters[len].keySet() ) {
						//ScoredSeq scoredSeq = iter.next();
						int fromPhone = scoredSeq.getLast();
						double ruleScore = ruleScores[fromPhone];
						if (ruleScore < 0)
						{
							ruleScore = 0;
						for (int fromSubstate = 0; fromSubstate < hmm.numSubstatesPerState[fromPhone]; ++fromSubstate) {
							double alpha_s = probChart.alphas[t][fromPhone][fromSubstate];
							double alpha_scale = probChart.alphasScale[t];
							for (int toSubstate = 0; toSubstate < hmm.numSubstatesPerState[toPhone]; ++toSubstate) {
								double beta_s = probChart.betas[t+1][toPhone][toSubstate];
								double beta_scale = probChart.betasScale[t+1];
								double rule_s = hmm.c[toPhone][fromPhone][fromSubstate]*hmm.a[toSubstate][toPhone][fromPhone][fromSubstate];
								double obsProb = (t == T - 2) ? 1.0 : probChart.obsLikelihoods[t+1][toPhone][toSubstate];
								double unscaled_posterior = alpha_s / z * beta_s * rule_s * obsProb;
								double posterior_scale = alpha_scale + beta_scale - z_scale;
								double exp_scale = probChart.getScaleFactor(posterior_scale);
							
								double gamma =  unscaled_posterior * exp_scale;
								if (Double.isInfinite(exp_scale)) gamma = 0;
								
								ruleScore += gamma;

							}
						}
					
						
						ruleScore /= normalizer;
						ruleScores[fromPhone] = ruleScore;
						}
						//System.out.println(ruleScore + "::" + t + "::" + fromPhone + "||" + toPhone + "&&" + normalizer);
						double seqScore = ruleScore * counters[len].getCount(scoredSeq);
						
						if (seqScore == 0.0) continue;
						if (fromPhone!=toPhone) ruleScore *= insertionPenalty;
						
						ScoredSeq toAdd = null;
						double priority = Double.NEGATIVE_INFINITY;
						if (toPhone == fromPhone)
						{
							
							toAdd = scoredSeq;
							priority = seqScore;
						}
						else
						{
							ScoredSeq newSeq = new ScoredSeq(scoredSeq);
							newSeq.addLast(toPhone);
							
								toAdd = newSeq;
								double prevCount = 0.0;
								if (counters[newSeq.size()] != null)
									prevCount = counters[newSeq.size()].getCount(newSeq);
								priority = prevCount + seqScore;
						}
						System.out.println("Adding " + toAdd + " score " + priority + " length " + toAdd.size());
						if (curr[toAdd.size()] == null)
						{
							curr[toAdd.size()] = new MyPriorityQueue(beamWidth);
						}
						if (curr[toAdd.size()].size() == beamWidth) {
							double currWorst = curr[toAdd.size()].getPriority();
							if (priority <= currWorst) {
								curr[toAdd.size()].next();
								curr[toAdd.size()].add(toAdd, priority);
							}
						} else {
							curr[toAdd.size()].add(toAdd, priority);
						}
						
						assert curr[toAdd.size()].size() <= beamWidth;
						
						
					}
					}
				}
				prev = curr;
			}
			
//			List<Phone> currPhones = new ArrayList<Phone>();
//			currPhones.add(Corpus.END_PHONE);
//			int lastPhoneIndex = 1;
//			for (int t = T-1; t >= 0; t--) {
//				lastPhoneIndex = maxChild[t][lastPhoneIndex];
//				currPhones.add(0,hmm.phoneIndexer.get(lastPhoneIndex));
//			}
		List<Phone> currPhones = new ArrayList<Phone>();
			ScoredSeq best = null;
			double bestScore = Double.POSITIVE_INFINITY;
			for (int len = 0; len < prev.length; ++len)
			{
				
			MyPriorityQueue pq = prev[len];
			if (pq == null) continue;
			 
			for (Iterator<ScoredSeq> it = pq; pq.hasNext();)
			{
				double priority = pq.getPriority();
				ScoredSeq seq = it.next();
				
//				List<Phone> x = new ArrayList<Phone>();
//				
//				for (int i = 0; i < seq.size(); ++i)
//				{
//					x.add(hmm.phoneIndexer.get(seq.get(i)));
//				}
//				AccuracyCalc.collapseAndPrintSeq(x, x, (PhoneIndexer)hmm.phoneIndexer);
				if (priority < bestScore)
				{
					best = seq;
					bestScore = priority;
				}
				
			}
			}
			for (int phone : best)
			{
				currPhones.add(hmm.phoneIndexer.get(phone));
			}
			retVal.add(currPhones);
		}
			
		return retVal;
	}
	
}
