package edu.berkeley.nlp.HDPPCFG;

import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.math.DoubleArrays;
import edu.berkeley.nlp.math.SloppyMath;
import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.CounterMap;
import edu.berkeley.nlp.util.Numberer;
import edu.berkeley.nlp.util.PriorityQueue;

import fig.basic.*;

class FullState {
	public short state;
	public short substate;
	/** A hack to make getting P(parent|child) easier.*/
	public double score;
	
	public FullState(short state, short substate) {
		this.state = state;
		this.substate = substate;
	}

	/**
	 * @param tagNumberer
	 * @return
	 */
	public String toString(Numberer tagNumberer) {
		String w;
		String name = tagNumberer.object(state)+"-"+substate;
		w = "<a href="+GrammarStatistics.reflabel("productions",name)+">"+name+"</a> ";
		return w;
	}
	
	/**
	 * @param tagNumberer
	 * @return
	 */
	public String toString(Numberer tagNumberer, String childFullName) {
		String w;
		String name = tagNumberer.object(state)+"-"+substate;
		w = "<a href="+GrammarStatistics.reflabel("parentrules",childFullName+"*under*"+name)+">"+name+"</a> ";
		return w;
	}
	
	public boolean equals(FullState s) {
		return (state==s.state && substate==s.substate);
	}
}

class SearchState {
	public ArrayList<FullState> produced = new ArrayList<FullState>();
	public FullState danglingState;
	public double score;
	public int insertPosition = 0;
	FullState parent = null;
	public boolean extended = false;
	
	public SearchState (FullState danglingState, double score) {
		this.danglingState = danglingState;
		this.score = score;
	}
	
	public SearchState (FullState danglingState, FullState firstProduction, double score) {
		this.danglingState = danglingState;
		produced.add(firstProduction);
		this.score = score;
	}
	
	public SearchState extend (FullState newProd, FullState newDangling, double scorePenalty, boolean left) {
		SearchState s = new SearchState(newDangling,score + scorePenalty);
		s.produced = new ArrayList<FullState>(produced);
		s.produced.add(insertPosition,newProd);
		s.insertPosition = insertPosition + (left ? 0 : 1);
		return s;
	}
	
	public String toString(Numberer tagNumberer) {
		String w="";
		if (parent!=null) {
			String name = tagNumberer.object(parent.state)+"-"+parent.substate;
			w += "<a href="+GrammarStatistics.reflabel("productions",name)+">"+name+"</a> -&gt; ";
		}
			
		for (FullState s : produced) {
			String name = tagNumberer.object(s.state)+"-"+s.substate;
			w += "<a href="+GrammarStatistics.reflabel("productions",name)+">"+name+"</a> ";
		}
		return w;
	}

	/**
	 * @param rs
	 * @param ps
	 * @param rscore
	 * @param b
	 * @return
	 */
	public SearchState extendUp(FullState cs, FullState ps, double rscore, boolean thisChildOnLeft) {
		SearchState s = new SearchState(ps,score + rscore);
		s.produced = new ArrayList<FullState>(produced);
		if (cs!=null) {
			if (thisChildOnLeft)
				s.produced.add(0,cs);
			else
				s.produced.add(produced.size(),cs);
		}
		s.extended = true;
		return s;
	}
}


public class GrammarStatistics {
	private static int topN = 10;
	
	public GrammarStatistics (Grammar grammar, Numberer tagNumberer, int nScores) {
		this.grammar = grammar;
		this.tagNumberer = tagNumberer;
		this.nScores = nScores;
	}
	
	public Grammar grammar;
	public Numberer tagNumberer;
	public int nScores;
	
	/** Find the best nScores productions by doing breadth-first search.
	 * 
	 * @param p
	 * @param nScores
	 * @return
	 */
	PriorityQueue<SearchState> getTopProductions(FullState p) {
		PriorityQueue<SearchState> results = new PriorityQueue<SearchState>(nScores+1);
		PriorityQueue<SearchState> unExpanded = new PriorityQueue<SearchState>();
		
		unExpanded.add(new SearchState(p,0),0);
		while ( unExpanded.size()!=0 && (results.size()<nScores || unExpanded.peek().score > -results.peek().score) ) {
//			System.out.println(p.state + " " +unExpanded.size()+" "+results.size());
			if (unExpanded.size()>300000) break;
			//expand best-looking SearchState so far
			SearchState state = unExpanded.next();
			//accept complete productions
			if (state.danglingState==null || (state.produced.size()!=0 && !continues(state.danglingState.state))) {
				if (state.danglingState!=null)
					state = state.extend(state.danglingState,null,0,false);
				results.add(state,-state.score);
				if (results.size()>nScores)
					results.next();
			}
			//try to complete partial productions
			else {
				for (UnaryRule rule: grammar.getUnaryRulesByParent(state.danglingState.state)) {
					double[][] scores = rule.getScores2();
					for (short cSubState = 0; cSubState < grammar.numSubStates[rule.getChildState()]; cSubState++) {
						if (scores[cSubState]==null) continue;
						double rscore = scores[cSubState][state.danglingState.substate];
						FullState s = new FullState(rule.getChildState(),cSubState);
						SearchState newState = state.extend(s,null,rscore,false);
						unExpanded.add(newState,newState.score);
					}
				}
				for (BinaryRule rule : grammar.splitRulesWithP(state.danglingState.state)) {// getBinaryRulesByParent(state.danglingState.state)) {
					double[][][] scores = rule.getScores2();
					for (short lSubState = 0; lSubState < grammar.numSubStates[rule.getLeftChildState()]; lSubState++) {
						FullState ls = new FullState(rule.getLeftChildState(),lSubState);
						for (short rSubState = 0; rSubState < grammar.numSubStates[rule.getRightChildState()]; rSubState++) {
							if (scores[lSubState][rSubState]==null) continue;
							FullState rs = new FullState(rule.getRightChildState(),rSubState);
							SearchState newState;
							double rscore = scores[lSubState][rSubState][state.danglingState.substate];
							if (continues(ls.state)) {
								newState = state.extend(rs,ls,rscore,true);
							} else {
								newState = state.extend(ls,rs,rscore,false);
							}
							unExpanded.add(newState,newState.score);
						}
					}
				}
			}
		}
		return results;
	}
	
	/** Find the best nScores productions by doing breadth-first search.
	 * 
	 * @param p
	 * @param nScores
	 * @return
	 */
	PriorityQueue<SearchState> getTopParentRuleProductions(FullState c,
			double[] probState, double[][] probSubGivenState) {
		PriorityQueue<SearchState> results = new PriorityQueue<SearchState>(nScores+1);
		PriorityQueue<SearchState> unExpanded = new PriorityQueue<SearchState>();
		
		double score = -(probState[c.state]+probSubGivenState[c.state][c.substate]);
		unExpanded.add(new SearchState(c,c,score),-score);
		int maxSize = 10000;
		while (unExpanded.size() != 0
				&& unExpanded.size() < maxSize
				&& (results.size() < nScores || unExpanded.peek().score > -results
						.peek().score)) {
			//expand best-looking SearchState so far
			SearchState state = unExpanded.next();
			//accept complete productions
			if (state.danglingState==null || (state.extended && !continues(state.danglingState.state))) {
				if (state.danglingState!=null)
					state.parent = state.danglingState;
				state.score += probState[state.parent.state]
						+ probSubGivenState[state.parent.state][state.parent.substate]; 
				results.add(state,-state.score);
				if (results.size()>nScores)
					results.next();
			}
			//try to complete partial productions
			else {
				for (UnaryRule rule: grammar.getUnaryRulesByChild(state.danglingState.state)) {
					double[][] scores = rule.getScores2();
					if (scores[state.danglingState.substate]==null) continue;
					for (short pSubState = 0; pSubState < grammar.numSubStates[rule.getParentState()]; pSubState++) {
						double rscore = scores[state.danglingState.substate][pSubState];
						FullState s = new FullState(rule.getParentState(),pSubState);
						SearchState newState = state.extendUp(null,s,rscore,false);
						unExpanded.add(newState,newState.score);
					}
				}
				for (BinaryRule rule : grammar.getBinaryRulesByLeftChild(state.danglingState.state)) {
					double[][][] scores = rule.getScores2();
					for (short pSubState = 0; pSubState < grammar.numSubStates[rule.getParentState()]; pSubState++) {
						FullState ps = new FullState(rule.getParentState(),pSubState);
						for (short rSubState = 0; rSubState < grammar.numSubStates[rule.getRightChildState()]; rSubState++) {
							if (scores[state.danglingState.substate][rSubState]==null) continue;
							FullState rs = new FullState(rule.getRightChildState(),rSubState);
							SearchState newState;
							double rscore = scores[state.danglingState.substate][rSubState][pSubState];
							newState = state.extendUp(rs,ps,rscore,false);
							unExpanded.add(newState,newState.score);
						}
					}
				}
				for (BinaryRule rule : grammar.getBinaryRulesByRightChild(state.danglingState.state)) {
					double[][][] scores = rule.getScores2();
					for (short pSubState = 0; pSubState < grammar.numSubStates[rule.getParentState()]; pSubState++) {
						FullState ps = new FullState(rule.getParentState(),pSubState);
						for (short lSubState = 0; lSubState < grammar.numSubStates[rule.getLeftChildState()]; lSubState++) {
							if (scores[lSubState][state.danglingState.substate]==null) continue;
							FullState rs = new FullState(rule.getLeftChildState(),lSubState);
							SearchState newState;
							double rscore = scores[lSubState][state.danglingState.substate][pSubState];
							newState = state.extendUp(rs,ps,rscore,true);
							unExpanded.add(newState,newState.score);
						}
					}
				}
			}
		}
		return results;
	}
	
	public boolean continues(short state) {
		return ((String)tagNumberer.object(state)).charAt(0)=='@';
	}
	
	public static String pad(String s, int width, char c) {
		StringBuffer sb = new StringBuffer(s);
		for (int i=s.length(); i<width; i++)
			sb.append(c);
		return sb.toString();
	}
	
  static NumberFormat f = NumberFormat.getInstance();

  @Option(required=true) public static String inputGrammar;
  @Option(required=true) public static String wsjPath;
  @Option public static double threshold;
  @Option public static String outputGrammar;
  @Option(required=true) public static String outputHTML;

  static PrintWriter out;

	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
    new OptionsParser(
      "main", GrammarStatistics.class,
      "corpus", Corpus.class
    ).doParseHard(args);

	  f.setMaximumFractionDigits(3);

    out = IOUtils.openOutHard(outputHTML);

	  out.println("<html><body>");
	  out.println("<h1>Links</h1><ul>");
	  out.println("<li><a href=\"#lexicon\">Lexicon</a></li>");
	  out.println("<li><a href=\"#grammar\">Grammar</a></li>");
	  out.println("<li><a href=\"#trunks\">Trunks</a></li>");
	  out.println("<li><a href=\"#parents\">Parents</a></li>");
	  out.println("<li><a href=\"#parentrules\">Parent Rules</a></li>");
	  out.println("</ul>");
		
	  out.println("<!--");
		String inFileName = inputGrammar;
		String path = wsjPath;
		String outName = outputGrammar;
		double thresh = threshold;

		System.out.println("Loading grammar from " + inFileName + ".");

		boolean columnOutput = true;
		if (args.length==3)
			columnOutput = false; 

		ParserData pData = ParserData.Load(inFileName);
		if (pData == null) {
			System.out.println("Failed to load grammar from file" + inFileName + ".");
			System.exit(1);
		}
		
		Grammar grammar = pData.getGrammar();
		SimpleLexicon lexicon = (SimpleLexicon)pData.getLexicon();
		Numberer.setNumberers(pData.getNumbs());
		Numberer tagNumberer = Numberer.getGlobalNumberer("tags");
		grammar.splitRules();
		if (thresh>0){
			grammar.removeUnlikelyRules(thresh,1.0);
			lexicon.removeUnlikelyTags(thresh);
		}
    CounterMap<Integer, Integer> symbolCounter = tallyParentCounts(grammar);

		if (outName!=null){
			pData.Save(outName+".gr");
			System.out.println("Writing grammar to file grammar.data...");
			Writer output = null;
			try {
				output = new BufferedWriter(new FileWriter(outName+".grammar"));
				//output.write(grammar.toString());
				grammar.writeData(output);
				if (output != null)	output.close();
				output = new BufferedWriter(new FileWriter(outName+".lexicon"));
				output.write(lexicon.toString());
				if (output != null)	output.close();
			} catch (IOException ex) { ex.printStackTrace();}
		}
		
    Corpus corpus = null;
   	Corpus.numTrainSections = 1;
   	corpus = new Corpus(path,1,1.0,false,false);
    //int nTrees = corpus.getTrainTrees().size();
    //binarize trees
    List<Tree<String>> trainTrees = Corpus.binarizeAndFilterTrees(corpus
				.getTrainTrees(), true, pData.v_markov,
				pData.h_markov, 10000, pData.bin, false,
				false, false,false);
    StateSetTreeList trainStateSetTrees = new StateSetTreeList(trainTrees, grammar.numSubStates, false, tagNumberer,false);
    
    double[][] substateCounts = GrammarMerger.computeMergeWeights(grammar, lexicon,trainStateSetTrees);
    // normalize the counts
    for (int state=0; state<substateCounts.length; state++){
    	double total = ArrayUtil.sum(substateCounts[state]);
    	if (total==0) total = 1;
    	substateCounts[state] = DoubleArrays.multiply(substateCounts[state], 1.0/total);
    }
		
		//put grammar and lexicon in logarithm mode so that we can
		//use our old code below
//		pData = ParserData.Load(inFileName);
//		if (pData == null) {
//			System.out.println("Failed to load grammar from file" + inFileName + ".");
//			System.exit(1);
//		}
//		grammar = pData.getGrammar();
//		lexicon = pData.getLexicon();
		grammar.logarithmMode();
//		lexicon.logarithmMode();
//
//		computeAndPrintCounts(grammar);

		
		//reload the grammar and lexicon because the ones we have now are in
		//logarithm mode, and we can't do inside/outside scores like that
//		ParserData pDataNoLog = ParserData.Load(inFileName);
//		if (pDataNoLog == null) {
//			System.out.println("Failed to load grammar from file" + inFileName + ".");
//			System.exit(1);
//		}		
//		Grammar nonLogGrammar = pDataNoLog.getGrammar();
//		LexiconInterface nonLogLexicon = pDataNoLog.getLexicon();
//		ArrayParser parser = new ArrayParser(nonLogGrammar,nonLogLexicon);
		    
//		Grammar nonLogGrammar = pData.getGrammar();
//		SimpleLexicon nonLogLexicon = (SimpleLexicon)pData.getLexicon();

//		computeAndPrintCounts(grammar);
		out.println("-->");
		
		
    int padding = 3;
		topN = 30;
		printLexiconStatistics(lexicon, tagNumberer,grammar.isGrammarTag,substateCounts);
		GrammarStatistics gs = new GrammarStatistics(grammar,tagNumberer, topN);

		// determine which tags need to be examined.
		// Continuation tags and lexical tags are excluded
		Set<Short> noContinueTags = new HashSet<Short>();
		Set<Short> continueTags = new HashSet<Short>();
		for (short i=0; i<tagNumberer.total(); i++) {
			if (!grammar.isGrammarTag[i]) continue;
			if (!gs.continues(i))
				noContinueTags.add(i);
			else
				continueTags.add(i);
		}
//		noContinueTags.removeAll(lexicon.getAllTags());
//		continueTags.removeAll(lexicon.getAllTags());		
   
		printGrammarStatistics(columnOutput, pData, tagNumberer, topN, gs, noContinueTags,substateCounts, symbolCounter);
		
//		printTrunkStatistics(columnOutput, tagNumberer, padding, topN, gs, continueTags);
	
//		System.out.println("<!--");
//    Corpus corpus = new Corpus(wsjLoc,1,1.0,false,false);
//    int nTrees = corpus.getTrainTrees().size();
//    //binarize trees
//    List<Tree<String>> trainTrees = Corpus.binarizeAndFilterTrees(corpus
//				.getTrainTrees(), true, pData.getV_markov(), pData.getH_markov(),
//				10000, pData.getBinarization(), false, false,deleteLabels,false);
//    System.out.println("-->");
    
//    Set<Short> allRealTags = new HashSet<Short>(noContinueTags);
////    allRealTags.addAll(lexicon.getAllTags());
//
//    double[] probState = new double[grammar.numStates];
//    double[][] probSubGivenState = new double[grammar.numStates][];
//    for (int state=0; state<grammar.numStates; state++) {
//    	probSubGivenState[state] = new double[grammar.numSubStates[state]];
//    }
////    StateSetTreeList trainStateSetTrees = new StateSetTreeList(trainTrees,
////				grammar.numSubStates, false, tagNumberer, false);
////    for (Tree<StateSet> tree : trainStateSetTrees) {
//////  			System.out.println("adding probs for tree "+nTree+" / "+trainStateSetTrees.size());
////    	parser.doInsideOutsideScores(tree,false,true);
////    	tallyProbState(tree,probState,allRealTags);
////    	tallyProbSubState(tree,probSubGivenState,allRealTags);
////    }
//    for (int state=0; state<grammar.numStates; state++) {
//    	double sum = 0;
//    	for (int substate=0; substate<grammar.numSubStates[state]; substate++) {
//    		sum += probSubGivenState[state][substate];
//    	}
//    	for (int substate=0; substate<grammar.numSubStates[state]; substate++) {
//    		probSubGivenState[state][substate] = Math.log(probSubGivenState[state][substate] / sum);
//    	}
//    }
//    double sumState = 0;
//    for (int state=0; state<grammar.numStates; state++) {
//    	sumState += probState[state];
//    }
//    for (int state=0; state<grammar.numStates; state++) {
//    	probState[state] = Math.log(probState[state] / sumState);
//    }

//		printParentRuleStatistics(columnOutput,pData,tagNumberer,topN,gs,allRealTags,probState,probSubGivenState);

//		printParentStatistics(columnOutput, grammar, tagNumberer, nonLogGrammar, nonLogLexicon, topN, gs, trainTrees, parser);
		
		out.println("</body></html>");
	}

	private static void tallyProbSubState(Tree<StateSet> tree,
			double[][] probSubGivenState, Set<Short> noContinueTags) {
		tallyProbSubStateHelper(tree,tree.getLabel().getIScore(0),
				probSubGivenState,noContinueTags);
	}

	
	/**
	 * @param tree
	 * @param probSubGivenState
	 */
	private static void tallyProbSubStateHelper(Tree<StateSet> tree,
			double treeProb, double[][] probSubGivenState,
			Set<Short> tags) {
		if (tree.isLeaf())
			return;
		StateSet label = tree.getLabel();
		short state = label.getState();
		if (tags.contains(state)) {
			double[] iScores = label.getIScores();
			double[] oScores = label.getOScores();
			double[] scores = new double[iScores.length];
			double sum = 0;
			for (int substate=0; substate<iScores.length; substate++) {
				scores[substate] = (iScores[substate] / treeProb) * oScores[substate];
				sum += scores[substate];
			}
			for (int substate=0; substate<iScores.length; substate++) {
				scores[substate] /= sum;
				probSubGivenState[state][substate] += scores[substate];
			}
		}
		for (Tree<StateSet> child : tree.getChildren())
			tallyProbSubStateHelper(child,treeProb,probSubGivenState,tags);
	}

	/**
	 * Count occurrences of each state.  Ignore states that start with "@".
	 * 
	 * @param tree
	 * @param probState
	 */
	private static void tallyProbState(Tree<StateSet> tree, double[] probState, Set<Short> tags) {
		if (tree.isLeaf())
			return;
		short state = tree.getLabel().getState();
		if (tags.contains(state))
			probState[state] += 1;
		for (Tree<StateSet> child : tree.getChildren())
			tallyProbState(child,probState,tags);
	}

	/**
	 * @param columnOutput
	 * @param grammar
	 * @param tagNumberer
	 * @param nonLogGrammar
	 * @param nonLogLexicon
	 * @param topN
	 * @param gs
	 * @param trainTrees
	 */
	private static FullState[][] printParentStatistics(boolean columnOutput, Grammar grammar, Numberer tagNumberer, Grammar nonLogGrammar, Lexicon nonLogLexicon, int topN, GrammarStatistics gs, List<Tree<String>> trainTrees, ArrayParser parser) {
		out.println("<a name=\"parents\"><h1>Parents</h1></a>");
		
		out.println("<!--");
		int nstates = grammar.numStates;
		double[][][][] parentProbs = new double[nstates][nstates][][];
		double[][] normFactors = new double[nstates][];
		FullState[][] parents = new FullState[grammar.numStates][];
		for (int state=0; state<nstates; state++) {
			normFactors[state] = new double[grammar.numSubStates[state]];
		}
    StateSetTreeList trainStateSetTrees = new StateSetTreeList(trainTrees,
				grammar.numSubStates, false, tagNumberer, false);
		/*CorpusStatistics corpusStats = new CorpusStatistics(tagNumberer, trainStateSetTrees);
		corpusStats.countSymbols();
		int counts[] = corpusStats.getSymbolCounts();
		corpusStats.printStateCountArray(tagNumberer,counts);*/
    int nTree = 0;
		System.out.print("Adding probabilities");
		for (Tree<StateSet> tree : trainStateSetTrees) {
//			System.out.println("adding probs for tree "+nTree+" / "+trainStateSetTrees.size());
			parser.doInsideOutsideScores(tree,false,true);
			logarithmModeTree(tree);
			gs.addProbs(tree, grammar, parentProbs, normFactors, tree.getLabel()
					.getIScore(0));
			if (nTree++%1000==0) System.out.print(".");
		}
		System.out.print("done.\n");
		out.println("-->");
		for (short childState=0; childState<nstates; childState++) {
			String[][] outputMatrix = new String[topN+1][grammar.numSubStates[childState]];
			String tagName = (String) tagNumberer.object(childState);
			for (short cS=0; cS<grammar.numSubStates[childState]; cS++) {
				String childFullName = outputMatrix[0][cS] = tagName + "-" + cS;
				PriorityQueue<FullState> results = new PriorityQueue<FullState>(topN+1);
				for (short parentState=0; parentState<nstates; parentState++) {
					double[][] probs = parentProbs[parentState][childState];
					if (probs==null)
						continue;
					double normFactor = normFactors[childState][cS];
					for (short pS=0; pS<grammar.numSubStates[parentState]; pS++) {
						//find max rules
						double score = probs[pS][cS] / normFactor;
						if (!results.isEmpty() && score < -results.getPriority())
							continue;
						FullState state = new FullState(parentState,pS);
						state.score = score;
						results.add(state,-state.score);
						if (results.size()>topN)
							results.next();
					}
				}
				
				ArrayList<FullState> resultsA = new ArrayList<FullState>(topN);
				while (results.size()!=0) {
					resultsA.add(0,results.next());
				}

				parents[childState] = new FullState[resultsA.size()];
				for (short j = 0; j < topN; j++){
					String o="";
					double p=-1;
					if (resultsA.size()>j) {
						parents[childState][j] = resultsA.get(j);
						p = resultsA.get(j).score;
						String w = resultsA.get(j).toString(tagNumberer,childFullName);
						o = f.format(p)+" "+w;
					}
					outputMatrix[j+1][cS] = o;
				}

			}
			printRules("Parent", "parent", columnOutput, outputMatrix);
		}
		return parents;
	}

	/**
	 * @param columnOutput
	 * @param tagNumberer
	 * @param padding
	 * @param topN
	 * @param gs
	 * @param continueTags
	 */
	private static void printTrunkStatistics(boolean columnOutput, Numberer tagNumberer, int padding, int topN, GrammarStatistics gs, Set<Short> continueTags) {
		out.println("<a name=\"trunks\"><h1>Trunks</h1></a>");
		
		//output trunk rule probabilities
		for (short tag : continueTags) {
			String tagS = ((String)tagNumberer.object(tag)).substring(1);
			short parentTag = (short)tagNumberer.number(tagS);
			gs.printTopRules(parentTag, topN, columnOutput, padding);
			gs.printTopRules(tag, topN, columnOutput, padding);
			out.println("");
		}
	}

	
	public static CounterMap<Integer, Integer> tallyParentCounts(Grammar gr) {
		CounterMap<Integer, Integer> symbolCounter = new CounterMap<Integer, Integer>();
//		for (UnaryRule unaryRule : gr.unaryRuleCounter.keySet()) {
//			double[][] unaryCounts = gr.unaryRuleCounter.getCount(unaryRule);
		for (int pState=0; pState < gr.numStates; pState++){
			int nParentSubStates = gr.numSubStates[pState];
			UnaryRule[] unaries = gr.getUnaryRulesByParent(pState).toArray(new UnaryRule[0]);
			for (int r = 0; r < unaries.length; r++) {
				UnaryRule ur = unaries[r];
				double[][] unaryCounts = ur.getScores2();
				if (ur.childState == pState) continue;
				double[] sum = new double[nParentSubStates];
				for (int j = 0; j < unaryCounts.length; j++) {
					if (unaryCounts[j]==null) continue;
					for (int i = 0; i < nParentSubStates; i++) {
						double val = unaryCounts[j][i];
						//if (val>=threshold)	
	//					System.out.println(val);
						sum[i] += val;//Math.exp(val);
					}
				}
				for (int i = 0; i < nParentSubStates; i++) {
					symbolCounter.incrementCount(pState, i, sum[i]);
				}
			}
//		for (BinaryRule binaryRule : gr.binaryRuleCounter.keySet()) {
//		double[][][] binaryCounts = gr.binaryRuleCounter.getCount(binaryRule);
			BinaryRule[] parentRules = gr.splitRulesWithP(pState);
			for (int r = 0; r < parentRules.length; r++) {
				BinaryRule br = parentRules[r];
				double[][][] binaryCounts = br.getScores2();
				double[] sum = new double[nParentSubStates];
				for (int j = 0; j < binaryCounts.length; j++) {
					for (int k = 0; k < binaryCounts[j].length; k++) {
					if (binaryCounts[j][k]==null) continue;
						for (int i = 0; i < nParentSubStates; i++) {
							double val = binaryCounts[j][k][i]; 
							//if (val>=threshold) 
							sum[i] += val;//Math.exp(val); 
						}
					}
				}
				for (int i = 0; i < nParentSubStates; i++) {
					symbolCounter.incrementCount(pState, i, sum[i]);
				}
			}
		}
		return symbolCounter;
	}

	
	/**
	 * @param columnOutput
	 * @param pData
	 * @param tagNumberer
	 * @param topN
	 * @param gs
	 * @param noContinueTags
	 */
	private static void printGrammarStatistics(boolean columnOutput, ParserData pData, Numberer tagNumberer, 
			int topN, GrammarStatistics gs, Set<Short> noContinueTags, double[][] posteriors, 
			CounterMap<Integer, Integer> symbolCounter) {
		out.println("<a name=\"grammar\"><h1>Grammar</h1></a>");
    out.println("<div id=\"grammar\">");
		// print rule probabilities
    pData.gr.splitRules();
    
    Grammar gr = pData.gr;
//    System.out.println(symbolCounter);
    
		for (short curTag : noContinueTags){
			if (!pData.gr.isGrammarTag[curTag]) continue;
			int nSubStates = pData.numSubStatesArray[curTag];
			ArrayList<SearchState>[] results = new ArrayList[nSubStates];
			ArrayList<Double> sortedP = new ArrayList();
			for (Double d : posteriors[curTag]) sortedP.add(d);
			Collections.sort(sortedP);
			double top5Mass = 0;
			for (int i=0; i<5; i++) top5Mass += sortedP.get(sortedP.size()-i-1);
			
			for (short i = 0; i < nSubStates; i++) {
				//do heavy computation
				PriorityQueue<SearchState> pq = gs.getTopProductions(new FullState(curTag,i));
				//convert pq to array
				results[i] = new ArrayList<SearchState>(topN);
				while (pq.size()!=0&&results[i].size()<topN) {
					pq.peek().score = Math.exp(pq.peek().score);
					results[i].add(0,pq.next());
				}
			}
			
			String[][] outputMatrix = new String[topN+1][nSubStates];
			
			String tagName = (String) tagNumberer.object(curTag);
			for (int i = 0; i < nSubStates; i++) {
				String post = f.format(posteriors[curTag][i]);
				String mass = f.format(symbolCounter.getCount((int)curTag, i));
				outputMatrix[0][i] = tagName + "-" + i +" ("+post+") ["+mass+"]";
  		}
			
			for (int j = 0; j < topN; j++){
				for (int i = 0; i < nSubStates; i++) {
					String o="";
					double p=-1;
					if (results[i].size()>j) {
						p = results[i].get(j).score;
						String w = results[i].get(j).toString(tagNumberer);
						o = f.format(p)+" "+w;
					}
					outputMatrix[j+1][i] = o;
				}
			}

			printRules("Grammar, "+tagName+", mass of top 5 substates: "+f.format(top5Mass),"productions", columnOutput, outputMatrix);
		}
		out.println("</div>");
	}
	
	/**
	 * @param columnOutput
	 * @param pData
	 * @param tagNumberer
	 * @param topN
	 * @param gs
	 * @param noContinueTags
	 */
	private static void printParentRuleStatistics(boolean columnOutput, ParserData pData, Numberer tagNumberer, int topN, GrammarStatistics gs, Set<Short> noContinueTags,
			double[] probState, double[][] probSubGivenState) {
		out.println("<a name=\"parentrules\"><h1>Parent Rules</h1></a>");
    
		// print rule probabilities
		for (short curTag : noContinueTags){
			int nSubStates = pData.numSubStatesArray[curTag];
			ArrayList<SearchState>[] results = new ArrayList[nSubStates];
			for (short i = 0; i < nSubStates; i++) {
				//do heavy computation
				PriorityQueue<SearchState> pq = gs.getTopParentRuleProductions(new FullState(curTag,i),probState,probSubGivenState);
				//convert pq to array
				results[i] = new ArrayList<SearchState>(topN);
				while (pq.size()!=0) {
					pq.peek().score = Math.exp(pq.peek().score);
					results[i].add(0,pq.next());
				}
			}
			
			String[][] outputMatrix = new String[topN+1][nSubStates];
			
			String tagName = (String) tagNumberer.object(curTag);
			for (int i = 0; i < nSubStates; i++) {
				outputMatrix[0][i] = tagName + "-" + i;
  		}
			
			for (int j = 0; j < topN; j++){
				for (int i = 0; i < nSubStates; i++) {
					String o="";
					double p=-1;
					if (results[i].size()>j) {
						p = results[i].get(j).score;
						String w = results[i].get(j).toString(tagNumberer);
						o = f.format(p)+" "+w;
					}
					outputMatrix[j+1][i] = o;
				}
			}

			printRules("Parent Rules","parentrules", columnOutput, outputMatrix);
		}
	}
	
	/**
	 * @param tree
	 */
	private static void logarithmModeTree(Tree<StateSet> tree) {
		if (tree.isLeaf())
			return;
		double[] iScores = tree.getLabel().getIScores();
		int iScale = tree.getLabel().getIScale();
		double[] oScores = tree.getLabel().getOScores();
		int oScale = tree.getLabel().getOScale();
		for (int i=0; i<iScores.length; i++) {
			iScores[i] = Math.log(iScores[i]) + 100*iScale;
			oScores[i] = Math.log(oScores[i]) + 100*oScale;
		}
		tree.getLabel().setIScores(iScores);
		tree.getLabel().setOScores(oScores);
		for (Tree child : tree.getChildren()) {
			logarithmModeTree(child);
		}
	}

	/**
	 * @param tree
	 * @param g
	 * @param parentProbs indexed by parent, child, psub, csub
	 */
	private void addProbs(Tree<StateSet> tree, Grammar g,
			double[][][][] parentProbs, double[][] normFactors,
			double treeScore) {
		int nSubStates = tree.getLabel().numSubStates();
		double[][] viterbiProbs = new double[nSubStates][nSubStates];
		for (int i=0; i<viterbiProbs.length; i++) {
			for (int j=0; j<viterbiProbs[i].length; j++) {
				if (i!=j) {
					viterbiProbs[i][j] = Double.NEGATIVE_INFINITY;
				} else {
					viterbiProbs[i][j] = tree.getLabel().getOScore(i)
							- treeScore;
				}
			}
		}
		addProbsHelper(tree.getLabel().getState(),tree,g,parentProbs,normFactors,viterbiProbs,treeScore);
	}

	/**
	 * @param tree
	 * @param g
	 * @param parentProbs
	 * @param viterbiProbs
	 */
	private void addProbsHelper(short gpState, Tree<StateSet> tree, Grammar g,
			double[][][][] parentProbs, double[][] normFactor, double[][] viterbiProbs, double treeScore) {
		if (tree.isPreTerminal() || tree.isLeaf())
			return;
		short pState = tree.getLabel().getState();
		int nParentStates = tree.getLabel().numSubStates();
		List<Tree<StateSet>> children = tree.getChildren();
		switch(children.size()) {
		case 1:
			Tree<StateSet> child = children.get(0);
			short cState = child.getLabel().getState();
			double[][] scores = g.getUnaryScore(pState,cState);
			int nChildStates = child.getLabel().numSubStates();
			double[][] newViterbiProbs = new double[viterbiProbs.length][nChildStates];
			for (int gpS=0; gpS<viterbiProbs.length; gpS++) {
				for (int cS=0; cS<nChildStates; cS++) {
					if (scores[cS]==null)
						continue;
					double[] scoresToSum = new double[nParentStates];
					for (int pS=0; pS<nParentStates; pS++) {
						scoresToSum[pS] = viterbiProbs[gpS][pS] + scores[cS][pS];
					}
					newViterbiProbs[gpS][cS] = SloppyMath.logAdd(scoresToSum); 
				}
			}
			if (continues(cState)) {
				addProbsHelper(gpState,child,g,parentProbs,normFactor,newViterbiProbs, treeScore);
			} else {
				addProbsFinal(child,gpState,cState,parentProbs,normFactor,newViterbiProbs);
				addProbs(child,g,parentProbs,normFactor,treeScore);
			}
			break;
		case 2:
			Tree<StateSet> lChild = children.get(0);
			Tree<StateSet> rChild = children.get(1);
			short lcState = lChild.getLabel().getState();
			short rcState = rChild.getLabel().getState();
			double[][][] scoresB = g.getBinaryScore(pState,lcState,rcState);
			int nLChildStates = lChild.getLabel().numSubStates();
			int nRChildStates = rChild.getLabel().numSubStates();
			double[][] newLViterbiProbs = new double[viterbiProbs.length][nLChildStates];
			double[][] newRViterbiProbs = new double[viterbiProbs.length][nRChildStates];
			for (int gpS=0; gpS<viterbiProbs.length; gpS++) {
				double[][] lScoresToSum = new double[nLChildStates][nParentStates*nRChildStates];
				double[][] rScoresToSum = new double[nRChildStates][nParentStates*nLChildStates];
				for (int lcS=0; lcS<nLChildStates; lcS++) {
					for (int rcS=0; rcS<nRChildStates; rcS++) {
						if (scoresB[lcS][rcS]==null)
							continue;
						for (int pS=0; pS<nParentStates; pS++) {
							double vp = viterbiProbs[gpS][pS];
							double sc = scoresB[lcS][rcS][pS];
							lScoresToSum[lcS][pS * nRChildStates + rcS] = vp
									+ sc + rChild.getLabel().getIScore(rcS);
							rScoresToSum[rcS][pS * nLChildStates + lcS] = vp
									+ sc + lChild.getLabel().getIScore(lcS);
						}
					}
				}
				for (int lcS=0; lcS<nLChildStates; lcS++) {
					newLViterbiProbs[gpS][lcS] = SloppyMath.logAdd(lScoresToSum[lcS]);
				}
				for (int rcS=0; rcS<nRChildStates; rcS++) {
					newRViterbiProbs[gpS][rcS] = SloppyMath.logAdd(rScoresToSum[rcS]);
				}
			}
			if (continues(lcState)) {
				addProbsHelper(gpState,lChild,g,parentProbs,normFactor,newLViterbiProbs, treeScore);
			} else {
				addProbsFinal(lChild,gpState,lcState,parentProbs,normFactor,newLViterbiProbs);
				addProbs(lChild,g,parentProbs,normFactor,treeScore);
			}
			if (continues(rcState)) {
				addProbsHelper(gpState,rChild,g,parentProbs,normFactor,newRViterbiProbs,treeScore);
			} else {
				addProbsFinal(rChild,gpState,rcState,parentProbs,normFactor,newRViterbiProbs);
				addProbs(rChild,g,parentProbs,normFactor,treeScore);
			}
			break;
		}
	}

	/**
	 * @param gpState
	 * @param state
	 * @param parentProbs
	 * @param newViterbiProbs
	 */
	private void addProbsFinal(Tree<StateSet> child, short gpState, short cState,
			double[][][][] parentProbs, double[][] normFactor, double[][] viterbiProbs) {
		for (int gpS=0; gpS<viterbiProbs.length; gpS++) {
			for (int cS=0; cS<viterbiProbs[gpS].length; cS++) {
				viterbiProbs[gpS][cS] = Math.exp(viterbiProbs[gpS][cS]
						+ child.getLabel().getIScore(cS));
			}
		}
		if (parentProbs[gpState][cState]==null) {
			parentProbs[gpState][cState] = new double[viterbiProbs.length][viterbiProbs[0].length];
		}
		double[][] parentProbsCC = parentProbs[gpState][cState];
		for (int gpS=0; gpS<viterbiProbs.length; gpS++) {
			for (int cS=0; cS<viterbiProbs[gpS].length; cS++) {
				parentProbsCC[gpS][cS] += viterbiProbs[gpS][cS];
				normFactor[cState][cS] += viterbiProbs[gpS][cS];
			}
		}
	}

	static class RuleStruct {
		public Rule r;
		public double score;
		public int pS;
		public int lS;
		public int rS;
		boolean binary;
		public RuleStruct(Rule r, double score, int pS, int lS, int rS) {
			this.r = r;
			this.score = score;
			this.pS = pS;
			this.lS = lS;
			this.rS = rS;
			this.binary = true;
		}
		public RuleStruct(Rule r, double score, int pS, int lS) {
			this.r = r;
			this.score = score;
			this.pS = pS;
			this.lS = lS;
			this.rS = -1;
			this.binary = false;
		}
	}

	/**
	 * Print the top topN rules starting from symbol tag.
	 * 
	 * @param tag
	 */
	private void printTopRules(short tag, int topN, boolean columnOutput, int padding) {
		String[][] outputMatrix = new String[topN+1][grammar.numSubStates[tag]];
		for (int i=0; i<outputMatrix.length; i++) {
			for (int j=0; j<outputMatrix[i].length; j++) {
				outputMatrix[i][j] = "";	
			}
		}
		for (int subState = 0; subState < grammar.numSubStates[tag]; subState++) {
			outputMatrix[0][subState] = (String) tagNumberer.object(tag) + "-" + subState;
			//hold top rules in reverse score order
			PriorityQueue<RuleStruct> topRules = new PriorityQueue<RuleStruct>();
			for (BinaryRule r : grammar.getBinaryRulesByParent(tag)) {
				for (int lSubState = 0; lSubState < grammar.numSubStates[r.getLeftChildState()]; lSubState++) {
					for (int rSubState = 0; rSubState < grammar.numSubStates[r.getRightChildState()]; rSubState++) {
						double score = r.getScore(subState,lSubState,rSubState);
						topRules.add(new RuleStruct(r,score,subState,lSubState,rSubState),-score);
						if (topRules.size() > topN)
							//remove worst rule
							topRules.next();
					}
				}
			}
			for (UnaryRule r : grammar.getUnaryRulesByParent(tag)) {
				for (int cSubState = 0; cSubState < grammar.numSubStates[r.getChildState()]; cSubState++) {
					double score = r.getScore(subState,cSubState);
					topRules.add(new RuleStruct(r,score,subState,cSubState),-score);
					if (topRules.size() > topN)
						//remove worst rule
						topRules.next();
				}
			}
			ArrayList<RuleStruct> r = new ArrayList<RuleStruct>();
			while (topRules.hasNext()) {
				RuleStruct s = topRules.next();
				r.add(0,s);
			}
			for (int i=0; i<r.size(); i++){
				outputMatrix[i+1][subState] = ruleToString(r.get(i));
			}
		}
		String tagName = (String)tagNumberer.object(tag);
		printRules("Trunk","topShortRules",columnOutput,outputMatrix);
	}
	
	public String ruleToString(RuleStruct r) {
		StringBuffer sB = new StringBuffer();
		sB.append(f.format(Math.exp(r.score)) + " ");
		if (r.binary) {
			BinaryRule b = (BinaryRule)r.r;
			String leftName = tagNumberer.object(b.leftChildState)+"-"+r.lS;
			String rightName = tagNumberer.object(b.rightChildState)+"-"+r.rS;
			sB.append("<a href="+reflabel("productions",leftName)+">"+leftName+"</a> ");
			sB.append("<a href="+reflabel("productions",rightName)+">"+rightName+"</a> ");
		} else {
			UnaryRule u = (UnaryRule)r.r;
			String childName = tagNumberer.object(u.childState)+"-"+r.lS;
			sB.append("<a href="+reflabel("productions",childName)+">"+childName+"</a> ");
		}
		return sB.toString();
	}

	/**
	 * @param columnOutput
	 * @param padding
	 * @param outputMatrix
	 */
	private static void printRules(String typeName, String ruleTypeName,
			boolean columnOutput, String[][] outputMatrix) {
		out.println("<h3>"+typeName+"</h3><table border=\"1\">");
		if (columnOutput) {
			for (int i = 0; i < outputMatrix.length; i++){
				out.println("<tr>");
				for (int j = 0; j < outputMatrix[0].length; j++) {
					if (i==0) {
						out.println("<th><a name="+label(ruleTypeName,outputMatrix[i][j])+"> <a href="+
								parentRefLabel(outputMatrix[i][j])+">");
						out.print(outputMatrix[i][j]);
						out.println("</a></a> (<a href="+label("parent",outputMatrix[i][j])+">p</a>)</th>");
					} else
						out.print("<td>"+sanitize(outputMatrix[i][j])+"</td>");
				}
				out.println("</tr>");
			}
		} else {
			for (int j = 0; j < outputMatrix[0].length; j++) {
				out.println("<tr>");
				for (int i = 0; i < outputMatrix.length; i++){
					if (j==0) {
						out.println("<th><a name="+label(ruleTypeName,outputMatrix[i][j])+"> <a href="+
								parentRefLabel(outputMatrix[i][j])+">");
						out.print(outputMatrix[i][j]);
						out.println("</a></a></th>");
					} else
						out.print("<td>"+sanitize(outputMatrix[i][j])+"</td>");						
				}
				out.println("</tr>");
			}
		}
		out.println("</table><br/>");
	}
		
	public static int maxWidthInRow(String[][] m,int row) {
		int l=0;
		for (int c=0; c<m[row].length; c++) {
			l = Math.max(l,m[row][c].length());
		}
		return l;
	}

	public static int maxWidthInCol(String[][] m,int col) {
		int l=0;
		for (int r=0; r<m.length; r++) {
			l = Math.max(l,m[r][col].length());
		}
		return l;
	}
	
	public static void computeAndPrintCounts(Grammar gr){
		int nUnaries=0, nBinaries=0;
		int totalU=0, totalB=0;
		int notInfU=0, notInfB=0;
		int nulledOutU=0, nulledOutB=0, notNulledOutU=0, notNulledOutB=0;
		for (int state=0; state<gr.numStates; state++){
			int nParentSubStates = gr.numSubStates[state];
			for (UnaryRule uRule : gr.getUnaryRulesByParent(state)){
				nUnaries++;
				int nChildSubStates = gr.numSubStates[uRule.childState];
				double[][] scores = uRule.getScores2();
				for (int j=0; j<scores.length; j++){
					totalU+=nChildSubStates;
					notNulledOutU++;
					if (scores[j]==null){
						nulledOutU++;
						continue;
					}
					for (int i=0; i<nParentSubStates; i++){
						if (!Double.isInfinite(scores[j][i])) notInfU++;
					}
				}
			}
			for (BinaryRule bRule : gr.getBinaryRulesByParent(state)){
				nBinaries++;
				double[][][] scores = bRule.getScores2();
				for (int j=0; j<scores.length; j++){
					for (int k=0; k<scores[j].length; k++){
						totalB+=nParentSubStates;
						notNulledOutB++;
						if (scores[j][k]==null){
							nulledOutB++;
							continue;
						}
						for (int i=0; i<scores[j][k].length; i++){
						  if (!Double.isInfinite(scores[j][k][i])) notInfB++;
						}
					}
				}
			}
		}
		int totalUS=0, totalBS=0;
		int notInfUS=0, notInfBS=0;
		for (int state=0; state<gr.numStates; state++){
			for (UnaryRule uRule : gr.getUnaryRulesByParent(state)){
				double[][] scores = uRule.getScores2();
				int nChildSubstates = gr.numSubStates[uRule.childState];
				for (int j=0; j<scores.length; j++){
					boolean okayInSomeSubstate = false;
					if (scores[j]!=null){
						for (int i=0; i<scores[j].length; i++){
							if (!Double.isInfinite(scores[j][i])) okayInSomeSubstate=true;
						}
					}
					totalUS+=nChildSubstates;
					if (okayInSomeSubstate)
						notInfUS+=nChildSubstates;
				}
			}
			for (BinaryRule bRule : gr.getBinaryRulesByParent(state)){
				double[][][] scores = bRule.getScores2();
				int nParentSubstates = gr.numSubStates[bRule.parentState];
				for (int j=0; j<scores.length; j++){
					for (int k=0; k<scores[0].length; k++){
						boolean okayInSomeSubstate = false;
						if (scores[j][k]!=null) {
							for (int i=0; i<scores[j][k].length; i++){
								if (!Double.isInfinite(scores[j][k][i])) okayInSomeSubstate = true;
							}
						}
						totalBS+=nParentSubstates;
						if (okayInSomeSubstate)
							notInfBS+=nParentSubstates;
					}
				}
			}
		}
		System.out.println("The baseline grammar has "+nUnaries+" unary and "+nBinaries+" binary rules.");
		System.out.println("When using substates there could be "+totalU+" unaries, but in fact there are only "+notInfU+".");
		System.out.println("When using substates there could be "+totalB+" binaries, but in fact there are only "+notInfB+".");
		System.out.println("Out of "+notNulledOutU+" slices "+nulledOutU+" are nulled out.");
		System.out.println("Out of "+notNulledOutB+" slices "+nulledOutB+" are nulled out.");
		System.out.println("Summed across substates, there could be "+totalUS+" unaries, but there are only "+notInfUS+".");
		System.out.println("Summed across substates, there could be "+totalBS+" binaries, but there are only "+notInfBS+".");
	}	
	
	public static void printLexiconUnknownStatistics(Lexicon lexicon, Numberer tagNumberer) {
		System.out.print(
				"\n" +
				"LEXICON UNKNOWN TAGS\n" +
				"  P(tag,substate | unknown signature)\n" +
				"\n" +
				"  Unknown signature meanings:\n" +
				"    -INITC    only first letter is capitalized\n" +
				"    -KNOWNLC  word is known when in lowercase\n" +
				"    -CAPS     letter other than 1st is capitalized\n" +
				"    -LC       word has a lower-case letter\n" +
				"    -NUM      word contains a digit\n" +
				"    -DASH     word contains a dash\n" +
				"    -s        word is >=3 letters long, ends with s, and not 'is' or 'us'\n" +
				"  The rest capture endings:\n" +
				"    -ed\n" +
				"    -ing\n" +
				"    -ion\n" +
				"    -er\n" +
				"    -est\n" +
				"    -ly\n" +
				"    -ity\n" +
				"    -y\n" +
				"    -al\n");
		Map<String,double[][]> unk = lexicon.getUnseenScores();
		for (String sig : unk.keySet()) {
			System.out.println();
			System.out.println("signature "+sig);
			double[][] scores = unk.get(sig);
			int maxWidth = 0;
			int count = 0;
			for (int tag=0; tag<scores.length; tag++) {
				if (scores[tag]==null)
					continue;
				count++;
				maxWidth = Math.max(maxWidth,scores[tag].length);
			}
			String[][] out = new String[count][maxWidth];
			int tagIdx = 0;
			for (int tag=0; tag<scores.length; tag++) {
				if (scores[tag]==null)
					continue;
				for (int substate=0; substate<maxWidth; substate++) {
					if (substate >= scores[tag].length)
						out[tagIdx][substate] = "";
					else
						out[tagIdx][substate] = f.format(scores[tag][substate]);
				}
				tagIdx++;
			}
			printRules("nothing","not ready",false,out);
		}
	}
	
	public static void printLexiconStatistics(Lexicon lexicon, Numberer tagNumberer, boolean[] grammarTags){
		//printLexiconUnknownStatistics(lexicon, tagNumberer);
		out.println("<a name=\"lexicon\"><h1>Lexicon</h1></a>");
		out.println("<div id=\"lexicon\">");
    
		
		HashMap<String, double[]>[] wordToTagCounters = lexicon.wordToTagCounters;
		for (short curTag=0; curTag<grammarTags.length; curTag++){
			if (grammarTags[curTag]) continue;
			int nSubStates = lexicon.numSubStates[curTag];
			PriorityQueue<String>[] pQs = new PriorityQueue[nSubStates];
			for (int i = 0; i < nSubStates; i++) {
				pQs[i] = new PriorityQueue<String>();
			}
			if (!lexicon.isConditional){
				HashMap<String, double[]> tagMap = wordToTagCounters[curTag];
				for (String word : tagMap.keySet()) {
					double[] lexiconScores = lexicon.score(word,curTag,0,false,false);
					double[] counts = tagMap.get(word);
					for (int i = 0; i < nSubStates; i++) {
						pQs[i].add(word, Math.exp(lexiconScores[i]));//counts[i]);
					}
				}
			}
			else {
				for (int w=0; w<lexicon.wordNumberer.total(); w++) {
					String word = (String)lexicon.wordNumberer.object(w);
					double[] lexiconScores = lexicon.score(word,curTag,0,false,false);
					for (int i = 0; i < nSubStates; i++) {
						pQs[i].add(word, Math.exp(lexiconScores[i]));//counts[i]);
					}
				}
			}
			String tagName = (String) tagNumberer.object(curTag);
			out.println("<h3>Lexicon</h3>");
			out.println("<table border=\"1\">");
			out.println("<tr>");			
			for (int i = 0; i < nSubStates; i++) {
				out.println("<th>"); 
				out.println("<a name=" + lexiconLabel(tagName + "-" + i)
						+ "> <a href=" + parentRefLabel(tagName + "-" + i) + ">");
				out.print(sanitize(tagName) + "-" + i);
				out.println("</a></a> (<a href="+label("parent",tagName)+">p</a>)");
				out.println("</th>"); 
  		}
			out.println("</tr>");
			for (int j = 0; j < topN; j++){
				out.println("<tr>");			
/*				System.out.println("The top " + topN + " words for the tag "
						+ (String) tagNumberer.object(curTag) + "-" + i + " are:");
				System.out.println(pQs[i].toString(topN));
			}
*/			for (int i = 0; i < nSubStates; i++) {
					if (i==0){ out.print("\n"); }
					String w="";
					double p=-1;
					if (pQs[i].hasNext()) {
						p = pQs[i].getPriority();
						w = pQs[i].next();
						String tmp = sanitize(w)+" "+f.format(p);
						if (tmp.length()<8) tmp = tmp.concat("\t");
					  out.print("<td>"+tmp+"</td>");
					}
				}
				out.println("</tr>");
			}
			out.println("</table><br/>");
		}
		out.println("</div>");
	}


	
	public static void printLexiconStatistics(SimpleLexicon lexicon, Numberer tagNumberer, boolean[] grammarTags, double[][] posteriors){
		//printLexiconUnknownStatistics(lexicon, tagNumberer);
		out.println("<a name=\"lexicon\"><h1>Lexicon</h1></a>");
		out.println("<div id=\"lexicon\">");

		double thresh = 0.005;
		for (short curTag=0; curTag<grammarTags.length; curTag++){
			if (grammarTags[curTag]) continue;
			int nSubStates = lexicon.numSubStates[curTag];
			int[] nProd = new int[nSubStates];
			PriorityQueue<String>[] pQs = new PriorityQueue[nSubStates];
			for (int i = 0; i < nSubStates; i++) {
				pQs[i] = new PriorityQueue<String>();
			}
			for (int w=0; w<lexicon.wordIndexer.size(); w++) {
				String word = (String)lexicon.wordIndexer.get(w);
				double[] lexiconScores = lexicon.score(word,curTag,0,false,false);
				for (int i = 0; i < nSubStates; i++) {
					pQs[i].add(word, lexiconScores[i]);//Math.exp(lexiconScores[i]));//counts[i]);
					if (lexiconScores[i]>thresh) nProd[i]++;
				}
			}
			ArrayList<Double> sortedP = new ArrayList();
			for (Double d : posteriors[curTag]) sortedP.add(d);
			Collections.sort(sortedP);
			double top5Mass = 0;
			for (int i=0; i<5; i++) top5Mass += sortedP.get(sortedP.size()-i-1);


			String tagName = (String) tagNumberer.object(curTag);
			out.println("<h3>Lexicon"+tagName+", mass of top 5 substates: "+f.format(top5Mass)+"</h3>");
			out.println("<table border=\"1\">");
			out.println("<tr>");			
			for (int i = 0; i < nSubStates; i++) {
				String mass = f.format(lexicon.scores[curTag][i]);
				String post = f.format(posteriors[curTag][i]);
				String prod = ""+nProd[i];
				out.println("<th>"); 
				out.println("<a name=" + lexiconLabel(tagName + "-" + i)
						+ "> <a href=" + parentRefLabel(tagName + "-" + i) + ">");
				out.print(sanitize(tagName) + "-" + i +" ("+post+") ["+mass+", "+prod+"]");
				out.println("</a></a> (<a href="+label("parent",tagName)+">p</a>)");
				out.println("</th>"); 
  		}
			out.println("</tr>");
			for (int j = 0; j < topN; j++){
				out.println("<tr>");			
/*				System.out.println("The top " + topN + " words for the tag "
						+ (String) tagNumberer.object(curTag) + "-" + i + " are:");
				System.out.println(pQs[i].toString(topN));
			}
*/			for (int i = 0; i < nSubStates; i++) {
					if (i==0){ out.print("\n"); }
					String w="";
					double p=-1;
					if (pQs[i].hasNext()) {
						p = pQs[i].getPriority();
						w = pQs[i].next();
						String tmp = sanitize(w)+" "+f.format(p);
						if (tmp.length()<8) tmp = tmp.concat("\t");
					  out.print("<td>"+tmp+"</td>");
					}
				}
				out.println("</tr>");
			}
			out.println("</table><br/>");
		}
		out.println("</div>");
	}

	/**
	 * @param tagName
	 * @return
	 */
	static String lexiconLabel(String tagName) {
		return "\"productions-"+tagName+"\"";
	}

	/**
	 * @param ruleTypeName
	 * @param tagName
	 * @return
	 */
	static String label(String ruleTypeName, String tagName) {
		return "\""+ruleTypeName+"-"+tagName+"\"";
	}

	static String reflabel(String ruleTypeName, String tagName) {
		return "\"#"+ruleTypeName+"-"+tagName+"\"";
	}
	
	static String parentLabel(String tagName) {
		return label("parentrules",tagName);
	}

	static String parentRefLabel(String tagName) {
		return reflabel("parentrules",tagName);
	}
	
	static String sanitize(String s) {
		return s.replaceAll("&","&amp;");
	}
}

	
	
