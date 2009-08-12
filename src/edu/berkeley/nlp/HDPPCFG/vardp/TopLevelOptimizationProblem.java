package edu.berkeley.nlp.HDPPCFG.vardp;

import java.util.*;
import fig.basic.*;
import fig.prob.*;
import fig.record.*;
import fig.exec.*;
import edu.berkeley.nlp.HDPPCFG.*;
import static fig.basic.LogInfo.*;
import edu.berkeley.nlp.util.Numberer;

/**
 * Solves an non-convex optimization problem to find the best
 * top-level distribution over substates for each state.
 * We use gradient projection.
 */
public class TopLevelOptimizationProblem {
  private final TopLevelDistrib[] topDistribs;
  private final Set<UnaryRule> unaryRules;
  private final Set<BinaryRule> binaryRules;
  // Whether to use the stick-breaking prior (GEM)
  private final boolean stick;

  private final int C; // Number of categories
  private int nx; // Number of variables (total number of subcategories)
  private Numberer tagNumberer;
  
  private int J(int c) { return topDistribs[c].dim(); }
  private String cstr(int c) { return tagNumberer.object(c)+"("+c+")"; }

  public TopLevelOptimizationProblem(TopLevelDistrib[] topDistribs,
      Set<UnaryRule> unaryRules, Set<BinaryRule> binaryRules, boolean stick) {
    this.topDistribs = topDistribs;
    this.unaryRules = unaryRules;
    this.binaryRules = binaryRules;
    this.stick = stick;

    this.C = topDistribs.length;
    this.nx = 0;
    for(int c = 0; c < C; c++)
      this.nx += topDistribs[c].getProbs().length;

    this.tagNumberer = Numberer.getGlobalNumberer("tags");
  }

  class TopLevelObjectiveFunction implements DifferentiableFunction {
    // The variables are the top-level distributions: pi_{cj}
    // The objective function has three components:
    // prior, unary likelihood, and binary likelihood
    // These terms can be grouped into the following:
    //   \log pi_{cj}                      [from prior]
    //   pi_{cj}                           [from unary likelihood]
    //   \log\Gamma(\beta pi_{cj})         [from unary likelihood]
    //   pi_{cj} pi_{dk}                   [from binary likelihood]
    //   \log\Gamma(\beta pi_{cj} pi_{dk}) [from binary likelihood]
    //
    // Each term has some coefficient, which is the sufficient statistics.
    // Assume all betas are the same.
    // Be careful not to count the quadratic coefficients twice in computing the value.
    //double[][] log_coeff; // c, j -> coefficient
    double[][] ident_coeff; // c, j -> coefficient
    Map<Double, double[][]> lgamma_coeff; // beta, c, j -> coefficient
    double[][][][] ident_coeff2; // c, j, d, k -> coefficient
    Map<Double, double[][][][]> lgamma_coeff2; // beta, c, j, d, k -> coefficient

    public double[][] allocate() {
      double[][] pi = new double[C][];
      for(int c = 0; c < C; c++)
        pi[c] = new double[J(c)];
      return pi;
    }
    public double[][][][] allocate2() {
      double[][][][] pi2 = new double[C][][][];
      for(int c = 0; c < C; c++)
        pi2[c] = new double[J(c)][C][];
      return pi2;
    }

    double[][] lgamma_coeff(double beta) {
      double[][] result = lgamma_coeff.get(beta);
      if(result == null) lgamma_coeff.put(beta, result = allocate());
      return result;
    }
    double[][][][] lgamma_coeff2(double beta) {
      double[][][][] result = lgamma_coeff2.get(beta);
      if(result == null) lgamma_coeff2.put(beta, result = allocate2());
      return result;
    }

    double[][] computeTailPi(double[][] pi) {
      double[][] tailpi = allocate();
      for(int c = 0; c < C; c++)
        for(int j = J(c)-1; j >= 0; j--)
          tailpi[c][j] = (j+1<J(c) ? tailpi[c][j+1] : 0) + pi[c][j];
      return tailpi;
    }

    // Contribution of the prior to the objective function
    public double priorContribValueGEM(double[][] pi) {
      double[][] tailpi = computeTailPi(pi);
      double sum = 0;
      for(int c = 0; c < C; c++) {
        double alpha = ((TopLevelOfDirichlet)topDistribs[c]).getAlpha().alpha;
        double[] T = tailpi[c];
        for(int j = 0; j < J(c)-1; j++)
          sum += (alpha-1) * Math.log(T[j+1]/T[j]);
      }
      return sum;
    }

    // Contribution of the prior to the gradient
    public void priorContribGradientGEM(double[][] pi, double[][] d_pi) {
      double[][] tailpi = computeTailPi(pi);
      for(int c = 0; c < C; c++) {
        double alpha = ((TopLevelOfDirichlet)topDistribs[c]).getAlpha().alpha;
        // Fact: (d tailpi[c][jj] / d pi[j]) = 1[jj <= j]
        double[] T = tailpi[c];
        double[] D = d_pi[c];
        D[J(c)-1] = -(alpha-1) / T[J(c)-1];
        for(int j = J(c)-2; j >= 0; j--)
          D[j] = 1/T[j+1] + (j == J(c)-2 ? 0 : D[j+1]);
        // WRONG: can be simplified and doesn't consider Jacobian
        /*for(int j = 0; j < J(c)-1; j++) {
          for(int jj = 0; jj <= j; jj++) {
            d_pi[c][j] += (alpha-1) * T[jj]/T[jj+1] *
              ((jj+1 <= j ? 1 : 0)*T[jj] - (jj <= j ? 1 : 0)*T[jj+1]) /
              T[jj]*T[jj];
          }
        }*/
      }
    }

    // Contribution of the prior to the objective function
    public double priorContribValueDirichlet(double[][] pi) {
      double sum = 0;
      for(int c = 0; c < C; c++) {
        double alpha = ((TopLevelOfDirichlet)topDistribs[c]).getAlpha().getValue(J(c));
        for(int j = 0; j < J(c); j++)
          sum += (alpha-1) * Math.log(pi[c][j]);
      }
      return sum;
    }

    // Contribution of the prior to the gradient
    public void priorContribGradientDirichlet(double[][] pi, double[][] d_pi) {
      for(int c = 0; c < C; c++) {
        double alpha = ((TopLevelOfDirichlet)topDistribs[c]).getAlpha().getValue(J(c));
        //for(int j = 0; j < J(c); j++) // TMP
          //dbg((alpha-1) / pi[c][j]);
        for(int j = 0; j < J(c); j++)
          d_pi[c][j] += (alpha-1) / pi[c][j];
      }
    }

    public TopLevelObjectiveFunction() {
      //this.log_coeff = allocate();
      this.ident_coeff = allocate();
      this.lgamma_coeff = new HashMap();
      this.ident_coeff2 = allocate2();
      this.lgamma_coeff2 = new HashMap();
      track("Building objective function: %d states, %d variables, %d unary rules, %d binary rules",
          C, nx, unaryRules.size(), binaryRules.size());

      // Prior
      /*for(int c = 0; c < C; c++) {
        double alpha = ((TopLevelOfDirichlet)topDistribs[c]).getAlpha().getValue(J(c));
        for(int j = 0; j < J(c); j++)
          log_coeff[c][j] += alpha-1;
      }*/

      // Unary likelihood
      for(UnaryRule rule : unaryRules) {
        int c = rule.parentState;
        int d = rule.childState;
        UnaryDirichletCollection dc = (UnaryDirichletCollection)rule.params;
        if(dc == null) continue; // Skip weird unaries
        for(int j = 0; j < J(c); j++) {
          double[] elog = dc.getParams()[j].getPrior().expectedLog();
          assert elog.length == J(d);
          for(int k = 0; k < J(d); k++) {
            double beta = dc.getAlpha().multFactor(J(d));
            ident_coeff[d][k] += beta*elog[k];
            lgamma_coeff(beta)[d][k]--;
          }
        }
      }

      // Binary likelihood
      for(BinaryRule rule : binaryRules) {
        int c = rule.parentState;
        int d = rule.leftChildState;
        int e = rule.rightChildState;
        BinaryDirichletCollection dc = (BinaryDirichletCollection)rule.params;
        assert dc != null;
        for(int j = 0; j < J(c); j++) {
          double[] elog = dc.getParams()[j].getPrior().expectedLog();
          assert elog.length == J(d)*J(e);
          for(int k = 0; k < J(d); k++) {
            for(int l = 0; l < J(e); l++) {
              double elogElem = elog[k*J(e)+l];
              double beta = dc.getAlpha().multFactor(J(d)*J(e));
              if(ident_coeff2[d][k][e] == null) ident_coeff2[d][k][e] = new double[J(e)];
              if(lgamma_coeff2(beta)[d][k][e] == null) lgamma_coeff2(beta)[d][k][e] = new double[J(e)];
              ident_coeff2[d][k][e][l] += beta*elogElem;
              if(V(d)) dbgs("ADD2 c=%s,j=%s -> d=%s,k=%d, e=%s,l=%d : %f",
                  cstr(c), j, cstr(d), k, cstr(e), l, beta*elogElem);
              lgamma_coeff2(beta)[d][k][e][l]--;
              if(d != e || k != l) {
                // Remember to get the reverse
                if(ident_coeff2[e][l][d] == null) ident_coeff2[e][l][d] = new double[J(d)];
                if(lgamma_coeff2(beta)[e][l][d] == null) lgamma_coeff2(beta)[e][l][d] = new double[J(d)];
                ident_coeff2[e][l][d][k] += beta*elogElem;
                lgamma_coeff2(beta)[e][l][d][k]--;
              }
            }
          }
        }
      }

      logs("%d unary betas, %d binary betas", lgamma_coeff.size(), lgamma_coeff2.size());

      end_track();
    }

    public double valueAt(double[] x) {
      double[][] pi = x2pi(x);

      double sum = 0;

      if(stick) sum += priorContribValueGEM(pi);
      else      sum += priorContribValueDirichlet(pi);

      for(int c = 0; c < C; c++) {
        for(int j = 0; j < J(c); j++) {
          //sum += log_coeff[c][j] * Math.log(pi[c][j]);
          sum += ident_coeff[c][j] * pi[c][j];
          for(int d = c; d < C; d++) {
            if(ident_coeff2[c][j][d] != null) {
              for(int k = 0; k < J(d); k++)
                sum += ident_coeff2[c][j][d][k] * pi[c][j]*pi[d][k];
            }
          }
        }
        //dbg(c + " " + sum);
      }

      for(Map.Entry<Double, double[][]> e : lgamma_coeff.entrySet()) {
        for(int c = 0; c < C; c++)
          for(int j = 0; j < J(c); j++)
            sum += e.getValue()[c][j] * NumUtils.logGamma(e.getKey()*pi[c][j]);
      }
      for(Map.Entry<Double, double[][][][]> e : lgamma_coeff2.entrySet()) {
        for(int c = 0; c < C; c++) {
          for(int j = 0; j < J(c); j++) {
            for(int d = c; d < C; d++) { // c
              if(e.getValue()[c][j][d] != null) {
                for(int k = 0; k < J(d); k++)
                  sum += e.getValue()[c][j][d][k] * NumUtils.logGamma(e.getKey()*pi[c][j]*pi[d][k]);
                NumUtils.assertIsFinite(sum);
              }
            }
          }
        }
      }

      NumUtils.assertIsFinite(sum);
      return sum;
    }

    //boolean V(int c) { return c == 6; } // ,
    boolean V(int c) { return false; }
    //boolean V(int c) { return true; }

    public double[] gradientAt(double[] x) {
      double[][] pi = x2pi(x);
      double[][] d_pi = allocate();

      if(stick) priorContribGradientGEM(pi, d_pi);
      else      priorContribGradientDirichlet(pi, d_pi);

      //if(pi != null) return pi2x(d_pi); // TMP

      for(int c = 0; c < C; c++) {
        for(int j = 0; j < J(c); j++) {
          double sum = 0;
          //sum += log_coeff[c][j] / pi[c][j];
          //if(V(c)) dbg("c=%s,j=%d: log=%f, sum=%f", cstr(c), j, log_coeff[c][j], sum);
          sum += ident_coeff[c][j];
          if(V(c)) dbgs("c=%s,j=%d: ident=%f, sum=%f", cstr(c), j, ident_coeff[c][j], sum);
          for(int d = 0; d < C; d++) {
            if(ident_coeff2[c][j][d] != null) {
              /*for(int k = 0; k < J(d); k++)
                sum += ident_coeff2[c][j][d][k] * (c==d&&j==k?2:1)*pi[d][k];*/
              for(int k = 0; k < J(d); k++) {
                sum += ident_coeff2[c][j][d][k] * (c==d&&j==k?2:1)*pi[d][k];
                if(V(c)) dbgs("c=%s,j=%d: d=%d,k=%d, ident = %f * %f, sum=%f", cstr(c), j, d, k,
                  ident_coeff2[c][j][d][k], (c==d&&j==k?2:1)*pi[d][k], sum);
              }
            }
          }
          if(V(c)) dbgs("c=%s,j=%d: ident2, sum=%f", cstr(c), j, sum);
          d_pi[c][j] += sum;
        }
      }

      for(Map.Entry<Double, double[][]> e : lgamma_coeff.entrySet()) {
        for(int c = 0; c < C; c++) {
          for(int j = 0; j < J(c); j++) {
            d_pi[c][j] += e.getValue()[c][j] * e.getKey()*NumUtils.digamma(e.getKey()*pi[c][j]);
            if(V(c)) dbgs("c=%s,j=%d: lgamma = %f * %f * %f, sum=%f", cstr(c), j, e.getValue()[c][j], e.getKey(), NumUtils.digamma(e.getKey()*pi[c][j]), d_pi[c][j]);
          }
        }
      }
      // Affect of these terms outweights everything else
      for(Map.Entry<Double, double[][][][]> e : lgamma_coeff2.entrySet()) {
        for(int c = 0; c < C; c++) {
          for(int j = 0; j < J(c); j++) {
            for(int d = 0; d < C; d++) { // 0
              if(e.getValue()[c][j][d] != null) {
                /*for(int k = 0; k < J(d); k++)
                  d_pi[c][j] += e.getValue()[c][j][d][k] * (c==d&&j==k?2:1)*pi[d][k]*e.getKey()*NumUtils.digamma(e.getKey()*pi[c][j]*pi[d][k]);*/
                for(int k = 0; k < J(d); k++) {
                  d_pi[c][j] += e.getValue()[c][j][d][k] * (c==d&&j==k?2:1)*pi[d][k]*e.getKey()*NumUtils.digamma(e.getKey()*pi[c][j]*pi[d][k]);
                  if(V(c)) dbgs("c=%s,j=%d: d=%d,k=%d, lgamma2 = %f * %f * %f, sum=%f", cstr(c), j, d, k,
                    e.getValue()[c][j][d][k], (c==d&&j==k?2:1)*pi[d][k]*e.getKey(),
                    NumUtils.digamma(e.getKey()*pi[c][j]*pi[d][k]),
                      d_pi[c][j]);
                }
              }
            }
          }
        }
      }

      return pi2x(d_pi);
    }

    public String xToString(double[] x) {
      double[][] pi = x2pi(x);
      List<String> buf = new ArrayList();
      for(int c = 0; c < C; c++)
        buf.add(cstr(c) + ": " + Fmt.D(pi[c]));
      return StrUtils.join(buf, " | ");
    }

    public String dxToString(double[] dx) {
      return xToString(dx);
    }
  }

  // Implements projections to the simplex
  class MultiSimplexStepTaker implements StepTaker {
    private static final double epsilon = 1e-12;

    public double[] takeStep(double[] x, double[] dx) {
      double[][] pi = x2pi(x);
      double[][] d_pi = x2pi(dx);
      for(int c = 0; c < C; c++) {
        // Take step (remember, last variable is negatively correlated with all others)
        for(int j = 0; j < J(c)-1; j++)
          pi[c][j] += d_pi[c][j] - d_pi[c][J(c)-1];

        // Is this two stage projection the same?
        // Project onto the (K-1)-dimensional triangle
        // Make sure all entries are positive first
        double sum = 0;
        for(int j = 0; j < J(c)-1; j++) {
          pi[c][j] = Math.max(pi[c][j], epsilon);
          sum += pi[c][j];
        }
        // If outside simplex (sum to 1-epsilon), project down to that constraint
        if(sum > 1-epsilon) {
          for(int j = 0; j < J(c)-1; j++)
            pi[c][j] *= (1-epsilon) / sum;
          sum = 1-epsilon;
        }
        pi[c][J(c)-1] = 1-sum;
      }
      return pi2x(pi);
    }
  }

  // Use exponentiated gradient, which I don't fully understand
  // new x_i \propto x_i * exp(dx_i) for each normalized group
  class MultiSimplexOvercompleteStepTaker implements StepTaker {
    private static final double epsilon = 1e-12;
    public double[] takeStep(double[] x, double[] dx) {
      double[][] pi = x2pi(x);
      double[][] d_pi = x2pi(dx);
      for(int c = 0; c < C; c++) {
        NumUtils.expNormalize(d_pi[c]);
        for(int j = 0; j < J(c); j++)
          pi[c][j] = Math.max(pi[c][j]*d_pi[c][j], epsilon);
        NumUtils.normalize(pi[c]);
      }
      return pi2x(pi);
    }
  }

  // Convert between the vector representation x and the native representation
  // pi
  public double[][] x2pi(double[] x) {
    double[][] pi = new double[C][];
    int xi = 0;
    for(int c = 0; c < C; c++) {
      pi[c] = new double[J(c)];
      for(int j = 0; j < J(c); j++)
        pi[c][j] = x[xi++];
    }
    return pi;
  }
  public double[] pi2x(double[][] pi) {
    double[] x = new double[nx];
    int xi = 0;
    for(int c = 0; c < C; c++)
      for(int j = 0; j < J(c); j++)
        x[xi++] = pi[c][j];
    return x;
  }
  public double[][] getPi() {
    double[][] pi = new double[C][];
    for(int c = 0; c < C; c++)
      pi[c] = topDistribs[c].getProbs();
    return pi;
  }
  public void setPi(double[][] pi) {
    for(int c = 0; c < C; c++)
      topDistribs[c].setProbs(pi[c]);
  }

  public void eval(double[][] pi) {
    TopLevelObjectiveFunction func = new TopLevelObjectiveFunction();
    logs("EVAL " + func.xToString(pi2x(pi)));
    logs("value = " + func.valueAt(pi2x(pi)));
  }

  public void optimize(int maxIters, int verbose) {
    TopLevelObjectiveFunction func = new TopLevelObjectiveFunction();
    MultiSimplexStepTaker stepTaker = new MultiSimplexStepTaker();
    GradientProjectionOptimizer opt = new GradientProjectionOptimizer();
    setPi(x2pi(opt.optimize(func, stepTaker, pi2x(getPi()), maxIters, verbose)));
  }

  ////////////////////////////////////////////////////////////
  public static class TestOptions {
    // The model is as follows:
    // top-level ~ GEM/Dir(alpha)
    // rule probabilities ~ Dir(beta * top-level)
    // p*strength ~ Dir(rule probabilities)
    @Option public double beta = 10;
    @Option public double alpha = 1;
    @Option public double p = 0.8; 
    @Option public double strength1 = 10;
    @Option public double strength2 = 10;
    @Option public int numIters = 10;
    @Option public int verbose = 2;
    @Option public boolean stick = false;
  }

  public static void main(String[] args) {
    TestOptions testOptions = new TestOptions();
    LogInfo.msPerLine = 0;
    Execution.init(args, testOptions);
    Record.init("TopLevel.record");
    double p = testOptions.p;
    double alpha = testOptions.alpha;
    double beta = testOptions.beta;
    double strength1 = testOptions.strength1;
    double strength2 = testOptions.strength2;

    VarDPOptions options = new VarDPOptions();
    options.estimationMethod = VarDPOptions.EstimationMethod.var;
    options.topSubstateAlpha = new Alpha(alpha, false);
    options.substateAlpha = new Alpha(beta, true);
    DiscreteDistribCollectionFactory ddcFactory =
      options.createDDCFactory();

    TopLevelDistrib[] topDistribs = new TopLevelDistrib[] {
      ddcFactory.newTopLevelSubstate(2)
    };
    // Initialize with something
    topDistribs[0].setProbs(new double[] { 0.51, 0.49 });

    Set<UnaryRule> unaryRules = new HashSet();
    UnaryRule u1 = new UnaryRule((short)0, (short)0);
    u1.params = ddcFactory.newUnarySubstate(topDistribs[0], 2, 2);
    unaryRules.add(u1);
    Set<BinaryRule> binaryRules = new HashSet();

    // Iterate between optimizing rule probabilities and top-level distribution
    for(int q = 0; q < testOptions.numIters; q++) {
      Record.begin("iteration", q);
      // Optimize rules with respect to data and prior
      u1.params.update(new double[][] {
        // Indices: child, parent
        //{ (1-p)*strength1, (1-p)*strength2 },
        //{ p*strength1, p*strength2 },
        // First subcategory has uniform observations (simulates an unused substate);
        // strength1 should be around 0, which is when the substate is unused
        // (it just tracks the prior, which slows convergence a bit)
        // Second has (p,1-p) observations
        { 0.5*strength1, p*strength2 },
        { 0.5*strength1, (1-p)*strength2 },
        //{ 0.5, 0.5 },
        //{ 0.5, 0.5 },
      });

      TopLevelOptimizationProblem prob = new TopLevelOptimizationProblem(topDistribs, unaryRules, binaryRules, testOptions.stick);
      prob.optimize(Integer.MAX_VALUE, testOptions.verbose);

      // Manually find the best answer
      TopLevelObjectiveFunction func = prob.new TopLevelObjectiveFunction();
      double bestValue = Double.NEGATIVE_INFINITY;
      double bestX = Double.NaN;
      Record.setStruct("x", "y");
      for(double x = 0; x <= 1; x += 0.01) {
        double value = func.valueAt(new double[] {x, 1-x});
        //System.out.println("PPP " + x + " " + value);
        Record.add(""+x, value);
        if(value > bestValue) {
          bestValue = value;
          bestX = x;
        }
      }
      logs("GRID_SEARCH " + bestX + " " + bestValue);
      logs("GRAD " + Fmt.D(topDistribs[0].getProbs()) + " " + func.valueAt(topDistribs[0].getProbs()));
      Record.end();
    }

    // Simple test
    /*double bestValue = Double.NEGATIVE_INFINITY;
    double bestX = Double.NaN;
    for(double x = 0; x <= 1; x += 0.01) {
      double value = Beta.logProb(alpha, alpha, x) + Beta.logProb(beta*(1-x), beta*x, p);
      stdout.println("QQQ " + x + " " + value);
      if(value > bestValue) {
        bestValue = value;
        bestX = x;
      }
    }
    stderr.println("BESTQQQ " + bestX + " " + bestValue);*/
  }
}
