package edu.berkeley.nlp.math;

import edu.berkeley.nlp.mapper.AsynchronousMapper;
import edu.berkeley.nlp.mapper.SimpleMapper;
import edu.berkeley.nlp.util.CallbackFunction;
import edu.berkeley.nlp.util.CollectionUtils;
import edu.berkeley.nlp.util.Logger;
//import edu.berkeley.nlp.util.optionparser.GlobalOptionParser;
import edu.berkeley.nlp.util.Option;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * User: aria42 Date: Mar 10, 2009
 */
public class StochasticObjectiveOptimizer<I> {

	Collection<I> items;
	List<? extends ObjectiveItemDifferentiableFunction<I>> itemFns;
	Regularizer regularizer;
	double initAlpha = 0.5;
	double upAlphaMult = 1.1;
	double downAlphaMult = 0.5;
	Object weightLock = new Object();
	double[] weights;
	double alpha;
	CallbackFunction iterDoneCallback;
	boolean printProgress = true;
	Random rand;

	@Option
	public int randSeed = 0;
	@Option
	public boolean doAveraging = false;
	@Option
	public boolean shuffleData = false;

	double[] sumWeightVector;
	int numUpdates;

	public StochasticObjectiveOptimizer(double initAlpha, double upAlphaMult,
			double downAlphaMult) {
		this(initAlpha, upAlphaMult, downAlphaMult, true);
	}

	public StochasticObjectiveOptimizer(double initAlpha, double upAlphaMult,
			double downAlphaMult, boolean printProgress) {
		this.initAlpha = initAlpha;
		this.upAlphaMult = upAlphaMult;
		this.downAlphaMult = downAlphaMult;
		this.printProgress = printProgress;
		// GlobalOptionParser.fillOptions(this);
		rand = new Random(randSeed);
	}

	public void setIterationCallback(CallbackFunction iterDoneCallback) {
		this.iterDoneCallback = iterDoneCallback;
	}

	// Do a pass through the data of SGD
	class GradMapper implements SimpleMapper<I> {
		double val = 0.0;
		ObjectiveItemDifferentiableFunction<I> itemFn;

		GradMapper(ObjectiveItemDifferentiableFunction<I> itemFn) {
			this.itemFn = itemFn;
		}

		public void map(I elem) {
			double[] localWeights;
			synchronized (weightLock) {
				localWeights = DoubleArrays.clone(weights);
			}
			double[] localGrad = new double[dimension()];
			itemFn.setWeights(localWeights);
			val += itemFn.update(elem, localGrad);
			if (regularizer != null) {
				val += regularizer.update(localWeights, localGrad,
						1.0 / items.size());
			}
			synchronized (weightLock) {
				DoubleArrays.addInPlace(weights, localGrad, -alpha);
				DoubleArrays.addInPlace(sumWeightVector, weights);
				numUpdates++;
			}
		}
	}

	// Compute the function value for a fixed set of parameters
	class ValMapper implements SimpleMapper<I> {
		double val = 0.0;
		ObjectiveItemDifferentiableFunction<I> itemFn;

		ValMapper(ObjectiveItemDifferentiableFunction<I> itemFn) {
			this.itemFn = itemFn;
		}

		public void map(I elem) {
			val += itemFn.update(elem, null);
			val += regularizer.val(weights, 1.0 / items.size());
		}
	}

	private double doIter() {
		List<GradMapper> gradMappers = new ArrayList<GradMapper>();
		for (ObjectiveItemDifferentiableFunction<I> itemFn : itemFns) {
			gradMappers.add(new GradMapper(itemFn));
		}
		List<I> shuffledItems = shuffleData ? CollectionUtils.shuffle(items,
				rand) : new ArrayList<I>(items);
		AsynchronousMapper.doMapping(shuffledItems, gradMappers);

		// List<ValMapper> valMappers = new ArrayList<ValMapper>();
		// for (ObjectiveItemDifferentiableFunction<I> itemFn : itemFns) {
		// valMappers.add(new ValMapper(itemFn));
		// }
		// AsynchronousMapper.doMapping(items,valMappers);

		double val = 0.0;
		for (GradMapper mapper : gradMappers) {
			val += mapper.val;
		}
		return val;
	}

	public double[] minimize(double[] initWeights, int numIters,
			Collection<I> items,
			List<? extends ObjectiveItemDifferentiableFunction<I>> itemFns,
			Regularizer regularizer) {
		this.items = items;
		this.itemFns = itemFns;
		this.numUpdates = 0;
		this.regularizer = regularizer;
		alpha = initAlpha;
		weights = DoubleArrays.clone(initWeights);
		sumWeightVector = DoubleArrays.constantArray(0.0, weights.length);
		double lastVal = Double.POSITIVE_INFINITY;
		for (int iter = 0; iter < numIters; iter++) {
			double val = doIter();
			double alphaMult = val < lastVal ? upAlphaMult : downAlphaMult;
			alpha *= alphaMult;
			lastVal = val;
			if (printProgress) {
				Logger.logs(
						"[StochasticObjectiveOptimizer] Ended Iteration %d with value %.5f",
						iter + 1, val);
				Logger.logs(
						"[StochasticObjectiveOptimizer] New Alpha: %.5f (scaled by %.5f)",
						alpha, alphaMult);
			}
			if (iterDoneCallback != null) {
				iterDoneCallback.callback(iter, doAveraging ? avgWeightVector()
						: weights, val, alpha);
			}

			if (alpha < initAlpha * Math.pow(10.0, -2.0)) {
				Logger.logs(
						"[StochasticObjectiveOptimizer] alpha %.5f below tolerance %.5f, saying converged",
						alpha, initAlpha * Math.pow(10.0, -2.0));
				break;
			}
		}
		return doAveraging ? avgWeightVector() : weights;
	}

	private double[] avgWeightVector() {
		double[] avgWeights = DoubleArrays.clone(sumWeightVector);
		DoubleArrays.scale(avgWeights, 1.0 / numUpdates);
		return avgWeights;
	}

	public int dimension() {
		return itemFns.get(0).dimension();
	}
}
