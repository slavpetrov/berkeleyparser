package edu.berkeley.nlp.util;

/**
 * A class for Integer objects that you can change.
 * 
 * @author Dan Klein
 */
public final class MutableInteger extends Number implements Comparable {

	private int i;

	// Mutable
	public void set(int i) {
		this.i = i;
	}

	public int hashCode() {
		return i;
	}

	/**
	 * Compares this object to the specified object. The result is
	 * <code>true</code> if and only if the argument is not <code>null</code>
	 * and is an <code>MutableInteger</code> object that contains the same
	 * <code>int</code> value as this object. Note that a MutableInteger isn't
	 * and can't be equal to an Integer.
	 * 
	 * @param obj
	 *            the object to compare with.
	 * @return <code>true</code> if the objects are the same; <code>false</code>
	 *         otherwise.
	 */
	public boolean equals(Object obj) {
		if (obj instanceof MutableInteger) {
			return i == ((MutableInteger) obj).i;
		}
		return false;
	}

	public String toString() {
		return Integer.toString(i);
	}

	// Comparable interface

	/**
	 * Compares two <code>MutableInteger</code> objects numerically.
	 * 
	 * @param anotherMutableInteger
	 *            the <code>MutableInteger</code> to be compared.
	 * @return Tthe value <code>0</code> if this <code>MutableInteger</code> is
	 *         equal to the argument <code>MutableInteger</code>; a value less
	 *         than <code>0</code> if this <code>MutableInteger</code> is
	 *         numerically less than the argument <code>MutableInteger</code>;
	 *         and a value greater than <code>0</code> if this
	 *         <code>MutableInteger</code> is numerically greater than the
	 *         argument <code>MutableInteger</code> (signed comparison).
	 */
	public int compareTo(MutableInteger anotherMutableInteger) {
		int thisVal = this.i;
		int anotherVal = anotherMutableInteger.i;
		return (thisVal < anotherVal ? -1 : (thisVal == anotherVal ? 0 : 1));
	}

	/**
	 * Compares this <code>MutableInteger</code> object to another object. If
	 * the object is an <code>MutableInteger</code>, this function behaves like
	 * <code>compareTo(MutableInteger)</code>. Otherwise, it throws a
	 * <code>ClassCastException</code> (as <code>MutableInteger</code> objects
	 * are only comparable to other <code>MutableInteger</code> objects).
	 * 
	 * @param o
	 *            the <code>Object</code> to be compared.
	 * @return 0/-1/1
	 * @throws <code>ClassCastException</code> if the argument is not an
	 *         <code>MutableInteger</code>.
	 * @see java.lang.Comparable
	 */
	public int compareTo(Object o) {
		return compareTo((MutableInteger) o);
	}

	// Number interface
	public int intValue() {
		return i;
	}

	public long longValue() {
		return (long) i;
	}

	public short shortValue() {
		return (short) i;
	}

	public byte byteValue() {
		return (byte) i;
	}

	public float floatValue() {
		return (float) i;
	}

	public double doubleValue() {
		return (double) i;
	}

	public MutableInteger() {
		this(0);
	}

	public MutableInteger(int i) {
		this.i = i;
	}

	private static final long serialVersionUID = 624465615824626762L;
}
