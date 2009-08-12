/**
 * 
 */
package edu.berkeley.nlp.HMM;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.Writer;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import Jama.Matrix;

import edu.berkeley.nlp.HMM.TimitTester.MergeThreshType;
import edu.berkeley.nlp.HMM.TimitTester.MergingType;
import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.math.SloppyMath;
import edu.berkeley.nlp.prob.DiagCovGaussianSuffStats;
import edu.berkeley.nlp.prob.DiagonalCovGaussian;
import edu.berkeley.nlp.prob.FullCovGaussian;
import edu.berkeley.nlp.prob.FullCovGaussianSuffStats;
import edu.berkeley.nlp.prob.Gaussian;
import edu.berkeley.nlp.prob.GaussianMixture;
import edu.berkeley.nlp.prob.GaussianSuffStats;
import edu.berkeley.nlp.prob.Randomizer;


import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.Counter;
import fig.basic.Indexer;
import edu.berkeley.nlp.util.PriorityQueue;
import fig.basic.LogInfo;
import fig.exec.Execution;
import fig.exec.remote.DataHolder;
import fig.exec.remote.PollingExecutorService;
import fig.exec.remote.RemoteCallable;
import fig.exec.remote.RemoteOpts;
//import fig.exec.remote.DataHolder;
//import fig.exec.remote.PollingExecutorService;
//import fig.exec.remote.RemoteCallable;
import fig.prob.SuffStats;

/**
 * @author adpauls b => P(emit acoustics | phone, substate ) a => P(transition
 *         to new substate | new phone, old phone, old substate ) c =>
 *         P(transition to any new phone | phone, substate )
 */
public class SubphoneHMM implements Serializable {
	private static final long serialVersionUID = 1L;

	protected double emissionAttenuation;

	protected final int numPhones;

	protected int[] numSubstatesPerState;

	private static final Integer DATA_KEY = new Integer(1);

	protected static final Integer OFFSET_KEY = new Integer(2);

	Gaussian[][] b;

	protected final int gaussianDim;

	double[][][][] a; // a[toSubstate][toState][fromState][fromSubstate]

	double[][][] c; // c[toState][fromState][fromSubstate]

	int maxNumSubstates;

	protected int[] currPhoneSequences;

	final Indexer<Phone> phoneIndexer;

	// protected Random randomGenerator;

	protected Randomizer randomizer;

	protected final boolean useFullGaussian;

	protected double loglikelihood;

	double transitionSmooth;

	transient boolean dataSent = false;

	transient protected long id;

	private double varSmooth;

	private double meanSmooth;

	private double minVarSmooth;

	private boolean printPosteriors;

	private String filenameForPosteriors;

	public SubphoneHMM(Indexer<Phone> phoneIndexer, int gaussianDim, int rSeed,
			boolean fullGauss, double transitionSmooth, double meanSmooth,
			double varSmooth, double minVarSmooth, double emissionAttenuation,
			boolean print, String filename) {
		this.numSubstatesPerState = new int[phoneIndexer.size()];
		this.phoneIndexer = phoneIndexer;
		this.gaussianDim = gaussianDim;
		this.numPhones = phoneIndexer.size();
		this.maxNumSubstates = -1;// ArrayMath.max(numSubstatesPerState);
		// this.randomGenerator = new Random(rSeed);
		randomizer = new Randomizer(rSeed);
		this.useFullGaussian = fullGauss;
		this.transitionSmooth = transitionSmooth;
		this.meanSmooth = meanSmooth;
		this.varSmooth = varSmooth;
		this.minVarSmooth = minVarSmooth;
		this.emissionAttenuation = emissionAttenuation;
		this.printPosteriors = print;
		this.filenameForPosteriors = filename;
	}

	/**
	 * creates a copy of the current HMM
	 */
	public SubphoneHMM clone() {
		SubphoneHMM clone = new SubphoneHMM(phoneIndexer, gaussianDim, 0,
				useFullGaussian, transitionSmooth, meanSmooth, varSmooth, minVarSmooth,
				emissionAttenuation, printPosteriors, filenameForPosteriors);
		clone.a = ArrayUtil.clone(a);
		clone.b = b.clone();
		clone.c = ArrayUtil.clone(c);
		clone.numSubstatesPerState = numSubstatesPerState.clone();
		clone.maxNumSubstates = maxNumSubstates;
		return clone;
	}

	static double calcScaleFactor(double logScale, double scale) {
		if (logScale == 0.0)
			return 1.0;
		if (logScale == 1.0)
			return scale;
		if (logScale == 2.0)
			return scale * scale;
		if (logScale == 3.0)
			return scale * scale * scale;
		if (logScale == -1.0)
			return 1.0 / scale;
		if (logScale == -2.0)
			return 1.0 / scale / scale;
		if (logScale == -3.0)
			return 1.0 / scale / scale / scale;
		return Math.pow(scale, logScale);
	}

	/**
	 * computes the empirical probabilities p(substate|state)
	 * 
	 * @param phoneObjectSequences
	 * @param acousticObservationSequences
	 * @return
	 */
	public double[][] computeConditionalProbabilities(
			List<int[]> phoneObjectSequences,
			List<double[][]> acousticObservationSequences) {
		// initialize
		double[][] counts = new double[numPhones][];
		for (int phone = 0; phone < numPhones; phone++) {
			counts[phone] = new double[numSubstatesPerState[phone]];
		}

		// convert lists into arrays
		final int[][] phoneSequences = new int[phoneObjectSequences.size()][];
		final double[][][] acousticObservationSequencesAsArray = new double[acousticObservationSequences
				.size()][][];

		for (int seq = 0; seq < phoneObjectSequences.size(); ++seq) {
			phoneSequences[seq] = phoneObjectSequences.get(seq);
			acousticObservationSequencesAsArray[seq] = acousticObservationSequences
					.get(seq);
		}

		// count up the posterior counts
		TrainingProbChart probChart = new TrainingProbChart(this,
				emissionAttenuation);
		for (int seq = 0; seq < phoneSequences.length; ++seq) {
			this.currPhoneSequences = phoneSequences[seq];
			final int T = acousticObservationSequencesAsArray[seq].length;
			probChart.init(phoneSequences[seq],
					acousticObservationSequencesAsArray[seq], seq);
			probChart.calc();

			for (int t = 0; t < T - 1; t++) {
				for (int phone : probChart.allowedPhonesAtTime(t)) {
					for (int substate = 0; substate < numSubstatesPerState[phone]; substate++) {
						counts[phone][substate] += probChart.getGamma(phone, substate, t);
					}
				}
			}
		}

		// normalize
		for (int phone = 0; phone < numPhones; phone++) {
			double sum = ArrayUtil.sum(counts[phone]);
			ArrayUtil.multiplyInPlace(counts[phone], divide(1.0, sum));
		}
		return counts;
	}

//	public void train(List<int[]> phoneObjectSequences,
//			List<double[][]> acousticObservationSequences, int step) {
//		train(phoneObjectSequences, acousticObservationSequences, step, Executors
//				.newSingleThreadExecutor(), 1);
//	}

	private static class EMCounts implements Serializable {
		public String host;

		public double[][][][] new_a;

		public double[][][] new_c;

		public double[][] new_c_normalize;

		public GaussianSuffStats[][] new_b;

		// public double[][][][] new_b_crossterms;
		//
		// public double[][][] new_b_crossterms_diag;
		//
		// public double[][] new_b_normalize;

		public double LL;
	}

	private static class DataSet implements Serializable {
		public int[][] phoneSequences;

		public double[][][] acousticObservationSequencesAsArray;
	}

	public void train(List<int[]> phoneObjectSequences,
			List<double[][]> acousticObservationSequences, int steps/*,
			ExecutorService exec, int numDivide*/) {
		assert phoneObjectSequences.size() == acousticObservationSequences.size();

		PollingExecutorService exec = RemoteOpts.getConfiguredExecutorPool();
		int numDivide = exec.pollExecutors();
		divideUpData(phoneObjectSequences, acousticObservationSequences, exec,
				numDivide);

		double LL = 0;

		for (int step = 0; step < steps; ++step) {
			LogInfo.track("train(step=" + step + ")");
			Execution.putOutput("currEMStep", step);

			LL = 0;
			double[][][][] new_a = new double[maxNumSubstates][numPhones][numPhones][];
			double[][][] new_c = new double[numPhones][numPhones][];
			double[][] new_c_normalize = new double[numPhones][];
			GaussianSuffStats[][] new_b = new GaussianSuffStats[numPhones][];
			// double[][][][] new_b_crossterms = null;
			// double[][][] new_b_crossterms_diag = null;
			// if (useFullGaussian) {
			// new_b_crossterms = new double[numPhones][][][];
			// } else {
			// new_b_crossterms_diag = new double[numPhones][][];
			// }
			// double[][] new_b_normalize = new double[numPhones][];

			for (int phone = 0; phone < numPhones; ++phone) {
				new_b[phone] = new GaussianSuffStats[numSubstatesPerState[phone]];
				for (int substate = 0; substate < new_b[phone].length; ++substate) {
					new_b[phone][substate] = newGaussianSuffStats(phone, substate);
				}
				;
				new_c_normalize[phone] = new double[numSubstatesPerState[phone]];
				// new_b_normalize[phone] = new double[numSubstatesPerState[phone]];
				// if (useFullGaussian) {
				// new_b_crossterms[phone] = new
				// double[numSubstatesPerState[phone]][gaussianDim][gaussianDim];
				// } else {
				// new_b_crossterms_diag[phone] = new
				// double[numSubstatesPerState[phone]][gaussianDim];
				// }
				for (int phone2 = 0; phone2 < numPhones; ++phone2) {
					new_c[phone][phone2] = new double[numSubstatesPerState[phone2]];
					for (int substate = 0; substate < numSubstatesPerState[phone]; ++substate) {
						new_a[substate][phone][phone2] = new double[numSubstatesPerState[phone2]];
					}
				}
			}

			List<Callable<EMCounts>> jobs = new ArrayList<Callable<EMCounts>>();
			for (int i = 0; i < numDivide; ++i) {

				jobs.add(new RemoteCallable<EMCounts>() {

					/**
					 * 
					 */
					private static final long serialVersionUID = -1870162187849893569L;

					public EMCounts call() {

						double[][][][] new_a = new double[maxNumSubstates][numPhones][numPhones][];
						double[][][] new_c = new double[numPhones][numPhones][];
						double[][] new_c_normalize = new double[numPhones][];
						GaussianSuffStats[][] new_b = new GaussianSuffStats[numPhones][];
						// double[][][][] new_b_crossterms = null;
						// double[][][] new_b_crossterms_diag = null;
						// if (useFullGaussian) {
						// new_b_crossterms = new double[numPhones][][][];
						// } else {
						// new_b_crossterms_diag = new double[numPhones][][];
						// }
						double[][] new_b_normalize = new double[numPhones][];

						for (int phone = 0; phone < numPhones; ++phone) {
							new_b[phone] = new GaussianSuffStats[numSubstatesPerState[phone]];
							for (int substate = 0; substate < new_b[phone].length; ++substate) {
								new_b[phone][substate] = newGaussianSuffStats(phone, substate);
							}
							;
							new_c_normalize[phone] = new double[numSubstatesPerState[phone]];
							new_b_normalize[phone] = new double[numSubstatesPerState[phone]];
							// if (useFullGaussian) {
							// new_b_crossterms[phone] = new
							// double[numSubstatesPerState[phone]][gaussianDim][gaussianDim];
							// } else {
							// new_b_crossterms_diag[phone] = new
							// double[numSubstatesPerState[phone]][gaussianDim];
							// }
							for (int phone2 = 0; phone2 < numPhones; ++phone2) {
								new_c[phone][phone2] = new double[numSubstatesPerState[phone2]];
								for (int substate = 0; substate < numSubstatesPerState[phone]; ++substate) {
									new_a[substate][phone][phone2] = new double[numSubstatesPerState[phone2]];
								}
							}
						}

						ITrainingProbChart probChart = createTrainingChart();
						double LL = 0;

						String host = null;
						try {
							host = java.net.InetAddress.getLocalHost().getHostName();

						} catch (UnknownHostException e) {

						}
						DataSet dataSet = (DataSet) DataHolder.get(id, new Integer(1));
						int[][] phoneSequences2 = dataSet.phoneSequences;

						LogInfo.logss("Starting calculation of  " + phoneSequences2.length
								+ " sequences on " + host);
						double[][][] acousticSequences2 = dataSet.acousticObservationSequencesAsArray;
						for (int seq = 0; seq < phoneSequences2.length; ++seq) {

							if (seq % 100 == 0)
								LogInfo.logs(host + ":" + seq);

							probChart
									.init(phoneSequences2[seq], acousticSequences2[seq], seq);
							try {
								probChart.calc();
							} catch (PathNotFoundException pe) {
								LogInfo.warning(pe.getMessage());
								continue;
							}
							double thisLL = probChart.getLogLikelihood();
							assert !SloppyMath.isVeryDangerous(thisLL);
							if (SloppyMath.isVeryDangerous(thisLL)) {
								LogInfo.warning("Training sequence " + seq
										+ " is given -Inf LL. Skipping it!");
								continue;
							}

							final double[][] o = acousticSequences2[seq];

							final int T = o.length;

							countTransitions(new_a, new_c, new_c_normalize, probChart, T);

							countEmissions(new_b, probChart, o, T);

							// print posteriors
							if (printPosteriors)
								writePosteriorsToFile(filenameForPosteriors + "-" + seq
										+ ".post", probChart, T);

							LL += thisLL;
						} // loop over sequences
						EMCounts retVal = new EMCounts();
						retVal.LL = LL;

						retVal.new_c_normalize = new_c_normalize;
						retVal.new_a = new_a;
						retVal.new_b = new_b;
						// retVal.new_b_crossterms_diag = new_b_crossterms_diag;
						retVal.new_c = new_c;
						// retVal.new_b_sum = new_b_sum;
						// retVal.new_b_normalize = new_b_normalize;
						retVal.host = host;
						return retVal;
					}

				});
			}
			List<Future<EMCounts>> futures = null;
			try {
				futures = exec.invokeAll(jobs);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}

			while (true) {
				boolean done = true;

				for (Future<EMCounts> future : futures) {
					done &= future.isDone();
				}
				if (done)
					break;
			}

			for (Future<EMCounts> future : futures) {
				try {
					EMCounts emCounts = future.get();
					LogInfo.logss("Received counts from " + emCounts.host);

					LL += emCounts.LL;
					for (int phone = 2; phone < numPhones; ++phone) {
						for (int substate = 0; substate < numSubstatesPerState[phone]; ++substate) {
							new_b[phone][substate].add(emCounts.new_b[phone][substate]);
						}
					}
					// addInPlace(new_b_sum, emCounts.new_b_sum);
					// addInPlace(new_b_crossterms, emCounts.new_b_crossterms);
					// addInPlace(new_b_crossterms_diag, emCounts.new_b_crossterms_diag);
					// addInPlace(new_b_normalize, emCounts.new_b_normalize);
					ArrayUtil.addInPlace(new_a, emCounts.new_a);
					ArrayUtil.addInPlace(new_c, emCounts.new_c);
					ArrayUtil.addInPlace(new_c_normalize, emCounts.new_c_normalize);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				} catch (ExecutionException e) {
					throw new RuntimeException(e);
				}

			}

			// Re-estimate transition probabilities
			for (int fromPhone = 0; fromPhone < numPhones; ++fromPhone) {
				for (int fromSubstate = 0; fromSubstate < numSubstatesPerState[fromPhone]; ++fromSubstate) {
					for (int toPhone = 0; toPhone < numPhones; ++toPhone) {
						for (int toSubstate = 0; toSubstate < numSubstatesPerState[toPhone]; ++toSubstate) {
							double transitionProb = divide(
									new_a[toSubstate][toPhone][fromPhone][fromSubstate],
									new_c[toPhone][fromPhone][fromSubstate]);
							assert FwdBwdChart.isProbability(transitionProb);
							a[toSubstate][toPhone][fromPhone][fromSubstate] = transitionProb;
						}
					}
				}
			}

			for (int toPhone = 0; toPhone < numPhones; ++toPhone) {
				for (int fromPhone = 0; fromPhone < numPhones; ++fromPhone) {
					for (int fromSubstate = 0; fromSubstate < numSubstatesPerState[fromPhone]; ++fromSubstate) {
						double transitionProb = divide(
								new_c[toPhone][fromPhone][fromSubstate],
								new_c_normalize[fromPhone][fromSubstate]);
						assert FwdBwdChart.isProbability(transitionProb);
						c[toPhone][fromPhone][fromSubstate] = transitionProb;
					}
				}
			}

			// Re-estimae the emissions
			for (int phone = 2; phone < numPhones; ++phone) {
				for (int substate = 0; substate < numSubstatesPerState[phone]; ++substate) {
					if (phone == 13 || phone == 46) {
						if (b[phone][substate] instanceof FullCovGaussian)
						{
							LogInfo.dbg("phone:: " + phone + " " + ArrayUtil.toString(b[phone][substate].getMean()));
						}
						else if (b[phone][substate] instanceof GaussianMixture)
						{
						final GaussianMixture gaussianMixture = ((GaussianMixture) b[phone][substate]);
						for (int i = 0; i < gaussianMixture.getNumMixtures(); ++i) {

							LogInfo.dbg("phone:: " + phone + " " + ArrayUtil.toString(gaussianMixture.getGaussian(i).getMean()));
						}
						}
					}
					b[phone][substate] = new_b[phone][substate].estimate();

				}
				// double[] this_sums = new_b_sum[phone][substate];
				// double[] mean = ArrayMath.multiply(this_sums, divide(1.0,
				// new_b_normalize[phone][substate]));
				// double normalizer = (new_b_normalize[phone][substate] == 0) ? 0
				// : divide(1.0, new_b_normalize[phone][substate]);
				// assert !SloppyMath.isVeryDangerous(normalizer);
				// if (useFullGaussian) {
				//
				// double[][] cov = new double[gaussianDim][gaussianDim];
				// for (int i = 0; i < gaussianDim; ++i) {
				// for (int j = 0; j < gaussianDim; ++j) {
				// // MUST have normalizer term in between the sums so that we
				// // don't get overflow!!
				//
				// cov[i][j] = new_b_crossterms[phone][substate][i][j]
				// - this_sums[j] * normalizer * this_sums[i];
				//
				// cov[i][j] *= normalizer;
				// }
				// }
				//
				// b[phone][substate] = new FullCovGaussian(mean, new Matrix(cov));
				// } else {
				// double[] variance = new double[gaussianDim];
				// for (int i = 0; i < gaussianDim; ++i) {
				//
				// variance[i] = new_b_crossterms_diag[phone][substate][i]
				// - (this_sums[i] * normalizer * this_sums[i]);
				// variance[i] *= normalizer;
				// }
				//
				// DiagonalCovGaussian newEmission = new DiagonalCovGaussian(mean,
				// variance);
				// b[phone][substate] = newEmission;
				// }
				// }

			}

			// if (transitionSmooth > 0)
			// smoothTransitions();
			// if (varSmooth > 0 || meanSmooth > 0 || minVarSmooth > 0)
			// wishartSmooth(new_b_normalize);
			loglikelihood = LL;
			LogInfo.logss("The LL after " + (step + 1) + " iterations is " + LL);
			assert checkValidGaussians();

			LogInfo.end_track();

		}
	}

	/**
	 * @param phone
	 *          TODO
	 * @param substate
	 *          TODO
	 * @return
	 */
	protected GaussianSuffStats newGaussianSuffStats(int phone, int substate) {
		if (useFullGaussian)
			return new FullCovGaussianSuffStats(gaussianDim);
		else
			return new DiagCovGaussianSuffStats(gaussianDim);

	}

	private boolean checkValidGaussians() {
		boolean valid = true;
		for (int phone = 2; phone < numPhones; ++phone)

		{
			for (int substate = 0; substate < numSubstatesPerState[phone]; ++substate) {
				valid &= b[phone][substate].isValid();
				assert b[phone][substate].isValid();
			}
		}
		return valid;
	}

	protected void writePosteriorsToFile(String filename,
			ITrainingProbChart probChart_, final int T) {
		TrainingProbChart probChart = (TrainingProbChart) probChart_;
		BufferedWriter output = null;
		try {
			output = new BufferedWriter(new FileWriter(filename));
			for (int t = 1; t < T - 1; t++) {
				for (int phone : probChart.allowedPhonesAtTime(t)) {
					for (int substate = 0; substate < numSubstatesPerState[phone]; ++substate) {
						double gamma = probChart.getGamma(phone, substate, t);
						output.write(gamma + " ");
					}
				}
				output.write("\n");
			}
			output.flush();
			output.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected void countEmissions(GaussianSuffStats[][] new_b,
			ITrainingProbChart probChart_, final double[][] o, final int T) {
		TrainingProbChart probChart = (TrainingProbChart) probChart_;
		for (int t = 1; t < T - 1; t++) {
			for (int phone : probChart.allowedPhonesAtTime(t)) {

				for (int substate = 0; substate < numSubstatesPerState[phone]; ++substate) {
					double gamma = probChart.getGamma(phone, substate, t);

					assert FwdBwdChart.isProbability(gamma);
					new_b[phone][substate].add(o[t], gamma);
					// double[] weightedObs = ArrayMath.multiply(o[t], gamma);
					//
					// // first the means
					// ArrayMath.addInPlace(new_b_sum[phone][substate], weightedObs);
					// new_b_normalize[phone][substate] += gamma;
					//
					// // now variance
					// double[][] scaledOuterProduct = null;
					// if (useFullGaussian) {
					// scaledOuterProduct = FullCovGaussian.scaledOuterSelfProduct(o[t],
					// gamma);
					// for (int i = 0; i < gaussianDim; ++i) {
					// ArrayMath.addInPlace(new_b_crossterms[phone][substate][i],
					// scaledOuterProduct[i]);
					// }
					// } else {
					//
					// double[] diagonal = ArrayMath.pairwiseMultiply(o[t], o[t]);
					// ArrayMath.addInPlace(new_b_crossterms_diag[phone][substate],
					// ArrayMath.multiply(diagonal, gamma));
					//
					// }

				}
			}
		}
	}

	protected void countTransitions(double[][][][] new_a, double[][][] new_c,
			double[][] new_c_normalize, ITrainingProbChart probChart, final int T) {
		// TrainingProbChart probChart = (TrainingProbChart)probChart_;
		for (int t = 0; t < T - 1; t++) {
			for (int fromPhone : probChart.allowedPhonesAtTime(t)) {
				// int fromPhone = phoneSeq[n];
				for (int toPhone : probChart.allowedNextPhonesAtTime(t, fromPhone)) {
					// int toPhone = phoneSeq[toN];
					double[][] probs = probChart.getProbability(t, fromPhone, toPhone);
					for (int fromSubstate = 0; fromSubstate < numSubstatesPerState[fromPhone]; ++fromSubstate) {
						for (int toSubstate = 0; toSubstate < numSubstatesPerState[toPhone]; ++toSubstate) {
							double prob = probs[fromSubstate][toSubstate];
							assert FwdBwdChart.isProbability(prob);

							new_a[toSubstate][toPhone][fromPhone][fromSubstate] += prob;
							new_c[toPhone][fromPhone][fromSubstate] += prob;
							new_c_normalize[fromPhone][fromSubstate] += prob;
						}
					}
				}
			}
		}
	}

	private void divideUpData(List<int[]> phoneObjectSequences,
			List<double[][]> acousticObservationSequences, ExecutorService exec,
			int numDivide) {

		if (!dataSent) {
			final double em = TimitTester.staticEmissionAttenuation;
			// convert lists into arrays
			final int[][][] phoneSequences = new int[numDivide][][];
			final double[][][][] acousticObservationSequencesAsArray = new double[numDivide][][][];

			final int lim = phoneObjectSequences.size() / numDivide;
			int curr = -1;
			for (int seq = 0; seq < phoneObjectSequences.size(); ++seq) {
				if (seq % lim == 0 && curr < numDivide - 1) {
					curr++;
					int last = lim;
					if (phoneObjectSequences.size() - seq < 2 * lim) {
						last = phoneObjectSequences.size() - seq;
					}
					phoneSequences[curr] = new int[last][];
					acousticObservationSequencesAsArray[curr] = new double[last][][];
				}

				phoneSequences[curr][seq - curr * lim] = phoneObjectSequences.get(seq);
				acousticObservationSequencesAsArray[curr][seq - curr * lim] = acousticObservationSequences
						.get(seq);
			}

			// get the data over to the individual machines
			List<Callable<Object>> dataJobs = new ArrayList<Callable<Object>>();
			for (int i = 0; i < numDivide; ++i) {
				final int currSet = i;
				final DataSet dataSet = new DataSet();
				dataSet.phoneSequences = phoneSequences[currSet];
				dataSet.acousticObservationSequencesAsArray = acousticObservationSequencesAsArray[currSet];
				final int offset = currSet * lim;

				id = DataHolder.genId();

				dataJobs.add(new RemoteCallable<Object>() {

					/**
					 * 
					 */
					private static final long serialVersionUID = -1870162187849893569L;

					public Object call() {
						TimitTester.staticEmissionAttenuation = em;

						DataHolder.put(id, DATA_KEY, dataSet);
						DataHolder.put(id, OFFSET_KEY, new Integer(offset));
						return null;
					}
				});
			}

			List<Future<Object>> dataFutures = null;
			try {
				dataFutures = exec.invokeAll(dataJobs);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}

			while (true) {
				boolean done = true;

				for (Future<Object> future : dataFutures) {
					done &= future.isDone();
				}
				if (done)
					break;
			}
		}
		dataSent = true;
	}

	protected ITrainingProbChart createTrainingChart() {
		return new TrainingProbChart(this, emissionAttenuation);
	}

	public void dumpGaussiansToDisk(String baseName) {
		for (int phone = 2; phone < numPhones; ++phone) {
			Writer output = null;
			try {
				Gaussian[] x = b[phone];

				for (int i = 0; i < x.length; i++) {
					Gaussian g = x[i];
					if (g == null)
						continue;
					// first the mean
					output = new BufferedWriter(new FileWriter(baseName + "."
							+ phoneIndexer.get(phone).getLabel() + "-" + i + ".mean"));
					double[] mean = g.getMean();
					for (double d : mean) {
						output.write(d + " ");
					}
					if (output != null)
						output.close();

					// then the (co)variance
					output = new BufferedWriter(new FileWriter(baseName + "."
							+ phoneIndexer.get(phone).getLabel() + "-" + i + ".cov"));
					double[][] cov = g.getCovariance();
					for (double[] d : cov) {
						for (double e : d) {
							output.write(e + " ");
						}
						output.write("\n");
					}
					if (output != null)
						output.close();

				}
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}

	public void writeDotFiles(String baseName, double threshold) {
		NumberFormat f = NumberFormat.getInstance();
		f.setMaximumFractionDigits(2);
		for (int phone = 2; phone < phoneIndexer.size(); phone++) {
			BufferedWriter output = null;
			String phoneStr = phoneIndexer.get(phone).getLabel();
			String fileName = baseName + "." + phoneStr + ".dot";
			try {
				output = new BufferedWriter((new FileWriter(fileName)));
				output.write("digraph G {\n");
				output
						.write("\tnode[fontsize=24];\n\tconcentrate = true;\n\trankdir = LR;\n\tsize = \"4,4\";");

				// the subphone structure
				output.write("subgraph cluster_0 {\n");
				// output.write("\t label="+phoneIndexer.get(phone).getLabel()+";\n");
				output.write("\t color=lightgrey;\n");
				for (int fromSubphone = 0; fromSubphone < numSubstatesPerState[phone]; fromSubphone++) {
					for (int toSubphone = 0; toSubphone < numSubstatesPerState[phone]; toSubphone++) {
						double p = c[phone][phone][fromSubphone]
								* a[toSubphone][phone][phone][fromSubphone];

						if (p > threshold)
							output.write("\t" + fromSubphone + " -> " + toSubphone + " \n");// [label="+f.format(p)+"]\n");
					}
				}
				output.write("}\n");
				// left contexts
				output.write("subgraph cluster_1 {\n");
				output.write("\t label=previous;\n");
				output.write("\t color=lightgrey;\n");
				boolean[] visible = new boolean[numPhones];
				for (int toSubphone = 0; toSubphone < numSubstatesPerState[phone]; toSubphone++) {
					for (int fromPhone = 0; fromPhone < phoneIndexer.size(); fromPhone++) {
						if (fromPhone == phone)
							continue;
						double p = 0;
						for (int fromSubphone = 0; fromSubphone < numSubstatesPerState[fromPhone]; fromSubphone++) {
							p += c[phone][fromPhone][fromSubphone]
									* a[toSubphone][phone][fromPhone][fromSubphone];
						}
						// p /=numSubstatesPerState[fromPhone];
						if (p > threshold / 3) {
							visible[fromPhone] = true;
							output.write("\t\"p_" + phoneIndexer.get(fromPhone).getLabel()
									+ "\" -> " + toSubphone + " \n");// [label="+f.format(p)+"]\n");
						}
					}
				}
				for (int fromPhone = 0; fromPhone < phoneIndexer.size(); fromPhone++) {
					if (visible[fromPhone])
						output.write("\t\"p_" + phoneIndexer.get(fromPhone).getLabel()
								+ "\" [label=\"" + phoneIndexer.get(fromPhone).getLabel()
								+ "\"]\n");
				}
				output.write("}\n");
				// right contexts
				output.write("subgraph cluster_2 {\n");
				output.write("\t label=next;\n");
				output.write("\t color=lightgrey;\n");
				visible = new boolean[numPhones];
				for (int fromSubphone = 0; fromSubphone < numSubstatesPerState[phone]; fromSubphone++) {
					for (int toPhone = 0; toPhone < phoneIndexer.size(); toPhone++) {
						if (toPhone == phone)
							continue;
						double p = 0;
						for (int toSubphone = 0; toSubphone < numSubstatesPerState[toPhone]; toSubphone++) {
							p += c[toPhone][phone][fromSubphone]
									* a[toSubphone][toPhone][phone][fromSubphone];
						}
						if (p > threshold / 3) {
							visible[toPhone] = true;
							output.write("\t" + fromSubphone + " -> \"n_"
									+ phoneIndexer.get(toPhone).getLabel() + "\"\n");// [label="+f.format(p)+"]\n");
						}
					}
				}
				for (int fromPhone = 0; fromPhone < phoneIndexer.size(); fromPhone++) {
					if (visible[fromPhone])
						output.write("\t\"n_" + phoneIndexer.get(fromPhone).getLabel()
								+ "\" [label=\"" + phoneIndexer.get(fromPhone).getLabel()
								+ "\"]\n");
				}
				output.write("}\n");
				output.write("}\n");
				if (output != null)
					output.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();
		NumberFormat f = NumberFormat.getInstance();
		f.setMaximumFractionDigits(2);
		sb.append("b:\n");
		for (int fromPhone = 2; fromPhone < numPhones; ++fromPhone) {
			Gaussian[] x = b[fromPhone];
			for (Gaussian g : x) {
				sb.append(g + "\n");
			}
		}

		sb.append("\nc: " + ArrayUtil.toString(c));
		sb.append("\na:\n");
		for (int fromPhone = 0; fromPhone < numPhones; ++fromPhone) {
			if (fromPhone == 1)
				continue;
			for (int fromSubstate = 0; fromSubstate < numSubstatesPerState[fromPhone]; ++fromSubstate) {
				for (int toPhone = 1; toPhone < numPhones; ++toPhone) {
					for (int toSubstate = 0; toSubstate < numSubstatesPerState[toPhone]; ++toSubstate) {
						double p = a[toSubstate][toPhone][fromPhone][fromSubstate];
						if (p > 0) {
							String pStr = f.format(p);
							sb.append("P(" + toSubstate + "*|" + toPhone + ", " + fromPhone
									+ ", " + fromSubstate + "*) = " + pStr + "\t");
						} else
							sb.append("\t\t\t");
					}
				}
				sb.append("\n");
			}
		}
		sb.append("c*a:\n");
		for (int fromPhone = 0; fromPhone < numPhones; ++fromPhone) {
			if (fromPhone == 1)
				continue;
			for (int fromSubstate = 0; fromSubstate < numSubstatesPerState[fromPhone]; ++fromSubstate) {
				for (int toPhone = 1; toPhone < numPhones; ++toPhone) {
					for (int toSubstate = 0; toSubstate < numSubstatesPerState[toPhone]; ++toSubstate) {
						double cFactor = c[toPhone][fromPhone][fromSubstate];
						double p = cFactor
								* a[toSubstate][toPhone][fromPhone][fromSubstate];
						if (p > 0) {
							String pStr = f.format(p);
							sb.append("(" + fromPhone + "-" + fromSubstate + "*)->("
									+ toPhone + "-" + toSubstate + "*) = " + pStr + "\t");
						} else
							sb.append("\t\t\t");
					}
				}
				sb.append("\n");
			}
		}

		return sb.toString();

	}

	public static double divide(double n, double d) {
		return d == 0.0 ? 0.0 : n / d;
	}

	/**
	 * @param stateSequences
	 * @param obsSequences
	 * @param randomization
	 */
	public void initializeModelFromStateSequence(List<int[]> stateSequences,
			List<double[][]> obsSequences, int numSubstates, int randomness) {

		Arrays.fill(numSubstatesPerState, numSubstates);
		numSubstatesPerState[phoneIndexer.indexOf(Corpus.START_PHONE)] = 1;
		numSubstatesPerState[phoneIndexer.indexOf(Corpus.END_PHONE)] = 1;
		this.maxNumSubstates = numSubstates;

		// if (useFullGaussian)
		// this.b = new FullCovGaussian[numPhones][maxNumSubstates];
		// else
		this.b = new Gaussian[numPhones][maxNumSubstates];
		this.a = new double[maxNumSubstates][numPhones][numPhones][maxNumSubstates];
		this.c = new double[numPhones][numPhones][maxNumSubstates];
		// this.c[phoneIndexer.indexOf(Corpus.START_PHONE)] = new double[1];
		// this.c[phoneIndexer.indexOf(Corpus.END_PHONE)] = new double[1];

		int nSeq = stateSequences.size();

		double[][] initMean = new double[numPhones][gaussianDim];
		double[][] initVar_diag = new double[numPhones][gaussianDim];
		Matrix[] initVar_full = new Matrix[numPhones];
		double[] normalize = new double[numPhones];
		double[][] A = new double[numPhones][numPhones];
		double[] C = new double[numPhones];
		for (int n = 0; n < nSeq; n++) {
			double[][] obsSeq = obsSequences.get(n);
			int[] stateSeq = stateSequences.get(n);
			int T = obsSeq.length;
			int prevPhone = stateSeq[0];
			int phone;
			normalize[prevPhone]++;
			for (int t = 1; t < T; t++) {
				phone = stateSeq[t];
				if (t < T - 1)
					ArrayUtil.addInPlace(initMean[phone], obsSeq[t]);
				A[prevPhone][phone]++;
				if (prevPhone != phone)
					C[prevPhone]++;
				normalize[phone]++;
				prevPhone = phone;
			}
		}
		for (int phone = 2; phone < numPhones; phone++) {
			ArrayUtil.multiplyInPlace(initMean[phone], divide(1.0, normalize[phone]));
			initVar_full[phone] = new Matrix(gaussianDim, gaussianDim);
			// System.out.println(Arrays.toString(initMean[phone]));
		}
		for (int n = 0; n < nSeq; n++) {
			double[][] obsSeq = obsSequences.get(n);
			int[] stateSeq = stateSequences.get(n);
			int T = obsSeq.length;
			for (int t = 1; t < T - 1; t++) {
				int phone = stateSeq[t];
				double[] diff = ArrayUtil.subtract(obsSeq[t], initMean[phone]);

				if (useFullGaussian) {
					Matrix diffVector = FullCovGaussian.vectorToMatrix(diff);
					Matrix thisVariance = diffVector.transpose().times(diffVector);

					initVar_full[phone].plusEquals(thisVariance);
				} else {
					double[] squaredDiff = DoubleArrays.pointwiseMultiply(diff, diff);
					ArrayUtil.addInPlace(initVar_diag[phone], squaredDiff);
				}
			}
		}
		for (int phone = 2; phone < numPhones; phone++) {
			if (useFullGaussian)
				initVar_full[phone].timesEquals(divide(1.0, normalize[phone]));
			else
				ArrayUtil.multiplyInPlace(initVar_diag[phone], divide(1.0,
						normalize[phone]));
		}

		for (int phone = 2; phone < numPhones; ++phone) {
			for (int substate = 0; substate < numSubstatesPerState[phone]; ++substate) {
				// double[] rInitVar = randPerturb(r, initVar[phone],randomness);
				if (useFullGaussian) {
					// for (int i = 0; i < gaussianDim; ++i)
					// {
					// initVar_full[phone].getArray()[i][i] += 0.1;
					// }
					b[phone][substate] = new FullCovGaussian(randomizer.randPerturb(
							initMean[phone], randomness), initVar_full[phone]);
				} else {
					b[phone][substate] = new DiagonalCovGaussian(randomizer.randPerturb(
							initMean[phone], randomness), initVar_diag[phone]);
				}
			}
		}

		for (int fromPhone = 0; fromPhone < numPhones; ++fromPhone) {
			double normFactor = divide(1.0, normalize[fromPhone]);
			for (int toPhone = 0; toPhone < numPhones; ++toPhone) {
				double splitFactor = numSubstatesPerState[toPhone];
				double empirical = (A[fromPhone][toPhone] * normFactor) / splitFactor;
				for (int substate = 0; substate < numSubstatesPerState[fromPhone]; ++substate) {
					c[toPhone][fromPhone][substate] = empirical
							* (1.0 + ((randomizer.nextDouble() - 0.5) * randomness / 100.0));
				}
			}
		}

		for (int fromPhone = 0; fromPhone < numPhones; ++fromPhone) {
			for (int j = 0; j < numSubstatesPerState[fromPhone]; ++j) {
				for (int toPhone = 0; toPhone < numPhones; ++toPhone) {
					if (A[fromPhone][toPhone] == 0)
						continue;
					double splitMass = 1.0 / numSubstatesPerState[toPhone]
							* numSubstatesPerState[fromPhone];
					for (int k = 0; k < numSubstatesPerState[toPhone]; ++k) {
						a[k][toPhone][fromPhone][j] = splitMass
								* (1.0 + ((randomizer.nextDouble() - 0.5) * randomness / 100.0));
					}
				}
			}
		}

		// System.out.println(this);
	}

	/**
	 * Doubles the number of substates and adds randomness to the - mean of the
	 * emission-model (variance is kept constant) - the tranisitionprobabilities a
	 * (leavestate prob [c] is kept constant)
	 * 
	 * @param randomness
	 * @param maxNumberOfStates
	 */
	public void splitModelInTwo(int randomness, int[] maxNumberOfStates) {
		int new_maxNumSubstates = maxNumSubstates * 2;
		int[] splitFactor = new int[numPhones];
		int[] new_NumSubstatesPerState = new int[numPhones];
		for (int phone = 0; phone < numPhones; phone++) {
			if (numSubstatesPerState[phone] < maxNumberOfStates[phone]) {
				splitFactor[phone] = 2;
				new_NumSubstatesPerState[phone] = 2 * numSubstatesPerState[phone];
			} else {
				splitFactor[phone] = 1;
				new_NumSubstatesPerState[phone] = 1 * numSubstatesPerState[phone];
			}
		}
		// ArrayMath
		// .multiply(numSubstatesPerState, 2);
		new_NumSubstatesPerState[phoneIndexer.indexOf(Corpus.START_PHONE)] = 1;
		new_NumSubstatesPerState[phoneIndexer.indexOf(Corpus.END_PHONE)] = 1;
		splitFactor[phoneIndexer.indexOf(Corpus.START_PHONE)] = 1;
		splitFactor[phoneIndexer.indexOf(Corpus.END_PHONE)] = 1;

		// Gaussian[][] new_b = new Gaussian[numPhones][new_maxNumSubstates];
		// double[][][][] new_a = new
		// double[new_maxNumSubstates][numPhones][numPhones][new_maxNumSubstates];
		// double[][][] new_c = new
		// double[numPhones][numPhones][new_maxNumSubstates];
		Gaussian[][] new_b = new Gaussian[numPhones][];
		double[][][][] new_a = new double[new_maxNumSubstates][numPhones][numPhones][];
		double[][][] new_c = new double[numPhones][numPhones][];
		for (int phone = 0; phone < numPhones; ++phone) {
			new_b[phone] = new Gaussian[new_NumSubstatesPerState[phone]];
			for (int phone2 = 0; phone2 < numPhones; ++phone2) {
				new_c[phone][phone2] = new double[new_NumSubstatesPerState[phone2]];
				for (int substate = 0; substate < new_NumSubstatesPerState[phone]; ++substate) {
					new_a[substate][phone][phone2] = new double[new_NumSubstatesPerState[phone2]];
				}
			}
		}

		// new_c[phoneIndexer.indexOf(Corpus.START_PHONE)] = new double[1];
		// new_c[phoneIndexer.indexOf(Corpus.END_PHONE)] = new double[1];

		for (int phone = 2; phone < numPhones; ++phone) {
			double rand = splitFactor[phone] == 2 ? randomness : 0.0;
			for (int substate = 0; substate < numSubstatesPerState[phone]; ++substate) {
				Gaussian[] splitGaussians = b[phone][substate].splitInTwo(randomizer,
						rand);
				for (int split = 0; split < splitFactor[phone]; split++) {

					new_b[phone][substate * splitFactor[phone] + split] = splitGaussians[split];
					// if (useFullGaussian)
					// new_b[phone][substate * splitFactor[phone] + split] = new
					// FullCovGaussian(
					// randPerturb(b[phone][substate].getMean(), rand),
					// (FullCovGaussian) b[phone][substate]);
					// else
					// new_b[phone][substate * splitFactor[phone] + split] = new
					// DiagonalCovGaussian(
					// randPerturb(b[phone][substate].getMean(), rand),
					// (DiagonalCovGaussian) b[phone][substate]);
				}
			}
		}
		// dummies, just so getNumMixtures returns the right amount
		new_b[0][0] = new_b[1][0] = new GaussianMixture(new Gaussian[1],
				new double[1]);

		for (int fromPhone = 0; fromPhone < numPhones; ++fromPhone) {
			for (int toPhone = 0; toPhone < numPhones; ++toPhone) {
				for (int substate = 0; substate < numSubstatesPerState[fromPhone]; ++substate) {
					for (int split = 0; split < splitFactor[fromPhone]; split++) {
						new_c[toPhone][fromPhone][substate * splitFactor[fromPhone] + split] = c[toPhone][fromPhone][substate];
					}
				}
			}
		}

		for (int fromPhone = 0; fromPhone < numPhones; ++fromPhone) {
			for (int j = 0; j < numSubstatesPerState[fromPhone]; ++j) {
				for (int toPhone = 0; toPhone < numPhones; ++toPhone) {
					for (int k = 0; k < numSubstatesPerState[toPhone]; ++k) {
						for (int split_j = 0; split_j < splitFactor[fromPhone]; split_j++) {
							for (int split_k = 0; split_k < splitFactor[toPhone]; split_k++) {
								double rand = splitFactor[toPhone] == 2 ? randomness : 0.0;
								new_a[k * splitFactor[toPhone] + split_k][toPhone][fromPhone][j
										* splitFactor[fromPhone] + split_j] = a[k][toPhone][fromPhone][j]
										/ splitFactor[toPhone]
										* (1.0 + ((randomizer.nextDouble() - 0.5) * rand / (100.0 * splitFactor[fromPhone] * splitFactor[toPhone])));
							}
						}
					}
				}
			}
		}

		this.a = new_a;
		this.b = new_b;
		this.c = new_c;
		this.numSubstatesPerState = new_NumSubstatesPerState;
		this.maxNumSubstates = new_maxNumSubstates;
		int total = 0;
		for (int phone = 0; phone < numPhones; ++phone) {
			total += numSubstatesPerState[phone];
		}
		LogInfo.logs("After splitting there are " + total + " states.");

	}

	/**
	 * raises transition probabilities to the power-th power
	 * 
	 * @param power
	 */
	public void boostTransitionProbabilities(double power, double cPower) {
		for (int fromPhone = 0; fromPhone < numPhones; ++fromPhone) {
			for (int toPhone = 0; toPhone < numPhones; ++toPhone) {
				for (int substate = 0; substate < numSubstatesPerState[fromPhone]; ++substate) {
					c[toPhone][fromPhone][substate] = Math.pow(
							c[toPhone][fromPhone][substate], power);
					if (fromPhone == toPhone)
						c[toPhone][fromPhone][substate] = Math.pow(
								c[toPhone][fromPhone][substate], cPower);
				}
			}
		}

		for (int fromPhone = 0; fromPhone < numPhones; ++fromPhone) {
			for (int j = 0; j < numSubstatesPerState[fromPhone]; ++j) {
				for (int toPhone = 0; toPhone < numPhones; ++toPhone) {
					for (int k = 0; k < numSubstatesPerState[toPhone]; ++k) {
						a[k][toPhone][fromPhone][j] = Math.pow(a[k][toPhone][fromPhone][j],
								power);
						if (fromPhone == toPhone)
							a[k][toPhone][fromPhone][j] = Math.pow(
									a[k][toPhone][fromPhone][j], cPower);
					}
				}
			}
		}
	}

	/**
	 * 
	 * @param percentage
	 * @param mergeThresType
	 */
	public void mergeModel(List<int[]> phoneObjectSequences,
			List<double[][]> acousticObservationSequences, double percentage,
			TimitTester.MergingType mergingType, MergeThreshType mergeThresType) {
		if (percentage == 0)
			return;
		// compute weights and deltas
		double[][] mergeWeights = computeConditionalProbabilities(
				phoneObjectSequences, acousticObservationSequences);

		// compute merged model parameters
		// double[][][][] merged_a = computeMergedTransitions(mergeWeights);
		Gaussian[][] merged_b = computeMergedEmissions(mergeWeights);
		// double[][] merged_c = computeMergedLeaveStates(mergeWeights);

		ArrayList<Double> deltaList = new ArrayList<Double>();

		double[][] deltaToUse = null;
		switch (mergingType) {
		case GEN_APPROX:
		case GEN_BUGGY:

			deltaToUse = computeDeltas(phoneObjectSequences,
					acousticObservationSequences, mergeWeights, merged_b, mergingType);
			break;
		case COND_APPROX:
		case COND_BUGGY: {
			double[][] jointDeltas = computeDeltas(phoneObjectSequences,
					acousticObservationSequences, mergeWeights, merged_b,
					TimitTester.MergingType.GEN_APPROX);
			deltaToUse = computeConditionalDeltas(phoneObjectSequences,
					acousticObservationSequences, mergeWeights, merged_b, jointDeltas,
					mergingType);
		}
			break;

		case GEN_EXACT:
			deltaToUse = computeExactDeltas(phoneObjectSequences,
					acousticObservationSequences, mergeWeights, false);
			break;

		case COND_EXACT:
			deltaToUse = computeExactDeltas(phoneObjectSequences,
					acousticObservationSequences, mergeWeights, true);
			break;
		// case GEN_BUGGY:
		//
		// deltaToUse = computeBuggyDeltas(phoneObjectSequences,
		// acousticObservationSequences, mergeWeights, merged_b);
		// break;
		// case COND_BUGGY:
		// {
		// double[][] jointDeltas = computeBuggyDeltas(phoneObjectSequences,
		// acousticObservationSequences, mergeWeights, merged_b);
		// deltaToUse = computeBuggyConditionalDeltas(phoneObjectSequences,
		// acousticObservationSequences, mergeWeights, merged_b, jointDeltas);
		// }
		// break;
		// case GEN_DOUBLE_BUGGY:
		//
		// deltaToUse = computeDoubleBuggyDeltas(phoneObjectSequences,
		// acousticObservationSequences, mergeWeights, merged_b);
		// break;
		// case COND_DOUBLE_BUGGY:
		// {
		// double[][] jointDeltas = computeDoubleBuggyDeltas(phoneObjectSequences,
		// acousticObservationSequences, mergeWeights, merged_b);
		// deltaToUse = computeDoubleBuggyConditionalDeltas(phoneObjectSequences,
		// acousticObservationSequences, mergeWeights, merged_b, jointDeltas);
		// }
		// break;
		default:
			throw new RuntimeException();
		}
		// determine threshold
		for (int phone = 0; phone < numPhones; phone++) {
			for (int substate = 0; substate < numSubstatesPerState[phone]; substate += 2) {
				deltaList.add(deltaToUse[phone][substate / 2]);
			}
		}
		double threshold = Double.NEGATIVE_INFINITY;
		switch (mergeThresType) {
		case PERCENT:

		{
			LogInfo.logs("Going to merge " + (int) (percentage * 100)
					+ "% of the last splits.");
			Collections.sort(deltaList);
			Collections.reverse(deltaList);
			threshold = deltaList.get((int) (deltaList.size() * percentage));
			break;
		}
		case ABS: {
			threshold = percentage;
			break;
		}
		default:
			throw new RuntimeException();
		}
		LogInfo.logs("Setting the threshold for merging to " + threshold);
		for (int phone = 0; phone < numPhones; phone++) {
			for (int substate = 0; substate < numSubstatesPerState[phone]; substate += 2) {
				LogInfo.logss("Merging (" + substate + "," + (substate + 1)
						+ ") for phone " + phoneIndexer.get(phone) + " (" + phone
						+ ")  will lose approx. " + deltaToUse[phone][substate / 2]);

			}
		}

		// decide which splits to reverse
		int new_maxNumSubstates = -1;
		int[] new_NumSubstatesPerState = new int[numPhones];
		boolean[][] mergeThisSplit = new boolean[numPhones][];
		int[][] mapping = new int[numPhones][];
		for (int phone = 0; phone < numPhones; phone++) {
			mergeThisSplit[phone] = new boolean[numSubstatesPerState[phone] / 2];
			mapping[phone] = new int[numSubstatesPerState[phone]];
			if (numSubstatesPerState[phone] == 1) {
				mergeThisSplit[phone] = new boolean[] { false };
				mapping[phone] = new int[2];
				new_NumSubstatesPerState[phone] = 1;
				continue;
			}
			int nSubstates = 0;
			for (int substate = 0; substate < numSubstatesPerState[phone]; substate += 2) {
				boolean mergeThis = deltaToUse[phone][substate / 2] > threshold;
				mergeThisSplit[phone][substate / 2] = mergeThis;
				if (mergeThis) {
					mapping[phone][substate] = mapping[phone][substate + 1] = nSubstates++;
					LogInfo.logss("Really merging " + phone + ":" + substate);
				} else {
					mapping[phone][substate] = nSubstates++;
					mapping[phone][substate + 1] = nSubstates++;
				}
			}
			new_NumSubstatesPerState[phone] = nSubstates;
			if (nSubstates > new_maxNumSubstates) {
				new_maxNumSubstates = nSubstates;
			}
		}

		// reverse the splits
		Gaussian[][] new_b = new Gaussian[numPhones][new_maxNumSubstates];
		double[][][][] new_a = new double[new_maxNumSubstates][numPhones][numPhones][new_maxNumSubstates];
		double[][][] new_c = new double[numPhones][numPhones][new_maxNumSubstates];

		// b and c are easy to update
		for (int phone = 0; phone < numPhones; ++phone) {
			int newSubstate = 0;
			for (int substate = 0; substate < numSubstatesPerState[phone]; substate += 2) {
				if (!mergeThisSplit[phone][substate / 2]) {
					new_b[phone][mapping[phone][substate]] = b[phone][substate];
					if (numSubstatesPerState[phone] > 1)
						new_b[phone][mapping[phone][substate + 1]] = b[phone][substate + 1];
					newSubstate += 2;
				} else {
					double mergeWeight1 = mergeWeights[phone][substate], mergeWeight2 = mergeWeights[phone][substate + 1];
					double tmpSum = mergeWeight1 + mergeWeight2;
					mergeWeight1 /= tmpSum;

					b[phone][substate]
							.mergeGaussian(b[phone][substate + 1], mergeWeight1);
					new_b[phone][newSubstate] = b[phone][substate];
					newSubstate++;
				}
			}
		}

		// a is a bit more tedious
		for (int fromPhone = 0; fromPhone < numPhones; ++fromPhone) {
			for (int fromSubstate = 0; fromSubstate < numSubstatesPerState[fromPhone]; fromSubstate += 2) {
				for (int toPhone = 0; toPhone < numPhones; ++toPhone) {
					for (int toSubstate = 0; toSubstate < numSubstatesPerState[toPhone]; toSubstate += 2) {
						if (!mergeThisSplit[fromPhone][fromSubstate / 2]) {
							new_c[toPhone][fromPhone][mapping[fromPhone][fromSubstate]] = c[toPhone][fromPhone][fromSubstate];
							if (numSubstatesPerState[fromPhone] > 1)
								new_c[toPhone][fromPhone][mapping[fromPhone][fromSubstate + 1]] = c[toPhone][fromPhone][fromSubstate + 1];

							if (!mergeThisSplit[toPhone][toSubstate / 2]) {
								// the easiest case: none of the splits is getting reversed
								new_a[mapping[toPhone][toSubstate]][toPhone][fromPhone][mapping[fromPhone][fromSubstate]] = a[toSubstate][toPhone][fromPhone][fromSubstate];
								if (numSubstatesPerState[toPhone] > 1)
									new_a[mapping[toPhone][toSubstate + 1]][toPhone][fromPhone][mapping[fromPhone][fromSubstate]] = a[toSubstate + 1][toPhone][fromPhone][fromSubstate];
								if (numSubstatesPerState[fromPhone] > 1) {
									new_a[mapping[toPhone][toSubstate]][toPhone][fromPhone][mapping[fromPhone][fromSubstate + 1]] = a[toSubstate][toPhone][fromPhone][fromSubstate + 1];
									if (numSubstatesPerState[toPhone] > 1)
										new_a[mapping[toPhone][toSubstate + 1]][toPhone][fromPhone][mapping[fromPhone][fromSubstate + 1]] = a[toSubstate + 1][toPhone][fromPhone][fromSubstate + 1];
								}
							} else {
								// the toSubstate gets merged
								new_a[mapping[toPhone][toSubstate]][toPhone][fromPhone][mapping[fromPhone][fromSubstate]] = a[toSubstate][toPhone][fromPhone][fromSubstate]
										+ a[toSubstate + 1][toPhone][fromPhone][fromSubstate];
								if (numSubstatesPerState[fromPhone] != 1)
									new_a[mapping[toPhone][toSubstate]][toPhone][fromPhone][mapping[fromPhone][fromSubstate + 1]] = a[toSubstate][toPhone][fromPhone][fromSubstate + 1]
											+ a[toSubstate + 1][toPhone][fromPhone][fromSubstate + 1];
							}
						} else {
							double mWeight1 = mergeWeights[fromPhone][fromSubstate], mWeight2 = mergeWeights[fromPhone][fromSubstate + 1];
							double tmpSum = mWeight1 + mWeight2;
							mWeight1 /= tmpSum;
							mWeight2 /= tmpSum;
							new_c[toPhone][fromPhone][mapping[fromPhone][fromSubstate]] = mWeight1
									* c[toPhone][fromPhone][fromSubstate]
									+ (mWeight2 * c[toPhone][fromPhone][fromSubstate + 1]);
							if (!mergeThisSplit[toPhone][toSubstate / 2]) {
								// the fromSubstate gets merged
								new_a[mapping[toPhone][toSubstate]][toPhone][fromPhone][mapping[fromPhone][fromSubstate]] = mWeight1
										* a[toSubstate][toPhone][fromPhone][fromSubstate]
										+ (mWeight2 * a[toSubstate][toPhone][fromPhone][fromSubstate + 1]);
								if (numSubstatesPerState[toPhone] != 1)
									new_a[mapping[toPhone][toSubstate + 1]][toPhone][fromPhone][mapping[fromPhone][fromSubstate]] = mWeight1
											* a[toSubstate + 1][toPhone][fromPhone][fromSubstate]
											+ (mWeight2 * a[toSubstate + 1][toPhone][fromPhone][fromSubstate + 1]);
							} else {
								// both splits get reversed
								new_a[mapping[toPhone][toSubstate]][toPhone][fromPhone][mapping[fromPhone][fromSubstate]] = mWeight1
										* (a[toSubstate][toPhone][fromPhone][fromSubstate] + a[toSubstate + 1][toPhone][fromPhone][fromSubstate])
										+ (mWeight2 * (a[toSubstate][toPhone][fromPhone][fromSubstate + 1] + a[toSubstate + 1][toPhone][fromPhone][fromSubstate + 1]));

							}
						}
					}
				}
			}
		}
		this.a = new_a;
		this.b = new_b;
		this.c = new_c;
		this.numSubstatesPerState = new_NumSubstatesPerState;
		this.maxNumSubstates = new_maxNumSubstates;
		int total = 0;
		for (int phone = 0; phone < numPhones; ++phone) {
			total += numSubstatesPerState[phone];
		}
		LogInfo.logs("After merging there are " + total + " states.");

	}

	public int getTotalNumStates() {
		int total = 0;
		for (int phone = 0; phone < numPhones; ++phone) {
			total += numSubstatesPerState[phone];

		}
		return total;
	}

	public int getTotalNumParameters() {
		int states = getTotalNumStates();
		if (useFullGaussian) {
			// transition matrix + covariance + means
			return states
					* (states + gaussianDim / 2 * (gaussianDim - 1) + gaussianDim);
		} else {
			// transition matrix + covariance + means
			return states * (states + gaussianDim + gaussianDim);
		}
	}

	/**
	 * @param mergeWeights
	 * @return
	 */
	private double[][][] computeMergedLeaveStates(double[][] mergeWeights) {
		double[][][] new_c = new double[numPhones][numPhones][];

		for (int toPhone = 0; toPhone < numPhones; ++toPhone) {
			for (int phone = 0; phone < numPhones; ++phone) {
				if (numSubstatesPerState[phone] == 1)
					continue; // start or end state
				new_c[toPhone][phone] = new double[numSubstatesPerState[phone] / 2];
				for (int substate = 0; substate < numSubstatesPerState[phone]; substate += 2) {
					double mergeWeight1 = mergeWeights[phone][substate], mergeWeight2 = mergeWeights[phone][substate + 1];
					double tmpSum = mergeWeight1 + mergeWeight2;
					mergeWeight1 /= tmpSum;

					new_c[toPhone][phone][substate / 2] = mergeWeight1
							* c[toPhone][phone][substate]
							+ ((1.0 - mergeWeight1) * c[toPhone][phone][substate + 1]);
				}
			}
		}
		return new_c;
	}

	/**
	 * @param mergeWeights
	 * @return
	 */
	private Gaussian[][] computeMergedEmissions(double[][] mergeWeights) {
		Gaussian[][] new_b = new Gaussian[numPhones][];

		for (int phone = 0; phone < numPhones; ++phone) {
			if (numSubstatesPerState[phone] == 1)
				continue; // start or end state
			new_b[phone] = new Gaussian[numSubstatesPerState[phone] / 2];
			for (int substate = 0; substate < numSubstatesPerState[phone]; substate += 2) {
				double mergeWeight1 = mergeWeights[phone][substate], mergeWeight2 = mergeWeights[phone][substate + 1];
				double tmpSum = mergeWeight1 + mergeWeight2;
				mergeWeight1 /= tmpSum;

				new_b[phone][substate / 2] = b[phone][substate].clone();
				new_b[phone][substate / 2].mergeGaussian(b[phone][substate + 1],
						mergeWeight1);
			}
		}
		return new_b;
	}

	/**
	 * @param mergeWeights
	 * @return
	 */
	private double[][][][] computeMergedTransitions(double[][] mergeWeights) {
		double[][][][] new_a = new double[maxNumSubstates / 2][numPhones][numPhones][maxNumSubstates];

		for (int fromPhone = 0; fromPhone < numPhones; ++fromPhone) {
			for (int fromSubstate = 0; fromSubstate < numSubstatesPerState[fromPhone]; fromSubstate++) {
				for (int toPhone = 0; toPhone < numPhones; ++toPhone) {
					for (int toSubstate = 0; toSubstate < numSubstatesPerState[toPhone]; toSubstate += 2) {
						new_a[toSubstate / 2][toPhone][fromPhone][fromSubstate] = a[toSubstate][toPhone][fromPhone][fromSubstate]
								+ a[toSubstate + 1][toPhone][fromPhone][fromSubstate];
					}
				}
			}
		}
		return new_a;
	}

	public void printNumberOfSubstates() {
		LogInfo.logss("Number of substates: [");
		for (int phone = 0; phone < numPhones; ++phone) {
			LogInfo.logss("(" + phoneIndexer.get(phone) + "," + phone + ")->"
					+ numSubstatesPerState[phone] + ", ");
		}
		LogInfo.logss("]");
	}

	/**
	 * @param phoneObjectSequences
	 * @param acousticObservationSequences
	 * @param mergingType
	 * @return
	 */
	private double[][] computeDeltas(List<int[]> phoneObjectSequences,
			List<double[][]> acousticObservationSequences, double[][] mergeWeights,
			Gaussian[][] merged_b, MergingType mergingType) {
		// initialize
		double[][] deltas = new double[numPhones][];
		for (int phone = 0; phone < numPhones; phone++) {
			if (numSubstatesPerState[phone] == 1)
				deltas[phone] = new double[] { Double.MAX_VALUE };
			else
				deltas[phone] = new double[numSubstatesPerState[phone] / 2];

		}

		// convert lists into arrays
		final int[][] phoneSequences = new int[phoneObjectSequences.size()][];
		final double[][][] acousticObservationSequencesAsArray = new double[acousticObservationSequences
				.size()][][];
		for (int seq = 0; seq < phoneObjectSequences.size(); ++seq) {
			phoneSequences[seq] = phoneObjectSequences.get(seq);
			acousticObservationSequencesAsArray[seq] = acousticObservationSequences
					.get(seq);
		}

		// count up the posterior counts
		TrainingProbChart probChart = new TrainingProbChart(this,
				emissionAttenuation);

		for (int seq = 0; seq < phoneSequences.length; ++seq) {
			this.currPhoneSequences = phoneSequences[seq];
			final int T = acousticObservationSequencesAsArray[seq].length;
			final double[][] observations = acousticObservationSequencesAsArray[seq];
			probChart.init(phoneSequences[seq],
					acousticObservationSequencesAsArray[seq], seq);
			probChart.calc();
			double z = probChart.getUnscaledLikelihood();
			double z_scale = probChart.alphasScale[T - 1];

			for (int t = 0; t < T - 1; t++) {
				for (int phone : probChart.allowedPhonesAtTime(t)) {
					int nSubstates = numSubstatesPerState[phone];
					if (nSubstates == 1) {
						continue;
					}

					double alpha_scale = probChart.alphasScale[t - 1];
					double beta_scale = probChart.betasScale[t];
					double scaleFactor = probChart.getScaleFactor(alpha_scale
							+ beta_scale - z_scale);
					// calculate merged scores
					for (short substate1 = 0; substate1 < nSubstates; substate1 += 2) {
						short substate2 = (short) (substate1 + 1);
						short[] map = new short[] { substate1, substate2 };
						double alpha = 0.0;

						for (int fromPhone : probChart.allowedPhonesAtTime(t - 1)) {
							for (int fromSubstate = 0; fromSubstate < numSubstatesPerState[fromPhone]; ++fromSubstate) {
								double leaveStateProb = c[phone][fromPhone][fromSubstate];
								alpha += probChart.alphas[t - 1][fromPhone][fromSubstate]
										* (a[substate1][phone][fromPhone][fromSubstate] + a[substate1 + 1][phone][fromPhone][fromSubstate])
										* leaveStateProb;
							}
						}
						if (t < T - 1) {
							double obsLik = merged_b[phone][substate1 / 2]
									.evalPdf(observations[t]);
							alpha *= obsLik;
						}

						double[] tmp2 = new double[2];

						double separatedMassForTheseSubstates = 0.0;
						for (int k = 0; k < 2; k++) {
							separatedMassForTheseSubstates += probChart.getGamma(phone,
									map[k], t);
						}
						// not sure why this happens . . . round off error?

						for (int k = 0; k < 2; k++) {

							tmp2[k] = probChart.betas[t][phone][map[k]];
						}
						double mergeWeightSum = mergeWeights[phone][substate1]
								+ mergeWeights[phone][substate2];
						double beta = (mergeWeights[phone][substate1] * tmp2[0])
								+ mergeWeights[phone][substate2] * tmp2[1];
						beta /= mergeWeightSum;
						double combinedScore = alpha / z * beta * scaleFactor;
						if (mergingType == TimitTester.MergingType.GEN_BUGGY) {
							combinedScore = combinedScore + 1.0
									- separatedMassForTheseSubstates;
						} else {
							double x = 1.0 - separatedMassForTheseSubstates;
							x = Math.max(x, 0);
							combinedScore = combinedScore + x;
							if (combinedScore <= 0.0) {
								combinedScore = Double.MIN_VALUE;
							}
						}
						// assert combinedScore > 0.0;

						deltas[phone][substate1 / 2] += Math.log(combinedScore);
					}
				}
			}
		}

		return deltas;
	}

	/**
	 * @param phoneObjectSequences
	 * @param acousticObservationSequences
	 * @return
	 */
	private double[][] computeConditionalDeltas(List<int[]> phoneObjectSequences,
			List<double[][]> acousticObservationSequences, double[][] mergeWeights,
			Gaussian[][] merged_b, double[][] jointDeltas, MergingType mergingType) {
		// initialize
		double[][] deltas = new double[numPhones][];
		for (int phone = 0; phone < numPhones; phone++) {
			if (numSubstatesPerState[phone] == 1)
				deltas[phone] = new double[] { Double.MAX_VALUE };
			else
				deltas[phone] = new double[numSubstatesPerState[phone] / 2];

		}

		// convert lists into arrays
		final int[][] phoneSequences = new int[phoneObjectSequences.size()][];
		final double[][][] acousticObservationSequencesAsArray = new double[acousticObservationSequences
				.size()][][];
		for (int seq = 0; seq < phoneObjectSequences.size(); ++seq) {
			phoneSequences[seq] = phoneObjectSequences.get(seq);
			acousticObservationSequencesAsArray[seq] = acousticObservationSequences
					.get(seq);
		}

		// count up the posterior counts
		PosteriorProbChart probChart = new PosteriorProbChart(this);
		TrainingProbChart trainingProbChart = new TrainingProbChart(this,
				emissionAttenuation);

		for (int seq = 0; seq < phoneSequences.length; ++seq) {
			this.currPhoneSequences = phoneSequences[seq];
			final int T = acousticObservationSequencesAsArray[seq].length;
			final double[][] observations = acousticObservationSequencesAsArray[seq];
			trainingProbChart.init(phoneSequences[seq],
					acousticObservationSequencesAsArray[seq], seq);
			probChart.init(acousticObservationSequencesAsArray[seq]);
			probChart.calc();
			double z = probChart.getUnscaledLikelihood();
			double z_scale = probChart.alphasScale[T - 1];

			for (int t = 0; t < T - 1; t++) {

				for (int phone : trainingProbChart.allowedPhonesAtTime(t)) {
					int nSubstates = numSubstatesPerState[phone];
					if (nSubstates == 1) {
						continue;
					}

					double alpha_scale = probChart.alphasScale[t - 1];
					double beta_scale = probChart.betasScale[t];
					double scaleFactor = probChart.getScaleFactor(alpha_scale
							+ beta_scale - z_scale);
					// calculate merged scores
					for (short substate1 = 0; substate1 < nSubstates; substate1 += 2) {

						short substate2 = (short) (substate1 + 1);
						short[] map = new short[] { substate1, substate2 };
						double alpha = 0.0;

						for (int fromPhone : probChart.allowedPhonesAtTime(t - 1)) {
							for (int fromSubstate = 0; fromSubstate < numSubstatesPerState[fromPhone]; ++fromSubstate) {
								double leaveStateProb = c[phone][fromPhone][fromSubstate];
								alpha += probChart.alphas[t - 1][fromPhone][fromSubstate]
										* (a[substate1][phone][fromPhone][fromSubstate] + a[substate1 + 1][phone][fromPhone][fromSubstate])
										* leaveStateProb;
							}
						}
						if (t < T - 1) {
							double obsLik = merged_b[phone][substate1 / 2]
									.evalPdf(observations[t]);
							alpha *= obsLik;
						}

						double[] tmp2 = new double[2];

						double separatedMassForTheseSubstates = 0.0;
						for (int k = 0; k < 2; k++) {
							separatedMassForTheseSubstates += probChart.getGamma(phone,
									map[k], t);
						}

						for (int k = 0; k < 2; k++) {

							tmp2[k] = probChart.betas[t][phone][map[k]];
						}
						double mergeWeightSum = mergeWeights[phone][substate1]
								+ mergeWeights[phone][substate2];
						double beta = (mergeWeights[phone][substate1] * tmp2[0])
								+ mergeWeights[phone][substate2] * tmp2[1];
						beta /= mergeWeightSum;
						double combinedScore = alpha / z * beta * scaleFactor;
						if (mergingType == TimitTester.MergingType.COND_BUGGY) {
							combinedScore = combinedScore + 1.0
									- separatedMassForTheseSubstates;
						} else {
							double x = 1.0 - separatedMassForTheseSubstates;
							x = Math.max(x, 0);
							combinedScore = combinedScore + x;
							if (combinedScore <= 0.0) {
								combinedScore = Double.MIN_VALUE;
							}
						}
						deltas[phone][substate1 / 2] += Math.log(combinedScore);
					}
				}
			}
		}
		for (int phone = 0; phone < numPhones; ++phone) {
			for (int substate = 0; substate < numSubstatesPerState[phone]; substate += 2) {
				deltas[phone][substate / 2] = +(jointDeltas[phone][substate / 2] - deltas[phone][substate / 2]);
			}
		}
		return deltas;
	}

	/**
	 * @param phoneObjectSequences
	 * @param acousticObservationSequences
	 * @return
	 */
	// private double[][] computeBuggyDeltas(List<int[]> phoneObjectSequences,
	// List<double[][]> acousticObservationSequences, double[][] mergeWeights,
	// Gaussian[][] merged_b) {
	// // initialize
	// double[][] deltas = new double[numPhones][];
	// for (int phone = 0; phone < numPhones; phone++) {
	// if (numSubstatesPerState[phone] == 1)
	// deltas[phone] = new double[] { Double.MAX_VALUE };
	// else
	// deltas[phone] = new double[numSubstatesPerState[phone] / 2];
	//
	// }
	//
	// // convert lists into arrays
	// final int[][] phoneSequences = new int[phoneObjectSequences.size()][];
	// final double[][][] acousticObservationSequencesAsArray = new
	// double[acousticObservationSequences
	// .size()][][];
	// for (int seq = 0; seq < phoneObjectSequences.size(); ++seq) {
	// phoneSequences[seq] = phoneObjectSequences.get(seq);
	// acousticObservationSequencesAsArray[seq] = acousticObservationSequences
	// .get(seq);
	// }
	//
	// // count up the posterior counts
	// ITrainingProbChart probChart = createTrainingChart();
	//
	// for (int seq = 0; seq < phoneSequences.length; ++seq) {
	// this.currPhoneSequences = phoneSequences[seq];
	// final int T = acousticObservationSequencesAsArray[seq].length;
	// final double[][] observations = acousticObservationSequencesAsArray[seq];
	// probChart.init(phoneSequences[seq],
	// acousticObservationSequencesAsArray[seq], seq);
	// probChart.calc();
	// double z = probChart.getUnscaledLikelihood();
	// double z_scale = probChart.alphasScale[T - 1];
	//
	// for (int t = 0; t < T - 1; t++) {
	// for (int phone : probChart.allowedPhonesAtTime(t)) {
	// int nSubstates = numSubstatesPerState[phone];
	// if (nSubstates == 1) {
	// continue;
	// }
	//
	// double[] separatedScores = new double[nSubstates];
	//
	// // calculate separated scores
	// for (int substate = 0; substate < nSubstates; substate++) {
	// separatedScores[substate] = probChart.getGamma(phone, substate, t);
	// }
	//
	// double alpha_scale = probChart.alphasScale[t - 1];
	// double beta_scale = probChart.betasScale[t];
	// double scaleFactor = probChart.getScaleFactor(alpha_scale
	// + beta_scale - z_scale);
	// // calculate merged scores
	// for (short substate1 = 0; substate1 < nSubstates; substate1 += 2) {
	// short substate2 = (short) (substate1 + 1);
	// short[] map = new short[] { substate1, substate2 };
	// double alpha = 0.0;
	//
	// for (int fromPhone : probChart.allowedPhonesAtTime(t - 1)) {
	// for (int fromSubstate = 0; fromSubstate < numSubstatesPerState[fromPhone];
	// ++fromSubstate) {
	// double leaveStateProb = c[phone][fromPhone][fromSubstate];
	// alpha += probChart.alphas[t - 1][fromPhone][fromSubstate]
	// * (a[substate1][phone][fromPhone][fromSubstate] + a[substate1 +
	// 1][phone][fromPhone][fromSubstate])
	// * leaveStateProb;
	// }
	// }
	// if (t < T - 1) {
	// double obsLik = merged_b[phone][substate1 / 2]
	// .evalPdf(observations[t]);
	// alpha *= obsLik;
	// }
	//
	// double[] tmp2 = new double[2];
	//
	// for (int k = 0; k < 2; k++) {
	//
	// tmp2[k] = probChart.betas[t][phone][map[k]];
	// }
	// double mergeWeightSum = mergeWeights[phone][substate1]
	// + mergeWeights[phone][substate2];
	// double beta = (mergeWeights[phone][substate1] * tmp2[0])
	// + mergeWeights[phone][substate2] * tmp2[1];
	// beta /= mergeWeightSum;
	// // deltas[phone][substate1/2] = Math.log(alpha
	// // / z * (tmp2[0]+tmp2[1])
	// // * scaleFactor);
	// deltas[phone][substate1 / 2] += Math.log(alpha) + Math.log(beta)
	// + 100 * (alpha_scale + beta_scale);
	//
	// // if (combinedScore!=0 && separatedScoreSum!=0)
	// // deltas[phone][substate1/2] +=
	// // Math.log(separatedScoreSum/combinedScore);
	// }
	// }
	// }
	// }
	//
	// return deltas;
	// }
	/**
	 * @param phoneObjectSequences
	 * @param acousticObservationSequences
	 * @return
	 */
	// private double[][] computeBuggyConditionalDeltas(
	// List<int[]> phoneObjectSequences,
	// List<double[][]> acousticObservationSequences, double[][] mergeWeights,
	// Gaussian[][] merged_b, double[][] jointDeltas) {
	// // initialize
	// double[][] deltas = new double[numPhones][];
	// for (int phone = 0; phone < numPhones; phone++) {
	// if (numSubstatesPerState[phone] == 1)
	// deltas[phone] = new double[] { Double.MAX_VALUE };
	// else
	// deltas[phone] = new double[numSubstatesPerState[phone] / 2];
	//
	// }
	//
	// // convert lists into arrays
	// final int[][] phoneSequences = new int[phoneObjectSequences.size()][];
	// final double[][][] acousticObservationSequencesAsArray = new
	// double[acousticObservationSequences
	// .size()][][];
	// for (int seq = 0; seq < phoneObjectSequences.size(); ++seq) {
	// phoneSequences[seq] = phoneObjectSequences.get(seq);
	// acousticObservationSequencesAsArray[seq] = acousticObservationSequences
	// .get(seq);
	// }
	//
	// // count up the posterior counts
	// PosteriorProbChart probChart = new PosteriorProbChart(this);
	// TrainingProbChart trainingProbChart = createTrainingChart();
	//
	// for (int seq = 0; seq < phoneSequences.length; ++seq) {
	// this.currPhoneSequences = phoneSequences[seq];
	// final int T = acousticObservationSequencesAsArray[seq].length;
	// final double[][] observations = acousticObservationSequencesAsArray[seq];
	// trainingProbChart.init(phoneSequences[seq],
	// acousticObservationSequencesAsArray[seq], seq);
	// probChart.init(acousticObservationSequencesAsArray[seq]);
	// probChart.calc();
	// double z = probChart.getUnscaledLikelihood();
	// double z_scale = probChart.alphasScale[T - 1];
	//
	// for (int t = 0; t < T - 1; t++) {
	//
	// // precompute the posterior mass for a given phone
	// // double[] otherMass = DoubleArrays.constantArray(0.0, numPhones);
	// // double totalMass = 0.0;
	// // for (int phone : probChart.allowedPhonesAtTime(t))
	// // {
	// // for (int substate = 0; substate < numSubstatesPerState[phone];
	// // ++substate)
	// // {
	// // double gamma = probChart.getGamma(phone, substate, t);
	// // otherMass[phone] += gamma;
	// // totalMass += gamma;
	// // }
	// // }
	// for (int phone : trainingProbChart.allowedPhonesAtTime(t)) {
	// int nSubstates = numSubstatesPerState[phone];
	// if (nSubstates == 1) {
	// continue;
	// }
	//
	// double alpha_scale = probChart.alphasScale[t - 1];
	// double beta_scale = probChart.betasScale[t];
	// double scaleFactor = probChart.getScaleFactor(alpha_scale
	// + beta_scale - z_scale);
	// // calculate merged scores
	// for (short substate1 = 0; substate1 < nSubstates; substate1 += 2) {
	// short substate2 = (short) (substate1 + 1);
	// short[] map = new short[] { substate1, substate2 };
	// double alpha = 0.0;
	//
	// for (int fromPhone : probChart.allowedPhonesAtTime(t - 1)) {
	// for (int fromSubstate = 0; fromSubstate < numSubstatesPerState[fromPhone];
	// ++fromSubstate) {
	// double leaveStateProb = c[phone][fromPhone][fromSubstate];
	// alpha += probChart.alphas[t - 1][fromPhone][fromSubstate]
	// * (a[substate1][phone][fromPhone][fromSubstate] + a[substate1 +
	// 1][phone][fromPhone][fromSubstate])
	// * leaveStateProb;
	// }
	// }
	// if (t < T - 1) {
	// double obsLik = merged_b[phone][substate1 / 2]
	// .evalPdf(observations[t]);
	// alpha *= obsLik;
	// }
	//
	// double[] tmp2 = new double[2];
	//
	// for (int k = 0; k < 2; k++) {
	//
	// tmp2[k] = probChart.betas[t][phone][map[k]];
	// }
	// // double combinedScore = alpha
	// // / z * (tmp2[0]+tmp2[1])
	// // * scaleFactor;
	// double mergeWeightSum = mergeWeights[phone][substate1]
	// + mergeWeights[phone][substate2];
	// double beta = (mergeWeights[phone][substate1] * tmp2[0])
	// + mergeWeights[phone][substate2] * tmp2[1];
	// beta /= mergeWeightSum;
	//
	// deltas[phone][substate1 / 2] += Math.log(alpha) + Math.log(beta)
	// + 100 * (alpha_scale + beta_scale);
	// }
	// }
	// }
	// }
	// for (int phone = 0; phone < numPhones; ++phone) {
	// for (int substate = 0; substate < numSubstatesPerState[phone]; ++substate)
	// {
	// deltas[phone][substate / 2] = jointDeltas[phone][substate / 2]
	// - deltas[phone][substate / 2];
	// }
	// }
	// return deltas;
	// }
	/**
	 * @param phoneObjectSequences
	 * @param acousticObservationSequences
	 * @return
	 */
	private double[][] computeExactDeltas(List<int[]> phoneObjectSequences,
			List<double[][]> acousticObservationSequences, double[][] mergeWeights,
			boolean doConditional) { // brute force way
		// initialize
		double[][] deltas = new double[numPhones][];
		for (int phone = 0; phone < numPhones; phone++) {
			if (numSubstatesPerState[phone] == 1)
				deltas[phone] = new double[] { Double.MAX_VALUE };
			else
				deltas[phone] = new double[numSubstatesPerState[phone] / 2];

		}

		// convert lists into arrays
		final int[][] phoneSequences = new int[phoneObjectSequences.size()][];
		final double[][][] acousticObservationSequencesAsArray = new double[acousticObservationSequences
				.size()][][];
		for (int seq = 0; seq < phoneObjectSequences.size(); ++seq) {
			phoneSequences[seq] = phoneObjectSequences.get(seq);
			acousticObservationSequencesAsArray[seq] = acousticObservationSequences
					.get(seq);
		}

		for (int mergePhone = 2; mergePhone < numPhones; mergePhone++) {
			for (short mergeSubstate1 = 0; mergeSubstate1 < numSubstatesPerState[mergePhone]; mergeSubstate1 += 2) {
				SubphoneHMM mergedHMM = this.clone();
				double weight1 = mergeWeights[mergePhone][mergeSubstate1], weight2 = mergeWeights[mergePhone][mergeSubstate1 + 1];
				double tmp = weight1 + weight2;
				if (tmp == 0)
					tmp = 1;
				weight1 /= tmp;
				weight2 /= tmp;
				int newNumSubstates = numSubstatesPerState[mergePhone] - 1;
				mergedHMM.numSubstatesPerState[mergePhone]--;
				Gaussian[] mergedB = new Gaussian[newNumSubstates];
				// double[][] mergedAfrom = new double[][];
				// double[][] mergedAto = new double[];
				// update emissions
				for (int i = 0; i < newNumSubstates; i++) {
					if (i < mergeSubstate1) {
						mergedB[i] = b[mergePhone][i].clone();
					} else if (i == mergeSubstate1) {
						mergedB[i] = b[mergePhone][i].clone();
						mergedB[i].mergeGaussian(b[mergePhone][i + 1], weight1);
					} else {
						mergedB[i] = b[mergePhone][i + 1].clone();
					}
				}
				mergedHMM.b[mergePhone] = mergedB;

				// update leave state probs: incoming
				// for (int fromP=0; fromP<numPhones; fromP++){
				// if (fromP == 1) continue;
				// int numFromSubstates = numSubstatesPerState[fromP];
				// double[] mergedC = new double[numFromSubstates];
				// for (int i=0; i<numFromSubstates; i++){
				// if (i<mergeSubstate1){
				// mergedC[i] = c[mergePhone][fromP][i];
				// } else if(i==mergeSubstate1){
				// mergedC[i] =
				// (c[mergePhone][fromP][i]*weight1)+(c[mergePhone][fromP][i+1]*weight2);
				// } else{
				// mergedC[i] = c[mergePhone][fromP][i+1];
				// }
				// }
				// mergedHMM.c[mergePhone][fromP] = mergedC;
				// }

				// update leave state probs: outging
				for (int toP = 1; toP < numPhones; toP++) {
					if (toP == mergePhone)
						continue;
					double[] mergedC = new double[newNumSubstates];
					for (int i = 0; i < newNumSubstates; i++) {
						if (i < mergeSubstate1) {
							mergedC[i] = c[toP][mergePhone][i];
						} else if (i == mergeSubstate1) {
							mergedC[i] = (c[toP][mergePhone][i] * weight1)
									+ (c[toP][mergePhone][i + 1] * weight2);
						} else {
							mergedC[i] = c[toP][mergePhone][i + 1];
						}
					}
					mergedHMM.c[toP][mergePhone] = mergedC;
				}

				// update transitions
				for (int otherP = 0; otherP < numPhones; otherP++) {
					if (otherP != mergePhone) {
						for (int otherS = 0; otherS < numSubstatesPerState[otherP]; otherS++) {
							double[] mergedAfrom = new double[newNumSubstates];
							for (int i = 0; i < newNumSubstates; i++) {
								if (i < mergeSubstate1) {
									mergedAfrom[i] = a[otherS][otherP][mergePhone][i];
									mergedHMM.a[i][mergePhone][otherP][otherS] = a[i][mergePhone][otherP][otherS];
								} else if (i == mergeSubstate1) {
									mergedAfrom[i] = (a[otherS][otherP][mergePhone][i] * weight1)
											+ (a[otherS][otherP][mergePhone][i + 1] * weight2);
									mergedHMM.a[i][mergePhone][otherP][otherS] = a[i][mergePhone][otherP][otherS]
											+ a[i + 1][mergePhone][otherP][otherS];
									// if (toP>1) mergedHMM.a[i][mergePhone][toP][toS] +=
									// a[i+1][mergePhone][toP][toS];
								} else {
									mergedAfrom[i] = a[otherS][otherP][mergePhone][i + 1];
									mergedHMM.a[i][mergePhone][otherP][otherS] = a[i + 1][mergePhone][otherP][otherS];
								}
							}
							mergedHMM.a[otherS][otherP][mergePhone] = mergedAfrom;
						}
					} else {
						for (int i = 0; i < newNumSubstates; i++) {
							if (i < mergeSubstate1) {
								// nothing to do
							} else if (i == mergeSubstate1) {
								mergedHMM.a[i][mergePhone][otherP][i] = weight1
										* (a[i][otherP][mergePhone][i] + a[i + 1][otherP][mergePhone][i])
										+ weight2
										* (a[i][otherP][mergePhone][i + 1] + a[i + 1][otherP][mergePhone][i + 1]);
							} else {
								mergedHMM.a[i][mergePhone][otherP][i] = a[i + 1][mergePhone][otherP][i + 1];
								mergedHMM.a[i][mergePhone][otherP] = a[i + 1][mergePhone][otherP];
							}
						}
					}
				}

				TrainingProbChart probChart = new TrainingProbChart(mergedHMM,
						emissionAttenuation);
				PosteriorProbChart totalProbChart = new PosteriorProbChart(mergedHMM);
				double mergedLL = 0;
				for (int seq = 0; seq < phoneSequences.length; ++seq) {
					this.currPhoneSequences = phoneSequences[seq];
					probChart.init(phoneSequences[seq],
							acousticObservationSequencesAsArray[seq], seq);
					probChart.calc();
					if (doConditional) {
						totalProbChart.init(acousticObservationSequencesAsArray[seq]);
						totalProbChart.calc();
					}
					double thisLL = probChart.getLogLikelihood();
					if (doConditional)
						thisLL -= totalProbChart.getLogLikelihood();
					if (!SloppyMath.isVeryDangerous(thisLL)) {
						mergedLL += thisLL;
					}
				}

				// mergedLL *=-1;
				if (mergedLL == 0)
					mergedLL = Double.MAX_VALUE;
				deltas[mergePhone][mergeSubstate1 / 2] = mergedLL;
				System.out.println("Phone\t" + mergePhone + "\t"
						+ (mergedLL - loglikelihood));
			}
		}

		for (int phone = 0; phone < numPhones; ++phone) {
			for (int substate = 0; substate < numSubstatesPerState[phone]; substate += 2) {
				deltas[phone][substate / 2] -= loglikelihood;
			}
		}
		return deltas;
	}

	/**
	 * @param phoneObjectSequences
	 * @param acousticObservationSequences
	 * @return
	 */
	// private double[][] computeDoubleBuggyDeltas(List<int[]>
	// phoneObjectSequences,
	// List<double[][]> acousticObservationSequences, double[][] mergeWeights,
	// Gaussian[][] merged_b) {
	// // initialize
	// double[][] deltas = new double[numPhones][];
	// for (int phone = 0; phone < numPhones; phone++) {
	// if (numSubstatesPerState[phone] == 1)
	// deltas[phone] = new double[] { Double.MAX_VALUE };
	// else
	// deltas[phone] = new double[numSubstatesPerState[phone] / 2];
	//
	// }
	//
	// // convert lists into arrays
	// final int[][] phoneSequences = new int[phoneObjectSequences.size()][];
	// final double[][][] acousticObservationSequencesAsArray = new
	// double[acousticObservationSequences
	// .size()][][];
	// for (int seq = 0; seq < phoneObjectSequences.size(); ++seq) {
	// phoneSequences[seq] = phoneObjectSequences.get(seq);
	// acousticObservationSequencesAsArray[seq] = acousticObservationSequences
	// .get(seq);
	// }
	//
	// // count up the posterior counts
	// TrainingProbChart probChart = createTrainingChart();
	//
	// for (int seq = 0; seq < phoneSequences.length; ++seq) {
	// this.currPhoneSequences = phoneSequences[seq];
	// final int T = acousticObservationSequencesAsArray[seq].length;
	// final double[][] observations = acousticObservationSequencesAsArray[seq];
	// probChart.init(phoneSequences[seq],
	// acousticObservationSequencesAsArray[seq], seq);
	// probChart.calc();
	// double z = probChart.getUnscaledLikelihood();
	// double z_scale = probChart.alphasScale[T - 1];
	//
	// for (int t = 0; t < T - 1; t++) {
	// for (int phone : probChart.allowedPhonesAtTime(t)) {
	// int nSubstates = numSubstatesPerState[phone];
	// if (nSubstates == 1) {
	// continue;
	// }
	//
	// double[] separatedScores = new double[nSubstates];
	//
	// // calculate separated scores
	// for (int substate = 0; substate < nSubstates; substate++) {
	// separatedScores[substate] = probChart.getGamma(phone, substate, t);
	// }
	//
	// double alpha_scale = probChart.alphasScale[t - 1];
	// double beta_scale = probChart.betasScale[t];
	// double scaleFactor = probChart.getScaleFactor(alpha_scale
	// + beta_scale - z_scale);
	// // calculate merged scores
	// for (short substate1 = 0; substate1 < nSubstates; substate1 += 2) {
	// short substate2 = (short) (substate1 + 1);
	// short[] map = new short[] { substate1, substate2 };
	// double alpha = 0.0;
	//
	// for (int fromPhone : probChart.allowedPhonesAtTime(t - 1)) {
	// for (int fromSubstate = 0; fromSubstate < numSubstatesPerState[fromPhone];
	// ++fromSubstate) {
	// double leaveStateProb = c[phone][fromPhone][fromSubstate];
	// alpha += probChart.alphas[t - 1][fromPhone][fromSubstate]
	// * (a[substate1][phone][fromPhone][fromSubstate] + a[substate1 +
	// 1][phone][fromPhone][fromSubstate])
	// * leaveStateProb;
	// }
	// }
	// if (t < T - 1) {
	// double obsLik = merged_b[phone][substate1 / 2]
	// .evalPdf(observations[t]);
	// alpha *= obsLik;
	// }
	//
	// double[] tmp2 = new double[2];
	//
	// for (int k = 0; k < 2; k++) {
	//
	// tmp2[k] = probChart.betas[t][phone][map[k]];
	// }
	// // deltas[phone][substate1/2] = Math.log(alpha
	// // / z * (tmp2[0]+tmp2[1])
	// // * scaleFactor);
	// deltas[phone][substate1 / 2] += Math.log(alpha)
	// + Math.log(tmp2[0] + tmp2[1]) + 100
	// * (alpha_scale + beta_scale);
	//
	// // if (combinedScore!=0 && separatedScoreSum!=0)
	// // deltas[phone][substate1/2] +=
	// // Math.log(separatedScoreSum/combinedScore);
	// }
	// }
	// }
	// }
	//
	// return deltas;
	// }
	/**
	 * @param phoneObjectSequences
	 * @param acousticObservationSequences
	 * @return
	 */
	// private double[][] computeDoubleBuggyConditionalDeltas(
	// List<int[]> phoneObjectSequences,
	// List<double[][]> acousticObservationSequences, double[][] mergeWeights,
	// Gaussian[][] merged_b, double[][] jointDeltas) {
	// // initialize
	// double[][] deltas = new double[numPhones][];
	// for (int phone = 0; phone < numPhones; phone++) {
	// if (numSubstatesPerState[phone] == 1)
	// deltas[phone] = new double[] { Double.MAX_VALUE };
	// else
	// deltas[phone] = new double[numSubstatesPerState[phone] / 2];
	//
	// }
	//
	// // convert lists into arrays
	// final int[][] phoneSequences = new int[phoneObjectSequences.size()][];
	// final double[][][] acousticObservationSequencesAsArray = new
	// double[acousticObservationSequences
	// .size()][][];
	// for (int seq = 0; seq < phoneObjectSequences.size(); ++seq) {
	// phoneSequences[seq] = phoneObjectSequences.get(seq);
	// acousticObservationSequencesAsArray[seq] = acousticObservationSequences
	// .get(seq);
	// }
	//
	// // count up the posterior counts
	// PosteriorProbChart probChart = new PosteriorProbChart(this);
	// TrainingProbChart trainingProbChart = createTrainingChart();
	//
	// for (int seq = 0; seq < phoneSequences.length; ++seq) {
	// this.currPhoneSequences = phoneSequences[seq];
	// final int T = acousticObservationSequencesAsArray[seq].length;
	// final double[][] observations = acousticObservationSequencesAsArray[seq];
	// trainingProbChart.init(phoneSequences[seq],
	// acousticObservationSequencesAsArray[seq], seq);
	// probChart.init(acousticObservationSequencesAsArray[seq]);
	// probChart.calc();
	// double z = probChart.getUnscaledLikelihood();
	// double z_scale = probChart.alphasScale[T - 1];
	//
	// for (int t = 0; t < T - 1; t++) {
	//
	// // precompute the posterior mass for a given phone
	// // double[] otherMass = DoubleArrays.constantArray(0.0, numPhones);
	// // double totalMass = 0.0;
	// // for (int phone : probChart.allowedPhonesAtTime(t))
	// // {
	// // for (int substate = 0; substate < numSubstatesPerState[phone];
	// // ++substate)
	// // {
	// // double gamma = probChart.getGamma(phone, substate, t);
	// // otherMass[phone] += gamma;
	// // totalMass += gamma;
	// // }
	// // }
	// for (int phone : trainingProbChart.allowedPhonesAtTime(t)) {
	// int nSubstates = numSubstatesPerState[phone];
	// if (nSubstates == 1) {
	// continue;
	// }
	//
	// double alpha_scale = probChart.alphasScale[t - 1];
	// double beta_scale = probChart.betasScale[t];
	// double scaleFactor = probChart.getScaleFactor(alpha_scale
	// + beta_scale - z_scale);
	// // calculate merged scores
	// for (short substate1 = 0; substate1 < nSubstates; substate1 += 2) {
	// short substate2 = (short) (substate1 + 1);
	// short[] map = new short[] { substate1, substate2 };
	// double alpha = 0.0;
	//
	// for (int fromPhone : probChart.allowedPhonesAtTime(t - 1)) {
	// for (int fromSubstate = 0; fromSubstate < numSubstatesPerState[fromPhone];
	// ++fromSubstate) {
	// double leaveStateProb = c[phone][fromPhone][fromSubstate];
	// alpha += probChart.alphas[t - 1][fromPhone][fromSubstate]
	// * (a[substate1][phone][fromPhone][fromSubstate] + a[substate1 +
	// 1][phone][fromPhone][fromSubstate])
	// * leaveStateProb;
	// }
	// }
	// if (t < T - 1) {
	// double obsLik = merged_b[phone][substate1 / 2]
	// .evalPdf(observations[t]);
	// alpha *= obsLik;
	// }
	//
	// double[] tmp2 = new double[2];
	//
	// for (int k = 0; k < 2; k++) {
	//
	// tmp2[k] = probChart.betas[t][phone][map[k]];
	// }
	// // double combinedScore = alpha
	// // / z * (tmp2[0]+tmp2[1])
	// // * scaleFactor;
	//
	// deltas[phone][substate1 / 2] += Math.log(alpha)
	// + Math.log(tmp2[0] + tmp2[1]) + 100
	// * (alpha_scale + beta_scale);
	// }
	// }
	// }
	// }
	// for (int phone = 0; phone < numPhones; ++phone) {
	// for (int substate = 0; substate < numSubstatesPerState[phone]; ++substate)
	// {
	// deltas[phone][substate / 2] = jointDeltas[phone][substate / 2]
	// - deltas[phone][substate / 2];
	// }
	// }
	// return deltas;
	// }
	public boolean Save(String fileName) {
		try {
			// here's some code from online; it looks good and gzips the output!
			// there's a whole explanation at
			// http://www.ecst.csuchico.edu/~amk/foo/advjava/notes/serial.html
			// Create the necessary output streams to save the scribble.
			FileOutputStream fos = new FileOutputStream(fileName); // Save to file
			GZIPOutputStream gzos = new GZIPOutputStream(fos); // Compressed
			ObjectOutputStream out = new ObjectOutputStream(gzos); // Save objects
			out.writeObject(this); // Write the mix of grammars
			out.flush(); // Always flush the output.
			out.close(); // And close the stream.
		} catch (IOException e) {
			LogInfo.warning(e);
			return false;
		}
		return true;
	}

	public static SubphoneHMM Load(String fileName) {
		SubphoneHMM pData = null;
		try {
			FileInputStream fis = new FileInputStream(fileName); // Load from file
			GZIPInputStream gzis = new GZIPInputStream(fis); // Compressed
			ObjectInputStream in = new ObjectInputStream(gzis); // Load objects
			pData = (SubphoneHMM) in.readObject(); // Read the mix of grammars
			in.close(); // And close the stream.
		} catch (IOException e) {
			LogInfo.warning("IOException\n" + e);
			return null;
		} catch (ClassNotFoundException e) {
			LogInfo.warning("Class not found" + e);
			return null;
		}
		return pData;
	}

	public double getLoglikelihood() {
		return loglikelihood;
	}

	/**
	 * Smooths the HMM (transition probabilites and mean and covariance of the
	 * emissions)
	 * 
	 */

	public void smoothTransitions() {
		double[][][][] new_a = new double[maxNumSubstates][numPhones][numPhones][maxNumSubstates];
		double[][][] new_c = new double[numPhones][numPhones][maxNumSubstates];

		// for (int phone = 2; phone < numPhones; ++phone) {
		// // since we add the original with weight 1 rather than 1-smoothingFactor
		// double smoothFraction = smoothingFactor/( (1-smoothingFactor) *
		// numSubstatesPerState[phone]);
		// for (int substate1 = 0; substate1 < numSubstatesPerState[phone];
		// ++substate1) {
		// new_b[phone][substate1] = b[phone][substate1].clone();
		// for (int substate2 = 0; substate2 < numSubstatesPerState[phone];
		// ++substate2) {
		// if (substate2==substate1) continue; // we copied this one already
		// new_b[phone][substate1].mergeGaussian(b[phone][substate2],
		// smoothFraction);
		// }
		// }
		// }

		for (int toPhone = 0; toPhone < numPhones; ++toPhone) {
			for (int fromPhone = 0; fromPhone < numPhones; ++fromPhone) {
				double smoothFraction = transitionSmooth
						/ numSubstatesPerState[fromPhone];

				for (int substate1 = 0; substate1 < numSubstatesPerState[fromPhone]; ++substate1) {
					new_c[toPhone][fromPhone][substate1] = c[toPhone][fromPhone][substate1]
							* (1.0 - transitionSmooth);
					for (int substate2 = 0; substate2 < numSubstatesPerState[fromPhone]; ++substate2) {
						// if (substate2==substate1) continue; // we copied this one already
						new_c[toPhone][fromPhone][substate1] += c[toPhone][fromPhone][substate2]
								* smoothFraction;
					}
				}
			}
		}

		for (int toPhone = 0; toPhone < numPhones; ++toPhone) {
			for (int k = 0; k < numSubstatesPerState[toPhone]; ++k) {
				for (int fromPhone = 0; fromPhone < numPhones; ++fromPhone) {
					double smoothFraction = transitionSmooth
							/ numSubstatesPerState[fromPhone];

					for (int substate1 = 0; substate1 < numSubstatesPerState[fromPhone]; ++substate1) {
						new_a[k][toPhone][fromPhone][substate1] = a[k][toPhone][fromPhone][substate1]
								* (1.0 - transitionSmooth);
						for (int substate2 = 0; substate2 < numSubstatesPerState[fromPhone]; ++substate2) {
							new_a[k][toPhone][fromPhone][substate1] += a[k][toPhone][fromPhone][substate2]
									* smoothFraction;
						}
					}
				}
			}
		}

		this.a = new_a;
		// this.b = new_b;
		this.c = new_c;

	}

	public void wishartSmooth(double[][] posteriorCounts) {

		for (int phone = 2; phone < numPhones; ++phone) {
			double[] counts = posteriorCounts[phone];

			double[] priorMean = new double[gaussianDim];

			for (int substate = 0; substate < numSubstatesPerState[phone]; ++substate) {
				ArrayUtil.addInPlace(priorMean, ArrayUtil.multiply(b[phone][substate]
						.getMean(), counts[substate]));
			}
			double totalCounts = ArrayUtil.sum(counts);
			// smooth the mean
			if (meanSmooth > 0.0) {
				ArrayUtil.multiplyInPlace(priorMean, 1.0 / totalCounts);
				for (int substate = 0; substate < numSubstatesPerState[phone]; ++substate) {
					double[] newMean = b[phone][substate].getMean().clone();

					ArrayUtil.addInPlace(newMean, ArrayUtil.multiply(priorMean,
							meanSmooth));
					ArrayUtil.multiplyInPlace(newMean, 1.0 / (meanSmooth + 1.0));
					b[phone][substate] = useFullGaussian ? new FullCovGaussian(newMean,
							b[phone][substate].getCovariance()) : new DiagonalCovGaussian(
							newMean, b[phone][substate].getCovariance());
				}
			}

			// smooth the variance
			if (varSmooth > 0.0 || minVarSmooth > 0.0) {
				double varSmooth = this.varSmooth;

				double[][] priorCov = new double[gaussianDim][gaussianDim];
				for (int substate = 0; substate < numSubstatesPerState[phone]; ++substate) {
					ArrayUtil.addInPlace(priorCov, multiply(b[phone][substate]
							.getCovariance(), counts[substate]));
				}
				multiplyInPlace(priorCov, varSmooth / totalCounts);

				// System.out.println("Prior covariance for " + phone + "::");
				// printMatrix(priorCov);

				for (int substate = 0; substate < numSubstatesPerState[phone]; ++substate) {

					double[][] newCov = ArrayUtil.clone(b[phone][substate]
							.getCovariance()); // .clone() operates only along the first dim
					ArrayUtil.addInPlace(newCov, priorCov);
					double[][] diagonalPriorCov = new double[gaussianDim][gaussianDim];
					for (int i = 0; i < gaussianDim; ++i) {
						diagonalPriorCov[i][i] = 1.0;
					}
					double denom = (posteriorCounts[phone][substate] + minVarSmooth);
					multiplyInPlace(diagonalPriorCov, minVarSmooth / denom);
					ArrayUtil.addInPlace(newCov, diagonalPriorCov);

					// System.out.println("Unsmoothed covariance for " + phone + ":" +
					// substate + "::");
					// printMatrix(newCov);

					double[] diff = ArrayUtil.subtract(b[phone][substate].getMean(),
							priorMean);
					ArrayUtil.addInPlace(newCov, FullCovGaussian.scaledOuterSelfProduct(
							diff, meanSmooth));
					multiplyInPlace(newCov,
							1.0 / (varSmooth + 1.0 + minVarSmooth / denom));
					// System.out.println("Smoothed covariance for " + phone + ":" +
					// substate + "::");
					// printMatrix(newCov);
					b[phone][substate] = useFullGaussian ? new FullCovGaussian(
							b[phone][substate].getMean(), newCov) : new DiagonalCovGaussian(
							b[phone][substate].getMean(), newCov);
				}
			}
		}

	}

	private static void printMatrix(double[][] a) {
		int len = 5;
		for (int i = 0; i < len; ++i) {
			System.out.print("[");
			for (int j = 0; j < len; ++j) {
				System.out.print(a[i][j] + "\t,");
			}
			System.out.println("]");
		}
	}

	private static double[][] multiply(double[][] a, double c) {
		double[][] retVal = new double[a.length][];
		for (int i = 0; i < a.length; ++i) {
			retVal[i] = ArrayUtil.multiply(a[i], c);
		}
		return retVal;
	}

	private static void multiplyInPlace(double[][] a, double c) {

		for (int i = 0; i < a.length; ++i) {
			ArrayUtil.multiplyInPlace(a[i], c);
		}

	}

	public double getMeanSmooth() {
		return meanSmooth;
	}

	public void setMeanSmooth(double meanSmooth) {
		this.meanSmooth = meanSmooth;
	}

	public double getMinVarSmooth() {
		return minVarSmooth;
	}

	public void setMinVarSmooth(double minVarSmooth) {
		this.minVarSmooth = minVarSmooth;
	}

	public double getTransitionSmooth() {
		return transitionSmooth;
	}

	public void setTransitionSmooth(double transitionSmooth) {
		this.transitionSmooth = transitionSmooth;
	}

	public double getVarSmooth() {
		return varSmooth;
	}

	public void setVarSmooth(double varSmooth) {
		this.varSmooth = varSmooth;
	}

	/**
	 * @param outName
	 */
	public void setWritePosteriors(String outName) {
		this.printPosteriors = true;
		this.filenameForPosteriors = outName;
	}

}
