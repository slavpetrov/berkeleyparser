package edu.berkeley.nlp.speech.features;


public class MelFilter {
	
	public static void main(String[] args) {
		new MelFilter(512,23,16000.0,0.0,8000.0);
	}
	
	private double[][] melTransform ;
	private int numSamples = 300;
	private int numFilters = 23;

//	private static double a = 700.0;
//	private static double b = 2595.0;

	public MelFilter(int numSamples, int numFilters, double samplingFreq, double minFreq, double maxFreq) {
		this.numSamples = numSamples;
		this.numFilters = numFilters;
		melTransform = new double[numFilters][numSamples];
		double melMax = mel(maxFreq);
		double melMin = mel(minFreq);
		double melDelta = (melMax-melMin) / (numFilters+1);

		double[] binfrqs = new double[numFilters+2];
		for (int i=0; i < binfrqs.length; ++i) {
			binfrqs[i] = invMel(melMin + i * melDelta);
		}

		double[] fftfrqs = new double[numSamples];
		for (int i=0; i < numSamples; ++i) {
			fftfrqs[i] = (i*samplingFreq) / numSamples;
		}

		double[] lowSlopes = new double[numSamples];
		double[] highSlopes = new double[numSamples];

		for (int i=0; i < numFilters; ++i) {
			double low =  binfrqs[i];
			double mid =  binfrqs[i+1];
			double high = binfrqs[i+2];

			for (int j=0; j < numSamples; ++j) {
				double freq = fftfrqs[j];
				lowSlopes[j] = (freq -low) / (mid - low);
				highSlopes[j] = (high - freq)/ (high - mid);
			}

			for (int j=0; j < numSamples  && j <= numSamples/2; ++j) {
				melTransform[i][j] = 2.0 / (high-low) * Math.max(0, Math.min(lowSlopes[j], highSlopes[j]));
			}
		}
	}


	private static double mel(double f) {
		return 2595 * Math.log10(1+f/700);
	}
	private static double invMel(double x) {
		return 700*(Math.pow(10,x/2595)-1);
	}

	public double[] transform(double[] samples) {
		if (samples.length != numSamples) {
			throw new IllegalArgumentException();
		}
		double[] output  = new double[numFilters];
		for (int i=0; i < numFilters; ++i) {
			for (int j=0; j < numSamples; ++j) {
				 output[i] += melTransform[i][j] * samples[j];
			}
		}
		return output;
	}
}
