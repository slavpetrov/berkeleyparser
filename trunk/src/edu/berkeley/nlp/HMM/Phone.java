/**
 * 
 */
package edu.berkeley.nlp.HMM;

import java.io.Serializable;

public class Phone implements Serializable{
	private static final long serialVersionUID = 1L;
	private String label;

	public Phone(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	public boolean equals(Object o) {
		return (o != null) && (o instanceof Phone) && ((Phone) o).label.equals(label);
	}
	
	public boolean unnannotatedEquals(Phone p)
	{
		return p!= null && p.label.equals(label); 
	}

	public int hashCode() {
		return label.hashCode();
	}

	public String toString() {
		return getLabel() + "\t";
	}
}