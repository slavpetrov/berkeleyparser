package edu.berkeley.nlp.HDPPCFG.vardp;

import fig.basic.*;
import fig.prob.*;

// A distribution over distribution over words
public interface TopLevelWordDistrib {
  public double[] getBase(int nw);
}

class UniformWordDistrib implements TopLevelWordDistrib, java.io.Serializable {
  public double[] getBase(int n) { return ListUtils.newDouble(n, 1.0/n); }
}
