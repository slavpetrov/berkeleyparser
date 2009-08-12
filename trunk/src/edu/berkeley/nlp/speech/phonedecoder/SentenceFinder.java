/**
 * 
 */
package edu.berkeley.nlp.speech.phonedecoder;

import java.util.ArrayList;
import java.util.List;

import edu.berkeley.nlp.HMM.Phone;
import edu.berkeley.nlp.speech.decoder.dict.PronounciationDictionary;
import edu.berkeley.nlp.util.Lists;

/**
 * @author aria42
 * 
 */
public class SentenceFinder {

	private static final int PREFIX_LEN = 3;

	PronounciationDictionary pDict;

	List<List<Integer>> sentencesAsPhones;

	List<String> sentences;

	public SentenceFinder(PronounciationDictionary pDict, List<String> sentences) {
		this.pDict = pDict;
		sentencesAsPhones = getSentencesAsPhones(sentences);
	}

	private List<List<Integer>> getSentencesAsPhones(List<String> sents) {
		List<List<Integer>> phoneSeqs = new ArrayList<List<Integer>>();
		this.sentences = new ArrayList<String>();
		boolean allWordsFound = true;
		for (String sent : sents) {
			String[] toks = sent.split("\\s+");
			List<Integer> phones = new ArrayList<Integer>();
			for (String tok : toks) {
				int[] phoneSeq = pDict.getPhonemes(tok);
				if (phoneSeq == null) {
					System.err.println("Didn't have: " + tok);
					System.err.println("Leaving out sentence: \"" + sent + "\"");
					allWordsFound = false;
					break;
				}
				for (int pIndex : phoneSeq) {
					phones.add(pIndex);
				}
			}
			if (allWordsFound) {
				phoneSeqs.add(phones);
				this.sentences.add(sent);
			} else {
			}
		}
		return phoneSeqs;
	}

	public String getSentence(List<Phone> phoneSequence) {
		List<Integer> collapsedSequence = new ArrayList<Integer>();
		Phone lastPhone = null;
		int silIndex = pDict.getPhoneIndexer().indexOf(new Phone("h#"));
		int startIndex = pDict.getPhoneIndexer().indexOf(new Phone("*START*"));
		int endIndex = pDict.getPhoneIndexer().indexOf(new Phone("*END*"));
		List<Integer> skips = Lists.newList(silIndex, startIndex, endIndex);

		for (Phone phone : phoneSequence) {
			if (lastPhone == null || !phone.equals(lastPhone)) {
				int phoneIndex = pDict.getPhoneIndexer().indexOf(phone);
				lastPhone = phone;
				if (!skips.contains(phoneIndex)) {
					collapsedSequence.add(phoneIndex);
					if (collapsedSequence.size() >= PREFIX_LEN)
						break;
				}
			}
		}

		int i = 0;
		for (List<Integer> sentence : sentencesAsPhones) {
			int len = Math.min(PREFIX_LEN, sentence.size());
			List<Integer> sentencePrefix = sentence.subList(0, len);
			if (sentencePrefix.equals(collapsedSequence)) {
				break;
			}
			i++;
		}

		return (i < sentences.size()) ? sentences.get(i) : null;
	}

	/**
	 * @param phoneSeq
	 * @return
	 */
	public List<Phone> collapse(List<Phone> phoneSeq) {
		List<Phone> collapsedSequence = new ArrayList<Phone>();
		Phone lastPhone = null;
		int silIndex = pDict.getPhoneIndexer().indexOf(new Phone("h#"));
		int startIndex = pDict.getPhoneIndexer().indexOf(new Phone("*START*"));
		int endIndex = pDict.getPhoneIndexer().indexOf(new Phone("*END*"));
		List<Integer> skips = Lists.newList(silIndex, startIndex, endIndex);

		for (Phone phone : phoneSeq) {
			if (lastPhone == null || !phone.equals(lastPhone)) {
				int phoneIndex = pDict.getPhoneIndexer().indexOf(phone);
				lastPhone = phone;
				if (!skips.contains(phoneIndex)) {
					collapsedSequence.add(phone);
				}
			}
		}
		return collapsedSequence;
	}
}
