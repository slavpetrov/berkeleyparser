package edu.berkeley.nlp.HDPPCFG.vardp;

import fig.basic.*;
import java.util.*;

public class VarDPOptions {
  // Default alpha: for sparse prior
  public static final Alpha defaultAlpha = new Alpha(1, true);

  public enum EstimationMethod { mle, map, var, normvar };
  @Option(required=true)
    public EstimationMethod estimationMethod = EstimationMethod.mle;

  @Option(gloss="How much variation from uniform to put in top-level")
    public double initTopLevelNoise = 0;
  @Option public Random initRandom = new Random(1);

  @Option public Alpha topStateAlpha = defaultAlpha;
  @Option public Alpha topSubstateAlpha = defaultAlpha;
  @Option public Alpha stateAlpha = defaultAlpha;
  @Option public Alpha substateAlpha = defaultAlpha;
  @Option public Alpha wordAlpha = defaultAlpha;

  @Option public boolean useFastExpExpectedLog = true;
  @Option(gloss="Do MLE parsing, then switch to the estimation method half way")
    public boolean trainFirstWithMLE = false;

  public DiscreteDistribCollectionFactory createDDCFactory() {
    switch(estimationMethod) {
      case mle: return new MLECollectionFactory();
      case map: return new DirichletCollectionFactory(this);
      case var: return new DirichletCollectionFactory(this);
      case normvar: return new DirichletCollectionFactory(this);
      default: throw new RuntimeException("Unknown estimation method: " + estimationMethod);
    }
  }
}
