/**
 * 
 */
package edu.berkeley.nlp.HMM;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import edu.berkeley.nlp.util.ArrayUtil;
import fig.basic.Pair;
import fig.basic.LogInfo;

//note : revision 2740 has non-weighted version

/**
 * @author adpauls
 *
 */
public class EditDistanceAccuracy extends AccuracyCalc{

	public int totalNum;

	/**
	 * @param phoneIndexer
	 */
	public EditDistanceAccuracy(PhoneIndexer phoneIndexer, List<List<Phone>> goldSeqs, List<List<Phone>> testSeqs) {
		super(phoneIndexer, goldSeqs, testSeqs);
		// TODO Auto-generated constructor stub
	}

	static double INSERT_COST = 3.0;

	static double DELETE_COST = 3.0;

	static double SUBSTITUTE_COST = 4.0;

	private double[][] initialize(double[][] d)
	{
		for (int i = 0; i < d.length; i++)
		{
			for (int j = 0; j < d[i].length; j++)
			{
				d[i][j] = Double.NaN;
			}
		}
		return d;
	}

	private static class EditList extends ArrayList<Edit> {

		/**
		 * 
		 */
		public EditList() {
			super();
			// TODO Auto-generated constructor stub
		}

		/**
		 * @param c
		 */
		public EditList(Collection<? extends Edit> c) {
			super(c == null ? new EditList() : c);
			// TODO Auto-generated constructor stub
		}}

	private enum Edit
	{
		INSERTION,DELETION,SUBSTITUTION, NOT_INITED;
	}
	
	public Pair<EditList,int[][]> getDistance(List<Phone> firstList, List<Phone> secondList, boolean computeConfusionMatrix)
	{
		double[][] bestDistances = initialize(new double[firstList.size() + 1][secondList.size() + 1]);
		EditList[][] edits = new EditList[firstList.size() + 1][secondList.size() + 1];
		int[][][][] substitutions = null;
		if (computeConfusionMatrix) substitutions = new int[firstList.size() + 1][secondList.size() + 1][phoneIndexer.size()][phoneIndexer.size()];
		getDistance(firstList, secondList, 0, 0, bestDistances, edits, substitutions, computeConfusionMatrix);
		
		// print some more detailed error statistics
		EditList finalEdits = edits[0][0];
		int[][] finalSubstitutions = null;
		if (computeConfusionMatrix) finalSubstitutions = substitutions[0][0];
		return new Pair<EditList,int[][]>(finalEdits,finalSubstitutions);
	}

	private double getDistance(List<Phone> firstList, List<Phone> secondList, int firstPosition, int secondPosition,
		double[][] bestDistances, 		EditList[][] edits, int[][][][] substitutions, boolean computeConfusionMatrix)
	{
		if (firstPosition > firstList.size() || secondPosition > secondList.size()) return Double.POSITIVE_INFINITY;
		if (firstPosition == firstList.size() && secondPosition == secondList.size()) return 0.0;
		if (Double.isNaN(bestDistances[firstPosition][secondPosition]))
		{
			double distance = Double.POSITIVE_INFINITY;
//			List<Edit> insertEdits = new ArrayList<Edit>();
//			List<Edit> deleteEdits = new ArrayList<Edit>();
//			List<Edit> substiEdits = new ArrayList<Edit>();
//			List<Edit> nothingEdits = new ArrayList<Edit>();
			double insertDistance = INSERT_COST + getDistance(firstList, secondList, firstPosition + 1, secondPosition, bestDistances, edits, substitutions, computeConfusionMatrix);
			distance = Math.min(distance, insertDistance);
			double deleteDistance = DELETE_COST + getDistance(firstList, secondList, firstPosition, secondPosition + 1, bestDistances, edits, substitutions, computeConfusionMatrix);
			distance = Math.min(distance, deleteDistance);
			double substiDistance = SUBSTITUTE_COST + getDistance(firstList, secondList, firstPosition + 1, secondPosition + 1, bestDistances, edits, substitutions, computeConfusionMatrix);
			distance = Math.min(distance, substiDistance);
			double nothingDistance = Double.POSITIVE_INFINITY;
			if (firstPosition < firstList.size() && secondPosition < secondList.size())
			{
				if (equal39(firstList.get(firstPosition),secondList.get(secondPosition)))
				{
				  nothingDistance = getDistance(firstList, secondList, firstPosition + 1, secondPosition + 1, bestDistances,edits, substitutions, computeConfusionMatrix);
					distance = Math.min(distance, nothingDistance);
				}
			}
			bestDistances[firstPosition][secondPosition] = distance;
			if (distance == insertDistance) { 
				edits[firstPosition][secondPosition] = new EditList(edits[firstPosition+1][secondPosition]); edits[firstPosition][secondPosition].add(Edit.INSERTION);
				if (computeConfusionMatrix) substitutions[firstPosition][secondPosition] = ArrayUtil.clone(substitutions[firstPosition+1][secondPosition]);
			}
			else if (distance == deleteDistance) { 
				edits[firstPosition][secondPosition] = new EditList(edits[firstPosition][secondPosition+1]); edits[firstPosition][secondPosition].add(Edit.DELETION);
				if (computeConfusionMatrix) substitutions[firstPosition][secondPosition] = ArrayUtil.clone(substitutions[firstPosition][secondPosition+1]);
			}
			else if (distance == substiDistance) { 
				edits[firstPosition][secondPosition] = new EditList(edits[firstPosition+1][secondPosition+1]); edits[firstPosition][secondPosition].add(Edit.SUBSTITUTION); 
				if (computeConfusionMatrix) { substitutions[firstPosition][secondPosition] = ArrayUtil.clone(substitutions[firstPosition+1][secondPosition+1]); substitutions[firstPosition][secondPosition][phoneIndexer.indexOf(mapDownPhone(firstList.get(firstPosition)))][phoneIndexer.indexOf(mapDownPhone(secondList.get(secondPosition)))]++;}
			}
			else { 
				edits[firstPosition][secondPosition] = new EditList(edits[firstPosition+1][secondPosition+1]);
				if (computeConfusionMatrix) substitutions[firstPosition][secondPosition] = ArrayUtil.clone(substitutions[firstPosition+1][secondPosition+1]);
			}
			
			
		}
		return bestDistances[firstPosition][secondPosition];
	}
	
	public double getAccuracy() {
		List<List<Phone>> seqs1 = goldSeqs;
		List<List<Phone>> seqs2 = testSeqs;
		if (seqs1.size() != seqs2.size())
			throw new RuntimeException("Hamming distance on unequal sequences");
		double sum = 0;
		totalNum = 0;
		
		String[] phoneOrder = {"iy", "ix", "eh", "ae", "ax", "uw", "uh", "aa", "ey", "ay", "oy", "aw", "ow", "er", "el", "r", "w", "y", "m", "n", "ng", "dx", "jh", "ch", "z", "s", "zh", "hh", "v", "f", "dh", "th", "b", "p", "d", "t", "g", "k", "sil"};
		
		boolean printConfusionMatrix = false;
		double insertions=0, deletions=0, substitutions=0;
		int[][] substitutionMatrix = null;
		if (printConfusionMatrix) substitutionMatrix = new int[phoneIndexer.size()][phoneIndexer.size()];
		for (int i = 0; i < seqs1.size(); ++i) {
			List<Phone> seq1 = PhoneIndexer.getCollapsedPhoneList(seqs1.get(i), false);
			List<Phone> seq2 = PhoneIndexer.getCollapsedPhoneList(seqs2.get(i), false);
			Pair<EditList,int[][]> result = getDistance(seq2.subList(1, seq2.size() - 1), seq1.subList(1, seq1.size() - 1), printConfusionMatrix);
			EditList thisEdits = result.getFirst();
			if (printConfusionMatrix){
				int[][] thisSubs = result.getSecond();
				ArrayUtil.addInPlace(substitutionMatrix, thisSubs);
			}
			
			for (Edit e : thisEdits){
				if (e==Edit.INSERTION) insertions++;
				else if (e==Edit.DELETION) deletions++;
				else /* if (e==Edit.SUBSTITUTIONS) */ substitutions++;
			}

			double thisDistance = thisEdits.size();
			sum += thisDistance;
			addAccuracy(thisDistance/ (seq1.size() - 2));
			totalNum+=seq1.size() - 2;

		}
		if (printConfusionMatrix){
			for (int i=0; i<39; i++){
				for (int j=0; j<39; j++){
					System.out.print(substitutionMatrix[phoneIndexer.indexOf(new Phone(phoneOrder[j]))][phoneIndexer.indexOf(new Phone(phoneOrder[i]))]+" ");
				}
				System.out.print("\n");
			}
		}
//		System.out.println(ArrayMath.toString(substitutionMatrix));
		insertions = ((int)(insertions/totalNum*1000+0.5))/10.0;
		deletions = ((int)(deletions/totalNum*1000+0.5))/10.0;
		substitutions = ((int)(substitutions/totalNum*1000+0.5))/10.0;
	LogInfo.logss("Insertions: "+insertions+"%, Deletions: "+deletions+"%, Substitutions: "+substitutions+"%.");

		return sum / totalNum;
	}





	

}
