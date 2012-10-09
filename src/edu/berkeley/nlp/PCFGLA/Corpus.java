/**
 * 
 */
package edu.berkeley.nlp.PCFGLA;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import edu.berkeley.nlp.io.PennTreebankReader;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees;
import edu.berkeley.nlp.syntax.Trees.PennTreeReader;
import edu.berkeley.nlp.util.Counter;

/**
 * Class Corpus will give easy access to loading the training, validation,
 * development testing, and testing sets from both the WSJ and Brown corpora.
 * 
 * @author leon
 * 
 */
public class Corpus {

	public enum TreeBankType {
		BROWN, WSJ, CHINESE, GERMAN, SPANISH, FRENCH, CONLL, SINGLEFILE
	}

	public static TreeBankType myTreebank = TreeBankType.WSJ;
	public static boolean keepFunctionLabels;

	ArrayList<Tree<String>> trainTrees = new ArrayList<Tree<String>>();
	ArrayList<Tree<String>> validationTrees = new ArrayList<Tree<String>>();
	ArrayList<Tree<String>> devTestTrees = new ArrayList<Tree<String>>();
	ArrayList<Tree<String>> finalTestTrees = new ArrayList<Tree<String>>();

	/**
	 * Load the WSJ, Brown, and Chinese corpora from the given locations. If
	 * either is null, don't load it. If both are null, use the dummy sentence.
	 * Then, throw away all but *fraction* of the data. To train on only the
	 * part of the Chinese Treebank that Levy and Manning do, use
	 * fraction=0.22225.
	 * 
	 * @param fraction
	 *            The fraction of training data to use. In the range [0,1].
	 */
	public Corpus(String path, TreeBankType treebank, double fraction,
			boolean onlyTest) {
		this(path, treebank, fraction, onlyTest, -1, false, false);
	}

	public Corpus(String path, TreeBankType treebank, double fraction,
			boolean onlyTest, int skipSection, boolean skipBilingual,
			boolean keepFunctionLabels) {
		this(path, treebank, onlyTest, skipSection, skipBilingual,
				keepFunctionLabels);
		int beforeSize = trainTrees.size();
		if (fraction < 0) {
			int startIndex = (int) Math.ceil(beforeSize * -1.0 * fraction);
			trainTrees = new ArrayList<Tree<String>>(trainTrees.subList(
					startIndex, trainTrees.size()));
		} else if (fraction < 1) {
			int endIndex = (int) Math.ceil(beforeSize * fraction);
			trainTrees = new ArrayList<Tree<String>>(trainTrees.subList(0,
					endIndex));
		}
		int nTrainingWords = 0;
		for (Tree<String> tree : trainTrees) {
			nTrainingWords += tree.getYield().size();
		}
		System.out.println("In training set we have # of words: "
				+ nTrainingWords);
		int afterSize = trainTrees.size();
		System.out.println("reducing number of training trees from "
				+ beforeSize + " to " + afterSize);
	}

	/**
	 * Load the WSJ, Brown, and Chinese corpora from the given locations. If any
	 * is null, don't load it. If all are null, use the dummy sentence. Don't
	 * load the English corpora if we load the Chinese one.
	 */
	private Corpus(String path, TreeBankType treebank, boolean onlyTest,
			int skipSection, boolean skipBilingual, boolean keepFunctionLabel) {
		myTreebank = treebank;
		boolean dummy = path == null;
		keepFunctionLabels = keepFunctionLabel;
		if (dummy) {
			System.out
					.println("Loading one dummy sentence into training set only.");
			Trees.PennTreeReader reader;
			Tree<String> tree;
			int exampleNumber = 8;
			List<String> sentences = new ArrayList<String>();
			switch (exampleNumber) {
			case 0:
				// Joshua Goodman's example
				sentences.add("((S (A x) (C x)))");
				// sentences.add("((S (A x) (C x)))");
				// sentences.add("((S (A y) (C x)))");
				sentences.add("((S (E x) (B x)))");
				// sentences.add("((S (F x) (B x)))");
				// sentences.add("((S (F x) (B x)))");
				// sentences.add("((T (E x) (C x)))");
				// sentences.add("((T (E x) (C x)))");
				break;
			case 1:
				// A single sentence
				// sentences.add("((S (UN1 (UN2 (NP (DT the) (JJ quick) (JJ brown) (NN fox)))) (VP (VBD jumped) (PP (IN over) (NP (DT the) (JJ lazy) (NN dog)))) (. .)))");
				// sentences.add("((S (NP (DT Some) (NNS traders)) (VP (VBD said) (SBAR (IN that) (S (NP (NP (DT the) (ADJP (RB closely) (VBN watched)) (NNP Majo) (NNP Market) (NNP Index)) (, ,) (SBAR (WHNP (WP$ whose) (NP (CD 20) (NNS stocks))) (S (VP (VBP mimic) (NP (DT the) (NNP Dow) (NNS industrials))))) (, ,)) (VP (VBD did) (RB n't) (VP (VB lead) (NP (NP (NN yesterday) (POS 's)) (JJ big) (NN rally))))))) (. .)))");
				// sentences.add("((S (NP (NP (JJ Influential) (NNS members)) (PP (IN of) (NP (DT the) (NNP House) (NNP Ways) (CC and) (NNP Means) (NNP Committee)))) (VP (VBD introduced) (NP (NP (NN legislation)) (SBAR (WHNP (WDT that)) (S (VP (MD would) (VP (VB restrict) (SBAR (WHADVP (WRB how)) (S (NP (DT the) (JJ new) (NN savings-and-loan) (NN bailout) (NN agency)) (VP (MD can) (VP (VB raise) (NP (NN capital)))))) (, ,) (S (VP (VBG creating) (NP (NP (DT another) (JJ potential) (NN obstacle)) (PP (TO to) (NP (NP (NP (DT the) (NN government) (POS 's)) (NN sale)) (PP (IN of)(NP (JJ sick) (NNS thrifts)))))))))))))) (. .)))");
				sentences
						.add("((S (NP (NP (DT The) (JJ complicated) (NN language)) (PP (IN in) (NP (DT the) (JJ huge) (JJ new) (NN law)))) (VP (VBZ has) (VP (VBD muddied) (NP (DT the) (NN fight)))) (. .)))");
				// sentences.add("((S (NP (NP (DT No) (NN fiddling)) (PP (IN with) (NP (NNS systems) (CC and) (NNS procedures)))) (VP (MD will) (ADVP (RB ever)) (VP (VB prevent) (NP (NNS markets)) (PP (IN from) (S (VP (VBG suffering) (NP (NP (DT a) (NN panic) (NN wave)) (PP (IN of) (NP (NN selling))))))))) (. .)))");
				break;
			case 2:
				// On this example, Max-rule should return
				// (ROOT (S (Z4 (Z5 x) (Z6 x)) (U (A (C x) (D x)) (B (G x) (H
				// x)))))
				// While Viterbi should return
				// (ROOT (S (Z4 (Z5 x) (Z6 x)) (U (B (G x) (H x)) (B (G x) (H
				// x)))))
				sentences
						.add("((S (Z1 (Z2 x) (NNPS x)) (U3 (Uu (A1 (NNP x1) (NNPS x2))))))");// (B
																								// (G
																								// x)
																								// (H
																								// x))))");
				sentences
						.add("((S (K (U2 (Z1 (Z2 x) (NNP x)))) (U7 (NNS x))))");//
				sentences.add("((S (Z1 (NNPS x) (NN x)) (F (CC y) (ZZ z))))");//
				// sentences.add("((S (Z4 (Z5 x) (Z6 x)) (U (B (G x) (H x)) (B (G x) (H x)))))");
				// sentences.add("((S (Z7 (Z8 x) (Z9 x)) (U (B (G x) (H x)) (B (G x) (H x)))))");
				// sentences.add("((S (V (Z10 (Z11 x) (Z12 x)) (A (C x) (D x))) (Z13 (Z14 x) (Z15 x))))");
				// sentences.add("((S (V (Z16 (Z17 x) (Z18 x)) (A (C x) (D x))) (Z19 (Z20 x) (Z21 x))))");
				break;
			case 3:
				// On this example, Max-rule should return
				// (ROOT (S (A x) (B x))) until the threshold is too large
				sentences.add("((X (C (B b) (B b)) (F (E (D d)))))");
				sentences.add("((Y (C (B a) (B a)) (E (D d))))");
				sentences.add("((X (C (B b) (B b)) (E (D d))))");
				// sentences.add("((T (X t) (X t)))");
				// sentences.add("((S (X s) (X s)))");
				// sentences.add("((S (A x) (B x)))");
				break;
			case 4:
				// sentences.add("((S (NP (DT The) (NN house)) (VP (VBZ is) (ADJP (JJ green))) (. .)))");
				sentences
						.add("( (S (SBAR (IN In) (NN order) (S (VP (TO to) (VP (VB strengthen) (NP (NP (JJ cultural) (NN exchange) (CC and) (NN contact)) (PP (IN between) (NP (NP (NP (DT the) (NNS descendents)) (PP (IN of) (NP (DT the) (NNPS Emperors)))) (UCP (PP (IN at) (NP (NN home))) (CC and) (ADVP (RB abroad)))))))))) (, ,) (NP (NNP China)) (VP (MD will) (VP (VB hold) (NP (DT the) (JJ \") (NNP China) (NNP Art) (NNP Festival) (NN \")) (PP (IN in) (NP (NP (NNP Beijing)) (CC and) (NNP Shenzhen))) (ADVP (RB simultaneously)) (PP (IN from) (NP (DT the) (NN 8th))) (PP (TO to) (NP (NP (DT the) (JJ 18th)) (PP (IN of) (NP (NNP December))))) (NP (DT this) (NN year)))) (. .)) )");
				sentences
						.add("( (S (PP (IN In) (NP (NP (NN order) (S (VP (TO to) (VP (VB strengthen) (NP (NP (JJ cultural) (NN exchange) (CC and) (NN contact)) (PP (IN between) (NP (NP (DT the) (NNS descendents)) (PP (IN of) (NP (DT the) (NNPS Emperors))) (PP (IN at) (NP (NN home)))))))))) (CC and) (ADVP (RB abroad)))) (, ,) (NP (NNP China)) (VP (MD will) (VP (VB hold) (NP (DT the) (JJ \") (NNP China) (NNP Art) (NNP Festival) (NN \")) (PP (IN in) (NP (NP (NNP Beijing)) (CC and) (NNP Shenzhen))) (ADVP (RB simultaneously)) (PP (IN from) (NP (DT the) (NN 8th))) (PP (TO to) (NP (NP (DT the) (JJ 18th)) (PP (IN of) (NP (NNP December))))) (NP (DT this) (NN year)))) (. .)) )");
				sentences
						.add("( (S (PP (IN In) (NP (NN order) (S (VP (TO to) (VP (VB strengthen) (NP (NP (JJ cultural) (NN exchange) (CC and) (NN contact)) (PP (IN between) (NP (NP (DT the) (NNS descendents)) (PP (IN of) (NP (DT the) (NNPS Emperors)))))) (UCP (PP (IN at) (ADVP (RB home))) (CC and) (ADVP (RB abroad)))))))) (, ,) (NP (NNP China)) (VP (MD will) (VP (VB hold) (NP (DT the) (`` \") (NNP China) (NNP Art) (NNP Festival) (NN \")) (PP (IN in) (NP (NNP Beijing) (CC and) (NNP Shenzhen))) (ADVP (RB simultaneously)) (PP (PP (IN from) (NP (DT the) (NN 8th))) (PP (IN to) (NP (DT the) (NN 18th))) (PP (IN of) (NP (NNP December)))) (NP (DT this) (NN year)))) (. .)) )");

				break;
			case 5:
				sentences.add("((X (C (B a) (B a)) (D d)))");
				sentences.add("((Y (C (B b) (B b)) (E (D d) (D d))))");
				sentences.add("((Y (C (B b) (B b)) (D d)))");
				sentences.add("((Y (E (B b) (B b)) (D d)))");
				sentences.add("((Y (C (B b) (B b)) (D d)))");
				sentences.add("((Y (C (B b) (B b)) (D d)))");
				sentences.add("((Y (C (B b) (B b)) (D d)))");
				sentences.add("((Y (C (B b) (B b)) (D d)))");
				break;
			case 6:
				sentences.add("((Y (C (B @) (B b)) (E (D d) (D e))))");
				sentences.add("((Y (C (B b) (D b)) (D d)))");
				// sentences.add("((Y (C (K (B b)) (D b)) (Z (D d))))");
				// sentences.add("((Y (C (K (N n)) (B b)) (Z (D d))))");
				// sentences.add("((X (V (C (B a) (B a))) (D d)))");
				// sentences.add("((X (C (B a) (B a)) (D d)))");

				sentences.add("((Y (C (B b) (B b)) (D d)))");
				sentences.add("((Y (U (C (B b) (B b))) (D d)))");
				// sentences.add("((Y (E (B b) (B b)) (D d)))");
				sentences.add("((Y (C (B b) (B b)) (D d)))");
				sentences.add("((Y (C (B b) (B b)) (D d)))");
				sentences.add("((Y (C (B b) (B b)) (D d)))");
				sentences.add("((Y (C (B b) (B b)) (D d)))");
				// sentences.add("((Y (C (B b) (B b)) (Z (D d))))");
				// sentences.add("((Y (C (K (B b)) (B b)) (Z (D d))))");

				break;
			case 7:
				sentences.add("((X (S (NP (X (PRP I))) (VP like))))");
				sentences.add("((X (C (U (V (W (B a) (B a))))) (D d)))");
				sentences.add("((X (Y (Z (V (C (B a) (B a))) (D d)))))");
				sentences.add("((X (C (B a) (B a)) (D d)))");
				sentences.add("((Y (C (B b) (B b)) (E (D d) (D d))))");
				sentences.add("((Y (C (B b) (B b)) (D d)))");
				sentences.add("((Y (U (C (B b) (B b))) (D d)))");
				sentences.add("((Y (E (B b) (B b)) (D d)))");
				sentences.add("((Y (C (B b) (B b)) (D d)))");
				sentences.add("((Y (C (B b) (B b)) (D d)))");
				sentences.add("((Y (C (B b) (B b)) (D d)))");
				sentences.add("((Y (C (B b) (B b)) (D d)))");
			case 8:
				sentences
						.add("((S-SBJ (NP (PRP We)) (VP (VBP 're) (RB about) (VP (TO to) (VP (VB see) (SBAR (IN if) (S (NP (NN advertising)) (VP (VBZ works))))))) (. .)))");
				break;
			default:

			}
			for (String sentence : sentences) {
				reader = new Trees.PennTreeReader(new StringReader(sentence));
				tree = reader.next();
				Trees.TreeTransformer<String> treeTransformer = (keepFunctionLabels) ? new Trees.FunctionLabelRetainingTreeNormalizer()
						: new Trees.StandardTreeNormalizer();
				Tree<String> normalizedTree = treeTransformer
						.transformTree(tree);
				tree = normalizedTree;
				trainTrees.add(tree);
				devTestTrees.add(tree);
				validationTrees.add(tree);
			}
		}
		// load from at least one corpus
		else {
			try {
				// load from chinese if possible
				if (myTreebank == TreeBankType.CHINESE) {
					System.out.println("Loading CHINESE data!");
					loadChinese(path, skipBilingual);
				}
				// load from WSJ & Brown only if no Chinese
				else if (myTreebank == TreeBankType.WSJ) {
					System.out.println("Loading ENGLISH WSJ data!");
					loadWSJ(path, onlyTest, skipSection);
					/*
					 * if (pathBrown!=null && !pathBrown.equals("null")) {
					 * loadBrown(pathBrown); }
					 */
				} else if (myTreebank == TreeBankType.GERMAN) {
					System.out.println("Loading GERMAN data!");
					loadGerman(path);
				} else if (myTreebank == TreeBankType.BROWN) {
					System.out.println("Loading BROWN data!");
					loadBrown(path);
				} else if (myTreebank == TreeBankType.SPANISH) {
					System.out.println("Loading SPANISH data!");
					loadSpanish(path);
				} else if (myTreebank == TreeBankType.FRENCH) {
					System.out.println("Loading FRENCH data!");
					loadCONLL(path, true);
				} else if (myTreebank == TreeBankType.CONLL) {
					System.out.println("Loading CoNLL converted data!");
					loadCONLL(path, false);
				} else if (myTreebank == TreeBankType.SINGLEFILE) {
					System.out.println("Loading data from single file!");
					loadSingleFile(path);
				}
			} catch (Exception e) {
				System.out.println("Error loading trees!");
				System.out.println(e.getStackTrace().toString());
				throw new Error(e.getMessage(), e);
			}
		}
	}

	/**
	 * @param pathChinese
	 * @param skipBilingual
	 * @throws Exception
	 */
	private void loadChinese(String pathChinese, boolean skipBilingual)
			throws Exception {
		System.out.print("Loading Chinese treebank trees...");
		if (!skipBilingual) {
			trainTrees.addAll(readTrees(pathChinese, 1, 25,
					Charset.forName("GB18030")));
			trainTrees.addAll(readTrees(pathChinese, 26, 270,
					Charset.forName("GB18030")));
		}
		trainTrees.addAll(readTrees(pathChinese, 400, 1151,
				Charset.forName("GB18030")));

		devTestTrees.addAll(readTrees(pathChinese, 301, 325,
				Charset.forName("GB18030")));
		validationTrees.addAll(readTrees(pathChinese, 301, 325,
				Charset.forName("GB18030")));
		finalTestTrees.addAll(readTrees(pathChinese, 271, 300,
				Charset.forName("GB18030")));
		System.out.print(""
				+ (trainTrees.size() + " " + validationTrees.size() + " "
						+ devTestTrees.size() + " " + finalTestTrees.size())
				+ " trees...");
		System.out.println("done");
	}

	/**
	 * @param pathBrown
	 * @throws Exception
	 */
	private void loadBrown(String pathBrown) throws Exception {
		String[] sections = { "cf", "cg", "ck", "cl", "cm", "cn", "cp", "cr" };
		int[] sectionTrainCounts = new int[sections.length];
		// read in each section at a time
		for (int i = 0; i < sections.length; i++) {
			List<Tree<String>> sectionTrainTrees = new ArrayList<Tree<String>>();
			List<Tree<String>> sectionValidationTrees = new ArrayList<Tree<String>>();
			List<Tree<String>> sectionDevTestTrees = new ArrayList<Tree<String>>();
			List<Tree<String>> sectionFinalTestTrees = new ArrayList<Tree<String>>();

			String sectionPath = pathBrown + "/" + sections[i];
			List<Tree<String>> sectionTrees = readTrees(sectionPath, 0, 1000,
					Charset.defaultCharset());
			splitTrainValidTest(sectionTrees, sectionTrainTrees,
					sectionValidationTrees, sectionDevTestTrees,
					sectionFinalTestTrees);
			trainTrees.addAll(sectionTrainTrees);
			validationTrees.addAll(sectionValidationTrees);
			devTestTrees.addAll(sectionDevTestTrees);
			finalTestTrees.addAll(sectionFinalTestTrees);
			sectionTrainCounts[i] = sectionTrainTrees.size();
			System.out.println("I read " + sectionTrainCounts[i]
					+ " training trees from section " + sections[i]);
		}
	}

	private void loadSpanish(String path) throws Exception {
		System.out.print("Loading Spanish trees...");
		trainTrees.addAll(readTrees(path, 1, 1, Charset.defaultCharset())); // was
																			// 200,
																			// 2099
		validationTrees
				.addAll(readTrees(path, 2, 279, Charset.defaultCharset())); // was
																			// 2100,
																			// 2199
		devTestTrees.addAll(readTrees(path, 2, 279, Charset.defaultCharset()));
		finalTestTrees
				.addAll(readTrees(path, 2, 279, Charset.defaultCharset()));
		System.out.println("done");
	}

	private void loadSingleFile(String path) throws Exception {
		System.out.print("Loading trees from single file...");
		InputStreamReader inputData = new InputStreamReader(
				new FileInputStream(path), "UTF-8");
		PennTreeReader treeReader = new PennTreeReader(inputData);

		while (treeReader.hasNext()) {
			trainTrees.add(treeReader.next());
		}

		Trees.TreeTransformer<String> treeTransformer = (keepFunctionLabels) ? new Trees.FunctionLabelRetainingTreeNormalizer()
				: new Trees.StandardTreeNormalizer();
		ArrayList<Tree<String>> normalizedTreeList = new ArrayList<Tree<String>>();
		for (Tree<String> tree : trainTrees) {
			Tree<String> normalizedTree = treeTransformer.transformTree(tree);
			normalizedTreeList.add(normalizedTree);
		}
		if (normalizedTreeList.size() == 0) {
			throw new Exception("failed to load any trees at " + path);
		}
		trainTrees = normalizedTreeList;

		devTestTrees = trainTrees;
		System.out.println("done");

		// trainTrees.addAll(readTrees(path,-1,
		// Integer.MAX_VALUE,Charset.defaultCharset()));
	}

	private void loadCONLL(String path, boolean useLatinEncoding)
			throws Exception {
		Charset charSet = (useLatinEncoding) ? Charset.forName("ISO8859_1")
				: Charset.forName("UTF-8");

		System.out.print("Loading CoNLL trees...");
		trainTrees = readAndPreprocessTrees(path, 1, 1, charSet);
		validationTrees = readAndPreprocessTrees(path, 2, 2, charSet);
		devTestTrees = readAndPreprocessTrees(path, 2, 2, charSet);
		finalTestTrees = readAndPreprocessTrees(path, 3, 3, charSet);
		for (Tree t : trainTrees) {
			if (t.getChildren().size() != 1)
				System.out.println("Malformed v: " + t);
		}
		for (Tree t : devTestTrees) {
			if (t.getChildren().size() != 1)
				System.out.println("Malformed v: " + t);
		}
		for (Tree t : finalTestTrees) {
			if (t.getChildren().size() != 1)
				System.out.println("Malformed t: " + t);
		}
		System.out.println("done");
	}

	/**
	 * @param path
	 * @param i
	 * @param j
	 * @param charSet
	 * @return
	 * @throws Exception
	 */
	private ArrayList<Tree<String>> readAndPreprocessTrees(String path, int i,
			int j, Charset charSet) throws Exception {
		List<Tree<String>> tmp = new ArrayList<Tree<String>>();
		ArrayList<Tree<String>> tmp2 = new ArrayList<Tree<String>>();
		tmp.addAll(readTrees(path, i, j, charSet));
		for (Tree t : tmp) {
			if (!t.getLabel().equals("ROOT")) {
				List<Tree<String>> childrenList = new ArrayList<Tree<String>>(1);
				childrenList.add(t);
				Tree<String> rootedTree = new Tree<String>("ROOT", childrenList);
				t = rootedTree;
			}
			tmp2.add(t);
		}
		return tmp2;
	}

	/**
	 * @param pathWSJ
	 * @throws Exception
	 */
	private void loadWSJ(String pathWSJ, boolean onlyTest, int skipSection)
			throws Exception {
		System.out.print("Loading WSJ trees...");
		if (!onlyTest) {
			if (skipSection == -1)
				trainTrees.addAll(readTrees(pathWSJ, 200, 2199,
						Charset.defaultCharset())); // was 200, 2199
			else {
				System.out.println("Skipping section " + skipSection + ".");
				if (skipSection == 2) {
					trainTrees.addAll(readTrees(pathWSJ, 300, 2199,
							Charset.defaultCharset())); // was 200, 2199
				} else if (skipSection == 21) {
					trainTrees.addAll(readTrees(pathWSJ, 200, 2099,
							Charset.defaultCharset())); // was 200, 2199
				} else {
					int middle = skipSection * 100;
					trainTrees.addAll(readTrees(pathWSJ, 200, middle - 1,
							Charset.defaultCharset())); // was 200, 2199
					trainTrees.addAll(readTrees(pathWSJ, middle + 100, 2199,
							Charset.defaultCharset())); // was 200, 2199
				}

			}
			validationTrees.addAll(readTrees(pathWSJ, 2100, 2199,
					Charset.defaultCharset())); // was 2100, 2199
		}

		devTestTrees.addAll(readTrees(pathWSJ, 2200, 2299,
				Charset.defaultCharset()));
		finalTestTrees.addAll(readTrees(pathWSJ, 2300, 2399,
				Charset.defaultCharset()));
		System.out.println("done");
	}

	private void loadGerman(String path) throws Exception {
		System.out.print("Loading German trees...");
		List<Tree<String>> tmp = readTrees(path, 1, 3, Charset.forName("UTF-8"));
		int i = 0;
		for (Tree<String> tree : tmp) {
			List<Tree<String>> childrenList = new ArrayList<Tree<String>>(1);
			tree.setLabel("PSEUDO");
			childrenList.add(tree);
			Tree<String> rootedTree = new Tree<String>("ROOT", childrenList);
			tree = rootedTree;
			if (i < 18602) {
				trainTrees.add(tree);
			} else if (i > 19601) {
				finalTestTrees.add(tree);
			} else {
				validationTrees.add(tree);
				devTestTrees.add(tree);
			}
			i++;
		}
		System.out
				.println("done.\nThere are " + trainTrees.size() + " "
						+ devTestTrees.size() + " " + finalTestTrees.size()
						+ " trees.");
	}

	public static List<Tree<String>> readTrees(String basePath, int low,
			int high, Charset charset) throws Exception {
		Collection<Tree<String>> trees = PennTreebankReader.readTrees(basePath,
				low, high, charset);
		// System.out.println("in readTrees");
		// normalize trees
		Trees.TreeTransformer<String> treeTransformer = (keepFunctionLabels) ? new Trees.FunctionLabelRetainingTreeNormalizer()
				: new Trees.StandardTreeNormalizer();
		List<Tree<String>> normalizedTreeList = new ArrayList<Tree<String>>();
		for (Tree<String> tree : trees) {
			Tree<String> normalizedTree = treeTransformer.transformTree(tree);
			normalizedTreeList.add(normalizedTree);
		}
		if (normalizedTreeList.size() == 0) {
			throw new Exception("failed to load any trees at " + basePath
					+ " from " + low + " to " + high);
		}
		return normalizedTreeList;
	}

	/**
	 * Split a set of trees into 7/10 training, 1/10 validation, 1/10 dev test,
	 * 1/10 final test sets. Every set of 10 sentences is split exactly into
	 * these sets deterministically.
	 * 
	 * @param sectionTrees
	 * @param trainTrees
	 * @param validationTrees
	 * @param sectionTestTrees
	 */
	public static void splitTrainValidTest(List<Tree<String>> sectionTrees,
			List<Tree<String>> trainTrees, List<Tree<String>> validationTrees,
			List<Tree<String>> devTestTrees, List<Tree<String>> finalTestTrees) {
		final int CYCLE_SIZE = 10;
		for (int i = 0; i < sectionTrees.size(); i++) {
			if (i % CYCLE_SIZE < 7) {
				trainTrees.add(sectionTrees.get(i));
			} else if (i % CYCLE_SIZE == 7) {
				validationTrees.add(sectionTrees.get(i));
			} else if (i % CYCLE_SIZE == 8) {
				devTestTrees.add(sectionTrees.get(i));
			} else if (i % CYCLE_SIZE == 9) {
				finalTestTrees.add(sectionTrees.get(i));
			}
		}
	}

	public static List<Tree<String>> filterTreesForConditional(
			List<Tree<String>> trees, boolean filterAllUnaries,
			boolean filterStupidFrickinWHNP, boolean justCollapseUnaryChains) {
		List<Tree<String>> filteredTrees = new ArrayList<Tree<String>>(
				trees.size());
		OUTER: for (Tree<String> tree : trees) {
			if (tree.getYield().size() == 1)
				continue;
			if (tree.hasUnaryChain()) {
				if (justCollapseUnaryChains) {
					// System.out.println(tree);
					tree.removeUnaryChains();
					// System.out.println(tree);
				} else
					continue;
			}
			if (filterStupidFrickinWHNP) {
				for (Tree<String> n : tree.getNonTerminals()) {
					// if (n.getLabel().equals("@WHNP^g") ||
					// (n.getLabel().equals("WHNP") && n.getChildren().size() >
					// 1))
					if (n.getLabel().contains("WHNP"))
						continue OUTER;
				}
			}
			if (filterAllUnaries && tree.hasUnariesOtherThanRoot())
				continue;
			filteredTrees.add(tree);
		}
		return filteredTrees;
	}

	public static List<Tree<String>> binarizeAndFilterTrees(
			List<Tree<String>> trees, int verticalAnnotations,
			int horizontalAnnotations, int sentenceMaxLength,
			Binarization binarization, boolean manualAnnotation, boolean VERBOSE) {
		return binarizeAndFilterTrees(trees, verticalAnnotations,
				horizontalAnnotations, sentenceMaxLength, binarization,
				manualAnnotation, VERBOSE, false);
	}

	public static List<Tree<String>> binarizeAndFilterTrees(
			List<Tree<String>> trees, int verticalAnnotations,
			int horizontalAnnotations, int sentenceMaxLength,
			Binarization binarization, boolean manualAnnotation,
			boolean VERBOSE, boolean markUnaryParents) {
		List<Tree<String>> binarizedTrees = new ArrayList<Tree<String>>();
		System.out.print("Binarizing and annotating trees...");

		if (VERBOSE)
			System.out.println("annotation levels: vertical="
					+ verticalAnnotations + " horizontal="
					+ horizontalAnnotations);

		int i = 0;
		for (Tree<String> tree : trees) {
			List<String> testSentence = tree.getYield();
			i++;
			if (testSentence.size() > sentenceMaxLength)
				continue;
			// if (noUnaries && tree.hasUnaryChain()) continue;
			if (true) {
				binarizedTrees
						.add(TreeAnnotations.processTree(tree,
								verticalAnnotations, horizontalAnnotations,
								binarization, manualAnnotation,
								markUnaryParents, true));
			} else {
				binarizedTrees.add(TreeAnnotations.binarizeTree(tree,
						binarization));
			}
		}
		System.out.print("done.\n");
		return binarizedTrees;
	}

	/**
	 * Get the training trees.
	 * 
	 * @return
	 */
	public List<Tree<String>> getTrainTrees() {
		return trainTrees;
	}

	/**
	 * Get the validation trees.
	 * 
	 * @return
	 */
	public List<Tree<String>> getValidationTrees() {
		return validationTrees;
	}

	/**
	 * Get the trees we test on during development.
	 * 
	 * @return
	 */
	public List<Tree<String>> getDevTestingTrees() {
		return devTestTrees;
	}

	/**
	 * Get the trees we test on for our final results.
	 * 
	 * @return
	 */
	public List<Tree<String>> getFinalTestingTrees() {
		return finalTestTrees;
	}

	public static List<Tree<String>> makePosTrees(List<Tree<String>> trees) {
		System.out.print("Making POS-trees...");
		List<Tree<String>> posTrees = new ArrayList<Tree<String>>();
		for (Tree<String> tree : trees) {
			posTrees.add(makePosTree(tree));
		}
		System.out.print(" done.\n");
		return posTrees;
	}

	public static Tree<String> makePosTree(Tree<String> tree) {
		List<Tree<String>> terminals = tree.getTerminals();
		List<String> preTerminals = tree.getPreTerminalYield();

		int n = preTerminals.size();
		String label = "STOP"; // preTerminals.get(n-1);

		List<Tree<String>> tmpChildList = new ArrayList<Tree<String>>();
		tmpChildList.add(new Tree<String>(label));// terminals.get(n-1));
		Tree<String> tmpTree = new Tree<String>(label, tmpChildList);

		// tmpChildList = new ArrayList<Tree<String>>();
		// tmpChildList.add(tmpTree);
		Tree<String> posTree = tmpTree; // new Tree<String>(label,tmpChildList);

		for (int i = n - 1; i >= 0; i--) {
			label = preTerminals.get(i);
			tmpChildList = new ArrayList<Tree<String>>();
			tmpChildList.add(terminals.get(i));
			tmpTree = new Tree<String>(label, tmpChildList);

			tmpChildList = new ArrayList<Tree<String>>();
			tmpChildList.add(tmpTree);
			tmpChildList.add(posTree);
			posTree = new Tree<String>(label, tmpChildList);// "X"
		}
		tmpChildList = new ArrayList<Tree<String>>();
		tmpChildList.add(posTree);
		posTree = new Tree<String>(tree.getLabel(), tmpChildList);
		return posTree;
	}

	public static void replaceRareWords(StateSetTreeList trainTrees,
			SimpleLexicon lexicon, int threshold) {
		Counter<String> wordCounts = new Counter<String>();
		for (Tree<StateSet> tree : trainTrees) {
			List<StateSet> words = tree.getYield();
			for (StateSet word : words) {
				String wordString = word.getWord();
				wordCounts.incrementCount(wordString, 1.0);
				lexicon.wordIndexer.add(wordString);
			}
		}
		// replace the rare words and also add the others to the appropriate
		// numberers
		for (Tree<StateSet> tree : trainTrees) {
			List<StateSet> words = tree.getYield();
			int ind = 0;
			for (StateSet word : words) {
				String sig = word.getWord();
				if (wordCounts.getCount(sig) <= threshold) {
					sig = lexicon.getSignature(word.getWord(), ind);
					word.setWord(sig);
				}
				ind++;
			}
		}
	}

	public static void replaceRareWords(List<Tree<String>> trainTrees,
			SimpleLexicon lexicon, int threshold) {
		Counter<String> wordCounts = new Counter<String>();
		for (Tree<String> tree : trainTrees) {
			List<String> words = tree.getYield();
			for (String word : words) {
				wordCounts.incrementCount(word, 1.0);
				lexicon.wordIndexer.add(word);
			}
		}
		// replace the rare words and also add the others to the appropriate
		// numberers
		for (Tree<String> tree : trainTrees) {
			List<Tree<String>> words = tree.getTerminals();
			int ind = 0;
			for (Tree<String> word : words) {
				String sig = word.getLabel();
				if (wordCounts.getCount(sig) <= threshold) {
					sig = lexicon.getSignature(word.getLabel(), ind);
					word.setLabel(sig);
				}
				ind++;
			}
		}
	}

	public static void lowercaseWords(List<Tree<String>> trainTrees) {
		for (Tree<String> tree : trainTrees) {
			List<Tree<String>> words = tree.getTerminals();
			for (Tree<String> word : words) {
				String lWord = word.getLabel().toLowerCase();
				word.setLabel(lWord);
			}
		}
	}

}
