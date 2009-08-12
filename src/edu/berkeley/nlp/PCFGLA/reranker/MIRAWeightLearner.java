/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import edu.berkeley.nlp.PCFGLA.Grammar;
import edu.berkeley.nlp.PCFGLA.Lexicon;
import edu.berkeley.nlp.PCFGLA.ParserData;
import edu.berkeley.nlp.PCFGLA.TreeAnnotations;
import edu.berkeley.nlp.PCFGLA.reranker.FeatureExtractorManager.Feature;
import edu.berkeley.nlp.parser.EnglishPennTreebankParseEvaluator;
import edu.berkeley.nlp.parser.EnglishPennTreebankParseEvaluator.LabeledConstituentEval;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.util.Counter;
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
public class MIRAWeightLearner implements Runnable {
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

	// Tree stuff

	@Option(gloss = "Path to Corpus (Default: null)")
	public static String path = null;

	@Option(gloss = "True if you're using a single file with trees rather than WSJ")
	public static boolean singleFile = false;

	@Option(gloss =  "Maximum sentence length (Default <=10000)")
	public static int maxSentenceLength = 10000;

	@Option(gloss = "File to write final weights to")
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

	@Option(gloss = "Number of MIRA iterations")
	public int miraIter = 10;

	@Option(gloss = "Filter for getting held out set")
	public String heldOutFilter;
	
	@Option(gloss = "Gold trees for held out set")
	public String heldOutTreePath;

	
	// Non-options global vars
	FeatureExtractorManager manager;
	ForestReranker reranker;
	OracleTreeFinder oracle;
	Pruner pruner;
	List<int[][]> parseLocalFeaturesBinary = null;
	List<int[][]> parseLocalFeaturesUnary = null;
	List<Pair<Double, int[]>> oracleFeatures = null;

	List<Tree<String>> trainTrees;
	List<Tree<String>> oracleTrees = new ArrayList<Tree<String>>();
	List<PrunedForest> trainForests = new ArrayList<PrunedForest>();
	List<RerankedForest> oracleForests = new ArrayList<RerankedForest>();
	Counter<Integer> localFeatureCounts = new Counter<Integer>();
	
	List<Tree<String>> heldOutTrees;
	List<PrunedForest> heldOutForests;

	EnglishPennTreebankParseEvaluator.LabeledConstituentEval<String> evaluator;


	// Training
	public static void main(String[] args) {
		MIRAWeightLearner m = new MIRAWeightLearner();
		Execution.init(args, m);
		System.out.println("Loading trees from "+path+" and using singleFile: "+ singleFile);

		System.out.println("Will remove sentences with more than "+maxSentenceLength+" words.");
		m.run();
		Execution.finish();
	}

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
		oracle = new OracleTreeFinder(baseModel);
		if(!readFromForest)
			pruner = new MaxRulePruner(grammar, lexicon, pData.getSpanPredictor(), pruningThreshold);
		Logger.logss("Done.");

		manager =  new FeatureExtractorManager(baseModel); 
		evaluator = new LabeledConstituentEval<String>(new HashSet<String>(), new HashSet<String>(0));
		
		reranker = new ForestReranker(baseModel, manager, beamSize);
		reranker.setWeights(new double[] {0.0} );

		TreeLoader tl = new TreeLoader(path,singleFile,goldTreeStart,goldTreeStop);
		trainTrees = tl.getTrainTrees();
		if(readFromForest) {
			loadForests(trainTrees);
		}
		precomputeLocalFeatures(trainTrees);
	}
	
	private void loadHeldOutSet(List<Tree<String>> heldOut) {
		heldOutTrees = heldOut;
		
	}

	private void loadForests(List<Tree<String>> trainTrees) {
		PrunedForestReader forestReader = new PrunedForestReader(new File(forestDirectory), forestFileFilter);
		for(int i = 0; i < trainTrees.size(); i++) {
			PrunedForest curForest = forestReader.getNextForest();
			Tree<String> oracleTree = oracle.getOracleTree(curForest, trainTrees.get(i));
			RerankedForest oracleForest = oracle.getOracleTreeAsForest(curForest, trainTrees.get(i));
			trainForests.add(curForest);
			oracleTrees.add(oracleTree);
			oracleForests.add(oracleForest);
		}
	}

	private void precomputeLocalFeaturesForest() {
		parseLocalFeaturesBinary = new ArrayList<int[][]>();
		parseLocalFeaturesUnary = new ArrayList<int[][]>();
		oracleFeatures = new ArrayList<Pair<Double,int[]>>();
		for(int i = 0; i < oracleForests.size(); i++) {
			PrunedForest curForest = trainForests.get(i);
			RerankedForest oracleForest = oracleForests.get(i);
			if(oracleForest == null) {
				parseLocalFeaturesBinary.add(null);
				parseLocalFeaturesUnary.add(null);
				oracleFeatures.add(null);
			} else {
				if(!curForest.getSentence().equals(oracleForest.getSentence())) {
					System.out.println("FORESTS NOT EQUAL:" );
					System.out.println("curForest: " + curForest.getSentence());
					System.out.println("oracleTree: " + oracleForest.getSentence());
				}
				//System.out.println("Oracle tree: " + oracleTree);
				//((CheatingLocalFeatureExtractor) manager.getLocalFeatureExtractor()).setGoldTree(trainTrees.get(i));
				int[][] binaryLocalFeatures = manager.getLocalFeatureExtractor().precomputeLocalIndicatorFeatures(curForest.getBinaryEdges(), curForest.getSentence());
				int[][] unaryLocalFeatures = manager.getLocalFeatureExtractor().precomputeLocalIndicatorFeatures(curForest.getUnaryEdges(), curForest.getSentence());
				int[][] binaryLocalFeaturesOracle = manager.getLocalFeatureExtractor().precomputeLocalIndicatorFeatures(oracleForest.getBinaryEdgesFromViterbiTree(), oracleForest.getSentence());
				int[][] unaryLocalFeaturesOracle = manager.getLocalFeatureExtractor().precomputeLocalIndicatorFeatures(oracleForest.getUnaryEdgesFromViterbiTree(), oracleForest.getSentence());

				addToFeatCounter(binaryLocalFeatures);
				addToFeatCounter(unaryLocalFeatures);
				addToFeatCounter(binaryLocalFeaturesOracle);
				addToFeatCounter(unaryLocalFeaturesOracle);
				Pair<Double, int[]> oracleFeatureSet = reranker.getViterbiTreeFeatureVector(oracleForest);
				parseLocalFeaturesBinary.add(binaryLocalFeatures);
				parseLocalFeaturesUnary.add(unaryLocalFeatures);
				oracleFeatures.add(oracleFeatureSet);
				
			}
			if((i % 100) == 0)
				System.out.println("Precomputed features for " + i + " trees.");
		}
	}
	
	private void addToFeatCounter(int[][] features) {
		for(int j = 0; j < features.length; j++) {
			for(int k = 0; k < features[j].length; k++)
				localFeatureCounts.incrementCount(features[j][k], 1);
		}
	}
	
	private void precomputeLocalFeatures(List<Tree<String>> oracleTrees) {
		if(readFromForest) {
			precomputeLocalFeaturesForest();
			return;
		}

		parseLocalFeaturesBinary = new ArrayList<int[][]>();
		parseLocalFeaturesUnary = new ArrayList<int[][]>();
		oracleFeatures = new ArrayList<Pair<Double,int[]>>();

		for(int i = 0; i < oracleTrees.size(); i++) {
			Tree<String> oracleTree = oracleTrees.get(i);
			PrunedForest curForest;

			List<String> sentence = oracleTree.getYield();
			curForest = pruner.getPrunedForest(sentence);
			RerankedForest oracleForest = oracle.getOracleTreeAsForest(curForest, oracleTree);
			if(oracleForest == null) {
				parseLocalFeaturesBinary.add(null);
				parseLocalFeaturesUnary.add(null);
				oracleFeatures.add(null);
			} else {
				if(!curForest.getSentence().equals(oracleTree.getYield())) {
					System.out.println("FORESTS NOT EQUAL:" );
					System.out.println("curForest: " + curForest.getSentence());
					System.out.println("oracleTree: " + oracleTree.getYield());
				}
				//System.out.println("Oracle tree: " + oracleTree);
				parseLocalFeaturesBinary.add(manager.getLocalFeatureExtractor().precomputeLocalIndicatorFeatures(curForest.getBinaryEdges(), curForest.getSentence()));
				parseLocalFeaturesUnary.add(manager.getLocalFeatureExtractor().precomputeLocalIndicatorFeatures(curForest.getUnaryEdges(), curForest.getSentence()));
				oracleFeatures.add(reranker.getViterbiTreeFeatureVector(oracleForest));
			}
			if((i % 100) == 0)
				System.out.println("Precomputed features for " + i + " trees.");
		}

	}

	public void runForest() {
		PrintWriter writer = IOUtils.openOutHard("trees.out");
		PrintWriter writerDebug = IOUtils.openOutHard("taus.out");

		List<Integer> numMissed = new ArrayList<Integer>();
		List<Double> hammingTotals = new ArrayList<Double>();

		int numIter = 0;
		while(true) {
			double curHammingError = 0;
			Logger.startTrack("Parsing sentences");
			int idx = 0;
			int curMissed = 0;
			for (int i = 0; i < oracleTrees.size(); i++) {
				PrunedForest curForest = trainForests.get(i);
				Tree<String> tree = reranker.getBestParse(curForest, parseLocalFeaturesBinary.get(i), parseLocalFeaturesUnary.get(i));
				if (tree == null || tree.isLeaf()) {
					Logger.err("Error parsing sentence %d: %s", idx+1, trainTrees.get(i).getYield());
					writer.println("()");
				} else {
					Tree<String> oracleTree = oracleTrees.get(i);
					//check if we're wrong; if so, update weights
					tree = TreeAnnotations.unAnnotateTree(tree);
					oracleTree = TreeAnnotations.unAnnotateTree(oracleTree);

//					System.out.println(tree);
//					System.out.println(oracleTree);
					if(!oracleTree.equals(tree)) {
						curMissed++;
						//RerankedForest oracleForest = oracle.getOracleTreeAsForest(curForest, oracleTree);
						double hamming = applyWeightChanges(curForest, oracleForests.get(i), i, tree, oracleTree);
						curHammingError += hamming;
					}
					writer.println("( " + tree.getChildren().get(0) + " )");
				}

				++idx;
				if((idx % 100) == 0)
					Logger.logs("Parsed %d sentences", ++idx);
			}
			numMissed.add(curMissed);
			System.out.println("Missed parses: " + curMissed);
			hammingTotals.add(curHammingError);
			System.out.println("Hamming error: " + curHammingError);
			System.out.println("Finished iteration " + numIter);
			Logger.endTrack();
			writeWeights(reranker.getCurrentWeights(), manager.features, numIter);
			numIter++;
			// check for exit conditions
			if(heldOutFilter == null) {
				if(numIter >= miraIter)
					break;
			} else {
				
			}
		}
		writer.close();
		writerDebug.close();
		System.out.println("Num missed over all iterations: " + numMissed);
		System.out.println("Hamming error all iterations: " + hammingTotals);
	}

	public void run() {
		init();

		if(readFromForest) {
			runForest();
			return;
		}
		//get the forest if necessary
		PrunedForestReader forestReader =null;
		if(readFromForest) {
			forestReader = new PrunedForestReader(new File(forestDirectory), forestFileFilter);
		}
		precomputeLocalFeatures(trainTrees);

		PrintWriter writer = IOUtils.openOutHard("trees.out");

		List<Integer> numMissed = new ArrayList<Integer>();
		for(int numIter = 0; numIter < miraIter; numIter++) {
			Logger.startTrack("Parsing sentences");
			int idx = 0;
			int curMissed = 0;
			if(readFromForest) {
				forestReader = new PrunedForestReader(new File(forestDirectory), forestFileFilter);
			}
			for (int i = 0; i < trainTrees.size(); i++) {
				Tree<String> curGoldTree = trainTrees.get(i);
				PrunedForest curForest;
				if(readFromForest) {
					curForest = forestReader.getNextForest();
				} else {
					List<String> sentence = curGoldTree.getYield();
					curForest = pruner.getPrunedForest(sentence);
				}

				Tree<String> tree = reranker.getBestParse(curForest, parseLocalFeaturesBinary.get(i), parseLocalFeaturesUnary.get(i));
				if (tree == null || tree.isLeaf()) {
					Logger.err("Error parsing sentence %d: %s", idx+1, curGoldTree.getYield());
					writer.println("()");
				} else {
					Tree<String> oracleTree = oracle.getOracleTree(curForest, curGoldTree);
					//check if we're wrong; if so, update weights
					System.out.println(tree);
					System.out.println(oracleTree);
					if(!oracleTree.equals(tree)) {
						curMissed++;
						RerankedForest oracleForest = oracle.getOracleTreeAsForest(curForest, oracleTree);
						applyWeightChanges(curForest, oracleForest, i, tree, oracleTree);
					}
					tree = TreeAnnotations.unAnnotateTree(tree);
					writer.println("( " + tree.getChildren().get(0) + " )");
				}

				++idx;
				if((idx % 100) == 0)
					Logger.logs("Parsed %d sentences", ++idx);
			}
			numMissed.add(curMissed);
			System.out.println("Missed parses: " + curMissed);
			System.out.println("Finished iteration " + numIter);
			Logger.endTrack();
			writeWeights(reranker.getCurrentWeights(), manager.features, numIter);

		}
		writer.close();

		System.out.println("Num missed over all iterations: " + numMissed);

	}

	private double getWeight(double[] weights, int featNum) {
		if(featNum < weights.length)
			return weights[featNum];
		else
			return 0;
	}

	private void writeWeights(double[] weights, Indexer<Feature> featureIndexer, int iteration) {
		System.out.println("Writing " + weights.length + " weights.");
		try {
//			for(Feature f : featureIndexer)
//				System.out.println(f.getClass());
			ObjectOutput out = new ObjectOutputStream(new FileOutputStream((weightFile + iteration + ".indexer")));
			out.writeObject(featureIndexer);
			out.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//		PrintWriter featWriter = IOUtils.openOutHard("featCounter.out");
//		List<Integer> sortedKeys = localFeatureCounts.getSortedKeys();
//		for(int i = 0; i < sortedKeys.size(); i++)
//			featWriter.println(localFeatureCounts.getCount(sortedKeys.get(i)));
//		featWriter.close();
//		
		PrintWriter weightWriter = IOUtils.openOutHard((weightFile + iteration +".weights"));
		for(int i = 0; i < weights.length; i++)
			weightWriter.println(weights[i]);
		weightWriter.close();
		System.out.println("Finished writing weights.");
	}

	private double applyWeightChanges(PrunedForest parseForest, RerankedForest oracleForest, int treeNum, Tree<String> parseTree, Tree<String> oracleTree) {
		Pair<Double, int[]> viterbiFeaturesForParse;
		Pair<Double, int[]> viterbiFeaturesForOracle;
		if(parseLocalFeaturesBinary == null) {
			viterbiFeaturesForParse = reranker.getViterbiTreeFeatureVector(parseForest);
			viterbiFeaturesForOracle = reranker.getViterbiTreeFeatureVector(oracleForest);
		} else {
			viterbiFeaturesForParse = reranker.getViterbiTreeFeatureVector(parseForest, parseLocalFeaturesBinary.get(treeNum), parseLocalFeaturesUnary.get(treeNum));
			viterbiFeaturesForOracle = oracleFeatures.get(treeNum);
		}
		double[] weights = reranker.getCurrentWeights();
		//first, preprocess all the features so we know if they were on for both or just one (and which one)
		double dotScoreOracle = dotProduct(weights, viterbiFeaturesForOracle.getSecond()) + weights[0]*viterbiFeaturesForOracle.getFirst();
		double dotScoreParse = dotProduct(weights, viterbiFeaturesForParse.getSecond()) + weights[0]*viterbiFeaturesForParse.getFirst();

		Counter<Integer> oracleFeats = makeCounts(viterbiFeaturesForOracle);
		Counter<Integer> parseFeats = makeCounts(viterbiFeaturesForParse);
//		Set<Integer> allFeats = new HashSet<Integer>(oracleFeats.getSortedKeys());
//		allFeats.addAll(parseFeats.getSortedKeys());
		Counter<Integer> difCounter = oracleFeats.difference(parseFeats);
		
		double tauDenom = difCounter.dotProduct(difCounter);
		//double margin = Math.abs(dotScoreOracle-dotScoreParse);//*(dotScoreOracle-dotScoreParse);
		double margin = dotScoreOracle - dotScoreParse;
		double loss = evaluator.getHammingDistance(parseTree, oracleTree);   //Math.abs(dotScoreOracle - dotScoreParse);
		double tau = (loss-margin)/tauDenom;
		if(Double.isNaN(tau)) {
			System.out.println("NAN badness");
		}
		tau = (tau > maxChange) ? maxChange : tau;
		//clip at zero
		tau = (tau < 0) ? 0 : tau;
		//perceptron hack
		//tau = 1;
		for(Integer curFeat : difCounter.keySet()) {
//			if((localFeatureCounts.containsKey(curFeat)) && (localFeatureCounts.getCount(curFeat) < 5))
//				continue;
			weights = updateWeights(weights, curFeat,tau*(oracleFeats.getCount(curFeat)- parseFeats.getCount(curFeat)));
		}
		//weights[0] = weights[0] + tau*Math.signum(viterbiFeaturesForOracle.getFirst()-viterbiFeaturesForParse.getFirst());
		reranker.setWeights(weights);

		return loss;
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

	private Counter<Integer> makeCounts(Pair<Double, int[]> features) {
		Counter<Integer> featureOccur = new Counter<Integer>();
		for(int f: features.getSecond()) {
			featureOccur.incrementCount(f, 1);
		}
		featureOccur.incrementCount(0, features.getFirst());
		//System.out.println("Feat counts: " + featureOccur.toString(10));
		return featureOccur;
	}

	private double[] updateWeights(double[] weights, int featNumber, double weightIncrement) {
		if(featNumber < weights.length) {
			//System.out.println("Updating: " + manager.getFeatureByNumber(featNumber).getClass() + " from " + weights[featNumber] + " by " + weightIncrement);
			weights[featNumber] = weights[featNumber]+weightIncrement;
			return weights;
		}
		else {
			//need to increase size of weights
			double[] newWeights = new double[featNumber+1];
			for(int i = 0; i < weights.length; i++) {
				newWeights[i] = weights[i];
			}
			Arrays.fill(newWeights,weights.length,newWeights.length, 0);
			//System.out.println("Updating: " + manager.getFeatureByNumber(featNumber).getClass() + " from " + newWeights[featNumber] + " by " + weightIncrement);
			newWeights[featNumber] = weightIncrement;
			return newWeights;
		}
	}

}

