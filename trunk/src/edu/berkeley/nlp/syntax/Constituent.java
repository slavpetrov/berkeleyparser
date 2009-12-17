package edu.berkeley.nlp.syntax;

import java.io.Serializable;

/**
 * A labeled span (start and end pair) representing a constituent tree node.
 *
 * @author Dan Klein
 */
public class Constituent<L> implements Serializable{
  private static final long serialVersionUID = 1L;
  L label;
  int start;
  int end;

  public L getLabel() {
    return label;
  }

  public int getStart() {
    return start;
  }

  public int getEnd() {
    return end;
  }
  
  public int getLength() {
	  return end-start+1;
  }

  public String toString() {
    return "<"+label+" : "+start+", "+end+">"; 
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Constituent)) return false;

    final Constituent constituent = (Constituent) o;

    if (end != constituent.end) return false;
    if (start != constituent.start) return false;
    if (label != null ? !label.equals(constituent.label) : constituent.label != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (label != null ? label.hashCode() : 0);
    result = 29 * result + start;
    result = 29 * result + end;
    return result;
  }

  public Constituent(L label, int start, int end) {
    this.label = label;
    this.start = start;
    this.end = end;
  }
}
