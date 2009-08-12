package edu.berkeley.nlp.HDPPCFG.vardp;

import java.io.Serializable;
import java.util.*;
import fig.basic.*;
import fig.prob.*;

interface DigammaComputerInterface {
  public double[] expExpectedLog(double[] counts);
  public double expDigamma(double count);
}
class FastDigammaComputer implements DigammaComputerInterface {
  public double[] expExpectedLog(double[] counts) { return DirichletUtils.fastExpExpectedLog(counts); }
  public double expDigamma(double count) { return DirichletUtils.fastExpDigamma(count); }
}
class DigammaComputer implements DigammaComputerInterface {
  public double[] expExpectedLog(double[] counts) { return DirichletUtils.expExpectedLog(counts); }
  public double expDigamma(double count) { return Math.exp(NumUtils.digamma(count)); }
}

/**
 * A simple multinomial distribution.
 * Can be optimized.
 */
class TopLevelOfDirichlet implements TopLevelDistrib, Serializable {
  private static final long serialVersionUID = 42L;

  // Specifies Dirichlet prior
  private Alpha alpha;
  private double[] probs;

  public TopLevelOfDirichlet(int n, Alpha alpha, Random random, double noise) {
    this.alpha = alpha;
    this.probs = new double[n];
    for(int i = 0; i < n; i++)
      probs[i] = 1 + random.nextDouble()*noise + edu.berkeley.nlp.HDPPCFG.Grammar.initBias(i, n);
    NumUtils.normalize(probs);
  }

  public double[] getProbs() { return probs; }
  public double getProb(int i) { return probs[i]; }
  //public void setProbs(double[] probs) { LogInfo.stderr.println("SET " + Fmt.D(probs)); this.probs = probs; }
  public void setProbs(double[] probs) { this.probs = probs; }
  public int dim() { return probs.length; }
  public Alpha getAlpha() { return alpha; }

  public void updateDirect(double[] counts) {
    //LogInfo.dbg(Fmt.D(counts));
    //probs = alpha.addPriorComputeMAP(counts);
    // Ignore prior
    probs = ListUtils.shallowClone(counts);
    Utils.normalize(probs);
  }
}

////////////////////////////////////////////////////////////

public class DirichletCollectionFactory implements DiscreteDistribCollectionFactory {
  private VarDPOptions options;
  private Ref<VarDPOptions.EstimationMethod> estimationMethod;
  public DirichletCollectionFactory(VarDPOptions options) {
    this.options = options;
    this.estimationMethod = new Ref<VarDPOptions.EstimationMethod>();
    estimationMethodChanged();
  }

  public void estimationMethodChanged() {
    this.estimationMethod.value = options.estimationMethod;
  }

  public TopLevelDistrib newTopLevelState(int n) {
    return new TopLevelOfDirichlet(n, options.topStateAlpha, options.initRandom, options.initTopLevelNoise);
  }
  public TopLevelDistrib newTopLevelSubstate(int n) {
    return new TopLevelOfDirichlet(n, options.topSubstateAlpha, options.initRandom, options.initTopLevelNoise);
  }
  public TopLevelWordDistrib newTopLevelWord() {
    return new UniformWordDistrib();
  }

  public DiscreteDistrib newWord(TopLevelWordDistrib top, int nw) {
    return new DirichletDD(top, nw, options.wordAlpha, estimationMethod, digammaComputer());
  }
  public UnaryDiscreteDistribCollection newUnaryState(
      TopLevelDistrib top, int nc, int np) {
    return new UnaryDirichletCollection(top, nc, np, options.stateAlpha, estimationMethod, digammaComputer());
  }
  public BinaryDiscreteDistribCollection newBinaryState(
      TopLevelDistrib top1, TopLevelDistrib top2, int nc1, int nc2, int np) {
    return new BinaryDirichletCollection(top1, top2, nc1, nc2, np, options.stateAlpha, estimationMethod, digammaComputer());
  }
  public UnaryDiscreteDistribCollection newUnarySubstate(
      TopLevelDistrib top, int nc, int np) {
    return new UnaryDirichletCollection(top, nc, np, options.substateAlpha, estimationMethod, digammaComputer());
  }
  public BinaryDiscreteDistribCollection newBinarySubstate(
      TopLevelDistrib top1, TopLevelDistrib top2, int nc1, int nc2, int np) {
    return new BinaryDirichletCollection(top1, top2, nc1, nc2, np, options.substateAlpha, estimationMethod, digammaComputer());
  }
  public DiscreteDistrib newRule() {
    // distribution over unary or binary rules
  	return new DirichletDD(new UniformWordDistrib(), 2, new Alpha(1, false), estimationMethod, digammaComputer());
  }

  //private boolean map() { return options.estimationMethod == VarDPOptions.EstimationMethod.map; }
  private DigammaComputerInterface digammaComputer() {
    return options.useFastExpExpectedLog ? new FastDigammaComputer() : new DigammaComputer();
  }
}

////////////////////////////////////////////////////////////

class DirichletDD implements DiscreteDistrib, Serializable {
  private static final long serialVersionUID = 42L;

  private TopLevelWordDistrib top; // Parameters of the Dirichlet prior
  private int nw; // Number of words
  private Alpha alpha;
  private Ref<VarDPOptions.EstimationMethod> estimationMethod;
  private MargMultinomial params;
  private transient DigammaComputerInterface dc;

  public DirichletDD(TopLevelWordDistrib top, int nw, Alpha alpha, Ref<VarDPOptions.EstimationMethod> estimationMethod, DigammaComputerInterface dc) {
    this.top = top;
    this.nw = nw;
    this.alpha = alpha;
    this.estimationMethod = estimationMethod;
    this.dc = dc;
  }

  private MargMultinomial getPrior() {
    return new MargMultinomial(new Dirichlet(alpha.multPrior(top.getBase(nw))));
  }

  public void update(double[] counts) {
    params = getPrior().getPosterior(new MultinomialSuffStats(counts));
    /*if(map) { // TMP
      double[] alpha = ((Dirichlet)params.getPrior()).getAlpha();
      double[] mode = Alpha.computeMAP(alpha);
      System.out.println("MODE: " + Fmt.D(mode));
    }*/
  }
  public double[] getScores() {
    //return ListUtils.exp(params.expectedLog());
    double[] alpha = ((Dirichlet)params.getPrior()).getAlpha();
    if(estimationMethod.value == VarDPOptions.EstimationMethod.map)
      return Alpha.computeMAP(alpha);
    else if(estimationMethod.value == VarDPOptions.EstimationMethod.var ||
            estimationMethod.value == VarDPOptions.EstimationMethod.normvar) {
      double normalizer = ListUtils.sum(alpha);
      if(estimationMethod.value == VarDPOptions.EstimationMethod.var)
        normalizer = dc.expDigamma(normalizer);
      double[] scores = new double[nw];
      for(int wi = 0; wi < nw; wi++)
        scores[wi] = dc.expDigamma(alpha[wi]) / normalizer;
      return scores;
    }
    else
      throw Exceptions.unknownCase;
  }
}

////////////////////////////////////////////////////////////

class UnaryDirichletCollection implements UnaryDiscreteDistribCollection, Serializable {
  private static final long serialVersionUID = 42L;

  private TopLevelOfDirichlet top;
  private int nc, np;
  private Alpha alpha; // Concentration parameter
  private Ref<VarDPOptions.EstimationMethod> estimationMethod;
  private transient DigammaComputerInterface dc;
  private MargMultinomial[] params; // q distribution for each parent substate

  public MargMultinomial[] getParams() { return params; }
  public Alpha getAlpha() { return alpha; }

  public UnaryDirichletCollection(TopLevelDistrib top, int nc, int np, Alpha alpha, Ref<VarDPOptions.EstimationMethod> estimationMethod, DigammaComputerInterface dc) {
    this.top = (TopLevelOfDirichlet)top;
    this.nc = nc;
    this.np = np;
    this.alpha = alpha;
    this.estimationMethod = estimationMethod;
    this.dc = dc;
  }

  private double[] getCountsOfParent(double[][] counts, int pi) {
    double[] subCounts = new double[nc];
    for(int ci = 0; ci < nc; ci++)
      if(counts[ci] != null)
        subCounts[ci] = counts[ci][pi];
    return subCounts;
  }

  private MargMultinomial getPrior() {
    return new MargMultinomial(new Dirichlet(alpha.multPrior(top.getProbs())));
  }

  // child substate, parent substate
  public void update(double[][] counts) {
    if(params == null) params = new MargMultinomial[np];
    MargMultinomial prior = getPrior();
    for(int pi = 0; pi < np; pi++) {
      params[pi] = prior.getPosterior(
          new MultinomialSuffStats(getCountsOfParent(counts, pi)));
      /*if(map) { // TMP
        double[] alpha = ((Dirichlet)params[pi].getPrior()).getAlpha();
        double[] mode = Alpha.computeMAP(alpha);
        System.out.println("MODE1 " + pi + ": " + Fmt.D(mode));
      }*/
    }
  }

  public double[][] getScores() {
    double[][] scores = new double[nc][np];
    /*for(int ci = 0; ci < nc; ci++)
      for(int pi = 0; pi < np; pi++)
        scores[ci][pi] = Math.exp(params[pi].expectedLog(ci));*/
    for(int pi = 0; pi < np; pi++) {
      double[] alpha = ((Dirichlet)params[pi].getPrior()).getAlpha();
      if(estimationMethod.value == VarDPOptions.EstimationMethod.map) {
        double[] mode = Alpha.computeMAP(alpha);
        for(int ci = 0; ci < nc; ci++)
          scores[ci][pi] = mode[ci];
      }
      else if(estimationMethod.value == VarDPOptions.EstimationMethod.var ||
              estimationMethod.value == VarDPOptions.EstimationMethod.normvar) {
        double normalizer = ListUtils.sum(alpha);
        if(estimationMethod.value == VarDPOptions.EstimationMethod.var)
          normalizer = dc.expDigamma(normalizer);
        for(int ci = 0; ci < nc; ci++)
          scores[ci][pi] = dc.expDigamma(alpha[ci]) / normalizer;
      }
      else
        throw Exceptions.unknownCase;
    }
    return scores;
  }
}

////////////////////////////////////////////////////////////

class BinaryDirichletCollection implements BinaryDiscreteDistribCollection, Serializable {
  private static final long serialVersionUID = 42L;

  private TopLevelOfDirichlet top1, top2;
  private int nc1, nc2, np;
  private Alpha alpha;
  private Ref<VarDPOptions.EstimationMethod> estimationMethod;
  private transient DigammaComputerInterface dc;
  private MargMultinomial[] params;

  public MargMultinomial[] getParams() { return params; }
  public Alpha getAlpha() { return alpha; }

  public BinaryDirichletCollection(TopLevelDistrib top1, TopLevelDistrib top2,
      int nc1, int nc2, int np, Alpha alpha, Ref<VarDPOptions.EstimationMethod> estimationMethod, DigammaComputerInterface dc) {
    this.top1 = (TopLevelOfDirichlet)top1;
    this.top2 = (TopLevelOfDirichlet)top2;
    this.nc1 = nc1;
    this.nc2 = nc2;
    this.np = np;
    this.alpha = alpha;
    this.estimationMethod = estimationMethod;
    this.dc = dc;
  }

  private int c2k(int ci1, int ci2) { return ci1*nc2+ci2; }
  private int k2c1(int k) { return k/nc2; }
  private int k2c2(int k) { return k%nc2; }
  private int K() { return nc1*nc2; }

  private double[] getCountsOfParent(double[][][] counts, int pi) {
    double[] subCounts = new double[K()];
    for(int ci1 = 0; ci1 < nc1; ci1++)
      for(int ci2 = 0; ci2 < nc2; ci2++)
        if(counts[ci1][ci2] != null)
          subCounts[c2k(ci1, ci2)] = counts[ci1][ci2][pi];
    return subCounts;
  }

  private MargMultinomial getPrior() {
    double[] base = new double[K()];
    for(int ci1 = 0; ci1 < nc1; ci1++)
      for(int ci2 = 0; ci2 < nc2; ci2++)
        base[c2k(ci1, ci2)] = alpha.multFactor(nc1*nc2) * top1.getProb(ci1) * top2.getProb(ci2);
    return new MargMultinomial(new Dirichlet(base));
  }

  // child substate, parent substate
  public void update(double[][][] counts) {
    if(params == null) params = new MargMultinomial[np];
    MargMultinomial prior = getPrior();
    for(int pi = 0; pi < np; pi++) {
      params[pi] = prior.getPosterior(
        new MultinomialSuffStats(getCountsOfParent(counts, pi)));
      /*if(map) { // TMP
        double[] alpha = ((Dirichlet)params[pi].getPrior()).getAlpha();
        double[] mode = Alpha.computeMAP(alpha);
        System.out.println("MODE2 " + pi + ": " + Fmt.D(mode));
      }*/
    }
  }

  public double[][][] getScores() {
    double[][][] scores = new double[nc1][nc2][np];
    /*for(int ci1 = 0; ci1 < nc1; ci1++)
      for(int ci2 = 0; ci2 < nc2; ci2++)
        for(int pi = 0; pi < np; pi++)
          scores[ci1][ci2][pi] = Math.exp(params[pi].expectedLog(c2k(ci1, ci2)));*/
    for(int pi = 0; pi < np; pi++) {
      double[] alpha = ((Dirichlet)params[pi].getPrior()).getAlpha();
      if(estimationMethod.value == VarDPOptions.EstimationMethod.map) {
        double[] mode = Alpha.computeMAP(alpha);
        for(int ci1 = 0; ci1 < nc1; ci1++)
          for(int ci2 = 0; ci2 < nc2; ci2++)
            scores[ci1][ci2][pi] = mode[c2k(ci1, ci2)];
      }
      else if(estimationMethod.value == VarDPOptions.EstimationMethod.var ||
              estimationMethod.value == VarDPOptions.EstimationMethod.normvar) {
        double normalizer = ListUtils.sum(alpha);
        if(estimationMethod.value == VarDPOptions.EstimationMethod.var)
          normalizer = dc.expDigamma(normalizer);
        for(int ci1 = 0; ci1 < nc1; ci1++)
          for(int ci2 = 0; ci2 < nc2; ci2++)
            scores[ci1][ci2][pi] = dc.expDigamma(alpha[c2k(ci1, ci2)]) / normalizer;
      }
      else
        throw Exceptions.unknownCase;
    }
    return scores;
  }
}
