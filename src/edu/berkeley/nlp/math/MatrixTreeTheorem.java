//
//package edu.berkeley.nlp.math;
//
//import Jama.Matrix;
//import Jama.EigenvalueDecomposition;
//import Jama.SingularValueDecomposition;
//import fig.basic.Pair;
//import fig.basic.NumUtils;
//
//import java.util.Arrays;
//
//import edu.berkeley.nlp.util.Logger;
//
///**
// * User: aria42
// * Date: Jan 13, 2009
// */
//public class MatrixTreeTheorem {
//
//  /**
//   * Computes sum over labeled trees. There is assumed to be a fixed
//   * number of labels possible for each edge.
//   *
//   * logPotentials[i][j][l] represents the logPotential (i,j)
//   * for the (i,j) attachment with label l. It's safe to have
//   * -inf as a value in this array to disallow attachments
//   * or labels
//   *
//   * Returns sum over labeled trees as well as posteriors
//   * stored in result.getSecond() 
//   * @param logPotentials
//   * @return
//   */
//  public static Pair<Double,double[][][]>
//  computeLabeledMultiRoot(double[][][] logPotentials)
//  {
//    int n = logPotentials.length;
//    int l = logPotentials[0][0].length;
//    double[][] collapsedLogPotentials = new double[n][n];
//    for (int i = 0; i < n; i++) {
//      for (int j = 0; j < n; j++) {
//        collapsedLogPotentials[i][j] = SloppyMath.logAdd(logPotentials[i][j]);
//      }
//    }
//    Pair<Double,double[][]> result = computeMultiRoot(collapsedLogPotentials);
//    double sumTrees = result.getFirst();
//    double[][] collapsedPosteriors = result.getSecond();
//    double[][][] posteriors = new double[n][n][l];
//    for (int i = 0; i < n; i++) {
//      for (int j = 0; j < n; j++) {
//        double post = collapsedPosteriors[i][j];
//        double attachSum = collapsedLogPotentials[i][j];
//        for (int k = 0; k < l; k++) {
//          double labelProb = Math.exp(logPotentials[i][j][k]-attachSum);
//          posteriors[i][j][k] = labelProb * post;
//        }
//      }
//    }
//    return Pair.newPair(sumTrees,posteriors);
//  }
//
//  /**
//   * logPotentials[i][j] is the logPotential of attaching (i,j) where
//   * i \neq j. When i==j, this represents the root potentials. Note
//   * that this version allows multiple roots so it's not suitable
//   * for dependency parsing.
//   *
//   * Returns sum of weighted trees and posteriors over attachments
//   * again root posteriors are stored on the diagonal
//   * @param logPotentials
//   * @return
//   */
//  public static Pair<Double,double[][]> computeMultiRoot(double[][] logPotentials)
//  {
//    int n = logPotentials.length;
//    double[][] potentials = new double[n][];
////    double max = Double.NEGATIVE_INFINITY;
////    for (int i = 0; i < n; i++) {
////      for (int j = 0; j < n; j++) {
////        if (logPotentials[i][j] < Double.POSITIVE_INFINITY) {
////          max = Math.max(logPotentials[i][j],max);
////        }
////      }
////    }
////    for (int i = 0; i < n; i++) {
////      for (int j = 0; j < n; j++) {
////        logPotentials[i][j] -= max;
////      }
////    }
//    for (int i = 0; i < n; i++) {
//      potentials[i] = DoubleArrays.exponentiate(logPotentials[i]);
//      DoubleArrays.checkValid(potentials[i]);
//      DoubleArrays.checkNonNegative(potentials[i]);
//    }
//    double[][] laplacian = new double[n][n];
//    for (int j = 0; j < n; j++) {
//      double sum = 0.0;
//      for (int i = 0; i < n; i++) {
//        sum += potentials[i][j];
//      }
//      for (int i = 0; i < n; i++) {
//        laplacian[i][j] = i == j ? sum : -potentials[i][j];
//      }
//    }
//    DoubleArrays.checkValid(laplacian);
//    Matrix L = new Matrix(laplacian);
//    SingularValueDecomposition svd = new SingularValueDecomposition(L);
//    Matrix U = svd.getU();
//    Matrix Sigma = svd.getS();
//    for (int i = 0; i < n; i++) {
//      double s= Sigma.get(i,i);
//      Sigma.set(i,i, s > 0.0 ? 1.0/s : s);
//    }
//    Matrix V = svd.getV();
//    Matrix Linv = V.times(Sigma).times(U.transpose());
//    EigenvalueDecomposition evd = L.eig();
//    //Matrix Linv = L.inverse();
//    double[][] posteriors = new double[n][n];
//    for (int i = 0; i < n; i++) {
//      for (int j = 0; j < n; j++) {
//        if (i == j) {
//          // Root Posterior
//          posteriors[i][j] = potentials[i][i] * Linv.get(i,i);
//        } else {
//          // Non-root Posterior
//          posteriors[i][j] = potentials[i][j] * (Linv.get(j,j) - Linv.get(j,i));
//        }
//        if (posteriors[i][j] < -1.0e-10) {
//          throw new RuntimeException("Error in Matrix-Tree Posteriors");
//        }
//        posteriors[i][j] = Math.max(posteriors[i][j],1.0e-10);        
//      }
//    }
//    DoubleArrays.checkValid(posteriors);
//    double logSumTree = 0.0;
//    for (double ev : evd.getRealEigenvalues()) {
//      logSumTree += Math.log(ev);
//    }        
//    return Pair.makePair(logSumTree,posteriors);
//  }
//
//  public static void main(String[] args)
//  {
//    int n = 3;
//    int l = 2;
//    double[][]  potentials = new double[n][n];
//    for (double[] row: potentials) {
//        Arrays.fill(row,Double.NEGATIVE_INFINITY);
//    }
//    for (int i=0; i < n; ++i) potentials[i][i] = 0.0;
//    for (int i = 0; i+1 < n; i++) {
//        potentials[i][i+1] = 0.0;
//    }
//    Pair<Double,double[][]> res = computeMultiRoot(potentials);
//    System.out.println("sumTrees: " + Math.exp(res.getFirst()));
//  }
// }
