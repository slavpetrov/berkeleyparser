///**
// * 
// */
//package edu.berkeley.nlp.auxv;
//
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Collections;
//import java.util.HashMap;
//import java.util.Iterator;
//import java.util.List;
//import java.util.Map;
//import java.util.Random;
//import java.util.SortedSet;
//import java.util.TreeSet;
//
//import nuts.tui.Table;
//import fig.basic.Pair;
//
///**
// * @author Alexandre Bouchard
// *
// */
//public class BracketConstraintsUtils 
//{
//	public static <T> BracketConstraints<T> newInstance(List<T> sentence, boolean overlapping)
//	{
//		if (!overlapping) return new NonOverlappingBracketConstraints<T>(sentence);
//		else 							return new OverlappingBracketConstraints<T>(sentence);
//	}
//  private static boolean isCrossingInterval(int leftIncl,  int rightExcl, int otherLeft, int otherRight)
//  {
//    if (otherLeft >= otherRight) throw new RuntimeException();
//    if (otherRight <= leftIncl) return false;
//    if (otherLeft >= rightExcl) return false;
//    return !containsInterval(leftIncl, rightExcl, otherLeft, otherRight) && 
//    	!containedInInterval(leftIncl, rightExcl, otherLeft, otherRight);
//  }
//  private static boolean containsInterval(int leftIncl, int rightExcl, int newLeft, int newRight)
//  {
//    return newLeft >= leftIncl && newRight <= rightExcl;
//  }
//  private static boolean contains(int leftIncl, int rightExcl, int point)
//  {
//    return leftIncl <= point && point < rightExcl;
//  }
//  private static boolean containedInInterval(int leftIncl, int rightExcl, int newLeft, int newRight)
//  {
//    return newLeft <= leftIncl && newRight >= rightExcl;
//  }
//  /**
//   * Used to assess the computational gain of adding spans
//   * @return position i,j tells the parsing complexity if the (positive)
//   * constraint [i,j) were to be added
//   */
//  public static <T> double [][] optimisticParsingComplexities(BracketConstraints<T> constraints)
//  {
//  	List<T> sentence = constraints.getView().getSentence();
//    double [][] complexitiesTable = new double[sentence.size()][sentence.size() + 1];
//    for (int l = 0; l < sentence.size(); l++)
//      for (int r = l + 1; r <= sentence.size(); r++)
//        if (!constraints.getView().optimisticIsSpanAllowed(l, r)) complexitiesTable[l][r] = 0.0;
//        else if (!constraints.getView().containsBracketConstraint(l, r))
//        {
//          constraints.updateBracket(l, r, true);
//          complexitiesTable[l][r] = constraints.parsingComplexity(true) - 1; 
//          // -1 is to avoid strange bug that adding constraint increaed the complexity
//          boolean result = constraints.removeBracket(l, r);
//          assert result == true;
//        }
//        else complexitiesTable[l][r] = constraints.parsingComplexity(true);
//    return complexitiesTable;
//  }
//  public static <T> double [][] pessimisticParsingComplexities(int size)
//  {
//  	double [][] pessimisticParsingComplexities = new double[size][size + 1];
//    for (int l = 0; l < size; l++)
//      for (int r = l + 1; r <= size; r++)
//      	pessimisticParsingComplexities[l][r] 
//      	             = Math.pow(Math.max(r - l, size - (r - l)), 3);
//    return pessimisticParsingComplexities;
//  }
//  public static <T> String compiledToString(BracketConstraints<T> constraints) 
//  { 
//  	return compiledToTable(constraints).toString(); 
//  }
//  public static <T> Table compiledToTable(final BracketConstraints<T> constraints)
//  {
//    return new Table(new Table.Populator() {
//      @Override public void populate() {
//        for (int i = 0; i < constraints.getView().getSentence().size(); i++)
//        {
//          set(0, i + 1, "" + i);
//          set(i + 1, 0, "" + i);
//        }
//        boolean [][] compiled = constraints.compile();
//        for (int i = 0; i < compiled.length; i++)
//          for (int j = 0; j < compiled[i].length; j++)
//            if (!compiled[i][j])
//              set(i + 1, j + 1, "X");
//      }
//    });
//  }
//  public static <T> Table spanScoresTable(final List<T> aSentence, final double [][] numbers)
//  {
//    if (numbers.length != aSentence.size() || numbers[0].length != aSentence.size() + 1)
//      throw new RuntimeException();
//    return new Table(new Table.Populator() {
//      @Override public void populate() {
//        for (int i = 0; i < aSentence.size(); i++)
//        {
//          set(0, i + 1, "" + i + "." + aSentence.get(i));
//          set(i + 1, 0, "" + i + "." + aSentence.get(i).toString());
//        }
//        for (int i = 0; i < numbers.length; i++)
//          for (int j = i + 1; j < numbers[i].length; j++)
//            set(i + 1, j + 1, numbers[i][j]);
//      }
//    });
//  }
//  public static <T> Table optimisticParsingComplexityToTable(final BracketConstraints<T> constraints)
//  {
//    return spanScoresTable(constraints.getView().getSentence(), 
//    		constraints.getView().optimisticParsingComplexities());
//  }
//  public static <T> String optimisticParsingComplexityToString(final BracketConstraints<T> constraints) 
//  {
//    return optimisticParsingComplexityToTable(constraints).toString();
//  }
//  
//	public static <T> String toString(BracketConstraints<T> constraints)
//	{
//	  return toTable(constraints).toString();
//	}
//	public static <T> Table toTable(final BracketConstraints<T> _constraints)
//	{
//		if (_constraints instanceof NonOverlappingBracketConstraints)
//		{
//			final NonOverlappingBracketConstraints constraints = (NonOverlappingBracketConstraints) _constraints;
//		  Table result = new Table(new Table.Populator() {
//		    @Override public void populate() {
//		      for (int i = 0; i < constraints.getView().getSentence().size(); i++)
//		        set(0, i,  "" + i + "." + constraints.getView().getSentence().get(i) + " ");
//		      populate(1, constraints.root);
//		    }
//		    private void populate(int depth, NonOverlappingBracketConstraints.Bracket bra)
//		    {
//		      append(depth, bra.leftIncl, (bra.isPositive ? "[" : "!["));
//		      append(depth, bra.rightExcl, ")");
//		      for (NonOverlappingBracketConstraints.Bracket child : bra.children)
//		        populate(depth + 1, child);
//		    }
//		  });
//		  result.setBorder(false);
//		  return result;
//		}
//		else if (_constraints instanceof OverlappingBracketConstraints)
//		{
//			final OverlappingBracketConstraints<T> constraints = (OverlappingBracketConstraints<T>) _constraints;
//		  Table result = new Table(new Table.Populator() {
//		    @Override public void populate() {
//		      for (int i = 0; i < constraints.getView().getSentence().size(); i++)
//		        set(0, i,  "" + i + "." + constraints.getView().getSentence().get(i) + " ");
//		      int row = 1;
//		      for (Pair<Integer, Integer> key : constraints.brackets.keySet())
//		      {
//		      	boolean isPos = constraints.brackets.get(key);
//		      	set(row, key.getFirst(), (isPos ? "[" : "!["));
//		      	set(row, key.getSecond(), ")");
//		      	row++;
//		      }
//		    }
//		  });
//		  result.setBorder(false);
//		  return result;
//		}
//		else throw new RuntimeException();
//	}
//	
//	public static void main(String [] args)
//	{
//	  List<String> sent = Arrays.asList("astronomers","saw","ears","with","ears","with","ears","with","stars","with","stars","with","astronomers");
//	  BracketConstraints<String> overLap = newInstance(sent, true);
//	  BracketConstraints<String> noOverLap = newInstance(sent, false);
//	  
//	  Random rand = new Random(1);
//	  for (int i = 0; i < 20; i++)
//	  {
//	    String overlapCompiledStr = compiledToString(overLap);
//	    String nonooverlapCompilerStr = compiledToString(noOverLap);
//	    if (!overlapCompiledStr.equals(nonooverlapCompilerStr))
//	    	throw new RuntimeException();
//	    System.out.println(overlapCompiledStr);
//	    System.out.println("---");
//	    int left = rand.nextInt(sent.size());
//	    int right = Utils.nextInt(rand, left + 1, sent.size() + 1);
//	    boolean pos = true; //rand.nextBoolean();
//	    if (overLap.getView().optimisticIsSpanAllowed(left, right))
//	    {
//		    System.out.println("Adding " + left + "," + right + " (" + pos + "): " 
//		        + noOverLap.updateBracket(left, right, pos));
//		    overLap.updateBracket(left, right, pos);
//		    System.out.println("For overlap: " + cmplxInterval(overLap));
//		    System.out.println("For non overlap: " + cmplxInterval(noOverLap));
//	    }
//	    
//	  }
//	}
//	public static <T> String cmplxInterval(BracketConstraints<T> constraints)
//	{
//		return "[" + constraints.parsingComplexity(true) + ", " +
//			constraints.parsingComplexity(false) + "]";
//	}
//	
//	private static class OverlappingBracketConstraints<T> implements BracketConstraints<T>
//	{
//		private BracketConstraints<T> instance() { return this; }
//		private final List<T> sentence;
//		private final Map<Pair<Integer, Integer>, Boolean> brackets 
//			= new HashMap<Pair<Integer,Integer>, Boolean>(); // interval -> 0 for negative, 1 for positive
//		private final OverlappingBracketConstraintsView view = new OverlappingBracketConstraintsView();
//		public int nActive()
//		{
//			int sum = 0;
//			for (Pair<Integer, Integer> key : brackets.keySet())
//				if (brackets.get(key))
//					sum++;
//			return sum;
//		}
//		private class OverlappingBracketConstraintsView implements BracketProposerView<T>
//		{
//			public boolean containsBracketConstraint(int left, int right)
//			{
//				return brackets.containsKey(new Pair<Integer, Integer>(left, right));
//			}
//			public int getNumberOfConstraints() 
//			{
//				return brackets.size();
//			}
//			public List<T> getSentence() 
//			{
//				return Collections.unmodifiableList(sentence);
//			}
//			public boolean optimisticIsSpanAllowed(int left, int right) 
//			{
//				for (Pair<Integer, Integer> bracket : brackets.keySet())
//				{
//					int otherLeft = bracket.getFirst(), otherRight = bracket.getSecond();
//					if (isCrossingInterval(left, right, otherLeft, otherRight))
//						return false;
//				}
//				return true;
//			}
//			public int optimisticParsingComplexity() 
//			{
//				return parsingComplexity(true);
//			}
//			public double pessimisticParsingComplexity() 
//			{
//				return parsingComplexity(false);
//			}
//			public double[][] optimisticParsingComplexities() 
//			{
//				return BracketConstraintsUtils.optimisticParsingComplexities(instance());
//			}
//	    private double [][] pessimisticParsingComplexities = null;
//	    public double [][] pessimisticParsingComplexities()
//	    {
//	    	if (pessimisticParsingComplexities != null) 
//	    		return pessimisticParsingComplexities;
//	    	pessimisticParsingComplexities = 
//	    		BracketConstraintsUtils.pessimisticParsingComplexities(sentence.size());
//	      return pessimisticParsingComplexities;
//	    }
//			/**
//			 * @return
//			 */
//			public List<Pair<Integer, Integer>> getConstraints() {
//				List<Pair<Integer, Integer>> result = new ArrayList<Pair<Integer,Integer>>();
//				result.addAll(brackets.keySet());
//				return result;
//			}
//		}
//		public boolean[][] compile() 
//		{
//			boolean [][] spanAllowed = new boolean[sentence.size()][sentence.size() + 1];
//			for (int i = 0; i < sentence.size(); i++)
//				for (int j = 0; j < sentence.size() + 1; j++)
//					if (j > i) spanAllowed[i][j] = true;
//			for (Pair<Integer, Integer> bracket : brackets.keySet())
//			{
//				final int left = bracket.getFirst(), right = bracket.getSecond();
//				boolean isPositive = brackets.get(bracket);
//				if (isPositive)
//				{
//					for (int otherRight = left + 1; otherRight < right; otherRight++)
//						for (int otherLeft = 0; otherLeft < left; otherLeft++)
//							spanAllowed[otherLeft][otherRight] = false;
//					for (int otherRight = right + 1; otherRight <= sentence.size(); otherRight++)
//						for (int otherLeft = left + 1; otherLeft < right; otherLeft++)
//							spanAllowed[otherLeft][otherRight] = false;
//				}
//				else spanAllowed[left][right] = false;
//			}
//			return spanAllowed;
//		}
//		public BracketProposerView<T> getView() 
//		{
//			return view;
//		}
//		public int parsingComplexity(boolean optimistic) 
//		{
//			if (optimistic)
//			{
//				// note: this might be a loose estimate
//				SortedSet<Integer> points = new TreeSet<Integer>();
//				for (Pair<Integer, Integer> bracket : brackets.keySet())
//				{
//					int otherLeft = bracket.getFirst(), otherRight = bracket.getSecond();
//					points.add(otherLeft); points.add(otherRight);
//				}
//				points.add(0); points.add(sentence.size());
//				List<Integer> list = new ArrayList<Integer>(points);
//				int max = Integer.MIN_VALUE;
//				for (int i = 1; i < points.size(); i++)
//				{
//					int cur = list.get(i) - list.get(i - 1);
//					if (cur > max) max = cur;
//				}
//				return max * max * max;
//			}
//			else return sentence.size() * sentence.size() * sentence.size(); // not supported yet (ever)
//		}
//		public boolean removeBracket(int left, int right) 
//		{
//			Pair<Integer, Integer> key = new Pair<Integer, Integer>(left, right);
//			return brackets.remove(key);
//		}
//		public boolean updateBracket(int newLeft, int newRight, boolean newIsPositive) 
//		{
//			Pair<Integer, Integer> key = new Pair<Integer, Integer>(newLeft, newRight);
//			brackets.put(key, newIsPositive);
//			return true;
//		}
//		private OverlappingBracketConstraints(final List<T> sentence) 
//		{
//			this.sentence = sentence;
//		}
//	}
//
//	private static class NonOverlappingBracketConstraints<T> implements BracketConstraints<T>
//	{
//	  private final List<T> sentence;
//	  private int nConstraints = 0;
//	  private final Bracket root;
//	  private final BracketProposerView<T> proposerView = new NonOverlappingBracketProposerView();
//	  public BracketProposerView<T> getView() { return proposerView; }
//	  private NonOverlappingBracketConstraints(List<T> sentence)
//	  {
//	    this.sentence = sentence;
//	    this.root = new Bracket(0, sentence.size(), true, new ArrayList<Bracket>());
//	  }
//	  private BracketConstraints<T> instance() { return this; }
//	  public int nActive() { return root.nActive(); }
//	  public class NonOverlappingBracketProposerView implements BracketConstraints.BracketProposerView<T>
//	  {
//	    public List<T> getSentence() { return Collections.unmodifiableList(sentence); }
//	    /**
//	     * Does not count the sentence-constraint
//	     * @return
//	     */
//	    public int getNumberOfConstraints() { return nConstraints; }
//	    public int optimisticParsingComplexity() { return parsingComplexity(true); }
//
//	    private double [][] pessimisticParsingComplexities = null;
//	    public double [][] pessimisticParsingComplexities()
//	    {
//	    	if (pessimisticParsingComplexities != null) 
//	    		return pessimisticParsingComplexities;
//	    	pessimisticParsingComplexities = 
//	    		BracketConstraintsUtils.pessimisticParsingComplexities(sentence.size());
//	      return pessimisticParsingComplexities;
//	    }
//	    public double pessimisticParsingComplexity()
//	    {
//	    	return Math.pow(sentence.size(), 3);
//	    }
//	    /**
//	     * Check if [left, right) overlap with existing constraints, treating them
//	     * all as if they were positive 
//	     * @param left
//	     * @param right
//	     * @return
//	     */
//	    public boolean optimisticIsSpanAllowed(int left, int right)
//	    {
//	      if (left < 0 || right > sentence.size()) throw new RuntimeException();
//	      return root.isSpanAllowed(true, left, right);
//	    }
//	    /**
//	     * Check whether there is a bracket constraint on [left, right)
//	     * @param left
//	     * @param right
//	     * @return
//	     */
//	    public boolean containsBracketConstraint(int left, int right)
//	    {
//	      return root.containsBracketConstraint(left, right);
//	    }
//			/**
//			 * @return
//			 */
//			public double[][] optimisticParsingComplexities() 
//			{
//				return BracketConstraintsUtils.optimisticParsingComplexities(instance());
//			}
//			/**
//			 * @return
//			 */
//			public List<Pair<Integer, Integer>> getConstraints() {
//				return root.getAllConstraints();
//			}
//	  }
//	  
//	  /**
//	   * If optimistic is true, the sign constraints will be ignored, more 
//	   * precisely all the bracket constraints will be considered positive
//	   * @param optimistic
//	   * @return
//	   */
//	  public int parsingComplexity(boolean optimistic)
//	  {
//	    return root.parsingComplexity(optimistic, 0, sentence.size());
//	  }
//	  
//	  /**
//	   * Remove the bra constraint [left, right), if any
//	   * @param left
//	   * @param right
//	   * @return Whether the provided span indeed corresponded to a bracket
//	   */
//	  public boolean removeBracket(int left, int right)
//	  {
//	    nConstraints--;
//	    return root.removeBracket(left, right);
//	  }
//	  
//	  /**
//	   * The update is successful iff the [newLeft, newRight) does not cross any
//	   * existing stored bracket, regardless of their sign
//	   * 
//	   * @param newLeft
//	   * @param newRight
//	   * @param newIsPositive
//	   * @return was the update successful?
//	   */
//	  public boolean updateBracket(int newLeft, int newRight, boolean newIsPositive)
//	  {
//	    if (!proposerView.containsBracketConstraint(newLeft, newRight)) nConstraints++;
//	    return root.updateBracket(newLeft, newRight, newIsPositive);
//	  }
//	  
//	  /**
//	   * This is used by the sampler to know where to prune.  This uses the signs
//	   * of the bracket constraints
//	   * @return an array s.t. i,j is true iff the span [i, j) is consitent with
//	   * all the (signed) bracket constraints
//	   */
//	  public boolean [][] compile()
//	  {
//	    boolean [][] spanAllowed = new boolean[sentence.size()][sentence.size() + 1];
//	    for (int l = 0; l < sentence.size(); l++)
//	      for (int r = l + 1; r <= sentence.size(); r++)
//	        spanAllowed[l][r] = root.isSpanAllowed(false, l, r);
//	    return spanAllowed;
//	  }
//	  
//	  /**
//	   * 
//	   * Note that the Bracket
//	   * are organized in a tree using the inclusion relation for efficiency. 
//	   * More precisely, the set of brackets b_i that form the children of a 
//	   * bracket b is the set for which for all i, b is the smallest bracket 
//	   * containing b_i. Moreover, the children are arranged from left to right
//	   * 
//	   * @author Alexandre Bouchard
//	   *
//	   */
//	  private static class Bracket
//	  {
//	    private final List<Bracket> children;
//	    private final int leftIncl, rightExcl;
//	    private boolean isPositive = true;
//	    
//	    private Bracket(final int leftIncl, final int rightExcl, boolean isPositive, List<Bracket> children)
//	    {
//	      this.leftIncl = leftIncl;
//	      this.rightExcl = rightExcl;
//	      this.isPositive = isPositive;
//	      this.children = children;
//	    }
//	    private int nActive()
//	    {
//	    	int result = (isPositive ? 1 : 0);
//	    	for (Bracket bra : children)
//	    		result += bra.nActive();
//	    	return result;
//	    }
//	    private List<Pair<Integer, Integer>> getAllConstraints()
//	    {
//	    	List<Pair<Integer, Integer>> result = new ArrayList<Pair<Integer,Integer>>();
//	    	result.add(new Pair<Integer, Integer>(leftIncl, rightExcl));
//	    	for (Bracket bra : children)
//	    		result.addAll(bra.getAllConstraints());
//	    	return result;
//	    }
//	    
//	    /**
//	     * Recursively checks whether a given [left, right) span is allowed 
//	     * (taking the sign into account iff !optimistic)
//	     * @param left
//	     * @param right
//	     * @return
//	     */
//	    private boolean isSpanAllowed(boolean optimistic, int left, int right)
//	    {
//	      if (optimistic)
//	      {
//	        if (left == leftIncl && right == rightExcl) return true;
//	        if (isCrossingInterval(left, right)) return false;
//	      }
//	      else
//	      {
//	        if (left == leftIncl && right == rightExcl)
//	          return isPositive;
//	        if (isPositive && isCrossingInterval(left, right))
//	          return false;
//	      }
//	      for (Bracket child : children)
//	        if (child.contains(left) || child.contains(right))
//	          if (!child.isSpanAllowed(optimistic, left, right))
//	            return false;
//	      return true;
//	    }
//	    
//	    /**
//	     * @see BracketProposerView
//	     */
//	    private boolean containsBracketConstraint(int left, int right)
//	    {
//	      if (left == leftIncl && right == rightExcl) return true;
//	      for (Bracket child : children)
//	        if (child.containsInterval(left, right))
//	          if (child.containsBracketConstraint(left, right))
//	            return true;
//	      return false;
//	    }
//
//	    /**
//	     * @see BracketConstraints
//	     */
//	    private int parsingComplexity(boolean optimistic, int outerLeft, int outerRight)
//	    {
//	      int result = 0;
//	      if (optimistic || isPositive)
//	      {
//	        outerLeft = leftIncl;
//	        outerRight = rightExcl;
//	      }
//	      int outerLength = outerRight - outerLeft;
//	      for (Bracket child : children)
//	      {
//	        result += child.parsingComplexity(optimistic, outerLeft, outerRight);
//	        outerLength = outerLength - (child.rightExcl - child.leftIncl) + 1;
//	      }
//	      if (outerLength == 1) return 1;
//	      return result + (int) Math.pow(outerLength + 1, 3)/6;
//	    }
//	    
//	    /**
//	     * @see BracketConstraints
//	     */
//	    private boolean removeBracket(int left, int right)
//	    {
//	      if (left < leftIncl || right > rightExcl || left >= right) 
//	        throw new RuntimeException();
//	      Iterator<Bracket> iter = children.iterator();
//	      int j = 0;
//	      while (iter.hasNext())
//	      {
//	        Bracket candidate = iter.next();
//	        if (candidate.leftIncl == left && candidate.rightExcl == right)
//	        {
//	          // delete this child
//	          iter.remove();
//	          children.addAll(j, candidate.children);
//	          assert isConsistent();
//	          return true;
//	        }
//	        if (candidate.containsInterval(left, right))
//	        {
//	          // delete inside the child
//	          boolean result = candidate.removeBracket(left, right);
//	          assert isConsistent();
//	          return result;
//	        }
//	        j++;
//	      }
//	      return false;
//	    }
//	    
//	    /**
//	     * @see BracketConstraints
//	     */
//	    private boolean updateBracket(int newLeft, int newRight, boolean newIsPositive)
//	    {
//	      if (newLeft < leftIncl || newRight > rightExcl || newLeft >= newRight) 
//	        throw new RuntimeException();
//	      // check it's not crossing
//	      for (Bracket child : children)
//	        if (child.isCrossingInterval(newLeft, newRight))
//	          return false;
//	      if (newLeft == leftIncl && newRight == rightExcl)
//	      {
//	        this.isPositive = newIsPositive;
//	        return true;
//	      }
//	      // is this new bracket inside one of the children?
//	      for (Bracket child : children)
//	        if (child.containsInterval(newLeft, newRight))
//	          return child.updateBracket(newLeft, newRight, newIsPositive);
//	      // does this new bracket contains some of the children?
//	      Iterator<Bracket> iter = children.iterator();
//	      List<Bracket> newChildren = new ArrayList<Bracket>();
//	      while (iter.hasNext())
//	      {
//	        Bracket child = iter.next();
//	        if (child.containedInInterval(newLeft, newRight))
//	        {
//	          newChildren.add(child);
//	          iter.remove();
//	        }
//	      }
//	      Bracket newBra = new Bracket(newLeft, newRight, newIsPositive, newChildren);
//	      // insert the new child at the right place
//	      int j = 0;
//	      while (j < children.size() && children.get(j).leftIncl < newLeft) j++;
//	      children.add(j, newBra);
//	      assert isConsistent();
//	      return true;
//	    }
//	    private boolean isConsistent()
//	    {
//	      int lastRightExcl = this.leftIncl;
//	      for (Bracket child : children)
//	      {
//	        if (lastRightExcl > child.leftIncl || !child.isConsistent())
//	          return false;
//	        lastRightExcl = child.rightExcl;
//	      }
//	      if (children.size() > 0 && children.get(children.size() - 1).rightExcl > this.rightExcl)
//	        return false;
//	      return true;
//	    }
//	    private boolean isCrossingInterval(int otherLeft, int otherRight)
//	    {
//	      return BracketConstraintsUtils.isCrossingInterval(leftIncl, rightExcl, otherLeft, otherRight);
//	    }
//	    private boolean containsInterval(int newLeft, int newRight)
//	    {
//	      return BracketConstraintsUtils.containsInterval(leftIncl, rightExcl, newLeft, newRight);
//	    }
//	    private boolean contains(int point)
//	    {
//	      return BracketConstraintsUtils.contains(leftIncl, rightExcl, point);
//	    }
//	    private boolean containedInInterval(int newLeft, int newRight)
//	    {
//	      return BracketConstraintsUtils.containedInInterval(leftIncl, rightExcl, newLeft, newRight);
//	    }
//	    /**
//	     * Returns true iff the following fields are equals() (or both null):
//	     * <ul>
//	     *  <li>children</li>
//	     * </ul>
//	     * and also the following primitive fields are ==
//	     * <ul>
//	     *  <li>leftIncl</li>
//	     *  <li>rightExcl</li>
//	     *	  <li>isPositive</li>
//	     * </ul> 
//	     * 
//	     * @param o
//	     * @return
//	     */
//	    @Override
//	    public boolean equals(Object o)
//	    {
//	      if (this == o)
//	        return true; // for performance
//	      if (o == null)
//	        return false;
//	      if (!(o instanceof Bracket))
//	        return false;
//	      final Bracket o_cast = (Bracket) o;
//	      // check children
//	      if (children != null ? !children.equals(o_cast.children)
//	          : o_cast.children != null)
//	        return false;
//	      // check leftIncl
//	      if (leftIncl != o_cast.leftIncl)
//	        return false;
//	      // check rightExcl
//	      if (rightExcl != o_cast.rightExcl)
//	        return false;
//	      // check isPositive
//	      if (isPositive != o_cast.isPositive)
//	        return false;
//	      return true;
//	    }
//
//	    /**
//	     * Returns a hashcode based on the following fields' hashcodes:
//	     * <ul>
//	     *  <li>children</li>
//	     * </ul> 
//	     * and also the following primitive values
//	     * <ul>
//	     *  <li>leftIncl</li>
//	     *  <li>rightExcl</li>
//	     *	  <li>isPositive</li>
//	     * </ul>
//	     * @return
//	     */
//	    @Override
//	    public int hashCode()
//	    {
//	      // start with the children
//	      int hashCode = (children != null ? children.hashCode() : 0);
//	      // include the leftIncl
//	      hashCode = 29 * hashCode + leftIncl;
//	      // include the rightExcl
//	      hashCode = 29 * hashCode + rightExcl;
//	      // include the isPositive
//	      hashCode = 29 * hashCode + (isPositive ? 1 : 0);
//	      return hashCode;
//	    }
//	  }
//	}
//}
