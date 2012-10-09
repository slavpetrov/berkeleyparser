package edu.berkeley.nlp.util;

/**
 * A class for Double objects that you can change.
 * 
 * @author Dan Klein
 */
public final class MutableDouble extends Number implements Comparable {

	private double d;

	// Mutable
	public void set(double d) {
		this.d = d;
	}

	public int hashCode() {
		long bits = Double.doubleToLongBits(d);
		return (int) (bits ^ (bits >>> 32));
	}

	/**
	 * Compares this object to the specified object. The result is
	 * <code>true</code> if and only if the argument is not <code>null</code>
	 * and is an <code>MutableDouble</code> object that contains the same
	 * <code>double</code> value as this object. Note that a MutableDouble isn't
	 * and can't be equal to an Double.
	 * 
	 * @param obj
	 *            the object to compare with.
	 * @return <code>true</code> if the objects are the same; <code>false</code>
	 *         otherwise.
	 */
	public boolean equals(Object obj) {
		if (obj instanceof MutableDouble) {
			return d == ((MutableDouble) obj).d;
		}
		return false;
	}

	public String toString() {
		return Double.toString(d);
	}

	// Comparable interface

	/**
	 * Compares two <code>MutableDouble</code> objects numerically.
	 * 
	 * @param anotherMutableDouble
	 *            the <code>MutableDouble</code> to be compared.
	 * @return Tthe value <code>0</code> if this <code>MutableDouble</code> is
	 *         equal to the argument <code>MutableDouble</code>; a value less
	 *         than <code>0</code> if this <code>MutableDouble</code> is
	 *         numerically less than the argument <code>MutableDouble</code>;
	 *         and a value greater than <code>0</code> if this
	 *         <code>MutableDouble</code> is numerically greater than the
	 *         argument <code>MutableDouble</code> (signed comparison).
	 */
	public int compareTo(MutableDouble anotherMutableDouble) {
		double thisVal = this.d;
		double anotherVal = anotherMutableDouble.d;
		return (thisVal < anotherVal ? -1 : (thisVal == anotherVal ? 0 : 1));
	}

	/**
	 * Compares this <code>MutableDouble</code> object to another object. If the
	 * object is an <code>MutableDouble</code>, this function behaves like
	 * <code>compareTo(MutableDouble)</code>. Otherwise, it throws a
	 * <code>ClassCastException</code> (as <code>MutableDouble</code> objects
	 * are only comparable to other <code>MutableDouble</code> objects).
	 * 
	 * @param o
	 *            the <code>Object</code> to be compared.
	 * @return 0/-1/1
	 * @throws <code>ClassCastException</code> if the argument is not an
	 *         <code>MutableDouble</code>.
	 * @see java.lang.Comparable
	 */
	public int compareTo(Object o) {
		return compareTo((MutableDouble) o);
	}

	// Number interface
	public int intValue() {
		return (int) d;
	}

	public long longValue() {
		return (long) d;
	}

	public short shortValue() {
		return (short) d;
	}

	public byte byteValue() {
		return (byte) d;
	}

	public float floatValue() {
		return (float) d;
	}

	public double doubleValue() {
		return d;
	}

	public MutableDouble() {
		this(0);
	}

	public MutableDouble(double d) {
		this.d = d;
	}

	public double increment(double inc) {
		this.d += inc;
		return d;
	}

	private static final long serialVersionUID = 624465615824626762L;
}
