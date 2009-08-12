/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import edu.berkeley.nlp.PCFGLA.Grammar;
import edu.berkeley.nlp.PCFGLA.Lexicon;
import edu.berkeley.nlp.PCFGLA.ParserData;
import edu.berkeley.nlp.PCFGLA.TreeAnnotations;
import edu.berkeley.nlp.PCFGLA.reranker.FeatureExtractorManager.Feature;
import edu.berkeley.nlp.parser.EnglishPennTreebankParseEvaluator;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.Lists;
import edu.berkeley.nlp.util.Logger;
import edu.berkeley.nlp.util.Numberer;
import fig.basic.IOUtils;
import fig.basic.Indexer;
import fig.basic.Option;
import fig.basic.Pair;
import fig.exec.Execution;

/**
 * @author rafferty
 *
 */
public class MIRAWeightTester {
	//Data
	@Option(gloss = "Sentence file to parse.")
	public String inputFile = null;

	// Approximation
	@Option(gloss="Baseline model grammar file location", required=true)
	public String grammarFile;
	@Option(gloss="Threshold (in probability) for baseline model pruning")
	public double pruningThreshold = -10;
	@Option(gloss="Size of beam at each forest node during reranking")
	public int beamSize = 10;

	// Tree stuf
	@Option(gloss = "True if you're using a single file with trees rather than WSJ")
	public static boolean singleFile = false;
	
	@Option(gloss = "Path to Corpus (Default: null)")
	public static String path = null;

	@Option(gloss =  "Maximum sentence length (Default <=10000)")
	public static int maxSentenceLength = 10000;

	@Option(gloss = "File to get weights from")
	public String weightFile;

	@Option(gloss = "Hyperparameter for MIRA")
	public double maxChange = 1.0;

	@Option(gloss = "Read from forest file")
	public boolean readFromForest = false;

	@Option(gloss = "Forest directory")
	public String forestDirectory;

	@Option(gloss = "Forest filefilter")
	public String forestFileFilter;
	
	@Option(gloss = "Start number from WSJ for gold trees")
	public int goldTreeStart = -1;

	@Option(gloss = "Stop number from WSJ for gold trees")
	public int goldTreeStop = -1;

	@Option(gloss = "debug comments on/off")
	public boolean debug = true;
	
	@Option(gloss = "use wsj dev set")
	public boolean useDev = false;
	
	@Option(gloss = "tree out file")
	public String treeOutFile = "trees.out";

	// Non-options global vars
	OracleTreeFinder oracle;
	FeatureExtractorManager manager;
	ForestReranker reranker;
	EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String> eval;
	Pruner pruner;

	/**
	 * Take care of basic setup; abstracted for code readability.
	 */
	private void init() {
		Logger.setFig();
		Logger.logss("Loading baseline grammar...");
		ParserData pData = ParserData.Load(grammarFile);
		Grammar grammar = pData.getGrammar();
		Lexicon lexicon = pData.getLexicon();
		Numberer.setNumberers(pData.getNumbs());
		Logger.logss("Done.");

		Logger.logss("Initializing baseline parser...");
		BaseModel baseModel = new BaseModel(grammar);
		if(!readFromForest)
			pruner = new MaxRulePruner(grammar, lexicon, pData.getSpanPredictor(), pruningThreshold);
		Logger.logss("Done.");

		eval = new EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String>(Collections.singleton("ROOT"), new HashSet<String>(Arrays.asList(new String[] {"''", "``", ".", ":", ","})));  
		double[] weights = readWeights(weightFile);
		Indexer<Feature> featureIndexer = readIndexer(weightFile);
		manager =  new FeatureExtractorManager(baseModel,featureIndexer); 
		oracle = new OracleTreeFinder(baseModel);

		reranker = new ForestReranker(baseModel, manager, beamSize);
		reranker.setWeights(weights);
		

	}
	
	private Indexer<Feature> readIndexer(String fileName) {
			try {
				ObjectInputStream in = new ObjectInputStream(new FileInputStream(fileName + ".indexer"));
				Indexer<Feature> featureIndexer = (Indexer<Feature>) in.readObject();
				return featureIndexer;
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return null;
	}
	
	private double[] readWeights(String weightFileName) {

		List<Double> weightList = new ArrayList<Double>();
		BufferedReader in = IOUtils.openInHard(weightFileName +".weights");
		try {
			while(true) {
				String line = in.readLine();
				if(line == null)
					break;
				weightList.add(Double.valueOf(line));
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.exit(-1);
		}
		return Lists.toPrimitiveArray(weightList);
	}

	// Training
	public static void main(String[] args) {
		MIRAWeightTester m = new MIRAWeightTester();
		Execution.init(args, m);
		System.out.println("Loading trees from "+path+"; singleFile:  "+ singleFile);

		System.out.println("Will remove sentences with more than "+maxSentenceLength+" words.");
		m.run();
		Execution.finish();
	}
	
	public void run() {
		init();
		List<Tree<String>> trainTrees = TreeLoader.getGoldTreesByNumber(path,singleFile,goldTreeStart,goldTreeStop);
		//get the forest if necessary
		PrunedForestReader forestReader =null;
		if(readFromForest) {
			forestReader = new PrunedForestReader(new File(forestDirectory), forestFileFilter);
		}

		PrintWriter writer = IOUtils.openOutHard(treeOutFile);

		Logger.startTrack("Parsing sentences");
			int idx = 0;
			for (int i = 0; i < trainTrees.size(); i++) {
				Tree<String> curGoldTree = trainTrees.get(i);
				PrunedForest curForest;
				if(readFromForest) {
					curForest = forestReader.getNextForest();
				} else {
					List<String> sentence = curGoldTree.getYield();
					curForest = pruner.getPrunedForest(sentence);
				}

				Tree<String> parseTree = reranker.getBestParse(curForest);
				if (parseTree == null || parseTree.isLeaf()) {
					Logger.err("Error parsing sentence %d: %s", idx+1, curGoldTree.getYield());
					writer.println("()");
				} else {
					
		      Tree<String> unAnParseTree = TreeAnnotations.unAnnotateTree(parseTree);
		      //Tree<String> unGoldTree = TreeAnnotations.unAnnotateTree(curGoldTree);

//		      System.out.println("Tree in tester:");
//		      System.out.println(unAnParseTree);
//		      System.out.println("Gold tree: " + curGoldTree);
		  		//eval.evaluate(unAnParseTree, unGoldTree);
		      if(debug) {
		    		Pair<Double, int[]> viterbiFeaturesForParse = reranker.getViterbiTreeFeatureVector(curForest);
		    		Pair<Double, int[]> viterbiFeaturesForOracle = reranker.getViterbiTreeFeatureVector(oracle.getOracleTreeAsForest(curForest, curGoldTree));
		    		double[] weights = reranker.getCurrentWeights();
		    		double dotScoreOracle = dotProduct(weights, viterbiFeaturesForOracle.getSecond()) + weights[0]*viterbiFeaturesForOracle.getFirst();
		    		double dotScoreParse = dotProduct(weights, viterbiFeaturesForParse.getSecond()) + weights[0]*viterbiFeaturesForParse.getFirst();
		    		System.out.println("Dot score oracle: " + dotScoreOracle + "; dot score parse: " + dotScoreParse);
		      }
		  		writer.println(unAnParseTree);
				}
				
				Logger.logs("Parsed %d sentences", ++idx);
			}
			System.out.println("Parsed "+idx+" sentences.");
      eval.display(true);
	 	  System.out.println("The computed F1,LP,LR scores are just a rough guide. They are typically 0.1-0.2 lower than the official EVALB scores.");
			Logger.endTrack();
			writer.close();

	}
	
	private double dotProduct(double[] w, int[] features) {
		double val = 0.0;
		for (int f : features) {
			if (f > 0 && f < w.length) {
				//				if(f < 10) {
				//					System.out.println("Cur feat: " + reranker.getFeatureExtractorManager().getFeatureByNumber(f));
				//					System.out.println("curWeight: " + w[f] + "; feat num: " + f + "; curVal: " + val);
				//				}
				val += w[f];
			}
		}
		return val;
	}

}
