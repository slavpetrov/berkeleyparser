package edu.berkeley.nlp.speech.features;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Deltas {

	public List<double[]> derivativeEstimate(List<double[]> inputList,  int windowSize, double timeDelta) {

		int stride = inputList.get(0).length;
		List<double[]> derivList = new ArrayList<double[]>();
		for (double[] input: inputList) {
			if (input.length != stride) {
				throw new IllegalArgumentException();
			}
			derivList.add(new double[input.length]);
		}

		double denominator = 0.0;
		for (int w=-windowSize; w <= windowSize; ++w) {
			denominator += w * w;
		}


		// Do one coefficient at a time
		for (int coefIndex=0; coefIndex < stride; ++coefIndex) {
			for (int i=0; i < inputList.size(); i += 1) {
				double numerator = 0.0;
				for (int w=-windowSize; w <= windowSize; ++w) {
					int index =  i + w ;
					//	First
					if (index < 0) {
						numerator += w * inputList.get(0)[coefIndex];
					}
					else if (index >= inputList.size()) {
						//	 Last
						numerator += w * inputList.get(inputList.size()-1)[coefIndex];
					}
					else { // In the middle
						numerator +=w * inputList.get(i+w)[coefIndex];
					}
				}
				double deltaVal = numerator / (denominator);
				derivList.get(i)[coefIndex] =  deltaVal  / (timeDelta);
			}
		}

		return derivList;
	}

	public static void main(String[] args) {
		int numPoints = 20;
		// x^2 on zero-one
		List<double[]> input = new ArrayList<double[]>();
		for (int i=0; i < numPoints; ++i) {
			double x = (i/(double) numPoints);
			double[] xArray = {2* x};
			input.add(xArray);
		}
		Deltas deltas = new Deltas();
		System.out.println("Input: " + Arrays.deepToString(input.toArray()));
		List<double[]> deriv = deltas.derivativeEstimate(input,  1, 1.0/numPoints);
		System.out.println("Deriv: " + Arrays.deepToString(deriv.toArray()));
		Object[] twoDeriv = deltas.derivativeEstimate(deriv,  1, 1.0/numPoints).toArray();
		System.out.println("2nd Deriv: " + Arrays.deepToString(twoDeriv));
	}
}
