/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import edu.berkeley.nlp.io.PennTreebankReader;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees;
import edu.berkeley.nlp.syntax.Trees.PennTreeReader;

/**
 * @author rafferty
 *
 */
public class TreeLoader {

	List<Tree<String>> trainTrees = new ArrayList<Tree<String>>();	
	
	
	public static List<Tree<String>> getGoldTreesByNumber(String path, boolean singleFile, int goldTreeStart, int goldTreeStop) {
		TreeLoader tl = new TreeLoader(path, singleFile, goldTreeStart, goldTreeStop);
		return tl.getTrainTrees();
	}
	
	
	/**
	 * @return the trainTrees
	 */
	public List<Tree<String>> getTrainTrees() {
		return trainTrees;
	}


	private List<Tree<String>> validationTrees = new ArrayList<Tree<String>>();	
	private List<Tree<String>> devTestTrees = new ArrayList<Tree<String>>();	
	private List<Tree<String>> finalTestTrees = new ArrayList<Tree<String>>();	
	
	public TreeLoader(String path, boolean singleFile, int treeStartNum, int treeStopNum) {
		this(path, singleFile);
		if((treeStartNum != -1) && (treeStopNum != -1)) {
			List<Tree<String>> newTrainTrees = new ArrayList<Tree<String>>();
			newTrainTrees.addAll(trainTrees.subList(treeStartNum, treeStopNum));
			trainTrees = newTrainTrees;
		}
	}

	public TreeLoader(String path, boolean singleFile, int numTreesToSkip, int numTreesTotal, int beginSkip, String printFile, boolean writeKeptTrees) {
		this(path, singleFile);
		int beforeSize = trainTrees.size();
		if(numTreesToSkip != -1) {
			//remove some trees
			List<Tree<String>> firstPart = new ArrayList<Tree<String>>(trainTrees.subList(0, beginSkip));
			List<Tree<String>> leftOutPart = new ArrayList<Tree<String>>();
			if((beginSkip+numTreesToSkip) < numTreesTotal) {
				leftOutPart.addAll(trainTrees.subList(beginSkip, beginSkip+numTreesToSkip));
				firstPart.addAll(trainTrees.subList(beginSkip+numTreesToSkip, numTreesTotal));
			} else {
				leftOutPart.addAll(trainTrees.subList(beginSkip, numTreesTotal));
			}
			System.out.println("Including trees from (0, "+beginSkip +") and ("+ (beginSkip+numTreesToSkip) + ", " + numTreesTotal + ")");
			if(printFile != null) {
				if(writeKeptTrees) {
					System.out.println("Writing " + trainTrees.size() + " kept trees");
					try {
						PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(printFile), "UTF-8"), true);
						for(int i = 0; i < trainTrees.size(); i++) {
							pw.println(trainTrees.get(i).toString());
						}
						pw.close();
					}	catch(Exception e) {
						System.out.println("Problem writing trees.");
					}
				} else {
					System.out.println("Writing " + leftOutPart.size() + " left out trees");
					try {
						PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(printFile), "UTF-8"), true);
						for(int i = 0; i < leftOutPart.size(); i++) {
							pw.println(leftOutPart.get(i).toString());
						}
						pw.close();
					}	catch(Exception e) {
						System.out.println("Problem writing trees.");
					}
				}
				System.exit(0);
			} 
			trainTrees = firstPart;
			int nTrainingWords = 0;
			for (Tree<String> tree : trainTrees) {
				nTrainingWords += tree.getYield().size();
			}
			System.out.println("In training set we have # of words: "+nTrainingWords);
			System.out.println("In training set we have # of sentences: "+trainTrees.size());

			int afterSize = trainTrees.size();
			System.out.println("reducing number of training trees from "+beforeSize+" to "+afterSize);
		}
	}

	/** Load the WSJ, Brown, and Chinese corpora from the given locations.  If any
	 * is null, don't load it.  If all are null, use the dummy sentence.  Don't
	 * load the English corpora if we load the Chinese one.
	 */
	private TreeLoader(String path, boolean singleFile) {
		try {
			if(singleFile) {
				System.out.println("Loading data from single file!");
				loadSingleFile(path);
			} else {
				System.out.println("Loading ENGLISH WSJ data!");
				loadWSJ(path);
			}
		} catch (Exception e) {
			System.out.println("Error loading trees!");
			System.out.println(e.getStackTrace().toString());
			throw new Error(e.getMessage(),e);
		}
	}		

	private void loadSingleFile(String path) throws Exception {
		System.out.print("Loading trees from single file...");
		InputStreamReader inputData = new InputStreamReader(new FileInputStream(path), "UTF-8");
		PennTreeReader treeReader = new PennTreeReader(inputData);

		while(treeReader.hasNext()){
			trainTrees.add(treeReader.next());
		}

		Trees.TreeTransformer<String> treeTransformer = new Trees.StandardTreeNormalizer();
		ArrayList<Tree<String>> normalizedTreeList = new ArrayList<Tree<String>>();
		for (Tree<String> tree : trainTrees) {
			Tree<String> normalizedTree = treeTransformer.transformTree(tree);
			normalizedTreeList.add(normalizedTree);
		}
		if (normalizedTreeList.size()==0) {
			throw new Exception("failed to load any trees at "+path);
		}
		trainTrees = normalizedTreeList;
		devTestTrees = trainTrees;
		System.out.println("done");

		//		trainTrees.addAll(readTrees(path,-1, Integer.MAX_VALUE,Charset.defaultCharset()));
	}

	/**
	 * @param pathWSJ
	 * @throws Exception
	 */
	private void loadWSJ(String pathWSJ) throws Exception {
		System.out.print("Loading WSJ trees...");

		trainTrees.addAll(readTrees(pathWSJ, 200, 2199,Charset.defaultCharset())); // was 200, 2199

		validationTrees.addAll(readTrees(pathWSJ, 2100, 2199,Charset.defaultCharset())); // was 2100, 2199

		devTestTrees.addAll(readTrees(pathWSJ, 2200, 2299,Charset.defaultCharset()));
		finalTestTrees.addAll(readTrees(pathWSJ, 2300, 2399,Charset.defaultCharset()));
		System.out.println("done");
	}
	
	/**
	 * NOT FINISHED
	 * @param basePath
	 * @param charset
	 * @return
	 */
	public static List<Tree<String>> readDev(String basePath, Charset charset) {
		return null;
	}

	public static List<Tree<String>> readTrees(String basePath, int low, int high, Charset charset) throws Exception {
		Collection<Tree<String>> trees = PennTreebankReader.readTrees(basePath, low, high, charset);
		System.out.println("in readTrees: " + basePath);
		// normalize trees
		//Trees.TreeTransformer<String> treeTransformer = new Trees.IncludeFunctionTagsTreeNormalizer();
		//Trees.TreeTransformer<String> treeTransformer = new Trees.StandardTreeNormalizer();
		Trees.TreeTransformer<String> treeTransformer = new Trees.EmptyNodeRelabeler();


		List<Tree<String>> normalizedTreeList = new ArrayList<Tree<String>>();
		for (Tree<String> tree : trees) {
			Tree<String> normalizedTree = treeTransformer.transformTree(tree);
			normalizedTreeList.add(normalizedTree);
		}
		if (normalizedTreeList.size()==0) {
			throw new Exception("failed to load any trees at "+basePath+" from "+low+" to "+high);
		}
		System.out.println("Read " + normalizedTreeList.size() + " trees.");
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


	public static List<Tree<String>> filterTreesForConditional(List<Tree<String>> trees, boolean filterAllUnaries, boolean filterStupidFrickinWHNP, boolean justCollapseUnaryChains){
		List<Tree<String>> filteredTrees = new ArrayList<Tree<String>>(trees.size());
		OUTER: for (Tree<String> tree : trees) {
			if (tree.getYield().size()==1) continue;
			if (tree.hasUnaryChain()) {
				if (justCollapseUnaryChains) {
					//    			System.out.println(tree);
					tree.removeUnaryChains();
					//    			System.out.println(tree);
				}
				else continue;
			}
			if (filterStupidFrickinWHNP)
			{
				for (Tree<String> n : tree.getNonTerminals())
				{
					//    			if (n.getLabel().equals("@WHNP^g") || (n.getLabel().equals("WHNP") && n.getChildren().size() > 1))
					if (n.getLabel().contains("WHNP"))
						continue OUTER;
				}
			}
			if (filterAllUnaries && tree.hasUnariesOtherThanRoot()) continue;
			filteredTrees.add(tree);
		}
		return filteredTrees;
	}





	public static void lowercaseWords(List<Tree<String>> trainTrees){
		for (Tree<String> tree : trainTrees){
			List<Tree<String>> words = tree.getTerminals();
			for (Tree<String> word : words){
				String lWord = word.getLabel().toLowerCase();
				word.setLabel(lWord);
			}
		}
	}
}
