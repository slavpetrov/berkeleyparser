//package edu.berkeley.nlp.auxv;
//
//import java.util.ArrayList;
//import java.util.Collection;
//import java.util.List;
//import java.util.Random;
//
//import nuts.util.Tree;
//import fig.basic.Pair;
//
//public class Utils
//{
//  public static boolean isSpanAllowed(int left, int right, 
//      Collection<Pair<Integer, Integer>> negativeBracketConstraints, 
//      Collection<Pair<Integer, Integer>> positiveBracketConstraints)
//  {
//    Pair<Integer, Integer> key = new Pair<Integer, Integer>(left, right);
//    if (negativeBracketConstraints.contains(key)) return false;
//    if (positiveBracketConstraints.contains(key)) return true;
//    // check for overlaps
//    for (Pair<Integer, Integer> constraint : positiveBracketConstraints)
//    {
//      int left2 = constraint.getFirst();
//      int right2 = constraint.getSecond();
//      if (right2 > left && right2 < right && left2 < left) return false;
//      if (left2 > left && left2 < right && right2 > right) return false;
//    }
//    return true;
//  }
//  
//  public static String summary(double [] numbers)
//  {
//    double max = Double.NEGATIVE_INFINITY, sum = 0.0;
//    for (double number : numbers)
//    {
//      if (number > max) max = number;
//      sum += number;
//    }
//    return "avg=" + (sum/((double) numbers.length)) + ", max=" + max;
//  }
//  
//  public static int nextInt(Random rand, int minIncl, int maxExcl)
//  {
//    return rand.nextInt(maxExcl - minIncl) + minIncl;
//  }
//  
//  public static String printSpan(Pair<Integer, Integer> span)
//  {
//    return "[" + span.getFirst() + "," + span.getSecond() + ")";
//  }
//  
//  public static <T> void terminals(Tree<T> tree, Collection<T> dest)
//  {
//    if (tree.getChildren().size() == 0) dest.add(tree.getLabel());
//    else for (Tree<T> child : tree.getChildren()) terminals(child, dest);
//  }
//  
//  public static <T> void nonTerminals(Tree<T> tree, Collection<T> dest)
//  {
//    if (tree.getChildren().size() != 0) dest.add(tree.getLabel());
//    for (Tree<T> child : tree.getChildren()) nonTerminals(child, dest);
//  }
//  
//  public static Tree<String> removeUnaryChains(Tree<String> tree)
//  {
//    Tree<String> result = new Tree<String>(tree.getLabel());
//    List<Tree<String>> resultsChildren = new ArrayList<Tree<String>>();
//    for (Tree<String> child : tree.getChildren())
//      resultsChildren.add(removeUnaryChains(child));
//    if (resultsChildren.size() == 1 && !resultsChildren.get(0).isLeaf())
//    {
//      result.setLabel(result.getLabel() + "-" + resultsChildren.get(0).getLabel());
//      result.setChildren(resultsChildren.get(0).getChildren());
//    }
//    else result.setChildren(resultsChildren);
//    return result;
//  }
//}
