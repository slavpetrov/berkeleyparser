package edu.berkeley.nlp.speech.features;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import edu.berkeley.nlp.speech.features.MFCC.WindowFunction;

/**
 * Look at the main method for usage. 
 * 
 * @author aria42
 *
 */
public class CepstralFeatureExtractor {

	int numCepstral ;
	int modelOrder  = 0;
	double wingap  = 0.010;
	int numPaddingOnEachSide ;
	double samplingRate ;
	int frameLength ;
	byte[] byteBuffer = new byte[20000];
	MFCC mfcc ;
	boolean verbose = false;
	
	public CepstralFeatureExtractor()
	{
		this(0.025,29,16000.0,13);
		setModelOrder(2);
	}

	public CepstralFeatureExtractor(double frameLengthInMS, int numMelFilters, double samplingRate, int numCeptralCoefs) {
		this.numCepstral = numCeptralCoefs;
		this.samplingRate = samplingRate;

		double winpts = Math.ceil(samplingRate * frameLengthInMS);
		int power = (int) Math.ceil((Math.log(winpts)/Math.log(2)));
		int frameLength = 1 << power;
		int numPadding = (int) (frameLength - winpts);
		if (!(numPadding % 2 == 0)) {
			throw new IllegalStateException();
		}
		this.numPaddingOnEachSide = numPadding / 2;
		this.frameLength = frameLength;
		this.mfcc = new MFCC(frameLength, numMelFilters, samplingRate, 0.0, samplingRate/2.0, numCeptralCoefs);
		mfcc.setWindowFunction(WindowFunction.HAMMING);

		if (verbose) {
			System.err.println("Window Size in Samples: " + frameLength);
			System.err.println("Window Size in Secs.: " + frameLengthInMS);
			System.err.println("Window Gap in Secs.: " + wingap);
			System.err.println("Window Size in Samples: " + Math.ceil(wingap * samplingRate));
			System.err.println("Num Cepstral Coefs: " + numCeptralCoefs);
		}


	}

	boolean isPowerOfTwo (int value) {
		return (value & -value) == value;
	}

	/**
	 * For the WAV file, each row of the result is a (modelOrder+1) * numCepstral
	 * double[] array representing the features for that window.  
	 * 
	 * @param wavFile
	 * @return
	 * @throws Exception
	 */
	public double[][] getFeatures(String wavFile) throws Exception {
		AudioInputStream ais  = null;
		try {
			ais = AudioSystem.getAudioInputStream(new File(wavFile));
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		AudioFormat format = ais.getFormat();
		double curSampingRate = format.getSampleRate();

		if (curSampingRate != samplingRate) {
			throw new IllegalArgumentException("Audio sample doesn't have same sampling rate as what was constructed")	;
		}

		List<Double> samples = readSamples(ais);
		return getFeatures(samples);
	}
		
	/**
	 * Get Features for the List<Double> passed in.
	 * @param samples
	 * @return
	 */
	private double[][] getFeatures(List<Double> samples) {	
		
		List<double[]> coefsList =new ArrayList<double[]>();
		double[] sampleBuffer = new double[frameLength];
		int numRealSamples = frameLength - 2 * numPaddingOnEachSide;
		for (int w=0; ; ++w) {
			int frameOffset = (int) Math.ceil(w * wingap * samplingRate);
			if  (frameOffset + numRealSamples > samples.size()) {
				break;
			}
			Arrays.fill(sampleBuffer, 0.0);
			// Fill Middle with real samples
			for (int i=0; i < numRealSamples; ++i) {
				sampleBuffer[i + numPaddingOnEachSide] = samples.get(frameOffset + i);
			}
			double[] coefs  = mfcc.getFeatures(sampleBuffer);
			coefsList.add(coefs);
		}
		int numWindows = coefsList.size();
		List<List<double[]>> featuresList = new ArrayList<List<double[]>>();
		featuresList.add(coefsList);
		List<double[]> last = coefsList;
		// Shrinking Window
		for (int m=0; m < modelOrder; ++m) {
			List<double[]> derivList = (new Deltas()).derivativeEstimate(last, modelOrder-m, wingap);
			last = derivList;
			featuresList.add(last);
		}
		double[][] features = new double[numWindows][numCepstral * (modelOrder+1)];
		for (int m=0; m < modelOrder+1; ++m) {
			List<double[]> input = featuresList.get(m);
			for (int i=0; i < numWindows; ++i) {
				for (int j=0; j < numCepstral; ++j) {
					features[i][m*numCepstral + j] = input.get(i)[j];
				}
			}
		}

		return features;
	}

	private static final short getShort(byte lowByte, byte hiByte) {
		return (short) (((hiByte & 0xff) << 8) + (lowByte & 0xff));
	}

	private List<Double> readSamples(AudioInputStream in) throws IOException {
		AudioFormat format = in.getFormat();
		if (format.getFrameSize() != 2) {
			throw new IllegalArgumentException("Only support 16-bit audio!");
		}
		List<Double> samples = new ArrayList<Double>();
		while (true) {
			int numRead = in.read(byteBuffer);
			if (numRead < 2) {
				break;
			}
			for (int i=0; i < numRead; i += 2) {
				byte hiByte = byteBuffer[i];
				byte lowByte = byteBuffer[i+1];
				short sh = getShort(lowByte, hiByte);
				samples.add((double) sh);
			}
		}
		return samples;
	}

	public void setModelOrder(int modelOrder) { this.modelOrder = modelOrder; }

	public static void main(String[] args) throws Exception {
		CepstralFeatureExtractor fe = new CepstralFeatureExtractor(0.025,29,16000.0,13);
		fe.setModelOrder(2);
		long start = System.currentTimeMillis();
		for (int i=0; i < 100; ++i) {
			fe.getFeatures(args[0]);
		}
		long stop = System.currentTimeMillis();
		double secs = (stop-start) / 1000.0;
		System.out.printf("Took %.3f seconds\n", secs);
	}

}
