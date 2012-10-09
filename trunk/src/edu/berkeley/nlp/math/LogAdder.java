package edu.berkeley.nlp.math;

public class LogAdder {
	boolean inited = false;
	double sum;
	boolean superSloppy;

	public LogAdder(boolean superSloppy) {
		super();
		this.superSloppy = superSloppy;
	}

	public LogAdder() {
		this(false);
	}

	public void logAdd(double logProb) {
		if (inited) {
			sum = sloppyLogAdd(sum, logProb);
			// sum += logProb;
		} else {
			inited = true;
			sum = logProb;
		}
	}

	public double sloppyLogAdd(double lx, double ly) {
		double max, negDiff;
		if (lx > ly) {
			max = lx;
			negDiff = ly - lx;
		} else {
			max = ly;
			negDiff = lx - ly;
		}
		if (max == Double.NEGATIVE_INFINITY) {
			return max;
		} else if (negDiff < -SloppyMath.LOGTOLERANCE) {
			return max;
		} else {
			if (superSloppy) {
				return max
						+ SloppyMath.approxLog(1.0 + SloppyMath
								.approxExp(negDiff));
			}
			return max + Math.log(1.0 + Math.exp(negDiff));
		}
	}

	public double getSum() {
		return inited ? sum : Double.NEGATIVE_INFINITY;
	}
}
