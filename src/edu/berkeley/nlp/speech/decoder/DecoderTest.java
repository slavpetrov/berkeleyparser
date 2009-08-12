/**
 * 
 */
package edu.berkeley.nlp.speech.decoder;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.berkeley.nlp.HMM.AcousticModel;
import edu.berkeley.nlp.PCFGLA.Option;
import edu.berkeley.nlp.PCFGLA.OptionParser;
import edu.berkeley.nlp.speech.decoder.dict.CMUDict;
import edu.berkeley.nlp.speech.decoder.dict.PronounciationDictionary;

/**
 * @author aria42
 *
 */
public class DecoderTest {
	
	public static class Options {
		
		@Option(name="-files",required=true, usage="colon seperated list of files")
		public String files;
		
		@Option(name="-mfccSuffix")
		public String mfccSuffix;
		
		@Option(name="-obsDimension", usage="dim. of mfcc vector")
		public int obsDimension = 39;
		
		@Option(name="-hmmModel", required=true)
		public String hmmModelPath ;
		
		@Option(name="-pdictPath", required=true)
		public String pDictPath ;
		
		@Option(name="-treebankPath")
		public String treebankPath = "/Users/aria42/Treebank3/parsed/mrg/wsj";
		
		@Option(name="-numWords")
		public int numWords = 5000;
		
	}
	
	private static List<double[][]> getSeqs(Options opts) {
		String[] files = opts.files.trim().split(":");
		List<double[][]> datums = new ArrayList<double[][]>();
		for (String file: files) {
			try {
				int lineNum = 0;
				BufferedReader br = new BufferedReader(new FileReader(file));
				List<double[]> tempSeq = new ArrayList<double[]>();
				double[] curObs = new double[opts.obsDimension];
				while (true) {
					String line = br.readLine();
					if (line == null) break;
					if (lineNum % opts.obsDimension == 0) {
						tempSeq.add(curObs);
						curObs = new double[opts.obsDimension];
					}
					double x = Double.parseDouble(line.trim());
					curObs[lineNum % opts.obsDimension] = x;
					lineNum++;
				}
				double[][] seq = new double[tempSeq.size()][];
				for (int i=0; i < tempSeq.size(); ++i) {
					seq[i] = tempSeq.get(i);
				}
				datums.add(seq);
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(0);
			}						
		}
		return datums;
	}
		
	public static void main(String[] args) {
		OptionParser optParser = new OptionParser(Options.class);
		Options opts = (Options) optParser.parse(args);
		List<double[][]> seqs = getSeqs(opts);
		AcousticModel acousticModel = new AcousticModel.SubphoneHMMWrapper(opts.hmmModelPath);
		PronounciationDictionary pDict = new CMUDict(opts.pDictPath,acousticModel.getPhoneIndexer());
		LanguageModel langModel = null;
		if (true) {
			UnigramLanguageModel uniLangModel = UnigramLanguageModel.getTreebankUnigramModel(opts.treebankPath);
			uniLangModel.prune(opts.numWords);
			langModel = uniLangModel;
		}
		intersectDictionaryAndLanguageModel(pDict, langModel);			
		LexiconTrie lexTrie = new LexiconTrie(pDict, langModel);		
		SloppyExhaustiveViterbiDecoder decoder = new SloppyExhaustiveViterbiDecoder(acousticModel, lexTrie);		
		System.out.println("Acoustic Model Max Number of Substates: " + acousticModel.getMaxNumberOfSubstates());		
		for (double[][] seq: seqs) {
			System.out.println("Seq. Length: " + seq.length);
			List<String> sent = decoder.decode(seq);
			System.out.println(sent);	
		}
	}

	private static void intersectDictionaryAndLanguageModel(PronounciationDictionary pDict, LanguageModel langModel) {
		Map<String, int[]> dictMap = pDict.getDictionary();
		Set<String> lmSupport = langModel.getSupport();
		Iterator<String> wordIt = dictMap.keySet().iterator();
		while (wordIt.hasNext()) {
			String word = wordIt.next();
			if (!lmSupport.contains(word)) {
				wordIt.remove();
			}
		}
	}

}
