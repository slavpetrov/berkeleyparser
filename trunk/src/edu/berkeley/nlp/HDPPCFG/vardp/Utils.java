package edu.berkeley.nlp.HDPPCFG.vardp;

import java.util.*;
import fig.basic.*;
import fig.prob.*;

public class Utils {
  public static void normalize(double[] counts) {
    for(int i = 0; i < counts.length; i++)
      counts[i] = Math.max(counts[i], 1e-12);
    NumUtils.normalize(counts);
  }
}
