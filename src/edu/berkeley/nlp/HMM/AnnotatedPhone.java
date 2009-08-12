/**
 * 
 */
package edu.berkeley.nlp.HMM;

public class AnnotatedPhone extends Phone {
	private int offset;

	public AnnotatedPhone(Phone unannotatatedPhone, int offset) {
		super(unannotatatedPhone.getLabel());
		this.offset = offset;
	}

	public boolean equals(Object o) {
		return (super.equals(o) && (o instanceof AnnotatedPhone) && ((AnnotatedPhone) o).offset == offset);
	}

	public int hashCode() {
		return toString().hashCode();
	}

	public String toString() {
		return getLabel() + "-" + offset+"\t";
	}
	
	public Phone getUnnannotatedPhone()
	{
		return new Phone(getLabel());
	}

}