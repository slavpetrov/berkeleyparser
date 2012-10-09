package edu.berkeley.nlp.ui;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;

public class EasyFormat {
	private static DecimalFormat stdFormat = null;

	public static DecimalFormat getStdFormat() {
		if (stdFormat == null) {
			DecimalFormatSymbols dsymb = new DecimalFormatSymbols();
			// on french locales, dots print like comma
			dsymb.setDecimalSeparator('.');
			stdFormat = new DecimalFormat("0.0000");
			stdFormat.setDecimalFormatSymbols(dsymb);
		}
		return stdFormat;
	}

	public static String std(double number) {
		return getStdFormat().format(number);
	}

	public static String fmt(double number) {
		return std(number);
	}

	public static String fmt(List<Double> numbers) {
		StringBuilder result = new StringBuilder();
		result.append("[");
		for (int i = 0; i < numbers.size(); i++) {
			result.append(fmt(numbers.get(i)));
			if (i != numbers.size() - 1)
				result.append(" ");
		}
		result.append("]");
		return result.toString();
	}
}
