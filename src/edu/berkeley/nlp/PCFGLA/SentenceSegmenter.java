package edu.berkeley.nlp.PCFGLA;

import edu.berkeley.nlp.PCFGLA.smoothing.SmoothAcrossParentBits;
import edu.berkeley.nlp.PCFGLA.smoothing.SmoothAcrossParentSubstate;
import edu.berkeley.nlp.PCFGLA.smoothing.Smoother;
import edu.berkeley.nlp.io.PTBLineLexer;
import edu.berkeley.nlp.io.PTBTokenizer;
import edu.berkeley.nlp.io.PTBLexer;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees;
import edu.berkeley.nlp.ui.TreeJPanel;
import edu.berkeley.nlp.util.CommandLineUtils;
import edu.berkeley.nlp.util.Numberer;
import edu.berkeley.nlp.util.Pair;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

/**
 * 
 * @author Slav Petrov
 */
public class SentenceSegmenter {
	static TreeJPanel tjp;
	static JFrame frame;

	public static class Options {

		@Option(name = "-gr", required = true, usage = "Grammarfile (Required)\n")
		public String grFileName;

		@Option(name = "-tokenize", usage = "Tokenize input first. (Default: false=text is already tokenized)")
		public boolean tokenize;

		@Option(name = "-accurate", usage = "Set thresholds for accuracy. (Default: set thresholds for efficiency)")
		public boolean accurate;

		@Option(name = "-constituent", usage = "Instead of sentence probabilities return constituent probabilities")
		public boolean constituent = false;

		@Option(name = "-inputFile", usage = "Read input from this file instead of reading it from STDIN.")
		public String inputFile;

		@Option(name = "-outputFile", usage = "Store output in this file instead of printing it to STDOUT.")
		public String outputFile;
	}

	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
		OptionParser optParser = new OptionParser(Options.class);
		Options opts = (Options) optParser.parse(args, true);

		double threshold = 1.0;

		String inFileName = opts.grFileName;
		ParserData pData = ParserData.Load(inFileName);
		if (pData == null) {
			System.out.println("Failed to load grammar from file" + inFileName
					+ ".");
			System.exit(1);
		}
		Grammar grammar = pData.getGrammar();
		Lexicon lexicon = pData.getLexicon();
		Numberer.setNumberers(pData.getNumbs());

		CoarseToFineMaxRuleParser parser = null;
		parser = new CoarseToFineMaxRuleParser(grammar, lexicon, threshold, -1,
				false, false, false, opts.accurate, false, true, true);
		parser.binarization = pData.getBinarization();

		try {
			BufferedReader inputData = (opts.inputFile == null) ? new BufferedReader(
					new InputStreamReader(System.in)) : new BufferedReader(
					new InputStreamReader(new FileInputStream(opts.inputFile),
							"UTF-8"));
			PrintWriter outputData = (opts.outputFile == null) ? new PrintWriter(
					new OutputStreamWriter(System.out)) : new PrintWriter(
					new OutputStreamWriter(
							new FileOutputStream(opts.outputFile), "UTF-8"),
					true);
			PTBLineLexer tokenizer = null;
			if (opts.tokenize)
				tokenizer = new PTBLineLexer();

			String line = "";
			while ((line = inputData.readLine()) != null) {
				List<String> sentence = null;
				List<String> posTags = null;

				String[] parts = line.split("\t");
				if (parts.length < 3)
					continue;
				int nPoints = Integer.parseInt(parts[0]);
				List<Pair<Integer, Integer>> points = new ArrayList<Pair<Integer, Integer>>(
						nPoints);

				String[] segments = parts[1].split("\\(");
				for (int i = 1; i <= nPoints; i++) {
					String[] numbers = segments[i].split(" ");
					String n0 = numbers[0];
					String n1 = numbers[1]
							.substring(0, numbers[1].length() - 1);
					Pair<Integer, Integer> number = new Pair<Integer, Integer>(
							Integer.parseInt(n0), Integer.parseInt(n1));
					points.add(number);
				}

				if (!opts.tokenize)
					sentence = Arrays
							.asList(parts[parts.length - 1].split(" "));
				else
					sentence = tokenizer.tokenizeLine(parts[parts.length - 1]);

				// if (sentence.size()==0) { outputData.write("\n"); continue;
				// }//break;
				if (sentence.size() >= 200) {
					sentence = new ArrayList<String>();
					System.err.println("Skipping sentence with "
							+ sentence.size() + " words since it is too long.");
					continue;
				}// break;

				Tree<String> parsedTree = parser.getBestConstrainedParse(
						sentence, posTags, null);
				double allLL = (parsedTree.getChildren().isEmpty()) ? Double.NEGATIVE_INFINITY
						: parser.getLogLikelihood();
				outputData.write(allLL + " ");
				for (Pair<Integer, Integer> point : points) {
					double partLL = parser.getSentenceProbability(
							point.getFirst(), point.getSecond(),
							opts.constituent);
					outputData.write(partLL + " ");
				}
				outputData.write("\n");
			}
			outputData.flush();
			outputData.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		System.exit(0);
	}

	// /**
	// * @param parsedTree
	// * @param outputData
	// * @param opts
	// */
	// private static void outputTrees(List<Tree<String>> parseTrees,
	// PrintWriter outputData,
	// CoarseToFineMaxRuleParser parser, Options opts) {
	// for (Tree<String> parsedTree : parseTrees){
	// double allLL = (parsedTree.getChildren().isEmpty()) ?
	// Double.NEGATIVE_INFINITY : parser.getLogLikelihood();
	// outputData.write(allLL+"\n");
	// // continue;
	// }
	// if (!opts.binarize) parsedTree =
	// TreeAnnotations.unAnnotateTree(parsedTree);
	// if (opts.confidence) {
	// double treeLL = (parsedTree.getChildren().isEmpty()) ?
	// Double.NEGATIVE_INFINITY : parser.getLogLikelihood(parsedTree);
	// outputData.write(treeLL+"\t");
	// }
	// if (!parsedTree.getChildren().isEmpty()) {
	// if (true) outputData.write("( "+parsedTree.getChildren().get(0)+" )\n");
	// // else outputData.write(parsedTree.getChildren().get(0)+"\n\n");
	// } else {
	// if (true) outputData.write("(())\n");
	// // else outputData.write("()\n\n");
	// }
	// }
	// }

	public static void writeTreeToImage(Tree<String> tree, String fileName)
			throws IOException {
		tjp.setTree(tree);

		BufferedImage bi = new BufferedImage(tjp.width(), tjp.height(),
				BufferedImage.TYPE_INT_ARGB);
		int t = tjp.height();
		Graphics2D g2 = bi.createGraphics();

		g2.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR, 1.0f));
		Rectangle2D.Double rect = new Rectangle2D.Double(0, 0, tjp.width(),
				tjp.height());
		g2.fill(rect);

		g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,
				1.0f));

		tjp.paintComponent(g2); // paint the graphic to the offscreen image
		g2.dispose();

		ImageIO.write(bi, "png", new File(fileName)); // save as png format
														// DONE!
	}

}
