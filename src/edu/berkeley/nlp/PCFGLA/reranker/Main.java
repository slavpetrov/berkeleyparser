package edu.berkeley.nlp.PCFGLA.reranker;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import edu.berkeley.nlp.PCFGLA.Grammar;
import edu.berkeley.nlp.PCFGLA.Lexicon;
import edu.berkeley.nlp.PCFGLA.ParserData;
import edu.berkeley.nlp.PCFGLA.SophisticatedLexicon;
import edu.berkeley.nlp.PCFGLA.TreeAnnotations;
import edu.berkeley.nlp.PCFGLA.reranker.FeatureExtractorManager.Feature;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees;
import edu.berkeley.nlp.syntax.Trees.PennTreeReader;
import edu.berkeley.nlp.util.Logger;
import edu.berkeley.nlp.util.Numberer;
import fig.basic.IOUtils;
import fig.basic.Indexer;
import fig.basic.Option;
import fig.exec.Execution;

public class Main implements Runnable {
	// Data
	@Option(gloss = "Sentence file to parse.")
	public String inputFile = null;
	@Option(gloss = "Number of sentences to skip.")
	public int offset = 0;

	// Approximation
	@Option(gloss="Baseline model grammar file location", required=true)
	public String grammarFile;
	@Option(gloss="Pruning model grammar file location.  Used to prune with different probs without affecting labels.")
	public String pruningGrammarFile = null;
	@Option(gloss="Threshold (in probability) for baseline model pruning")
	public double pruningThreshold = -20;
	@Option(gloss="Size of beam at each forest node during reranking")
	public int beamSize = 10;
	
	// Intermediate forests
	@Option(gloss="Write forests to disk and stop")
	public boolean writeForestsToDisk = false;
	@Option(gloss="Read forests from disk instead of reading sentences from file")
	public boolean readForestsFromDisk = false;
	@Option(gloss="Directory for reading/writing forests")
	public String forestsDir = "prunedForests";
	
	// Debug feature extractors
	@Option(gloss="Use real feature extractors")
	public boolean useFeatExtractors = false;
	
	// Debug oracle
	@Option(gloss="Get oracle trees")
	public boolean getOracleTrees = false;
	@Option(gloss="Gold tree file")
	public String goldTreeFile = null;

	// Training
	public static void main(String[] args) {
		Main m = new Main();
		Execution.init(args, m);
		m.run();
		Execution.finish();
	}

	public void run() {
		Logger.setFig();
		Logger.startTrack("Loading baseline grammar...");
		ParserData pData = ParserData.Load(grammarFile);
		Logger.logss("Data loaded.");
		Grammar grammar = pData.getGrammar();
		Lexicon lexicon = pData.getLexicon();
		Numberer.setNumberers(pData.getNumbs());
		BaseModel baseModel = new BaseModel(grammar);
		Logger.logss("State info precomputed.");
		Logger.endTrack();
		
		Grammar pruningGrammar = grammar;
		Lexicon pruningLexicon = lexicon;
		if (pruningGrammarFile != null && !readForestsFromDisk) {
			Logger.startTrack("Loading and copying scores from pruning grammar...");
			GrammarMerger merger = new GrammarMerger();
			ParserData pruningData = ParserData.Load(pruningGrammarFile);
			Logger.logss("Data loaded.");
			pruningGrammar = merger.mergeGrammars(grammar, pruningData.getGrammar());
			Logger.logss("Grammars merged.");
			pruningLexicon = ((SophisticatedLexicon)pruningData.getLexicon()).remapStates(pruningData.getGrammar().getTagNumberer(), grammar.getTagNumberer());
			Logger.logss("Lexicons merged.");
			Logger.endTrack();
		}

		Pruner pruner = null;
		if (!readForestsFromDisk) {
			Logger.startTrack("Initializing baseline parser...");
			pruner = new MaxRulePruner(pruningGrammar, pruningLexicon, pData.getSpanPredictor(), pruningThreshold);
			Logger.endTrack();
		}

		FeatureExtractorManager manager;
		if(useFeatExtractors) {
			manager = new FeatureExtractorManager(baseModel);
		} else {
			Indexer<Feature> index = new Indexer<Feature>();
			index.add(new Feature() {});
			DummyFeatureExtractor extractor = new DummyFeatureExtractor();
			manager = new FeatureExtractorManager(index, extractor, extractor);
		}

		ForestReranker reranker = new ForestReranker(baseModel, manager, beamSize);
		reranker.setWeights(new double[] {1.0} );
		
		OracleTreeFinder oracleFinder = new OracleTreeFinder(baseModel);

		BufferedReader sentenceReader = null;
		Trees.PennTreeReader goldTreeReader = null;
		PrunedForestReader forestReader = null;
		PrunedForestWriter forestWriter = null;
		PrintWriter treeWriter = null;
		if (readForestsFromDisk) {
			forestReader = new PrunedForestReader(new File(forestsDir), ".bin");
		} else {
			sentenceReader = IOUtils.openInHard(inputFile);
		}
		if (writeForestsToDisk) {
			forestWriter = new PrunedForestWriter(new File(forestsDir), "forests.bin", 1000, true);
		} else {
			treeWriter = IOUtils.openOutHard("trees.out");
		}
		if (getOracleTrees && !writeForestsToDisk) {
			goldTreeReader = new Trees.PennTreeReader(IOUtils.openInHard(goldTreeFile));
		}

		try {
			for (int i=0; i<offset; i++) {
				burn(sentenceReader, goldTreeReader, forestReader);
			}
			int idx = 0;
			if (getOracleTrees) {
				Logger.startTrack("Finding oracle trees");
			} else {
				Logger.startTrack("Parsing sentences");
			}
			for (PrunedForest prunedForest = getNextForest(sentenceReader, forestReader, pruner); prunedForest != null;
			prunedForest = getNextForest(sentenceReader, forestReader, pruner)) {
				if (writeForestsToDisk) {
					forestWriter.writeForest(prunedForest);
				} else {
					Tree<String> tree;
					if (getOracleTrees) {
						Tree<String> goldTree = goldTreeReader.next();
						tree = oracleFinder.getOracleTree(prunedForest, goldTree);
					} else {
						tree = reranker.getBestParse(prunedForest);
					}
					if (tree == null || tree.isLeaf()) {
						Logger.err("Error parsing sentence %d", idx+1);
						treeWriter.println("()");
					} else {
						tree = TreeAnnotations.unAnnotateTree(tree);
						treeWriter.println("( " + tree.getChildren().get(0) + " )");
					}
				}
				Logger.logs("Processed %d sentences", ++idx);
			}
			Logger.endTrack();
			if (sentenceReader != null) {
				sentenceReader.close();
			}
		} catch (IOException e) {
			Logger.err("Error reading input file: %s", e);
		}
		if (forestWriter != null) {
			forestWriter.closeOutputStream();
		}
		if (treeWriter != null) {
			treeWriter.close();
		}
	}

	/**
	 * @param globalNumberer
	 * @param treeWriter
	 */
	private void dumpNumberer(Numberer globalNumberer, PrintWriter treeWriter) {
		for (int i=0; i<globalNumberer.size(); i++) {
			treeWriter.println(i+":\t"+ globalNumberer.object(i));
		}
		treeWriter.close();
	}

	private void burn(BufferedReader sentenceReader, PennTreeReader goldTreeReader, PrunedForestReader forestReader) throws IOException {
		if (sentenceReader != null) {
			sentenceReader.readLine();
		}
		if (goldTreeReader != null) {
			goldTreeReader.next();
		}
		if (forestReader != null) {
			forestReader.getNextForest();
		}
	}

	private PrunedForest getNextForest(BufferedReader sentenceReader, PrunedForestReader forestReader, Pruner pruner) throws IOException {
		if (readForestsFromDisk) {
			return forestReader.getNextForest();
		} else {
			String line = sentenceReader.readLine();
			if (line == null) {
				return null;
			}
			List<String> sentence = tokenize(line);
			return pruner.getPrunedForest(sentence);
		}
	}

	private List<String> tokenize(String line) {
		String[] words = line.split(" ");
		ArrayList<String> list = new ArrayList<String>(words.length);
		for (String w : words) {
			list.add(w);
		}
		return list;
	}
}
