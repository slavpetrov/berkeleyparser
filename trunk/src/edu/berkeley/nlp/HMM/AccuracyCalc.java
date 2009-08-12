/**
 * 
 */
package edu.berkeley.nlp.HMM;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author adpauls
 * 
 */
public abstract class AccuracyCalc {

	protected static final Map<Phone, Phone> $48to39Map = new HashMap<Phone, Phone>();
	static {
		$48to39Map.put(new Phone("epi"), new Phone("sil"));
		$48to39Map.put(new Phone("cl"), new Phone("sil"));
		$48to39Map.put(new Phone("vcl"), new Phone("sil"));
		$48to39Map.put(new Phone("el"), new Phone("l"));
		$48to39Map.put(new Phone("en"), new Phone("n"));
		$48to39Map.put(new Phone("sh"), new Phone("zh"));
		$48to39Map.put(new Phone("ao"), new Phone("aa"));
		$48to39Map.put(new Phone("ih"), new Phone("ix"));
		$48to39Map.put(new Phone("ah"), new Phone("ax"));

	}
	
	private List<Double> accuracies = new ArrayList<Double>();

	protected PhoneIndexer phoneIndexer;

	protected List<List<Phone>> goldSeqs;
	protected List<List<Phone>> testSeqs;
	
	public AccuracyCalc(PhoneIndexer phoneIndexer, List<List<Phone>> goldSeqs, List<List<Phone>> testSeqs) {
		this.phoneIndexer = phoneIndexer;
		this.goldSeqs = goldSeqs;
		this.testSeqs = testSeqs;
	}

	public abstract double getAccuracy();
	
	protected 
	void addAccuracy(double accuracy)
	{
		accuracies.add(accuracy);
	}
	
	public List<Double> getIndividualAccuracies()
	{
		return accuracies;
	}
	
	public static boolean staticEqual39(Phone p1, Phone p2, PhoneIndexer phoneIndexer) {
		p1 = phoneIndexer.get(phoneIndexer.indexOf(p1));
		p2 = phoneIndexer.get(phoneIndexer.indexOf(p2));
		// try also mapping down both
		Phone p = $48to39Map.get(p2);
//		if (p!=null && p.unnannotatedEquals($48to39Map.get(p1))) return true;
		return p1.unnannotatedEquals(p2)
				|| p1.unnannotatedEquals($48to39Map.get(p2))
				|| p2.unnannotatedEquals($48to39Map.get(p1));
	}
	
	protected boolean equal39(Phone p1, Phone p2)
	{
		return staticEqual39(p1, p2, phoneIndexer);
	}
	
	public static void printSeq(List<Phone> seq, List<Phone> goldSeq, PhoneIndexer phoneIndexer)
	{
//		System.out.println(seq.toString());
		System.out.print("[");
		for (int i = 0; i < seq.size(); ++i)
		{
			boolean match = staticEqual39(seq.get(i),goldSeq.get(i),phoneIndexer);
			Phone phone = seq.get(i);//mapDownPhone(seq.get(i));
			String phoneString = phone.toString();
			
			if (!match) phoneString = phoneString.toUpperCase();
			System.out.print(phone + ",");
		}
		System.out.println("]");
	}

	static Phone mapDownPhone(Phone phone) {
		phone = (phone instanceof AnnotatedPhone) ? ((AnnotatedPhone)phone).getUnnannotatedPhone() : phone;
		phone = PhoneIndexer.mapDown(phone);
		if ($48to39Map.containsKey(phone)) phone = $48to39Map.get(phone);
		return phone;
	}
	
	public static void collapseAndPrintSeq(List<Phone> seq, List<Phone> goldSeq, PhoneIndexer phoneIndexer)
	{
		System.out.print("[");
		seq = PhoneIndexer.getCollapsedPhoneList(seq, false);
		goldSeq = PhoneIndexer.getCollapsedPhoneList(goldSeq, false);
		for (int i = 0; i < seq.size(); ++i)
		{
			
		
			Phone phone = mapDownPhone(seq.get(i));
			
			System.out.print(phone + ",");
		}
		System.out.println("]");
	}

	
}
