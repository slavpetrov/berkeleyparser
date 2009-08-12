//package edu.berkeley.nlp.auxv;
//
//import java.io.IOException;
//import java.util.List;
//import java.util.Random;
//
//import edu.berkeley.nlp.syntax.StateSet;
//
//import nuts.tui.Table;
//
//
///**
// * Contains an implementation of the inside outside algorithm, and data struct
// * and accessor to use the result of this computation to update expected suff.
// * stats.
// * 
// * 
// * @author Alexandre Bouchard
// *
// */
//public class ExactInsideOutside implements ConstrainedInsideOutside
//{
//  // input
//  private int [] sentence;
//  private NaiveSuffStat suffStat;
//  // grammar
//  private final Grammar g;
//  // internal data struct
//  private double [][][] inside;  
//  private double [][][] outside;
//  // internal accessor for these struct
//  private double i(int root, int left, int right) { return get(root, left, right, inside); }
//  private double o(int root, int left, int right) { return get(root, left, right, outside); }
//  private static double get(int root, int left, int right, double [][][] prs) { return prs[root][left][right]; }
//  private void setI(int root, int left, int right, double value) { inside[root][left][right] = value; }
//  private void setO(int root, int left, int right, double value) { outside[root][left][right] = value; }
//  
//  public double logSentencePr()
//  {
//    return Math.log(sentencePr());
//  }
//	public double stateSetPosterior(StateSet parentEdge, StateSet leftEdge, StateSet rightEdge) {
//		throw new RuntimeException();
//	}
//	public double stateSetPosterior(StateSet parentEdge, StateSet childEdge) {
//		throw new RuntimeException();
//	}
//	public double stateSetPosterior(StateSet edge) {
//		throw new RuntimeException();
//	}
//  private double sentencePr()
//  {
//    if (sentence == null) throw new RuntimeException("Call insideOutside() first");
//    return i(g.startSymbol(), 0, length());
//  }
//  
//  public ExactInsideOutside(Grammar g) { this.g = g; }
//  
//  /**
//   * Execute the inside outside algorithm
//   * @param sentence
//   * @param suffStat
//   */
//  public boolean compute(List<String> sentence, SuffStat suffStat)
//  {
//    return compute(sentence, suffStat, null);
//  }
//  
//  private void updateThreeNonTermPostCounts()
//  {
//    double sentencePr = sentencePr();
//    for (int j = 0; j < g.nNonTerm(); j++)
//      for (int r = 0; r < g.nNonTerm(); r++)
//        for (int s = 0; s < g.nNonTerm(); s++)
//        {
//          double rulePr = g.binPr(j, r, s);
//          if (rulePr != 0.0)
//          {
//            double sum = 0.0;
//            for (int p = 0; p < length(); p++)
//              for (int q = p + 1; q <= length(); q++)
//              {
//                if (isSpanAllowed(p, q))
//                {
//                  double subSum = 0.0;
//                  for (int d = p + 1; d < q; d++)
//                  {
//                    subSum += i(r, p, d) * i(s, d, q);
//                  }
//                  sum += subSum * o(j, p, q);
//                }
//              }
//            if (sum != 0.0)
//              suffStat.incrThreeNonTermPostCount(j, r, s, rulePr * sum / sentencePr);
//          }
//        }
//  }
//  
//  private void updateOneNonTermPostCounts()
//  {
//    double sentencePr = sentencePr();
//    for (int symb = 0; symb < g.nNonTerm(); symb++)
//    {
//      double sum = 0.0;
//      for (int p = 0; p < length(); p++)
//        for (int q = p; q <= length(); q++)
//          sum += i(symb, p, q) * o(symb, p, q);
//      if (sum != 0.0)
//        suffStat.incrOneNonTermPostCount(symb, sum / sentencePr);
//    }
//  }
//  
//  private void updateTermNonTermPostCounts()
//  {
//    double sentencePr = sentencePr();
//    for (int position = 0; position < length(); position++)
//    {
//      int terminalCode = sentence[position];
//      for (int nonTermCode = 0; nonTermCode < g.nNonTerm(); nonTermCode++)
//        suffStat.incrTermNonTermPostCount(terminalCode, nonTermCode, 
//            i(nonTermCode, position, position + 1) * 
//            o(nonTermCode, position, position + 1) / sentencePr);
//    }
//  }
//  
//  private void computeInside()
//  {
//    // deal with unary case / initialization
//    for (int preterminal = 0; preterminal < g.nNonTerm(); preterminal++)
//      for (int position = 0; position < length(); position++)
//        setI(preterminal, position, position + 1, g.unPr(preterminal, sentence[position])); 
//    //binary case / larger spans 
//    for (int spanLength = 2; spanLength <= length(); spanLength++)
//      for (int position = 0; position <= length() - spanLength; position++)
//        if (isSpanAllowed(position, position + spanLength))
//          for (int rootSymb = 0; rootSymb < g.nNonTerm(); rootSymb++)
//          {
//            double sum = 0.0;
//            for (int leftChildLength = 1; leftChildLength < spanLength; leftChildLength++)
//              if (isSpanAllowed(position, position + leftChildLength) &&
//                  isSpanAllowed(position + leftChildLength, position + spanLength))
//                for (int leftChildSymb = 0; leftChildSymb < g.nNonTerm(); leftChildSymb++)
//                {
//                  double leftPr = i(leftChildSymb, position, position + leftChildLength);
//                  if (leftPr != 0.0)
//                    for (int rightChildSymb = 0; rightChildSymb < g.nNonTerm(); rightChildSymb++)
//                    {
//                      double rightPr = i(rightChildSymb, position + leftChildLength,
//                          position + spanLength);
//                      if (rightPr == 0) continue;
//                      double rulePr = g.binPr(rootSymb, leftChildSymb, rightChildSymb);
//                      sum += leftPr * rightPr * rulePr;
//                    }
//                }
//            if (sum != 0.0) setI(rootSymb, position, position + spanLength, sum);
//          }
//  }
//  
//  private void computeOutside()
//  {
//    // init
//    setO(g.startSymbol(), 0, length(), 1.0);
//    // smaller spans
//    for (int spanLength = length() - 1; spanLength > 0; spanLength--)
//      for (int position = 0; position <= length() - spanLength; position++)
//        if (isSpanAllowed(position, position + spanLength))
//          for (int currentSymb = 0; currentSymb < g.nNonTerm(); currentSymb++)
//          {
//            double sum = 0.0;
//            // current span is attached at the left
//            for (int siblingLength = 1; position + spanLength + siblingLength <= length(); siblingLength++) /**/
//              if (isSpanAllowed(position + spanLength, position + spanLength + siblingLength) && /**/
//                  isSpanAllowed(position, position + spanLength + siblingLength)) /**/
//                for (int siblingSymb = 0; siblingSymb < g.nNonTerm(); siblingSymb++)
//                {
//                  double siblingPr = i(siblingSymb, position + spanLength, position + spanLength + siblingLength); /**/
//                  if (siblingPr != 0.0)
//                    for (int parentSymb = 0; parentSymb < g.nNonTerm(); parentSymb++)
//                    {
//                      double parentPr = o(parentSymb, position, position + spanLength + siblingLength); /**/
//                      if (parentPr == 0) continue;
//                      double rulePr = g.binPr(parentSymb, currentSymb, siblingSymb); /**/
//                      sum += siblingPr * parentPr * rulePr;
//                    }
//                }
//            // current span is attached at the right
//            for (int siblingLength = 1; siblingLength <= position; siblingLength++) /**/
//              if (isSpanAllowed(position - siblingLength, position) && /**/
//                  isSpanAllowed(position - siblingLength, position + spanLength)) /**/
//                for (int siblingSymb = 0; siblingSymb < g.nNonTerm(); siblingSymb++)
//                {
//                  double siblingPr = i(siblingSymb, position - siblingLength, position); /**/
//                  if (siblingPr != 0.0)
//                    for (int parentSymb = 0; parentSymb < g.nNonTerm(); parentSymb++)
//                    {
//                      double parentPr = o(parentSymb, position - siblingLength, position + spanLength); /**/
//                      if (parentPr == 0) continue;
//                      double rulePr = g.binPr(parentSymb, siblingSymb, currentSymb); /**/
//                      sum += siblingPr * parentPr * rulePr;
//                    }
//                }
//            if (sum != 0.0) setO(currentSymb, position, position + spanLength, sum);
//          }
//  }
//  
//  /**
//   * Execute the inside outside algorithm with constraints on the 
//   * brackets
//   * @param sentence
//   * @param suffStat
//   */
//  public boolean compute(List<String> sentence, SuffStat suffStat, 
//      boolean [][] spanAllowed)
//  {
//    this.sentence = g.getIndex().convertSentence(sentence);
//    setConstraints(spanAllowed);
//    this.suffStat = (NaiveSuffStat) suffStat;
//    inside = new double[g.nNonTerm()][length()][length() + 1];
//    outside = new double[g.nNonTerm()][length()][length() + 1];
//    computeInside();
//    computeOutside();
//    if (sentencePr() > 0.0)
//    {
//      updateThreeNonTermPostCounts();
//      updateOneNonTermPostCounts();
//      updateTermNonTermPostCounts();
//      return true;
//    }
//    return false;
//  }
//  
//  private boolean [][] spanAllowed;
//  
//  /**
//   * null will set no constraint
//   * @param inSpanAllowed
//   */
//  private void setConstraints(boolean [][] inSpanAllowed)
//  {
//    spanAllowed = new boolean[length()][length() + 1];
//    for (int l = 0; l < length(); l++)
//      for (int r = l + 1; r <= length(); r++)
//        spanAllowed[l][r] = (inSpanAllowed == null ? true : inSpanAllowed[l][r]);
//  }
//  
//  private boolean isSpanAllowed(int leftIncl, int rightExcl)
//  {
//    return spanAllowed[leftIncl][rightExcl];
//  }
//  
//  public double [][] getBracketPosteriors()
//  {
//    double [][] result = new double[length()][length() + 1];
//    for (int i = 0; i < length(); i++)
//      for (int j = i + 1; j < length() + 1; j++)
//      {
//        double sum = 0.0;
//        for (int symb = 0; symb < g.nNonTerm(); symb++)
//        {
//          sum += i(symb, i, j) * o(symb, i, j);
//        }
//        result[i][j] = sum / sentencePr();
//      }
//    return result;
//  }
//  
//  @Override
//  public String toString()
//  {
//    StringBuilder builder = new StringBuilder();
//    double [][][][] tables = {inside, outside};
//    for (final double [][][] table : tables)
//    {
//      Table tableRepn = new Table(new Table.Populator() {
//        @Override public void populate() {
//          for (int i = 0; i < length(); i++)
//          {
//            addLines(0, i + 1, g.termIndex2String(sentence[i]));
//            addLines(i + 1, 0, g.termIndex2String(sentence[i]));
//          }
//          for (int s = 0; s < g.nNonTerm(); s++)
//            for (int l = 0; l < length(); l++)
//              for (int r = 0; r <= length(); r++)
//                if (get(s, l, r, table) > 0.0)
//                  addLines(l + 1, r + 1, g.nonTermIndex2String(s) + ":" + 
//                      get(s, l, r, table));
//        }});
//      builder.append(tableRepn.toString());
//    }
//    return builder.toString();
//  }
//  
//  private int length() { return sentence.length; }
//  public int currentSentenceLength() { return length(); }
//  
//  public static void main(String [] args) throws IOException
//  {
//    Random rand = new Random(1);
//    Grammar g = Grammar.loadGrammar("test/astroGrammar");
//    System.out.println(g.toString() + "\n\n");
//    long start = System.currentTimeMillis();
//    NaiveSuffStat suffStats = new NaiveSuffStat(g.getIndex());
//    ExactInsideOutside parser = new ExactInsideOutside(g);
//    for (int i = 0; i < 1000; i++)
//    {
//      List<String> sample = g.generate(rand);
//      if (sample.size() > 100) System.out.println(sample);
//      System.out.println("Sent. #" + i + ", length is " + sample.size());
//      //List<String> sent = Arrays.asList("astronomers", "saw", "stars", "with", "ears");
//      parser.compute(sample, suffStats);
//    }
//    long finish = System.currentTimeMillis();
//    System.out.println("\nTime:" + (finish - start));
//    Grammar mlEstimate = suffStats.mlGrammar();
//    System.out.println(mlEstimate);
//  }
//}
