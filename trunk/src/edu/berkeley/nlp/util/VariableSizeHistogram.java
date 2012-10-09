package edu.berkeley.nlp.util;

import java.text.NumberFormat;
import java.util.SortedSet;
import java.util.Map.Entry;

public class VariableSizeHistogram {

	private SortedSet<Double> startPoints;

	private Counter<Number> data;

	private boolean intKeys;

	public VariableSizeHistogram(SortedSet<Double> startPoints) {
		this(startPoints, true);
	}

	public VariableSizeHistogram(SortedSet<Double> startPoints, boolean intKeys) {

		this.startPoints = startPoints;
		this.intKeys = intKeys;

		data = new Counter<Number>();
	}

	public void add(double key, double value) {
		SortedSet<Double> tailSet = startPoints.tailSet(key);
		Double startPoint = tailSet.isEmpty() ? startPoints.last() : tailSet
				.first();
		data.incrementCount(startPoint, value);
	}

	public void addAll(Counter<? extends Number> counter) {
		for (Entry<? extends Number, Double> entry : counter.entrySet()) {
			add(entry.getKey().doubleValue(), entry.getValue());
		}
	}

	@Override
	public String toString() {

		StringBuilder sb = new StringBuilder("[");

		NumberFormat f = NumberFormat.getInstance();
		f.setMaximumFractionDigits(5);
		Number previous = null;
		for (Number element : startPoints) {
			if (previous != null) {
				Number rangeEnd = null;
				if (intKeys) {
					int num = element.intValue() - 1;
					int prev = previous.intValue();
					if (num != prev)
						rangeEnd = num;
				} else
					rangeEnd = element;

				sb.append(f.format(previous)
						+ (rangeEnd == null ? "" : "-" + f.format(rangeEnd)));
				sb.append(" : ");
				sb.append(f.format(data.getCount(element)));
				sb.append(", ");

			}
			previous = element;
		}

		sb.append(f.format(previous) + "-");
		sb.append(" : ");
		sb.append(f.format(data.getCount(previous)));

		sb.append("]");
		return sb.toString();
	}
}
