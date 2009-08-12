/**
 *
 */
package edu.berkeley.nlp.speech.decoder.dict;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.berkeley.nlp.HMM.Phone;
import fig.basic.Indexer;

/**
 * @author aria42
 *
 */
public class CMUDict implements PronounciationDictionary {

	private Map<String, int[]> wordToPhoneMap = new HashMap<String, int[]>();
	private Indexer<Phone> phoneIndexer;

	public CMUDict(String path, Indexer<Phone> phoneIndexer) {
		this.phoneIndexer = phoneIndexer;

		try {
			FileReader fileReader = new FileReader(path);
			BufferedReader br = new BufferedReader(fileReader);
			while (true) {
				String line = br.readLine();
				if (line == null) {
					break;
				}
				if (line.length() == 0 || line.charAt(0) == '#') {
					continue;
				}
				String[] fields = line.split("\\s+");
				String word = fields[0].toLowerCase();
				List<String> phones = new ArrayList<String>();
				boolean quitWord = false;
				for (int i=1; i < fields.length && !quitWord; ++i) {
					String p = fields[i].trim().replaceAll("\\d+", "");
					p = p.replaceAll("[a-z]*", "").toLowerCase();					
					if (p.equals("e")) {
						p = "eh";
					}
					Phone ph = new Phone(p);
					if (!phoneIndexer.contains(ph)) {
						System.err.println("Warning: uncrecognized phone " + ph);
						quitWord = true;
						continue;
					}

					phones.add(p);
				}
				if (!quitWord) {
					int[] phoneIndices = new int[phones.size()];
					for (int i=0; i < phones.size(); ++i) {
						Phone phone = new Phone(phones.get(i));
						int pIndex = phoneIndexer.indexOf(phone);
						assert pIndex > -1;
						phoneIndices[i] = pIndex;
					}
					wordToPhoneMap.put(word, phoneIndices);
				}
			}			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public int[] getPhonemes(String word) {
		return wordToPhoneMap.get(word);
	}

	public Map<String, int[]> getDictionary() {
		return wordToPhoneMap;
	}

	public Indexer<Phone> getPhoneIndexer() {
		return phoneIndexer;
	}

}
