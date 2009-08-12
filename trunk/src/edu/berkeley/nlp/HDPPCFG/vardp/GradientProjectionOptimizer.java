package edu.berkeley.nlp.HDPPCFG.vardp;

import fig.basic.*;
import fig.exec.*;
import static fig.basic.LogInfo.*;

interface DifferentiableFunction {
  public double valueAt(double[] x);
  public double[] gradientAt(double[] x);
  public String xToString(double[] x);
  public String dxToString(double[] x);
}

interface FeasibleSet {
  public double[] project(double[] x);
}
class BoxFeasibleSet implements FeasibleSet {
  private double[] epsilon;
  public BoxFeasibleSet(double[] epsilon) {
    this.epsilon = epsilon;
  }
  public double[] project(double[] x) {
    int n = x.length;
    double[] newx = new double[n];
    for(int i = 0; i < n; i++)
      newx[i] = Math.min(Math.max(x[i], epsilon[i]), 1-epsilon[i]);
    return newx;
  }
}
/*class SimplexFeasibleSet implements FeasibleSet {
  private double epsilon;
  public SimplexFeasibleSet(double epsilon) {
    this.epsilon = epsilon;
  }
  public double[] project(double[] x) {
    int n = x.length;
    double[] newx = new double[n];
    for(int i = 0; i < n; i++)
      newx[i] = Math.max(x[i], epsilon);
    NumUtils.normalize(newx);
    return newx;
  }
}*/

interface StepTaker {
  public double[] takeStep(double[] x, double[] dx);
}

class GradientStepTaker {
  private final FeasibleSet set;
  public GradientStepTaker(FeasibleSet set) { this.set = set; }
  public double[] takeStep(double[] x, double[] dx) {
    return set.project(ListUtils.add(x, dx));
  }
}

/**
 * Maximize the given function subject to being in the feasible set.
 * Compute the gradient and take a step using that gradient (and project to feasible set).
 * While progress is not being made, reduce the step size.
 */
class GradientProjectionOptimizer {
  private double convergenceThreshold = 1e-4;

  public GradientProjectionOptimizer() {
  }

  public double[] optimize(DifferentiableFunction func, StepTaker stepTaker,
      double[] initx, int maxIters, int verbose) {
    track("GradientProjectionOptimizer.optimize()");

    final double beta = 0.5; // Stepsize reduction factor
    final int maxReductions = 50; // Maximum number of step size reductions allowed

    double[] x = initx;
    double[] lastX = initx;
    double value = func.valueAt(x); // Value of the function
    double lastValue = Double.NEGATIVE_INFINITY;
    double step = 0.2; // Step size

    for(int iter = 0; iter < maxIters; iter++) {
      // Test for convergence
      double dValue = value - lastValue; // Change in objective
      if(Math.abs(dValue) < convergenceThreshold) { logss("Converged"); break; }
      double dXNorm = NumUtils.l2Dist(x, lastX); // How much has x changed?
      lastValue = value;
      lastX = x;

      track("Iteration "+iter, false);
      double[] dx = func.gradientAt(x); // Compute the gradient (expensive)
      logss("value = %f", value);
      logss("dValue = %f", dValue);
           if(verbose >= 2) logss("x = %s", func.xToString(x));
      else if(verbose >= 1) logss("x = %s", Fmt.D(ListUtils.subArray(x, 10)));
      logss("||dX|| = %s", dXNorm);
           if(verbose >= 2) logss("gradient = %s", func.dxToString(dx));
      else if(verbose >= 1) logss("gradient = %s", Fmt.D(ListUtils.subArray(x, 10)));

      //double[] direction = dx : ListUtils.sub(goalx, x);
      //double directionDotProd = ListUtils.dot(dx, direction);
      //if(!NumUtils.equals(dXNorm, 0) && directionDotProd <= 0)
        //logss("Warning: not moving in the right direction!");
      // Gradient is too big, so have to use this surrogate
      //directionDotProd = ListUtils.dot(direction, direction);

      double[] newX = null;
      double newValue = Double.NaN;
      double lastNewValue = Double.NEGATIVE_INFINITY;
      double[] lastNewX = null;
      
      // Try to reduce the step size until we see sufficient improvement
      // in the objective function.  Note that the step-size we start
      // with is based on the ending step size from the previous iteration.
      int i;
      for(i = 0; i < maxReductions; i++) {
        // Try this step
        newX = stepTaker.takeStep(x, ListUtils.mult(step, dx));
        newValue = func.valueAt(newX);
        if(verbose >= 3) logs("new point (value = " + newValue + "): " + func.xToString(newX));
        if(verbose >= 1) logs("Reduction %d (step size = %s): dValue = %s", i, Fmt.D(step), Fmt.D(newValue-value));
        //logs("Reduction %d: dValue = %s, threshold = %s",
            //i, Fmt.D(newValue-value), Fmt.D(sigma*step2*directionDotProd));
        // Heuristic: keep on reducing step size while dValue is going up
        // This is a greedy approximation to line minimization
        if(lastNewValue-value >= 0 && newValue - lastNewValue <= 0) {
          // When it stops getting better, rollback to last setting
          // Stop only when we can rollback to something good
          newValue = lastNewValue;
          newX = lastNewX;
          step /= beta;
          break; // Stop when not improving
        }
        // Correct Armijo's rule: do we have sufficient improvement?
        //if(newValue - value >= sigma * step2 * directionDotProd) break;
        // If not, reduce the step size
        step *= beta;
        lastNewValue = newValue;
        lastNewX = newX;
      }
      if(newValue > value) {
        // We were successful in improving the objective
        logss("Improved objective by %s in %d step-size reductions", newValue-value, i+1);
        x = newX;
        value = newValue;
        step /= beta; // Try to make bigger steps next time
      }
      else
        logss("Unable to improve objective with %d reductions", i+1);
      end_track();
    }
    end_track();
    return x;
  }

  /*private double[] takeStep(double[] x, double[] dx, FeasibleSet set) {
    if(exponentiated)
      x = set.project(ListUtils.mult(x, ListUtils.exp(dx)));
    else
      x = ListUtils.add(x, dx);
    return set.project(x); // Project back to the set
  }*/

  public static void main(String[] args) {
    Execution.init(args);

    /*double[][] a = new double[][] { { 4 } };
    double[] b = new double[] { 1 };
    double[] x = new double[] { 0.5 };*/

    double[][] a = new double[][] { {-1, 0}, {0, -1} };
    double[] b = new double[] {+0.3, +0.4};
    double[] x = new double[] {0.9, 0.2};

    /*QuadraticFunction func = new QuadraticFunction(a, b, 0);
    BoxFeasibleSet set = new BoxFeasibleSet(new double[]{1e-4, 1e-4});
    GradientProjectionOptimizer opt = new GradientProjectionOptimizer();
    x = opt.optimize(func, set, x, Integer.MAX_VALUE);
    logss(Fmt.D(x));*/

    Execution.finish();
  }
}
