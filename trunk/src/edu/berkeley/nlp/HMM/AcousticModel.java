package edu.berkeley.nlp.HMM;

import edu.berkeley.nlp.prob.Gaussian;
import fig.basic.Indexer;

/**
 * 
 * @author aria42
 * 
 */
public interface AcousticModel {

	public double getObservationScore(int phone, int subPhone, double[] signal);

	public double getTransitionScore(int phone, int subphone, int nextPhone,
			int nextSubphone);

	public int getNumStates(int phone);

	public Indexer<Phone> getPhoneIndexer();

	public int getMaxNumberOfSubstates();

	public int getStartPhoneIndex();

	public int getEndPhoneIndex();

	public int getSilencePhone();
	
	public static class SubphoneHMMWrapper implements AcousticModel {

		SubphoneHMM hmm;

		public SubphoneHMMWrapper(String pathToModel) {
			try {
				hmm = SubphoneHMM.Load(pathToModel);
				// hmm.boostTransitionProbabilities(3.0, 1.0);//
				// boostTransitionProbabilities(4.0);
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(0);
			}
		}

		public Gaussian[][] getB() {
			return hmm.b;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see edu.berkeley.nlp.HMM.AcousticModel#getNumstates(int)
		 */
		public int getNumStates(int phone) {
			return hmm.numSubstatesPerState[phone];
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see edu.berkeley.nlp.HMM.AcousticModel#getObservationScore(int, int,
		 *      double[])
		 */
		public double getObservationScore(int phone, int subPhone, double[] signal) {
			assert subPhone < hmm.numSubstatesPerState[phone];
			assert signal != null;
			return hmm.b[phone][subPhone].evalLogPdf(signal);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see edu.berkeley.nlp.HMM.AcousticModel#getPhoneIndexer()
		 */
		public Indexer<Phone> getPhoneIndexer() {
			return hmm.phoneIndexer;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see edu.berkeley.nlp.HMM.AcousticModel#getTransitionScore(int, int, int,
		 *      int)
		 */
		public double getTransitionScore(int phone, int subphone, int nextPhone,
				int nextSubphone) {
			return hmm.a[nextSubphone][nextPhone][phone][subphone]
					* hmm.c[nextPhone][phone][subphone];
		}

		/*
		 * 
		 */
		public int getMaxNumberOfSubstates() {
			return hmm.maxNumSubstates;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see edu.berkeley.nlp.HMM.AcousticModel#getEndPhoneIndex()
		 */
		public int getEndPhoneIndex() {
			// TODO Auto-generated method stub
			return hmm.phoneIndexer.indexOf(new Phone("*END*"));
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see edu.berkeley.nlp.HMM.AcousticModel#getStartPhoneIndex()
		 */
		public int getStartPhoneIndex() {
			return hmm.phoneIndexer.indexOf(new Phone("*START*"));
		}
		public int getPhoneIndex() {
			return hmm.phoneIndexer.indexOf(new Phone("*SIL*"));
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see edu.berkeley.nlp.HMM.AcousticModel#getTotalStates()
		 */
		public int getTotalStates() {
			int s = 0;
			for (int i = 0; i < getPhoneIndexer().size(); i++) {
				s += getNumStates(i);
			}
			return s;
		}

		/* (non-Javadoc)
		 * @see edu.berkeley.nlp.HMM.AcousticModel#getSilencePhone()
		 */
		public int getSilencePhone() {
			return hmm.phoneIndexer.indexOf(new Phone("*SIL*"));
//			return 0;
		}

	}

	/**
	 * 
	 */
	public int getTotalStates();

}