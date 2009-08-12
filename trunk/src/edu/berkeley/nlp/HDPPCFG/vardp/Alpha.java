package edu.berkeley.nlp.HDPPCFG.vardp;

import fig.basic.*;
import fig.prob.*;

/**
 * Concentration parameter.
 */
public class Alpha implements java.io.Serializable {
  private static final long serialVersionUID = 42L;

  private final static double epsilon = 1e-12;
  public double alpha;
  public boolean isTotal;

  public Alpha(double alpha, boolean isTotal) {
    this.alpha = alpha;
    this.isTotal = isTotal;
  }

  public Alpha(Alpha alpha) { set(alpha); }
  public void set(Alpha alpha) {
    this.alpha = alpha.alpha;
    this.isTotal = alpha.isTotal;
  }

  // Format: <double>[/]
  // 3.0/ means a (3/K, ..., 3/K) prior
  // 3.0 means a (3, ..., 3) prior
  public Alpha(String s) {
    if(s.endsWith("/")) {
      this.isTotal = true;
      s = s.substring(0, s.length()-1);
    }
    else
      this.isTotal = false;
    this.alpha = Double.parseDouble(s);
  }
  
  // This is for alpha * base distribution
  // isTotal = true: (alpha * base)
  // isTotal = false: (alpha*K * base)
  public double[] multPrior(double[] base) {
    base = ListUtils.shallowClone(base);
    for(int i = 0; i < base.length; i++)
      base[i] *= multFactor(base.length);
    return base;
  }
  public double multFactor(int n) {
    return isTotal ? alpha : alpha*n;
  }

  // For when for a top level prior
  // isTotal = true: (alpha/K * base)
  // isTotal = false: (alpha * base)
  public double getValue(int n) { return isTotal ? alpha/n : alpha; }

  public double[] addPriorComputeMAP(double[] counts) {
    counts = ListUtils.shallowClone(counts);
    for(int i = 0; i < counts.length; i++) {
      counts[i] += (isTotal ? alpha/counts.length : alpha) - 1;
      counts[i] = Math.max(counts[i], epsilon);
    }
    NumUtils.normalize(counts);
    return counts;
  }

  public static double[] computeMAP(double[] counts) {
    counts = ListUtils.shallowClone(counts);
    for(int i = 0; i < counts.length; i++) {
      counts[i]--;
      counts[i] = Math.max(counts[i], epsilon);
    }
    NumUtils.normalize(counts);
    return counts;
  }

  //public double getValue(int n) { return isTotal ? alpha/n : alpha; }

  public String toString() { return alpha+(isTotal?"/":""); }
}
