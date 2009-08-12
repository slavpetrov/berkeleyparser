package edu.berkeley.nlp.HDPPCFG;

import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.StateSet;
import edu.berkeley.nlp.util.ArrayUtil;
import edu.berkeley.nlp.util.Numberer;
import java.util.*;

/**
 * Simple mixture parser.
 */
public class ArrayParser implements Parser {
  // i dont know how to initialize shorts...
  short zero = 0, one = 1;
  protected Numberer tagNumberer = Numberer.getGlobalNumberer("tags");

  // inside scores
  protected double[][][] iScore; // start idx, end idx, state -> logProb

  // outside scores
  protected double[][][] oScore; // start idx, end idx, state -> logProb

  protected int[][] narrowLExtent = null; // the rightmost left extent of state
                                          // s ending at position i

  protected int[][] wideLExtent = null; // the leftmost left extent of state s
                                        // ending at position i

  protected int[][] narrowRExtent = null; // the leftmost right extent of state
                                          // s starting at position i

  protected int[][] wideRExtent = null; // the rightmost right extent of state s
                                        // starting at position i

  protected short length;

  protected int arraySize = 0;

  protected int myMaxLength = 200;

  LexiconInterface lexicon;

  int numStates;
  int maxNSubStates;
  int[] idxC;
  double[] scoresToAdd;
  int touchedRules;

  Grammar grammar;

  public ArrayParser(){
  }

  public ArrayParser(Grammar gr, LexiconInterface lex) {
  	this.touchedRules = 0;
    this.grammar = gr;
    this.lexicon = lex;
    this.tagNumberer = Numberer.getGlobalNumberer("tags");
    this.numStates = gr.numStates;
    this.maxNSubStates = maxSubStates(gr);
    this.idxC = new int[maxNSubStates];
    this.scoresToAdd = new double[maxNSubStates];
   // System.out.println("This grammar has " + numStates
   //     + " states and a total of " + grammar.totalSubStates() + " substates.");
  }

  @SuppressWarnings("unchecked")
  public List<Integer>[][] getPossibleStates(List<String> sentence, double logThreshold){
    length = (short)sentence.size();
    initializeArrays();
    initializeChart(sentence, false);

    doInsideScores();
    double score = iScore[0][length][0];
    if (score > Double.NEGATIVE_INFINITY) {
      System.out.println("\nFound a parse for sentence with length " + length
          + ". The LL is " + score + ".");
    } else {
      System.out.println("Did NOT find a parse for sentence with length "
          + length + ".");
    }
    oScore[0][length][tagNumberer.number("ROOT")] = 0.0;
    doOutsideScores();

    List<Integer>[][] possibleStates = new ArrayList[length + 1][length + 1];

    int unprunedStates = 0;
    int prunedStates = 0;

    double sentenceProb = iScore[0][length][0];
    for (int diff = 1; diff <= length; diff++) {
      for (int start = 0; start < (length - diff + 1); start++) {
        int end = start + diff;
        possibleStates[start][end] = new ArrayList<Integer>();
        for (int state = 0; state < numStates; state++) {
          double viterbiPosterior = iScore[start][end][state]
              + oScore[start][end][state] - sentenceProb;

          if (!Double.isInfinite(viterbiPosterior)) {
            unprunedStates++;
          }
          if (viterbiPosterior > logThreshold) {
            possibleStates[start][end].add(new Integer(state));
            prunedStates++;
            // if ((start==0)&&(end==length) )System.out.println(start+" "+end+"
            // "+state);
            // System.out.println("i "+iScore[start][end][state]+" o
            // "+oScore[start][end][state]+" v-pos: "+viterbiPosterior);
          }
        }
      }
    }
    System.out.print("Down to " + prunedStates + " states from "
        + unprunedStates + ". ");
    return possibleStates;

  }

//belongs in the grammar but i didnt want to change the signature for now...
  public int maxSubStates(Grammar grammar) {
    int max = 0;
    for (int i = 0; i < numStates; i++) {
      if (grammar.numSubStates[i]>max) max = grammar.numSubStates[i];
    }
    return max;
  }


  public Tree<String> getBestParse(List<String> sentence) {
    System.out
        .println("This parser assumes an unsplit grammar (= split grammar with 1 substate)");
    length = (short)sentence.size();
    initializeArrays();
    initializeChart(sentence, false);

    doInsideScores();
/*    for (int i = 0; i < numStates; i++) {
      // if (iScore[15][16][i] != null){
      if (iScore[12][13][i] > -30) {// != Double.NEGATIVE_INFINITY){//
        System.out.println(i + " " + (String) tagNumberer.object(i) + " "
            + iScore[12][13][i]);
      }
    }
*/
    // for (int i =0; i<numStates; i++){
    // if (iScore[0][1][i] != Double.NEGATIVE_INFINITY){
    // System.out.println(i + " " + (String) tagNumberer.object(i) + "
    // "+iScore[0][1][i]);}
    // }

    // oScore[0][length][tagNumberer.number("ROOT")] = 0.0f;
    // doOutsideScores();

    Tree<String> bestTree = new Tree<String>("ROOT");
    double score = iScore[0][length][tagNumberer.number("ROOT")];
    if (score > Double.NEGATIVE_INFINITY) {
      System.out.println("\nFound a parse for sentence with length " + length
          + ". The LL is " + score + ".");
      bestTree = extractBestParse(tagNumberer.number("ROOT"), 0, length,
          sentence);
      restoreUnaries(bestTree);
    } else {
      System.out.println("Did NOT find a parse for sentence with length "
          + length + ".");
    }

    return bestTree;
  }

  public boolean hasParse() {
    if (length > arraySize) {
      return false;
    }
    return (iScore[0][length][tagNumberer.number("ROOT")] > Double.NEGATIVE_INFINITY);
  }

  void initializeArrays() {
    if (length > arraySize) {
      if (length > myMaxLength) {
        throw new OutOfMemoryError("Refusal to create such large arrays.");
      } else {
        try {
          createArrays(length + 1);
        } catch (OutOfMemoryError e) {
          myMaxLength = length;
          if (arraySize > 0) {
            try {
              createArrays(arraySize);
            } catch (OutOfMemoryError e2) {
              throw new RuntimeException(
                  "CANNOT EVEN CREATE ARRAYS OF ORIGINAL SIZE!!!");
            }
          }
          throw e;
        }
      }
      arraySize = length + 1;
    }
    for (int start = 0; start < length; start++) {
      for (int end = start + 1; end <= length; end++) {
        Arrays.fill(iScore[start][end], Double.NEGATIVE_INFINITY);
        Arrays.fill(oScore[start][end], Double.NEGATIVE_INFINITY);
      }
    }
    for (int loc = 0; loc <= length; loc++) {
      Arrays.fill(narrowLExtent[loc], -1); // the rightmost left with state s
                                            // ending at i that we can get is
                                            // the beginning
      Arrays.fill(wideLExtent[loc], length + 1); // the leftmost left with
                                                  // state s ending at i that we
                                                  // can get is the end
      Arrays.fill(narrowRExtent[loc], length + 1); // the leftmost right with
                                                    // state s starting at i
                                                    // that we can get is the
                                                    // end
      Arrays.fill(wideRExtent[loc], -1); // the rightmost right with state s
                                          // starting at i that we can get is
                                          // the beginning
    }

  }

  void initializeChart(List<String> sentence, boolean noSmoothing) {
    // for simplicity the lexicon will store words and tags as strings,
    // while the grammar will be using integers -> Numberer()
    int start = 0;
    int end = start + 1;
    for (String word : sentence) {
      end = start + 1;
//      for (short tag : lexicon.getAllTags()) {
      for (short tag=0; tag<numStates; tag++){
      	if (grammar.isGrammarTag[tag]) continue;
        double prob = lexicon.score(word, tag, start, noSmoothing,false)[0];
        iScore[start][end][tag] = prob;
        narrowRExtent[start][tag] = end;
        narrowLExtent[end][tag] = start;
        wideRExtent[start][tag] = end;
        wideLExtent[end][tag] = start;
        /*
         * UnaryRule[] unaries = grammar.getClosedUnaryRulesByChild(state); for
         * (int r = 0; r < unaries.length; r++) { UnaryRule ur = unaries[r]; int
         * parentState = ur.parent; double pS = (double) ur.score; double tot =
         * prob + pS; if (tot > iScore[start][end][parentState]) {
         * iScore[start][end][parentState] = tot;
         * narrowRExtent[start][parentState] = end;
         * narrowLExtent[end][parentState] = start;
         * wideRExtent[start][parentState] = end; wideLExtent[end][parentState] =
         * start; } }
         */
      }
      //scaleIScores(start,end,0);
      start++;
    }
  }

  /**
   * Fills in the iScore array of each category over each span of length 2 or
   * more.
   *
   * Note: This places the grammar and lexicon into logarithm mode!
   */

  void doInsideScores() {
    grammar.logarithmMode();
    lexicon.logarithmMode();
    // for all symbol lengths
    for (int diff = 1; diff <= length; diff++) {
      // for all symbol starting positions
      for (int start = 0; start < (length - diff + 1); start++) {
        int end = start + diff;
        // for all symbols, calculate the inside score without unaries
        for (int pparentState = 0; pparentState < numStates; pparentState++) {
          BinaryRule[] parentRules = grammar.splitRulesWithP(pparentState);
          // for all rules with this parent symbol
          for (int i = 0; i < parentRules.length; i++) {
            BinaryRule r = parentRules[i];
            int leftState = r.leftChildState;
            int parentState = r.parentState;

            int narrowR = narrowRExtent[start][leftState];
            boolean iPossibleL = (narrowR < end); // can this left constituent
            // leave space for a right
            // constituent?
            if (!iPossibleL) {
              continue;
            }

            int narrowL = narrowLExtent[end][r.rightChildState];
            boolean iPossibleR = (narrowL >= narrowR); // can this right
            // constituent fit next
            // to the left
            // constituent?
            if (!iPossibleR) {
              continue;
            }

            int min1 = narrowR;
            int min2 = wideLExtent[end][r.rightChildState];
            int min = (min1 > min2 ? min1 : min2); // can this right
            // constituent stretch far
            // enough to reach the left
            // constituent?
            if (min > narrowL) {
              continue;
            }

            int max1 = wideRExtent[start][leftState];
            int max2 = narrowL;
            int max = (max1 < max2 ? max1 : max2); // can this left constituent
            // stretch far enough to
            // reach the right
            // constituent?
            if (min > max) {
              continue;
            }

            double pS = r.getScore(0, 0, 0);
            double oldIScore = iScore[start][end][parentState];
            double bestIScore = oldIScore;
            boolean foundBetter; // always set below for this rule

            for (int split = min; split <= max; split++) {
              double lS = iScore[start][split][leftState];
              if (Double.isInfinite(lS)) {
                continue;
              }
              double rS = iScore[split][end][r.rightChildState];
              if (Double.isInfinite(rS)) {
                continue;
              }
              touchedRules++;
              double tot = pS + lS + rS;

              if (tot > bestIScore) {
                bestIScore = tot;
              }
            }
            foundBetter = bestIScore > oldIScore;
            if (foundBetter) { // this way of making "parentState" is better
              // than previous
              iScore[start][end][parentState] = bestIScore;
              if (Double.isInfinite(oldIScore)) {
                if (start > narrowLExtent[end][parentState]) {
                  narrowLExtent[end][parentState] = start;
                  wideLExtent[end][parentState] = start;
                } else {
                  if (start < wideLExtent[end][parentState]) {
                    wideLExtent[end][parentState] = start;
                  }
                }
                if (end < narrowRExtent[start][parentState]) {
                  narrowRExtent[start][parentState] = end;
                  wideRExtent[start][parentState] = end;
                } else {
                  if (end > wideRExtent[start][parentState]) {
                    wideRExtent[start][parentState] = end;
                  }
                }
              }
            }
          }
        }
        // for all symbols, close all unary productions
        for (int pState = 0; pState < numStates; pState++) {
          UnaryRule[] unaries = grammar.getClosedViterbiUnaryRulesByParent(pState);
          double cur = iScore[start][end][pState];
          double best = cur;
          for (int r = 0; r < unaries.length; r++) {
            UnaryRule ur = unaries[r];
            int cState = ur.childState;
            if (pState == cState) continue;
            
            double pS = ur.getScore(0, 0);
            double iS = iScore[start][end][cState];
            if (Double.isInfinite(iS)) {
              continue;
            }

            double tot = iS + pS;
            touchedRules++;
            if (tot > best){
            	best = tot;
            }
          }
          if (best>cur) {
            iScore[start][end][pState] = best;
            if (cur == Double.NEGATIVE_INFINITY) {
              if (start > narrowLExtent[end][pState]) {
                narrowLExtent[end][pState] = start;
                wideLExtent[end][pState] = start;
              } else {
                if (start < wideLExtent[end][pState]) {
                  wideLExtent[end][pState] = start;
                }
              }
              if (end < narrowRExtent[start][pState]) {
                narrowRExtent[start][pState] = end;
                wideRExtent[start][pState] = end;
              } else {
                if (end > wideRExtent[start][pState]) {
                  wideRExtent[start][pState] = end;
                }
              }
            }
          }
        }// ~ for all symbols
      }// ~for all symbol starting positions
    }// ~for all symbol lengths
  }

  /** Calculate outside scores using internal arrays.
   *
   * Note: This places the grammar and lexicon into logarithm mode!
   */
  private void doOutsideScores() {
    grammar.logarithmMode();
    lexicon.logarithmMode();
    //TODO: this almost certainly underflows!
    for (int diff = length; diff >= 1; diff--) {
      for (int start = 0; start + diff <= length; start++) {
        int end = start + diff;
        // do unaries
        for (int s = 0; s < numStates; s++) {
          double oS = oScore[start][end][s];
          if (Double.isInfinite(oS)) {
            continue;
          }
          UnaryRule[] rules = grammar.getClosedViterbiUnaryRulesByParent(s);
          for (int r = 0; r < rules.length; r++) {
            UnaryRule ur = rules[r];
            double pS = ur.getScore(0, 0);
            double tot = oS + pS;
            touchedRules++;
            if (tot > oScore[start][end][ur.childState]
                && iScore[start][end][ur.childState] > Double.NEGATIVE_INFINITY) {
              oScore[start][end][ur.childState] = tot;
            }
          }
        }
        // do binaries
        for (int s = 0; s < numStates; s++) {
          BinaryRule[] rules = grammar.splitRulesWithP(s);
          for (int r = 0; r < rules.length; r++) {
            BinaryRule br = rules[r];
            double oS = oScore[start][end][br.parentState];
            if (Double.isInfinite(oS)) {
              continue;
            }
            int min1 = narrowRExtent[start][br.leftChildState];
            if (end < min1) {
              continue;
            }
            int max1 = narrowLExtent[end][br.rightChildState];
            if (max1 < min1) {
              continue;
            }
            int min = min1;
            int max = max1;
            if (max - min > 2) {
              int min2 = wideLExtent[end][br.rightChildState];
              min = (min1 > min2 ? min1 : min2);
              if (max1 < min) {
                continue;
              }
              int max2 = wideRExtent[start][br.leftChildState];
              max = (max1 < max2 ? max1 : max2);
              if (max < min) {
                continue;
              }
            }
            double pS = br.getScore(0, 0, 0);
            for (int split = min; split <= max; split++) {
              double lS = iScore[start][split][br.leftChildState];
              if (Double.isInfinite(lS)) {
                continue;
              }
              double rS = iScore[split][end][br.rightChildState];
              if (Double.isInfinite(rS)) {
                continue;
              }
              double totL = pS + rS + oS;
              touchedRules++;
              if (totL > oScore[start][split][br.leftChildState]) {
                oScore[start][split][br.leftChildState] = totL;
              }
              double totR = pS + lS + oS;
              if (totR > oScore[split][end][br.rightChildState]) {
                oScore[split][end][br.rightChildState] = totR;
              }
            }
          }
        }
/*        for (int s = 0; s < numStates; s++) {
          int max1 = narrowLExtent[end][s];
          if (max1 < start) {
            continue;
          }
          BinaryRule[] rules = grammar.splitRulesWithRC(s);
          for (int r = 0; r < rules.length; r++) {
            BinaryRule br = rules[r];
            double oS = oScore[start][end][br.parentState];
            if (Double.isInfinite(oS)) {
              continue;
            }
            int min1 = narrowRExtent[start][br.leftChildState];
            if (max1 < min1) {
              continue;
            }
            int min = min1;
            int max = max1;
            if (max - min > 2) {
              int min2 = wideLExtent[end][br.rightChildState];
              min = (min1 > min2 ? min1 : min2);
              if (max1 < min) {
                continue;
              }
              int max2 = wideRExtent[start][br.leftChildState];
              max = (max1 < max2 ? max1 : max2);
              if (max < min) {
                continue;
              }
            }
            double pS = br.getScore(0, 0, 0);
            for (int split = min; split <= max; split++) {
              double lS = iScore[start][split][br.leftChildState];
              if (Double.isInfinite(lS)) {
                continue;
              }
              double rS = iScore[split][end][br.rightChildState];
              if (Double.isInfinite(rS)) {
                continue;
              }
              double totL = pS + rS + oS;
              if (totL > oScore[start][split][br.leftChildState]) {
                oScore[start][split][br.leftChildState] = totL;
              }
              double totR = pS + lS + oS;
              if (totR > oScore[split][end][br.rightChildState]) {
                oScore[split][end][br.rightChildState] = totR;
              }
            }
          }
        }*/
      }
    }
  }

  /**
   * Calculate the inside scores, P(words_i,j|nonterminal_i,j) of a tree given
   * the string if words it should parse to.
   *
   * @param tree
   * @param sentence
   */
  void doInsideScores(Tree<StateSet> tree, boolean noSmoothing, boolean debugOutput) {
    if (grammar.isLogarithmMode() || lexicon.isLogarithmMode())
      throw new Error("Grammar in logarithm mode!  Cannot do inside scores!");
    if (tree.isLeaf()){
      return;
    }
    List<Tree<StateSet>> children = tree.getChildren();
    for (Tree<StateSet> child : children) {
      if (!child.isLeaf()) doInsideScores(child, noSmoothing, debugOutput);
    }
    StateSet parent = tree.getLabel();
    short pState = parent.getState();
    int nParentStates = parent.numSubStates();
    if (tree.isPreTerminal()) {
      // Plays a role similar to initializeChart()
      String word = tree.getChildren().get(0).getLabel().getWord();
      int pos = tree.getChildren().get(0).getLabel().from;
      double[] lexiconScores = lexicon.score(word, pState, pos, noSmoothing,false);
      if (lexiconScores.length!=nParentStates){ // truncate the array
        double[] truncated = new double[nParentStates];
        for (int s=0; s<nParentStates; s++){
          truncated[s]=lexiconScores[s];
        }
        parent.setIScores(truncated);
      }
      else{
        parent.setIScores(lexiconScores);
      }
      parent.scaleIScores(0);
    } else {
      switch (children.size()) {
      case 0:
        break;
      case 1:
        StateSet child = children.get(0).getLabel();
        short cState = child.getState();
        int nChildStates = child.numSubStates();
        double[][] uscores = grammar.getUnaryScore(pState,cState);
        double[] iScores = new double[nParentStates];
        boolean foundOne = false;
        for (int j = 0; j < nChildStates; j++) {
          if (uscores[j]!=null) { //check whether one of the parents can produce this child
            double cS = child.getIScore(j);
            if (cS==0) continue;
            for (int i = 0; i < nParentStates; i++) {
              double rS = uscores[j][i]; // rule score
              if (rS==0) continue;
              double res = rS * cS;
              /*if (res == 0) {
                System.out.println("Prevented an underflow: rS "+rS+" cS "+cS);
                res = Double.MIN_VALUE;
              }*/
              iScores[i] += res;
              foundOne = true;
            }
          }
        }
        //if (!foundOne) System.out.println("Did not find a way to build unary transition from "+pState+" to "+cState+" "+ArrayUtil.toString(uscores));
        if (debugOutput && !foundOne) {
          System.out.println("iscore reached zero!");
          System.out.println(grammar.getUnaryRule(pState,cState));
          System.out.println(Arrays.toString(iScores));
          System.out.println(ArrayUtil.toString(uscores));
          System.out.println(Arrays.toString(child.getIScores()));
        }
        parent.setIScores(iScores);
        parent.scaleIScores(child.getIScale());
        break;
      case 2:
        StateSet leftChild = children.get(0).getLabel();
        StateSet rightChild = children.get(1).getLabel();
        int nLeftChildStates = leftChild.numSubStates();
        int nRightChildStates = rightChild.numSubStates();
        short lState = leftChild.getState();
        short rState = rightChild.getState();
        double[][][] bscores = grammar.getBinaryScore(pState,lState,rState);
        double[] iScores2 = new double[nParentStates];
        boolean foundOne2 = false;
        for (int j = 0; j < nLeftChildStates; j++) {
          double lcS = leftChild.getIScore(j);
          if (lcS==0) continue;
          for (int k = 0; k < nRightChildStates; k++) {
            double rcS = rightChild.getIScore(k);
            if (rcS==0) continue;
            if (bscores[j][k]!=null) { // check whether one of the parents can produce these kids
              for (int i = 0; i < nParentStates; i++) {
                double rS = bscores[j][k][i];
                if (rS==0) continue;
                double res = rS * lcS * rcS;
                /*if (res == 0) {
                  System.out.println("Prevented an underflow: rS "+rS+" lcS "+lcS+" rcS "+rcS);
                  res = Double.MIN_VALUE;
                }*/
                iScores2[i] += res;
                foundOne2 = true;
              }
            }
          }
        }
        //if (!foundOne2) System.out.println("Did not find a way to build binary transition from "+pState+" to "+lState+" and "+rState+" "+ArrayUtil.toString(bscores));
        if (debugOutput && !foundOne2) {
          System.out.println("iscore reached zero!");
          System.out.println(grammar.getBinaryRule(pState,lState,rState));
          System.out.println(Arrays.toString(iScores2));
          System.out.println(Arrays.toString(bscores));
          System.out.println(Arrays.toString(leftChild.getIScores()));
          System.out.println(Arrays.toString(rightChild.getIScores()));
        }
        parent.setIScores(iScores2);
        parent.scaleIScores(leftChild.getIScale()+rightChild.getIScale());
        break;
      default:
        throw new Error("Malformed tree: more than two children");
      }
    }
  }

  /**
   * Set the outside score of the root node to P=1.
   *
   * @param tree
   */
  void setRootOutsideScore(Tree<StateSet> tree) {
    tree.getLabel().setOScore(0, 1);
    tree.getLabel().setOScale(0);
  }

  /**
   * Calculate the outside scores of a tree; that is,
   * P(nonterminal_i,j|words_0,i; words_j,end). It is calculate from the inside
   * scores of the tree.
   *
   * <p>
   * Note: when calling this, call setRootOutsideScore() first.
   *
   * @param tree
   */
  void doOutsideScores(Tree<StateSet> tree) {
    if (grammar.isLogarithmMode() || lexicon.isLogarithmMode())
      throw new Error("Grammar in logarithm mode!  Cannot do inside scores!");
    if (tree.isLeaf())
      return;
    List<Tree<StateSet>> children = tree.getChildren();
    StateSet parent = tree.getLabel();
    short pState = parent.getState();
    int nParentStates = parent.numSubStates();
    // this sets the outside scores for the children
    if (tree.isPreTerminal()) {

    } else {
      switch (children.size()) {
      case 0:
        // Nothing to do
        break;
      case 1:
        StateSet child = children.get(0).getLabel();
        short cState = child.getState();
        int nChildStates = child.numSubStates();
        //UnaryRule uR = new UnaryRule(pState,cState);
        double[][] uscores = grammar.getUnaryScore(pState,cState);
        double[] oScores = new double[nChildStates];
        for (int j = 0; j < nChildStates; j++) {
          if (uscores[j]!=null){
            double childScore = 0;
            for (int i = 0; i < nParentStates; i++) {
              double pS = parent.getOScore(i);
              if (pS == 0) continue;
              double rS = uscores[j][i]; // rule score
              if (rS == 0) continue;
              childScore += pS * rS;
            }
            oScores[j] = childScore;
          }
        }
        child.setOScores(oScores);
        child.scaleOScores(parent.getOScale());
        break;
      case 2:
        StateSet leftChild = children.get(0).getLabel();
        StateSet rightChild = children.get(1).getLabel();
        int nLeftChildStates = leftChild.numSubStates();
        int nRightChildStates = rightChild.numSubStates();
        short lState = leftChild.getState();
        short rState = rightChild.getState();
        //double[] leftScoresToAdd -> use childScores array instead = new double[nRightChildStates * nParentStates];
        //double[][] rightScoresToAdd -> use binaryScores array instead = new double[nRightChildStates][nLeftChildStates * nParentStates];
        double[][][] bscores = grammar.getBinaryScore(pState,lState,rState);
        double[] lOScores = new double[nLeftChildStates];
        double[] rOScores = new double[nRightChildStates];
        for (int j = 0; j < nLeftChildStates; j++) {
          double lcS = leftChild.getIScore(j);
          double leftScore = 0;
          for (int k = 0; k < nRightChildStates; k++) {
            double rcS = rightChild.getIScore(k);
            if (bscores[j][k]!=null){
              for (int i = 0; i < nParentStates; i++) {
                double pS = parent.getOScore(i);
                if (pS==0) continue;
                double rS = bscores[j][k][i];
                if (rS==0) continue;
                leftScore += pS * rS * rcS;
                rOScores[k] += pS * rS * lcS;
              }
            }
          }
          lOScores[j] = leftScore;
        }
        leftChild.setOScores(lOScores);
        leftChild.scaleOScores(parent.getOScale()+rightChild.getIScale());
        rightChild.setOScores(rOScores);
        rightChild.scaleOScores(parent.getOScale()+leftChild.getIScale());
        break;
      default:
        throw new Error("Malformed tree: more than two children");
      }
      for (Tree<StateSet> child : children) {
        doOutsideScores(child);
      }
    }
  }

  public void doInsideOutsideScores(Tree<StateSet> tree, boolean noSmoothing, boolean debugOutput) {
    doInsideScores(tree, noSmoothing, debugOutput);
    setRootOutsideScore(tree);
    doOutsideScores(tree);
  }

  private void createArrays(int length) {

    // zero out some stuff first in case we recently ran out of memory and are
    // reallocating
    clearArrays();

    // allocate just the parts of iScore and oScore used (end > start, etc.)
    // System.out.println("initializing iScore arrays with length " + length + "
    // and numStates " + numStates);
    iScore = new double[length][length + 1][];
    for (int start = 0; start < length; start++) {
      for (int end = start + 1; end <= length; end++) {
        iScore[start][end] = new double[numStates];
      }
    }
    // System.out.println("finished initializing iScore arrays");
    // System.out.println("initializing oScore arrays with length " + length + "
    // and numStates " + numStates);
    oScore = new double[length][length + 1][];
    for (int start = 0; start < length; start++) {
      for (int end = start + 1; end <= length; end++) {
        oScore[start][end] = new double[numStates];
      }
    }
    // System.out.println("finished initializing oScore arrays");

    // iPossibleByL = new boolean[length + 1][numStates];
    // iPossibleByR = new boolean[length + 1][numStates];
    narrowRExtent = new int[length + 1][numStates];
    wideRExtent = new int[length + 1][numStates];
    narrowLExtent = new int[length + 1][numStates];
    wideLExtent = new int[length + 1][numStates];
    /*
     * (op.doDep) { oPossibleByL = new boolean[length + 1][numStates];
     * oPossibleByR = new boolean[length + 1][numStates];
     *
     * oFilteredStart = new boolean[length + 1][numStates]; oFilteredEnd = new
     * boolean[length + 1][numStates]; } tags = new boolean[length +
     * 1][numTags];
     *
     * if (Test.lengthNormalization) { wordsInSpan = new int[length + 1][length +
     * 1][]; for (int start = 0; start <= length; start++) { for (int end =
     * start + 1; end <= length; end++) { wordsInSpan[start][end] = new
     * int[numStates]; } } }
     */// System.out.println("ExhaustivePCFGParser constructor finished.");
  }

  protected void clearArrays() {
    iScore = oScore = null;
    // iPossibleByL = iPossibleByR = oFilteredEnd = oFilteredStart =
    // oPossibleByL = oPossibleByR = tags = null;
    narrowRExtent = wideRExtent = narrowLExtent = wideLExtent = null;
  }

  // borrowed from the stanford parser
  /**
   * Return all best parses (except no ties allowed on POS tags?). Note that the
   * returned tree may be missing intermediate nodes in a unary chain because it
   * parses with a unary-closed grammar.
   */
  public Tree<String> extractBestParse(int goal, int start, int end,
      List<String> sentence) {
    grammar.logarithmMode();
    lexicon.logarithmMode();
    // find sources of inside score
    // no backtraces so we can speed up the parsing for its primary use
    double bestScore = iScore[start][end][goal];
    String goalStr = (String) tagNumberer.object(goal);
//    System.out.println("Looking for " + goalStr + " from " + start + " to " + end + " with score " + bestScore + ".");
    if (end - start == 1) {
      // handle the (pre)terminal nodes differently
      // System.out.println("Tag node: "+goalStr);
      // check whether there is a rewrite that is actually better

      // if the lexicon contains the goal, then we're already at
      // the preterminal level, so we don't need to try to find any
      // unary rules to get to a preterminal tag
     	if (!grammar.isGrammarTag[goal]){
//      if (lexicon.getAllTags().contains(goal)) {
        List<Tree<String>> child = new ArrayList<Tree<String>>();
        child.add(new Tree<String>(sentence.get(start)));
        return new Tree<String>(goalStr, child);
      }
      // if the lexicon does not contain the goal, then we must find
      // the best way to get from the goal tag to a preterminal tag
      else {
        double veryBestScore = Double.NEGATIVE_INFINITY;
        int newIndex = -1;
        UnaryRule[] unaries = grammar.getClosedViterbiUnaryRulesByParent(goal);
        for (int r = 0; r < unaries.length; r++) {
          UnaryRule ur = unaries[r];
          double ruleScore = iScore[start][end][ur.childState] + grammar.getUnaryScore(ur)[0][0];
          if ((ruleScore > veryBestScore) && (goal != ur.childState)
              	&& (!grammar.isGrammarTag[ur.getChildState()])){

//              && lexicon.getAllTags().contains(ur.getChildState())) {
            veryBestScore = ruleScore;
            newIndex = ur.childState;
          }
        }

        // insert the nonterminal tag into the tree
        List<Tree<String>> child1 = new ArrayList<Tree<String>>();
        child1.add(new Tree<String>(sentence.get(start)));
        String goalStr1 = (String) tagNumberer.object(newIndex);

        List<Tree<String>> child = new ArrayList<Tree<String>>();
        child.add(new Tree<String>(goalStr1, child1));
        return new Tree<String>(goalStr, child);
      }
      /*
       * IntTaggedWord tagging = new IntTaggedWord(words[start],
       * tagNumberer.number(goalStr)); double tagScore = lex.score(tagging,
       * start); if (tagScore > Double.NEGATIVE_INFINITY || floodTags) { //
       * return a pre-terminal tree String wordStr = (String)
       * wordNumberer.object(words[start]); Tree wordNode = tf.newLeaf(new
       * StringLabel(wordStr)); List childList = new ArrayList();
       * childList.add(wordNode); Tree tagNode = tf.newTreeNode(new
       * StringLabel(goalStr), childList); //System.out.println("Tag node:
       * "+tagNode); return Collections.singletonList(tagNode); }
       */
    }
    // check binaries first
    for (int split = start + 1; split < end; split++) {
      BinaryRule[] parentRules = grammar.splitRulesWithP(goal);
      for (int i = 0; i < parentRules.length; i++) {
        BinaryRule br = parentRules[i];
        double score = br.getScore(0, 0, 0)
            + iScore[start][split][br.leftChildState]
            + iScore[split][end][br.rightChildState];
        if (matches(score, bestScore)) {
          // build binary split
          Tree<String> leftChildTree = extractBestParse(br.leftChildState,
              start, split, sentence);
          Tree<String> rightChildTree = extractBestParse(br.rightChildState,
              split, end, sentence);
          List<Tree<String>> children = new ArrayList<Tree<String>>();
          children.add(leftChildTree);
          children.add(rightChildTree);
          Tree<String> result = new Tree<String>(goalStr, children);
          // System.out.println("Binary node: "+result);
          // result.setScore(score);
          return result;
        }
      }
    }
    // check unaries
    UnaryRule[] unaries = grammar.getClosedViterbiUnaryRulesByParent(goal);
    for (int r = 0; r < unaries.length; r++) {
      UnaryRule ur = unaries[r];
      double score = ur.getScore(0, 0) + iScore[start][end][ur.childState];
      if (ur.childState != ur.parentState && matches(score, bestScore)) {
        // build unary
        Tree<String> childTree = extractBestParse(ur.childState, start, end,
            sentence);
        List<Tree<String>> children = new ArrayList<Tree<String>>();
        children.add(childTree);
        Tree<String> result = new Tree<String>(goalStr, children);
        // System.out.println("Unary node: "+result);
        // result.setScore(score);
        return result;
      }
    }
    System.err.println("Warning: no parse found");
    return null;
  }

  protected void restoreUnaries(Tree<String> t) {
    // System.out.println("In restoreUnaries...");
    for (Iterator nodeI = t.subTreeList().iterator(); nodeI.hasNext();) {
      Tree<String> node = (Tree<String>) nodeI.next();
      // System.out.println("Doing node: "+node.getLabel());
      if (node.isLeaf() || node.isPreTerminal()
          || node.getChildren().size() != 1) {
        // System.out.println("Skipping node: "+node.getLabel());
        continue;
      }
      // System.out.println("Not skipping node: "+node.getLabel());
      Tree<String> parent = node;
      // Tree<String> child = node.getChildren().get(0);
      short pLabel = (short)tagNumberer.number(parent.getLabel());
      short cLabel = (short)tagNumberer.number(node.getChildren().get(0).getLabel());
      // List path =
      // grammar.getBestPath(stateNumberer.number(parent.getLabel().value()),
      // stateNumberer.number(child.label().value().toString()));
      // if (grammar.getUnaryScore(new UnaryRule(pLabel,cLabel))[0][0] == 0){
      // continue; }// means the rule was already in grammar
      List<short[]> path = grammar.getBestViterbiPath(pLabel, (short)0, cLabel, (short)0);
      // System.out.println("Got path for "+pLabel + " to " + cLabel + " via " +
      // path);
      for (int pos=1; pos < path.size() - 1; pos++) {
        int tmp = path.get(pos)[0];
        int interState = tmp;
        Tree<String> intermediate = new Tree<String>((String) tagNumberer
            .object(interState), parent.getChildren());
        List<Tree<String>> children = new ArrayList<Tree<String>>();
        children.add(intermediate);
        parent.setChildren(children);
        parent = intermediate;
      }
    }
  }

  private static final double TOL = 1e-5;

  protected static boolean matches(double x, double y) {
    return (Math.abs(x - y) / (Math.abs(x) + Math.abs(y) + 1e-10) < TOL);
  }

  // this doesnt really belong here, but lets put it here for now
  /*public void scaleIScores(int start, int end, int previousScale){
    int logScale = 0;
    double scale = 1.0;
    double max = ArrayMath.max(iScore[start][end]);
    while (max > GrammarTrainer.SCALE) {
      max /= GrammarTrainer.SCALE;
      scale *= GrammarTrainer.SCALE;
      logScale += 1;
    }
    while (max > 0.0 && max < 1.0 / GrammarTrainer.SCALE) {
      max *= GrammarTrainer.SCALE;
      scale /= GrammarTrainer.SCALE;
      logScale -= 1;
    }
    if (logScale != 0) {
      for (int i = 0; i < numStates; i++) {
        iScore[start][end][i] /= scale;
      }
    }
    iScale[start][end] = previousScale + logScale;
  }
*/


}
