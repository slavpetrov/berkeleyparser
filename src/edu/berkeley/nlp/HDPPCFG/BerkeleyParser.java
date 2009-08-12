package edu.berkeley.nlp.HDPPCFG;

import edu.berkeley.nlp.io.PTBLineLexer;
import edu.berkeley.nlp.io.PTBTokenizer;
import edu.berkeley.nlp.io.PTBLexer;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees;
import edu.berkeley.nlp.ui.TreeJPanel;
import edu.berkeley.nlp.util.CommandLineUtils;
import edu.berkeley.nlp.util.Numberer;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.JFrame;


/**
 * Reads in the Penn Treebank and generates N_GRAMMARS different grammars.
 *
 * @author Slav Petrov
 */
public class BerkeleyParser  {
	static TreeJPanel tjp;
	static JFrame frame;
	
  @SuppressWarnings("unchecked")
	public static void main(String[] args) {
    if (args.length<1) {
      System.out.println(
          "usage: java -jar berkeleyParser.jar \n" +
          "              -gr       Grammarfile (Required)\n" +
          "              -tokenize Tokenize input first: true/false (Default: false=text is already tokenized)\n"+
          "              -parser   Parsertype: viterbi/max-rule (Default: max-rule)\n" +
          "              -bin      Output binarized trees: true/false (Default: false)\n" +
          "              -scores   Output inside scores (only for binarized trees): true/false (Default: false)\n" +
          "              -sub      Output subcategories (only for binarized viterbi trees): true/false (Default: false)\n" +
          "              -render   Write rendered tree to image file: true/false (Default: false)\n"+
          " reads sentences (one per line) from STDIN and writes parse trees to STDOUT."
                	);
      System.exit(2);
    }

    //  parse the input arguments
    Map<String, String> input = CommandLineUtils.simpleCommandLineParser(args);
    
    double threshold = 1.0;
    
    String inFileName = CommandLineUtils.getValueOrUseDefault(input, "-gr", null);
    if (inFileName==null) {	throw new Error("Did not provide a grammar."); }
    ParserData pData = ParserData.Load(inFileName);
    if (pData==null) {
      System.out.println("Failed to load grammar from file"+inFileName+".");
      System.exit(1);
    }
    Grammar grammar = pData.getGrammar();
    LexiconInterface lexicon = pData.getLexicon();
    Numberer.setNumberers(pData.getNumbs());
    
    boolean tokenize = CommandLineUtils.getValueOrUseDefault(input, "-tokenize", "false").equals("true");
    	
    
    
    boolean viterbi = CommandLineUtils.getValueOrUseDefault(input, "-parser", "max-rule").equals("viterbi");
    
    boolean binarized = CommandLineUtils.getValueOrUseDefault(input, "-bin", "false").equals("true");
    boolean scores = CommandLineUtils.getValueOrUseDefault(input, "-scores", "false").equals("true") && binarized;
    boolean sub = CommandLineUtils.getValueOrUseDefault(input, "-sub", "false").equals("true") && binarized && viterbi;
    
    boolean render = CommandLineUtils.getValueOrUseDefault(input, "-render", "false").equals("true");
        
    ConstrainedParser parser = null;//new CoarseToFineMaxRuleParser(grammar, lexicon, threshold,-1,viterbi,sub,scores);      
    
    if (render) tjp = new TreeJPanel();

    try{
    	BufferedReader inputData = new BufferedReader(new InputStreamReader(System.in));//FileReader(inData));
    	PTBLineLexer tokenizer = null;
    	if (tokenize) tokenizer = new PTBLineLexer();

    	List<String> sentence = null;
    	String line = "";
    	while((line=inputData.readLine()) != null){
    		if (!tokenize) sentence = Arrays.asList(line.split(" "));
    		else sentence = tokenizer.tokenizeLine(line);
    		
    		if (sentence.size()==0) break;

    		System.err.println("Warning: Adam commented out code here");
				// Tree<String> parsedTree =
				// parser.getBestConstrainedParse(sentence,null);
				// if (!binarized) parsedTree =
				// TreeAnnotations.unAnnotateTree(parsedTree);
				//    		
				// if (!parsedTree.getChildren().isEmpty()) {
				// System.out.println(parsedTree.getChildren().get(0));
				// } else System.out.println("()");
				//    		
				// if (render) writeTreeToImage(parsedTree,line.replaceAll("[^a-zA-Z]",
				// "")+".png");
    	}
    } catch (Exception ex) {
      ex.printStackTrace();
    }
    System.exit(0);
  }

  
  
  public static void writeTreeToImage(Tree<String> tree, String fileName) throws IOException{
  	tjp.setTree(tree);

    
    BufferedImage bi =new BufferedImage(tjp.width(),tjp.height(),BufferedImage.TYPE_INT_ARGB);
    int t=tjp.height();
    Graphics2D g2 = bi.createGraphics();
    
    
    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR, 1.0f));
    Rectangle2D.Double rect = new Rectangle2D.Double(0,0,tjp.width(),tjp.height()); 
    g2.fill(rect);
    
    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    
    tjp.paintComponent(g2); //paint the graphic to the offscreen image
    g2.dispose();
    
    ImageIO.write(bi,"png",new File(fileName)); //save as png format DONE!
  }

}

