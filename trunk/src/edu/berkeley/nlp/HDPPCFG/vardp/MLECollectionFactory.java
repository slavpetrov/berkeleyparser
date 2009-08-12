package edu.berkeley.nlp.HDPPCFG.vardp;

import java.io.Serializable;
import fig.basic.*;

public class MLECollectionFactory implements DiscreteDistribCollectionFactory {
  public TopLevelDistrib newTopLevelState(int n) { return null; }
  public TopLevelDistrib newTopLevelSubstate(int n) { return null; }
  public TopLevelWordDistrib newTopLevelWord() { return null; }

  public void estimationMethodChanged() { }

  public DiscreteDistrib newWord(TopLevelWordDistrib top, int nw) {
    return new MLEDD();
  }
  public UnaryDiscreteDistribCollection newUnaryState(TopLevelDistrib top, int nc, int np) {
    return new UnaryMLECollection(nc, np);
  }
  public BinaryDiscreteDistribCollection newBinaryState(TopLevelDistrib top1, TopLevelDistrib top2,
      int nc1, int nc2, int np) {
    return new BinaryMLEDistribCollection(nc1, nc2, np);
  }
  public UnaryDiscreteDistribCollection newUnarySubstate(TopLevelDistrib top, int nc, int np) {
    return new UnaryMLECollection(nc, np);
  }
  public BinaryDiscreteDistribCollection newBinarySubstate(TopLevelDistrib top1, TopLevelDistrib top2,
      int nc1, int nc2, int np) {
    return new BinaryMLEDistribCollection(nc1, nc2, np);
  }
  public DiscreteDistrib newRule() {
  	return new MLEDD(); // distribution over unary or binary rules
  }
}

////////////////////////////////////////////////////////////

class MLEDD implements DiscreteDistrib, Serializable {
	private static final long serialVersionUID = 1L;
	private double[] probs;

  public void update(double[] counts) {
    probs = ListUtils.shallowClone(counts);
    NumUtils.normalize(probs);
    //System.out.println("MODE: " + Fmt.D(probs)); // TMP
  }
  public double[] getScores() { return probs; }
}

////////////////////////////////////////////////////////////

class UnaryMLECollection implements UnaryDiscreteDistribCollection, Serializable {
	private static final long serialVersionUID = 1L;
	private double[][] probs;
  private int nc, np;

  public UnaryMLECollection(int nc, int np) {
    this.nc = nc;
    this.np = np;
  }

  // child substate, parent substate
  public void update(double[][] counts) {
    if(probs == null) probs = new double[nc][np];

    // Compute counts
    double[] sums = new double[np];
    for(int pi = 0; pi < np; pi++)
      for(int ci = 0; ci < nc; ci++)
        if(counts[ci] != null) sums[pi] += counts[ci][pi];

    // Normalize
    for(int ci = 0; ci < nc; ci++) {
      if(counts[ci] == null) { probs[ci] = null; continue; }
      for(int pi = 0; pi < np; pi++)
        if (sums[pi]!=0) probs[ci][pi] = counts[ci][pi] / sums[pi];
    }

    // TMP
    /*for(int pi = 0; pi < np; pi++) {
      double[] v = new double[nc];
      for(int ci = 0; ci < nc; ci++)
        if(probs[ci] != null) v[ci] = probs[ci][pi];
      System.out.println("MODE1 " + pi + ": " + Fmt.D(v));
    }*/
  }

  public double[][] getScores() { return probs; }
}

////////////////////////////////////////////////////////////

class BinaryMLEDistribCollection implements BinaryDiscreteDistribCollection, Serializable {
	private static final long serialVersionUID = 1L;
	private double[][][] probs;
  private int nc1, nc2, np;

  public BinaryMLEDistribCollection(int nc1, int nc2, int np) {
    this.nc1 = nc1;
    this.nc2 = nc2;
    this.np = np;
  }

  // child substate, parent substate
  public void update(double[][][] counts) {
    if(probs == null) probs = new double[nc1][nc2][np];

    // Compute counts
    double[] sums = new double[np];
    for(int ci1 = 0; ci1 < nc1; ci1++){
      for(int ci2 = 0; ci2 < nc2; ci2++){
      	if(counts[ci1][ci2] == null) continue;
      	for(int pi = 0; pi < np; pi++)
           sums[pi] += counts[ci1][ci2][pi];
      }
    }

    // Normalize
    for(int ci1 = 0; ci1 < nc1; ci1++) {
      for(int ci2 = 0; ci2 < nc2; ci2++) {
        if(counts[ci1][ci2] == null) { probs[ci1][ci2] = null; continue; }
        for(int pi = 0; pi < np; pi++)
          if (sums[pi]!=0) probs[ci1][ci2][pi] = counts[ci1][ci2][pi] / sums[pi];
      }
    }

    // TMP
    /*for(int pi = 0; pi < np; pi++) {
      double[] v = new double[nc1*nc2];
      for(int ci1 = 0; ci1 < nc1; ci1++)
        for(int ci2 = 0; ci2 < nc2; ci2++)
          if(probs[ci1][ci2] != null) v[ci1*nc2+ci2] = probs[ci1][ci2][pi];
      System.out.println("MODE2 " + pi + ": " + Fmt.D(v));
    }*/
  }

  public double[][][] getScores() { return probs; }
}
