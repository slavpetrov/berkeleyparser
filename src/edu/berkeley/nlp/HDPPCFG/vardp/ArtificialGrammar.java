package edu.berkeley.nlp.HDPPCFG.vardp;

import java.io.*;
import java.util.*;
import fig.basic.*;
import fig.record.*;
import fig.prob.*;
import fig.exec.*;
import static fig.basic.LogInfo.*;
import edu.berkeley.nlp.syntax.Tree;

/**
 * Generate artificial grammars.
 * Have the ability to merge categories.
 */
public class ArtificialGrammar implements Recordable {
  private static final String rootCat = "ROOT";

  public static ArtificialGrammar[] grammars = new ArtificialGrammar[] {
    G( // 0
      r(rootCat, "A", 0.3, "B", 0.7),
      r("A", "a", 1),
      r("B", "b", 1),
    null),
    G( // 1
      r(rootCat, "A", 0.3, "B", 0.7),
      r("A", "a", 0.8, "B", 0.2),
      r("B", "b", 0.4, "a", 0.6),
    null),
    G( // 2
      r(rootCat, "A", 0.3, "B", 0.2, "A B", 0.5),
      r("A", "a", 0.4, "B", 0.2, "C A", 0.4),
      r("B", "b", 0.4, "a", 0.6),
      r("C", "c", 0.8, "a", 0.2),
    null),
    G( // 3
      r(rootCat, "A", 1),
      r("A", "a", 1),
    null),
    G( // 4
      r(rootCat, "A0", 0.4, "A1", 0.1, "B", 0.5),
      r("A0", "C", 0.5, "B", 0.5),
      r("A1", "D", 0.9, "B", 0.1),
      r("B", "A1", 0.9, "D", 0.1),
      r("C", "a", 1),
      r("D", "d", 0.9, "a", 0.1),
    null),
    G( // 5
      r(rootCat, "A0", 0.4, "A1", 0.1, "B", 0.5),
      r("A0", "C", 0.5, "B", 0.5),
      r("A1", "B", 1),
      r("B", "b", 1),
      r("C", "a", 1),
    null),
    G( // 6 (easy clustering task: no noise)
      r(rootCat, "S", 1),
      r("S",
        "X-0 X-0", 1.0/4,
        "X-1 X-1", 1.0/4,
        "X-2 X-2", 1.0/4,
        "X-3 X-3", 1.0/4),
      r("X-0", "a0", 1),
      r("X-1", "a1", 1),
      r("X-2", "a2", 1),
      r("X-3", "a3", 1),
    null),
    G( // 7 (harder clustering task: with noise)
      r(rootCat, "S", 1),
      r("S",
        "X-0 X-0", 1.0/4,
        "X-1 X-1", 1.0/4,
        "X-2 X-2", 1.0/4,
        "X-3 X-3", 1.0/4),
      r("X-0", "a0", 1.0/4, "b0", 1.0/4, "c0", 1.0/4, "d0", 1.0/4),
      r("X-1", "a1", 1.0/4, "b1", 1.0/4, "c1", 1.0/4, "d1", 1.0/4),
      r("X-2", "a2", 1.0/4, "b2", 1.0/4, "c2", 1.0/4, "d2", 1.0/4),
      r("X-3", "a3", 1.0/4, "b3", 1.0/4, "c3", 1.0/4, "d3", 1.0/4),
    null),
    G( // 8 (same as 7, but have S and T instead of just S);
       // hopefully answers whether tying the DPs over X is necessary
      r(rootCat, "S", 0.5, "T", 0.5),
      r("S",
        "X-0 X-0", 1.0/4,
        "X-1 X-1", 1.0/4,
        "X-2 X-2", 1.0/4,
        "X-3 X-3", 1.0/4),
      r("T",
        "X-0 X-0", 1.0/4,
        "X-1 X-1", 1.0/4,
        "X-2 X-2", 1.0/4,
        "X-3 X-3", 1.0/4),
      r("X-0", "a0", 1.0/4, "b0", 1.0/4, "c0", 1.0/4, "d0", 1.0/4),
      r("X-1", "a1", 1.0/4, "b1", 1.0/4, "c1", 1.0/4, "d1", 1.0/4),
      r("X-2", "a2", 1.0/4, "b2", 1.0/4, "c2", 1.0/4, "d2", 1.0/4),
      r("X-3", "a3", 1.0/4, "b3", 1.0/4, "c3", 1.0/4, "d3", 1.0/4),
    null),
  null };

  private Map<String, Rule> rules;

  public static ArtificialGrammar G(Rule... ruleList) {
    return new ArtificialGrammar(ruleList);
  }

  public ArtificialGrammar(Rule[] ruleList) {
    this.rules = new LinkedHashMap<String, Rule>();
    for(Rule rule : ruleList)
      if(rule != null)
        rules.put(rule.cat, rule);
  }

  public List<Tree<String>> generateTrees(Random random, int n) {
    List<Tree<String>> list = new ArrayList<Tree<String>>();
    for(int i = 0; i < n; i++) {
      try {
        list.add(generateTree(random));
      } catch(Exception e) { // Exception: probably exceed maximum depth
      }
    }
    logs("Generated %d/%d trees (%d exceeded maximum depth)", list.size(), n, n-list.size());
    return list;
  }

  final int maxDepth = 1000;
  public Tree<String> generateTree(Random random) {
    return generateTree(rootCat, random, 0);
  }
  public Tree<String> generateTree(String cat, Random random, int depth) {
    if(depth > maxDepth) throw new RuntimeException("Exceeded maxDepth");
    Rule rule = rules.get(cat);
    if(rule == null) // Assume is terminal
      return new Tree<String>(cat);

    String[] childCats = rule.sample(random);
    List<Tree<String>> children = new ArrayList<Tree<String>>();
    for(String childCat : childCats)
      children.add(generateTree(childCat, random, depth+1));

    return new Tree<String>(cat, children);
  }

  // Return a larger grammar, where each category C has been split into
  // C-1, C-2, ..., C-K.
  // Can use this grammar to generate trees.
  // Then call TreeUtils.removeCategoryNumber() to strip the split.
  // See if we can recover these splits.
  public ArtificialGrammar splitCategories(Random random, int avgK, double concentration) {
    assert avgK > 0;
    //if(avgK == 1) return this;

    // Determine number of subcategories for each category
    Map<String,Integer> Kmap = new HashMap();
    Record.begin("numSubcategories");
    for(String cat : rules.keySet()) {
      int K = cat.equals(rootCat) ? 1 : 
        (int)(SampleUtils.samplePoisson(random, 1.0/avgK)+1);
      Kmap.put(cat, K);
      Record.add(cat, K);
    }
    Record.end();

    // Split all the nonterminals and preterminals into K subcategories
    List<Rule> newRules = new ArrayList<Rule>();
    for(Rule rule : rules.values()) {
      for(int k = 0; k < Kmap.get(rule.cat); k++) { // For each subcategory of the parent
        // Concoct a new distribution over rules based on the old parent
        int R = rule.rewrites.length;
        double[] probs = new double[R];
        for(int r = 0; r < R; r++)
          probs[r] = rule.rewrites[r].getSecond();
        probs = new Dirichlet(ListUtils.mult(concentration, probs)).sample(random);
        // Create new rewrites (backbone)
        List<Pair<String[],Double>> newRewrites = new ArrayList();
        for(int r = 0; r < R; r++)
          newRewrites.add(new Pair(rule.rewrites[r].getFirst(), probs[r]));

        int arity = 0;
        for(Pair<String[],Double> rewrite : newRewrites)
          arity = Math.max(arity, rewrite.getFirst().length);

        // Now, we need to expand each children in the rewrites
        // Expand the ith child in all the rewrites (e.g. i < 2 for binary grammars)
        for(int i = 0; i < arity; i++) {
          List<Pair<String[],Double>> newerRewrites = new ArrayList();
          for(Pair<String[],Double> rewrite : newRewrites) {
            String[] childCats = rewrite.getFirst();
            double value = rewrite.getSecond();
            if(i >= childCats.length || Character.isLowerCase(childCats[i].charAt(0))) // Don't split words
              newerRewrites.add(rewrite);
            else {
              // Divide the mass randomly
              int K1 = Kmap.get(childCats[i]);
              double[] newValues = ListUtils.mult(value, new Dirichlet(K1, concentration).sample(random));
              for(int k1 = 0; k1 < K1; k1++) {
                String[] newChildCats = (String[])childCats.clone();
                newChildCats[i] += "-"+k1;
                newerRewrites.add(new Pair(newChildCats, newValues[k1]));
              }
            }
          }
          newRewrites = newerRewrites;
        }
        Rule newRule = new Rule(rule.cat.equals(rootCat) ? rule.cat : rule.cat+"-"+k,
            (Pair<String[],Double>[])newRewrites.toArray(new Pair[0]));
        newRules.add(newRule);
      }
    }
    return new ArtificialGrammar((Rule[])newRules.toArray(new Rule[0]));
  }

  public static Rule r(String cat, Object... args) {
    int n = args.length;
    assert args.length % 2 == 0;
    Pair<String[],Double>[] rewrites = new Pair[n/2];
    double sum = 0;
    for(int i = 0; i < n; i += 2) {
      String[] key = StrUtils.split((String)args[i]);
      double value = fig.basic.Utils.toDouble(args[i+1]);
      rewrites[i/2] = new Pair<String[],Double>(key, value);
      sum += value;
    }
    NumUtils.assertEquals(sum, 1);
    return new Rule(cat, rewrites);
  }

  public void record(Object arg) {
    for(Rule rule : rules.values())
      Record.addEmbed("cat", rule.cat, rule);
  }

  public static class Rule implements Recordable {
    public String cat;
    // Either String or Pair<String,String> -> probability
    public Pair<String[],Double>[] rewrites;

    public Rule(String cat, Pair<String[],Double>[] rewrites) {
      this.cat = cat;
      this.rewrites = rewrites;
    }

    public String[] sample(Random random) {
      double target = random.nextDouble();
      double accum = 0;
      for(Pair<String[],Double> rewrite : rewrites) {
        accum += rewrite.getSecond();
        if(accum >= target) return rewrite.getFirst();
      }
      throw Exceptions.bad("accum = %f", accum);
    }

    public void record(Object arg) {
      Record.setStruct("rewrite", "prob");
      for(Pair<String[],Double> rewrite : rewrites)
        Record.add(StrUtils.join(rewrite.getFirst()), rewrite.getSecond());
    }
  }

  private static String randState(Random random, int C, int c) {
    if(c == C) return "C"+random.nextInt(C/2); // Root rewrites to only nonterminals
    return "C"+random.nextInt(C);
  }
  private static String randWord(Random random, int W) {
    return "w"+random.nextInt(W);
  }

  // The first C/2 categories will be nonterminals
  // and the latter C/2 categories will be preterminals
  // Last one is ROOT
  public static ArtificialGrammar generateGrammar(Random random,
      int C, int W, double concentration, double unaryFrac) {
    Rule[] rules = new Rule[C+1];
    for(int c = 0; c <= C; c++) { // For each category c...
      int R = C;
      // Chose R rewrites at random (duplicates are okay)
      Pair<String[],Double>[] rewrites = new Pair[R];
      double[] probs = new Dirichlet(R, concentration).sample(random);

      for(int r = 0; r < R; r++) {
        String[] rhs;
        if(c < C/2 || c == C) { // Non-terminal
          // Generate either a unary or binary RHS
          if(random.nextDouble() < unaryFrac)
            rhs = new String[] { randState(random, C, c) };
          else
            rhs = new String[] { randState(random, C, c), randState(random, C, c) };
        }
        else { // Pre-terminal
          // Would be nice to get some Zipfian distribution over words
          rhs = new String[] { randWord(random, W) };
        }
        rewrites[r] = new Pair(rhs, probs[r]);
      }
      rules[c] = new Rule(c == C ? "ROOT" : "C"+c, rewrites);
    }

    return new ArtificialGrammar(rules);
  }

  // Remove the subcategory identifier of each category string.
  public static Tree<String> removeEndingCategoryNumber(Tree<String> t) {
    String label = t.getLabel();
    int n = label.lastIndexOf('-');
    String newLabel = n == -1 ? label : label.substring(0, n);

    List<Tree<String>> newChildren = new ArrayList<Tree<String>>();
    for(Tree<String> child : t.getChildren())
      newChildren.add(removeEndingCategoryNumber(child));
    return new Tree<String>(newLabel, newChildren);
  }
  public static List<Tree<String>> removeEndingCategoryNumber(List<Tree<String>> ts) {
    List<Tree<String>> newts = new ArrayList<Tree<String>>();
    for(Tree<String> t : ts) newts.add(removeEndingCategoryNumber(t));
    return newts;
  }

  public static class Main implements Runnable {
    @Option public int numCategories = 4;
    @Option public int numSubcategories = 2;
    @Option public int numWords = 10;
    @Option public int numTrees = 10;
    @Option(gloss="Fraction of unary rules")
      public double unaryFrac = 0.4;
    @Option(gloss="How uniform the backbone rewrite distributions should be")
      public double backboneConcentration = 1;
    @Option(gloss="How close rewrite distributions of subcategories should be to their parent")
      public double splitConcentration = 1;
    @Option public double trainFrac = 0.8;
    @Option public Random grammarRandom = new Random(1);
    @Option public Random splitRandom = new Random(1);
    @Option public Random treeRandom = new Random(1);
    @Option public String trainOutPath = "/dev/stdout";
    @Option public String testOutPath = "/dev/stdout";
    @Option public String grammarOutPath = "/dev/stdout";

    @Option public int grammarIndex = -1;
    @Option public boolean splitGrammar = true;

    public void run() {
      Record.init(grammarOutPath);

      // Generate a grammar and split it
      ArtificialGrammar origGrammar =
        grammarIndex == -1 ?
          ArtificialGrammar.generateGrammar(grammarRandom, numCategories, numWords, backboneConcentration, unaryFrac) :
          grammars[grammarIndex];
      Record.addEmbed("origGrammar", origGrammar);
      ArtificialGrammar splitGrammar =
        this.splitGrammar ?
          origGrammar.splitCategories(splitRandom, numSubcategories, splitConcentration) :
          origGrammar;
      Record.addEmbed("splitGrammar", splitGrammar);

      // Generate trees
      List<Tree<String>> trees = splitGrammar.generateTrees(treeRandom, numTrees);
      trees = removeEndingCategoryNumber(trees);

      // Output trees
      int split = (int)(trainFrac*trees.size()+(1-1e-10));
      PrintWriter out;
      out = IOUtils.openOutHard(trainOutPath);
      for(Tree<String> tree : trees.subList(0, split)) out.println(tree);
      out.close();
      out = IOUtils.openOutHard(testOutPath);
      for(Tree<String> tree : trees.subList(split, trees.size())) out.println(tree);
      out.close();
    }
  }

  public static void main(String[] args) {
    Execution.run(args, new Main());
  }
}
