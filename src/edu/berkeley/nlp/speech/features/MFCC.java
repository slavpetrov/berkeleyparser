package edu.berkeley.nlp.speech.features;

import java.util.Arrays;

/**
 *
 * @author aria42
 *
 */
public class MFCC {

	/**
	 *
	 * @author aria42
	 *
	 */
	public static enum WindowFunction {HAMMING, HANNING, NONE };

	/**
	 *
	 */
	private static final double TWO_PI = 2 * Math.PI;


	private int numCoefs ;
	private int frameLength;
	private FastFourierTransform fft;
	private double[][] dctMatrix;
	private int numMelFilters ;
	private MelFilter melFilter ;
	private WindowFunction windowFunction = WindowFunction.HAMMING;

	private double[] reBuffer ;
	private double[] imBuffer ;

	/**
	 *
	 * @return
	 */
	public MFCC(int frameLength, int numMelFilters, double samplingRate, double minFreq, double maxFreq, int numCoefs) {
//		System.out.println("Frame Length: " + frameLength);
		this.frameLength = frameLength;
		this.numMelFilters = numMelFilters;
		this.numCoefs = numCoefs;

		reBuffer = new double[frameLength];
		imBuffer = new double[frameLength];
		// Construct FFT
		fft = new FastFourierTransform(frameLength);
		// Construct Mel Filter
		melFilter = new MelFilter(frameLength, numMelFilters, samplingRate, minFreq, maxFreq);
		// Construct DCT Matrix
		initDCTMatrix(numMelFilters, numCoefs);
	}

	private void initDCTMatrix(int numMelFilters, int numCoefs) {
		dctMatrix = new double[numCoefs+1][numMelFilters];
		for (int i=0; i < numCoefs+1; ++i) {
			for (int j=0; j < numMelFilters; ++j) {
				double k = (2.0*j+1) /(2.0*numMelFilters);
				dctMatrix[i][j] = Math.cos(Math.PI * (i-1) * k) * Math.sqrt(2.0/numMelFilters);
				if (i == 0) {
					dctMatrix[i][j] /= Math.sqrt(2.0);
				}
			}
		}
	}

	/**
	 *
	 * @param frameLength
	 */
	public void setFrameLength(int frameLength) {
		this.frameLength = frameLength;
	}
	/**
	 *
	 * @param windowFunction
	 */
	public void setWindowFunction(WindowFunction windowFunction) {
		this.windowFunction = windowFunction;
	}


	/**
	 * Returns the first <code>numCoefs</code> coefficients of the
	 * cepstral representation of the signal for zeroth- first- and second-
	 * derivatives. So there are a total of <code>3 * numCoefs</code> numbers
	 * returned.
	 * @param signal
	 * @return
	 */
	public double[] getFeatures(double[] signal) {

		if (signal.length != this.frameLength) {
			throw new IllegalArgumentException("origSignal.length must equal == frameLength");
		}

		// Do FFT to convert signal -> power in place
		powerTransform(signal);
		// Signal is now freqs.
		// Hit it with Mel Filter Bank
		// convert to cepstral scale
		double[] freqs = melFilter.transform(signal);
		double[] coefs = doDCTTransform(freqs);
		return coefs;
	}

	private double[] doDCTTransform(double[] freqs) {
		double[] coefs = new double[numCoefs];
		for (int i=1; i <= numCoefs; ++i) {
			for (int j=0; j < numMelFilters; ++j) {
				coefs[i-1] += dctMatrix[i][j] * Math.log(freqs[j]);
			}
		}
		return coefs;
	}



	private void powerTransform(double[] signal) {
		Arrays.fill(imBuffer, 0.0);
		System.arraycopy(signal, 0, reBuffer, 0, frameLength);
		applyWindow(reBuffer);
		fft.transform(reBuffer, imBuffer);
		for (int i=0; i < frameLength; ++i) {
			signal[i] = reBuffer[i] * reBuffer[i] + imBuffer[i] * imBuffer[i];
		}
	}

	/**
	 *
	 * @param signal
	 */
	private void applyWindow(double[] signal) {
		for (int i=0; i < signal.length; ++i) {
			switch (this.windowFunction) {
				case HAMMING:
					signal[i] *= (0.53836-0.46164) * Math.cos(TWO_PI / (i+1));
					break;
				case NONE:
					break;
				default:
					throw new IllegalStateException(""+this.windowFunction + " not implemented");
			}
		}
	}

	public static void main(String[] args) {

	}
}
