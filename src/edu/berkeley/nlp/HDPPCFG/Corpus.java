/**
 * 
 */
package edu.berkeley.nlp.HDPPCFG;

import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import edu.berkeley.nlp.io.PennTreebankReader;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees;

import fig.basic.Option;

/**
 * Class Corpus will give easy access to loading the training, validation, development
 * testing, and testing sets from both the WSJ and Brown corpora.
 * 
 * @author leon
 *
 */
public class Corpus {
	
	public static final int BROWN = 0;
	public static final int ENGLISH = 1;
	public static final int CHINESE = 2;
	public static final int GERMAN = 3;
	public static final int SPANISH = 4;
	public static final int FRENCH = 5;
	public static final int DANISH = 6;
	
	public static int myLanguage = -1;

  @Option public static int startTrainSection = 2;
  @Option public static int numTrainSections = 20;
  @Option public static boolean hmm = false;
	
	ArrayList<Tree<String>> trainTrees = new ArrayList<Tree<String>>();	
	ArrayList<Tree<String>> validationTrees = new ArrayList<Tree<String>>();	
	ArrayList<Tree<String>> devTestTrees = new ArrayList<Tree<String>>();	
	ArrayList<Tree<String>> finalTestTrees = new ArrayList<Tree<String>>();	

	
	/** Load the WSJ, Brown, and Chinese corpora from the given locations.  If either
	 * is null, don't load it.  If both are null, use the dummy sentence.  Then, throw
	 * away all but *fraction* of the data.  To train on only the part of the Chinese
	 * Treebank that Levy and Manning do, use fraction=0.22225.
	 * 
	 * @param fraction The fraction of training data to use.  In the range [0,1].
	 */
	public Corpus(String path, int lang, double fraction, boolean chineseSmall, boolean onlyTest) {
		this(path, lang, chineseSmall,onlyTest);
		int beforeSize = trainTrees.size();
		int endIndex = (int)Math.ceil(beforeSize * fraction);
		trainTrees = new ArrayList<Tree<String>>(trainTrees.subList(0,endIndex));
    int nTrainingWords = 0;
    for (Tree<String> tree : trainTrees) {
    	nTrainingWords += tree.getYield().size();
    }
    System.out.println("In training set we have # of words: "+nTrainingWords);
		int afterSize = trainTrees.size();
		System.out.println("reducing number of training trees from "+beforeSize+" to "+afterSize);
	}
		
	/** Load the WSJ, Brown, and Chinese corpora from the given locations.  If any
	 * is null, don't load it.  If all are null, use the dummy sentence.  Don't
	 * load the English corpora if we load the Chinese one.
	 */
	private Corpus(String path, int lang, boolean chineseSmall, boolean onlyTest) {
		myLanguage = lang;
		boolean dummy = path==null;
    if (dummy) {
      System.out.println("Loading one dummy sentence into training set only.");
      Trees.PennTreeReader reader;
      Tree<String> tree;
      int exampleNumber = 2;
      List<String> sentences = new ArrayList<String>();
      switch (exampleNumber) {
      case 0:
        // Joshua Goodman's example 
        sentences.add("((S (A x) (C x)))");
        //sentences.add("((S (A x) (C x)))");
        //sentences.add("((S (A y) (C x)))");
        sentences.add("((S (E x) (B x)))");
        //sentences.add("((S (F x) (B x)))");
        //sentences.add("((S (F x) (B x)))");
        //sentences.add("((T (E x) (C x)))");
        //sentences.add("((T (E x) (C x)))");
        break;
      case 1:
        // A single sentence
        //sentences.add("((S (UN1 (UN2 (NP (DT the) (JJ quick) (JJ brown) (NN fox)))) (VP (VBD jumped) (PP (IN over) (NP (DT the) (JJ lazy) (NN dog)))) (. .)))");
        //sentences.add("((S (NP (DT Some) (NNS traders)) (VP (VBD said) (SBAR (IN that) (S (NP (NP (DT the) (ADJP (RB closely) (VBN watched)) (NNP Majo) (NNP Market) (NNP Index)) (, ,) (SBAR (WHNP (WP$ whose) (NP (CD 20) (NNS stocks))) (S (VP (VBP mimic) (NP (DT the) (NNP Dow) (NNS industrials))))) (, ,)) (VP (VBD did) (RB n't) (VP (VB lead) (NP (NP (NN yesterday) (POS 's)) (JJ big) (NN rally))))))) (. .)))");
      	//sentences.add("((S (NP (NP (JJ Influential) (NNS members)) (PP (IN of) (NP (DT the) (NNP House) (NNP Ways) (CC and) (NNP Means) (NNP Committee)))) (VP (VBD introduced) (NP (NP (NN legislation)) (SBAR (WHNP (WDT that)) (S (VP (MD would) (VP (VB restrict) (SBAR (WHADVP (WRB how)) (S (NP (DT the) (JJ new) (NN savings-and-loan) (NN bailout) (NN agency)) (VP (MD can) (VP (VB raise) (NP (NN capital)))))) (, ,) (S (VP (VBG creating) (NP (NP (DT another) (JJ potential) (NN obstacle)) (PP (TO to) (NP (NP (NP (DT the) (NN government) (POS 's)) (NN sale)) (PP (IN of)(NP (JJ sick) (NNS thrifts)))))))))))))) (. .)))");
      	sentences.add("((S (NP (NP (DT The) (JJ complicated) (NN language)) (PP (IN in) (NP (DT the) (JJ huge) (JJ new) (NN law)))) (VP (VBZ has) (VP (VBD muddied) (NP (DT the) (NN fight)))) (. .)))");
//sentences.add("((S (NP (NP (DT No) (NN fiddling)) (PP (IN with) (NP (NNS systems) (CC and) (NNS procedures)))) (VP (MD will) (ADVP (RB ever)) (VP (VB prevent) (NP (NNS markets)) (PP (IN from) (S (VP (VBG suffering) (NP (NP (DT a) (NN panic) (NN wave)) (PP (IN of) (NP (NN selling))))))))) (. .)))");
      	break;
      case 2:
        // On this example, Max-rule should return
        // (ROOT (S (Z4 (Z5 x) (Z6 x)) (U (A (C x) (D x)) (B (G x) (H x)))))
        // While Viterbi should return
        // (ROOT (S (Z4 (Z5 x) (Z6 x)) (U (B (G x) (H x)) (B (G x) (H x)))))
        sentences.add("((S (Z1 (Z2 The) (NNPS crazsgsds)) (U3 (Uu (A1 (NNP x1) (NNPS x2))))))");// (B (G x) (H x))))");
        sentences.add("((S (K (U2 (Z1 (Z2 x) (NNP x)))) (U7 (NNS x))))");//
        sentences.add("((S (Z1 (NNPS x) (NN x)) (F (CC y) (ZZ z))))");//
        //sentences.add("((S (Z4 (Z5 x) (Z6 x)) (U (B (G x) (H x)) (B (G x) (H x)))))");
        //sentences.add("((S (Z7 (Z8 x) (Z9 x)) (U (B (G x) (H x)) (B (G x) (H x)))))");
        //sentences.add("((S (V (Z10 (Z11 x) (Z12 x)) (A (C x) (D x))) (Z13 (Z14 x) (Z15 x))))");
        //sentences.add("((S (V (Z16 (Z17 x) (Z18 x)) (A (C x) (D x))) (Z19 (Z20 x) (Z21 x))))");
        break;
      case 3:
        // On this example, Max-rule should return
        // (ROOT (S (A x) (B x))) until the threshold is too large
//      	sentences.add("((X (C (B b) (B b)) (F (E (D d)))))");
      	sentences.add("((Y (C (B a) (B a)) (E (D d))))");
      	sentences.add("((X (C (B b) (B b)) (E (D d))))");
        //sentences.add("((T (X t) (X t)))");
        //sentences.add("((S (X s) (X s)))");
        //sentences.add("((S (A x) (B x)))");
        break;
      case 4:
      	//sentences.add("((S (NP (DT The) (NN house)) (VP (VBZ is) (ADJP (JJ green))) (. .)))");
      	sentences.add("((S (NP (NP (DT The) (JJ complicated) (NN language)) (PP (IN in) (NP (DT the) (JJ huge) (JJ new) (NN law)))) (VP (VBZ has) (VP (VBN muddied) (NP (DT the) (NN fight)))) (. .)))");
      	break;
      default:
      	
      }
      for (String sentence : sentences) {
        reader = new Trees.PennTreeReader(new StringReader(sentence));
        tree = reader.next();
        trainTrees.add(tree);
        devTestTrees.add(tree);
        validationTrees.add(tree);
      }
    }
    //load from at least one corpus
    else {
    	try {
    		//load from chinese if possible
    		if (myLanguage==CHINESE) {
    			System.out.println("Loading CHINESE data!");
    			loadChinese(path, chineseSmall);
    		}
    		//load from WSJ & Brown only if no Chinese
    		else if(myLanguage==ENGLISH) {
    			System.out.println("Loading ENGLISH data!");
    			loadWSJ(path,onlyTest);
	    		/*if (pathBrown!=null && !pathBrown.equals("null")) {
	          loadBrown(pathBrown);    			
	    		}*/
    		} 
    		else if(myLanguage==GERMAN){
    			System.out.println("Loading GERMAN data!");
    			loadGerman(path);
    		}
    		else if(myLanguage==BROWN){
    			System.out.println("Loading SPANISH data!");
    			loadBrown(path);
    		}
    		else if(myLanguage==SPANISH){
    			System.out.println("Loading SPANISH data!");
    			loadSpanish(path);
    		}
    		else if(myLanguage==DANISH){
    			System.out.println("Loading CoNLL converted data!");
    			loadCONLL(path);
    		}
    	} catch (Exception e) {
    		System.out.println("error loading trees");
    		throw new Error(e.getMessage(),e);
    	}
    }		
	}

	/**
	 * @param pathChinese
	 * @throws Exception
	 */
	private void loadChinese(String pathChinese, boolean chineseSmall) throws Exception {
		System.out.print("Loading Chinese treebank trees...");
		trainTrees.addAll(readTrees(pathChinese, 1, 25,Charset.forName("GB18030")));
		trainTrees.addAll(readTrees(pathChinese, 26, 270,Charset.forName("GB18030")));
		if (!chineseSmall) {
			trainTrees.addAll(readTrees(pathChinese,400,1151,Charset.forName("GB18030")));
		}
		devTestTrees.addAll(readTrees(pathChinese, 301, 325,Charset.forName("GB18030")));
		validationTrees.addAll(readTrees(pathChinese, 301, 325,Charset.forName("GB18030")));
		finalTestTrees.addAll(readTrees(pathChinese, 271, 300,Charset.forName("GB18030")));
		System.out.print(""
				+ (trainTrees.size() + " "+ validationTrees.size() +  " "+  devTestTrees.size()+ " "
						+ finalTestTrees.size()) + " trees...");
		System.out.println("done");
	}
	
	/**
	 * @param pathBrown
	 * @throws Exception
	 */
	private void loadBrown(String pathBrown) throws Exception {
		String[] sections = {"cf", "cg", "ck", "cl", "cm", "cn", "cp", "cr"};
		int[] sectionTrainCounts = new int[sections.length];
		//read in each section at a time
		for (int i=0; i<sections.length; i++) {
		  List<Tree<String>> sectionTrainTrees = new ArrayList<Tree<String>>();
		  List<Tree<String>> sectionValidationTrees = new ArrayList<Tree<String>>();
		  List<Tree<String>> sectionDevTestTrees = new ArrayList<Tree<String>>();
		  List<Tree<String>> sectionFinalTestTrees = new ArrayList<Tree<String>>();

		  String sectionPath = pathBrown+"/"+sections[i];
		  List<Tree<String>> sectionTrees = readTrees(sectionPath,0,1000,Charset.defaultCharset());
		  splitTrainValidTest(sectionTrees, sectionTrainTrees,
					sectionValidationTrees, sectionDevTestTrees,
					sectionFinalTestTrees);
		  trainTrees.addAll(sectionTrainTrees);
		  validationTrees.addAll(sectionValidationTrees);
		  devTestTrees.addAll(sectionDevTestTrees);
		  finalTestTrees.addAll(sectionFinalTestTrees);
		  sectionTrainCounts[i] = sectionTrainTrees.size();
		  System.out.println("I read "+sectionTrainCounts[i]+" training trees from section "+sections[i]);
		}
	}

	private void loadSpanish(String path) throws Exception {
		System.out.print("Loading Spanish trees...");
		trainTrees.addAll(readTrees(path, 1, 1,Charset.defaultCharset())); // was 200, 2099
		validationTrees.addAll(readTrees(path, 2, 279,Charset.defaultCharset())); // was 2100, 2199
		devTestTrees.addAll(readTrees(path, 2, 279,Charset.defaultCharset()));
		finalTestTrees.addAll(readTrees(path, 2, 279,Charset.defaultCharset()));
		System.out.println("done");
	}
	
	private void loadCONLL(String path) throws Exception {
		System.out.print("Loading CoNLL trees...");
		trainTrees.addAll(readTrees(path, 1, 1,Charset.forName("UTF-8")));
		validationTrees.addAll(readTrees(path, 2, 2,Charset.forName("UTF-8")));
		devTestTrees.addAll(readTrees(path, 2, 2,Charset.forName("UTF-8")));
		finalTestTrees.addAll(readTrees(path, 2, 2,Charset.forName("UTF-8")));
		System.out.println("done");
	}

	/**
	 * @param pathWSJ
	 * @throws Exception
	 */
	private void loadWSJ(String pathWSJ,boolean onlyTest) throws Exception {
		System.out.print("Loading WSJ trees...");
		if (!onlyTest) {
			trainTrees.addAll(readTrees(pathWSJ, startTrainSection*100, (startTrainSection+numTrainSections)*100-1,Charset.defaultCharset()));
			//trainTrees.addAll(readTrees(pathWSJ, 200, 2199,Charset.defaultCharset())); // was 200, 2199
			validationTrees.addAll(readTrees(pathWSJ, 2200, 2299,Charset.defaultCharset())); // was 2100, 2199
		}
		//devTestTrees.addAll(readTrees(pathWSJ, 200, 299,Charset.defaultCharset())); // was 200, 2099
		
		devTestTrees.addAll(readTrees(pathWSJ, 2400, 2499,Charset.defaultCharset()));
		finalTestTrees.addAll(readTrees(pathWSJ, 2300, 2399,Charset.defaultCharset()));
		System.out.println("done");
	}
	
	private void loadGerman(String path) throws Exception {
		System.out.print("Loading German trees...");
		List<Tree<String>> tmp = readTrees(path, 1, 3,Charset.forName("UTF-8"));
		int i=0;
		for (Tree<String> tree : tmp){
			if (i<18602) { trainTrees.add(tree); }
			else if (i<19602){ finalTestTrees.add(tree);}
			else { validationTrees.add(tree); devTestTrees.add(tree); }
			i++;
		} 
		System.out.println("done.\nThere are "+trainTrees.size()+" "+devTestTrees.size()+" "+finalTestTrees.size()+" trees.");
	}
	
	
	public static List<Tree<String>> readTrees(String basePath, int low, int high, Charset charset) throws Exception {
    Collection<Tree<String>> trees = PennTreebankReader.readTrees(basePath, low, high, charset);
    // normalize trees
    Trees.TreeTransformer<String> treeTransformer = new Trees.StandardTreeNormalizer();
    List<Tree<String>> normalizedTreeList = new ArrayList<Tree<String>>();
    for (Tree<String> tree : trees) {
      Tree<String> normalizedTree = treeTransformer.transformTree(tree);
      normalizedTreeList.add(normalizedTree);
    }
    if (normalizedTreeList.size()==0) {
    	throw new Exception("failed to load any trees at "+basePath+" from "+low+" to "+high);
    }
    if(hmm) {
      System.out.println("Converting to POS trees for HMMs");
      normalizedTreeList = makePosTrees(normalizedTreeList);
    }
    return normalizedTreeList;
  }
  
  /** Split a set of trees into 7/10 training, 1/10 validation, 1/10 dev test, 1/10 final test
   * sets.  Every set of 10 sentences is split exactly into these sets deterministically.
   * 
   * @param sectionTrees
   * @param trainTrees
   * @param validationTrees
   * @param sectionTestTrees
   */
  public static void splitTrainValidTest( List<Tree<String>> sectionTrees, List<Tree<String>> trainTrees, List<Tree<String>> validationTrees, List<Tree<String>> devTestTrees, List<Tree<String>> finalTestTrees ) {
    final int CYCLE_SIZE=10;
    for( int i=0; i<sectionTrees.size(); i++ ){
      if (i%CYCLE_SIZE < 7) {
        trainTrees.add(sectionTrees.get(i));
      } else if (i%CYCLE_SIZE == 7) {
        validationTrees.add(sectionTrees.get(i));
      } else if (i%CYCLE_SIZE == 8) {
        devTestTrees.add(sectionTrees.get(i));
      } else if (i%CYCLE_SIZE == 9) {
        finalTestTrees.add(sectionTrees.get(i));
      }
    }
  }
  
  public static List<Tree<String>> binarizeAndFilterTrees(
			List<Tree<String>> trees, boolean annotate, int verticalAnnotations,
			int horizontalAnnotations, int sentenceMaxLength,
			Binarization binarization, boolean manualAnnotation, boolean VERBOSE, 
			boolean deleteLabels, boolean deletePC) {
		List<Tree<String>> binarizedTrees = new ArrayList<Tree<String>>();
    System.out.print("Binarizing and annotating trees...");

    if (VERBOSE && annotate) 
      System.out.println("annotation levels: vertical="+verticalAnnotations+" horizontal="+horizontalAnnotations);

    int i = 0;
    for (Tree<String> tree : trees) {
      List<String> testSentence = tree.getYield();
      i++;
      if (testSentence.size() > sentenceMaxLength) continue;
      //if (noUnaries && tree.hasUnaryChain()) continue;
      if (annotate) {
        binarizedTrees.add(TreeAnnotations.processTree(tree,verticalAnnotations,horizontalAnnotations,binarization,manualAnnotation,deleteLabels, deletePC));
      } else {
        binarizedTrees.add(TreeAnnotations.binarizeTree(tree,binarization));
      }
    }
    System.out.print("done.\n");
    return binarizedTrees;
  }

  /** Get the training trees.
   * 
   * @return
   */
  public List<Tree<String>> getTrainTrees() {
		return trainTrees;
	}
	
  /** Get the validation trees.
   * 
   * @return
   */
	public List<Tree<String>> getValidationTrees() {
		return validationTrees;
	}
	
	/** Get the trees we test on during development.
	 * 
	 * @return
	 */
	public List<Tree<String>> getDevTestingTrees() {
		return devTestTrees;
	}
	
	/** Get the trees we test on for our final results.
	 * 
	 * @return
	 */
	public List<Tree<String>> getFinalTestingTrees() {
		return finalTestTrees;
	}
	
	public static List<Tree<String>> makePosTrees(List<Tree<String>> trees){
		System.out.print("Making POS-trees...");
		List<Tree<String>> posTrees = new ArrayList<Tree<String>>();
    for (Tree<String> tree : trees) {
    	posTrees.add(makePosTree(tree));
    }
    System.out.print(" done.\n");
    return posTrees;
	}

	
	public static Tree<String> makePosTree(Tree<String> tree){
  	List<Tree<String>> terminals = tree.getTerminals();
  	List<String> preTerminals = tree.getPreTerminalYield();
  	
  	int n = preTerminals.size();
  	String label = "STOP"; //preTerminals.get(n-1); 

  	List<Tree<String>> tmpChildList = new ArrayList<Tree<String>>();
  	tmpChildList.add(new Tree<String>(label));//terminals.get(n-1));
  	Tree<String> tmpTree = new Tree<String>(label,tmpChildList);

  	//tmpChildList = new ArrayList<Tree<String>>();
  	//tmpChildList.add(tmpTree);
  	Tree<String> posTree = tmpTree; //new Tree<String>(label,tmpChildList);

  	for (int i=n-1; i>=0; i--){
  		label = preTerminals.get(i);
  		tmpChildList = new ArrayList<Tree<String>>();
    	tmpChildList.add(terminals.get(i));
    	tmpTree = new Tree<String>(label,tmpChildList);

  		tmpChildList = new ArrayList<Tree<String>>();
  		tmpChildList.add(tmpTree);
    	tmpChildList.add(posTree);
  		posTree = new Tree<String>(label,tmpChildList);//"X"
  	}
  	tmpChildList = new ArrayList<Tree<String>>();
  	tmpChildList.add(posTree);
  	posTree = new Tree<String>(tree.getLabel(),tmpChildList);
  	return posTree;
  }
  

	
}
