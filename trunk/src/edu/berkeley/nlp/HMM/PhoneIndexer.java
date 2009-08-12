/**
 * 
 */
package edu.berkeley.nlp.HMM;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fig.basic.Indexer;

/**
 * @author adpauls
 * 
 */
public class PhoneIndexer extends Indexer<Phone> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5405315288133861414L;
	static final Map<Phone, Phone> allto48Map = new HashMap<Phone, Phone>();

	static {
		allto48Map.put(new Phone("ux"), new Phone("uw"));
		allto48Map.put(new Phone("axr"), new Phone("er"));
		allto48Map.put(new Phone("em"), new Phone("m"));
		allto48Map.put(new Phone("nx"), new Phone("n"));
		allto48Map.put(new Phone("eng"), new Phone("ng"));
		allto48Map.put(new Phone("hv"), new Phone("hh"));
		allto48Map.put(new Phone("pcl"), new Phone("cl"));
		allto48Map.put(new Phone("tcl"), new Phone("cl"));
		allto48Map.put(new Phone("kcl"), new Phone("cl"));
		allto48Map.put(new Phone("qcl"), new Phone("cl"));
		allto48Map.put(new Phone("bcl"), new Phone("vcl"));
		allto48Map.put(new Phone("dcl"), new Phone("vcl"));
		allto48Map.put(new Phone("gcl"), new Phone("vcl"));
		allto48Map.put(new Phone("h#"), new Phone("sil"));
		allto48Map.put(new Phone("#h"), new Phone("sil"));
		allto48Map.put(new Phone("pau"), new Phone("sil"));

	}

	@Override
	public boolean add(Phone e) {
		Phone phone = mapDown(e);

		boolean retVal = super.add(phone);

		return retVal;
	}

	@Override
	public boolean contains(Object o) {
		return super.contains(mapDown((Phone) o));
	}

	@Override
	public int indexOf(Object o) {

		Phone p = (o instanceof AnnotatedPhone) ? ((AnnotatedPhone) o)
				.getUnnannotatedPhone() : (Phone) o;
		return super.indexOf(mapDown(p));
	}

	public List<int[]> indexSequences(List<List<Phone>> phoneSequences) {
		List<int[]> retVal = new ArrayList<int[]>();
		for (List<Phone> phoneSeq : phoneSequences) {
			int[] currSeq = new int[phoneSeq.size()];
			int i = 0;
			for (Phone p : phoneSeq) {
				int indexOf = indexOf(p);
				currSeq[i++] = indexOf;
			}
			retVal.add(currSeq);
		}
		return retVal;
	}

	public static List<Phone> getCollapsedPhoneList(List<Phone> seq, boolean training) {
		return getCollapsedPhoneList(seq, training, 1);
	}
	
	public static List<Phone> getCollapsedPhoneList(List<Phone> seq, boolean training, int replicates) {
		List<Phone> collapsedSeq1 = new ArrayList<Phone>();
		collapsedSeq1 = new ArrayList<Phone>();
		for (int k = 0; k < seq.size(); ++k) {
			Phone phone = seq.get(k);
			phone = training ? mapDown(phone) : AccuracyCalc.mapDownPhone(phone);
			boolean end = k == seq.size() - 1;
			Phone nextPhone = end ? null : seq.get(k+1);
			//if (!end) 
				nextPhone =  training ? mapDown(nextPhone) : AccuracyCalc.mapDownPhone(nextPhone);

			if (end || !phone.unnannotatedEquals(nextPhone)) {
				int r = (phone.equals(Corpus.START_PHONE) || phone.equals(Corpus.END_PHONE)) ? 1 : replicates;
				{
				for (int i = 0; i < r; i++)
				{
					collapsedSeq1.add(phone);
				}
				}
			}
		}
		return collapsedSeq1;
	}

	public static Phone mapDown(Phone phone) {
		return allto48Map.containsKey(phone) ? allto48Map.get(phone) : phone;
	}

	public static List<List<Phone>> getCollapsedPhoneLists(List<List<Phone>> seqs, boolean training) {
		return getCollapsedPhoneLists(seqs, training,1);
	}
	
	public static List<List<Phone>> getCollapsedPhoneLists(List<List<Phone>> seqs, boolean training, int replicates) {
		List<List<Phone>> retVal = new ArrayList<List<Phone>>();
		for (List<Phone> seq : seqs) {
			retVal.add(getCollapsedPhoneList(seq, training, replicates));
		}
		return retVal;
	}
	
	public static List<int[]> phonesToIndexes(List<int[]> phones)
	{
		List<int[]> retVal = new ArrayList<int[]>(phones.size());
		
		for (int[] phoneSequences : phones)
		{
			int n = 0;
			int[] x = new int[phoneSequences.length];
			for (int i = 0; i < phoneSequences.length; ++i)
			{
				if (i < phoneSequences.length - 1 && phoneSequences[i] == phoneSequences[i+1])
				{
					x[i] = n;
				}
				else
				{
					x[i] = n++;
				}
			}
			retVal.add(x);
		}
		return retVal;
	}

}
