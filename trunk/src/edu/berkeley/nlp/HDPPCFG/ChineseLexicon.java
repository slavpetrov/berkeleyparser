package edu.berkeley.nlp.HDPPCFG;

import edu.berkeley.nlp.HDPPCFG.smoothing.Smoother;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.Numberer;

import java.io.Serializable;
import java.util.*;

/**
 * Stores, trains, and scores with an unknown word model.  A couple
 * of filters deterministically force rewrites for certain proper
 * nouns, dates, and cardinal and ordinal numbers; when none of these
 * filters are met, either the distribution of terminals with the same
 * first character is used, or Good-Turing smoothing is used. Although
 * this is developed for Chinese, the training and storage methods
 * could be used cross-linguistically.
 *
 * @author Roger Levy
 */
public class ChineseLexicon extends Lexicon implements Serializable {

  private static final String encoding = "GB18030"; // used only for debugging

  private boolean useFirst = true;
  private boolean useGT = false;

  private static final String unknown = "UNK";


  /* These strings are stored in ascii-stype Unicode encoding.  To
   * edit them, either use the Unicode codes or use native2ascii or a
   * similar program to convert the file into a Chinese encoding, then
   * convert back. */
  public static final String dateMatch = ".*[\u5e74\u6708\u65e5\u53f7]$";
  public static final String numberMatch = ".*[\uff10\uff11\uff12\uff13\uff14\uff15\uff16\uff17\uff18\uff19\uff11\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d\u5341\u767e\u5343\u4e07\u4ebf].*";
  public static final String ordinalMatch = "^\u7b2c.*";
  public static final String properNameMatch = ".*\u00b7.*";

  private Counter[][] tagHash;
  private Set[] seenFirst;

  //private Map[] unknownGT;
  private short tagNT, tagOD, tagCD, tagNR;
  private HashMap[] c;
  private double[][] tc;
  
	public ChineseLexicon(short[] numSubStates, Smoother smoother) {
		super(numSubStates, 0, null, smoother,-1);
		Numberer tagNumberer = Numberer.getGlobalNumberer("tags");
		tagNT = (short)tagNumberer.number("NT");
		tagOD = (short)tagNumberer.number("OD");
		tagCD = (short)tagNumberer.number("CD");
		tagNR = (short)tagNumberer.number("NR");
		int nSubStates = numSubStates.length;
		
		c = new HashMap[nSubStates]; // counts
    tc = new double[nSubStates][];
    tagHash = new Counter[nSubStates][];
    seenFirst = new HashSet[nSubStates];
    for (int i=0; i<numSubStates.length; i++) {
    	tc[i] = new double[numSubStates[i]];
    	tagHash[i] = new Counter[numSubStates[i]];
    	seenFirst[i] = new HashSet();
    }

	}

		
		void useGoodTuring() {
    useGT = true;
    useFirst = false;
  }


  /* returns the log-prob score of a particular TaggedWord in the
   * unknown word model.
   * @param tw the tag->word reproduction in TaggedWord form
   */
  public double[] score(String word, short tag) {
    double[] prob = new double[numSubStates[tag]];

    // testing
    //EncodingPrintWriter.out.println("Scoring unknown word " + word + " with tag " + tag,encoding);
    // end testing    

    
    if (word.matches(dateMatch)) {
      //EncodingPrintWriter.out.println("Date match for " + word,encoding);
      if (tag == tagNT){ //(tag.equals("NT")) {
        Arrays.fill(prob,1.0);
      } else {
        //prob = 0.0;
      }
    } else if (word.matches(numberMatch)) {
      //EncodingPrintWriter.out.println("Number match for " + word,encoding);
      if (tag == tagCD /*tag.equals("CD")*/ && (!word.matches(ordinalMatch))) {
      	Arrays.fill(prob,1.0);//prob = 1.0;
      } else if ( tag == tagOD /*tag.equals("OD")*/ && word.matches(ordinalMatch)) {
      	Arrays.fill(prob,1.0);//prob = 1.0;
      } else {
        //prob = 0.0;
      }
    } else if (word.matches(properNameMatch)) {
      //EncodingPrintWriter.out.println("Proper name match for " + word,encoding);
      if (tag == tagNR){ //tag.equals("NR")) {
      	Arrays.fill(prob,1.0);//prob = 1.0;
      } else {
        //prob = 0.0;
      }
    } else {
      first:
      if (useFirst) {
        String first = word.substring(0, 1);
        if (!seenFirst[tag].contains(first)) {
          if (useGT) {
            //prob = 0;//scoreGT(tag);
            break first;
          } else {
            first = unknown;
          }
        }
        //System.out.println("using first-character model for for unknown word "+  word + " for tag " + tag);

        /* get the Counter of terminal rewrites for the relevant tag */
        for (int substate=0; substate<numSubStates[tag]; substate++){
        	Counter wordProbs = tagHash[tag][substate];
	
	        /* if the proposed tag has never been seen before, issue a
	         * warning and return probability 0 */
	        if (wordProbs == null) {
	          //System.err.println("Warning: proposed tag is unseen in training data!");
	          prob[substate] = 0.0;
	        } else if (wordProbs.containsKey(first)) {
	          prob[substate] = (double) (wordProbs.getCount(first));
	        } else {
	          prob[substate] = (double) (wordProbs.getCount(unknown));
	        }
        }
      } else if (useGT) {
      	 System.err.println("Good Turing smoothing not yet supported.");
      	 //prob = 0;//scoreGT(tag);
      } else {
        System.err.println("Warning: no unknown word model in place!\nGiving the combination " + word + " " + tag + " zero probability.");
        //prob = 0; // should never get this!
      }
    }

    //EncodingPrintWriter.out.println("Unknown word estimate for " + word + " as " + tag + ": " + logProb,encoding); //debugging
    return prob;
  }
/*
  private double scoreGT(short tag) {
    //System.out.println("using GT for unknown word and tag " + tag);
    double prob;
    if (false&&unknownGT.containsKey(tag)) {
      prob = ((Double) unknownGT.get(tag)).doubleValue();
    } else {
      prob = 0.0;
    }
    return prob;
  }
*/

  /**
   * trains the first-character based unknown word model.
   *
   * @param trees the collection of trees to be trained over
   */
//  Leon blacked this out because it had compile-time errors.
//  public void train(Tree<StateSet> trainTree) {
//    if (useFirst) {
//      //System.out.println("treating unknown word as the average of their equivalents by first-character identity.");
//    }
//    if (useGT) {
//      System.out.println("using Good-Turing smoothing for unknown words.");
//    }
//    int sentenceScale = trainTree.getLabel().getIScale();
//
//    //trainUnknownGT(trainTree);
//
//  	List<StateSet> words = trainTree.getYield();
//  	List<StateSet> tags = trainTree.getPreTerminalYield();
//  	//for all words in sentence, incremement the counters with the current counts
//  	for (int position = 0; position < words.size(); position++) {
////  		totalWords++;
//  		String word = words.get(position).getWord();
//  		int nSubStates = tags.get(position).numSubStates();
//  		short tag = tags.get(position).getState();
//      String first = word.substring(0, 1);
//      for (int substate=0; substate<numSubStates[tag]; substate++){
//		    if (!c.containsKey(tag)) {
//		      c.put(tag, new Counter());
//		    }
//		    ((Counter) c.get(tag)).incrementCount(first,1.0);
//		    
//		    tc[tag][substate] += tags.ge;
//		    
//		    
//		    seenFirst.add(first);
//      }
//    }
//
//
//    for (Iterator i = c.keySet().iterator(); i.hasNext();) {
//      String tag = (String) i.next();
//      Counter wc = (Counter) c.get(tag); // counts for words given a tag
//
//      /* outer iteration is over tags */
//      if (!tagHash.containsKey(tag)) {
//        tagHash.put(tag, new Counter());
//      }
//
//      /* the UNKNOWN first character is assumed to be seen once in each tag */
//      // this is really sort of broken!
//      tc.incrementCount(tag, 1.0);
//      wc.setCount(unknown, 1.0);
//
//      /* inner iteration is over words */
//      for (Iterator j = wc.keySet().iterator(); j.hasNext();) {
//        String first = (String) j.next();
//        double prob = (((double) (wc.getCount(first))) / ((double) (tc.getCount(tag))));
//        ((Counter) tagHash.get(tag)).setCount(first, prob);
//        //if (Test.verbose)
//        //EncodingPrintWriter.out.println(tag + " rewrites as " + first + " firstchar with probability " + prob,encoding);
//      }
//    }
//  }

  /* trains Good-Turing estimation of unknown words */
//  private void trainUnknownGT(Collection trees) {
//
//    Counter twCount = new Counter();
//    Counter wtCount = new Counter();
//    Counter tagCount = new Counter();
//    Counter r1 = new Counter(); // for each tag, # of words seen once
//    Counter r0 = new Counter(); // for each tag, # of words not seen
//    Set seenWords = new HashSet();
//
//    int tokens = 0;
//
//    /* get TaggedWord and total tag counts, and get set of all
//     * words attested in training */
//    for (Iterator i = trees.iterator(); i.hasNext();) {
//      Tree t = (Tree) i.next();
//      List words = t.taggedYield();
//      for (Iterator j = words.iterator(); j.hasNext();) {
//        tokens++;
//        TaggedWord tw = (TaggedWord) (j.next());
//        WordTag wt = toWordTag(tw);
//        String word = wt.word();
//        String tag = wt.tag();
//        //if (Test.verbose) EncodingPrintWriter.out.println("recording instance of " + wt.toString(),encoding); // testing
//
//        wtCount.incrementCount(wt);
//        twCount.incrementCount(tw);
//        //if (Test.verbose) EncodingPrintWriter.out.println("This is the " + wtCount.countOf(wt) + "th occurrence of" + wt.toString(),encoding); // testing
//        tagCount.incrementCount(tag);
//        boolean alreadySeen = seenWords.add(word);
//
//        // if (Test.verbose) if(! alreadySeen) EncodingPrintWriter.out.println("already seen " + wt.toString(),encoding); // testing
//
//      }
//    }
//    
//    // testing: get some stats here
//    System.out.println("Total tokens: " + tokens + " [num words + numSent (boundarySymbols)]");
//    System.out.println("Total WordTag types: " + wtCount.keySet().size());
//    System.out.println("Total TaggedWord types: " + twCount.keySet().size() + " [should equal word types!]");
//    System.out.println("Total tag types: " + tagCount.keySet().size());
//    System.out.println("Total word types: " + seenWords.size());
//
//
//    /* find # of once-seen words for each tag */
//    for (Iterator i = wtCount.keySet().iterator(); i.hasNext();) {
//      WordTag wt = (WordTag) i.next();
//      if ((wtCount.getCount(wt)) == 1) {
//        r1.incrementCount(wt.tag());
//      }
//    }
//
//    /* find # of unseen words for each tag */
//    for (Iterator i = tagCount.keySet().iterator(); i.hasNext();) {
//      String tag = (String) i.next();
//      for (Iterator j = seenWords.iterator(); j.hasNext();) {
//        String word = (String) j.next();
//        WordTag wt = new WordTag(word, tag);
//        //EncodingPrintWriter.out.println("seeking " + wt.toString(),encoding); // testing
//        if (!(wtCount.containsKey(wt))) {
//          r0.incrementCount(tag);
//          //EncodingPrintWriter.out.println("unseen " + wt.toString(),encoding); // testing
//        } else {
//          //EncodingPrintWriter.out.println("count for " + wt.toString() + " is " + wtCount.countOf(wt),encoding);
//        }
//      }
//    }
//
//    /* set unseen word probability for each tag */
//    for (Iterator i = tagCount.keySet().iterator(); i.hasNext();) {
//      String tag = (String) i.next();
//      //System.out.println("Tag " + tag + ".  Word types for which seen once: " + r1.countOf(tag) + ".  Word types for which unseen: " + r0.countOf(tag) + ".  Total count token for tag: " + tagCount.countOf(tag)); // testing
//
//      double logprob = Math.log((r1.getCount(tag)) / ((tagCount.getCount(tag)) * (r0.getCount(tag))));
//
//      unknownGT.put(tag, new Double(logprob));
//    }
//
//    /* testing only: print the GT-smoothed model */
//    //System.out.println("The GT-smoothing model:");
//    //System.out.println(unknownGT.toString());
//    //EncodingPrintWriter.out.println(wtCount.toString(),encoding);
//
//
//  }

//  public static void main(String[] args) {
//    System.out.println("Testing unknown matching");
//    String s = "\u5218\u00b7\u9769\u547d";
//    if (s.matches(properNameMatch)) {
//      System.out.println("hooray names!");
//    } else {
//      System.out.println("Uh-oh names!");
//    }
//    String s1 = "\uff13\uff10\uff10\uff10";
//    if (s1.matches(numberMatch)) {
//      System.out.println("hooray numbers!");
//    } else {
//      System.out.println("Uh-oh numbers!");
//    }
//    String s11 = "\u767e\u5206\u4e4b\u56db\u5341\u4e09\u70b9\u4e8c";
//    if (s1.matches(numberMatch)) {
//      System.out.println("hooray numbers!");
//    } else {
//      System.out.println("Uh-oh numbers!");
//    }
//    String s12 = "\u767e\u5206\u4e4b\u4e09\u5341\u516b\u70b9\u516d";
//    if (s1.matches(numberMatch)) {
//      System.out.println("hooray numbers!");
//    } else {
//      System.out.println("Uh-oh numbers!");
//    }
//    String s2 = "\u4e09\u6708";
//    if (s2.matches(dateMatch)) {
//      System.out.println("hooray dates!");
//    } else {
//      System.out.println("Uh-oh dates!");
//    }
//
//    System.out.println("Testing tagged word");
//    Counter c = new Counter();
//    TaggedWord tw1 = new TaggedWord("w", "t");
//    c.incrementCount(tw1);
//    TaggedWord tw2 = new TaggedWord("w", "t2");
//    System.out.println(c.containsKey(tw2));
//    System.out.println(tw1.equals(tw2));
//
//    WordTag wt1 = toWordTag(tw1);
//    WordTag wt2 = toWordTag(tw2);
//    WordTag wt3 = new WordTag("w", "t2");
//    System.out.println(wt1.equals(wt2));
//    System.out.println(wt2.equals(wt3));
//  }
/*
  private static WordTag toWordTag(TaggedWord tw) {
    return new WordTag(tw.word(), tw.tag());
  }
*/
  private static final long serialVersionUID = 1L;

}