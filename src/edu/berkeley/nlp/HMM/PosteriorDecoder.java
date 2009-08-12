/**
 * 
 */
package edu.berkeley.nlp.HMM;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * @author adpauls
 *
 */
public class PosteriorDecoder implements Decoder{
	
	
	private SubphoneHMM hmm;
	
	private boolean posteriorTable;
	
	private static class DoubleComparator implements Comparator<Double>
	{

		/* (non-Javadoc)
		 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		 */
		public int compare(Double arg0, Double arg1) {
			return -arg0.compareTo(arg1);
		}
		
	}
	
	private List<List<Phone>> actualPhoneSequences;

	private double emissionAttenuationCoefficient;
	public PosteriorDecoder(SubphoneHMM hmm, boolean posteriorTable, List<List<Phone>> actualPhoneSequences, double emissionAttenuationCoefficient)
	{
		this.hmm = hmm;
		this.posteriorTable = posteriorTable;
		this.actualPhoneSequences = actualPhoneSequences;
		this.emissionAttenuationCoefficient = emissionAttenuationCoefficient;
	}
	
	public List<List<Phone>> decode(List<double[][]> obsSequences) {
		System.out.println("Starting posterior sum decoding");
		List<List<Phone>> retVal = new ArrayList<List<Phone>>();
		

		PosteriorProbChart probChart = new PosteriorProbChart(hmm);
		int k = 0;
		for (double[][] o : obsSequences) {
			System.out.print(".");
			List<Phone> currPhones = new ArrayList<Phone>();
			final int T = o.length;
			List<SortedMap<Double,Integer>> posteriorTable = new ArrayList<SortedMap<Double,Integer>>();

			probChart.init(o);
			probChart.calc();
			for (int t = 0; t < T; ++t) {
				SortedMap<Double,Integer> currPosteriors = new TreeMap<Double,Integer>(new DoubleComparator());
				double max = Double.NEGATIVE_INFINITY;
				int maxPhone = -1;

				for (int phone = 0; phone < hmm.numPhones; ++phone) {
					double sum = 0.0;
					for (int substate = 0; substate < hmm.numSubstatesPerState[phone]; ++substate) {
						double gamma = probChart.getGamma(phone, substate, t);
						sum += gamma;

					}
					currPosteriors.put(sum,phone);
					if (sum > max) {
						max = sum;
						maxPhone = phone;

					}
				}
				currPhones.add(hmm.phoneIndexer.get(maxPhone));
				
				posteriorTable.add(currPosteriors);
			}
			if (this.posteriorTable) printPosteriorTable(posteriorTable, actualPhoneSequences.get(k));
			++k;
			retVal.add(currPhones);
		}
		System.out.println();
		return retVal;
	}

	/**
	 * @param posteriorTable2
	 */
	private  void printPosteriorTable(List<SortedMap<Double,Integer>> posteriorTable, List<Phone> actualSequence) {
		System.out.print("<table border=1><tr><th></th>");
		NumberFormat f = new DecimalFormat("0.###E0");
		
		for (int i = 0; i < 5; ++i)
		{
			System.out.print("<th>" + i + "th</th>");
		}
		System.out.print("<th> real </th>");
		System.out.println();
		for (int t = 0; t < posteriorTable.size(); ++t)
		{
			boolean bold = 
			posteriorTable.get(t).get(posteriorTable.get(t).firstKey()) == hmm.phoneIndexer.indexOf(actualSequence.get(t))
					;
			System.out.print("<tr><td>" + t + "</td>");
			Iterator<Double> iter = posteriorTable.get(t).keySet().iterator();
			for (int i = 0; i < 5; ++i)
			{
				if (!iter.hasNext()) break;
				double x = iter.next();
				String num = f.format(x);
				num = num + " (" + hmm.phoneIndexer.get(posteriorTable.get(t).get(x)).toString().trim() + ")";
			
				if (bold) num = "<b> " + num + "</b>";
				System.out.print("<td>" + num + "</td>");
			}
			for (Double x : posteriorTable.get(t).keySet())
			{
				if (posteriorTable.get(t).get(x) == hmm.phoneIndexer.indexOf(actualSequence.get(t)))
				{
					
					String num = f.format(x);
					num = num + " (" + hmm.phoneIndexer.get(posteriorTable.get(t).get(x)).toString().trim() + ")";
					if (bold) num = "<b> " + num + "</b>";
					
					System.out.print("<td>" + num + "</td>");
				}
			}
			System.out.println();
		}
		System.out.println("</table>");
		
	}
//	<table border=1><tr><th><a name=sil> sil(total appearances: 8258.0)
//	</th><tr><td>22 23 29 12 8 24 25 5 </td><td><a href=#left-sil22>LEFT</a></td><td><a href=#right-sil5>RIGHT</a></a></td><td>536 = 6%</td></tr>
//	<tr><td>3 15 30 17 </td><td><a href=#left-sil3>LEFT</a></td><td><a href=#right-sil17>RIGHT</a></a></td><td>246 = 2%</td></tr>
//
//	<tr><td>22 23 28 26 4 </td><td><a href=#left-sil22>LEFT</a></td><td><a href=#right-sil4>RIGHT</a></a></td><td>171 = 2%</td></tr>
//	<tr><td>0 1 15 30 17 </td><td><a href=#left-sil0>LEFT</a></td><td><a href=#right-sil17>RIGHT</a></a></td><td>151 = 1%</td></tr>
//	<tr><td>22 23 28 9 8 24 25 5 </td><td><a href=#left-sil22>LEFT</a></td><td><a href=#right-sil5>RIGHT</a></a></td><td>118 = 1%</td></tr>
//	<tr><td>1 30 16 </td><td><a href=#left-sil1>LEFT</a></td><td><a href=#right-sil16>RIGHT</a></a></td><td>107 = 1%</td></tr>
//
//	<tr><td>3 9 28 18 </td><td><a href=#left-sil3>LEFT</a></td><td><a href=#right-sil18>RIGHT</a></a></td><td>104 = 1%</td></tr>
//	<tr><td>22 23 29 12 8 26 4 </td><td><a href=#left-sil22>LEFT</a></td><td><a href=#right-sil4>RIGHT</a></a></td><td>103 = 1%</td></tr>
//	<tr><td>0 1 30 16 </td><td><a href=#left-sil0>LEFT</a></td><td><a href=#right-sil16>RIGHT</a></a></td><td>100 = 1%</td></tr>
//	<tr><td>2 15 30 17 </td><td><a href=#left-sil2>LEFT</a></td><td><a href=#right-sil17>RIGHT</a></a></td><td>98 = 1%</td></tr>
//
//	</table>
	
//	public List<List<AnnotatedPhone>> posteriorDecode(
//		List<double[][]> obsSequences) {
//	List<List<AnnotatedPhone>> retVal = new ArrayList<List<AnnotatedPhone>>();
//
//	PosteriorProbChart probChart = new PosteriorProbChart(this);
//	for (double[][] o : obsSequences) {
//		System.out.println(".");
//		List<AnnotatedPhone> currPhones = new ArrayList<AnnotatedPhone>();
//		final int T = o.length;
//		;
//
//		probChart.init(o);
//		probChart.calc();
//		for (int t = 0; t < T; ++t) {
//			double max = Double.NEGATIVE_INFINITY;
//			int maxPhone = -1;
//			int maxSubstate = -1;
//			for (int phone = 0; phone < numPhones; ++phone) {
//				for (int substate = 0; substate < numSubstatesPerState[phone]; ++substate) {
//					double gamma = probChart.getGamma(phone, substate, t);
//					if (gamma > max) {
//						max = gamma;
//						maxPhone = phone;
//						maxSubstate = substate;
//					}
//				}
//			}
//			currPhones.add(new AnnotatedPhone(phoneIndexer.get(maxPhone),
//					maxSubstate));
//		}
//		retVal.add(currPhones);
//	}
//
//	return retVal;
//}


}
