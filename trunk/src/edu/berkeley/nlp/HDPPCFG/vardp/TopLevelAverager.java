package edu.berkeley.nlp.HDPPCFG.vardp;

import java.util.*;
import fig.basic.*;
import fig.prob.*;
import edu.berkeley.nlp.HDPPCFG.*;

/**
 * Just collects the rule probabilities of the 
 */
public class TopLevelAverager {
  private final TopLevelDistrib[] topDistribs;
  private final Set<UnaryRule> unaryRules;
  private final Set<BinaryRule> binaryRules;
  private final int C; // Number of categories
  
  private int J(int c) { return topDistribs[c].dim(); }

  public TopLevelAverager(TopLevelDistrib[] topDistribs,
      Set<UnaryRule> unaryRules, Set<BinaryRule> binaryRules) {
    this.topDistribs = topDistribs;
    this.unaryRules = unaryRules;
    this.binaryRules = binaryRules;
    this.C = topDistribs.length;
  }

  public double[][] getCounts() {
    // Set the state probability to how often they were used
    double[][] counts = new double[C][];
    for(int c = 0; c < C; c++)
      counts[c] = new double[J(c)];

    for(UnaryRule rule : unaryRules) {
      int c = rule.parentState;
      int d = rule.childState;
      UnaryDirichletCollection dc = (UnaryDirichletCollection)rule.params;
      if(dc == null) continue; // Skip weird unaries
      for(int j = 0; j < J(c); j++) {
        double[] alpha = ((Dirichlet)dc.getParams()[j].getPrior()).getAlpha();
        ListUtils.incr(counts[d], 1, alpha);
      }
    }

    for(BinaryRule rule : binaryRules) {
      int c = rule.parentState;
      int d = rule.leftChildState;
      int e = rule.rightChildState;
      BinaryDirichletCollection dc = (BinaryDirichletCollection)rule.params;
      assert dc != null;
      for(int j = 0; j < J(c); j++) {
        double[] alpha = ((Dirichlet)dc.getParams()[j].getPrior()).getAlpha();
        for(int k = 0; k < J(d); k++) {
          for(int l = 0; l < J(e); l++) {
            double alphaElem = alpha[k*J(e)+l];
            counts[d][k] += alphaElem;
            counts[e][l] += alphaElem;
          }
        }
      }
    }
    return counts;
  }
}
