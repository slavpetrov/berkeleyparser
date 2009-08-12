package edu.berkeley.nlp.HDPPCFG;

import edu.berkeley.nlp.util.*;
import java.io.Serializable;
import edu.berkeley.nlp.HDPPCFG.vardp.*;

/**
 * Binary rules (ints for parent, left and right children)
 *
 * @author Dan Klein
 */

public class BinaryRule extends Rule implements Serializable, java.lang.Comparable {

  public short leftChildState = -1;
  public short rightChildState = -1;
 /**
   * NEW:
	 * scores[leftSubState][rightSubState][parentSubState] gives score for this rule
	 */
	public double[][][] scores; 

  // Distribution over children substates for each parent substate
  public BinaryDiscreteDistribCollection params;

  /**
   * Creates a BinaryRule from String s, assuming it was created using toString().
   *
   * @param s
   */
/*  public BinaryRule(String s, Numberer n) {
    String[] fields = StringUtils.splitOnCharWithQuoting(s, ' ', '\"', '\\');
    //    System.out.println("fields:\n" + fields[0] + "\n" + fields[2] + "\n" + fields[3] + "\n" + fields[4]);
    this.parent = n.number(fields[0]);
    this.leftChild = n.number(fields[2]);
    this.rightChild = n.number(fields[3]);
    this.score = Double.parseDouble(fields[4]);
  }
*/
  public BinaryRule(short pState, short lState, short rState, double[][][] scores) {
    this.parentState = pState;
    this.leftChildState = lState;
    this.rightChildState = rState;
    this.scores = scores;
  }
  
  public BinaryRule(short pState, short lState, short rState) {
    this.parentState = pState;
    this.leftChildState = lState;
    this.rightChildState = rState;
    this.scores = new double[1][1][1];
  }

  
  /** Copy constructor */
  public BinaryRule(BinaryRule b) {
  	this(b.parentState,b.leftChildState,b.rightChildState,ArrayUtil.copy(b.scores));
  }

  public BinaryRule(BinaryRule b, double[][][] newScores) {
  	this(b.parentState,b.leftChildState,b.rightChildState,newScores);
  }

  public BinaryRule(short pState, short lState, short rState, short pSubStates, int lSubStates, int rSubStates) {
    this.parentState = pState;
    this.leftChildState = lState;
    this.rightChildState = rState;
    this.scores = new double[lSubStates][rSubStates][pSubStates];
  }

  public int hashCode() {
    return ((int)parentState << 16) ^ ((int)leftChildState << 8) ^ ((int)rightChildState);
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof BinaryRule) {
      BinaryRule br = (BinaryRule) o;
      if (parentState == br.parentState && 
    		  leftChildState == br.leftChildState &&
    		  rightChildState == br.rightChildState) {
        return true;
      }
    }
    return false;
  }

  private static final char[] charsToEscape = new char[]{'\"'};

  public String toString() {
    Numberer n = Numberer.getGlobalNumberer("tags");
    String lState = (String)n.object(leftChildState);
    String rState = (String)n.object(rightChildState);
    String pState = (String)n.object(parentState);
    StringBuilder sb = new StringBuilder();
    //sb.append(pState+ " -> "+lState+ " "+rState+ "\n");
    for (int lS=0; lS<scores.length; lS++){
    	for (int rS=0; rS<scores[lS].length; rS++){
    		if (scores[lS][rS]==null) continue;
    		for (int pS=0; pS<scores[lS][rS].length; pS++){
    			double p = scores[lS][rS][pS]; 
    			if (p>Double.NEGATIVE_INFINITY)
    				sb.append(pState+"_"+pS+ " -> "+lState+"_"+lS+ " "+rState+"_"+rS +" "+p+"\n");
    		}
    	}
    }
    return sb.toString();
  }
    
    
  public String toString_old() {
    Numberer n = Numberer.getGlobalNumberer("tags");
    return "\"" + 
    	StringUtils.escapeString(n.object(parentState).toString(), charsToEscape, '\\') + 
    	"\" -> \"" + 
    	StringUtils.escapeString(n.object(leftChildState).toString(), charsToEscape, '\\') + 
    	"\" \"" + 
    	StringUtils.escapeString(n.object(rightChildState).toString(), charsToEscape, '\\') + 
    	"\" " + ArrayUtil.toString(scores);
  }

  public int compareTo(Object o) {
    BinaryRule ur = (BinaryRule) o;
    if (parentState < ur.parentState) {
      return -1;
    }
    if (parentState > ur.parentState) {
      return 1;
    }
    if (leftChildState < ur.leftChildState) {
      return -1;
    }
    if (leftChildState > ur.leftChildState) {
      return 1;
    }
    if (rightChildState < ur.rightChildState) {
      return -1;
    }
    if (rightChildState > ur.rightChildState) {
      return 1;
    }
    return 0;
  }


  public short getLeftChildState() {
    return leftChildState;
  }

  public short getRightChildState() {
    return rightChildState;
  }
  
  public void setScore(int pS, int lS, int rS, double score){
  	// sets the score for a particular combination of substates
  	scores[lS][rS][pS] = score;
  }

  public double getScore(int pS, int lS, int rS){
  	// gets the score for a particular combination of substates
  	if (scores[lS][rS]==null) {
  		if (logarithmMode)
  			return Double.NEGATIVE_INFINITY;
  		return 0;
  	}
  	return scores[lS][rS][pS];
  }
  
  public void setScores2(double[][][] scores){
  	this.scores = scores;
  }

  /**
	 * scores[parentSubState][leftSubState][rightSubState] gives score for this rule
	 */
  public double[][][] getScores2(){
  	return scores;
  }
  
  public void setNodes(short pState, short lState, short rState){
  	this.parentState = pState;
  	this.leftChildState = lState;
  	this.rightChildState = rState;
  }


  private static final long serialVersionUID = 2L;


}
