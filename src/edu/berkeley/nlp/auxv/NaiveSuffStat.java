//package edu.berkeley.nlp.auxv;
//
//import java.util.List;
//
//import nuts.util.Tree;
//
//import edu.berkeley.nlp.auxv.Grammar.GrammarIndex;
//
///**
// * The result of posterior PCFG computations needed to train Grammars
// * @author Alexandre Bouchard
// *
// */
//public class NaiveSuffStat implements SuffStat
//{
//  public double [][][] threeNonTermPostCounts; // parent -> child1 -> child2
//  public double [] oneNonTermPostCounts;
//  public double [][] termNonTermPostCounts; // term -> nonTerm post count
//  private GrammarIndex index;
//  
//  public NaiveSuffStat(GrammarIndex index) 
//  { 
//    this.index = index; 
//    this.threeNonTermPostCounts = new double[index.nNonTerm()][index.nNonTerm()][index.nNonTerm()];
//    this.oneNonTermPostCounts = new double[index.nNonTerm()];
//    this.termNonTermPostCounts = new double[index.nTerm()][index.nNonTerm()];
//  }
//  public GrammarIndex getGrammarIndex() { return index; }
//  public void incrThreeNonTermPostCount(int parent, int child1, int child2, double value)
//  {
//    threeNonTermPostCounts[parent][child1][child2] += value;
//  }
//  private void setThreeNonTermPostCount(int parent, int child1, int child2, double value)
//  {
//    threeNonTermPostCounts[parent][child1][child2] = value;
//  }
//  public double getThreeNonTermPostCount(int parent, int child1, int child2)
//  {
//    return threeNonTermPostCounts[parent][child1][child2];
//  }
//  public void incrOneNonTermPostCount(int symbol, double value)
//  {
//    oneNonTermPostCounts[symbol] += value;
//  }
//  private void setOneNonTermPostCount(int symbol, double value)
//  {
//    oneNonTermPostCounts[symbol] = value;
//  }
//  public double getOneNonTermPostCount(int symbol)
//  {
//    return oneNonTermPostCounts[symbol];
//  }
//  public void incrTermNonTermPostCount(int childTerminal, int parentNonTerm, double value)
//  {
//    termNonTermPostCounts[childTerminal][parentNonTerm] += value;
//  }
//  private void setTermNonTermPostCount(int childTerminal, int parentNonTerm, double value)
//  {
//    termNonTermPostCounts[childTerminal][parentNonTerm] = value;
//  }
//  public double getTermNonTermPostCount(int childTerminal, int parentNonTerm)
//  {
//    return termNonTermPostCounts[childTerminal][parentNonTerm];
//  }
//  
//  public void incr(Tree<String> observation)
//  {
//    List<Tree<String>> children = observation.getChildren();
//    String nodeName = observation.getLabel();
//    if (children.size() == 0) {} // terminal: do nothing
//    else if (children.size() > 2) throw new RuntimeException();
//    else 
//    {
//      incrOneNonTermPostCount(index.nonTermString2Index(nodeName), 1.0);
//      if (children.size() == 1)
//        incrTermNonTermPostCount( index.termString2Index(children.get(0).getLabel()), 
//                                  index.nonTermString2Index(nodeName), 1.0);
//      else
//        incrThreeNonTermPostCount(index.nonTermString2Index(nodeName), 
//                                  index.nonTermString2Index(children.get(0).getLabel()), 
//                                  index.nonTermString2Index(children.get(1).getLabel()), 1.0);
//    }
//    for (Tree<String> child : children) incr(child);
//  }
//  
//  public void times(double scalar)
//  {
//    for (int s1 = 0; s1 < index.nNonTerm(); s1++)
//    {
//      // one node
//      setOneNonTermPostCount(s1, getOneNonTermPostCount(s1) * scalar);
//      // two nodes emissions
//      for (int t = 0; t < index.nTerm(); t++)
//        setTermNonTermPostCount(t, s1, getTermNonTermPostCount(t, s1) * scalar);
//      // three nodes
//      for (int s2 = 0; s2 < index.nNonTerm(); s2++)
//        for (int s3 = 0; s3 < index.nNonTerm(); s3++)
//          setThreeNonTermPostCount(s1, s2, s3, getThreeNonTermPostCount(s1, s2, s3) * scalar);
//    }
//  }
//  
//  public void add(SuffStat other)
//  {
//  	NaiveSuffStat otherCast = (NaiveSuffStat) other;
//    for (int s1 = 0; s1 < index.nNonTerm(); s1++)
//    {
//      // one node
//      setOneNonTermPostCount(s1, getOneNonTermPostCount(s1) 
//          + otherCast.getOneNonTermPostCount(s1));
//      // two nodes emissions
//      for (int t = 0; t < index.nTerm(); t++)
//        setTermNonTermPostCount(t, s1, getTermNonTermPostCount(t, s1) 
//            + otherCast.getTermNonTermPostCount(t, s1));
//      // three nodes
//      for (int s2 = 0; s2 < index.nNonTerm(); s2++)
//        for (int s3 = 0; s3 < index.nNonTerm(); s3++)
//          setThreeNonTermPostCount(s1, s2, s3, getThreeNonTermPostCount(s1, s2, s3) 
//              + otherCast.getThreeNonTermPostCount(s1, s2, s3));
//    }
//  }
//  
//  /**
//   * Maximum likely grammar from the current suff stats
//   * 
//   * More precisely: for observed lhs, set the pr of corresponding rules to the mle
//   * for unobserved lhs, set all grammar scores to zero
//   * @return
//   */
//  public Grammar mlGrammar()
//  {
//    Grammar g = new Grammar(index);
//    for (int j = 0; j < g.nNonTerm(); j++)
//    {
//      for (int term = 0; term < g.nTerm(); term++)
//        g.setUnPr(j, term, getTermNonTermPostCount(term, j) /
//            getOneNonTermPostCount(j));
//      for (int r = 0; r < g.nNonTerm(); r++)
//        for (int s = 0; s < g.nNonTerm(); s++)
//          if (getOneNonTermPostCount(j) > 0.0)
//            g.setBinPr(j, r, s, getThreeNonTermPostCount(j, r, s) /
//                getOneNonTermPostCount(j));
//    }
//    return g;
//  }
//
//	/**
//	 * @return
//	 */
//	public SuffStat newInstance() 
//	{
//		return new NaiveSuffStat(index);
//	}
//}
