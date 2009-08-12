/**
 * 
 */
package edu.berkeley.nlp.HMM;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.berkeley.nlp.PCFGLA.Option;
import edu.berkeley.nlp.PCFGLA.OptionParser;
import edu.berkeley.nlp.util.CollectionUtils;
import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.PriorityQueue;

/**
 * @author petrov
 *
 */
public class CorpusStatistics {
	public static class Options
	{
		@Option(name = "-corpus", usage="Which corpus to use: TIMIT, CHE, or DUMMY")
		public Corpus.CorpusType corpusType = Corpus.CorpusType.TIMIT;
		
		@Option(name="-path",usage="Path to corpus")
		public File corpusPath = null;

		@Option(name="-in",usage="Path to corpus")
		public String inFile = null;
	}
	
	public static void main(String[] argv) {
		
		OptionParser optionParser = new OptionParser(Options.class);
		CorpusStatistics.Options opts = (CorpusStatistics.Options)optionParser.parse(argv,true,false);
//		System.out.println("Calling with " + optionParser.getPassedInOptions());
		
		Corpus corpus = new Corpus(opts.corpusPath.getPath(), opts.corpusType, false, false,39,false);
//		System.out.println("Done loading.");
		int[] phoneAppearances = new int[corpus.getPhoneIndexer().size()];
		double[] averageLengths = new double[corpus.getPhoneIndexer().size()];
		System.out.print("<html><body>");
		getPhoneCounts(corpus,averageLengths,phoneAppearances);
		
		
		if (opts.inFile==null) System.exit(1);
			
		SubphoneHMM hmm = SubphoneHMM.Load(opts.inFile);
		hmm.dataSent = false;
	  TrainingProbChart trainChart = new TrainingProbChart(hmm,0);
		List<double[][]> obsListsDev = corpus.getObsListsTrain();
		List<List<Phone>> goldPhoneSequences = corpus.getPhoneSequencesTrain();
		PhoneIndexer pIndexer = corpus.getPhoneIndexer();
		
		hmm.writeDotFiles(opts.inFile,0.05);
		
		
		List<int[]> phoneSequences = pIndexer.indexSequences(goldPhoneSequences);
		int nPhones = pIndexer.size();
		Counter<String>[] seqCounts = new Counter[nPhones];
		int[][][] leftContexts = new int[nPhones][nPhones][nPhones];
		int[][][] rightContexts = new int[nPhones][nPhones][nPhones];
		for (int i=0; i<seqCounts.length; i++){
			seqCounts[i] = new Counter<String>();
		}
		for (int i=0; i<obsListsDev.size(); i++){
			int[] phones = phoneSequences.get(i);
			trainChart.init(phones,obsListsDev.get(i),i);
			trainChart.calc();
			int curPhone=-1, lastPhone=-1, prevPhone=0;
			int curSubstate=-1, lastSubstate=-1, beginSubstate=0;
			StringBuilder sb = new StringBuilder();
			for (int t=0; t<phones.length; t++){
				curPhone = phones[t];
				curSubstate = trainChart.getMostLikelySubstate(t, curPhone);
				if (lastPhone==-1){ lastPhone = curPhone; }
				if (curPhone!=lastPhone){
					String sequence = sb.toString();
					seqCounts[lastPhone].incrementCount(sequence, 1.0);
					leftContexts[lastPhone][beginSubstate][prevPhone]++;
					rightContexts[lastPhone][lastSubstate][curPhone]++;
					prevPhone = lastPhone;
					beginSubstate = curSubstate;
					lastSubstate = -1;
					sb = new StringBuilder();
//					sb.append(pIndexer.get(curPhone)+": ");
				}
				if (curSubstate!=lastSubstate) sb.append(curSubstate+" ");
				lastPhone = curPhone;
				lastSubstate = curSubstate;
			}
//			System.out.println(seqCounts);
		}
		
		
		double thresh = 0.80;
		System.out.print("<br>");

		for (int i=0; i<nPhones; i++){
			double total = seqCounts[i].totalCount();
			double mass = 0;
			String phone = pIndexer.get(i).getLabel();
			System.out.print("<br><table border=1><tr><th>");
			System.out.println("<a name="+phone+"> "+phone+"(total appearances: "+total+")");
			System.out.print("</th>");
			PriorityQueue<String> pq = seqCounts[i].asPriorityQueue();
			while (pq.hasNext() && mass<thresh*total){
			  double pr = pq.getPriority();
			  String seq = pq.next();
			  String[] substates = seq.split(" ");
			  int first = Integer.parseInt(substates[0]);
			  int last = Integer.parseInt(substates[substates.length-1]);
			  int percent = (int)(pr/total*100);
			  if (percent<1) continue;
				System.out.println("<tr><td>"+seq+"</td><td><a href=#left-"+phone+first+">LEFT</a></td><td><a href=#right-"+phone+last+">RIGHT</a>"+"</a></td><td>"+(int)pr +" = "+percent+"%</td></tr>");
				mass += pr;
			}
			System.out.println("</table>");
		}	  
		System.out.print("<br><br>");

		for (int i=0; i<nPhones; i++){
			String phone = pIndexer.get(i).getLabel();
			PriorityQueue<String> pq = seqCounts[i].asPriorityQueue();
			for (int l=0; l<nPhones; l++){
				boolean first = true;
				for (int j=0; j<nPhones; j++){
					String left = "";
					String p = pIndexer.get(j).getLabel();
					if (leftContexts[i][l][j]>0) {
						if (first){
							System.out.print("<br><table border=1><tr><th>");
							System.out.println("<a name=left-"+phone+l+"> <a href=#"+phone+"> Left context for "+phone+" - "+l);
							System.out.print("</th>");
							first = false;
						}
						left = p + " " + leftContexts[i][l][j];
						System.out.println("<tr><td>"+left+"</td></tr>");
					}
				}
				if (!first) System.out.println("</table>");
			}

			for (int r=0; r<nPhones; r++){
				boolean first = true;
				for (int j=0; j<nPhones; j++){
					String right = "";
					String p = pIndexer.get(j).getLabel();
					if (rightContexts[i][r][j]>0) {
						if (first){
							System.out.print("<br><table border=1><tr><th>");
							System.out.println("<a name=right-"+phone+r+"> <a href=#"+phone+"> Right context for "+phone+" - "+r);
							System.out.print("</th>");
							first = false;
						}
						right = p + " " + rightContexts[i][r][j];
						System.out.println("<tr><td>"+right+"</td></tr>");
					}
				}
				if (!first) System.out.println("</table>");
			}

		}	  
		System.out.print("</body></html>");

		//		Decoder viterbiDecoder = new ViterbiDecoder(hmm);
//		List<List<Phone>> testSequences = new ArrayList<List<Phone>>();			
//		testSequences.addAll(viterbiDecoder.decode(obsListsDev));

	
	}

	
	public static void getPhoneCounts(Corpus corpus, double[] averageLengths, int[] phoneAppearances ) {
		List<List<Phone>> phoneSequencesAsObjects = corpus.getPhoneSequencesTrain();
		List<int[]> phoneSequences = corpus.getPhoneIndexer().indexSequences(phoneSequencesAsObjects);
		//List<double[][]> obsList = corpus.getObsListsTrain();
		PhoneIndexer phoneIndexer = corpus.getPhoneIndexer();
		int gaussianDim = corpus.getGaussianDim();
		
		System.out.println("Loaded "+phoneSequences.size()+" training sequences with dimension "+gaussianDim+".<br>");
		System.out.println("There are "+phoneIndexer.size()+" phones.<br>");

		double avgUtteranceLength = 0;
		int[] phoneFrameCounts = new int[phoneIndexer.size()];
		//int[] phoneLength = new int[phoneIndexer.size()];
		
		for (int[] thisSeq : phoneSequences) {
			avgUtteranceLength += thisSeq.length;
			int thisPhone = -1, lastPhone=-1;// thisLength = 0;
			for (int i=0; i<thisSeq.length; i++){
				thisPhone = thisSeq[i];
				phoneFrameCounts[thisPhone]++;
				if (thisPhone!=lastPhone){ //thisLength++;
//				else {
					phoneAppearances[thisPhone]++;
					
				}
				lastPhone=thisPhone;
			}
		}
	
		avgUtteranceLength /= phoneSequences.size();
		System.out.println("There are "+phoneSequences.size()+" utterances with an average number of "+avgUtteranceLength+" frames.<br>");
		
		for (int phone=0; phone<phoneIndexer.size(); phone++){
			double avgLength = (double)phoneFrameCounts[phone]/(double)phoneAppearances[phone];
			averageLengths[phone] = avgLength;
			System.out.println("Phone\t"+phoneIndexer.get(phone)+" occurs in\t"+phoneFrameCounts[phone]+"\tframes. " +
					"There are "+phoneAppearances[phone]+" appearances with an average length of "+avgLength+".<br>");
		}
//		return averageLengths;//phoneFrameCounts;//phoneAppearances;
		
	}
		
}
