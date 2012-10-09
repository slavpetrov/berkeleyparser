package edu.berkeley.nlp.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Finds the minimum k of a set of observations
 * 
 * @author denero
 */
public class Beam<T> {

	private int size;
	private double kstat = Double.POSITIVE_INFINITY; // kth order statistic
	private List<T> kbest;
	private double[] kbestValues;

	public Beam(int size) {
		this.size = size;
		kbest = new ArrayList<T>(size + 1);
		kbestValues = new double[size];
		Arrays.fill(kbestValues, Double.POSITIVE_INFINITY);
	}

	public void observe(T t, double val) {
		if (val < kstat || (val <= kstat && kbest.size() < size)) {
			// something falls off the beam
			if (kbest.size() == size)
				kbest.remove(kbest.size() - 1);
			int index = Arrays.binarySearch(kbestValues, val);
			int pos = (index < 0) ? -1 * index - 1 : index;
			kbest.add(pos, t);
			kbestValues[kbest.size() - 1] = val;
			Arrays.sort(kbestValues); // This step might be a little slow
			kstat = kbestValues[size - 1];
		}
	}

	public int getSize() {
		return size;
	}

	public double beamCutoff() {
		return kstat;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public List<T> contents() {
		return kbest;
	}

	public double[] getKbestValues() {
		return kbestValues;
	}

	public int size() {
		return kbest.size();
	}

	public T argMin() {
		if (size() == 0)
			return null;
		return kbest.get(0);
	}

	public static void main(String[] args) {
		Beam<String> bs = new Beam<String>(3);
		bs.observe("what1", 1);
		bs.observe("what2", 4);
		bs.observe("what3", 0);
		bs.observe("what4", 2);
		bs.observe("what5", 3);
		bs.observe("what6", 1);
		System.out.println(bs.contents());

		int n = 10000;
		Beam<Double> bsd = new Beam<Double>(n / 10);
		List<Double> l = new ArrayList<Double>(n);
		Random random = new Random();
		for (int i = 0; i < n; i++) {
			double r = random.nextDouble();
			bsd.observe(r, r);
			l.add(r);
		}
		Collections.sort(l);
		for (int i = 0; i < n; i++) {
			if (i < bsd.kbest.size()) {
				System.out.println("Same?:\t" + bsd.kbest.get(i) + "\t"
						+ l.get(i));
			}
		}

	}

}
