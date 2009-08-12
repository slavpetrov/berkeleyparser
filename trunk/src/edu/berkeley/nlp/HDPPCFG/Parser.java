package edu.berkeley.nlp.HDPPCFG;

import edu.berkeley.nlp.syntax.Tree;
import java.util.*;

interface Parser {
  public Tree<String> getBestParse(List<String> sentence);
}

