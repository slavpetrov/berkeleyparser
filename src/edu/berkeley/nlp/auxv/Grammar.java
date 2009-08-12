//package edu.berkeley.nlp.auxv;
//
//import java.io.IOException;
//import java.util.ArrayList;
//import java.util.Collection;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Map;
//import java.util.Random;
//import java.util.Set;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
//import nuts.io.IO;
//import nuts.util.Counter;
//import nuts.util.Indexer;
//import nuts.util.MathUtils;
//import nuts.util.Tree;
//import fig.basic.Pair;
//import fig.prob.SampleUtils;
//
///**
// * A CFG in Chomsky Normal Form with associated numerical scores
// * 
// * Note that although the terminoly is "probability" in the code below, they 
// * are not actually required to be probabilities (the method isProb() can
// * inform the user)
// * 
// * @author Alexandre Bouchard
// *
// */
//public final class Grammar
//{
//  private final double [][][] binPrs; // parent -> child1 -> child2 -> pr
//  private final Map<Pair<Integer, Integer>, Double> unPrs = new HashMap<Pair<Integer, Integer>, Double>();
//  private final GrammarIndex idx;
//  
//  public Grammar(GrammarIndex idx)
//  {
//    this.idx = idx;
//    this.binPrs = new double[idx.nNonTerm()][idx.nNonTerm()][idx.nNonTerm()];
//  }
//  
//  public boolean isProb()
//  {
//    for (int s1 = 0; s1 < idx.nNonTerm(); s1++)
//    {
//      // two nodes emissions
//      double sum = 0.0;
//      for (int t = 0; t < idx.nTerm(); t++)
//        sum += unPr(t, s1);
//      if (!MathUtils.close(sum, 1.0)) return false;
//      // three nodes
//      sum = 0.0;
//      for (int s2 = 0; s2 < idx.nNonTerm(); s2++)
//        for (int s3 = 0; s3 < idx.nNonTerm(); s3++)
//          sum += binPr(s1, s2, s3);
//      if (!MathUtils.close(sum, 1.0)) return false;
//    }
//    return true;
//  }
//  
//  public double binPr(int parent, int child1, int child2)
//  {
//    return binPrs[parent][child1][child2];
//  }
//  public void setBinPr(int parent, int child1, int child2, double value)
//  {
//    binPrs[parent][child1][child2] = value;
//  }
//  public double unPr(int parent, int child)
//  {
//    if (parent < 0 || parent >= idx.nNonTerm() || 
//        child < 0 || child >= idx.nTerm()) throw new RuntimeException();
//    Double result = unPrs.get(new Pair<Integer, Integer>(parent, child));
//    if (result == null) return 0.0;
//    else return result;
//  }
//  public void setUnPr(int parent, int child, double value)
//  {
//    unPrs.put(new Pair<Integer, Integer>(parent, child), value);
//  }
//  
//  public int nNonTerm() { return idx.nNonTerm(); }
//  public int nTerm() { return idx.nTerm(); }
//  public int startSymbol() { return idx.startSymbol; }
//  
//  public GrammarIndex getIndex() { return idx; }
//  
//  @Override
//  public String toString()  
//  {
//    StringBuilder builder = new StringBuilder();
//    builder.append("START SYMBOL:" +  idx.nonTermIndex2String(startSymbol()) + "\n");
//    for (int parent = 0; parent < nNonTerm(); parent++)
//    {
//      for (int prod = 0; prod < nTerm(); prod++)
//        if (unPr(parent, prod) > 0.0) 
//          builder.append(toString(parent, prod) + "\n");
//      for (int child1 = 0; child1 < nNonTerm(); child1++)
//        for (int child2 = 0; child2 < nNonTerm(); child2++)
//          if (binPr(parent, child1, child2) > 0.0) 
//            builder.append(toString(parent, child1, child2) + "\n");
//    }
//    return builder.toString();
//  }
//  
//  public String toString(int parent, int child)
//  {
//    return idx.toString(parent, child) + "\t" + unPr(parent, child);
//  }
//  
//  public String toString(int parent, int child1, int child2)
//  {
//    return idx.toString(parent, child1, child2) + "\t" + binPr(parent, child1, child2);
//  }
//  
//  public String nonTermIndex2String(int index) { return idx.nonTermIndexer.i2o(index); }
//  public String termIndex2String(int index) { return idx.termIndexer.i2o(index); }
//  
//  public int nonTermString2Index(String string) { return idx.nonTermIndexer.o2i(string); }
//  public int termString2Index(String string) { return idx.termIndexer.o2i(string); }
//  
//  public static void main(String [] args) throws IOException
//  {
//    Grammar g = loadGrammar("test/astroGrammar");
//    Random rand = new Random();
//    List<List<String>> test = new ArrayList<List<String>>();
//    for (int i = 0; i < 100000; i++)
//    {
//      test.add(g.generate(rand));
//    }
//    Counter<List<String>> counter = new Counter<List<String>>();
//    counter.incrementAll(test, 1.0);
//    counter.normalize();
//    System.out.println(counter.toString(30));
//  }
//  
//  public List<String> generate(Random rand)
//  {
//    return generate(idx.startSymbol, rand);
//  }
//  
//  private List<String> generate(int symbol, Random rand)
//  {
//    List<String> result = new ArrayList<String>();
//    // linearize
//    double [] prs = new double[nNonTerm() + nNonTerm() * nNonTerm()];
//    for (int prod1 = 0; prod1 < nNonTerm(); prod1++)
//    {
//      prs[prod1] = unPr(symbol, prod1);
//      for (int prod2 = 0; prod2 < nNonTerm(); prod2++)
//        prs[nNonTerm() + prod1 + nNonTerm() * prod2] = binPrs[symbol][prod1][prod2];
//    }
//    if (!MathUtils.isProb(prs)) throw new RuntimeException();
//    // roll the biased dice
//    int sample = SampleUtils.sampleMultinomial(rand, prs);
//    // delinearize
//    if (sample < nNonTerm())
//    {
//      // unary
//      int prod = sample;
//      result.add(termIndex2String(prod));
//    }
//    else
//    {
//      // binary
//      sample = sample - nNonTerm();
//      int prod1 = sample % nNonTerm();
//      int prod2 = sample / nNonTerm();
//      result.addAll(generate(prod1, rand));
//      result.addAll(generate(prod2, rand));
//    }
//    return result;
//  }
//  
//  public static GrammarIndex loadGrammarIndex(Iterable<Tree<String>> trees)
//  {
//    Set<String> terminals = new HashSet<String>();
//    Set<String> nonTerminals = new HashSet<String>();
//    String start = null;
//    for (Tree<String> tree : trees)
//    {
//      if (start != null && !start.equals(tree.getLabel()))
//        throw new RuntimeException("All trees should have the same root");
//      start = tree.getLabel();
//      Utils.terminals(tree, terminals);
//      Utils.nonTerminals(tree, nonTerminals); 
//    }
//    return new GrammarIndex(nonTerminals, terminals, start);
//  }
//  
//  public static final class GrammarIndex
//  {
//    private Indexer<String> termIndexer, nonTermIndexer;
//    private int startSymbol;
//    
//    private GrammarIndex() {}
//    
//    public GrammarIndex(Collection<String> nonTerminals, Collection<String> terminals, String startSymbol)
//    {
//      termIndexer = new Indexer<String>();
//      nonTermIndexer = new Indexer<String>();
//      for (String nonTerminal : nonTerminals) nonTermIndexer.addToIndex(nonTerminal);
//      for (String terminal : terminals) termIndexer.addToIndex(terminal);
//      this.startSymbol = nonTermString2Index(startSymbol);
//    }
//    
//    public int [] convertSentence(List<String> sentence)
//    {
//      int [] result = new int[sentence.size()];
//      for (int i = 0; i < sentence.size(); i++)
//      {
//        result[i] = termIndexer.o2i(sentence.get(i));
//      }
//      return result;
//    }
//    
//    public int nNonTerm() { return nonTermIndexer.size(); }
//    public int nTerm() { return termIndexer.size(); }
//    
//    public String nonTermIndex2String(int index) { return nonTermIndexer.i2o(index); }
//    public String termIndex2String(int index) { return termIndexer.i2o(index); }
//    
//    public int nonTermString2Index(String string) { return nonTermIndexer.o2i(string); }
//    public int termString2Index(String string) { return termIndexer.o2i(string); }
//    
//    public String toString(int parent, int child)
//    {
//      return "" + nonTermIndex2String(parent) + " -> " + termIndex2String(child);
//    }
//    
//    public String toString(int parent, int child1, int child2)
//    {
//      return "" + nonTermIndex2String(parent) + " -> " + nonTermIndex2String(child1) 
//      + " " +nonTermIndex2String(child2);
//    }
//  }
//  
//  public static Grammar hardLoadGrammar(String path)
//  {
//    try { return loadGrammar(path); }
//    catch (IOException e) { throw new RuntimeException(); }
//  }
//  
//  /**
//   * 
//   * @param path
//   * @throws IOException 
//   * @throws NumberFormatException 
//   */
//  public static Grammar loadGrammar(String path) throws IOException
//  {
//    // Create the indexers first
//    final GrammarIndex idx = new GrammarIndex();
//    idx.termIndexer = new Indexer<String>();
//    idx.nonTermIndexer = new Indexer<String>();
//    processRules(path, new RuleProcessor() {
//      public void processStartSymbol(String symbol)
//      {
//        idx.nonTermIndexer.addToIndex(symbol);
//        idx.startSymbol = idx.nonTermString2Index(symbol);
//      }
//      public void processBinary(String lhs, String rhs1, String rhs2, double pr) {
//        idx.nonTermIndexer.addToIndex(lhs, rhs1, rhs2);
//      }
//      public void processUnary(String lhs, String rhs, double pr) {
//        idx.nonTermIndexer.addToIndex(lhs);
//        idx.termIndexer.addToIndex(rhs);
//      } });
//    // then, populate
//    final Grammar g = new Grammar(idx);
//    processRules(path, new RuleProcessor() {
//      public void processStartSymbol(String symbol) {}
//      public void processBinary(String lhs, String rhs1, String rhs2, double pr) {
//        int parent = g.idx.nonTermIndexer.o2i(lhs);
//        int child1 = g.idx.nonTermIndexer.o2i(rhs1);
//        int child2 = g.idx.nonTermIndexer.o2i(rhs2);
//        g.setBinPr(parent, child1, child2, pr);
//      }
//      public void processUnary(String lhs, String rhs, double pr) {
//        int parent = g.idx.nonTermIndexer.o2i(lhs);
//        int child = g.idx.termIndexer.o2i(rhs);
//        g.setUnPr(parent, child, pr);
//      } });
//    return g;
//  }
//  private static interface RuleProcessor
//  {
//    public void processUnary(String lhs, String rhs, double pr);
//    public void processBinary(String lhs, String rhs1, String rhs2, double pr);
//    public void processStartSymbol(String symbol);
//  }
//  
//  public static final String allowedSymbolsRegex = "[^ ]+";
//  public static final  Pattern rule = Pattern.compile(
//          "^(" + allowedSymbolsRegex + ")\\s+[-][>]\\s+(" + allowedSymbolsRegex 
//            + ")(?:\\s+(" + allowedSymbolsRegex + "))?\\t(.*)$");
//  public static final  Pattern ignoredLines = Pattern.compile("(^\\s*[#]|^\\s*$)");
//  public static final  Pattern rootSpec = Pattern.compile("START SYMBOL[:]\\s*(" + allowedSymbolsRegex + ")");
//  private static void processRules(String path, RuleProcessor p) 
//                      throws NumberFormatException, IOException
//  {
//    Matcher m;
//    for (String line : IO.i(path))
//      if (ignoredLines.matcher(line).matches()) {} // comments, do nothing
//      else if ((m = rootSpec.matcher(line)).matches())
//        p.processStartSymbol(m.group(1));
//      else if ((m = rule.matcher(line)).matches())
//      {
//        double pr = Double.parseDouble(m.group(4));
//        String lhs = m.group(1); 
//        String rhs = m.group(2);
//        if (m.group(3) == null) p.processUnary(lhs, rhs, pr);
//        else p.processBinary(lhs, rhs, m.group(3), pr);
//      }
//      else throw new RuntimeException("Wrong format: " + line);
//  }
//  
//  public static double [] nonTermHells(Grammar realGrammar, Grammar approx)
//  {
//    return nonTermDivergence(realGrammar, approx, new Hellinger());
//  }
//  
//  private static double [] nonTermDivergence(Grammar realGrammar, Grammar approximation, Divergence factory)
//  {
//    final int nSymb = realGrammar.idx.nNonTerm();
//    if (nSymb != approximation.idx.nNonTerm())
//      throw new RuntimeException();
//    double [] result = new double[nSymb];
//    for (int lhs = 0; lhs < nSymb; lhs++)
//    {
//      Divergence d = factory.newInstance();
//      for (int rhs1 = 0; rhs1 < nSymb; rhs1++)
//        for (int rhs2 = 0; rhs2 < nSymb; rhs2++)
//        {
//          double p = realGrammar.binPr(lhs, rhs1, rhs2);
//          double q = approximation.binPr(lhs, rhs1, rhs2);
//          d.addPoints(p, q);
////          if (p == 0 && q == 0) {}
////          else if (p == 0) {}
////          else if (q == 0) sum = Double.POSITIVE_INFINITY;
////          else sum += p * Math.log(p / q);
//        }
//      result[lhs] = d.getDivergence();
//    }
//    return result;
//  }
//  
//  private static interface Divergence
//  {
//    public void addPoints(double real, double approx);
//    public double getDivergence();
//    public Divergence newInstance();
//  }
//  
//  public static class Hellinger implements Divergence
//  {
//    private double sum = 0.0;
//    public void addPoints(double real, double approx)
//    {
//      sum += Math.pow(Math.sqrt(real) - Math.sqrt(approx) ,2);
//    }
//    public double getDivergence() { return Math.sqrt(sum); }
//    public Divergence newInstance() { return new Hellinger(); }
//  }
//}
