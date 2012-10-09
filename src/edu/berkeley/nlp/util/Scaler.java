package edu.berkeley.nlp.util;

import java.util.Arrays;

public class Scaler {
	final int MAX_LEN;
	int[] scales;
	double[] unscaled;
	int index;

	final double SCALE;
	double sumUnscaled;
	int sumScale;

	public Scaler(double scale, int maxLen) {
		this.SCALE = scale;
		this.MAX_LEN = maxLen;
		scales = new int[MAX_LEN];
		unscaled = new double[MAX_LEN];
		clear();
	}

	public Scaler() {
		this(Math.exp(200), 400);
		clear();
	}

	public void add(double score, int scale) {
		assert score >= 0.0 : "Invald score: " + score;
		unscaled[index] = score;
		scales[index] = scale;
		index++;
	}

	public void clear() {
		index = 0;
		Arrays.fill(unscaled, 0.0);
		Arrays.fill(scales, 0);
	}

	private double getScale(int logScale) {
		if (logScale == 0.0)
			return 1.0;
		if (logScale == 1.0)
			return SCALE;
		if (logScale == 2.0)
			return SCALE * SCALE;
		if (logScale == 3.0)
			return SCALE * SCALE * SCALE;
		if (logScale == -1.0)
			return 1.0 / SCALE;
		if (logScale == -2.0)
			return 1.0 / SCALE / SCALE;
		if (logScale == -3.0)
			return 1.0 / SCALE / SCALE / SCALE;
		return Math.pow(SCALE, logScale);
	}

	public void scale() {
		sumScale = Integer.MIN_VALUE;
		for (int scale : scales)
			sumScale = Math.max(sumScale, scale);
		assert sumScale > Integer.MIN_VALUE;
		sumUnscaled = 0.0;
		for (int i = 0; i < index; ++i) {
			double scale = getScale(scales[i] - sumScale);
			sumUnscaled += scale * unscaled[i];
		}
		while (true) {
			if (sumUnscaled == 0.0) {
				break;
			}
			if (sumUnscaled > SCALE) {
				sumUnscaled /= SCALE;
				sumScale++;
				continue;
			}
			if (sumUnscaled < 1.0 / SCALE) {
				sumUnscaled *= SCALE;
				sumScale--;
				continue;
			}
			break;
		}
	}

	public double getScaleFactor() {
		return SCALE;
	}

	public int getLogScale() {
		return (int) Math.log(SCALE);
	}

	public double getSumUnscaled() {
		return sumUnscaled;
	}

	public int getSumScale() {
		return sumScale;
	}

	public double getSumScaled() {
		return getScale(getSumScale()) * getSumUnscaled();
	}

	public double getScaled(double unscaled, int scale) {
		return unscaled * getScale(scale);
	}
}