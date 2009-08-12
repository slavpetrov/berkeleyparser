//package edu.berkeley.nlp.auxv;
//
//import static fig.basic.LogInfo.end_track;
//import static fig.basic.LogInfo.logs;
//import static fig.basic.LogInfo.track;
//
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.LinkedList;
//import java.util.List;
//import java.util.Random;
//
//import nuts.math.Avger;
//import nuts.tui.Table;
//import nuts.tui.Table.Populator;
//import edu.berkeley.nlp.auxv.Grammar.GrammarIndex;
//import edu.berkeley.nlp.auxv.bractrl.BracketNumberController;
//import edu.berkeley.nlp.auxv.bractrl.BracketProposer;
//import edu.berkeley.nlp.syntax.StateSet;
//import edu.berkeley.nlp.syntax.Tree;
//import edu.berkeley.nlp.math.SloppyMath;
//import fig.basic.Pair;
//import fig.basic.StopWatch;
//
//public class AuxVarInsideOutside implements InsideOutside
//{
//  // input
//  private List<String> sent;
//  private SuffStat suffStat;
//  private NodeSuffStat nodeSuffStat;
//  // grammar
//  //private final Grammar g;
//  // internal use
//  private List<Pair<Integer, Integer>> constraintsList;
//  private BracketConstraints<String> constraints;
//  // propose gibbs kernels
//  private BracketProposer bracketProposer;
//  private BracketNumberController braNumberController;
//  // support for sampling
//  private IterManager iterSpecs;
//  private Random rand;
//  private ConstrainedInsideOutside eio;
//  private Monitor monitor = new DevNullMonitor();
//  // counters for posterior statistics
//  private double [][] bracketPosteriorsSums;
//  private boolean allowOverlapsOrRepetitionsForNewBrackets;
//  
//  private Choke choke;
//  
//  public static class Choke
//  {
//  	private final double minActiveRatio;
//    private double nCheat = 0.0;
//    private double nQueries = 0.0;
//    public Choke(double minActiveRatio)
//    {
//    	if (minActiveRatio < 0 || minActiveRatio > 1) throw new RuntimeException();
//    	this.minActiveRatio = minActiveRatio;
//    }
//    public Choke() { minActiveRatio = 0.0; }
//    public boolean cheat(BracketConstraints<String> constraints, boolean isNewDrawPositive)
//    {
//    	nQueries++;
//    	int totalBraN = constraints.getView().getNumberOfConstraints() + 1;
//    	int nActive = constraints.nActive() + (isNewDrawPositive ? 1 : 0);
//    	double cRatio = ((double) nActive) / ((double) totalBraN);
//    	boolean cheat = (cRatio < minActiveRatio);
//    	if (cheat) nCheat++;
//    	return cheat;
//    }
//    public double cheatRatio() { return nCheat/nQueries; }
//  }
//
//  public AuxVarInsideOutside(ConstrainedInsideOutside eio, BracketProposer braProposer,
//      BracketNumberController braNumCont,
//      IterManager iterSpecs, Random rand) 
//  { 
//    this.eio = eio;
//    this.rand = rand;
//    this.iterSpecs = iterSpecs;
//    this.bracketProposer = braProposer;
//    this.braNumberController = braNumCont;
//    this.allowOverlapsOrRepetitionsForNewBrackets = braProposer.allowsOverlappingBrackets();
//  }
//  
//  public void setMonitor(Monitor monitor)
//  {
//    this.monitor = monitor;
//  }
//  
//  public boolean compute(List<String> sentence, SuffStat finalSuffStats)
//  {
//  	return compute(sentence, finalSuffStats, null, new Choke());
//  }
//
//  /** 
//   * Execute the inside outside algorithm
//   * 
//   * Additionally, keep in track extra stats for the posterior probability of a 
//   * particular derivation (stored in treeOfInterest)
//   * 
//   * @param sentence
//   * @param suffStat
//   */
//  public boolean compute(List<String> sentence, SuffStat finalSuffStats, Tree<StateSet> treeOfInterest, Choke choke)
//  {
//  	if (!yield(treeOfInterest).equals(sentence)) throw new RuntimeException();
//  	this.choke = choke;
//    this.sent = sentence;
//    monitor.beginCompute();
//    initStatistics(finalSuffStats, treeOfInterest);
//    initializeBrackets();
//    boolean success = false;
//    for (iterSpecs.reset(); iterSpecs.hasNext(); iterSpecs.next())
//      if (sample()) success = true;
//    monitor.endCompute(success);
//    // normalize the sufficient statistics by the number of samples added to it, 
//    this.suffStat.times(1.0 / ((double) iterSpecs.numberOfEffectiveIterations()));
//    //then add to the final suff stats
//    finalSuffStats.add(this.suffStat);
//    return success;
//  }
//  
//  private boolean sample()
//  {
//    monitor.beginSampleRound();
//    boolean result = false;
//    // remove some of the brackets if needed
//    for (int numToRem = braNumberController.numberOfBracketsToRemove(rand, constraints.getView());
//          numToRem > 0 && constraintsList.size() > 0; numToRem--) 
//       removeConstraint();
//    boolean [][] compiledBra = constraints.compile();
//    // compute posteriors after setting the new constraints 
//    SuffStat currentSuffStat = suffStat.newInstance();
//    long start = System.currentTimeMillis();
//    result = eio.compute(sent, currentSuffStat, compiledBra);
//    long finish = System.currentTimeMillis();
////    System.out.println(BracketConstraintsUtils.compiledToString(constraints));
//    System.out.println("TIME: " + (finish - start));
//    monitor.computeInsideOutside(currentSuffStat, finish - start);
//    
//    double [][] bracketPosteriors = eio.getBracketPosteriors();
//    // add a new bracket
//    Pair<Integer, Integer> newBra = nextConstraint(allowOverlapsOrRepetitionsForNewBrackets);
//    if (!result) 
//    {
//      monitor.sampleStepFailed(); // i.e. we are outside of the set of positive prob.
//      addConstraint(newBra, false); // don't add more hard constraints in this case
//    }
//    else 
//    {
//      if (!iterSpecs.isBurnIn()) 
//      {
//      	updateStatistics(bracketPosteriors, currentSuffStat);
//      	updateNodeStatistics(eio);
//      }
//      double nextRand = rand.nextDouble();
//      double newBraPosterior = bracketPosteriors[newBra.getFirst()][newBra.getSecond()];
//      monitor.sampleStep(nextRand, newBraPosterior);
//      boolean isNewBraPositive = (nextRand <= newBraPosterior);
//      if (choke.cheat(constraints, isNewBraPositive)) isNewBraPositive = true;
//      addConstraint(newBra, isNewBraPositive);
//    }
//    monitor.endSampleRound();
//    return result;
//  }
//  
//  private void updateNodeStatistics(InsideOutside io)
//  {
//  	if (nodeSuffStat != null) nodeSuffStat.update(io);
//  }
//  
//  private void updateStatistics(double [][] bracketPosteriors, SuffStat currentSuffStat)
//  {
//    suffStat.add(currentSuffStat);
//    for (int left = 0; left < sent.size(); left++)
//      for (int right = left + 1; right < sent.size() + 1; right++)
//        bracketPosteriorsSums[left][right] += bracketPosteriors[left][right];
//  }
//  
//  private void initStatistics(SuffStat factory, Tree<StateSet> treeOfInterest)
//  {
//    this.bracketPosteriorsSums = new double[sent.size()][sent.size() + 1];
//    this.suffStat = factory.newInstance();
//    if (treeOfInterest == null) nodeSuffStat = null;
//    else nodeSuffStat = new NodeSuffStat(treeOfInterest);
//  }
//  
//  private void initializeBrackets()
//  {
//    monitor.beginInitializeBrackets();
//    constraints = 
//    		BracketConstraintsUtils.newInstance(sent, allowOverlapsOrRepetitionsForNewBrackets);
//    // ALSO: make sure this argument is used properly in nextConstraint()
//    constraintsList = new LinkedList<Pair<Integer,Integer>>();
//    int numberOfBrackets = braNumberController.initialNumberOfBrackets(rand, Collections.unmodifiableList(sent));
//    for (int i = 0; i < numberOfBrackets; i++)
//      addConstraint(nextConstraint(false), true);
//    monitor.endInitializeBrackets();
//  }
//  private final int NEXT_CONSTRAINTS_TRIES = 100;
//  private Pair<Integer, Integer> nextConstraint(boolean allowOverlapsOrRepetitions)
//  {
//    Pair<Integer, Integer> proposedBracket = null;
//    for (int i = 0; i < NEXT_CONSTRAINTS_TRIES; i++)
//    {
//    	proposedBracket = bracketProposer.next(rand, constraints.getView()); 
//    	if (allowOverlapsOrRepetitions || !isOverlapOrRepetition(proposedBracket))
//    		break;
//    }
//    // the conditions in the loop seemed problematic
////    do    proposedBracket = bracketProposer.next(rand, constraints.getView()); 
////    while (!constraints.getView().containsBracketConstraint(proposedBracket.getFirst(), proposedBracket.getSecond()) &&
////        !constraints.getView().optimisticIsSpanAllowed(proposedBracket.getFirst(), proposedBracket.getSecond()));
//    monitor.constraintProposed(bracketProposer, proposedBracket);
//    return proposedBracket;
//  }
//  private boolean isOverlapOrRepetition(Pair<Integer, Integer> proposedBracket)
//  {
//  	if (constraints.getView().containsBracketConstraint(proposedBracket.getFirst(), 
//  			proposedBracket.getSecond()))
//  		return true;
//  	if (!constraints.getView().optimisticIsSpanAllowed(proposedBracket.getFirst(), 
//  			proposedBracket.getSecond()))
//  		return true;
//  	return false;
//  }
//  public void addConstraint(Pair<Integer, Integer> cons, boolean sign)
//  {
//    constraints.updateBracket(cons.getFirst(), cons.getSecond(), sign);
//    constraintsList.add(cons);
//    monitor.addConstraint(cons);
//  }
//  public void removeConstraint()
//  {
//    Pair<Integer, Integer> leastPriority = constraintsList.get(0);
//    constraintsList.remove(0);
//    constraints.removeBracket(leastPriority.getFirst(), leastPriority.getSecond());
//    monitor.removeConstraint(leastPriority);
//  }
//  /**
//   * The posterior log pr of the tree of interest provided with
//   * compute(), (if the appropriate version of compute() was called)
//   * @return
//   */
//  public double posteriorDerivationLogPr()
//  {
//  	double result = nodeSuffStat.posteriorDerivationLogPr();
//  	if (SloppyMath.isDangerous(result))
//  		System.out.println("warning: dangerous posterior derivation log pr:" + result);
//  	return result;
//  }
//  private static List<String> yield(Tree<StateSet> tree)
//  {
//  	if (tree.isLeaf()) return Collections.singletonList(tree.getLabel().getWord());
//  	List<String> result = new ArrayList<String>();
//  	for (Tree<StateSet> child : tree.getChildren())
//  		result.addAll(yield(child));
//  	return result;
//  }
//  public int currentSentenceLength() { return sent.size(); }
//  public double [][] getBracketPosteriors()
//  {
//    double [][] result = new double[sent.size()][sent.size() + 1];
//    for (int i = 0; i < sent.size(); i++)
//      for (int j = i + 1; j < sent.size() + 1; j++)
//        result[i][j] = bracketPosteriorsSums[i][j] / (double) iterSpecs.numberOfEffectiveIterations();
//    return result;
//  }
//  
//  public interface Monitor
//  {
//    public void beginInitializeBrackets();
//    public void constraintProposed(BracketProposer braSampler, Pair<Integer, Integer> proposedBracket);
//    public void endCompute(boolean b);
//    public void beginCompute();
//    public void sampleStep(double nextRand, double newBraPosterior);
//    public void sampleStepFailed();
//    public void endSampleRound();
//    public void computeInsideOutside(SuffStat currentSuffStat, long time);
//    public void beginSampleRound();
//    public void removeConstraint(Pair<Integer, Integer> cons);
//    public void addConstraint(Pair<Integer, Integer> cons);
//    public void endInitializeBrackets();
//  }
//  
//  public static class DevNullMonitor implements Monitor
//  {
//    public void constraintProposed(BracketProposer braSampler, Pair<Integer, Integer> proposedBracket) {}
//    public void endCompute(boolean b) {}
//    public void beginCompute() {}
//    public void sampleStep(double nextRand, double newBraPosterior) {}
//    public void sampleStepFailed() {}
//    public void addConstraint(Pair<Integer, Integer> cons) {}
//    public void beginInitializeBrackets() {}
//    public void beginSampleRound() {}
//    public void computeInsideOutside(SuffStat currentSuffStat, long time) {}
//    public void endInitializeBrackets() {}
//    public void endSampleRound() {}
//    public void removeConstraint(Pair<Integer, Integer> cons) {}
//  }
//  
//  public static class BottleNeckTimerMonitor implements Monitor
//  {
//  	public long totalTimeCallingExact = 0;
//  	public long maxTimeCallingExact = 0;
//  	public long minTimeCallingExact = Long.MAX_VALUE;
//
//		/**
//		 * @param cons
//		 */
//		public void addConstraint(Pair<Integer, Integer> cons) {
//			// TODO Auto-generated method stub
//			
//		}
//
//		/**
//		 * 
//		 */
//		public void beginCompute() {
//			// TODO Auto-generated method stub
//			
//		}
//
//		/**
//		 * 
//		 */
//		public void beginInitializeBrackets() {
//			// TODO Auto-generated method stub
//			
//		}
//
//		/**
//		 * 
//		 */
//		public void beginSampleRound() {
//			// TODO Auto-generated method stub
//			
//		}
//
//		/**
//		 * @param currentSuffStat
//		 * @param time
//		 */
//		public void computeInsideOutside(SuffStat currentSuffStat, long time) {
//			totalTimeCallingExact += time;
//			if (time > maxTimeCallingExact) maxTimeCallingExact = time;
//			if (time < minTimeCallingExact) minTimeCallingExact = time;
//		}
//
//		/**
//		 * @param braSampler
//		 * @param proposedBracket
//		 */
//		public void constraintProposed(BracketProposer braSampler, Pair<Integer, Integer> proposedBracket) {
//			// TODO Auto-generated method stub
//			
//		}
//
//		/**
//		 * @param b
//		 */
//		public void endCompute(boolean b) {
//			// TODO Auto-generated method stub
//			
//		}
//
//		/**
//		 * 
//		 */
//		public void endInitializeBrackets() {
//			// TODO Auto-generated method stub
//			
//		}
//
//		/**
//		 * 
//		 */
//		public void endSampleRound() {
//			// TODO Auto-generated method stub
//			
//		}
//
//		/**
//		 * @param cons
//		 */
//		public void removeConstraint(Pair<Integer, Integer> cons) {
//			// TODO Auto-generated method stub
//			
//		}
//
//		/**
//		 * @param nextRand
//		 * @param newBraPosterior
//		 */
//		public void sampleStep(double nextRand, double newBraPosterior) {
//			// TODO Auto-generated method stub
//			
//		}
//
//		/**
//		 * 
//		 */
//		public void sampleStepFailed() {
//			// TODO Auto-generated method stub
//			
//		}
//  	
//  }
//  
//  public class PrintMonitor implements Monitor
//  {
//    private Avger acceptance = new Avger();
//    private final StopWatch watch = new StopWatch();
//    //private final Grammar realGrammar;
//    private final HTMLRenderer renderer;
//    private final double cutOff = 0.01;
//    public PrintMonitor(/*Grammar realGram, */HTMLRenderer htmlr) 
//    {
//      watch.start(); 
//      //realGrammar = realGram;
//      renderer = htmlr;
//    }
//    private void println(String string) 
//    { 
//      logs(string); 
//      renderer.addItem(string);
//    }
//    public void beginCompute() 
//    { 
//      track("AuxVarInsideOutside.computer()",true);
//      println(bracketProposer.toString());
//    }
//    public void endCompute(boolean b)
//    {
//      end_track();
////      if (suffStat instanceof NaiveSuffStat)
////      	renderer.addItem(grammarComparisons(realGrammar, ((NaiveSuffStat) suffStat).mlGrammar()), "Final grammar");
//      renderer.addItem("" + acceptance.avg(), "Acceptance ratio");
//    }
//    private Table grammarComparisons(final Grammar trueGrammar, final Grammar approxGrammar)
//    {
//      return new Table(new Populator() 
//      {
//        @Override public void populate() 
//        {
//          set(0, 0, "Rule");
//          set(0, 1, "Exact");
//          set(0, 2, "Approx");
//          set(0, 3, "Delta");
//        	if (!(suffStat instanceof NaiveSuffStat)) return;
//        	NaiveSuffStat suffStatCast = (NaiveSuffStat) suffStat;
//        	GrammarIndex index = suffStatCast.getGrammarIndex();
//        	if (index.nNonTerm() != trueGrammar.getIndex().nNonTerm())
//            throw new RuntimeException();
//        	int nNonTerm = index.nNonTerm();
//          
//          int row = 1;
//          for (int lhs = 0; lhs < nNonTerm; lhs++)
//            for (int rhs1 = 0; rhs1 < nNonTerm; rhs1++)
//              for (int rhs2 = 0; rhs2 < nNonTerm; rhs2++)
//              {
//                double trueValue = trueGrammar.binPr(lhs, rhs1, rhs2);
//                double approxValue = approxGrammar.binPr(lhs, rhs1, rhs2);
//                if (trueValue > cutOff || approxValue > cutOff)
//                {
//                  set(row, 0, index.toString(lhs, rhs1, rhs2));
//                  set(row, 1, trueValue);
//                  set(row, 2, approxValue);
//                  set(row, 3, Math.abs(trueValue - approxValue));
//                  row++;
//                }
//              }
//        }
//      });
//    }
//    public void sampleStep(double nextRand, double newBraPosterior)
//    {
//      acceptance.put((nextRand <= newBraPosterior));
//      renderer.addItem("New bracket " + (nextRand <= newBraPosterior ? "accepted" : "rejected") +
//          " (random:" + nextRand + ",post:" + newBraPosterior + ")");
//    }
//    public void sampleStepFailed()
//    {
//      acceptance.put(false);
//      renderer.addItem("Sample step failed (jumped to zero prob)");
//    }
//    public void addConstraint(Pair<Integer, Integer> cons) 
//    {
//      renderer.addItem(BracketConstraintsUtils.toTable(constraints),
//          "Constraint added: " + Utils.printSpan(cons) + " (" + parsingComplexities() + ")");
//    }
//    public void removeConstraint(Pair<Integer, Integer> cons) 
//    {
//      renderer.addItem(BracketConstraintsUtils.toTable(constraints), 
//          "Constraint removed: " + Utils.printSpan(cons));
//    }
//    public void beginInitializeBrackets() 
//    {
//      renderer.indent("Initializing brackets...");
//    }
//    public void endInitializeBrackets() { renderer.unIndent(); }
//    public void beginSampleRound() 
//    {
//      renderer.indent("Starting sample rount " 
//          + iterSpecs.cIter() + "/" + iterSpecs.nIters);
//    }
//    private long last = 0;
//    public void endSampleRound() 
//    { 
//      watch.accumStop();
//      long delta = watch.ms - last;
//      last = watch.ms;
////      if (!iterSpecs.isBurnIn() && suffStat instanceof NaiveSuffStat)
////        renderer.addItem(grammarComparisons(realGrammar, ((NaiveSuffStat) suffStat).mlGrammar()), "Current grammar from sufficient statistics");
//      String line;
//      line = "Ellapsed time: " + watch.ms + "ms (delta:" + delta + "ms)";
////      if (suffStat instanceof NaiveSuffStat)
////      	line += (!iterSpecs.isBurnIn() ?
////          "; precision: " + Utils.summary(Grammar.nonTermHells(realGrammar, ((NaiveSuffStat) suffStat).mlGrammar())) : "");
//      println(line);
//      renderer.unIndent(); 
//      watch.start();
//    }
//    public void computeInsideOutside(SuffStat currentSuffStat, long time) 
//    {
//      watch.accumStop();
//      renderer.addItem("Inside outside computed in " + time + " ms");
//      renderer.addItem("Ratio actual/realistic: " + time / constraints.parsingComplexity(false));
//      renderer.addItem(BracketConstraintsUtils.compiledToTable(constraints), "Constraints on the spans (" + parsingComplexities() + "):");
//      renderer.addItem("Effective parsing complexity:" + constraints.parsingComplexity(false));
//      watch.start();
//    }
//    public void constraintProposed(BracketProposer braSampler, Pair<Integer, Integer> proposedBracket)
//    {
////      watch.accumStop();
////      if (braSampler instanceof InformedBracketProposer)
////      {
////        InformedBracketProposer ibp = (InformedBracketProposer) braSampler;
////        addStat(BracketConstraints.spanScoresTable(sent, ibp.distribution(constraints.proposerView)),
////            "Bracket constraint proposal distribution");
////      }
////      watch.start();
//    }
//    private String parsingComplexities()
//    {
//      return "realistic parsing cmpxity: " + constraints.parsingComplexity(false) 
//        + ", optimistic: " + constraints.parsingComplexity(true);
//    }
//  }
//  
//  public static class IterManager
//  {
//    private final int nIters;
//    private final int burnIn;
//    private int cIter;
//    public IterManager(int nBra, double ratioItersToBras, double ratioBurnInToIters)
//    {
//    	this((int) ((double) nBra * ratioItersToBras), 
//    			(int) ((double) nBra * ratioItersToBras * ratioBurnInToIters));
//    }
//    public IterManager(int nIters, int burnIn)
//    {
//    	if (nIters < 1 || burnIn >= nIters || burnIn < 0.0) 
//    		throw new RuntimeException("Invalid number of iterations: " + nIters);
//      this.nIters = nIters;
//      this.burnIn = burnIn;
//    }
//    public IterManager(int nIters) { this(nIters, nIters/4); }
//    public IterManager() { this(10); }
//    public boolean isBurnIn()
//    {
//      return cIter <= burnIn;
//    }
//    public boolean hasNext() { return cIter < nIters; }
//    public void next() 
//    { 
//      cIter++; 
//    }
//    public int cIter() { return cIter; }
//    public int numberOfEffectiveIterations()
//    {
//      if (hasNext()) throw new RuntimeException("Only make sense after iterations are completed");
//      return nIters - burnIn;
//    }
//    public void reset() { cIter = 0; }
//  }
//	public double stateSetPosterior(StateSet parent, StateSet left, StateSet right) {
//		throw new RuntimeException();
//	}
//	public double stateSetPosterior(StateSet parent, StateSet child) {
//		throw new RuntimeException();
//	}
//	public double stateSetPosterior(StateSet parent) {
//		throw new RuntimeException();
//	}
//}
