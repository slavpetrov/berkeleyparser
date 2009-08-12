//package edu.berkeley.nlp.auxv;
//
//import static fig.basic.LogInfo.end_track;
//import static fig.basic.LogInfo.logs;
//import static fig.basic.LogInfo.track;
//
//import java.io.IOException;
//import java.io.PrintWriter;
//import java.util.Arrays;
//import java.util.List;
//import java.util.Random;
//
//import nuts.io.Extensions;
//import nuts.io.IO;
//import edu.berkeley.nlp.auxv.AuxVarInsideOutside.IterManager;
//import edu.berkeley.nlp.auxv.AuxVarInsideOutside.PrintMonitor;
//import fig.basic.IOUtils;
//import fig.basic.Option;
//import fig.basic.StopWatch;
//import fig.exec.Execution;
//
///**
// * @deprecated See ExperimentsPCFGLA instead
// * @author Alexandre Bouchard
// *
// */
//public class Experiments //implements Runnable
//{
////  private ExactInsideOutside eio;
////  private Grammar g;
////  private AuxVarInsideOutside aux;
////  private InformedBracketProposer prop;
////  
////  @Option(gloss="Locatio of the grammar")
////  public String grammarLocation = "/home/eecs/bouchard/workspace/lg/corpora/grammar";
////  @Option(gloss="Source of sentences to parse")
////  public String sentencesLocation = "/home/eecs/bouchard/workspace/lg/corpora/sentences";
////  @Option(gloss="Output file prefix for dumping statistics")
////  public String outputPrefix = "output";
////  
////  @Option(gloss="Higher values encourage selection large bracket constraints")
////  public double largeSpanExponent = 10.0;
////  @Option(gloss="Higher values encourage selection of high proposal posterior probability")
////  public double likelySpanExponent = 10.0;
////  @Option(gloss="Grid search for exponents of bracket proposals: increment size") 
////  public double exponentsIncrementSize = 1.0;
////  @Option(gloss="Grid search for exponents of bracket proposals: number of increments (1 to disable grid search)") 
////  public int exponentsNIncrements = 1;
////  
////  @Option(gloss="Number of bracket constraints")
////  public int nBrackets = 8;
////  @Option(gloss="Number of iterations")
////  public int nIterations = 20;
////  @Option(gloss="Length of the burnin period")
////  public int nBurnIn = 5;
////  @Option(gloss="Minimum sentence length")
////  public int minSentenceLength = 60;
////  @Option(gloss="Source of randomness")
////  public Random rand = new Random(1);
////  
////  public void init() throws IOException
////  {
////    g = Grammar.loadGrammar(grammarLocation);
////    eio = new ExactInsideOutside(g);
////    prop = new InformedBracketProposer(largeSpanExponent, likelySpanExponent);
////    aux = new AuxVarInsideOutside(eio, prop, 
////        new SimpleBracketNumberController(nBrackets, 0), new IterManager(nIterations, nBurnIn), rand);
////  }
////
////  public static void main(String [] args) throws IOException 
////  {
////    Execution.run(args, new Experiments());
////  }
////
////  private int iteration = 0;
////  private HTMLRenderer renderer = new HTMLRenderer();
////  public void run()
////  {
////    Execution.printOptions();
////    try
////    {
////      init();
////      iteration = 0;
////      for (String line : IO.i(sentencesLocation))
////      {
////        List<String> sentence = Arrays.asList(line.split("\\s+"));
////        if (sentence.size() >= minSentenceLength)
////          compareMethods(sentence);
////        iteration++;
////      }
////    }
////    catch (IOException e) { e.printStackTrace(); }
////  }
////  
////  private int currentLikelyIncrement = 1;
////  private int currentLargeSpanIncrement = 1;
////  private void compareMethods(List<String> sentence)
////  {
////    track("Comparing exact and approximate, sentence length: " + sentence.size(),true);
////    {
////    	NaiveSuffStat suffStatExact = new NaiveSuffStat(g.getIndex());
////      StopWatch watch = new StopWatch();
////      
////      track("ExactInsideOutside.compute()", true);
////      {
////        watch.start();
////        eio.compute(sentence, suffStatExact);
////        watch.stop();
////      }
////      end_track();
////  
////      prop.put(sentence, eio.getBracketPosteriors());
////      prop.setEffExp(largeSpanExponent);
////      for (currentLargeSpanIncrement = 1; currentLargeSpanIncrement < exponentsNIncrements; currentLargeSpanIncrement++)
////      {
////        prop.setStatExp(likelySpanExponent);
////        for (currentLikelyIncrement = 1; currentLikelyIncrement < exponentsNIncrements; currentLikelyIncrement++)
////        {
////          println("Time for exact: " + watch.ms + "ms");
////          
////          NaiveSuffStat suffStat = new NaiveSuffStat(g.getIndex());
////          PrintMonitor monitor = aux.new PrintMonitor(suffStatExact.mlGrammar(), renderer);
////          aux.setMonitor(monitor);
////          try {
////            aux.compute(sentence, suffStat);
////          } catch (Exception e) {
////            println(e.toString());
////          }
////          printStats(renderer.getHTMLPage());
////          renderer = new HTMLRenderer();
////          
////          prop.setStatExp(prop.getStatExp() + exponentsIncrementSize);
////        }  
////        prop.setEffExp(prop.getEffExp() + exponentsIncrementSize);
////      }
////    }
////    end_track();
////  }
////  
////  private void printStats(StringBuilder stats)
////  {
////    try
////    {
////      String outputFile = Execution.getFile(outputPrefix);
////      if (outputFile == null) outputFile = outputPrefix;
////      PrintWriter out = IOUtils.openOut(outputFile + "." + Extensions.extension2String(iteration) 
////          + ".eff" + Extensions.extension2String(currentLargeSpanIncrement) 
////          + ".sta" + Extensions.extension2String(currentLikelyIncrement));
////      out.append(stats);
////      out.close();
////    }
////    catch (IOException e) { e.printStackTrace(); }
////  }
////  
////  private void println(String string) 
////  { 
////    logs(string); 
////    renderer.addItem(string);
////  }
//}
