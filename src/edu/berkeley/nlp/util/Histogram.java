package edu.berkeley.nlp.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * A simple histogram class. It can be used to accumulate a histogram and
 * calculate statistical information about it.
 * 
 * @author Simon George
 * @version 1.0 31 Aug 2001
 * 
 *          Extended by John DeNero
 */
public class Histogram {

	private static final long serialVersionUID = 1L;
	private static final int DEFAULT_NUM_BINS = 10;
	private static int currentNumBins = DEFAULT_NUM_BINS;
	private boolean binsHaveBeenSet;
	private List<Double> data;

	public Histogram() {
		this("Histogram");
	}

	public Histogram(String title) {
		this.title = title;
		data = new ArrayList<Double>();
	}

	public static <T> Histogram histogramOfCounts(Counter<T> counter) {
		Histogram h = new Histogram();
		for (T o : counter.keySet()) {
			h.add(counter.getCount(o));
		}
		return h;
	}

	public static <T> Histogram histogramOfValues(Counter<Double> counter) {
		Histogram h = new Histogram();
		for (Double d : counter.keySet()) {
			double count = counter.getCount(d);
			for (int i = 0; i < count; i++) {
				h.add(d);
			}
		}
		return h;
	}

	public void add(double value) {
		data.add(value);
	}

	/**
	 * Enter data into the histogram. The fill method takes the given value,
	 * works out which bin this corresponds to, and increments this bin by one.
	 * 
	 * @param x
	 *            is the value to add in to the histogram
	 */
	private void fill(double x) {
		// use findBin method to work out which bin x falls in
		BinInfo bin = findBin(x);
		// check the result of findBin in case it was an overflow or underflow
		if (bin.isUnderflow) {
			m_underflow++;
		}
		if (bin.isOverflow) {
			m_overflow++;
		}
		if (bin.isInRange) {
			m_hist[bin.index]++;
		}
		// count the number of entries made by the fill method
		m_entries++;
	}

	private class BinInfo {
		public int index;
		public boolean isUnderflow;
		public boolean isOverflow;
		public boolean isInRange;
	}

	/**
	 * Private internal utility method to figure out which bin of the histogram
	 * a number falls in.
	 * 
	 * @return info on which bin x falls in.
	 */
	private BinInfo findBin(double x) {
		BinInfo bin = new BinInfo();
		bin.isInRange = false;
		bin.isUnderflow = false;
		bin.isOverflow = false;
		// first check if x is outside the range of the normal histogram bins
		if (x < minValue) {
			bin.isUnderflow = true;
		} else if (x > maxValue) {
			bin.isOverflow = true;
		} else {
			bin.isInRange = true;
			for (int i = 0; i < numBins; i++) {
				if (x < binUpperBounds[i]) {
					bin.index = i;
					break;
				}
			}
			if (x == maxValue) {
				bin.index = numBins - 1;
			}
		}
		return bin;
	}

	/**
	 * Save the histogram data to a file. The file format is very simple,
	 * human-readable text so it can be imported into Excel or cut & pasted into
	 * other applications.
	 * 
	 * @param fileName
	 *            name of the file to write the histogram to. Note this must be
	 *            valid for your operating system, e.g. a unix filename might
	 *            not work under windows
	 * @exception IOException
	 *                if file cannot be opened or written to.
	 */

	public void write(PrintWriter outfile) {
		setBuckets();
		fillHistogram();
		writeToPrintWriter(outfile);
	}

	private void writeToPrintWriter(PrintWriter outfile) {
		outfile.println(title);
		outfile.println("Bins:\t" + numBins);
		outfile.println("Min:\t" + minValue);
		outfile.println("Max:\t" + maxValue);
		outfile.println("Entries:\t" + m_entries);
		if (m_overflow > 0) {
			outfile.println("Over:\t" + m_overflow);
		}
		if (m_underflow > 0) {
			outfile.println("Under:\t" + m_underflow);
		}
		for (int i = 0; i < numBins; i++) {
			String l = String.format("%.2f", binLowerBounds[i]);
			String u = String.format("%.2f", binUpperBounds[i]);
			outfile.print("[" + l + ", " + u);
			if (numBins - 1 != i) {
				outfile.print(")");
			} else {
				outfile.print("]");
			}
			outfile.println(":\t" + m_hist[i]);
		}
		outfile.close();
	}

	@Override
	public String toString() {
		setBuckets();
		fillHistogram();
		StringWriter s = new StringWriter();
		PrintWriter pw = new PrintWriter(new BufferedWriter(s));
		writeToPrintWriter(pw);
		return s.getBuffer().toString();
	}

	private void fillHistogram() {
		m_entries = 0;
		m_overflow = 0;
		m_underflow = 0;
		m_hist = new int[numBins];
		for (double d : data) {
			fill(d);
		}
	}

	private void setBuckets() {
		setBuckets(currentNumBins);
	}

	private void setBuckets(int numBins) {
		if (!binsHaveBeenSet) {
			setBuckets(numBins, getMin(), getMax());
			binsHaveBeenSet = false;
		}
	}

	public void setBuckets(int numBins, double min, double max) {
		double[] lowers = new double[numBins];
		double step = (max - min) / (numBins);
		for (int i = 0; i < numBins; i++) {
			lowers[i] = min + i * step;
		}
		setBuckets(lowers, min, max);
	}

	private void setBuckets(double[] lowers) {
		setBuckets(lowers, lowers[0], Double.POSITIVE_INFINITY);
	}

	public void setBuckets(double[] binLowerBounds, double min, double max) {
		numBins = binLowerBounds.length;
		this.binLowerBounds = binLowerBounds;
		assert (min == binLowerBounds[0]);
		minValue = min;
		maxValue = max;
		updateBinUpperBounds();
		binsHaveBeenSet = true;
	}

	private void updateBinUpperBounds() {
		binUpperBounds = new double[numBins];
		for (int i = 0; i < numBins - 1; i++) {
			binUpperBounds[i] = binLowerBounds[i + 1];
		}
		binUpperBounds[numBins - 1] = maxValue;
	}

	public double getMax() {
		double max = Double.NEGATIVE_INFINITY;
		for (double d : data) {
			max = Math.max(max, d);
		}
		return max;
	}

	public double getMin() {
		double min = Double.POSITIVE_INFINITY;
		for (double d : data) {
			min = Math.min(min, d);
		}
		return min;
	}

	// private data used internally by this class.
	private int[] m_hist;
	private String title;
	private double minValue;
	private double maxValue;
	private int numBins;
	private double[] binLowerBounds, binUpperBounds;
	private int m_entries;
	private double m_overflow;
	private double m_underflow;

	public static void main(String[] args) {
		Histogram h = new Histogram();
		for (double i = 1; i < 43400; i *= 1.2) {
			h.add(i);
		}
		System.out.println(h);
		double[] lowers = new double[3];
		lowers[0] = 0;
		lowers[1] = 100;
		lowers[2] = 1000;
		h.setBuckets(lowers);
		System.out.println(h);
		h.setLogBuckets(10);
		System.out.println(h);
	}

	public void setLogBuckets(int numBuckets) {
		setLogBuckets(numBuckets, getMin(), getMax());
	}

	public void setLogBuckets(int numBuckets, double min, double max) {
		double step = Math.pow(max - min + 1, 1.0 / numBuckets);
		double[] lowers = new double[numBuckets];
		for (int i = 0; i < numBuckets; i++) {
			lowers[i] = (min - 1) + Math.pow(step, i);
		}
		setBuckets(lowers, min, max);
	}

	public void setTitle(String t) {
		title = t;
	}

	public static int getNumBins() {
		return currentNumBins;
	}

	public static void setNumBins(int currentNumBins) {
		Histogram.currentNumBins = currentNumBins;
	}

}