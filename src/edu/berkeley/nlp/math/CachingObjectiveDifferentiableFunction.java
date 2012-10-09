package edu.berkeley.nlp.math;

import edu.berkeley.nlp.mapper.AsynchronousMapper;
import edu.berkeley.nlp.mapper.SimpleMapper;
import edu.berkeley.nlp.util.Pair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * User: aria42 Date: Mar 10, 2009
 */
public class CachingObjectiveDifferentiableFunction<I> extends
		CachingDifferentiableFunction {

	private List<? extends ObjectiveItemDifferentiableFunction<I>> itemFns;
	private Regularizer regularizer;
	private Collection<I> items;

	public CachingObjectiveDifferentiableFunction(Collection<I> items,
			List<? extends ObjectiveItemDifferentiableFunction<I>> itemFns,
			Regularizer regularizer) {
		this.itemFns = itemFns;
		this.regularizer = regularizer;
		this.items = items;
	}

	public CachingObjectiveDifferentiableFunction(Collection<I> items,
			ObjectiveItemDifferentiableFunction<I> itemFn,
			Regularizer regularizer) {
		this(items, Collections.singletonList(itemFn), regularizer);
	}

	private class Mapper implements SimpleMapper<I> {
		ObjectiveItemDifferentiableFunction<I> itemFn;
		double objVal;
		double[] localGrad;

		Mapper(ObjectiveItemDifferentiableFunction<I> itemFn) {
			this.itemFn = itemFn;
			this.objVal = 0.0;
			this.localGrad = new double[itemFn.dimension()];
		}

		public void map(I elem) {
			objVal += itemFn.update(elem, localGrad);
		}
	}

	private List<Mapper> getMappers() {
		List<Mapper> mappers = new ArrayList<Mapper>();
		for (ObjectiveItemDifferentiableFunction<I> itemFn : itemFns) {
			mappers.add(new Mapper(itemFn));
		}
		return mappers;
	}

	protected Pair<Double, double[]> calculate(double[] x) {
		for (ObjectiveItemDifferentiableFunction<I> itemFn : itemFns) {
			itemFn.setWeights(x);
		}
		List<Mapper> mappers = getMappers();
		AsynchronousMapper.doMapping(items, mappers);
		double objVal = 0.0;
		double[] grad = new double[dimension()];
		for (Mapper mapper : mappers) {
			objVal += mapper.objVal;
			DoubleArrays.addInPlace(grad, mapper.localGrad);
		}
		if (regularizer != null) {
			objVal += regularizer.update(x, grad, 1.0);
		}
		return Pair.newPair(objVal, grad);
	}

	public int dimension() {
		return itemFns.get(0).dimension();
	}
}
