package edu.berkeley.nlp.bitext;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.berkeley.nlp.io.PennTreebankReader;
//import edu.berkeley.nlp.ling.HeadBinarizer;
import edu.berkeley.nlp.syntax.Tree;
//import edu.berkeley.nlp.syntax.TreebankFetcher;
import edu.berkeley.nlp.syntax.Trees;
import edu.berkeley.nlp.syntax.Trees.PennTreeRenderer;
import edu.berkeley.nlp.syntax.Trees.TreeTransformer;
import edu.berkeley.nlp.bitext.GrammarStateFactory.GrammarState;
import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.CounterMap;
import edu.berkeley.nlp.util.Interner;

public class Grammar  {

	private final GrammarStateFactory stateFactory;

	private final Interner<BinaryRule> binaryInterner = new Interner<BinaryRule>();
	private final Interner<UnaryRule> unaryInterner = new Interner<UnaryRule>();

	private List<List<BinaryRule>> binarysByParentList = new ArrayList<List<BinaryRule>>();
	private List<List<BinaryRule>> binarysByLeftList = new ArrayList<List<BinaryRule>>();
	private List<List<BinaryRule>> binarysByRightList = new ArrayList<List<BinaryRule>>();

	private BinaryRule[][] binarysByParent;
	private BinaryRule[][] binarysByLeft;
	private BinaryRule[][] binarysByRight;

	private List<List<UnaryRule>> unarysByParentList =  new ArrayList<List<UnaryRule>>();
	private List<List<UnaryRule>> unarysByChildList =  new ArrayList<List<UnaryRule>>();

	private UnaryRule[][] unarysByParent;
	private UnaryRule[][] unarysByChild;

	// The grammar can't be altered anymore
	private boolean locked = false;

	public GrammarState getState(String state) {
		return stateFactory.getState(state);
	}

	public GrammarState getState(int stateId) {
		return stateFactory.getGrammarState(stateId);
	}

	public boolean isLocked() {
		return locked;
	}

	public GrammarState getRootState() {
		return stateFactory.getState("ROOT");
	}

	public Collection<UnaryRule> getUnarys() {
		return unaryInterner.getCanonicalObjects();
	}

	public Collection<BinaryRule> getBinarys() {
		return binaryInterner.getCanonicalObjects();
	}

	private static <R>  void  grow(List<R> lst, int size) {
		for (int i=lst.size(); i < size; ++i) {
			lst.add(null);
		}		
	}

	private static <R extends Rule> void addToRuleIndex(List<List<R>> ruleLists, GrammarState state, R rule) {	
		grow(ruleLists, state.id()+1);
		List<R> ruleList = ruleLists.get(state.id());
		if (ruleList == null) {
			ruleList = new ArrayList<R>();
			ruleLists.set(state.id(), ruleList);
		}
		ruleList.add(rule);
	}

	public int getNumStates() {
		return stateFactory.getNumGrammarStates();
	}

	private static List emptyList = Collections.unmodifiableList(new ArrayList());

	@SuppressWarnings("unchecked")
	private static <R extends Rule> List<R> getRuleList(List<List<R>> ruleLists, GrammarState state) {
		return getRuleList(ruleLists, state.id());
	}

	@SuppressWarnings("unchecked")
	private static <R extends Rule> List<R> getRuleList(List<List<R>> ruleLists, int stateId) {
		if (stateId >= ruleLists.size()) {
			return emptyList;
		}
		List<R> ruleList = ruleLists.get(stateId);
		if (ruleList == null) {
			return emptyList;
		}
		return ruleList;
	}

	private UnaryRule addUnaryRule(UnaryRule ur) {
		if (locked) {
			throw new RuntimeException("Can't add to a locked gramamr");
		}
		if (unaryInterner.contains(ur)) {
			return (UnaryRule) intern(ur);
		}
		ur = unaryInterner.intern(ur);
		addToRuleIndex(unarysByParentList, ur.parent(), ur);
		addToRuleIndex(unarysByChildList, ur.child(), ur);
		return ur;
	}

	private BinaryRule addBinaryRule(BinaryRule br) {
		if (locked) {
			throw new RuntimeException("Can't add to a locked gramamr");
		}
		if (binaryInterner.contains(br)) {
			return (BinaryRule) intern(br);
		}
		br = binaryInterner.intern(br);
		addToRuleIndex(binarysByParentList, br.parent(), br);
		addToRuleIndex(binarysByLeftList, br.leftChild(), br);
		addToRuleIndex(binarysByRightList, br.rightChild(), br);		
		return br;
	}
	
	public void setScore(BinaryRule br, double score) {
		if (!binaryInterner.contains(br)) {
			if (locked) {
				throw new RuntimeException("Trying to setScore for non-existant rull");
			} else {
				br = addBinaryRule(br);
			}
		}
		br = binaryInterner.intern(br);
		br.setScore(score);
	}
	
	public void setScore(Rule r, double score) {
		if (r instanceof UnaryRule) {
			setScore((UnaryRule) r, score);
		} else  if (r instanceof BinaryRule) {
			setScore((BinaryRule) r, score);
		} 
	}

	public void setScore(UnaryRule ur, double score) {
		if (!unaryInterner.contains(ur)) {
			if (locked) {
				throw new RuntimeException("Trying to setScore for non-existant rull");
			} else {
				ur = addUnaryRule(ur);				
			}
		}
		ur = unaryInterner.intern(ur);
		ur.setScore(score);
	}

	
	private Rule intern(Rule r) {
		if (r instanceof UnaryRule) return unaryInterner.intern((UnaryRule) r);
		return binaryInterner.intern((BinaryRule) r);
	}

	private Rule addRule(Rule r) {
		if (r instanceof BinaryRule) {
			return addBinaryRule((BinaryRule) r);
		} 
		else {
			return addUnaryRule((UnaryRule) r);
		}
	}

	public UnaryRule getUnaryRule(String parent, String child) {		
		GrammarState parentState = stateFactory.getState(parent);	
		GrammarState childState = stateFactory.getState(child);		
		UnaryRule ur = new UnaryRule(parentState, childState);
		if (unaryInterner.contains(ur))  {
			return unaryInterner.intern(ur);
		}
		return ur;
	}

	public UnaryRule addUnaryRule(String parent, String child) {
		if (locked) {
			throw new RuntimeException("Can't add to a locked gramamr");
		}
		return addUnaryRule(getUnaryRule(parent, child));
	}

	public BinaryRule getBinaryRule(String parent, String lChild, String rChild) {
		GrammarState parentState = stateFactory.getState(parent);	
		GrammarState lState = stateFactory.getState(lChild);
		GrammarState rState = stateFactory.getState(rChild);	
		BinaryRule br = new BinaryRule(parentState, lState, rState);
		if ( binaryInterner.contains(br)) {
			return binaryInterner.intern(br);
		}
		return br;
		
	}

	public boolean isTag(GrammarState state) {
		return getBinarysByParent(state).length == 0  && getUnarysByParent(state).length == 0; 
	}

	public BinaryRule addBinaryRule(String parent, String lChild, String rChild) {
		if (locked) {
			throw new RuntimeException("Can't add to a locked gramamr");
		}
		return addBinaryRule(getBinaryRule(parent, lChild, rChild));
	}

	public BinaryRule[] getBinarysByParent(GrammarState parent) {
		return getBinarysByParent(parent.id());
	}

	public BinaryRule[] getBinarysByLeftChild(int leftChild) {
		if (locked) {
			return binarysByLeft[leftChild];
		}
		return (BinaryRule[]) getRuleList(binarysByLeftList, leftChild).toArray();
	}

	public BinaryRule[] getBinarysByRightChild(int rightChildId) {
		if (locked) {
			return binarysByRight[rightChildId];
		}
		return (BinaryRule[]) getRuleList(binarysByRightList, rightChildId).toArray();
	}

	public BinaryRule[] getBinarysByParent(int  parentId) {
		if (locked) {
			return binarysByParent[parentId];
		}
		return (BinaryRule[]) getRuleList(binarysByParentList, parentId).toArray();
	}

	public BinaryRule[] getBinarysByLeftChild(GrammarState leftChild) {
		return getBinarysByLeftChild(leftChild.id());
	}

	public BinaryRule[] getBinarysByRightChild(GrammarState rightChildState) {
		return getBinarysByRightChild(rightChildState.id());
	}

	public UnaryRule[] getUnarysByParent(GrammarState parentState) {
		return getUnarysByParent(parentState.id());
	}

	public UnaryRule[] getUnarysByChild(GrammarState childState) {
		return getUnarysByChild(childState.id());
	}

	public UnaryRule[] getUnarysByParent(int parentState) {
		if (locked) {
			return unarysByParent[parentState];
		}
		return (UnaryRule[]) unarysByParentList.get(parentState).toArray();
	}

	public UnaryRule[] getUnarysByChild(int childState) {
		if (locked) {
			return unarysByChild[childState];
		}
		return (UnaryRule[]) unarysByChildList.get(childState).toArray();
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();
//		for (BinaryRule br: binaryInterner.getCanonicalObjects()) {
//		sb.append(String.format("%s %.5f\n",br.toString(),br.getScore()));
//		}
//		for (UnaryRule ur: unaryInterner.getCanonicalObjects()) {
//		sb.append(String.format("%s %.5f\n",ur.toString(),ur.getScore()));
//		}
		sb.append("Num Unary Rules: " + unaryInterner.size()+"\n");
		sb.append("Num Binary Rules " + binaryInterner.size());
		return sb.toString();
	}

//	public void train(Collection<Tree<String>> trees) {
//		CounterMap<String, Rule> ruleCounter = new CounterMap<String, Rule>();
//		for (Tree<String> tree: trees) {
//			for (Tree<String> node: tree.toSubTreeList()) {
//				if (!node.isPhrasal())	 continue;
//				List<Tree<String>> children = node.getChildren();
//				if (children.size() > 2) {
//					throw new RuntimeException("Un-binarized tree:\n"+PennTreeRenderer.render(node));
//				}
//				Rule r = null;
//				String parent = node.getLabel();
//				if (children.size() == 1) {
//					r = getUnaryRule(parent, children.get(0).getLabel());
//
//				} else if (children.size() == 2) {
//					r = getBinaryRule(parent, children.get(0).getLabel(), children.get(1).getLabel());
//				}
//				ruleCounter.incrementCount(parent, r, 1.0);
//			}
//		}
//		ruleCounter.normalize();
//
//
//		for (String parent: ruleCounter.keySet()) {
//			Counter<Rule> parentRuleCounter = ruleCounter.getCounter(parent);
//			double sumProb = 0.0;
//			for (Rule r: parentRuleCounter.keySet()) {
//				double prob = parentRuleCounter.getCount(r);
//				sumProb += prob;				
//				r = addRule(r);
//				r.setScore( Math.log(prob) );
//			}
//			if (!(sumProb > 0.99 && sumProb < 1.01)) {
//				throw new RuntimeException();
//			}
//		}		
//		lock();
//	}

	public void writeData(BufferedWriter bw) {
		try {
			for (UnaryRule ur: unaryInterner.getCanonicalObjects()) {
				bw.write( String.format("%s %s %.5f\n", ur.parent(), ur.child(), ur.getScore()) );								
			}
			for (BinaryRule br: binaryInterner.getCanonicalObjects()) {
				bw.write( String.format("%s %s %s %.5f\n", br.parent(), br.leftChild(), br.rightChild(), br.getScore()));
			}
			bw.flush();
			bw.close();
		} catch (Exception e) { e.printStackTrace(); }

	}

	/**
	 * Reads the grammar from the passed in <code>Reader</code> and <code>locks()</code>
	 * the resulting Grammar.
	 * @param br
	 */
	public Grammar(BufferedReader br) {
		
		stateFactory = new GrammarStateFactory();
		
		Pattern unaryPattern = Pattern.compile("\\s*(\\S+)\\s+(\\S+)\\s*(\\S+)\\s*$");
		Pattern binaryPattern = Pattern.compile("\\s*(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s*$");

		try {
			while (true) {
				String line = br.readLine();
				if (line == null) {
					break;
				}
				Matcher unaryMatcher = unaryPattern.matcher(line);				
				Matcher binaryMatcher = binaryPattern.matcher(line);
				if (unaryMatcher.matches()) {
					String parent = unaryMatcher.group(1);
					String child = unaryMatcher.group(2);
					double score = Double.parseDouble(unaryMatcher.group(3));
					UnaryRule ur = getUnaryRule(parent, child);
					ur.setScore(score);
					addUnaryRule(ur);
				}
				else if (binaryMatcher.matches()) {
					String parent = binaryMatcher.group(1);
					String lChild = binaryMatcher.group(2);
					String rChild = binaryMatcher.group(3);
					double score = Double.parseDouble(binaryMatcher.group(4));
					BinaryRule binaryRule = getBinaryRule(parent, lChild, rChild);
					binaryRule.setScore(score);
					addBinaryRule(binaryRule);				
				} else {
					throw new RuntimeException("Error:\n"+line);
				}
			}
			lock();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	private static final BinaryRule[] emptyBinaryArray = new BinaryRule[0];
	private static final UnaryRule[] emptyUnaryArray = new UnaryRule[0];

	private BinaryRule[][] convertToArrays(List<List<BinaryRule>> listOfRules) {
		BinaryRule[][] arraysOfRules = new BinaryRule[getNumStates()][];

		for (int i=0; i < getNumStates(); ++i) {

			if (i >= listOfRules.size() || listOfRules.get(i) == null) {
				arraysOfRules[i] = emptyBinaryArray;
			} else {
				List<BinaryRule> rules = listOfRules.get(i);
				arraysOfRules[i] = new BinaryRule[rules.size()];
				for (int r=0; r < rules.size(); ++r) {
					arraysOfRules[i][r] = rules.get(r);
				}
			}			
		}

		return arraysOfRules;
	}

	private UnaryRule[][] convertToArrays(List<List<UnaryRule>> listOfRules) {
		UnaryRule[][] arraysOfRules = new UnaryRule[getNumStates()][];

		for (int i=0; i < getNumStates(); ++i) {			
			if (i >= listOfRules.size() || listOfRules.get(i) == null) {
				arraysOfRules[i] = emptyUnaryArray;
			} else {
				List<UnaryRule> rules = listOfRules.get(i);
				arraysOfRules[i] = new UnaryRule[rules.size()];
				for (int r=0; r < rules.size(); ++r) {
					arraysOfRules[i][r] = rules.get(r);
				}				
			}			
		}

		return arraysOfRules;
	}

	public void lock() {
		if (locked) {
			throw new RuntimeException("Grammar already locked");				
		}
		binarysByParent = convertToArrays(binarysByParentList);
		binarysByLeft = convertToArrays(binarysByLeftList);
		binarysByRight = convertToArrays(binarysByRightList);

		binarysByParentList = null;
		binarysByLeftList = null;
		binarysByRightList = null;
		
		unarysByParent = convertToArrays(unarysByParentList);
		unarysByChild = convertToArrays(unarysByChildList);
				
		unarysByParentList = null;
		unarysByChildList = null;
		System.gc();
		
		stateFactory.lock();
		
		locked = true;
	}

	public Grammar() {
		stateFactory = new GrammarStateFactory();
	}
	
	public Grammar(GrammarStateFactory stateFactory) {
		this.stateFactory = stateFactory; 
	}


//	/**
//	 * @param args
//	 * @throws IOException 
//	 * @throws FileNotFoundException 
//	 * @throws ClassNotFoundException 
//	 */
//	public static void main(String[] args) throws FileNotFoundException, IOException, ClassNotFoundException {
//		String treebankPath = System.getenv("TREEBANK");
//		TreebankFetcher fetcher = new TreebankFetcher(true);
//		fetcher.addTransformer( new Trees.StandardTreeNormalizer()  );
//		fetcher.addTransformer( new HeadBinarizer()  );
//		fetcher.addTransformer( new ParentAnnotater()  );
//		fetcher.setMaxLength(40);
//
//		Collection<Tree<String>> trees = fetcher.getTrees(treebankPath, 200, 2199);
//				
//
//		System.out.printf("Done binarizing trees\nTraining...");
//		Grammar g = new Grammar();
//		g.train(trees);
//		
////		trees = null; System.gc();
//		
//		System.out.println(g);
////		List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
//		MemoryMXBean bean = ManagementFactory.getMemoryMXBean();
//		MemoryUsage usage = bean.getHeapMemoryUsage();
//		long bytes = usage.getUsed(); 
//		System.out.printf("Number of megs: " + (bytes/1.0e6) );
////		for (MemoryPoolMXBean p: pools) {
////			System.out.println("Memory type="+p.getType()+" Memory usage="+p.getUsage());
////		}
//		System.out.printf("Done\n");
//	}

}
